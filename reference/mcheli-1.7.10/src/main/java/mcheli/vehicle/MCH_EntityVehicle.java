package mcheli.vehicle;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.MCH_Config;
import mcheli.MCH_Lib;
import mcheli.aircraft.MCH_AircraftInfo;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_PacketStatusRequest;
import mcheli.weapon.MCH_WeaponParam;
import mcheli.weapon.MCH_WeaponSet;
import mcheli.wrapper.W_Entity;
import mcheli.wrapper.W_Lib;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public class MCH_EntityVehicle extends MCH_EntityAircraft {
   private MCH_VehicleInfo vehicleInfo = null;
   public boolean isUsedPlayer;
   public float lastRiderYaw;
   public float lastRiderPitch;

   public MCH_EntityVehicle(World world) {
      super(world);
      this.currentSpeed = 0.07;
      this.preventEntitySpawning = true;
      this.setSize(2.0F, 0.7F);
      this.yOffset = this.height / 2.0F;
      this.motionX = 0.0;
      this.motionY = 0.0;
      this.motionZ = 0.0;
      this.isUsedPlayer = false;
      this.lastRiderYaw = 0.0F;
      this.lastRiderPitch = 0.0F;
      this.weapons = this.createWeapon(0);
   }

   @Override
   public String getKindName() {
      return "vehicles";
   }

   @Override
   public String getEntityType() {
      return "Vehicle";
   }

   public MCH_VehicleInfo getVehicleInfo() {
      return this.vehicleInfo;
   }

   @Override
   public void changeType(String type) {
      if (!type.isEmpty()) {
         this.vehicleInfo = MCH_VehicleInfoManager.get(type);
      }

      if (this.vehicleInfo == null) {
         MCH_Lib.Log(this, "##### MCH_EntityVehicle changeVehicleType() Vehicle info null %d, %s, %s", W_Entity.getEntityId(this), type, this.getEntityName());
         this.setDead();
      } else {
         this.setAcInfo(this.vehicleInfo);
         this.newSeats(this.getAcInfo().getNumSeatAndRack());
         this.weapons = this.createWeapon(1 + this.getSeatNum());
         this.initPartRotation(this.rotationYaw, this.rotationPitch);
      }
   }

   @Override
   public boolean canMountWithNearEmptyMinecart() {
      return MCH_Config.MountMinecartVehicle.prmBool;
   }

   @Override
   protected void entityInit() {
      super.entityInit();
   }

   @Override
   protected void writeEntityToNBT(NBTTagCompound par1NBTTagCompound) {
      super.writeEntityToNBT(par1NBTTagCompound);
   }

   @Override
   protected void readEntityFromNBT(NBTTagCompound par1NBTTagCompound) {
      super.readEntityFromNBT(par1NBTTagCompound);
      if (this.vehicleInfo == null) {
         this.vehicleInfo = MCH_VehicleInfoManager.get(this.getTypeName());
         if (this.vehicleInfo == null) {
            MCH_Lib.Log(this, "##### MCH_EntityVehicle readEntityFromNBT() Vehicle info null %d, %s", W_Entity.getEntityId(this), this.getEntityName());
            this.setDead();
         } else {
            this.setAcInfo(this.vehicleInfo);
         }
      }
   }

   @Override
   public Item getItem() {
      return this.getVehicleInfo() != null ? this.getVehicleInfo().item : null;
   }

   @Override
   public void setDead() {
      super.setDead();
   }

   @Override
   public float getSoundVolume() {
      return (float)this.getCurrentThrottle() * 2.0F;
   }

   @Override
   public float getSoundPitch() {
      return (float)(this.getCurrentThrottle() * 0.5);
   }

   @Override
   public String getDefaultSoundName() {
      return "";
   }

   @SideOnly(Side.CLIENT)
   @Override
   public void zoomCamera() {
      if (this.canZoom()) {
         float z = this.camera.getCameraZoom();
         z++;
         this.camera.setCameraZoom(z <= this.getZoomMax() + 0.01 ? z : 1.0F);
      }
   }

   public void _updateCameraRotate(float yaw, float pitch) {
      this.camera.prevRotationYaw = this.camera.rotationYaw;
      this.camera.prevRotationPitch = this.camera.rotationPitch;
      if (pitch > 89.0F) {
         pitch = 89.0F;
      }

      if (pitch < -89.0F) {
         pitch = -89.0F;
      }

      this.camera.rotationYaw = yaw;
      this.camera.rotationPitch = pitch;
   }

   @Override
   public boolean isCameraView(Entity entity) {
      return true;
   }

   @Override
   public boolean useCurrentWeapon(MCH_WeaponParam prm) {
      if (prm.user != null) {
         MCH_WeaponSet currentWs = this.getCurrentWeapon(prm.user);
         if (currentWs != null) {
            MCH_AircraftInfo.Weapon w = this.getAcInfo().getWeaponByName(currentWs.getInfo().name);
            if (w != null && w.maxYaw != 0.0F && w.minYaw != 0.0F) {
               return super.useCurrentWeapon(prm);
            }
         }
      }

      float breforeUseWeaponPitch = this.rotationPitch;
      float breforeUseWeaponYaw = this.rotationYaw;
      this.rotationPitch = prm.user.rotationPitch;
      this.rotationYaw = prm.user.rotationYaw;
      boolean result = super.useCurrentWeapon(prm);
      this.rotationPitch = breforeUseWeaponPitch;
      this.rotationYaw = breforeUseWeaponYaw;
      return result;
   }

   @Override
   public void onUpdateAircraft() {
      if (this.vehicleInfo == null) {
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
            this.getRiddenByEntity().rotationPitch = 0.0F;
            this.getRiddenByEntity().prevRotationPitch = 0.0F;
            this.initCurrentWeapon(this.getRiddenByEntity());
         }

         this.updateWeapons();
         this.onUpdate_Seats();
         this.onUpdate_Control();
         this.prevPosX = this.posX;
         this.prevPosY = this.posY;
         this.prevPosZ = this.posZ;
         if (this.isInWater()) {
            this.rotationPitch *= 0.9F;
         }

         if (this.worldObj.isRemote) {
            this.onUpdate_Client();
         } else {
            this.onUpdate_Server();
         }
      }
   }

   protected void onUpdate_Control() {
      double max_y = 1.0;
      if (this.riddenByEntity == null || this.riddenByEntity.isDead) {
         if (this.getCurrentThrottle() > 0.0) {
            this.addCurrentThrottle(-0.00125);
         } else {
            this.setCurrentThrottle(0.0);
         }
      } else if (this.getVehicleInfo().isEnableMove || this.getVehicleInfo().isEnableRot) {
         this.onUpdate_ControlOnGround();
      }

      if (this.getCurrentThrottle() < 0.0) {
         this.setCurrentThrottle(0.0);
      }

      if (this.worldObj.isRemote) {
         if (!W_Lib.isClientPlayer(this.getRiddenByEntity())) {
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

   protected void onUpdate_ControlOnGround() {
      if (!this.worldObj.isRemote) {
         boolean move = false;
         float yaw = this.rotationYaw;
         double x = 0.0;
         double z = 0.0;
         if (this.getVehicleInfo().isEnableMove) {
            if (this.throttleUp) {
               yaw = this.rotationYaw;
               x += Math.sin(yaw * Math.PI / 180.0);
               z += Math.cos(yaw * Math.PI / 180.0);
               move = true;
            }

            if (this.throttleDown) {
               yaw = this.rotationYaw - 180.0F;
               x += Math.sin(yaw * Math.PI / 180.0);
               z += Math.cos(yaw * Math.PI / 180.0);
               move = true;
            }
         }

         if (this.getVehicleInfo().isEnableMove) {
            if (this.moveLeft && !this.moveRight) {
               this.rotationYaw = (float)(this.rotationYaw - 0.5);
            }

            if (this.moveRight && !this.moveLeft) {
               this.rotationYaw = (float)(this.rotationYaw + 0.5);
            }
         }

         if (move) {
            double d = Math.sqrt(x * x + z * z);
            this.motionX -= x / d * 0.03F;
            this.motionZ += z / d * 0.03F;
         }
      }
   }

   protected void onUpdate_Particle() {
      double particlePosY = this.posY;
      boolean b = false;

      int y;
      for (y = 0; y < 5 && !b; y++) {
         for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
               int block = W_WorldFunc.getBlockId(
                  this.worldObj, (int)(this.posX + 0.5) + x, (int)(this.posY + 0.5) - y, (int)(this.posZ + 0.5) + z
               );
               if (block != 0 && !b) {
                  particlePosY = (int)(this.posY + 1.0) - y;
                  b = true;
               }
            }
         }

         for (int x = -3; b && x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
               if (W_WorldFunc.isBlockWater(
                  this.worldObj, (int)(this.posX + 0.5) + x, (int)(this.posY + 0.5) - y, (int)(this.posZ + 0.5) + z
               )) {
                  for (int i = 0; i < 7.0 * this.getCurrentThrottle(); i++) {
                     this.worldObj
                        .spawnParticle(
                           "splash",
                           this.posX + 0.5 + x + (this.rand.nextDouble() - 0.5) * 2.0,
                           particlePosY + this.rand.nextDouble(),
                           this.posZ + 0.5 + z + (this.rand.nextDouble() - 0.5) * 2.0,
                           x + (this.rand.nextDouble() - 0.5) * 2.0,
                           -0.3,
                           z + (this.rand.nextDouble() - 0.5) * 2.0
                        );
                  }
               }
            }
         }
      }

      double pn = (5 - y + 1) / 5.0;
      if (b) {
         for (int k = 0; k < (int)(this.getCurrentThrottle() * 6.0 * pn); k++) {
            float f3 = 0.25F;
            this.worldObj
               .spawnParticle(
                  "explode",
                  this.posX + (this.rand.nextDouble() - 0.5),
                  particlePosY + (this.rand.nextDouble() - 0.5),
                  this.posZ + (this.rand.nextDouble() - 0.5),
                  (this.rand.nextDouble() - 0.5) * 2.0,
                  -0.4,
                  (this.rand.nextDouble() - 0.5) * 2.0
               );
         }
      }
   }

   protected void onUpdate_Client() {
      this.updateCameraViewers();
      if (this.riddenByEntity != null && W_Lib.isClientPlayer(this.getRiddenByEntity())) {
         this.getRiddenByEntity().rotationPitch = this.getRiddenByEntity().prevRotationPitch;
      }

      if (this.aircraftPosRotInc > 0) {
         double rpinc = this.aircraftPosRotInc;
         double yaw = MathHelper.wrapAngleTo180_double(this.aircraftYaw - this.rotationYaw);
         this.rotationYaw = (float)(this.rotationYaw + yaw / rpinc);
         this.rotationPitch = (float)(this.rotationPitch + (this.aircraftPitch - this.rotationPitch) / rpinc);
         this.setPosition(
            this.posX + (this.aircraftX - this.posX) / rpinc,
            this.posY + (this.aircraftY - this.posY) / rpinc,
            this.posZ + (this.aircraftZ - this.posZ) / rpinc
         );
         this.setRotation(this.rotationYaw, this.rotationPitch);
         this.aircraftPosRotInc--;
      } else {
         this.setPosition(this.posX + this.motionX, this.posY + this.motionY, this.posZ + this.motionZ);
         if (this.onGround) {
            this.motionX *= 0.95;
            this.motionZ *= 0.95;
         }

         if (this.isInWater()) {
            this.motionX *= 0.99;
            this.motionZ *= 0.99;
         }
      }

      if (this.riddenByEntity != null) {
      }

      this.updateCamera(this.posX, this.posY, this.posZ);
   }

   private void onUpdate_Server() {
      double prevMotion = Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
      this.updateCameraViewers();
      double dp = 0.0;
      if (this.canFloatWater()) {
         dp = this.getWaterDepth();
      }

      if (dp == 0.0) {
         this.motionY = this.motionY + (!this.isInWater() ? this.getAcInfo().gravity : this.getAcInfo().gravityInWater);
      } else if (dp < 1.0) {
         this.motionY -= 1.0E-4;
         this.motionY = this.motionY + 0.007 * this.getCurrentThrottle();
      } else {
         if (this.motionY < 0.0) {
            this.motionY /= 2.0;
         }

         this.motionY += 0.007;
      }

      double motion = Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
      float speedLimit = this.getAcInfo().speed;
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

      if (this.onGround) {
         this.motionX *= 0.5;
         this.motionZ *= 0.5;
      }

      this.moveEntity(this.motionX, this.motionY, this.motionZ);
      this.motionY *= 0.95;
      this.motionX *= 0.99;
      this.motionZ *= 0.99;
      this.onUpdate_updateBlock();
      if (this.riddenByEntity != null && this.riddenByEntity.isDead) {
         this.unmountEntity();
         this.riddenByEntity = null;
      }
   }

   @Override
   public void onUpdateAngles(float partialTicks) {
   }

   public void _updateRiderPosition() {
      float yaw = this.rotationYaw;
      if (this.riddenByEntity != null) {
         this.rotationYaw = this.riddenByEntity.rotationYaw;
      }

      super.updateRiderPosition();
      this.rotationYaw = yaw;
   }

   @Override
   public boolean canSwitchFreeLook() {
      return false;
   }
}
