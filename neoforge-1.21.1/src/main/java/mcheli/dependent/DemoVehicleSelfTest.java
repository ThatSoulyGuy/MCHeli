package mcheli.dependent;

import com.mojang.logging.LogUtils;
import mcheli.dependent.entity.MchDemoVehicle;
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
 * Headless, log-verifiable proof of the vertical slice's core claim: <em>agnostic logic, running through
 * a real {@link mcheli.dependent.port.NeoEntityRef} wrapping a real spawned entity, moves that entity in a
 * live server world.</em>
 *
 * <p>On server start it spawns a {@link MchDemoVehicle}, force-mounts a pig "pilot" so the piloted branch
 * of {@code SimpleVehicleLogic} runs, lets the server tick the entity normally, and after a fixed number
 * of ticks logs the horizontal displacement and a PASS/FAIL verdict. This runs on a dedicated server (no
 * rendering, no human), which is exactly how the slice is validated in CI-style headless runs.
 */
public final class DemoVehicleSelfTest {
    private static final Logger LOG = LogUtils.getLogger();

    /** Ticks to let the vehicle cruise before measuring. At {@code CRUISE_SPEED} this is several blocks. */
    private static final int CHECK_AFTER_TICKS = 40;
    /** Minimum +Z distance (blocks) the frozen-pilot cruise must cover — asserts correct FORWARD steering. */
    private static final double FORWARD_MIN = 2.0;
    /** Max sideways (|dx|) drift allowed for the cruise to count as "straight" — catches axis/sign bugs. */
    private static final double LATERAL_MAX = 0.5;

    private MchDemoVehicle vehicle;
    private Entity pilot;
    private Vec3 startPos;
    private int ticks = -1;

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        double x = spawn.getX() + 0.5;
        // Spawn high in OPEN AIR: the dev world is persistent, so a low spawn deterministically collides
        // with the same tree/hill near world spawn and the cruise stalls after ~0.75 blocks. +40 clears it.
        double y = spawn.getY() + 40.0;
        double z = spawn.getZ() + 0.5;

        vehicle = MchRegistries.DEMO_VEHICLE.get().create(level);
        if (vehicle == null) {
            LOG.error("[SELFTEST] FAIL: EntityType.create returned null for demo_vehicle");
            return;
        }
        vehicle.setPos(x, y, z);
        vehicle.setYRot(0.0F);
        level.addFreshEntity(vehicle);

        pilot = EntityType.PIG.create(level);
        boolean mounted = false;
        if (pilot != null) {
            // Freeze the pilot at yaw 0 so its heading is DETERMINISTIC: the agnostic logic reads
            // pilot.yaw()==0 every tick, so a correct forward vector must drive the vehicle straight +Z.
            if (pilot instanceof Mob mob) {
                mob.setNoAi(true);
            }
            pilot.setPos(x, y, z);
            pilot.setYRot(0.0F);
            level.addFreshEntity(pilot);
            mounted = pilot.startRiding(vehicle, true);  // force-mount to exercise the piloted branch
        }

        startPos = vehicle.position();
        ticks = 0;
        LOG.info("[SELFTEST] spawned demo_vehicle id={} at ({}, {}, {}); pilot mounted={} passengers={}",
            vehicle.getId(), fmt(x), fmt(y), fmt(z), mounted, vehicle.getPassengers().size());
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (ticks < 0 || vehicle == null) {
            return;
        }
        ticks++;
        if (ticks < CHECK_AFTER_TICKS) {
            return;
        }

        Vec3 end = vehicle.position();
        double dx = end.x - startPos.x;
        double dz = end.z - startPos.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        // With the pilot frozen at yaw 0, correct steering must carry the vehicle FORWARD (+Z) with almost
        // no lateral drift. Asserting DIRECTION (not just magnitude) means a steering sign/axis regression
        // in SimpleVehicleLogic's forward vector actually fails this test instead of passing on |motion|.
        boolean forward = dz > FORWARD_MIN;
        boolean straight = Math.abs(dx) < LATERAL_MAX;
        boolean pass = forward && straight;

        LOG.info("[SELFTEST] after {} ticks: start=({}, {}, {}) end=({}, {}, {}) dz={} dx={} horizontal={} passengers={}",
            CHECK_AFTER_TICKS, fmt(startPos.x), fmt(startPos.y), fmt(startPos.z),
            fmt(end.x), fmt(end.y), fmt(end.z), fmt(dz), fmt(dx), fmt(horizontal), vehicle.getPassengers().size());
        LOG.info("[SELFTEST] RESULT: {} - real VehicleFlightModel {} via AircraftFlightController+NeoEntityRef (dz={} need>{}, |dx|={} need<{})",
            pass ? "PASS" : "FAIL",
            pass ? "cruised the entity straight FORWARD (+Z)"
                 : (forward ? "cruised but DRIFTED sideways" : "did NOT cruise forward"),
            fmt(dz), fmt(FORWARD_MIN), fmt(Math.abs(dx)), fmt(LATERAL_MAX));

        // Clean up so the test leaves no entities behind.
        if (pilot != null) {
            pilot.stopRiding();
            pilot.discard();
        }
        vehicle.discard();
        vehicle = null;
        pilot = null;
        ticks = -1;
    }

    private static String fmt(double v) {
        return String.format("%.3f", v);
    }
}
