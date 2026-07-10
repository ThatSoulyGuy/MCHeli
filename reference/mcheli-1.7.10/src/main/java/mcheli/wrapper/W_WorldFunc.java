package mcheli.wrapper;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class W_WorldFunc {
   public static void DEF_playSoundEffect(World w, double x, double y, double z, String name, float volume, float pitch) {
      w.playSoundEffect(x, y, z, name, volume, pitch);
   }

   public static void MOD_playSoundEffect(World w, double x, double y, double z, String name, float volume, float pitch) {
      DEF_playSoundEffect(w, x, y, z, W_MOD.DOMAIN + ":" + name, volume, pitch);
   }

   private static void playSoundAtEntity(Entity e, String name, float volume, float pitch) {
      e.worldObj.playSoundAtEntity(e, name, volume, pitch);
   }

   public static void MOD_playSoundAtEntity(Entity e, String name, float volume, float pitch) {
      playSoundAtEntity(e, W_MOD.DOMAIN + ":" + name, volume, pitch);
   }

   public static int getBlockId(World w, int x, int y, int z) {
      return Block.getIdFromBlock(w.getBlock(x, y, z));
   }

   public static Block getBlock(World w, int x, int y, int z) {
      return w.getBlock(x, y, z);
   }

   public static Material getBlockMaterial(World w, int x, int y, int z) {
      return w.getBlock(x, y, z).getMaterial();
   }

   public static boolean isBlockWater(World w, int x, int y, int z) {
      return isEqualBlock(w, x, y, z, W_Block.getWater());
   }

   public static boolean isEqualBlock(World w, int x, int y, int z, Block block) {
      return Block.isEqualTo(w.getBlock(x, y, z), block);
   }

   public static MovingObjectPosition clip(World w, Vec3 par1Vec3, Vec3 par2Vec3) {
      return w.rayTraceBlocks(par1Vec3, par2Vec3);
   }

   public static MovingObjectPosition clip(World w, Vec3 par1Vec3, Vec3 par2Vec3, boolean b) {
      return w.rayTraceBlocks(par1Vec3, par2Vec3, b);
   }

   public static MovingObjectPosition clip(World w, Vec3 par1Vec3, Vec3 par2Vec3, boolean b1, boolean b2, boolean b3) {
      return w.func_147447_a(par1Vec3, par2Vec3, b1, b2, b3);
   }

   public static boolean setBlock(World w, int a, int b, int c, Block d) {
      return w.setBlock(a, b, c, d);
   }

   public static void setBlock(World w, int x, int y, int z, Block b, int i, int j) {
      w.setBlock(x, y, z, b, i, j);
   }

   public static boolean destroyBlock(World w, int x, int y, int z, boolean par4) {
      return w.func_147480_a(x, y, z, par4);
   }

   public static Vec3 getWorldVec3(World w, double x, double y, double z) {
      return Vec3.createVectorHelper(x, y, z);
   }

   public static Vec3 getWorldVec3EntityPos(Entity e) {
      return getWorldVec3(e.worldObj, e.posX, e.posY, e.posZ);
   }
}
