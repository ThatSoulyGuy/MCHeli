package mcheli.dependent.entity;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.physics.MCH_BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The per-part hitboxes (weak points / armored zones): the per-entity copies of the config {@code BoundingBox} list
 * posed to the live hull (reference {@code updateExtraBoundingBox}), the tight core box, and the ray queries the
 * projectiles use. The terrain pass reads the posed boxes through {@link #boxes()}. Holds only the back-reference to its
 * vehicle; writes no synced accessor.
 */
final class MchPartHitboxes {
    private final AbstractMchVehicle v;

    /** Lazily-built per-entity copy of {@code info.extraBoundingBox}, refreshed to the live pose each server tick. */
    private MCH_BoundingBox[] extraBoxes;

    MchPartHitboxes(AbstractMchVehicle v) {
        this.v = v;
    }

    /** A config change drops the copies; the next {@link #update} rebuilds them against the new config. */
    void onConfigChanged() {
        this.extraBoxes = null;
    }

    /** The posed part boxes (for the terrain crush/ram pass), or null until the first {@link #update} after a spawn or
     *  a config change. */
    MCH_BoundingBox[] boxes() {
        return this.extraBoxes;
    }

    /** Build (once) and refresh the per-entity extra hitboxes to the live pose (reference {@code updateExtraBoundingBox}). */
    void update() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (this.extraBoxes == null) {
            if (info == null || info.extraBoundingBox == null || info.extraBoundingBox.isEmpty()) {
                this.extraBoxes = new MCH_BoundingBox[0];
            } else {
                this.extraBoxes = new MCH_BoundingBox[info.extraBoundingBox.size()];
                for (int i = 0; i < this.extraBoxes.length; i++) {
                    this.extraBoxes[i] = info.extraBoundingBox.get(i).copy();
                }
            }
        }
        for (MCH_BoundingBox bb : this.extraBoxes) {
            bb.updatePosition(v.getX(), v.getY(), v.getZ(), v.getYRot(), v.getXRot(), v.getRollAngle());
        }
    }

    /** The tight core collision box — the reference entity box {@code setSize(bodyWidth, bodyHeight)} (defaults 2.0×0.7),
     *  the same size {@link AbstractMchVehicle#getDimensions} gives the entity box, built at the current position; NOT the
     *  culling box (inflated to the whole visual model) and NOT the render-only {@code entityWidth/entityHeight}. Used as
     *  the factor-1.0 hull for the ray query + the projectile hit test. */
    private AABB coreBox() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        float w = info != null && info.bodyWidth > 0.0F ? info.bodyWidth : 2.0F;
        float h = info != null && info.bodyHeight > 0.0F ? info.bodyHeight : 0.7F;
        return new AABB(v.getX() - w / 2.0, v.getY(), v.getZ() - w / 2.0, v.getX() + w / 2.0, v.getY() + h, v.getZ() + w / 2.0);
    }

    /**
     * The per-part weak-point/armor factor of the box a bullet segment strikes NEAREST its origin, else 1.0 (the plain
     * hull) — the port of {@code MCH_AircraftBoundingBox.calculateIntercept}. The FULL segment {@code [from, to]} (the
     * bullet's whole trajectory) is tested against the core box AND every extra box, nearest-to-origin wins. The caller
     * MUST pass the full ray, NOT the point where it entered the vanilla selection AABB — that would truncate the
     * segment before it reaches any interior zone, silently resolving them all to 1.0.
     */
    float boundingBoxDamageFactorAt(Vec3 from, Vec3 to) {
        if (this.extraBoxes == null || this.extraBoxes.length == 0) return 1.0F;
        float factor = 1.0F;
        double best = Double.MAX_VALUE;
        java.util.Optional<Vec3> coreHit = coreBox().clip(from, to);
        if (coreHit.isPresent()) best = from.distanceToSqr(coreHit.get());
        for (MCH_BoundingBox bb : this.extraBoxes) {
            java.util.Optional<Vec3> h = MchVehicleMath.mcBox(bb).clip(from, to);
            if (h.isPresent()) {
                double d = from.distanceToSqr(h.get());
                if (d < best) {
                    best = d;
                    factor = bb.damegeFactor;
                }
            }
        }
        return factor;
    }

    /**
     * Nearest point where the segment {@code [from, to]} enters this vehicle's core box OR any extra part box, else
     * {@code null}. The projectile hit test uses this so a round that clips only a part box PROTRUDING beyond the
     * vanilla selection AABB (wingtip / tail / hull overhang) still registers a hit — the reference feeds the extra
     * boxes into the bullet's own intercept, so protruding zones are hittable. Returns null if the segment misses all.
     */
    Vec3 clipParts(Vec3 from, Vec3 to) {
        Vec3 best = null;
        double bestSq = Double.MAX_VALUE;
        java.util.Optional<Vec3> coreHit = coreBox().clip(from, to);
        if (coreHit.isPresent()) {
            bestSq = from.distanceToSqr(coreHit.get());
            best = coreHit.get();
        }
        if (this.extraBoxes != null) {
            for (MCH_BoundingBox bb : this.extraBoxes) {
                java.util.Optional<Vec3> h = MchVehicleMath.mcBox(bb).clip(from, to);
                if (h.isPresent()) {
                    double d = from.distanceToSqr(h.get());
                    if (d < bestSq) {
                        bestSq = d;
                        best = h.get();
                    }
                }
            }
        }
        return best;
    }
}
