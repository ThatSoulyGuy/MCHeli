/*
 * Decompiled with CFR 0.152.
 */
package mcheli.aircraft;

import com.google.common.io.ByteArrayDataInput;
import java.io.DataOutputStream;
import java.io.IOException;
import mcheli.MCH_Packet;
import mcheli.wrapper.W_Entity;
import mcheli.wrapper.W_Network;
import mcheli.wrapper.W_PacketBase;
import net.minecraft.entity.Entity;

public class MCH_PacketNotifyWeaponID
extends MCH_Packet {
    public int entityID_Ac = -1;
    public int seatID = -1;
    public int weaponID = -1;
    public short ammo = 0;
    public short restAmmo = 0;

    public int getMessageID() {
        return 0x10001031;
    }

    public void readData(ByteArrayDataInput data) {
        try {
            this.entityID_Ac = data.readInt();
            this.seatID = data.readByte();
            this.weaponID = data.readByte();
            this.ammo = data.readShort();
            this.restAmmo = data.readShort();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeData(DataOutputStream dos) {
        try {
            dos.writeInt(this.entityID_Ac);
            dos.writeByte(this.seatID);
            dos.writeByte(this.weaponID);
            dos.writeShort(this.ammo);
            dos.writeShort(this.restAmmo);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void send(Entity sender, int sid, int wid, int ammo, int rest_ammo) {
        MCH_PacketNotifyWeaponID s = new MCH_PacketNotifyWeaponID();
        s.entityID_Ac = W_Entity.getEntityId((Entity)sender);
        s.seatID = sid;
        s.weaponID = wid;
        s.ammo = (short)ammo;
        s.restAmmo = (short)rest_ammo;
        W_Network.sendToAllAround((W_PacketBase)s, (Entity)sender, (double)150.0);
    }
}

