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

public class MCH_PacketNotifyOnMountEntity
extends MCH_Packet {
    public int entityID_Ac = -1;
    public int entityID_rider = -1;
    public int seatID = -1;

    public int getMessageID() {
        return 0x10001050;
    }

    public void readData(ByteArrayDataInput data) {
        try {
            this.entityID_Ac = data.readInt();
            this.entityID_rider = data.readInt();
            this.seatID = data.readByte();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeData(DataOutputStream dos) {
        try {
            dos.writeInt(this.entityID_Ac);
            dos.writeInt(this.entityID_rider);
            dos.writeByte(this.seatID);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void send(MCH_EntityAircraft ac, Entity rider, int seatId) {
        if (ac == null || rider == null) {
            return;
        }
        Entity pilot = ac.getRiddenByEntity();
        if (!(pilot instanceof EntityPlayer) || pilot.field_70128_L) {
            return;
        }
        MCH_PacketNotifyOnMountEntity s = new MCH_PacketNotifyOnMountEntity();
        s.entityID_Ac = W_Entity.getEntityId((Entity)ac);
        s.entityID_rider = W_Entity.getEntityId((Entity)rider);
        s.seatID = seatId;
        W_Network.sendToPlayer((W_PacketBase)s, (EntityPlayer)((EntityPlayer)pilot));
    }
}

