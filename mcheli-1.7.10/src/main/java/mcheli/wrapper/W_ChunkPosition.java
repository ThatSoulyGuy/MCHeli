package mcheli.wrapper;

import net.minecraft.world.ChunkPosition;

public class W_ChunkPosition {
   public static int getChunkPosX(ChunkPosition c) {
      return c.chunkPosX;
   }

   public static int getChunkPosY(ChunkPosition c) {
      return c.chunkPosY;
   }

   public static int getChunkPosZ(ChunkPosition c) {
      return c.chunkPosZ;
   }
}
