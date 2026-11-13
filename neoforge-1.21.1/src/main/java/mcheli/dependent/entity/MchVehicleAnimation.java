package mcheli.dependent.entity;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.vehicle.RadarSweep;
import mcheli.agnostic.vehicle.RecoilKick;
import mcheli.agnostic.vehicle.RunningGear;
import mcheli.agnostic.weapon.BarrelSpin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The client-only cosmetic animation of one vehicle, ticked from {@link AbstractMchVehicle}'s client branch and read by
 * the renderers, the HUD and the camera through {@link AbstractMchVehicle#animation()}:
 * <ul>
 *   <li>the running gear (accumulated from actual forward movement + yaw rate, like the rotor; NOT synced —
 *       position/rotation already interpolate on every client). Drives {@code $wheelN} spin/steer, per-side
 *       {@code $track_rollerN} spin + crawler-belt scroll (differential on pivot turns), and the {@code $partN}
 *       gatling barrel ({@link RunningGear}, {@link BarrelSpin});</li>
 *   <li>the rotor / propeller spin (reference {@code rotationRotor}), driven by the CONFIG {@code rotorSpeed};</li>
 *   <li>the entity radar (reference {@code MCH_Radar} + {@code MCH_EntityAircraft.updateRadar}): the sweep
 *       ({@link RadarSweep}) and the blip scan, for the HUD radar dial;</li>
 *   <li>the weapon recoil kick (reference {@code MCH_EntityAircraft.updateRecoil}), driven by the synced
 *       {@code DATA_RECOIL_SEQ} ({@link RecoilKick}).</li>
 * </ul>
 * Only the read methods are public. It reads {@code DATA_FIRING} (through {@link AbstractMchVehicle#isFiringRecently}),
 * {@code DATA_RECOIL_SEQ/VALUE/YAW} and the engine power, and writes no synced accessor.
 */
public final class MchVehicleAnimation {

    private final AbstractMchVehicle v;

    // ---- client-only running-gear + gun animation ----
    private final RunningGear runningGear = new RunningGear();
    private final BarrelSpin barrel = new BarrelSpin();

    // ---- Rotor / propeller spin (reference rotationRotor) — CLIENT-only, driven by the CONFIG rotorSpeed ----
    private float rotorSpin;
    private float prevRotorSpin;

    // ---- Entity radar (reference MCH_Radar + MCH_EntityAircraft.updateRadar) — CLIENT-only, drives the HUD radar dial ----
    private static final double[] EMPTY_XZ = new double[0];
    private final RadarSweep radar = new RadarSweep(); // sweep angle 0..360, advances radarSweepSpeed() per client tick
    private double[] radarEntityXZ = EMPTY_XZ; // neutral blips: relative (x0,z0,x1,z1,…) in blocks from this vehicle
    private double[] radarEnemyXZ = EMPTY_XZ;  // hostile blips (Monster), same layout

    // ---- Weapon recoil kick (reference MCH_EntityAircraft.updateRecoil) — CLIENT-only visual, driven by DATA_RECOIL_SEQ ----
    private final RecoilKick recoil = new RecoilKick();

    MchVehicleAnimation(AbstractMchVehicle v) {
        this.v = v;
    }

    /** CLIENT: advance every cosmetic animation one tick, in the reference order. */
    void tickClient() {
        updateWheelTrackPhase();
        advanceRotorSpin(); // heli rotor / plane propeller spin — CONFIG rotorSpeed, no hardcoded rate
        advanceRadar();     // entity-radar sweep + periodic entity scan (only while ridden), for the cockpit HUD
        advanceRecoil();    // weapon recoil kick countdown (model + first-person camera), from the synced trigger
    }

    /** CLIENT: advance the wheel-spin + track-scroll phase from the SIGNED forward distance moved this tick (so parked
     *  wheels/tracks stop and reverse scrolls backward). Cosmetic + client-local, exactly like the rotor. */
    private void updateWheelTrackPhase() {
        // Gatling barrel — the EXACT reference accumulation (MCH_WeaponSet.update): near-instant spin-up to 74 deg/tick
        // while a weapon recently fired (synced DATA_FIRING), ~50-tick coast-down. The 360 wrap subtracts from prev in
        // lockstep so the interpolation seam stays valid.
        this.barrel.tick(v.isFiringRecently(), () -> v.getRandom().nextInt(3));

        // The steer runs for EVERY config, like the reference updatePartWheel (:1635, whenever getAcInfo() != null):
        // the common steering wheel (AddPartSteeringWheel) and the steering search lights read it too, and a config can
        // have those without any road wheel or track. The spin and the tracks only run for a config that draws them.
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (info == null) {
            return;
        }
        this.runningGear.tick(v.getX(), v.getZ(), v.getYRot(), RunningGear.runningGear(info),
            RunningGear.halfTrackWidth(info), RunningGear.linkLength(info));
    }

    /** Road-wheel spin (degrees, [0,360)) about the X axle. */
    public float wheelSpin() { return this.runningGear.wheelSpin(); }
    /** Last tick's road-wheel spin, for render interpolation. */
    public float prevWheelSpin() { return this.runningGear.prevWheelSpin(); }
    /** Steered-wheel yaw fraction [-1,1] (× the wheel's rotDir degrees). */
    public float wheelSteer() { return this.runningGear.wheelSteer(); }
    /** Last tick's steer fraction, for render interpolation. */
    public float prevWheelSteer() { return this.runningGear.prevWheelSteer(); }
    /** Crawler-belt scroll [0,1) of {@code side} (1 = the +X track, 0 = the -X track). */
    public float trackScroll(int side) { return this.runningGear.trackScroll(side); }
    /** Last tick's belt scroll of {@code side}. */
    public float prevTrackScroll(int side) { return this.runningGear.prevTrackScroll(side); }
    /** Track-roller spin (degrees, [0,360)) of {@code side}. */
    public float rollerSpin(int side) { return this.runningGear.rollerSpin(side); }
    /** Last tick's roller spin of {@code side}. */
    public float prevRollerSpin(int side) { return this.runningGear.prevRollerSpin(side); }
    /** Gatling barrel angle (degrees). */
    public float barrelSpin() { return this.barrel.barrelSpin(); }
    /** Last tick's gatling barrel angle, for render interpolation. */
    public float prevBarrelSpin() { return this.barrel.prevBarrelSpin(); }

    /** Advance the rotor/propeller spin angle this client tick — the port of the reference {@code rotationRotor +=
     *  throttle × info.rotorSpeed} ({@code MCP_EntityPlane}:173, heli equivalent). The per-tick rate is the config
     *  {@code RotorSpeed} (heli 79.99, prop plane 47.94; 0 for jets/tanks so nothing spins) scaled by the engine power,
     *  so a running engine idles the blades and full throttle spins them fast. NOTHING is hardcoded — the rate is the
     *  vehicle's own config value. */
    private void advanceRotorSpin() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        float speed = info != null ? info.rotorSpeed : 0.0F;
        this.prevRotorSpin = this.rotorSpin;
        if (speed != 0.0F) {
            this.rotorSpin = (this.rotorSpin + v.getEnginePower() * speed) % 360.0F;
        }
    }

    /** Current rotor/propeller spin angle (degrees), for the renderer. */
    public float rotorSpin() { return this.rotorSpin; }
    /** Previous-tick rotor/propeller spin angle, for render interpolation. */
    public float prevRotorSpin() { return this.prevRotorSpin; }

    /** Advance the radar sweep and, once per full rotation, re-scan nearby entities. Only runs while the vehicle is
     *  ridden (the radar is a rider-only cockpit display), matching the reference calling {@code updateRadar} from the
     *  client tick handler for the ridden aircraft. */
    private void advanceRadar() {
        if (!v.isRadarMounted() || v.getPassengers().isEmpty()) {
            return;
        }
        if (this.radar.advance(v.radarSweepSpeed())) {
            scanRadar(64);
        }
    }

    /** Port of {@code MCH_Radar.updateXZ}: collect every living entity within {@code range} blocks (circular) as a blip
     *  at its position RELATIVE to this vehicle, split into hostile ({@code Monster}) and neutral, skipping any entity
     *  buried under 5+ solid blocks (the reference's crude line-of-sight cull). Client-only. */
    private void scanRadar(int range) {
        java.util.List<Double> ent = new java.util.ArrayList<>();
        java.util.List<Double> enm = new java.util.ArrayList<>();
        net.minecraft.world.phys.AABB box = v.getBoundingBox().inflate(range);
        net.minecraft.core.BlockPos.MutableBlockPos bp = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (Entity e : v.level().getEntities(v, box)) {
            if (!(e instanceof LivingEntity)) {
                continue;
            }
            double x = e.getX() - v.getX();
            double z = e.getZ() - v.getZ();
            if (x * x + z * z >= (double) range * range) {
                continue;
            }
            int ex = (int) Math.floor(e.getX());
            int ez = (int) Math.floor(e.getZ());
            int y = Math.max(1, 1 + (int) e.getY());
            int blocks = 0;
            for (; y < 200 && blocks < 5; y++) {
                if (!v.level().getBlockState(bp.set(ex, y, ez)).isAir()) {
                    blocks++;
                }
            }
            if (blocks >= 5) {
                continue; // buried under thick cover — the reference hides it from the radar
            }
            java.util.List<Double> dst = e instanceof net.minecraft.world.entity.monster.Monster ? enm : ent;
            dst.add(x);
            dst.add(z);
        }
        this.radarEntityXZ = toXZArray(ent);
        this.radarEnemyXZ = toXZArray(enm);
    }

    private static double[] toXZArray(java.util.List<Double> list) {
        if (list.isEmpty()) {
            return EMPTY_XZ;
        }
        double[] out = new double[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    /** Interpolated radar sweep angle for the HUD sweep line (reference {@code getRadarRot}: smooth the tick sweep,
     *  adding 360 across the 355→0 wrap so it never spins backwards). */
    public float radarRotInterp(float partialTick) {
        return this.radar.interp(partialTick);
    }

    /** Neutral radar blips (relative x,z pairs in blocks) for the HUD {@code DrawEntityRadar}. */
    public double[] radarEntityXZ() { return this.radarEntityXZ; }
    /** Hostile radar blips (relative x,z pairs) for the HUD {@code DrawEnemyRadar}. */
    public double[] radarEnemyXZ() { return this.radarEnemyXZ; }

    /** Advance the client recoil kick each client tick: a new shot (seq bump) restarts the 30-tick countdown; else decay. */
    private void advanceRecoil() {
        this.recoil.observe(v.getEntityData().get(AbstractMchVehicle.DATA_RECOIL_SEQ),
            v.getEntityData().get(AbstractMchVehicle.DATA_RECOIL_VALUE),
            v.getEntityData().get(AbstractMchVehicle.DATA_RECOIL_YAW));
    }

    /** Recoil PITCH offset (degrees) this frame — a nose-up kick for a forward gun (reference splits the kick by the
     *  weapon aim: pitch = cos(recoilYaw), roll = sin(recoilYaw)), easing out over the count. Added to the model render
     *  and the first-person camera, NOT the physics — so it never fights the client-authored rotation sync. */
    public float recoilPitchDeg(float partialTick) {
        float k = this.recoil.envelope(partialTick);
        return k == 0.0F ? 0.0F : -k * Mth.cos(this.recoil.yaw() * Mth.DEG_TO_RAD);
    }

    /** Recoil ROLL offset (degrees) this frame — the side-gun component. */
    public float recoilRollDeg(float partialTick) {
        float k = this.recoil.envelope(partialTick);
        return k == 0.0F ? 0.0F : k * Mth.sin(this.recoil.yaw() * Mth.DEG_TO_RAD);
    }
}
