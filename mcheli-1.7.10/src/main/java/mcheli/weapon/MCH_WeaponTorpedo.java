package mcheli.weapon;

import mcheli.MCH_Lib;
import mcheli.wrapper.W_MovingObjectPosition;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class MCH_WeaponTorpedo extends MCH_WeaponBase {
   public MCH_WeaponTorpedo(World w, Vec3 v, float yaw, float pitch, String nm, MCH_WeaponInfo wi) {
      super(w, v, yaw, pitch, nm, wi);
      this.acceleration = 0.5F;
      this.explosionPower = 8;
      this.power = 35;
      this.interval = -100;
      if (w.isRemote) {
         this.interval -= 10;
      }
   }

   @Override
   public boolean shot(MCH_WeaponParam prm) {
      if (this.getInfo() != null) {
         return this.getInfo().isGuidedTorpedo ? this.shotGuided(prm) : this.shotNoGuided(prm);
      } else {
         return false;
      }
   }

   protected boolean shotNoGuided(MCH_WeaponParam prm) {
      if (this.worldObj.isRemote) {
         return true;
      }

      float yaw = prm.rotYaw;
      float pitch = prm.rotPitch;
      double mx = -MathHelper.sin(yaw / 180.0F * (float) Math.PI) * MathHelper.cos(pitch / 180.0F * (float) Math.PI);
      double mz = MathHelper.cos(yaw / 180.0F * (float) Math.PI) * MathHelper.cos(pitch / 180.0F * (float) Math.PI);
      double my = -MathHelper.sin(pitch / 180.0F * (float) Math.PI);
      mx = mx * this.getInfo().acceleration + prm.entity.motionX;
      my = my * this.getInfo().acceleration + prm.entity.motionY;
      mz = mz * this.getInfo().acceleration + prm.entity.motionZ;
      this.acceleration = MathHelper.sqrt_double(mx * mx + my * my + mz * mz);
      MCH_EntityTorpedo e = new MCH_EntityTorpedo(this.worldObj, prm.posX, prm.posY, prm.posZ, mx, my, mz, yaw, 0.0F, this.acceleration);
      e.setName(this.name);
      e.setParameterFromWeapon(this, prm.entity, prm.user);
      e.motionX = mx;
      e.motionY = my;
      e.motionZ = mz;
      e.accelerationInWater = this.getInfo() != null ? this.getInfo().accelerationInWater : 1.0;
      this.worldObj.spawnEntityInWorld(e);
      this.playSound(prm.entity);
      return true;
   }

   protected boolean shotGuided(MCH_WeaponParam prm) {
      float yaw = prm.user.rotationYaw;
      float pitch = prm.user.rotationPitch;
      Vec3 v = MCH_Lib.RotVec3(0.0, 0.0, 1.0, -yaw, -pitch, -prm.rotRoll);
      double tX = v.xCoord;
      double tZ = v.zCoord;
      double tY = v.yCoord;
      double dist = MathHelper.sqrt_double(tX * tX + tY * tY + tZ * tZ);
      if (this.worldObj.isRemote) {
         tX = tX * 100.0 / dist;
         tY = tY * 100.0 / dist;
         tZ = tZ * 100.0 / dist;
      } else {
         tX = tX * 150.0 / dist;
         tY = tY * 150.0 / dist;
         tZ = tZ * 150.0 / dist;
      }

      Vec3 src = W_WorldFunc.getWorldVec3(this.worldObj, prm.user.posX, prm.user.posY, prm.user.posZ);
      Vec3 dst = W_WorldFunc.getWorldVec3(this.worldObj, prm.user.posX + tX, prm.user.posY + tY, prm.user.posZ + tZ);
      MovingObjectPosition m = W_WorldFunc.clip(this.worldObj, src, dst);
      if (m != null && W_MovingObjectPosition.isHitTypeTile(m) && MCH_Lib.isBlockInWater(this.worldObj, m.blockX, m.blockY, m.blockZ)) {
         if (!this.worldObj.isRemote) {
            double mx = -MathHelper.sin(yaw / 180.0F * (float) Math.PI) * MathHelper.cos(pitch / 180.0F * (float) Math.PI);
            double mz = MathHelper.cos(yaw / 180.0F * (float) Math.PI) * MathHelper.cos(pitch / 180.0F * (float) Math.PI);
            double my = -MathHelper.sin(pitch / 180.0F * (float) Math.PI);
            mx = mx * this.getInfo().acceleration + prm.entity.motionX;
            my = my * this.getInfo().acceleration + prm.entity.motionY;
            mz = mz * this.getInfo().acceleration + prm.entity.motionZ;
            this.acceleration = MathHelper.sqrt_double(mx * mx + my * my + mz * mz);
            MCH_EntityTorpedo e = new MCH_EntityTorpedo(
               this.worldObj,
               prm.posX,
               prm.posY,
               prm.posZ,
               prm.entity.motionX,
               prm.entity.motionY,
               prm.entity.motionZ,
               yaw,
               0.0F,
               this.acceleration
            );
            e.setName(this.name);
            e.setParameterFromWeapon(this, prm.entity, prm.user);
            e.targetPosX = m.hitVec.xCoord;
            e.targetPosY = m.hitVec.yCoord;
            e.targetPosZ = m.hitVec.zCoord;
            e.motionX = mx;
            e.motionY = my;
            e.motionZ = mz;
            e.accelerationInWater = this.getInfo() != null ? this.getInfo().accelerationInWater : 1.0;
            this.worldObj.spawnEntityInWorld(e);
            this.playSound(prm.entity);
         }

         return true;
      } else {
         return false;
      }
   }
}
