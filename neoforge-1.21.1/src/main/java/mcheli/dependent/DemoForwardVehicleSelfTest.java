package mcheli.dependent;

import java.util.function.Supplier;
import com.mojang.logging.LogUtils;
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
 * Reusable headless, log-verifiable DIFFERENTIAL proof that a forward-thrust {@link net.minecraft.world.entity.Entity}
 * flight model (plane / tank) generates rider-gated thrust in a live server. Shared by the plane and tank demos —
 * both share the {@code rot2Vec3(yaw, pitch−10°)} forward model, differing only in lift strength and speed cap, so
 * one parameterised test covers both.
 *
 * <p>It spawns two identical demo entities in open air, force-mounts a pig "pilot" on one, leaves the other
 * pilotless, holds throttle on both, and after a fixed window asserts the PILOTED entity flew forward and stayed
 * far above the pilotless one. Throttle starts at 0 on both (faithful: no rider → no thrust), and the rider/canopy/
 * fuel gate in the model decides spool-UP vs. spin-DOWN — so only the piloted entity develops thrust. This makes
 * the test honest: if the mount fails or {@code hasRider} regresses, the piloted entity behaves like the pilotless
 * one, the forward distance and the altitude differential both collapse, and the test FAILS.
 *
 * <p>Yaw is 0, so "forward" is +Z. The differential is robust to the world-spawn entity-ticking core: even if the
 * piloted entity flies far enough to leave the ticking chunks and freeze mid-flight, it remains forward-of and above
 * the free-falling pilotless control (which never moves horizontally, so it always ticks), so both differentials
 * still hold.
 */
public final class DemoForwardVehicleSelfTest {
    private static final Logger LOG = LogUtils.getLogger();

    private static final int CHECK_AFTER_TICKS = 100;
    /** The pilotless entity must genuinely fall this far — proves gravity/physics is running. */
    private static final double PILOTLESS_FELL_BELOW = -20.0;
    /** The pilotless entity must NOT fly forward (no rider → no thrust) beyond this. */
    private static final double PILOTLESS_FORWARD_MAX = 2.0;
    /** Max lateral drift for the piloted entity — yaw 0 gives no X thrust; catches stray sideways motion. */
    private static final double LATERAL_MAX = 1.0;

    private final String label;
    private final Supplier<? extends EntityType<? extends Entity>> type;
    private final double xPiloted;
    private final double xPilotless;
    private final double yOffset;
    private final double forwardMin;
    private final double altDifferentialMin;

    private Entity piloted;
    private Entity pilotless;
    private Entity pilot;
    private Vec3 startPiloted;
    private Vec3 startPilotless;
    private int ticks = -1;

    /**
     * @param label               short name for the log lines (e.g. "PLANE")
     * @param type                the demo entity type supplier
     * @param xPiloted            X offset from world spawn for the piloted entity (keep within ~14 for ticking)
     * @param xPilotless          X offset for the pilotless control (distinct from xPiloted; within ~14)
     * @param yOffset             Y offset above spawn (high enough the pilotless ~76-block fall stays airborne)
     * @param forwardMin          minimum piloted forward (+Z) distance proving thrust
     * @param altDifferentialMin  minimum (pilotedDy − pilotlessDy) proving the control path kept it aloft
     */
    public DemoForwardVehicleSelfTest(String label, Supplier<? extends EntityType<? extends Entity>> type,
                                      double xPiloted, double xPilotless, double yOffset,
                                      double forwardMin, double altDifferentialMin) {
        this.label = label;
        this.type = type;
        this.xPiloted = xPiloted;
        this.xPilotless = xPilotless;
        this.yOffset = yOffset;
        this.forwardMin = forwardMin;
        this.altDifferentialMin = altDifferentialMin;
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        double y = spawn.getY() + yOffset;
        double z = spawn.getZ() + 0.5;

        piloted = type.get().create(level);
        pilotless = type.get().create(level);
        if (piloted == null || pilotless == null) {
            LOG.error("[{}-SELFTEST] FAIL: EntityType.create returned null", label);
            return;
        }
        piloted.setPos(spawn.getX() + xPiloted, y, z);
        piloted.setYRot(0.0F);
        level.addFreshEntity(piloted);
        pilotless.setPos(spawn.getX() + xPilotless, y, z);
        pilotless.setYRot(0.0F);
        level.addFreshEntity(pilotless);

        pilot = EntityType.PIG.create(level);
        boolean mounted = false;
        if (pilot != null) {
            if (pilot instanceof Mob mob) {
                mob.setNoAi(true);
            }
            pilot.setPos(spawn.getX() + xPiloted, y, z);
            pilot.setYRot(0.0F);
            level.addFreshEntity(pilot);
            mounted = pilot.startRiding(piloted, true); // ONLY the piloted entity gets a rider
        }

        startPiloted = piloted.position();
        startPilotless = pilotless.position();
        ticks = 0;
        LOG.info("[{}-SELFTEST] spawned piloted id={} (mounted={}, passengers={}) + pilotless id={} at y={}",
            label, piloted.getId(), mounted, piloted.getPassengers().size(), pilotless.getId(), fmt(y));
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (ticks < 0 || piloted == null || pilotless == null) {
            return;
        }
        ticks++;
        if (ticks < CHECK_AFTER_TICKS) {
            return;
        }

        Vec3 endP = piloted.position();
        Vec3 endU = pilotless.position();
        double forwardPiloted = endP.z - startPiloted.z;
        double forwardPilotless = endU.z - startPilotless.z;
        double lateralPiloted = Math.abs(endP.x - startPiloted.x);
        double dyPiloted = endP.y - startPiloted.y;
        double dyPilotless = endU.y - startPilotless.y;
        double altDifferential = dyPiloted - dyPilotless;

        boolean flew = forwardPiloted > forwardMin;
        boolean stayedAloft = altDifferential > altDifferentialMin;
        boolean gravityReal = dyPilotless < PILOTLESS_FELL_BELOW;
        boolean pilotlessDidNotFly = forwardPilotless < PILOTLESS_FORWARD_MAX;
        boolean straight = lateralPiloted < LATERAL_MAX;
        boolean pass = flew && stayedAloft && gravityReal && pilotlessDidNotFly && straight;

        LOG.info("[{}-SELFTEST] after {} ticks: piloted forward={} dy={} lateral={} | pilotless forward={} dy={} | altDifferential={}",
            label, CHECK_AFTER_TICKS, fmt(forwardPiloted), fmt(dyPiloted), fmt(lateralPiloted),
            fmt(forwardPilotless), fmt(dyPilotless), fmt(altDifferential));
        LOG.info("[{}-SELFTEST] RESULT: {} - real flight model {} via AircraftFlightController+NeoEntityRef (forward={} need>{}, altDiff={} need>{}, pilotlessDy={} need<{}, pilotlessFwd={} need<{}, lateral={} need<{})",
            label,
            pass ? "PASS" : "FAIL",
            pass ? "RIDER-gated thrust flew the piloted entity forward and held it ~" + fmt(altDifferential) + " blocks above the pilotless one"
                 : failReason(flew, stayedAloft, gravityReal, pilotlessDidNotFly),
            fmt(forwardPiloted), fmt(forwardMin), fmt(altDifferential), fmt(altDifferentialMin),
            fmt(dyPilotless), fmt(PILOTLESS_FELL_BELOW), fmt(forwardPilotless), fmt(PILOTLESS_FORWARD_MAX),
            fmt(lateralPiloted), fmt(LATERAL_MAX));

        if (pilot != null) {
            pilot.stopRiding();
            pilot.discard();
        }
        piloted.discard();
        pilotless.discard();
        piloted = null;
        pilotless = null;
        pilot = null;
        ticks = -1;
    }

    private static String failReason(boolean flew, boolean stayedAloft, boolean gravityReal, boolean pilotlessDidNotFly) {
        if (!gravityReal) return "pilotless entity did NOT fall (gravity/physics not running)";
        if (!pilotlessDidNotFly) return "pilotless entity flew forward (thrust not rider-gated)";
        if (!flew) return "piloted entity did NOT fly forward (rider/control path not driving thrust)";
        if (!stayedAloft) return "piloted entity did NOT out-climb the pilotless one";
        return "piloted entity DRIFTED sideways";
    }

    private static String fmt(double v) {
        return String.format("%.3f", v);
    }
}
