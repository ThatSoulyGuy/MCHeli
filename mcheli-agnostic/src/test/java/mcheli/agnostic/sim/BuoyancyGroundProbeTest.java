package mcheli.agnostic.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.spi.RandomSource;
import mcheli.agnostic.spi.Role;
import mcheli.agnostic.spi.WorldView;
import mcheli.agnostic.value.AABB;
import mcheli.agnostic.value.RayHit;
import mcheli.agnostic.value.Vec3d;
import org.junit.jupiter.api.Test;

/**
 * Parity harness for the world-sampling seams: asserts the ported {@link Buoyancy} 5-slice sampler and the
 * {@link GroundProbe} column scans reproduce the reference arithmetic exactly against a scripted in-memory
 * block/water layout — verifying the pure math (slice bounds, float offset, the 1.21 build-height bounds,
 * collidable-only gating) with zero Minecraft. The stub's block predicates follow the production
 * {@code NeoWorldView}: {@code isCollidable} is the reference {@code canCollideCheck(0, true)} (water and plants
 * count, fire does not) and {@code hasCollision} is the collision shape (solid blocks only).
 */
class BuoyancyGroundProbeTest {

    /** Minimal WorldView: solid/water/plant/fire cells + a flat water surface for AABB overlap; other surfaces unused. */
    static final class StubWorld implements WorldView {
        static final int SOLID = 1, WATER = 2, PLANT = 3, FIRE = 4;
        final Map<String, Integer> cells = new HashMap<>();
        Double waterSurfaceY; // if set, isAABBInWater(box) = box.minY() < surface (a flat water body)
        int minY = -64;       // the build range, the WorldView defaults (a 1.21 overworld) unless a test narrows it
        int maxY = 319;

        static String k(int x, int y, int z) { return x + ":" + y + ":" + z; }
        StubWorld solid(int x, int y, int z) { cells.put(k(x, y, z), SOLID); return this; }
        StubWorld water(int x, int y, int z) { cells.put(k(x, y, z), WATER); return this; }
        StubWorld plant(int x, int y, int z) { cells.put(k(x, y, z), PLANT); return this; }
        StubWorld fire(int x, int y, int z) { cells.put(k(x, y, z), FIRE); return this; }
        StubWorld surface(double y) { this.waterSurfaceY = y; return this; }

        @Override public int getBlockId(int x, int y, int z) { return cells.getOrDefault(k(x, y, z), 0); }
        @Override public boolean isWater(int x, int y, int z) { return cells.getOrDefault(k(x, y, z), 0) == WATER; }
        // Production semantics (NeoWorldView): every non-air block but fire is "collidable"; only solids have a shape.
        @Override public boolean isCollidable(int x, int y, int z) {
            int c = cells.getOrDefault(k(x, y, z), 0);
            return c != 0 && c != FIRE;
        }
        @Override public boolean hasCollision(int x, int y, int z) { return cells.getOrDefault(k(x, y, z), 0) == SOLID; }
        @Override public boolean isAABBInWater(AABB box) { return waterSurfaceY != null && box.minY() < waterSurfaceY; }
        @Override public int minBuildHeight() { return minY; }
        @Override public int maxBuildHeight() { return maxY; }

        @Override public boolean isRemote() { return false; }
        @Override public long totalTime() { return 0; }
        @Override public String materialName(int x, int y, int z) { throw new UnsupportedOperationException(); }
        @Override public List<EntityRef> entitiesInAABB(AABB box) { throw new UnsupportedOperationException(); }
        @Override public List<EntityRef> entitiesInAABB(AABB box, Role role) { throw new UnsupportedOperationException(); }
        @Override public RayHit rayTraceBlocks(Vec3d from, Vec3d to) { throw new UnsupportedOperationException(); }
        @Override public RayHit clip(Vec3d from, Vec3d to, boolean stopOnLiquid) { throw new UnsupportedOperationException(); }
        @Override public void spawn(EntityRef entity) { throw new UnsupportedOperationException(); }
        @Override public void spawnParticle(String name, Vec3d pos, Vec3d velocity) { throw new UnsupportedOperationException(); }
        @Override public void playSound(Vec3d pos, String name, float volume, float pitch) { throw new UnsupportedOperationException(); }
        @Override public void playSoundAtEntity(EntityRef entity, String name, float volume, float pitch) { throw new UnsupportedOperationException(); }
        @Override public RandomSource random() { throw new UnsupportedOperationException(); }
    }

    // box spanning y in [100,101], x/z in [-1,1]
    static AABB box100() { return new AABB(-1.0, 100.0, -1.0, 1.0, 101.0, 1.0); }

    @Test
    void buoyancyFullAndNone() {
        assertEquals(1.0, Buoyancy.waterDepth(new StubWorld().surface(500.0), box100(), 0.0F), 1e-9);
        assertEquals(0.0, Buoyancy.waterDepth(new StubWorld().surface(50.0), box100(), 0.0F), 1e-9);
    }

    @Test
    void buoyancyPartialSliceBounds() {
        // slice i lower edge d1 = 100 + (i)/5 - 0.125 = 99.875, 100.075, 100.275, 100.475, 100.675
        // surface 100.3 -> slices 0,1,2 submerged -> 3 * 0.2
        assertEquals(0.6, Buoyancy.waterDepth(new StubWorld().surface(100.3), box100(), 0.0F), 1e-9);
    }

    @Test
    void buoyancyFloatOffsetLowersSlices() {
        // floatOffset -0.5 shifts every slice edge down 0.5 -> all d1 < 100.3 -> full submersion
        assertEquals(1.0, Buoyancy.waterDepth(new StubWorld().surface(100.3), box100(), -0.5F), 1e-9);
    }

    @Test
    void groundProbeDownwardReachAndDepth() {
        StubWorld w = new StubWorld().solid(0, 97, 0);
        // entity at (0.4,100.4,0.4) -> px=0,py=100,pz=0; size 1 -> single column
        assertEquals(0, GroundProbe.blockIdInColumn(w, 0.4, 100.4, 0.4, 1, -3, true)); // reads 100,99,98 -> miss
        assertEquals(1, GroundProbe.blockIdInColumn(w, 0.4, 100.4, 0.4, 1, -4, true)); // reads down to 97 -> hit
    }

    @Test
    void groundProbeCollidableOnlyGate() {
        // Only fire fails canCollideCheck: the collidable-only scan skips it, the any-block scan returns it.
        StubWorld w = new StubWorld().fire(0, 99, 0);
        assertEquals(0, GroundProbe.blockIdInColumn(w, 0.4, 100.4, 0.4, 1, -3, true));
        assertEquals(StubWorld.FIRE, GroundProbe.blockIdInColumn(w, 0.4, 100.4, 0.4, 1, -3, false));
    }

    @Test
    void groundProbeStopsOnWaterAndPlants() {
        // The reference probe counts liquids (BlockLiquid.canCollideCheck(0, true)) and every plant/snow layer
        // (Block.isCollidable() is true for all but fire), so heli ground effect and the on-ground gates fire over them.
        assertEquals(StubWorld.WATER, GroundProbe.blockIdInColumn(new StubWorld().water(0, 99, 0), 0.4, 100.4, 0.4, 1, -3, true));
        assertEquals(StubWorld.PLANT, GroundProbe.blockIdInColumn(new StubWorld().plant(0, 99, 0), 0.4, 100.4, 0.4, 1, -3, true));
    }

    @Test
    void waterFirstInColumnReportsTheFirstCollidableBlock() {
        StubWorld sea = new StubWorld().water(0, 98, 0).solid(0, 90, 0);
        assertTrue(GroundProbe.waterFirstInColumn(sea, 0.4, 100.4, 0.4, 1, -20));
        StubWorld land = new StubWorld().solid(0, 98, 0).water(0, 97, 0);
        assertFalse(GroundProbe.waterFirstInColumn(land, 0.4, 100.4, 0.4, 1, -20));
        assertFalse(GroundProbe.waterFirstInColumn(new StubWorld(), 0.4, 100.4, 0.4, 3, -20)); // nothing below
        assertFalse(GroundProbe.waterFirstInColumn(sea, 0.4, 100.4, 0.4, 1, -2));               // out of reach
    }

    @Test
    void groundProbeUpwardCeiling() {
        StubWorld w = new StubWorld().solid(0, 102, 0);
        assertEquals(1, GroundProbe.blockIdInColumn(w, 0.4, 100.4, 0.4, 1, 3, true)); // reads 100,101,102 -> hit
    }

    @Test
    void groundProbeAbove255() {
        // 1.21 worlds reach y=319. The old 1.7.10 [0,255] clamp aborted this scan (py+y = 256 > 255) and reported
        // "airborne" for a craft parked on a mountain; the probe now uses the world's real build height.
        StubWorld w = new StubWorld().solid(0, 252, 0);
        assertEquals(1, GroundProbe.blockIdInColumn(w, 0.4, 254.4, 0.4, 1, -3, true));
        StubWorld high = new StubWorld().solid(0, 299, 0);
        assertEquals(1, GroundProbe.blockIdInColumn(high, 0.4, 300.0, 0.4, 3, -2, true));
    }

    @Test
    void groundProbeBelowZeroSuperflat() {
        // Superflat: grass top at y=-61, a parked vehicle stands at y=-60.0. The old clamp (py<0 -> air) AND the
        // truncating (int) cast (-59.5 -> -59) both made the 2-deep "on the ground?" probe miss the grass.
        StubWorld w = new StubWorld();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                w.solid(x, -61, z);
            }
        }
        assertEquals(1, GroundProbe.blockIdInColumn(w, 0.3, -60.0, 0.3, 3, -2, true));
        assertEquals(1, GroundProbe.blockIdInColumn(w, -1.3, -60.0, -0.6, 3, -2, true)); // negative x/z too
        // and a craft hovering 4 blocks up is still airborne
        assertEquals(0, GroundProbe.blockIdInColumn(w, 0.3, -56.0, 0.3, 3, -2, true));
    }

    @Test
    void groundProbeStopsAtWorldBottom() {
        // Scanning below the world floor finds nothing (and never reads out-of-world cells).
        StubWorld w = new StubWorld().solid(0, -70, 0);
        assertEquals(0, GroundProbe.blockIdInColumn(w, 0.4, -63.0, 0.4, 1, -10, true));
    }

    @Test
    void groundProbeFindsTopLayerFromAboveTheBuildLimit() {
        // A craft resting on the topmost block layer (y=319) sits at y=320: the first scanned row is above the world,
        // which must be skipped (not end the scan), so the probe still reaches the block at 319.
        StubWorld w = new StubWorld();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                w.solid(x, 319, z);
            }
        }
        assertEquals(1, GroundProbe.blockIdInColumn(w, 0.3, 320.0, 0.3, 3, -2, true));
        assertTrue(GroundProbe.solidNonWaterInColumn(w, 0.3, 320.0, 0.3, 3, -2));
        // further up, two rows down stay above the world: still airborne
        assertEquals(0, GroundProbe.blockIdInColumn(w, 0.3, 325.0, 0.3, 3, -2, true));
        assertFalse(GroundProbe.solidNonWaterInColumn(w, 0.3, 325.0, 0.3, 3, -2));
    }

    // ---- GroundProbe.airBelow, the rope repelling's getAlt ----

    /** Reference {@code MCH_EntityAircraft.getAlt} (EA:1817-1825), verbatim but for the world. */
    static int referenceGetAlt(WorldView w, double px, double py, double pz) {
        int i = 0;
        while (i < 256 && !(py - i <= 0.0) && (py - i >= 256.0 || 0 == w.getBlockId((int) px, (int) py - i, (int) pz))) {
            i++;
        }
        return i;
    }

    @Test
    void airBelowCountsTheAirCellsStraightDown() {
        StubWorld w = new StubWorld().solid(0, 0, 0);
        assertEquals(12, GroundProbe.airBelow(w, 0.5, 12.0, 0.5)); // a floor 12 below: cells 12..1
        assertEquals(12, GroundProbe.airBelow(w, 0.5, 12.9, 0.5)); // the cell is floor-rounded
        assertEquals(0, GroundProbe.airBelow(new StubWorld().solid(0, 10, 0), 0.5, 10.2, 0.5)); // its own cell counts
        // Any non-air block stops it: water and plants too (the reference compared the block id with 0).
        assertEquals(3, GroundProbe.airBelow(new StubWorld().water(0, 7, 0), 0.5, 10.2, 0.5));
        assertEquals(3, GroundProbe.airBelow(new StubWorld().plant(0, 7, 0), 0.5, 10.2, 0.5));
        assertEquals(3, GroundProbe.airBelow(new StubWorld().fire(0, 7, 0), 0.5, 10.2, 0.5));
        // One column only: a block beside it does not count.
        assertEquals(12, GroundProbe.airBelow(new StubWorld().solid(0, 0, 0).solid(1, 6, 0), 0.5, 12.0, 0.5));
        // Floor rounding at negative x/z: -0.5 is cell -1, where the reference's cast read cell 0.
        assertEquals(5, GroundProbe.airBelow(new StubWorld().solid(-1, 0, -1), -0.5, 5.0, -0.5));
    }

    @Test
    void airBelowUsesTheWorldsBuildRangeAndTheCap() {
        // Above the top (319) counts as air: from 330 over a floor at 310, cells 330..311.
        assertEquals(20, GroundProbe.airBelow(new StubWorld().solid(0, 310, 0), 0.5, 330.0, 0.5));
        // The superflat self-test ground: at -40 over a floor at -60 it gives 20, where the reference stopped at y <= 0
        // and gave 0 (deliberate divergence).
        StubWorld flat = new StubWorld().solid(0, -60, 0);
        assertEquals(20, GroundProbe.airBelow(flat, 0.5, -40.0, 0.5));
        assertEquals(0, referenceGetAlt(flat, 0.5, -40.0, 0.5));
        // Nothing below: it stops at the world's bottom (-64), as the reference stopped at 0.
        assertEquals(24, GroundProbe.airBelow(new StubWorld(), 0.5, -40.0, 0.5));
        assertEquals(0, GroundProbe.airBelow(new StubWorld(), 0.5, -64.0, 0.5));
        // At most 256.
        assertEquals(256, GroundProbe.airBelow(new StubWorld(), 0.5, 300.0, 0.5));
        assertEquals(GroundProbe.AIR_BELOW_CAP, GroundProbe.airBelow(new StubWorld(), 0.5, 1000.0, 0.5));
    }

    @Test
    void airBelowIsTheReferenceGetAltInThe1710World() {
        Random rnd = new Random(1817L);
        int compared = 0;
        for (int n = 0; n < 5000; n++) {
            StubWorld w = new StubWorld();
            w.minY = 0;   // the 1.7.10 world: 0..255
            w.maxY = 255;
            int x = rnd.nextInt(4);
            int z = rnd.nextInt(4);
            int blocks = rnd.nextInt(6);
            for (int k = 0; k < blocks; k++) {
                int y = rnd.nextInt(300);
                switch (rnd.nextInt(3)) {
                    case 0 -> w.solid(x, y, z);
                    case 1 -> w.water(x, y, z);
                    default -> w.plant(x, y, z);
                }
            }
            double px = x + rnd.nextDouble();
            double pz = z + rnd.nextDouble();
            double py = rnd.nextDouble() * 330.0 - 10.0;
            assertEquals(referenceGetAlt(w, px, py, pz), GroundProbe.airBelow(w, px, py, pz),
                "getAlt at " + px + ", " + py + ", " + pz);
            compared++;
        }
        assertEquals(5000, compared);
    }
}
