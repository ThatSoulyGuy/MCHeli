package mcheli.agnostic.vehicle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * {@link VehicleWreck} against a private oracle: verbatim copies of the dependent wreck code it replaces
 * (AbstractMchVehicle {@code isExploded} baseline line 4459, the crash gate of {@code tick} 2531, {@code seedWreckTumble}
 * 4663-4667, the yaw step of {@code applyWreckTumble} 4675, and {@code explosionByCrash} 4691-4699), with the entity
 * reads turned into parameters, the RNG draws passed in, and {@code Mth.clamp(float,float,float)} inlined as its
 * NeoForge 1.21.1 body. Compared bit-exactly on seeded random inputs and on hand-traced edges.
 */
class VehicleWreckTest {

    /** The baseline bodies, verbatim except for the parameters and the inlined Mth. */
    private static final class Oracle {
        float rotDestroyedYaw;
        float rotDestroyedPitch;
        float rotDestroyedRoll;

        /** Baseline :4459 ({@code isDestroyed()}, {@code getMaxHp()} as parameters). */
        static boolean isExploded(boolean isDestroyed, int damageSinceDestroyed, int maxHp) {
            return isDestroyed && damageSinceDestroyed > maxHp / 10 + 1;
        }

        /** Baseline :2531 ({@code isExploded()}, {@code onGround()} as parameters). */
        static boolean crashGate(boolean isExploded, boolean prevOnGround, boolean onGround, double prevMotionY) {
            return !isExploded && !prevOnGround && onGround && prevMotionY < -0.2;
        }

        /** Baseline :4663-4667 (the two {@code random.nextFloat()} draws as parameters, in draw order). */
        void seedWreckTumble(float draw1, float draw2) {
            this.rotDestroyedPitch = draw1 - 0.5F;
            this.rotDestroyedRoll = (draw2 - 0.5F) * 0.5F;
            this.rotDestroyedYaw = 0.0F;
        }

        /** Baseline :4675. */
        void yawStep() {
            if (this.rotDestroyedYaw < 15.0F) this.rotDestroyedYaw += 0.3F;
        }

        /** Baseline :4693-4697 ({@code info != null}, {@code info.maxFuel} as parameters): {power, block}. */
        static int[] explosionByCrash(boolean infoPresent, int maxFuel) {
            float exp = infoPresent ? mthClamp(maxFuel / 400.0F, 1.0F, 15.0F) : 2.0F;
            int power = Math.round(exp);
            int block = Math.round(exp >= 2.0F ? exp * 0.5F : 1.0F);
            return new int[] {power, block};
        }

        /** net.minecraft.util.Mth.clamp(float,float,float), NeoForge 21.1.235. */
        private static float mthClamp(float p_14037_, float p_14038_, float p_14039_) {
            return p_14037_ < p_14038_ ? p_14038_ : Math.min(p_14037_, p_14039_);
        }
    }

    private static void assertBits(float expected, float actual, String what) {
        assertEquals(Float.floatToIntBits(expected), Float.floatToIntBits(actual), what + ": " + expected + " vs " + actual);
    }

    private static int randomFuel(Random rnd) {
        switch (rnd.nextInt(5)) {
            case 0: return rnd.nextInt();                 // anything, negatives included
            case 1: return rnd.nextInt(8000);             // the bundled range
            case 2: return 400 * rnd.nextInt(20) + rnd.nextInt(3) - 1; // around the clamp/rounding steps
            case 3: return rnd.nextBoolean() ? Integer.MAX_VALUE : Integer.MIN_VALUE;
            default: return 200 * rnd.nextInt(40) + rnd.nextInt(3) - 1; // around the .5 rounding edges
        }
    }

    @Test
    void crashBlastMatchesTheOracle() {
        for (long seed : new long[] {1L, 42L, 20261111L, -7L, 0xC0FFEEL}) {
            Random rnd = new Random(seed);
            for (int step = 0; step < 20_000; step++) {
                boolean info = rnd.nextInt(8) != 0;
                int maxFuel = randomFuel(rnd);
                assertArrayEquals(Oracle.explosionByCrash(info, maxFuel), VehicleWreck.crashBlast(info, maxFuel),
                    "seed " + seed + " step " + step + " info=" + info + " maxFuel=" + maxFuel);
            }
        }
        for (int maxFuel = -1000; maxFuel <= 7000; maxFuel++) {
            assertArrayEquals(Oracle.explosionByCrash(true, maxFuel), VehicleWreck.crashBlast(true, maxFuel), "maxFuel=" + maxFuel);
        }
    }

    @Test
    void isExplodedAndTheCrashGateMatchTheOracle() {
        for (long seed : new long[] {3L, 99L, 20261111L, -11L, 0xBEEFL}) {
            Random rnd = new Random(seed);
            for (int step = 0; step < 20_000; step++) {
                boolean destroyed = rnd.nextBoolean();
                int maxHp = rnd.nextInt(4) == 0 ? rnd.nextInt() : rnd.nextInt(2000);
                int dsd = rnd.nextInt(3) == 0 ? rnd.nextInt() : maxHp / 10 + rnd.nextInt(5) - 1;
                assertEquals(Oracle.isExploded(destroyed, dsd, maxHp), VehicleWreck.isExploded(destroyed, dsd, maxHp),
                    "seed " + seed + " step " + step);
                boolean exploded = rnd.nextBoolean();
                boolean prevOnGround = rnd.nextBoolean();
                boolean onGround = rnd.nextBoolean();
                double prevMotionY = rnd.nextInt(4) == 0 ? -0.2 + (rnd.nextInt(3) - 1) * Math.ulp(0.2) : rnd.nextDouble() * 4.0 - 3.0;
                assertEquals(Oracle.crashGate(exploded, prevOnGround, onGround, prevMotionY),
                    VehicleWreck.crashSlam(exploded, prevOnGround, onGround, prevMotionY), "seed " + seed + " step " + step);
            }
        }
    }

    @Test
    void heliSeedAndYawSpinMatchTheOracle() {
        for (long seed : new long[] {5L, 77L, 20261111L, -13L, 0xFACEL}) {
            Random rnd = new Random(seed);
            Oracle o = new Oracle();
            float yawSeed = 0.0F;
            for (int step = 0; step < 20_000; step++) {
                if (rnd.nextInt(60) == 0) {
                    float r1 = rnd.nextFloat();
                    float r2 = rnd.nextFloat();
                    o.seedWreckTumble(r1, r2);
                    float[] s = VehicleWreck.heliSeed(r1, r2);
                    assertBits(o.rotDestroyedYaw, s[0], "yaw seed");
                    assertBits(o.rotDestroyedPitch, s[1], "pitch seed");
                    assertBits(o.rotDestroyedRoll, s[2], "roll seed");
                    yawSeed = s[0];
                } else if (rnd.nextInt(500) == 0) {
                    float any = rnd.nextFloat() * 40.0F - 10.0F;  // a subclass setRotDestroyedYaw
                    o.rotDestroyedYaw = any;
                    yawSeed = any;
                }
                o.yawStep();
                yawSeed = VehicleWreck.nextYawSeed(yawSeed);
                assertBits(o.rotDestroyedYaw, yawSeed, "seed " + seed + " step " + step + " yaw spin");
            }
        }
    }

    @Test
    void handTracedEdges() {
        // explosionByCrash (baseline :4691-4699): no config gives power 2, block 1 (2 >= 2 -> round(1.0)).
        assertArrayEquals(new int[] {2, 1}, VehicleWreck.crashBlast(false, 123456));
        // MaxFuel 0 clamps up to 1: power 1, block 1 (below 2 -> the block floor 1).
        assertArrayEquals(new int[] {1, 1}, VehicleWreck.crashBlast(true, 0));
        // MaxFuel 6000 -> 15 (at the cap): power 15, block round(7.5) = 8.
        assertArrayEquals(new int[] {15, 8}, VehicleWreck.crashBlast(true, 6000));
        // The ah-64 (MaxFuel 1200) -> 3: power 3, block round(1.5) = 2.
        assertArrayEquals(new int[] {3, 2}, VehicleWreck.crashBlast(true, 1200));
        // Just under 2 (799/400 = 1.9975): power 2, block 1 (the 1.0 floor branch).
        assertArrayEquals(new int[] {2, 1}, VehicleWreck.crashBlast(true, 799));

        // isExploded (baseline :4459): int division, strictly above maxHp/10 + 1.
        assertFalse(VehicleWreck.isExploded(true, 0, 100));
        assertFalse(VehicleWreck.isExploded(true, 11, 100));
        assertTrue(VehicleWreck.isExploded(true, 12, 100));
        assertTrue(VehicleWreck.isExploded(true, 100, 100));   // the crash detonation sets it to maxHp
        assertFalse(VehicleWreck.isExploded(false, 100, 100)); // never for an intact vehicle
        assertTrue(VehicleWreck.isExploded(true, 2, 9));       // 9/10 = 0, so above 1

        // The ground-slam gate (baseline :2531).
        assertTrue(VehicleWreck.crashSlam(false, false, true, -0.5));
        assertFalse(VehicleWreck.crashSlam(true, false, true, -0.5));   // only once
        assertFalse(VehicleWreck.crashSlam(false, true, true, -0.5));   // already on the ground
        assertFalse(VehicleWreck.crashSlam(false, false, false, -0.5)); // still airborne
        assertFalse(VehicleWreck.crashSlam(false, false, true, -0.2));  // strictly faster than 0.2

        // The heli seed (baseline :4664-4666) and the yaw spin (:4675): +0.3 per tick while below 15.
        float[] s = VehicleWreck.heliSeed(0.75F, 0.25F);
        assertBits(0.0F, s[0], "yaw");
        assertBits(0.25F, s[1], "pitch");
        assertBits(-0.125F, s[2], "roll");
        float y = 0.0F;
        int steps = 0;
        while (y < 15.0F) {
            y = VehicleWreck.nextYawSeed(y);
            steps++;
        }
        assertEquals(50, steps); // the float sum of fifty 0.3F steps is 15.000007, so it stops there
        assertBits(15.000007F, y, "the yaw seed it holds");
        assertBits(y, VehicleWreck.nextYawSeed(y), "held once at or above 15");
        assertEquals(500, VehicleWreck.DESPAWN_TICKS);
    }
}
