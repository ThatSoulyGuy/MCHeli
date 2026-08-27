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
 *
 * <p>The reference's fore/aft {@code onGround} forcing (the two {@code zmog} passes, {@code MCH_WheelManager}:92-117)
 * is applied here, between the probe and the contact list. It is not cosmetic: {@link WheelTerrainSolver} zeroes an
 * axle's weight when neither of its wheels is grounded, so a body with NO contact at all would accumulate a zero
 * normal, and {@code atan2(0, 0) == 0} would yield a hard {@code pitch=+90 / roll=-90}. The forcing guarantees at
 * least one fore and one aft axle always carries weight, which keeps an airborne or wedged hull solving to its
 * CURRENT attitude instead of snapping vertical.
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

        // Weighted-center world origin, x/z only (rotated by the hull, as the reference does). Its Y is NOT taken
        // from the hull — see the note in phase C, where it is set to the mean contact height instead.
        Vec3d wc = transform(new Vec3d(0.0, WEIGHTED_CENTER_Y, weightedCenterZ), yaw, pitch, roll, pos);

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

        // Phase A — transform each axle's +x/-x wheel into the world and probe the surface beneath it.
        int n = wheels.size();
        Vec3d[] leftW = new Vec3d[n];
        Vec3d[] rightW = new Vec3d[n];
        double[] leftY = new double[n];
        double[] rightY = new double[n];
        boolean[] leftOn = new boolean[n];
        boolean[] rightOn = new boolean[n];
        double[] localZ = new double[n];
        boolean[] isPlus = new boolean[n];
        for (int i = 0; i < n; i++) {
            MCH_AircraftInfo.Wheel w = wheels.get(i);
            double lx = w.pos.x();
            double ly = w.pos.y();
            double lz = w.pos.z();
            leftW[i] = transform(new Vec3d(lx, ly, lz), yaw, pitch, roll, pos);    // +x wheel (reference v1)
            rightW[i] = transform(new Vec3d(-lx, ly, lz), yaw, pitch, roll, pos);  // -x wheel (reference v2)
            leftY[i] = groundSurfaceY(world, leftW[i]);
            rightY[i] = groundSurfaceY(world, rightW[i]);
            leftOn[i] = !Double.isNaN(leftY[i]);
            rightOn[i] = !Double.isNaN(rightY[i]);
            localZ[i] = lz;
            isPlus[i] = lz >= weightedCenterZ; // reference MCH_EntityWheel.setWheelPos: pos.z >= weightedCenter.z
        }

        // Phase B — the reference's two fore/aft onGround forcing passes (MCH_WheelManager:92-117), verbatim over
        // axles (the reference indexes wheel PAIRS, wheels[2i+0]/wheels[2i+1], which is one axle each).
        // Pass 1 walks front->back looking for a grounded AFT (!isPlus) axle; if it never finds one, the last axle
        // visited is forced grounded. Pass 2 mirrors that back->front for the FORE (isPlus) axles.
        int zmog = -1;
        for (int i = 0; i < n; i++) {
            zmog = i;
            if (isPlus[i] || (!leftOn[i] && !rightOn[i])) {
                continue;
            }
            zmog = -1;
            break;
        }
        if (zmog >= 0) {
            leftOn[zmog] = true;
            rightOn[zmog] = true;
        }
        zmog = -1;
        for (int i = n - 1; i >= 0; i--) {
            zmog = i;
            if (!isPlus[i] || (!leftOn[i] && !rightOn[i])) {
                continue;
            }
            zmog = -1;
            break;
        }
        if (zmog >= 0) {
            leftOn[zmog] = true;
            rightOn[zmog] = true;
        }

        // Phase C — resolve each wheel's resting height. The forcing flips only the solver's weight gate, never the
        // geometry (the reference forces the wheel entity's onGround flag, not its position), so a forced-but-unprobed
        // wheel keeps its own transformed Y — which is what makes an airborne hull solve to its current attitude.
        double[] leftC = new double[n];
        double[] rightC = new double[n];
        double sumY = 0.0;
        for (int i = 0; i < n; i++) {
            leftC[i] = !Double.isNaN(leftY[i]) ? leftY[i] : leftW[i].y();
            rightC[i] = !Double.isNaN(rightY[i]) ? rightY[i] : rightW[i].y();
            sumY += leftC[i] + rightC[i];
        }

        // DIVERGENCE FROM THE REFERENCE, and it is load-bearing. The reference measures each contact from
        // y = ac.posY + weightedCenter.y (a fixed -0.35 under the hull) because its contacts are real MCH_EntityWheel
        // entities that LAG toward the hull's transformed wheel position — so when the hull pitches, a lifting wheel's
        // own Y rises with it and the height offset stays small. This port has no wheel entities: it snaps each
        // contact straight onto the terrain. With a hull-referenced origin that leaves every contact pinned a constant
        // h above the origin, and since the solver's normal for an axle is (0, 2az, -2ah), tilting the hull shrinks
        // the z lever while h stays put — the tilt feeds itself and runs away (measured: a LEVEL tank on FLAT ground
        // reaching 30 deg of pitch in 20 ticks, still climbing).
        //
        // Referencing the contact plane instead makes the solve exact and self-calibrating: for ground of gradient g
        // the mean-centred heights are h_i = g*z_i, so the axle normal is (0, 2az_i, -2a*g*z_i) proportional to
        // (0, 1, -g) — the true surface normal — and flat ground gives exactly (0, 1, 0), i.e. zero tilt, for ANY
        // hull height and ANY wheelbase (the reference's formula only cancels for a symmetric one).
        Vec3d origin = new Vec3d(wc.x(), sumY / (2.0 * n), wc.z());

        List<WheelTerrainSolver.WheelContact> contacts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Vec3d left = new Vec3d(leftW[i].x() - origin.x(), leftC[i] - origin.y(), leftW[i].z() - origin.z());
            Vec3d right = new Vec3d(rightW[i].x() - origin.x(), rightC[i] - origin.y(), rightW[i].z() - origin.z());
            contacts.add(new WheelTerrainSolver.WheelContact(left, right, localZ[i], leftOn[i], rightOn[i]));
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
