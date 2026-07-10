/*
 * Decompiled with CFR 0.152.
 */
package mcheli.helicopter;

import com.google.common.io.ByteArrayDataInput;
import java.io.DataOutputStream;
import java.io.IOException;
import mcheli.aircraft.MCH_PacketPlayerControlBase;

public class MCH_HeliPacketPlayerControl
extends MCH_PacketPlayerControlBase {
    public byte switchFold = (byte)-1;
    public int unhitchChainId = -1;

    public int getMessageID() {
        return 0x20002010;
    }

    public void readData(ByteArrayDataInput data) {
        super.readData(data);
        try {
            this.switchFold = data.readByte();
            this.unhitchChainId = data.readInt();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeData(DataOutputStream dos) {
        super.writeData(dos);
        try {
            dos.writeByte(this.switchFold);
            dos.writeInt(this.unhitchChainId);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}

