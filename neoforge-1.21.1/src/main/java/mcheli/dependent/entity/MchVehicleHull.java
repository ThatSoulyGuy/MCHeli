package mcheli.dependent.entity;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.vehicle.VehicleWreck;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The hull of one vehicle (faithful port of {@code MCH_EntityAircraft.attackEntityFrom} + {@code destroyAircraft}):
 * the hit points and the hurt pipeline (armour, the environmental gates, the PvP gate, salvage and MCHeli's own damage
 * cooldowns), the vehicle data on the spawn item, the destruction into a wreck (the base tumble, the one-shot crash
 * detonation, the rider burn and the despawn countdown), auto repair, area repair and medevac regeneration, and the HP
 * NBT ({@code AcDamage}, {@code Destroyed}, {@code Despawn}). It is the only writer of
 * {@link AbstractMchVehicle#DATA_DAMAGE_TAKEN} and {@link AbstractMchVehicle#DATA_DESTROYED}.
 *
 * <p>The destruction and tumble hooks ({@code destroyVehicle}, {@code seedWreckTumble}, {@code applyWreckTumble},
 * {@code explosionByCrash}, {@code dropItem}) stay on the entity, which subclasses override; this class holds the base
 * bodies ({@code base*}) and calls the hooks only through {@code v}, so an override is never skipped. The wreck
 * arithmetic is the agnostic, unit-tested {@link VehicleWreck}.
 */
final class MchVehicleHull {
    private final AbstractMchVehicle v;

    // ---- HP / armor / destruction (server-only transients; NOT synced) ----
    /** Per-part weak-point/armor multiplier for the NEXT hit — set by {@link MchBullet} immediately before {@code hurt()}
     *  (the port's stand-in for the reference {@code lastBBDamageFactor} bounding-box side effect); read+reset in hurt()
     *  and re-zeroed each server tick so a stale factor never leaks. Explosions leave it at 1.0 (whole-body armor). */
    private float lastBBDamageFactor = 1.0F;
    /** MCHeli's OWN damage cooldown (2/1/10/20 for lava/explosion/onFire/destroy). Distinct from vanilla
     *  {@code invulnerableTime} — the vehicle extends {@link Entity} (not LivingEntity), which has no i-frame gate. */
    private int mchTimeSinceHit;
    /** Wreck lifetime countdown (500 ticks ≈ 25 s), decremented each server tick once destroyed, then {@code discard()}. */
    private int despawnCount;
    /** Drives the {@code isExploded()} one-shot gate so a tumbling wreck crash-detonates exactly once. */
    private int damageSinceDestroyed;
    /** Wreck tumble seeds (reference {@code rotDestroyed*}); applied to yaw/pitch/roll each tick while destroyed. */
    private float rotDestroyedYaw;
    private float rotDestroyedPitch;
    private float rotDestroyedRoll;

    MchVehicleHull(AbstractMchVehicle v) {
        this.v = v;
    }

    // ---- NBT (AcDamage, Destroyed, Despawn) and the server tick seams ----

    /** Read the HP and wreck state, after the config (the damage clamps to it); Destroyed and Despawn are raw. */
    void load(CompoundTag tag) {
        v.setDamageTaken(tag.getInt("AcDamage"));
        v.getEntityData().set(AbstractMchVehicle.DATA_DESTROYED, tag.getBoolean("Destroyed"));
        this.despawnCount = tag.getInt("Despawn");
    }

    /** Write the HP and wreck state. */
    void save(CompoundTag tag) {
        tag.putInt("AcDamage", v.getDamageTaken());
        tag.putBoolean("Destroyed", v.isDestroyed());
        tag.putInt("Despawn", this.despawnCount);
    }

    /** SERVER tick, pre-physics: MCHeli's own damage cooldown counts down. */
    void tickHitCooldown() {
        if (this.mchTimeSinceHit > 0) this.mchTimeSinceHit--;
    }

    /**
     * SERVER tick, post-move, while destroyed. Wreck: the flight model produced no lift (its !isDestroyed gate is now
     * false), so it falls; add the per-type tumble, crash-detonate once on the ground-slam, and burn any lingering
     * riders, then despawn.
     *
     * @param prevOnGround the onGround flag sampled before the physics
     * @param prevMotionY  the vertical motion sampled before the physics (ground contact zeroes it during the move)
     * @return true when the wreck was discarded this tick (the caller returns: the rest of the server tick is skipped)
     */
    boolean tickWreck(boolean prevOnGround, double prevMotionY) {
        v.applyWreckTumble();
        if (VehicleWreck.crashSlam(v.isExploded(), prevOnGround, v.onGround(), prevMotionY)) {
            v.explosionByCrash(prevMotionY);
            this.damageSinceDestroyed = v.getMaxHp();
        }
        if (v.tickCount % 20 == 0) {
            for (Entity p : v.getPassengers()) {
                // Reference :1385-1391: only riders the DamageVs lists would let fire hurt (factor > 0).
                if (!p.fireImmune() && mcheli.dependent.config.MchServerRules.applyDamageVsEntity(p, 1.0F) > 0.0F) {
                    p.setRemainingFireTicks(100); // 5 s (reference setFire(5))
                }
            }
        }
        if (--this.despawnCount <= 1) {
            v.discard();
            return true;
        }
        return false;
    }

    /** SERVER tick: clear any unread part factor so it can't leak to a later hit (reference :1363). */
    void clearPartFactor() {
        this.lastBBDamageFactor = 1.0F;
    }

    /** SERVER tick: repair + medevac (reference repairOtherAircraft :1439, autoRepair :1479, regenerationMob :1503). */
    void tickRepair() {
        repairOtherVehicles();
        autoRepair();
        regenerateRiders();
    }

    // ---- hit points ----

    /** Config max HP ({@code MaxHP}), or 100 when this vehicle has no parsed info (reference {@code getMaxHP}). */
    int getMaxHp() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        return info != null ? info.maxHp : 100;
    }

    /** SERVER: set the accumulated damage, clamped to {@code [0, maxHp]} (reference {@code setDamageTaken}). The
     *  parameter shadows the vehicle field, so the vehicle reads here are {@code this.v}. */
    void setDamageTaken(int v) {
        if (!this.v.level().isClientSide) {
            this.v.getEntityData().set(AbstractMchVehicle.DATA_DAMAGE_TAKEN, Mth.clamp(v, 0, this.v.getMaxHp()));
        }
    }

    /** Current hit points = {@code maxHp - damageTaken}, floored at 0 (reference {@code getHP}). */
    int getHp() { return Math.max(0, v.getMaxHp() - v.getDamageTaken()); }

    /** Mend {@code tpd} HP of accumulated damage (reference {@code MCH_EntityAircraft.repair}) — the wrench's per-tick
     *  heal. Returns whether there was damage to mend, so the tool only spends durability + plays its sound when it
     *  actually repairs something. Server-authoritative; a no-op (but truthful) read on the client. */
    boolean repair(int tpd) {
        if (v.getDamageTaken() <= 0) {
            return false;
        }
        if (!v.level().isClientSide) {
            v.setDamageTaken(v.getDamageTaken() - mcheli.agnostic.vehicle.VehicleRepair.repairStep(tpd));
        }
        return true;
    }

    // ---- self-repair, area repair, medevac regeneration (reference MCH_EntityAircraft:2120-2197) ----

    private final mcheli.agnostic.vehicle.VehicleRepair autoRepairState = new mcheli.agnostic.vehicle.VehicleRepair();

    /** Reference {@code autoRepair} (:2120-2142): 30 s after the last hit, mend 1% of max HP every 2 s while the HP
     *  fraction is at least the server option {@code AutoRepairHP} (default 0.5). Server tick. */
    private void autoRepair() {
        int amount = this.autoRepairState.autoRepairTick(v.getDamageTaken(), v.getMaxHp(), v.isDestroyed(),
            mcheli.dependent.config.MchConfig.SERVER.autoRepairHP());
        if (amount > 0) {
            v.repair(amount);
        }
    }

    /** Reference {@code repairOtherAircraft} (:2161-2175): a config with {@code RepairOtherVehicles = range, value}
     *  mends every other damaged vehicle in range by {@code value} once a second. Like the reference, neither side is
     *  gated on being intact. Server tick. */
    private void repairOtherVehicles() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        float range = info != null ? info.repairOtherVehiclesRange : 0.0F;
        if (!mcheli.agnostic.vehicle.VehicleRepair.repairsOtherThisTick(v.tickCount, range)) {
            return;
        }
        for (AbstractMchVehicle o : v.nearby(range)) {
            if (o.getHp() < o.getMaxHp()) {
                o.setDamageTaken(o.getDamageTaken() - info.repairOtherVehiclesValue);
            }
        }
    }

    /** Reference {@code regenerationMob} (:2177-2197): a {@code Regeneration = true} config (the nh90 / an2 / us-2
     *  medevacs) keeps Regeneration I on every living CREW rider while a pilot is aboard. The pilot is not one of them:
     *  the reference walks {@code getSeats()}, the crew seat entities, and the pilot rides the aircraft itself. The
     *  250-tick effect is re-applied each tick while under 500 ticks are left, as in the reference. Server tick. */
    private void regenerateRiders() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (v.isDestroyed() || info == null || !info.regeneration || v.pilot() == null) {
            return;
        }
        for (Entity p : v.getPassengers()) {
            if (v.seatIndexOf(p) > 0 && p instanceof LivingEntity le && le.isAlive()) {
                net.minecraft.world.effect.MobEffectInstance cur =
                    le.getEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
                int left = cur == null ? -1 : cur.isInfiniteDuration() ? Integer.MAX_VALUE : cur.getDuration();
                if (mcheli.agnostic.vehicle.VehicleRepair.refreshesRegeneration(left)) {
                    le.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.REGENERATION,
                        mcheli.agnostic.vehicle.VehicleRepair.REGENERATION_TICKS, 0, true, true));
                }
            }
        }
    }

    /** True after a wreck has already crash-detonated — the one-shot gate on
     *  {@link AbstractMchVehicle#explosionByCrash} (reference). */
    boolean isExploded() { return VehicleWreck.isExploded(v.isDestroyed(), this.damageSinceDestroyed, v.getMaxHp()); }

    /** SERVER: stash the per-part weak-point factor for the imminent {@code hurt()} call (called by {@link MchBullet}). */
    void setLastBBDamageFactor(float f) { this.lastBBDamageFactor = f; }

    /**
     * The faithful port of {@code MCH_EntityAircraft.attackEntityFrom} (its exact order is load-bearing). All incoming
     * damage — weapons, explosions, lava/fire, crash, water submersion — funnels here. The per-part armor formula
     * asymmetry (a {@code factor<=1} armor plate multiplies BEFORE {@code armorDamageFactor}/{@code armorMinDamage};
     * a {@code factor>1} weak-point multiplies AFTER the min-subtraction, so it can't be zeroed) must not be
     * "simplified" (it lives in the unit-tested {@link mcheli.agnostic.vehicle.VehicleArmor}). lava (×2..9) and onFire
     * BYPASS the armor block; inFire/cactus do no damage. A player's melee hit may instead reclaim the vehicle as its
     * spawn item ({@link mcheli.agnostic.vehicle.VehicleSalvage}).
     */
    boolean handleHurt(DamageSource src, float amount) {
        float factor = this.lastBBDamageFactor;   // capture the per-part factor, then reset (reference :809-810)
        this.lastBBDamageFactor = 1.0F;

        if (v.isInvulnerableTo(src) || v.isRemoved()) return false;
        if (this.mchTimeSinceHit > 0) return false;                              // MCHeli's own cooldown (reference :819)
        if (src.is(DamageTypes.IN_FIRE) || src.is(DamageTypes.CACTUS)) return false; // no damage (reference :824,:828)
        // Client no-op BEFORE any state mutation (reference returns at isRemote :832, before the cooldown/RNG below) —
        // otherwise a client-side lava/fire baseTick would burn the entity RNG and latch the server-only cooldown.
        if (v.level().isClientSide) return true;

        // The server's DamageMCHeli*ByExternal factor lists (reference :836).
        amount = mcheli.dependent.config.MchServerRules.applyDamageByExternal(v, src, amount);
        // Reference MCH_Multiplay.canAttackEntity (:837): a player may not hurt a vehicle whose PILOT is a player he may
        // not harm (server PVP off, or the same team without friendly fire): 1.7.10 canAttackPlayer = canHarmPlayer.
        if (src.getEntity() instanceof Player ap && v.pilot() instanceof Player pp && !ap.canHarmPlayer(pp)) {
            return false;
        }
        boolean lava = src.is(DamageTypes.LAVA);
        boolean onFire = src.is(DamageTypes.ON_FIRE);
        if (lava) {
            amount *= v.getRandom().nextInt(8) + 2;                                // ×2..9 (reference :841)
            this.mchTimeSinceHit = 2;
        }
        if (src.is(DamageTypeTags.IS_EXPLOSION)) {
            this.mchTimeSinceHit = 1;                                            // (reference :846)
        } else if (src.getEntity() != null && v.hasPassenger(src.getEntity())) {
            return false;   // a rider can't damage the craft they ride (reference isMountedEntity guard :848)
        }
        if (onFire) this.mchTimeSinceHit = 10;                                    // (reference :852)

        // Damage SFX (reference :879 ping / :959-960 thud): a PLAYER attacker -> the "hit" ping, played before the
        // armor; anything else (crash, collision, explosion, fall) -> the "helidmg" metal thud with a randomised pitch,
        // played at the END (below), so a hit the armor absorbs is silent. Both sit OUTSIDE the !isDestroyed()/break
        // gates, so a burning wreck still thuds and a salvage still pings.
        Player attacker = src.getEntity() instanceof Player pl ? pl : null;
        if (attacker != null) {
            v.playMchSound("hit", 1.0F, 1.0F);
        }

        boolean playerBreak = isPlayerBreak(src);
        boolean creative = attacker != null && attacker.getAbilities().instabuild;
        MCH_AircraftInfo info = v.weaponHostInfo();

        if (!v.isDestroyed()) {
            if (!playerBreak && !lava && !onFire && info != null) {               // ARMOR FORMULA (reference :884-908)
                amount = mcheli.agnostic.vehicle.VehicleArmor.apply(amount, factor,
                    info.armorMaxDamage, info.armorDamageFactor, info.armorMinDamage);
                if (amount <= 0.0F) return false;                               // fully absorbed (no thud either)
            }
            if (!playerBreak) v.setDamageTaken(v.getDamageTaken() + (int) amount);   // (int) truncation (reference :911)
            v.markHurtBridge();                                                  // client hit-wobble (reference :914)
            if (v.getDamageTaken() >= v.getMaxHp() || playerBreak) {
                if (!playerBreak) {
                    v.setDamageTaken(v.getMaxHp());
                    v.destroyVehicle(src.getEntity(), src.is(DamageTypes.IN_WALL));
                    this.mchTimeSinceHit = 20;
                } else {
                    // Salvage: no wreck; the spawn item drops by the reference rule (:937-952; a creative player
                    // gets it only when sneaking unless DropItemInCreativeMode), carrying damage + fuel.
                    if (mcheli.agnostic.vehicle.VehicleSalvage.dropsItem(creative, attacker.isShiftKeyDown(),
                            mcheli.dependent.config.MchConfig.SERVER.dropItemInCreativeMode())) {
                        dropSelfItem();
                    }
                    v.discard();
                }
            }
        } else if (mcheli.agnostic.vehicle.VehicleSalvage.clearsWreck(playerBreak, creative)) {
            v.discard();               // a creative player clears an existing wreck (reference :955-957)
        }
        if (attacker == null) {
            v.playMchSound("helidmg", 1.0F, 0.9F + v.getRandom().nextFloat() * 0.1F);
        }
        return true;
    }

    /** Reference {@code isDamegeSourcePlayer} (:861-877) with the server options {@code PreventingBroken} and
     *  {@code BreakableOnlyPickaxe}: does this hit reclaim the vehicle instead of damaging it? */
    private boolean isPlayerBreak(DamageSource src) {
        if (!(src.getEntity() instanceof Player p)) {
            return false;
        }
        ItemStack held = p.getMainHandItem();
        boolean pickaxe = held.getItem() instanceof net.minecraft.world.item.PickaxeItem
            || held.is(net.minecraft.tags.ItemTags.PICKAXES);
        return mcheli.agnostic.vehicle.VehicleSalvage.isPlayerBreak(src.is(DamageTypes.PLAYER_ATTACK),
            p.getAbilities().instabuild, mcheli.dependent.config.MchConfig.SERVER.preventingBroken(),
            mcheli.dependent.config.MchConfig.SERVER.breakableOnlyPickaxe(), pickaxe, v.isRidePlayer());
    }

    /** True while any seat holds a player (reference {@code isRidePlayer}:3504-3516). */
    boolean isRidePlayer() {
        for (Entity p : v.getPassengers()) {
            if (p instanceof Player) {
                return true;
            }
        }
        return false;
    }

    /** Reference {@code dropItemWithOffset(item, 1, 0)} → {@code entityDropItem}, which stamps the vehicle data on the
     *  stack ({@link #writeItemData}). */
    private void dropSelfItem() {
        Item it = v.dropItem();
        if (it != null && v.level() instanceof ServerLevel) {
            ItemStack stack = new ItemStack(it);
            v.writeItemData(stack);
            v.spawnAtLocation(stack, 0.0F);
        }
    }

    // ---- vehicle data on the spawn item (reference setAcDataToItem / getAcDataFromItem, MCH_EntityAircraft:987-1015) ----

    /** Custom-data keys on a recovered spawn item. The reference stored damage as the item's damage value; a 1.21
     *  stack clamps that to the item's max damage, which a vehicle item has none of (giving it one would make the
     *  vehicle item enchantable and anvil-repairable), so both values live in custom data. */
    private static final String ITEM_FUEL = "MCH_Fuel";
    private static final String ITEM_DAMAGE = "MCH_Damage";

    /** Stamp this vehicle's fuel and damage on {@code stack} when the server options {@code ItemFuel} /
     *  {@code ItemDamage} are on (both default on). Reference {@code setAcDataToItem}. */
    void writeItemData(ItemStack stack) {
        boolean fuel = mcheli.dependent.config.MchConfig.SERVER.itemFuel();
        boolean damage = mcheli.dependent.config.MchConfig.SERVER.itemDamage();
        if (!fuel && !damage) {
            return;
        }
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            stack, tag -> {
                if (fuel) {
                    tag.putInt(ITEM_FUEL, v.getFuel());
                }
                if (damage) {
                    tag.putInt(ITEM_DAMAGE, v.getDamageTaken());
                }
            });
    }

    /** SERVER: restore the fuel and damage a recovered spawn item carries, under the same options. Call after
     *  {@link AbstractMchVehicle#setConfigName} (both values clamp to the config). Reference {@code getAcDataFromItem}. */
    void readItemData(ItemStack stack) {
        net.minecraft.world.item.component.CustomData data =
            stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return;
        }
        CompoundTag tag = data.copyTag();
        if (mcheli.dependent.config.MchConfig.SERVER.itemFuel() && tag.contains(ITEM_FUEL)) {
            v.setFuel(tag.getInt(ITEM_FUEL));
        }
        if (mcheli.dependent.config.MchConfig.SERVER.itemDamage() && tag.contains(ITEM_DAMAGE)) {
            v.setDamageTaken(tag.getInt(ITEM_DAMAGE));
        }
    }

    // ---- the wreck (the base bodies of the entity's destruction and tumble hooks) ----

    /**
     * Turn the craft into a wreck (reference {@code destroyAircraft} + the destruction branch of attackEntityFrom):
     * flip the synced destroyed flag, arm the despawn countdown, seed the tumble, eject riders, and spawn the death
     * blast — {@link AbstractMchVehicle#explosionByCrash} for a terrain ("inWall") kill, else a fixed power-2 explosion.
     *
     * @param killer the attacker, or null; a PLAYER killer is credited with the death blast, which spares nobody
     *               (reference :933-935: {@code newExplosion(world, null, entity, ...)}, and {@code MCH_Explosion}
     *               keeps {@code explodedPlayer} only for an {@code EntityPlayer})
     */
    void baseDestroyVehicle(Entity killer, boolean terrainKill) {
        if (v.level().isClientSide) return;
        v.getEntityData().set(AbstractMchVehicle.DATA_DESTROYED, true);
        this.despawnCount = VehicleWreck.DESPAWN_TICKS;
        v.seedWreckTumble();
        v.ejectPassengers();
        if (v.level() instanceof ServerLevel sl) {
            if (terrainKill) {
                v.explosionByCrash(0.0);
                this.damageSinceDestroyed = v.getMaxHp();
            } else {
                MchExplosion.explode(sl, v.position(), 2, 2, true, true, 5, null, null,
                    killer instanceof Player ? killer : null);
            }
        }
    }

    /** Seed the wreck tumble (reference heli default). Subclasses override: the tank zeroes it (no tumble), the plane
     *  rolls further into its existing bank. */
    void baseSeedWreckTumble() {
        float r1 = v.getRandom().nextFloat();
        float r2 = v.getRandom().nextFloat();
        float[] seed = VehicleWreck.heliSeed(r1, r2); // {yaw, pitch, roll}; pitch draws first, as in the reference
        this.rotDestroyedPitch = seed[1];
        this.rotDestroyedRoll = seed[2];
        this.rotDestroyedYaw = seed[0];
    }

    /** Apply one tick of wreck tumble (reference heli). Overridable: the plane gates roll differently, the tank no-ops.
     *  The pitch/roll tumble freezes once a block is within 3 below (reference {@code getBlockIdY(this,3,-3)==0}), so the
     *  hull settles before impact — NOT the vanilla {@code onGround()} flag, which only trips on literal contact. The
     *  yaw spin scales with the wreck's flight-sim throttle (reference {@code getCurrentThrottle()}), which bleeds off
     *  near the ground and after the crash ({@code AircraftFlightController.decayWreckThrottle}). */
    void baseApplyWreckTumble() {
        this.rotDestroyedYaw = VehicleWreck.nextYawSeed(this.rotDestroyedYaw);
        v.setYRot(v.getYRot() + this.rotDestroyedYaw * (float) v.simThrottle());
        if (!v.solidBlockBelow()) {
            if (Math.abs(Mth.wrapDegrees(v.getXRot())) < 10.0F) v.setXRot(v.getXRot() + this.rotDestroyedPitch);
            v.setRollAngle(v.getRollAngle() + this.rotDestroyedRoll);
        }
    }

    /** Read-only accessors for subclass tumble overrides. */
    float baseRotDestroyedRoll() { return this.rotDestroyedRoll; }
    float baseRotDestroyedPitch() { return this.rotDestroyedPitch; }
    void baseSetRotDestroyedRoll(float r) { this.rotDestroyedRoll = r; }
    void baseSetRotDestroyedPitch(float p) { this.rotDestroyedPitch = p; }
    void baseSetRotDestroyedYaw(float y) { this.rotDestroyedYaw = y; }

    /** A fuel-scaled AOE fireball at the wreck (reference {@code explosionByCrash}); {@code MaxFuel = 0} → power 1.
     *  The reference clamps maxFuel/400 to [1,15]; with no config it defaults the blast to 2.0 (not the 0-fuel floor of
     *  1): {@link VehicleWreck#crashBlast}. */
    void baseExplosionByCrash(double prevMotionY) {
        if (!(v.level() instanceof ServerLevel sl)) return;
        MCH_AircraftInfo info = v.weaponHostInfo();
        int[] blast = VehicleWreck.crashBlast(info != null, info != null ? info.maxFuel : 0); // {power, block}
        MchExplosion.explode(sl, v.position(), blast[0], blast[1], true, true, 5, null, null, null); // reference :2043: no one
    }

    /** Wreck lifetime countdown (500 at destruction, →0 then discard); exposed for diagnostics/self-test. */
    int despawnCount() { return this.despawnCount; }
}
