package mcheli.agnostic.vehicle;

/**
 * The ejection-seat rules of the reference aircraft, static and pure: how many ejection seats (and so how many
 * parachute slots in the riding menu) a config has, who may pull the handle ({@link #canEjectSeat}), and who leaves
 * when it is pulled ({@link #plan}). The dependent entity feeds it plain config values and seat occupants; the slots,
 * the key and the launch stay on the entity.
 */
public final class EjectionSeat {
    private EjectionSeat() {}

    /** The most ejection seats a config can have, which is also the number of parachute slots the reference riding
     *  inventory has ({@code MCH_AircraftInventory} {@code SLOT_PARACHUTE0/1}, :17-18). */
    public static final int MAX_SEATS = 2;

    /** Who sits in seat 1 when the pilot ejects: the reference takes a mob co-pilot along and leaves a player. */
    public enum Occupant { EMPTY, PLAYER, MOB }

    /**
     * Reference {@code MCH_EntityAircraft.canEjectSeat} (EA:3868-3871): the pilot (seat 0) and seat 1 of an
     * {@code EnableEjectionSeat} config, except the pilot seat of a UAV (flown from a station, not sat in).
     *
     * @param sid    the rider's seat, -1 for none
     * @param uav    the config {@code UAV}
     * @param enable the config {@code EnableEjectionSeat}
     */
    public static boolean canEjectSeat(int sid, boolean uav, boolean enable) {
        if (sid == 0 && uav) {
            return false;
        }
        return sid >= 0 && sid < MAX_SEATS && enable;
    }

    /**
     * Who one pull of the handle throws out, in order (reference {@code ejectSeat}, EA:3818-3839): nothing for a seat
     * other than 0 or 1, or with no parachute left; seat 1 alone for its own occupant; the pilot for seat 0, and then a
     * MOB in seat 1 too when a second parachute is left. A player in seat 1 decides for himself, and with the pilot's
     * DismountAll on the mob crew has already been unloaded plainly by the time seat 1 is looked at
     * ({@code unmountEntity}, EA:3661-3663), so then the pilot goes alone. Each entry uses up one parachute.
     *
     * @param sid         the seat of the rider who pulled it
     * @param parachutes  the parachutes in the slots (0 to {@link #MAX_SEATS})
     * @param seat1       who sits in seat 1 before anyone leaves
     * @param dismountAll the pilot's DismountAll setting ({@code cs_dismountAll})
     * @return the seats to eject, in order
     */
    public static int[] plan(int sid, int parachutes, Occupant seat1, boolean dismountAll) {
        if (sid < 0 || sid > 1 || parachutes <= 0) {
            return new int[0];
        }
        if (sid == 1) {
            return new int[] {1};
        }
        boolean mobGoes = seat1 == Occupant.MOB && !dismountAll && parachutes >= 2;
        return mobGoes ? new int[] {0, 1} : new int[] {0};
    }

    /**
     * The ejection seats of a plane, and the parachute slots its riding menu shows (reference
     * {@code MCP_EntityPlane.getNumEjectionSeat}:124-132 with {@code getSeatNum},
     * {@code MCH_EntityAircraft}:3366-3373):
     * 0 unless the config sets {@code EnableEjectionSeat}; else {@code getSeatNum() + 1}, where {@code getSeatNum} is
     * {@code s - 1} for {@code s = getNumSeatAndRack() >= 1} and 1 for {@code s = 0}, used only when it is at most
     * {@link #MAX_SEATS}. So a 1-seat config has 1, a 2-seat config 2, and a config with 3 or more seats and racks 0.
     * Every other category has none (the base {@code getNumEjectionSeat}, :3873-3875, returns 0).
     *
     * @param enableEjectionSeat the config {@code EnableEjectionSeat}
     * @param numSeatAndRack     the config's seats and racks, the pilot seat included ({@code getNumSeatAndRack})
     */
    public static int numEjectionSeats(boolean enableEjectionSeat, int numSeatAndRack) {
        if (!enableEjectionSeat) {
            return 0;
        }
        int seatNum = numSeatAndRack >= 1 ? numSeatAndRack - 1 : 1;
        int n = seatNum + 1;
        return n <= MAX_SEATS ? n : 0;
    }
}
