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

    /**
     * Whether a PILOT occupies seat 0 — the reference gates powered flight on {@code getRiddenByEntity() != null} (the
     * pilot only), NOT on any passenger, so a heli left with only a gunner spools DOWN instead of hovering.
     *
     * <p>Deliberately NOT defaulted. This gates the entire powered-flight branch ({@code HeliFlightModel}:39), so a
     * defaulted value silently decides whether an unpiloted heli holds its collective — and the previous
     * {@code default true} did exactly that: it made a riderless stub claim to be its own pilot, which inverted the
     * reference's condition (absence of a rider must mean NO pilot) and left the trace suite red for six weeks
     * without anyone having to acknowledge it. Forcing every implementer to answer is the point.
     */
    boolean hasPilot();

    void switchHoveringMode(boolean on);
    void switchGunnerMode(boolean on);
}
