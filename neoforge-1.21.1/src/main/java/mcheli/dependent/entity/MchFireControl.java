package mcheli.dependent.entity;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.weapon.MCH_WeaponBallistics;
import mcheli.agnostic.weapon.MCH_WeaponInfo;
import mcheli.agnostic.weapon.MCH_WeaponInfoManager;
import mcheli.agnostic.weapon.RotBarrel;
import mcheli.agnostic.weapon.VehicleWeapons;
import mcheli.agnostic.weapon.WeaponAim;
import mcheli.agnostic.weapon.WeaponSlot;
import mcheli.agnostic.weapon.WeaponSwitchQueue;
import mcheli.dependent.registry.MchSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The server fire control of one vehicle.
 * <ul>
 *   <li>The config-driven loadout ({@link VehicleWeapons}), built lazily on the first weapon tick, with the saved
 *       ammo restored then (by save key, or a legacy array mapped through its slot order) and the ammo NBT.</li>
 *   <li>The per-seat weapon-switch queue ({@link WeaponSwitchQueue}) and the weapon-mode switch.</li>
 *   <li>The weapon tick: the fire-control counters, the queued switches, the lock audit ({@link MchLockAudits}, which
 *       it owns), each seat's fire while its trigger is held, then the weapon publishing and the resupply-GUI gate.</li>
 *   <li>{@code fireSelectedWeapon}: the round-robin mount, the aim, the muzzle, the dispersion, the per-type spawn, the
 *       report, the destruct, the TV missile, the gatling firing latch and the recoil trigger.</li>
 *   <li>Resupply from the player's inventory, reload-all, the HUD mortar distance and the self-test hooks.</li>
 * </ul>
 * It is the only writer of DATA_FIRING, DATA_RELOADING and DATA_RECOIL_SEQ/VALUE/YAW. The entity keeps the public
 * one-line delegates, the package bridges {@code weaponLoadout()} and {@code weaponInfinityAmmo(Entity)}, the
 * {@code fireBodyYaw}/{@code fireBodyPitch} hooks and the synced reads {@code isFiringRecently}/{@code isPilotReloading}.
 */
final class MchFireControl {
    private final AbstractMchVehicle v;
    /** The server lock audit (one per vehicle, created with this component). */
    private final MchLockAudits lockAudits;

    MchFireControl(AbstractMchVehicle v) {
        this.v = v;
        this.lockAudits = new MchLockAudits(v);
    }

    // Config-driven weapon loadout (server-authoritative), built lazily from weaponHostInfo(). Net pending weapon-cycle
    // steps: each one-shot switch payload adds ±1, and the whole accumulator is drained on the next server tick, so two
    // presses that batch into a single tick both count instead of coalescing to one.
    private VehicleWeapons weapons;
    private final WeaponSwitchQueue switchQueue = new WeaponSwitchQueue();

    private int firedCooldownTicks;                 // SERVER: ticks since the last shot (publishes DATA_FIRING while >0)

    /** Ticks left of the post-close tail (reference {@code supplyAmmoWait}, set to 20 while the GUI is open). */
    private int supplyAmmoWait;

    /** Ammo totals read from NBT by save key, applied once the weapon list exists (it is built lazily on the first
     *  weapon tick). Until then they are also what {@link #saveAmmo} writes back. */
    private java.util.Map<String, Integer> savedAmmo;
    /** A legacy (by slot position) {@code AcWeaponsAmmo} array, migrated to keys when the loadout is built. */
    private int[] savedLegacyAmmo;

    /** SERVER: the latest TV-guided missile this vehicle fired (reference {@code setTVMissile}); only it obeys the
     *  operator, an older one flies on straight. */
    private MchBullet tvMissile;

    // Reference getLandInDistance cache: recomputed every 5 ticks, held in between (MCH_EntityAircraft:4726-4747).
    private int landInDistanceTick = Integer.MIN_VALUE;
    private double landInDistance = -1.0;

    /** A config change: the loadout rebuilds against the new config on the next weapon tick. The saved ammo survives
     *  (it is applied when the loadout builds). */
    void onConfigChanged() {
        this.weapons = null;
    }

    /** The live weapon loadout, null until the server builds it (behind the entity's {@code weaponLoadout()}). */
    VehicleWeapons weaponLoadout() {
        return this.weapons;
    }

    /** The server lock audit. */
    MchLockAudits lockAudits() {
        return this.lockAudits;
    }

    /** NBT: the saved ammo totals, applied when the loadout is built. */
    void loadAmmo(CompoundTag tag) {
        // Ammo totals by weapon (save key, VehicleWeapons.ammoKeys). A save from before the keyed format holds only the
        // legacy AcWeaponsAmmo array, by the slot positions of its time; it is mapped to keys once the loadout exists.
        this.savedAmmo = null;
        this.savedLegacyAmmo = null;
        if (tag.contains("AcWeaponsAmmoByName", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag a = tag.getCompound("AcWeaponsAmmoByName");
            this.savedAmmo = new java.util.HashMap<>();
            for (String k : a.getAllKeys()) {
                this.savedAmmo.put(k, a.getInt(k));
            }
        } else if (tag.contains("AcWeaponsAmmo", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
            this.savedLegacyAmmo = tag.getIntArray("AcWeaponsAmmo");
        }
    }

    /** NBT: the live loadout's totals, else the restored totals not yet applied, else the legacy array unmigrated. */
    void saveAmmo(CompoundTag tag) {
        if (this.weapons != null) {
            // Keyed by weapon, not slot position, so a change to the selectable-type set (or a config edit that adds
            // or drops a weapon) never moves a total onto another weapon.
            CompoundTag ammo = new CompoundTag();
            java.util.List<String> keys = this.weapons.ammoKeys();
            for (int i = 0; i < keys.size(); i++) {
                ammo.putInt(keys.get(i), this.weapons.get(i).totalAmmo()); // magazine + reserve; -1 = no economy
            }
            tag.put("AcWeaponsAmmoByName", ammo);
        } else if (this.savedAmmo != null) {
            // Loaded but never weapon-ticked (e.g. in a border chunk that is not entity-ticking): the loadout was
            // never built, so hand the restored totals straight back instead of saving the vehicle dry.
            CompoundTag ammo = new CompoundTag();
            this.savedAmmo.forEach(ammo::putInt);
            tag.put("AcWeaponsAmmoByName", ammo);
        } else if (this.savedLegacyAmmo != null) {
            tag.putIntArray("AcWeaponsAmmo", this.savedLegacyAmmo); // still unmigrated: written back as it was read
        }
    }

    /**
     * CLIENT: where {@code user}'s selected lobbing weapon would land, for the HUD {@code mt_dist} / {@code MORTAR_DIST}
     * readout — the port of {@code MCH_EntityAircraft.getLandInDistance} -> {@code MCH_WeaponSet.getLandInDistance}:
     * the weapon's aim (hull + its own traverse + the mount's fixed angles, exactly as {@link #fireSelectedWeapon} aims)
     * marched by {@link MCH_WeaponBallistics#landInDistance} from the vehicle's position. -1 without a landing point.
     */
    double mortarLandInDistance(Entity user) {
        if (v.tickCount != this.landInDistanceTick && v.tickCount % 5 == 0) {
            this.landInDistanceTick = v.tickCount;
            WeaponSlot slot = v.weaponSync().selectedHudSlot(v.seatIndexOf(user));
            if (slot == null) {
                this.landInDistance = -1.0;
            } else {
                MCH_AircraftInfo.Weapon mount = slot.mounts.get(0);
                float hullYaw = v.fireBodyYaw(user, mount);
                float hullPitch = v.fireBodyPitch(user, mount);
                float[] aim = weaponAim(user, mount, hullYaw, hullPitch);
                Vec3 dir = MchVehicleMath.aimVector(aim[0] + mount.yaw, aim[1] + mount.pitch, v.getRollAngle());
                this.landInDistance = MCH_WeaponBallistics.landInDistance(slot.info.gravity, slot.info.acceleration,
                    dir.x, dir.y, dir.z, v.getX(), v.getY(), v.getZ(), (x0, y0, z0, x1, y1, z1) -> {
                        net.minecraft.world.phys.BlockHitResult h = v.level().clip(new net.minecraft.world.level.ClipContext(
                            new Vec3(x0, y0, z0), new Vec3(x1, y1, z1), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                            net.minecraft.world.level.ClipContext.Fluid.NONE, v));
                        return h.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? null
                            : new int[]{h.getBlockPos().getX(), h.getBlockPos().getZ()};
                    });
            }
        }
        return this.landInDistance;
    }

    /** SERVER: queue a weapon cycle (+1 next / -1 previous), applied on the next tick. Called by the switch payload;
     *  accumulates so multiple presses in one tick window each register, bounded at one full lap of the loadout so a
     *  packet flood cannot queue unbounded work for the next tick. */
    void queueWeaponSwitch(Entity sender, int direction) {
        int seat = v.seatIndexOf(sender);
        if (seat < 0) {
            return;
        }
        int lap = this.weapons != null ? Math.max(1, this.weapons.size()) : 1;
        this.switchQueue.queue(seat, direction, lap);
    }

    /** SERVER: the reference {@code KeySwWeaponMode} for {@code player}'s seat ({@code switchCurrentWeaponMode} ->
     *  {@code MCH_WeaponSet.switchMode}), with the reference client's gates: not a wreck, not in the resupply GUI. */
    void switchWeaponMode(Player player) {
        if (v.level().isClientSide || this.weapons == null || v.isDestroyed() || v.isPilotReloading()) {
            return;
        }
        int seat = v.seatIndexOf(player);
        WeaponSlot s = seat >= 0 ? this.weapons.selected(seat) : null;
        if (s != null && s.switchMode()) {
            notifySeatWeapon(seat);
        }
    }

    /** True if {@code seat}'s currently-selected weapon is a lock-on missile (server-side; the NotifyLock anti-spoof). */
    boolean selectedWeaponIsLockOn(int seat) {
        if (this.weapons == null) {
            return false;
        }
        WeaponSlot s = this.weapons.selected(seat);
        return s != null && s.info != null && AbstractMchVehicle.isLockOnType(s.info.type);
    }

    /**
     * Rearm ONE weapon by index from the riding GUI — the port of {@code supplyAmmo} / {@code canPlayerSupplyAmmo}: the
     * vehicle must be grounded ({@link AbstractMchVehicle#canSupply()}) and the weapon must have a finite reserve that
     * is not already full; the config's ammo items ({@code Item = count, name} — most bundled weapons need iron ingots +
     * gunpowder) are consumed from the player's main inventory first, regardless of game mode, as in the reference
     * ({@code canPlayerSupplyAmmo}/{@code supplyAmmo} have no creative bypass). A water/lava-bucket round hands back an
     * empty bucket. The index is the weapon the GUI is SHOWING, so it is bounds-checked here — a modified client
     * cannot address a weapon that does not exist. Returns true if a supply pulse was granted.
     */
    boolean supplyAmmoFromPlayer(Player player, int weaponIndex) {
        if (v.level().isClientSide || this.weapons == null || !v.canSupply()) {
            return false;
        }
        if (weaponIndex < 0 || weaponIndex >= this.weapons.size()) {
            return false;
        }
        WeaponSlot slot = this.weapons.get(weaponIndex);
        if (!slot.hasEconomy() || slot.totalAmmo() >= slot.maxAmmo()) {
            return false;
        }
        java.util.List<mcheli.agnostic.weapon.MCH_WeaponInfo.RoundItem> cost = slot.info.roundItems;
        if (cost != null && !cost.isEmpty()) {
            for (mcheli.agnostic.weapon.MCH_WeaponInfo.RoundItem ri : cost) {
                if (countItem(player, ri) < ri.num) {
                    return false; // cannot afford this weapon's round item (no creative bypass — reference has none)
                }
            }
            for (mcheli.agnostic.weapon.MCH_WeaponInfo.RoundItem ri : cost) {
                takeItem(player, ri, ri.num);
            }
        }
        slot.supplyRestAllAmmo();
        if (slot.magazine() <= 0) {
            slot.reloadMag();
        }
        return true;
    }

    // The reference scans mainInventory only (indices 0..35): hotbar + main grid, never armor (36..39) or offhand (40).
    private static final int MAIN_INVENTORY_SIZE = 36;

    private static int countItem(Player player, mcheli.agnostic.weapon.MCH_WeaponInfo.RoundItem ri) {
        int n = 0;
        int size = Math.min(MAIN_INVENTORY_SIZE, player.getInventory().getContainerSize());
        for (int i = 0; i < size; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (matches(s, ri)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static void takeItem(Player player, mcheli.agnostic.weapon.MCH_WeaponInfo.RoundItem ri, int num) {
        int size = Math.min(MAIN_INVENTORY_SIZE, player.getInventory().getContainerSize());
        for (int i = 0; i < size && num > 0; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!matches(s, ri)) {
                continue;
            }
            // A water/lava-bucket round hands back an EMPTY bucket rather than destroying the stack (reference
            // MCH_EntityAircraft:2400-2402); those items are max-stack 1, so one slot yields one round.
            if (s.is(net.minecraft.world.item.Items.WATER_BUCKET) || s.is(net.minecraft.world.item.Items.LAVA_BUCKET)) {
                player.getInventory().setItem(i, new ItemStack(net.minecraft.world.item.Items.BUCKET));
                num--;
            } else {
                int take = Math.min(num, s.getCount());
                s.shrink(take);
                num -= take;
            }
        }
    }

    /** Reference {@code isItemEqual(ri.itemStack)}: the exact item the round names, resolved at load (1.7.10 name +
     *  metadata, e.g. {@code dye, 15} -> bone meal). An unknown name never matches, so that cost can never be paid. */
    private static boolean matches(ItemStack s, mcheli.agnostic.weapon.MCH_WeaponInfo.RoundItem ri) {
        Item want = mcheli.dependent.port.NeoItemHandle.itemOf(ri.item);
        return want != null && !s.isEmpty() && s.is(want);
    }

    /**
     * Track the pilot's resupply GUI — the port of {@code updateSupplyAmmo}:2345-2370. While the PILOT has the vehicle
     * menu open the vehicle is "reloading" (the HUD blanks its weapon readout), and the flag persists for a 20-tick
     * tail after the GUI closes so the readout does not flicker back mid-reload.
     */
    private void updateReloadingState() {
        boolean open = v.pilot() instanceof Player p && p.isAlive()
            && p.containerMenu instanceof mcheli.dependent.menu.MchVehicleMenu m && m.vehicle() == v;
        if (open) {
            this.supplyAmmoWait = 20;
        } else if (this.supplyAmmoWait > 0) {
            this.supplyAmmoWait--;
        }
        v.getEntityData().set(AbstractMchVehicle.DATA_RELOADING, open || this.supplyAmmoWait > 0);
    }

    /** Pull every weapon's reserve into its magazine — the reference reloads all weapons on the GUI-CLOSE edge, and
     *  only while the vehicle is intact ({@code updateSupplyAmmo}:2355 gates on {@code !isDestroyed()}). */
    void reloadAllWeapons() {
        if (this.weapons != null && !v.isDestroyed()) {
            this.weapons.reloadAll();
        }
    }

    /** Server tick for the weapon system: build the loadout once, advance fire-control counters, apply a queued
     *  switch, audit the seats' missile locks, and fire each seat's selected weapon while its trigger is held. */
    void tickServer() {
        if (this.weapons == null) {
            this.weapons = VehicleWeapons.build(v.weaponHostInfo(), MCH_WeaponInfoManager::get, v.seatCount());
            this.weapons.initSelection(seat -> v.seatPassenger(seat) instanceof Player);
            // Restore the saved reserves now the slots exist (reference :795-801: set the total, then fill the mag),
            // matched by weapon. A legacy array is first mapped through the slot order it was saved with.
            java.util.Map<String, Integer> saved = this.savedAmmo != null ? this.savedAmmo
                : this.savedLegacyAmmo != null ? VehicleWeapons.legacyAmmoByKey(v.weaponHostInfo(),
                    MCH_WeaponInfoManager::get, this.savedLegacyAmmo) : null;
            if (saved != null) {
                java.util.List<String> keys = this.weapons.ammoKeys();
                for (int i = 0; i < keys.size(); i++) {
                    Integer total = saved.get(keys.get(i));
                    if (total != null && total >= 0) {
                        this.weapons.get(i).setRestAllAmmo(total);
                        this.weapons.get(i).reloadMag();
                    }
                }
            }
            this.savedAmmo = null;
            this.savedLegacyAmmo = null;
        }
        this.weapons.tick();
        if (this.firedCooldownTicks > 0) {
            this.firedCooldownTicks--;
        }
        if (this.tvMissile != null && this.tvMissile.isRemoved()) {
            this.tvMissile = null;
        }

        java.util.function.IntPredicate manned = s -> v.seatPassenger(s) instanceof Player;

        // Per-seat weapon switching: each seat cycles only through the weapons IT may use. Every step is a reference
        // switchWeapon (the old weapon reloads a partial magazine, the new one starts the switch lockout).
        for (java.util.Map.Entry<Integer, Integer> e : this.switchQueue.drain().entrySet()) {
            int seat = e.getKey();
            int pending = e.getValue();
            if (pending == 0 || this.weapons.isEmpty() || !(v.seatPassenger(seat) instanceof Player) || v.isDestroyed()) {
                continue;
            }
            int dir = pending > 0 ? 1 : -1;
            boolean infinity = v.weaponInfinityAmmo(v.seatPassenger(seat));
            for (int i = 0; i < Math.abs(pending); i++) {
                this.weapons.cycle(seat, dir, manned, infinity);
            }
            this.lockAudits.drop(seat); // a new weapon builds a new lock (the client tracker restarts too)
            notifySeatWeapon(seat);
        }

        this.lockAudits.tickLockAudits();

        // Every seat fires ITS OWN weapon while ITS trigger is held.
        for (Entity p : v.getPassengers()) {
            int seat = v.seatIndexOf(p);
            // No weapon may fire while the pilot is in the resupply GUI (or its 20-tick tail), nor from a wreck — the
            // reference gates weapon use and weapon switching on !isDestroyed() && !isPilotReloading()
            // (MCH_AircraftClientTickHandler:212). Enforced on the SERVER, which owns the ammo, so a client that keeps
            // sending FIRE cannot shoot through a reload.
            if (seat >= 0 && v.controlState(seat).fire && !v.isPilotReloading() && !v.isDestroyed()) {
                fireSelectedWeapon(seat);
            }
        }

        // Publish every seat's weapon for its own HUD + lock tracker (reference ac.getCurrentWeapon(player)).
        v.publishSeatWeapons();
        // Publish the "recently fired" bit (the reference useWeaponStat) so the gatling barrel spins on every client.
        v.getEntityData().set(AbstractMchVehicle.DATA_FIRING, this.firedCooldownTicks > 0);
        v.weaponSync().publishAmmo(this.weapons);
        updateReloadingState();
    }

    /** Reference {@code isInfinityAmmo}: a creative operator, or the server's {@code infinityAmmo} option
     *  ({@code getCommonStatus(3)}, reference {@code MCH_EntityAircraft:398-400}). Behind the entity bridge the crew's
     *  boarding reads ({@link MchVehicleCrew#handlePassengerAdded}). */
    boolean weaponInfinityAmmo(Entity operator) {
        return (operator instanceof Player pl && pl.getAbilities().instabuild)
            || mcheli.dependent.config.MchConfig.SERVER.infinityAmmo();
    }

    /** True if {@code b} is this vehicle's live, latest TV-guided missile (reference {@code ac.getTVMissile() == this}). */
    boolean isActiveTvMissile(MchBullet b) {
        return b != null && b == this.tvMissile && !b.isRemoved();
    }

    /**
     * The world yaw/pitch {@code mount} points along for its operator, before the mount's fixed AddWeapon angles: the
     * body angles plus the weapon's own traverse toward the operator's look, clamped to the mount's limits — the SAME
     * computation the RENDERER uses to orient the weapon model ({@code MchModelEntityRenderer.weaponYaw/weaponPitch}),
     * so the projectile leaves EXACTLY where the barrel visually points.
     */
    private float[] weaponAim(Entity aimer, MCH_AircraftInfo.Weapon mount, float hullYaw, float hullPitch) {
        // Reference MCH_EntityAircraft.updateWeaponsRotation (:4485-4515) + MCH_WeaponSet.use (:312-315): the shot
        // is hull + THIS WEAPON's own rotationYaw/rotationPitch, and that angle tracks the player who OPERATES
        // this mount — never the pilot's turret ring. `turret` contributes only `ty` (:4495), the frame the
        // traverse limits are measured in; it cancels outright on an unlimited mount.
        //
        // Firing a turret mount along turretOrbitYaw() was the "secondary weapons only shoot forward" bug: the
        // ring is PILOT-sourced (updateAimLatch reads pilot()), so a gunner's shots left along the driver's aim,
        // or along hull-forward when no driver had ever boarded. The ring and the aim are different quantities;
        // they coincide only when the operator IS the pilot (on a tank: the main gun, not the secondaries).
        //
        // A no-traverse weapon has 0 limits, so the clamp pins relYaw to whatever hullYaw already is — the hull for an
        // aircraft (fires forward), or the gunner's look for an emplacement (via the fireBodyYaw override).
        //   AIM SOURCE = the operator's BODY yaw (getYRot()), NOT the head yaw (getYHeadRot()). This is the
        // reference's field (MCH_WeaponSet.use reads entity.rotationYaw; updateWeaponsRotation and lastRiderYaw
        // likewise) AND the only one that survives to the server: a passenger's rotation arrives via
        // ServerboundMovePlayerPacket.Rot -> absMoveTo, which sets getYRot()/getXRot() but NEVER yHeadRot.
        float aimTy = WeaponAim.turretBase(mount, v.turretOrbitYaw(1.0F));
        float relYaw = WeaponAim.rotationYaw(mount, Mth.wrapDegrees(aimer.getYRot() - hullYaw), aimTy);
        float relPitch = WeaponAim.rotationPitch(mount, Mth.wrapDegrees(aimer.getXRot() - hullPitch));
        return new float[]{hullYaw + relYaw, hullPitch + relPitch};
    }

    /** Reference {@code MCH_Lib.isBlockInWater}: water in the block itself or in the block below, above or beside it
     *  (the reference's seven offsets). */
    private boolean isBlockInWater(BlockPos p) {
        if (p.getY() <= v.level().getMinBuildHeight()) {
            return false;
        }
        BlockPos[] around = {p.below(), p, p.north(), p.south(), p.west(), p.east(), p.above()};
        for (BlockPos q : around) {
            if (v.level().getFluidState(q).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    /** The first block along {@code operator}'s look within {@code range} blocks from {@code from} (fluids ignored, as
     *  the reference's {@code rayTraceBlocks}), or null. */
    private net.minecraft.world.phys.BlockHitResult lookRay(Entity operator, Vec3 from, double range) {
        Vec3 look = Vec3.directionFromRotation(operator.getXRot(), operator.getYRot());
        net.minecraft.world.phys.BlockHitResult hit = v.level().clip(new net.minecraft.world.level.ClipContext(
            from, from.add(look.scale(range)), net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE, v));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? null : hit;
    }

    /**
     * Fire ONE shot of the selected weapon this tick, if its fire-control allows. Picks the next mount round-robin,
     * places the muzzle and aims it in world space with the port's orientation convention
     * ({@code Ry(-yaw)·Rx(pitch)·Rz(roll)}, forward = model +Z — the same transform that seats the pilot and renders
     * the model, so muzzles line up with the guns), then spawns a {@link MchBullet} carrying the weapon's real stats
     * (in the weapon's current mode) and plays the weapon's own report. The round is credited to the seat's PLAYER
     * (reference {@code shootingEntity = prm.user}); the vehicle it leaves is never hit by it.
     */
    private void fireSelectedWeapon(int seat) {
        WeaponSlot slot = this.weapons.selected(seat);
        Entity shooter = v.seatPassenger(seat);
        if (slot == null || shooter == null || !slot.canFire()) {
            return;
        }
        // A seat may only fire a weapon it actually operates (its own, or — for the pilot — a canUsePilot weapon whose
        // gunner seat is empty). Eligibility already enforces this, but re-check: crew may have boarded mid-trigger.
        if (this.weapons.eligible(slot, seat, s -> v.seatPassenger(s) instanceof Player)) {
            // ok
        } else {
            return;
        }
        String preType = slot.info.type == null ? "" : slot.info.type.toLowerCase();
        // Smoke and dummy weapons fire nothing: their reference shot() returns false, so no ammo, delay or report.
        if (preType.equals("smoke") || preType.equals("dummy")) {
            return;
        }
        // Guided missiles (AA/AT) require a COMPLETED lock: the operator's client resolves the lock and ships the
        // target entity id (controlState.lockTargetId), which the server's lock audit must back (auditedLockTarget).
        // Resolve + validate it here BEFORE consuming ammo — with no valid target the weapon does not fire (faithful to
        // the reference shot() returning false pre-lock).
        boolean guided = AbstractMchVehicle.isLockOnType(preType);
        Entity guidedTarget = null;
        byte guidedKind = MchBullet.GUIDED_NONE;
        if (guided) {
            guidedTarget = this.lockAudits.auditedLockTarget(seat, slot.info);
            if (guidedTarget == null) {
                return; // no valid lock → hold fire (do not consume ammo)
            }
            guidedKind = preType.equals("aamissile") ? MchBullet.GUIDED_AA
                : mcheli.agnostic.weapon.MCH_WeaponMode.isTopAttack(slot.info, slot.mode())
                    ? MchBullet.GUIDED_AT_TOP : MchBullet.GUIDED_AT_DIRECT;
        }
        // AS missile: the operator's look from the vehicle (+1.62, the reference eye offset) must meet a dry block within
        // 250 blocks, or it does not fire (reference MCH_WeaponASMissile.shot); it then homes on that point.
        net.minecraft.world.phys.BlockHitResult groundPoint = null;
        if (preType.equals("asmissile")) {
            groundPoint = lookRay(shooter, v.position().add(0.0, 1.62, 0.0), 250.0);
            if (groundPoint == null || isBlockInWater(groundPoint.getBlockPos())) {
                return;
            }
        }
        // Guided torpedo: the operator's sight line must meet a block in water within 150 blocks (reference
        // MCH_WeaponTorpedo.shotGuided; it traced from the user's position, the port from the eye, where the crosshair
        // is). The torpedo then runs at that point underwater.
        net.minecraft.world.phys.BlockHitResult waterPoint = null;
        if (preType.equals("torpedo") && slot.info.isGuidedTorpedo) {
            waterPoint = lookRay(shooter, shooter.getEyePosition(), 150.0);
            if (waterPoint == null || !isBlockInWater(waterPoint.getBlockPos())) {
                return;
            }
        }
        MCH_AircraftInfo.Weapon mount = slot.fireOneShot(v.weaponInfinityAmmo(shooter));
        this.weapons.shareGroupCooldown(slot); // one barrel, several round types (config Group)
        MCH_WeaponInfo wi = slot.info;
        MCH_WeaponInfo shotWi = slot.shotInfo(); // the round's mode-dependent explosion/piercing (MCH_WeaponMode)
        if (firesRotBarrel(slot)) {
            this.firedCooldownTicks = 4; // reference MCH_WeaponSet.WAIT_CLEAR_USED_COUNT — barrel stays "used" ~4 ticks
        }

        // The BODY angles the muzzle position AND the fire direction rotate by. For an aircraft/tank this is the hull.
        // An EMPLACEMENT ({@link MchGroundVehicle}) OVERRIDES these to the operator's LOOK for a no-traverse weapon —
        // the port of {@code MCH_EntityVehicle.useCurrentWeapon}, which snaps {@code this.rotationYaw/Pitch} to
        // {@code prm.user.rotationYaw/Pitch} for the shot so a fixed forward launcher (searam, Phalanx) fires exactly
        // where the gunner looks. A weapon WITH a real yaw range keeps the hull here and traverses per-weapon below.
        float hullYaw = v.fireBodyYaw(shooter, mount);
        float hullPitch = v.fireBodyPitch(shooter, mount);
        float roll = v.getRollAngle();

        // Fire along the OPERATOR's look, clamped to the mount's config traverse/elevation limits (weaponAim). The
        // renderer calls WeaponAim.renderYaw with the same inputs, so the barrel and the projectile differ only by
        // mount.defaultYaw, which the model geometry already carries.
        Entity aimer = v.weaponOperator(mount);
        if (aimer == null) {
            aimer = shooter; // eligibility() already guarantees these are the same player; belt and braces
        }
        float[] aim = weaponAim(aimer, mount, hullYaw, hullPitch);
        float yaw = aim[0];
        float pitch = aim[1];

        // Muzzle world position = vehiclePos + R(HULL) · mountLocalOffset. The mount offset is authored in MODEL space,
        // so it must be rotated by the hull (plus the turret ring for a ring-mounted gun) — NOT by the operator's look:
        // rotating it by the aim made the muzzle swing around (and outside) the vehicle whenever a gunner turned his head.
        org.joml.Quaternionf qHull = new org.joml.Quaternionf()
            .rotateY((float) Math.toRadians(-hullYaw))
            .rotateX((float) Math.toRadians(hullPitch))
            .rotateZ((float) Math.toRadians(roll));
        org.joml.Vector3f mpos = new org.joml.Vector3f((float) mount.pos.x(), (float) mount.pos.y(), (float) mount.pos.z());
        if (mount.turret) {
            // A ring-mounted gun orbits turretPosition with the turret before the hull transform (matches the renderer).
            MCH_AircraftInfo info = v.weaponHostInfo();
            org.joml.Vector3f tp = new org.joml.Vector3f(
                (float) info.turretPosition.x(), (float) info.turretPosition.y(), (float) info.turretPosition.z());
            mpos.sub(tp).rotateY((float) Math.toRadians(-v.turretOrbitYaw(1.0F))).add(tp);
        }
        qHull.transform(mpos);
        Vec3 muzzle = v.position().add(mpos.x, mpos.y, mpos.z);

        // Per-shot dispersion: the reference perturbs the firing yaw AND pitch by (rand-0.5)*accuracy degrees on each
        // shot (MCH_WeaponSet.use), so sustained fire scatters over a cone instead of a perfect laser.
        float accuracy = wi.accuracy;
        float spreadYaw = accuracy > 0.0F ? (v.getRandom().nextFloat() - 0.5F) * accuracy : 0.0F;
        float spreadPitch = accuracy > 0.0F ? (v.getRandom().nextFloat() - 0.5F) * accuracy : 0.0F;

        // Direction = R(vehicle yaw+mountYaw+spread, pitch+mountPitch+spread, roll) · forward(+Z). The mount's fixed aim
        // offset (from AddWeapon) plus the dispersion are summed onto the body angles as the reference does before
        // RotVec3(0,0,1,...).
        Vec3 dir = MchVehicleMath.aimVector(yaw + mount.yaw + spreadYaw, pitch + mount.pitch + spreadPitch, roll);

        // Ballistics + model, all from config.
        boolean bulletOrRocket = MCH_WeaponBallistics.isBulletOrRocket(wi.type);
        float speed = MCH_WeaponBallistics.initialSpeed(wi.acceleration);
        float accFactor = MCH_WeaponBallistics.accelerationFactor(wi.acceleration, bulletOrRocket);
        float gravity = wi.gravity; // 0 for guns/rockets; negative for bombs
        String model = (wi.bulletModelName != null && !wi.bulletModelName.isEmpty())
            ? wi.bulletModelName : MCH_WeaponBallistics.defaultBulletModel(wi.type);
        int color = MchVehicleMath.packColor(wi.color);
        boolean report = true;

        if (preType.equals("bomb") && wi.destruct) {
            // Destruct (the plastic bomb, reference MCH_WeaponBomb.shot): a seat-less UAV helicopter blows itself up in
            // place; on anything else the shot is spent with no effect, as in the reference.
            report = destructInPlace(shooter, wi);
        } else if (preType.equals("bomb")) {
            // A bomb is RELEASED at the aircraft's own velocity (reference MCH_WeaponBomb.shot overwrites the round's
            // motion with the carrier's), NOT fired forward at muzzle speed — config gravity (auto -0.03) carries it
            // down. Longer life than guns so a high-altitude release reaches the ground.
            MchBullet.spawnDropped(v.level(), muzzle, v.getDeltaMovement(), gravity, wi.power, 1200, shooter,
                model, color, shotWi);
        } else if (guidedKind != MchBullet.GUIDED_NONE) {
            MchBullet.spawnGuided(v.level(), muzzle, dir, speed, accFactor, gravity, wi.power, 600, shooter, model,
                color, shotWi, guidedTarget, guidedKind);
        } else if (preType.equals("asmissile")) {
            // Launched along the operator's sight line toward the point (reference: the missile's initial motion is the
            // look ray), then it homes on it (MchBullet AS steering).
            Vec3 look = Vec3.directionFromRotation(shooter.getXRot(), shooter.getYRot());
            MchBullet.spawnPointGuided(v.level(), muzzle, look, speed, accFactor, gravity, wi.power, 600, shooter,
                model, color, shotWi, groundPoint.getLocation(), groundPoint.getBlockPos(), MchBullet.GUIDED_AS);
        } else if (preType.equals("tvmissile")) {
            // Reference MCH_WeaponTvMissile.shotServer: launched along the operator's look (+ the mount's fixed angles).
            // Mode 0 is operator-steered; mode 1 flies straight at 1.5x the acceleration (still capped at 3.9).
            Vec3 look = Vec3.directionFromRotation(shooter.getXRot() + mount.pitch, shooter.getYRot() + mount.yaw);
            if (mcheli.agnostic.weapon.MCH_WeaponMode.isTvGuided(wi, slot.mode())) {
                this.tvMissile = MchBullet.spawnPointGuided(v.level(), muzzle, look, speed, accFactor, gravity,
                    wi.power, 600, shooter, model, color, shotWi, null, null, MchBullet.GUIDED_TV);
            } else {
                MchBullet.spawnWeapon(v.level(), muzzle, look, MCH_WeaponBallistics.initialSpeed(wi.acceleration * 1.5F),
                    accFactor, gravity, wi.power, 600, shooter, model, color, shotWi);
            }
        } else if (preType.equals("torpedo")) {
            // A torpedo launches at unit_dir × config Acceleration PLUS the carrier's own velocity (reference
            // MCH_WeaponTorpedo.shotNoGuided) — NOT the clamped muzzle speed. That carrier term is essential: several
            // shipped torpedoes ship Acceleration 0, so the launcher's motion is their whole thrust; it then self-steers
            // underwater (MchBullet water-run). A guided one launches along the operator's look (shotGuided) and runs
            // at the aimed water point instead.
            if (waterPoint != null) {
                Vec3 look = Vec3.directionFromRotation(shooter.getXRot(), shooter.getYRot());
                Vec3 launch = look.scale(wi.acceleration).add(v.getDeltaMovement());
                MchBullet.spawnGuidedTorpedo(v.level(), muzzle, launch, gravity, wi.power, 600, shooter, model, color,
                    shotWi, waterPoint.getLocation());
            } else {
                Vec3 launch = dir.scale(wi.acceleration).add(v.getDeltaMovement());
                MchBullet.spawnTorpedo(v.level(), muzzle, launch, gravity, wi.power, 600, shooter, model, color, shotWi);
            }
        } else if (preType.equals("cas")) {
            // Close air support: raycast the operator's aim to the ground and call in a BOMBING RUN there (reference
            // MCH_WeaponCAS calls an A-10 gun run from a compass direction — the flying MCH_EntityA10 strafing entity is
            // a deferred sub-port, so this port rains a bombing run at the aim point instead: same ordnance-on-target
            // effect, reusing the proven #21 airstrike path). Aimed at open sky (no ground hit) → no strike.
            Vec3 to = muzzle.add(dir.scale(180.0));
            net.minecraft.world.phys.BlockHitResult bh = v.level().clip(new net.minecraft.world.level.ClipContext(
                muzzle, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, v));
            if (bh.getType() != net.minecraft.world.phys.HitResult.Type.MISS
                    && v.level() instanceof ServerLevel sl) {
                Vec3 tgt = bh.getLocation();
                MchBullet.callAirstrike(sl, tgt.x, tgt.y, tgt.z, shooter, v, Math.max(wi.explosion, 3), wi.power,
                    6 + v.getRandom().nextInt(3));
            }
        } else {
            MchBullet.spawnWeapon(v.level(), muzzle, dir, speed, accFactor, gravity, wi.power, 600, shooter, model,
                color, shotWi);
        }

        // Report: the weapon's own sound at its configured volume, with the reference pitch jitter.
        SoundEvent sound = report ? MchSounds.byName(wi.soundFileName) : null;
        if (sound != null) {
            float pitchR = wi.soundPitch * (1.0F - wi.soundPitchRandom) + v.getRandom().nextFloat() * wi.soundPitchRandom;
            v.level().playSound(null, muzzle.x, muzzle.y, muzzle.z, sound, SoundSource.NEUTRAL,
                wi.soundVolume, pitchR);
        }

        if (v.isRemoved()) {
            return; // a destruct shot discarded the vehicle
        }
        v.effects().spawnFireCosmetics(muzzle, dir, wi, yaw + mount.yaw, pitch + mount.pitch);

        // Recoil kick: on a weapon with config Recoil>0, bump the synced trigger so every client runs the visual kick
        // (reference sets recoilCount=30/value/yaw on the firing rising edge). The direction is the weapon's aim
        // relative to the hull, so a forward gun kicks pitch (nose up) and a side gun kicks roll.
        if (wi.recoil > 0.0F) {
            v.getEntityData().set(AbstractMchVehicle.DATA_RECOIL_SEQ,
                v.getEntityData().get(AbstractMchVehicle.DATA_RECOIL_SEQ) + 1);
            v.getEntityData().set(AbstractMchVehicle.DATA_RECOIL_VALUE, wi.recoil);
            v.getEntityData().set(AbstractMchVehicle.DATA_RECOIL_YAW, Mth.wrapDegrees(yaw - hullYaw));
        }
    }

    /**
     * Reference {@code MCH_WeaponBomb.shot} for a {@code Destruct} weapon: only a UAV helicopter with no seat but the
     * operator's ({@code isUAV() && getSeatNum() == 0}) detonates at its own position (explosion, block damage and
     * flames from the weapon, credited to the operator) and is destroyed ({@code destruct()}: the rider dismounts, the
     * entity dies). Returns whether it detonated (only then does the weapon's report play). The blast spares this
     * vehicle and its rider: the reference operator flies a UAV from a station, out of the blast, while the port's
     * rides it (no UAV station), and it is about to be put off.
     */
    private boolean destructInPlace(Entity operator, MCH_WeaponInfo wi) {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (!(v instanceof MchHelicopter) || info == null || !info.isUAV || info.getNumSeatAndRack() > 1
            || !(v.level() instanceof ServerLevel sl)) {
            return false;
        }
        MchExplosion.explode(sl, v.position(), wi.explosion, wi.explosionBlock, wi.flaming, true, 0, null, v,
            operator);
        v.ejectPassengers();
        v.discard();
        return true;
    }

    /** Tell the seat's rider (action-bar) which weapon, in which mode, is now selected. */
    private void notifySeatWeapon(int seat) {
        WeaponSlot slot = this.weapons.selected(seat);
        Entity rider = v.seatPassenger(seat);
        if (slot == null || !(rider instanceof Player p)) {
            return;
        }
        p.displayClientMessage(Component.literal("Weapon: " + slot.displayName()), true);
    }

    /** Test hook: top a seat's selected weapon up to a full reserve + magazine, so a headless probe can fire without
     *  standing up the whole ammo-supply economy. Server-side only. */
    void debugSupplySeatWeapon(int seat) {
        if (this.weapons == null || v.level().isClientSide) {
            return;
        }
        WeaponSlot s = this.weapons.selected(seat);
        if (s != null) {
            s.supplyRestAllAmmo();
            s.reloadMag();
        }
    }

    /** Diagnostic hook for the headless self-tests: what a seat has selected and whether it can actually fire. */
    String debugSeatWeapon(int seat) {
        if (this.weapons == null) {
            return "<no weapons>";
        }
        WeaponSlot s = this.weapons.selected(seat);
        if (s == null) {
            return "seat " + seat + ": <nothing selected> slots=" + this.weapons.all().size();
        }
        return "seat " + seat + ": " + s.info.name + " canFire=" + s.canFire()
            + " mag=" + s.magazine() + " reload=" + s.reloadFraction() + " heat=" + s.heatFraction()
            + " occupant=" + (v.seatPassenger(seat) == null ? "<none>" : v.seatPassenger(seat).getType().toShortString())
            + " fireHeld=" + v.controlState(seat).fire + " reloadingGate=" + v.isPilotReloading();
    }

    /** True when the FIRED weapon drives a spinning gatling barrel, so only that weapon arms the barrel latch (see
     *  {@link RotBarrel}). */
    private boolean firesRotBarrel(WeaponSlot slot) {
        return RotBarrel.firesRotBarrel(v.weaponHostInfo(), slot.weaponName);
    }
}
