package mcheli.agnostic.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * {@link RadarSweep} against a private oracle: a verbatim copy of the dependent client tick it replaces
 * (AbstractMchVehicle {@code advanceRadar}, baseline lines 1849-1856, after the mounted/ridden gate) and
 * {@code radarRotInterp} (1909-1916), with {@code radarSweepSpeed()} turned into a parameter and the
 * {@code scanRadar(64)} call into a counter. Compared bit-exactly on seeded random speeds, gates and partial ticks, and
 * on hand-traced edges.
 */
class RadarSweepTest {

    /** Baseline AbstractMchVehicle:1849-1856 and 1909-1916, verbatim except for the parameters. */
    private static final class Oracle {
        private int radarRotate;
        private int prevRadarRotate;
        int scans;

        void advanceRadar(int radarSweepSpeed) {
            this.prevRadarRotate = this.radarRotate;
            this.radarRotate += radarSweepSpeed;
            if (this.radarRotate >= 360) {
                this.radarRotate = 0;
            }
            if (this.radarRotate == 0) {
                this.scans++; // scanRadar(64)
            }
        }

        float radarRotInterp(float partialTick) {
            float rot = this.radarRotate;
            float prev = this.prevRadarRotate;
            if (rot < prev) {
                rot += 360.0F;
            }
            return prev + (rot - prev) * partialTick;
        }
    }

    private static void assertBits(float expected, float actual, String what) {
        assertEquals(Float.floatToIntBits(expected), Float.floatToIntBits(actual), what + ": " + expected + " vs " + actual);
    }

    @Test
    void matchesTheOracleOnSeededRandomSweeps() {
        for (long seed : new long[] {1L, 42L, 20261111L, -7L, 0xC0FFEEL}) {
            Random rnd = new Random(seed);
            // The real per-type speeds (5, 10), plus speeds that do not divide 360, to cover the >= 360 reset.
            int[] speeds = {5, 10, 7, 13, 1, 359, 360};
            int speed = speeds[(int) Math.floorMod(seed, (long) speeds.length)];
            Oracle o = new Oracle();
            RadarSweep r = new RadarSweep();
            int scans = 0;
            for (int step = 0; step < 20_000; step++) {
                if (rnd.nextInt(500) == 0) {
                    speed = speeds[rnd.nextInt(speeds.length)];
                }
                boolean gated = rnd.nextInt(6) == 0; // unridden / no radar: the dependent gate skips the call
                if (!gated) {
                    o.advanceRadar(speed);
                    if (r.advance(speed)) {
                        scans++;
                    }
                }
                String at = "seed " + seed + " step " + step;
                assertEquals(o.scans, scans, "scans " + at);
                assertEquals(o.radarRotate, r.rotate(), "rotate " + at);
                for (int f = 0; f < 3; f++) {
                    float pt = f == 0 ? 0.0F : (f == 2 ? 1.0F : rnd.nextFloat());
                    assertBits(o.radarRotInterp(pt), r.interp(pt), "interp(" + pt + ") " + at);
                }
            }
            assertTrue(scans > 10, "scans on seed " + seed + ": " + scans);
        }
    }

    @Test
    void speedsFiveAndTenRescanExactlyAtZero() {
        for (int speed : new int[] {5, 10}) {
            RadarSweep r = new RadarSweep();
            List<Integer> at = new ArrayList<>();
            for (int t = 1; t <= 3 * 360 / speed; t++) {
                if (r.advance(speed)) {
                    at.add(t);
                    assertEquals(0, r.rotate());
                }
            }
            int period = 360 / speed;
            assertEquals(List.of(period, 2 * period, 3 * period), at, "speed " + speed);
        }
    }

    @Test
    void theWrapInterpolatesForward() {
        RadarSweep r = new RadarSweep();
        for (int i = 0; i < 71; i++) {
            r.advance(5); // 355
        }
        assertEquals(355, r.rotate());
        assertTrue(r.advance(5)); // 355 -> 0, rescan
        // Baseline :1912-1914: rot 0 < prev 355 adds 360, so the line keeps sweeping forward through 357.5.
        assertBits(357.5F, r.interp(0.5F), "355 -> 0 interpolation");
        assertBits(355.0F, r.interp(0.0F), "pt 0 is prev");
        assertBits(360.0F, r.interp(1.0F), "pt 1 is 360, not 0");
    }
}
