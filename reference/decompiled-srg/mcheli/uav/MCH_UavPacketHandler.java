/*
 * Decompiled with CFR 0.152.
 */
package mcheli.uav;

import com.google.common.io.ByteArrayDataInput;
import mcheli.uav.MCH_EntityUavStation;
import mcheli.uav.MCH_UavPacketStatus;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

public class MCH_UavPacketHandler {
    public static void onPacketUavStatus(EntityPlayer player, ByteArrayDataInput data) {
        if (!player.field_70170_p.field_72995_K) {
            MCH_UavPacketStatus status = new MCH_UavPacketStatus();
            status.readData(data);
            if (player.field_70154_o instanceof MCH_EntityUavStation) {
                ((MCH_EntityUavStation)player.field_70154_o).setUavPosition((int)status.posUavX, (int)status.posUavY, (int)status.posUavZ);
                if (status.continueControl) {
                    ((MCH_EntityUavStation)player.field_70154_o).controlLastAircraft((Entity)player);
                }
            }
        }
    }
}

