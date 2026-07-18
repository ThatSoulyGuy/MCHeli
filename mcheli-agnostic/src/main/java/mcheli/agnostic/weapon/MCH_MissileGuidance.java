package mcheli.agnostic.weapon;

/**
 * Pure missile-guidance vector math — the port of {@code MCH_EntityBaseBullet.guidanceToTarget} (the 6:1 pursuit
 * blend) and the AT direct hard-snap ({@code MCH_EntityATMissile.onUpdateMotion} guidanceType 0). Operates on plain
 * {@code double} triples so it never depends on a Minecraft {@code Vec3}; the dependent {@code MchBullet} resolves the
 * target entity's live position and feeds the scalars in. ZERO Minecraft imports.
 *
 * <p><b>Fidelity:</b> the reference {@code guidanceToTarget} NEVER reads its {@code accelerationFactor} argument — it
 * is pure pursuit with a fixed turn rate (no proportional-navigation lead), so a fast crossing target can out-turn the
 * missile exactly as in the reference. {@link #guide} preserves that; only the AT-direct {@link #hardSnap} applies
 * the ramp factor {@code af}.
 */
public final class MCH_MissileGuidance {

    private MCH_MissileGuidance() {}

    /** A plain 3-vector guidance result (keeps the sim free of any Minecraft vector type). */
    public static final class Motion {
        public final double x;
        public final double y;
        public final double z;
        public Motion(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    }

    /**
     * Pure-pursuit blend toward the target (reference {@code guidanceToTarget}): {@code desired = unit(target-pos) *
     * speed}; {@code newMotion = (motion*6 + desired)/7}. {@code speed} is the missile's cruise speed (the reference
     * {@code this.acceleration}). Used by AA homing and the AT top-attack phases.
     */
    public static Motion guide(double mx, double my, double mz, double px, double py, double pz,
                               double tx, double ty, double tz, double speed) {
        double dx = tx - px, dy = ty - py, dz = tz - pz;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1.0e-6) {
            return new Motion(mx, my, mz);
        }
        double desiredX = dx * speed / d;
        double desiredY = dy * speed / d;
        double desiredZ = dz * speed / d;
        return new Motion((mx * 6.0 + desiredX) / 7.0, (my * 6.0 + desiredY) / 7.0, (mz * 6.0 + desiredZ) / 7.0);
    }

    /**
     * AT direct hard-snap (reference guidanceType 0): {@code motion = unit(target-pos) * speed * af} — an instant
     * heading change toward the target, scaled by the ramp factor {@code af} (0.5 during the initial rigidity boost,
     * 1.0 afterwards).
     */
    public static Motion hardSnap(double px, double py, double pz, double tx, double ty, double tz,
                                  double speed, float af) {
        double dx = tx - px, dy = ty - py, dz = tz - pz;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1.0e-6) {
            return new Motion(0.0, 0.0, 0.0);
        }
        return new Motion(dx * speed / d * af, dy * speed / d * af, dz * speed / d * af);
    }
}
