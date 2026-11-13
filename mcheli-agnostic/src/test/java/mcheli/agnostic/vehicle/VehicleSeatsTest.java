package mcheli.agnostic.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

/** Pure tests for the seat map rules ({@link VehicleSeats}). */
class VehicleSeatsTest {

    @Test
    void canOccupyNeedsAnEmptyExistingSeat() {
        Set<Integer> taken = Set.of(1);
        assertTrue(VehicleSeats.canOccupy(0, 3, taken::contains, null));
        assertFalse(VehicleSeats.canOccupy(1, 3, taken::contains, null), "occupied");
        assertFalse(VehicleSeats.canOccupy(3, 3, taken::contains, null), "rack slots / out of range");
        assertFalse(VehicleSeats.canOccupy(-1, 3, taken::contains, null));
    }

    @Test
    void exclusionGroupsAllowOneOccupant() {
        List<Integer[]> groups = List.of(new Integer[] {1, 2}, new Integer[] {3, null});
        Set<Integer> taken = Set.of(1);
        assertFalse(VehicleSeats.canOccupy(2, 5, taken::contains, groups), "seat 1 of the same group is taken");
        assertTrue(VehicleSeats.canOccupy(3, 5, taken::contains, groups), "another group, null ids skipped");
        assertTrue(VehicleSeats.canOccupy(4, 5, taken::contains, groups), "in no group");
    }

    @Test
    void firstFreeSeatReservesThePilotSeatForPlayers() {
        IntPredicate all = s -> true;
        assertEquals(0, VehicleSeats.firstFreeSeat(true, true, -1, 3, all));
        assertEquals(1, VehicleSeats.firstFreeSeat(false, true, -1, 3, all), "a mob never takes seat 0 by itself");
        assertEquals(1, VehicleSeats.firstFreeSeat(true, false, -1, 3, all), "CanRide=false: no pilot seat");
        assertEquals(0, VehicleSeats.firstFreeSeat(false, true, 0, 3, all), "a requested seat wins");
        assertEquals(-1, VehicleSeats.firstFreeSeat(false, true, -1, 1, all), "single-seat vehicle, mob rider");
    }

    @Test
    void firstFreeSeatFallsBackWhenThePreferredSeatIsTaken() {
        Set<Integer> taken = new HashSet<>(Set.of(0, 2));
        IntPredicate free = s -> !taken.contains(s);
        assertEquals(1, VehicleSeats.firstFreeSeat(true, true, 2, 4, free));
        taken.add(1);
        assertEquals(3, VehicleSeats.firstFreeSeat(true, true, -1, 4, free));
        taken.add(3);
        assertEquals(-1, VehicleSeats.firstFreeSeat(true, true, -1, 4, free));
    }

    @Test
    void aForcedPlayerMountFreesTheHighestCrewSeatAMobHolds() {
        // 3 seats, all taken: a player in seat 1, mobs in seats 0 (a scripted pilot) and 2.
        Set<Integer> mobs = Set.of(0, 2);
        IntPredicate occupied = s -> s >= 0 && s < 3;
        assertEquals(2, VehicleSeats.seatToVacateForPlayer(true, 3, mobs::contains, occupied, null),
            "the highest crew seat a mob holds comes first");
        assertEquals(0, VehicleSeats.seatToVacateForPlayer(true, 3, Set.of(0)::contains, occupied, null),
            "the pilot seat is the last resort");
        assertEquals(-1, VehicleSeats.seatToVacateForPlayer(false, 3, Set.of(0)::contains, occupied, null),
            "CanRide=false: a player can never take the pilot seat, so a mob pilot is not put off for nothing");
        assertEquals(-1, VehicleSeats.seatToVacateForPlayer(true, 3, s -> false, occupied, null),
            "only players aboard: nobody is bumped");
    }

    @Test
    void aSeatIsOnlyVacatedWhenTheExclusionRuleLetsThePlayerSitThere() {
        // Seats 1 and 2 share an exclusion group; a (config-changed) save has a mob in 2 AND a player in 1.
        List<Integer[]> groups = List.<Integer[]>of(new Integer[] {1, 2});
        IntPredicate occupied = s -> s == 0 || s == 1 || s == 2;
        assertEquals(-1, VehicleSeats.seatToVacateForPlayer(false, 3, Set.of(2)::contains, occupied, groups),
            "freeing seat 2 still leaves its group held by seat 1");
        assertEquals(2, VehicleSeats.seatToVacateForPlayer(false, 3, Set.of(2)::contains, s -> s == 2, groups),
            "the mob itself was the group's only occupant");
    }

    @Test
    void seatCycleSkipsOccupiedSeatsAndNeverReturnsThePilotSeat() {
        int count = 4;
        IntPredicate empty = s -> s == 1 || s == 3;
        assertEquals(3, VehicleSeats.nextSeatTarget(1, count, empty));
        assertEquals(3, VehicleSeats.prevSeatTarget(1, count, empty));
        assertEquals(1, VehicleSeats.nextSeatTarget(3, count, empty));
        assertEquals(1, VehicleSeats.prevSeatTarget(3, count, empty));
        assertEquals(3, VehicleSeats.nextSeatTarget(2, count, empty));
        assertEquals(1, VehicleSeats.prevSeatTarget(2, count, empty));
        assertEquals(1, VehicleSeats.nextSeatTarget(0, count, empty), "pilot -> lowest empty passenger seat");
        assertEquals(3, VehicleSeats.prevSeatTarget(0, count, empty), "pilot -> highest empty passenger seat");
        IntPredicate none = s -> false;
        assertEquals(-1, VehicleSeats.nextSeatTarget(1, count, none));
        assertEquals(-1, VehicleSeats.prevSeatTarget(1, count, none));
        IntPredicate everything = s -> true;
        assertEquals(2, VehicleSeats.nextSeatTarget(1, count, everything));
        assertEquals(-1, VehicleSeats.nextSeatTarget(1, 2, s -> s == 0), "seat 0 is never a target");
    }

    /**
     * Private oracle for the gunner bits: verbatim copies of the baseline AbstractMchVehicle methods they replace,
     * with the {@code DATA_SEAT_GUNNER_MODE} read and write turned into a parameter and a return value.
     */
    private static final class GunnerBitOracle {
        /** seatGunnerBit, baseline AbstractMchVehicle:2966-2968. */
        static boolean seatGunnerBit(int mask, int seat) {
            return seat >= Integer.SIZE || (mask >>> seat & 1) != 0;
        }

        /** setSeatGunnerBit, baseline AbstractMchVehicle:2971-2977 (the early return included); returns the new mask. */
        static int setSeatGunnerBit(int mask, int seat, boolean on) {
            if (seat <= 0 || seat >= Integer.SIZE) {
                return mask;
            }
            return on ? mask | 1 << seat : mask & ~(1 << seat);
        }
    }

    /** The dependent setSeatGunnerBit after the switch: the same early return, then {@link VehicleSeats#withGunnerBit}. */
    private static int setSeatGunnerBitNow(int mask, int seat, boolean on) {
        if (seat <= 0 || seat >= Integer.SIZE) {
            return mask;
        }
        return VehicleSeats.withGunnerBit(mask, seat, on);
    }

    @Test
    void gunnerBitsMatchTheBaselineOnRandomMasksAndEverySeat() {
        for (long seed : new long[] {1L, 7L, 42L, 20240611L}) {
            java.util.Random r = new java.util.Random(seed);
            for (int step = 0; step < 12_000; step++) {
                int mask = r.nextInt(4) == 0 ? (r.nextBoolean() ? 0 : -1) : r.nextInt();
                for (int seat = -1; seat <= 40; seat++) {
                    assertEquals(GunnerBitOracle.seatGunnerBit(mask, seat), VehicleSeats.gunnerBit(mask, seat),
                        "gunnerBit seed " + seed + " step " + step + " mask " + mask + " seat " + seat);
                    // The bare expression, outside the caller's range check (the shift wraps the same way).
                    int on = mask | 1 << seat;
                    int off = mask & ~(1 << seat);
                    assertEquals(on, VehicleSeats.withGunnerBit(mask, seat, true), "withGunnerBit on, seat " + seat);
                    assertEquals(off, VehicleSeats.withGunnerBit(mask, seat, false), "withGunnerBit off, seat " + seat);
                    assertEquals(GunnerBitOracle.setSeatGunnerBit(mask, seat, true), setSeatGunnerBitNow(mask, seat, true));
                    assertEquals(GunnerBitOracle.setSeatGunnerBit(mask, seat, false),
                        setSeatGunnerBitNow(mask, seat, false));
                }
            }
        }
    }

    @Test
    void gunnerBitSequencesMatchTheBaseline() {
        // Random boarding / key / leaving sequences on one synced mask, as the vehicle drives it.
        for (long seed : new long[] {3L, 99L, 123456789L}) {
            java.util.Random r = new java.util.Random(seed);
            int oracle = 0;
            int now = 0;
            for (int step = 0; step < 20_000; step++) {
                int seat = r.nextInt(42) - 1; // -1..40
                int op = r.nextInt(3);
                if (op == 0) {        // boarding: set on (onPassengerSeatTaken)
                    oracle = GunnerBitOracle.setSeatGunnerBit(oracle, seat, true);
                    now = setSeatGunnerBitNow(now, seat, true);
                } else if (op == 1) { // leaving or a seat switch: set off (removePassenger, moveToSeat)
                    oracle = GunnerBitOracle.setSeatGunnerBit(oracle, seat, false);
                    now = setSeatGunnerBitNow(now, seat, false);
                } else {              // the gunner-mode key: flip (toggleGunnerMode)
                    oracle = GunnerBitOracle.setSeatGunnerBit(oracle, seat, !GunnerBitOracle.seatGunnerBit(oracle, seat));
                    now = setSeatGunnerBitNow(now, seat, !VehicleSeats.gunnerBit(now, seat));
                }
                assertEquals(oracle, now, "mask, seed " + seed + " step " + step);
                for (int s = -1; s <= 40; s++) {
                    assertEquals(GunnerBitOracle.seatGunnerBit(oracle, s), VehicleSeats.gunnerBit(now, s));
                }
            }
        }
    }

    @Test
    void gunnerBitEdges() {
        // Baseline :2967: a seat past the mask reads as on, whatever the mask.
        assertTrue(VehicleSeats.gunnerBit(0, 32));
        assertTrue(VehicleSeats.gunnerBit(0, 40));
        assertFalse(VehicleSeats.gunnerBit(0, 31));
        assertTrue(VehicleSeats.gunnerBit(1 << 31, 31));
        assertTrue(VehicleSeats.gunnerBit(0b10, 1));
        assertFalse(VehicleSeats.gunnerBit(0b10, 2));
        // A negative seat shifts by seat & 31 (seat -1 reads bit 31); no caller passes one (isSeatGunnerMode returns
        // first for seat < 0, baseline :2938).
        assertTrue(VehicleSeats.gunnerBit(1 << 31, -1));
        assertFalse(VehicleSeats.gunnerBit(~(1 << 31), -1));
        // Baseline :2976: set and clear touch only that bit; seat 31 is the sign bit.
        assertEquals(0b1010, VehicleSeats.withGunnerBit(0b0010, 3, true));
        assertEquals(0b0010, VehicleSeats.withGunnerBit(0b1010, 3, false));
        assertEquals(Integer.MIN_VALUE, VehicleSeats.withGunnerBit(0, 31, true));
        assertEquals(Integer.MAX_VALUE, VehicleSeats.withGunnerBit(-1, 31, false));
        assertEquals(0b0010, VehicleSeats.withGunnerBit(0b0010, 1, true), "setting a set bit is a no-op");
        // The dependent range check (baseline :2972-2974) keeps the pilot seat and seats of 32+ out of the mask.
        assertEquals(0, setSeatGunnerBitNow(0, 0, true));
        assertEquals(0, setSeatGunnerBitNow(0, 32, true), "seat 32 would wrap to bit 0 without the check");
        assertEquals(-1, setSeatGunnerBitNow(-1, -1, false));
    }
}
