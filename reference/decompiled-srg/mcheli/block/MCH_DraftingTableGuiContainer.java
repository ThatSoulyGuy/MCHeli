/*
 * Decompiled with CFR 0.152.
 */
package mcheli.block;

import java.util.Map;
import mcheli.MCH_IRecipeList;
import mcheli.MCH_ItemRecipe;
import mcheli.MCH_Lib;
import mcheli.MCH_MOD;
import mcheli.block.MCH_DraftingTableGuiContainer;
import mcheli.helicopter.MCH_HeliInfoManager;
import mcheli.plane.MCP_PlaneInfoManager;
import mcheli.tank.MCH_TankInfoManager;
import mcheli.vehicle.MCH_VehicleInfoManager;
import mcheli.wrapper.W_Block;
import mcheli.wrapper.W_EntityPlayer;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

public class MCH_DraftingTableGuiContainer
extends Container {
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
        for (int y = 0; y < 3; ++y) {
            for (int x = 0; x < 9; ++x) {
                this.func_75146_a(new Slot((IInventory)player.field_71071_by, 9 + x + y * 9, 30 + x * 18, 140 + y * 18));
            }
        }
        for (int x = 0; x < 9; ++x) {
            this.func_75146_a(new Slot((IInventory)player.field_71071_by, x, 30 + x * 18, 198));
        }
        this.outputSlotIndex = this.field_75153_a.size();
        1 a = new /* Unavailable Anonymous Inner Class!! */;
        this.func_75146_a((Slot)a);
        MCH_Lib.DbgLog((World)player.field_70170_p, (String)"MCH_DraftingTableGuiContainer.MCH_DraftingTableGuiContainer", (Object[])new Object[0]);
    }

    public void func_75142_b() {
        super.func_75142_b();
    }

    public boolean func_75145_c(EntityPlayer player) {
        Block block = W_WorldFunc.getBlock((World)player.field_70170_p, (int)this.posX, (int)this.posY, (int)this.posZ);
        if (W_Block.isEqual((Block)block, (Block)MCH_MOD.blockDraftingTable) || W_Block.isEqual((Block)block, (Block)MCH_MOD.blockDraftingTableLit)) {
            return player.func_70092_e((double)this.posX, (double)this.posY, (double)this.posZ) <= 144.0;
        }
        return false;
    }

    public ItemStack func_82846_b(EntityPlayer player, int slotIndex) {
        ItemStack itemstack = null;
        Slot slot = (Slot)this.field_75151_b.get(slotIndex);
        if (slot != null && slot.func_75216_d()) {
            ItemStack itemstack1 = slot.func_75211_c();
            itemstack = itemstack1.func_77946_l();
            if (slotIndex == this.outputSlotIndex) {
                if (!this.func_75135_a(itemstack1, 0, 36, true)) {
                    return null;
                }
            } else {
                return null;
            }
            slot.func_75220_a(itemstack1, itemstack);
            if (itemstack1.field_77994_a == 0) {
                slot.func_75215_d((ItemStack)null);
            } else {
                slot.func_75218_e();
            }
            if (itemstack1.field_77994_a == itemstack.field_77994_a) {
                return null;
            }
            slot.func_82870_a(player, itemstack1);
        }
        return itemstack;
    }

    public void func_75134_a(EntityPlayer player) {
        ItemStack itemstack;
        super.func_75134_a(player);
        if (!player.field_70170_p.field_72995_K && (itemstack = this.func_75139_a(this.outputSlotIndex).func_75211_c()) != null) {
            W_EntityPlayer.dropPlayerItemWithRandomChoice((EntityPlayer)player, (ItemStack)itemstack, (boolean)false, (boolean)false);
        }
        MCH_Lib.DbgLog((World)player.field_70170_p, (String)"MCH_DraftingTableGuiContainer.onContainerClosed", (Object[])new Object[0]);
    }

    public void createRecipeItem(Item outputItem, Map<Item, Integer> map) {
        MCH_IRecipeList[] recipeLists;
        boolean isCreativeMode = this.player.field_71075_bZ.field_75098_d;
        if (this.func_75139_a(this.outputSlotIndex).func_75216_d() && !isCreativeMode) {
            MCH_Lib.DbgLog((World)this.player.field_70170_p, (String)"MCH_DraftingTableGuiContainer.createRecipeItem:OutputSlot is not empty", (Object[])new Object[0]);
            return;
        }
        if (outputItem == null) {
            MCH_Lib.DbgLog((World)this.player.field_70170_p, (String)"Error:MCH_DraftingTableGuiContainer.createRecipeItem:outputItem = null", (Object[])new Object[0]);
            return;
        }
        if (map == null || map.size() <= 0) {
            MCH_Lib.DbgLog((World)this.player.field_70170_p, (String)("Error:MCH_DraftingTableGuiContainer.createRecipeItem:map is null : " + map), (Object[])new Object[0]);
            return;
        }
        ItemStack itemStack = new ItemStack(outputItem);
        boolean result = false;
        IRecipe recipe = null;
        for (MCH_IRecipeList rl : recipeLists = new MCH_IRecipeList[]{MCH_ItemRecipe.getInstance(), MCH_HeliInfoManager.getInstance(), MCP_PlaneInfoManager.getInstance(), MCH_VehicleInfoManager.getInstance(), MCH_TankInfoManager.getInstance()}) {
            int index = this.searchRecipeFromList(rl, itemStack);
            if (index < 0) continue;
            recipe = this.isValidRecipe(rl, itemStack, index, map);
            break;
        }
        if (recipe != null && (isCreativeMode || MCH_Lib.canPlayerCreateItem(recipe, (InventoryPlayer)this.player.field_71071_by))) {
            for (Item key : map.keySet()) {
                for (int i = 0; i < map.get(key); ++i) {
                    if (!isCreativeMode) {
                        W_EntityPlayer.consumeInventoryItem((EntityPlayer)this.player, (Item)key);
                    }
                    this.func_75139_a(this.outputSlotIndex).func_75215_d(recipe.func_77571_b().func_77946_l());
                    result = true;
                }
            }
        }
        MCH_Lib.DbgLog((World)this.player.field_70170_p, (String)("MCH_DraftingTableGuiContainer:Result=" + result + ":Recipe=" + recipe + " :" + outputItem.func_77658_a() + ": map=" + map), (Object[])new Object[0]);
    }

    public IRecipe isValidRecipe(MCH_IRecipeList list, ItemStack itemStack, int startIndex, Map<Item, Integer> map) {
        for (int index = startIndex; index >= 0 && index < list.getRecipeListSize(); ++index) {
            IRecipe recipe = list.getRecipe(index);
            if (itemStack.func_77969_a(recipe.func_77571_b())) {
                Map mapRecipe = MCH_Lib.getItemMapFromRecipe((IRecipe)recipe);
                boolean isEqual = true;
                for (Item key : map.keySet()) {
                    if (mapRecipe.containsKey(key) && mapRecipe.get(key) == map.get(key)) continue;
                    isEqual = false;
                    break;
                }
                if (!isEqual) continue;
                return recipe;
            }
            return null;
        }
        return null;
    }

    public int searchRecipeFromList(MCH_IRecipeList list, ItemStack item) {
        for (int i = 0; i < list.getRecipeListSize(); ++i) {
            if (!list.getRecipe(i).func_77571_b().func_77969_a(item)) continue;
            return i;
        }
        return -1;
    }
}

