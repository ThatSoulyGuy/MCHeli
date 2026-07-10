/*
 * Decompiled with CFR 0.152.
 */
package mcheli.command;

import com.google.common.io.ByteArrayDataInput;
import java.io.DataOutputStream;
import java.io.IOException;
import mcheli.MCH_Packet;
import mcheli.wrapper.W_Network;
import mcheli.wrapper.W_PacketBase;

public class MCH_PacketCommandSave
extends MCH_Packet {
    public String str = "";

    public int getMessageID() {
        return 536873729;
    }

    public void readData(ByteArrayDataInput data) {
        try {
            this.str = data.readUTF();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeData(DataOutputStream dos) {
        try {
            dos.writeUTF(this.str);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void send(String cmd) {
        MCH_PacketCommandSave s = new MCH_PacketCommandSave();
        s.str = cmd;
        W_Network.sendToServer((W_PacketBase)s);
    }
}

