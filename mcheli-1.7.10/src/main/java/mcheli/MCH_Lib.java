package mcheli;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import mcheli.wrapper.W_Block;
import mcheli.wrapper.W_Blocks;
import mcheli.wrapper.W_McClient;
import mcheli.wrapper.W_Reflection;
import mcheli.wrapper.W_Vec3;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemMapBase;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class MCH_Lib {
   private static HashMap<String, Material> mapMaterial = new HashMap<>();
   public static final String[] AZIMUTH_8 = new String[]{"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
   public static final int AZIMUTH_8_ANG = 360 / AZIMUTH_8.length;

   private MCH_Lib() {
   }

   public static void init() {
      mapMaterial.clear();
      mapMaterial.put("air", Material.air);
      mapMaterial.put("grass", Material.grass);
      mapMaterial.put("ground", Material.ground);
      mapMaterial.put("wood", Material.wood);
      mapMaterial.put("rock", Material.rock);
      mapMaterial.put("iron", Material.iron);
      mapMaterial.put("anvil", Material.anvil);
      mapMaterial.put("water", Material.water);
      mapMaterial.put("lava", Material.lava);
      mapMaterial.put("leaves", Material.leaves);
      mapMaterial.put("plants", Material.plants);
      mapMaterial.put("vine", Material.vine);
      mapMaterial.put("sponge", Material.sponge);
      mapMaterial.put("cloth", Material.cloth);
      mapMaterial.put("fire", Material.fire);
      mapMaterial.put("sand", Material.sand);
      mapMaterial.put("circuits", Material.circuits);
      mapMaterial.put("carpet", Material.carpet);
      mapMaterial.put("glass", Material.glass);
      mapMaterial.put("redstoneLight", Material.redstoneLight);
      mapMaterial.put("tnt", Material.tnt);
      mapMaterial.put("coral", Material.coral);
      mapMaterial.put("ice", Material.ice);
      mapMaterial.put("packedIce", Material.packedIce);
      mapMaterial.put("snow", Material.snow);
      mapMaterial.put("craftedSnow", Material.craftedSnow);
      mapMaterial.put("cactus", Material.cactus);
      mapMaterial.put("clay", Material.clay);
      mapMaterial.put("gourd", Material.gourd);
      mapMaterial.put("dragonEgg", Material.dragonEgg);
      mapMaterial.put("portal", Material.portal);
      mapMaterial.put("cake", Material.cake);
      mapMaterial.put("web", Material.web);
      mapMaterial.put("piston", Material.piston);
   }

   public static Material getMaterialFromName(String name) {
      return mapMaterial.containsKey(name) ? mapMaterial.get(name) : null;
   }

   public static Vec3 calculateFaceNormal(Vec3[] vertices) {
      Vec3 v1 = Vec3.createVectorHelper(
         vertices[1].xCoord - vertices[0].xCoord,
         vertices[1].yCoord - vertices[0].yCoord,
         vertices[1].zCoord - vertices[0].zCoord
      );
      Vec3 v2 = Vec3.createVectorHelper(
         vertices[2].xCoord - vertices[0].xCoord,
         vertices[2].yCoord - vertices[0].yCoord,
         vertices[2].zCoord - vertices[0].zCoord
      );
      return v1.crossProduct(v2).normalize();
   }

   public static double parseDouble(String s) {
      return s == null ? 0.0 : Double.parseDouble(s.replace(',', '.'));
   }

   public static float RNG(float a, float min, float max) {
      return a < min ? min : (a > max ? max : a);
   }

   public static double RNG(double a, double min, double max) {
      return a < min ? min : (a > max ? max : a);
   }

   public static float smooth(float rot, float prevRot, float tick) {
      return prevRot + (rot - prevRot) * tick;
   }

   public static float smoothRot(float rot, float prevRot, float tick) {
      if (rot - prevRot < -180.0F) {
         prevRot -= 360.0F;
      } else if (prevRot - rot < -180.0F) {
         prevRot += 360.0F;
      }

      return prevRot + (rot - prevRot) * tick;
   }

   public static double getRotateDiff(double base, double target) {
      base = getRotate360(base);
      target = getRotate360(target);
      if (target - base < -180.0) {
         target += 360.0;
      } else if (target - base > 180.0) {
         base += 360.0;
      }

      return target - base;
   }

   public static float getPosAngle(double tx, double tz, double cx, double cz) {
      double length_A = Math.sqrt(tx * tx + tz * tz);
      double length_B = Math.sqrt(cx * cx + cz * cz);
      double cos_sita = (tx * cx + tz * cz) / (length_A * length_B);
      double sita = Math.acos(cos_sita);
      return (float)(sita * 180.0 / Math.PI);
   }

   public static boolean canPlayerCreateItem(IRecipe recipe, InventoryPlayer inventory) {
      if (recipe != null) {
         Map<Item, Integer> map = getItemMapFromRecipe(recipe);

         for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack is = inventory.getStackInSlot(i);
            if (is != null) {
               Item item = is.getItem();
               if (map.containsKey(item)) {
                  map.put(item, map.get(item) - is.stackSize);
               }
            }
         }

         for (int i : map.values()) {
            if (i > 0) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public static void applyEntityHurtResistantTimeConfig(Entity entity) {
      if (entity instanceof EntityLivingBase) {
         EntityLivingBase elb = (EntityLivingBase)entity;
         double h_time = MCH_Config.HurtResistantTime.prmDouble * elb.hurtResistantTime;
         elb.hurtResistantTime = (int)h_time;
      }
   }

   public static int round(double d) {
      return (int)(d + 0.5);
   }

   public static Vec3 Rot2Vec3(float yaw, float pitch) {
      return Vec3.createVectorHelper(
         -MathHelper.sin(yaw / 180.0F * (float) Math.PI) * MathHelper.cos(pitch / 180.0F * (float) Math.PI),
         -MathHelper.sin(pitch / 180.0F * (float) Math.PI),
         MathHelper.cos(yaw / 180.0F * (float) Math.PI) * MathHelper.cos(pitch / 180.0F * (float) Math.PI)
      );
   }

   public static Vec3 RotVec3(double x, double y, double z, float yaw, float pitch) {
      Vec3 v = Vec3.createVectorHelper(x, y, z);
      v.rotateAroundX(pitch / 180.0F * (float) Math.PI);
      v.rotateAroundY(yaw / 180.0F * (float) Math.PI);
      return v;
   }

   public static Vec3 RotVec3(double x, double y, double z, float yaw, float pitch, float roll) {
      Vec3 v = Vec3.createVectorHelper(x, y, z);
      W_Vec3.rotateAroundZ(roll / 180.0F * (float) Math.PI, v);
      v.rotateAroundX(pitch / 180.0F * (float) Math.PI);
      v.rotateAroundY(yaw / 180.0F * (float) Math.PI);
      return v;
   }

   public static Vec3 RotVec3(Vec3 vin, float yaw, float pitch) {
      Vec3 v = Vec3.createVectorHelper(vin.xCoord, vin.yCoord, vin.zCoord);
      v.rotateAroundX(pitch / 180.0F * (float) Math.PI);
      v.rotateAroundY(yaw / 180.0F * (float) Math.PI);
      return v;
   }

   public static Vec3 RotVec3(Vec3 vin, float yaw, float pitch, float roll) {
      Vec3 v = Vec3.createVectorHelper(vin.xCoord, vin.yCoord, vin.zCoord);
      W_Vec3.rotateAroundZ(roll / 180.0F * (float) Math.PI, v);
      v.rotateAroundX(pitch / 180.0F * (float) Math.PI);
      v.rotateAroundY(yaw / 180.0F * (float) Math.PI);
      return v;
   }

   public static Vec3 _Rot2Vec3(float yaw, float pitch, float roll) {
      return Vec3.createVectorHelper(
         -MathHelper.sin(yaw / 180.0F * (float) Math.PI) * MathHelper.cos(pitch / 180.0F * (float) Math.PI),
         -MathHelper.sin(pitch / 180.0F * (float) Math.PI),
         MathHelper.cos(yaw / 180.0F * (float) Math.PI) * MathHelper.cos(pitch / 180.0F * (float) Math.PI)
      );
   }

   public static double getRotate360(double r) {
      r %= 360.0;
      return r >= 0.0 ? r : r + 360.0;
   }

   public static void Log(String format, Object... data) {
      String side = MCH_MOD.proxy.isRemote() ? "[Client]" : "[Server]";
      System.out.printf("[" + getTime() + "][" + "mcheli" + "]" + side + " " + format + "\n", data);
   }

   public static void Log(World world, String format, Object... data) {
      if (world != null) {
         Log((world.isRemote ? "[ClientWorld]" : "[ServerWorld]") + " " + format, data);
      } else {
         Log("[UnknownWorld]" + format, data);
      }
   }

   public static void Log(Entity entity, String format, Object... data) {
      if (entity != null) {
         Log(entity.worldObj, format, data);
      } else {
         Log((World)null, format, data);
      }
   }

   public static void DbgLog(boolean isRemote, String format, Object... data) {
      if (MCH_Config.DebugLog) {
         String t = getTime();
         if (isRemote) {
            String playerName = "null";
            if (getClientPlayer() instanceof EntityPlayer) {
               playerName = ((EntityPlayer)getClientPlayer()).getDisplayName();
            }

            System.out.println(String.format(format, data));
         } else {
            System.out.println(String.format(format, data));
         }
      }
   }

   public static void DbgLog(World w, String format, Object... data) {
      DbgLog(w.isRemote, format, data);
   }

   public static String getTime() {
      SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss:SSS");
      return sdf.format(new Date());
   }

   public static String getAzimuthStr8(int dir) {
      dir %= 360;
      if (dir < 0) {
         dir += 360;
      }

      dir /= AZIMUTH_8_ANG;
      return AZIMUTH_8[dir];
   }

   public static void rotatePoints(double[] points, float r) {
      r = r / 180.0F * (float) Math.PI;

      for (int i = 0; i + 1 < points.length; i += 2) {
         double x = points[i + 0];
         double y = points[i + 1];
         points[i + 0] = x * MathHelper.cos(r) - y * MathHelper.sin(r);
         points[i + 1] = x * MathHelper.sin(r) + y * MathHelper.cos(r);
      }
   }

   public static void rotatePoints(ArrayList<MCH_Vector2> points, float r) {
      r = r / 180.0F * (float) Math.PI;

      for (int i = 0; i + 1 < points.size(); i += 2) {
         double x = points.get(i + 0).x;
         double y = points.get(i + 0).y;
         points.get(i + 0).x = x * MathHelper.cos(r) - y * MathHelper.sin(r);
         points.get(i + 0).y = x * MathHelper.sin(r) + y * MathHelper.cos(r);
      }
   }

   public static String[] listupFileNames(String path) {
      File dir = new File(path);
      return dir.list();
   }

   public static boolean isBlockInWater(World w, int x, int y, int z) {
      int[][] offset = new int[][]{{0, -1, 0}, {0, 0, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, 1, 0}};
      if (y <= 0) {
         return false;
      }

      for (int[] o : offset) {
         if (W_WorldFunc.isBlockWater(w, x + o[0], y + o[1], z + o[2])) {
            return true;
         }
      }

      return false;
   }

   public static int getBlockIdY(World w, double posX, double posY, double posZ, int size, int lenY, boolean canColliableOnly) {
      Block block = getBlockY(w, posX, posY, posZ, size, lenY, canColliableOnly);
      return block == null ? 0 : W_Block.getIdFromBlock(block);
   }

   public static int getBlockIdY(Entity entity, int size, int lenY) {
      return getBlockIdY(entity, size, lenY, true);
   }

   public static int getBlockIdY(Entity entity, int size, int lenY, boolean canColliableOnly) {
      Block block = getBlockY(entity, size, lenY, canColliableOnly);
      return block == null ? 0 : W_Block.getIdFromBlock(block);
   }

   public static Block getBlockY(Entity entity, int size, int lenY, boolean canColliableOnly) {
      return getBlockY(entity.worldObj, entity.posX, entity.posY, entity.posZ, size, lenY, canColliableOnly);
   }

   public static Block getBlockY(World world, Vec3 pos, int size, int lenY, boolean canColliableOnly) {
      return getBlockY(world, pos.xCoord, pos.yCoord, pos.zCoord, size, lenY, canColliableOnly);
   }

   public static Block getBlockY(World world, double posX, double posY, double posZ, int size, int lenY, boolean canColliableOnly) {
      if (lenY == 0) {
         return W_Blocks.air;
      }

      int px = (int)(posX + 0.5);
      int py = (int)(posY + 0.5);
      int pz = (int)(posZ + 0.5);
      int cntY = lenY > 0 ? lenY : -lenY;

      for (int y = 0; y < cntY; y++) {
         if (py + y < 0 || py + y > 255) {
            return W_Blocks.air;
         }

         for (int x = -size / 2; x <= size / 2; x++) {
            for (int z = -size / 2; z <= size / 2; z++) {
               Block block = W_WorldFunc.getBlock(world, px + x, py + (lenY > 0 ? y : -y), pz + z);
               if (block != null && block != W_Blocks.air) {
                  if (!canColliableOnly) {
                     return block;
                  }

                  if (block.canCollideCheck(0, true)) {
                     return block;
                  }
               }
            }
         }
      }

      return W_Blocks.air;
   }

   public static Vec3 getYawPitchFromVec(Vec3 v) {
      return getYawPitchFromVec(v.xCoord, v.yCoord, v.zCoord);
   }

   public static Vec3 getYawPitchFromVec(double x, double y, double z) {
      double p = MathHelper.sqrt_double(x * x + z * z);
      float yaw = (float)(Math.atan2(z, x) * 180.0 / Math.PI);
      float roll = (float)(Math.atan2(y, p) * 180.0 / Math.PI);
      return Vec3.createVectorHelper(0.0, yaw, roll);
   }

   public static float getAlpha(int argb) {
      return (argb >> 24) / 255.0F;
   }

   public static float getRed(int argb) {
      return (argb >> 16 & 0xFF) / 255.0F;
   }

   public static float getGreen(int argb) {
      return (argb >> 8 & 0xFF) / 255.0F;
   }

   public static float getBlue(int argb) {
      return (argb & 0xFF) / 255.0F;
   }

   public static void enableFirstPersonItemRender() {
      switch (MCH_Config.DisableItemRender.prmInt) {
         case 1:
         default:
            break;
         case 2:
            MCH_ItemRendererDummy.disableDummyItemRenderer();
            break;
         case 3:
            W_Reflection.restoreCameraZoom();
      }
   }

   public static void disableFirstPersonItemRender(ItemStack itemStack) {
      if (itemStack == null || !(itemStack.getItem() instanceof ItemMapBase) || W_McClient.getRenderEntity() instanceof MCH_ViewEntityDummy) {
         disableFirstPersonItemRender();
      }
   }

   public static void disableFirstPersonItemRender() {
      switch (MCH_Config.DisableItemRender.prmInt) {
         case 1:
            W_Reflection.setItemRenderer_ItemToRender(new ItemStack(MCH_MOD.invisibleItem));
            break;
         case 2:
            MCH_ItemRendererDummy.enableDummyItemRenderer();
            break;
         case 3:
            W_Reflection.setCameraZoom(1.01F);
      }
   }

   public static Entity getClientPlayer() {
      return MCH_MOD.proxy.getClientPlayer();
   }

   public static void setRenderViewEntity(EntityLivingBase entity) {
      if (MCH_Config.ReplaceRenderViewEntity.prmBool) {
         W_McClient.setRenderEntity(entity);
      }
   }

   public static Map<Item, Integer> getItemMapFromRecipe(IRecipe recipe) {
      Map<Item, Integer> map = new HashMap<>();
      if (recipe instanceof ShapedRecipes) {
         for (ItemStack is : ((ShapedRecipes)recipe).recipeItems) {
            if (is != null) {
               Item item = is.getItem();
               if (map.containsKey(item)) {
                  map.put(item, map.get(item) + 1);
               } else {
                  map.put(item, 1);
               }
            }
         }
      } else if (recipe instanceof ShapelessRecipes) {
         for (Object o : ((ShapelessRecipes)recipe).recipeItems) {
            ItemStack is = (ItemStack)o;
            if (is != null) {
               Item item = is.getItem();
               if (map.containsKey(item)) {
                  map.put(item, map.get(item) + 1);
               } else {
                  map.put(item, 1);
               }
            }
         }
      }

      return map;
   }
}
