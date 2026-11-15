package mcheli.agnostic.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link MCH_Queue}, the reference ring buffer behind the aircraft's {@code prevPosition} trail (EA:220): what
 * {@code oldest()} answers ({@code MCH_Queue}:42-44, the slot after {@code current}), and the vehicle's tick protocol
 * around it, a clear on the first two ticks (EA:1358-1360) and a put at the end of each (EA:1543).
 */
class MCH_QueueTest {

    @Test
    void oldestIsTheNinthLastPut() {
        MCH_Queue<Integer> q = new MCH_Queue<>(10, 0);
        q.clear(-1);
        for (int k = 1; k <= 40; k++) {
            q.put(k);
            assertEquals(k >= 9 ? k - 8 : -1, q.oldest(), "after " + k + " puts");
        }
    }

    @Test
    void clearFillsEverySlotAndKeepsTheWritePosition() {
        MCH_Queue<Integer> q = new MCH_Queue<>(10, 0);
        for (int k = 1; k <= 13; k++) {
            q.put(k);
        }
        q.clear(7);
        for (int i = 0; i < q.size(); i++) {
            assertEquals(7, q.get(i), "slot " + i);
        }
        assertEquals(7, q.oldest());
        q.put(100);
        assertEquals(100, q.get(3), "the next put lands where the last one left off");
    }

    @Test
    void aLengthBelowOneBecomesOne() {
        MCH_Queue<String> q = new MCH_Queue<>(0, "a");
        assertEquals(1, q.size());
        q.put("b");
        assertEquals("b", q.oldest());
        assertEquals(1, new MCH_Queue<>(-3, "x").size());
    }

    /** The vehicle's use: at the drop check of tick {@code T} (after the move, before that tick's put) the oldest entry
     *  is the position put at the end of tick {@code T - 9}, and the position after tick 1 before that. */
    @Test
    void atTheDropCheckTheTrailIsNineTicksOld() {
        MCH_Queue<Integer> trail = new MCH_Queue<>(10, 0);
        int pos = 0; // the vehicle position: the end of tick t puts 1000 + t
        for (int t = 1; t <= 60; t++) {
            if (t <= 2) {
                trail.clear(pos); // the start of the tick, before the move: the position the last tick left
            }
            pos = 1000 + t; // the move
            int expected = t <= 1 ? 0 : 1000 + Math.max(1, t - 9);
            assertEquals(expected, trail.oldest(), "the drop check of tick " + t);
            trail.put(pos);
        }
    }
}
