package mcheli.tank;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import mcheli.MCH_Config;
import mcheli.MCH_Lib;
import mcheli.MCH_MOD;
import mcheli.MCH_Math;
import mcheli.aircraft.MCH_AircraftInfo;
import mcheli.aircraft.MCH_BoundingBox;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_EntityHitBox;
import mcheli.aircraft.MCH_EntitySeat;
import mcheli.aircraft.MCH_PacketStatusRequest;
import mcheli.aircraft.MCH_Parts;
import mcheli.chain.MCH_EntityChain;
import mcheli.particles.MCH_ParticleParam;
import mcheli.particles.MCH_ParticlesUtil;
import mcheli.weapon.MCH_EntityBaseBullet;
import mcheli.weapon.MCH_WeaponSet;
import mcheli.wrapper.W_Block;
import mcheli.wrapper.W_Blocks;
import mcheli.wrapper.W_Entity;
import mcheli.wrapper.W_Lib;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.command.IEntitySelector;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class MCH_EntityTank extends MCH_EntityAircraft {
   private MCH_TankInfo tankInfo = null;
   public float soundVolume;
   public float soundVolumeTarget;
   public float rotationRotor;
   public float prevRotationRotor;
   public float addkeyRotValue;
   public final MCH_WheelManager WheelMng;

   public MCH_EntityTank(World world) {
      super(world);
      this.currentSpeed = 0.07;
      this.preventEntitySpawning = true;
      this.setSize(2.0F, 0.7F);
      this.yOffset = this.height / 2.0F;
      this.motionX = 0.0;
      this.motionY = 0.0;
      this.motionZ = 0.0;
      this.weapons = this.createWeapon(0);
      this.soundVolume = 0.0F;
      this.stepHeight = 0.6F;
      this.rotationRotor = 0.0F;
      this.prevRotationRotor = 0.0F;
      this.WheelMng = new MCH_WheelManager(this);
   }

   @Override
   public String getKindName() {
      return "tanks";
   }

   @Override
   public String getEntityType() {
      return "Vehicle";
   }

   public MCH_TankInfo getTankInfo() {
      return this.tankInfo;
   }

   @Override
   public void changeType(String type) {
      if (!type.isEmpty()) {
         this.tankInfo = MCH_TankInfoManager.get(type);
      }

      if (this.tankInfo == null) {
         MCH_Lib.Log(this, "##### MCH_EntityTank changeTankType() Tank info null %d, %s, %s", W_Entity.getEntityId(this), type, this.getEntityName());
         this.setDead();
      } else {
         this.setAcInfo(this.tankInfo);
         this.newSeats(this.getAcInfo().getNumSeatAndRack());
         this.switchFreeLookModeClient(this.getAcInfo().defaultFreelook);
         this.weapons = this.createWeapon(1 + this.getSeatNum());
         this.initPartRotation(this.getRotYaw(), this.getRotPitch());
         this.WheelMng.createWheels(this.worldObj, this.getAcInfo().wheels, Vec3.createVectorHelper(0.0, -0.35, this.getTankInfo().weightedCenterZ));
      }
   }

   @Override
   public Item getItem() {
      return this.getTankInfo() != null ? this.getTankInfo().item : null;
   }

   @Override
   public boolean canMountWithNearEmptyMinecart() {
      return MCH_Config.MountMinecartTank.prmBool;
   }

   @Override
   protected void entityInit() {
      super.entityInit();
   }

   @Override
   public float getGiveDamageRot() {
      return 91.0F;
   }

   @Override
   protected void writeEntityToNBT(NBTTagCompound par1NBTTagCompound) {
      super.writeEntityToNBT(par1NBTTagCompound);
   }

   @Override
   protected void readEntityFromNBT(NBTTagCompound par1NBTTagCompound) {
      super.readEntityFromNBT(par1NBTTagCompound);
      if (this.tankInfo == null) {
         this.tankInfo = MCH_TankInfoManager.get(this.getTypeName());
         if (this.tankInfo == null) {
            MCH_Lib.Log(this, "##### MCH_EntityTank readEntityFromNBT() Tank info null %d, %s", W_Entity.getEntityId(this), this.getEntityName());
            this.setDead();
         } else {
            this.setAcInfo(this.tankInfo);
         }
      }
   }

   @Override
   public void setDead() {
      super.setDead();
   }

   @Override
   public void onInteractFirst(EntityPlayer player) {
      this.addkeyRotValue = 0.0F;
      player.rotationYawHead = player.prevRotationYawHead = this.getLastRiderYaw();
      player.prevRotationYaw = player.rotationYaw = this.getLastRiderYaw();
      player.rotationPitch = this.getLastRiderPitch();
   }

   @Override
   public boolean canSwitchGunnerMode() {
      return !super.canSwitchGunnerMode() ? false : false;
   }

   @Override
   public void onUpdateAircraft() {
      if (this.tankInfo == null) {
         this.changeType(this.getTypeName());
         this.prevPosX = this.posX;
         this.prevPosY = this.posY;
         this.prevPosZ = this.posZ;
      } else {
         if (!this.isRequestedSyncStatus) {
            this.isRequestedSyncStatus = true;
            if (this.worldObj.isRemote) {
               MCH_PacketStatusRequest.requestStatus(this);
            }
         }

         if (this.lastRiddenByEntity == null && this.getRiddenByEntity() != null) {
            this.initCurrentWeapon(this.getRiddenByEntity());
         }

         this.updateWeapons();
         this.onUpdate_Seats();
         this.onUpdate_Control();
         this.prevRotationRotor = this.rotationRotor;
         this.rotationRotor = (float)(this.rotationRotor + this.getCurrentThrottle() * this.getAcInfo().rotorSpeed);
         if (this.rotationRotor > 360.0F) {
            this.rotationRotor -= 360.0F;
            this.prevRotationRotor -= 360.0F;
         }

         if (this.rotationRotor < 0.0F) {
            this.rotationRotor += 360.0F;
            this.prevRotationRotor += 360.0F;
         }

         this.prevPosX = this.posX;
         this.prevPosY = this.posY;
         this.prevPosZ = this.posZ;
         if (this.isDestroyed() && this.getCurrentThrottle() > 0.0) {
            if (MCH_Lib.getBlockIdY(this, 3, -2) > 0) {
               this.setCurrentThrottle(this.getCurrentThrottle() * 0.8);
            }

            if (this.isExploded()) {
               this.setCurrentThrottle(this.getCurrentThrottle() * 0.98);
            }
         }

         this.updateCameraViewers();
         if (this.worldObj.isRemote) {
            this.onUpdate_Client();
         } else {
            this.onUpdate_Server();
         }
      }
   }

   @SideOnly(Side.CLIENT)
   public boolean canRenderOnFire() {
      return this.isDestroyed() || super.canRenderOnFire();
   }

   @Override
   public void updateExtraBoundingBox() {
      if (this.worldObj.isRemote) {
         super.updateExtraBoundingBox();
      } else if (this.getCountOnUpdate() <= 1) {
         super.updateExtraBoundingBox();
         super.updateExtraBoundingBox();
      }
   }

   public double calculateXOffset(List list, AxisAlignedBB bb, double parX) {
      for (int i = 0; i < list.size(); i++) {
         parX = ((AxisAlignedBB)list.get(i)).calculateXOffset(bb, parX);
      }

      bb.offset(parX, 0.0, 0.0);
      return parX;
   }

   public double calculateYOffset(List list, AxisAlignedBB bb, double parY) {
      for (int i = 0; i < list.size(); i++) {
         parY = ((AxisAlignedBB)list.get(i)).calculateYOffset(bb, parY);
      }

      bb.offset(0.0, parY, 0.0);
      return parY;
   }

   public double calculateZOffset(List list, AxisAlignedBB bb, double parZ) {
      for (int i = 0; i < list.size(); i++) {
         parZ = ((AxisAlignedBB)list.get(i)).calculateZOffset(bb, parZ);
      }

      bb.offset(0.0, 0.0, parZ);
      return parZ;
   }

   @Override
   public void moveEntity(double parX, double parY, double parZ) {
      this.worldObj.theProfiler.startSection("move");
      this.ySize *= 0.4F;
      double nowPosX = this.posX;
      double nowPosY = this.posY;
      double nowPosZ = this.posZ;
      double mx = parX;
      double my = parY;
      double mz = parZ;
      AxisAlignedBB backUpAxisalignedBB = this.boundingBox.copy();
      List list = getCollidingBoundingBoxes(this, this.boundingBox.addCoord(parX, parY, parZ));
      parY = this.calculateYOffset(list, this.boundingBox, parY);
      boolean flag1 = this.onGround || my != parY && my < 0.0;

      for (MCH_BoundingBox ebb : this.extraBoundingBox) {
         ebb.updatePosition(this.posX, this.posY, this.posZ, this.getRotYaw(), this.getRotPitch(), this.getRotRoll());
      }

      parX = this.calculateXOffset(list, this.boundingBox, parX);
      parZ = this.calculateZOffset(list, this.boundingBox, parZ);
      if (this.stepHeight > 0.0F && flag1 && this.ySize < 0.05F && (mx != parX || mz != parZ)) {
         double bkParX = parX;
         double bkParY = parY;
         double bkParZ = parZ;
         parX = mx;
         parY = this.stepHeight;
         parZ = mz;
         AxisAlignedBB axisalignedbb1 = this.boundingBox.copy();
         this.boundingBox.setBB(backUpAxisalignedBB);
         list = getCollidingBoundingBoxes(this, this.boundingBox.addCoord(mx, parY, mz));
         parY = this.calculateYOffset(list, this.boundingBox, parY);
         parX = this.calculateXOffset(list, this.boundingBox, parX);
         parZ = this.calculateZOffset(list, this.boundingBox, parZ);
         parY = this.calculateYOffset(list, this.boundingBox, -this.stepHeight);
         if (bkParX * bkParX + bkParZ * bkParZ >= parX * parX + parZ * parZ) {
            parX = bkParX;
            parY = bkParY;
            parZ = bkParZ;
            this.boundingBox.setBB(axisalignedbb1);
         }
      }

      double prevPX = this.posX;
      double prevPZ = this.posZ;
      this.worldObj.theProfiler.endSection();
      this.worldObj.theProfiler.startSection("rest");
      double minX = this.boundingBox.minX;
      double minZ = this.boundingBox.minZ;
      double maxX = this.boundingBox.maxX;
      double maxZ = this.boundingBox.maxZ;
      this.posX = (minX + maxX) / 2.0;
      this.posY = this.boundingBox.minY + this.yOffset - this.ySize;
      this.posZ = (minZ + maxZ) / 2.0;
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

   private void rotationByKey(float partialTicks) {
      float rot = 0.2F;
      if (this.moveLeft && !this.moveRight) {
         this.addkeyRotValue -= rot * partialTicks;
      }

      if (this.moveRight && !this.moveLeft) {
         this.addkeyRotValue += rot * partialTicks;
      }
   }

   @Override
   public void onUpdateAngles(float partialTicks) {
      if (!this.isDestroyed()) {
         if (this.isGunnerMode) {
            this.setRotPitch(this.getRotPitch() * 0.95F);
            this.setRotYaw(this.getRotYaw() + this.getAcInfo().autoPilotRot * 0.2F);
            if (MathHelper.abs(this.getRotRoll()) > 20.0F) {
               this.setRotRoll(this.getRotRoll() * 0.95F);
            }
         }

         this.updateRecoil(partialTicks);
         this.setRotPitch(this.getRotPitch() + (this.WheelMng.targetPitch - this.getRotPitch()) * partialTicks);
         this.setRotRoll(this.getRotRoll() + (this.WheelMng.targetRoll - this.getRotRoll()) * partialTicks);
         boolean isFly = MCH_Lib.getBlockIdY(this, 3, -3) == 0;
         if (!isFly || this.getAcInfo().isFloat && this.getWaterDepth() > 0.0) {
            float gmy = 1.0F;
            if (!isFly) {
               gmy = this.getAcInfo().mobilityYawOnGround;
               if (!this.getAcInfo().canRotOnGround) {
                  Block block = MCH_Lib.getBlockY(this, 3, -2, false);
                  if (!W_Block.isEqual(block, W_Block.getWater()) && !W_Block.isEqual(block, W_Blocks.air)) {
                     gmy = 0.0F;
                  }
               }
            }

            float pivotTurnThrottle = this.getAcInfo().pivotTurnThrottle;
            double dx = this.posX - this.prevPosX;
            double dz = this.posZ - this.prevPosZ;
            double dist = dx * dx + dz * dz;
            if (pivotTurnThrottle <= 0.0F
               || this.getCurrentThrottle() >= pivotTurnThrottle
               || this.throttleBack >= pivotTurnThrottle / 10.0F
               || dist > this.throttleBack * 0.01) {
               float sf = (float)Math.sqrt(dist <= 1.0 ? dist : 1.0);
               if (pivotTurnThrottle <= 0.0F) {
                  sf = 1.0F;
               }

               float flag = !this.throttleUp && this.throttleDown && this.getCurrentThrottle() < pivotTurnThrottle + 0.05 ? -1.0F : 1.0F;
               if (this.moveLeft && !this.moveRight) {
                  this.setRotYaw(this.getRotYaw() - 0.6F * gmy * partialTicks * flag * sf);
               }

               if (this.moveRight && !this.moveLeft) {
                  this.setRotYaw(this.getRotYaw() + 0.6F * gmy * partialTicks * flag * sf);
               }
            }
         }

         this.addkeyRotValue = (float)(this.addkeyRotValue * (1.0 - 0.1F * partialTicks));
      }
   }

   protected void onUpdate_Control() {
      if (this.isGunnerMode && !this.canUseFuel()) {
         this.switchGunnerMode(false);
      }

      this.throttleBack = (float)(this.throttleBack * 0.8);
      if (this.getBrake()) {
         this.throttleBack = (float)(this.throttleBack * 0.5);
         if (this.getCurrentThrottle() > 0.0) {
            this.addCurrentThrottle(-0.02 * this.getAcInfo().throttleUpDown);
         } else {
            this.setCurrentThrottle(0.0);
         }
      }

      if (this.getRiddenByEntity() != null && !this.getRiddenByEntity().isDead && this.isCanopyClose() && this.canUseFuel() && !this.isDestroyed()) {
         this.onUpdate_ControlSub();
      } else if (this.isTargetDrone() && this.canUseFuel() && !this.isDestroyed()) {
         this.throttleUp = true;
         this.onUpdate_ControlSub();
      } else if (this.getCurrentThrottle() > 0.0) {
         this.addCurrentThrottle(-0.0025 * this.getAcInfo().throttleUpDown);
      } else {
         this.setCurrentThrottle(0.0);
      }

      if (this.getCurrentThrottle() < 0.0) {
         this.setCurrentThrottle(0.0);
      }

      if (this.worldObj.isRemote) {
         if (!W_Lib.isClientPlayer(this.getRiddenByEntity()) || this.getCountOnUpdate() % 200 == 0) {
            double ct = this.getThrottle();
            if (this.getCurrentThrottle() > ct) {
               this.addCurrentThrottle(-0.005);
            }

            if (this.getCurrentThrottle() < ct) {
               this.addCurrentThrottle(0.005);
            }
         }
      } else {
         this.setThrottle(this.getCurrentThrottle());
      }
   }

   protected void onUpdate_ControlSub() {
      if (!this.isGunnerMode) {
         float throttleUpDown = this.getAcInfo().throttleUpDown;
         if (this.throttleUp) {
            float f = throttleUpDown;
            if (this.getRidingEntity() != null) {
               double mx = this.getRidingEntity().motionX;
               double mz = this.getRidingEntity().motionZ;
               f *= MathHelper.sqrt_double(mx * mx + mz * mz) * this.getAcInfo().throttleUpDownOnEntity;
            }

            if (this.getAcInfo().enableBack && this.throttleBack > 0.0F) {
               this.throttleBack = (float)(this.throttleBack - 0.01 * f);
            } else {
               this.throttleBack = 0.0F;
               if (this.getCurrentThrottle() < 1.0) {
                  this.addCurrentThrottle(0.01 * f);
               } else {
                  this.setCurrentThrottle(1.0);
               }
            }
         } else if (this.throttleDown) {
            if (this.getCurrentThrottle() > 0.0) {
               this.addCurrentThrottle(-0.01 * throttleUpDown);
            } else {
               this.setCurrentThrottle(0.0);
               if (this.getAcInfo().enableBack) {
                  this.throttleBack = (float)(this.throttleBack + 0.0025 * throttleUpDown);
                  if (this.throttleBack > 0.6F) {
                     this.throttleBack = 0.6F;
                  }
               }
            }
         } else if (this.cs_tankAutoThrottleDown && this.getCurrentThrottle() > 0.0) {
            this.addCurrentThrottle(-0.005 * throttleUpDown);
            if (this.getCurrentThrottle() <= 0.0) {
               this.setCurrentThrottle(0.0);
            }
         }
      }
   }

   protected void onUpdate_Particle2() {
      if (this.worldObj.isRemote) {
         if (!(this.getHP() >= this.getMaxHP() * 0.5)) {
            if (this.getTankInfo() != null) {
               int bbNum = this.getTankInfo().extraBoundingBox.size();
               if (bbNum < 0) {
                  bbNum = 0;
               }

               if (this.isFirstDamageSmoke || this.prevDamageSmokePos.length != bbNum + 1) {
                  this.prevDamageSmokePos = new Vec3[bbNum + 1];
               }

               float yaw = this.getRotYaw();
               float pitch = this.getRotPitch();
               float roll = this.getRotRoll();

               for (int ri = 0; ri < bbNum; ri++) {
                  if (this.getHP() >= this.getMaxHP() * 0.2 && this.getMaxHP() > 0) {
                     int d = (int)(((double)this.getHP() / this.getMaxHP() - 0.2) / 0.3 * 15.0);
                     if (d > 0 && this.rand.nextInt(d) > 0) {
                        continue;
                     }
                  }

                  MCH_BoundingBox bb = this.getTankInfo().extraBoundingBox.get(ri);
                  Vec3 pos = this.getTransformedPosition(bb.offsetX, bb.offsetY, bb.offsetZ);
                  double x = pos.xCoord;
                  double y = pos.yCoord;
                  double z = pos.zCoord;
                  this.onUpdate_Particle2SpawnSmoke(ri, x, y, z, 1.0F);
               }

               boolean b = true;
               if (this.getHP() >= this.getMaxHP() * 0.2 && this.getMaxHP() > 0) {
                  int d = (int)(((double)this.getHP() / this.getMaxHP() - 0.2) / 0.3 * 15.0);
                  if (d > 0 && this.rand.nextInt(d) > 0) {
                     b = false;
                  }
               }

               if (b) {
                  double px = this.posX;
                  double py = this.posY;
                  double pz = this.posZ;
                  if (this.getSeatInfo(0) != null && this.getSeatInfo(0).pos != null) {
                     Vec3 pos = MCH_Lib.RotVec3(0.0, this.getSeatInfo(0).pos.yCoord, -2.0, -yaw, -pitch, -roll);
                     px += pos.xCoord;
                     py += pos.yCoord;
                     pz += pos.zCoord;
                  }

                  this.onUpdate_Particle2SpawnSmoke(bbNum, px, py, pz, bbNum == 0 ? 2.0F : 1.0F);
               }

               this.isFirstDamageSmoke = false;
            }
         }
      }
   }

   public void onUpdate_Particle2SpawnSmoke(int ri, double x, double y, double z, float size) {
      if (this.isFirstDamageSmoke || this.prevDamageSmokePos[ri] == null) {
         this.prevDamageSmokePos[ri] = Vec3.createVectorHelper(x, y, z);
      }

      Vec3 prev = this.prevDamageSmokePos[ri];
      double dx = x - prev.xCoord;
      double dy = y - prev.yCoord;
      double dz = z - prev.zCoord;
      int num = 1;

      for (int i = 0; i < num; i++) {
         float c = 0.2F + this.rand.nextFloat() * 0.3F;
         MCH_ParticleParam prm = new MCH_ParticleParam(this.worldObj, "smoke", x, y, z);
         prm.motionX = size * (this.rand.nextDouble() - 0.5) * 0.3;
         prm.motionY = size * this.rand.nextDouble() * 0.1;
         prm.motionZ = size * (this.rand.nextDouble() - 0.5) * 0.3;
         prm.size = size * (this.rand.nextInt(5) + 5.0F) * 1.0F;
         prm.setColor(0.7F + this.rand.nextFloat() * 0.1F, c, c, c);
         MCH_ParticlesUtil.spawnParticle(prm);
      }

      this.prevDamageSmokePos[ri].xCoord = x;
      this.prevDamageSmokePos[ri].yCoord = y;
      this.prevDamageSmokePos[ri].zCoord = z;
   }

   public void onUpdate_Particle2SpawnSmode(int ri, double x, double y, double z, float size) {
      if (this.isFirstDamageSmoke) {
         this.prevDamageSmokePos[ri] = Vec3.createVectorHelper(x, y, z);
      }

      Vec3 prev = this.prevDamageSmokePos[ri];
      double dx = x - prev.xCoord;
      double dy = y - prev.yCoord;
      double dz = z - prev.zCoord;
      int num = (int)(MathHelper.sqrt_double(dx * dx + dy * dy + dz * dz) / 0.3) + 1;

      for (int i = 0; i < num; i++) {
         float c = 0.2F + this.rand.nextFloat() * 0.3F;
         MCH_ParticleParam prm = new MCH_ParticleParam(this.worldObj, "smoke", x, y, z);
         prm.motionX = size * (this.rand.nextDouble() - 0.5) * 0.3;
         prm.motionY = size * this.rand.nextDouble() * 0.1;
         prm.motionZ = size * (this.rand.nextDouble() - 0.5) * 0.3;
         prm.size = size * (this.rand.nextInt(5) + 5.0F) * 1.0F;
         prm.setColor(0.7F + this.rand.nextFloat() * 0.1F, c, c, c);
         MCH_ParticlesUtil.spawnParticle(prm);
      }

      this.prevDamageSmokePos[ri].xCoord = x;
      this.prevDamageSmokePos[ri].yCoord = y;
      this.prevDamageSmokePos[ri].zCoord = z;
   }

   public void onUpdate_ParticleLandingGear() {
      this.WheelMng.particleLandingGear();
   }

   private void onUpdate_ParticleSplash() {
      if (this.getAcInfo() != null) {
         if (this.worldObj.isRemote) {
            double mx = this.posX - this.prevPosX;
            double mz = this.posZ - this.prevPosZ;
            double dist = mx * mx + mz * mz;
            if (dist > 1.0) {
               dist = 1.0;
            }

            for (MCH_AircraftInfo.ParticleSplash p : this.getAcInfo().particleSplashs) {
               for (int i = 0; i < p.num; i++) {
                  if (dist > 0.03 + this.rand.nextFloat() * 0.1) {
                     this.setParticleSplash(p.pos, -mx * p.acceleration, p.motionY, -mz * p.acceleration, p.gravity, p.size * (0.5 + dist * 0.5), p.age);
                  }
               }
            }
         }
      }
   }

   private void setParticleSplash(Vec3 pos, double mx, double my, double mz, float gravity, double size, int age) {
      Vec3 v = this.getTransformedPosition(pos);
      v = v.addVector(this.rand.nextDouble() - 0.5, (this.rand.nextDouble() - 0.5) * 0.5, this.rand.nextDouble() - 0.5);
      int x = (int)(v.xCoord + 0.5);
      int y = (int)(v.yCoord + 0.0);
      int z = (int)(v.zCoord + 0.5);
      if (W_WorldFunc.isBlockWater(this.worldObj, x, y, z)) {
         float c = this.rand.nextFloat() * 0.3F + 0.7F;
         MCH_ParticleParam prm = new MCH_ParticleParam(this.worldObj, "smoke", v.xCoord, v.yCoord, v.zCoord);
         prm.motionX = mx + (this.rand.nextFloat() - 0.5) * 0.7;
         prm.motionY = my;
         prm.motionZ = mz + (this.rand.nextFloat() - 0.5) * 0.7;
         prm.size = (float)size * (this.rand.nextFloat() * 0.2F + 0.8F);
         prm.setColor(0.9F, c, c, c);
         prm.age = age + (int)(this.rand.nextFloat() * 0.5 * age);
         prm.gravity = gravity;
         MCH_ParticlesUtil.spawnParticle(prm);
      }
   }

   @Override
   public void destroyAircraft() {
      super.destroyAircraft();
      this.rotDestroyedPitch = 0.0F;
      this.rotDestroyedRoll = 0.0F;
      this.rotDestroyedYaw = 0.0F;
   }

   @Override
   public int getClientPositionDelayCorrection() {
      return this.getTankInfo() == null ? 7 : (this.getTankInfo().weightType == 1 ? 2 : 7);
   }

   protected void onUpdate_Client() {
      if (this.getRiddenByEntity() != null && W_Lib.isClientPlayer(this.getRiddenByEntity())) {
         this.getRiddenByEntity().rotationPitch = this.getRiddenByEntity().prevRotationPitch;
      }

      if (this.aircraftPosRotInc > 0) {
         this.applyServerPositionAndRotation();
      } else {
         this.setPosition(this.posX + this.motionX, this.posY + this.motionY, this.posZ + this.motionZ);
         if (!this.isDestroyed() && (this.onGround || MCH_Lib.getBlockIdY(this, 1, -2) > 0)) {
            this.motionX *= 0.95;
            this.motionZ *= 0.95;
            this.applyOnGroundPitch(0.95F);
         }

         if (this.isInWater()) {
            this.motionX *= 0.99;
            this.motionZ *= 0.99;
         }
      }

      this.updateWheels();
      this.onUpdate_Particle2();
      this.updateSound();
      if (this.worldObj.isRemote) {
         this.onUpdate_ParticleLandingGear();
         this.onUpdate_ParticleSplash();
         this.onUpdate_ParticleSandCloud(true);
      }

      this.updateCamera(this.posX, this.posY, this.posZ);
   }

   @Override
   public void applyOnGroundPitch(float factor) {
   }

   private void onUpdate_Server() {
      Entity rdnEnt = this.getRiddenByEntity();
      double prevMotion = Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
      double dp = 0.0;
      if (this.canFloatWater()) {
         dp = this.getWaterDepth();
      }

      boolean levelOff = this.isGunnerMode;
      if (dp == 0.0) {
         if (!levelOff) {
            this.motionY = this.motionY + (0.04 + (!this.isInWater() ? this.getAcInfo().gravity : this.getAcInfo().gravityInWater));
            this.motionY = this.motionY + -0.047 * (1.0 - this.getCurrentThrottle());
         } else {
            this.motionY *= 0.8;
         }
      } else {
         if (MathHelper.abs(this.getRotRoll()) < 40.0F) {
         }

         if (dp < 1.0) {
            this.motionY -= 1.0E-4;
            this.motionY = this.motionY + 0.007 * this.getCurrentThrottle();
         } else {
            if (this.motionY < 0.0) {
               this.motionY /= 2.0;
            }

            this.motionY += 0.007;
         }
      }

      float throttle = (float)(this.getCurrentThrottle() / 10.0);
      Vec3 v = MCH_Lib.Rot2Vec3(this.getRotYaw(), this.getRotPitch() - 10.0F);
      if (!levelOff) {
         this.motionY = this.motionY + v.yCoord * throttle / 8.0;
      }

      boolean canMove = true;
      if (!this.getAcInfo().canMoveOnGround) {
         Block block = MCH_Lib.getBlockY(this, 3, -2, false);
         if (!W_Block.isEqual(block, W_Block.getWater()) && !W_Block.isEqual(block, W_Blocks.air)) {
            canMove = false;
         }
      }

      if (canMove) {
         if (this.getAcInfo().enableBack && this.throttleBack > 0.0F) {
            this.motionX = this.motionX - v.xCoord * this.throttleBack;
            this.motionZ = this.motionZ - v.zCoord * this.throttleBack;
         } else {
            this.motionX = this.motionX + v.xCoord * throttle;
            this.motionZ = this.motionZ + v.zCoord * throttle;
         }
      }

      double motion = Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
      float speedLimit = this.getMaxSpeed();
      if (motion > speedLimit) {
         this.motionX *= speedLimit / motion;
         this.motionZ *= speedLimit / motion;
         motion = speedLimit;
      }

      if (motion > prevMotion && this.currentSpeed < speedLimit) {
         this.currentSpeed = this.currentSpeed + (speedLimit - this.currentSpeed) / 35.0;
         if (this.currentSpeed > speedLimit) {
            this.currentSpeed = speedLimit;
         }
      } else {
         this.currentSpeed = this.currentSpeed - (this.currentSpeed - 0.07) / 35.0;
         if (this.currentSpeed < 0.07) {
            this.currentSpeed = 0.07;
         }
      }

      if (this.onGround || MCH_Lib.getBlockIdY(this, 1, -2) > 0) {
         this.motionX = this.motionX * this.getAcInfo().motionFactor;
         this.motionZ = this.motionZ * this.getAcInfo().motionFactor;
         if (MathHelper.abs(this.getRotPitch()) < 40.0F) {
            this.applyOnGroundPitch(0.8F);
         }
      }

      this.updateWheels();
      this.moveEntity(this.motionX, this.motionY, this.motionZ);
      this.motionY *= 0.95;
      this.motionX = this.motionX * this.getAcInfo().motionFactor;
      this.motionZ = this.motionZ * this.getAcInfo().motionFactor;
      this.setRotation(this.getRotYaw(), this.getRotPitch());
      this.onUpdate_updateBlock();
      this.updateCollisionBox();
      if (this.getRiddenByEntity() != null && this.getRiddenByEntity().isDead) {
         this.unmountEntity();
         this.riddenByEntity = null;
      }
   }

   private void collisionEntity(AxisAlignedBB bb) {
      if (bb != null) {
         double speed = Math.sqrt(this.motionX * this.motionX + this.motionY * this.motionY + this.motionZ * this.motionZ);
         if (!(speed <= 0.05)) {
            Entity rider = this.getRiddenByEntity();
            float damage = (float)(speed * 15.0);
            final MCH_EntityAircraft rideAc = this.ridingEntity instanceof MCH_EntityAircraft
               ? (MCH_EntityAircraft)this.ridingEntity
               : (this.ridingEntity instanceof MCH_EntitySeat ? ((MCH_EntitySeat)this.ridingEntity).getParent() : null);
            List list = this.worldObj
               .getEntitiesWithinAABBExcludingEntity(
                  this,
                  bb.expand(0.3, 0.3, 0.3),
                  new IEntitySelector() {
                     public boolean isEntityApplicable(Entity e) {
                        if (e != rideAc
                           && !(e instanceof EntityItem)
                           && !(e instanceof EntityXPOrb)
                           && !(e instanceof MCH_EntityBaseBullet)
                           && !(e instanceof MCH_EntityChain)
                           && !(e instanceof MCH_EntitySeat)) {
                           if (e instanceof MCH_EntityTank) {
                              MCH_EntityTank tank = (MCH_EntityTank)e;
                              if (tank.getTankInfo() != null && tank.getTankInfo().weightType == 2) {
                                 return MCH_Config.Collision_EntityTankDamage.prmBool;
                              }
                           }

                           return MCH_Config.Collision_EntityDamage.prmBool;
                        } else {
                           return false;
                        }
                     }
                  }
               );

            for (int i = 0; i < list.size(); i++) {
               Entity e = (Entity)list.get(i);
               if (this.shouldCollisionDamage(e)) {
                  double dx = e.posX - this.posX;
                  double dz = e.posZ - this.posZ;
                  double dist = Math.sqrt(dx * dx + dz * dz);
                  if (dist > 5.0) {
                     dist = 5.0;
                  }

                  damage = (float)(damage + (5.0 - dist));
                  DamageSource ds;
                  if (rider instanceof EntityLivingBase) {
                     ds = DamageSource.causeMobDamage((EntityLivingBase)rider);
                  } else {
                     ds = DamageSource.generic;
                  }

                  MCH_Lib.applyEntityHurtResistantTimeConfig(e);
                  e.attackEntityFrom(ds, damage);
                  if (e instanceof MCH_EntityAircraft) {
                     e.motionX = e.motionX + this.motionX * 0.05;
                     e.motionZ = e.motionZ + this.motionZ * 0.05;
                  } else if (e instanceof EntityArrow) {
                     e.setDead();
                  } else {
                     e.motionX = e.motionX + this.motionX * 1.5;
                     e.motionZ = e.motionZ + this.motionZ * 1.5;
                  }

                  if (this.getTankInfo().weightType != 2 && (e.width >= 1.0F || e.height >= 1.5)) {
                     if (e instanceof EntityLivingBase) {
                        ds = DamageSource.causeMobDamage((EntityLivingBase)e);
                     } else {
                        ds = DamageSource.generic;
                     }

                     this.attackEntityFrom(ds, damage / 3.0F);
                  }

                  MCH_Lib.DbgLog(this.worldObj, "MCH_EntityTank.collisionEntity damage=%.1f %s", damage, e.toString());
               }
            }
         }
      }
   }

   private boolean shouldCollisionDamage(Entity e) {
      if (this.getSeatIdByEntity(e) >= 0) {
         return false;
      }

      if (this.noCollisionEntities.containsKey(e)) {
         return false;
      }

      if (e instanceof MCH_EntityHitBox && ((MCH_EntityHitBox)e).parent != null) {
         MCH_EntityAircraft ac = ((MCH_EntityHitBox)e).parent;
         if (this.noCollisionEntities.containsKey(ac)) {
            return false;
         }
      }

      return e.ridingEntity instanceof MCH_EntityAircraft && this.noCollisionEntities.containsKey(e.ridingEntity)
         ? false
         : !(e.ridingEntity instanceof MCH_EntitySeat)
            || ((MCH_EntitySeat)e.ridingEntity).getParent() == null
            || !this.noCollisionEntities.containsKey(((MCH_EntitySeat)e.ridingEntity).getParent());
   }

   public void updateCollisionBox() {
      if (this.getAcInfo() != null) {
         this.WheelMng.updateBlock();

         for (MCH_BoundingBox bb : this.extraBoundingBox) {
            if (this.rand.nextInt(3) == 0) {
               if (MCH_Config.Collision_DestroyBlock.prmBool) {
                  Vec3 v = this.getTransformedPosition(bb.offsetX, bb.offsetY, bb.offsetZ);
                  this.destoryBlockRange(v, bb.width, bb.height);
               }

               this.collisionEntity(bb.boundingBox);
            }
         }

         if (MCH_Config.Collision_DestroyBlock.prmBool) {
            this.destoryBlockRange(this.getTransformedPosition(0.0, 0.0, 0.0), this.width * 1.5, this.height * 2.0F);
         }

         this.collisionEntity(this.getBoundingBox());
      }
   }

   public void destoryBlockRange(Vec3 v, double w, double h) {
      if (this.getAcInfo() != null) {
         List<Block> destroyBlocks = MCH_Config.getBreakableBlockListFromType(this.getTankInfo().weightType);
         List<Block> noDestroyBlocks = MCH_Config.getNoBreakableBlockListFromType(this.getTankInfo().weightType);
         List<Material> destroyMaterials = MCH_Config.getBreakableMaterialListFromType(this.getTankInfo().weightType);
         int ws = (int)(w + 2.0) / 2;
         int hs = (int)(h + 2.0) / 2;

         for (int x = -ws; x <= ws; x++) {
            for (int z = -ws; z <= ws; z++) {
               for (int y = -hs; y <= hs + 1; y++) {
                  int bx = (int)(v.xCoord + x - 0.5);
                  int by = (int)(v.yCoord + y - 1.0);
                  int bz = (int)(v.zCoord + z - 0.5);
                  Block block = by >= 0 && by < 256 ? this.worldObj.getBlock(bx, by, bz) : Blocks.air;
                  Material mat = block.getMaterial();
                  if (!Block.isEqualTo(block, Blocks.air)) {
                     for (Block c : noDestroyBlocks) {
                        if (Block.isEqualTo(block, c)) {
                           block = null;
                           break;
                        }
                     }

                     if (block == null) {
                        break;
                     }

                     for (Block c : destroyBlocks) {
                        if (Block.isEqualTo(block, c)) {
                           this.destroyBlock(bx, by, bz);
                           mat = null;
                           break;
                        }
                     }

                     if (mat == null) {
                        break;
                     }

                     for (Material m : destroyMaterials) {
                        if (block.getMaterial() == m) {
                           this.destroyBlock(bx, by, bz);
                           break;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public void destroyBlock(int bx, int by, int bz) {
      if (this.rand.nextInt(8) == 0) {
         W_WorldFunc.destroyBlock(this.worldObj, bx, by, bz, true);
      } else {
         this.worldObj.setBlockToAir(bx, by, bz);
      }
   }

   private void updateWheels() {
      this.WheelMng.move(this.motionX, this.motionY, this.motionZ);
   }

   public float getMaxSpeed() {
      return this.getTankInfo().speed + 0.0F;
   }

   @Override
   public void setAngles(Entity player, boolean fixRot, float fixYaw, float fixPitch, float deltaX, float deltaY, float x, float y, float partialTicks) {
      if (partialTicks < 0.03F) {
         partialTicks = 0.4F;
      }

      if (partialTicks > 0.9F) {
         partialTicks = 0.6F;
      }

      this.lowPassPartialTicks.put(partialTicks);
      partialTicks = this.lowPassPartialTicks.getAvg();
      float ac_pitch = this.getRotPitch();
      float ac_yaw = this.getRotYaw();
      float ac_roll = this.getRotRoll();
      if (this.isFreeLookMode()) {
         y = 0.0F;
         x = 0.0F;
      }

      float yaw = 0.0F;
      float pitch = 0.0F;
      float roll = 0.0F;
      MCH_Math.FMatrix m_add = MCH_Math.newMatrix();
      MCH_Math.MatTurnZ(m_add, roll / 180.0F * (float) Math.PI);
      MCH_Math.MatTurnX(m_add, pitch / 180.0F * (float) Math.PI);
      MCH_Math.MatTurnY(m_add, yaw / 180.0F * (float) Math.PI);
      MCH_Math.MatTurnZ(m_add, (float)(this.getRotRoll() / 180.0F * Math.PI));
      MCH_Math.MatTurnX(m_add, (float)(this.getRotPitch() / 180.0F * Math.PI));
      MCH_Math.MatTurnY(m_add, (float)(this.getRotYaw() / 180.0F * Math.PI));
      MCH_Math.FVector3D v = MCH_Math.MatrixToEuler(m_add);
      v.x = MCH_Lib.RNG(v.x, -90.0F, 90.0F);
      v.z = MCH_Lib.RNG(v.z, -90.0F, 90.0F);
      if (v.z > 180.0F) {
         v.z -= 360.0F;
      }

      if (v.z < -180.0F) {
         v.z += 360.0F;
      }

      this.setRotYaw(v.y);
      this.setRotPitch(v.x);
      this.setRotRoll(v.z);
      this.onUpdateAngles(partialTicks);
      if (this.getAcInfo().limitRotation) {
         v.x = MCH_Lib.RNG(this.getRotPitch(), -90.0F, 90.0F);
         v.z = MCH_Lib.RNG(this.getRotRoll(), -90.0F, 90.0F);
         this.setRotPitch(v.x);
         this.setRotRoll(v.z);
      }

      float RV = 180.0F;
      if (MathHelper.abs(this.getRotPitch()) > 90.0F) {
         MCH_Lib.DbgLog(true, "MCH_EntityAircraft.setAngles Error:Pitch=%.1f", this.getRotPitch());
         this.setRotPitch(0.0F);
      }

      if (this.getRotRoll() > 180.0F) {
         this.setRotRoll(this.getRotRoll() - 360.0F);
      }

      if (this.getRotRoll() < -180.0F) {
         this.setRotRoll(this.getRotRoll() + 360.0F);
      }

      this.prevRotationRoll = this.getRotRoll();
      this.prevRotationPitch = this.getRotPitch();
      if (this.getRidingEntity() == null) {
         this.prevRotationYaw = this.getRotYaw();
      }

      float deltaLimit = this.getAcInfo().cameraRotationSpeed * partialTicks;
      MCH_WeaponSet ws = this.getCurrentWeapon(player);
      deltaLimit *= ws != null && ws.getInfo() != null ? ws.getInfo().cameraRotationSpeedPitch : 1.0F;
      if (deltaX > deltaLimit) {
         deltaX = deltaLimit;
      }

      if (deltaX < -deltaLimit) {
         deltaX = -deltaLimit;
      }

      if (deltaY > deltaLimit) {
         deltaY = deltaLimit;
      }

      if (deltaY < -deltaLimit) {
         deltaY = -deltaLimit;
      }

      if (!this.isOverridePlayerYaw() && !fixRot) {
         player.setAngles(deltaX, 0.0F);
      } else {
         if (this.getRidingEntity() == null) {
            player.prevRotationYaw = this.getRotYaw() + fixYaw;
         } else {
            if (this.getRotYaw() - player.rotationYaw > 180.0F) {
               player.prevRotationYaw += 360.0F;
            }

            if (this.getRotYaw() - player.rotationYaw < -180.0F) {
               player.prevRotationYaw -= 360.0F;
            }
         }

         player.rotationYaw = this.getRotYaw() + fixYaw;
      }

      if (!this.isOverridePlayerPitch() && !fixRot) {
         player.setAngles(0.0F, deltaY);
      } else {
         player.prevRotationPitch = this.getRotPitch() + fixPitch;
         player.rotationPitch = this.getRotPitch() + fixPitch;
      }

      float playerYaw = MathHelper.wrapAngleTo180_float(this.getRotYaw() - player.rotationYaw);
      float playerPitch = this.getRotPitch() * MathHelper.cos((float)(playerYaw * Math.PI / 180.0))
         + -this.getRotRoll() * MathHelper.sin((float)(playerYaw * Math.PI / 180.0));
      if (MCH_MOD.proxy.isFirstPerson()) {
         player.rotationPitch = MCH_Lib.RNG(
            player.rotationPitch, playerPitch + this.getAcInfo().minRotationPitch, playerPitch + this.getAcInfo().maxRotationPitch
         );
         player.rotationPitch = MCH_Lib.RNG(player.rotationPitch, -90.0F, 90.0F);
      }

      player.prevRotationPitch = player.rotationPitch;
      if (this.getRidingEntity() == null && ac_yaw != this.getRotYaw() || ac_pitch != this.getRotPitch() || ac_roll != this.getRotRoll()) {
         this.aircraftRotChanged = true;
      }
   }

   @Override
   public float getSoundVolume() {
      return this.getAcInfo() != null && this.getAcInfo().throttleUpDown <= 0.0F ? 0.0F : this.soundVolume * 0.7F;
   }

   public void updateSound() {
      float target = (float)this.getCurrentThrottle();
      if (this.getRiddenByEntity() != null && (this.partCanopy == null || this.getCanopyRotation() < 1.0F)) {
         target += 0.1F;
      }

      if (!this.moveLeft && !this.moveRight && !this.throttleDown) {
         this.soundVolumeTarget *= 0.8F;
      } else {
         this.soundVolumeTarget += 0.1F;
         if (this.soundVolumeTarget > 0.75F) {
            this.soundVolumeTarget = 0.75F;
         }
      }

      if (target < this.soundVolumeTarget) {
         target = this.soundVolumeTarget;
      }

      if (this.soundVolume < target) {
         this.soundVolume += 0.02F;
         if (this.soundVolume >= target) {
            this.soundVolume = target;
         }
      } else if (this.soundVolume > target) {
         this.soundVolume -= 0.02F;
         if (this.soundVolume <= target) {
            this.soundVolume = target;
         }
      }
   }

   @Override
   public float getSoundPitch() {
      float target1 = (float)(0.5 + this.getCurrentThrottle() * 0.5);
      float target2 = (float)(0.5 + this.soundVolumeTarget * 0.5);
      return target1 > target2 ? target1 : target2;
   }

   @Override
   public String getDefaultSoundName() {
      return "prop";
   }

   @Override
   public boolean hasBrake() {
      return true;
   }

   @Override
   public void updateParts(int stat) {
      super.updateParts(stat);
      if (!this.isDestroyed()) {
         MCH_Parts[] parts = new MCH_Parts[0];

         for (MCH_Parts p : parts) {
            if (p != null) {
               p.updateStatusClient(stat);
               p.update();
            }
         }
      }
   }

   @Override
   public float getUnfoldLandingGearThrottle() {
      return 0.7F;
   }
}
