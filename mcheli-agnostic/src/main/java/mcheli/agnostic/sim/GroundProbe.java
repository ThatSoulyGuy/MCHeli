package mcheli.agnostic.sim;

import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.spi.WorldView;

/**
 * Agnostic port of {@code MCH_Lib.getBlockIdY}/{@code getBlockY} and {@code isBlockInWater}: the column/neighbor
 * block probes MCHeli uses as airborne / on-ground / ceiling / submersion gates. The scan ARITHMETIC (position
 * rounding, layer + radius iteration, scan direction, first-hit order) stays pure here; only the per-cell block
 * test is delegated to {@link WorldView}.
 *
 * <p>NOTE (1.21 height range): the reference clamps the scan to {@code y in [0, 255]} (the 1.7.10 world height).
 * This is preserved verbatim for bit-fidelity; it must be revisited for 1.21's {@code [-64, 319]} range once the
 * flight model is validated in-game (a vehicle above y=255 or below y=0 would currently miss ground).
 */
public final class GroundProbe {
    private GroundProbe() {}

    /** Reference {@code MCH_Lib.getBlockIdY(entity, size, lenY)}: collidable-only column scan around the entity. */
    public static int blockIdInColumn(EntityRef self, int size, int lenY) {
        return blockIdInColumn(self, size, lenY, true);
    }

    public static int blockIdInColumn(EntityRef self, int size, int lenY, boolean collidableOnly) {
        return blockIdInColumn(self.world(),
            self.position().x(), self.position().y(), self.position().z(), size, lenY, collidableOnly);
    }

    /**
     * Reference {@code MCH_Lib.getBlockY(...)} + {@code getIdFromBlock}: the id of the first non-air (and, when
     * {@code collidableOnly}, collidable) block in the {@code size}-radius column of {@code lenY} layers
     * (upward if {@code lenY>0}, downward if {@code lenY<0}), scanning layer-by-layer then x then z; else 0.
     */
    public static int blockIdInColumn(WorldView world, double posX, double posY, double posZ,
                                      int size, int lenY, boolean collidableOnly) {
        if (lenY == 0) {
            return 0; // reference returns air -> id 0
        }
        int px = (int) (posX + 0.5);
        int py = (int) (posY + 0.5);
        int pz = (int) (posZ + 0.5);
        int cntY = lenY > 0 ? lenY : -lenY;

        for (int y = 0; y < cntY; y++) {
            if (py + y < 0 || py + y > 255) { // reference bounds-checks py+y even when scanning downward
                return 0;
            }

            int wy = py + (lenY > 0 ? y : -y);
            for (int x = -size / 2; x <= size / 2; x++) {
                for (int z = -size / 2; z <= size / 2; z++) {
                    int id = world.getBlockId(px + x, wy, pz + z);
                    if (id != 0) {
                        if (!collidableOnly) {
                            return id;
                        }
                        if (world.isCollidable(px + x, wy, pz + z)) {
                            return id;
                        }
                    }
                }
            }
        }

        return 0;
    }

    /**
     * Reference {@code getBlockY(size, lenY, false)} + the {@code !isEqual(block,water) && !isEqual(block,air)}
     * test the plane uses for its {@code canMoveOnGround} gate: is the first NON-AIR block in the column a SOLID
     * (non-water) block? All-air or water-first returns false.
     */
    public static boolean solidNonWaterInColumn(WorldView world, double posX, double posY, double posZ, int size, int lenY) {
        if (lenY == 0) {
            return false;
        }
        int px = (int) (posX + 0.5);
        int py = (int) (posY + 0.5);
        int pz = (int) (posZ + 0.5);
        int cntY = lenY > 0 ? lenY : -lenY;

        for (int y = 0; y < cntY; y++) {
            if (py + y < 0 || py + y > 255) {
                return false;
            }
            int wy = py + (lenY > 0 ? y : -y);
            for (int x = -size / 2; x <= size / 2; x++) {
                for (int z = -size / 2; z <= size / 2; z++) {
                    if (world.getBlockId(px + x, wy, pz + z) != 0) { // first non-air block
                        return !world.isWater(px + x, wy, pz + z);
                    }
                }
            }
        }
        return false;
    }

    /** Reference {@code MCH_Lib.isBlockInWater}: whether any of the 7 offset neighbor cells is water. */
    public static boolean isBlockInWater(WorldView world, int x, int y, int z) {
        int[][] offset = {{0, -1, 0}, {0, 0, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, 1, 0}};
        if (y <= 0) {
            return false;
        }

        for (int[] o : offset) {
            if (world.isWater(x + o[0], y + o[1], z + o[2])) {
                return true;
            }
        }

        return false;
    }
}
