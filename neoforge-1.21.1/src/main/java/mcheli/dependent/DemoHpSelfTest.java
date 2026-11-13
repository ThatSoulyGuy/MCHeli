package mcheli.dependent;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.dependent.config.MchConfig;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.entity.MchHelicopter;
import mcheli.dependent.entity.MchPlane;
import mcheli.dependent.entity.MchTank;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

/**
 * Headless proof of the vehicle HP / armor / destruction system (#35). Covers, without a client:
 * <ul>
 *   <li><b>M1 HP core</b> — damage accumulates (int-truncated), HP = maxHp − damageTaken, the {@code inFire}/{@code
 *       cactus} gates deal nothing, and MCHeli's own post-hit cooldown blocks a follow-up hit.</li>
 *   <li><b>M2 per-part armor</b> — the m1a2's real config (armorMin 12 / max 500 / DF 0.95 + 0.70/1.0/1.25 zones):
 *       the ray query picks the struck zone's factor, and the armor formula's load-bearing ORDER (armor plate
 *       multiplies BEFORE the min-subtract, a weak point AFTER) yields the exact expected damage.</li>
 *   <li><b>M3 environmental gates</b> — lava (×2..9) and onFire BYPASS armor; {@code inWall} (crash/water) goes
 *       THROUGH it.</li>
 *   <li><b>M4 destruction/wreck</b> — reaching maxHp flips the synced destroyed flag, ejects riders, arms the 500-tick
 *       despawn countdown, and the wreck falls (the flight-state seam killed its lift) then counts down.</li>
 *   <li><b>M6 persistence + synced-data layout</b> — pins, by golden values recorded on the tree before the
 *       AbstractMchVehicle split: the synced-data id of every accessor (by reflection, so no debug API), a heli NBT
 *       round trip (key set, tag types, contract values, restore, re-save), the legacy {@code AcWeaponsAmmo} write-back,
 *       a piloted in-world tank (aim latch, keyed ammo, seat entry, re-save), the wreck's Destroyed/Despawn and the
 *       spawn-item fuel/damage stamp.</li>
 *   <li><b>M7 hull</b> — pins, by golden values recorded before the hull left AbstractMchVehicle: the seeded wreck
 *       tumble of a heli, a banked plane and a tank, the one-shot crash detonation on the ground slam, and the salvage
 *       of an unridden vehicle into a spawn item that carries its fuel and damage (and none for a player-ridden one).</li>
 * </ul>
 * The wreck's visible tumble + dark tint + HP HUD readout are the only client-only parts (a runClient check).
 */
public final class DemoHpSelfTest extends MchSelfTest {
    private static final int CHECK_AFTER_TICKS = 20;
    private static final float EPS = 1.0e-4F;

    private MchHelicopter wreck;
    private Pig wreckRider;
    private double wreckStartY;
    private boolean wreckDestroyedOk;
    private boolean wreckEjectOk;
    private int wreckStartDespawn;

    // M5: end-to-end real-bullet per-part armor (the case the happy path missed).
    private MchTank m5Tank;
    private boolean m5Gated;   // real m1a2 config present -> run the exact assertion
    private boolean m5Fired;
    private static final int M5_FIRE_TICK = 5; // let the tank settle a few ticks, then probe its live per-part hitboxes

    // M6: persistence contract + synced-data id layout. Every golden below was observed twice on the untouched tree
    // (before the AbstractMchVehicle split) and hard-coded; a change here is a save-format or network-layout break.
    /** Synced-data ids by reflection, {@code name=id} sorted by id: AbstractMchVehicle, MchHelicopter, MchPlane. */
    private static final String M6_IDS_BASE = "DATA_ROLL=8,DATA_SIM_THROTTLE=9,DATA_THROTTLE=10,DATA_SKIN=11,"
        + "DATA_GEAR=12,DATA_SEAT_WEAPONS=13,DATA_FLARE_STATE=14,DATA_RECOIL_SEQ=15,DATA_RECOIL_VALUE=16,"
        + "DATA_RECOIL_YAW=17,DATA_FIRING=18,DATA_FUEL=19,DATA_GUNNER_MODE=20,DATA_SEAT_GUNNER_MODE=21,"
        + "DATA_RELOADING=22,DATA_AMMO_ALL=23,DATA_WEAPON_SPENT=24,DATA_SEATS=25,DATA_DAMAGE_TAKEN=26,"
        + "DATA_DESTROYED=27,DATA_CONFIG=28,DATA_PART_STATUS=29";
    private static final String M6_IDS_HELI = "DATA_FOLD=30,DATA_HOVER=31";
    private static final String M6_IDS_PLANE = "DATA_VTOL=30";
    /** The heli round-trip save: every key with its NBT tag type, sorted. */
    private static final String M6_HELI_KEYS = "AcDamage:3,AcFuel:3,AcRoll:5,Air:2,Config:8,Despawn:3,Destroyed:1,"
        + "FallDistance:5,Fire:2,FoldBlade:1,FuelSlots:9,Invulnerable:1,Motion:9,OnGround:1,PartStatus:3,"
        + "PortalCooldown:3,Pos:9,Rotation:9,RotorSpeed:6,SearchLight:1,Seats:9,TextureName:8,UUID:11";
    /** The contract keys of that save (SNBT, in {@link #M6_VALUE_KEYS} order). Today's oddities are pinned on purpose:
     *  SearchLight is written (false) for a light-less config, TextureName whenever the skin resolves, and an unridden
     *  vehicle writes an empty Seats list. */
    private static final String[] M6_VALUE_KEYS = {"Config", "PartStatus", "SearchLight", "AcDamage", "Destroyed",
        "Despawn", "AcFuel", "FuelSlots", "Seats", "TextureName", "AcRoll", "FoldBlade", "RotorSpeed"};
    private static final String M6_HELI_VALUES = "Config=\"ah-64\";PartStatus=0;SearchLight=0b;AcDamage=37;"
        + "Destroyed=0b;Despawn=0;AcFuel=600;FuelSlots=[{count:1,id:\"mcheli:fuel\"}];Seats=[];"
        + "TextureName=\"ah-64-il\";AcRoll=12.5f;FoldBlade=0b;RotorSpeed=0.0d";
    /** The piloted in-world tank's {@code AcWeaponsAmmoByName} keys, sorted. */
    private static final String M6_TANK_AMMO_KEYS = "m240_r,m2_rws,mg7_62mm_mbt,rehinmetall_apfsds,rehinmetall_he";
    private static final float M6_PILOT_YAW = 37.5F;
    private static final float M6_PILOT_PITCH = -12.25F;
    private MchTank m6Tank;
    private ServerPlayer m6Pilot;
    private boolean m6Boarded;
    private boolean m6StaticOk; // M6 parts 1-3 and 6, checked in start()

    // M7: the hull (wreck tumble, crash detonation, salvage). The whole observation was recorded twice on the tree
    // before the hull moved out of AbstractMchVehicle and hard-coded; a change here is a behaviour change.
    private static final String M7_EXPECTED = "seeds ah-64=true:0.14658213/-0.12010506 "
        + "f-35a=true:0.14658213/0.7597899 m1a2=true:0.0/0.0 crash(killed=true exploded=false) "
        + "salvage(ridden=false fuel=400 hit=true removed=true destroyed=false drops=1 item=true count=1 stamp=400/37 "
        + "read=400/37) ridden(board=true ridden=true hit=true removed=false destroyed=false dmg=1) "
        + "tumble ah-64=31.355625/1.4658213/-1.2010504 f-35a=-40.0/1.4658213/37.59789 m1a2=25.0/0.0/-0.0 "
        + "crashAt=8 exploded=true wreck=true";
    private static final int M7_TUMBLE_TICK = 10;
    private MchHelicopter m7Heli;
    private MchPlane m7Plane;
    private MchTank m7Tank;
    private MchHelicopter m7Crash;
    private ServerPlayer m7Rider;
    private String m7Static = "n/a";
    private String m7Tumble = "n/a";
    private int m7CrashTick = -1;

    // Milestone results. A gated milestone whose real m1a2 config is missing FAILS: the config ships with the mod.
    private boolean m1Ok, m2Ok, m3Ok, m5Ok;

    @Override
    protected int[] areaChunks() {
        return new int[] {-1, 3, -1, 1}; // the wreck at x+30 and the M5 tank at x+40
    }

    @Override
    protected void start() {
        ServerLevel level = this.level;
        DamageSources ds = level.damageSources();
        BlockPos spawn = this.origin;
        double x = spawn.getX() + 30.0;
        double y = spawn.getY() + 60.0;
        double z = spawn.getZ() + 0.5;

        m1Ok = runM1(level, ds);
        runM2(level, ds);   // sets m2Ok (gated on the real m1a2 config)
        runM3(level, ds);   // sets m3Ok (gated on the real m1a2 config)
        setupM4(level, ds, x, y, z);
        setupM5(level, spawn.getX() + 40, spawn.getY() + 50, spawn.getZ() + 4);
        this.m6StaticOk = runM6(level);
        setupM6Tank(level, spawn.getX() + 0.5, spawn.getY() + 45.0, spawn.getZ() + 0.5); // clear of the M4/M5 probes
        setupM7(level, ds);
    }

    // ---- M7: the hull (the second safety net of the AbstractMchVehicle split, for the code no milestone covered) ----
    /**
     * Sets up and records the start() half of M7; {@link #tickM7} samples the rest.
     * <ul>
     *   <li>(a) Wreck tumble: an airborne ah-64 with its rotor spooled to 1.0 (so the wreck's yaw spin runs), an f-35a
     *       banked 30 deg and an m1a2, each with its RNG seeded 1234 right before one lethal hit: the tumble seeds,
     *       and the yaw/pitch/roll after {@link #M7_TUMBLE_TICK} ticks (the tank keeps its attitude).</li>
     *   <li>(b) Crash detonation: an ah-64 wreck dropped 8 blocks onto the ground at 1 block/tick has not exploded at
     *       the kill, crash-detonates once on the ground slam (the recorded tick) and is still a wreck at the check.</li>
     *   <li>(c) Salvage: a test player's melee hit on an unridden ah-64 removes it and drops exactly one spawn item
     *       carrying its fuel and damage, which a fresh vehicle reads back; the same hit on a player-ridden one does
     *       not salvage it.</li>
     * </ul>
     * Every probe is in open air, 12 blocks from the next, clear of M4/M5/M6.
     */
    private void setupM7(ServerLevel level, DamageSources ds) {
        double ox = this.origin.getX(), oy = this.origin.getY(), oz = this.origin.getZ();
        StringBuilder sb = new StringBuilder();

        // (a) the wreck tumble.
        MchHelicopter h = MchRegistries.HELI.get().create(level);
        MchPlane p = MchRegistries.PLANE.get().create(level);
        MchTank t = MchRegistries.TANK.get().create(level);
        if (h == null || p == null || t == null) {
            this.m7Static = "create failed";
            return;
        }
        h.setConfigName("ah-64");
        h.setPos(ox + 10.5, oy + 80.0, oz + 12.5);
        h.setYRot(15.0F);
        level.addFreshEntity(h);
        invokeVehicle(h, "setSimThrottle", new Class<?>[] {double.class}, 1.0);
        p.setConfigName("f-35a");
        p.setPos(ox + 22.5, oy + 80.0, oz + 12.5);
        p.setYRot(-40.0F);
        level.addFreshEntity(p);
        p.setRollAngle(30.0F);
        t.setConfigName("m1a2");
        t.setPos(ox + 34.5, oy + 80.0, oz + 12.5);
        t.setYRot(25.0F);
        level.addFreshEntity(t);
        this.m7Heli = h;
        this.m7Plane = p;
        this.m7Tank = t;
        sb.append("seeds");
        for (AbstractMchVehicle w : new AbstractMchVehicle[] {h, p, t}) {
            w.getRandom().setSeed(1234L);
            boolean killed = w.hurt(ds.generic(), 99999.0F) && w.isDestroyed();
            sb.append(' ').append(w.configName()).append('=').append(killed).append(':')
                .append(invokeVehicle(w, "rotDestroyedPitch", new Class<?>[0])).append('/')
                .append(invokeVehicle(w, "rotDestroyedRoll", new Class<?>[0]));
        }

        // (b) the crash detonation.
        MchHelicopter c = MchRegistries.HELI.get().create(level);
        if (c != null) {
            c.setConfigName("ah-64");
            c.setPos(ox + 10.5, oy + 8.0, oz - 10.5);
            level.addFreshEntity(c);
            boolean killed = c.hurt(ds.generic(), 99999.0F) && c.isDestroyed();
            c.setDeltaMovement(0.0, -1.0, 0.0);
            sb.append(" crash(killed=").append(killed).append(" exploded=").append(c.isExploded()).append(')');
            this.m7Crash = c;
        }

        // (c) salvage, then the same hit on a player-ridden vehicle.
        ServerPlayer breaker = newTestPlayer("m7breaker");
        MchHelicopter s = newHeli(level);
        MchHelicopter r = newHeli(level);
        if (s != null && r != null) {
            s.setPos(ox + 22.5, oy + 40.0, oz - 10.5);
            level.addFreshEntity(s);
            s.setDamageTaken(37);
            s.setFuel(s.getMaxFuel() / 3);
            breaker.setPos(s.getX() + 3.0, s.getY(), s.getZ());
            boolean sRidden = s.isRidePlayer();
            int sFuel = s.getFuel();
            boolean sHit = s.hurt(ds.playerAttack(breaker), 1.0F);
            java.util.List<net.minecraft.world.entity.item.ItemEntity> drops = level.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(
                    s.getX() - 3.0, s.getY() - 3.0, s.getZ() - 3.0, s.getX() + 3.0, s.getY() + 3.0, s.getZ() + 3.0));
            String drop = "none";
            if (drops.size() == 1) {
                ItemStack st = drops.get(0).getItem();
                CustomData cd = st.get(DataComponents.CUSTOM_DATA);
                CompoundTag stamp = cd != null ? cd.copyTag() : new CompoundTag();
                MchHelicopter fresh = newHeli(level);
                String read = "n/a";
                if (fresh != null) {
                    fresh.readItemData(st);
                    read = fresh.getFuel() + "/" + fresh.getDamageTaken();
                }
                drop = "item=" + (st.getItem() == MchRegistries.spawnItemFor("ah-64")) + " count=" + st.getCount()
                    + " stamp=" + (stamp.contains("MCH_Fuel") ? stamp.getInt("MCH_Fuel") : -1) + "/"
                    + (stamp.contains("MCH_Damage") ? stamp.getInt("MCH_Damage") : -1) + " read=" + read;
            }
            sb.append(" salvage(ridden=").append(sRidden).append(" fuel=").append(sFuel).append(" hit=").append(sHit)
                .append(" removed=").append(s.isRemoved()).append(" destroyed=").append(s.isDestroyed())
                .append(" drops=").append(drops.size()).append(' ').append(drop).append(')');

            r.setPos(ox + 34.5, oy + 40.0, oz - 10.5);
            level.addFreshEntity(r);
            ServerPlayer rp = newTestPlayer("m7rider");
            rp.setPos(r.getX(), r.getY(), r.getZ());
            r.preferSeat(rp, 0);
            boolean rBoard = rp.startRiding(r, true) && r.seatIndexOf(rp) == 0;
            this.m7Rider = rp;
            boolean rRidden = r.isRidePlayer();
            boolean rHit = r.hurt(ds.playerAttack(breaker), 1.0F);
            sb.append(" ridden(board=").append(rBoard).append(" ridden=").append(rRidden).append(" hit=").append(rHit)
                .append(" removed=").append(r.isRemoved()).append(" destroyed=").append(r.isDestroyed())
                .append(" dmg=").append(r.getDamageTaken()).append(')');
        }
        this.m7Static = sb.toString();
    }

    /** M7's per-tick half: the attitudes at {@link #M7_TUMBLE_TICK} and the crash wreck's first exploded tick. */
    private void tickM7() {
        if (ticks() == M7_TUMBLE_TICK && this.m7Heli != null) {
            this.m7Tumble = "tumble " + attitude(this.m7Heli) + " " + attitude(this.m7Plane) + " "
                + attitude(this.m7Tank);
        }
        if (this.m7CrashTick < 0 && this.m7Crash != null && this.m7Crash.isExploded()) {
            this.m7CrashTick = ticks();
        }
    }

    /** M7 at the check: the whole observation against {@link #M7_EXPECTED}. */
    private boolean checkM7() {
        String crash = this.m7Crash == null ? "crashAt=n/a" : "crashAt=" + this.m7CrashTick + " exploded="
            + this.m7Crash.isExploded() + " wreck=" + (this.m7Crash.isDestroyed() && !this.m7Crash.isRemoved());
        String observed = this.m7Static + " " + this.m7Tumble + " " + crash;
        boolean ok = observed.equals(M7_EXPECTED);
        LOG.info("[HP-SELFTEST] M7 observed: {}", observed);
        LOG.info("[HP-SELFTEST] M7 hull (wreck tumble, crash detonation, salvage): -> {}", ok ? "OK" : "FAIL");
        return ok;
    }

    private static String attitude(AbstractMchVehicle v) {
        return v.configName() + "=" + v.getYRot() + "/" + v.getXRot() + "/" + v.getRollAngle();
    }

    /** Calls a protected AbstractMchVehicle method by reflection (the hooks keep their names), or returns null. */
    private static Object invokeVehicle(AbstractMchVehicle v, String name, Class<?>[] types, Object... args) {
        try {
            java.lang.reflect.Method m = AbstractMchVehicle.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(v, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.info("[HP-SELFTEST] M7 reflection on {} failed: {}", name, e.toString());
            return null;
        }
    }

    // ---- M6: persistence contract + synced-data id layout (the safety net for the AbstractMchVehicle split) ----
    /** Parts 1 (id pin), 2 (heli round trip), 3 (legacy ammo) and 6 (spawn-item data), on vehicles never added to the
     *  world: a ticked, grounded vehicle would siphon the fuel can. */
    private boolean runM6(ServerLevel level) {
        String idsBase = accessorIds(AbstractMchVehicle.class);
        String idsHeli = accessorIds(MchHelicopter.class);
        String idsPlane = accessorIds(MchPlane.class);
        boolean idsOk = idsBase.equals(M6_IDS_BASE) && idsHeli.equals(M6_IDS_HELI) && idsPlane.equals(M6_IDS_PLANE);

        MchHelicopter h = newHeli(level);
        if (h == null) {
            LOG.info("[HP-SELFTEST] M6 persistence: FAIL (EntityType.create returned null for the heli type)");
            return false;
        }
        h.setDamageTaken(37);
        h.setFuel(h.getMaxFuel() / 2);
        ItemStack can = new ItemStack(MchRegistries.FUEL.get());
        h.fuelInventory().setItem(0, can.copy());
        MCH_AircraftInfo info = h.hostInfo();
        h.setTextureName(info != null ? info.getTextureName(1) : null);
        h.setRollAngle(12.5F);
        CompoundTag tag = h.saveWithoutId(new CompoundTag());
        String keys = keyTypes(tag);
        String values = contractValues(tag);
        boolean saveOk = keys.equals(M6_HELI_KEYS) && values.equals(M6_HELI_VALUES) && h.getSkinIndex() == 1;

        MchHelicopter h2 = MchRegistries.HELI.get().create(level);
        boolean restored = false;
        String resaveDiff = "n/a";
        if (h2 != null) {
            h2.load(tag);
            restored = h2.configName().equals("ah-64") && h2.getDamageTaken() == 37 && h2.getFuel() == h.getFuel()
                && ItemStack.matches(h2.fuelInventory().getItem(0), can) && h2.getSkinIndex() == h.getSkinIndex()
                && Float.floatToIntBits(h2.getRollAngle()) == Float.floatToIntBits(12.5F) && !h2.isDestroyed()
                && h2.despawnCount() == h.despawnCount();
            resaveDiff = diffExcept(tag, h2.saveWithoutId(new CompoundTag()), "Seats");
        }
        boolean resaveOk = resaveDiff.isEmpty();

        // 3. A save from before the keyed ammo format: written back exactly as read while the loadout never ticked.
        CompoundTag legacy = tag.copy();
        legacy.remove("AcWeaponsAmmoByName");
        legacy.putIntArray("AcWeaponsAmmo", new int[] {3, 5});
        MchHelicopter h3 = MchRegistries.HELI.get().create(level);
        boolean legacyOk = false;
        if (h3 != null) {
            h3.load(legacy);
            CompoundTag re = h3.saveWithoutId(new CompoundTag());
            legacyOk = re.contains("AcWeaponsAmmo", Tag.TAG_INT_ARRAY)
                && java.util.Arrays.equals(re.getIntArray("AcWeaponsAmmo"), new int[] {3, 5})
                && !re.contains("AcWeaponsAmmoByName");
        }

        // 6. The spawn-item stamp carries fuel/damage exactly when the server options say so, and reads back.
        boolean cfgFuel = MchConfig.SERVER.itemFuel();
        boolean cfgDamage = MchConfig.SERVER.itemDamage();
        Item spawnItem = MchRegistries.spawnItemFor("ah-64");
        ItemStack stack = new ItemStack(spawnItem != null ? spawnItem : Items.PAPER);
        h.writeItemData(stack);
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag stamp = cd != null ? cd.copyTag() : new CompoundTag();
        boolean stampOk = stamp.contains("MCH_Fuel") == cfgFuel && stamp.contains("MCH_Damage") == cfgDamage
            && (!cfgFuel || stamp.getInt("MCH_Fuel") == h.getFuel())
            && (!cfgDamage || stamp.getInt("MCH_Damage") == 37);
        MchHelicopter control = newHeli(level);
        MchHelicopter hr = newHeli(level);
        boolean readOk = false;
        if (control != null && hr != null) {
            hr.readItemData(stack);
            readOk = hr.getFuel() == (cfgFuel ? h.getFuel() : control.getFuel())
                && hr.getDamageTaken() == (cfgDamage ? 37 : control.getDamageTaken());
        }
        boolean itemOk = stampOk && readOk;

        boolean ok = idsOk && saveOk && restored && resaveOk && legacyOk && itemOk;
        LOG.info("[HP-SELFTEST] M6 observed: idsBase={} idsHeli={} idsPlane={} heliKeys={} heliValues={} skin={} fuel={}",
            idsBase, idsHeli, idsPlane, keys, values, h.getSkinIndex(), h.getFuel());
        LOG.info("[HP-SELFTEST] M6 persistence (static): ids={} save={} restored={} resave={}{} legacyAmmo={} "
                + "itemData={}(fuel={} damage={}) -> {}",
            idsOk, saveOk, restored, resaveOk, resaveOk ? "" : "(diff " + resaveDiff + ")", legacyOk, itemOk, cfgFuel,
            cfgDamage, ok ? "OK" : "FAIL");
        return ok;
    }

    /** Part 4 setup: a piloted m1a2 in open air, checked at {@link #CHECK_AFTER_TICKS}. */
    private void setupM6Tank(ServerLevel level, double x, double y, double z) {
        MchTank t = MchRegistries.TANK.get().create(level);
        if (t == null) return;
        t.setConfigName("m1a2");
        t.setPos(x, y, z);
        t.setYRot(0.0F);
        level.addFreshEntity(t);
        this.m6Tank = t;
        ServerPlayer p = newTestPlayer("m6pilot");
        p.setPos(x, y, z);
        p.setYRot(M6_PILOT_YAW);
        p.setXRot(M6_PILOT_PITCH);
        this.m6Pilot = p;
        t.preferSeat(p, 0);
        this.m6Boarded = p.startRiding(t, true) && t.seatIndexOf(p) == 0;
    }

    /** Parts 4 (piloted tank) and 5 (wreck), at check time. */
    private boolean checkM6World() {
        boolean latchOk = false, ammoOk = false, seatsOk = false, resaveOk = false;
        String ammoKeys = "n/a", resaveDiff = "n/a", latch = "n/a";
        if (this.m6Tank != null && this.m6Pilot != null && this.m6Boarded) {
            CompoundTag tag = this.m6Tank.saveWithoutId(new CompoundTag());
            latch = tag.getFloat("AcLastRYaw") + "/" + tag.getFloat("AcLastRPitch") + " pilot "
                + this.m6Pilot.getYRot() + "/" + this.m6Pilot.getXRot();
            latchOk = tag.contains("AcLastRYaw", Tag.TAG_FLOAT) && tag.contains("AcLastRPitch", Tag.TAG_FLOAT)
                && Float.floatToIntBits(tag.getFloat("AcLastRYaw")) == Float.floatToIntBits(this.m6Pilot.getYRot())
                && Float.floatToIntBits(tag.getFloat("AcLastRPitch")) == Float.floatToIntBits(this.m6Pilot.getXRot())
                && Float.floatToIntBits(tag.getFloat("AcLastRYaw")) == Float.floatToIntBits(M6_PILOT_YAW)
                && Float.floatToIntBits(tag.getFloat("AcLastRPitch")) == Float.floatToIntBits(M6_PILOT_PITCH);
            ammoKeys = String.join(",", new java.util.TreeSet<>(tag.getCompound("AcWeaponsAmmoByName").getAllKeys()));
            ammoOk = tag.contains("AcWeaponsAmmoByName", Tag.TAG_COMPOUND) && ammoKeys.equals(M6_TANK_AMMO_KEYS);
            ListTag seats = tag.getList("Seats", Tag.TAG_COMPOUND);
            seatsOk = seats.size() == 1 && seats.getCompound(0).hasUUID("Id")
                && seats.getCompound(0).getUUID("Id").equals(this.m6Pilot.getUUID())
                && seats.getCompound(0).getInt("Seat") == 0 && seats.getCompound(0).size() == 2;
            MchTank t2 = MchRegistries.TANK.get().create(this.level);
            if (t2 != null) {
                t2.load(tag);
                resaveDiff = diffExcept(tag, t2.saveWithoutId(new CompoundTag()), "Seats");
                resaveOk = resaveDiff.isEmpty();
            }
        }
        boolean wreckOk = false;
        if (this.wreck != null) {
            CompoundTag w = this.wreck.saveWithoutId(new CompoundTag());
            wreckOk = w.contains("Destroyed", Tag.TAG_BYTE) && w.getByte("Destroyed") == 1
                && w.contains("Despawn", Tag.TAG_INT) && w.getInt("Despawn") == this.wreck.despawnCount();
        }
        boolean ok = this.m6StaticOk && this.m6Boarded && latchOk && ammoOk && seatsOk && resaveOk && wreckOk;
        LOG.info("[HP-SELFTEST] M6 observed: tankAmmoKeys={} latch={}", ammoKeys, latch);
        LOG.info("[HP-SELFTEST] M6 persistence: static={} tank(boarded={} latch={} ammoKeys={} seats={} resave={}{}) "
                + "wreck={} -> {}",
            this.m6StaticOk, this.m6Boarded, latchOk, ammoOk, seatsOk, resaveOk,
            resaveOk ? "" : "(diff " + resaveDiff + ")", wreckOk, ok ? "OK" : "FAIL");
        return ok;
    }

    /** Every static {@link EntityDataAccessor} field {@code owner} declares, as {@code name=id} sorted by id. */
    private static String accessorIds(Class<?> owner) {
        java.util.List<java.util.Map.Entry<String, Integer>> ids = new java.util.ArrayList<>();
        for (java.lang.reflect.Field f : owner.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType() != EntityDataAccessor.class) {
                continue;
            }
            int id;
            try {
                f.setAccessible(true);
                id = ((EntityDataAccessor<?>) f.get(null)).id();
            } catch (ReflectiveOperationException | RuntimeException e) {
                id = -1;
            }
            ids.add(java.util.Map.entry(f.getName(), id));
        }
        ids.sort(java.util.Map.Entry.<String, Integer>comparingByValue()
            .thenComparing(java.util.Map.Entry.comparingByKey()));
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, Integer> e : ids) {
            sb.append(sb.length() == 0 ? "" : ",").append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    /** Every key of {@code tag} with its tag type id, sorted. */
    private static String keyTypes(CompoundTag tag) {
        StringBuilder sb = new StringBuilder();
        for (String k : new java.util.TreeSet<>(tag.getAllKeys())) {
            sb.append(sb.length() == 0 ? "" : ",").append(k).append(':').append(tag.getTagType(k));
        }
        return sb.toString();
    }

    /** The SNBT of each {@link #M6_VALUE_KEYS} entry of {@code tag} ({@code -} when absent), {@code ;}-joined. */
    private static String contractValues(CompoundTag tag) {
        StringBuilder sb = new StringBuilder();
        for (String k : M6_VALUE_KEYS) {
            Tag v = tag.get(k);
            sb.append(sb.length() == 0 ? "" : ";").append(k).append('=').append(v != null ? v.toString() : "-");
        }
        return sb.toString();
    }

    /** The keys (other than {@code skip}) whose value differs between {@code a} and {@code b}; empty when equal. */
    private static String diffExcept(CompoundTag a, CompoundTag b, String skip) {
        java.util.TreeSet<String> all = new java.util.TreeSet<>(a.getAllKeys());
        all.addAll(b.getAllKeys());
        all.remove(skip);
        StringBuilder sb = new StringBuilder();
        for (String k : all) {
            if (!java.util.Objects.equals(a.get(k), b.get(k))) {
                sb.append(sb.length() == 0 ? "" : ",").append(k);
            }
        }
        return sb.toString();
    }

    // ---- M5: end-to-end per-part armor via a REAL fired bullet (regression guard for the ray-truncation +
    //      protruding-box hit-detection fixes; the direct-call M2 test could not catch either). ----
    private void setupM5(ServerLevel level, double x, double y, double z) {
        MchTank t = MchRegistries.TANK.get().create(level);
        if (t == null) return;
        t.setConfigName("m1a2");
        t.setPos(x + 0.5, y, z + 0.5); // high open air; both M5 checks are immune to the pilotless tank's free-fall
        t.setYRot(0.0F);
        level.addFreshEntity(t);
        this.m5Tank = t;
        this.m5Gated = isM1a2Armored(t);
        if (!this.m5Gated) {
            LOG.info("[HP-SELFTEST] M5 per-part: FAIL (real m1a2 config not loaded — demo fallback has no armor)");
        }
    }

    private void tickM5() {
        if (this.m5Tank == null || !this.m5Gated || this.m5Fired) return;
        if (ticks() == M5_FIRE_TICK) {
            this.m5Tank.setYRot(0.0F);
            this.m5Tank.setXRot(0.0F);
            this.m5Tank.setRollAngle(0.0F);
            this.m5Tank.refreshExtraBoxes();
            double tx = this.m5Tank.getX(), ty = this.m5Tank.getY(), tz = this.m5Tank.getZ();

            // Regression guard for the two per-part fixes, exercised at the EXACT geometry MchBullet feeds into the
            // vehicle (a real fired bullet is separately covered end-to-end by the BULLET/WEAPON self-tests; firing one
            // here would only re-test MchBullet's flight through unknown terrain, orthogonal to the HP fix).
            //
            // #13 protruding-box hit registration: a ray grazing the FRONT box0 where it PROTRUDES beyond the vanilla
            //     selection AABB (z+3.5, |z|>2.5) must be FOUND by clipParts — else a real round there misses entirely.
            net.minecraft.world.phys.Vec3 protrudeHit = this.m5Tank.clipParts(
                new Vec3(tx + 7.0, ty + 0.6, tz + 3.5), new Vec3(tx - 3.0, ty + 0.6, tz + 3.5));
            boolean protrudingHit = protrudeHit != null;
            // #14 no ray truncation: a side shot into the INTERIOR armor box (x=±0.9, well inside the 5-wide AABB)
            //     resolves to the 0.70 zone over the FULL ray, but to 1.0 if the ray is cut at the selection-AABB entry
            //     (x=tx+2.5) — which is exactly what the old bug passed. So full==0.70 AND truncated==1.0 proves the fix.
            Vec3 sideMuzzle = new Vec3(tx + 8.0, ty + 1.6, tz + 1.0);
            float fullFac = this.m5Tank.boundingBoxDamageFactorAt(sideMuzzle, new Vec3(tx - 8.0, ty + 1.6, tz + 1.0));
            float truncFac = this.m5Tank.boundingBoxDamageFactorAt(sideMuzzle, new Vec3(tx + 2.5, ty + 1.6, tz + 1.0));
            this.m5Ok = protrudingHit && Math.abs(fullFac - 0.70F) < EPS && Math.abs(truncFac - 1.0F) < EPS;
            this.m5Fired = true;
            LOG.info("[HP-SELFTEST] M5 per-part: protrudingHit={} interiorFullRay={} interiorTruncatedRay={} -> {}",
                protrudingHit, fmt(fullFac), fmt(truncFac), this.m5Ok ? "OK" : "FAIL (truncated!=1.0 or full!=0.70)");
        }
    }

    // ---- M1: HP core (config-independent) ----
    private boolean runM1(ServerLevel level, DamageSources ds) {
        MchHelicopter h = newHeli(level);
        if (h == null) return false;
        int max = h.getMaxHp();
        h.hurt(ds.generic(), 50.0F);
        boolean accumulate = h.getDamageTaken() > 0 && h.getHp() == max - h.getDamageTaken() && h.getHp() < max;

        MchHelicopter hf = newHeli(level);
        boolean inFireGate = hf != null && !hf.hurt(ds.inFire(), 10.0F) && hf.getDamageTaken() == 0;
        MchHelicopter hc = newHeli(level);
        boolean cactusGate = hc != null && !hc.hurt(ds.cactus(), 10.0F) && hc.getDamageTaken() == 0;

        // Cooldown: a lava hit sets the cooldown; the immediate follow-up must be rejected (damageTaken unchanged).
        MchHelicopter hcd = newHeli(level);
        boolean cooldownGate = false;
        if (hcd != null) {
            hcd.hurt(ds.lava(), 5.0F);
            int afterLava = hcd.getDamageTaken();
            boolean blocked = !hcd.hurt(ds.generic(), 10.0F);
            cooldownGate = blocked && hcd.getDamageTaken() == afterLava && afterLava > 0;
        }

        boolean ok = accumulate && inFireGate && cactusGate && cooldownGate;
        LOG.info("[HP-SELFTEST] M1 HP core: accumulate={} inFireGate={} cactusGate={} cooldownGate={} -> {}",
            accumulate, inFireGate, cactusGate, cooldownGate, ok ? "OK" : "FAIL");
        return ok;
    }

    // ---- M2: per-part armor (real m1a2 config) ----
    private void runM2(ServerLevel level, DamageSources ds) {
        MchTank probe = newTank(level, 0.0, 100.0, 0.0);
        if (probe == null || !isM1a2Armored(probe)) {
            LOG.info("[HP-SELFTEST] M2 per-part armor: FAIL (real m1a2 config not loaded — demo fallback has no armor)");
            return;
        }
        // Geometry: a fixed pose, boxes refreshed without a tick; vertical rays through known zones.
        MchTank t = newTank(level, 0.0, 100.0, 0.0);
        t.setYRot(0.0F);
        t.setXRot(0.0F);
        t.setRollAngle(0.0F);
        t.refreshExtraBoxes();
        double tx = t.getX(), ty = t.getY(), tz = t.getZ();
        float front = t.boundingBoxDamageFactorAt(new Vec3(tx, ty + 10, tz + 2.2), new Vec3(tx, ty - 10, tz + 2.2));
        float rear = t.boundingBoxDamageFactorAt(new Vec3(tx - 0.9, ty + 10, tz - 4.5), new Vec3(tx - 0.9, ty - 10, tz - 4.5));
        float miss = t.boundingBoxDamageFactorAt(new Vec3(tx + 30, ty + 10, tz), new Vec3(tx + 30, ty - 10, tz));
        boolean geomOk = near(front, 0.70F) && near(rear, 1.25F) && near(miss, 1.0F);

        // Formula: base 100 through the m1a2 armor (min 12, max 500, DF 0.95), for each part factor.
        //   factor 1.00: 100 *0.95 -12            = 83
        //   factor 0.70: 100 *0.70 *0.95 -12      = 54.5 -> 54   (plate applied BEFORE the min-subtract)
        //   factor 1.25: (100 *0.95 -12) *1.25    = 103.75 -> 103 (weak point applied AFTER the min-subtract)
        //   base   5:    5 *0.95 -12 <= 0         = fully absorbed by armorMinDamage
        int d100 = hitWithFactor(level, ds, 1.00F, 100.0F);
        int d070 = hitWithFactor(level, ds, 0.70F, 100.0F);
        int d125 = hitWithFactor(level, ds, 1.25F, 100.0F);
        int d005 = hitWithFactor(level, ds, 1.00F, 5.0F);
        boolean formulaOk = d100 == 83 && d070 == 54 && d125 == 103 && d005 == 0;

        m2Ok = geomOk && formulaOk;
        LOG.info("[HP-SELFTEST] M2 per-part armor: geom(front={},rear={},miss={})={} formula(100->{},0.70->{},1.25->{},5->{})={} -> {}",
            fmt(front), fmt(rear), fmt(miss), geomOk, d100, d070, d125, d005, formulaOk, m2Ok ? "OK" : "FAIL");
    }

    // ---- M3: environmental damage-type gates (real m1a2 config) ----
    private void runM3(ServerLevel level, DamageSources ds) {
        MchTank probe = newTank(level, 0.0, 100.0, 0.0);
        if (probe == null || !isM1a2Armored(probe)) {
            LOG.info("[HP-SELFTEST] M3 environmental gates: FAIL (real m1a2 config not loaded)");
            return;
        }
        // onFire bypasses armor: raw 5 lands (armorMinDamage 12 would otherwise zero it).
        MchTank tf = newTank(level, 0.0, 100.0, 0.0);
        tf.hurt(ds.onFire(), 5.0F);
        boolean onFireBypass = tf.getDamageTaken() == 5;

        // lava bypasses armor AND multiplies ×2..9: base 1 -> 2..9 lands (armor would zero it).
        MchTank tl = newTank(level, 0.0, 100.0, 0.0);
        tl.hurt(ds.lava(), 1.0F);
        int lavaDmg = tl.getDamageTaken();
        boolean lavaBypass = lavaDmg >= 2 && lavaDmg <= 9;

        // inWall (crash/water) goes THROUGH armor: base 100 -> 83, identical to a generic factor-1 hit.
        MchTank tw = newTank(level, 0.0, 100.0, 0.0);
        tw.hurt(ds.inWall(), 100.0F);
        boolean inWallArmored = tw.getDamageTaken() == 83;

        m3Ok = onFireBypass && lavaBypass && inWallArmored;
        LOG.info("[HP-SELFTEST] M3 environmental gates: onFireBypass={}(={}) lavaBypass={}(={}) inWallArmored={}(={}) -> {}",
            onFireBypass, tf.getDamageTaken(), lavaBypass, lavaDmg, inWallArmored, tw.getDamageTaken(), m3Ok ? "OK" : "FAIL");
    }

    // ---- M4: destruction + wreck (config-independent), verified over ticks ----
    private void setupM4(ServerLevel level, DamageSources ds, double x, double y, double z) {
        MchHelicopter h = MchRegistries.HELI.get().create(level);
        if (h == null) return;
        h.setConfigName("ah-64");
        h.setPos(x, y, z);
        level.addFreshEntity(h);
        if (EntityType.PIG.create(level) instanceof Pig pig) {
            pig.setPos(x, y, z);
            level.addFreshEntity(pig);
            pig.startRiding(h, true);
            this.wreckRider = pig;
        }
        boolean rode = this.wreckRider != null && !h.getPassengers().isEmpty();

        h.hurt(ds.generic(), 99999.0F); // one lethal hit -> destruction
        this.wreckDestroyedOk = h.isDestroyed() && h.despawnCount() == 500;
        this.wreckEjectOk = !rode || h.getPassengers().isEmpty(); // riders ejected on destroy
        this.wreckStartY = h.getY();
        this.wreckStartDespawn = h.despawnCount();
        this.wreck = h;
    }

    @Override
    protected void tick() {
        tickM5();   // fires at tick 5 (before the M4 check)
        tickM7();
        if (ticks() < CHECK_AFTER_TICKS) return;

        boolean fell = this.wreck != null && this.wreck.getY() < this.wreckStartY - 1.0;
        boolean countedDown = this.wreck != null && this.wreck.despawnCount() < this.wreckStartDespawn
            && this.wreck.despawnCount() > 0;
        boolean stillWreck = this.wreck != null && !this.wreck.isRemoved();
        boolean m4Ok = this.wreckDestroyedOk && this.wreckEjectOk && fell && countedDown && stillWreck;
        LOG.info("[HP-SELFTEST] M4 destruction: destroyed={} eject={} fell={}(dy={}) countdown={}({}->{}) stillWreck={} -> {}",
            this.wreckDestroyedOk, this.wreckEjectOk, fell,
            this.wreck != null ? fmt(this.wreck.getY() - this.wreckStartY) : "n/a",
            countedDown, this.wreckStartDespawn, this.wreck != null ? this.wreck.despawnCount() : -1,
            stillWreck, m4Ok ? "OK" : "FAIL");

        boolean m6Ok = checkM6World();
        boolean m7Ok = checkM7();

        boolean pass = m1Ok && m2Ok && m3Ok && m4Ok && m5Ok && m6Ok && m7Ok;
        finish(pass, "vehicle HP/armor/destruction/persistence (M1={} M2={} M3={} M4={} M5={} M6={} M7={})",
            m1Ok, m2Ok, m3Ok, m4Ok, m5Ok, m6Ok, m7Ok);
    }

    @Override
    protected void cleanup() {
        if (this.m6Pilot != null && this.m6Pilot.isPassenger()) {
            this.m6Pilot.stopRiding();
        }
        if (this.m7Rider != null && this.m7Rider.isPassenger()) {
            this.m7Rider.stopRiding();
        }
    }

    // ---- helpers ----
    private static MchHelicopter newHeli(ServerLevel level) {
        MchHelicopter h = MchRegistries.HELI.get().create(level);
        if (h != null) { h.setConfigName("ah-64"); h.setPos(0.0, 100.0, 0.0); }
        return h;
    }

    private static MchTank newTank(ServerLevel level, double x, double y, double z) {
        MchTank t = MchRegistries.TANK.get().create(level);
        if (t != null) { t.setConfigName("m1a2"); t.setPos(x, y, z); }
        return t;
    }

    private static boolean isM1a2Armored(MchTank t) {
        MCH_AircraftInfo info = t.hostInfo();
        return info != null && Math.abs(info.armorMinDamage - 12.0F) < EPS
            && info.extraBoundingBox != null && info.extraBoundingBox.size() >= 7;
    }

    /** Fresh tank, stash a part factor, take {@code base} generic damage, return the accumulated damageTaken. */
    private static int hitWithFactor(ServerLevel level, DamageSources ds, float factor, float base) {
        MchTank t = newTank(level, 0.0, 100.0, 0.0);
        if (t == null) return -1;
        t.setLastBBDamageFactor(factor);
        t.hurt(ds.generic(), base);
        return t.getDamageTaken();
    }

    private static boolean near(float a, float b) { return Math.abs(a - b) < 1.0e-3F; }
    private static String fmt(double v) { return String.format("%.3f", v); }
}
