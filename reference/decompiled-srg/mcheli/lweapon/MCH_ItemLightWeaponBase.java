/*
 * Decompiled with CFR 0.152.
 */
package mcheli.lweapon;

import mcheli.lweapon.MCH_ItemLightWeaponBullet;
import mcheli.wrapper.W_Item;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;

public class MCH_ItemLightWeaponBase
extends W_Item {
    public final MCH_ItemLightWeaponBullet bullet;

    public MCH_ItemLightWeaponBase(int par1, MCH_ItemLightWeaponBullet bullet) {
        super(par1);
        this.func_77656_e(10);
        this.func_77625_d(1);
        this.bullet = bullet;
    }

    public static String getName(ItemStack itemStack) {
        if (itemStack != null && itemStack.func_77973_b() instanceof MCH_ItemLightWeaponBase) {
            String name = itemStack.func_77977_a();
            int li = name.lastIndexOf(":");
            if (li >= 0) {
                name = name.substring(li + 1);
            }
            return name;
        }
        return "";
    }

    public static boolean isHeld(EntityPlayer player) {
        ItemStack is;
        ItemStack itemStack = is = player != null ? player.func_70694_bm() : null;
        if (is != null && is.func_77973_b() instanceof MCH_ItemLightWeaponBase) {
            return player.func_71057_bx() > 10;
        }
        return false;
    }

    public void onUsingTick(ItemStack stack, EntityPlayer player, int count) {
        PotionEffect pe = player.func_70660_b(Potion.field_76439_r);
        if (pe != null && pe.func_76459_b() < 220) {
            player.func_70690_d(new PotionEffect(Potion.field_76439_r.func_76396_c(), 250, 0, false));
        }
    }

    public boolean onEntitySwing(EntityLivingBase entityLiving, ItemStack stack) {
        return true;
    }

    public EnumAction func_77661_b(ItemStack par1ItemStack) {
        return EnumAction.bow;
    }

    public int func_77626_a(ItemStack par1ItemStack) {
        return 72000;
    }

    public ItemStack func_77659_a(ItemStack par1ItemStack, World par2World, EntityPlayer par3EntityPlayer) {
        if (par1ItemStack != null) {
            par3EntityPlayer.func_71008_a(par1ItemStack, this.func_77626_a(par1ItemStack));
        }
        return par1ItemStack;
    }
}

