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
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (ticks < 0 || tank == null) {
            return;
        }
        ticks++;
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
