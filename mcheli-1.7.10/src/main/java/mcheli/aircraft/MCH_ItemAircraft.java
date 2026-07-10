package mcheli.aircraft;

import java.util.List;
import mcheli.MCH_Achievement;
import mcheli.MCH_Config;
import mcheli.wrapper.W_EntityPlayer;
import mcheli.wrapper.W_Item;
import mcheli.wrapper.W_MovingObjectPosition;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDispenser;
import net.minecraft.block.BlockSponge;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecartEmpty;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public abstract class MCH_ItemAircraft extends W_Item {
   private static boolean isRegistedDispenseBehavior = false;

   public MCH_ItemAircraft(int i) {
      super(i);
   }

   public static void registerDispenseBehavior(Item item) {
      if (!isRegistedDispenseBehavior) {
         BlockDispenser.dispenseBehaviorRegistry.putObject(item, new MCH_ItemAircraftDispenseBehavior());
      }
   }

   public abstract MCH_AircraftInfo getAircraftInfo();

   public abstract MCH_EntityAircraft createAircraft(World var1, double var2, double var4, double var6, ItemStack var8);

   public MCH_EntityAircraft onTileClick(ItemStack itemStack, World world, float rotationYaw, int x, int y, int z) {
      MCH_EntityAircraft ac = this.createAircraft(world, x + 0.5F, y + 1.0F, z + 0.5F, itemStack);
      if (ac == null) {
         return null;
      }

      ac.initRotationYaw(((MathHelper.floor_double(rotationYaw * 4.0F / 360.0F + 0.5) & 3) - 1) * 90);
      return !world.getCollidingBoundingBoxes(ac, ac.boundingBox.expand(-0.1, -0.1, -0.1)).isEmpty() ? null : ac;
   }

   public String toString() {
      MCH_AircraftInfo info = this.getAircraftInfo();
      return info != null ? super.toString() + "(" + info.getDirectoryName() + ":" + info.name + ")" : super.toString() + "(null)";
   }

   public ItemStack onItemRightClick(ItemStack par1ItemStack, World world, EntityPlayer player) {
      float f = 1.0F;
      float f1 = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * f;
      float f2 = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * f;
      double d0 = player.prevPosX + (player.posX - player.prevPosX) * f;
      double d1 = player.prevPosY + (player.posY - player.prevPosY) * f + 1.62 - player.yOffset;
      double d2 = player.prevPosZ + (player.posZ - player.prevPosZ) * f;
      Vec3 vec3 = W_WorldFunc.getWorldVec3(world, d0, d1, d2);
      float f3 = MathHelper.cos(-f2 * (float) (Math.PI / 180.0) - (float) Math.PI);
      float f4 = MathHelper.sin(-f2 * (float) (Math.PI / 180.0) - (float) Math.PI);
      float f5 = -MathHelper.cos(-f1 * (float) (Math.PI / 180.0));
      float f6 = MathHelper.sin(-f1 * (float) (Math.PI / 180.0));
      float f7 = f4 * f5;
      float f8 = f3 * f5;
      double d3 = 5.0;
      Vec3 vec31 = vec3.addVector(f7 * d3, f6 * d3, f8 * d3);
      MovingObjectPosition mop = W_WorldFunc.clip(world, vec3, vec31, true);
      if (mop == null) {
         return par1ItemStack;
      }

      Vec3 vec32 = player.getLook(f);
      boolean flag = false;
      float f9 = 1.0F;
      List list = world.getEntitiesWithinAABBExcludingEntity(
         player, player.boundingBox.addCoord(vec32.xCoord * d3, vec32.yCoord * d3, vec32.zCoord * d3).expand(f9, f9, f9)
      );

      for (int i = 0; i < list.size(); i++) {
         Entity entity = (Entity)list.get(i);
         if (entity.canBeCollidedWith()) {
            float f10 = entity.getCollisionBorderSize();
            AxisAlignedBB axisalignedbb = entity.boundingBox.expand(f10, f10, f10);
            if (axisalignedbb.isVecInside(vec3)) {
               flag = true;
            }
         }
      }

      if (flag) {
         return par1ItemStack;
      }

      if (W_MovingObjectPosition.isHitTypeTile(mop)) {
         if (MCH_Config.PlaceableOnSpongeOnly.prmBool) {
            Block block = world.getBlock(mop.blockX, mop.blockY, mop.blockZ);
            if (!(block instanceof BlockSponge)) {
               return par1ItemStack;
            }
         }

         this.spawnAircraft(par1ItemStack, world, player, mop.blockX, mop.blockY, mop.blockZ);
      }

      return par1ItemStack;
   }

   public MCH_EntityAircraft spawnAircraft(ItemStack itemStack, World world, EntityPlayer player, int x, int y, int z) {
      MCH_EntityAircraft ac = this.onTileClick(itemStack, world, player.rotationYaw, x, y, z);
      if (ac != null) {
         if (ac.isUAV()) {
            if (world.isRemote) {
               if (ac.isSmallUAV()) {
                  W_EntityPlayer.addChatMessage(player, "Please use the UAV station OR Portable Controller");
               } else {
                  W_EntityPlayer.addChatMessage(player, "Please use the UAV station");
               }
            }

            ac = null;
         } else {
            if (!world.isRemote) {
               ac.getAcDataFromItem(itemStack);
               world.spawnEntityInWorld(ac);
               MCH_Achievement.addStat(player, MCH_Achievement.welcome, 1);
            }

            if (!player.capabilities.isCreativeMode) {
               itemStack.stackSize--;
            }
         }
      }

      return ac;
   }

   public void rideEntity(ItemStack item, Entity target, EntityPlayer player) {
      if (!MCH_Config.PlaceableOnSpongeOnly.prmBool && target instanceof EntityMinecartEmpty && target.riddenByEntity == null) {
         MCH_EntityAircraft ac = this.spawnAircraft(
            item, player.worldObj, player, (int)target.posX, (int)target.posY + 2, (int)target.posZ
         );
         if (!player.worldObj.isRemote && ac != null) {
            ac.mountEntity(target);
         }
      }
   }
}
