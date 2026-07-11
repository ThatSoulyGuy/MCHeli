package mcheli.dependent.entity;

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
 * The demo ground vehicle: a rideable {@link Entity} driven by the ported {@code VehicleFlightModel} through
 * {@link AbstractMchVehicle}'s server tick + rider control state. Fully drivable with WASD — throttle from W/S and
 * server-authoritative keyboard yaw (0.5°/tick) from A/D; an un-ridden vehicle holds no keys and coasts.
 * {@code VehicleFlightModel} with {@code isFloat=false} never touches {@code WorldView}.
 */
public class MchDemoVehicle extends AbstractMchVehicle {

    private static final FlightModel MODEL = new VehicleFlightModel();
    private static final AircraftState ALIVE = () -> false; // never destroyed

    private final MCH_VehicleInfo info = buildInfo();
    private final AircraftSimState simState = new AircraftSimState(0.07);

    public MchDemoVehicle(EntityType<? extends MchDemoVehicle> type, Level level) {
        super(type, level);
    }

    @Override
    protected void tickPhysics(ControlInput in) {
        AircraftFlightController.tickServer(this.ref, this.info, this.simState, in, ALIVE, MODEL);
    }

    private static MCH_VehicleInfo buildInfo() {
        MCH_VehicleInfo vi = new MCH_VehicleInfo("demo_vehicle");
        vi.speed = 0.3F;
        vi.gravity = -0.04F;
        vi.gravityInWater = -0.04F;
        vi.motionFactor = 0.96F;
        vi.throttleUpDown = 1.0F;
        vi.isFloat = false;
        vi.isEnableMove = true;
        return vi;
    }
}
