package mcheli.agnostic.weapon;

/**
 * Pure math for the MCHeli explosion, distilled from {@code mcheli.MCH_Explosion.doExplosionA} so the dependent
 * explosion and the headless self-tests agree. The reference reimplements vanilla's explosion with two load-bearing
 * differences preserved here:
 *
 * <ul>
 *   <li><b>Two radii.</b> Block destruction uses the weapon's {@code explosionBlock} strength; <b>entity</b> damage
 *       uses a radius of {@code 2 * explosionPower} (the {@code explosionSize *= 2} doubling at {@code MCH_Explosion}
 *       :124). Using a single vanilla radius would halve reach/damage — so the dependent layer runs its own entity
 *       loop with {@link #entityRadius}.</li>
 *   <li><b>Custom falloff.</b> Damage = {@code floor((t²+t)/2 · 8 · radius + 1)} where {@code t = (1 - dist/radius) ·
 *       seenPercent} — steeper and radius-scaled vs vanilla ({@code MCH_Explosion:149}).</li>
 * </ul>
 */
public final class MCH_ExplosionMath {
    private MCH_ExplosionMath() {}

    /** Weaker, flatter knockback than vanilla (reference {@code MCH_Explosion:171-175}). */
    public static final float KNOCKBACK_HORIZONTAL = 0.4F;
    public static final float KNOCKBACK_VERTICAL = 0.1F;

    /** Entity-damage radius = {@code 2 * explosionPower} blocks (the {@code explosionSize *= 2} doubling). */
    public static float entityRadius(int explosionPower) {
        return 2.0F * explosionPower;
    }

    /** The impact scalar {@code t = (1 - dist/radius) · seenPercent} (0 at the edge, up to {@code seenPercent} at the
     *  centre). {@code seenPercent} is the line-of-sight-unobstructed fraction (vanilla {@code getSeenPercent}). */
    public static float impactFactor(double dist, float radius, float seenPercent) {
        return (float) ((1.0 - dist / radius) * seenPercent);
    }

    /** Explosion entity damage {@code floor((t²+t)/2 · 8 · radius + 1)} (before the per-victim damage factor). */
    public static int explosionDamage(float t, float radius) {
        return (int) ((t * t + t) / 2.0F * 8.0F * radius + 1.0F);
    }

    /** Seconds of fire imparted to a victim: {@code (1 - dist/radius) · countSetFireEntity} (FAE uses count 15). */
    public static float fireSeconds(double dist, float radius, int countSetFireEntity) {
        return (float) ((1.0 - dist / radius) * countSetFireEntity);
    }

    /** Underwater block-resistance multiplier {@code rand·0.2 + 0.2} (blocks ~3-5× easier to blow up in water). */
    public static float waterResistanceMul(float rand) {
        return rand * 0.2F + 0.2F;
    }
}
