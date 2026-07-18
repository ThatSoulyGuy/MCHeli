package mcheli.dependent;

import com.mojang.logging.LogUtils;
import mcheli.agnostic.weapon.MCH_WeaponBallistics;
import mcheli.agnostic.weapon.MCH_WeaponInfo;
import mcheli.agnostic.weapon.MCH_WeaponInfoManager;
import mcheli.dependent.entity.MchBullet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Headless proof of the <b>server-side guided-missile homing</b> (#25). Bypasses the CLIENT lock machinery (a visual
 * path verified in a live client) by launching a guided {@link MchBullet} straight ahead with a real AA-missile config
 * (aim92) and a live off-axis target, then measuring that (1) the guided round CURVES onto the target (its min distance
 * collapses), (2) an identical UNGUIDED control fired parallel never approaches it, and (3) the guided round DETONATES.
 *
 * <p>NOTE: a no-player dedicated server only ticks a small spawn-chunk radius, so the flight path is force-loaded first
 * (otherwise a fast missile flies out of the ticking area and freezes mid-flight).
 */
public final class DemoHomingSelfTest {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int CHECK_AFTER_TICKS = 120;

    private ServerLevel level;
    private int ticks = -1;
    private int forceCx;
    private int forceCz;

    private LivingEntity target;
    private float targetStartHealth;
    private MchBullet guided;
    private MchBullet straight;

    private double guidedMinDist = Double.MAX_VALUE;
    private double straightMinDist = Double.MAX_VALUE;
    private boolean guidedDetonated;

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        this.level = event.getServer().overworld();
        BlockPos spawn = this.level.getSharedSpawnPos();
        double baseY = spawn.getY() + 40.0;

        MCH_WeaponInfo aa = MCH_WeaponInfoManager.get("aim92"); // Type=AAMissile, Accel 3, ProximityFuseDist 1
        if (aa == null || !aa.type.equalsIgnoreCase("aamissile")) {
            LOG.warn("[HOMING-SELFTEST] aim92 AA config not available; skipping");
            return;
        }

        // Force-load the flight-path chunks so a fast missile keeps ticking beyond the tiny no-player spawn radius.
        this.forceCx = spawn.getX() >> 4;
        this.forceCz = spawn.getZ() >> 4;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 7; dz++) {
                this.level.setChunkForced(this.forceCx + dx, this.forceCz + dz, true);
            }
        }

        Vec3 launch = new Vec3(spawn.getX() + 0.5, baseY, spawn.getZ() + 0.5);
        LivingEntity shooter = spawnGolem(launch, true); // the missile's shooter — excluded from its own collision
        Vec3 targetPos = new Vec3(spawn.getX() + 15.5, baseY + 2.0, spawn.getZ() + 60.5); // ahead (+Z) AND off to +X
        this.target = spawnGolem(targetPos, true);
        if (shooter == null || this.target == null) {
            LOG.warn("[HOMING-SELFTEST] could not spawn test entities; skipping");
            return;
        }
        this.targetStartHealth = this.target.getHealth();

        float speed = MCH_WeaponBallistics.initialSpeed(aa.acceleration);
        float accFactor = MCH_WeaponBallistics.accelerationFactor(aa.acceleration,
            MCH_WeaponBallistics.isBulletOrRocket(aa.type));
        Vec3 forward = new Vec3(0.0, 0.0, 1.0); // fired straight ahead, NOT at the target

        this.guided = MchBullet.spawnGuided(this.level, launch, forward, speed, accFactor, aa.gravity, aa.power, 600,
            shooter, "bullet", 0xFFFFFFFF, aa, this.target, MchBullet.GUIDED_AA);
        this.straight = MchBullet.spawnWeapon(this.level, launch, forward, speed, accFactor, aa.gravity, aa.power, 600,
            shooter, "bullet", 0xFF808080, aa); // unguided control from the same lane -> stays straight, misses

        this.ticks = 0;
        LOG.info("[HOMING-SELFTEST] launched guided + control AA rounds (speed={}, target off-axis by ~15 blocks)",
            String.format("%.2f", speed));
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (this.ticks < 0) {
            return;
        }
        this.ticks++;

        if (this.guided != null && this.target != null) {
            if (!this.guided.isRemoved()) {
                this.guidedMinDist = Math.min(this.guidedMinDist, this.guided.distanceTo(this.target));
            } else {
                this.guidedDetonated = true;
            }
        }
        if (this.straight != null && this.target != null && !this.straight.isRemoved()) {
            this.straightMinDist = Math.min(this.straightMinDist, this.straight.distanceTo(this.target));
        }

        if (this.ticks < CHECK_AFTER_TICKS) {
            return;
        }

        float targetDamage = this.target != null ? this.targetStartHealth - this.target.getHealth() : 0.0F;
        boolean homedClose = this.guidedMinDist < 5.0;
        boolean controlStayedFar = this.straightMinDist > 10.0;
        boolean clearHomingEffect = this.guidedMinDist < this.straightMinDist - 6.0;
        boolean pass = homedClose && controlStayedFar && clearHomingEffect;

        LOG.info("[HOMING-SELFTEST] guidedMinDist={} straightMinDist={} detonated={} targetDmg={}",
            fmt(this.guidedMinDist), fmt(this.straightMinDist), this.guidedDetonated, fmt(targetDamage));
        LOG.info("[HOMING-SELFTEST] RESULT: {} - homedClose={} controlFar={} clearHoming={} detonated={}",
            pass ? "PASS" : "FAIL", homedClose, controlStayedFar, clearHomingEffect, this.guidedDetonated);

        cleanup();
    }

    private LivingEntity spawnGolem(Vec3 pos, boolean airborne) {
        if (EntityType.IRON_GOLEM.create(this.level) instanceof LivingEntity g) {
            if (g instanceof Mob m) {
                m.setNoAi(true);
            }
            g.setNoGravity(airborne);
            g.setPos(pos.x, pos.y, pos.z);
            this.level.addFreshEntity(g);
            return g;
        }
        return null;
    }

    private void cleanup() {
        if (this.target != null) {
            this.target.discard();
        }
        for (MchBullet b : this.level.getEntitiesOfClass(MchBullet.class,
            new AABB(-100000, -100, -100000, 100000, 400, 100000))) {
            b.discard();
        }
        for (LivingEntity g : this.level.getEntitiesOfClass(LivingEntity.class,
            new AABB(-100000, -100, -100000, 100000, 400, 100000), e -> e instanceof net.minecraft.world.entity.animal.IronGolem)) {
            g.discard();
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 7; dz++) {
                this.level.setChunkForced(this.forceCx + dx, this.forceCz + dz, false);
            }
        }
        this.ticks = -1;
        this.target = null;
        this.guided = null;
        this.straight = null;
    }

    private static String fmt(double v) {
        return String.format("%.3f", v);
    }
}
