package mcheli.agnostic.sim;

import mcheli.agnostic.math.MchMath;

/**
 * The pilot-aim latch and the turret slew of a vehicle, stepped once per tick on both sides.
 *
 * <p><b>Aim latch</b>: the reference {@code lastRiderYaw}/{@code lastRiderPitch} ({@code MCH_EntityAircraft:1486-1497},
 * NBT {@code "AcLastRYaw"}): the PILOT's look, refreshed only while a live pilot is aboard and the craft is intact, and
 * HELD after dismount so the turret, the guns and the crew seats keep their last pose instead of snapping to
 * hull-forward.
 *
 * <p><b>Turret slew</b> (reference {@code updateWeaponsRotation} ±15°/tick): the shared turret ring EASES toward the
 * operator's aim at a capped rate instead of snapping. It is tracked in WORLD space, so a hull turn does not drag it
 * (only a change in the operator's own look slews it). The host derives the rel-hull angle from {@link #slew()} and
 * {@link #prevSlew()}; the render, the rotSeat crew orbit and the ring-mounted muzzle and shot all read that one angle,
 * so they stay locked.
 *
 * <p>Moved from the dependent AbstractMchVehicle {@code updateAimLatch}/{@code updateTurretSlew} (baseline lines
 * 867-901), with the entity reads turned into parameters and {@code Mth.wrapDegrees} replaced by the identical
 * {@link MchMath#wrapAngleTo180}. The NBT restore keeps the semantics of baseline lines 2073-2077.
 */
public final class AimLatch {
    /** The reference {@code updateWeaponsRotation} rate cap, degrees per tick. */
    public static final float TURRET_SLEW_MAX = 15.0F;

    private float lastRiderYaw, prevLastRiderYaw;
    private float lastRiderPitch, prevLastRiderPitch;
    private boolean aimLatched;

    private float turretSlewYaw, prevTurretSlewYaw;
    private boolean turretSlewInit;

    /**
     * One tick: refresh the latch from the pilot's look when {@code pilotLive} (a live pilot aboard an intact craft),
     * else hold it, then step the turret slew. {@code pilotYaw}/{@code pilotPitch} are read only when
     * {@code pilotLive}; {@code hullYaw} only while nothing is latched (the hull-forward target).
     */
    public void update(boolean pilotLive, float pilotYaw, float pilotPitch, float hullYaw) {
        if (pilotLive) {
            this.prevLastRiderYaw = this.aimLatched ? this.lastRiderYaw : pilotYaw;
            this.prevLastRiderPitch = this.aimLatched ? this.lastRiderPitch : pilotPitch;
            this.lastRiderYaw = pilotYaw;
            this.lastRiderPitch = pilotPitch;
            this.aimLatched = true;
        } else {
            this.prevLastRiderYaw = this.lastRiderYaw;   // no pilot: HOLD the last aim (reference latch)
            this.prevLastRiderPitch = this.lastRiderPitch;
        }
        updateTurretSlew(hullYaw);
    }

    /** Step the world-space turret yaw toward the operator's look by at most {@link #TURRET_SLEW_MAX}° this tick — the
     *  reference {@code updateWeaponsRotation} rate cap. Runs both sides from the (synced) operator look, so the render
     *  slew and the server's fire-time turret agree within a step. */
    private void updateTurretSlew(float hullYaw) {
        float target = this.aimLatched ? this.lastRiderYaw : hullYaw; // operator world look, else hull-forward
        if (!this.turretSlewInit) {
            this.turretSlewYaw = this.prevTurretSlewYaw = MchMath.wrapAngleTo180(target); // snap on the first tick (no start jerk)
            this.turretSlewInit = true;
            return;
        }
        this.prevTurretSlewYaw = this.turretSlewYaw;
        float diff = MchMath.wrapAngleTo180(target - this.turretSlewYaw);
        if (diff > TURRET_SLEW_MAX) {
            this.turretSlewYaw = MchMath.wrapAngleTo180(this.turretSlewYaw + TURRET_SLEW_MAX);
        } else if (diff < -TURRET_SLEW_MAX) {
            this.turretSlewYaw = MchMath.wrapAngleTo180(this.turretSlewYaw - TURRET_SLEW_MAX);
        } else {
            this.turretSlewYaw = MchMath.wrapAngleTo180(target);
        }
    }

    /** The saved latch (NBT {@code AcLastRYaw}/{@code AcLastRPitch}, reference {@code readEntityFromNBT}): latched at
     *  that look with no interpolation step; the turret slew is untouched. */
    public void restore(float yaw, float pitch) {
        this.lastRiderYaw = this.prevLastRiderYaw = yaw;
        this.lastRiderPitch = this.prevLastRiderPitch = pitch;
        this.aimLatched = true;
    }

    /** True once a live pilot has set the latch (or it was restored). */
    public boolean latched() { return this.aimLatched; }

    /** The latched pilot yaw (reference {@code lastRiderYaw}). */
    public float yaw() { return this.lastRiderYaw; }

    /** The latched pilot yaw one tick earlier. */
    public float prevYaw() { return this.prevLastRiderYaw; }

    /** The latched pilot pitch (reference {@code lastRiderPitch}). */
    public float pitch() { return this.lastRiderPitch; }

    /** The latched pilot pitch one tick earlier. */
    public float prevPitch() { return this.prevLastRiderPitch; }

    /** True once the slew has been stepped (snapped onto its first target). */
    public boolean slewInit() { return this.turretSlewInit; }

    /** The world-space turret yaw, wrapped to [-180, 180). */
    public float slew() { return this.turretSlewYaw; }

    /** The world-space turret yaw one tick earlier. */
    public float prevSlew() { return this.prevTurretSlewYaw; }
}
