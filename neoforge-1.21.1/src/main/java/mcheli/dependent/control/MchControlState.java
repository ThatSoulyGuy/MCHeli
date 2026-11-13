package mcheli.dependent.control;

import mcheli.agnostic.sim.ControlInput;

/**
 * Mutable, server-authoritative holder of one rider's <em>raw</em> control inputs for a single piloted vehicle.
 *
 * <p>This is the clean seam between "rider keys" and the agnostic flight sim. {@link ServerboundControlPayload}
 * overwrites these fields from the rider's control-flag bitset, sent on change plus a keepalive (the port of
 * {@code MCH_PacketPlayerControlBase}: {@code throttleUp/throttleDown/moveLeft/moveRight/useBrake}, the fire trigger and
 * the completed lock id, plus the client settings the reference sent in {@code MCH_PacketNotifyClientSetting}). The
 * agnostic layer never sees this mutable state directly: each tick the server calls {@link #snapshot(float)} to produce
 * an immutable {@link ControlInput} value object, which is what
 * {@link mcheli.agnostic.sim.AircraftFlightController#tickServer} consumes. One instance exists per seat
 * ({@code AbstractMchVehicle.controlState(seat)}; only seat 0's drive bits are ever set); it is NOT thread safe and is
 * touched only from the server tick / packet-handler thread. Self-tests drive a vehicle by writing it directly.
 *
 * <p>The mouse stick is not here: rotation is client-authoritative (the rider's client runs the rotation mappings and
 * ships the result in {@link ServerboundRotationPayload}), so the server snapshot carries a centred stick and no
 * flight-sim flag (both matter only to those client mappings).
 */
public final class MchControlState {

    // --- raw control bits (momentary; packet-driven each tick) ---
    /** Rider is holding "throttle up" this tick. */
    public boolean throttleUp;
    /** Rider is holding "throttle down" this tick. */
    public boolean throttleDown;
    /** Rider is holding "move / steer left" this tick. */
    public boolean moveLeft;
    /** Rider is holding "move / steer right" this tick. */
    public boolean moveRight;
    /** Rider is holding the brake this tick (reference {@code useBrake}). */
    public boolean brake;
    /** Rider is holding the fire trigger this tick (dependent-side combat input; not part of the flight snapshot). */
    public boolean fire;
    /** Entity id of the rider's currently COMPLETED missile lock (client resolves the lock; ships the id here for the
     *  server to fire a guided round at). -1 == no lock. Not part of the flight snapshot. */
    public int lockTargetId = -1;

    // --- latched mode flags + client settings (sent with every control packet; not momentary) ---
    /** Free-look camera mode is engaged (decouples view from vehicle heading). */
    public boolean freeLook;
    /** The rider's auto-throttle-down preference for this vehicle kind (reference {@code cs_heliAutoThrottleDown} /
     *  {@code cs_planeAutoThrottleDown} / {@code cs_tankAutoThrottleDown}, from {@code MchConfig.CLIENT}). */
    public boolean autoThrottleDown;
    /** The rider's DismountAll preference (reference {@code cs_dismountAll}): when this seat-0 pilot leaves, the mob
     *  crew dismounts too. Not part of the flight snapshot. */
    public boolean dismountAll;

    public MchControlState() {}

    /**
     * Build an immutable {@link ControlInput} from the current raw state for one server tick.
     *
     * @param partialTicks render/interpolation fraction to stamp into the snapshot; server ticks pass {@code 1.0F}.
     * @return a fresh {@link ControlInput} carrying a copy of every field (never {@code null}).
     */
    public ControlInput snapshot(float partialTicks) {
        return new ControlInput(
            0.0, 0.0,
            throttleUp, throttleDown, moveLeft, moveRight, brake,
            freeLook, false, autoThrottleDown,
            partialTicks);
    }

    /**
     * Zero the momentary control bits (the fields a per-tick packet overwrites), leaving the latched mode flags and
     * client settings intact. Useful when a rider dismounts or a packet is missed and the vehicle should coast rather
     * than keep the last held keys.
     */
    public void clearMomentary() {
        throttleUp = throttleDown = moveLeft = moveRight = brake = fire = false;
        lockTargetId = -1;
    }
}
