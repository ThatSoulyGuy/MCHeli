/*
 * Decompiled with CFR 0.152.
 */
package mcheli;

import com.google.common.io.ByteArrayDataInput;
import java.io.DataOutputStream;
import java.io.IOException;
import mcheli.MCH_Packet;
import mcheli.MCH_PacketEffectExplosion;
import mcheli.wrapper.W_Network;
import mcheli.wrapper.W_PacketBase;

public class MCH_PacketEffectExplosion
extends MCH_Packet {
    ExplosionParam prm = new ExplosionParam(this);

    public int getMessageID() {
        return 0x10000810;
    }

    public void readData(ByteArrayDataInput data) {
        try {
            this.prm.posX = data.readDouble();
            this.prm.posY = data.readDouble();
            this.prm.posZ = data.readDouble();
            this.prm.size = data.readFloat();
            this.prm.exploderID = data.readInt();
            this.prm.inWater = data.readByte() != 0;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeData(DataOutputStream dos) {
        try {
            dos.writeDouble(this.prm.posX);
            dos.writeDouble(this.prm.posY);
            dos.writeDouble(this.prm.posZ);
            dos.writeFloat(this.prm.size);
            dos.writeInt(this.prm.exploderID);
            dos.writeByte(this.prm.inWater ? 1 : 0);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static ExplosionParam create() {
        return new MCH_PacketEffectExplosion().aaa();
    }

    private ExplosionParam aaa() {
        return new ExplosionParam(this);
    }

    public static void send(ExplosionParam param) {
        if (param != null) {
            MCH_PacketEffectExplosion s = new MCH_PacketEffectExplosion();
            s.prm = param;
            W_Network.sendToAllPlayers((W_PacketBase)s);
        }
    }
}

