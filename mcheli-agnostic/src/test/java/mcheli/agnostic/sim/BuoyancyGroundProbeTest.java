package mcheli.agnostic.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * {@link GroundProbe} column/neighbor scans reproduce the reference arithmetic exactly against a scripted
 * in-memory block/water layout — verifying the pure math (slice bounds, float offset, the {@code py+y}
 * downward-scan bounds quirk, collidable-only gating) with zero Minecraft.
 */
class BuoyancyGroundProbeTest {

    /** Minimal WorldView: solid/water cells + a flat water surface for AABB overlap; other surfaces unused. */
    static final class StubWorld implements WorldView {
        final Map<String, Integer> cells = new HashMap<>(); // 1 = solid, 2 = water
        Double waterSurfaceY; // if set, isAABBInWater(box) = box.minY() < surface (a flat water body)

        static String k(int x, int y, int z) { return x + ":" + y + ":" + z; }
        StubWorld solid(int x, int y, int z) { cells.put(k(x, y, z), 1); return this; }
        StubWorld water(int x, int y, int z) { cells.put(k(x, y, z), 2); return this; }
        StubWorld surface(double y) { this.waterSurfaceY = y; return this; }

        @Override public int getBlockId(int x, int y, int z) { return cells.getOrDefault(k(x, y, z), 0); }
        @Override public boolean isWater(int x, int y, int z) { return cells.getOrDefault(k(x, y, z), 0) == 2; }
        @Override public boolean isCollidable(int x, int y, int z) { return cells.getOrDefault(k(x, y, z), 0) == 1; }
        @Override public boolean isAABBInWater(AABB box) { return waterSurfaceY != null && box.minY() < waterSurfaceY; }

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
        StubWorld w = new StubWorld().water(0, 99, 0);
        assertEquals(0, GroundProbe.blockIdInColumn(w, 0.4, 100.4, 0.4, 1, -3, true));  // water not collidable -> skipped
        assertEquals(2, GroundProbe.blockIdInColumn(w, 0.4, 100.4, 0.4, 1, -3, false)); // any non-air -> returned
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
    void isBlockInWaterNeighbors() {
        StubWorld w = new StubWorld().water(0, 99, 0);
        assertTrue(GroundProbe.isBlockInWater(w, 0, 100, 0));  // {0,-1,0} neighbor is water
        assertFalse(GroundProbe.isBlockInWater(w, 5, 100, 5)); // no water nearby
        assertFalse(GroundProbe.isBlockInWater(w, 0, 0, 0));   // y<=0 short-circuit
    }
}
