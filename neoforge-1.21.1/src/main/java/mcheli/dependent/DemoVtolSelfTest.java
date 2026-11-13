package mcheli.dependent;

import mcheli.dependent.entity.MchPlane;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * Headless proof of <b>VTOL thrust vectoring</b> (#28), as a DIFFERENTIAL: two identical Harriers, both piloted at
 * full throttle from a standstill. One engages VTOL (nozzle ramps 0→90°, {@code vtolMode} 0→1→2) and HOVERS IN PLACE —
 * its thrust is redirected vertical, so it barely moves horizontally while its vertical thrust keeps it from
 * free-falling. The other stays conventional (nozzle 0) and FLIES FORWARD (thrust stays horizontal). The large gap in
 * horizontal travel is the unambiguous thrust-vectoring signature. Also checks the {@code canSwitchVtol} gate.
 */
public final class DemoVtolSelfTest extends MchSelfTest {
    private static final int RAMP_DONE_TICK = 66;   // 90/1.5 = 60 ticks + margin
    private static final int MEASURE_TICK = 110;     // + a 44-tick hover/cruise window

    private MchPlane vtol;      // engages VTOL -> hovers
    private MchPlane conv;      // stays conventional -> flies forward
    private boolean gateOkAtSpawn;
    private boolean gateBlockedMidTransition;
    private boolean rampReached;
    private boolean mv22SpawnsEngaged;
    private double vtolStartY, vtolStartX, vtolStartZ;
    private double convStartX, convStartZ;

    @Override
    protected int[] areaChunks() {
        return new int[] {-4, 4, -4, 10}; // cover the conventional plane's forward (+Z) run
    }

    @Override
    protected void start() {
        BlockPos spawn = this.origin;
        double y = spawn.getY() + 80.0;
        double z = spawn.getZ() + 0.5;

        this.vtol = spawnHarrier(spawn.getX() + 8.5, y, z);
        this.conv = spawnHarrier(spawn.getX() - 8.5, y, z);
        if (this.vtol == null || this.conv == null || !this.vtol.isVtolEnabled()) {
            fail("harrier unavailable or its VTOL is disabled (vtol={}, conv={})", this.vtol, this.conv);
            return;
        }
        Entity vtolPilot = seatPilot(this.vtol, spawn.getX() + 8.5, y, z);
        seatPilot(this.conv, spawn.getX() - 8.5, y, z);
        this.vtol.getControlState().throttleUp = true;
        this.conv.getControlState().throttleUp = true;

        this.gateOkAtSpawn = this.vtol.canSwitchVtol(); // level, settled, nozzle 0 -> engageable
        this.vtol.toggleVtol(vtolPilot);                // conv is left conventional

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
        LOG.info("[VTOL-SELFTEST] two harriers spawned; one engaged VTOL (gateOkAtSpawn={})", this.gateOkAtSpawn);
    }

    @Override
    protected void tick() {
        this.vtol.getControlState().throttleUp = true;
        this.conv.getControlState().throttleUp = true;

        if (ticks() == 3) {
            this.gateBlockedMidTransition = !this.vtol.canSwitchVtol(); // mid-ramp -> mode 1 -> blocked
        }
        if (ticks() == RAMP_DONE_TICK) {
            this.rampReached = this.vtol.getNozzleRotation() >= 89.99F && this.vtol.getVtolMode() == 2;
            this.vtolStartY = this.vtol.getY();
        }

        if (ticks() < MEASURE_TICK) {
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
        finish(pass, "gate={} ramp={} thrustVectored(decel+lessTravel)={} notFreeFalling={} mv22SpawnsEngaged={}",
            this.gateOkAtSpawn && this.gateBlockedMidTransition, this.rampReached,
            thrustVectored, notFreeFalling, this.mv22SpawnsEngaged);
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
}
