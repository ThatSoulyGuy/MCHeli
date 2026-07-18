package mcheli.dependent.control;

import mcheli.agnostic.sim.ControlInput;

/**
 * Mutable, server-authoritative holder of one rider's <em>raw</em> control inputs for a single piloted vehicle.
 *
 * <p>This is the clean seam between "rider keys" and the agnostic flight sim. In the finished port a
 * {@code ServerboundControlPacket} handler will overwrite these fields every tick from the decoded control-flag
 * bitset (the equivalent of {@code MCH_PacketPlayerControlBase}: {@code throttleUp/throttleDown/moveLeft/moveRight/
 * useBrake} plus the mouse deltas and the derived mode toggles). The agnostic layer never sees this mutable state
 * directly: each tick the server calls {@link #snapshot(float)} to produce an immutable
 * {@link ControlInput} value object, which is what
 * {@link mcheli.agnostic.sim.AircraftFlightController#tickServer} consumes.
 *
 * <p><strong>Deferred plumbing.</strong> The CLIENT capture path (keybinds -&gt; mouse helper -&gt; averaged deltas
 * -&gt; flight-sim roll stick -&gt; encode -&gt; {@code ServerboundControlPacket} -&gt; server) is a networking step
 * that is intentionally NOT implemented here (see the package javadoc). Until that packet exists, an orchestrator /
 * self-test can drive a vehicle by mutating this holder directly (e.g. {@code state.throttleUp = true}) and calling
 * {@link #snapshot(float)}. One instance is expected per piloted vehicle (or per controlling seat); it is NOT thread
 * safe and is meant to be touched only from the server tick / packet-handler thread.
 *
 * <p>Field semantics mirror {@link ControlInput}:
 * <ul>
 *   <li><b>raw control bits</b> ({@link #throttleUp} {@link #throttleDown} {@link #moveLeft} {@link #moveRight}
 *       {@link #brake}) &mdash; the momentary key-held state sent by the rider each tick.</li>
 *   <li><b>mouse delta</b> ({@link #deltaX} {@link #deltaY}) &mdash; the per-frame averaged pointer motion used for
 *       yaw/pitch (and, outside flight-sim mode, roll). Transient: clear it after each tick with
 *       {@link #resetMouseDelta()} if the packet source does not re-send a zero.</li>
 *   <li><b>flight-sim roll stick</b> ({@link #stickX} {@link #stickY}) &mdash; the accumulated virtual stick position
 *       used when {@link #flightSimMode} is on (roll = mouse, yaw = key).</li>
 *   <li><b>mode flags</b> ({@link #freeLook} {@link #gunnerMode} {@link #flightSimMode} {@link #autoThrottleDown})
 *       &mdash; latched modes derived server-side from the packet's toggle bits / seat config / vehicle config,
 *       not momentary key state.</li>
 * </ul>
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

    // --- mouse delta (per-frame averaged pointer motion) ---
    /** Averaged horizontal mouse delta for this frame (yaw, or roll outside flight-sim mode). */
    public double deltaX;
    /** Averaged vertical mouse delta for this frame (pitch). */
    public double deltaY;

    // --- flight-sim virtual roll stick ---
    /** Accumulated flight-sim roll-stick X position (used when {@link #flightSimMode}). */
    public double stickX;
    /** Accumulated flight-sim roll-stick Y position (used when {@link #flightSimMode}). */
    public double stickY;

    // --- latched mode flags (derived server-side; not momentary) ---
    /** Free-look camera mode is engaged (decouples view from vehicle heading). */
    public boolean freeLook;
    /** Rider is in gunner mode (aiming a turret/optic rather than flying). */
    public boolean gunnerMode;
    /** Mouse-flight-sim control scheme is active (roll = mouse, yaw = key). */
    public boolean flightSimMode;
    /** Vehicle/config auto-throttle-down assist is enabled for this rider. */
    public boolean autoThrottleDown;

    public MchControlState() {}

    /**
     * Build an immutable {@link ControlInput} from the current raw state for one server tick.
     *
     * @param partialTicks render/interpolation fraction to stamp into the snapshot; server ticks pass {@code 1.0F}.
     * @return a fresh {@link ControlInput} carrying a copy of every field (never {@code null}).
     */
    public ControlInput snapshot(float partialTicks) {
        return new ControlInput(
            stickX, stickY,
            deltaX, deltaY,
            throttleUp, throttleDown, moveLeft, moveRight, brake,
            freeLook, gunnerMode, flightSimMode, autoThrottleDown,
            partialTicks);
    }

    /** Zero the per-frame mouse delta; call after a tick has consumed it if the source does not re-send zeros. */
    public void resetMouseDelta() {
        deltaX = 0.0;
        deltaY = 0.0;
    }

    /**
     * Zero the momentary control bits and mouse delta (the fields a per-tick packet overwrites), leaving the latched
     * mode flags and the flight-sim stick position intact. Useful when a rider dismounts or a packet is missed and
     * the vehicle should coast rather than keep the last held keys.
     */
    public void clearMomentary() {
        throttleUp = throttleDown = moveLeft = moveRight = brake = fire = false;
        lockTargetId = -1;
        deltaX = 0.0;
        deltaY = 0.0;
    }
}
