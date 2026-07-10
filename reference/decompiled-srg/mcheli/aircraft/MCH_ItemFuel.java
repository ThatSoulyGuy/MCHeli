/*
 * Decompiled with CFR 0.152.
 */
package mcheli.aircraft;

import mcheli.wrapper.W_Item;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemCoal;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class MCH_ItemFuel
extends W_Item {
    public MCH_ItemFuel(int itemID) {
        super(itemID);
        this.func_77656_e(600);
        this.func_77625_d(1);
        this.setNoRepair();
        this.func_77664_n();
    }

    public ItemStack func_77659_a(ItemStack stack, World world, EntityPlayer player) {
        int damage = stack.func_77960_j();
        if (!world.field_72995_K && stack.func_77951_h() && !player.field_71075_bZ.field_75098_d) {
            this.refuel(stack, player, 1);
            this.refuel(stack, player, 0);
        }
        return stack;
    }

    private void refuel(ItemStack stack, EntityPlayer player, int coalType) {
        ItemStack[] list = player.field_71071_by.field_70462_a;
        for (int i = 0; i < list.length; ++i) {
            ItemStack is = list[i];
            if (is == null || !(is.func_77973_b() instanceof ItemCoal) || is.func_77960_j() != coalType) continue;
            for (int j = 0; is.field_77994_a > 0 && stack.func_77951_h() && j < 64; ++j) {
                int damage = stack.func_77960_j() - (coalType == 1 ? 75 : 100);
                if (damage < 0) {
                    damage = 0;
                }
                stack.func_77964_b(damage);
                --is.field_77994_a;
            }
            if (is.field_77994_a > 0) continue;
            list[i] = null;
        }
    }
}

