package mcheli.particles;

import java.util.ArrayList;
import java.util.List;
import mcheli.wrapper.W_Blocks;
import mcheli.wrapper.W_EntityFX;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ReportedException;
import net.minecraft.world.World;

public abstract class MCH_EntityParticleBase extends W_EntityFX {
   public boolean isEffectedWind;
   public boolean diffusible;
   public boolean toWhite;
   public float particleMaxScale;
   public float gravity;
   public float moutionYUpAge;

   public MCH_EntityParticleBase(World par1World, double x, double y, double z, double mx, double my, double mz) {
      super(par1World, x, y, z, mx, my, mz);
      this.motionX = mx;
      this.motionY = my;
      this.motionZ = mz;
      this.isEffectedWind = false;
      this.particleMaxScale = this.particleScale;
   }

   public MCH_EntityParticleBase setParticleScale(float scale) {
      this.particleScale = scale;
      return this;
   }

   public void setParticleMaxAge(int age) {
      this.particleMaxAge = age;
   }

   public void setParticleTextureIndex(int par1) {
      this.particleTextureIndexX = par1 % 8;
      this.particleTextureIndexY = par1 / 8;
   }

   public int getFXLayer() {
      return 2;
   }

   public void moveEntity(double par1, double par3, double par5) {
      if (this.noClip) {
         this.boundingBox.offset(par1, par3, par5);
         this.posX = (this.boundingBox.minX + this.boundingBox.maxX) / 2.0;
         this.posY = this.boundingBox.minY + this.yOffset - this.ySize;
         this.posZ = (this.boundingBox.minZ + this.boundingBox.maxZ) / 2.0;
      } else {
         this.worldObj.theProfiler.startSection("move");
         this.ySize *= 0.4F;
         double d3 = this.posX;
         double d4 = this.posY;
         double d5 = this.posZ;
         double d6 = par1;
         double d7 = par3;
         double d8 = par5;
         AxisAlignedBB axisalignedbb = this.boundingBox.copy();
         boolean flag = false;
         List list = this.worldObj.getCollidingBoundingBoxes(this, this.boundingBox.addCoord(par1, par3, par5));

         for (int i = 0; i < list.size(); i++) {
            par3 = ((AxisAlignedBB)list.get(i)).calculateYOffset(this.boundingBox, par3);
         }

         this.boundingBox.offset(0.0, par3, 0.0);
         if (!this.field_70135_K && d7 != par3) {
            par5 = 0.0;
            par3 = 0.0;
            par1 = 0.0;
         }

         if (!this.onGround && (d7 == par3 || !(d7 < 0.0))) {
            boolean flag1 = false;
         } else {
            boolean flag1 = true;
         }

         for (int j = 0; j < list.size(); j++) {
            par1 = ((AxisAlignedBB)list.get(j)).calculateXOffset(this.boundingBox, par1);
         }

         this.boundingBox.offset(par1, 0.0, 0.0);
         if (!this.field_70135_K && d6 != par1) {
            par5 = 0.0;
            par3 = 0.0;
            par1 = 0.0;
         }

         for (int var35 = 0; var35 < list.size(); var35++) {
            par5 = ((AxisAlignedBB)list.get(var35)).calculateZOffset(this.boundingBox, par5);
         }

         this.boundingBox.offset(0.0, 0.0, par5);
         if (!this.field_70135_K && d8 != par5) {
            par5 = 0.0;
            par3 = 0.0;
            par1 = 0.0;
         }

         this.worldObj.theProfiler.endSection();
         this.worldObj.theProfiler.startSection("rest");
         this.posX = (this.boundingBox.minX + this.boundingBox.maxX) / 2.0;
         this.posY = this.boundingBox.minY + this.yOffset - this.ySize;
         this.posZ = (this.boundingBox.minZ + this.boundingBox.maxZ) / 2.0;
         this.isCollidedHorizontally = d6 != par1 || d8 != par5;
         this.isCollidedVertically = d7 != par3;
         this.onGround = d7 != par3 && d7 < 0.0;
         this.isCollided = this.isCollidedHorizontally || this.isCollidedVertically;
         this.updateFallState(par3, this.onGround);
         if (d6 != par1) {
            this.motionX = 0.0;
         }

         if (d7 != par3) {
            this.motionY = 0.0;
         }

         if (d8 != par5) {
            this.motionZ = 0.0;
         }

         double d12 = this.posX - d3;
         double d10 = this.posY - d4;
         double d11 = this.posZ - d5;

         try {
            this.doBlockCollisions();
         } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Checking entity block collision");
            CrashReportCategory crashreportcategory = crashreport.makeCategory("Entity being checked for collision");
            this.addEntityCrashInfo(crashreportcategory);
            throw new ReportedException(crashreport);
         }

         this.worldObj.theProfiler.endSection();
      }
   }

   public List getCollidingBoundingBoxes(Entity par1Entity, AxisAlignedBB par2AxisAlignedBB) {
      ArrayList collidingBoundingBoxes = new ArrayList();
      int i = MathHelper.floor_double(par2AxisAlignedBB.minX);
      int j = MathHelper.floor_double(par2AxisAlignedBB.maxX + 1.0);
      int k = MathHelper.floor_double(par2AxisAlignedBB.minY);
      int l = MathHelper.floor_double(par2AxisAlignedBB.maxY + 1.0);
      int i1 = MathHelper.floor_double(par2AxisAlignedBB.minZ);
      int j1 = MathHelper.floor_double(par2AxisAlignedBB.maxZ + 1.0);

      for (int k1 = i; k1 < j; k1++) {
         for (int l1 = i1; l1 < j1; l1++) {
            if (this.worldObj.blockExists(k1, 64, l1)) {
               for (int i2 = k - 1; i2 < l; i2++) {
                  Block block;
                  if (k1 >= -30000000 && k1 < 30000000 && l1 >= -30000000 && l1 < 30000000) {
                     block = W_WorldFunc.getBlock(this.worldObj, k1, i2, l1);
                  } else {
                     block = W_Blocks.stone;
                  }

                  block.addCollisionBoxesToList(this.worldObj, k1, i2, l1, par2AxisAlignedBB, collidingBoundingBoxes, par1Entity);
               }
            }
         }
      }

      return collidingBoundingBoxes;
   }
}
