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

    /** The first block a world ray-trace meets on a segment (the dependent layer clips the world). */
    @FunctionalInterface
    public interface BlockRay {
        /** @return {blockX, blockZ} of the first block hit between the two points, or null when the segment is clear */
        int[] firstBlock(double x0, double y0, double z0, double x1, double y1, double z1);
    }

    /**
     * Where a lobbed shell lands — the port of {@code MCH_WeaponBase.getLandInDistance} (the HUD {@code MORTAR_DIST}
     * readout of {@code DisplayMortarDistance} weapons). The shell's arc is marched in at most 50 steps from 3 blocks
     * above the vehicle; a weapon faster than the first step's 12-tick rise has its steps (and gravity, squared, with
     * the reference's 0.95) stretched so the 50 steps still reach the ground. Returns the horizontal distance from the
     * vehicle to the first block the arc meets (block corner, as the reference), or to where it falls back below the
     * vehicle's height; -1 when the weapon does not fall ({@code gravity >= 0}), is aimed level or down, or the arc
     * does not land within the march.
     *
     * @param gravity      the weapon's config gravity (negative for a lobbed shell)
     * @param acceleration the weapon's config acceleration (the reference reads the un-clamped {@code acceleration})
     * @param dirX         the aim direction (any length)
     * @param posX         the vehicle position (the reference passes the entity position, not the muzzle)
     */
    public static double landInDistance(float gravity, float acceleration, double dirX, double dirY, double dirZ,
                                        double posX, double posY, double posZ, BlockRay ray) {
        if (gravity >= 0.0F) {
            return -1.0;
        }
        double s = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (s < 1.0e-9) {
            return -1.0;
        }
        double acc = acceleration < 4.0F ? acceleration : 4.0;
        double my = dirY * acceleration / s;
        if (my <= 0.0) {
            return -1.0;
        }
        double accFac = acceleration / acc;
        double mx = dirX * acceleration / s;
        double mz = dirZ * acceleration / s;
        double ls = my / gravity;
        double g = gravity * accFac;
        if (ls < -12.0) {
            double f = ls / -12.0;
            mx *= f;
            my *= f;
            mz *= f;
            g *= f * f * 0.95;
        }
        double spx = posX;
        double spy = posY + 3.0;
        double spz = posZ;
        for (int i = 0; i < 50; i++) {
            int[] hit = ray.firstBlock(spx, spy, spz, spx + mx, spy + my, spz + mz);
            if (hit != null) {
                double dx = hit[0] - posX;
                double dz = hit[1] - posZ;
                return Math.sqrt(dx * dx + dz * dz);
            }
            my += g;
            spx += mx;
            spy += my;
            spz += mz;
            if (spy < posY) {
                double dx = spx - posX;
                double dz = spz - posZ;
                return Math.sqrt(dx * dx + dz * dz);
            }
        }
        return -1.0;
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
