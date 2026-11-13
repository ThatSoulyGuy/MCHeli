package mcheli.agnostic.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.tank.MCH_TankInfo;
import org.junit.jupiter.api.Test;

/**
 * {@link RunningGear} against a private oracle: a verbatim copy of the dependent client tick it replaces
 * (AbstractMchVehicle {@code updateWheelTrackPhase}, baseline lines 5142-5192, the part after the gatling barrel) and
 * its helpers {@code halfTrackWidth}, {@code wrap360} and {@code wrap01} (5198-5227), with the entity reads
 * ({@code getX()}, {@code getZ()}, {@code getYRot()}, {@code weaponHostInfo()}) turned into parameters and
 * {@code Mth.wrapDegrees(float)} / {@code Mth.clamp(float,float,float)} inlined as their NeoForge 1.21.1 bodies.
 * Compared bit-exactly on seeded random motion over several configs, and on hand-traced edges.
 */
class RunningGearTest {

    /** Baseline AbstractMchVehicle:5142-5192 and 5198-5227, verbatim except for the parameters and the inlined Mth. */
    private static final class Oracle {
        private float wheelSpin, prevWheelSpin;
        private float wheelSteer, prevWheelSteer;
        private final float[] trackScroll = new float[2], prevTrackScroll = new float[2];
        private final float[] rollerSpin = new float[2], prevRollerSpin = new float[2];
        private double lastTrackX, lastTrackZ;
        private float lastTrackYaw;
        private boolean trackInit;
        private static final float STEER_GAIN = 8.0F;

        void updateWheelTrackPhase(MCH_AircraftInfo info, double getX, double getZ, float getYRot) {
            if (info == null) {
                return;
            }
            boolean runningGear = !(info.partWheel.isEmpty() && info.partCrawlerTrack.isEmpty() && info.partTrackRoller.isEmpty());
            this.prevWheelSteer = this.wheelSteer;
            if (runningGear) {
                this.prevWheelSpin = this.wheelSpin;
                this.prevTrackScroll[0] = this.trackScroll[0];
                this.prevTrackScroll[1] = this.trackScroll[1];
                this.prevRollerSpin[0] = this.rollerSpin[0];
                this.prevRollerSpin[1] = this.rollerSpin[1];
            }
            if (!this.trackInit) {
                this.lastTrackX = getX;
                this.lastTrackZ = getZ;
                this.lastTrackYaw = getYRot;
                this.trackInit = true;
            }
            double dx = getX - this.lastTrackX;
            double dz = getZ - this.lastTrackZ;
            this.lastTrackX = getX;
            this.lastTrackZ = getZ;
            double yr = Math.toRadians(getYRot);
            double fwd = -dx * Math.sin(yr) + dz * Math.cos(yr);
            double yawRate = Math.toRadians(mthWrapDegrees(getYRot - this.lastTrackYaw));
            this.lastTrackYaw = getYRot;

            float steerTarget = mthClamp((float) (-yawRate * STEER_GAIN), -1.0F, 1.0F);
            this.wheelSteer += (steerTarget - this.wheelSteer) * 0.3F;
            if (!runningGear) {
                return;
            }
            this.wheelSpin = wrap360(this.wheelSpin + (float) (fwd * 220.0));

            float w = halfTrackWidth(info);
            float linkLen = info.partCrawlerTrack.isEmpty() ? 0.37F : Math.max(0.05F, info.partCrawlerTrack.get(0).len);
            for (int side = 0; side < 2; side++) {
                float sideX = side == 1 ? w : -w;
                float sideSpeed = (float) (fwd + yawRate * sideX);
                this.rollerSpin[side] = wrap360(this.rollerSpin[side] + sideSpeed * 220.0F);
                this.trackScroll[side] = wrap01(this.trackScroll[side] - sideSpeed / linkLen);
            }
        }

        static float halfTrackWidth(MCH_AircraftInfo info) {
            if (!info.partCrawlerTrack.isEmpty()) {
                float z = Math.abs(info.partCrawlerTrack.get(0).z);
                if (z > 0.01F) {
                    return z;
                }
            }
            if (!info.partTrackRoller.isEmpty()) {
                float x = Math.abs((float) info.partTrackRoller.get(0).pos.x());
                if (x > 0.01F) {
                    return x;
                }
            }
            if (!info.partWheel.isEmpty()) {
                float x = Math.abs((float) info.partWheel.get(0).pos.x());
                if (x > 0.01F) {
                    return x;
                }
            }
            return 1.0F;
        }

        private static float wrap360(float a) {
            a %= 360.0F;
            return a < 0.0F ? a + 360.0F : a;
        }
        private static float wrap01(float a) {
            a %= 1.0F;
            return a < 0.0F ? a + 1.0F : a;
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

        /** net.minecraft.util.Mth.clamp(float,float,float), NeoForge 21.1.235. */
        private static float mthClamp(float p_14037_, float p_14038_, float p_14039_) {
            return p_14037_ < p_14038_ ? p_14038_ : Math.min(p_14037_, p_14039_);
        }
    }

    private static void tickNew(RunningGear g, MCH_AircraftInfo info, double x, double z, float yaw) {
        if (info == null) {
            return; // the dependent caller's gate
        }
        g.tick(x, z, yaw, RunningGear.runningGear(info), RunningGear.halfTrackWidth(info), RunningGear.linkLength(info));
    }

    private static void assertSame(Oracle o, RunningGear g, String at) {
        assertBits(o.wheelSpin, g.wheelSpin(), "wheelSpin " + at);
        assertBits(o.prevWheelSpin, g.prevWheelSpin(), "prevWheelSpin " + at);
        assertBits(o.wheelSteer, g.wheelSteer(), "wheelSteer " + at);
        assertBits(o.prevWheelSteer, g.prevWheelSteer(), "prevWheelSteer " + at);
        for (int s = 0; s < 2; s++) {
            assertBits(o.trackScroll[s], g.trackScroll(s), "trackScroll[" + s + "] " + at);
            assertBits(o.prevTrackScroll[s], g.prevTrackScroll(s), "prevTrackScroll[" + s + "] " + at);
            assertBits(o.rollerSpin[s], g.rollerSpin(s), "rollerSpin[" + s + "] " + at);
            assertBits(o.prevRollerSpin[s], g.prevRollerSpin(s), "prevRollerSpin[" + s + "] " + at);
        }
    }

    private static void assertBits(float expected, float actual, String what) {
        assertEquals(Float.floatToIntBits(expected), Float.floatToIntBits(actual), what + ": " + expected + " vs " + actual);
    }

    // ---- config fixtures ----

    private static MCH_TankInfo bare() {
        return new MCH_TankInfo("rg_test");
    }

    private static MCH_AircraftInfo.CrawlerTrack track(float z, float len) {
        MCH_AircraftInfo.CrawlerTrack t = new MCH_AircraftInfo.CrawlerTrack("crawler_track0");
        t.z = z;
        t.len = len;
        return t;
    }

    private static MCH_AircraftInfo tank(float trackZ, float len) {
        MCH_TankInfo i = bare();
        i.partCrawlerTrack.add(track(trackZ, len));
        i.partCrawlerTrack.add(track(-trackZ, len));
        i.partTrackRoller.add(new MCH_AircraftInfo.TrackRoller(1.4F, 0.3F, 0.0F, "track_roller0"));
        return i;
    }

    /** KV-2 style: the crawler tracks sit at Z = 0.00, the offset lives in the rollers. */
    private static MCH_AircraftInfo kv2(float rollerX, float wheelX) {
        MCH_TankInfo i = bare();
        i.partCrawlerTrack.add(track(0.0F, 0.42F));
        i.partTrackRoller.add(new MCH_AircraftInfo.TrackRoller(rollerX, 0.3F, 0.0F, "track_roller0"));
        i.partWheel.add(new MCH_AircraftInfo.PartWheel(wheelX, 0.2F, 1.0F, 1.0F, 0.0F, 0.0F, 30.0F, "wheel0"));
        return i;
    }

    private static MCH_AircraftInfo car(float wheelX) {
        MCH_TankInfo i = bare();
        i.partWheel.add(new MCH_AircraftInfo.PartWheel(wheelX, 0.2F, 1.0F, 1.0F, 0.0F, 0.0F, 30.0F, "wheel0"));
        i.partWheel.add(new MCH_AircraftInfo.PartWheel(-wheelX, 0.2F, 1.0F, 1.0F, 0.0F, 0.0F, 30.0F, "wheel1"));
        return i;
    }

    private static List<MCH_AircraftInfo> configs() {
        List<MCH_AircraftInfo> l = new ArrayList<>();
        l.add(tank(1.2F, 0.4F));
        l.add(tank(-1.6F, 0.01F));   // a negative Z (abs) and a link shorter than the 0.05 floor
        l.add(kv2(1.5F, 1.1F));      // crawler z = 0 -> the rollers
        l.add(kv2(0.0F, 1.1F));      // ... -> the wheels
        l.add(kv2(0.005F, 0.0F));    // ... -> the 1.0 default
        l.add(car(0.9F));            // wheels only: link length 0.37
        l.add(bare());               // no running gear: only the steer runs
        return l;
    }

    @Test
    void matchesTheOracleOnSeededRandomMotion() {
        int cfg = 0;
        for (MCH_AircraftInfo info : configs()) {
            for (long seed : new long[] {1L, 42L, 20261111L, -7L}) {
                Random rnd = new Random(seed * 31 + cfg);
                Oracle o = new Oracle();
                RunningGear g = new RunningGear();
                double x = rnd.nextDouble() * 2000.0 - 1000.0;
                double z = rnd.nextDouble() * 2000.0 - 1000.0;
                float yaw = rnd.nextFloat() * 720.0F - 360.0F;
                for (int step = 0; step < 12_000; step++) {
                    int mode = rnd.nextInt(8);
                    if (mode == 0) {
                        // parked
                    } else if (mode == 1) {
                        yaw += rnd.nextFloat() * 20.0F - 10.0F; // a pivot turn in place
                    } else if (mode == 2) {
                        yaw += rnd.nextFloat() * 400.0F - 200.0F; // a teleport-sized yaw jump across the 180 wrap
                    } else if (mode == 3) {
                        x += rnd.nextGaussian() * 30.0; // a teleport-sized jump
                        z += rnd.nextGaussian() * 30.0;
                    } else {
                        // driving: forward or reverse along the hull, with some yaw rate and some side slip
                        double sp = rnd.nextGaussian() * 0.5;
                        double yr = Math.toRadians(yaw);
                        x += -Math.sin(yr) * sp + rnd.nextGaussian() * 0.02;
                        z += Math.cos(yr) * sp + rnd.nextGaussian() * 0.02;
                        yaw += (float) (rnd.nextGaussian() * 3.0);
                    }
                    o.updateWheelTrackPhase(info, x, z, yaw);
                    tickNew(g, info, x, z, yaw);
                    assertSame(o, g, "cfg " + cfg + " seed " + seed + " step " + step);
                }
            }
            cfg++;
        }
    }

    @Test
    void theStaticsMatchTheOracleHelpers() {
        for (MCH_AircraftInfo info : configs()) {
            assertBits(Oracle.halfTrackWidth(info), RunningGear.halfTrackWidth(info), "halfTrackWidth");
            float link = info.partCrawlerTrack.isEmpty() ? 0.37F : Math.max(0.05F, info.partCrawlerTrack.get(0).len);
            assertBits(link, RunningGear.linkLength(info), "linkLength");
        }
        // Baseline :5198-5218: KV-2 crawler z = 0 falls through to the rollers, then the wheels, then 1.0.
        assertBits(1.2F, RunningGear.halfTrackWidth(tank(1.2F, 0.4F)), "a crawler track Z");
        assertBits(1.6F, RunningGear.halfTrackWidth(tank(-1.6F, 0.4F)), "|Z|");
        assertBits(1.5F, RunningGear.halfTrackWidth(kv2(1.5F, 1.1F)), "the rollers");
        assertBits(1.1F, RunningGear.halfTrackWidth(kv2(0.0F, 1.1F)), "the wheels");
        assertBits(1.0F, RunningGear.halfTrackWidth(kv2(0.005F, 0.0F)), "the default");
        // Baseline :5182: the link floor and the no-crawler default.
        assertBits(0.05F, RunningGear.linkLength(tank(1.2F, 0.01F)), "the 0.05 floor");
        assertBits(0.37F, RunningGear.linkLength(car(0.9F)), "no crawler track");
        assertEquals(false, RunningGear.runningGear(bare()));
        assertEquals(true, RunningGear.runningGear(car(0.9F)));
    }

    @Test
    void theFirstTickSeedsWithoutMoving() {
        RunningGear g = new RunningGear();
        MCH_AircraftInfo info = tank(1.2F, 0.4F);
        // Baseline :5155-5160: the first tick seeds lastTrack* from the current pose, so dx = dz = yawRate = 0.
        tickNew(g, info, 500.0, -300.0, 135.0F);
        assertBits(0.0F, g.wheelSpin(), "no spin on the seed tick");
        assertBits(0.0F, g.wheelSteer(), "no steer on the seed tick");
        assertBits(0.0F, g.trackScroll(0), "no scroll on the seed tick");
        assertBits(0.0F, g.rollerSpin(1), "no roller spin on the seed tick");
    }

    @Test
    void aPivotTurnRunsTheTracksOppositeAndReverseSpinsBackward() {
        RunningGear g = new RunningGear();
        MCH_AircraftInfo info = tank(1.2F, 0.4F);
        tickNew(g, info, 0.0, 0.0, 0.0F);
        tickNew(g, info, 0.0, 0.0, 5.0F); // a right pivot (yaw increases) in place
        // Baseline :5184-5191: on a right turn the LEFT (+X, side 1) track runs forward, the right one backward.
        assertTrue(g.rollerSpin(1) > 0.0F && g.rollerSpin(1) < 180.0F, "left rollers forward: " + g.rollerSpin(1));
        assertTrue(g.rollerSpin(0) > 180.0F, "right rollers backward (wrapped): " + g.rollerSpin(0));
        assertTrue(g.wheelSteer() < 0.0F, "a right turn steers negative: " + g.wheelSteer());
        assertBits(0.0F, g.wheelSpin(), "no hull travel on a pivot");
        // Reverse: the hull faces +Z at yaw 0; moving -Z spins the wheels backward through the 360 wrap.
        RunningGear r = new RunningGear();
        tickNew(r, info, 0.0, 0.0, 0.0F);
        tickNew(r, info, 0.0, -0.1, 0.0F);
        assertBits(Oracle.wrap360((float) (-0.1 * 220.0)), r.wheelSpin(), "reverse wraps below 0");
        assertTrue(r.trackScroll(0) >= 0.0F && r.trackScroll(0) < 1.0F, "scroll stays in [0,1)");
        // Forward: the belt scroll goes negative and wraps into [0,1) (a negative wrap01).
        RunningGear f = new RunningGear();
        tickNew(f, info, 0.0, 0.0, 0.0F);
        tickNew(f, info, 0.0, 0.1, 0.0F);
        assertBits(Oracle.wrap01(-0.1F / 0.4F), f.trackScroll(0), "a negative wrap01");
        assertTrue(f.trackScroll(1) > 0.5F && f.trackScroll(1) < 1.0F, "wrapped: " + f.trackScroll(1));
    }

    @Test
    void withoutRunningGearOnlyTheSteerRuns() {
        RunningGear g = new RunningGear();
        MCH_AircraftInfo info = bare();
        tickNew(g, info, 0.0, 0.0, 0.0F);
        for (int i = 1; i <= 20; i++) {
            tickNew(g, info, 0.0, i * 0.3, -i * 4.0F); // a left turn while driving forward
        }
        assertTrue(g.wheelSteer() > 0.0F, "the steer runs for every config: " + g.wheelSteer());
        assertTrue(g.prevWheelSteer() > 0.0F, "prevWheelSteer is copied before the running-gear gate");
        assertBits(0.0F, g.wheelSpin(), "spin frozen");
        assertBits(0.0F, g.prevWheelSpin(), "prev spin frozen");
        assertBits(0.0F, g.trackScroll(0), "tracks frozen");
        assertBits(0.0F, g.prevRollerSpin(1), "prev rollers frozen");
    }
}
