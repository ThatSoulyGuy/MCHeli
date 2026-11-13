package mcheli.agnostic.sim;

/**
 * Tank-specific extension of the {@link AircraftState} mod-state seam: the gameplay predicates gating the tank
 * control/physics (gunner, target-drone, fuel, canopy). {@code TankFlightModel} casts the controller's
 * {@code AircraftState} to this. (Brake is rider input via {@code ControlInput.brake()}. The wheel terrain-follow
 * needs no predicate here — the reference's {@code WheelMng.move} has no destroyed/gunner gate either; it runs even
 * on a wreck.)
 */
public interface TankState extends AircraftState {
    boolean isGunnerMode();
    boolean isTargetDrone();
    boolean canUseFuel();
    boolean isCanopyClose();
    /** Reference {@code switchGunnerMode(mode)}: leaving restores the throttle saved on entry. */
    void switchGunnerMode(boolean on);
}
