package mcheli.dependent;

import com.mojang.logging.LogUtils;
import mcheli.dependent.entity.MchBullet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Headless proof of the projectile pipeline: spawn a target, fire an {@link MchBullet} straight at it, and assert the
 * bullet TRAVELS, HITS the target, DAMAGES it, and then despawns. This validates the fire → flight → raytrace →
 * damage core without a client (rendering the tracer is the only client-side part).
 */
public final class DemoBulletSelfTest {
    private static final Logger LOG = LogUtils.getLogger();

    private static final int CHECK_AFTER_TICKS = 20;
    private static final float SPEED = 5.0F;
    private static final float DAMAGE = 5.0F;

    private LivingEntity target;
    private MchBullet bullet;
    private float startHealth;
    private int ticks = -1;

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        double x = spawn.getX() - 12.5;   // clear of the other self-tests
        double y = spawn.getY() + 40.0;   // open air
        double z = spawn.getZ() + 0.5;

        // Target 8 blocks downrange, AI off so it holds still.
        if (EntityType.PIG.create(level) instanceof LivingEntity t) {
            target = t;
            if (target instanceof Mob mob) {
                mob.setNoAi(true);
            }
            target.setPos(x, y, z + 8.0);
            level.addFreshEntity(target);
            startHealth = target.getHealth();
        }

        // Fire a bullet from (x,y,z) straight down +Z at the target.
        bullet = MchBullet.spawn(level, new Vec3(x, y, z), new Vec3(0.0, 0.0, 1.0), SPEED, DAMAGE, 0.03F, 100, null);

        ticks = 0;
        LOG.info("[BULLET-SELFTEST] fired bullet id={} at target id={} (health={}) 8 blocks downrange",
            bullet != null ? bullet.getId() : -1, target != null ? target.getId() : -1, fmt(startHealth));
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (ticks < 0) {
            return;
        }
        ticks++;
        if (ticks < CHECK_AFTER_TICKS) {
            return;
        }

        boolean bulletGone = bullet == null || bullet.isRemoved();
        float endHealth = target != null ? target.getHealth() : startHealth;
        boolean damaged = target != null && endHealth < startHealth - 0.001F;
        boolean pass = bulletGone && damaged;

        LOG.info("[BULLET-SELFTEST] after {} ticks: bulletRemoved={} targetHealth {} -> {} (took {} damage)",
            CHECK_AFTER_TICKS, bulletGone, fmt(startHealth), fmt(endHealth), fmt(startHealth - endHealth));
        LOG.info("[BULLET-SELFTEST] RESULT: {} - MchBullet {} (bulletRemoved={} need=true, damageDealt={} need>0)",
            pass ? "PASS" : "FAIL",
            pass ? "flew downrange, hit the target and dealt damage, then despawned"
                 : (!bulletGone ? "did NOT hit anything (still flying / missed)" : "hit but dealt no damage"),
            bulletGone, fmt(startHealth - endHealth));

        if (target != null) {
            target.discard();
        }
        if (bullet != null && !bullet.isRemoved()) {
            bullet.discard();
        }
        ticks = -1;
        target = null;
        bullet = null;
    }

    private static String fmt(double v) {
        return String.format("%.3f", v);
    }
}
