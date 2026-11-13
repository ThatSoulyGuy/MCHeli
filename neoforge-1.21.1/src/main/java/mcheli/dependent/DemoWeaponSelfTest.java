package mcheli.dependent;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
 *   <li><b>Cluster bomblets</b> — a Hydra-70 MPSM round splits into its {@code Bomblet} count of children, counted by
 *       identity ({@link MchBullet#isBomblet}), and the siblings fly on without shooting each other down.</li>
 *   <li><b>Fire control</b> — the gatling firing latch, the recoil trigger and the TV-missile hand-off, pinned to the
 *       values recorded before the fire control left the entity ({@code testFireControl}).</li>
 * </ol>
 */
public final class DemoWeaponSelfTest extends MchSelfTest {
    private static final int CHECK_AFTER_TICKS = 20;

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
    private int bombletExpected;

    @Override
    protected int[] areaChunks() {
        return new int[] {-5, 3, -3, 8}; // the heli's rounds and the bomblet children fly down +Z
    }

    @Override
    protected void start() {
        BlockPos spawn = this.origin;
        double y = spawn.getY() + 45.0;

        // ---- Parts 1-3: config loadout / ballistics / switching ----
        MCH_AircraftInfo ah64 = MCH_HeliInfoManager.get("ah-64");
        if (ah64 == null) {
            fail("ah-64 config not loaded");
            return;
        }
        VehicleWeapons weapons = VehicleWeapons.build(ah64, MCH_WeaponInfoManager::get);
        // Selection is per seat now and starts EMPTY until seeded, exactly as the vehicle seeds it when it builds its
        // loadout (MchFireControl.tickServer); no crew aboard -> the pilot gets the first weapon he may use.
        weapons.initSelection(seat -> false);
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
            if (m230s != null) {
                float sp = MCH_WeaponBallistics.initialSpeed(m230s.info.acceleration);
                float fac = MCH_WeaponBallistics.accelerationFactor(m230s.info.acceleration,
                    MCH_WeaponBallistics.isBulletOrRocket(m230s.info.type));
                ballisticsOk = Math.abs(sp - 3.9F) < 1.0e-4F && Math.abs(fac - 2.0F) < 1.0e-4F;
            }
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

        // ---- Part 4b: a real heli fires from its config mounts while its PILOT holds the trigger ----
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
                // The M230 traverses with its operator's yaw, and a fresh mob spawns with a random one (0 to 6.28
                // degrees): face the pig down the hull's heading so the stream flies straight down its column.
                rider.setYRot(0.0F);
                rider.setYHeadRot(0.0F);
                rider.setYBodyRot(0.0F);
                this.level.addFreshEntity(rider);
                // The pilot seat is players-only unless requested; a pig in the gunner seat would leave pilot() null,
                // and the pilot's control state (the trigger held below) is cleared every tick without a pilot.
                heli.preferSeat(rider, 0);
                if (rider.startRiding(heli)) {
                    heli.getControlState().fire = true;
                    this.firingHeli = heli;
                }
            }
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
            // Its own X column on the far side of the area, clear of the direct-fire golem (x≈8.5), the heli's M230
            // stream (x≈24.5) and the tick-2 blast at x≈-8.5 (radius 6), so nothing in the test can strike the children.
            // The children are counted by identity below, not by position. (This check used to flake for two reasons,
            // both gone: under the old rule that a null owner never matched, the 9 siblings of this ownerless cluster
            // shot each other down right after the split, so only about 3 flew on; and the M230 stream, swung by the
            // pig's random spawn yaw, strayed into the old position-based count, which passed only when it padded it.)
            Vec3 bombletOrigin = new Vec3(spawn.getX() - 24.5, y, spawn.getZ() + 8.5);
            float speed = MCH_WeaponBallistics.initialSpeed(mpsm.acceleration);
            float factor = MCH_WeaponBallistics.accelerationFactor(mpsm.acceleration,
                MCH_WeaponBallistics.isBulletOrRocket(mpsm.type));
            String model = mpsm.bulletModelName.isEmpty() ? "rocket" : mpsm.bulletModelName;
            this.bombletParent = MchBullet.spawnWeapon(this.level, bombletOrigin, new Vec3(0.0, 0.0, 1.0),
                speed, factor, mpsm.gravity, mpsm.power, 600, null, model, 0xFFFFFFFF, mpsm);
        }

        // ---- Part 8: the fire-control goldens, hand-ticked to completion right here (see testFireControl). ----
        testFireControl(spawn, y + 100.0);
    }

    @Override
    protected void tick() {
        // The heli's weapon list is built on its first server tick, and the M230 spawns DRY (MaxAmmo=1200, the ammo
        // economy doing its job, orthogonal to the fire path under test): keep the pilot's selection supplied.
        if (this.firingHeli != null && ticks() < 10) {
            this.firingHeli.debugSupplySeatWeapon(0);
        }

        // Detonate the AoE test on tick 2 (targets now indexed), measure the falloff, then remove the targets.
        if (!this.explosionDone && ticks() >= 2 && this.expWi != null && this.expNear != null && this.expFar != null) {
            this.explosionDone = true;
            float nearHp0 = this.expNear.getHealth();
            float farHp0 = this.expFar.getHealth();
            MchExplosion.explode(this.level, this.expCenter, this.expWi.explosion, this.expWi.explosionBlock,
                this.expWi.flaming, true, 0, this.expWi, null, null);
            float nearDmg = nearHp0 - this.expNear.getHealth();
            float farDmg = farHp0 - this.expFar.getHealth();
            this.explosionOk = nearDmg > farDmg && farDmg > 0.0F;
            LOG.info("[WEAPON-SELFTEST] explosion(power={}): near(d2)={} far(d5)={} -> {}",
                this.expWi.explosion, fmt(nearDmg), fmt(farDmg), this.explosionOk ? "OK" : "FAIL");
            this.expNear.discard();
            this.expFar.discard();
        }

        if (ticks() < CHECK_AFTER_TICKS) {
            return;
        }

        boolean directOk = this.directBullet != null && this.directBullet.isRemoved()
            && this.target != null && Math.abs((this.startHealth - this.target.getHealth()) - this.expectedDamage) < 0.5F;

        // The heli's rounds, in its own narrow X-column (its pilot faces down the hull's heading, see start()).
        int vehicleBullets = 0;
        if (this.firingHeli != null) {
            Vec3 hp = this.firingHeli.position();
            AABB col = new AABB(hp.x - 5.0, hp.y - 30.0, hp.z - 5.0, hp.x + 5.0, hp.y + 30.0, hp.z + 400.0);
            vehicleBullets = this.level.getEntitiesOfClass(MchBullet.class, col, b -> b != this.directBullet).size();
        }
        boolean vehicleOk = this.firingHeli != null && vehicleBullets > 0;

        // Bomblets, by identity: the rounds the split created (only the MPSM carrier splits in this test), wherever they
        // flew. The carrier must be gone, it must have made exactly its Bomblet count, and the siblings must still be
        // flying (they share their owners, here none, so they pass through each other); one may have struck something.
        int bombletChildren = 0;
        int bombletsFlying = 0;
        boolean bombletOk = false;
        if (this.bombletParent != null) {
            for (Entity e : spawned()) {
                if (e instanceof MchBullet b && b.isBomblet()) {
                    bombletChildren++;
                    if (!b.isRemoved()) {
                        bombletsFlying++;
                    }
                }
            }
            bombletOk = this.bombletParent.isRemoved() && bombletChildren == this.bombletExpected
                && bombletsFlying >= this.bombletExpected - 1;
        }

        boolean pass = loadoutOk && ballisticsOk && switchOk && directOk && vehicleOk
            && explosionOk && damageFactorOk && bombletOk && fireControlOk;
        LOG.info("[WEAPON-SELFTEST] direct={} vehicle={}({}) bomblets created={} flying={} (need {}) carrierGone={}",
            directOk, vehicleOk, vehicleBullets, bombletChildren, bombletsFlying, this.bombletExpected,
            this.bombletParent != null && this.bombletParent.isRemoved());
        finish(pass, "loadout={} ballistics={} switch={} direct={} vehicle={} explosion={} damageFactor={} bomblets={}"
                + " fireControl={}",
            loadoutOk, ballisticsOk, switchOk, directOk, vehicleOk, explosionOk, damageFactorOk, bombletOk,
            fireControlOk);
    }

    // ---- Part 8: fire-control goldens ----

    /** The fire-control observation, recorded twice on the pre-split tree (identical both runs): the GAU-8 latch holds
     *  4 ticks (WAIT_CLEAR_USED_COUNT), the Hydra pod never sets it; the m1a2 shot bumps the recoil trigger once with
     *  Recoil 1.8 at 45 degrees off the hull; the ka50n's second TV missile (100-tick Delay later) takes over. */
    private static final String FC_EXPECTED = "a board=true sel=gau-8 before=false shots=1 latch=TTTTFFFF sel2=hydra70"
        + " wait=25 shots2=1 latch2=FFFFF ; b board=true sel=rehinmetall_apfsds shots=1 seq=0->1 value=1.8 yaw=45.0"
        + " hull=25.0 latch=FFFFF ; c board=true switches=3 sel=9k121tv_ka50 first=25 second=125 firstActive=true"
        + " after: first=false second=true firstAlive=true";

    private boolean fireControlOk;
    private final java.util.List<net.minecraft.server.level.ServerPlayer> testPlayers = new java.util.ArrayList<>();

    /**
     * The fire-control goldens. Each probe hangs high above the other parts ({@code probeY}, behind them on -Z) and is
     * hand-ticked to completion inside {@link #start}, so nothing it fires ever flies: every round it spawned is
     * discarded at the end, with the probes.
     * <ul>
     *   <li>(a) a-10: one GAU-8 shot (its {@code AddPartRotWeapon}) holds {@code isFiringRecently()} for the recorded
     *       ticks; after a switch to the Hydra-70 pod (no rotating barrel), one rocket leaves it false.</li>
     *   <li>(b) m1a2 at hull yaw 25, the pilot looking at yaw 70: one main-gun shot bumps {@code DATA_RECOIL_SEQ} by
     *       exactly 1, with {@code DATA_RECOIL_VALUE} = the config Recoil and {@code DATA_RECOIL_YAW} = the recorded
     *       aim-relative-to-hull yaw (all read through the reflected accessors); the latch stays false.</li>
     *   <li>(c) ka50n: the pilot switched to the 9K121tv (TV-guided, mode 0) fires twice; after the first shot it is the
     *       active TV missile, after the second only the second is, while the first is still in the world.</li>
     * </ul>
     */
    private void testFireControl(BlockPos spawn, double probeY) {
        StringBuilder obs = new StringBuilder();
        int mark = spawned().size();
        mcheli.dependent.entity.AbstractMchVehicle[] probes = new mcheli.dependent.entity.AbstractMchVehicle[3];
        try {
            // (a) the gatling firing latch.
            mcheli.dependent.entity.MchPlane a10 = MchRegistries.PLANE.get().create(this.level);
            probes[0] = a10;
            a10.setConfigName("a-10");
            Vec3 pa = new Vec3(spawn.getX() - 8.5, probeY, spawn.getZ() - 32.5);
            a10.setPos(pa.x, pa.y, pa.z);
            this.level.addFreshEntity(a10);
            net.minecraft.server.level.ServerPlayer ap = testPlayer("w8a10");
            boolean aBoard = board(ap, a10, 0);
            holdTick(a10, pa); // builds the loadout: the pilot's first weapon, the GAU-8
            String aSel = selectedSlotName(a10);
            a10.debugSupplySeatWeapon(0);
            boolean aBefore = a10.isFiringRecently();
            int aMark = bullets();
            a10.controlState(0).fire = true;
            holdTick(a10, pa);
            a10.controlState(0).fire = false;
            int aShots = bullets() - aMark;
            StringBuilder aLatch = new StringBuilder().append(a10.isFiringRecently() ? 'T' : 'F');
            for (int t = 0; t < 7; t++) {
                holdTick(a10, pa);
                aLatch.append(a10.isFiringRecently() ? 'T' : 'F');
            }
            a10.queueWeaponSwitch(ap, 1);
            holdTick(a10, pa);
            String aSel2 = selectedSlotName(a10);
            a10.debugSupplySeatWeapon(0);
            int aWait = 0;
            while (aWait < 60 && !a10.debugSeatWeapon(0).contains("canFire=true")) {
                holdTick(a10, pa);
                aWait++;
            }
            int aMark2 = bullets();
            a10.controlState(0).fire = true;
            holdTick(a10, pa);
            a10.controlState(0).fire = false;
            int aShots2 = bullets() - aMark2;
            StringBuilder aLatch2 = new StringBuilder().append(a10.isFiringRecently() ? 'T' : 'F');
            for (int t = 0; t < 4; t++) {
                holdTick(a10, pa);
                aLatch2.append(a10.isFiringRecently() ? 'T' : 'F');
            }
            obs.append("a board=").append(aBoard).append(" sel=").append(aSel).append(" before=").append(aBefore)
                .append(" shots=").append(aShots).append(" latch=").append(aLatch).append(" sel2=").append(aSel2)
                .append(" wait=").append(aWait).append(" shots2=").append(aShots2).append(" latch2=").append(aLatch2);

            // (b) the recoil trigger.
            MchTank tk = MchRegistries.TANK.get().create(this.level);
            probes[1] = tk;
            tk.setConfigName("m1a2");
            Vec3 pt = new Vec3(spawn.getX() + 8.5, probeY, spawn.getZ() - 32.5);
            tk.setPos(pt.x, pt.y, pt.z);
            tk.setYRot(25.0F);
            tk.yRotO = 25.0F;
            this.level.addFreshEntity(tk);
            net.minecraft.server.level.ServerPlayer tp = testPlayer("w8tank");
            tp.setYRot(70.0F);
            tp.setXRot(-5.0F);
            boolean bBoard = board(tp, tk, 0);
            holdTick(tk, pt); // builds the loadout: the pilot's first weapon, the 120 mm APFSDS
            String bSel = selectedSlotName(tk);
            tk.debugSupplySeatWeapon(0);
            net.minecraft.network.syncher.EntityDataAccessor<Integer> seqA = accessor("DATA_RECOIL_SEQ");
            net.minecraft.network.syncher.EntityDataAccessor<Float> valueA = accessor("DATA_RECOIL_VALUE");
            net.minecraft.network.syncher.EntityDataAccessor<Float> yawA = accessor("DATA_RECOIL_YAW");
            int seq0 = tk.getEntityData().get(seqA);
            int bMark = bullets();
            tk.controlState(0).fire = true;
            holdTick(tk, pt);
            tk.controlState(0).fire = false;
            int bShots = bullets() - bMark;
            int seq1 = tk.getEntityData().get(seqA);
            float rValue = tk.getEntityData().get(valueA);
            float rYaw = tk.getEntityData().get(yawA);
            StringBuilder bLatch = new StringBuilder().append(tk.isFiringRecently() ? 'T' : 'F');
            for (int t = 0; t < 4; t++) {
                holdTick(tk, pt);
                bLatch.append(tk.isFiringRecently() ? 'T' : 'F');
            }
            obs.append(" ; b board=").append(bBoard).append(" sel=").append(bSel).append(" shots=").append(bShots)
                .append(" seq=").append(seq0).append("->").append(seq1).append(" value=").append(rValue)
                .append(" yaw=").append(rYaw).append(" hull=").append(tk.getYRot()).append(" latch=").append(bLatch);

            // (c) the TV-missile hand-off.
            MchHelicopter kh = MchRegistries.HELI.get().create(this.level);
            probes[2] = kh;
            kh.setConfigName("ka50n");
            Vec3 pk = new Vec3(spawn.getX() + 24.5, probeY, spawn.getZ() - 32.5);
            kh.setPos(pk.x, pk.y, pk.z);
            this.level.addFreshEntity(kh);
            net.minecraft.server.level.ServerPlayer kp = testPlayer("w8ka50");
            boolean cBoard = board(kp, kh, 0);
            holdTick(kh, pk); // builds the loadout
            int cSwitches = 0;
            while (cSwitches < 12 && !selectedSlotName(kh).equalsIgnoreCase("9k121tv_ka50")) {
                kh.queueWeaponSwitch(kp, 1);
                holdTick(kh, pk);
                cSwitches++;
            }
            String cSel = selectedSlotName(kh);
            kh.debugSupplySeatWeapon(0);
            kh.controlState(0).fire = true;
            MchBullet first = null;
            MchBullet second = null;
            int firstTick = -1;
            int secondTick = -1;
            boolean firstActive = false;
            int seen = spawned().size();
            for (int t = 1; t <= 200 && second == null; t++) {
                holdTick(kh, pk);
                for (int i = seen; i < spawned().size(); i++) {
                    if (spawned().get(i) instanceof MchBullet b) {
                        if (first == null) {
                            first = b;
                            firstTick = t;
                            firstActive = kh.isActiveTvMissile(b);
                        } else if (second == null) {
                            second = b;
                            secondTick = t;
                        }
                    }
                }
                seen = spawned().size();
            }
            kh.controlState(0).fire = false;
            boolean firstAfter = kh.isActiveTvMissile(first);
            boolean secondAfter = kh.isActiveTvMissile(second);
            boolean firstAlive = first != null && !first.isRemoved();
            obs.append(" ; c board=").append(cBoard).append(" switches=").append(cSwitches).append(" sel=").append(cSel)
                .append(" first=").append(firstTick).append(" second=").append(secondTick)
                .append(" firstActive=").append(firstActive).append(" after: first=").append(firstAfter)
                .append(" second=").append(secondAfter).append(" firstAlive=").append(firstAlive);
        } catch (RuntimeException e) {
            obs.append(" <threw ").append(e).append('>');
        } finally {
            for (net.minecraft.server.level.ServerPlayer p : this.testPlayers) {
                p.stopRiding();
            }
            for (int i = mark; i < spawned().size(); i++) {
                if (spawned().get(i) instanceof MchBullet b) {
                    b.discard();
                }
            }
            for (mcheli.dependent.entity.AbstractMchVehicle p : probes) {
                if (p != null) {
                    p.discard();
                }
            }
        }
        String o = obs.toString();
        this.fireControlOk = FC_EXPECTED.equals(o);
        LOG.info("[WEAPON-SELFTEST] fire control observed: {}", o);
    }

    @Override
    protected void cleanup() {
        for (net.minecraft.server.level.ServerPlayer p : this.testPlayers) {
            p.stopRiding();
        }
    }

    private net.minecraft.server.level.ServerPlayer testPlayer(String label) {
        net.minecraft.server.level.ServerPlayer p = newTestPlayer(label);
        p.setPos(this.origin.getX() + 0.5, this.origin.getY() + 20.0, this.origin.getZ() + 0.5);
        this.testPlayers.add(p);
        return p;
    }

    /** Seat {@code p} in {@code seat} of {@code v} (a forced mount, as a scripted crew would board). */
    private static boolean board(Entity p, mcheli.dependent.entity.AbstractMchVehicle v, int seat) {
        v.preferSeat(p, seat);
        return p.startRiding(v, true) && v.seatIndexOf(p) == seat;
    }

    /** One hand tick of {@code v}, then put it back at {@code p} at rest (a probe that hangs in the air). */
    private static void holdTick(mcheli.dependent.entity.AbstractMchVehicle v, Vec3 p) {
        v.tick();
        v.setPos(p.x, p.y, p.z);
        v.setDeltaMovement(Vec3.ZERO);
    }

    /** The pilot seat's selected weapon slot name (synced selection, config-resolved slot), or "-". */
    private static String selectedSlotName(mcheli.dependent.entity.AbstractMchVehicle v) {
        int idx = v.selectedWeaponIndex(0);
        WeaponSlot s = idx >= 0 ? v.weaponAt(idx) : null;
        return s != null ? s.weaponName : "-";
    }

    /** The rounds that joined the world since this test started. */
    private int bullets() {
        int n = 0;
        for (Entity e : spawned()) {
            if (e instanceof MchBullet) {
                n++;
            }
        }
        return n;
    }

    /** The package-private synced accessor {@code name} of AbstractMchVehicle, by reflection. */
    @SuppressWarnings("unchecked")
    private static <T> net.minecraft.network.syncher.EntityDataAccessor<T> accessor(String name) {
        try {
            java.lang.reflect.Field f = mcheli.dependent.entity.AbstractMchVehicle.class.getDeclaredField(name);
            f.setAccessible(true);
            return (net.minecraft.network.syncher.EntityDataAccessor<T>) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("no accessor " + name, e);
        }
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
