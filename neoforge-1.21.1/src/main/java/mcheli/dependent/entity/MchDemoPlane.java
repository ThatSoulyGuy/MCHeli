package mcheli.dependent.entity;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.plane.MCP_PlaneInfo;
import mcheli.agnostic.plane.MCP_PlaneInfoManager;
import mcheli.agnostic.sim.AircraftFlightController;
import mcheli.agnostic.sim.AircraftSimState;
import mcheli.agnostic.sim.ControlInput;
import mcheli.agnostic.sim.FlightModel;
import mcheli.agnostic.sim.PlaneControlMapping;
import mcheli.agnostic.sim.PlaneFlightModel;
import mcheli.agnostic.sim.PlaneState;
import mcheli.agnostic.sim.RotationSolver;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Demo fixed-wing plane: a rideable {@link Entity} driven by the ported {@code PlaneFlightModel} (vectored-thrust
 * aerodynamics) through {@link AbstractMchVehicle}. Holding throttle-up (W) spools the engine; it flies forward and
 * stays aloft, while a pilotless plane makes no thrust and sinks. Exercises {@code rot2Vec3} thrust (lift + drive),
 * the {@code −0.047·(1−throttle)} sink, the {@code currentSpeed} spool, and the {@code motionFactor} horizontal
 * drag. Mouse pitch/roll/yaw steering is a later increment.
 */
public class MchDemoPlane extends AbstractMchVehicle {

    private static final FlightModel MODEL = new PlaneFlightModel();

    private final MCP_PlaneInfo info = pickInfo(); // real "a-10" config if loaded, else the hard-coded fallback
    private final PlaneState planeState = new DemoPlaneState();
    private final AircraftSimState simState = new AircraftSimState(0.07);
    // Client rotation state + the plane's mouse->rotation mapping (declared last so ref/info/planeState are set).
    private final AircraftSimState rotState = new AircraftSimState();
    private final RotationSolver.ControlMapping mapping = new PlaneControlMapping(this.ref, this.info, this.rotState, this.planeState);

    public MchDemoPlane(EntityType<? extends MchDemoPlane> type, Level level) {
        super(type, level);
    }

    @Override protected RotationSolver.ControlMapping controlMapping() { return this.mapping; }
    @Override protected MCH_AircraftInfo rotationInfo() { return this.info; }
    @Override protected MCH_AircraftInfo weaponHostInfo() { return this.info; }

    /** a-10 pilot seat = model (0, 2.8, 0.0); feet = seat - 0.5Y so the eye sits in the cockpit. */
    @Override protected net.minecraft.world.phys.Vec3 pilotFeetOffset() {
        return new net.minecraft.world.phys.Vec3(0.0, 2.3, 0.0);
    }

    @Override protected int skinCount() { return this.info.getTextureNameCount(); }
    @Override public String skinTextureName() { return this.info.getTextureName(getSkinIndex()); }

    @Override
    protected void tickPhysics(ControlInput in) {
        AircraftFlightController.tickServer(this.ref, this.info, this.simState, in, this.planeState, MODEL);
    }

    private static MCP_PlaneInfo pickInfo() {
        MCP_PlaneInfo real = MCP_PlaneInfoManager.get("a-10");
        return real != null ? real : buildInfo();
    }

    private static MCP_PlaneInfo buildInfo() {
        MCP_PlaneInfo pi = new MCP_PlaneInfo("demo_plane");
        pi.speed = 0.4F;            // horizontal speed cap (blocks/tick) — planes are fast
        pi.gravity = -0.04F;        // cancelled by the model's +0.04 term; net lift/sink comes from thrust
        pi.gravityInWater = -0.04F;
        pi.motionFactor = 0.96F;    // horizontal drag (also the post-move X/Z drag for planes)
        pi.throttleUpDown = 1.0F;
        pi.onGroundPitch = 0.0F;
        pi.isFloat = false;
        pi.maxFuel = 0;             // canUseFuel() true (see DemoPlaneState)
        pi.isVariableSweepWing = false;
        return pi;
    }
}
