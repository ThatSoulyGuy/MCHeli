package mcheli.agnostic.sim;

/**
 * Immutable per-tick / per-render-frame value object carrying everything the rider produces, so the sim never
 * touches LWJGL / keybinds / DataWatcher. The dependent client adapter fills it from the virtual mouse stick (already
 * scaled by the sensitivity/invert config), the movement keys and the client config; the server adapter fills the
 * booleans from the control-flag packet bits.
 *
 * <p>The reference's {@code setAngles} also received the averaged per-frame mouse delta, but only to turn the PLAYER's
 * view on an axis the aircraft does not override (hover/gunner mode). The dependent client keeps vanilla's own mouse
 * look on that axis instead ({@link RotationSolver.ControlMapping#overridesRiderYaw}), so no mapping needs the delta.
 * Gunner mode is likewise read from the synced entity state ({@code *State.isGunnerMode()}), not from the input.
 */
public record ControlInput(
    double stickX, double stickY,   // the accumulated virtual mouse stick (reference mouseRollDeltaX/Y)
    boolean throttleUp, boolean throttleDown, boolean moveLeft, boolean moveRight, boolean brake,
    boolean freeLook, boolean flightSimMode, boolean autoThrottleDown,
    float partialTicks) {

    public static final ControlInput NONE =
        new ControlInput(0, 0, false, false, false, false, false, false, false, false, 0.0F);

    /** Ground-vehicle control: forward/back throttle + left/right steering. */
    public static ControlInput ground(boolean up, boolean down, boolean left, boolean right) {
        return new ControlInput(0, 0, up, down, left, right, false, false, false, false, 0.0F);
    }

    /** Copy with {@code throttleUp} forced on (the reference's target-drone path sets {@code throttleUp = true}). */
    public ControlInput withThrottleUp() {
        return new ControlInput(stickX, stickY, true, throttleDown, moveLeft, moveRight, brake,
            freeLook, flightSimMode, autoThrottleDown, partialTicks);
    }

    /** Copy with the mouse-stick position (stickX/stickY) zeroed — the reference's free-look zeroes ONLY the stick
     *  args before still invoking the per-vehicle control mapping. */
    public ControlInput withZeroStick() {
        return new ControlInput(0, 0, throttleUp, throttleDown, moveLeft, moveRight, brake,
            freeLook, flightSimMode, autoThrottleDown, partialTicks);
    }
}
