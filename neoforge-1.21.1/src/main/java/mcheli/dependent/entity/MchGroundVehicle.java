package mcheli.dependent.entity;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.sim.AircraftFlightController;
import mcheli.agnostic.sim.AircraftSimState;
import mcheli.agnostic.sim.AircraftState;
import mcheli.agnostic.sim.ControlInput;
import mcheli.agnostic.sim.FlightModel;
import mcheli.agnostic.sim.VehicleFlightModel;
import mcheli.agnostic.vehicle.MCH_VehicleInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The ground-vehicle entity — ONE class serving EVERY "vehicle" config, selected by the synced config name; the
 * per-category flight model is {@code VehicleFlightModel}. In the real content the vehicles bucket is static
 * emplacements (AA guns / SAM / ammo box — {@code CanMove=false}), so they sit still, can be ridden, and fire; the
 * flight model with {@code isEnableMove=false} keeps them in place. The concrete {@link MCH_VehicleInfo} is resolved
 * from the config name via {@link mcheli.agnostic.vehicle.MCH_VehicleInfoManager}.
 */
public class MchGroundVehicle extends AbstractMchVehicle {

    private static final FlightModel MODEL = new VehicleFlightModel();
    private static final MCH_VehicleInfo FALLBACK = buildFallback();

    // Live destroyed-state seam: a wrecked vehicle stops driving (its flight model gates on !isDestroyed()).
    private final AircraftState state = this::isDestroyed;
    private final AircraftSimState simState = new AircraftSimState(0.07);

    public MchGroundVehicle(EntityType<? extends MchGroundVehicle> type, Level level) {
        super(type, level);
    }

    @Override protected MCH_AircraftInfo resolveInfo(String name) {
        MCH_VehicleInfo i = mcheli.agnostic.vehicle.MCH_VehicleInfoManager.get(name);
        return i != null ? i : FALLBACK;
    }

    @Override protected String modelDir() { return "vehicles"; }


    /** Fuel burns with the FLIGHT-SIM throttle (reference getThrottle), not the display enginePower —
     *  which idles at 0.5 while merely ridden and would drain the tank at rest. */
    @Override protected double simThrottle() { return this.simState.getCurrentThrottle(); }
    /** Free the rider's look so an emplacement (Phalanx / VADS / 46cm / SAM) can be AIMED with the mouse: the gun renders
     *  toward the rider's look and {@link #fireBodyYaw}/{@link #fireBodyPitch} launch along that same free look — the
     *  reference {@code MCH_EntityVehicle} rider aims a static mount exactly as the tank rider aims the turret. */
    @Override public boolean locksViewToVehicle() { return false; }

    /**
     * Port of {@code MCH_EntityVehicle.useCurrentWeapon}: an emplacement weapon with NO real yaw traverse
     * ({@code minYaw==0 || maxYaw==0} — the searam missiles, a fixed Phalanx) fires along the operator's LOOK, because
     * the reference momentarily snaps the whole body to {@code prm.user.rotationYaw/Pitch} for the shot. The shared
     * {@code fireSelectedWeapon} then rotates the muzzle position AND direction by this look, so the round leaves the
     * launcher exactly where the gunner aims (the mount's own 0..0 clamp adds nothing). A mount WITH a real yaw range
     * keeps the hull here and traverses per-weapon, matching the reference's {@code maxYaw!=0 && minYaw!=0} guard.
     */
    @Override protected float fireBodyYaw(Entity shooter, MCH_AircraftInfo.Weapon mount) {
        return firesAlongLook(mount) ? shooter.getYRot() : super.fireBodyYaw(shooter, mount);
    }
    @Override protected float fireBodyPitch(Entity shooter, MCH_AircraftInfo.Weapon mount) {
        return firesAlongLook(mount) ? shooter.getXRot() : super.fireBodyPitch(shooter, mount);
    }
    private static boolean firesAlongLook(MCH_AircraftInfo.Weapon mount) {
        return mount != null && (mount.minYaw == 0.0F || mount.maxYaw == 0.0F);
    }

    /** Reference {@code MCH_ClientVehicleTickHandler:98} — emplacement riders ALWAYS render from the config camera. */
    @Override public boolean usesConfigCameraEye() { return weaponHostInfo() != null; }

    /** Reference {@code MCH_ClientCommonTickHandler:334-343} — the camera-roll path never runs for emplacements. */
    @Override public boolean cameraRollFade() { return false; }

    /** Reference {@code MCH_ClientVehicleTickHandler:54-57} — clamp the rider's look pitch EVERY tick while riding, to
     *  the VEHICLE-level {@code MinRotationPitch/MaxRotationPitch} (NOT the seat's -30/70 defaults). */
    @Override public float[] riderPitchClampNow() {
        MCH_AircraftInfo info = weaponHostInfo();
        if (info instanceof MCH_VehicleInfo vi) {
            return new float[]{vi.minRotationPitch, vi.maxRotationPitch};
        }
        return null;
    }

    /** A ground-vehicle wreck does NOT tumble (reference {@code MCH_EntityVehicle} never touches rotDestroyed*) — it
     *  just stops and burns where it dies, like the tank. */
    @Override protected void seedWreckTumble() {
        setRotDestroyedYaw(0.0F);
        setRotDestroyedPitch(0.0F);
        setRotDestroyedRoll(0.0F);
    }
    @Override protected void applyWreckTumble() { /* no tumble */ }

    @Override
    protected void tickPhysics(ControlInput in) {
        AircraftFlightController.tickServer(this.ref, weaponHostInfo(), this.simState, in, this.state, MODEL);
    }

    private static MCH_VehicleInfo buildFallback() {
        MCH_VehicleInfo vi = new MCH_VehicleInfo("");
        vi.speed = 0.3F;
        vi.gravity = -0.04F;
        vi.gravityInWater = -0.04F;
        vi.motionFactor = 0.96F;
        vi.throttleUpDown = 1.0F;
        vi.isFloat = false;
        return vi;
    }
}
