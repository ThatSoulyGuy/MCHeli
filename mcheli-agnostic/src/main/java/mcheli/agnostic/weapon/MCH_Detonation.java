package mcheli.agnostic.weapon;

/**
 * Which blast a round makes when it dies, distilled from {@code MCH_EntityBaseBullet} so the dependent projectile and
 * the tests agree. The reference has two entry points with different rules:
 *
 * <ul>
 *   <li>{@link #onImpact} ({@code onImpact}:771-821) for a round that STRUCK something: a direct entity hit, a block
 *       hit, or the tile hit the airburst and proximity fuses fabricate. FAE and the in-water variant are chosen here.</li>
 *   <li>{@link #onTimeout} ({@code onUpdateTimeout}:553-563) for a round whose {@code delayFuse} or {@code timeFuse}
 *       ran out in flight. It never makes an FAE blast, and a submerged round with {@code ExplosionInWater = 0} makes
 *       no blast at all instead of a full-power air burst.</li>
 * </ul>
 *
 * <p>A negative {@code Explosion} is the reference's "bang only" setting: it plays the explosion sound and nothing else.
 */
public final class MCH_Detonation {
    private MCH_Detonation() {}

    /** The blast variant: {@link #NORMAL} = {@code newExplosion}, {@link #WATER} = {@code newExplosionInWater},
     *  {@link #FAE} = {@code newFAExplosion}, {@link #SOUND} = {@code playExplosionSound}, {@link #NONE} = silent. */
    public enum Kind { NONE, SOUND, NORMAL, WATER, FAE }

    /** Where the blast is centred: the hit point ({@code m.hitVec}), the round itself ({@code posX/Y/Z}), or the
     *  struck block's integer coordinates ({@code m.blockX/Y/Z}, the reference's in-water block-hit quirk). */
    public enum At { HIT, SELF, BLOCK }

    /** A chosen blast: the variant, its entity power, its block power and where it goes off. */
    public record Blast(Kind kind, int power, int sizeBlock, At at) {
        static final Blast NONE = new Blast(Kind.NONE, 0, 0, At.SELF);
        static final Blast SOUND = new Blast(Kind.SOUND, 0, 0, At.SELF);
    }

    /**
     * Reference {@code onImpact}, non-piercing branch (the {@code damageFactor} argument is always 1 there).
     *
     * @param power         {@code explosionPower} (weapon {@code Explosion})
     * @param powerInWater  {@code explosionPowerInWater} (weapon {@code ExplosionInWater})
     * @param explosionBlock the weapon's {@code ExplosionBlock}
     * @param isFAE         the weapon's {@code FAE} flag
     * @param entityHit     the impact struck an entity
     * @param roundInWater  the round itself is in water
     * @param blockInWater  the struck block, or one of its six neighbours, is water ({@code MCH_Lib.isBlockInWater});
     *                      ignored for an entity hit
     */
    public static Blast onImpact(int power, int powerInWater, int explosionBlock, boolean isFAE, boolean entityHit,
                                 boolean roundInWater, boolean blockInWater) {
        if (powerInWater == 0) {
            if (isFAE) {
                return new Blast(Kind.FAE, power, explosionBlock, At.SELF);
            }
            return airBlast(power, explosionBlock, At.HIT);
        }
        if (entityHit) {
            // The reference makes this blast without the power > 0 check the other branches have.
            return roundInWater
                ? new Blast(Kind.WATER, powerInWater, powerInWater, At.HIT)
                : new Blast(Kind.NORMAL, power, explosionBlock, At.HIT);
        }
        if (roundInWater || blockInWater) {
            return new Blast(Kind.WATER, powerInWater, powerInWater, At.BLOCK);
        }
        return airBlast(power, explosionBlock, At.HIT);
    }

    /** Reference {@code onUpdateTimeout}: a fuse ran out. In water only {@code ExplosionInWater > 0} explodes. */
    public static Blast onTimeout(int power, int powerInWater, int explosionBlock, boolean inWater) {
        if (inWater) {
            return powerInWater > 0 ? new Blast(Kind.WATER, powerInWater, powerInWater, At.SELF) : Blast.NONE;
        }
        return airBlast(power, explosionBlock, At.SELF);
    }

    private static Blast airBlast(int power, int explosionBlock, At at) {
        if (power > 0) {
            return new Blast(Kind.NORMAL, power, explosionBlock, at);
        }
        return power < 0 ? Blast.SOUND : Blast.NONE;
    }
}
