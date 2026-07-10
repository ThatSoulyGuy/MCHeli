/*
 * Decompiled with CFR 0.152.
 */
package mcheli.block;

import com.google.common.io.ByteArrayDataInput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import mcheli.MCH_Lib;
import mcheli.MCH_Packet;
import mcheli.wrapper.W_Item;
import mcheli.wrapper.W_Network;
import mcheli.wrapper.W_PacketBase;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.IRecipe;

public class MCH_DraftingTableCreatePacket
extends MCH_Packet {
    public Item outputItem;
    public Map<Item, Integer> map = new HashMap();

    public int getMessageID() {
        return 537395216;
    }

    public void readData(ByteArrayDataInput data) {
        try {
            this.outputItem = W_Item.getItemByName((String)data.readUTF());
            int size = data.readByte();
            for (int i = 0; i < size; ++i) {
                String s = data.readUTF();
                byte num = data.readByte();
                Item item = W_Item.getItemByName((String)s);
                if (item == null) continue;
                this.map.put(item, 0 + num);
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    public void writeData(DataOutputStream dos) {
        try {
            dos.writeUTF(this.getItemName(this.outputItem));
            dos.writeByte(this.map.size());
            for (Item key : this.map.keySet()) {
                dos.writeUTF(this.getItemName(key));
                dos.writeByte(((Integer)this.map.get(key)).byteValue());
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getItemName(Item item) {
        return W_Item.getNameForItem((Item)item);
    }

    public static void send(IRecipe recipe) {
        if (recipe != null) {
            MCH_DraftingTableCreatePacket s = new MCH_DraftingTableCreatePacket();
            Item item = s.outputItem = recipe.func_77571_b() != null ? recipe.func_77571_b().func_77973_b() : null;
            if (s.outputItem != null) {
                s.map = MCH_Lib.getItemMapFromRecipe((IRecipe)recipe);
                W_Network.sendToServer((W_PacketBase)s);
            }
            MCH_Lib.DbgLog((boolean)true, (String)("MCH_DraftingTableCreatePacket.send outputItem = " + s.outputItem), (Object[])new Object[0]);
        }
    }
}

