package mcheli.agnostic.sim;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.value.MoveResult;
import mcheli.agnostic.value.Vec3d;

/**
 * The shared per-tick SERVER orchestrator, mirroring the physics slice
 * of {@code MCH_EntityAircraft.onUpdate}: snapshot prev-throttle, run the {@link FlightModel}'s throttle/control
 * update and the {@code onUpdateAircraft} tail, run its force integration, delegate the swept move to
 * {@link EntityRef#move}, run its post-move damping, then wrap the rotation. Owns NO vehicle-specific math — it
 * delegates entirely to the {@link FlightModel}. The client dead-reckoning / interpolation path stays in the
 * dependent adapter (this is server-authoritative only).
 */
public final class AircraftFlightController {
    private AircraftFlightController() {}

    public static void tickServer(EntityRef self, MCH_AircraftInfo info, AircraftSimState st,
                                  ControlInput in, AircraftState mod, FlightModel model) {
        st.snapshotPreTick();                                   // base onUpdate: prevCurrentThrottle = currentThrottle
        model.updateThrottle(self, info, st, in, mod);          // onUpdate_Control
        model.afterControl(self, info, st, mod);                // onUpdateAircraft: hover levelling, wreck bleed
        Vec3d delta = model.integrateForces(self, info, st, in, mod); // onUpdate_Server up to moveEntity
        MoveResult mv = self.move(delta);                       // moveEntity: collision-resolved position commit
        model.postMoveDamp(self, info, st, mv);                 // zero blocked axes + multiplicative drag
        wrapRotation(self);                                     // onUpdate_Server tail: setRotation(yaw, pitch)
    }

    /**
     * Reference {@code onUpdateAircraft} (MCH_EntityHeli:349-351, MCP_EntityPlane:191-193): while hovering (gunner or
     * hover mode) an intact craft levels its pitch by 5% per tick, once it is within 70 degrees of level. The mouse no
     * longer pitches a hovering craft ({@code canUpdatePitch} is false), so without this it would keep the attitude it
     * entered the mode with. Runs per tick on the server and on the rotation-owning client.
     */
    public static void levelHoverPitch(EntityRef self, AircraftState mod) {
        if (!mod.isDestroyed() && mod.isHovering() && Math.abs(self.pitch()) < 70.0F) {
            self.setRotation(self.yaw(), self.pitch() * 0.95F);
        }
    }

    /**
     * The plane's own gunner-mode gate (reference {@code MCP_EntityPlane.canSwitchGunnerMode}:140-147, on top of the
     * shared aircraft gate): a near-level attitude (|wrapped roll| and |wrapped pitch| at most 40), a fast throttle
     * (over 0.6) and nothing collidable within 5 blocks below ({@code getBlockIdY(this, 3, -5) == 0}), so a parked or
     * just-airborne plane cannot be gunned. It runs on the server AND on the pilot's client (the gunner-mode key's
     * pre-check), so {@code throttle} must be the value that side actually has: the sim throttle on the server, the
     * synced copy on a client (the client never advances the sim state; the reference client lerped the throttle).
     */
    public static boolean canPlaneSwitchGunnerMode(EntityRef self, double throttle) {
        float roll = Math.abs(mcheli.agnostic.math.MchMath.wrapAngleTo180(self.roll()));
        float pitch = Math.abs(mcheli.agnostic.math.MchMath.wrapAngleTo180(self.pitch()));
        if (roll > 40.0F || pitch > 40.0F) {
            return false;
        }
        return throttle > 0.6F && GroundProbe.blockIdInColumn(self, 3, -5) == 0;
    }

    /**
     * Reference {@code onUpdateAircraft} (MCH_EntityHeli:353-361, MCP_EntityPlane:195-203, MCH_EntityTank:200-208): a
     * wreck's throttle bleeds by x0.8 per tick once a block is within 2 below ({@code getBlockIdY(this, 3, -2) > 0}) and
     * by x0.98 per tick once it has exploded, on top of the -0.00125 decay of the control branch. A falling wreck
     * therefore keeps some collective/thrust in the air but loses it near the ground and after the crash.
     */
    public static void decayWreckThrottle(EntityRef self, AircraftSimState st, AircraftState mod) {
        if (mod.isDestroyed() && st.getCurrentThrottle() > 0.0) {
            if (GroundProbe.blockIdInColumn(self, 3, -2) > 0) {
                st.setCurrentThrottle(st.getCurrentThrottle() * 0.8);
            }
            if (mod.isExploded()) {
                st.setCurrentThrottle(st.getCurrentThrottle() * 0.98);
            }
        }
    }

    /**
     * Reference {@code MCH_EntityAircraft.setRotation(y, p)} = {@code setRotYaw(y % 360); setRotPitch(p % 360)}, called at
     * the end of every {@code onUpdate_Server} (MCH_EntityHeli:905, MCP_EntityPlane:830, MCH_EntityTank:796). It keeps a
     * yaw that only the server writes (target-drone auto-pilot, ground-vehicle steering) bounded. The reference ground
     * vehicle wrapped on its client instead (MCH_EntityVehicle:364); wrapping its server copy too is harmless, since
     * every consumer of the yaw is periodic.
     */
    public static void wrapRotation(EntityRef self) {
        self.setRotation(self.yaw() % 360.0F, self.pitch() % 360.0F);
    }
}
