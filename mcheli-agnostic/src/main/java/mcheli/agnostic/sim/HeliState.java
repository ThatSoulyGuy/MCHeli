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

    /** True while a fold-capable heli's blades are stowed (reference {@code isEnableFoldBlade && rotors.length > 0 &&
     *  getFoldBladeStat() == 0}) — the gate of the on-ground A/D hull yaw in {@code onUpdateAngles}. Default false. */
    default boolean isBladeFolded() { return false; }

    // hasPilot() (the seat-0 pilot gate of the powered-flight branch) is declared on AircraftState.

    /** Reference {@code switchHoveringMode}: honours {@code canSwitchHoveringMode} (attitude, blades, not gunner mode)
     *  and restores the pre-hover throttle when leaving. */
    void switchHoveringMode(boolean on);
    void switchGunnerMode(boolean on);
}
