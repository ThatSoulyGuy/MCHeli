package mcheli.weapon;

import java.util.List;
import mcheli.wrapper.W_Lib;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class MCH_EntityBomb extends MCH_EntityBaseBullet {
   public MCH_EntityBomb(World par1World) {
      super(par1World);
   }

   public MCH_EntityBomb(
      World par1World, double posX, double posY, double posZ, double targetX, double targetY, double targetZ, float yaw, float pitch, double acceleration
   ) {
      super(par1World, posX, posY, posZ, targetX, targetY, targetZ, yaw, pitch, acceleration);
   }

   @Override
   public void onUpdate() {
      super.onUpdate();
      if (!this.worldObj.isRemote && this.getInfo() != null) {
         this.motionX *= 0.999;
         this.motionZ *= 0.999;
         if (this.isInWater()) {
            this.motionX = this.motionX * this.getInfo().velocityInWater;
            this.motionY = this.motionY * this.getInfo().velocityInWater;
            this.motionZ = this.motionZ * this.getInfo().velocityInWater;
         }

         float dist = this.getInfo().proximityFuseDist;
         if (dist > 0.1F && this.getCountOnUpdate() % 10 == 0) {
            List list = this.worldObj.getEntitiesWithinAABBExcludingEntity(this, this.boundingBox.expand(dist, dist, dist));
            if (list != null) {
               for (int i = 0; i < list.size(); i++) {
                  Entity entity = (Entity)list.get(i);
                  if (W_Lib.isEntityLivingBase(entity) && this.canBeCollidedEntity(entity)) {
                     MovingObjectPosition m = new MovingObjectPosition(
                        (int)(this.posX + 0.5),
                        (int)(this.posY + 0.5),
                        (int)(this.posZ + 0.5),
                        0,
                        Vec3.createVectorHelper(this.posX, this.posY, this.posZ)
                     );
                     this.onImpact(m, 1.0F);
                     break;
                  }
               }
            }
         }
      }

      this.onUpdateBomblet();
   }

   @Override
   public void sprinkleBomblet() {
      if (!this.worldObj.isRemote) {
         MCH_EntityBomb e = new MCH_EntityBomb(
            this.worldObj,
            this.posX,
            this.posY,
            this.posZ,
            this.motionX,
            this.motionY,
            this.motionZ,
            this.rand.nextInt(360),
            0.0F,
            this.acceleration
         );
         e.setParameterFromWeapon(this, this.shootingAircraft, this.shootingEntity);
         e.setName(this.getName());
         float MOTION = 1.0F;
         float RANDOM = this.getInfo().bombletDiff;
         e.motionX = this.motionX * 1.0 + (this.rand.nextFloat() - 0.5F) * RANDOM;
         e.motionY = this.motionY * 1.0 / 2.0 + (this.rand.nextFloat() - 0.5F) * RANDOM / 2.0F;
         e.motionZ = this.motionZ * 1.0 + (this.rand.nextFloat() - 0.5F) * RANDOM;
         e.setBomblet();
         this.worldObj.spawnEntityInWorld(e);
      }
   }

   @Override
   public MCH_BulletModel getDefaultBulletModel() {
      return MCH_DefaultBulletModels.Bomb;
   }
}
