/*
 * Decompiled with CFR 0.152.
 */
package mcheli.aircraft;

import com.google.common.io.ByteArrayDataInput;
import java.io.DataOutputStream;
import java.io.IOException;
import mcheli.MCH_Packet;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.wrapper.W_Entity;
import mcheli.wrapper.W_Network;
import mcheli.wrapper.W_PacketBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

public class MCH_PacketNotifyHitBullet
extends MCH_Packet {
    public int entityID_Ac = -1;

    public int getMessageID() {
        return 268439602;
    }

    public void readData(ByteArrayDataInput data) {
        try {
            this.entityID_Ac = data.readInt();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeData(DataOutputStream dos) {
        try {
            dos.writeInt(this.entityID_Ac);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void send(MCH_EntityAircraft ac, EntityPlayer rider) {
        if (rider == null || rider.field_70128_L) {
            return;
        }
        MCH_PacketNotifyHitBullet s = new MCH_PacketNotifyHitBullet();
        s.entityID_Ac = ac != null && !ac.field_70128_L ? W_Entity.getEntityId((Entity)ac) : -1;
        W_Network.sendToPlayer((W_PacketBase)s, (EntityPlayer)rider);
    }
}

