package mcheli.container;

import java.util.List;
import mcheli.wrapper.W_Item;
import mcheli.wrapper.W_MovingObjectPosition;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class MCH_ItemContainer extends W_Item {
   public MCH_ItemContainer(int par1) {
      super(par1);
      this.maxStackSize = 1;
   }

   public ItemStack onItemRightClick(ItemStack par1ItemStack, World par2World, EntityPlayer par3EntityPlayer) {
      float f = 1.0F;
      float f1 = par3EntityPlayer.prevRotationPitch + (par3EntityPlayer.rotationPitch - par3EntityPlayer.prevRotationPitch) * f;
      float f2 = par3EntityPlayer.prevRotationYaw + (par3EntityPlayer.rotationYaw - par3EntityPlayer.prevRotationYaw) * f;
      double d0 = par3EntityPlayer.prevPosX + (par3EntityPlayer.posX - par3EntityPlayer.prevPosX) * f;
      double d1 = par3EntityPlayer.prevPosY
         + (par3EntityPlayer.posY - par3EntityPlayer.prevPosY) * f
         + 1.62
         - par3EntityPlayer.yOffset;
      double d2 = par3EntityPlayer.prevPosZ + (par3EntityPlayer.posZ - par3EntityPlayer.prevPosZ) * f;
      Vec3 vec3 = W_WorldFunc.getWorldVec3(par2World, d0, d1, d2);
      float f3 = MathHelper.cos(-f2 * (float) (Math.PI / 180.0) - (float) Math.PI);
      float f4 = MathHelper.sin(-f2 * (float) (Math.PI / 180.0) - (float) Math.PI);
      float f5 = -MathHelper.cos(-f1 * (float) (Math.PI / 180.0));
      float f6 = MathHelper.sin(-f1 * (float) (Math.PI / 180.0));
      float f7 = f4 * f5;
      float f8 = f3 * f5;
      double d3 = 5.0;
      Vec3 vec31 = vec3.addVector(f7 * d3, f6 * d3, f8 * d3);
      MovingObjectPosition movingobjectposition = W_WorldFunc.clip(par2World, vec3, vec31, true);
      if (movingobjectposition == null) {
         return par1ItemStack;
      }

      Vec3 vec32 = par3EntityPlayer.getLook(f);
      boolean flag = false;
      float f9 = 1.0F;
      List list = par2World.getEntitiesWithinAABBExcludingEntity(
         par3EntityPlayer,
         par3EntityPlayer.boundingBox.addCoord(vec32.xCoord * d3, vec32.yCoord * d3, vec32.zCoord * d3).expand(f9, f9, f9)
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

      if (W_MovingObjectPosition.isHitTypeTile(movingobjectposition)) {
         int var32 = movingobjectposition.blockX;
         int j = movingobjectposition.blockY;
         int k = movingobjectposition.blockZ;
         MCH_EntityContainer entityboat = new MCH_EntityContainer(par2World, var32 + 0.5F, j + 1.0F, k + 0.5F);
         entityboat.rotationYaw = ((MathHelper.floor_double(par3EntityPlayer.rotationYaw * 4.0F / 360.0F + 0.5) & 3) - 1) * 90;
         if (!par2World.getCollidingBoundingBoxes(entityboat, entityboat.boundingBox.expand(-0.1, -0.1, -0.1)).isEmpty()) {
            return par1ItemStack;
         }

         if (!par2World.isRemote) {
            par2World.spawnEntityInWorld(entityboat);
         }

         if (!par3EntityPlayer.capabilities.isCreativeMode) {
            par1ItemStack.stackSize--;
         }
      }

      return par1ItemStack;
   }
}
