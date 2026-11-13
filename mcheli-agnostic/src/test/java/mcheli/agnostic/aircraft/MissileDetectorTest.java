package mcheli.agnostic.aircraft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;

/**
 * {@link MCH_MissileDetector} against a private oracle: a verbatim copy of the dependent tick it replaces
 * (AbstractMchVehicle {@code tickFlares}, baseline lines 2642-2660) with the entity reads turned into parameters. The
 * decoy scan and the alert packet are replaced by the returned code. Compared on seeded random flag sequences (actions,
 * the cooldown and the number of scan calls, step by step) and on hand-traced edges.
 */
class MissileDetectorTest {

    private static final int NONE = 0;
    private static final int DECOY = 1;
    private static final int ALERT = 2;

    /** Baseline AbstractMchVehicle:2642-2660, verbatim except for the parameters and the returned code. */
    private static final class Oracle {
        private int rwrAlertCooldown;

        int tick(boolean isUsing, boolean isDestroyed, boolean crew, BooleanSupplier isLockedByMissile) {
            if (this.rwrAlertCooldown > 0) {
                this.rwrAlertCooldown--;
            }
            if (isUsing && !isDestroyed && crew) {
                return DECOY; // the decoy scan
            } else if (!isDestroyed && crew && this.rwrAlertCooldown == 0 && isLockedByMissile.getAsBoolean()) {
                this.rwrAlertCooldown = 20;
                return ALERT; // sendRwrToCrew(ALERT)
            }
            return NONE;
        }
    }

    private static int code(MCH_MissileDetector.Action a) {
        switch (a) {
            case DECOY: return DECOY;
            case ALERT: return ALERT;
            default: return NONE;
        }
    }

    /** A scan that answers a fixed value and counts its calls. */
    private static final class Scan implements BooleanSupplier {
        final boolean answer;
        int calls;
        Scan(boolean answer) { this.answer = answer; }
        @Override public boolean getAsBoolean() { this.calls++; return this.answer; }
    }

    @Test
    void matchesTheOracleOnSeededRandomSequences() {
        for (long seed : new long[] {1L, 42L, 20261111L, -7L, 0xC0FFEEL}) {
            Random rnd = new Random(seed);
            Oracle o = new Oracle();
            MCH_MissileDetector d = new MCH_MissileDetector();
            int alerts = 0;
            int decoys = 0;
            for (int step = 0; step < 20_000; step++) {
                // Biased so every branch is hit often: mostly crewed and intact, sometimes flaring.
                boolean using = rnd.nextInt(10) < 3;
                boolean destroyed = rnd.nextInt(10) == 0;
                boolean crew = rnd.nextInt(10) < 8;
                boolean locked = rnd.nextBoolean();
                Scan so = new Scan(locked);
                Scan sd = new Scan(locked);
                int expected = o.tick(using, destroyed, crew, so);
                int actual = code(d.tick(using, destroyed, crew, sd));
                String at = "seed " + seed + " step " + step;
                assertEquals(expected, actual, "action at " + at);
                assertEquals(so.calls, sd.calls, "scan calls at " + at);
                assertEquals(o.rwrAlertCooldown, d.alertCount(), "cooldown at " + at);
                if (actual == ALERT) {
                    alerts++;
                } else if (actual == DECOY) {
                    decoys++;
                }
            }
            // The sequence really exercised both branches.
            assertEquals(true, alerts > 100, "alerts on seed " + seed + ": " + alerts);
            assertEquals(true, decoys > 100, "decoys on seed " + seed + ": " + decoys);
        }
    }

    @Test
    void alertsAreExactlyTwentyTicksApart() {
        MCH_MissileDetector d = new MCH_MissileDetector();
        List<Integer> at = new ArrayList<>();
        for (int t = 1; t <= 100; t++) {
            if (d.tick(false, false, true, () -> true) == MCH_MissileDetector.Action.ALERT) {
                at.add(t);
            }
        }
        // Baseline :2658: the cooldown is set to 20; :2642-2644 count it down before the next decision.
        assertEquals(List.of(1, 21, 41, 61, 81), at);
    }

    @Test
    void theDecrementHappensBeforeTheDecision() {
        MCH_MissileDetector d = new MCH_MissileDetector();
        assertEquals(MCH_MissileDetector.Action.ALERT, d.tick(false, false, true, () -> true));
        assertEquals(20, d.alertCount());
        for (int i = 0; i < 19; i++) {
            assertEquals(MCH_MissileDetector.Action.NONE, d.tick(false, false, true, () -> true));
        }
        assertEquals(1, d.alertCount());
        // The 20th tick counts 1 -> 0 and alerts in the same tick.
        assertEquals(MCH_MissileDetector.Action.ALERT, d.tick(false, false, true, () -> true));
        assertEquals(20, d.alertCount());
    }

    @Test
    void theDecoyBranchOnlyCountsTheCooldownDown() {
        MCH_MissileDetector d = new MCH_MissileDetector();
        d.tick(false, false, true, () -> true); // cooldown 20
        Scan s = new Scan(true);
        for (int i = 0; i < 5; i++) {
            assertEquals(MCH_MissileDetector.Action.DECOY, d.tick(true, false, true, s));
        }
        assertEquals(15, d.alertCount(), "the decoy ticks only decrement, never reset");
        assertEquals(0, s.calls, "the decoy branch never runs the missile scan");
        // A decoy with the cooldown at 0 leaves it at 0 (baseline :2647 branch never writes it).
        MCH_MissileDetector fresh = new MCH_MissileDetector();
        assertEquals(MCH_MissileDetector.Action.DECOY, fresh.tick(true, false, true, s));
        assertEquals(0, fresh.alertCount());
        assertEquals(0, s.calls);
    }

    @Test
    void noScanWhenGated() {
        Scan s = new Scan(true);
        // A wreck: no decoy and no alert, even while flaring.
        MCH_MissileDetector d = new MCH_MissileDetector();
        assertEquals(MCH_MissileDetector.Action.NONE, d.tick(true, true, true, s));
        assertEquals(MCH_MissileDetector.Action.NONE, d.tick(false, true, true, s));
        // No rider in seat 0 or 1.
        assertEquals(MCH_MissileDetector.Action.NONE, d.tick(true, false, false, s));
        assertEquals(MCH_MissileDetector.Action.NONE, d.tick(false, false, false, s));
        assertEquals(0, s.calls, "a gated tick never scans");
        assertEquals(0, d.alertCount());
        // The cooldown still running.
        d.tick(false, false, true, s); // alerts, cooldown 20
        assertEquals(1, s.calls);
        d.tick(false, false, true, s);
        assertEquals(1, s.calls, "a running cooldown skips the scan");
        // The scan answers no: no alert and the cooldown is not set.
        MCH_MissileDetector quiet = new MCH_MissileDetector();
        Scan no = new Scan(false);
        assertEquals(MCH_MissileDetector.Action.NONE, quiet.tick(false, false, true, no));
        assertEquals(1, no.calls);
        assertEquals(0, quiet.alertCount());
    }
}
