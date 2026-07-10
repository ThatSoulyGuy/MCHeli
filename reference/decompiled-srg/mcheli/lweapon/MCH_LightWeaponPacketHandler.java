/*
 * Decompiled with CFR 0.152.
 */
package mcheli.lweapon;

import com.google.common.io.ByteArrayDataInput;
import mcheli.MCH_Lib;
import mcheli.lweapon.MCH_ItemLightWeaponBase;
import mcheli.lweapon.MCH_PacketLightWeaponPlayerControl;
import mcheli.weapon.MCH_WeaponBase;
import mcheli.weapon.MCH_WeaponCreator;
import mcheli.weapon.MCH_WeaponParam;
import mcheli.wrapper.W_EntityPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class MCH_LightWeaponPacketHandler {
    public static void onPacket_PlayerControl(EntityPlayer player, ByteArrayDataInput data) {
        ItemStack is;
        if (player.field_70170_p.field_72995_K) {
            return;
        }
        MCH_PacketLightWeaponPlayerControl pc = new MCH_PacketLightWeaponPlayerControl();
        pc.readData(data);
        if (pc.camMode == 1) {
            player.func_82170_o(Potion.field_76439_r.func_76396_c());
        }
        if ((is = player.func_70694_bm()) == null) {
            return;
        }
        if (!(is.func_77973_b() instanceof MCH_ItemLightWeaponBase)) {
            return;
        }
        MCH_ItemLightWeaponBase lweapon = (MCH_ItemLightWeaponBase)is.func_77973_b();
        if (pc.camMode == 2 && MCH_ItemLightWeaponBase.isHeld((EntityPlayer)player)) {
            player.func_70690_d(new PotionEffect(Potion.field_76439_r.func_76396_c(), 255, 0, false));
        }
        if (pc.camMode > 0) {
            MCH_Lib.DbgLog((boolean)false, (String)"MCH_LightWeaponPacketHandler NV=%s", (Object[])new Object[]{pc.camMode == 2 ? "ON" : "OFF"});
        }
        if (pc.useWeapon && is.func_77960_j() < is.func_77958_k()) {
            String name = MCH_ItemLightWeaponBase.getName((ItemStack)player.func_70694_bm());
            MCH_WeaponBase w = MCH_WeaponCreator.createWeapon((World)player.field_70170_p, (String)name, (Vec3)Vec3.func_72443_a((double)0.0, (double)0.0, (double)0.0), (float)0.0f, (float)0.0f, null, (boolean)false);
            MCH_WeaponParam prm = new MCH_WeaponParam();
            prm.entity = player;
            prm.user = player;
            prm.setPosAndRot(pc.useWeaponPosX, pc.useWeaponPosY, pc.useWeaponPosZ, player.field_70177_z, player.field_70125_A);
            prm.option1 = pc.useWeaponOption1;
            prm.option2 = pc.useWeaponOption2;
            w.shot(prm);
            if (!player.field_71075_bZ.field_75098_d && is.func_77958_k() == 1) {
                --is.field_77994_a;
            }
            if (is.func_77958_k() > 1) {
                is.func_77964_b(is.func_77958_k());
            }
        } else if (pc.cmpReload > 0 && is.func_77960_j() > 1 && W_EntityPlayer.hasItem((EntityPlayer)player, (Item)lweapon.bullet)) {
            if (!player.field_71075_bZ.field_75098_d) {
                W_EntityPlayer.consumeInventoryItem((EntityPlayer)player, (Item)lweapon.bullet);
            }
            is.func_77964_b(0);
        }
    }
}

