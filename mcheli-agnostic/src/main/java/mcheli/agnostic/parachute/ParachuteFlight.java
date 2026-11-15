package mcheli.agnostic.parachute;

import mcheli.agnostic.spi.WorldView;
import mcheli.agnostic.value.Vec3d;

/**
 * The flight rules of the parachute, reference {@code parachute/MCH_EntityParachute}: the server motion step of
 * {@code onUpdateServer} (:213-317) split around the move, the fall-distance damping of {@code onUpdate} (:134-136),
 * the open test (:372-374), the riderless heading slew, the push between two parachutes (the 1.7.10
 * {@code Entity.applyEntityCollision} the reference called at :303-311) and the water probe of {@code onWaterSetBoat}
 * (:325-370). The dependent {@code MchParachute} feeds it plain values and applies the results; the arithmetic is
 * unit-tested against a verbatim copy of the reference body.
 *
 * <p>An instance holds the reference's server-only, unsaved state: {@code speedMultiplier} (:26, starting at 0.07) and
 * {@code onGroundCount} (:40). Both reset when a saved parachute reloads, as they did in 1.7.10.
 */
public final class ParachuteFlight {

    /** Reference DataWatcher 31 values (:67-77): 0 is the bare-construction default and draws nothing. */
    public static final int TYPE_NONE = 0;
    /** The hand-held parachute ({@code MCH_ItemParachute}); always open. */
    public static final int TYPE_HAND = 1;
    /** The ejection seat ({@code ejectSeatSub}); open only while falling faster than 0.1 a tick. */
    public static final int TYPE_SEAT = 2;
    /** The crew drop and seat parachuting ({@code dropEntityParachute}); always open. */
    public static final int TYPE_DROP = 3;

    /** The horizontal speed cap of an open parachute (reference :237-241). */
    public static final double SPEED_CAP = 0.35;
    /** The steering multiplier's ceiling (reference :243-247), the same 0.35 as the speed cap. */
    public static final double MULT_MAX = 0.35;
    /** The steering multiplier's floor and start value (reference :26, :249-252). */
    public static final double MULT_MIN = 0.07;
    /** Ground ticks a parachute survives; the next one lands it (reference :256-261, {@code > 5}). */
    public static final int GROUND_TICKS = 5;
    /** A fresh ejection seat's launch window: gentle gravity and Y damping while {@code age < 20} (:216, :265). */
    public static final int SEAT_LAUNCH_TICKS = 20;
    /** A boat needs strictly more water cells than this in the 5x5x3 box (reference :352, :360). */
    public static final int BOAT_WATER_CELLS = 37;

    private double speedMultiplier = MULT_MIN;
    private int onGroundCount;

    /** The server steering multiplier (reference {@code speedMultiplier}), in {@code [0.07, 0.35]}. */
    public double speedMultiplier() {
        return this.speedMultiplier;
    }

    /** Ground ticks counted so far (reference {@code onGroundCount}; cumulative, never reset). */
    public int onGroundCount() {
        return this.onGroundCount;
    }

    /** The result of {@link #preMove}: the motion to move by, or {@code landed} (the 6th ground tick, no move). */
    public record Step(Vec3d motion, boolean landed) {}

    /** Reference {@code isOpenParachute} (:372-374): an ejection seat opens only while falling faster than 0.1. */
    public static boolean isOpen(int type, double motionY) {
        return type != TYPE_SEAT || motionY < -0.1;
    }

    /**
     * Reference :134-136, at the start of every tick on both sides with the motion the last tick left: an open
     * parachute falling slower than 0.3 a tick after its 20th tick bleeds 15 % of its fall distance, which holds it
     * near 2.0 at the steady descent, so a landing does no damage.
     */
    public static float dampedFallDistance(boolean open, double motionY, int age, float fallDistance) {
        if (open && motionY > -0.3 && age > 20) {
            return (float) (fallDistance * 0.85);
        }
        return fallDistance;
    }

    /**
     * Reference {@code onUpdateServer} :214-262, everything before {@code moveEntity}: gravity (0.03, 0.01 on the
     * ground or during a seat's launch window), then, when open with the gravity applied, the rider's forward push,
     * the 0.35 speed cap and the steering-multiplier ramp, then the ground count. The push is forward only
     * ({@code moveForward > 0}; back and strafe are ignored), along the rider's yaw with the reference's float angle
     * maths. The second {@code if (!isOpenParachute()) mv = 0} (:224-226) is dead inside the open branch and is
     * omitted.
     *
     * @param age          the reference {@code ticksExisted} (1 on the first tick)
     * @param livingRider  the rider is a living entity (reference {@code W_Lib.isEntityLivingBase(riddenByEntity)})
     * @param riderForward the rider's {@code moveForward} (1.21 {@code zza}); ignored without a living rider
     * @param riderYaw     the rider's yaw in degrees; ignored without a living rider
     */
    public Step preMove(int type, int age, boolean onGround, Vec3d motion, boolean livingRider, float riderForward,
                        float riderYaw) {
        double mx = motion.x();
        double my = motion.y();
        double mz = motion.z();
        double prevSpeed = Math.sqrt(mx * mx + mz * mz);
        double gravity = onGround ? 0.01 : 0.03;
        if (type == TYPE_SEAT && age < SEAT_LAUNCH_TICKS) {
            gravity = 0.01;
        }

        my -= gravity;
        if (isOpen(type, my)) {
            if (livingRider) {
                double mv = riderForward; // reference W_Lib.getEntityMoveDist: moveForward
                if (mv > 0.0) {
                    double fx = -Math.sin(riderYaw * (float) Math.PI / 180.0F);
                    double fz = Math.cos(riderYaw * (float) Math.PI / 180.0F);
                    mx = mx + fx * this.speedMultiplier * 0.05;
                    mz = mz + fz * this.speedMultiplier * 0.05;
                }
            }

            double speed = Math.sqrt(mx * mx + mz * mz);
            if (speed > SPEED_CAP) {
                mx *= SPEED_CAP / speed;
                mz *= SPEED_CAP / speed;
                speed = SPEED_CAP;
            }

            if (speed > prevSpeed && this.speedMultiplier < MULT_MAX) {
                this.speedMultiplier = this.speedMultiplier + (MULT_MAX - this.speedMultiplier) / 35.0;
                if (this.speedMultiplier > MULT_MAX) {
                    this.speedMultiplier = MULT_MAX;
                }
            } else {
                this.speedMultiplier = this.speedMultiplier - (this.speedMultiplier - MULT_MIN) / 35.0;
                if (this.speedMultiplier < MULT_MIN) {
                    this.speedMultiplier = MULT_MIN;
                }
            }
        }

        boolean landed = false;
        if (onGround) {
            this.onGroundCount++;
            if (this.onGroundCount > GROUND_TICKS) {
                landed = true; // reference onGroundAndDead + return: no move this tick
            }
        }
        return new Step(new Vec3d(mx, my, mz), landed);
    }

    /**
     * Reference :265-277, after the move: Y damping (0.95 in a seat's launch window, else 0.9), then the horizontal
     * damping (0.95 open, 0.99 closed), the open test re-read on the damped Y.
     */
    public static Vec3d postMove(int type, int age, Vec3d motion) {
        double my = motion.y();
        if (type == TYPE_SEAT && age < SEAT_LAUNCH_TICKS) {
            my *= 0.95;
        } else {
            my *= 0.9;
        }
        double mx = motion.x();
        double mz = motion.z();
        if (isOpen(type, my)) {
            mx *= 0.95;
            mz *= 0.95;
        } else {
            mx *= 0.99;
            mz *= 0.99;
        }
        return new Vec3d(mx, my, mz);
    }

    /**
     * Reference :280-301: with a rider the parachute takes the rider's yaw; riderless it turns at most 20 degrees a
     * tick toward its heading over the last move, when that move was longer than about 0.03 (squared 0.001). Both
     * branches end in the 1.7.10 {@code Entity.setRotation}, which stored {@code yaw % 360}.
     *
     * <p>Deliberate divergence: reference bug. The reference heading was {@code atan2(prevX - x, prevZ - z)} (:284),
     * the X axis mirror of the heading ({@code 180 - heading}): a parachute drifting along +Z turned to face -Z, one
     * drifting along X faced the right way. The heading here is the move's own, {@code atan2(-(x - prevX), z - prevZ)}.
     */
    public static float nextYaw(float yaw, boolean hasRider, float riderYaw, double prevX, double prevZ,
                                double x, double z) {
        double target = yaw;
        double dx = prevX - x;
        double dz = prevZ - z;
        if (dx * dx + dz * dz > 0.001) {
            target = (float) (Math.atan2(dx, -dz) * 180.0 / Math.PI); // deliberate divergence: reference bug (:284 dz)
        }

        double yawDiff = wrapAngleTo180(target - yaw);
        if (yawDiff > 20.0) {
            yawDiff = 20.0;
        }
        if (yawDiff < -20.0) {
            yawDiff = -20.0;
        }

        if (hasRider) {
            return riderYaw % 360.0F;
        }
        return (float) (yaw + yawDiff) % 360.0F;
    }

    /**
     * The push two overlapping parachutes give each other: the 1.7.10 {@code Entity.applyEntityCollision} maths the
     * reference ran on every other parachute within its box grown by 0.2 (:303-311). {@code (dx, dz)} is the other
     * parachute's position minus this one's; the result is the other's push, and this one takes the negated value. Below
     * a separation of 0.01 on both axes nothing is pushed. The 1.21 {@code Entity.push(Entity)} is not used because it
     * skips a ridden entity, while the reference pushed both parachutes whoever rode them.
     */
    public static double[] pushApart(double dx, double dz) {
        double d2 = absMax(dx, dz);
        if (d2 >= 0.009999999776482582) { // (double) 0.01F
            d2 = (float) Math.sqrt(d2);   // 1.7.10 MathHelper.sqrt_double returns a float
            dx /= d2;
            dz /= d2;
            double d3 = 1.0 / d2;
            if (d3 > 1.0) {
                d3 = 1.0;
            }
            dx *= d3;
            dz *= d3;
            dx *= 0.05000000074505806;     // (double) 0.05F
            dz *= 0.05000000074505806;
            // The 1.7.10 maths then scales by (1 - entityCollisionReduction), which is 0 for every entity.
            return new double[] {dx, dz};
        }
        return new double[] {0.0, 0.0};
    }

    /**
     * Reference {@code onWaterSetBoat} (:329-366): from the cell {@code (px, py, pz)}, the first water cell within 5
     * below (the cell itself included); then, if more than 37 cells of the 5x5x3 box under it (x and z within 2, that
     * layer and the 2 below) are water, that water cell's y, where the boat goes one above. Otherwise
     * {@link Integer#MIN_VALUE}. "Water" is any water fluid: 1.21 has one water block where 1.7.10 compared with the
     * still-water block.
     *
     * <p>Deliberate divergence (quirk 18, the {@code GroundProbe} precedent): the reference bounded both scans with
     * {@code py + y} in {@code [0, 255]} while it read row {@code py - y}. Here the row actually read is checked against
     * the world's build range ({@link WorldView#minBuildHeight()}..{@link WorldView#maxBuildHeight()}): rows above it
     * are skipped, the scan stops below it. The caller rounds the cell with {@code floor} where the reference cast with
     * {@code (int)}, which is off by one at negative coordinates.
     */
    public static int boatY(WorldView world, int px, int py, int pz) {
        int minY = world.minBuildHeight();
        int maxY = world.maxBuildHeight();
        boolean found = false;
        for (int y = 0; y < 5; y++) {
            int wy = py - y;
            if (wy > maxY) {
                continue;
            }
            if (wy < minY) {
                break;
            }
            if (world.isWater(px, wy, pz)) {
                py = wy;
                found = true;
                break;
            }
        }
        if (!found) {
            return Integer.MIN_VALUE;
        }

        int countWater = 0;
        for (int y = 0; y < 3; y++) {
            int wy = py - y;
            if (wy < minY) {
                break;
            }
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (world.isWater(px + x, wy, pz + z) && ++countWater > BOAT_WATER_CELLS) {
                        return py; // the reference counts on past 38; only "> 37" is read
                    }
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    /** 1.7.10 {@code MathHelper.wrapAngleTo180_double}. */
    static double wrapAngleTo180(double value) {
        value %= 360.0;
        if (value >= 180.0) {
            value -= 360.0;
        }
        if (value < -180.0) {
            value += 360.0;
        }
        return value;
    }

    /** 1.7.10 {@code MathHelper.abs_max}. */
    static double absMax(double a, double b) {
        if (a < 0.0) {
            a = -a;
        }
        if (b < 0.0) {
            b = -b;
        }
        return a > b ? a : b;
    }
}
