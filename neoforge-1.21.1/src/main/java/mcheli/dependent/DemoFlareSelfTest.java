package mcheli.dependent;

import com.mojang.logging.LogUtils;
import mcheli.agnostic.weapon.MCH_WeaponBallistics;
import mcheli.agnostic.weapon.MCH_WeaponInfo;
import mcheli.agnostic.weapon.MCH_WeaponInfoManager;
import mcheli.dependent.entity.MchBullet;
import mcheli.dependent.entity.MchFlare;
import mcheli.dependent.entity.MchHelicopter;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Headless proof of the <b>flare countermeasure decoy</b> (#26): a flare-capable helicopter (ah-1z, {@code FlareType})
 * dispenses flares, and while its burn window is active a guided AA {@link MchBullet} homing on it is decoyed —
 * targetEntity nulled + the round removed AT RANGE (not by impact) — while an UNGUIDED control round nearby is
 * untouched (the decoy filters by {@code isGuided() && isHomingOn(self)}). Also asserts flares actually spawn.
 */
public final class DemoFlareSelfTest {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int CHECK_AFTER_TICKS = 16;

    private ServerLevel level;
    private int ticks = -1;
    private int forceCx;
    private int forceCz;

    private MchHelicopter heli;
    private MchBullet missile;   // GUIDED_AA homing on the heli — should be decoyed
    private MchBullet control;   // unguided — should be ignored by the decoy
    private double missileDecoyDist = -1.0;
    private boolean missileRemoved;
    private boolean sawFlaring;

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        this.level = event.getServer().overworld();
        BlockPos spawn = this.level.getSharedSpawnPos();
        double baseY = spawn.getY() + 40.0;
        this.forceCx = spawn.getX() >> 4;
        this.forceCz = spawn.getZ() >> 4;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                this.level.setChunkForced(this.forceCx + dx, this.forceCz + dz, true);
            }
        }

        MCH_WeaponInfo aa = MCH_WeaponInfoManager.get("aim92");
        this.heli = MchRegistries.HELI.get().create(this.level);
        if (aa == null || this.heli == null) {
            LOG.warn("[FLARE-SELFTEST] aim92 or heli unavailable; skipping");
            return;
        }
        this.heli.setConfigName("ah-1z"); // ships FlareType = 3
        this.heli.setPos(spawn.getX() + 0.5, baseY, spawn.getZ() + 0.5);
        this.heli.setNoGravity(true);
        this.level.addFreshEntity(this.heli);
        if (!this.heli.haveFlare()) {
            LOG.warn("[FLARE-SELFTEST] ah-1z reports no flares; skipping");
            this.heli = null;
            return;
        }

        float speed = MCH_WeaponBallistics.initialSpeed(aa.acceleration);
        float accF = MCH_WeaponBallistics.accelerationFactor(aa.acceleration, MCH_WeaponBallistics.isBulletOrRocket(aa.type));
        // Guided round 40 blocks out, homing on the heli (aimed away so it must turn back — but the decoy kills it first).
        Vec3 mPos = new Vec3(spawn.getX() + 0.5, baseY, spawn.getZ() + 40.5);
        this.missile = MchBullet.spawnGuided(this.level, mPos, new Vec3(0, 0, 1), speed, accF, aa.gravity, aa.power, 600,
            null, "bullet", 0xFFFFFFFF, aa, this.heli, MchBullet.GUIDED_AA);
        // Unguided control 20 blocks out, flying away — within the 60-block scan but not guided, so never decoyed.
        Vec3 cPos = new Vec3(spawn.getX() + 6.5, baseY, spawn.getZ() + 20.5);
        this.control = MchBullet.spawnWeapon(this.level, cPos, new Vec3(0, 0, 1), speed, accF, 0.0F, aa.power, 600,
            null, "bullet", 0xFF808080, aa);

        this.ticks = 0;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (this.ticks < 0) {
            return;
        }
        this.ticks++;

        if (this.ticks == 2 && this.heli != null) {
            this.heli.deployFlare(this.heli.currentFlareType()); // start the burn window
        }
        if (this.heli != null && this.heli.isFlareUsing()) {
            this.sawFlaring = true;
        }
        if (this.missile != null && !this.missile.isRemoved() && this.heli != null) {
            this.missileDecoyDist = this.missile.distanceTo(this.heli); // last-known distance while alive
        }
        if (this.missile != null && this.missile.isRemoved()) {
            this.missileRemoved = true;
        }

        if (this.ticks < CHECK_AFTER_TICKS) {
            return;
        }

        int flareCount = this.level.getEntitiesOfClass(MchFlare.class,
            new AABB(-100000, -100, -100000, 100000, 400, 100000)).size();
        boolean controlAlive = this.control != null && !this.control.isRemoved();

        boolean decoyedAtRange = this.missileRemoved && this.missileDecoyDist > 8.0;
        boolean flaresSpawned = flareCount > 0;
        boolean pass = decoyedAtRange && flaresSpawned && this.sawFlaring && controlAlive;

        LOG.info("[FLARE-SELFTEST] missileRemoved={} decoyDist={} flares={} flaring={} controlAlive={}",
            this.missileRemoved, String.format("%.2f", this.missileDecoyDist), flareCount, this.sawFlaring, controlAlive);
        LOG.info("[FLARE-SELFTEST] RESULT: {} - decoyedAtRange={} flaresSpawned={} sawFlaring={} controlUntouched={}",
            pass ? "PASS" : "FAIL", decoyedAtRange, flaresSpawned, this.sawFlaring, controlAlive);

        cleanup();
    }

    private void cleanup() {
        if (this.heli != null) {
            this.heli.discard();
        }
        for (MchBullet b : this.level.getEntitiesOfClass(MchBullet.class,
            new AABB(-100000, -100, -100000, 100000, 400, 100000))) {
            b.discard();
        }
        for (MchFlare f : this.level.getEntitiesOfClass(MchFlare.class,
            new AABB(-100000, -100, -100000, 100000, 400, 100000))) {
            f.discard();
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                this.level.setChunkForced(this.forceCx + dx, this.forceCz + dz, false);
            }
        }
        this.ticks = -1;
        this.heli = null;
        this.missile = null;
        this.control = null;
    }
}
