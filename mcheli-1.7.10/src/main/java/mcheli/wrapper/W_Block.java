package mcheli.wrapper;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

public abstract class W_Block extends Block {
   protected W_Block(Material p_i45394_1_) {
      super(p_i45394_1_);
   }

   public static Block getBlockFromName(String name) {
      return Block.getBlockFromName(name);
   }

   public static Block getSnowLayer() {
      return W_Blocks.snow_layer;
   }

   public static boolean isNull(Block block) {
      return block == null || block == W_Blocks.air;
   }

   public static boolean isEqual(int blockId, Block block) {
      return Block.isEqualTo(Block.getBlockById(blockId), block);
   }

   public static boolean isEqual(Block block1, Block block2) {
      return Block.isEqualTo(block1, block2);
   }

   public static Block getWater() {
      return W_Blocks.water;
   }

   public static Block getBlockById(int i) {
      return Block.getBlockById(i);
   }
}
