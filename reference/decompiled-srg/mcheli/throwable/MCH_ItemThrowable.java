/*
 * Decompiled with CFR 0.152.
 */
package mcheli.throwable;

import mcheli.MCH_Lib;
import mcheli.throwable.MCH_EntityThrowable;
import mcheli.throwable.MCH_ItemThrowableDispenseBehavior;
import mcheli.throwable.MCH_ThrowableInfo;
import mcheli.throwable.MCH_ThrowableInfoManager;
import mcheli.wrapper.W_Item;
import net.minecraft.block.BlockDispenser;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class MCH_ItemThrowable
extends W_Item {
    public MCH_ItemThrowable(int par1) {
        super(par1);
        this.func_77625_d(1);
    }

    public static void registerDispenseBehavior(Item item) {
        BlockDispenser.field_149943_a.func_82595_a((Object)item, (Object)new MCH_ItemThrowableDispenseBehavior());
    }

    public ItemStack func_77659_a(ItemStack itemStack, World world, EntityPlayer player) {
        player.func_71008_a(itemStack, this.func_77626_a(itemStack));
        return itemStack;
    }

    public void func_77615_a(ItemStack itemStack, World world, EntityPlayer player, int par4) {
        MCH_ThrowableInfo info;
        if (itemStack != null && itemStack.field_77994_a > 0 && (info = MCH_ThrowableInfoManager.get((Item)itemStack.func_77973_b())) != null) {
            if (!player.field_71075_bZ.field_75098_d) {
                --itemStack.field_77994_a;
                if (itemStack.field_77994_a <= 0) {
                    player.field_71071_by.field_70462_a[player.field_71071_by.field_70461_c] = null;
                }
            }
            world.func_72956_a((Entity)player, "random.bow", 0.5f, 0.4f / (field_77697_d.nextFloat() * 0.4f + 0.8f));
            if (!world.field_72995_K) {
                float acceleration = 1.0f;
                par4 = itemStack.func_77988_m() - par4;
                if (par4 <= 35) {
                    if (par4 < 5) {
                        par4 = 5;
                    }
                    acceleration = (float)par4 / 25.0f;
                }
                MCH_Lib.DbgLog((World)world, (String)"MCH_ItemThrowable.onPlayerStoppedUsing(%d)", (Object[])new Object[]{par4});
                MCH_EntityThrowable entity = new MCH_EntityThrowable(world, (EntityLivingBase)player, acceleration);
                entity.setInfo(info);
                world.func_72838_d((Entity)entity);
            }
        }
    }

    public int func_77626_a(ItemStack par1ItemStack) {
        return 72000;
    }

    public EnumAction func_77661_b(ItemStack par1ItemStack) {
        return EnumAction.bow;
    }
}

