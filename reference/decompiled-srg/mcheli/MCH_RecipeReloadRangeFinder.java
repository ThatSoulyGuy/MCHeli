/*
 * Decompiled with CFR 0.152.
 */
package mcheli;

import mcheli.MCH_MOD;
import mcheli.tool.rangefinder.MCH_ItemRangeFinder;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemRedstone;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

public class MCH_RecipeReloadRangeFinder
implements IRecipe {
    public boolean func_77569_a(InventoryCrafting inv, World var2) {
        int jcnt = 0;
        int ccnt = 0;
        for (int i = 0; i < inv.func_70302_i_(); ++i) {
            ItemStack is = inv.func_70301_a(i);
            if (is == null) continue;
            if (is.func_77973_b() instanceof MCH_ItemRangeFinder) {
                if (is.func_77960_j() == 0) {
                    return false;
                }
                if (++jcnt <= 1) continue;
                return false;
            }
            if (is.func_77973_b() instanceof ItemRedstone && is.field_77994_a > 0) {
                if (++ccnt <= 1) continue;
                return false;
            }
            return false;
        }
        return jcnt == 1 && ccnt > 0;
    }

    public ItemStack func_77572_b(InventoryCrafting inv) {
        ItemStack output = new ItemStack((Item)MCH_MOD.itemRangeFinder);
        return output;
    }

    public int func_77570_a() {
        return 9;
    }

    public ItemStack func_77571_b() {
        return null;
    }
}

