package mcheli.agnostic.math;

import java.util.ArrayList;

/**
 * Pure math / angle / color helpers relocated from the reference's {@code MCH_Lib} (which was
 * platform-bound). {@link #sin}/{@link #cos} replicate Minecraft's fast lookup table — identical in
 * 1.7.10 {@code MathHelper} and 1.21.1 {@code Mth} — so coerced physics/aim stays numerically
 * identical to both the original and the port target.
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

    public static final String[] AZIMUTH_8 = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    public static final int AZIMUTH_8_ANG = 360 / AZIMUTH_8.length;

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

    /** Signed shortest angular difference (degrees) from base to target. */
    public static double getRotateDiff(double base, double target) {
        base = getRotate360(base);
        target = getRotate360(target);
        if (target - base < -180.0) {
            target += 360.0;
        } else if (target - base > 180.0) {
            base += 360.0;
        }
        return target - base;
    }

    public static float getPosAngle(double tx, double tz, double cx, double cz) {
        double lenA = Math.sqrt(tx * tx + tz * tz);
        double lenB = Math.sqrt(cx * cx + cz * cz);
        double cos = (tx * cx + tz * cz) / (lenA * lenB);
        return (float) (Math.acos(cos) * 180.0 / Math.PI);
    }

    /** Clamp helpers (the reference misnamed these "RNG"). */
    public static float  clamp(float a, float min, float max)   { return a < min ? min : (a > max ? max : a); }
    public static double clamp(double a, double min, double max) { return a < min ? min : (a > max ? max : a); }

    public static float smooth(float rot, float prevRot, float tick) { return prevRot + (rot - prevRot) * tick; }

    public static float smoothRot(float rot, float prevRot, float tick) {
        if (rot - prevRot < -180.0F) {
            prevRot -= 360.0F;
        } else if (prevRot - rot < -180.0F) {
            prevRot += 360.0F;
        }
        return prevRot + (rot - prevRot) * tick;
    }

    public static int round(double d) { return (int) (d + 0.5); }

    public static String getAzimuthStr8(int dir) {
        dir %= 360;
        if (dir < 0) {
            dir += 360;
        }
        dir /= AZIMUTH_8_ANG;
        return AZIMUTH_8[dir];
    }

    // ARGB channel unpack (preserving the reference's signed >>24 for alpha).
    public static float getAlpha(int argb) { return (argb >> 24) / 255.0F; }
    public static float getRed(int argb)   { return (argb >> 16 & 0xFF) / 255.0F; }
    public static float getGreen(int argb) { return (argb >> 8 & 0xFF) / 255.0F; }
    public static float getBlue(int argb)  { return (argb & 0xFF) / 255.0F; }

    public static void rotatePoints(double[] points, float degrees) {
        float r = degrees / 180.0F * (float) Math.PI;
        float c = cos(r), s = sin(r);
        for (int i = 0; i + 1 < points.length; i += 2) {
            double x = points[i], y = points[i + 1];
            points[i] = x * c - y * s;
            points[i + 1] = x * s + y * c;
        }
    }

    public static void rotatePoints(ArrayList<MCH_Vector2> points, float degrees) {
        float r = degrees / 180.0F * (float) Math.PI;
        float c = cos(r), s = sin(r);
        for (int i = 0; i + 1 < points.size(); i += 2) {
            double x = points.get(i).x;
            double y = points.get(i).y;
            points.get(i).x = x * c - y * s;
            points.get(i).y = x * s + y * c;
        }
    }
}
