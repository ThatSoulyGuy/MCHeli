/*
 * Decompiled with CFR 0.152.
 */
package mcheli.tool;

import com.google.common.io.ByteArrayDataInput;
import mcheli.MCH_Config;
import mcheli.multiplay.MCH_Multiplay;
import mcheli.multiplay.MCH_PacketIndSpotEntity;
import mcheli.tool.rangefinder.MCH_ItemRangeFinder;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

public class MCH_ToolPacketHandler {
    public static void onPacket_IndSpotEntity(EntityPlayer player, ByteArrayDataInput data) {
        if (player.field_70170_p.field_72995_K) {
            return;
        }
        MCH_PacketIndSpotEntity pc = new MCH_PacketIndSpotEntity();
        pc.readData(data);
        ItemStack itemStack = player.func_70694_bm();
        if (itemStack != null && itemStack.func_77973_b() instanceof MCH_ItemRangeFinder) {
            if (pc.targetFilter == 0) {
                if (MCH_Multiplay.markPoint((EntityPlayer)player, (double)player.field_70165_t, (double)(player.field_70163_u + (double)player.func_70047_e()), (double)player.field_70161_v)) {
                    W_WorldFunc.MOD_playSoundAtEntity((Entity)player, (String)"pi", (float)1.0f, (float)1.0f);
                } else {
                    W_WorldFunc.MOD_playSoundAtEntity((Entity)player, (String)"ng", (float)1.0f, (float)1.0f);
                }
            } else if (itemStack.func_77960_j() < itemStack.func_77958_k()) {
                if (MCH_Config.RangeFinderConsume.prmBool) {
                    itemStack.func_77972_a(1, (EntityLivingBase)player);
                }
                int time = (pc.targetFilter & 0xFC) == 0 ? 60 : MCH_Config.RangeFinderSpotTime.prmInt;
                if (MCH_Multiplay.spotEntity((EntityPlayer)player, null, (double)player.field_70165_t, (double)(player.field_70163_u + (double)player.func_70047_e()), (double)player.field_70161_v, (int)pc.targetFilter, (float)MCH_Config.RangeFinderSpotDist.prmInt, (int)time, (float)20.0f)) {
                    W_WorldFunc.MOD_playSoundAtEntity((Entity)player, (String)"pi", (float)1.0f, (float)1.0f);
                } else {
                    W_WorldFunc.MOD_playSoundAtEntity((Entity)player, (String)"ng", (float)1.0f, (float)1.0f);
                }
            }
        }
    }
}

