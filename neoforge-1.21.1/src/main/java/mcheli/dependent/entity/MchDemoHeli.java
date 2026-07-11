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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Demo helicopter: a rideable {@link Entity} driven by the ported {@code HeliFlightModel} (collective/cyclic flight)
 * through {@link AbstractMchVehicle}. Holding throttle-up (W) spools the rotor and it climbs; a pilotless heli
 * develops no collective lift and settles under gravity. It exercises collective LIFT, the {@code currentSpeed}
 * spool, ground-effect probing (→ {@code NeoWorldView}), buoyancy branch selection, hover jitter (→
 * {@code NeoRandomSource}), and the 0.95/0.99 post-move drag. Mouse cyclic steering is a later increment.
 */
public class MchDemoHeli extends AbstractMchVehicle {

    private static final FlightModel MODEL = new HeliFlightModel();

    private final MCH_HeliInfo info = pickInfo(); // real "ah-64" config if loaded, else the hard-coded fallback
    private final HeliState heliState = new DemoHeliState();
    private final AircraftSimState simState = new AircraftSimState(0.07); // heli idles currentSpeed at 0.07
    // Client rotation state + the heli's mouse->rotation mapping (declared last so ref/info/heliState are set).
    private final AircraftSimState rotState = new AircraftSimState();
    private final RotationSolver.ControlMapping mapping = new HeliControlMapping(this.ref, this.info, this.rotState, this.heliState);

    /** Max blade sweep at full engine power (deg/tick, ~150 rpm). */
    private static final float MAX_ROTOR_DEG_PER_TICK = 45.0F;
    // Client-side rotor spin, accumulated from the synced engine power so RPM ramps up/down with the throttle.
    private float rotorAngle;
    private float prevRotorAngle;

    public MchDemoHeli(EntityType<? extends MchDemoHeli> type, Level level) {
        super(type, level);
    }

    @Override protected RotationSolver.ControlMapping controlMapping() { return this.mapping; }
    @Override protected MCH_AircraftInfo rotationInfo() { return this.info; }

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

    /** ah-64 pilot seat = model (0, 1.5, -0.28); feet = seat - 0.5Y -> eye lands at the reference (0, 2.62, -0.28). */
    @Override protected net.minecraft.world.phys.Vec3 pilotFeetOffset() {
        return new net.minecraft.world.phys.Vec3(0.0, 1.0, -0.28);
    }

    @Override protected int skinCount() { return this.info.getTextureNameCount(); }
    @Override public String skinTextureName() { return this.info.getTextureName(getSkinIndex()); }

    @Override
    protected void tickPhysics(ControlInput in) {
        AircraftFlightController.tickServer(this.ref, this.info, this.simState, in, this.heliState, MODEL);
    }

    private static MCH_HeliInfo pickInfo() {
        MCH_HeliInfo real = MCH_HeliInfoManager.get("ah-64");
        return real != null ? real : buildInfo();
    }

    private static MCH_HeliInfo buildInfo() {
        MCH_HeliInfo hi = new MCH_HeliInfo("demo_heli");
        hi.speed = 0.35F;           // horizontal cruise-speed cap (blocks/tick)
        hi.gravity = -0.04F;        // collective near full throttle (~0.055) overcomes it -> climb
        hi.gravityInWater = -0.04F;
        hi.rotorSpeed = 0.5F;       // cosmetic rotor-spin rate
        hi.throttleUpDown = 1.0F;   // collective spool-rate multiplier
        hi.onGroundPitch = 0.0F;
        hi.isFloat = false;
        hi.maxFuel = 0;             // canUseFuel() true (see DemoHeliState)
        return hi;
    }
}
