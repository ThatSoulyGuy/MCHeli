package mcheli.block;

import java.util.Iterator;
import java.util.Map;
import mcheli.MCH_IRecipeList;
import mcheli.MCH_ItemRecipe;
import mcheli.MCH_Lib;
import mcheli.MCH_MOD;
import mcheli.helicopter.MCH_HeliInfoManager;
import mcheli.plane.MCP_PlaneInfoManager;
import mcheli.tank.MCH_TankInfoManager;
import mcheli.vehicle.MCH_VehicleInfoManager;
import mcheli.wrapper.W_Block;
import mcheli.wrapper.W_EntityPlayer;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;

public class MCH_DraftingTableGuiContainer extends Container {
   public final EntityPlayer player;
   public final int posX;
   public final int posY;
   public final int posZ;
   public final int outputSlotIndex;
   private IInventory outputSlot = new InventoryCraftResult();

   public MCH_DraftingTableGuiContainer(EntityPlayer player, int posX, int posY, int posZ) {
      this.player = player;
      this.posX = posX;
      this.posY = posY;
      this.posZ = posZ;

      for (int y = 0; y < 3; y++) {
         for (int x = 0; x < 9; x++) {
            this.addSlotToContainer(new Slot(player.inventory, 9 + x + y * 9, 30 + x * 18, 140 + y * 18));
         }
      }

      for (int x = 0; x < 9; x++) {
         this.addSlotToContainer(new Slot(player.inventory, x, 30 + x * 18, 198));
      }

      this.outputSlotIndex = this.inventoryItemStacks.size();
      Slot a = new Slot(this.outputSlot, this.outputSlotIndex, 178, 90) {
         public boolean isItemValid(ItemStack par1ItemStack) {
            return false;
         }
      };
      this.addSlotToContainer(a);
      MCH_Lib.DbgLog(player.worldObj, "MCH_DraftingTableGuiContainer.MCH_DraftingTableGuiContainer");
   }

   public void detectAndSendChanges() {
      super.detectAndSendChanges();
   }

   public boolean canInteractWith(EntityPlayer player) {
      Block block = W_WorldFunc.getBlock(player.worldObj, this.posX, this.posY, this.posZ);
      return !W_Block.isEqual(block, MCH_MOD.blockDraftingTable) && !W_Block.isEqual(block, MCH_MOD.blockDraftingTableLit)
         ? false
         : player.getDistanceSq(this.posX, this.posY, this.posZ) <= 144.0;
   }

   public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
      ItemStack itemstack = null;
      Slot slot = (Slot)this.inventorySlots.get(slotIndex);
      if (slot != null && slot.getHasStack()) {
         ItemStack itemstack1 = slot.getStack();
         itemstack = itemstack1.copy();
         if (slotIndex != this.outputSlotIndex) {
            return null;
         }

         if (!this.mergeItemStack(itemstack1, 0, 36, true)) {
            return null;
         }

         slot.onSlotChange(itemstack1, itemstack);
         if (itemstack1.stackSize == 0) {
            slot.putStack((ItemStack)null);
         } else {
            slot.onSlotChanged();
         }

         if (itemstack1.stackSize == itemstack.stackSize) {
            return null;
         }

         slot.onPickupFromSlot(player, itemstack1);
      }

      return itemstack;
   }

   public void onContainerClosed(EntityPlayer player) {
      super.onContainerClosed(player);
      if (!player.worldObj.isRemote) {
         ItemStack itemstack = this.getSlot(this.outputSlotIndex).getStack();
         if (itemstack != null) {
            W_EntityPlayer.dropPlayerItemWithRandomChoice(player, itemstack, false, false);
         }
      }

      MCH_Lib.DbgLog(player.worldObj, "MCH_DraftingTableGuiContainer.onContainerClosed");
   }

   public void createRecipeItem(Item outputItem, Map<Item, Integer> map) {
      boolean isCreativeMode = this.player.capabilities.isCreativeMode;
      if (this.getSlot(this.outputSlotIndex).getHasStack() && !isCreativeMode) {
         MCH_Lib.DbgLog(this.player.worldObj, "MCH_DraftingTableGuiContainer.createRecipeItem:OutputSlot is not empty");
      } else if (outputItem == null) {
         MCH_Lib.DbgLog(this.player.worldObj, "Error:MCH_DraftingTableGuiContainer.createRecipeItem:outputItem = null");
      } else if (map != null && map.size() > 0) {
         ItemStack itemStack = new ItemStack(outputItem);
         boolean result = false;
         IRecipe recipe = null;
         MCH_IRecipeList[] recipeLists = new MCH_IRecipeList[]{
            MCH_ItemRecipe.getInstance(),
            MCH_HeliInfoManager.getInstance(),
            MCP_PlaneInfoManager.getInstance(),
            MCH_VehicleInfoManager.getInstance(),
            MCH_TankInfoManager.getInstance()
         };

         for (MCH_IRecipeList rl : recipeLists) {
            int index = this.searchRecipeFromList(rl, itemStack);
            if (index >= 0) {
               recipe = this.isValidRecipe(rl, itemStack, index, map);
               break;
            }
         }

         if (recipe != null && (isCreativeMode || MCH_Lib.canPlayerCreateItem(recipe, this.player.inventory))) {
            for (Item key : map.keySet()) {
               for (int i = 0; i < map.get(key); i++) {
                  if (!isCreativeMode) {
                     W_EntityPlayer.consumeInventoryItem(this.player, key);
                  }

                  this.getSlot(this.outputSlotIndex).putStack(recipe.getRecipeOutput().copy());
                  result = true;
               }
            }
         }

         MCH_Lib.DbgLog(
            this.player.worldObj,
            "MCH_DraftingTableGuiContainer:Result=" + result + ":Recipe=" + recipe + " :" + outputItem.getUnlocalizedName() + ": map=" + map
         );
      } else {
         MCH_Lib.DbgLog(this.player.worldObj, "Error:MCH_DraftingTableGuiContainer.createRecipeItem:map is null : " + map);
      }
   }

   public IRecipe isValidRecipe(MCH_IRecipeList list, ItemStack itemStack, int startIndex, Map<Item, Integer> map) {
      for (int index = startIndex; index >= 0 && index < list.getRecipeListSize(); index++) {
         IRecipe recipe = list.getRecipe(index);
         if (!itemStack.isItemEqual(recipe.getRecipeOutput())) {
            return null;
         }

         Map<Item, Integer> mapRecipe = MCH_Lib.getItemMapFromRecipe(recipe);
         boolean isEqual = true;
         Iterator i$ = map.keySet().iterator();

         while (true) {
            if (i$.hasNext()) {
               Item key = (Item)i$.next();
               if (mapRecipe.containsKey(key) && mapRecipe.get(key) == map.get(key)) {
                  continue;
               }

               isEqual = false;
            }

            if (isEqual) {
               return recipe;
            }
            break;
         }
      }

      return null;
   }

   public int searchRecipeFromList(MCH_IRecipeList list, ItemStack item) {
      for (int i = 0; i < list.getRecipeListSize(); i++) {
         if (list.getRecipe(i).getRecipeOutput().isItemEqual(item)) {
            return i;
         }
      }

      return -1;
   }
}
