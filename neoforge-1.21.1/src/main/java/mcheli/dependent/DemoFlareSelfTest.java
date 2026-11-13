package mcheli.dependent;

import mcheli.agnostic.weapon.MCH_WeaponBallistics;
import mcheli.agnostic.weapon.MCH_WeaponInfo;
import mcheli.agnostic.weapon.MCH_WeaponInfoManager;
import mcheli.dependent.entity.MchBullet;
import mcheli.dependent.entity.MchFlare;
import mcheli.dependent.entity.MchHelicopter;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Headless proof of the <b>flare countermeasure decoy</b> (#26): a flare-capable helicopter (ah-1z, {@code FlareType})
 * dispenses flares, and while its burn window is active a guided AA {@link MchBullet} homing on it is decoyed —
 * targetEntity nulled + the round removed AT RANGE (not by impact) — while an UNGUIDED control round nearby is
 * untouched (the decoy filters by {@code isGuided() && isHomingOn(self)}). Also asserts flares actually spawn.
 */
public final class DemoFlareSelfTest extends MchSelfTest {
    private static final int CHECK_AFTER_TICKS = 16;

    private MchHelicopter heli;
    private MchBullet missile;   // GUIDED_AA homing on the heli — should be decoyed
    private MchBullet control;   // unguided — should be ignored by the decoy
    private double missileDecoyDist = -1.0;
    private boolean missileRemoved;
    private boolean sawFlaring;

    @Override
    protected void start() {
        BlockPos spawn = this.origin;
        double baseY = spawn.getY() + 40.0;

        MCH_WeaponInfo aa = MCH_WeaponInfoManager.get("aim92");
        this.heli = MchRegistries.HELI.get().create(this.level);
        if (aa == null || this.heli == null) {
            fail("aim92 config or the heli type unavailable (aim92={}, heli={})", aa, this.heli);
            return;
        }
        this.heli.setConfigName("ah-1z"); // ships FlareType = 3
        this.heli.setPos(spawn.getX() + 0.5, baseY, spawn.getZ() + 0.5);
        this.heli.setNoGravity(true);
        this.level.addFreshEntity(this.heli);
        if (!this.heli.haveFlare()) {
            fail("ah-1z reports no flares");
            return;
        }
        // The decoy + RWR require a rider present (reference MCH_MissileDetector gate) — seat a stand-in pilot.
        if (net.minecraft.world.entity.EntityType.PIG.create(this.level) instanceof net.minecraft.world.entity.Mob p) {
            p.setNoAi(true);
            p.setPos(spawn.getX() + 0.5, baseY, spawn.getZ() + 0.5);
            this.level.addFreshEntity(p);
            this.heli.preferSeat(p, 0);
            p.startRiding(this.heli, true);
        }

        float speed = MCH_WeaponBallistics.initialSpeed(aa.acceleration);
        float accF = MCH_WeaponBallistics.accelerationFactor(aa.acceleration, MCH_WeaponBallistics.isBulletOrRocket(aa.type));
        // Guided round 40 blocks out, homing on the heli (aimed away so it must turn back — but the decoy kills it first).
        // It needs a SHOOTER: the guidance (reference AA/AT onUpdate) drops a guided round whose shootingEntity is null on
        // its first tick, so without one the round would vanish before any decoy could act. A floating golem off to the
        // -X side launches it (clear of the control round's lane at +6).
        Vec3 mPos = new Vec3(spawn.getX() + 0.5, baseY, spawn.getZ() + 40.5);
        net.minecraft.world.entity.Entity shooter = null;
        if (net.minecraft.world.entity.EntityType.IRON_GOLEM.create(this.level) instanceof net.minecraft.world.entity.Mob g) {
            g.setNoAi(true);
            g.setNoGravity(true);
            g.setPos(mPos.x - 6.0, mPos.y, mPos.z);
            this.level.addFreshEntity(g);
            shooter = g;
        }
        this.missile = MchBullet.spawnGuided(this.level, mPos, new Vec3(0, 0, 1), speed, accF, aa.gravity, aa.power, 600,
            shooter, "bullet", 0xFFFFFFFF, aa, this.heli, MchBullet.GUIDED_AA);
        // Unguided control 20 blocks out, flying away — within the 60-block scan but not guided, so never decoyed.
        Vec3 cPos = new Vec3(spawn.getX() + 6.5, baseY, spawn.getZ() + 20.5);
        this.control = MchBullet.spawnWeapon(this.level, cPos, new Vec3(0, 0, 1), speed, accF, 0.0F, aa.power, 600,
            null, "bullet", 0xFF808080, aa);
    }

    @Override
    protected void tick() {
        if (ticks() == 2) {
            this.heli.deployFlare(this.heli.currentFlareType()); // start the burn window
        }
        if (this.heli.isFlareUsing()) {
            this.sawFlaring = true;
        }
        if (this.missile != null && !this.missile.isRemoved()) {
            this.missileDecoyDist = this.missile.distanceTo(this.heli); // last-known distance while alive
        }
        if (this.missile != null && this.missile.isRemoved()) {
            this.missileRemoved = true;
        }

        if (ticks() < CHECK_AFTER_TICKS) {
            return;
        }

        int flareCount = this.level.getEntitiesOfClass(MchFlare.class, area()).size();
        boolean controlAlive = this.control != null && !this.control.isRemoved();

        // missileDecoyDist stays -1 if the round never lived through a tick (e.g. dropped by its own guidance guard).
        boolean decoyedAtRange = this.missileRemoved && this.missileDecoyDist > 8.0;
        boolean flaresSpawned = flareCount > 0;
        boolean pass = decoyedAtRange && flaresSpawned && this.sawFlaring && controlAlive;

        LOG.info("[FLARE-SELFTEST] missileRemoved={} decoyDist={} flares={} flaring={} controlAlive={}",
            this.missileRemoved, String.format("%.2f", this.missileDecoyDist), flareCount, this.sawFlaring, controlAlive);
        finish(pass, "decoyedAtRange={} flaresSpawned={} sawFlaring={} controlUntouched={}",
            decoyedAtRange, flaresSpawned, this.sawFlaring, controlAlive);
    }
}
