/*
 * Decompiled with CFR 0.152.
 */
package mcheli.multiplay;

import com.google.common.io.ByteArrayDataInput;
import java.io.DataOutputStream;
import mcheli.MCH_Packet;
import mcheli.wrapper.W_Network;
import mcheli.wrapper.W_PacketBase;
import net.minecraft.entity.player.EntityPlayer;

public class MCH_PacketNotifyMarkPoint
extends MCH_Packet {
    public int px = 0;
    public int py = 0;
    public int pz = 0;

    public int getMessageID() {
        return 268437762;
    }

    public void readData(ByteArrayDataInput data) {
        try {
            this.px = data.readInt();
            this.py = data.readInt();
            this.pz = data.readInt();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeData(DataOutputStream dos) {
        try {
            dos.writeInt(this.px);
            dos.writeInt(this.py);
            dos.writeInt(this.pz);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void send(EntityPlayer player, int x, int y, int z) {
        MCH_PacketNotifyMarkPoint pkt = new MCH_PacketNotifyMarkPoint();
        pkt.px = x;
        pkt.py = y;
        pkt.pz = z;
        W_Network.sendToPlayer((W_PacketBase)pkt, (EntityPlayer)player);
    }
}

