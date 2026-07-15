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

    /** True while a fold-capable heli is folded AND resting on the ground — enables the parked-taxi nudge
     *  ({@code onUpdate_ControlFoldBladeAndOnGround}). Default false (no rotor-fold, or airborne/unfolded). */
    default boolean isFoldedOnGround() { return false; }

    /** Whether a PILOT occupies seat 0 — the reference gates powered flight on {@code getRiddenByEntity() != null} (the
     *  pilot only), NOT on any passenger, so a heli left with only a gunner spools DOWN instead of hovering. Default
     *  true (a single-rider stub is its own pilot). */
    default boolean hasPilot() { return true; }

    void switchHoveringMode(boolean on);
    void switchGunnerMode(boolean on);
}
