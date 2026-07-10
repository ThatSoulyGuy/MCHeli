package mcheli.aircraft;

import mcheli.MCH_Lib;
import mcheli.parachute.MCH_ItemParachute;
import mcheli.uav.MCH_EntityUavStation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class MCH_AircraftGuiContainer extends Container {
   public final EntityPlayer player;
   public final MCH_EntityAircraft aircraft;

   public MCH_AircraftGuiContainer(EntityPlayer player, MCH_EntityAircraft ac) {
      this.player = player;
      this.aircraft = ac;
      MCH_AircraftInventory iv = this.aircraft.getGuiInventory();
      this.addSlotToContainer(new Slot(iv, 0, 10, 30));
      this.addSlotToContainer(new Slot(iv, 1, 10, 48));
      this.addSlotToContainer(new Slot(iv, 2, 10, 66));
      int num = this.aircraft.getNumEjectionSeat();

      for (int i = 0; i < num; i++) {
         this.addSlotToContainer(new Slot(iv, 3 + i, 10 + 18 * i, 105));
      }

      for (int y = 0; y < 3; y++) {
         for (int x = 0; x < 9; x++) {
            this.addSlotToContainer(new Slot(player.inventory, 9 + x + y * 9, 25 + x * 18, 135 + y * 18));
         }
      }

      for (int x = 0; x < 9; x++) {
         this.addSlotToContainer(new Slot(player.inventory, x, 25 + x * 18, 195));
      }
   }

   public int getInventoryStartIndex() {
      return this.aircraft == null ? 3 : 3 + this.aircraft.getNumEjectionSeat();
   }

   public void detectAndSendChanges() {
      super.detectAndSendChanges();
   }

   public boolean canInteractWith(EntityPlayer player) {
      if (this.aircraft.getGuiInventory().isUseableByPlayer(player)) {
         return true;
      }

      if (this.aircraft.isUAV()) {
         MCH_EntityUavStation us = this.aircraft.getUavStation();
         if (us != null) {
            double x = us.posX + us.posUavX;
            double z = us.posZ + us.posUavZ;
            if (this.aircraft.posX < x + 10.0
               && this.aircraft.posX > x - 10.0
               && this.aircraft.posZ < z + 10.0
               && this.aircraft.posZ > z - 10.0) {
               return true;
            }
         }
      }

      return false;
   }

   public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
      MCH_AircraftInventory iv = this.aircraft.getGuiInventory();
      Slot slot = (Slot)this.inventorySlots.get(slotIndex);
      if (slot == null) {
         return null;
      }

      ItemStack itemStack = slot.getStack();
      MCH_Lib.DbgLog(player.worldObj, "transferStackInSlot : %d :" + itemStack, slotIndex);
      if (itemStack == null) {
         return null;
      }

      if (slotIndex < this.getInventoryStartIndex()) {
         for (int i = this.getInventoryStartIndex(); i < this.inventorySlots.size(); i++) {
            Slot playerSlot = (Slot)this.inventorySlots.get(i);
            if (playerSlot.getStack() == null) {
               playerSlot.putStack(itemStack);
               slot.putStack(null);
               return itemStack;
            }
         }
      } else if (itemStack.getItem() instanceof MCH_ItemFuel) {
         for (int i = 0; i < 3; i++) {
            if (iv.getFuelSlotItemStack(i) == null) {
               iv.setInventorySlotContents(0 + i, itemStack);
               slot.putStack(null);
               return itemStack;
            }
         }
      } else if (itemStack.getItem() instanceof MCH_ItemParachute) {
         int num = this.aircraft.getNumEjectionSeat();

         for (int i = 0; i < num; i++) {
            if (iv.getParachuteSlotItemStack(i) == null) {
               iv.setInventorySlotContents(3 + i, itemStack);
               slot.putStack(null);
               return itemStack;
            }
         }
      }

      return null;
   }

   public void onContainerClosed(EntityPlayer player) {
      super.onContainerClosed(player);
      if (!player.worldObj.isRemote) {
         MCH_AircraftInventory iv = this.aircraft.getGuiInventory();

         for (int i = 0; i < 3; i++) {
            ItemStack is = iv.getFuelSlotItemStack(i);
            if (is != null && !(is.getItem() instanceof MCH_ItemFuel)) {
               this.dropPlayerItem(player, 0 + i);
            }
         }

         for (int i = 0; i < 2; i++) {
            ItemStack is = iv.getParachuteSlotItemStack(i);
            if (is != null && !(is.getItem() instanceof MCH_ItemParachute)) {
               this.dropPlayerItem(player, 3 + i);
            }
         }
      }
   }

   public void dropPlayerItem(EntityPlayer player, int slotID) {
      if (!player.worldObj.isRemote) {
         ItemStack itemstack = this.aircraft.getGuiInventory().getStackInSlotOnClosing(slotID);
         if (itemstack != null) {
            player.dropPlayerItemWithRandomChoice(itemstack, false);
         }
      }
   }
}
