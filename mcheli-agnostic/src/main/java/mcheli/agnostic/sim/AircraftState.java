package mcheli.agnostic.sim;

/**
 * Seam for the opaque mod/gameplay booleans that gate whole physics branches but are NOT physics themselves
 * (they live on the dependent entity: health, the pilot seat, hover mode, gunner mode, ...). Every {@link FlightModel}
 * reads {@link #isDestroyed()} and {@link #hasPilot()}; the per-type extensions ({@link HeliState},
 * {@link PlaneState}, {@link TankState}) add the predicates only their model needs.
 */
public interface AircraftState {
    boolean isDestroyed();

    /**
     * Whether a PILOT occupies seat 0 — the reference gates powered flight/driving on {@code getRiddenByEntity()}
     * (the seat-0 pilot only; the plane, tank and ground vehicle also require {@code !isDead}), NOT on any passenger.
     * A craft left with only a gunner aboard, or whose lone pilot moved to a gunner seat, therefore spools its throttle
     * DOWN instead of holding it. Passenger order is mount order, so {@code passengers().get(0)} is not the pilot.
     *
     * <p>Deliberately NOT defaulted. This gates the entire powered branch of every model, so a defaulted value silently
     * decides whether an unpiloted craft holds its throttle — and the old heli {@code default true} did exactly that: it
     * made a riderless stub claim to be its own pilot, which inverted the reference's condition (absence of a rider
     * must mean NO pilot) and left the trace suite red for six weeks without anyone having to acknowledge it. Forcing
     * every implementer to answer is the point.
     */
    boolean hasPilot();

    /** {@code isGunnerMode || isHoveringMode} — reference {@code MCH_EntityAircraft.isHovering()}. False for a type
     *  that has neither mode; {@link HeliState} and {@link PlaneState} re-declare it abstract. */
    default boolean isHovering() { return false; }

    /** A wreck that has already crash-detonated (reference {@code isExploded}): its throttle bleeds faster
     *  ({@link AircraftFlightController#decayWreckThrottle}). False while intact. */
    default boolean isExploded() { return false; }
}
