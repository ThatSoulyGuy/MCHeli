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

public class MCH_PacketIndNotifyAmmoNum
extends MCH_Packet {
    public int entityID_Ac = -1;
    public byte weaponID = (byte)-1;

    public int getMessageID() {
        return 536875061;
    }

    public void readData(ByteArrayDataInput data) {
        try {
            this.entityID_Ac = data.readInt();
            this.weaponID = data.readByte();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeData(DataOutputStream dos) {
        try {
            dos.writeInt(this.entityID_Ac);
            dos.writeByte(this.weaponID);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void send(MCH_EntityAircraft ac, int wid) {
        MCH_PacketIndNotifyAmmoNum s = new MCH_PacketIndNotifyAmmoNum();
        s.entityID_Ac = W_Entity.getEntityId((Entity)ac);
        s.weaponID = (byte)wid;
        W_Network.sendToServer((W_PacketBase)s);
    }
}

