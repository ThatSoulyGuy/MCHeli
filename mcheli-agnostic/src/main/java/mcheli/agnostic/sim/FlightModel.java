package mcheli.agnostic.sim;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.value.MoveResult;
import mcheli.agnostic.value.Vec3d;

/**
 * Strategy per vehicle type encoding the server-side force integration, throttle curve and control response —
 * one implementation per 1.7.10 entity subclass. Reads the already-agnostic {@link MCH_AircraftInfo}, mutates
 * the entity only through {@link EntityRef}, and keeps its own analog state in {@link AircraftSimState}. The
 * shared {@link AircraftFlightController} sequences the three phases each server tick.
 */
public interface FlightModel {

    /** {@code onUpdate_Control}: rider input becomes throttle change + control-nudge motion + steering. */
    void updateThrottle(EntityRef self, MCH_AircraftInfo info, AircraftSimState st, ControlInput in, AircraftState mod);

    /**
     * {@code onUpdate_Server} up to (but not including) the collision move: gravity/buoyancy/thrust, horizontal
     * speed clamp, {@code currentSpeed} envelope and on-ground friction. Sets the entity motion to the computed
     * value and returns that same delta for the controller to pass to {@link EntityRef#move}.
     */
    Vec3d integrateForces(EntityRef self, MCH_AircraftInfo info, AircraftSimState st, ControlInput in, AircraftState mod);

    /**
     * Post-move: zero the motion axes the collision {@link MoveResult} reports as blocked (the reference's
     * {@code moveEntity} did this in place), then apply the multiplicative drag ({@code motionY*=0.95},
     * {@code motionX/Z*=0.99} for the ground vehicle).
     */
    void postMoveDamp(EntityRef self, MCH_AircraftInfo info, AircraftSimState st, MoveResult mv);
}
