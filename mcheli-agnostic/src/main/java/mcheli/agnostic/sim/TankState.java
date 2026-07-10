package mcheli.agnostic.sim;

/**
 * Tank-specific extension of the {@link AircraftState} mod-state seam: the gameplay predicates gating the tank
 * control/physics (gunner, target-drone, fuel, canopy). {@code TankFlightModel} casts the controller's
 * {@code AircraftState} to this. (Brake is rider input via {@code ControlInput.brake()}; the wheel terrain-follow
 * is deferred to a future {@code WheelTerrainSolver} seam.)
 */
public interface TankState extends AircraftState {
    boolean isGunnerMode();
    boolean isTargetDrone();
    boolean canUseFuel();
    boolean isCanopyClose();
    void switchGunnerMode(boolean on);
}
