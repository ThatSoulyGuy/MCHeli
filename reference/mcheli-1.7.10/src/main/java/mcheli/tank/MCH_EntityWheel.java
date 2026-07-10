package mcheli.tank;

import java.util.ArrayList;
import java.util.List;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_EntityHitBox;
import mcheli.aircraft.MCH_EntitySeat;
import mcheli.wrapper.W_Entity;
import mcheli.wrapper.W_Lib;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class MCH_EntityWheel extends W_Entity {
   private MCH_EntityAircraft parents;
   public Vec3 pos;
   boolean isPlus;

   public MCH_EntityWheel(World w) {
      super(w);
      this.setSize(1.0F, 1.0F);
      this.stepHeight = 1.5F;
      this.isImmuneToFire = true;
      this.isPlus = false;
   }

   public void setWheelPos(Vec3 pos, Vec3 weightedCenter) {
      this.pos = pos;
      this.isPlus = pos.zCoord >= weightedCenter.zCoord;
   }

   public void travelToDimension(int p_71027_1_) {
   }

   public MCH_EntityAircraft getParents() {
      return this.parents;
   }

   public void setParents(MCH_EntityAircraft parents) {
      this.parents = parents;
   }

   protected void readEntityFromNBT(NBTTagCompound p_70037_1_) {
      this.setDead();
   }

   protected void writeEntityToNBT(NBTTagCompound p_70014_1_) {
   }

   public void moveEntity(double parX, double parY, double parZ) {
      this.worldObj.theProfiler.startSection("move");
      this.ySize *= 0.4F;
      double nowPosX = this.posX;
      double nowPosY = this.posY;
      double nowPosZ = this.posZ;
      double mx = parX;
      double my = parY;
      double mz = parZ;
      AxisAlignedBB axisalignedbb = this.boundingBox.copy();
      List list = this.getCollidingBoundingBoxes(this, this.boundingBox.addCoord(parX, parY, parZ));

      for (int i = 0; i < list.size(); i++) {
         parY = ((AxisAlignedBB)list.get(i)).calculateYOffset(this.boundingBox, parY);
      }

      this.boundingBox.offset(0.0, parY, 0.0);
      boolean flag1 = this.onGround || my != parY && my < 0.0;

      for (int i = 0; i < list.size(); i++) {
         parX = ((AxisAlignedBB)list.get(i)).calculateXOffset(this.boundingBox, parX);
      }

      this.boundingBox.offset(parX, 0.0, 0.0);

      for (int j = 0; j < list.size(); j++) {
         parZ = ((AxisAlignedBB)list.get(j)).calculateZOffset(this.boundingBox, parZ);
      }

      this.boundingBox.offset(0.0, 0.0, parZ);
      if (this.stepHeight > 0.0F && flag1 && this.ySize < 0.05F && (mx != parX || mz != parZ)) {
         double bkParX = parX;
         double bkParY = parY;
         double bkParZ = parZ;
         parX = mx;
         parY = this.stepHeight;
         parZ = mz;
         AxisAlignedBB axisalignedbb1 = this.boundingBox.copy();
         this.boundingBox.setBB(axisalignedbb);
         list = this.getCollidingBoundingBoxes(this, this.boundingBox.addCoord(mx, parY, mz));

         for (int k = 0; k < list.size(); k++) {
            parY = ((AxisAlignedBB)list.get(k)).calculateYOffset(this.boundingBox, parY);
         }

         this.boundingBox.offset(0.0, parY, 0.0);

         for (int k = 0; k < list.size(); k++) {
            parX = ((AxisAlignedBB)list.get(k)).calculateXOffset(this.boundingBox, parX);
         }

         this.boundingBox.offset(parX, 0.0, 0.0);

         for (int k = 0; k < list.size(); k++) {
            parZ = ((AxisAlignedBB)list.get(k)).calculateZOffset(this.boundingBox, parZ);
         }

         this.boundingBox.offset(0.0, 0.0, parZ);
         parY = -this.stepHeight;

         for (int k = 0; k < list.size(); k++) {
            parY = ((AxisAlignedBB)list.get(k)).calculateYOffset(this.boundingBox, parY);
         }

         this.boundingBox.offset(0.0, parY, 0.0);
         if (bkParX * bkParX + bkParZ * bkParZ >= parX * parX + parZ * parZ) {
            parX = bkParX;
            parY = bkParY;
            parZ = bkParZ;
            this.boundingBox.setBB(axisalignedbb1);
         }
      }

      this.worldObj.theProfiler.endSection();
      this.worldObj.theProfiler.startSection("rest");
      this.posX = (this.boundingBox.minX + this.boundingBox.maxX) / 2.0;
      this.posY = this.boundingBox.minY + this.yOffset - this.ySize;
      this.posZ = (this.boundingBox.minZ + this.boundingBox.maxZ) / 2.0;
      this.isCollidedHorizontally = mx != parX || mz != parZ;
      this.isCollidedVertically = my != parY;
      this.onGround = my != parY && my < 0.0;
      this.isCollided = this.isCollidedHorizontally || this.isCollidedVertically;
      this.updateFallState(parY, this.onGround);
      if (mx != parX) {
         this.motionX = 0.0;
      }

      if (my != parY) {
         this.motionY = 0.0;
      }

      if (mz != parZ) {
         this.motionZ = 0.0;
      }

      try {
         this.doBlockCollisions();
      } catch (Throwable throwable) {
         CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Checking entity tile collision");
         CrashReportCategory crashreportcategory = crashreport.makeCategory("Entity being checked for collision");
         this.addEntityCrashInfo(crashreportcategory);
      }

      this.worldObj.theProfiler.endSection();
   }

   public List getCollidingBoundingBoxes(Entity par1Entity, AxisAlignedBB par2AxisAlignedBB) {
      ArrayList collidingBoundingBoxes = new ArrayList();
      collidingBoundingBoxes.clear();
      int i = MathHelper.floor_double(par2AxisAlignedBB.minX);
      int j = MathHelper.floor_double(par2AxisAlignedBB.maxX + 1.0);
      int k = MathHelper.floor_double(par2AxisAlignedBB.minY);
      int l = MathHelper.floor_double(par2AxisAlignedBB.maxY + 1.0);
      int i1 = MathHelper.floor_double(par2AxisAlignedBB.minZ);
      int j1 = MathHelper.floor_double(par2AxisAlignedBB.maxZ + 1.0);

      for (int k1 = i; k1 < j; k1++) {
         for (int l1 = i1; l1 < j1; l1++) {
            if (par1Entity.worldObj.blockExists(k1, 64, l1)) {
               for (int i2 = k - 1; i2 < l; i2++) {
                  Block block = W_WorldFunc.getBlock(par1Entity.worldObj, k1, i2, l1);
                  if (block != null) {
                     block.addCollisionBoxesToList(par1Entity.worldObj, k1, i2, l1, par2AxisAlignedBB, collidingBoundingBoxes, par1Entity);
                  }
               }
            }
         }
      }

      double d0 = 0.25;
      List list = par1Entity.worldObj.getEntitiesWithinAABBExcludingEntity(par1Entity, par2AxisAlignedBB.expand(d0, d0, d0));

      for (int j2 = 0; j2 < list.size(); j2++) {
         Entity entity = (Entity)list.get(j2);
         if (!W_Lib.isEntityLivingBase(entity) && !(entity instanceof MCH_EntitySeat) && !(entity instanceof MCH_EntityHitBox) && entity != this.parents) {
            AxisAlignedBB axisalignedbb1 = entity.getBoundingBox();
            if (axisalignedbb1 != null && axisalignedbb1.intersectsWith(par2AxisAlignedBB)) {
               collidingBoundingBoxes.add(axisalignedbb1);
            }

            axisalignedbb1 = par1Entity.getCollisionBox(entity);
            if (axisalignedbb1 != null && axisalignedbb1.intersectsWith(par2AxisAlignedBB)) {
               collidingBoundingBoxes.add(axisalignedbb1);
            }
         }
      }

      return collidingBoundingBoxes;
   }
}
