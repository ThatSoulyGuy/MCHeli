package mcheli.agnostic.aircraft;

/**
 * The bit layout of a vehicle's synced part status word: the port of the reference DataWatcher 31 part bits plus the
 * port's own camera and weapon-bay fields. Bits 0, 3 and 4 keep the reference layout: canopy open, wing folded, hatch
 * open (bits 1 nozzle and 2 landing gear are the VTOL and gear states, which the port syncs elsewhere). Bit 6 is the
 * search light (reference common-status bit 6). Bits 8-9 and 10-11 hold the camera mode of seats 0 and 1 (reference
 * {@code MCH_Camera.mode}). Bits 16-31 mark the weapon bays whose weapon is selected (reference
 * {@code updateWeaponBay}'s open target). Static and pure: the dependent layer owns the synced word and its writes.
 * ZERO Minecraft imports.
 */
public final class MCH_PartStatus {
    private MCH_PartStatus() {}

    /** Canopy open (reference DataWatcher 31 bit 0). */
    public static final int CANOPY = 1;
    /** Wing folded (reference bit 3). */
    public static final int WING = 1 << 3;
    /** Hatch open (reference bit 4). */
    public static final int HATCH = 1 << 4;
    /** Search light on (reference common-status bit 6). */
    public static final int SEARCH_LIGHT = 1 << 6;
    /** Seat 0's camera mode sits in the two bits from here, seat 1's in the next two. */
    public static final int CAMERA_MODE_SHIFT = 8;
    /** Weapon bay {@code i}'s open target is bit {@code BAY_SHIFT + i}. */
    public static final int BAY_SHIFT = 16;
    /** Bays that fit in the word. */
    public static final int MAX_BAYS = 16;
    /** Reference {@code getLastPartStatusMask() == 24}: only the wing and hatch survive a save. */
    public static final int SAVED_MASK = WING | HATCH;
    /** What the reference {@code destroyAircraft} resets: the search light and both seats' camera modes. */
    public static final int DESTROY_CLEAR = SEARCH_LIGHT | 0xF << CAMERA_MODE_SHIFT;

    /** {@code s} with {@code bit} set or cleared (baseline AbstractMchVehicle {@code setPartBit}:278-281). */
    public static int with(int s, int bit, boolean on) {
        return on ? s | bit : s & ~bit;
    }

    /** The camera mode of {@code seat} (baseline AbstractMchVehicle {@code cameraMode}:726-729). The caller checks
     *  {@link MCH_CameraMode#isValidSeat} first; any other seat reads the wrong bits. */
    public static int cameraMode(int s, int seat) {
        return s >>> (CAMERA_MODE_SHIFT + 2 * seat) & 3;
    }

    /** {@code s} with {@code seat}'s camera mode replaced by the low two bits of {@code mode} (baseline
     *  AbstractMchVehicle {@code setCameraModeBits}:731-734). */
    public static int withCameraMode(int s, int seat, int mode) {
        int shift = CAMERA_MODE_SHIFT + 2 * seat;
        return s & ~(3 << shift) | (mode & 3) << shift;
    }

    /** {@code s} with every bay bit replaced by {@code bits} (bit {@code i} = bay {@code i}); the bits below
     *  {@link #BAY_SHIFT} stay (baseline AbstractMchVehicle {@code updateWeaponBayTargets}:431). */
    public static int withBays(int s, int bits) {
        return (s & ~(-1 << BAY_SHIFT)) | bits << BAY_SHIFT;
    }

    /** Is bay {@code i}'s open target set (baseline AbstractMchVehicle :301 and :345)? */
    public static boolean bayOpen(int s, int i) {
        return (s >>> (BAY_SHIFT + i) & 1) != 0;
    }

    /** The {@code PartStatus} NBT value: only the saved bits (baseline AbstractMchVehicle {@code savePartStatus}:838).
     *  The search light goes in its own {@code SearchLight} key. */
    public static int saved(int s) {
        return s & SAVED_MASK;
    }

    /** The word restored from the {@code PartStatus} and {@code SearchLight} NBT values (baseline AbstractMchVehicle
     *  {@code loadPartStatus}:843-846): the camera and bay bits start clear. */
    public static int restored(int savedInt, boolean searchLight) {
        int s = savedInt & SAVED_MASK;
        if (searchLight) {
            s |= SEARCH_LIGHT;
        }
        return s;
    }
}
