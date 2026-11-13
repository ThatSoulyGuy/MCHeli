package mcheli.dependent;

import mcheli.dependent.control.MchControlState;
import mcheli.dependent.entity.MchTank;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Headless proof that a tank (the m1a2) is a GROUND vehicle: driven forward it cruises straight and — critically — does
 * NOT climb. The tank shares the plane's forward model (which cancels gravity with +0.04 and adds throttle lift), so
 * with too-light gravity it takes off; this test spawns it in the air, holds throttle, and asserts it drove FORWARD
 * (+Z), stayed straight, and FELL rather than flew (net −Y). The climb guard ({@link #MAX_CLIMB}) is what catches the
 * "flying tank" regression that a too-light gravity reintroduces.
 *
 * <p>Two grounded probes are part of the same verdict: a TURN probe (a tank on the ground holding A must yaw left) and a
 * CLIMB probe (a tank driven into a 1-block step must crest it, which needs the config {@code StepHeight}).
 *
 * <p>Every driven tank gets its pig in the PILOT seat via {@code preferSeat} (the automatic rule gives seat 0 only to
 * players; a pig in a crew seat leaves {@code pilot()} null, which clears the throttle every tick) and a full tank of
 * fuel (m1a2 MaxFuel=1200 spawns dry, and the thrust gate reads {@code canUseFuel}).
 */
public final class DemoTankSelfTest extends MchSelfTest {

    private static final int CHECK_AFTER_TICKS = 40;
    private static final double FORWARD_MIN = 2.0;   // must drive forward
    private static final double LATERAL_MAX = 0.5;   // must stay straight (yaw 0, no A/D)
    private static final double MAX_CLIMB = 1.0;     // must NOT fly up (a flying tank => dy > 0)

    private MchTank tank;
    private Vec3 startPos;

    // Grounded turn probe: a second tank ON the ground holding A (moveLeft) — the forward tank is airborne (isFly),
    // so its onUpdateAngles turn branch never runs. This one verifies the hull actually yaws server-side.
    private MchTank turnTank;
    private float turnStartYaw;

    // Climb probe: a tank on flat ground with a 1-block step placed in its forward (+Z) path — driven into it, it must
    // crest the step (Y rises ~1) rather than stall against it. Verifies the config StepHeight actually lifts the hull.
    private MchTank climbTank;
    private double climbStartY;
    private double climbStartZ;

    @Override
    protected void start() {
        BlockPos spawn = this.origin;
        double x = spawn.getX() - 8.5;         // clear of the two grounded probes
        double y = spawn.getY() + 40.0;        // in open air; a grounded tank falls, a flying one climbs
        double z = spawn.getZ() + 0.5;

        tank = MchRegistries.TANK.get().create(level);
        if (tank == null) {
            fail("EntityType.create returned null for the tank type");
            return;
        }
        tank.setConfigName("m1a2");
        tank.setPos(x, y, z);
        tank.setYRot(0.0F);
        level.addFreshEntity(tank);
        tank.setFuel(tank.getMaxFuel());
        boolean mounted = seatPilot(tank, x, y, z);

        tank.getControlState().throttleUp = true; // drive forward (the pig sends no packets)

        startPos = tank.position();
        LOG.info("[TANK-SELFTEST] spawned demo_tank id={} at ({}, {}, {}); mounted={} passengers={} pilotSeat={}",
            tank.getId(), fmt(x), fmt(y), fmt(z), mounted, tank.getPassengers().size(),
            tank.getPassengers().isEmpty() ? -1 : tank.seatIndexOf(tank.getPassengers().get(0)));

        // --- grounded turn probe ---
        double tx = spawn.getX() + 8.5;
        double tz = spawn.getZ() + 8.5;
        int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            (int) Math.floor(tx), (int) Math.floor(tz));
        turnTank = MchRegistries.TANK.get().create(level);
        if (turnTank != null) {
            turnTank.setConfigName("m1a2");
            turnTank.setPos(tx, surfaceY, tz);
            turnTank.setYRot(0.0F);
            level.addFreshEntity(turnTank);
            turnTank.setFuel(turnTank.getMaxFuel());
            seatPilot(turnTank, tx, surfaceY, tz);
            turnStartYaw = turnTank.getYRot(); // A is driven via applyClientRotation each tick (client-auth path)
            // Climb: the entity's step-up height must be the config StepHeight (m1a2=1.5), not the entity default 0.
            LOG.info("[TANK-SELFTEST] turn probe: grounded demo_tank id={} at ({}, {}, {}) surfaceY={} startYaw={} stepHeight={} {}",
                turnTank.getId(), fmt(tx), fmt(surfaceY), fmt(tz), surfaceY, fmt(turnStartYaw), fmt(turnTank.maxUpStep()),
                turnTank.maxUpStep() > 0.5F ? "CLIMB-OK" : "CLIMB-FAIL(cannot crest hills)");
        }

        // --- climb probe: on a BUILT flat platform, drive a tank into a 1-block step some distance ahead ---
        double cx = spawn.getX() + 8.5;
        double cz = spawn.getZ() - 8.5;
        int cbx = (int) Math.floor(cx);
        int cbz = (int) Math.floor(cz);
        int floorY = spawn.getY() - 1;                 // a controlled flat floor (top face at floorY+1)
        var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (int bx = cbx - 4; bx <= cbx + 4; bx++) {
            for (int bz = cbz - 2; bz <= cbz + 14; bz++) {
                level.setBlockAndUpdate(new BlockPos(bx, floorY, bz), stone);               // flat floor
                for (int by = floorY + 1; by <= floorY + 6; by++) {
                    level.setBlockAndUpdate(new BlockPos(bx, by, bz), air);                  // clear headroom
                }
            }
        }
        for (int bx = cbx - 4; bx <= cbx + 4; bx++) {
            for (int bz = cbz + 9; bz <= cbz + 11; bz++) {
                level.setBlockAndUpdate(new BlockPos(bx, floorY + 1, bz), stone);            // a 1-block step to crest
            }
        }
        double climbSpawnY = floorY + 1;               // stand on the floor (top face)
        climbTank = MchRegistries.TANK.get().create(level);
        if (climbTank != null) {
            climbTank.setConfigName("m1a2");
            climbTank.setPos(cx, climbSpawnY, cz);
            climbTank.setYRot(0.0F); // yaw 0 => forward +Z, toward the step
            level.addFreshEntity(climbTank);
            climbTank.setFuel(climbTank.getMaxFuel());
            seatPilot(climbTank, cx, climbSpawnY, cz);
            climbTank.getControlState().throttleUp = true; // drive forward into the step
            climbStartY = climbTank.getY();
            climbStartZ = climbTank.getZ();
            LOG.info("[TANK-SELFTEST] climb probe: tank id={} at ({}, {}, {}) 1-block step at z={}..{} (top y={})",
                climbTank.getId(), fmt(cx), fmt(climbSpawnY), fmt(cz), cbz + 9, cbz + 11, floorY + 2);
        }
    }

    /** A no-AI pig in the PILOT seat (seat 0 is players-only unless asked for). */
    private boolean seatPilot(MchTank t, double x, double y, double z) {
        Entity pig = EntityType.PIG.create(level);
        if (pig == null) {
            return false;
        }
        if (pig instanceof Mob mob) {
            mob.setNoAi(true);
        }
        pig.setPos(x, y, z);
        pig.setYRot(0.0F);
        level.addFreshEntity(pig);
        t.preferSeat(pig, 0);
        return pig.startRiding(t, true);
    }

    @Override
    protected void tick() {
        // Drive the turn probe's client-authoritative rotation path (what MchClientRotation does each render frame on
        // the real client): hold A (moveLeft) and run the ported onUpdateAngles hull-yaw. partialTicks 0.6 ~ steady.
        // The input is built through MchControlState.snapshot, the same path the server uses, so this test does not
        // depend on ControlInput's constructor.
        if (turnTank != null) {
            MchControlState keys = new MchControlState();
            keys.moveLeft = true;
            turnTank.applyClientRotation(keys.snapshot(0.6F));
        }
        // Climb probe: the wheel-terrain tilt (hull pitch to the slope) lives in onUpdateAngles, which is client-auth,
        // so drive it each tick (W held). The server tick's TankFlightModel reads the resulting pitch for its thrust.
        if (climbTank != null) {
            MchControlState keys = new MchControlState();
            keys.throttleUp = true;
            climbTank.applyClientRotation(keys.snapshot(0.6F));
        }

        if (ticks() < CHECK_AFTER_TICKS) {
            return;
        }

        Vec3 end = tank.position();
        double dx = end.x - startPos.x;
        double dy = end.y - startPos.y;
        double dz = end.z - startPos.z;

        boolean forward = dz > FORWARD_MIN;
        boolean straight = Math.abs(dx) < LATERAL_MAX;
        boolean grounded = dy < MAX_CLIMB; // did NOT fly up
        boolean driveOk = forward && straight && grounded;

        LOG.info("[TANK-SELFTEST] after {} ticks: dz={} dx={} dy={} passengers={}",
            CHECK_AFTER_TICKS, fmt(dz), fmt(dx), fmt(dy), tank.getPassengers().size());
        LOG.info("[TANK-SELFTEST] drive: {} - real TankFlightModel {} via AircraftFlightController+NeoEntityRef (dz={} need>{}, |dx|={} need<{}, dy={} need<{})",
            driveOk ? "OK" : "FAIL",
            driveOk ? "drove straight FORWARD (did not fly)"
                 : (!grounded ? "FLEW (tank should not take off)" : (forward ? "drove but DRIFTED sideways" : "did NOT drive forward")),
            fmt(dz), fmt(FORWARD_MIN), fmt(Math.abs(dx)), fmt(LATERAL_MAX), fmt(dy), fmt(MAX_CLIMB));

        boolean turnOk = false;
        if (turnTank != null) {
            float yawDelta = net.minecraft.util.Mth.degreesDifference(turnStartYaw, turnTank.getYRot());
            boolean turned = Math.abs(yawDelta) > 5.0F;         // held A for ~40 ticks -> should have yawed clearly
            boolean leftward = yawDelta < 0.0F;                 // A = moveLeft = yaw decreases (reference sign)
            turnOk = turned && leftward;
            LOG.info("[TANK-SELFTEST] turn: {} - held A for {} ticks: yawDelta={} (need |>5|, leftward={}) onGround={}",
                turnOk ? "OK" : "FAIL", CHECK_AFTER_TICKS, fmt(yawDelta), leftward, turnTank.onGround());
        }

        boolean climbOk = false;
        if (climbTank != null) {
            double climbDy = climbTank.getY() - climbStartY;
            double climbDz = climbTank.getZ() - climbStartZ;
            boolean climbed = climbDy > 0.8;     // rose onto the 1-block step
            boolean advanced = climbDz > 1.0;    // actually reached/crossed the step, not stalled at start
            climbOk = climbed && advanced;
            LOG.info("[TANK-SELFTEST] climb: {} - drove into a 1-block step: dy={} (need>0.8) dz={} (need>1.0) onGround={}",
                climbOk ? "OK" : "FAIL", fmt(climbDy), fmt(climbDz), climbTank.onGround());
        }

        finish(driveOk && turnOk && climbOk, "drive={} (dz={} dx={} dy={}) turn={} climb={}",
            driveOk, fmt(dz), fmt(dx), fmt(dy), turnOk, climbOk);
    }

    private static String fmt(double v) {
        return String.format("%.3f", v);
    }
}
