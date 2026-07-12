package mcheli.agnostic.sim;

import java.util.ArrayList;
import java.util.List;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.spi.WorldView;
import mcheli.agnostic.value.Vec3d;

/**
 * The host seam {@link WheelTerrainSolver} needs: it produces the per-axle wheel-ground {@link
 * WheelTerrainSolver.WheelContact} the solver turns into the tank's target hull pitch/roll — the mechanism that makes
 * a tank tilt onto, and drive UP, slopes (a pitched hull's thrust {@code rot2Vec3(yaw, pitch-10)} gains an upward
 * component). The reference {@code MCH_WheelManager} dropped real {@code MCH_EntityWheel} entities that collided the
 * world; this reproduces their resting contact with a downward block scan under each wheel — pure and agnostic (no
 * entities), so it runs the same on client and server.
 *
 * <p>Each configured axle ({@code SetWheelPos}) spawns a left ({@code +x}) and right ({@code -x}) wheel. The local
 * position is transformed into the world by the hull orientation (reference {@code getTransformedPosition} =
 * {@code RotVec3(-yaw,-pitch,-roll) + pos}), the terrain surface Y beneath it is probed, and the contact is expressed
 * relative to the weighted-center world origin exactly as the reference's {@code v1}/{@code v2}.
 */
public final class WheelTerrainSampler {

    private WheelTerrainSampler() {}

    private static final double WEIGHTED_CENTER_Y = -0.35; // reference createWheels: weightedCenter = (0,-0.35,wcZ)
    private static final int SCAN_DOWN = 6;                 // blocks to search below a wheel for its resting surface

    /**
     * Sample the terrain under the tank's wheels and solve the ground-attitude tilt, or {@code null} if the config
     * has no wheels (then the caller keeps the hull level).
     *
     * @param weightedCenterZ the tank config's {@code WeightedCenterZ}
     */
    public static WheelTerrainSolver.TerrainTilt sample(EntityRef self, MCH_AircraftInfo info, double weightedCenterZ) {
        List<MCH_AircraftInfo.Wheel> wheels = info.wheels;
        if (wheels == null || wheels.isEmpty()) {
            return null;
        }
        float yaw = self.yaw();
        float pitch = self.pitch();
        float roll = self.roll();
        Vec3d pos = self.position();
        WorldView world = self.world();

        // Weighted-center world origin: x/z rotated by the hull, y = the raw weightedCenter.y (reference wc.y).
        Vec3d wc = transform(new Vec3d(0.0, WEIGHTED_CENTER_Y, weightedCenterZ), yaw, pitch, roll, pos);
        Vec3d origin = new Vec3d(wc.x(), pos.y() + WEIGHTED_CENTER_Y, wc.z());

        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (MCH_AircraftInfo.Wheel w : wheels) {
            minZ = Math.min(minZ, w.pos.z());
            maxZ = Math.max(maxZ, w.pos.z());
        }
        double avgZ = maxZ - minZ;
        if (avgZ <= 0.0) {
            return null; // single-axle config: no fore/aft lever, no meaningful pitch
        }

        List<WheelTerrainSolver.WheelContact> contacts = new ArrayList<>(wheels.size());
        for (MCH_AircraftInfo.Wheel w : wheels) {
            double lx = w.pos.x();
            double ly = w.pos.y();
            double lz = w.pos.z();
            Vec3d leftW = transform(new Vec3d(lx, ly, lz), yaw, pitch, roll, pos);    // +x wheel (reference v1)
            Vec3d rightW = transform(new Vec3d(-lx, ly, lz), yaw, pitch, roll, pos);  // -x wheel (reference v2)
            double leftY = groundSurfaceY(world, leftW);
            double rightY = groundSurfaceY(world, rightW);
            boolean leftOn = !Double.isNaN(leftY);
            boolean rightOn = !Double.isNaN(rightY);
            Vec3d left = new Vec3d(leftW.x() - origin.x(),
                (leftOn ? leftY : leftW.y()) - origin.y(), leftW.z() - origin.z());
            Vec3d right = new Vec3d(rightW.x() - origin.x(),
                (rightOn ? rightY : rightW.y()) - origin.y(), rightW.z() - origin.z());
            contacts.add(new WheelTerrainSolver.WheelContact(left, right, lz, leftOn, rightOn));
        }
        return WheelTerrainSolver.solve(self, info, contacts, avgZ);
    }

    /** Body-local → world: {@code RotVec3(v,-yaw,-pitch,-roll) + pos} (reference {@code getTransformedPosition}). */
    private static Vec3d transform(Vec3d local, float yaw, float pitch, float roll, Vec3d pos) {
        return local
            .rotateAroundZ(-roll / 180.0F * (float) Math.PI)
            .rotateAroundX(-pitch / 180.0F * (float) Math.PI)
            .rotateAroundY(-yaw / 180.0F * (float) Math.PI)
            .add(pos);
    }

    /** Top surface Y of the first collidable block at or below the wheel (its resting height), or NaN if none within
     *  {@link #SCAN_DOWN} blocks — the analogue of the reference wheel entity falling and coming to rest. */
    private static double groundSurfaceY(WorldView world, Vec3d wheel) {
        int bx = (int) Math.floor(wheel.x());
        int bz = (int) Math.floor(wheel.z());
        int top = (int) Math.floor(wheel.y()) + 1;
        for (int y = top; y >= top - SCAN_DOWN; y--) {
            if (world.getBlockId(bx, y, bz) != 0 && world.isCollidable(bx, y, bz)) {
                return y + 1.0; // block's top face = the wheel's resting Y
            }
        }
        return Double.NaN;
    }
}
