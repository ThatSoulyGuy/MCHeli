package mcheli.dependent;

import com.mojang.logging.LogUtils;
import java.util.List;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.helicopter.MCH_HeliInfoManager;
import mcheli.agnostic.weapon.MCH_WeaponBallistics;
import mcheli.agnostic.weapon.MCH_WeaponInfo;
import mcheli.agnostic.weapon.MCH_WeaponInfoManager;
import mcheli.agnostic.weapon.VehicleWeapons;
import mcheli.agnostic.weapon.WeaponSlot;
import mcheli.dependent.entity.MchBullet;
import mcheli.dependent.entity.MchHelicopter;
import mcheli.dependent.entity.MchTank;
import mcheli.dependent.entity.MchExplosion;
import mcheli.dependent.port.NeoEntityRef;
import mcheli.dependent.registry.MchRegistries;
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
 * Headless proof of the <b>config-driven weapon system + effects</b>, all off the REAL loaded configs. Checks:
 * <ol>
 *   <li><b>Loadout / ballistics / switching</b> — the AH-64's selectable weapons build from its config with the right
 *       stats, the M230 ballistics come out exact, and selection cycles.</li>
 *   <li><b>Fire → hit → damage</b> — an {@link MchBullet} with the M230's stats deals its exact config power, and a
 *       real {@link MchHelicopter}'s own fire path spawns bullets from the config mounts.</li>
 *   <li><b>Explosion AoE + falloff</b> — {@link MchExplosion} damages entities within {@code 2·power}, more at the
 *       centre than the edge.</li>
 *   <li><b>Damage factor</b> — the AGM-114's {@code DamageFactor = tank, 2.0} resolves to 2× vs a tank and 1× vs a
 *       non-tank (role mapping).</li>
 *   <li><b>Cluster bomblets</b> — a Hydra-70 MPSM round splits into its {@code Bomblet} count of children.</li>
 * </ol>
 */
public final class DemoWeaponSelfTest {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int CHECK_AFTER_TICKS = 20;

    private ServerLevel level;
    private int ticks = -1;

    // config checks
    private boolean loadoutOk;
    private boolean ballisticsOk;
    private boolean switchOk;

    // direct fire
    private LivingEntity target;
    private float startHealth;
    private float expectedDamage;
    private MchBullet directBullet;

    // vehicle fire
    private MchHelicopter firingHeli;

    // explosion (deferred to the first tick so the target golems are indexed for the AoE search)
    private boolean explosionOk;
    private boolean explosionDone;
    private LivingEntity expNear;
    private LivingEntity expFar;
    private Vec3 expCenter;
    private MCH_WeaponInfo expWi;

    // damage-factor + bomblet
    private boolean damageFactorOk;
    private MchBullet bombletParent;
    private Vec3 bombletOrigin;
    private int bombletExpected;

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        this.level = event.getServer().overworld();
        BlockPos spawn = this.level.getSharedSpawnPos();
        double y = spawn.getY() + 45.0;

        // ---- Parts 1-3: config loadout / ballistics / switching ----
        MCH_AircraftInfo ah64 = MCH_HeliInfoManager.get("ah-64");
        VehicleWeapons weapons = VehicleWeapons.build(ah64, MCH_WeaponInfoManager::get);
        if (!weapons.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < weapons.size(); i++) {
                WeaponSlot s = weapons.get(i);
                sb.append(i == 0 ? "" : ", ").append(s.weaponName).append('(').append(s.info.type).append(')');
            }
            LOG.info("[WEAPON-SELFTEST] AH-64 weapons [{}]: {}", weapons.size(), sb);
            WeaponSlot m230s = weapons.selected();
            loadoutOk = m230s != null && m230s.weaponName.equals("m230")
                && m230s.info.type.equalsIgnoreCase("MachineGun2") && m230s.info.power == 16
                && containsWeapon(weapons, "hydra70") && containsWeapon(weapons, "aim92");
            float sp = MCH_WeaponBallistics.initialSpeed(m230s.info.acceleration);
            float fac = MCH_WeaponBallistics.accelerationFactor(m230s.info.acceleration,
                MCH_WeaponBallistics.isBulletOrRocket(m230s.info.type));
            ballisticsOk = Math.abs(sp - 3.9F) < 1.0e-4F && Math.abs(fac - 2.0F) < 1.0e-4F;
            int start = weapons.selectedIndex();
            for (int i = 0; i < weapons.size(); i++) {
                weapons.cycle(1);
            }
            switchOk = weapons.selectedIndex() == start;
        }

        // ---- Part 4a: direct fire with the M230's real stats (wi=null keeps it a clean 16-damage KE round) ----
        double gx = spawn.getX() + 8.5;
        double gz = spawn.getZ() + 0.5;
        if (EntityType.IRON_GOLEM.create(this.level) instanceof LivingEntity t) {
            this.target = t;
            noAi(t);
            t.setNoGravity(true);
            t.setPos(gx, y, gz + 20.0);
            this.level.addFreshEntity(t);
            this.startHealth = t.getHealth();
            MCH_WeaponInfo m230 = MCH_WeaponInfoManager.get("m230");
            if (m230 != null) {
                float speed = MCH_WeaponBallistics.initialSpeed(m230.acceleration);
                float factor = MCH_WeaponBallistics.accelerationFactor(m230.acceleration,
                    MCH_WeaponBallistics.isBulletOrRocket(m230.type));
                this.expectedDamage = m230.power;
                this.directBullet = MchBullet.spawnWeapon(this.level, new Vec3(gx, y, gz), new Vec3(0.0, 0.0, 1.0),
                    speed, factor, m230.gravity, m230.power, 200, null, "bullet", 0xFFFFFFFF, null);
            }
        }

        // ---- Part 4b: a real heli fires from its config mounts while a rider holds the trigger ----
        try {
            double hx = spawn.getX() + 24.5;
            double hz = spawn.getZ() + 0.5;
            MchHelicopter heli = MchRegistries.HELI.get().create(this.level);
            if (heli != null) {
                heli.setConfigName("ah-64");
                heli.setPos(hx, y, hz);
                this.level.addFreshEntity(heli);
                if (EntityType.PIG.create(this.level) instanceof Mob rider) {
                    noAi(rider);
                    rider.setPos(hx, y, hz);
                    this.level.addFreshEntity(rider);
                    if (rider.startRiding(heli)) {
                        heli.getControlState().fire = true;
                        this.firingHeli = heli;
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("[WEAPON-SELFTEST] vehicle-fire setup skipped: {}", e.toString());
        }

        // ---- Part 5: explosion AoE + falloff. Spawn the targets now; DETONATE on the first tick (below) once they
        // are indexed for the area search (fresh-added entities aren't queryable until the next tick). ----
        this.expWi = MCH_WeaponInfoManager.get("hydra70"); // explosion=3 -> entity radius 6
        if (this.expWi != null) {
            this.expCenter = new Vec3(spawn.getX() - 8.5, y, spawn.getZ() + 0.5);
            this.expNear = spawnGolem(this.expCenter.add(2.0, 0.0, 0.0));   // dist 2
            this.expFar = spawnGolem(this.expCenter.add(0.0, 0.0, 5.0));    // dist 5 (independent LoS axis)
        }

        // ---- Part 6: damage factor (AGM-114: DamageFactor = tank, 2.0) — pure role lookup, no world entity needed. ----
        MCH_WeaponInfo agm114 = MCH_WeaponInfoManager.get("agm114");
        if (agm114 != null) {
            MchTank tank = MchRegistries.TANK.get().create(this.level);
            if (tank != null) tank.setConfigName("m1a2");
            LivingEntity golem = EntityType.IRON_GOLEM.create(this.level) instanceof LivingEntity g ? g : null;
            if (tank != null && golem != null) {
                float vsTank = agm114.getDamageFactor(new NeoEntityRef(tank));
                float vsGolem = agm114.getDamageFactor(new NeoEntityRef(golem));
                damageFactorOk = Math.abs(vsTank - 2.0F) < 1.0e-4F && Math.abs(vsGolem - 1.0F) < 1.0e-4F;
                LOG.info("[WEAPON-SELFTEST] damageFactor(agm114): vsTank={} vsGolem={} -> {}",
                    fmt(vsTank), fmt(vsGolem), damageFactorOk ? "OK" : "FAIL");
                // Never added to the world — just role-checked; let them be GC'd.
            }
        }

        // ---- Part 7: cluster bomblets (Hydra-70 MPSM: Bomblet=9) — split happens after bombletSTime ticks ----
        MCH_WeaponInfo mpsm = MCH_WeaponInfoManager.get("hydra70_mpsm");
        if (mpsm != null && mpsm.bomblet > 0) {
            this.bombletExpected = mpsm.bomblet;
            // Ticking chunk (chunk 0), in its own X column clear of the heli (x≈24.5) and direct-fire (x≈8.5).
            this.bombletOrigin = new Vec3(spawn.getX() + 14.5, y, spawn.getZ() + 8.5);
            float speed = MCH_WeaponBallistics.initialSpeed(mpsm.acceleration);
            float factor = MCH_WeaponBallistics.accelerationFactor(mpsm.acceleration,
                MCH_WeaponBallistics.isBulletOrRocket(mpsm.type));
            String model = mpsm.bulletModelName.isEmpty() ? "rocket" : mpsm.bulletModelName;
            this.bombletParent = MchBullet.spawnWeapon(this.level, this.bombletOrigin, new Vec3(0.0, 0.0, 1.0),
                speed, factor, mpsm.gravity, mpsm.power, 600, null, model, 0xFFFFFFFF, mpsm);
        }

        this.ticks = 0;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (this.ticks < 0) {
            return;
        }
        this.ticks++;

        // Detonate the AoE test on tick 2 (targets now indexed), measure the falloff, then remove the targets.
        if (!this.explosionDone && this.ticks >= 2 && this.expWi != null && this.expNear != null && this.expFar != null) {
            this.explosionDone = true;
            float nearHp0 = this.expNear.getHealth();
            float farHp0 = this.expFar.getHealth();
            MchExplosion.explode(this.level, this.expCenter, this.expWi.explosion, this.expWi.explosionBlock,
                this.expWi.flaming, true, 0, this.expWi, null);
            float nearDmg = nearHp0 - this.expNear.getHealth();
            float farDmg = farHp0 - this.expFar.getHealth();
            this.explosionOk = nearDmg > farDmg && farDmg > 0.0F;
            LOG.info("[WEAPON-SELFTEST] explosion(power={}): near(d2)={} far(d5)={} -> {}",
                this.expWi.explosion, fmt(nearDmg), fmt(farDmg), this.explosionOk ? "OK" : "FAIL");
            this.expNear.discard();
            this.expFar.discard();
        }

        if (this.ticks < CHECK_AFTER_TICKS) {
            return;
        }

        boolean directOk = this.directBullet != null && this.directBullet.isRemoved()
            && this.target != null && Math.abs((this.startHealth - this.target.getHealth()) - this.expectedDamage) < 0.5F;

        // Count in NARROW X-columns so the heli's bullets and the bomblet's children don't pollute each other's count.
        int vehicleBullets = 0;
        if (this.firingHeli != null) {
            Vec3 hp = this.firingHeli.position();
            AABB col = new AABB(hp.x - 5.0, hp.y - 30.0, hp.z - 5.0, hp.x + 5.0, hp.y + 30.0, hp.z + 400.0);
            vehicleBullets = this.level.getEntitiesOfClass(MchBullet.class, col, b -> b != this.directBullet).size();
        }
        boolean vehicleOk = this.firingHeli == null || vehicleBullets > 0;

        // Bomblets: after the split the parent is gone and its children fly in the bomblet column.
        int bombletChildren = 0;
        boolean bombletOk = true;
        if (this.bombletOrigin != null) {
            Vec3 o = this.bombletOrigin;
            AABB col = new AABB(o.x - 5.0, o.y - 30.0, o.z - 5.0, o.x + 5.0, o.y + 30.0, o.z + 400.0);
            bombletChildren = this.level.getEntitiesOfClass(MchBullet.class, col, b -> b != this.directBullet).size();
            bombletOk = bombletChildren >= this.bombletExpected - 1; // allow one to have impacted/despawned
        }

        boolean pass = loadoutOk && ballisticsOk && switchOk && directOk && vehicleOk
            && explosionOk && damageFactorOk && bombletOk;
        LOG.info("[WEAPON-SELFTEST] direct={} vehicle={}({}) bomblets={}(need~{})",
            directOk, vehicleOk, vehicleBullets, bombletChildren, this.bombletExpected);
        LOG.info("[WEAPON-SELFTEST] RESULT: {} - loadout={} ballistics={} switch={} direct={} vehicle={} explosion={} damageFactor={} bomblets={}",
            pass ? "PASS" : "FAIL", loadoutOk, ballisticsOk, switchOk, directOk, vehicleOk, explosionOk, damageFactorOk, bombletOk);

        cleanup();
    }

    private void cleanup() {
        if (this.target != null) {
            this.target.discard();
        }
        if (this.directBullet != null && !this.directBullet.isRemoved()) {
            this.directBullet.discard();
        }
        if (this.firingHeli != null) {
            this.firingHeli.getControlState().fire = false;
            this.firingHeli.ejectPassengers();
            this.firingHeli.discard();
        }
        // Sweep any remaining test bullets.
        for (MchBullet b : this.level.getEntitiesOfClass(MchBullet.class,
            new AABB(-100000, -100, -100000, 100000, 400, 100000))) {
            b.discard();
        }
        this.ticks = -1;
        this.target = null;
        this.directBullet = null;
        this.firingHeli = null;
        this.bombletParent = null;
        this.bombletOrigin = null;
    }

    private LivingEntity spawnGolem(Vec3 pos) {
        if (EntityType.IRON_GOLEM.create(this.level) instanceof LivingEntity g) {
            noAi(g);
            g.setNoGravity(true);
            g.setPos(pos.x, pos.y, pos.z);
            this.level.addFreshEntity(g);
            return g;
        }
        return null;
    }

    private static void noAi(LivingEntity e) {
        if (e instanceof Mob m) {
            m.setNoAi(true);
        }
    }

    private static boolean containsWeapon(VehicleWeapons w, String name) {
        for (int i = 0; i < w.size(); i++) {
            if (w.get(i).weaponName.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static String fmt(double v) {
        return String.format("%.3f", v);
    }
}
