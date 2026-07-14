package mcheli.dependent.entity;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.sim.AircraftFlightController;
import mcheli.agnostic.sim.AircraftSimState;
import mcheli.agnostic.sim.ControlInput;
import mcheli.agnostic.sim.FlightModel;
import mcheli.agnostic.sim.RotationSolver;
import mcheli.agnostic.sim.TankControlMapping;
import mcheli.agnostic.sim.TankFlightModel;
import mcheli.agnostic.sim.TankState;
import mcheli.agnostic.tank.MCH_TankInfo;
import mcheli.dependent.control.MchControlState;
import mcheli.agnostic.tank.MCH_TankInfoManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The tank entity — ONE class serving EVERY tank config (m1a2, t-90, leopard2, …), selected by the synced config
 * name; the per-category flight model is {@code TankFlightModel} (forward {@code rot2Vec3} thrust, weak {@code
 * throttle/8} lift, no VTOL). Holding throttle-up drives forward, A/D turn the hull, and the config {@code StepHeight}
 * + wheel-terrain tilt let it climb. Rotation is CLIENT-authoritative (free mouse-look; the hull turns under the view).
 * The concrete {@link MCH_TankInfo} is resolved from the config name via {@link MCH_TankInfoManager}.
 */
public class MchTank extends AbstractMchVehicle {

    private static final FlightModel MODEL = new TankFlightModel();
    private static final MCH_TankInfo FALLBACK = buildFallback();

    private final TankState tankState = new DemoTankState(this);
    private final AircraftSimState simState = new AircraftSimState(0.07);
    private RotationSolver.ControlMapping mapping; // lazy — needs the resolved concrete MCH_TankInfo

    public MchTank(EntityType<? extends MchTank> type, Level level) {
        super(type, level);
    }

    @Override protected MCH_AircraftInfo resolveInfo(String name) {
        MCH_TankInfo i = MCH_TankInfoManager.get(name);
        return i != null ? i : FALLBACK;
    }

    @Override protected String modelDir() { return "tanks"; }

    /** Reference {@code MCH_ClientTankTickHandler} sweeps the tank radar at 10°/tick (twice the heli/vehicle rate). */
    @Override protected int radarSweepSpeed() { return 10; }


    /** Fuel burns with the FLIGHT-SIM throttle (reference getThrottle), not the display enginePower —
     *  which idles at 0.5 while merely ridden and would drain the tank at rest. */
    @Override protected double simThrottle() { return this.simState.getCurrentThrottle(); }

    // Reference MCH_EntityTank.getSoundVolume/getSoundPitch: volume = SoundVolume × T × 0.7; pitch = 0.5 + T×0.5, where
    // T = max(forward throttle, maneuver term) — see soundThrottle() below. Default "prop" is the reference's
    // copy-paste default, but every bundled tank sets Sound= so it never surfaces.
    @Override protected String defaultEngineSound() { return "prop"; }
    @Override public float engineSoundVolume() { return configSoundVolume() * getThrottleInput() * 0.7F; }
    @Override public float engineSoundPitch() { return configSoundPitch() * (0.5F + getThrottleInput() * 0.5F); }

    /** The tank revs when MANEUVERING, not just driving forward — the reference's {@code soundVolumeTarget} climbs to
     *  0.75 whenever the hull is turning or reversing ({@code moveLeft/moveRight/throttleDown}). Synced as the sound
     *  throttle so {@code getThrottleInput() = max(forwardThrottle, maneuver)} matches the reference's {@code max(...)}. */
    @Override protected double soundThrottle() {
        MchControlState c = getControlState();
        boolean maneuvering = c.moveLeft || c.moveRight || c.throttleDown;
        return Math.max(simThrottle(), maneuvering ? 0.75 : 0.0);
    }

    /** Reference {@code MCH_EntityTank.canSwitchGunnerMode}:159 hard-returns false — a tank pilot always aims the turret
     *  by looking, so there is no separate gunner mode. (No bundled tank config sets {@code EnableGunnerMode} anyway.) */
    @Override public boolean canSwitchGunnerMode() { return false; }
    /** Reference {@code MCH_EntityTank.setAngles:1119-1126}: the FIRST-PERSON rider's look pitch is clamped every frame
     *  to the hull-projected pitch + the config {@code MinRotationPitch/MaxRotationPitch}. */
    @Override public float[] riderPitchClampNow() {
        MCH_AircraftInfo info = weaponHostInfo();
        return info != null ? new float[]{info.minRotationPitch, info.maxRotationPitch} : null;
    }
    @Override public boolean riderPitchClampFirstPersonOnly() { return true; }
    @Override public boolean riderPitchClampHullRelative() { return true; }

    @Override protected RotationSolver.ControlMapping controlMapping() {
        if (this.mapping == null) {
            this.mapping = new TankControlMapping(this.ref, (MCH_TankInfo) weaponHostInfo(), this.simState, this.tankState);
        }
        return this.mapping;
    }
    @Override protected void onConfigChanged() { this.mapping = null; }

    /** Steer with A/D, but keep free mouse-look (no camera lock / cockpit-parent) — the hull turns under the view. */
    @Override public boolean locksViewToVehicle() { return false; }

    /** Config-driven climb: the vanilla move/collide step-up uses maxUpStep() (entity default 0 = can't climb any
     *  rise), so feed the tank config's {@code StepHeight} — this is what lets the tank crest block edges + hills. */
    @Override public float maxUpStep() { return weaponHostInfo().stepHeight; }

    /** A tank wreck does NOT tumble (reference {@code MCH_EntityTank} zeroes all {@code rotDestroyed*}) — it just
     *  stops and burns where it dies. */
    @Override protected void seedWreckTumble() {
        setRotDestroyedYaw(0.0F);
        setRotDestroyedPitch(0.0F);
        setRotDestroyedRoll(0.0F);
    }
    @Override protected void applyWreckTumble() { /* no tumble */ }

    /** A tank only takes attitude-crash damage when essentially flipped (reference {@code MCH_EntityTank}=91°), so it
     *  can crest steep hills without self-destructing. */
    @Override protected float giveDamageRot() { return 91.0F; }

    @Override
    protected void tickPhysics(ControlInput in) {
        AircraftFlightController.tickServer(this.ref, weaponHostInfo(), this.simState, in, this.tankState, MODEL);
    }

    private static MCH_TankInfo buildFallback() {
        MCH_TankInfo ti = new MCH_TankInfo("");
        ti.speed = 0.35F;
        ti.gravity = -0.1F;   // heavier than the model's lift terms so a tank stays grounded (real configs -0.06..-0.1)
        ti.gravityInWater = -0.1F;
        ti.motionFactor = 0.96F;
        ti.throttleUpDown = 1.0F;
        ti.mobilityYawOnGround = 2.0F;
        ti.isFloat = false;
        ti.maxFuel = 0;
        return ti;
    }
}
