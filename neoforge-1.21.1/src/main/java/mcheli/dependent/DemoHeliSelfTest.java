package mcheli.dependent;

import com.mojang.logging.LogUtils;
import mcheli.dependent.entity.MchHelicopter;
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
 * Headless, log-verifiable proof that the ported {@code HeliFlightModel} produces LIFT <em>because the rider-gated
 * control path runs</em> — the flight-physics counterpart to {@link DemoVehicleSelfTest} (which only proves the
 * ground path). It is a DIFFERENTIAL test: it spawns two identical demo helis in open air, force-mounts a pig
 * "pilot" on one and leaves the other pilotless, holds collective on both, ticks the server, and asserts the
 * PILOTED heli ends up far above the pilotless one.
 *
 * <p>The differential is the whole point, and it is what makes the test honest. Throttle starts at 0 on both
 * (faithful to the reference: a pilotless heli develops no lift). The rider/blade/canopy/fuel gate in
 * {@code HeliFlightModel.updateThrottle} decides spool-UP vs. spin-DOWN — so ONLY the piloted heli spools its
 * rotor and climbs; the pilotless one, gate closed, keeps throttle at 0 and free-falls under gravity. A numeric
 * sim of the exact model over the window gives piloted ≈ −2 blocks and pilotless ≈ −65 blocks — a ~+63 differential.
 * If the mount fails, {@code hasRider} regresses, or the throttle spool breaks, the piloted heli behaves like the
 * pilotless one and the differential collapses toward 0, FAILING the test — which a naive single-heli "did it
 * climb?" check (with a primed throttle) could not detect.
 */
public final class DemoHeliSelfTest {
    private static final Logger LOG = LogUtils.getLogger();

    /** Ticks to run before measuring — long enough for the piloted rotor to spool up and the pilotless one to fall. */
    private static final int CHECK_AFTER_TICKS = 100;
    /** Minimum (piloted − pilotless) net-Y separation (blocks) proving the RIDER drove collective lift (~+63 correct). */
    private static final double LIFT_DIFFERENTIAL_MIN = 20.0;
    /** The pilotless heli must genuinely fall this far — proves gravity is active and lift is not universal (~−65). */
    private static final double PILOTLESS_FELL_BELOW = -20.0;
    /** Max horizontal drift for the piloted heli — pitch/roll start at 0 so cyclic thrust is 0; catches stray motion. */
    private static final double LATERAL_MAX = 0.5;

    private MchHelicopter piloted;
    private MchHelicopter pilotless;
    private Entity pilot;
    private Vec3 startPiloted;
    private Vec3 startPilotless;
    private int ticks = -1;

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        // Spawn both high in OPEN AIR (+80 so the pilotless heli's ~65-block fall never reaches the ground within
        // the window). Keep BOTH within ~16 blocks of world spawn on opposite sides: on a playerless dedicated
        // server only the core spawn chunks are entity-ticking, so an entity 2+ chunks out (e.g. x+32) never ticks
        // and would spuriously read dy=0. x+16 and x-16 both sit inside the ticking core (and clear of the vehicle
        // self-test at x+0 and of each other).
        double y = spawn.getY() + 80.0;
        double xPiloted = spawn.getX() + 16.5;
        double xPilotless = spawn.getX() - 15.5;
        double z = spawn.getZ() + 0.5;

        piloted = MchRegistries.HELI.get().create(level);
        pilotless = MchRegistries.HELI.get().create(level);
        if (piloted == null || pilotless == null) {
            LOG.error("[HELI-SELFTEST] FAIL: EntityType.create returned null for the heli type");
            return;
        }
        piloted.setConfigName("ah-64");
        pilotless.setConfigName("ah-64");
        piloted.setPos(xPiloted, y, z);
        piloted.setYRot(0.0F);
        level.addFreshEntity(piloted);
        pilotless.setPos(xPilotless, y, z);
        pilotless.setYRot(0.0F);
        level.addFreshEntity(pilotless);

        pilot = EntityType.PIG.create(level);
        boolean mounted = false;
        if (pilot != null) {
            if (pilot instanceof Mob mob) {
                mob.setNoAi(true);
            }
            pilot.setPos(xPiloted, y, z);
            pilot.setYRot(0.0F);
            level.addFreshEntity(pilot);
            mounted = pilot.startRiding(piloted, true); // ONLY the piloted heli gets a rider -> only it spools/climbs
        }

        // Drive ONLY the piloted heli's control state (collective up) — the pilotless one is left with no input, so
        // it free-falls. Equivalent to a player holding throttle-up. Bits persist while a passenger is aboard.
        piloted.getControlState().throttleUp = true;

        startPiloted = piloted.position();
        startPilotless = pilotless.position();
        ticks = 0;
        LOG.info("[HELI-SELFTEST] spawned piloted id={} (mounted={}, passengers={}) + pilotless id={} at y={}",
            piloted.getId(), mounted, piloted.getPassengers().size(), pilotless.getId(), fmt(y));
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
        double dyPiloted = endP.y - startPiloted.y;
        double dyPilotless = endU.y - startPilotless.y;
        double differential = dyPiloted - dyPilotless;
        double horizontal = Math.hypot(endP.x - startPiloted.x, endP.z - startPiloted.z);

        boolean lifted = differential > LIFT_DIFFERENTIAL_MIN;
        boolean gravityReal = dyPilotless < PILOTLESS_FELL_BELOW;
        boolean straight = horizontal < LATERAL_MAX;
        boolean pass = lifted && gravityReal && straight;

        LOG.info("[HELI-SELFTEST] after {} ticks: piloted dy={} pilotless dy={} differential={} pilotedHorizontal={} passengers={}",
            CHECK_AFTER_TICKS, fmt(dyPiloted), fmt(dyPilotless), fmt(differential), fmt(horizontal), piloted.getPassengers().size());
        LOG.info("[HELI-SELFTEST] RESULT: {} - real HeliFlightModel {} via AircraftFlightController+NeoEntityRef (differential={} need>{}, pilotlessDy={} need<{}, horizontal={} need<{})",
            pass ? "PASS" : "FAIL",
            pass ? "RIDER-gated collective lift held the piloted heli ~" + fmt(differential) + " blocks above the pilotless one"
                 : (!gravityReal ? "pilotless heli did NOT fall (gravity/physics not running)"
                    : (!lifted ? "piloted heli did NOT out-climb the pilotless one (rider/control path not driving lift)"
                       : "piloted heli DRIFTED sideways")),
            fmt(differential), fmt(LIFT_DIFFERENTIAL_MIN), fmt(dyPilotless), fmt(PILOTLESS_FELL_BELOW), fmt(horizontal), fmt(LATERAL_MAX));

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

    private static String fmt(double v) {
        return String.format("%.3f", v);
    }
}
