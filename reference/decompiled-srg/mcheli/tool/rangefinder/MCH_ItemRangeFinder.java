/*
 * Decompiled with CFR 0.152.
 */
package mcheli.tool.rangefinder;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_EntitySeat;
import mcheli.multiplay.MCH_PacketIndSpotEntity;
import mcheli.wrapper.W_Item;
import mcheli.wrapper.W_McClient;
import mcheli.wrapper.W_Reflection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/*
 * Exception performing whole class analysis ignored.
 */
public class MCH_ItemRangeFinder
extends W_Item {
    public static int rangeFinderUseCooldown = 0;
    public static boolean continueUsingItem = false;
    public static float zoom = 2.0f;
    public static int mode = 0;

    public MCH_ItemRangeFinder(int itemId) {
        super(itemId);
        this.field_77777_bU = 1;
        this.func_77656_e(10);
    }

    public static boolean canUse(EntityPlayer player) {
        MCH_EntityAircraft ac;
        if (player == null) {
            return false;
        }
        if (player.field_70170_p == null) {
            return false;
        }
        if (player.func_71045_bC() == null) {
            return false;
        }
        if (!(player.func_71045_bC().func_77973_b() instanceof MCH_ItemRangeFinder)) {
            return false;
        }
        if (player.field_70154_o instanceof MCH_EntityAircraft) {
            return false;
        }
        return !(player.field_70154_o instanceof MCH_EntitySeat) || (ac = ((MCH_EntitySeat)player.field_70154_o).getParent()) == null || !ac.getIsGunnerMode((Entity)player) && ac.getWeaponIDBySeatID(ac.getSeatIdByEntity((Entity)player)) < 0;
    }

    public static boolean isUsingScope(EntityPlayer player) {
        return player.func_71057_bx() > 8 || continueUsingItem;
    }

    public static void onStartUseItem() {
        zoom = 2.0f;
        W_Reflection.setCameraZoom((float)2.0f);
        continueUsingItem = true;
    }

    public static void onStopUseItem() {
        W_Reflection.restoreCameraZoom();
        continueUsingItem = false;
    }

    @SideOnly(value=Side.CLIENT)
    public void spotEntity(EntityPlayer player, ItemStack itemStack) {
        if (player != null && player.field_70170_p.field_72995_K && rangeFinderUseCooldown == 0 && player.func_71057_bx() > 8) {
            if (mode == 2) {
                rangeFinderUseCooldown = 60;
                MCH_PacketIndSpotEntity.send((EntityLivingBase)player, (int)0);
            } else if (itemStack.func_77960_j() < itemStack.func_77958_k()) {
                rangeFinderUseCooldown = 60;
                MCH_PacketIndSpotEntity.send((EntityLivingBase)player, (int)(mode == 0 ? 60 : 3));
            } else {
                W_McClient.MOD_playSoundFX((String)"ng", (float)1.0f, (float)1.0f);
            }
        }
    }

    public void func_77615_a(ItemStack p_77615_1_, World p_77615_2_, EntityPlayer p_77615_3_, int p_77615_4_) {
        if (p_77615_2_.field_72995_K) {
            MCH_ItemRangeFinder.onStopUseItem();
        }
    }

    public ItemStack func_77654_b(ItemStack p_77654_1_, World p_77654_2_, EntityPlayer p_77654_3_) {
        return p_77654_1_;
    }

    @SideOnly(value=Side.CLIENT)
    public boolean func_77662_d() {
        return true;
    }

    public EnumAction func_77661_b(ItemStack itemStack) {
        return EnumAction.bow;
    }

    public int func_77626_a(ItemStack itemStack) {
        return 72000;
    }

    public ItemStack func_77659_a(ItemStack itemStack, World world, EntityPlayer player) {
        if (MCH_ItemRangeFinder.canUse((EntityPlayer)player)) {
            player.func_71008_a(itemStack, this.func_77626_a(itemStack));
        }
        return itemStack;
    }
}

