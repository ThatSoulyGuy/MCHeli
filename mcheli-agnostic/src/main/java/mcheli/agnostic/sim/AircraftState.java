package mcheli.agnostic.sim;

/**
 * Seam for the opaque mod/gameplay booleans that gate whole physics branches but are NOT physics themselves
 * (they live on the dependent entity: health, hover mode, gunner mode, ...). Kept minimal — predicates are added
 * as each {@link FlightModel} needs them. Today only {@link #isDestroyed()} (gates {@code canFloatWater} and, later,
 * crash handling) is required by the ground-vehicle model.
 */
public interface AircraftState {
    boolean isDestroyed();
}
