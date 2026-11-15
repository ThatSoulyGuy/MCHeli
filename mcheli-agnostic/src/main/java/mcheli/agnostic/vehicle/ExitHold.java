package mcheli.agnostic.vehicle;

/**
 * The hold-to-confirm of the jump-key actions that throw the rider OUT of the vehicle (the ejection seat, seat
 * parachuting and the rope slide): the client state behind {@code MchKeybinds.holdToExit}, one per client, stepped
 * once every client tick.
 *
 * <p>Deliberate divergence: the reference fired each of them on a single press of {@code KeySwitchHovering} (Space),
 * the same key as a helicopter's hover toggle ({@code MCP_ClientPlaneTickHandler}:219, {@code MCH_ClientSeatTickHandler}
 * :75), so one stray tap could eject a pilot or drop a passenger. Here the key must stay down {@link #HOLD_TICKS}
 * client ticks in a row while the action is open.
 *
 * <p>The hold starts only on a press made while seated: the key's own down edge (down now, up last tick, as the
 * reference's polled {@code MCH_Key.isKeyDown() = !isBeforePress && isPress}, MCH_Key:19-21), seen on a tick that
 * follows a seated tick. It never reads the key's click queue, which the OS key repeat refills for as long as the key
 * is held. So a key already down when boarding, a key held through a screen (closing one restores the held keys as
 * down, {@code KeyMapping.setAll}) and a key held while the action was still shut (the an2 above 13 blocks, the mv-22
 * hatch, the ropes before 2.5 s) never start one: the rider presses again. Releasing the key, the action closing, a
 * change of action (e.g. after a seat switch), getting off or a screen opening resets it. After it fires the key must
 * be released before another hold can start.
 */
public final class ExitHold {

    /** Client ticks the key must stay down before the action fires: 10, about half a second. */
    public static final int HOLD_TICKS = 10;

    /** What one tick did, for the caller's action-bar progress. */
    public enum Step {
        /** Nothing to show or change. */
        IDLE,
        /** A running hold was reset: clear its progress. */
        CLEARED,
        /** The hold runs; {@link #ticks()} of {@link #HOLD_TICKS} done. */
        PROGRESS,
        /** The hold completed this tick: clear the progress and send the action (once). */
        FIRED
    }

    private boolean keyWasDown;
    private boolean wasSeated;
    private int ticks;
    private boolean fired;
    private String action;

    /**
     * One client tick; call it on every tick, off a vehicle and with a screen open too, so the key's edge stays true.
     *
     * @param keyDown    the jump key is held now
     * @param screenOpen a screen has the keys
     * @param seated     the player rides an MCHeli vehicle
     * @param action     the exit the key would make now (its message key), or null when none is open
     */
    public Step tick(boolean keyDown, boolean screenOpen, boolean seated, String action) {
        boolean press = keyDown && !this.keyWasDown && seated && this.wasSeated;
        this.keyWasDown = keyDown || screenOpen;
        this.wasSeated = seated;
        if (screenOpen || !seated || action == null || !keyDown
                || this.action != null && !this.action.equals(action)) {
            boolean running = this.ticks > 0;
            reset();
            return running ? Step.CLEARED : Step.IDLE;
        }
        if (this.fired || this.ticks == 0 && !press) {
            return Step.IDLE;
        }
        this.action = action;
        this.ticks++;
        if (this.ticks >= HOLD_TICKS) {
            this.fired = true;
            this.ticks = 0;
            return Step.FIRED;
        }
        return Step.PROGRESS;
    }

    /** The ticks the running hold has been held, 0 when none runs. */
    public int ticks() {
        return this.ticks;
    }

    private void reset() {
        this.ticks = 0;
        this.fired = false;
        this.action = null;
    }
}
