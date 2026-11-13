package mcheli.agnostic.sim;

import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.spi.WorldView;

/**
 * Agnostic port of {@code MCH_Lib.getBlockIdY}/{@code getBlockY}: the column block probes MCHeli uses as airborne /
 * on-ground / ceiling gates. The scan ARITHMETIC (position rounding, layer + radius iteration, scan direction,
 * first-hit order) stays pure here; only the per-cell block test is delegated to {@link WorldView}. "Collidable" is the
 * reference {@code block.canCollideCheck(0, true)} ({@link WorldView#isCollidable}): every non-air block but fire,
 * liquids included — so tall grass, flowers, snow layers and water all stop the probe, as they did in 1.7.10.
 *
 * <p><b>1.21 height range + negative coordinates (fixed).</b> The reference clamped the scan to {@code y in [0, 255]}
 * (the 1.7.10 world height) and rounded with a plain {@code (int)} cast. Both broke in 1.21: below y=0 (deepslate,
 * superflat worlds at y=-60) or above y=255 the probe always answered "air", so a parked aircraft counted as
 * AIRBORNE — the mouse could pitch/roll it freely on the ground and the model clipped into the terrain. The scan now
 * uses the world's real build height ({@link WorldView#minBuildHeight()}..{@link WorldView#maxBuildHeight()}), checks
 * the row it actually reads (rows outside the world are skipped while scanning toward it, e.g. a craft resting on the
 * top layer above the build limit), and rounds with {@code floor} (identical to the cast for positive coordinates, but
 * correct for negative ones, where the cast rounds toward zero and shifts the probe one block up).
 */
public final class GroundProbe {
    private GroundProbe() {}

    /** Reference {@code MCH_Lib.getBlockIdY(entity, size, lenY)}: collidable-only column scan around the entity. */
    public static int blockIdInColumn(EntityRef self, int size, int lenY) {
        return blockIdInColumn(self, size, lenY, true);
    }

    public static int blockIdInColumn(EntityRef self, int size, int lenY, boolean collidableOnly) {
        mcheli.agnostic.value.Vec3d p = self.position(); // read once: a port may build a new vector per call
        return blockIdInColumn(self.world(), p.x(), p.y(), p.z(), size, lenY, collidableOnly);
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
        int px = (int) Math.floor(posX + 0.5);
        int py = (int) Math.floor(posY + 0.5);
        int pz = (int) Math.floor(posZ + 0.5);
        int cntY = lenY > 0 ? lenY : -lenY;
        int minY = world.minBuildHeight();
        int maxY = world.maxBuildHeight();

        for (int y = 0; y < cntY; y++) {
            int wy = py + (lenY > 0 ? y : -y);
            if (wy < minY || wy > maxY) {
                if (lenY > 0 ? wy > maxY : wy < minY) {
                    return 0; // left the world in the scan direction: nothing more to find
                }
                continue; // not inside the world yet (e.g. above the build limit, scanning down): keep scanning
            }
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
        int px = (int) Math.floor(posX + 0.5);
        int py = (int) Math.floor(posY + 0.5);
        int pz = (int) Math.floor(posZ + 0.5);
        int cntY = lenY > 0 ? lenY : -lenY;
        int minY = world.minBuildHeight();
        int maxY = world.maxBuildHeight();

        for (int y = 0; y < cntY; y++) {
            int wy = py + (lenY > 0 ? y : -y);
            if (wy < minY || wy > maxY) {
                if (lenY > 0 ? wy > maxY : wy < minY) {
                    return false; // left the world in the scan direction
                }
                continue; // not inside the world yet: keep scanning toward it
            }
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

    /**
     * Reference {@code W_Block.isEqual(getBlockY(..., size, lenY, true), water)}: is the first collidable block of the
     * column (see {@link #blockIdInColumn}) water? False when the column holds none. The landing-gear rules use it to
     * keep a floatplane's gear up over the sea.
     */
    public static boolean waterFirstInColumn(WorldView world, double posX, double posY, double posZ, int size, int lenY) {
        if (lenY == 0) {
            return false;
        }
        int px = (int) Math.floor(posX + 0.5);
        int py = (int) Math.floor(posY + 0.5);
        int pz = (int) Math.floor(posZ + 0.5);
        int cntY = lenY > 0 ? lenY : -lenY;
        int minY = world.minBuildHeight();
        int maxY = world.maxBuildHeight();

        for (int y = 0; y < cntY; y++) {
            int wy = py + (lenY > 0 ? y : -y);
            if (wy < minY || wy > maxY) {
                if (lenY > 0 ? wy > maxY : wy < minY) {
                    return false; // left the world in the scan direction
                }
                continue; // not inside the world yet: keep scanning toward it
            }
            for (int x = -size / 2; x <= size / 2; x++) {
                for (int z = -size / 2; z <= size / 2; z++) {
                    if (world.getBlockId(px + x, wy, pz + z) != 0 && world.isCollidable(px + x, wy, pz + z)) {
                        return world.isWater(px + x, wy, pz + z);
                    }
                }
            }
        }
        return false;
    }

    // MCH_Lib.isBlockInWater (the 7-cell submersion test) is not here: its only vehicle use, the submersion damage, is
    // AbstractMchVehicle.waterAtProbe, and the projectiles test water natively in MchBullet.
}
