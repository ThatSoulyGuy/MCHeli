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
import mcheli.agnostic.tank.MCH_TankInfoManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Demo tank: a rideable {@link Entity} driven by the ported {@code TankFlightModel} through
 * {@link AbstractMchVehicle}. The tank shares the plane's forward model ({@code rot2Vec3} thrust) with weaker
 * {@code throttle/8} lift and no VTOL/sweep, and preserves the tank-specific trap: {@code applyOnGroundPitch} is a
 * no-op (the reference tank overrides it). Holding throttle-up (W) drives forward. Keyboard hull YAW needs
 * RotationSolver wired server-side — a later increment; the {@code WheelMng} terrain-follow is likewise deferred.
 */
public class MchDemoTank extends AbstractMchVehicle {

    private static final FlightModel MODEL = new TankFlightModel();

    // Use the REAL "m1a2" config if it loaded; else the hard-coded fallback. (Config load happens at commonSetup,
    // before any entity is constructed.)
    private final MCH_TankInfo info = pickInfo();
    private final TankState tankState = new DemoTankState();
    private final AircraftSimState simState = new AircraftSimState(0.07);
    // Server-side hull-yaw mapping (shares the physics sim-state so the pivot-turn logic sees live throttle).
    private final RotationSolver.ControlMapping mapping = new TankControlMapping(this.ref, this.info, this.simState, this.tankState);

    public MchDemoTank(EntityType<? extends MchDemoTank> type, Level level) {
        super(type, level);
    }

    @Override protected RotationSolver.ControlMapping serverRotationMapping() { return this.mapping; }
    @Override protected MCH_AircraftInfo rotationInfo() { return this.info; }
    @Override protected MCH_AircraftInfo weaponHostInfo() { return this.info; }

    /** m1a2 has no AddSeat directive; seat the commander at the turret hatch (model ~y2.5) so the eye clears the hull. */
    @Override protected net.minecraft.world.phys.Vec3 pilotFeetOffset() {
        return new net.minecraft.world.phys.Vec3(0.0, 2.5, 0.0);
    }

    @Override protected int skinCount() { return this.info.getTextureNameCount(); }
    @Override public String skinTextureName() { return this.info.getTextureName(getSkinIndex()); }

    @Override
    protected void tickPhysics(ControlInput in) {
        AircraftFlightController.tickServer(this.ref, this.info, this.simState, in, this.tankState, MODEL);
    }

    private static MCH_TankInfo pickInfo() {
        MCH_TankInfo real = MCH_TankInfoManager.get("m1a2");
        return real != null ? real : buildInfo();
    }

    private static MCH_TankInfo buildInfo() {
        MCH_TankInfo ti = new MCH_TankInfo("demo_tank");
        ti.speed = 0.35F;           // horizontal speed cap (blocks/tick) — real tanks are 0.45-0.7
        // Gravity MUST be heavier than the model's +0.04 lift-cancel + throttle/8 lift, or the tank flies. Real
        // tank configs use -0.06..-0.1 (t-90/m1a2 = -0.1); -0.04 (the base default) left it neutral -> it took off.
        ti.gravity = -0.1F;
        ti.gravityInWater = -0.1F;
        ti.motionFactor = 0.96F;
        ti.throttleUpDown = 1.0F;
        ti.onGroundPitch = 0.0F;
        ti.mobilityYawOnGround = 2.0F; // hull-turn rate (real tanks 2.0-3.0); also a bit faster than the default 1.0
        ti.isFloat = false;
        ti.maxFuel = 0;             // canUseFuel() true (see DemoTankState)
        return ti;
    }
}
