package mcheli.dependent;

import java.util.function.Supplier;
import mcheli.dependent.control.MchControllable;
import mcheli.dependent.entity.AbstractMchVehicle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Reusable headless, log-verifiable DIFFERENTIAL proof that a forward-thrust {@link net.minecraft.world.entity.Entity}
 * flight model (plane / tank) generates rider-gated thrust in a live server. The {@code plane} self-test runs it; the
 * tank shares the {@code rot2Vec3(yaw, pitch−10°)} forward model (differing only in lift strength and speed cap), but
 * its own ground-path checks live in {@link DemoTankSelfTest}.
 *
 * <p>It spawns two identical vehicles in open air, force-mounts a pig "pilot" in the PILOT seat of one (the
 * automatic seat rule gives seat 0 only to players, so the pig asks for it with {@code preferSeat}), fuels it, leaves
 * the other pilotless, holds throttle on both, and after a fixed window asserts the PILOTED entity flew forward and
 * stayed far above the pilotless one. Throttle starts at 0 on both (faithful: no rider → no thrust), and the
 * rider/canopy/fuel gate in the model decides spool-UP vs. spin-DOWN — so only the piloted entity develops thrust. This
 * makes the test honest: if the mount fails or {@code hasRider} regresses, the piloted entity behaves like the
 * pilotless one, the forward distance and the altitude differential both collapse, and the test FAILS.
 *
 * <p>Yaw is 0, so "forward" is +Z, and the test's force-loaded area reaches 12 chunks down +Z so the piloted entity
 * keeps simulating for the whole window.
 */
public final class DemoForwardVehicleSelfTest extends MchSelfTest {

    private static final int CHECK_AFTER_TICKS = 100;
    /** The pilotless entity must genuinely fall this far — proves gravity/physics is running. */
    private static final double PILOTLESS_FELL_BELOW = -20.0;
    /** The pilotless entity must NOT fly forward (no rider → no thrust) beyond this. */
    private static final double PILOTLESS_FORWARD_MAX = 2.0;
    /** Max lateral drift for the piloted entity — yaw 0 gives no X thrust; catches stray sideways motion. */
    private static final double LATERAL_MAX = 1.0;

    private final String label;
    private final Supplier<? extends EntityType<? extends Entity>> type;
    private final String configName;
    private final double xPiloted;
    private final double xPilotless;
    private final double yOffset;
    private final double forwardMin;
    private final double altDifferentialMin;

    private Entity piloted;
    private Entity pilotless;
    private Vec3 startPiloted;
    private Vec3 startPilotless;

    /**
     * @param label               short name for the log lines (e.g. "PLANE")
     * @param type                the vehicle entity type supplier
     * @param xPiloted            X offset from the area origin for the piloted entity
     * @param xPilotless          X offset for the pilotless control (distinct from xPiloted)
     * @param yOffset             Y offset above the ground (high enough the pilotless ~76-block fall stays airborne)
     * @param forwardMin          minimum piloted forward (+Z) distance proving thrust
     * @param altDifferentialMin  minimum (pilotedDy − pilotlessDy) proving the control path kept it aloft
     */
    public DemoForwardVehicleSelfTest(String label, Supplier<? extends EntityType<? extends Entity>> type,
                                      String configName, double xPiloted, double xPilotless, double yOffset,
                                      double forwardMin, double altDifferentialMin) {
        this.label = label;
        this.type = type;
        this.configName = configName;
        this.xPiloted = xPiloted;
        this.xPilotless = xPilotless;
        this.yOffset = yOffset;
        this.forwardMin = forwardMin;
        this.altDifferentialMin = altDifferentialMin;
    }

    @Override
    protected int[] areaChunks() {
        return new int[] {-2, 2, -2, 12};
    }

    @Override
    protected void start() {
        BlockPos spawn = this.origin;
        double y = spawn.getY() + yOffset;
        double z = spawn.getZ() + 0.5;

        piloted = type.get().create(level);
        pilotless = type.get().create(level);
        if (piloted == null || pilotless == null) {
            fail("EntityType.create returned null");
            return;
        }
        if (piloted instanceof AbstractMchVehicle pv) pv.setConfigName(configName);
        if (pilotless instanceof AbstractMchVehicle lv) lv.setConfigName(configName);
        piloted.setPos(spawn.getX() + xPiloted, y, z);
        piloted.setYRot(0.0F);
        level.addFreshEntity(piloted);
        pilotless.setPos(spawn.getX() + xPilotless, y, z);
        pilotless.setYRot(0.0F);
        level.addFreshEntity(pilotless);
        // A fresh vehicle spawns DRY and the thrust gate reads canUseFuel (a-10: MaxFuel=1800), so fuel the piloted one
        // as a spawn item would. The pilotless control stays dry, which changes nothing for it (no rider, no thrust).
        if (piloted instanceof AbstractMchVehicle pv) pv.setFuel(pv.getMaxFuel());

        Entity pilot = EntityType.PIG.create(level);
        boolean mounted = false;
        if (pilot != null) {
            if (pilot instanceof Mob mob) {
                mob.setNoAi(true);
            }
            pilot.setPos(spawn.getX() + xPiloted, y, z);
            pilot.setYRot(0.0F);
            level.addFreshEntity(pilot);
            // Seat 0 is reserved for players by the automatic rule; without this the pig sits in a crew seat, pilot()
            // is null and the injected throttle is cleared every tick.
            if (piloted instanceof AbstractMchVehicle pv) pv.preferSeat(pilot, 0);
            mounted = pilot.startRiding(piloted, true); // ONLY the piloted entity gets a rider
        }

        // Drive ONLY the piloted entity's control state (throttle up) — the pilotless one gets no input and
        // free-falls. Every MCHeli vehicle implements MchControllable; bits persist while a pilot is aboard.
        if (piloted instanceof MchControllable c) {
            c.getControlState().throttleUp = true;
        }

        startPiloted = piloted.position();
        startPilotless = pilotless.position();
        LOG.info("[{}-SELFTEST] spawned piloted id={} (mounted={}, passengers={}, pilotSeat={}) + pilotless id={} at y={}",
            label, piloted.getId(), mounted, piloted.getPassengers().size(),
            pilot != null && piloted instanceof AbstractMchVehicle pv ? pv.seatIndexOf(pilot) : -1,
            pilotless.getId(), fmt(y));
    }

    @Override
    protected void tick() {
        if (ticks() < CHECK_AFTER_TICKS) {
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
        finish(pass, "real flight model {} via AircraftFlightController+NeoEntityRef (forward={} need>{}, altDiff={} need>{}, pilotlessDy={} need<{}, pilotlessFwd={} need<{}, lateral={} need<{})",
            pass ? "RIDER-gated thrust flew the piloted entity forward and held it ~" + fmt(altDifferential) + " blocks above the pilotless one"
                 : failReason(flew, stayedAloft, gravityReal, pilotlessDidNotFly),
            fmt(forwardPiloted), fmt(forwardMin), fmt(altDifferential), fmt(altDifferentialMin),
            fmt(dyPilotless), fmt(PILOTLESS_FELL_BELOW), fmt(forwardPilotless), fmt(PILOTLESS_FORWARD_MAX),
            fmt(lateralPiloted), fmt(LATERAL_MAX));
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
