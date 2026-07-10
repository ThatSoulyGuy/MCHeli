package mcheli.wrapper;

import net.minecraft.block.Block;
import net.minecraft.item.Item;

public class W_Item extends Item {
   public W_Item(int par1) {
   }

   public W_Item() {
   }

   public static int getIdFromItem(Item i) {
      return i == null ? 0 : itemRegistry.getIDForObject(i);
   }

   public Item setTexture(String par1Str) {
      this.setTextureName(W_MOD.DOMAIN + ":" + par1Str);
      return this;
   }

   public static Item getItemById(int i) {
      return Item.getItemById(i);
   }

   public static Item getItemByName(String nm) {
      if (nm.indexOf(58) < 0) {
         nm = "minecraft:" + nm;
      }

      return (Item)Item.itemRegistry.getObject(nm);
   }

   public static String getNameForItem(Item item) {
      return Item.itemRegistry.getNameForObject(item);
   }

   public static Item getItemFromBlock(Block block) {
      return Item.getItemFromBlock(block);
   }
}
