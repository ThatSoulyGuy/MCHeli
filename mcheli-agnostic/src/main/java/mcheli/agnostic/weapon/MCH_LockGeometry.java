package mcheli.agnostic.weapon;

/**
 * Pure lock-on geometry — the math half of the reference {@code MCH_WeaponGuidanceSystem} (the entity queries, LOS
 * raytrace, and on-ground/in-water tests live in the dependent client tracker). All angles are in degrees and all
 * positions in world coordinates. ZERO Minecraft imports, so it stays in the agnostic sim and is unit-testable.
 */
public final class MCH_LockGeometry {

    private MCH_LockGeometry() {}

    /** Reference {@code MCH_Lib.getRotate360}: wrap an angle into {@code [0, 360)}. */
    public static double getRotate360(double r) {
        r %= 360.0;
        return r >= 0.0 ? r : r + 360.0;
    }

    /**
     * Port of {@code MCH_WeaponGuidanceSystem.inLockRange}: is {@code target} inside the observer's FOV cone of
     * half-width {@code lockAngle} degrees (in BOTH yaw and pitch)? The observer sits at ({@code ox},{@code oy},
     * {@code oz}) looking along {@code yaw}/{@code pitch}; {@code targetY} must already be the aim point
     * ({@code posY + height/2}). Faithful to the reference's {@code -90} yaw offset and rotate360 wrapping.
     */
    public static boolean inLockCone(double ox, double oy, double oz, float yaw, float pitch,
                                     double tx, double targetY, double tz, float lockAngle) {
        double dx = tx - ox;
        double dy = targetY - oy;
        double dz = tz - oz;
        float entityYaw = (float) getRotate360(yaw);
        float targetYaw = (float) getRotate360(Math.atan2(dz, dx) * 180.0 / Math.PI);
        float diffYaw = (float) getRotate360(targetYaw - entityYaw - 90.0F);
        double dxz = Math.sqrt(dx * dx + dz * dz);
        float targetPitch = -((float) (Math.atan2(dy, dxz) * 180.0 / Math.PI));
        float diffPitch = targetPitch - pitch;
        return (diffYaw < lockAngle || diffYaw > 360.0F - lockAngle) && Math.abs(diffPitch) < lockAngle;
    }

    // ---- stealth scalers (reference lock() lines 142-144 + getLockCountMax) ----
    // A stealthier target (entityStealth 0..1) shrinks the effective lock RANGE and CONE and inflates the lock TIME.
    // With entityStealth 0 (the port default until aircraft stealth is ported) these are identities: range=lockRange,
    // angle=lockAngle, countMax=lockCountMax.

    /** Effective lock range = {@code lockRange * (1 - entityStealth)}. */
    public static double effectiveRange(double lockRange, float entityStealth) {
        return lockRange * (1.0F - entityStealth);
    }

    /** Effective cone half-width = {@code lockAngle * ((1-entityStealth)/2 + 0.5)}. */
    public static float effectiveAngle(float lockAngle, float entityStealth) {
        float s = 1.0F - entityStealth;
        return lockAngle * (s / 2.0F + 0.5F);
    }

    /** Effective lock time (ticks) = {@code lockCountMax * (1 + entityStealth)}. */
    public static int effectiveCountMax(int lockCountMax, float entityStealth) {
        return (int) (lockCountMax + lockCountMax * entityStealth);
    }
}
