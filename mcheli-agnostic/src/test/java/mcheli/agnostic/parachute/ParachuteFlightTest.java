package mcheli.agnostic.parachute;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.IntPredicate;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.spi.RandomSource;
import mcheli.agnostic.spi.Role;
import mcheli.agnostic.spi.WorldView;
import mcheli.agnostic.value.AABB;
import mcheli.agnostic.value.RayHit;
import mcheli.agnostic.value.Vec3d;
import org.junit.jupiter.api.Test;

/**
 * {@link ParachuteFlight} against a private oracle: a verbatim copy of the reference {@code MCH_EntityParachute}
 * fall-distance damping (:134-136) and {@code onUpdateServer} (:213-317), with the entity reads turned into fields, the
 * rider as a stub, {@code moveEntity} as {@code pos += motion} plus the 1.7.10 fall-state update, and
 * {@code Entity.setRotation}'s {@code % 360} inlined. The one deliberate change, the riderless heading (a reference
 * bug, see {@link ParachuteFlight#nextYaw}), is a flag of the oracle, so the bit-exact traces run with it fixed and a
 * separate test shows the reference mirror. Compared bit for bit on scripted traces; the pinned numbers come from the
 * reference simulation ({@code parasim.py} in the port notes).
 */
class ParachuteFlightTest {

    /** What the reference read of {@code riddenByEntity}. */
    private static final class Rider {
        final boolean living;
        float rotationYaw;
        float moveForward;

        Rider(boolean living, float yaw, float forward) {
            this.living = living;
            this.rotationYaw = yaw;
            this.moveForward = forward;
        }
    }

    /** Reference MCH_EntityParachute :128-317 (server side), verbatim but for the noted parameters. */
    private static final class Oracle {
        final boolean fixMirroredHeading;
        double speedMultiplier = 0.07;
        int onGroundCount;
        int type;
        int ticksExisted;
        double posX, posY, posZ, prevPosX, prevPosY, prevPosZ, motionX, motionY, motionZ;
        float rotationYaw, rotationPitch, fallDistance;
        boolean onGround;
        Rider riddenByEntity;
        boolean landed;

        Oracle(boolean fixMirroredHeading) {
            this.fixMirroredHeading = fixMirroredHeading;
        }

        int getType() {
            return this.type;
        }

        boolean isOpenParachute() {
            return this.getType() != 2 || this.motionY < -0.1;
        }

        /** World.updateEntityWithOptionalForce (++ticksExisted) then onUpdate :134-161, server branch. */
        void onUpdate(boolean groundNow, boolean groundAfterMove) {
            this.ticksExisted++;
            if (this.isOpenParachute() && this.motionY > -0.3 && this.ticksExisted > 20) {
                this.fallDistance = (float) (this.fallDistance * 0.85);
            }
            this.prevPosX = this.posX;
            this.prevPosY = this.posY;
            this.prevPosZ = this.posZ;
            this.onGround = groundNow;
            this.onUpdateServer(groundAfterMove);
        }

        void onUpdateServer(boolean groundAfterMove) {
            double prevSpeed = Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
            double gravity = this.onGround ? 0.01 : 0.03;
            if (this.getType() == 2 && this.ticksExisted < 20) {
                gravity = 0.01;
            }

            this.motionY -= gravity;
            if (this.isOpenParachute()) {
                if (this.riddenByEntity != null && this.riddenByEntity.living) {
                    double mv = this.riddenByEntity.moveForward;
                    if (!this.isOpenParachute()) {
                        mv = 0.0;
                    }

                    if (mv > 0.0) {
                        double mx = -Math.sin(this.riddenByEntity.rotationYaw * (float) Math.PI / 180.0F);
                        double mz = Math.cos(this.riddenByEntity.rotationYaw * (float) Math.PI / 180.0F);
                        this.motionX = this.motionX + mx * this.speedMultiplier * 0.05;
                        this.motionZ = this.motionZ + mz * this.speedMultiplier * 0.05;
                    }
                }

                double speed = Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
                if (speed > 0.35) {
                    this.motionX *= 0.35 / speed;
                    this.motionZ *= 0.35 / speed;
                    speed = 0.35;
                }

                if (speed > prevSpeed && this.speedMultiplier < 0.35) {
                    this.speedMultiplier = this.speedMultiplier + (0.35 - this.speedMultiplier) / 35.0;
                    if (this.speedMultiplier > 0.35) {
                        this.speedMultiplier = 0.35;
                    }
                } else {
                    this.speedMultiplier = this.speedMultiplier - (this.speedMultiplier - 0.07) / 35.0;
                    if (this.speedMultiplier < 0.07) {
                        this.speedMultiplier = 0.07;
                    }
                }
            }

            if (this.onGround) {
                this.onGroundCount++;
                if (this.onGroundCount > 5) {
                    this.landed = true; // onGroundAndDead()
                    return;
                }
            }

            this.moveEntity(this.motionX, this.motionY, this.motionZ, groundAfterMove);
            if (this.getType() == 2 && this.ticksExisted < 20) {
                this.motionY *= 0.95;
            } else {
                this.motionY *= 0.9;
            }

            if (this.isOpenParachute()) {
                this.motionX *= 0.95;
                this.motionZ *= 0.95;
            } else {
                this.motionX *= 0.99;
                this.motionZ *= 0.99;
            }

            this.rotationPitch = 0.0F;
            double yaw = this.rotationYaw;
            double dx = this.prevPosX - this.posX;
            double dz = this.prevPosZ - this.posZ;
            if (dx * dx + dz * dz > 0.001) {
                yaw = (float) (Math.atan2(dx, this.fixMirroredHeading ? -dz : dz) * 180.0 / Math.PI);
            }

            double yawDiff = wrapAngleTo180_double(yaw - this.rotationYaw);
            if (yawDiff > 20.0) {
                yawDiff = 20.0;
            }

            if (yawDiff < -20.0) {
                yawDiff = -20.0;
            }

            if (this.riddenByEntity != null) {
                this.setRotation(this.riddenByEntity.rotationYaw, this.rotationPitch);
            } else {
                this.rotationYaw = (float) (this.rotationYaw + yawDiff);
                this.setRotation(this.rotationYaw, this.rotationPitch);
            }
            // :303-316 (pushing other parachutes, the dead rider) need a world; not part of a single trace.
        }

        /** moveEntity with no collision: the move, then 1.7.10 Entity.updateFallState. */
        void moveEntity(double x, double y, double z, boolean groundAfterMove) {
            this.posX += x;
            this.posY += y;
            this.posZ += z;
            this.onGround = groundAfterMove;
            if (this.onGround) {
                if (this.fallDistance > 0.0F) {
                    this.fallDistance = 0.0F;
                }
            } else if (y < 0.0) {
                this.fallDistance = (float) (this.fallDistance - y);
            }
        }

        /** 1.7.10 Entity.setRotation. */
        void setRotation(float yaw, float pitch) {
            this.rotationYaw = yaw % 360.0F;
            this.rotationPitch = pitch % 360.0F;
        }

        /** 1.7.10 MathHelper.wrapAngleTo180_double. */
        static double wrapAngleTo180_double(double v) {
            v %= 360.0;
            if (v >= 180.0) {
                v -= 360.0;
            }
            if (v < -180.0) {
                v += 360.0;
            }
            return v;
        }

        /**
         * 1.7.10 {@code receiver.applyEntityCollision(arg)}: returns the receiver's {@code addVelocity(-d0, 0, -d1)}
         * (the argument gets the negation). The reference parachute called it as {@code other.applyEntityCollision(this)}.
         */
        static double[] applyEntityCollision(double receiverX, double receiverZ, double argX, double argZ) {
            double d0 = argX - receiverX; // p_70108_1_.posX - this.posX
            double d1 = argZ - receiverZ;
            double d2 = absMax(d0, d1);
            if (d2 >= 0.009999999776482582D) {
                d2 = (double) (float) Math.sqrt(d2);
                d0 /= d2;
                d1 /= d2;
                double d3 = 1.0D / d2;
                if (d3 > 1.0D) {
                    d3 = 1.0D;
                }
                d0 *= d3;
                d1 *= d3;
                d0 *= 0.05000000074505806D;
                d1 *= 0.05000000074505806D;
                d0 *= (double) (1.0F - 0.0F);
                d1 *= (double) (1.0F - 0.0F);
                return new double[] {-d0, -d1};
            }
            return new double[] {0.0, 0.0};
        }

        static double absMax(double a, double b) {
            if (a < 0.0D) {
                a = -a;
            }
            if (b < 0.0D) {
                b = -b;
            }
            return a > b ? a : b;
        }
    }

    /** The port: ParachuteFlight driven the way MchParachute drives it, around the same move. */
    private static final class Port {
        final ParachuteFlight flight = new ParachuteFlight();
        int type;
        int age;
        double x, y, z, mx, my, mz;
        float yaw, fallDistance;
        boolean onGround;
        Rider rider;
        boolean landed;

        void tick(boolean groundNow, boolean groundAfterMove) {
            this.age++;
            this.fallDistance = ParachuteFlight.dampedFallDistance(ParachuteFlight.isOpen(this.type, this.my), this.my,
                this.age, this.fallDistance);
            double px = this.x;
            double pz = this.z;
            this.onGround = groundNow;
            ParachuteFlight.Step s = this.flight.preMove(this.type, this.age, this.onGround,
                new Vec3d(this.mx, this.my, this.mz), this.rider != null && this.rider.living,
                this.rider != null ? this.rider.moveForward : 0.0F, this.rider != null ? this.rider.rotationYaw : 0.0F);
            this.mx = s.motion().x();
            this.my = s.motion().y();
            this.mz = s.motion().z();
            if (s.landed()) {
                this.landed = true;
                return;
            }
            this.x += this.mx;
            this.y += this.my;
            this.z += this.mz;
            this.onGround = groundAfterMove;
            if (this.onGround) {
                if (this.fallDistance > 0.0F) {
                    this.fallDistance = 0.0F;
                }
            } else if (this.my < 0.0) {
                this.fallDistance = (float) (this.fallDistance - this.my); // 1.21 Entity.checkFallDamage
            }
            Vec3d m = ParachuteFlight.postMove(this.type, this.age, new Vec3d(this.mx, this.my, this.mz));
            this.mx = m.x();
            this.my = m.y();
            this.mz = m.z();
            this.yaw = ParachuteFlight.nextYaw(this.yaw, this.rider != null,
                this.rider != null ? this.rider.rotationYaw : 0.0F, px, pz, this.x, this.z);
        }
    }

    /** How a scenario's rider changes over the trace (called before each tick with the tick number). */
    private interface RiderScript {
        void before(int tick, Rider r);
    }

    private record Scenario(String name, int type, double mx0, double my0, double mz0, float yaw0, float fd0,
                            boolean hasRider, boolean living, float riderYaw, float forward, RiderScript script,
                            IntPredicate ground) {}

    private static List<Scenario> scenarios() {
        List<Scenario> out = new ArrayList<>();
        RiderScript fixed = (t, r) -> { };
        RiderScript toggled = (t, r) -> {
            r.moveForward = (t / 37) % 2 == 0 ? 1.0F : 0.0F;
            r.rotationYaw += 7.0F; // keeps turning past 360, so the setRotation % 360 is exercised
        };
        IntPredicate air = t -> false;
        IntPredicate lands = t -> t > 260;
        IntPredicate bounce = t -> (t >= 120 && t <= 122) || t > 270; // cumulative count: 3 + 3 lands
        for (int type = 0; type <= 3; type++) {
            for (double my0 : new double[] {2.0, 0.0, -1.0}) {
                String k = "type " + type + " my0 " + my0;
                out.add(new Scenario(k + " riderless", type, 0.0, my0, 0.0, 0.0F, 0.0F, false, false, 0, 0, fixed, lands));
                out.add(new Scenario(k + " riderless drift", type, 0.5, my0, -0.3, 170.0F, 3.0F, false, false, 0, 0,
                    fixed, bounce));
                out.add(new Scenario(k + " non-living rider", type, 0.1, my0, 0.2, 10.0F, 0.0F, true, false, 33.0F, 1.0F,
                    fixed, lands));
                for (float yaw : new float[] {0.0F, 90.0F, -135.0F}) {
                    out.add(new Scenario(k + " forward yaw " + yaw, type, 0.0, my0, 0.0, 0.0F, 0.0F, true, true, yaw,
                        1.0F, fixed, lands));
                }
                out.add(new Scenario(k + " forward toggled", type, 0.2, my0, 0.0, 0.0F, 0.0F, true, true, 20.0F, 1.0F,
                    toggled, bounce));
                out.add(new Scenario(k + " back only", type, 0.0, my0, 0.3, 0.0F, 0.0F, true, true, 45.0F, -1.0F, fixed,
                    air));
                out.add(new Scenario(k + " strafe only", type, 0.0, my0, 0.0, 0.0F, 0.0F, true, true, 45.0F, 0.0F, fixed,
                    air));
            }
        }
        return out;
    }

    private static Oracle oracle(Scenario s, boolean fix) {
        Oracle o = new Oracle(fix);
        o.type = s.type();
        o.motionX = s.mx0();
        o.motionY = s.my0();
        o.motionZ = s.mz0();
        o.rotationYaw = s.yaw0();
        o.fallDistance = s.fd0();
        o.riddenByEntity = s.hasRider() ? new Rider(s.living(), s.riderYaw(), s.forward()) : null;
        return o;
    }

    private static Port port(Scenario s) {
        Port p = new Port();
        p.type = s.type();
        p.mx = s.mx0();
        p.my = s.my0();
        p.mz = s.mz0();
        p.yaw = s.yaw0();
        p.fallDistance = s.fd0();
        p.rider = s.hasRider() ? new Rider(s.living(), s.riderYaw(), s.forward()) : null;
        return p;
    }

    // ---- (a) the oracle, bit for bit ----

    @Test
    void tracesMatchTheReferenceBitForBit() {
        int compared = 0;
        for (Scenario s : scenarios()) {
            Oracle o = oracle(s, true);
            Port p = port(s);
            for (int t = 1; t <= 300 && !o.landed; t++) {
                if (o.riddenByEntity != null) {
                    s.script().before(t, o.riddenByEntity);
                    s.script().before(t, p.rider);
                }
                o.onUpdate(s.ground().test(t), s.ground().test(t + 1));
                p.tick(s.ground().test(t), s.ground().test(t + 1));
                String at = s.name() + " tick " + t;
                assertEquals(o.landed, p.landed, at + " landed");
                assertEquals(o.motionX, p.mx, at + " mX");
                assertEquals(o.motionY, p.my, at + " mY");
                assertEquals(o.motionZ, p.mz, at + " mZ");
                assertEquals(o.speedMultiplier, p.flight.speedMultiplier(), at + " speedMultiplier");
                assertEquals(o.fallDistance, p.fallDistance, at + " fallDistance");
                assertEquals(o.onGroundCount, p.flight.onGroundCount(), at + " onGroundCount");
                if (!o.landed) {
                    assertEquals(o.rotationYaw, p.yaw, at + " yaw");
                    assertEquals(o.posY, p.y, at + " y");
                }
                compared++;
            }
        }
        assertTrue(compared > 20000, "compared " + compared + " ticks");
    }

    @Test
    void landingScriptsLandOnTheSixthGroundTick() {
        // "lands": ground from tick 261 -> preMoves 261..265 count 1..5, 266 lands. "bounce": 3 at 120-122, then 271+.
        Scenario lands = scenarios().get(0);
        Port p = port(lands);
        int landedAt = -1;
        for (int t = 1; t <= 300 && landedAt < 0; t++) {
            p.tick(lands.ground().test(t), lands.ground().test(t + 1));
            if (p.landed) {
                landedAt = t;
            }
        }
        assertEquals(266, landedAt);
        Scenario bounce = scenarios().get(1);
        Port b = port(bounce);
        landedAt = -1;
        for (int t = 1; t <= 300 && landedAt < 0; t++) {
            b.tick(bounce.ground().test(t), bounce.ground().test(t + 1));
            if (b.landed) {
                landedAt = t;
            }
        }
        assertEquals(273, landedAt, "the ground count is cumulative: 3 ground ticks at 120-122, then 271, 272, 273");
    }

    // ---- (b), (c) pinned numbers from the reference simulation ----

    private static Port riderless(int type, double my0) {
        Port p = new Port();
        p.type = type;
        p.my = my0;
        return p;
    }

    @Test
    void ejectionSeatLaunchedAtTwoClimbsThenOpens() {
        Port p = riderless(ParachuteFlight.TYPE_SEAT, 2.0);
        double apex = 0.0;
        int apexT = 0;
        int openT = -1;
        double y20 = 0.0;
        for (int t = 1; t <= 100; t++) {
            p.tick(false, false);
            if (t == 20) {
                y20 = p.y;
            }
            if (p.y > apex) {
                apex = p.y;
                apexT = t;
            }
            if (openT < 0 && ParachuteFlight.isOpen(p.type, p.my)) {
                openT = t;
            }
        }
        assertEquals(24.078, y20, 1e-3);
        assertEquals(26.392, apex, 1e-3);
        assertEquals(30, apexT);
        assertEquals(35, openT, "first open at the end of tick 35");
        assertEquals(-0.2698, p.my, 1e-4);
    }

    @Test
    void steadyDescentHoldsTheFallDistanceNearTwo() {
        Port p = riderless(ParachuteFlight.TYPE_HAND, 0.0);
        double prevY = 0.0;
        double step = 0.0;
        for (int t = 1; t <= 300; t++) {
            prevY = p.y;
            p.tick(false, false);
            step = p.y - prevY;
        }
        assertEquals(-0.27, p.my, 1e-5);
        assertEquals(-0.30, step, 1e-5);
        assertEquals(2.0, p.fallDistance, 1e-3);

        Port drop = riderless(ParachuteFlight.TYPE_DROP, -1.0);
        for (int t = 1; t <= 20; t++) {
            drop.tick(false, false);
        }
        assertEquals(12.41, drop.fallDistance, 0.01, "a fast drop keeps its fall distance for 20 ticks");
    }

    // ---- (d) steering ----

    @Test
    void steeringPushesOnlyWhileOpenAndOnlyForward() {
        ParachuteFlight f = new ParachuteFlight();
        ParachuteFlight.Step s = f.preMove(ParachuteFlight.TYPE_HAND, 30, false, Vec3d.ZERO, true, 1.0F, 90.0F);
        double push = 0.07 * 0.05;
        assertEquals(-push, s.motion().x(), 1e-6, "yaw 90 pushes toward -X");
        assertEquals(0.0, s.motion().z(), 1e-6);
        assertEquals(0.07 + 0.28 / 35.0, f.speedMultiplier(), 1e-12, "the multiplier ramps by (0.35 - m) / 35");

        ParachuteFlight closed = new ParachuteFlight();
        s = closed.preMove(ParachuteFlight.TYPE_SEAT, 30, false, new Vec3d(0.0, 0.5, 0.0), true, 1.0F, 90.0F);
        assertEquals(0.0, s.motion().x(), "a closed seat is not steered");
        assertEquals(ParachuteFlight.MULT_MIN, closed.speedMultiplier(), "a closed seat keeps its multiplier");

        for (float forward : new float[] {0.0F, -1.0F}) {
            ParachuteFlight g = new ParachuteFlight();
            s = g.preMove(ParachuteFlight.TYPE_DROP, 30, false, Vec3d.ZERO, true, forward, 0.0F);
            assertEquals(0.0, s.motion().x());
            assertEquals(0.0, s.motion().z(), "forward " + forward + " adds nothing");
        }

        ParachuteFlight dead = new ParachuteFlight();
        s = dead.preMove(ParachuteFlight.TYPE_DROP, 30, false, Vec3d.ZERO, false, 1.0F, 0.0F);
        assertEquals(0.0, s.motion().z(), "a rider that is not living does not steer");
    }

    @Test
    void speedAndMultiplierStayInTheirBounds() {
        for (Scenario s : scenarios()) {
            Port p = port(s);
            for (int t = 1; t <= 300 && !p.landed; t++) {
                if (p.rider != null) {
                    s.script().before(t, p.rider);
                }
                double prevMult = p.flight.speedMultiplier();
                Vec3d before = new Vec3d(p.mx, p.my, p.mz);
                p.tick(s.ground().test(t), s.ground().test(t + 1));
                double m = p.flight.speedMultiplier();
                assertTrue(m >= ParachuteFlight.MULT_MIN && m <= ParachuteFlight.MULT_MAX, s.name() + " multiplier " + m);
                if (ParachuteFlight.isOpen(p.type, before.y())) { // open at the start, so open after the gravity too
                    assertTrue(Math.sqrt(p.mx * p.mx + p.mz * p.mz) <= ParachuteFlight.SPEED_CAP + 1e-12,
                        s.name() + " tick " + t + " speed");
                }
                if (m != prevMult) {
                    boolean up = m > prevMult;
                    double expected = up ? Math.min(0.35, prevMult + (0.35 - prevMult) / 35.0)
                        : Math.max(0.07, prevMult - (prevMult - 0.07) / 35.0);
                    assertEquals(expected, m, 0.0, s.name() + " tick " + t + " ramp from " + before);
                }
            }
        }
    }

    // ---- (e) ground ----

    @Test
    void fiveGroundTicksDoNotLandTheSixthDoes() {
        ParachuteFlight f = new ParachuteFlight();
        for (int i = 1; i <= 5; i++) {
            ParachuteFlight.Step s = f.preMove(ParachuteFlight.TYPE_DROP, 40 + i, true, new Vec3d(0.0, -0.2, 0.0),
                false, 0.0F, 0.0F);
            assertFalse(s.landed(), "ground tick " + i);
            assertEquals(-0.21, s.motion().y(), 1e-12, "gravity is 0.01 on the ground");
        }
        assertTrue(f.preMove(ParachuteFlight.TYPE_DROP, 46, true, Vec3d.ZERO, false, 0.0F, 0.0F).landed());
        ParachuteFlight air = new ParachuteFlight();
        assertEquals(-0.03, air.preMove(ParachuteFlight.TYPE_DROP, 40, false, Vec3d.ZERO, false, 0, 0).motion().y(),
            1e-12, "0.03 in the air");
        assertEquals(-0.01, air.preMove(ParachuteFlight.TYPE_SEAT, 19, false, Vec3d.ZERO, false, 0, 0).motion().y(),
            1e-12, "0.01 in a seat's first 19 ticks");
        assertEquals(-0.03, air.preMove(ParachuteFlight.TYPE_SEAT, 20, false, Vec3d.ZERO, false, 0, 0).motion().y(),
            1e-12, "0.03 from its 20th");
    }

    // ---- (f) yaw ----

    @Test
    void yawFollowsTheRiderModulo360() {
        assertEquals(37.5F, ParachuteFlight.nextYaw(0.0F, true, 37.5F, 0, 0, 1, 1));
        assertEquals(5.0F, ParachuteFlight.nextYaw(0.0F, true, 725.0F, 0, 0, 0, 0), "setRotation stores yaw % 360");
        assertEquals(-90.0F, ParachuteFlight.nextYaw(170.0F, true, -450.0F, 0, 0, 0, 0));
    }

    @Test
    void riderlessYawSlewsAtMostTwentyDegreesTowardTheHeading() {
        // Moving +Z: heading 0. From 90 it turns 20 a tick.
        assertEquals(70.0F, ParachuteFlight.nextYaw(90.0F, false, 0, 0, 0, 0, 0.3));
        assertEquals(0.0F, ParachuteFlight.nextYaw(10.0F, false, 0, 0, 0, 0, 0.3));
        // Moving -X: heading 90; +X: -90; -Z: 180.
        assertEquals(90.0F, ParachuteFlight.nextYaw(85.0F, false, 0, 0, 0, -0.3, 0));
        assertEquals(-90.0F, ParachuteFlight.nextYaw(-80.0F, false, 0, 0, 0, 0.3, 0));
        assertEquals(170.0F, ParachuteFlight.nextYaw(150.0F, false, 0, 0, 0, 0, -0.3));
        // Too short a move (squared 0.001 or less, strict): no turn.
        assertEquals(90.0F, ParachuteFlight.nextYaw(90.0F, false, 0, 0, 0, 0, 0.0316));
        assertEquals(70.0F, ParachuteFlight.nextYaw(90.0F, false, 0, 0, 0, 0, 0.0317));
        // Wraps through +-180: from 170 the -Z heading is 10 away, from -150 it is 30 the other way round.
        assertEquals(180.0F, ParachuteFlight.nextYaw(170.0F, false, 0, 0, 0, 0, -0.3));
        assertEquals(-170.0F, ParachuteFlight.nextYaw(-150.0F, false, 0, 0, 0, 0, -0.3));
    }

    @Test
    void theReferenceHeadingWasMirroredAboutX() {
        // A riderless parachute drifting +Z at yaw 90: the fix turns it toward 0, the reference toward 180.
        Oracle ref = new Oracle(false);
        ref.type = ParachuteFlight.TYPE_DROP;
        ref.motionZ = 0.3;
        ref.rotationYaw = 90.0F;
        Oracle fixed = new Oracle(true);
        fixed.type = ParachuteFlight.TYPE_DROP;
        fixed.motionZ = 0.3;
        fixed.rotationYaw = 90.0F;
        ref.onUpdate(false, false);
        fixed.onUpdate(false, false);
        assertEquals(110.0F, ref.rotationYaw, "the reference turns away, toward 180");
        assertEquals(70.0F, fixed.rotationYaw, "the fix turns toward the +Z heading, 0");
        // Drifting along X the two agree.
        Oracle refX = new Oracle(false);
        refX.type = ParachuteFlight.TYPE_DROP;
        refX.motionX = -0.3;
        refX.rotationYaw = 60.0F;
        Oracle fixX = new Oracle(true);
        fixX.type = ParachuteFlight.TYPE_DROP;
        fixX.motionX = -0.3;
        fixX.rotationYaw = 60.0F;
        refX.onUpdate(false, false);
        fixX.onUpdate(false, false);
        assertEquals(refX.rotationYaw, fixX.rotationYaw);
        assertEquals(80.0F, fixX.rotationYaw);
    }

    // ---- (g) pushApart ----

    @Test
    void pushApartIsThe1710CollisionMaths() {
        Random r = new Random(1710L);
        for (int i = 0; i < 5000; i++) {
            double ax = (r.nextDouble() - 0.5) * 4.0;
            double az = (r.nextDouble() - 0.5) * 4.0;
            double bx = ax + (r.nextDouble() - 0.5) * (i % 3 == 0 ? 0.05 : 3.0);
            double bz = az + (r.nextDouble() - 0.5) * (i % 3 == 0 ? 0.05 : 3.0);
            // A (at a) ticks; the reference ran B.applyEntityCollision(A): B is the receiver, A the argument.
            double[] ref = Oracle.applyEntityCollision(bx, bz, ax, az);
            double[] port = ParachuteFlight.pushApart(bx - ax, bz - az);
            assertArrayEquals(ref, port, 0.0, "pair " + i);
            double[] back = ParachuteFlight.pushApart(ax - bx, az - bz);
            assertArrayEquals(new double[] {-port[0], -port[1]}, back, 0.0, "odd symmetry " + i);
        }
        assertArrayEquals(new double[] {0.0, 0.0}, ParachuteFlight.pushApart(0.0099, -0.0099), 0.0);
        double[] p = ParachuteFlight.pushApart(0.01, 0.0);
        assertTrue(p[0] > 0.0 && p[1] == 0.0, "0.01 on one axis pushes");
        assertEquals(0.05, ParachuteFlight.pushApart(2.0, 0.0)[0], 1e-8, "full push past 1 block (float sqrt)");
    }

    // ---- (h) boatY ----

    /** Water cells only; everything else is air. */
    private static final class WaterWorld implements WorldView {
        record Cell(int x, int y, int z) {}

        final Set<Cell> water = new HashSet<>();
        int min = -64;
        int max = 319;

        static Cell k(int x, int y, int z) {
            return new Cell(x, y, z);
        }

        WaterWorld pool(int cx, int topY, int cz, int half, int depth) {
            for (int y = 0; y < depth; y++) {
                for (int x = -half; x <= half; x++) {
                    for (int z = -half; z <= half; z++) {
                        this.water.add(k(cx + x, topY - y, cz + z));
                    }
                }
            }
            return this;
        }

        @Override public boolean isWater(int x, int y, int z) { return this.water.contains(k(x, y, z)); }
        @Override public int getBlockId(int x, int y, int z) { return isWater(x, y, z) ? 2 : 0; }
        @Override public boolean isCollidable(int x, int y, int z) { return isWater(x, y, z); }
        @Override public int minBuildHeight() { return this.min; }
        @Override public int maxBuildHeight() { return this.max; }
        @Override public boolean isRemote() { return false; }
        @Override public long totalTime() { return 0; }
        @Override public String materialName(int x, int y, int z) { return "stub"; }
        @Override public boolean isAABBInWater(AABB box) { return false; }
        @Override public List<EntityRef> entitiesInAABB(AABB box) { return List.of(); }
        @Override public List<EntityRef> entitiesInAABB(AABB box, Role role) { return List.of(); }
        @Override public RayHit rayTraceBlocks(Vec3d from, Vec3d to) { return null; }
        @Override public RayHit clip(Vec3d from, Vec3d to, boolean stopOnLiquid) { return null; }
        @Override public void spawn(EntityRef entity) { }
        @Override public void spawnParticle(String name, Vec3d pos, Vec3d velocity) { }
        @Override public void playSound(Vec3d pos, String name, float volume, float pitch) { }
        @Override public void playSoundAtEntity(EntityRef entity, String name, float volume, float pitch) { }
        @Override public RandomSource random() { throw new UnsupportedOperationException(); }
    }

    @Test
    void aFullPoolGivesTheWaterSurfaceRow() {
        WaterWorld w = new WaterWorld().pool(10, -61, 20, 2, 3); // 75 cells, like the self-test's superflat pool
        assertEquals(-61, ParachuteFlight.boatY(w, 10, -60, 20), "the first water cell within 5 below");
        assertEquals(-61, ParachuteFlight.boatY(w, 10, -57, 20), "4 below still found");
        assertEquals(Integer.MIN_VALUE, ParachuteFlight.boatY(w, 10, -56, 20), "5 below is out of reach");
        assertEquals(-61, ParachuteFlight.boatY(w, 10, -61, 20), "the cell itself counts");
    }

    @Test
    void thirtySevenCellsGiveNoBoatThirtyEightDo() {
        WaterWorld w = new WaterWorld();
        // Fill the 5x5x3 box under (0, 50, 0) cell by cell, top layer first; the probe cell itself is first.
        List<long[]> cells = new ArrayList<>();
        cells.add(new long[] {0, 50, 0});
        for (int y = 0; y < 3; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (x != 0 || y != 0 || z != 0) {
                        cells.add(new long[] {x, 50 - y, z});
                    }
                }
            }
        }
        for (int n = 1; n <= 37; n++) {
            long[] c = cells.get(n - 1);
            w.water.add(WaterWorld.k((int) c[0], (int) c[1], (int) c[2]));
        }
        assertEquals(Integer.MIN_VALUE, ParachuteFlight.boatY(w, 0, 50, 0), "37 water cells");
        long[] c = cells.get(37);
        w.water.add(WaterWorld.k((int) c[0], (int) c[1], (int) c[2]));
        assertEquals(50, ParachuteFlight.boatY(w, 0, 50, 0), "38 water cells");
        assertEquals(Integer.MIN_VALUE, ParachuteFlight.boatY(new WaterWorld().pool(0, 50, 0, 1, 3), 0, 50, 0),
            "a 3x3x3 pool is 27 cells");
    }

    @Test
    void boatProbeRespectsTheBuildRange() {
        // A pool whose lower layers are below the world floor only counts the rows inside the world.
        WaterWorld w = new WaterWorld().pool(0, -63, 0, 2, 3);
        w.min = -64;
        assertEquals(-63, ParachuteFlight.boatY(w, 0, -63, 0), "rows -63 and -64: 50 cells");
        WaterWorld shallow = new WaterWorld().pool(0, -64, 0, 2, 3);
        assertEquals(Integer.MIN_VALUE, ParachuteFlight.boatY(shallow, 0, -64, 0), "only row -64 is in the world");
        // Scanning down from above the build limit skips the rows outside it.
        WaterWorld top = new WaterWorld().pool(0, 319, 0, 2, 3);
        assertEquals(319, ParachuteFlight.boatY(top, 0, 321, 0));
        // Works at negative y as at positive y.
        assertEquals(-20, ParachuteFlight.boatY(new WaterWorld().pool(-7, -20, -9, 2, 3), -7, -18, -9));
    }

    // ---- (i) isOpen ----

    @Test
    void onlyTheEjectionSeatOpensAndOnlyBelowMinusPointOne() {
        assertFalse(ParachuteFlight.isOpen(ParachuteFlight.TYPE_SEAT, -0.1));
        assertTrue(ParachuteFlight.isOpen(ParachuteFlight.TYPE_SEAT, Math.nextDown(-0.1)));
        assertFalse(ParachuteFlight.isOpen(ParachuteFlight.TYPE_SEAT, 2.0));
        for (int type : new int[] {ParachuteFlight.TYPE_NONE, ParachuteFlight.TYPE_HAND, ParachuteFlight.TYPE_DROP}) {
            assertTrue(ParachuteFlight.isOpen(type, 2.0));
            assertTrue(ParachuteFlight.isOpen(type, -0.1));
        }
    }

    @Test
    void fallDistanceBleedsOnlyWhenOpenSlowAndPastTwentyTicks() {
        assertEquals((float) (4.0F * 0.85), ParachuteFlight.dampedFallDistance(true, -0.2, 21, 4.0F));
        assertEquals(4.0F, ParachuteFlight.dampedFallDistance(true, -0.2, 20, 4.0F), "not before tick 21");
        assertEquals(4.0F, ParachuteFlight.dampedFallDistance(true, -0.3, 30, 4.0F), "strict -0.3");
        assertEquals(4.0F, ParachuteFlight.dampedFallDistance(false, -0.2, 30, 4.0F), "closed");
        assertNotEquals(4.0F, ParachuteFlight.dampedFallDistance(true, 0.5, 30, 4.0F), "rising counts too");
    }
}
