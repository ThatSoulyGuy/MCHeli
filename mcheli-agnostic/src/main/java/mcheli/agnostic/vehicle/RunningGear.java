package mcheli.agnostic.vehicle;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.math.MchMath;

/**
 * The cosmetic running-gear kinematics of a vehicle: road-wheel spin and steer, and the per-side crawler-belt scroll and
 * roller spin (differential on pivot turns). ZERO Minecraft imports. Accumulated from the actual forward movement and
 * yaw rate, like the rotor, and NOT synced: position and rotation already interpolate on every client. Drives the
 * {@code $wheelN} spin/steer and the per-side {@code $track_rollerN} spin + crawler-belt scroll.
 *
 * <p>Transliterated from the port's own client tick (AbstractMchVehicle {@code updateWheelTrackPhase}, baseline lines
 * 5146-5192, after its {@code info == null} return), which the dependent layer calls once per client tick with the
 * vehicle's position, yaw and the three config facts below. The steer runs for EVERY config, like the reference
 * {@code updatePartWheel} (1.7.10 {@code MCH_EntityAircraft}:1635, whenever {@code getAcInfo() != null}): the common
 * steering wheel ({@code AddPartSteeringWheel}) and the steering search lights read it too, and a config can have those
 * without any road wheel or track. The spin and the tracks only run for a config that draws them.
 */
public final class RunningGear {

    /** Yaw-rate (rad/tick) to [-1,1] steer fraction. */
    public static final float STEER_GAIN = 8.0F;

    private float wheelSpin, prevWheelSpin;         // deg [0,360): road-wheel spin about the X axle
    private float wheelSteer, prevWheelSteer;       // [-1,1]: steered-wheel yaw fraction (× the wheel's rotDir degrees)
    private final float[] trackScroll = new float[2], prevTrackScroll = new float[2]; // [0,1) belt scroll per side (0=left,1=right)
    private final float[] rollerSpin = new float[2], prevRollerSpin = new float[2];   // deg [0,360) roller spin per side
    private double lastTrackX, lastTrackZ;          // last position for the per-tick forward-distance delta
    private float lastTrackYaw;                     // last yaw for the per-tick yaw-rate (track/wheel differential)
    private boolean trackInit;

    /**
     * Advance the wheel-spin + track-scroll phase from the SIGNED forward distance moved this tick (so parked
     * wheels/tracks stop and reverse scrolls backward).
     *
     * @param x              the vehicle's X this tick
     * @param z              the vehicle's Z this tick
     * @param yawDeg         the vehicle's yaw (degrees) this tick
     * @param runningGear    the config draws wheels, crawler tracks or track rollers ({@link #runningGear})
     * @param halfTrackWidth the half track gauge ({@link #halfTrackWidth})
     * @param linkLen        the crawler link length ({@link #linkLength})
     */
    public void tick(double x, double z, float yawDeg, boolean runningGear, float halfTrackWidth, float linkLen) {
        this.prevWheelSteer = this.wheelSteer;
        if (runningGear) {
            this.prevWheelSpin = this.wheelSpin;
            this.prevTrackScroll[0] = this.trackScroll[0];
            this.prevTrackScroll[1] = this.trackScroll[1];
            this.prevRollerSpin[0] = this.rollerSpin[0];
            this.prevRollerSpin[1] = this.rollerSpin[1];
        }
        if (!this.trackInit) {
            this.lastTrackX = x;
            this.lastTrackZ = z;
            this.lastTrackYaw = yawDeg;
            this.trackInit = true;
        }
        double dx = x - this.lastTrackX;
        double dz = z - this.lastTrackZ;
        this.lastTrackX = x;
        this.lastTrackZ = z;
        // Forward distance = motion projected onto the hull-forward vector (yaw about -Y -> forward = (-sin, 0, cos)).
        double yr = Math.toRadians(yawDeg);
        double fwd = -dx * Math.sin(yr) + dz * Math.cos(yr);
        // Yaw rate (rad/tick): drives the wheel STEER and the per-side track DIFFERENTIAL (a point at lateral offset x
        // gains forward speed -yawRate·x, so on a pivot the two tracks scroll opposite while the hull barely moves).
        double yawRate = Math.toRadians(MchMath.wrapAngleTo180(yawDeg - this.lastTrackYaw));
        this.lastTrackYaw = yawDeg;

        // Reference rotYawWheel is +1 for a LEFT turn; MC yaw DECREASES on a left turn, so negate the yaw rate.
        float steerTarget = MchMath.clamp((float) (-yawRate * STEER_GAIN), -1.0F, 1.0F);
        this.wheelSteer += (steerTarget - this.wheelSteer) * 0.3F; // smoothed toward the turn direction
        if (!runningGear) {
            return;
        }
        this.wheelSpin = wrap360(this.wheelSpin + (float) (fwd * 220.0)); // ~0.52-block-radius wheel: 220°/block

        float w = halfTrackWidth;
        for (int side = 0; side < 2; side++) {
            // side 1 = +X = the hull's LEFT (at yaw 0 the hull faces +Z and +X is east = left); side 0 = -X = right —
            // matching the parse (TrackRoller/CrawlerTrack side = x/z >= 0 ? 1 : 0). Yaw INCREASES on a right turn and
            // omega = -yawRate*Y, so a point at lateral offset x gains forward speed +yawRate*x: on a right turn the
            // LEFT (+X) track runs faster — the sign below must be +.
            float sideX = side == 1 ? w : -w;
            float sideSpeed = (float) (fwd + yawRate * sideX); // blocks/tick along this track
            this.rollerSpin[side] = wrap360(this.rollerSpin[side] + sideSpeed * 220.0F); // rollers spin +
            this.trackScroll[side] = wrap01(this.trackScroll[side] - sideSpeed / linkLen); // belt scrolls - (reference sign)
        }
    }

    /** Whether the config draws any running gear (a road wheel, a crawler track or a track roller). */
    public static boolean runningGear(MCH_AircraftInfo info) {
        return !(info.partWheel.isEmpty() && info.partCrawlerTrack.isEmpty() && info.partTrackRoller.isEmpty());
    }

    /** Half the track gauge (lateral distance from centre to a track), for the per-side differential. Some configs
     *  (KV-2) declare their crawler tracks at Z = 0.00 (the offset lives in the mesh), so a near-zero source falls
     *  through to the rollers, then the wheels, then a 1-block default — else the differential silently dies. */
    public static float halfTrackWidth(MCH_AircraftInfo info) {
        if (!info.partCrawlerTrack.isEmpty()) {
            float z = Math.abs(info.partCrawlerTrack.get(0).z);
            if (z > 0.01F) {
                return z;
            }
        }
        if (!info.partTrackRoller.isEmpty()) {
            float x = Math.abs((float) info.partTrackRoller.get(0).pos.x());
            if (x > 0.01F) {
                return x;
            }
        }
        if (!info.partWheel.isEmpty()) {
            float x = Math.abs((float) info.partWheel.get(0).pos.x());
            if (x > 0.01F) {
                return x;
            }
        }
        return 1.0F;
    }

    /** The crawler link length the belt scroll divides by: the first crawler track's {@code len} (at least 0.05), or
     *  0.37 for a config without one. */
    public static float linkLength(MCH_AircraftInfo info) {
        return info.partCrawlerTrack.isEmpty() ? 0.37F : Math.max(0.05F, info.partCrawlerTrack.get(0).len);
    }

    private static float wrap360(float a) {
        a %= 360.0F;
        return a < 0.0F ? a + 360.0F : a;
    }
    private static float wrap01(float a) {
        a %= 1.0F;
        return a < 0.0F ? a + 1.0F : a;
    }

    /** Road-wheel spin (degrees, [0,360)). */
    public float wheelSpin() { return this.wheelSpin; }
    /** Last tick's road-wheel spin, for render interpolation. */
    public float prevWheelSpin() { return this.prevWheelSpin; }
    /** Steered-wheel yaw fraction ([-1,1], times the wheel's rotDir degrees). */
    public float wheelSteer() { return this.wheelSteer; }
    /** Last tick's steer fraction, for render interpolation. */
    public float prevWheelSteer() { return this.prevWheelSteer; }
    /** Crawler-belt scroll ([0,1)) of {@code side} (1 = the +X track, 0 = the -X track; masked to the low bit). */
    public float trackScroll(int side) { return this.trackScroll[side & 1]; }
    /** Last tick's belt scroll of {@code side}. */
    public float prevTrackScroll(int side) { return this.prevTrackScroll[side & 1]; }
    /** Track-roller spin (degrees, [0,360)) of {@code side}. */
    public float rollerSpin(int side) { return this.rollerSpin[side & 1]; }
    /** Last tick's roller spin of {@code side}. */
    public float prevRollerSpin(int side) { return this.prevRollerSpin[side & 1]; }
}
