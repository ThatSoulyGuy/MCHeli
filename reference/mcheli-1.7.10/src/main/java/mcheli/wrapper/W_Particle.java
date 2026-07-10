package mcheli.wrapper;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;

public class W_Particle {
   public static String getParticleTileCrackName(World w, int blockX, int blockY, int blockZ) {
      Block block = w.getBlock(blockX, blockY, blockZ);
      return block.getMaterial() != Material.air ? "blockcrack_" + Block.getIdFromBlock(block) + "_" + w.getBlockMetadata(blockX, blockY, blockZ) : "";
   }

   public static String getParticleTileDustName(World w, int blockX, int blockY, int blockZ) {
      Block block = w.getBlock(blockX, blockY, blockZ);
      return block.getMaterial() != Material.air ? "blockdust_" + Block.getIdFromBlock(block) + "_" + w.getBlockMetadata(blockX, blockY, blockZ) : "";
   }
}
