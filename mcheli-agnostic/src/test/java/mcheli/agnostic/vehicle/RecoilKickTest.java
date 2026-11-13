package mcheli.agnostic.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * {@link RecoilKick} against a private oracle: a verbatim copy of the dependent client tick and envelope it replaces
 * (AbstractMchVehicle {@code advanceRecoil}, baseline lines 1931-1948, and {@code recoilEnvelope}, 1970-1976) with the
 * synced reads ({@code DATA_RECOIL_SEQ/VALUE/YAW}) turned into parameters and {@code Mth.lerp(float,float,float)}
 * inlined as its NeoForge 1.21.1 body. Compared bit-exactly on seeded random shot sequences and partial ticks, and on
 * hand-traced edges.
 */
class RecoilKickTest {

    /** Baseline AbstractMchVehicle:1931-1948 and 1965-1976, verbatim except for the parameters and the inlined Mth. */
    private static final class Oracle {
        private int lastRecoilSeq = -1;
        private int clientRecoilCount;
        private int prevClientRecoilCount;
        private float clientRecoilValue;
        private float clientRecoilYaw;
        private static final float RECOIL_GAIN = 3.0F;

        void advanceRecoil(int dataRecoilSeq, float dataRecoilValue, float dataRecoilYaw) {
            int seq = dataRecoilSeq;
            if (seq != this.lastRecoilSeq) {
                boolean firstObserve = this.lastRecoilSeq < 0;
                this.lastRecoilSeq = seq;
                if (seq != 0 && !firstObserve) {
                    this.clientRecoilCount = 30;
                    this.clientRecoilValue = dataRecoilValue;
                    this.clientRecoilYaw = dataRecoilYaw;
                }
            }
            this.prevClientRecoilCount = this.clientRecoilCount;
            if (this.clientRecoilCount > 0) {
                this.clientRecoilCount--;
            }
        }

        float recoilEnvelope(float partialTick) {
            float count = mthLerp(partialTick, this.prevClientRecoilCount, this.clientRecoilCount);
            if (count < 12.0F) {
                return 0.0F;
            }
            return (count - 12.0F) / 18.0F * this.clientRecoilValue * RECOIL_GAIN;
        }

        /** net.minecraft.util.Mth.lerp(float,float,float), NeoForge 21.1.235. */
        private static float mthLerp(float p_14180_, float p_14181_, float p_14182_) {
            return p_14181_ + p_14180_ * (p_14182_ - p_14181_);
        }
    }

    private static void assertBits(float expected, float actual, String what) {
        assertEquals(Float.floatToIntBits(expected), Float.floatToIntBits(actual), what + ": " + expected + " vs " + actual);
    }

    @Test
    void matchesTheOracleOnSeededRandomShots() {
        for (long seed : new long[] {1L, 42L, 20261111L, -7L, 0xC0FFEEL}) {
            Random rnd = new Random(seed);
            Oracle o = new Oracle();
            RecoilKick k = new RecoilKick();
            int seq = rnd.nextBoolean() ? 0 : rnd.nextInt(50); // sometimes the vehicle already fired before tracking
            float value = 0.0F;
            float yaw = 0.0F;
            int kicks = 0;
            for (int step = 0; step < 20_000; step++) {
                int r = rnd.nextInt(40);
                if (r == 0) {
                    seq += 1 + rnd.nextInt(3); // one or more shots between two client ticks
                    value = rnd.nextFloat() * 8.0F;
                    yaw = rnd.nextFloat() * 360.0F - 180.0F;
                } else if (r == 1) {
                    value = rnd.nextFloat() * 8.0F; // a value change with no seq bump is ignored
                }
                int before = k.count();
                o.advanceRecoil(seq, value, yaw);
                k.observe(seq, value, yaw);
                String at = "seed " + seed + " step " + step;
                assertEquals(o.clientRecoilCount, k.count(), "count " + at);
                assertBits(o.clientRecoilYaw, k.yaw(), "yaw " + at);
                for (int f = 0; f < 4; f++) {
                    float pt = f == 0 ? 0.0F : (f == 3 ? 1.0F : rnd.nextFloat());
                    assertBits(o.recoilEnvelope(pt), k.envelope(pt), "envelope(" + pt + ") " + at);
                }
                if (k.count() == 29 && before != 30) {
                    kicks++;
                }
            }
            assertTrue(kicks > 100, "kicks on seed " + seed + ": " + kicks);
        }
    }

    @Test
    void noPhantomKickOnTheFirstObserve() {
        RecoilKick k = new RecoilKick();
        // Baseline :1935-1937: the first tick adopts an already-bumped seq without a kick.
        k.observe(7, 5.0F, 30.0F);
        assertEquals(0, k.count());
        assertBits(0.0F, k.envelope(0.5F), "no kick");
        assertBits(0.0F, k.yaw(), "the yaw is not adopted");
        // The next bump kicks: count 30, decremented to 29 in the same tick.
        k.observe(8, 5.0F, 30.0F);
        assertEquals(29, k.count());
        assertBits(30.0F, k.yaw(), "adopted on the kick");
        // pt 0 reads prev (30): the full kick (30-12)/18 * 5 * 3 = 15.
        assertBits((30.0F - 12.0F) / 18.0F * 5.0F * RecoilKick.RECOIL_GAIN, k.envelope(0.0F), "the full kick");
        // A seq of 0 never kicks, even after tracking started (a fresh server counter).
        RecoilKick z = new RecoilKick();
        z.observe(0, 5.0F, 0.0F);
        z.observe(0, 5.0F, 0.0F);
        assertEquals(0, z.count());
        z.observe(1, 5.0F, 0.0F);
        assertEquals(29, z.count(), "the first real shot kicks");
    }

    @Test
    void theEnvelopeGatesBelowTwelve() {
        RecoilKick k = new RecoilKick();
        k.observe(0, 0.0F, 0.0F);
        k.observe(1, 2.0F, 90.0F);
        int ticks = 0;
        while (k.count() > 12) {
            k.observe(1, 2.0F, 90.0F);
            ticks++;
        }
        // count 12, prev 13: pt 1 lands exactly on 12, still inside the window (0 because 12-12 = 0).
        assertEquals(17, ticks);
        assertBits(0.0F, k.envelope(1.0F), "zero at 12");
        assertTrue(k.envelope(0.0F) > 0.0F, "positive at 13");
        k.observe(1, 2.0F, 90.0F); // count 11, prev 12
        assertBits(0.0F, k.envelope(0.5F), "gated below 12");
    }
}
