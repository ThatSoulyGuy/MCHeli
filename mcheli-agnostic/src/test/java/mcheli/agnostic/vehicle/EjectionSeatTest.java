package mcheli.agnostic.vehicle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import mcheli.agnostic.vehicle.EjectionSeat.Occupant;
import org.junit.jupiter.api.Test;

/**
 * {@link EjectionSeat} against a private oracle: verbatim copies of the reference
 * {@code MCP_EntityPlane.getNumEjectionSeat} (:124-132), {@code MCH_EntityAircraft.getSeatNum} (:3366-3373),
 * {@code canEjectSeat} (EA:3868-3871) and {@code ejectSeat} (EA:3818-3839, with the DismountAll unload of
 * {@code unmountEntity}, EA:3661-3663), with {@code getAcInfo()}, the seat occupants and the parachute inventory turned
 * into parameters.
 */
class EjectionSeatTest {

    /** The reference bodies, verbatim except for the parameters. */
    private static final class Oracle {
        /** EA:3868-3871; {@code isUAV()} is {@code getAcInfo() != null && getAcInfo().isUAV}. */
        static boolean canEjectSeat(int sid, boolean hasInfo, boolean isUAV, boolean isEnableEjectionSeat) {
            return sid == 0 && hasInfo && isUAV ? false : sid >= 0 && sid < 2 && hasInfo && isEnableEjectionSeat;
        }

        /**
         * EA:3818-3839 over abstract seats: the caller sits in {@code sidOfEntity}, seat 1 holds {@code seat1}, the
         * inventory holds {@code parachutes}. Each {@code ejectSeatSub(entity, n)} call is recorded as {@code n}.
         * {@code unmountEntity()} contributes only its last lines (EA:3661-3663: with {@code cs_dismountAll}, the
         * {@code unmountCrew(false)} that puts every mob off).
         */
        static List<Integer> ejectSeat(int sidOfEntity, int parachutes, Occupant seat1, boolean csDismountAll) {
            List<Integer> subCalls = new ArrayList<>();
            int[] inventory = {parachutes};
            Occupant[] seats = {Occupant.PLAYER, seat1};
            // The caller: the pilot for sid 0, the seat-1 occupant for sid 1 (never null there).
            Occupant entity = sidOfEntity == 1 ? (seat1 == Occupant.EMPTY ? Occupant.PLAYER : seat1) : Occupant.PLAYER;
            int sid = sidOfEntity;
            if (sid >= 0 && sid <= 1) {
                if (inventory[0] > 0) {
                    if (sid == 0) {
                        inventory[0]--;
                        // unmountEntity(): ... if (this.cs_dismountAll) { this.unmountCrew(false); }
                        if (csDismountAll && seats[1] == Occupant.MOB) {
                            seats[1] = Occupant.EMPTY;
                        }
                        subCalls.add(0);
                        entity = seats[1] == Occupant.EMPTY ? null : seats[1];
                        if (entity == Occupant.PLAYER) {
                            entity = null;
                        }
                    }

                    if (inventory[0] > 0 && entity != null) {
                        inventory[0]--;
                        subCalls.add(1);
                    }
                }
            }
            return subCalls;
        }
        /** MCP_EntityPlane:124-132. */
        static int getNumEjectionSeat(boolean hasInfo, boolean isEnableEjectionSeat, int numSeatAndRack) {
            if (hasInfo && isEnableEjectionSeat) {
                int n = getSeatNum(hasInfo, numSeatAndRack) + 1;
                return n <= 2 ? n : 0;
            } else {
                return 0;
            }
        }

        /** MCH_EntityAircraft:3366-3373. */
        static int getSeatNum(boolean hasInfo, int numSeatAndRack) {
            if (!hasInfo) {
                return 0;
            }

            int s = numSeatAndRack;
            return s >= 1 ? s - 1 : 1;
        }
    }

    @Test
    void matchesTheReferenceForEverySeatCount() {
        for (int s = 0; s <= 30; s++) {
            for (boolean enable : new boolean[] {false, true}) {
                assertEquals(Oracle.getNumEjectionSeat(true, enable, s), EjectionSeat.numEjectionSeats(enable, s),
                    "enable=" + enable + " seats+racks=" + s);
            }
        }
    }

    @Test
    void pinnedCounts() {
        assertEquals(2, EjectionSeat.numEjectionSeats(true, 0), "no seat at all: getSeatNum is 1, so 2 (the edge)");
        assertEquals(1, EjectionSeat.numEjectionSeats(true, 1), "the pilot alone (a-10, f22a): 1");
        assertEquals(2, EjectionSeat.numEjectionSeats(true, 2), "pilot and one more seat (f-15e, b-2a): 2");
        assertEquals(0, EjectionSeat.numEjectionSeats(true, 3), "three seats: none");
        assertEquals(0, EjectionSeat.numEjectionSeats(true, 2 + 1), "two seats and one rack count as three: none");
        for (int s = 0; s <= 30; s++) {
            assertEquals(0, EjectionSeat.numEjectionSeats(false, s), "EnableEjectionSeat off: none, seats+racks=" + s);
        }
    }

    @Test
    void neverMoreThanTheTwoParachuteSlots() {
        for (int s = 0; s <= 30; s++) {
            int n = EjectionSeat.numEjectionSeats(true, s);
            assertTrue(n >= 0 && n <= EjectionSeat.MAX_SEATS, "seats+racks=" + s + " gave " + n);
        }
        assertEquals(2, EjectionSeat.MAX_SEATS, "MCH_AircraftInventory SLOT_PARACHUTE0/1");
    }

    @Test
    void canEjectSeatMatchesTheReference() {
        for (int sid = -2; sid <= 6; sid++) {
            for (boolean uav : new boolean[] {false, true}) {
                for (boolean enable : new boolean[] {false, true}) {
                    assertEquals(Oracle.canEjectSeat(sid, true, uav, enable), EjectionSeat.canEjectSeat(sid, uav, enable),
                        "sid=" + sid + " uav=" + uav + " enable=" + enable);
                }
            }
        }
    }

    @Test
    void canEjectSeatPinned() {
        assertTrue(EjectionSeat.canEjectSeat(0, false, true), "the pilot");
        assertTrue(EjectionSeat.canEjectSeat(1, false, true), "seat 1");
        assertFalse(EjectionSeat.canEjectSeat(2, false, true), "seat 2 has no ejection seat");
        assertFalse(EjectionSeat.canEjectSeat(-1, false, true), "not seated");
        assertFalse(EjectionSeat.canEjectSeat(0, true, true), "a UAV's pilot seat");
        assertTrue(EjectionSeat.canEjectSeat(1, true, true), "a UAV's seat 1 still may");
        assertFalse(EjectionSeat.canEjectSeat(0, false, false), "EnableEjectionSeat off");
        assertFalse(EjectionSeat.canEjectSeat(1, false, false), "EnableEjectionSeat off, seat 1");
    }

    @Test
    void planMatchesTheReferenceExhaustively() {
        int cases = 0;
        for (int sid = -1; sid <= 2; sid++) {
            for (Occupant seat1 : Occupant.values()) {
                for (int parachutes = 0; parachutes <= 2; parachutes++) {
                    for (boolean dismountAll : new boolean[] {false, true}) {
                        int[] expected = Oracle.ejectSeat(sid, parachutes, seat1, dismountAll).stream()
                            .mapToInt(Integer::intValue).toArray();
                        assertArrayEquals(expected, EjectionSeat.plan(sid, parachutes, seat1, dismountAll),
                            "sid=" + sid + " seat1=" + seat1 + " parachutes=" + parachutes + " dismountAll="
                                + dismountAll);
                        cases++;
                    }
                }
            }
        }
        assertEquals(4 * 3 * 3 * 2, cases);
    }

    @Test
    void planPinned() {
        assertArrayEquals(new int[] {0, 1}, EjectionSeat.plan(0, 2, Occupant.MOB, false), "a mob co-pilot goes along");
        assertArrayEquals(new int[] {0}, EjectionSeat.plan(0, 1, Occupant.MOB, false), "one parachute: the pilot only");
        assertArrayEquals(new int[] {0}, EjectionSeat.plan(0, 2, Occupant.PLAYER, false),
            "a player co-pilot decides for himself");
        assertArrayEquals(new int[] {0}, EjectionSeat.plan(0, 2, Occupant.EMPTY, false), "seat 1 empty");
        assertArrayEquals(new int[] {1}, EjectionSeat.plan(1, 1, Occupant.PLAYER, false), "seat 1 ejects himself");
        assertArrayEquals(new int[] {1}, EjectionSeat.plan(1, 2, Occupant.MOB, true), "seat 1 alone, whatever the rest");
        assertArrayEquals(new int[] {0}, EjectionSeat.plan(0, 2, Occupant.MOB, true),
            "DismountAll: the mob crew was put off plainly first");
        assertArrayEquals(new int[0], EjectionSeat.plan(0, 0, Occupant.MOB, false), "no parachute: nothing");
        assertArrayEquals(new int[0], EjectionSeat.plan(1, 0, Occupant.PLAYER, false), "no parachute, seat 1: nothing");
        assertArrayEquals(new int[0], EjectionSeat.plan(2, 2, Occupant.PLAYER, false), "seat 2: nothing");
        assertArrayEquals(new int[0], EjectionSeat.plan(-1, 2, Occupant.PLAYER, false), "not seated: nothing");
    }

    /** Reference {@code destroyAircraft} (EA:615-621): {@code ejectSeat(pilot)}, then {@code ejectSeat(seat-1
     *  occupant)}, here as two plan calls with what the first one left. */
    @Test
    void destructionIsTwoCalls() {
        // A player co-pilot and 2 parachutes: the pilot, then seat 1 with the parachute left.
        int[] first = EjectionSeat.plan(0, 2, Occupant.PLAYER, false);
        assertArrayEquals(new int[] {0}, first);
        assertArrayEquals(new int[] {1}, EjectionSeat.plan(1, 2 - first.length, Occupant.PLAYER, false));
        // With 1 parachute: the pilot only.
        int[] one = EjectionSeat.plan(0, 1, Occupant.PLAYER, false);
        assertArrayEquals(new int[] {0}, one);
        assertArrayEquals(new int[0], EjectionSeat.plan(1, 1 - one.length, Occupant.PLAYER, false));
        // A mob co-pilot and 2: both go in the first call, so seat 1 is empty for the second.
        assertArrayEquals(new int[] {0, 1}, EjectionSeat.plan(0, 2, Occupant.MOB, false));
    }
}
