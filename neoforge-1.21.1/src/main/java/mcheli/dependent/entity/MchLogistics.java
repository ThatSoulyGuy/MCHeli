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
 * auras): the fuel burn, the three fuel-can slots and their siphon, the two parachute slots the ejection seat draws
 * from (reference {@code MCH_AircraftInventory}), the fuel and ammo supply auras a config may declare, the cargo hold
 * ({@code InventorySize}, reference {@code W_EntityContainer}), the spill of all of them on removal, and the
 * {@code AcFuel}/{@code FuelSlots}/{@code Items} NBT. The only writer of {@link AbstractMchVehicle#DATA_FUEL}. Server
 * logic; the public fuel, parachute and cargo queries are delegates on the entity.
 */
final class MchLogistics {

    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    private final AbstractMchVehicle v;

    // The burn's fractional carry, so a <1/sec burn rate still drains over time (rates: agnostic VehicleFuel).
    private final mcheli.agnostic.vehicle.VehicleFuel fuelBurn = new mcheli.agnostic.vehicle.VehicleFuel();
    private int fuelSuppliedCount;     // ticks of consumption PAUSE granted by a nearby supplier (reference sets 40)

    /** The vehicle's {@link AbstractMchVehicle#FUEL_SLOTS} fuel slots: one container for the entity's lifetime (the
     *  riding menu keeps the reference it is handed). */
    private final net.minecraft.world.SimpleContainer fuelInventory =
        new net.minecraft.world.SimpleContainer(AbstractMchVehicle.FUEL_SLOTS);

    /** The vehicle's {@link AbstractMchVehicle#PARACHUTE_SLOTS} parachute slots, the reference
     *  {@code MCH_AircraftInventory} slots 3 and 4 ({@code SLOT_PARACHUTE0/1}, :17-18): one container for the entity's
     *  lifetime, on every vehicle (as the reference inventory had them); the riding menu shows
     *  {@link AbstractMchVehicle#numEjectionSeats()} of them. */
    private final net.minecraft.world.SimpleContainer parachuteInventory =
        new net.minecraft.world.SimpleContainer(AbstractMchVehicle.PARACHUTE_SLOTS);

    /** The {@code SlotAC} of the first parachute slot in {@code FuelSlots}: the reference layout puts the parachutes
     *  right after the {@link AbstractMchVehicle#FUEL_SLOTS} fuel slots ({@code SLOT_PARACHUTE0 = 3}). */
    private static final int PARACHUTE_SLOT_AC = AbstractMchVehicle.FUEL_SLOTS;

    MchLogistics(AbstractMchVehicle v) {
        this.v = v;
    }

    net.minecraft.world.SimpleContainer fuelInventory() {
        return this.fuelInventory;
    }

    net.minecraft.world.SimpleContainer parachuteInventory() {
        return this.parachuteInventory;
    }

    /** Reference {@code MCH_AircraftInventory.haveParachute}:34-43: either slot holds a parachute (both slots count,
     *  whatever {@link AbstractMchVehicle#numEjectionSeats()} the menu shows, as there). */
    boolean haveParachute() {
        for (int i = 0; i < this.parachuteInventory.getContainerSize(); i++) {
            if (this.parachuteInventory.getItem(i).getItem() instanceof mcheli.dependent.item.MchParachuteItem) {
                return true;
            }
        }
        return false;
    }

    /** How many parachute slots hold a parachute (0 to 2): how many ejections {@code haveParachute} would allow in a row
     *  (each {@link #consumeParachute} empties one slot). */
    int parachuteCount() {
        int n = 0;
        for (int i = 0; i < this.parachuteInventory.getContainerSize(); i++) {
            if (this.parachuteInventory.getItem(i).getItem() instanceof mcheli.dependent.item.MchParachuteItem) {
                n++;
            }
        }
        return n;
    }

    /** Reference {@code MCH_AircraftInventory.consumeParachute}:45-53: empty the first slot holding a parachute (slot 0
     *  before slot 1); nothing when neither does. The whole slot is cleared, as there (a parachute stacks to 1). */
    void consumeParachute() {
        for (int i = 0; i < this.parachuteInventory.getContainerSize(); i++) {
            if (this.parachuteInventory.getItem(i).getItem() instanceof mcheli.dependent.item.MchParachuteItem) {
                this.parachuteInventory.setItem(i, ItemStack.EMPTY);
                return;
            }
        }
    }

    /** The cargo hold: the aircraft's own inventory in the reference ({@code MCH_EntityAircraft extends
     *  W_EntityContainer}, {@code getSizeInventory}:5670-5672 = the config {@code InventorySize}, 0 to 54), apart from
     *  the fuel slots of {@code MCH_AircraftInventory}. Built on first use, and rebuilt by {@link #cargo()} when the
     *  config size changes. Server state: a client's chest screen keeps its own copy, filled by the menu sync. */
    private net.minecraft.world.SimpleContainer cargo;
    /** Cargo stacks the hold has no free slot for, each with the slot it was saved in: a slot past a smaller
     *  {@code InventorySize} (an edited config or pack), or a slot two entries claim. Deliberate divergence: the
     *  reference's {@code readEntityFromNBT} (:187-192) dropped them, so a config edit deleted cargo. Here they are
     *  saved back, move into the hold when the size allows, and spill with it. */
    private final java.util.List<AsideStack> cargoAside = new java.util.ArrayList<>();

    private record AsideStack(int slot, ItemStack stack) {}

    /** The hold's container. Its {@code stillValid}, which the vanilla chest menu asks, is the reference
     *  {@code isUseableByPlayer} ({@link #cargoUsableBy}). */
    private final class CargoHold extends net.minecraft.world.SimpleContainer {
        CargoHold(int size) {
            super(size);
        }

        @Override
        public boolean stillValid(Player player) {
            return cargoUsableBy(this, player);
        }
    }

    /**
     * The cargo hold at the current config size. When the size differs from the hold's (another config, or a save
     * loaded under an edited one) every stack moves to a new hold of the new size, slot for slot; a stack whose slot is
     * gone waits in {@link #cargoAside}. A chest screen still open on the old hold fails {@code stillValid} and closes.
     */
    net.minecraft.world.SimpleContainer cargo() {
        int size = v.cargoSize();
        if (this.cargo == null || this.cargo.getContainerSize() != size) {
            net.minecraft.world.SimpleContainer old = this.cargo;
            this.cargo = new CargoHold(size);
            if (old != null) {
                java.util.List<AsideStack> moved = new java.util.ArrayList<>();
                for (int i = 0; i < old.getContainerSize(); i++) {
                    ItemStack stack = old.removeItemNoUpdate(i);
                    if (!stack.isEmpty()) {
                        moved.add(new AsideStack(i, stack));
                    }
                }
                this.cargoAside.addAll(0, moved); // the old hold's stacks reclaim their slots before older asides
            }
            restoreAside();
        }
        return this.cargo;
    }

    /** Move every stack kept aside whose slot the hold now has, and has free, into that slot. */
    private void restoreAside() {
        for (java.util.Iterator<AsideStack> it = this.cargoAside.iterator(); it.hasNext(); ) {
            AsideStack a = it.next();
            if (a.slot() < this.cargo.getContainerSize() && this.cargo.getItem(a.slot()).isEmpty()) {
                this.cargo.setItem(a.slot(), a.stack());
                it.remove();
            }
        }
    }

    /**
     * Reference {@code MCH_EntityAircraft.isUseableByPlayer}:1018-1025: never after removal (the reference's
     * {@code isDead}); a seated rider within 64 blocks, anyone else within 8; on a UAV everyone within 8 (the
     * {@code W_EntityContainer} rule, :89-91). A hold the vehicle has since replaced (a size change) is not usable.
     */
    private boolean cargoUsableBy(net.minecraft.world.SimpleContainer hold, Player player) {
        if (v.isRemoved() || hold != cargo()) {
            return false;
        }
        MCH_AircraftInfo info = v.weaponHostInfo();
        boolean uav = info != null && info.isUAV;
        double reachSq = !uav && v.seatIndexOf(player) >= 0 ? 4096.0 : 64.0;
        return player.distanceToSqr(v) <= reachSq;
    }

    /**
     * Open the cargo hold for {@code player} — reference {@code W_EntityContainer.openInventory}:201-208, reached from
     * the sneaking click ({@code interactFirst}:4326-4329) and the riding GUI's Inventory button
     * ({@code MCH_CommonPacketHandler.onPacketIndOpenScreen}:36-40). It is the vanilla chest screen, as
     * {@code displayGUIChest} was, with {@code size / 9} rows (the 1.7.10 {@code ContainerChest}/{@code GuiChest} rule),
     * so the 10-slot holds show 9 slots, as they did there. Returns whether a screen opens; a client only predicts that,
     * the server opens it.
     */
    boolean openCargo(Player player) {
        int rows = v.cargoRows();
        if (rows <= 0) {
            // Also InventorySize 1 to 8 (no bundled config): 1.21 has no zero-row chest menu, and the reference's
            // zero-row screen showed no cargo slot, so nothing opens.
            return false;
        }
        if (!v.level().isClientSide) {
            net.minecraft.world.SimpleContainer hold = cargo();
            player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inv, p) -> new net.minecraft.world.inventory.ChestMenu(chestMenuType(rows), id, inv, hold, rows),
                cargoTitle()));
        }
        return true;
    }

    /** The vanilla chest menu type of {@code rows} rows (1 to 6). */
    private static net.minecraft.world.inventory.MenuType<net.minecraft.world.inventory.ChestMenu> chestMenuType(
            int rows) {
        return switch (rows) {
            case 1 -> net.minecraft.world.inventory.MenuType.GENERIC_9x1;
            case 2 -> net.minecraft.world.inventory.MenuType.GENERIC_9x2;
            case 3 -> net.minecraft.world.inventory.MenuType.GENERIC_9x3;
            case 4 -> net.minecraft.world.inventory.MenuType.GENERIC_9x4;
            case 5 -> net.minecraft.world.inventory.MenuType.GENERIC_9x5;
            default -> net.minecraft.world.inventory.MenuType.GENERIC_9x6;
        };
    }

    /** The chest screen's title — reference {@code MCH_EntityAircraft.getInvName}:5675-5682: the display name, cut to
     *  31 characters when it is longer than 32. */
    private net.minecraft.network.chat.Component cargoTitle() {
        String s = v.displayName();
        return net.minecraft.network.chat.Component.literal(s.length() <= 32 ? s : s.substring(0, 31));
    }

    /** Server tick, in the entity's tick order: burn, then the can siphon, then any supply aura this config declares. */
    void tickServer() {
        updateFuel();
        siphonFuelSlots();   // fuel cans in the GUI slots -> the tank
        updateSupply();
    }

    /** Per-entry slot index in {@code FuelSlots}, the reference's own key ({@code MCH_AircraftInventory}:190). */
    private static final String SLOT_KEY = "SlotAC";

    /** The cargo hold's NBT list and its per-entry slot index: the reference's own keys
     *  ({@code W_EntityContainer.writeEntityToNBT}:167-180, {@code Slot} as a byte), the vanilla chest format too. */
    private static final String CARGO_KEY = "Items";
    private static final String CARGO_SLOT_KEY = "Slot";

    /** NBT load, at the entity's AcFuel/FuelSlots/Items position: the fuel is a raw set (no clamp), as before the split.
     *  Runs after the config is restored, so the hold is sized for it. */
    void load(CompoundTag tag) {
        v.getEntityData().set(AbstractMchVehicle.DATA_FUEL, tag.getInt("AcFuel"));
        if (tag.contains("FuelSlots")) {
            loadFuelSlots(tag.getList("FuelSlots", net.minecraft.nbt.Tag.TAG_COMPOUND));
        }
        loadCargo(tag.getList(CARGO_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)); // no key (a save before the hold): empty
    }

    /** NBT save, at the entity's AcFuel/FuelSlots/Items position. {@code FuelSlots} is the reference {@code ItemsAC}
     *  list ({@code MCH_AircraftInventory.writeEntityToNBT}:184-197): every non-empty slot of the riding inventory with
     *  its {@code SlotAC} byte, the fuel cans as 0 to 2 and the parachutes as 3 and 4, in slot order, so each reloads
     *  where it was (vanilla's {@code SimpleContainer.createTag} saved no slot). A vehicle with no parachute saves
     *  exactly what it did before the parachute slots. */
    void save(CompoundTag tag) {
        tag.putInt("AcFuel", v.getFuel());
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < this.fuelInventory.getContainerSize(); i++) {
            addSlotEntry(list, i, this.fuelInventory.getItem(i));
        }
        for (int i = 0; i < this.parachuteInventory.getContainerSize(); i++) {
            addSlotEntry(list, PARACHUTE_SLOT_AC + i, this.parachuteInventory.getItem(i));
        }
        tag.put("FuelSlots", list);
        saveCargo(tag);
    }

    private void addSlotEntry(net.minecraft.nbt.ListTag list, int slotAc, ItemStack stack) {
        if (!stack.isEmpty()) {
            CompoundTag entry = new CompoundTag();
            entry.putByte(SLOT_KEY, (byte) slotAc);
            list.add(stack.save(v.registryAccess(), entry));
        }
    }

    /** Reference {@code W_EntityContainer.writeEntityToNBT}:167-180: {@code Items}, one entry per stack with its
     *  {@code Slot} byte, then the stacks kept aside with their own slots. A vehicle without a hold and with nothing
     *  aside writes no key (the reference wrote an empty list), so its save is the same as before the hold. */
    private void saveCargo(CompoundTag tag) {
        net.minecraft.world.SimpleContainer hold = cargo();
        if (hold.getContainerSize() == 0 && this.cargoAside.isEmpty()) {
            return;
        }
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < hold.getContainerSize(); i++) {
            ItemStack stack = hold.getItem(i);
            if (!stack.isEmpty()) {
                list.add(cargoEntry(i, stack));
            }
        }
        for (AsideStack a : this.cargoAside) {
            list.add(cargoEntry(a.slot(), a.stack()));
        }
        tag.put(CARGO_KEY, list);
    }

    private net.minecraft.nbt.Tag cargoEntry(int slot, ItemStack stack) {
        CompoundTag entry = new CompoundTag();
        entry.putByte(CARGO_SLOT_KEY, (byte) slot);
        return stack.save(v.registryAccess(), entry);
    }

    /** Reference {@code W_EntityContainer.readEntityFromNBT}:182-194: the hold is refilled at the config size, each entry
     *  in its {@code Slot} (a byte read unsigned). An entry for a slot the hold lacks or has already filled is kept
     *  aside ({@link #cargoAside}) where the reference dropped it. */
    private void loadCargo(net.minecraft.nbt.ListTag list) {
        net.minecraft.world.SimpleContainer hold = cargo();
        hold.clearContent();
        this.cargoAside.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            java.util.Optional<ItemStack> stack = ItemStack.parse(v.registryAccess(), entry);
            if (stack.isEmpty()) {
                continue;
            }
            int slot = entry.getByte(CARGO_SLOT_KEY) & 255;
            if (slot < hold.getContainerSize() && hold.getItem(slot).isEmpty()) {
                hold.setItem(slot, stack.get());
            } else {
                this.cargoAside.add(new AsideStack(slot, stack.get()));
            }
        }
        if (!this.cargoAside.isEmpty()) {
            LOG.warn("MCHeli: {} ({}) holds {} cargo stack(s) its InventorySize {} has no free slot for; they are kept "
                + "in the save and spill with the vehicle", v.configName(), v.getUUID(), this.cargoAside.size(),
                hold.getContainerSize());
        }
    }

    /** Reference {@code MCH_AircraftInventory.readEntityFromNBT}:199-210: an entry goes to its {@code SlotAC} slot (a
     *  byte read unsigned): 0 to 2 a fuel slot, 3 and 4 a parachute slot; one out of range is dropped, as there. An
     *  entry without the key, from a save before the port wrote it (which held only fuel slots), fills the fuel slots
     *  left free in list order, the vanilla {@code SimpleContainer.fromTag} the port used to load with. */
    private void loadFuelSlots(net.minecraft.nbt.ListTag list) {
        this.fuelInventory.clearContent();
        this.parachuteInventory.clearContent();
        java.util.List<ItemStack> unslotted = new java.util.ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            java.util.Optional<ItemStack> stack = ItemStack.parse(v.registryAccess(), entry);
            if (stack.isEmpty()) {
                continue;
            }
            if (!entry.contains(SLOT_KEY, net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) {
                unslotted.add(stack.get());
                continue;
            }
            int slot = entry.getByte(SLOT_KEY) & 255;
            if (slot < this.fuelInventory.getContainerSize()) {
                this.fuelInventory.setItem(slot, stack.get());
            } else if (slot - PARACHUTE_SLOT_AC < this.parachuteInventory.getContainerSize()) {
                this.parachuteInventory.setItem(slot - PARACHUTE_SLOT_AC, stack.get());
            }
        }
        for (ItemStack stack : unslotted) {
            this.fuelInventory.addItem(stack);
        }
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
     * Spill the cargo hold, then the fuel slots and the parachute slots, when the vehicle leaves the world for good —
     * the port of {@code W_EntityContainer.setDead} (:127-162) and {@code MCH_AircraftInventory.setDead} (:63-75, its
     * slots in order: fuel 0 to 2, parachutes 3 and 4), in that order, which drop the contents when
     * {@code setDead(true)} kills the aircraft (the wreck despawn, salvage, a creative kill, destruct). Gated on the removal REASON so a chunk unload or a dimension change (which also remove the
     * entity) can never duplicate the items; the reference kept them on its plain {@code setDead()} and on
     * {@code travelToDimension} too. The stacks kept aside spill with the hold.
     */
    void spillOnRemove(Entity.RemovalReason reason) {
        if (!v.level().isClientSide
            && (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED)) {
            if (this.cargo != null) {
                net.minecraft.world.Containers.dropContents(v.level(), v, this.cargo);
            }
            for (AsideStack a : this.cargoAside) {
                net.minecraft.world.Containers.dropItemStack(v.level(), v.getX(), v.getY(), v.getZ(), a.stack());
            }
            this.cargoAside.clear();
            net.minecraft.world.Containers.dropContents(v.level(), v, this.fuelInventory);
            net.minecraft.world.Containers.dropContents(v.level(), v, this.parachuteInventory);
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
