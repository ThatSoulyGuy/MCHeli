package mcheli.dependent.entity;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.helicopter.MCH_HeliInfo;
import mcheli.agnostic.helicopter.MCH_HeliInfoManager;
import mcheli.agnostic.sim.AircraftFlightController;
import mcheli.agnostic.sim.AircraftSimState;
import mcheli.agnostic.sim.ControlInput;
import mcheli.agnostic.sim.FlightModel;
import mcheli.agnostic.sim.HeliControlMapping;
import mcheli.agnostic.sim.HeliFlightModel;
import mcheli.agnostic.sim.HeliState;
import mcheli.agnostic.sim.RotationSolver;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The helicopter entity — ONE class serving EVERY helicopter config (ah-64, mi-28, ka-52, …). Which vehicle it is
 * comes from the synced config name ({@link AbstractMchVehicle#configName()}); the per-category flight model is
 * {@code HeliFlightModel}. Collective/cyclic flight: holding throttle-up spools the rotor and it climbs; a pilotless
 * heli develops no lift and settles under gravity. The concrete {@link MCH_HeliInfo} (model, HP, weapons, seats,
 * rotor list) is resolved from the config name via {@link MCH_HeliInfoManager}.
 */
public class MchHelicopter extends AbstractMchVehicle {

    private static final FlightModel MODEL = new HeliFlightModel();
    /** Fallback for an unknown/blank config name so the physics never sees a null info. */
    private static final MCH_HeliInfo FALLBACK = buildFallback();

    private final HeliState heliState = new DemoHeliState(this);
    private final AircraftSimState simState = new AircraftSimState(0.07); // heli idles currentSpeed at 0.07
    private final AircraftSimState rotState = new AircraftSimState();
    private RotationSolver.ControlMapping mapping; // lazy — needs the resolved concrete MCH_HeliInfo

    /** Max blade sweep at full engine power (deg/tick, ~150 rpm). */
    private static final float MAX_ROTOR_DEG_PER_TICK = 45.0F;
    // Client-side rotor spin, accumulated from the synced engine power so RPM ramps up/down with the throttle.
    private float rotorAngle;
    private float prevRotorAngle;

    public MchHelicopter(EntityType<? extends MchHelicopter> type, Level level) {
        super(type, level);
    }

    @Override protected MCH_AircraftInfo resolveInfo(String name) {
        MCH_HeliInfo i = MCH_HeliInfoManager.get(name);
        return i != null ? i : FALLBACK;
    }

    @Override protected String modelDir() { return "helicopters"; }


    /** Fuel burns with the FLIGHT-SIM throttle (reference getThrottle), not the display enginePower —
     *  which idles at 0.5 while merely ridden and would drain the tank at rest. */
    @Override protected double simThrottle() { return this.simState.getCurrentThrottle(); }
    @Override protected RotationSolver.ControlMapping controlMapping() {
        if (this.mapping == null) {
            this.mapping = new HeliControlMapping(this.ref, (MCH_HeliInfo) weaponHostInfo(), this.rotState, this.heliState);
        }
        return this.mapping;
    }
    @Override protected void onConfigChanged() { this.mapping = null; }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.prevRotorAngle = this.rotorAngle;
            this.rotorAngle = (this.rotorAngle + getEnginePower() * MAX_ROTOR_DEG_PER_TICK) % 360.0F;
        }
    }

    public float getRotorAngle() { return this.rotorAngle; }
    public float getPrevRotorAngle() { return this.prevRotorAngle; }

    @Override
    protected void tickPhysics(ControlInput in) {
        AircraftFlightController.tickServer(this.ref, weaponHostInfo(), this.simState, in, this.heliState, MODEL);
    }

    private static MCH_HeliInfo buildFallback() {
        MCH_HeliInfo hi = new MCH_HeliInfo("");
        hi.speed = 0.35F;
        hi.gravity = -0.04F;
        hi.gravityInWater = -0.04F;
        hi.rotorSpeed = 0.5F;
        hi.throttleUpDown = 1.0F;
        hi.isFloat = false;
        hi.maxFuel = 0;
        return hi;
    }
}
