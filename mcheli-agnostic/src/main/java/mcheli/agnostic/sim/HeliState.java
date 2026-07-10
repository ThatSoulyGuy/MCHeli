package mcheli.agnostic.sim;

/**
 * Heli-specific extension of the {@link AircraftState} mod-state seam — the gameplay booleans that gate the
 * helicopter control path (hover mode/state, gunner mode, fuel, blades, canopy) plus the two mode-switch side
 * effects the reference performs when fuel runs out. {@code HeliFlightModel} casts the controller's
 * {@code AircraftState} to this, the same way {@code VehicleFlightModel} casts the info to {@code MCH_VehicleInfo}.
 */
public interface HeliState extends AircraftState {
    /** True while actually hovering (physics branch), vs {@link #isHoveringMode()} (the requested mode). */
    boolean isHovering();
    boolean isHoveringMode();
    boolean isGunnerMode();
    boolean canUseFuel(boolean checkOtherSeat);
    boolean canUseFuel();
    boolean canUseBlades();
    boolean isCanopyClose();

    void switchHoveringMode(boolean on);
    void switchGunnerMode(boolean on);
}
