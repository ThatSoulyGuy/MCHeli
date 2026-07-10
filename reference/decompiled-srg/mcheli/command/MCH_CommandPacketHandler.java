/*
 * Decompiled with CFR 0.152.
 */
package mcheli.command;

import com.google.common.io.ByteArrayDataInput;
import mcheli.MCH_MOD;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.command.MCH_PacketCommandSave;
import mcheli.command.MCH_PacketTitle;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

public class MCH_CommandPacketHandler {
    public static void onPacketTitle(EntityPlayer player, ByteArrayDataInput data) {
        if (player == null || !player.field_70170_p.field_72995_K) {
            return;
        }
        MCH_PacketTitle req = new MCH_PacketTitle();
        req.readData(data);
        MCH_MOD.proxy.printChatMessage(req.chatComponent, req.showTime, req.position);
    }

    public static void onPacketSave(EntityPlayer player, ByteArrayDataInput data) {
        if (player == null || player.field_70170_p.field_72995_K) {
            return;
        }
        MCH_PacketCommandSave req = new MCH_PacketCommandSave();
        req.readData(data);
        MCH_EntityAircraft ac = MCH_EntityAircraft.getAircraft_RiddenOrControl((Entity)player);
        if (ac != null) {
            ac.setCommand(req.str, player);
        }
    }
}

