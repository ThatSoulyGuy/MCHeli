package mcheli.weapon;

import mcheli.MCH_Lib;
import mcheli.wrapper.W_MovingObjectPosition;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class MCH_WeaponASMissile extends MCH_WeaponBase {
   public MCH_WeaponASMissile(World w, Vec3 v, float yaw, float pitch, String nm, MCH_WeaponInfo wi) {
      super(w, v, yaw, pitch, nm, wi);
      this.acceleration = 3.0F;
      this.explosionPower = 9;
      this.power = 40;
      this.interval = -350;
      if (w.isRemote) {
         this.interval -= 10;
      }
   }

   @Override
   public boolean isCooldownCountReloadTime() {
      return true;
   }

   @Override
   public void update(int countWait) {
      super.update(countWait);
   }

   @Override
   public boolean shot(MCH_WeaponParam prm) {
      float yaw = prm.user.rotationYaw;
      float pitch = prm.user.rotationPitch;
      double tX = -MathHelper.sin(yaw / 180.0F * (float) Math.PI) * MathHelper.cos(pitch / 180.0F * (float) Math.PI);
      double tZ = MathHelper.cos(yaw / 180.0F * (float) Math.PI) * MathHelper.cos(pitch / 180.0F * (float) Math.PI);
      double tY = -MathHelper.sin(pitch / 180.0F * (float) Math.PI);
      double dist = MathHelper.sqrt_double(tX * tX + tY * tY + tZ * tZ);
      if (this.worldObj.isRemote) {
         tX = tX * 200.0 / dist;
         tY = tY * 200.0 / dist;
         tZ = tZ * 200.0 / dist;
      } else {
         tX = tX * 250.0 / dist;
         tY = tY * 250.0 / dist;
         tZ = tZ * 250.0 / dist;
      }

      Vec3 src = W_WorldFunc.getWorldVec3(this.worldObj, prm.entity.posX, prm.entity.posY + 1.62, prm.entity.posZ);
      Vec3 dst = W_WorldFunc.getWorldVec3(this.worldObj, prm.entity.posX + tX, prm.entity.posY + 1.62 + tY, prm.entity.posZ + tZ);
      MovingObjectPosition m = W_WorldFunc.clip(this.worldObj, src, dst);
      if (m != null && W_MovingObjectPosition.isHitTypeTile(m) && !MCH_Lib.isBlockInWater(this.worldObj, m.blockX, m.blockY, m.blockZ)) {
         if (!this.worldObj.isRemote) {
            MCH_EntityASMissile e = new MCH_EntityASMissile(this.worldObj, prm.posX, prm.posY, prm.posZ, tX, tY, tZ, yaw, pitch, this.acceleration);
            e.setName(this.name);
            e.setParameterFromWeapon(this, prm.entity, prm.user);
            e.targetPosX = m.hitVec.xCoord;
            e.targetPosY = m.hitVec.yCoord;
            e.targetPosZ = m.hitVec.zCoord;
            this.worldObj.spawnEntityInWorld(e);
            this.playSound(prm.entity);
         }

         return true;
      } else {
         return false;
      }
   }
}
