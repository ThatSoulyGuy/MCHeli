package mcheli.agnostic.sim;

/**
 * Plane-specific extension of the {@link AircraftState} mod-state seam: the gameplay predicates that gate the
 * plane control/physics (gunner, target-drone, fuel, wing, canopy) plus the VTOL nozzle state and the variable
 * sweep-wing factor feeding {@code getMaxSpeed}. {@code PlaneFlightModel} casts the controller's
 * {@code AircraftState} to this.
 */
public interface PlaneState extends AircraftState {
    boolean isGunnerMode();
    /** {@code isGunnerMode || isHoveringMode} — gates the mouse rotation ({@code canUpdate*} = base &amp;&amp; !isHovering). */
    boolean isHovering();
    boolean isTargetDrone();
    boolean canUseFuel();
    boolean canUseWing();
    boolean isCanopyClose();
    void switchGunnerMode(boolean on);

    /** VTOL nozzle rotation in degrees (0 = level thrust, up to 90 = vertical). */
    float getNozzleRotation();
    int getVtolMode();

    /** True if a variable-sweep wing/hatch part exists (partWing preferred, else partHatch). */
    boolean hasVariableSweepPart();
    /** The sweep part's morph factor (0..1) — {@code partWing.getFactor()} in the reference. */
    float sweepPartFactor();
}
