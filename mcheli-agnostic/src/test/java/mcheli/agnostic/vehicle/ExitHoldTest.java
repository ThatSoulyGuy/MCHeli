package mcheli.agnostic.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import mcheli.agnostic.vehicle.ExitHold.Step;
import org.junit.jupiter.api.Test;

/**
 * {@link ExitHold}, the jump-key exits' hold. A fresh press made while seated fires on its 10th held tick, once; a key
 * that went down before boarding, inside a screen or while the action was shut never starts a hold however long it
 * stays down (the OS key repeat, which refilled the jump click queue the hold used to read, has no say here).
 */
class ExitHoldTest {

    private static final String EJECT = "mcheli.message.hold_to_eject";
    private static final String SLIDE = "mcheli.message.hold_to_slide";

    /** {@code n} ticks with the same inputs; the steps in order. */
    private static List<Step> run(ExitHold h, int n, boolean down, boolean screen, boolean seated, String action) {
        List<Step> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(h.tick(down, screen, seated, action));
        }
        return out;
    }

    private static int count(List<Step> steps, Step s) {
        int n = 0;
        for (Step x : steps) {
            n += x == s ? 1 : 0;
        }
        return n;
    }

    /** Seated with the key up for a couple of ticks. */
    private static ExitHold seated() {
        ExitHold h = new ExitHold();
        run(h, 2, false, false, true, EJECT);
        return h;
    }

    @Test
    void aFreshPressFiresOnItsTenthHeldTickOnce() {
        ExitHold h = seated();
        for (int i = 1; i < ExitHold.HOLD_TICKS; i++) {
            assertEquals(Step.PROGRESS, h.tick(true, false, true, EJECT), "held tick " + i);
            assertEquals(i, h.ticks());
        }
        assertEquals(Step.FIRED, h.tick(true, false, true, EJECT));
        assertEquals(0, h.ticks());
        // Still held: nothing more, ever.
        List<Step> after = run(h, 200, true, false, true, EJECT);
        assertEquals(200, count(after, Step.IDLE), "a held key fires once: " + after);
        // Released, then a new press: a new hold.
        assertEquals(Step.IDLE, h.tick(false, false, true, EJECT));
        assertEquals(Step.PROGRESS, h.tick(true, false, true, EJECT));
        assertEquals(1, h.ticks());
    }

    @Test
    void aKeyHeldWhileBoardingNeverStartsAHold() {
        ExitHold h = new ExitHold();
        run(h, 5, true, false, false, null); // Space held on foot (a creative flight, a hop)
        List<Step> seatedHeld = run(h, 400, true, false, true, EJECT); // boarded, still held (OS key repeat running)
        assertEquals(400, count(seatedHeld, Step.IDLE), "held across boarding: " + seatedHeld);
        assertEquals(Step.IDLE, h.tick(false, false, true, EJECT));
        assertEquals(Step.PROGRESS, h.tick(true, false, true, EJECT), "a new press after boarding starts it");
    }

    @Test
    void aPressOnTheBoardingTickDoesNotCount() {
        ExitHold h = new ExitHold();
        run(h, 3, false, false, false, null);
        List<Step> steps = run(h, 50, true, false, true, EJECT); // the key goes down on the first seated tick
        assertEquals(50, count(steps, Step.IDLE), "not a press made while seated: " + steps);
    }

    @Test
    void aKeyHeldThroughAScreenNeverStartsAHold() {
        ExitHold h = seated();
        run(h, 10, false, true, true, EJECT); // a screen: the keys are released while it is open
        // Closed with Space held: the key is restored as down, which is no press.
        List<Step> steps = run(h, 100, true, false, true, EJECT);
        assertEquals(100, count(steps, Step.IDLE), "held through the screen: " + steps);
        // A screen opening mid-hold resets it.
        h = seated();
        run(h, 4, true, false, true, EJECT);
        assertEquals(4, h.ticks());
        assertEquals(Step.CLEARED, h.tick(true, true, true, EJECT));
        assertEquals(0, h.ticks());
        assertEquals(100, count(run(h, 100, true, false, true, EJECT), Step.IDLE), "held on after the screen closed");
    }

    @Test
    void aKeyHeldWhileTheActionWasShutNeverStartsAHold() {
        ExitHold h = seated();
        run(h, 30, true, false, true, null); // pressed while the gate is still shut (the ropes not 2.5 s out)
        List<Step> open = run(h, 100, true, false, true, SLIDE); // the gate opens, the key still held
        assertEquals(100, count(open, Step.IDLE), "held from before the gate opened: " + open);
        assertEquals(Step.IDLE, h.tick(false, false, true, SLIDE));
        assertEquals(Step.PROGRESS, h.tick(true, false, true, SLIDE));
    }

    @Test
    void releasingClosingChangingOrGettingOffResets() {
        ExitHold h = seated();
        run(h, 5, true, false, true, EJECT);
        assertEquals(Step.CLEARED, h.tick(false, false, true, EJECT), "released");
        assertEquals(Step.PROGRESS, h.tick(true, false, true, EJECT));
        assertEquals(1, h.ticks(), "a new press starts over");

        run(h, 3, true, false, true, EJECT);
        assertEquals(Step.CLEARED, h.tick(true, false, true, null), "the action closed");
        assertEquals(Step.IDLE, h.tick(true, false, true, EJECT), "reopened under a held key: no restart");

        h = seated();
        run(h, 5, true, false, true, EJECT);
        assertEquals(Step.CLEARED, h.tick(true, false, true, SLIDE), "another action");
        assertEquals(10, count(run(h, 10, true, false, true, SLIDE), Step.IDLE), "it needs a new press");

        h = seated();
        run(h, 5, true, false, true, EJECT);
        assertEquals(Step.CLEARED, h.tick(true, false, false, null), "off the vehicle");
        assertEquals(Step.IDLE, h.tick(false, false, false, null));
    }
}
