package mcheli.dependent;

import mcheli.agnostic.weapon.VehicleWeapons;
import mcheli.dependent.entity.MchBullet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/**
 * Headless proof of the <b>CAS weapon</b> (#31): {@code cas} is now SELECTABLE (VehicleWeapons.isFireable), and calling
 * in a strike ({@link MchBullet#callAirstrike}, the seam the fireSelectedWeapon {@code cas} branch drives after
 * raycasting the aim to the ground) rains a BOMBING RUN on the target — a volley of bombs spawns high above the aimed
 * point, scattered around its column, and descends onto it. (The reference A-10 strafing entity is a deferred sub-port;
 * this is the ordnance-on-target stand-in, reusing the #21/#27 bomb path whose impact+explosion is already proven.)
 */
public final class DemoCasSelfTest extends MchSelfTest {
    private static final int CHECK_AFTER_TICKS = 12;

    private boolean casFireable;
    private double tx, ty, tz;
    private int volleyCount;
    private double spawnMaxY = -1.0;
    private double minY = Double.MAX_VALUE;
    private double maxHorizFromTarget;

    @Override
    protected void start() {
        this.casFireable = VehicleWeapons.isFireable("cas");

        BlockPos spawn = this.origin;
        this.tx = spawn.getX() + 0.5;
        this.ty = spawn.getY();
        this.tz = spawn.getZ() + 0.5;
        MchBullet.callAirstrike(this.level, this.tx, this.ty, this.tz, null, null, 4, 30, 8);

        // Snapshot the just-spawned volley: how many, and how high above the target they start.
        for (MchBullet b : liveBombs()) {
            this.volleyCount++;
            this.spawnMaxY = Math.max(this.spawnMaxY, b.getY());
        }
    }

    @Override
    protected void tick() {
        for (MchBullet b : liveBombs()) {
            this.minY = Math.min(this.minY, b.getY());
            double dx = b.getX() - this.tx;
            double dz = b.getZ() - this.tz;
            this.maxHorizFromTarget = Math.max(this.maxHorizFromTarget, Math.sqrt(dx * dx + dz * dz));
        }
        if (ticks() < CHECK_AFTER_TICKS) {
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
        finish(pass, "fireable={} volleyRained={} startedHigh={} descending={} onTarget={}",
            this.casFireable, volleyRained, startedHigh, descending, onTarget);
    }

    /** The strike's bombs still in flight: the rounds that joined the world during this test. */
    private java.util.List<MchBullet> liveBombs() {
        java.util.List<MchBullet> out = new java.util.ArrayList<>();
        for (Entity e : spawned()) {
            if (e instanceof MchBullet b && !b.isRemoved()) {
                out.add(b);
            }
        }
        return out;
    }
}
