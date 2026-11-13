package mcheli.agnostic.vehicle;

import java.util.List;
import java.util.function.IntPredicate;

/**
 * Seat rules shared by every vehicle: who may take which seat, and where a seat switch lands. The 1.21 port keeps all
 * riders as vanilla passengers with a seat map (seat 0 = pilot) in place of the reference's per-seat
 * {@code MCH_EntitySeat} entities; these are the map's rules. Pure, so they are unit-tested.
 */
public final class VehicleSeats {
    private VehicleSeats() {}

    /**
     * May {@code seat} be boarded now? It must exist ({@code 0..seatCount-1}; rack slots lie outside that range), be
     * empty, and no other member of an {@code ExclusionSeat} group containing it may be occupied (reference
     * {@code MCH_EntityAircraft.canRideSeatOrRack}:4382-4402).
     *
     * @param occupied       whether a seat currently holds a rider
     * @param exclusionGroups the config {@code exclusionSeatList}, may be null; null entries inside a group are skipped
     */
    public static boolean canOccupy(int seat, int seatCount, IntPredicate occupied, List<Integer[]> exclusionGroups) {
        if (seat < 0 || seat >= seatCount || occupied.test(seat)) {
            return false;
        }
        if (exclusionGroups != null) {
            for (Integer[] group : exclusionGroups) {
                if (group == null || !contains(group, seat)) {
                    continue;
                }
                for (Integer id : group) {
                    if (id != null && id != seat && occupied.test(id)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean contains(Integer[] group, int seat) {
        for (Integer id : group) {
            if (id != null && id == seat) {
                return true;
            }
        }
        return false;
    }

    /**
     * The seat a boarding rider takes: an explicitly requested seat (a clicked seat box, or a scripted preference) if
     * it is free, else the pilot seat (players only, and only when the config allows riding: {@code CanRide}), else
     * the first free crew seat. -1 when the vehicle is full for this rider.
     */
    public static int firstFreeSeat(boolean isPlayer, boolean canRide, int preferred, int seatCount,
                                    IntPredicate canOccupy) {
        if (preferred >= 0 && canOccupy.test(preferred)) {
            return preferred;
        }
        if (isPlayer && canRide && canOccupy.test(0)) {
            return 0;
        }
        for (int i = 1; i < seatCount; i++) {
            if (canOccupy.test(i)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * A FORCED player mount onto a vehicle with no seat left for them (vanilla's login reattach of a saved
     * {@code RootVehicle} after the config lost seats, or {@code /ride}): the seat to free by putting its non-player
     * occupant off, so the player can sit instead of being ejected. The highest crew seat comes first, the pilot seat
     * last and only when {@code CanRide} lets a player take it; the freed seat must pass the {@code ExclusionSeat} rule
     * once its occupant is gone. -1 when no non-player holds a seat the player could take. No reference counterpart:
     * 1.7.10 riders sat on per-seat entities and vanilla's reattach-or-discard rule did not exist.
     *
     * @param heldByNonPlayer whether a seat is held by a rider that is not a player
     * @param occupied        whether a seat holds any rider
     */
    public static int seatToVacateForPlayer(boolean canRide, int seatCount, IntPredicate heldByNonPlayer,
                                            IntPredicate occupied, List<Integer[]> exclusionGroups) {
        for (int s = seatCount - 1; s >= 0; s--) {
            if (!heldByNonPlayer.test(s) || (s == 0 && !canRide)) {
                continue;
            }
            final int freed = s;
            if (canOccupy(s, seatCount, k -> k != freed && occupied.test(k), exclusionGroups)) {
                return s;
            }
        }
        return -1;
    }

    /** The next-higher empty PASSENGER seat from {@code cur}, wrapping to the lowest empty; -1 if none. Seat 0 is never
     *  a next/prev target (reference {@code switchNextSeat} cycles the crew seats only). */
    public static int nextSeatTarget(int cur, int count, IntPredicate empty) {
        for (int i = cur + 1; i < count; i++) {   // next-higher empty (for a pilot caller this scans all of 1..N-1)
            if (empty.test(i)) { return i; }
        }
        for (int i = 1; i < cur; i++) {           // wrap to lowest empty passenger seat
            if (empty.test(i)) { return i; }
        }
        return -1;
    }

    /** The next-lower empty PASSENGER seat from {@code cur}, wrapping to the highest empty; -1 if none (reference
     *  {@code switchPrevSeat}). */
    public static int prevSeatTarget(int cur, int count, IntPredicate empty) {
        for (int i = cur - 1; i >= 1; i--) {      // next-lower empty (excludes seat 0)
            if (empty.test(i)) { return i; }
        }
        for (int i = count - 1; i > cur; i--) {   // wrap to highest empty passenger seat
            if (empty.test(i)) { return i; }
        }
        return -1;
    }

    /**
     * The switchable-seat gunner bit of {@code seat} in {@code mask} (bit {@code s} set while seat {@code s}'s occupant
     * is in gunner mode: the reference {@code isGunnerModeOtherSeat}, which boarding turns on,
     * {@code onMountPlayerSeat}:3439-3441). A seat past the 32-bit mask reads as on, the boarding default. The
     * expression of the baseline {@code AbstractMchVehicle.seatGunnerBit} (2966-2968), unchanged: a negative seat
     * shifts by {@code seat & 31}, as Java's {@code >>>} does.
     */
    public static boolean gunnerBit(int mask, int seat) {
        return seat >= Integer.SIZE || (mask >>> seat & 1) != 0;
    }

    /**
     * {@code mask} with {@code seat}'s switchable gunner bit set ({@code on}) or cleared (reference
     * {@code switchGunnerModeOtherSeat}:5316 flips it). The expression of the baseline
     * {@code AbstractMchVehicle.setSeatGunnerBit} (2976), unchanged: the shift is {@code seat & 31}, so the caller
     * keeps its own range check (it never writes the pilot seat 0 or a seat of 32 or more).
     */
    public static int withGunnerBit(int mask, int seat, boolean on) {
        return on ? mask | 1 << seat : mask & ~(1 << seat);
    }
}
