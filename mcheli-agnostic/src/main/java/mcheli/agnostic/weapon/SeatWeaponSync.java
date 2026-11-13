package mcheli.agnostic.weapon;

/**
 * The pure half of a vehicle's synced weapon view: the values the server derives from its live {@link VehicleWeapons}
 * loadout before it publishes them. The vehicle's weapon-sync component keeps the synced compound, the totals map, the
 * payload's quantising and the sends; this class only reads the loadout.
 *
 * <ul>
 *   <li>{@link #selection} and {@link #modes} are the {@code "s"} and {@code "m"} arrays of the vehicle's synced
 *       seat-weapon compound (rebuilt only when one of them changes).</li>
 *   <li>{@link #spentMask} is the synced spent-rail mask: the renderer hides a fired missile part while its slot
 *       reloads (reference {@code renderWeapon} gate).</li>
 *   <li>{@link #packNotice} is a rider's seat-weapon notice as one comparable key, so an unchanged notice is not sent
 *       again.</li>
 * </ul>
 * Bodies are the AbstractMchVehicle {@code publishSeatWeapons}, {@code publishAllAmmo} and {@code packSeatWeaponHud}
 * code from before the vehicle split, moved verbatim.
 */
public final class SeatWeaponSync {

    private SeatWeaponSync() {}

    /**
     * Each seat's selected weapon index, for seats {@code 0 .. seatCount-1}: -1 for a seat whose {@link
     * VehicleWeapons#selected(int)} is null, even when {@link VehicleWeapons#selectedIndex(int)} holds an index (a seat
     * beyond the loadout's own seat count reads -1 too).
     */
    public static int[] selection(VehicleWeapons w, int seatCount) {
        int[] selection = new int[seatCount];
        for (int seat = 0; seat < selection.length; seat++) {
            selection[seat] = w.selected(seat) != null ? w.selectedIndex(seat) : -1;
        }
        return selection;
    }

    /**
     * Every weapon's {@link WeaponSlot#mode() mode}, in slot order, or an EMPTY array when every mode is 0. The array
     * is non-empty exactly when the {@code "m"} entry is written; a reader takes a missing index as mode 0.
     */
    public static int[] modes(VehicleWeapons w) {
        int[] modes = new int[w.size()];
        boolean anyMode = false;
        for (int i = 0; i < modes.length; i++) {
            modes[i] = w.get(i).mode();
            anyMode |= modes[i] != 0;
        }
        if (!anyMode) {
            modes = new int[0];
        }
        return modes;
    }

    /** The spent-rail mask: bit {@code i} is set while slot {@code i} is {@link WeaponSlot#reloading() reloading}.
     *  Only slots 0..31 have a bit; later slots are ignored. */
    public static int spentMask(VehicleWeapons w) {
        int spent = 0;
        for (int i = 0; i < w.size(); i++) {
            if (i < 32 && w.get(i).reloading()) {
                spent |= 1 << i; // this slot just fired and is reloading -> its missile part hides
            }
        }
        return spent;
    }

    /**
     * One rider's seat-weapon notice as a comparable key: the magazine in the low 32 bits (-1, an endless magazine,
     * reads 0xFFFFFFFF), the quantised reload byte at bit 32, the heat byte at bit 40, the preparation flag at bit 48
     * and {@code seat & 0x7F} from bit 49. Only the low byte of {@code reloadQ} and {@code heatQ} is used, so a
     * sign-extended quantised byte packs the same as its masked value.
     */
    public static long packNotice(int seat, int magazine, long reloadQ, long heatQ, boolean prep) {
        long reload = reloadQ & 0xFFL;
        long heat = heatQ & 0xFFL;
        return (magazine & 0xFFFFFFFFL) | reload << 32 | heat << 40 | (prep ? 1L : 0L) << 48
            | (seat & 0x7FL) << 49;
    }
}
