package mcheli.agnostic.vehicle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * {@link Repelling} against a verbatim copy of the reference {@code onUpdate_Repelling} and
 * {@code onUpdate_UnmountCrewRepelling} (MCH_EntityAircraft:1831-1877), with the entity reads turned into parameters
 * (the hooks' intervals, the repelling flag, {@code getAlt}, {@code countOnUpdate}, {@code isRemote}) and the
 * cadence's mob drop recorded as "count:rope". The port side runs the same sequence the vehicle's
 * {@code MchCrewDrop.stepRepelling} does. Traces compare bit for bit; the pinned numbers come from the reference
 * arithmetic.
 */
class RepellingTest {

    /** Reference EA:1831-1877, verbatim but for the parameters. */
    static final class Oracle {
        float ropesLength;
        int tickRepelling;
        final boolean remote;
        final List<String> drops = new ArrayList<>();

        Oracle(boolean remote) {
            this.remote = remote;
        }

        void onUpdate_Repelling(int[] hooks, boolean isRepelling, int alt, int countOnUpdate) {
            if (hooks.length > 0) { // getAcInfo() != null && getAcInfo().haveRepellingHook()
                if (isRepelling) {
                    if (this.ropesLength > -50.0F && this.ropesLength > -alt) {
                        this.ropesLength = (float) (this.ropesLength - (this.remote ? 0.3F : 0.25));
                    }
                } else {
                    this.ropesLength = 0.0F;
                }
            }
            this.onUpdate_UnmountCrewRepelling(hooks, isRepelling, countOnUpdate);
        }

        private void onUpdate_UnmountCrewRepelling(int[] hooks, boolean isRepelling, int countOnUpdate) {
            if (!isRepelling) {
                this.tickRepelling = 0;
            } else if (this.tickRepelling < 60) {
                this.tickRepelling++;
            } else if (!this.remote) {
                for (int ropeIdx = 0; ropeIdx < hooks.length; ropeIdx++) {
                    if (countOnUpdate % hooks[ropeIdx] == 0) {
                        this.drops.add(countOnUpdate + ":" + ropeIdx);
                    }
                }
            }
        }
    }

    /** The port's per-tick sequence (MchCrewDrop.stepRepelling). */
    static final class Port {
        float len;
        int t;
        final boolean client;
        final List<String> drops = new ArrayList<>();

        Port(boolean client) {
            this.client = client;
        }

        void step(int[] hooks, boolean repelling, int alt, int count) {
            boolean hasHooks = hooks.length > 0;
            this.len = Repelling.ropeStep(this.len, hasHooks, repelling, hasHooks && repelling ? alt : 0, this.client);
            boolean cadence = Repelling.cadenceOpen(repelling, this.t);
            this.t = Repelling.tickStep(repelling, this.t);
            if (cadence && !this.client) {
                for (int idx = 0; idx < hooks.length; idx++) {
                    if (Repelling.fires(count, hooks[idx])) {
                        this.drops.add(count + ":" + idx);
                    }
                }
            }
        }
    }

    private static final int[][] HOOK_SETS = {{}, {30, 30}, {10}, {7, 13}, {1}, {30}};

    @Test
    void matchesTheReferenceBitForBitOnScriptedAndSeededRuns() {
        Random rnd = new Random(1831L);
        int compared = 0;
        for (int[] hooks : HOOK_SETS) {
            for (boolean client : new boolean[] {false, true}) {
                for (int run = 0; run < 12; run++) {
                    Oracle o = new Oracle(client);
                    Port p = new Port(client);
                    boolean repelling = false;
                    int alt = 256;
                    int count = 1 + rnd.nextInt(50);
                    for (int tick = 0; tick < 400; tick++, count++) {
                        // Scripted: toggle at fixed points for the first runs, at random for the rest.
                        if (run < 3 ? tick == 5 || tick == 150 + 40 * run || tick == 330 : rnd.nextInt(60) == 0) {
                            repelling = !repelling;
                        }
                        if (rnd.nextInt(25) == 0) {
                            alt = rnd.nextInt(5) == 0 ? rnd.nextInt(8) : 5 + rnd.nextInt(256);
                        }
                        o.onUpdate_Repelling(hooks, repelling, alt, count);
                        p.step(hooks, repelling, alt, count);
                        assertEquals(Float.floatToIntBits(o.ropesLength), Float.floatToIntBits(p.len),
                            "rope length, hooks " + hooks.length + " client " + client + " tick " + tick);
                        assertEquals(o.tickRepelling, p.t, "tick counter at " + tick);
                        compared++;
                    }
                    assertEquals(o.drops, p.drops, "the cadence's drops");
                }
            }
        }
        assertEquals(HOOK_SETS.length * 2 * 12 * 400, compared);
    }

    /** Runs {@code n} ticks of a server or client rope from 0, repelling, at the altitude {@code alt}. */
    private static float rope(int n, int alt, boolean client) {
        float len = 0.0F;
        for (int i = 0; i < n; i++) {
            len = Repelling.ropeStep(len, true, true, alt, client);
        }
        return len;
    }

    @Test
    void theServerRopeReaches50In200TicksAndTheClientsIn167() {
        assertEquals(-49.75F, rope(199, 256, false));
        assertEquals(-50.0F, rope(200, 256, false));
        assertEquals(-50.0F, rope(400, 256, false));
        float c166 = rope(166, 256, true);
        float c167 = rope(167, 256, true);
        assertTrue(c166 > -50.0F, "the client rope is still short after 166 ticks: " + c166);
        assertEquals(-50.1, c167, 1.0e-3);
        assertEquals(c167, rope(400, 256, true));
    }

    @Test
    void theRopeStopsAtTheAltitude() {
        assertEquals(-10.0F, rope(400, 10, false));
        assertEquals(-10.2, rope(400, 10, true), 1.0e-4);
        assertEquals(-9.75F, rope(39, 10, false));
        assertEquals(0.0F, rope(400, 0, false)); // ground right below: no rope
        // The altitude is only read while repelling: a longer rope keeps its length when the ground comes up.
        assertEquals(-20.0F, Repelling.ropeStep(-20.0F, true, true, 5, false));
    }

    @Test
    void stoppingPullsTheRopesInOnBothSides() {
        assertEquals(0.0F, Repelling.ropeStep(-37.5F, true, false, 256, false));
        assertEquals(0.0F, Repelling.ropeStep(-37.5F, true, false, 256, true));
        assertEquals(0, Repelling.tickStep(false, 60));
        // No hooks: the rope length is left alone (the reference skipped it).
        assertEquals(-3.0F, Repelling.ropeStep(-3.0F, false, true, 256, false));
        assertEquals(-3.0F, Repelling.ropeStep(-3.0F, false, false, 256, false));
    }

    @Test
    void theCounterSaturatesAt60TheCadenceOpensOnThe61stTickAndTheSlideAfter50() {
        int t = 0;
        int firstCadence = -1;
        int firstSlide = -1;
        for (int tick = 1; tick <= 100; tick++) {
            if (Repelling.cadenceOpen(true, t) && firstCadence < 0) {
                firstCadence = tick;
            }
            t = Repelling.tickStep(true, t);
            if (Repelling.canRepelling(true, t) && firstSlide < 0) {
                firstSlide = tick;
            }
            assertTrue(t <= Repelling.ROPE_OUT_TICKS);
        }
        assertEquals(60, t);
        assertEquals(61, firstCadence);
        assertEquals(51, firstSlide);
        assertFalse(Repelling.canRepelling(true, 50));
        assertTrue(Repelling.canRepelling(true, 51));
        assertFalse(Repelling.canRepelling(false, 60));
        assertFalse(Repelling.cadenceOpen(true, 59));
        assertTrue(Repelling.cadenceOpen(true, 60));
        assertFalse(Repelling.cadenceOpen(false, 60));
    }

    @Test
    void startingNeedsHooksHoverALevelHullAndStillnessAllStrict() {
        assertTrue(Repelling.canStartRepelling(true, true, 0.0F, 0.0F, 0.0));
        assertTrue(Repelling.canStartRepelling(true, true, 2.999F, -2.999F, 0.2999));
        assertFalse(Repelling.canStartRepelling(true, true, 3.0F, 0.0F, 0.0));
        assertFalse(Repelling.canStartRepelling(true, true, -3.0F, 0.0F, 0.0));
        assertFalse(Repelling.canStartRepelling(true, true, 0.0F, 3.0F, 0.0));
        assertFalse(Repelling.canStartRepelling(true, true, 0.0F, -3.0F, 0.0));
        assertFalse(Repelling.canStartRepelling(true, true, 0.0F, 0.0F, 0.3));
        assertFalse(Repelling.canStartRepelling(false, true, 0.0F, 0.0F, 0.0));
        assertFalse(Repelling.canStartRepelling(true, false, 0.0F, 0.0F, 0.0));
    }

    @Test
    void aHookFiresOnTheMultiplesOfItsInterval() {
        assertTrue(Repelling.fires(30, 30));
        assertTrue(Repelling.fires(60, 30));
        assertFalse(Repelling.fires(31, 30));
        assertFalse(Repelling.fires(29, 30));
        for (int c = 1; c < 100; c++) {
            assertTrue(Repelling.fires(c, 1));
        }
    }

    /**
     * The server's {@code lastUsedRopeIndex} as the reference writes it: the player slide (EA:1939, verbatim) and the
     * mob cadence (EA:1853-1871, verbatim but for the seats: {@code mobs} mobs from seat 2 up, one per firing hook),
     * with {@code countOnUpdate} and the hook intervals as parameters.
     */
    static final class RopeOracle {
        int lastUsedRopeIndex;
        int mobs;
        final List<Integer> used = new ArrayList<>();

        void unmountPlayer(int[] hooks) { // the rope branch of unmount(entity), canRepelling && haveRepellingHook
            this.lastUsedRopeIndex = (this.lastUsedRopeIndex + 1) % hooks.length;
            this.used.add(this.lastUsedRopeIndex);
        }

        void cadence(int[] hooks, int countOnUpdate) { // the tickRepelling >= 60 server branch
            for (int ropeIdx = 0; ropeIdx < hooks.length; ropeIdx++) {
                if (countOnUpdate % hooks[ropeIdx] == 0) {
                    if (this.mobs > 0) {
                        this.mobs--;
                        this.lastUsedRopeIndex = ropeIdx;
                        this.used.add(100 + ropeIdx);
                    }
                }
            }
        }
    }

    @Test
    void thePlayerSlideTakesTheRopeAfterTheLastOneUsedByAnyone() {
        // Pinned: from 0 with 2 hooks 1, 0, 1, ...; 1 hook always 0; 3 hooks 1, 2, 0, 1.
        int last = 0;
        int[] two = new int[6];
        for (int i = 0; i < two.length; i++) {
            two[i] = last = Repelling.nextRope(last, 2);
        }
        assertArrayEquals(new int[] {1, 0, 1, 0, 1, 0}, two);
        int one = 0;
        for (int i = 0; i < 5; i++) {
            assertEquals(0, one = Repelling.nextRope(one, 1));
        }
        assertEquals(1, Repelling.nextRope(0, 3));
        assertEquals(2, Repelling.nextRope(1, 3));
        assertEquals(0, Repelling.nextRope(2, 3));
        // A cadence firing on both hooks of an mh-60g leaves rope 1 last, so the next player takes rope 0; with one
        // mob left only rope 0 is used and the player takes rope 1.
        RopeOracle o = new RopeOracle();
        o.mobs = 2;
        o.cadence(new int[] {30, 30}, 90);
        o.unmountPlayer(new int[] {30, 30});
        assertEquals(List.of(100, 101, 0), o.used);
        o = new RopeOracle();
        o.mobs = 1;
        o.cadence(new int[] {30, 30}, 90);
        o.unmountPlayer(new int[] {30, 30});
        assertEquals(List.of(100, 1), o.used);

        // Seeded runs: slides and cadence firings interleaved, the port's sequence (Repelling.nextRope for a slide,
        // the firing hook for a mob, as MchCrewDrop does) against the oracle.
        Random rnd = new Random(1939L);
        int[][] sets = {{30}, {30, 30}, {7, 13}, {5, 10, 15}};
        int compared = 0;
        for (int[] hooks : sets) {
            for (int run = 0; run < 20; run++) {
                RopeOracle ref = new RopeOracle();
                ref.mobs = rnd.nextInt(12);
                int mobs = ref.mobs;
                int port = 0;
                List<Integer> used = new ArrayList<>();
                for (int count = 1 + rnd.nextInt(30), n = 0; n < 200; n++, count++) {
                    if (rnd.nextInt(9) == 0) {
                        ref.unmountPlayer(hooks);
                        port = Repelling.nextRope(port, hooks.length);
                        used.add(port);
                    }
                    ref.cadence(hooks, count);
                    for (int idx = 0; idx < hooks.length; idx++) {
                        if (Repelling.fires(count, hooks[idx]) && mobs > 0) {
                            mobs--;
                            port = idx;
                            used.add(100 + idx);
                        }
                    }
                    assertEquals(ref.lastUsedRopeIndex, port, "the last rope, hooks " + hooks.length + " tick " + n);
                    compared++;
                }
                assertEquals(ref.used, used, "the ropes used in order");
            }
        }
        assertEquals(sets.length * 20 * 200, compared);
    }

    @Test
    void aSliderLetsGoStrictlyPastTheRopeEndPlusFive() {
        assertFalse(Repelling.sliderReleased(-5.0, 50.0, -50.0F));     // 55 below: still on the rope
        assertTrue(Repelling.sliderReleased(-5.0001, 50.0, -50.0F));
        assertTrue(Repelling.sliderReleased(105.0001, 50.0, -50.0F));  // the distance is absolute
        assertFalse(Repelling.sliderReleased(45.0, 50.0, 0.0F));       // a rope pulled in still leaves 5
        assertTrue(Repelling.sliderReleased(44.9, 50.0, 0.0F));
    }

    /** Reference MCH_AircraftClientTickHandler:156-158 + MCH_HeliPacketHandler:117-118, verbatim but for the
     *  parameters: the bit becomes {@code (isFreeLookMode() ? 2 : 1) == 1}, {@code isFreeLookMode = bit || repelling}. */
    private static boolean referenceFreeLookPress(boolean bit, boolean repelling) {
        byte switchFreeLook = (byte) ((bit || repelling) ? 2 : 1);
        return switchFreeLook == 1;
    }

    @Test
    void theFreeLookKeyWhileRepellingTurnsTheToggleOff() {
        for (boolean bit : new boolean[] {false, true}) {
            for (boolean repelling : new boolean[] {false, true}) {
                assertEquals(referenceFreeLookPress(bit, repelling), Repelling.freeLookAfterKey(bit, repelling),
                    "toggle " + bit + ", repelling " + repelling);
            }
        }
        assertTrue(Repelling.freeLookAfterKey(false, false));  // outside repelling a press flips it
        assertFalse(Repelling.freeLookAfterKey(true, false));
        assertFalse(Repelling.freeLookAfterKey(false, true));  // while repelling it stays (or goes) off
        assertFalse(Repelling.freeLookAfterKey(true, true));
    }
}
