package mcheli.agnostic.sim;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.spi.WorldView;
import mcheli.agnostic.value.AABB;

/**
 * Agnostic port of {@code MCH_EntityAircraft.getWaterDepth()} — the 5-slice buoyancy sampler shared by every
 * vehicle's force integration. Splits the entity's bounding box vertical extent into {@link #SLICES} slices
 * (each dropped 0.125 and shifted by {@code info.floatOffset}) and returns the 0..1 fraction of slices whose AABB
 * is in water.
 *
 * <p>Bit-faithful to the reference: the slice bounds use the SAME double arithmetic and the water accumulation is
 * {@code 1.0 / 5} per submerged slice. The only platform touch is the per-slice water test, delegated to
 * {@link WorldView#isAABBInWater}.
 */
public final class Buoyancy {
    private Buoyancy() {}

    /** Number of vertical slices — the reference's {@code byte b0 = 5}. */
    public static final int SLICES = 5;

    /** Sample {@code self}'s current bounding box for submerged fraction, using {@code info.floatOffset}. */
    public static double waterDepth(EntityRef self, MCH_AircraftInfo info) {
        return waterDepth(self.world(), self.boundingBox(), info.floatOffset);
    }

    /** Direct form (for testing / reuse): submerged fraction of {@code box} in {@code world} with {@code floatOffset}. */
    public static double waterDepth(WorldView world, AABB box, float floatOffset) {
        int b0 = SLICES;
        double d0 = 0.0;

        for (int i = 0; i < b0; i++) {
            double d1 = box.minY() + (box.maxY() - box.minY()) * (i + 0) / b0 - 0.125;
            double d2 = box.minY() + (box.maxY() - box.minY()) * (i + 1) / b0 - 0.125;
            d1 += floatOffset;
            d2 += floatOffset;
            AABB slice = new AABB(box.minX(), d1, box.minZ(), box.maxX(), d2, box.maxZ());
            if (world.isAABBInWater(slice)) {
                d0 += 1.0 / b0;
            }
        }

        return d0;
    }
}
