package mcheli.dependent;

import com.mojang.logging.LogUtils;
import mcheli.dependent.entity.MchPlane;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Headless proof of <b>VTOL thrust vectoring</b> (#28), as a DIFFERENTIAL: two identical Harriers, both piloted at
 * full throttle from a standstill. One engages VTOL (nozzle ramps 0→90°, {@code vtolMode} 0→1→2) and HOVERS IN PLACE —
 * its thrust is redirected vertical, so it barely moves horizontally while its vertical thrust keeps it from
 * free-falling. The other stays conventional (nozzle 0) and FLIES FORWARD (thrust stays horizontal). The large gap in
 * horizontal travel is the unambiguous thrust-vectoring signature. Also checks the {@code canSwitchVtol} gate.
 */
public final class DemoVtolSelfTest {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int RAMP_DONE_TICK = 66;   // 90/1.5 = 60 ticks + margin
    private static final int MEASURE_TICK = 110;     // + a 44-tick hover/cruise window

    private ServerLevel level;
    private int forceCx;
    private int forceCz;
    private MchPlane vtol;      // engages VTOL -> hovers
    private MchPlane conv;      // stays conventional -> flies forward
    private Entity vtolPilot;
    private Entity convPilot;
    private boolean gateOkAtSpawn;
    private boolean gateBlockedMidTransition;
    private boolean rampReached;
    private boolean mv22SpawnsEngaged;
    private double vtolStartY, vtolStartX, vtolStartZ;
    private double convStartX, convStartZ;
    private int ticks = -1;

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        this.level = event.getServer().overworld();
        BlockPos spawn = this.level.getSharedSpawnPos();
        double y = spawn.getY() + 80.0;
        double z = spawn.getZ() + 0.5;
        this.forceCx = spawn.getX() >> 4;
        this.forceCz = spawn.getZ() >> 4;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                this.level.setChunkForced(this.forceCx + dx, this.forceCz + dz, true); // cover the conventional forward run
            }
        }

        this.vtol = spawnHarrier(spawn.getX() + 8.5, y, z);
        this.conv = spawnHarrier(spawn.getX() - 8.5, y, z);
        if (this.vtol == null || this.conv == null || !this.vtol.isVtolEnabled()) {
            LOG.warn("[VTOL-SELFTEST] harrier unavailable / VTOL disabled; skipping");
            this.vtol = null;
            return;
        }
        this.vtolPilot = seatPilot(this.vtol, spawn.getX() + 8.5, y, z);
        this.convPilot = seatPilot(this.conv, spawn.getX() - 8.5, y, z);
        this.vtol.getControlState().throttleUp = true;
        this.conv.getControlState().throttleUp = true;

        this.gateOkAtSpawn = this.vtol.canSwitchVtol(); // level, settled, nozzle 0 -> engageable
        this.vtol.toggleVtol(this.vtolPilot);           // conv is left conventional

        // DefaultVtol (MV-22 Osprey): must resolve its config ALREADY in hover (reference forceSwitch) — nozzle 90,
        // mode 2 — with no toggle needed. A brief spawn + check, then discard.
        MchPlane mv22 = MchRegistries.PLANE.get().create(this.level);
        if (mv22 != null) {
            mv22.setConfigName("mv-22");
            mv22.setPos(spawn.getX() + 0.5, y, spawn.getZ() + 40.5);
            this.level.addFreshEntity(mv22);
            this.mv22SpawnsEngaged = mv22.isDefaultVtol() && mv22.getVtolMode() == 2
                && mv22.getNozzleRotation() >= 89.99F;
            mv22.discard();
        }

        this.vtolStartX = this.vtol.getX();
        this.vtolStartZ = this.vtol.getZ();
        this.convStartX = this.conv.getX();
        this.convStartZ = this.conv.getZ();
        this.ticks = 0;
        LOG.info("[VTOL-SELFTEST] two harriers spawned; one engaged VTOL (gateOkAtSpawn={})", this.gateOkAtSpawn);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (this.ticks < 0 || this.vtol == null) {
            return;
        }
        this.ticks++;
        this.vtol.getControlState().throttleUp = true;
        this.conv.getControlState().throttleUp = true;

        if (this.ticks == 3) {
            this.gateBlockedMidTransition = !this.vtol.canSwitchVtol(); // mid-ramp -> mode 1 -> blocked
        }
        if (this.ticks == RAMP_DONE_TICK) {
            this.rampReached = this.vtol.getNozzleRotation() >= 89.99F && this.vtol.getVtolMode() == 2;
            this.vtolStartY = this.vtol.getY();
        }

        if (this.ticks < MEASURE_TICK) {
            return;
        }

        double vtolHoriz = Math.hypot(this.vtol.getX() - this.vtolStartX, this.vtol.getZ() - this.vtolStartZ);
        double convHoriz = Math.hypot(this.conv.getX() - this.convStartX, this.conv.getZ() - this.convStartZ);
        double vtolDYHover = this.vtol.getY() - this.vtolStartY; // over the hover window (nozzle already 90)
        // Current horizontal SPEED: the VTOL plane has redirected thrust vertical, so it decelerates toward a hover;
        // the conventional plane keeps its forward thrust and holds cruise speed.
        double vtolSpeed = Math.hypot(this.vtol.getDeltaMovement().x, this.vtol.getDeltaMovement().z);
        double convSpeed = Math.hypot(this.conv.getDeltaMovement().x, this.conv.getDeltaMovement().z);

        // Thrust vectoring: the VTOL plane travels far less AND is now much slower than the conventional one (it
        // decelerated from forward flight to a hover — a real Harrier bleeds off speed, it does not stop instantly).
        boolean thrustVectored = convHoriz > vtolHoriz * 2.0 && vtolSpeed < convSpeed * 0.5;
        boolean notFreeFalling = vtolDYHover > -15.0;               // vertical thrust holds it (free-fall would be ~-30+)
        boolean pass = this.gateOkAtSpawn && this.gateBlockedMidTransition && this.rampReached
            && thrustVectored && notFreeFalling && this.mv22SpawnsEngaged;

        LOG.info("[VTOL-SELFTEST] gate={} rampReached={} vtolHoriz={} convHoriz={} vtolSpeed={} convSpeed={} vtolHoverDY={} (throttle vtol={} conv={})",
            this.gateOkAtSpawn && this.gateBlockedMidTransition, this.rampReached,
            String.format("%.2f", vtolHoriz), String.format("%.2f", convHoriz),
            String.format("%.3f", vtolSpeed), String.format("%.3f", convSpeed), String.format("%.2f", vtolDYHover),
            String.format("%.2f", this.vtol.getThrottleInput()), String.format("%.2f", this.conv.getThrottleInput()));
        LOG.info("[VTOL-SELFTEST] RESULT: {} - gate={} ramp={} thrustVectored(decel+lessTravel)={} notFreeFalling={} mv22SpawnsEngaged={}",
            pass ? "PASS" : "FAIL", this.gateOkAtSpawn && this.gateBlockedMidTransition, this.rampReached,
            thrustVectored, notFreeFalling, this.mv22SpawnsEngaged);

        cleanup();
    }

    private MchPlane spawnHarrier(double x, double y, double z) {
        MchPlane p = MchRegistries.PLANE.get().create(this.level);
        if (p == null) {
            return null;
        }
        p.setConfigName("harrier");
        p.setPos(x, y, z);
        p.setYRot(0.0F);
        this.level.addFreshEntity(p);
        p.setFuel(p.getMaxFuel());
        return p;
    }

    private Entity seatPilot(MchPlane plane, double x, double y, double z) {
        Entity pig = EntityType.PIG.create(this.level);
        if (pig != null) {
            if (pig instanceof Mob m) {
                m.setNoAi(true);
            }
            pig.setPos(x, y, z);
            this.level.addFreshEntity(pig);
            plane.preferSeat(pig, 0);
            pig.startRiding(plane, true);
        }
        return pig;
    }

    private void cleanup() {
        for (Entity e : new Entity[] {this.vtolPilot, this.convPilot, this.vtol, this.conv}) {
            if (e != null) {
                if (e instanceof Mob) {
                    e.stopRiding();
                }
                e.discard();
            }
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                this.level.setChunkForced(this.forceCx + dx, this.forceCz + dz, false);
            }
        }
        this.ticks = -1;
        this.vtol = null;
        this.conv = null;
    }
}
