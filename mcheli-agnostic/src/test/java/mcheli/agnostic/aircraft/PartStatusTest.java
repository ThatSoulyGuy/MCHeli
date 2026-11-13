package mcheli.agnostic.aircraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * {@link MCH_PartStatus} against a private oracle: verbatim copies of the baseline AbstractMchVehicle part-status
 * expressions it replaces, with {@code partStatus()} and the NBT reads turned into parameters. Compared on seeded
 * random words and operation sequences (20,000 steps per seed) and on hand-traced edges.
 */
class PartStatusTest {

    /** Baseline AbstractMchVehicle:245-253 and the expressions at the cited lines, verbatim apart from parameters. */
    private static final class Oracle {
        static final int PART_CANOPY = 1;
        static final int PART_WING = 1 << 3;
        static final int PART_HATCH = 1 << 4;
        static final int PART_SEARCH_LIGHT = 1 << 6;
        static final int CAMERA_MODE_SHIFT = 8;
        static final int BAY_SHIFT = 16;
        static final int MAX_BAYS = 16;
        static final int SAVED_PART_MASK = PART_WING | PART_HATCH;

        /** setPartBit:278-281. */
        static int setPartBit(int s, int bit, boolean on) {
            return on ? s | bit : s & ~bit;
        }

        /** cameraMode:726-729, the valid-seat branch. */
        static int cameraMode(int partStatus, int seat) {
            return partStatus >>> (CAMERA_MODE_SHIFT + 2 * seat) & 3;
        }

        /** setCameraModeBits:731-734. */
        static int setCameraModeBits(int partStatus, int seat, int mode) {
            int shift = CAMERA_MODE_SHIFT + 2 * seat;
            return partStatus & ~(3 << shift) | (mode & 3) << shift;
        }

        /** updateWeaponBayTargets:430-431. */
        static int bays(int s, int bits) {
            int next = (s & ~(-1 << BAY_SHIFT)) | bits << BAY_SHIFT;
            return next;
        }

        /** ensureParts:301 / tickParts:345. */
        static boolean bayOpen(int s, int i) {
            return (s >>> (BAY_SHIFT + i) & 1) != 0;
        }

        /** tickParts:328-330: the gate and the cleared word. */
        static boolean destroyNeeded(int partStatus) {
            return (partStatus & (PART_SEARCH_LIGHT | 0xF << CAMERA_MODE_SHIFT)) != 0;
        }

        static int destroyCleared(int partStatus) {
            return partStatus & ~(PART_SEARCH_LIGHT | 0xF << CAMERA_MODE_SHIFT);
        }

        /** savePartStatus:838 (the int key). */
        static int savedInt(int partStatus) {
            return partStatus & SAVED_PART_MASK;
        }

        /** loadPartStatus:843-846. */
        static int loaded(int tagInt, boolean tagSearchLight) {
            int s = tagInt & SAVED_PART_MASK;
            if (tagSearchLight) {
                s |= PART_SEARCH_LIGHT;
            }
            return s;
        }
    }

    private static final int[] BITS = {
        MCH_PartStatus.CANOPY, MCH_PartStatus.WING, MCH_PartStatus.HATCH, MCH_PartStatus.SEARCH_LIGHT,
    };

    @Test
    void bitValuesKeepTheReferenceLayout() {
        assertEquals(Oracle.PART_CANOPY, MCH_PartStatus.CANOPY);
        assertEquals(Oracle.PART_WING, MCH_PartStatus.WING);
        assertEquals(Oracle.PART_HATCH, MCH_PartStatus.HATCH);
        assertEquals(Oracle.PART_SEARCH_LIGHT, MCH_PartStatus.SEARCH_LIGHT);
        assertEquals(Oracle.CAMERA_MODE_SHIFT, MCH_PartStatus.CAMERA_MODE_SHIFT);
        assertEquals(Oracle.BAY_SHIFT, MCH_PartStatus.BAY_SHIFT);
        assertEquals(Oracle.MAX_BAYS, MCH_PartStatus.MAX_BAYS);
        assertEquals(Oracle.SAVED_PART_MASK, MCH_PartStatus.SAVED_MASK);
        // Reference DataWatcher 31: canopy bit 0, wing bit 3, hatch bit 4; getLastPartStatusMask() == 24.
        assertEquals(1, MCH_PartStatus.CANOPY);
        assertEquals(8, MCH_PartStatus.WING);
        assertEquals(16, MCH_PartStatus.HATCH);
        assertEquals(64, MCH_PartStatus.SEARCH_LIGHT);
        assertEquals(24, MCH_PartStatus.SAVED_MASK);
        assertEquals(0xF40, MCH_PartStatus.DESTROY_CLEAR);
    }

    @Test
    void matchesTheOracleOnSeededRandomWords() {
        for (long seed : new long[] {1L, 42L, 20261111L, -7L, 0xC0FFEEL}) {
            Random rnd = new Random(seed);
            for (int step = 0; step < 20_000; step++) {
                int s = rnd.nextInt();
                String at = "seed " + seed + " step " + step + " s=" + Integer.toHexString(s);
                int bit = rnd.nextBoolean() ? BITS[rnd.nextInt(BITS.length)] : 1 << rnd.nextInt(32);
                boolean on = rnd.nextBoolean();
                assertEquals(Oracle.setPartBit(s, bit, on), MCH_PartStatus.with(s, bit, on), "with " + at);
                for (int seat = 0; seat < MCH_CameraMode.SEATS; seat++) {
                    int mode = rnd.nextInt(8) - 2;
                    assertEquals(Oracle.cameraMode(s, seat), MCH_PartStatus.cameraMode(s, seat), "cameraMode " + at);
                    assertEquals(Oracle.setCameraModeBits(s, seat, mode), MCH_PartStatus.withCameraMode(s, seat, mode),
                        "withCameraMode " + at);
                }
                int bits = rnd.nextBoolean() ? rnd.nextInt(1 << MCH_PartStatus.MAX_BAYS) : rnd.nextInt();
                assertEquals(Oracle.bays(s, bits), MCH_PartStatus.withBays(s, bits), "withBays " + at);
                for (int i = 0; i < MCH_PartStatus.MAX_BAYS; i++) {
                    assertEquals(Oracle.bayOpen(s, i), MCH_PartStatus.bayOpen(s, i), "bayOpen " + i + " " + at);
                }
                assertEquals(Oracle.destroyNeeded(s), (s & MCH_PartStatus.DESTROY_CLEAR) != 0, "destroy gate " + at);
                assertEquals(Oracle.destroyCleared(s), s & ~MCH_PartStatus.DESTROY_CLEAR, "destroy clear " + at);
                assertEquals(Oracle.savedInt(s), MCH_PartStatus.saved(s), "saved " + at);
                boolean light = rnd.nextBoolean();
                assertEquals(Oracle.loaded(s, light), MCH_PartStatus.restored(s, light), "restored " + at);
            }
        }
    }

    @Test
    void matchesTheOracleOnSeededOperationSequences() {
        for (long seed : new long[] {3L, 99L, 0xBEEFL}) {
            Random rnd = new Random(seed);
            int o = 0;
            int n = 0;
            for (int step = 0; step < 20_000; step++) {
                switch (rnd.nextInt(5)) {
                    case 0 -> {
                        int bit = BITS[rnd.nextInt(BITS.length)];
                        boolean on = rnd.nextBoolean();
                        o = Oracle.setPartBit(o, bit, on);
                        n = MCH_PartStatus.with(n, bit, on);
                    }
                    case 1 -> {
                        int seat = rnd.nextInt(MCH_CameraMode.SEATS);
                        int mode = MCH_CameraMode.next(Oracle.cameraMode(o, seat), true);
                        o = Oracle.setCameraModeBits(o, seat, mode);
                        n = MCH_PartStatus.withCameraMode(n, seat, MCH_CameraMode.next(MCH_PartStatus.cameraMode(n, seat), true));
                    }
                    case 2 -> {
                        int bits = rnd.nextInt(1 << 6);
                        int next = Oracle.bays(o, bits);
                        if (next != o) {
                            o = next;
                        }
                        int nn = MCH_PartStatus.withBays(n, bits);
                        if (nn != n) {
                            n = nn;
                        }
                    }
                    case 3 -> {
                        if (Oracle.destroyNeeded(o)) {
                            o = Oracle.destroyCleared(o);
                        }
                        if ((n & MCH_PartStatus.DESTROY_CLEAR) != 0) {
                            n = n & ~MCH_PartStatus.DESTROY_CLEAR;
                        }
                    }
                    default -> {
                        boolean light = (o & Oracle.PART_SEARCH_LIGHT) != 0;
                        o = Oracle.loaded(Oracle.savedInt(o), light);
                        n = MCH_PartStatus.restored(MCH_PartStatus.saved(n), (n & MCH_PartStatus.SEARCH_LIGHT) != 0);
                    }
                }
                assertEquals(o, n, "seed " + seed + " step " + step);
            }
        }
    }

    @Test
    void theSaveDropsTheCanopyCameraAndBayBits() {
        int all = -1;
        assertEquals(MCH_PartStatus.WING | MCH_PartStatus.HATCH, MCH_PartStatus.saved(all));
        assertEquals(0, MCH_PartStatus.saved(MCH_PartStatus.CANOPY | MCH_PartStatus.SEARCH_LIGHT
            | 0xF << MCH_PartStatus.CAMERA_MODE_SHIFT | 0xFFFF << MCH_PartStatus.BAY_SHIFT));
        // The light lives in its own key: restoring without it leaves it off, with it turns it on.
        assertEquals(MCH_PartStatus.WING | MCH_PartStatus.HATCH, MCH_PartStatus.restored(all, false));
        assertEquals(MCH_PartStatus.WING | MCH_PartStatus.HATCH | MCH_PartStatus.SEARCH_LIGHT,
            MCH_PartStatus.restored(all, true));
        assertEquals(MCH_PartStatus.SEARCH_LIGHT, MCH_PartStatus.restored(0, true));
    }

    @Test
    void theRestoreRoundTripsTheSavedBitsAndTheLight() {
        Random rnd = new Random(7L);
        for (int step = 0; step < 10_000; step++) {
            int s = rnd.nextInt();
            boolean light = (s & MCH_PartStatus.SEARCH_LIGHT) != 0;
            int back = MCH_PartStatus.restored(MCH_PartStatus.saved(s), light);
            assertEquals(s & (MCH_PartStatus.SAVED_MASK | MCH_PartStatus.SEARCH_LIGHT), back);
            assertEquals(0, MCH_PartStatus.cameraMode(back, 0));
            assertEquals(0, MCH_PartStatus.cameraMode(back, 1));
            for (int i = 0; i < MCH_PartStatus.MAX_BAYS; i++) {
                assertFalse(MCH_PartStatus.bayOpen(back, i));
            }
        }
    }

    @Test
    void seatZeroAndSeatOneCameraNibblesAreIndependent() {
        Random rnd = new Random(11L);
        for (int step = 0; step < 10_000; step++) {
            int s = rnd.nextInt();
            int mode = rnd.nextInt(4);
            int s0 = MCH_PartStatus.withCameraMode(s, 0, mode);
            assertEquals(mode, MCH_PartStatus.cameraMode(s0, 0));
            assertEquals(MCH_PartStatus.cameraMode(s, 1), MCH_PartStatus.cameraMode(s0, 1));
            assertEquals(s & ~(3 << 8), s0 & ~(3 << 8));
            int s1 = MCH_PartStatus.withCameraMode(s, 1, mode);
            assertEquals(mode, MCH_PartStatus.cameraMode(s1, 1));
            assertEquals(MCH_PartStatus.cameraMode(s, 0), MCH_PartStatus.cameraMode(s1, 0));
            assertEquals(s & ~(3 << 10), s1 & ~(3 << 10));
        }
        // Baseline :733 keeps only the low two bits of the mode.
        assertEquals(3 << 8, MCH_PartStatus.withCameraMode(0, 0, 7));
        assertEquals(2 << 10, MCH_PartStatus.withCameraMode(0, 1, -2));
    }

    @Test
    void baysNeverTouchTheBitsBelowSixteen() {
        Random rnd = new Random(13L);
        for (int step = 0; step < 10_000; step++) {
            int s = rnd.nextInt();
            int bits = rnd.nextInt();
            int next = MCH_PartStatus.withBays(s, bits);
            assertEquals(s & 0xFFFF, next & 0xFFFF);
            assertEquals(bits << 16, next & 0xFFFF0000);
        }
        // Baseline :345: bay 15 is bit 31, read unsigned.
        assertTrue(MCH_PartStatus.bayOpen(Integer.MIN_VALUE, 15));
        assertFalse(MCH_PartStatus.bayOpen(0xFFFF, 0));
        assertEquals(0x1234, MCH_PartStatus.withBays(0xFFFF1234, 0));
    }

    @Test
    void theDestroyClearTouchesOnlyTheLightAndCameraBits() {
        Random rnd = new Random(17L);
        for (int step = 0; step < 10_000; step++) {
            int s = rnd.nextInt();
            int cleared = s & ~MCH_PartStatus.DESTROY_CLEAR;
            assertEquals(s & MCH_PartStatus.DESTROY_CLEAR, s ^ cleared);
            assertEquals(0, MCH_PartStatus.cameraMode(cleared, 0));
            assertEquals(0, MCH_PartStatus.cameraMode(cleared, 1));
        }
        // Baseline :328-330 on a word with every bit set.
        assertEquals(0xFFFFF0BF, -1 & ~MCH_PartStatus.DESTROY_CLEAR);
    }
}
