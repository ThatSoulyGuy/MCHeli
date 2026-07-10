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
import net.minecraft.item.Item;
import net.minecraft.item.crafting.IRecipe;

public class MCH_DraftingTableCreatePacket extends MCH_Packet {
   public Item outputItem;
   public Map<Item, Integer> map = new HashMap<>();

   @Override
   public int getMessageID() {
      return 537395216;
   }

   @Override
   public void readData(ByteArrayDataInput data) {
      try {
         this.outputItem = W_Item.getItemByName(data.readUTF());
         int size = data.readByte();

         for (int i = 0; i < size; i++) {
            String s = data.readUTF();
            int num = data.readByte();
            Item item = W_Item.getItemByName(s);
            if (item != null) {
               this.map.put(item, 0 + num);
            }
         }
      } catch (Exception e) {
      }
   }

   @Override
   public void writeData(DataOutputStream dos) {
      try {
         dos.writeUTF(this.getItemName(this.outputItem));
         dos.writeByte(this.map.size());

         for (Item key : this.map.keySet()) {
            dos.writeUTF(this.getItemName(key));
            dos.writeByte(this.map.get(key).byteValue());
         }
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   private String getItemName(Item item) {
      return W_Item.getNameForItem(item);
   }

   public static void send(IRecipe recipe) {
      if (recipe != null) {
         MCH_DraftingTableCreatePacket s = new MCH_DraftingTableCreatePacket();
         s.outputItem = recipe.getRecipeOutput() != null ? recipe.getRecipeOutput().getItem() : null;
         if (s.outputItem != null) {
            s.map = MCH_Lib.getItemMapFromRecipe(recipe);
            W_Network.sendToServer(s);
         }

         MCH_Lib.DbgLog(true, "MCH_DraftingTableCreatePacket.send outputItem = " + s.outputItem);
      }
   }
}
