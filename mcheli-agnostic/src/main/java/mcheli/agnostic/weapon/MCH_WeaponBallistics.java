package mcheli.agnostic.weapon;

/**
 * Pure ballistics helpers for the projectile launch — the faithful arithmetic distilled from
 * {@code MCH_EntityBaseBullet}'s constructor and {@code onSetWeasponInfo} so the dependent projectile entity and the
 * headless self-tests agree bit-for-bit.
 *
 * <p><b>The load-bearing detail (see the reference):</b> the projectile's per-tick velocity magnitude is clamped to
 * {@link #MAX_SPEED} (3.9 blocks/tick), and "fast" bullets/rockets instead advance by scaling the position step with
 * an {@code accelerationFactor} ({@code acceleration / 4}). So a weapon with {@code Acceleration = 8} flies with a
 * motion vector of length 3.9 but steps {@code motion * 2.0} each tick — the collision segment is
 * {@code motion * accelerationFactor}, never {@code motion} alone. Getting this exactly right is what keeps fast rounds
 * from tunnelling through targets or falling short.
 */
public final class MCH_WeaponBallistics {
    private MCH_WeaponBallistics() {}

    /** Reference velocity clamp: {@code MCH_EntityBaseBullet} caps the launch speed at 3.9 blocks/tick. */
    public static final float MAX_SPEED = 3.9F;

    /** Launch speed = {@code min(acceleration, 3.9)} (the reference clamps in the bullet ctor). */
    public static float initialSpeed(float acceleration) {
        return acceleration < MAX_SPEED ? acceleration : MAX_SPEED;
    }

    /**
     * The position-step multiplier. Only {@link #isBulletOrRocket bullets & rockets} with {@code acceleration > 4}
     * get {@code acceleration / 4}; everything else (missiles, and slow guns) steps at 1.0. Mirrors
     * {@code MCH_EntityBaseBullet.onSetWeasponInfo}'s conditional exactly.
     */
    public static float accelerationFactor(float acceleration, boolean bulletOrRocket) {
        return (bulletOrRocket && acceleration > 4.0F) ? acceleration / 4.0F : 1.0F;
    }

    /**
     * The two projectile classes that get an {@code accelerationFactor} in the reference: {@code MCH_EntityBullet}
     * (MachineGun1/2) and {@code MCH_EntityRocket} (Rocket). NOTE: {@code MkRocket} is deliberately excluded —
     * {@code MCH_EntityMarkerRocket} extends {@code MCH_EntityBaseBullet} directly and never sets an
     * {@code accelerationFactor}, so it keeps 1.0 even at high acceleration (matching {@code onSetWeasponInfo}).
     */
    public static boolean isBulletOrRocket(String type) {
        if (type == null) {
            return false;
        }
        String t = type.toLowerCase();
        return t.equals("machinegun1") || t.equals("machinegun2") || t.equals("rocket");
    }

    /**
     * Whether a weapon type leaves a smoke TRAIL — the reference emits it only from the rocket/missile projectile
     * subclasses ({@code MCH_EntityRocket} & co), never from plain gun rounds ({@code MCH_EntityBullet}). This is the
     * faithful type gate; the trail's <em>appearance</em> (particle name + size) is still read from config.
     */
    public static boolean isTrailingType(String type) {
        if (type == null) {
            return false;
        }
        switch (type.toLowerCase()) {
            case "rocket":
            case "mkrocket":
            case "aamissile":
            case "asmissile":
            case "atmissile":
            case "tvmissile":
            case "torpedo":
                return true;
            default:
                return false;
        }
    }

    /**
     * Per-type trail-size multiplier (× {@code smokeSize}), matching the reference's per-subclass {@code spawnParticle}
     * size argument ({@code 5.0*smokeSize*0.5} rocket = 2.5, {@code 7.0*..*0.5} AA = 3.5, {@code 10.0*..*0.5} AS = 5.0).
     */
    public static float trailSizeMultiplier(String type) {
        if (type == null) {
            return 2.5F;
        }
        switch (type.toLowerCase()) {
            case "aamissile":
                return 3.5F;
            case "asmissile":
                return 5.0F;
            default:
                return 2.5F;
        }
    }

    /**
     * The shared default bullet model NAME for a weapon type when the config sets no {@code ModelBullet=} — the
     * analogue of {@code MCH_DefaultBulletModels} / each bullet entity's {@code getDefaultBulletModel()}. The name
     * doubles as the texture name ({@code models/bullets/<name>.obj} + {@code textures/bullets/<name>.png}).
     */
    public static String defaultBulletModel(String type) {
        if (type == null) {
            return "bullet";
        }
        switch (type.toLowerCase()) {
            case "rocket":
            case "mkrocket":
                return "rocket";
            case "aamissile":
                return "aamissile";
            case "asmissile":
                return "asmissile";
            case "atmissile":
            case "tvmissile":
                return "atmissile";
            case "bomb":
                return "bomb";
            case "torpedo":
                return "torpedo";
            default:
                return "bullet";
        }
    }
}
