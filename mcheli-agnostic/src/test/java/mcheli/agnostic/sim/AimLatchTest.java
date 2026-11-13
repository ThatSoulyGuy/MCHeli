package mcheli.agnostic.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import mcheli.agnostic.math.MchMath;
import org.junit.jupiter.api.Test;

/**
 * {@link AimLatch} against a private oracle: a verbatim copy of the dependent code it replaces (AbstractMchVehicle
 * {@code updateAimLatch} and {@code updateTurretSlew}, baseline lines 867-901, and the {@code AcLastRYaw} restore,
 * baseline lines 2073-2077), with the entity reads ({@code pilot()}, {@code p.isAlive()}, {@code isDestroyed()},
 * {@code p.getYRot()}, {@code p.getXRot()}, {@code getYRot()}) turned into parameters and {@code Mth.wrapDegrees(float)}
 * inlined as its NeoForge 1.21.1 body. Compared bit-exactly on seeded random sequences and on hand-traced edges.
 */
class AimLatchTest {

    /** Baseline AbstractMchVehicle:854-901 and :2073-2077, verbatim except for the parameters and the inlined Mth. */
    private static final class Oracle {
        private float lastRiderYaw, prevLastRiderYaw;
        private float lastRiderPitch, prevLastRiderPitch;
        private boolean aimLatched;
        private static final float TURRET_SLEW_MAX = 15.0F;
        private float turretSlewYaw, prevTurretSlewYaw;
        private boolean turretSlewInit;

        /** :867-880; {@code pilotLive} is {@code p != null && p.isAlive() && !isDestroyed()}. */
        void updateAimLatch(boolean pilotLive, float pGetYRot, float pGetXRot, float getYRot) {
            if (pilotLive) {
                this.prevLastRiderYaw = this.aimLatched ? this.lastRiderYaw : pGetYRot;
                this.prevLastRiderPitch = this.aimLatched ? this.lastRiderPitch : pGetXRot;
                this.lastRiderYaw = pGetYRot;
                this.lastRiderPitch = pGetXRot;
                this.aimLatched = true;
            } else {
                this.prevLastRiderYaw = this.lastRiderYaw;   // no pilot: HOLD the last aim (reference latch)
                this.prevLastRiderPitch = this.lastRiderPitch;
            }
            updateTurretSlew(getYRot);
        }

        /** :885-901. */
        void updateTurretSlew(float getYRot) {
            float target = this.aimLatched ? this.lastRiderYaw : getYRot; // operator world look, else hull-forward
            if (!this.turretSlewInit) {
                this.turretSlewYaw = this.prevTurretSlewYaw = mthWrapDegrees(target); // snap on the first tick (no start jerk)
                this.turretSlewInit = true;
                return;
            }
            this.prevTurretSlewYaw = this.turretSlewYaw;
            float diff = mthWrapDegrees(target - this.turretSlewYaw);
            if (diff > TURRET_SLEW_MAX) {
                this.turretSlewYaw = mthWrapDegrees(this.turretSlewYaw + TURRET_SLEW_MAX);
            } else if (diff < -TURRET_SLEW_MAX) {
                this.turretSlewYaw = mthWrapDegrees(this.turretSlewYaw - TURRET_SLEW_MAX);
            } else {
                this.turretSlewYaw = mthWrapDegrees(target);
            }
        }

        /** :2073-2077, the body under {@code tag.contains("AcLastRYaw")}. */
        void readLatch(float acLastRYaw, float acLastRPitch) {
            this.lastRiderYaw = this.prevLastRiderYaw = acLastRYaw;
            this.lastRiderPitch = this.prevLastRiderPitch = acLastRPitch;
            this.aimLatched = true;
        }

        /** net.minecraft.util.Mth.wrapDegrees(float), NeoForge 21.1.235. */
        private static float mthWrapDegrees(float p_14178_) {
            float f = p_14178_ % 360.0F;
            if (f >= 180.0F) {
                f -= 360.0F;
            }
            if (f < -180.0F) {
                f += 360.0F;
            }
            return f;
        }
    }

    private static void assertSame(Oracle o, AimLatch a, String where) {
        assertEquals(o.aimLatched, a.latched(), where + " latched");
        assertEquals(o.turretSlewInit, a.slewInit(), where + " slewInit");
        assertBits(o.lastRiderYaw, a.yaw(), where + " yaw");
        assertBits(o.prevLastRiderYaw, a.prevYaw(), where + " prevYaw");
        assertBits(o.lastRiderPitch, a.pitch(), where + " pitch");
        assertBits(o.prevLastRiderPitch, a.prevPitch(), where + " prevPitch");
        assertBits(o.turretSlewYaw, a.slew(), where + " slew");
        assertBits(o.prevTurretSlewYaw, a.prevSlew(), where + " prevSlew");
    }

    private static void assertBits(float want, float got, String what) {
        assertEquals(Float.floatToIntBits(want), Float.floatToIntBits(got), what + ": want " + want + " got " + got);
    }

    /** A yaw/pitch drawn from a mix of ranges: small looks, full turns, multi-turn values and the seams. */
    private static float angle(Random r) {
        switch (r.nextInt(6)) {
            case 0: return (r.nextFloat() - 0.5F) * 360.0F;
            case 1: return (r.nextFloat() - 0.5F) * 2000.0F;
            case 2: return new float[] {180.0F, -180.0F, 540.0F, -540.0F, -0.0F, 0.0F, 360.0F, -360.0F, 179.99998F,
                -179.99998F, 15.0F, -15.0F}[r.nextInt(12)];
            case 3: return (float) (r.nextGaussian() * 30.0);
            case 4: return r.nextInt(721) - 360;
            default: return (r.nextFloat() - 0.5F) * 1.0e6F;
        }
    }

    @Test
    void seededSequencesMatchTheOracleBitExactly() {
        for (long seed = 1; seed <= 6; seed++) {
            Random r = new Random(seed * 0x9E3779B97F4A7C15L);
            Oracle o = new Oracle();
            AimLatch a = new AimLatch();
            boolean live = r.nextBoolean();
            float pilotYaw = angle(r);
            float pilotPitch = angle(r);
            float hullYaw = angle(r);
            for (int step = 0; step < 12_000; step++) {
                if (r.nextInt(40) == 0) {
                    live = !live; // pilot boards / leaves (runs of live and dead ticks)
                }
                if (r.nextInt(3) == 0) {
                    pilotYaw = r.nextInt(4) == 0 ? angle(r) : pilotYaw + (r.nextFloat() - 0.5F) * 80.0F;
                }
                if (r.nextInt(3) == 0) {
                    pilotPitch = r.nextInt(4) == 0 ? angle(r) : pilotPitch + (r.nextFloat() - 0.5F) * 20.0F;
                }
                if (r.nextInt(4) == 0) {
                    hullYaw = r.nextInt(5) == 0 ? angle(r) : hullYaw + (r.nextFloat() - 0.5F) * 10.0F;
                }
                if (r.nextInt(500) == 0) {
                    float sy = angle(r);
                    float sp = angle(r);
                    o.readLatch(sy, sp);
                    a.restore(sy, sp);
                    assertSame(o, a, "seed " + seed + " restore at " + step);
                }
                if (r.nextInt(3000) == 0) {
                    o = new Oracle(); // a fresh vehicle: unlatched, slew not initialised
                    a = new AimLatch();
                }
                o.updateAimLatch(live, pilotYaw, pilotPitch, hullYaw);
                a.update(live, pilotYaw, pilotPitch, hullYaw);
                assertSame(o, a, "seed " + seed + " step " + step);
            }
        }
    }

    @Test
    void firstLiveUpdateSetsPrevToCurrent() {
        AimLatch a = new AimLatch();
        assertFalse(a.latched());
        a.update(true, 30.0F, -10.0F, 0.0F);
        assertTrue(a.latched());
        assertBits(30.0F, a.yaw(), "yaw");
        assertBits(30.0F, a.prevYaw(), "prevYaw (no interpolation step on the first latch, baseline :870)");
        assertBits(-10.0F, a.pitch(), "pitch");
        assertBits(-10.0F, a.prevPitch(), "prevPitch (baseline :871)");
        a.update(true, 40.0F, -12.0F, 0.0F);
        assertBits(30.0F, a.prevYaw(), "prevYaw after a second live tick");
        assertBits(40.0F, a.yaw(), "yaw after a second live tick");
        assertBits(-10.0F, a.prevPitch(), "prevPitch after a second live tick");
    }

    @Test
    void noPilotHoldsTheLatch() {
        AimLatch a = new AimLatch();
        a.update(true, 30.0F, -10.0F, 0.0F);
        a.update(true, 45.0F, -20.0F, 0.0F);
        for (int i = 0; i < 5; i++) {
            a.update(false, 999.0F, 999.0F, 77.0F); // baseline :875-877: the pilot look is not read
            assertTrue(a.latched());
            assertBits(45.0F, a.yaw(), "held yaw");
            assertBits(45.0F, a.prevYaw(), "held prevYaw");
            assertBits(-20.0F, a.pitch(), "held pitch");
            assertBits(-20.0F, a.prevPitch(), "held prevPitch");
        }
        assertBits(45.0F, a.slew(), "the slew keeps its latched target, not the hull (baseline :886)");
    }

    @Test
    void slewSnapsThenStepsAcrossTheSeam() {
        AimLatch a = new AimLatch();
        a.update(true, 170.0F, 0.0F, 0.0F);
        assertTrue(a.slewInit());
        assertBits(170.0F, a.slew(), "snap (baseline :887-890)");
        assertBits(170.0F, a.prevSlew(), "snap prev");
        a.update(true, -170.0F, 0.0F, 0.0F); // wrap(-170 - 170) = +20 > 15: one capped step across +180
        assertBits(-175.0F, a.slew(), "170 toward -170 steps +15 to -175 (baseline :893-895)");
        assertBits(170.0F, a.prevSlew(), "prev slew");
        a.update(true, -170.0F, 0.0F, 0.0F);
        assertBits(-170.0F, a.slew(), "within 15: lands on the target (baseline :898-899)");
        a.update(true, 100.0F, 0.0F, 0.0F); // wrap(100 - -170) = -90: capped -15
        assertBits(175.0F, a.slew(), "-170 toward 100 steps -15 to 175 (baseline :896-897)");
        Oracle o = new Oracle();
        o.updateAimLatch(true, 170.0F, 0.0F, 0.0F);
        o.updateAimLatch(true, -170.0F, 0.0F, 0.0F);
        o.updateAimLatch(true, -170.0F, 0.0F, 0.0F);
        o.updateAimLatch(true, 100.0F, 0.0F, 0.0F);
        assertSame(o, a, "hand trace");
    }

    @Test
    void unlatchedTheSlewTargetsTheHull() {
        AimLatch a = new AimLatch();
        a.update(false, 999.0F, 999.0F, 50.0F);
        assertFalse(a.latched());
        assertBits(50.0F, a.slew(), "snap onto the hull yaw (baseline :886)");
        a.update(false, 999.0F, 999.0F, 100.0F);
        assertBits(65.0F, a.slew(), "one capped step toward the turning hull");
        a.update(false, 999.0F, 999.0F, 70.0F);
        assertBits(70.0F, a.slew(), "within 15 of the hull: lands on it");
        assertBits(0.0F, a.yaw(), "nothing latched");
    }

    @Test
    void restoreLatchesWithoutTouchingTheSlew() {
        AimLatch a = new AimLatch();
        a.restore(12.5F, -3.25F);
        assertTrue(a.latched());
        assertFalse(a.slewInit(), "restore leaves the slew uninitialised (baseline :2073-2077)");
        assertBits(12.5F, a.yaw(), "yaw");
        assertBits(12.5F, a.prevYaw(), "prevYaw");
        assertBits(-3.25F, a.pitch(), "pitch");
        assertBits(-3.25F, a.prevPitch(), "prevPitch");
        a.update(true, 50.0F, 0.0F, 0.0F);
        assertBits(50.0F, a.slew(), "first step after a restore snaps onto the live look");
        a.restore(-90.0F, 5.0F);
        assertBits(50.0F, a.slew(), "a later restore leaves the slew");
        assertBits(50.0F, a.prevSlew(), "and its prev");
        a.update(false, 0.0F, 0.0F, 0.0F);
        assertBits(35.0F, a.slew(), "then slews toward the restored yaw");
    }

    @Test
    void wrapMatchesMthAtTheSeams() {
        float[] edges = {180.0F, -180.0F, 540.0F, -540.0F, -0.0F, 0.0F, 360.0F, -360.0F, 720.0F, -720.0F, 179.99998F,
            -179.99998F, 180.00002F, -180.00002F, 1.0e7F, -1.0e7F, Float.MIN_VALUE, -Float.MIN_VALUE};
        for (float e : edges) {
            assertBits(Oracle.mthWrapDegrees(e), MchMath.wrapAngleTo180(e), "wrap " + e);
            Oracle o = new Oracle();
            AimLatch a = new AimLatch();
            for (float t : edges) {
                o.updateAimLatch(true, e, t, t);
                a.update(true, e, t, t);
                assertSame(o, a, "latch at " + e + "/" + t);
                o.updateAimLatch(true, t, e, e);
                a.update(true, t, e, e);
                assertSame(o, a, "latch at " + t + "/" + e);
            }
        }
        assertBits(180.0F - 360.0F, MchMath.wrapAngleTo180(180.0F), "+180 wraps to -180");
        assertBits(-180.0F, MchMath.wrapAngleTo180(-540.0F), "-540 wraps to -180");
        assertBits(-0.0F, MchMath.wrapAngleTo180(-0.0F), "-0.0 keeps its sign, as Mth does");
    }
}
