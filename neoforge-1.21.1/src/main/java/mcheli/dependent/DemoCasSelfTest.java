package mcheli.dependent;

import com.mojang.logging.LogUtils;
import mcheli.agnostic.weapon.VehicleWeapons;
import mcheli.dependent.entity.MchBullet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Headless proof of the <b>CAS weapon</b> (#31): {@code cas} is now SELECTABLE (VehicleWeapons.isFireable), and calling
 * in a strike ({@link MchBullet#callAirstrike}, the seam the fireSelectedWeapon {@code cas} branch drives after
 * raycasting the aim to the ground) rains a BOMBING RUN on the target — a volley of bombs spawns high above the aimed
 * point, scattered around its column, and descends onto it. (The reference A-10 strafing entity is a deferred sub-port;
 * this is the ordnance-on-target stand-in, reusing the #21/#27 bomb path whose impact+explosion is already proven.)
 */
public final class DemoCasSelfTest {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int CHECK_AFTER_TICKS = 12;

    private ServerLevel level;
    private int ticks = -1;
    private int forceCx;
    private int forceCz;

    private boolean casFireable;
    private double tx, ty, tz;
    private int volleyCount;
    private double spawnMaxY = -1.0;
    private double minY = Double.MAX_VALUE;
    private double maxHorizFromTarget;

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        this.level = event.getServer().overworld();
        this.casFireable = VehicleWeapons.isFireable("cas");

        BlockPos spawn = this.level.getSharedSpawnPos();
        this.forceCx = spawn.getX() >> 4;
        this.forceCz = spawn.getZ() >> 4;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                this.level.setChunkForced(this.forceCx + dx, this.forceCz + dz, true);
            }
        }

        this.tx = spawn.getX() + 0.5;
        this.ty = spawn.getY();
        this.tz = spawn.getZ() + 0.5;
        MchBullet.callAirstrike(this.level, this.tx, this.ty, this.tz, null, 4, 30, 8);

        // Snapshot the just-spawned volley: how many, and how high above the target they start.
        for (MchBullet b : liveBombs()) {
            this.volleyCount++;
            this.spawnMaxY = Math.max(this.spawnMaxY, b.getY());
        }
        this.ticks = 0;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (this.ticks < 0) {
            return;
        }
        this.ticks++;
        for (MchBullet b : liveBombs()) {
            this.minY = Math.min(this.minY, b.getY());
            double dx = b.getX() - this.tx;
            double dz = b.getZ() - this.tz;
            this.maxHorizFromTarget = Math.max(this.maxHorizFromTarget, Math.sqrt(dx * dx + dz * dz));
        }
        if (this.ticks < CHECK_AFTER_TICKS) {
            return;
        }

        boolean volleyRained = this.volleyCount >= 6;                     // a full CAS volley spawned
        boolean startedHigh = this.spawnMaxY > this.ty + 100.0;           // from high above the target
        boolean descending = this.minY < this.spawnMaxY;                  // falling toward the ground
        boolean onTarget = this.maxHorizFromTarget <= 20.0;               // scattered AROUND the aimed column, not elsewhere
        boolean pass = this.casFireable && volleyRained && startedHigh && descending && onTarget;

        LOG.info("[CAS-SELFTEST] fireable={} volley={} spawnMaxY={} minY={} maxHoriz={}",
            this.casFireable, this.volleyCount, String.format("%.1f", this.spawnMaxY),
            String.format("%.1f", this.minY), String.format("%.1f", this.maxHorizFromTarget));
        LOG.info("[CAS-SELFTEST] RESULT: {} - fireable={} volleyRained={} startedHigh={} descending={} onTarget={}",
            pass ? "PASS" : "FAIL", this.casFireable, volleyRained, startedHigh, descending, onTarget);

        cleanup();
    }

    private java.util.List<MchBullet> liveBombs() {
        return this.level.getEntitiesOfClass(MchBullet.class, new AABB(-100000, -100, -100000, 100000, 400, 100000));
    }

    private void cleanup() {
        for (MchBullet b : liveBombs()) {
            b.discard();
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                this.level.setChunkForced(this.forceCx + dx, this.forceCz + dz, false);
            }
        }
        this.ticks = -1;
    }
}
