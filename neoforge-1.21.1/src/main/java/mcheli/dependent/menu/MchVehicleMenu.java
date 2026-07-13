package mcheli.dependent.menu;

import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.item.MchFuelItem;
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
 * into its tank while it is on the ground) plus the player's inventory. The reload button lives on the
 * {@link mcheli.dependent.client.screen.MchVehicleScreen screen} and is serviced by
 * {@link mcheli.dependent.control.ServerboundVehicleGuiPayload}.
 *
 * <p>Slot coordinates are the reference's: fuel at (10,30)/(10,48)/(10,66), the inventory grid at
 * {@code (25 + x·18, 135 + y·18)} and the hotbar at {@code (25 + x·18, 195)}.
 */
public class MchVehicleMenu extends AbstractContainerMenu {

    private final Container fuel;
    private final AbstractMchVehicle vehicle; // null on the client until resolved (the id travels in the open packet)

    /** Client constructor — the vehicle id arrives in the buffer. */
    public MchVehicleMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, resolve(playerInv.player, buf.readVarInt()));
    }

    public MchVehicleMenu(int containerId, Inventory playerInv, AbstractMchVehicle vehicle) {
        super(MchRegistries.VEHICLE_MENU.get(), containerId);
        this.vehicle = vehicle;
        this.fuel = vehicle != null ? vehicle.fuelInventory() : new SimpleContainer(AbstractMchVehicle.FUEL_SLOTS);

        for (int i = 0; i < AbstractMchVehicle.FUEL_SLOTS; i++) {
            final int slot = i;
            addSlot(new Slot(this.fuel, i, 10, 30 + i * 18) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof MchFuelItem; // only fuel cans
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

    private static AbstractMchVehicle resolve(Player player, int id) {
        Entity e = player.level().getEntity(id);
        return e instanceof AbstractMchVehicle v ? v : null;
    }

    public AbstractMchVehicle vehicle() {
        return this.vehicle;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.vehicle != null && this.vehicle.isAlive() && player.getVehicle() == this.vehicle;
    }

    /** Shift-click: a fuel can moves into the first free fuel slot; anything else moves between inventory and hotbar. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int fuelEnd = AbstractMchVehicle.FUEL_SLOTS;
        int invEnd = fuelEnd + 36;
        if (index < fuelEnd) {                                   // fuel slot -> player
            if (!moveItemStackTo(stack, fuelEnd, invEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof MchFuelItem) {     // player -> fuel slots
            if (!moveItemStackTo(stack, 0, fuelEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else {                                                 // inventory <-> hotbar
            int hotbarStart = fuelEnd + 27;
            if (index < hotbarStart) {
                if (!moveItemStackTo(stack, hotbarStart, invEnd, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, fuelEnd, hotbarStart, false)) {
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
