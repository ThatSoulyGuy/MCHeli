package mcheli.dependent;

import mcheli.agnostic.weapon.MCH_WeaponBallistics;
import mcheli.agnostic.weapon.MCH_WeaponInfo;
import mcheli.agnostic.weapon.MCH_WeaponInfoManager;
import mcheli.dependent.entity.MchBullet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Headless proof of the <b>server-side guided-missile homing</b> (#25), isolated from the fire path: it launches a
 * guided {@link MchBullet} straight ahead with {@link MchBullet#spawnGuided} and a real AA-missile config (aim92), at a
 * live off-axis target, then measures that (1) the guided round CURVES onto the target (its min distance collapses),
 * (2) an identical UNGUIDED control fired parallel never approaches it, and (3) the guided round DETONATES. The way a
 * client's lock reaches that guided round (control payload -> {@code fireSelectedWeapon}) is
 * {@link DemoLockFireSelfTest}.
 */
public final class DemoHomingSelfTest extends MchSelfTest {
    private static final int CHECK_AFTER_TICKS = 120;

    private LivingEntity target;
    private float targetStartHealth;
    private MchBullet guided;
    private MchBullet straight;

    private double guidedMinDist = Double.MAX_VALUE;
    private double straightMinDist = Double.MAX_VALUE;
    private boolean guidedDetonated;

    @Override
    protected int[] areaChunks() {
        return new int[] {-2, 2, -2, 7}; // the flight path runs ~60 blocks down +Z
    }

    @Override
    protected void start() {
        BlockPos spawn = this.origin;
        double baseY = spawn.getY() + 40.0;

        MCH_WeaponInfo aa = MCH_WeaponInfoManager.get("aim92"); // Type=AAMissile, Accel 3, ProximityFuseDist 1
        if (aa == null || !aa.type.equalsIgnoreCase("aamissile")) {
            fail("aim92 AA config not available (got {})", aa == null ? "null" : aa.type);
            return;
        }

        Vec3 launch = new Vec3(spawn.getX() + 0.5, baseY, spawn.getZ() + 0.5);
        LivingEntity shooter = spawnGolem(launch); // the missile's shooter — excluded from its own collision
        Vec3 targetPos = new Vec3(spawn.getX() + 15.5, baseY + 2.0, spawn.getZ() + 60.5); // ahead (+Z) AND off to +X
        this.target = spawnGolem(targetPos);
        if (shooter == null || this.target == null) {
            fail("could not spawn the shooter/target golems");
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

        LOG.info("[HOMING-SELFTEST] launched guided + control AA rounds (speed={}, target off-axis by ~15 blocks)",
            String.format("%.2f", speed));
    }

    @Override
    protected void tick() {
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

        if (ticks() < CHECK_AFTER_TICKS) {
            return;
        }

        float targetDamage = this.target != null ? this.targetStartHealth - this.target.getHealth() : 0.0F;
        boolean homedClose = this.guidedMinDist < 5.0;
        boolean controlStayedFar = this.straightMinDist > 10.0;
        boolean clearHomingEffect = this.guidedMinDist < this.straightMinDist - 6.0;
        boolean pass = homedClose && controlStayedFar && clearHomingEffect && this.guidedDetonated;

        LOG.info("[HOMING-SELFTEST] guidedMinDist={} straightMinDist={} detonated={} targetDmg={}",
            fmt(this.guidedMinDist), fmt(this.straightMinDist), this.guidedDetonated, fmt(targetDamage));
        finish(pass, "homedClose={} controlFar={} clearHoming={} detonated={}",
            homedClose, controlStayedFar, clearHomingEffect, this.guidedDetonated);
    }

    private LivingEntity spawnGolem(Vec3 pos) {
        if (EntityType.IRON_GOLEM.create(this.level) instanceof LivingEntity g) {
            if (g instanceof Mob m) {
                m.setNoAi(true);
            }
            g.setNoGravity(true); // both float in open air
            g.setPos(pos.x, pos.y, pos.z);
            this.level.addFreshEntity(g);
            return g;
        }
        return null;
    }

    private static String fmt(double v) {
        return String.format("%.3f", v);
    }
}
