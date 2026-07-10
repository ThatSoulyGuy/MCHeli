package mcheli.agnostic.sim;

import java.util.List;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.value.Vec3d;

/**
 * Bit-faithful extraction of the PORTABLE terrain-follow math from {@code MCH_WheelManager.move(...)}
 * (the tank ground-attitude solver). Given the world contacts the host sampled for each axle, this
 * accumulates a body-up surface normal from weighted per-axle cross products and turns it into a
 * clamped target pitch/roll plus a slope-slide motion nudge.
 *
 * <h2>What stays HOST (the wheel-contact sampling seam)</h2>
 * The reference drives real {@code MCH_EntityWheel} entities that collide the world, and everything
 * that produces a {@link WheelContact} is host-coupled and NOT ported here:
 * <ul>
 *   <li>spawning/positioning the per-axle left+right wheel entities and moving/colliding them
 *       ({@code moveEntity}, the {@code motionY *= 0.15} settle, the {@code -0.1} gravity probe),
 *       which yields each wheel's collided world position and its {@code onGround} flag;</li>
 *   <li>the front/back {@code onGround} forcing (the two {@code zmog} passes over {@code isPlus}) —
 *       the host must apply this <b>before</b> sampling {@code onGround} into the contacts;</li>
 *   <li>the weighted-center world origin — {@code wc = ac.getTransformedPosition(weightedCenter)}
 *       with {@code wc.y = weightedCenter.y}, giving the origin
 *       {@code (ac.posX + wc.x, ac.posY + wc.y, ac.posZ + wc.z)}. This uses the entity transform
 *       (host rotation), so the host computes it and subtracts it, passing each wheel's world
 *       position <b>relative to that origin</b> as {@link WheelContact#left()}/{@link WheelContact#right()}
 *       (the reference's {@code v1}/{@code v2});</li>
 *   <li>{@code avgZ = maxZ - minZ} — a spawn-time constant over the configured wheel local-Z's
 *       (computed in {@code createWheels}); passed in as {@code avgZ};</li>
 *   <li>applying the result: the reference writes {@code ac.motionX/Z += slopeSlide}, stores
 *       {@code targetPitch/targetRoll}, and (only when the rider is not the client player)
 *       {@code setRotPitch/Roll} directly. The caller applies {@link TerrainTilt} the same way; the
 *       client-player gate and the later {@code onUpdateAngles} lerp toward the target are host/caller
 *       concerns;</li>
 *   <li>the post-solve wheel re-clamp to the body box and {@code updateBlock}/particle effects.</li>
 * </ul>
 *
 * <p>Operand types (float vs double) and the reference's float-precision {@code Vec3.normalize} are
 * preserved exactly — see {@link #refNormalize}.
 */
public final class WheelTerrainSolver {

    private WheelTerrainSolver() {}

    /**
     * One axle's sampled contact. The two wheels of an axle share a local Z; the surface normal for
     * the axle is their cross product, so the faithful unit here is the <b>pair</b>, not a single wheel
     * (a lone wheel cannot yield a normal). {@code left}/{@code right} are the wheels' collided world
     * positions <b>already made relative to the weighted-center world origin</b> (host-computed) — the
     * reference's {@code v1} (wheels[2i+0], the {@code +x} wheel) and {@code v2} (wheels[2i+1], {@code -x}).
     *
     * @param left          v1: {@code +x} wheel world pos minus the weighted-center world origin
     * @param right         v2: {@code -x} wheel world pos minus the weighted-center world origin
     * @param localZ        the axle's local Z ({@code wheel.pos.zCoord}); selects the cross-product
     *                      order and weights the axle by {@code abs(localZ / avgZ)}
     * @param leftOnGround  {@code wheels[2i+0].onGround} after the host's front/back forcing
     * @param rightOnGround {@code wheels[2i+1].onGround} after the host's front/back forcing
     */
    public record WheelContact(Vec3d left, Vec3d right, double localZ,
                               boolean leftOnGround, boolean rightOnGround) {}

    /**
     * Solver output. {@code targetPitch}/{@code targetRoll} are the reference's clamped
     * {@code WheelMng.targetPitch}/{@code targetRoll}; {@code slopeSlide} is the additive
     * {@code (rv.x/50, 0, rv.z/50)} body-motion nudge (zero when the slope gate does not fire), to be
     * added to the entity's motion by the caller.
     */
    public record TerrainTilt(float targetPitch, float targetRoll, Vec3d slopeSlide) {}

    /**
     * Port of the pure contact-to-attitude math in {@code MCH_WheelManager.move}.
     *
     * @param self     the tank body (read {@code yaw}/{@code pitch}/{@code roll} — the MCHeli rot axes)
     * @param info     for {@code onGroundPitchFactor}/{@code onGroundRollFactor} (per-tick tilt clamp)
     * @param contacts one {@link WheelContact} per axle, in the reference's wheel-array pair order
     * @param avgZ     {@code MCH_WheelManager.avgZ = maxZ - minZ}, a spawn-time wheel-config constant
     */
    public static TerrainTilt solve(EntityRef self, MCH_AircraftInfo info,
                                    List<WheelContact> contacts, double avgZ) {
        // Accumulate the body-up normal: sum of each axle's (normalized) cross product, weighted by the
        // axle's fractional lever abs(localZ/avgZ), dropping axles with no wheel on the ground.
        Vec3d rv = Vec3d.ZERO;
        for (WheelContact c : contacts) {
            Vec3d v1 = c.left();
            Vec3d v2 = c.right();
            Vec3d v = c.localZ() >= 0.0 ? v2.cross(v1) : v1.cross(v2);
            v = refNormalize(v);
            double f = Math.abs(c.localZ() / avgZ);
            if (!c.leftOnGround() && !c.rightOnGround()) {
                f = 0.0;
            }
            rv = rv.add(v.scale(f));
        }

        rv = refNormalize(rv);

        // Slope-slide nudge: on a moderate slope (normal-Y in (0.01, 0.7)) the body is pushed downhill
        // by rv.x/50, rv.z/50. Uses the UNROTATED normal (reference does this before rotateAroundY).
        Vec3d slopeSlide = (rv.y() > 0.01 && rv.y() < 0.7)
            ? new Vec3d(rv.x() / 50.0, 0.0, rv.z() / 50.0)
            : Vec3d.ZERO;

        // Rotate the normal into the body's yaw frame, then read pitch/roll off it. rotateAroundY uses
        // the MC LUT trig (bit-identical to the reference's Vec3.rotateAroundY(MathHelper.cos/sin)).
        Vec3d rvRot = rv.rotateAroundY(self.yaw() * Math.PI / 180.0);

        float pitch = (float) (90.0 - Math.atan2(rvRot.y(), rvRot.z()) * 180.0 / Math.PI);
        float roll = -((float) (90.0 - Math.atan2(rvRot.y(), rvRot.x()) * 180.0 / Math.PI));

        // Per-tick clamp: pitch/roll may only move +/- onGround*Factor from the body's current attitude.
        float ogpf = info.onGroundPitchFactor;
        if (pitch - self.pitch() > ogpf) {
            pitch = self.pitch() + ogpf;
        }
        if (pitch - self.pitch() < -ogpf) {
            pitch = self.pitch() - ogpf;
        }

        float ogrf = info.onGroundRollFactor;
        if (roll - self.roll() > ogrf) {
            roll = self.roll() + ogrf;
        }
        if (roll - self.roll() < -ogrf) {
            roll = self.roll() - ogrf;
        }

        return new TerrainTilt(pitch, roll, slopeSlide);
    }

    /**
     * Replicates {@code net.minecraft.util.Vec3.normalize}: length via {@code MathHelper.sqrt_double}
     * — a <b>float-precision</b> sqrt — with a {@code 1.0E-4} zero threshold. Deliberately NOT
     * {@link Vec3d#normalize()} (which uses a double sqrt and a {@code 1e-8} threshold), so the
     * accumulated normal is bit-identical to the 1.7.10 original.
     */
    private static Vec3d refNormalize(Vec3d v) {
        double len = (double) (float) Math.sqrt(v.x() * v.x() + v.y() * v.y() + v.z() * v.z());
        return len < 1.0E-4D ? Vec3d.ZERO : new Vec3d(v.x() / len, v.y() / len, v.z() / len);
    }
}
