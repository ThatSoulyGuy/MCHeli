package mcheli.agnostic.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * {@link WeaponSwitchQueue} against a private oracle: a verbatim copy of the dependent code it replaces
 * (AbstractMchVehicle {@code queueWeaponSwitch}, baseline lines 1663-1670, and the drain loop of {@code tickWeapons},
 * baseline lines 3815-3818), with the loadout size turned into the {@code lap} parameter and the int
 * {@code Mth.clamp} inlined as its NeoForge 21.1.235 body ({@code Mth.java}:93-95). Compared on seeded random
 * press/drain sequences (every drained seat, its net count and the iteration order) and on hand-traced edges.
 */
class WeaponSwitchQueueTest {

    /** The baseline code, verbatim except for the parameters. */
    private static final class Oracle {
        private final java.util.Map<Integer, Integer> switchBySeat = new java.util.HashMap<>();

        /** NeoForge 21.1.235 {@code Mth.clamp(int, int, int)}. */
        private static int clamp(int p_14046_, int p_14047_, int p_14048_) {
            return Math.min(Math.max(p_14046_, p_14047_), p_14048_);
        }

        /** Baseline :1669 (the seat < 0 return and the lap computation stay dependent). */
        void queueWeaponSwitch(int seat, int direction, int lap) {
            this.switchBySeat.merge(seat, direction >= 0 ? 1 : -1, (a, b) -> clamp(a + b, -lap, lap));
        }

        /** Baseline :3815-3818: the loop over a snapshot that removes each key, here recording (seat, pending). */
        List<int[]> drain() {
            List<int[]> out = new ArrayList<>();
            for (java.util.Map.Entry<Integer, Integer> e : new java.util.HashMap<>(this.switchBySeat).entrySet()) {
                int seat = e.getKey();
                int pending = e.getValue();
                this.switchBySeat.remove(seat);
                out.add(new int[] {seat, pending});
            }
            return out;
        }
    }

    private static List<int[]> entries(Map<Integer, Integer> m) {
        List<int[]> out = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : m.entrySet()) {
            out.add(new int[] {e.getKey(), e.getValue()});
        }
        return out;
    }

    private static void assertSame(List<int[]> expected, List<int[]> actual, String at) {
        assertEquals(expected.size(), actual.size(), "size " + at);
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i)[0], actual.get(i)[0], "seat #" + i + " " + at);
            assertEquals(expected.get(i)[1], actual.get(i)[1], "pending #" + i + " " + at);
        }
    }

    @Test
    void matchesTheOracleOnSeededRandomPressesAndDrains() {
        for (long seed : new long[] {1L, 42L, 20261111L, -7L, 0xC0FFEEL}) {
            Random rnd = new Random(seed);
            Oracle o = new Oracle();
            WeaponSwitchQueue q = new WeaponSwitchQueue();
            int drains = 0;
            for (int step = 0; step < 20_000; step++) {
                String at = "seed " + seed + " step " + step;
                if (rnd.nextInt(6) == 0) {
                    assertSame(o.drain(), entries(q.drain()), at);
                    assertTrue(q.isEmpty(), "empty after the drain " + at);
                    drains++;
                } else {
                    // Seats up to 63 so the map's table grows past 16 buckets (the iteration order then depends on
                    // the table's history, which both must share); directions include 0 and the extremes; the lap
                    // changes as a rebuilt loadout would.
                    int seat = rnd.nextInt(4) == 0 ? rnd.nextInt(64) : rnd.nextInt(4);
                    int direction = switch (rnd.nextInt(5)) {
                        case 0 -> 0;
                        case 1 -> Integer.MIN_VALUE;
                        case 2 -> Integer.MAX_VALUE;
                        default -> rnd.nextInt(7) - 3;
                    };
                    int lap = 1 + rnd.nextInt(rnd.nextInt(8) == 0 ? 40 : 8);
                    o.queueWeaponSwitch(seat, direction, lap);
                    q.queue(seat, direction, lap);
                }
            }
            assertSame(o.drain(), entries(q.drain()), "final seed " + seed);
            assertTrue(drains > 1000, "enough drains on seed " + seed);
        }
    }

    @Test
    void theNetCountIsClampedToOneLap() {
        WeaponSwitchQueue q = new WeaponSwitchQueue();
        for (int i = 0; i < 5; i++) {
            q.queue(0, 1, 3);
        }
        for (int i = 0; i < 9; i++) {
            q.queue(1, -1, 2);
        }
        Map<Integer, Integer> d = q.drain();
        assertEquals(3, d.get(0)); // +1 x5, clamped at the 3-slot lap
        assertEquals(-2, d.get(1)); // -1 x9, clamped at -2
        // The first press of a seat is never clamped (merge stores the value as is), as in the baseline: a 1-slot lap
        // still queues +1, and a lap of 1 keeps every later sum in [-1, 1].
        q.queue(2, 5, 1);
        q.queue(2, 5, 1);
        assertEquals(1, q.drain().get(2));
    }

    @Test
    void oppositePressesCancelToZeroAndAreStillDrained() {
        WeaponSwitchQueue q = new WeaponSwitchQueue();
        q.queue(0, 1, 7);
        q.queue(0, -1, 7);
        q.queue(0, 0, 7); // direction 0 counts as +1 (direction >= 0)
        q.queue(0, -9, 7);
        Map<Integer, Integer> d = q.drain();
        assertEquals(1, d.size());
        assertEquals(0, d.get(0)); // the caller skips a net 0 (pending == 0 -> continue)
        assertTrue(q.isEmpty());
        assertTrue(q.drain().isEmpty());
    }

    @Test
    void drainsInHashMapOrder() {
        WeaponSwitchQueue q = new WeaponSwitchQueue();
        Oracle o = new Oracle();
        int[] seats = {3, 0, 2, 17, 1, 33};
        for (int s : seats) {
            q.queue(s, 1, 4);
            o.queueWeaponSwitch(s, 1, 4);
        }
        List<int[]> got = entries(q.drain());
        assertSame(o.drain(), got, "mixed seats");
        // Hand trace: a 16-bucket table puts an Integer key k in bucket k & 15, so 1, 17 and 33 share bucket 1.
        // HashMap.merge links a NEW key at the head of its bin (put appends at the tail), so the bin reads 33, 1, 17,
        // and the drained copy re-inserts in that iteration order: 0, 33, 1, 17, 2, 3.
        int[] order = {0, 33, 1, 17, 2, 3};
        assertEquals(order.length, got.size());
        for (int i = 0; i < order.length; i++) {
            assertEquals(order[i], got.get(i)[0], "order #" + i);
        }
        // A fresh queue with only pilot/gunner seats drains in ascending seat order.
        q.queue(2, 1, 4);
        q.queue(0, -1, 4);
        q.queue(1, 1, 4);
        List<int[]> small = entries(q.drain());
        assertEquals(0, small.get(0)[0]);
        assertEquals(1, small.get(1)[0]);
        assertEquals(2, small.get(2)[0]);
    }
}
