package mcheli.dependent.menu;

import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.item.MchFuelItem;
import mcheli.dependent.item.MchParachuteItem;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The riding menu — the 1.21.1 port of {@code MCH_AircraftGuiContainer}. Three FUEL slots (which the vehicle siphons
 * into its tank while it is on the ground), one PARACHUTE slot per ejection seat (the ejection seat draws from them),
 * then the player's inventory. The reload button lives on the
 * {@link mcheli.dependent.client.screen.MchVehicleScreen screen} and is serviced by
 * {@link mcheli.dependent.control.ServerboundVehicleGuiPayload}.
 *
 * <p>Slot coordinates are the reference's: fuel at (10,30)/(10,48)/(10,66), the parachutes at {@code (10 + i·18, 105)}
 * for {@code i < numEjectionSeats()} (:22-26), the inventory grid at {@code (25 + x·18, 135 + y·18)} and the hotbar at
 * {@code (25 + x·18, 195)}. The slot list is fuel, parachutes, inventory, hotbar, so the player's slots start at
 * {@code 3 + numEjectionSeats()} ({@code getInventoryStartIndex}, :39-41).
 */
public class MchVehicleMenu extends AbstractContainerMenu {

    /** The end (exclusive) of the fuel slots, which start the list. */
    public static final int FUEL_END = AbstractMchVehicle.FUEL_SLOTS;
    /** The player's main inventory (27 slots) and hotbar (9 slots), after the vehicle's slots. */
    private static final int INVENTORY_SLOTS = 27;
    private static final int HOTBAR_SLOTS = 9;

    private final Container fuel;
    private final Container parachutes;
    private final AbstractMchVehicle vehicle; // null on the client until resolved (the id travels in the open packet)
    /** How many parachute slots this menu has (the vehicle's {@code numEjectionSeats()}, 0 to 2). */
    private final int parachuteSlots;

    /**
     * Client constructor — the open buffer {@link #writeOpenData} wrote: the vehicle id, then the parachute slot
     * count. The count comes from the server rather than from the client's own entity, so the slot list matches the
     * server's even while the id does not resolve yet (the arguments are read left to right).
     */
    public MchVehicleMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, resolve(playerInv.player, buf.readVarInt()), buf.readVarInt());
    }

    public MchVehicleMenu(int containerId, Inventory playerInv, AbstractMchVehicle vehicle) {
        this(containerId, playerInv, vehicle, vehicle != null ? vehicle.numEjectionSeats() : 0);
    }

    private MchVehicleMenu(int containerId, Inventory playerInv, AbstractMchVehicle vehicle, int parachuteSlots) {
        super(MchRegistries.VEHICLE_MENU.get(), containerId);
        this.vehicle = vehicle;
        this.fuel = vehicle != null ? vehicle.fuelInventory() : new SimpleContainer(AbstractMchVehicle.FUEL_SLOTS);
        this.parachutes = vehicle != null ? vehicle.parachuteInventory()
            : new SimpleContainer(AbstractMchVehicle.PARACHUTE_SLOTS);
        // Bounded by the container, so a malformed open buffer cannot index past it.
        this.parachuteSlots = Math.max(0, Math.min(parachuteSlots, AbstractMchVehicle.PARACHUTE_SLOTS));

        for (int i = 0; i < AbstractMchVehicle.FUEL_SLOTS; i++) {
            addSlot(new Slot(this.fuel, i, 10, 30 + i * 18) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof MchFuelItem; // only fuel cans
                }
                @Override public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        // Reference :22-26. Deliberate divergence, following the fuel slots: these slots accept only a parachute, where
        // the reference's took any item and threw a non-parachute back to the player on close (:114-133). Same outcome.
        for (int i = 0; i < this.parachuteSlots; i++) {
            addSlot(new Slot(this.parachutes, i, 10 + i * 18, 105) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof MchParachuteItem; // only parachutes
                }
                @Override public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 9; x++) {
                addSlot(new Slot(playerInv, 9 + x + y * 9, 25 + x * 18, 135 + y * 18));
            }
        }
        for (int x = 0; x < 9; x++) {
            addSlot(new Slot(playerInv, x, 25 + x * 18, 195));
        }
    }

    /**
     * SERVER: the open buffer the client constructor reads — the vehicle id, then {@code numEjectionSeats()}. The menu
     * provider of {@code ServerboundVehicleGuiPayload} writes it.
     */
    public static void writeOpenData(RegistryFriendlyByteBuf buf, AbstractMchVehicle vehicle) {
        buf.writeVarInt(vehicle.getId());
        buf.writeVarInt(vehicle.numEjectionSeats());
    }

    private static AbstractMchVehicle resolve(Player player, int id) {
        Entity e = player.level().getEntity(id);
        return e instanceof AbstractMchVehicle v ? v : null;
    }

    public AbstractMchVehicle vehicle() {
        return this.vehicle;
    }

    /** How many parachute slots this menu shows (0 to {@link AbstractMchVehicle#PARACHUTE_SLOTS}); they follow the fuel
     *  slots. */
    public int parachuteSlots() {
        return this.parachuteSlots;
    }

    /** The end (exclusive) of the parachute slots, which is where the player's main inventory starts. */
    private int parachuteEnd() {
        return FUEL_END + this.parachuteSlots;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.vehicle != null && this.vehicle.isAlive() && player.getVehicle() == this.vehicle;
    }

    /**
     * Shift-click (reference {@code transferStackInSlot}:69-112): a vehicle slot's item moves to the player; a fuel can
     * moves into the first free fuel slot and a parachute into the first free parachute slot (both hold one); anything
     * else moves between inventory and hotbar, as vanilla menus do (the reference moved nothing).
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int paraEnd = parachuteEnd();
        int hotbarStart = paraEnd + INVENTORY_SLOTS;
        int invEnd = hotbarStart + HOTBAR_SLOTS;
        if (index < paraEnd) {                                   // fuel or parachute slot -> player
            if (!moveItemStackTo(stack, paraEnd, invEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof MchFuelItem) {     // player -> fuel slots
            if (!moveItemStackTo(stack, 0, FUEL_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (this.parachuteSlots > 0 && stack.getItem() instanceof MchParachuteItem) { // player -> parachutes
            if (!moveItemStackTo(stack, FUEL_END, paraEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else {                                                 // inventory <-> hotbar
            if (index < hotbarStart) {
                if (!moveItemStackTo(stack, hotbarStart, invEnd, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, paraEnd, hotbarStart, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    /** Closing the menu reloads every weapon from its reserve (reference {@code updateSupplyAmmo} on GUI close). */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (this.vehicle != null && !player.level().isClientSide) {
            this.vehicle.reloadAllWeapons();
        }
    }
}
