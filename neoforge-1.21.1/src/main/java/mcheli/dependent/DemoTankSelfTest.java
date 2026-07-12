package mcheli.dependent;

import com.mojang.logging.LogUtils;
import mcheli.dependent.entity.MchDemoTank;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Headless proof that the demo tank is a GROUND vehicle: driven forward it cruises straight and — critically — does
 * NOT climb. The tank shares the plane's forward model (which cancels gravity with +0.04 and adds throttle lift), so
 * with too-light gravity it takes off; this test spawns it in the air, holds throttle, and asserts it drove FORWARD
 * (+Z), stayed straight, and FELL rather than flew (net −Y). The climb guard ({@link #MAX_CLIMB}) is what catches the
 * "flying tank" regression that a too-light gravity reintroduces.
 */
public final class DemoTankSelfTest {
    private static final Logger LOG = LogUtils.getLogger();

    private static final int CHECK_AFTER_TICKS = 40;
    private static final double FORWARD_MIN = 2.0;   // must drive forward
    private static final double LATERAL_MAX = 0.5;   // must stay straight (yaw 0, no A/D)
    private static final double MAX_CLIMB = 1.0;     // must NOT fly up (a flying tank => dy > 0)

    private MchDemoTank tank;
    private Entity pilot;
    private Vec3 startPos;
    private int ticks = -1;

    // Grounded turn probe: a second tank ON the ground holding A (moveLeft) — the forward tank is airborne (isFly),
    // so its onUpdateAngles turn branch never runs. This one verifies the hull actually yaws server-side.
    private MchDemoTank turnTank;
    private Entity turnPilot;
    private float turnStartYaw;

    // Climb probe: a tank on flat ground with a 1-block step placed in its forward (+Z) path — driven into it, it must
    // crest the step (Y rises ~1) rather than stall against it. Verifies the config StepHeight actually lifts the hull.
    private MchDemoTank climbTank;
    private Entity climbPilot;
    private double climbStartY;
    private double climbStartZ;

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        double x = spawn.getX() - 8.5;         // clear of the vehicle/heli/plane self-tests
        double y = spawn.getY() + 40.0;        // in open air; a grounded tank falls, a flying one climbs
        double z = spawn.getZ() + 0.5;

        tank = MchRegistries.DEMO_TANK.get().create(level);
        if (tank == null) {
            LOG.error("[TANK-SELFTEST] FAIL: EntityType.create returned null for demo_tank");
            return;
        }
        tank.setPos(x, y, z);
        tank.setYRot(0.0F);
        level.addFreshEntity(tank);

        pilot = EntityType.PIG.create(level);
        boolean mounted = false;
        if (pilot != null) {
            if (pilot instanceof Mob mob) {
                mob.setNoAi(true);
            }
            pilot.setPos(x, y, z);
            pilot.setYRot(0.0F);
            level.addFreshEntity(pilot);
            mounted = pilot.startRiding(tank, true);
        }

        tank.getControlState().throttleUp = true; // drive forward (the pig sends no packets)

        startPos = tank.position();
        ticks = 0;
        LOG.info("[TANK-SELFTEST] spawned demo_tank id={} at ({}, {}, {}); mounted={} passengers={}",
            tank.getId(), fmt(x), fmt(y), fmt(z), mounted, tank.getPassengers().size());

        // --- grounded turn probe ---
        double tx = spawn.getX() + 8.5;
        double tz = spawn.getZ() + 8.5;
        int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            (int) Math.floor(tx), (int) Math.floor(tz));
        turnTank = MchRegistries.DEMO_TANK.get().create(level);
        if (turnTank != null) {
            turnTank.setPos(tx, surfaceY, tz);
            turnTank.setYRot(0.0F);
            level.addFreshEntity(turnTank);
            turnPilot = EntityType.PIG.create(level);
            if (turnPilot != null) {
                if (turnPilot instanceof Mob mob) {
                    mob.setNoAi(true);
                }
                turnPilot.setPos(tx, surfaceY, tz);
                level.addFreshEntity(turnPilot);
                turnPilot.startRiding(turnTank, true);
            }
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
        climbTank = MchRegistries.DEMO_TANK.get().create(level);
        if (climbTank != null) {
            climbTank.setPos(cx, climbSpawnY, cz);
            climbTank.setYRot(0.0F); // yaw 0 => forward +Z, toward the step
            level.addFreshEntity(climbTank);
            climbPilot = EntityType.PIG.create(level);
            if (climbPilot != null) {
                if (climbPilot instanceof Mob mob) {
                    mob.setNoAi(true);
                }
                climbPilot.setPos(cx, climbSpawnY, cz);
                level.addFreshEntity(climbPilot);
                climbPilot.startRiding(climbTank, true);
            }
            climbTank.getControlState().throttleUp = true; // drive forward into the step
            climbStartY = climbTank.getY();
            climbStartZ = climbTank.getZ();
            LOG.info("[TANK-SELFTEST] climb probe: tank id={} at ({}, {}, {}) 1-block step at z={}..{} (top y={})",
                climbTank.getId(), fmt(cx), fmt(climbSpawnY), fmt(cz), cbz + 9, cbz + 11, floorY + 2);
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (ticks < 0 || tank == null) {
            return;
        }
        ticks++;

        // Drive the turn probe's client-authoritative rotation path (what MchClientRotation does each render frame on
        // the real client): hold A (moveLeft) and run the ported onUpdateAngles hull-yaw. partialTicks 0.6 ~ steady.
        if (turnTank != null) {
            turnTank.applyClientRotation(new mcheli.agnostic.sim.ControlInput(
                0, 0, 0, 0, false, false, true, false, false, false, false, false, false, 0.6F));
        }
        // Climb probe: the wheel-terrain tilt (hull pitch to the slope) lives in onUpdateAngles, which is client-auth,
        // so drive it each tick (W held). The server tick's TankFlightModel reads the resulting pitch for its thrust.
        if (climbTank != null) {
            climbTank.applyClientRotation(new mcheli.agnostic.sim.ControlInput(
                0, 0, 0, 0, true, false, false, false, false, false, false, false, false, 0.6F));
        }

        if (ticks < CHECK_AFTER_TICKS) {
            return;
        }

        Vec3 end = tank.position();
        double dx = end.x - startPos.x;
        double dy = end.y - startPos.y;
        double dz = end.z - startPos.z;

        boolean forward = dz > FORWARD_MIN;
        boolean straight = Math.abs(dx) < LATERAL_MAX;
        boolean grounded = dy < MAX_CLIMB; // did NOT fly up
        boolean pass = forward && straight && grounded;

        LOG.info("[TANK-SELFTEST] after {} ticks: dz={} dx={} dy={} passengers={}",
            CHECK_AFTER_TICKS, fmt(dz), fmt(dx), fmt(dy), tank.getPassengers().size());
        LOG.info("[TANK-SELFTEST] RESULT: {} - real TankFlightModel {} via AircraftFlightController+NeoEntityRef (dz={} need>{}, |dx|={} need<{}, dy={} need<{})",
            pass ? "PASS" : "FAIL",
            pass ? "drove straight FORWARD on the ground (did not fly)"
                 : (!grounded ? "FLEW (tank should not take off)" : (forward ? "drove but DRIFTED sideways" : "did NOT drive forward")),
            fmt(dz), fmt(FORWARD_MIN), fmt(Math.abs(dx)), fmt(LATERAL_MAX), fmt(dy), fmt(MAX_CLIMB));

        if (turnTank != null) {
            float yawDelta = net.minecraft.util.Mth.degreesDifference(turnStartYaw, turnTank.getYRot());
            boolean turned = Math.abs(yawDelta) > 5.0F;         // held A for ~40 ticks -> should have yawed clearly
            boolean leftward = yawDelta < 0.0F;                 // A = moveLeft = yaw decreases (reference sign)
            LOG.info("[TANK-SELFTEST] TURN RESULT: {} - held A for {} ticks: yawDelta={} (need |>5|, leftward={}) onGround={}",
                turned && leftward ? "PASS" : "FAIL", CHECK_AFTER_TICKS, fmt(yawDelta), leftward, turnTank.onGround());
            if (turnPilot != null) {
                turnPilot.stopRiding();
                turnPilot.discard();
            }
            turnTank.discard();
            turnTank = null;
            turnPilot = null;
        }

        if (climbTank != null) {
            double climbDy = climbTank.getY() - climbStartY;
            double climbDz = climbTank.getZ() - climbStartZ;
            boolean climbed = climbDy > 0.8;     // rose onto the 1-block step
            boolean advanced = climbDz > 1.0;    // actually reached/crossed the step, not stalled at start
            LOG.info("[TANK-SELFTEST] CLIMB RESULT: {} - drove into a 1-block step: dy={} (need>0.8) dz={} (need>1.0) onGround={}",
                climbed && advanced ? "PASS" : "FAIL", fmt(climbDy), fmt(climbDz), climbTank.onGround());
            if (climbPilot != null) {
                climbPilot.stopRiding();
                climbPilot.discard();
            }
            climbTank.discard();
            climbTank = null;
            climbPilot = null;
        }

        if (pilot != null) {
            pilot.stopRiding();
            pilot.discard();
        }
        tank.discard();
        tank = null;
        pilot = null;
        ticks = -1;
    }

    private static String fmt(double v) {
        return String.format("%.3f", v);
    }
}
