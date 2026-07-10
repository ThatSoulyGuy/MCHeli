package mcheli.agnostic.sim;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.value.MoveResult;
import mcheli.agnostic.value.Vec3d;

/**
 * The shared per-tick SERVER orchestrator that replaces {@code SimpleVehicleLogic}, mirroring the physics slice
 * of {@code MCH_EntityAircraft.onUpdate}: snapshot prev-throttle, run the {@link FlightModel}'s throttle/control
 * update, run its force integration, delegate the swept move to {@link EntityRef#move}, then run its post-move
 * damping. Owns NO vehicle-specific math — it delegates entirely to the {@link FlightModel}. The client
 * dead-reckoning / interpolation path stays in the dependent adapter (this is server-authoritative only).
 */
public final class AircraftFlightController {
    private AircraftFlightController() {}

    public static void tickServer(EntityRef self, MCH_AircraftInfo info, AircraftSimState st,
                                  ControlInput in, AircraftState mod, FlightModel model) {
        st.snapshotPreTick();                                   // base onUpdate: prevCurrentThrottle = currentThrottle
        model.updateThrottle(self, info, st, in, mod);          // onUpdate_Control
        Vec3d delta = model.integrateForces(self, info, st, in, mod); // onUpdate_Server up to moveEntity
        MoveResult mv = self.move(delta);                       // moveEntity: collision-resolved position commit
        model.postMoveDamp(self, info, st, mv);                 // zero blocked axes + multiplicative drag
    }
}
