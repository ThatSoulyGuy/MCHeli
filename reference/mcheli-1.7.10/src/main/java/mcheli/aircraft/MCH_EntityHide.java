package mcheli.aircraft;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.List;
import mcheli.wrapper.W_Entity;
import mcheli.wrapper.W_Lib;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ReportedException;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class MCH_EntityHide extends W_Entity {
   private MCH_EntityAircraft ac;
   private Entity user;
   private int paraPosRotInc;
   private double paraX;
   private double paraY;
   private double paraZ;
   private double paraYaw;
   private double paraPitch;
   @SideOnly(Side.CLIENT)
   private double velocityX;
   @SideOnly(Side.CLIENT)
   private double velocityY;
   @SideOnly(Side.CLIENT)
   private double velocityZ;

   public MCH_EntityHide(World par1World) {
      super(par1World);
      this.setSize(1.0F, 1.0F);
      this.preventEntitySpawning = true;
      this.yOffset = this.height / 2.0F;
      this.user = null;
      this.motionX = this.motionY = this.motionZ = 0.0;
   }

   public MCH_EntityHide(World par1World, double x, double y, double z) {
      this(par1World);
      this.posX = x;
      this.posY = y;
      this.posZ = z;
   }

   @Override
   protected void entityInit() {
      super.entityInit();
      this.createRopeIndex(-1);
      this.getDataWatcher().addObject(31, new Integer(0));
   }

   public void setParent(MCH_EntityAircraft ac, Entity user, int ropeIdx) {
      this.ac = ac;
      this.setRopeIndex(ropeIdx);
      this.user = user;
   }

   protected boolean canTriggerWalking() {
      return false;
   }

   public AxisAlignedBB getCollisionBox(Entity par1Entity) {
      return par1Entity.boundingBox;
   }

   public AxisAlignedBB getBoundingBox() {
      return this.boundingBox;
   }

   public boolean canBePushed() {
      return true;
   }

   public double getMountedYOffset() {
      return this.height * 0.0 - 0.3;
   }

   @Override
   public boolean attackEntityFrom(DamageSource par1DamageSource, float par2) {
      return false;
   }

   public boolean canBeCollidedWith() {
      return !this.isDead;
   }

   protected void writeEntityToNBT(NBTTagCompound nbt) {
   }

   protected void readEntityFromNBT(NBTTagCompound nbt) {
   }

   @SideOnly(Side.CLIENT)
   public float getShadowSize() {
      return 0.0F;
   }

   @Override
   public boolean interactFirst(EntityPlayer par1EntityPlayer) {
      return false;
   }

   public void createRopeIndex(int defaultValue) {
      this.getDataWatcher().addObject(30, new Integer(defaultValue));
   }

   public int getRopeIndex() {
      return this.getDataWatcher().getWatchableObjectInt(30);
   }

   public void setRopeIndex(int value) {
      this.getDataWatcher().updateObject(30, new Integer(value));
   }

   @SideOnly(Side.CLIENT)
   public void setPositionAndRotation2(double par1, double par3, double par5, float par7, float par8, int par9) {
      this.paraPosRotInc = par9 + 10;
      this.paraX = par1;
      this.paraY = par3;
      this.paraZ = par5;
      this.paraYaw = par7;
      this.paraPitch = par8;
      this.motionX = this.velocityX;
      this.motionY = this.velocityY;
      this.motionZ = this.velocityZ;
   }

   @SideOnly(Side.CLIENT)
   public void setVelocity(double par1, double par3, double par5) {
      this.velocityX = this.motionX = par1;
      this.velocityY = this.motionY = par3;
      this.velocityZ = this.motionZ = par5;
   }

   public void setDead() {
      super.setDead();
   }

   public void onUpdate() {
      super.onUpdate();
      if (this.user != null && !this.worldObj.isRemote) {
         if (this.ac != null) {
            this.getDataWatcher().updateObject(31, new Integer(this.ac.getEntityId()));
         }

         this.user.mountEntity(this);
         this.user = null;
      }

      if (this.ac == null && this.worldObj.isRemote) {
         int id = this.getDataWatcher().getWatchableObjectInt(31);
         if (id > 0) {
            Entity entity = this.worldObj.getEntityByID(id);
            if (entity instanceof MCH_EntityAircraft) {
               this.ac = (MCH_EntityAircraft)entity;
            }
         }
      }

      this.prevPosX = this.posX;
      this.prevPosY = this.posY;
      this.prevPosZ = this.posZ;
      this.fallDistance = 0.0F;
      if (this.riddenByEntity != null) {
         this.riddenByEntity.fallDistance = 0.0F;
      }

      if (this.ac != null) {
         if (!this.ac.isRepelling()) {
            this.setDead();
         }

         int id = this.getRopeIndex();
         if (id >= 0) {
            Vec3 v = this.ac.getRopePos(id);
            this.posX = v.xCoord;
            this.posZ = v.zCoord;
         }
      }

      this.setPosition(this.posX, this.posY, this.posZ);
      if (this.worldObj.isRemote) {
         this.onUpdateClient();
      } else {
         this.onUpdateServer();
      }
   }

   public void onUpdateClient() {
      if (this.paraPosRotInc > 0) {
         double x = this.posX + (this.paraX - this.posX) / this.paraPosRotInc;
         double y = this.posY + (this.paraY - this.posY) / this.paraPosRotInc;
         double z = this.posZ + (this.paraZ - this.posZ) / this.paraPosRotInc;
         double yaw = MathHelper.wrapAngleTo180_double(this.paraYaw - this.rotationYaw);
         this.rotationYaw = (float)(this.rotationYaw + yaw / this.paraPosRotInc);
         this.rotationPitch = (float)(this.rotationPitch + (this.paraPitch - this.rotationPitch) / this.paraPosRotInc);
         this.paraPosRotInc--;
         this.setPosition(x, y, z);
         this.setRotation(this.rotationYaw, this.rotationPitch);
         if (this.riddenByEntity != null) {
            this.setRotation(this.riddenByEntity.prevRotationYaw, this.rotationPitch);
         }
      } else {
         this.setPosition(this.posX + this.motionX, this.posY + this.motionY, this.posZ + this.motionZ);
         this.motionX *= 0.99;
         this.motionY *= 0.95;
         this.motionZ *= 0.99;
      }
   }

   public void onUpdateServer() {
      this.motionY = this.motionY - (this.onGround ? 0.01 : 0.03);
      if (this.onGround) {
         this.onGroundAndDead();
      } else {
         this.moveEntity(this.motionX, this.motionY, this.motionZ);
         this.motionY *= 0.9;
         this.motionX *= 0.95;
         this.motionZ *= 0.95;
         int id = this.getRopeIndex();
         if (this.ac != null && id >= 0) {
            Vec3 v = this.ac.getRopePos(id);
            if (Math.abs(this.posY - v.yCoord) > Math.abs(this.ac.ropesLength) + 5.0F) {
               this.onGroundAndDead();
            }
         }

         if (this.riddenByEntity != null && this.riddenByEntity.isDead) {
            this.riddenByEntity = null;
            this.setDead();
         }
      }
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
            if (this.worldObj.blockExists(k1, 64, l1)) {
               for (int i2 = k - 1; i2 < l; i2++) {
                  Block block = W_WorldFunc.getBlock(this.worldObj, k1, i2, l1);
                  if (block != null) {
                     block.addCollisionBoxesToList(this.worldObj, k1, i2, l1, par2AxisAlignedBB, collidingBoundingBoxes, par1Entity);
                  }
               }
            }
         }
      }

      double d0 = 0.25;
      List list = this.worldObj.getEntitiesWithinAABBExcludingEntity(par1Entity, par2AxisAlignedBB.expand(d0, d0, d0));

      for (int j2 = 0; j2 < list.size(); j2++) {
         Entity entity = (Entity)list.get(j2);
         if (!W_Lib.isEntityLivingBase(entity) && !(entity instanceof MCH_EntitySeat) && !(entity instanceof MCH_EntityHitBox)) {
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

   public void moveEntity(double par1, double par3, double par5) {
      this.worldObj.theProfiler.startSection("move");
      this.ySize *= 0.4F;
      double d3 = this.posX;
      double d4 = this.posY;
      double d5 = this.posZ;
      double d6 = par1;
      double d7 = par3;
      double d8 = par5;
      AxisAlignedBB axisalignedbb = this.boundingBox.copy();
      List list = this.getCollidingBoundingBoxes(this, this.boundingBox.addCoord(par1, par3, par5));

      for (int i = 0; i < list.size(); i++) {
         par3 = ((AxisAlignedBB)list.get(i)).calculateYOffset(this.boundingBox, par3);
      }

      this.boundingBox.offset(0.0, par3, 0.0);
      if (!this.field_70135_K && d7 != par3) {
         par5 = 0.0;
         par3 = 0.0;
         par1 = 0.0;
      }

      boolean flag1 = this.onGround || d7 != par3 && d7 < 0.0;

      for (int j = 0; j < list.size(); j++) {
         par1 = ((AxisAlignedBB)list.get(j)).calculateXOffset(this.boundingBox, par1);
      }

      this.boundingBox.offset(par1, 0.0, 0.0);
      if (!this.field_70135_K && d6 != par1) {
         par5 = 0.0;
         par3 = 0.0;
         par1 = 0.0;
      }

      for (int var37 = 0; var37 < list.size(); var37++) {
         par5 = ((AxisAlignedBB)list.get(var37)).calculateZOffset(this.boundingBox, par5);
      }

      this.boundingBox.offset(0.0, 0.0, par5);
      if (!this.field_70135_K && d8 != par5) {
         par5 = 0.0;
         par3 = 0.0;
         par1 = 0.0;
      }

      if (this.stepHeight > 0.0F && flag1 && this.ySize < 0.05F && (d6 != par1 || d8 != par5)) {
         double d12 = par1;
         double d10 = par3;
         double d11 = par5;
         par1 = d6;
         par3 = this.stepHeight;
         par5 = d8;
         AxisAlignedBB axisalignedbb1 = this.boundingBox.copy();
         this.boundingBox.setBB(axisalignedbb);
         list = this.getCollidingBoundingBoxes(this, this.boundingBox.addCoord(d6, par3, d8));

         for (int k = 0; k < list.size(); k++) {
            par3 = ((AxisAlignedBB)list.get(k)).calculateYOffset(this.boundingBox, par3);
         }

         this.boundingBox.offset(0.0, par3, 0.0);
         if (!this.field_70135_K && d7 != par3) {
            par5 = 0.0;
            par3 = 0.0;
            par1 = 0.0;
         }

         for (int var40 = 0; var40 < list.size(); var40++) {
            par1 = ((AxisAlignedBB)list.get(var40)).calculateXOffset(this.boundingBox, par1);
         }

         this.boundingBox.offset(par1, 0.0, 0.0);
         if (!this.field_70135_K && d6 != par1) {
            par5 = 0.0;
            par3 = 0.0;
            par1 = 0.0;
         }

         for (int var41 = 0; var41 < list.size(); var41++) {
            par5 = ((AxisAlignedBB)list.get(var41)).calculateZOffset(this.boundingBox, par5);
         }

         this.boundingBox.offset(0.0, 0.0, par5);
         if (!this.field_70135_K && d8 != par5) {
            par5 = 0.0;
            par3 = 0.0;
            par1 = 0.0;
         }

         if (!this.field_70135_K && d7 != par3) {
            par5 = 0.0;
            par3 = 0.0;
            par1 = 0.0;
         } else {
            par3 = -this.stepHeight;

            for (int var42 = 0; var42 < list.size(); var42++) {
               par3 = ((AxisAlignedBB)list.get(var42)).calculateYOffset(this.boundingBox, par3);
            }

            this.boundingBox.offset(0.0, par3, 0.0);
         }

         if (d12 * d12 + d11 * d11 >= par1 * par1 + par5 * par5) {
            par1 = d12;
            par3 = d10;
            par5 = d11;
            this.boundingBox.setBB(axisalignedbb1);
         }
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
         CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Checking entity tile collision");
         CrashReportCategory crashreportcategory = crashreport.makeCategory("Entity being checked for collision");
         this.addEntityCrashInfo(crashreportcategory);
         throw new ReportedException(crashreport);
      }

      this.worldObj.theProfiler.endSection();
   }

   public void onGroundAndDead() {
      this.posY += 0.5;
      this.updateRiderPosition();
      this.setDead();
   }

   public void _updateRiderPosition() {
      if (this.riddenByEntity != null) {
         double x = -Math.sin(this.rotationYaw * Math.PI / 180.0) * 0.1;
         double z = Math.cos(this.rotationYaw * Math.PI / 180.0) * 0.1;
         this.riddenByEntity
            .setPosition(this.posX + x, this.posY + this.getMountedYOffset() + this.riddenByEntity.getYOffset(), this.posZ + z);
      }
   }
}
