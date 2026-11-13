package mcheli.agnostic.vehicle;

import mcheli.agnostic.math.MchMath;

/**
 * The wreck rules of {@code MCH_EntityAircraft}: the despawn countdown and the default (heli) tumble seeds of
 * {@code destroyAircraft} (:605-610), the heli yaw spin ({@code MCH_EntityHeli}:770-772), the one-shot crash-detonation
 * gate {@code isExploded} (:966-967) with its ground-slam trigger (:1524), and the fuel-scaled blast of
 * {@code explosionByCrash} (:2032-2043). Static and pure: the dependent hull feeds it plain values, the RNG draws stay
 * there in the reference order, and the arithmetic is unit-tested. The per-type tumble overrides (plane, tank, ground
 * vehicle) stay on their entities.
 */
public final class VehicleWreck {
    private VehicleWreck() {}

    /** Wreck lifetime in ticks (reference {@code setDespawnCount(500)}, about 25 s); the wreck is discarded after it. */
    public static final int DESPAWN_TICKS = 500;

    /**
     * True after a wreck has already crash-detonated (reference {@code isExploded}, :966-967): destroyed, and the
     * damage counted since the destruction is above a tenth of the max HP plus one (int division). A crash detonation
     * sets that counter to the max HP, so it trips once and stays tripped.
     */
    public static boolean isExploded(boolean destroyed, int damageSinceDestroyed, int maxHp) {
        return destroyed && damageSinceDestroyed > maxHp / 10 + 1;
    }

    /**
     * The ground slam that crash-detonates a falling wreck, once (reference :1524): not yet exploded, airborne before
     * this tick's move, on the ground after it, and falling faster than 0.2 a tick before the move.
     */
    public static boolean crashSlam(boolean exploded, boolean prevOnGround, boolean onGround, double prevMotionY) {
        return !exploded && !prevOnGround && onGround && prevMotionY < -0.2;
    }

    /**
     * The crash blast {@code {power, blockPower}} (reference {@code explosionByCrash}, :2032-2043): the config
     * {@code MaxFuel / 400} clamped to {@code [1, 15]}, or 2.0 with no config (not the 0-fuel floor of 1); the power is
     * that rounded, the block power half of it rounded, or 1 below 2.
     *
     * @param hasInfo whether the vehicle has a parsed config
     * @param maxFuel the config {@code MaxFuel} (read only when {@code hasInfo})
     */
    public static int[] crashBlast(boolean hasInfo, int maxFuel) {
        float exp = hasInfo ? MchMath.clamp(maxFuel / 400.0F, 1.0F, 15.0F) : 2.0F;
        int power = Math.round(exp);
        int block = Math.round(exp >= 2.0F ? exp * 0.5F : 1.0F);
        return new int[] {power, block};
    }

    /**
     * The default (heli) wreck tumble seeds {@code {yaw, pitch, roll}} from two uniform draws in [0, 1), drawn in
     * that order by the caller (reference {@code destroyAircraft}:608-610): pitch {@code r1 - 0.5}, roll
     * {@code (r2 - 0.5) * 0.5}, yaw 0.
     */
    public static float[] heliSeed(float r1, float r2) {
        return new float[] {0.0F, r1 - 0.5F, (r2 - 0.5F) * 0.5F};
    }

    /** One tick of the heli wreck's yaw spin seed: +0.3 a tick up to 15 (reference {@code MCH_EntityHeli}:770-772). */
    public static float nextYawSeed(float yawSeed) {
        return yawSeed < 15.0F ? yawSeed + 0.3F : yawSeed;
    }
}
