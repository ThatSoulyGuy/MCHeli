package mcheli.agnostic.sim;

/**
 * Immutable per-tick / per-render-frame value object carrying everything the rider produces, so the sim never
 * touches LWJGL / keybinds / DataWatcher. The dependent client adapter fills it from the mouse helper, keybinds,
 * config and invert/sensitivity; the server adapter fills the booleans from the control-flag packet bits.
 */
public record ControlInput(
    double stickX, double stickY,   // roll-stick position (flight-sim mode)
    double deltaX, double deltaY,   // per-frame mouse delta (averaged)
    boolean throttleUp, boolean throttleDown, boolean moveLeft, boolean moveRight, boolean brake,
    boolean freeLook, boolean gunnerMode, boolean flightSimMode, boolean autoThrottleDown,
    float partialTicks) {

    public static final ControlInput NONE =
        new ControlInput(0, 0, 0, 0, false, false, false, false, false, false, false, false, false, 0.0F);

    /** Ground-vehicle control: forward/back throttle + left/right steering. */
    public static ControlInput ground(boolean up, boolean down, boolean left, boolean right) {
        return new ControlInput(0, 0, 0, 0, up, down, left, right, false, false, false, false, false, 0.0F);
    }

    /** Copy with {@code throttleUp} forced on (the reference's target-drone path sets {@code throttleUp = true}). */
    public ControlInput withThrottleUp() {
        return new ControlInput(stickX, stickY, deltaX, deltaY, true, throttleDown, moveLeft, moveRight, brake,
            freeLook, gunnerMode, flightSimMode, autoThrottleDown, partialTicks);
    }

    /** Copy with the mouse-stick position (stickX/stickY) zeroed, keeping deltaX/deltaY — the reference's free-look
     *  zeroes ONLY the stick args before still invoking the per-vehicle control mapping. */
    public ControlInput withZeroStick() {
        return new ControlInput(0, 0, deltaX, deltaY, throttleUp, throttleDown, moveLeft, moveRight, brake,
            freeLook, gunnerMode, flightSimMode, autoThrottleDown, partialTicks);
    }
}
