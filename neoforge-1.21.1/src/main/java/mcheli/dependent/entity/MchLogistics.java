package mcheli.dependent.entity;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.weapon.WeaponSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The fuel and supply economy of one vehicle (#37) — reference MCH_EntityAircraft:2223-2308 (fuel), :2264-2451 (supply
 * auras): the fuel burn, the three fuel-can slots and their siphon, the fuel and ammo supply auras a config may declare,
 * the can spill on removal, and the {@code AcFuel}/{@code FuelSlots} NBT. The only writer of
 * {@link AbstractMchVehicle#DATA_FUEL}. Server logic; the public fuel queries are delegates on the entity.
 */
final class MchLogistics {

    private final AbstractMchVehicle v;

    // The burn's fractional carry, so a <1/sec burn rate still drains over time (rates: agnostic VehicleFuel).
    private final mcheli.agnostic.vehicle.VehicleFuel fuelBurn = new mcheli.agnostic.vehicle.VehicleFuel();
    private int fuelSuppliedCount;     // ticks of consumption PAUSE granted by a nearby supplier (reference sets 40)

    /** The vehicle's {@link AbstractMchVehicle#FUEL_SLOTS} fuel slots: one container for the entity's lifetime (the
     *  riding menu keeps the reference it is handed). */
    private final net.minecraft.world.SimpleContainer fuelInventory =
        new net.minecraft.world.SimpleContainer(AbstractMchVehicle.FUEL_SLOTS);

    MchLogistics(AbstractMchVehicle v) {
        this.v = v;
    }

    net.minecraft.world.SimpleContainer fuelInventory() {
        return this.fuelInventory;
    }

    /** Server tick, in the entity's tick order: burn, then the can siphon, then any supply aura this config declares. */
    void tickServer() {
        updateFuel();
        siphonFuelSlots();   // fuel cans in the GUI slots -> the tank
        updateSupply();
    }

    /** NBT load, at the entity's AcFuel/FuelSlots position: the fuel is a raw set (no clamp), as before the split. */
    void load(CompoundTag tag) {
        v.getEntityData().set(AbstractMchVehicle.DATA_FUEL, tag.getInt("AcFuel"));
        if (tag.contains("FuelSlots")) {
            this.fuelInventory.fromTag(tag.getList("FuelSlots", net.minecraft.nbt.Tag.TAG_COMPOUND), v.registryAccess());
        }
    }

    /** NBT save, at the entity's AcFuel/FuelSlots position. */
    void save(CompoundTag tag) {
        tag.putInt("AcFuel", v.getFuel());
        tag.put("FuelSlots", this.fuelInventory.createTag(v.registryAccess()));
    }

    /** A supplier's fuel aura pauses this vehicle's burn (reference: the supplier sets the receiver's
     *  {@code fuelSuppliedCount}), even when the tank is already full. */
    void pauseBurnForSupply() {
        this.fuelSuppliedCount = mcheli.agnostic.vehicle.VehicleFuel.SUPPLIED_PAUSE;
    }

    /**
     * Siphon the fuel slots into the tank — the port of {@code MCH_EntityAircraft.updateSupplyFuel} (:2310-2335): every
     * 10 ticks, while the vehicle {@link #canSupply() is on the ground} and the tank is not full, each can gives up to
     * {@code 100} units (capped by what the tank needs and what the can still holds).
     */
    private void siphonFuelSlots() {
        if (v.level().isClientSide || v.getMaxFuel() <= 0
            || v.tickCount % mcheli.agnostic.vehicle.VehicleFuel.SIPHON_INTERVAL != 0) {
            return;
        }
        // The reference wraps BOTH the burn and this siphon in !isDestroyed() (MCH_EntityAircraft:2295): a wreck lies
        // grounded for ~25 s before it despawns, and without this gate canSupply() would stay true and drain the cans
        // it is about to drop.
        if (v.isDestroyed() || v.getFuel() >= v.getMaxFuel() || !v.canSupply()) {
            return;
        }
        int fuel = v.getFuel();
        for (int i = 0; i < AbstractMchVehicle.FUEL_SLOTS && fuel < v.getMaxFuel(); i++) {
            ItemStack can = this.fuelInventory.getItem(i);
            if (can.getItem() instanceof mcheli.dependent.item.MchFuelItem) {
                int want = mcheli.agnostic.vehicle.VehicleFuel.siphonWant(fuel, v.getMaxFuel());
                fuel += mcheli.dependent.item.MchFuelItem.drain(can, want);
            }
        }
        v.setFuel(fuel);
    }

    /**
     * Spill the fuel slots when the vehicle leaves the world for good — the port of {@code MCH_AircraftInventory.setDead}
     * (:63-75), which drops the inventory contents on death. Gated on the removal REASON so a chunk unload or a
     * dimension change (which also remove the entity) can never duplicate the cans.
     */
    void spillOnRemove(Entity.RemovalReason reason) {
        if (!v.level().isClientSide
            && (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED)) {
            net.minecraft.world.Containers.dropContents(v.level(), v, this.fuelInventory);
        }
    }

    void setFuel(int f) {
        if (!v.level().isClientSide) {
            v.getEntityData().set(AbstractMchVehicle.DATA_FUEL, Math.max(0, Math.min(f, v.getMaxFuel())));
        }
    }

    /** Fuel fraction 0..1 for the HUD gauge — faithfully 0 when the config declares no tank (reference
     *  {@code getFuelP}:2247-2250; the HUD's low-fuel warning gates on {@code maxFuel>0} so nothing false-alarms). */
    float getFuelP() {
        return mcheli.agnostic.vehicle.VehicleFuel.fraction(v.getFuel(), v.getMaxFuel());
    }

    /** The server option {@code InfinityFuel} or a creative occupant makes fuel free — reference
     *  {@code isInfinityFuel}:402-416 (the option is its {@code commonStatus} bit 4, set from the config at :329-330 and
     *  :1042-1043; the server config is synced, so the client HUD reads the same answer). It only keeps the engine
     *  running below the fuel floor; the tank still burns down, as in the reference.
     *  {@code anySeat}: scan EVERY rider (the heli's hover gate uses this variant), else only the pilot. */
    boolean isInfinityFuel(boolean anySeat) {
        if (mcheli.dependent.config.MchConfig.SERVER.infinityFuel()) {
            return true;
        }
        if (anySeat) {
            for (Entity p : v.getPassengers()) {
                if (p instanceof Player pl && pl.getAbilities().instabuild) {
                    return true;
                }
            }
            return false;
        }
        Entity p = v.pilot();
        return p instanceof Player pl && pl.getAbilities().instabuild;
    }

    /** Can the engine still run? Reference {@code canUseFuel}:2252-2258 — note the floor is fuel {@code > 1}, not 0. */
    boolean canUseFuel(boolean anySeat) {
        // isInfinityFuel scans the riders, so it is only asked once the tank itself says no.
        return mcheli.agnostic.vehicle.VehicleFuel.canUseFuel(v.getMaxFuel(), v.getFuel(), false) || v.isInfinityFuel(anySeat);
    }

    /** Receivers must be on solid ground to take on fuel/ammo (reference {@code canSupply}:2223-2225) — a floating
     *  vehicle is exempt from the water rule. */
    boolean canSupply() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        boolean floats = info != null && info.isFloat;
        if (!floats && v.isInWater()) {
            return false;
        }
        // Reference MCH_Lib.getBlockIdY(this, 1, -3): start at (int)(posY + 0.5) and scan 3 blocks down for a
        // COLLIDABLE block (a plant/torch does not count as ground, and the probe is not shifted a block low).
        BlockPos from = BlockPos.containing(v.getX(), v.getY() + 0.5, v.getZ());
        for (int i = 0; i < 3; i++) {
            BlockPos at = from.below(i);
            if (!v.level().getBlockState(at).getCollisionShape(v.level(), at).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Burn fuel with the throttle (reference {@code updateFuel}:2289-2308): once a second, while actually under power
     *  and not currently being resupplied; the {@code >1} floor mirrors the reference exactly. */
    private void updateFuel() {
        if (v.getMaxFuel() == 0) {
            return;
        }
        if (this.fuelSuppliedCount > 0) {
            this.fuelSuppliedCount--;
        }
        if (v.isDestroyed()) {
            return;
        }
        MCH_AircraftInfo info = v.weaponHostInfo();
        double throttle = v.simThrottle();
        if (info != null && mcheli.agnostic.vehicle.VehicleFuel.burnsThisTick(v.tickCount, v.getFuel(), throttle,
                this.fuelSuppliedCount)) {
            // Factor 1.0: every reference category's getFuelConsumptionFactor is 1 (MCP_EntityPlane's VTOL term too).
            int burn = this.fuelBurn.burn(throttle, info.fuelConsumption, 1.0F);
            if (burn > 0) {
                v.setFuel(v.getFuel() - burn);
            }
        }
    }

    /** Fuel/ammo supply auras — a config with {@code FuelSupplyRange}/{@code AmmoSupplyRange} (the fuel truck, the
     *  ammo box) tops up nearby vehicles. The supplier is an infinite source (the reference never decrements it), and
     *  the {@code !onGround} term is SUPPLIER-side: an airborne tanker refuels flying receivers, a grounded one
     *  requires the receiver to be landed. Reference :2264-2287 / :2418-2451. */
    private void updateSupply() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (info == null || v.level().isClientSide) {
            return; // NOTE: no isDestroyed gate — the reference keeps a wrecked truck/box supplying (only BURN stops)
        }
        if (info.fuelSupplyRange > 0.0F && v.tickCount % mcheli.agnostic.vehicle.VehicleFuel.SUPPLY_INTERVAL == 0) {
            for (AbstractMchVehicle o : v.nearby(info.fuelSupplyRange)) {
                if ((!v.onGround() || o.canSupply()) && o.getFuel() < o.getMaxFuel()) {
                    o.setFuel(o.getFuel() + mcheli.agnostic.vehicle.VehicleFuel.supplyPulse(o.getFuel(), o.getMaxFuel()));
                }
                // unconditional: even a full tank pauses its burn while in the aura
                o.logistics().pauseBurnForSupply();
            }
        }
        if (info.ammoSupplyRange > 0.0F
                && v.tickCount % mcheli.agnostic.vehicle.VehicleFuel.AMMO_SUPPLY_INTERVAL == 0) {
            for (AbstractMchVehicle o : v.nearby(info.ammoSupplyRange)) {
                if (!o.canSupply() || o.weaponLoadout() == null) {
                    continue;
                }
                for (WeaponSlot s : o.weaponLoadout().economySlots()) {
                    if (s.totalAmmo() < s.maxAmmo()) {
                        s.setRestAllAmmo(s.totalAmmo() + mcheli.agnostic.vehicle.VehicleFuel.ammoSupplyPulse(s.maxAmmo()));
                        if (s.magazine() <= 0) {
                            s.reloadMag();
                        }
                    }
                }
            }
        }
    }
}
