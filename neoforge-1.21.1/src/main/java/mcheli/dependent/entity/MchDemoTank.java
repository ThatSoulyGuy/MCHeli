package mcheli.dependent.entity;

import mcheli.agnostic.sim.AircraftFlightController;
import mcheli.agnostic.sim.AircraftSimState;
import mcheli.agnostic.sim.ControlInput;
import mcheli.agnostic.sim.FlightModel;
import mcheli.agnostic.sim.TankFlightModel;
import mcheli.agnostic.sim.TankState;
import mcheli.agnostic.tank.MCH_TankInfo;
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

    private final MCH_TankInfo info = buildInfo();
    private final TankState tankState = new DemoTankState();
    private final AircraftSimState simState = new AircraftSimState(0.07);

    public MchDemoTank(EntityType<? extends MchDemoTank> type, Level level) {
        super(type, level);
    }

    @Override
    protected void tickPhysics(ControlInput in) {
        AircraftFlightController.tickServer(this.ref, this.info, this.simState, in, this.tankState, MODEL);
    }

    private static MCH_TankInfo buildInfo() {
        MCH_TankInfo ti = new MCH_TankInfo("demo_tank");
        ti.speed = 0.2F;            // horizontal speed cap (blocks/tick) — tanks are slow
        ti.gravity = -0.04F;
        ti.gravityInWater = -0.04F;
        ti.motionFactor = 0.96F;
        ti.throttleUpDown = 1.0F;
        ti.onGroundPitch = 0.0F;
        ti.isFloat = false;
        ti.maxFuel = 0;             // canUseFuel() true (see DemoTankState)
        return ti;
    }
}
