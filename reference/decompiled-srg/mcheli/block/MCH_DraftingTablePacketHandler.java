/*
 * Decompiled with CFR 0.152.
 */
package mcheli.block;

import com.google.common.io.ByteArrayDataInput;
import mcheli.MCH_Lib;
import mcheli.block.MCH_DraftingTableCreatePacket;
import mcheli.block.MCH_DraftingTableGuiContainer;
import net.minecraft.entity.player.EntityPlayer;

public class MCH_DraftingTablePacketHandler {
    public static void onPacketCreate(EntityPlayer player, ByteArrayDataInput data) {
        if (!player.field_70170_p.field_72995_K) {
            MCH_DraftingTableCreatePacket packet = new MCH_DraftingTableCreatePacket();
            packet.readData(data);
            boolean openScreen = player.field_71070_bA instanceof MCH_DraftingTableGuiContainer;
            MCH_Lib.DbgLog((boolean)false, (String)("MCH_DraftingTablePacketHandler.onPacketCreate : " + openScreen), (Object[])new Object[0]);
            if (openScreen) {
                ((MCH_DraftingTableGuiContainer)player.field_71070_bA).createRecipeItem(packet.outputItem, packet.map);
            }
        }
    }
}

