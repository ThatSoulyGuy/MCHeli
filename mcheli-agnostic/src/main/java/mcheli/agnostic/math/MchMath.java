package mcheli.agnostic.math;

/**
 * Pure math / angle helpers relocated from the reference's {@code MCH_Lib} (which was
 * platform-bound). {@link #sin}/{@link #cos} replicate Minecraft's fast lookup table — identical in
 * 1.7.10 {@code MathHelper} and 1.21.1 {@code Mth} — so coerced physics/aim stays numerically
 * identical to both the original and the port target.
 *
 * <p>Not carried over: the ARGB unpackers (unused in the reference too); {@code smooth}/{@code smoothRot}
 * (renderer partial-tick interpolation, done natively with {@code Mth.lerp}/{@code rotLerp}); and the HUD-only
 * {@code rotatePoints}/{@code getAzimuthStr8}/{@code getRotateDiff}, which the agnostic {@code MCH_Hud} computes
 * inline (packed {@code double[]} radar blips, its own compass letters, a camera-yaw delta from {@code HudState}).
 */
public final class MchMath {
    private MchMath() {}

    // Minecraft's fast trig lookup table (bit-identical to MathHelper.SIN_TABLE / Mth.SIN).
    private static final float[] SIN_TABLE = new float[65536];
    static {
        for (int i = 0; i < 65536; i++) {
            SIN_TABLE[i] = (float) Math.sin(i * Math.PI * 2.0 / 65536.0);
        }
    }
    public static float sin(float rad) { return SIN_TABLE[(int) (rad * 10430.378F) & 65535]; }
    public static float cos(float rad) { return SIN_TABLE[(int) (rad * 10430.378F + 16384.0F) & 65535]; }

    /** Normalize a degree angle to [0, 360). */
    public static double getRotate360(double r) {
        r %= 360.0;
        return r >= 0.0 ? r : r + 360.0;
    }

    /** Wrap a degree angle to [-180, 180). Agnostic replacement for {@code MathHelper.wrapAngleTo180_float}. */
    public static float wrapAngleTo180(float value) {
        value %= 360.0F;
        if (value >= 180.0F) {
            value -= 360.0F;
        }
        if (value < -180.0F) {
            value += 360.0F;
        }
        return value;
    }

    /** Unsigned angle (degrees) between two XZ vectors — reference {@code MCH_Lib.getPosAngle} (the search-light beam
     *  test in {@code AbstractMchVehicle.searchLightValue}; the target-marker test is not ported). */
    public static float getPosAngle(double tx, double tz, double cx, double cz) {
        double lenA = Math.sqrt(tx * tx + tz * tz);
        double lenB = Math.sqrt(cx * cx + cz * cz);
        double cos = (tx * cx + tz * cz) / (lenA * lenB);
        return (float) (Math.acos(cos) * 180.0 / Math.PI);
    }

    /** Clamp helpers (the reference misnamed these "RNG"). */
    public static float  clamp(float a, float min, float max)   { return a < min ? min : (a > max ? max : a); }
    public static double clamp(double a, double min, double max) { return a < min ? min : (a > max ? max : a); }

    public static int round(double d) { return (int) (d + 0.5); }
}
