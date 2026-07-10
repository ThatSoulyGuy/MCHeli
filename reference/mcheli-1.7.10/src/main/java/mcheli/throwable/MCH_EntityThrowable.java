package mcheli.throwable;

import mcheli.MCH_Explosion;
import mcheli.MCH_Lib;
import mcheli.particles.MCH_ParticleParam;
import mcheli.particles.MCH_ParticlesUtil;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

public class MCH_EntityThrowable extends EntityThrowable {
   private static final int DATAID_NAME = 31;
   private int countOnUpdate;
   private MCH_ThrowableInfo throwableInfo;
   public double boundPosX;
   public double boundPosY;
   public double boundPosZ;
   public MovingObjectPosition lastOnImpact;
   public int noInfoCount;

   public MCH_EntityThrowable(World par1World) {
      super(par1World);
      this.init();
   }

   public MCH_EntityThrowable(World par1World, EntityLivingBase par2EntityLivingBase, float acceleration) {
      super(par1World, par2EntityLivingBase);
      this.motionX *= acceleration;
      this.motionY *= acceleration;
      this.motionZ *= acceleration;
      this.init();
   }

   public MCH_EntityThrowable(World par1World, double par2, double par4, double par6) {
      super(par1World, par2, par4, par6);
      this.init();
   }

   public MCH_EntityThrowable(World p_i1777_1_, double x, double y, double z, float yaw, float pitch) {
      this(p_i1777_1_);
      this.setSize(0.25F, 0.25F);
      this.setLocationAndAngles(x, y, z, yaw, pitch);
      this.posX = this.posX - MathHelper.cos(this.rotationYaw / 180.0F * (float) Math.PI) * 0.16F;
      this.posY -= 0.1F;
      this.posZ = this.posZ - MathHelper.sin(this.rotationYaw / 180.0F * (float) Math.PI) * 0.16F;
      this.setPosition(this.posX, this.posY, this.posZ);
      this.yOffset = 0.0F;
      float f = 0.4F;
      this.motionX = -MathHelper.sin(this.rotationYaw / 180.0F * (float) Math.PI)
         * MathHelper.cos(this.rotationPitch / 180.0F * (float) Math.PI)
         * f;
      this.motionZ = MathHelper.cos(this.rotationYaw / 180.0F * (float) Math.PI)
         * MathHelper.cos(this.rotationPitch / 180.0F * (float) Math.PI)
         * f;
      this.motionY = -MathHelper.sin((this.rotationPitch + this.func_70183_g()) / 180.0F * (float) Math.PI) * f;
      this.setThrowableHeading(this.motionX, this.motionY, this.motionZ, this.func_70182_d(), 1.0F);
   }

   public void init() {
      this.lastOnImpact = null;
      this.countOnUpdate = 0;
      this.setInfo(null);
      this.noInfoCount = 0;
      this.getDataWatcher().addObject(31, new String(""));
   }

   public void setDead() {
      String s = this.getInfo() != null ? this.getInfo().name : "null";
      MCH_Lib.DbgLog(this.worldObj, "MCH_EntityThrowable.setDead(%s)", s);
      super.setDead();
   }

   public void onUpdate() {
      this.boundPosX = this.posX;
      this.boundPosY = this.posY;
      this.boundPosZ = this.posZ;
      if (this.getInfo() != null) {
         Block block = W_WorldFunc.getBlock(this.worldObj, (int)(this.posX + 0.5), (int)this.posY, (int)(this.posZ + 0.5));
         Material mat = W_WorldFunc.getBlockMaterial(
            this.worldObj, (int)(this.posX + 0.5), (int)this.posY, (int)(this.posZ + 0.5)
         );
         if (block != null && mat == Material.water) {
            this.motionY = this.motionY + this.getInfo().gravityInWater;
         } else {
            this.motionY = this.motionY + this.getInfo().gravity;
         }
      }

      super.onUpdate();
      if (this.lastOnImpact != null) {
         this.boundBullet(this.lastOnImpact);
         this.setPosition(this.boundPosX + this.motionX, this.boundPosY + this.motionY, this.boundPosZ + this.motionZ);
         this.lastOnImpact = null;
      }

      this.countOnUpdate++;
      if (this.countOnUpdate >= 2147483632) {
         this.setDead();
      } else {
         if (this.getInfo() == null) {
            String s = this.getDataWatcher().getWatchableObjectString(31);
            if (!s.isEmpty()) {
               this.setInfo(MCH_ThrowableInfoManager.get(s));
            }

            if (this.getInfo() == null) {
               this.noInfoCount++;
               if (this.noInfoCount > 10) {
                  this.setDead();
               }

               return;
            }
         }

         if (!this.isDead) {
            if (!this.worldObj.isRemote) {
               if (this.countOnUpdate == this.getInfo().timeFuse && this.getInfo().explosion > 0) {
                  MCH_Explosion.newExplosion(
                     this.worldObj,
                     null,
                     null,
                     this.posX,
                     this.posY,
                     this.posZ,
                     this.getInfo().explosion,
                     this.getInfo().explosion,
                     true,
                     true,
                     false,
                     true,
                     0
                  );
                  this.setDead();
                  return;
               }

               if (this.countOnUpdate >= this.getInfo().aliveTime) {
                  this.setDead();
                  return;
               }
            } else if (this.countOnUpdate >= this.getInfo().timeFuse && this.getInfo().explosion <= 0) {
               for (int i = 0; i < this.getInfo().smokeNum; i++) {
                  float y = this.getInfo().smokeVelocityVertical >= 0.0F ? 0.2F : -0.2F;
                  float r = this.getInfo().smokeColor.r * 0.9F + this.rand.nextFloat() * 0.1F;
                  float g = this.getInfo().smokeColor.g * 0.9F + this.rand.nextFloat() * 0.1F;
                  float b = this.getInfo().smokeColor.b * 0.9F + this.rand.nextFloat() * 0.1F;
                  if (this.getInfo().smokeColor.r == this.getInfo().smokeColor.g) {
                     g = r;
                  }

                  if (this.getInfo().smokeColor.r == this.getInfo().smokeColor.b) {
                     b = r;
                  }

                  if (this.getInfo().smokeColor.g == this.getInfo().smokeColor.b) {
                     b = g;
                  }

                  this.spawnParticle(
                     "explode",
                     4,
                     this.getInfo().smokeSize + this.rand.nextFloat() * this.getInfo().smokeSize / 3.0F,
                     r,
                     g,
                     b,
                     this.getInfo().smokeVelocityHorizontal * (this.rand.nextFloat() - 0.5F),
                     this.getInfo().smokeVelocityVertical * this.rand.nextFloat(),
                     this.getInfo().smokeVelocityHorizontal * (this.rand.nextFloat() - 0.5F)
                  );
               }
            }
         }
      }
   }

   public void spawnParticle(String name, int num, float size, float r, float g, float b, float mx, float my, float mz) {
      if (this.worldObj.isRemote) {
         if (name.isEmpty() || num < 1) {
            return;
         }

         double x = (this.posX - this.prevPosX) / num;
         double y = (this.posY - this.prevPosY) / num;
         double z = (this.posZ - this.prevPosZ) / num;

         for (int i = 0; i < num; i++) {
            MCH_ParticleParam prm = new MCH_ParticleParam(
               this.worldObj, "smoke", this.prevPosX + x * i, 1.0 + this.prevPosY + y * i, this.prevPosZ + z * i
            );
            prm.setMotion(mx, my, mz);
            prm.size = size;
            prm.setColor(1.0F, r, g, b);
            prm.isEffectWind = true;
            prm.toWhite = true;
            MCH_ParticlesUtil.spawnParticle(prm);
         }
      }
   }

   protected float getGravityVelocity() {
      return 0.0F;
   }

   public void boundBullet(MovingObjectPosition m) {
      float bound = this.getInfo().bound;
      switch (m.sideHit) {
         case 0:
         case 1:
            this.motionX *= 0.9F;
            this.motionZ *= 0.9F;
            this.boundPosY = m.hitVec.yCoord;
            if ((m.sideHit != 0 || !(this.motionY > 0.0)) && (m.sideHit != 1 || !(this.motionY < 0.0))) {
               this.motionY = 0.0;
            } else {
               this.motionY = -this.motionY * bound;
            }
            break;
         case 2:
            if (this.motionZ > 0.0) {
               this.motionZ = -this.motionZ * bound;
            }
            break;
         case 3:
            if (this.motionZ < 0.0) {
               this.motionZ = -this.motionZ * bound;
            }
            break;
         case 4:
            if (this.motionX > 0.0) {
               this.motionX = -this.motionX * bound;
            }
            break;
         case 5:
            if (this.motionX < 0.0) {
               this.motionX = -this.motionX * bound;
            }
      }
   }

   protected void onImpact(MovingObjectPosition m) {
      if (this.getInfo() != null) {
         this.lastOnImpact = m;
      }
   }

   public MCH_ThrowableInfo getInfo() {
      return this.throwableInfo;
   }

   public void setInfo(MCH_ThrowableInfo info) {
      this.throwableInfo = info;
      if (info != null && !this.worldObj.isRemote) {
         this.getDataWatcher().updateObject(31, new String(info.name));
      }
   }
}
