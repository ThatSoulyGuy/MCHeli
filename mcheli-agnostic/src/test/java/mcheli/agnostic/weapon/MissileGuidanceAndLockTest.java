package mcheli.agnostic.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pure-math tests for the guided-missile helpers — the lock FOV cone / stealth scalers ({@link MCH_LockGeometry}) and
 * the pursuit-blend / hard-snap guidance ({@link MCH_MissileGuidance}). No Minecraft required.
 */
class MissileGuidanceAndLockTest {

    // ---- MCH_LockGeometry.inLockCone: observer at origin looking +Z (yaw 0), 5° half-cone ----

    @Test
    void targetStraightAheadIsInCone() {
        // +Z is straight ahead at yaw 0; a target 10 blocks ahead, same height, is dead-centre.
        assertTrue(MCH_LockGeometry.inLockCone(0, 0, 0, 0.0F, 0.0F, 0.0, 0.0, 10.0, 5.0F));
    }

    @Test
    void targetSlightlyOffAxisIsInCone() {
        // 3° off the +Z axis in yaw -> inside a 5° cone.
        double ang = Math.toRadians(3.0);
        double tx = Math.sin(ang) * 10.0, tz = Math.cos(ang) * 10.0;
        assertTrue(MCH_LockGeometry.inLockCone(0, 0, 0, 0.0F, 0.0F, tx, 0.0, tz, 5.0F));
    }

    @Test
    void targetBeyondConeIsRejected() {
        // 10° off the axis -> outside a 5° cone.
        double ang = Math.toRadians(10.0);
        double tx = Math.sin(ang) * 10.0, tz = Math.cos(ang) * 10.0;
        assertFalse(MCH_LockGeometry.inLockCone(0, 0, 0, 0.0F, 0.0F, tx, 0.0, tz, 5.0F));
    }

    @Test
    void targetBehindIsRejected() {
        assertFalse(MCH_LockGeometry.inLockCone(0, 0, 0, 0.0F, 0.0F, 0.0, 0.0, -10.0, 5.0F));
    }

    @Test
    void targetAboveBeyondPitchConeIsRejected() {
        // Straight ahead in yaw but 45° up in pitch -> outside a 5° cone (pitch axis).
        assertFalse(MCH_LockGeometry.inLockCone(0, 0, 0, 0.0F, 0.0F, 0.0, 10.0, 10.0, 5.0F));
    }

    // ---- stealth scalers (entityStealth 0 == identity) ----

    @Test
    void stealthScalersAreIdentityAtZero() {
        assertEquals(200.0, MCH_LockGeometry.effectiveRange(200.0, 0.0F), 1.0e-9);
        assertEquals(5.0F, MCH_LockGeometry.effectiveAngle(5.0F, 0.0F), 1.0e-6F);
        assertEquals(30, MCH_LockGeometry.effectiveCountMax(30, 0.0F));
    }

    @Test
    void stealthScalersShrinkRangeAndConeAndInflateTime() {
        // entityStealth 1.0: range -> 0, cone half-width -> lockAngle*0.5, lock time -> doubled.
        assertEquals(0.0, MCH_LockGeometry.effectiveRange(200.0, 1.0F), 1.0e-9);
        assertEquals(2.5F, MCH_LockGeometry.effectiveAngle(5.0F, 1.0F), 1.0e-6F);
        assertEquals(60, MCH_LockGeometry.effectiveCountMax(30, 1.0F));
    }

    // ---- MCH_MissileGuidance.guide: pure-pursuit 6:1 blend ----

    @Test
    void guideKeepsAMatchedHeadingSteady() {
        // Already flying +Z at cruise speed 2 toward a target dead ahead -> motion unchanged.
        MCH_MissileGuidance.Motion m = MCH_MissileGuidance.guide(0, 0, 2, 0, 0, 0, 0, 0, 100, 2.0);
        assertEquals(0.0, m.x, 1.0e-9);
        assertEquals(0.0, m.y, 1.0e-9);
        assertEquals(2.0, m.z, 1.0e-9);
    }

    @Test
    void guideTurnsSlowlyTowardTarget() {
        // Flying +X but the target is +Z: the blend nudges toward +Z but keeps most of the current heading (6:1).
        MCH_MissileGuidance.Motion m = MCH_MissileGuidance.guide(2, 0, 0, 0, 0, 0, 0, 0, 100, 2.0);
        assertEquals(12.0 / 7.0, m.x, 1.0e-9); // (2*6 + 0)/7
        assertEquals(2.0 / 7.0, m.z, 1.0e-9);  // (0*6 + 2)/7
        assertTrue(m.x > m.z, "should still be mostly heading the old way after one tick");
    }

    @Test
    void guideClosesMonotonicallyOnAStationaryTarget() {
        // Iterate guide + integrate: the missile should get strictly closer to a fixed target every tick.
        double px = 0, py = 0, pz = 0, mx = 3, my = 0, mz = 0; // launched +X
        double tx = 0, ty = 0, tz = 60;                        // target off to +Z
        double prev = dist(px, py, pz, tx, ty, tz);
        boolean everClose = false;
        for (int i = 0; i < 40; i++) {
            MCH_MissileGuidance.Motion m = MCH_MissileGuidance.guide(mx, my, mz, px, py, pz, tx, ty, tz, 3.0);
            mx = m.x; my = m.y; mz = m.z;
            px += mx; py += my; pz += mz;
            double d = dist(px, py, pz, tx, ty, tz);
            if (d < 3.0) { everClose = true; break; }
            prev = d;
        }
        assertTrue(everClose, "pursuit guidance should home in on a stationary target");
    }

    @Test
    void hardSnapPointsStraightAtTargetScaledByAf() {
        // AT direct: motion becomes the unit vector to the target * speed * af (instant heading change).
        MCH_MissileGuidance.Motion full = MCH_MissileGuidance.hardSnap(0, 0, 0, 0, 0, 10, 2.0, 1.0F);
        assertEquals(0.0, full.x, 1.0e-9);
        assertEquals(2.0, full.z, 1.0e-9);
        MCH_MissileGuidance.Motion half = MCH_MissileGuidance.hardSnap(0, 0, 0, 0, 0, 10, 2.0, 0.5F);
        assertEquals(1.0, half.z, 1.0e-9); // af 0.5 halves the boost
    }

    private static double dist(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx, dy = ay - by, dz = az - bz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
