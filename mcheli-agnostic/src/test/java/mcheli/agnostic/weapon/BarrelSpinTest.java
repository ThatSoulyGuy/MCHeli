package mcheli.agnostic.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.function.IntSupplier;

import org.junit.jupiter.api.Test;

/**
 * {@link BarrelSpin} against a private oracle: a verbatim copy of the dependent client tick it replaces
 * (AbstractMchVehicle {@code updateWheelTrackPhase}, baseline lines 5120-5137) with {@code isFiringRecently()} turned
 * into a parameter and {@code this.random.nextInt(3)} into a counted draw. Compared bit-exactly on seeded random firing
 * sequences (angle, prev, speed and the number of draws, step by step) and on hand-traced edges.
 */
class BarrelSpinTest {

    /** Baseline AbstractMchVehicle:5120-5137, verbatim except for the parameters. */
    private static final class Oracle {
        private float barrelSpin, prevBarrelSpin, barrelSpeed;

        void tick(boolean isFiringRecently, Draws random) {
            this.prevBarrelSpin = this.barrelSpin;
            if (isFiringRecently && this.barrelSpeed < 75.0F) {
                this.barrelSpeed += 25.0F + random.nextInt(3);
                if (this.barrelSpeed > 74.0F) {
                    this.barrelSpeed = 74.0F;
                }
            }
            this.barrelSpin += this.barrelSpeed;
            if (this.barrelSpin >= 360.0F) {
                this.barrelSpin -= 360.0F;
                this.prevBarrelSpin -= 360.0F;
            }
            if (this.barrelSpeed > 0.0F) {
                this.barrelSpeed -= 1.48F;
                if (this.barrelSpeed < 0.0F) {
                    this.barrelSpeed = 0.0F;
                }
            }
        }
    }

    /** A seeded {@code nextInt(3)} source that counts its draws. */
    private static final class Draws implements IntSupplier {
        final Random r;
        int calls;
        Draws(long seed) { this.r = new Random(seed); }
        int nextInt(int bound) { this.calls++; return this.r.nextInt(bound); }
        @Override public int getAsInt() { return nextInt(3); }
    }

    private static void assertBits(float expected, float actual, String what) {
        assertEquals(Float.floatToIntBits(expected), Float.floatToIntBits(actual), what + ": " + expected + " vs " + actual);
    }

    @Test
    void matchesTheOracleOnSeededRandomFiring() {
        for (long seed : new long[] {1L, 42L, 20261111L, -7L, 0xC0FFEEL}) {
            Random rnd = new Random(seed);
            Oracle o = new Oracle();
            BarrelSpin b = new BarrelSpin();
            Draws od = new Draws(seed ^ 0x5DEECE66DL);
            Draws nd = new Draws(seed ^ 0x5DEECE66DL);
            int wraps = 0;
            boolean firing = false;
            for (int step = 0; step < 20_000; step++) {
                // Bursts and pauses of random length, so both the spin-up and the full coast-down are covered.
                if (rnd.nextInt(30) == 0) {
                    firing = !firing;
                }
                float before = b.barrelSpin();
                o.tick(firing, od);
                b.tick(firing, nd);
                String at = "seed " + seed + " step " + step;
                assertBits(o.barrelSpin, b.barrelSpin(), "barrelSpin " + at);
                assertBits(o.prevBarrelSpin, b.prevBarrelSpin(), "prevBarrelSpin " + at);
                assertBits(o.barrelSpeed, b.barrelSpeed(), "barrelSpeed " + at);
                assertEquals(od.calls, nd.calls, "draws " + at);
                if (b.barrelSpin() < before) {
                    wraps++;
                }
            }
            assertTrue(wraps > 100, "wraps on seed " + seed + ": " + wraps);
        }
    }

    @Test
    void theWrapMovesPrevInLockstepAndTheDrawIsGated() {
        BarrelSpin b = new BarrelSpin();
        Draws d = new Draws(7L);
        // Not firing: never draws, never moves.
        for (int i = 0; i < 10; i++) {
            b.tick(false, d);
        }
        assertEquals(0, d.calls, "no draw while not firing");
        assertBits(0.0F, b.barrelSpin(), "parked");
        // Firing: one draw per tick (the speed is capped at 74 < 75, so the gate stays open).
        int ticks = 0;
        boolean wrapped = false;
        while (!wrapped && ticks < 100) {
            float before = b.barrelSpin();
            b.tick(true, d);
            ticks++;
            if (b.barrelSpin() < before) {
                wrapped = true;
                // Baseline :5129-5132: the 360 wrap subtracts from prev too, so prev < spin still holds.
                assertTrue(b.prevBarrelSpin() < b.barrelSpin(), "prev " + b.prevBarrelSpin() + " spin " + b.barrelSpin());
                assertBits(before - 360.0F, b.prevBarrelSpin(), "prev shifted by -360");
            }
        }
        assertTrue(wrapped, "the barrel wrapped");
        assertEquals(ticks, d.calls, "one draw per firing tick");
        // The coast-down: 74 - 1.48 per tick reaches 0 after ~50 ticks with no draw.
        int before = d.calls;
        int coast = 0;
        while (b.barrelSpeed() > 0.0F && coast < 200) {
            b.tick(false, d);
            coast++;
        }
        assertEquals(before, d.calls, "no draw while coasting");
        assertTrue(coast >= 49 && coast <= 51, "coast-down ticks: " + coast);
    }
}
