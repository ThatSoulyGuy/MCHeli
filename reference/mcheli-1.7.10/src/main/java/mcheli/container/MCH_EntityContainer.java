package mcheli.container;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import mcheli.MCH_Config;
import mcheli.MCH_Lib;
import mcheli.MCH_MOD;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_EntitySeat;
import mcheli.aircraft.MCH_IEntityCanRideAircraft;
import mcheli.aircraft.MCH_SeatRackInfo;
import mcheli.multiplay.MCH_Multiplay;
import mcheli.wrapper.W_AxisAlignedBB;
import mcheli.wrapper.W_Block;
import mcheli.wrapper.W_Blocks;
import mcheli.wrapper.W_EntityContainer;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public class MCH_EntityContainer extends W_EntityContainer implements MCH_IEntityCanRideAircraft {
   private boolean isBoatEmpty;
   private double speedMultiplier = 0.07;
   private int boatPosRotationIncrements;
   private double boatX;
   private double boatY;
   private double boatZ;
   private double boatYaw;
   private double boatPitch;
   @SideOnly(Side.CLIENT)
   private double velocityX;
   @SideOnly(Side.CLIENT)
   private double velocityY;
   @SideOnly(Side.CLIENT)
   private double velocityZ;

   public MCH_EntityContainer(World par1World) {
      super(par1World);
      this.preventEntitySpawning = true;
      this.setSize(2.0F, 1.0F);
      this.yOffset = this.height / 2.0F;
      this.stepHeight = 0.6F;
      this.isImmuneToFire = true;
      this.renderDistanceWeight = 2.0;
   }

   public MCH_EntityContainer(World par1World, double par2, double par4, double par6) {
      this(par1World);
      this.setPosition(par2, par4 + this.yOffset, par6);
      this.motionX = 0.0;
      this.motionY = 0.0;
      this.motionZ = 0.0;
      this.prevPosX = par2;
      this.prevPosY = par4;
      this.prevPosZ = par6;
   }

   protected boolean canTriggerWalking() {
      return false;
   }

   @Override
   protected void entityInit() {
      this.dataWatcher.addObject(17, new Integer(0));
      this.dataWatcher.addObject(18, new Integer(1));
      this.dataWatcher.addObject(19, new Integer(0));
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

   @Override
   public int getSizeInventory() {
      return 54;
   }

   @Override
   public String getInvName() {
      return "Container " + super.getInvName();
   }

   public double getMountedYOffset() {
      return -0.3;
   }

   @Override
   public boolean attackEntityFrom(DamageSource ds, float damage) {
      if (this.isEntityInvulnerable()) {
         return false;
      }

      if (!this.worldObj.isRemote && !this.isDead) {
         damage = MCH_Config.applyDamageByExternal(this, ds, damage);
         if (!MCH_Multiplay.canAttackEntity(ds, this)) {
            return false;
         }

         if (ds.getEntity() instanceof EntityPlayer && ds.getDamageType().equalsIgnoreCase("player")) {
            MCH_Lib.DbgLog(this.worldObj, "MCH_EntityContainer.attackEntityFrom:damage=%.1f:%s", damage, ds.getDamageType());
            W_WorldFunc.MOD_playSoundAtEntity(this, "hit", 1.0F, 1.3F);
            this.setDamageTaken(this.getDamageTaken() + (int)(damage * 20.0F));
            this.setForwardDirection(-this.getForwardDirection());
            this.setTimeSinceHit(10);
            this.setBeenAttacked();
            boolean flag = ds.getEntity() instanceof EntityPlayer && ((EntityPlayer)ds.getEntity()).capabilities.isCreativeMode;
            if (flag || this.getDamageTaken() > 40.0F) {
               if (!flag) {
                  this.dropItemWithOffset(MCH_MOD.itemContainer, 1, 0.0F);
               }

               this.setDead();
            }

            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   @SideOnly(Side.CLIENT)
   public void performHurtAnimation() {
      this.setForwardDirection(-this.getForwardDirection());
      this.setTimeSinceHit(10);
      this.setDamageTaken(this.getDamageTaken() * 11);
   }

   public boolean canBeCollidedWith() {
      return !this.isDead;
   }

   @SideOnly(Side.CLIENT)
   public void setPositionAndRotation2(double par1, double par3, double par5, float par7, float par8, int par9) {
      this.boatPosRotationIncrements = par9 + 10;
      this.boatX = par1;
      this.boatY = par3;
      this.boatZ = par5;
      this.boatYaw = par7;
      this.boatPitch = par8;
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

   public void onUpdate() {
      super.onUpdate();
      if (this.getTimeSinceHit() > 0) {
         this.setTimeSinceHit(this.getTimeSinceHit() - 1);
      }

      if (this.getDamageTaken() > 0.0F) {
         this.setDamageTaken(this.getDamageTaken() - 1);
      }

      this.prevPosX = this.posX;
      this.prevPosY = this.posY;
      this.prevPosZ = this.posZ;
      byte b0 = 5;
      double d0 = 0.0;

      for (int i = 0; i < b0; i++) {
         double d1 = this.boundingBox.minY + (this.boundingBox.maxY - this.boundingBox.minY) * (i + 0) / b0 - 0.125;
         double d2 = this.boundingBox.minY + (this.boundingBox.maxY - this.boundingBox.minY) * (i + 1) / b0 - 0.125;
         AxisAlignedBB axisalignedbb = W_AxisAlignedBB.getAABB(
            this.boundingBox.minX, d1, this.boundingBox.minZ, this.boundingBox.maxX, d2, this.boundingBox.maxZ
         );
         if (this.worldObj.isAABBInMaterial(axisalignedbb, Material.water)) {
            d0 += 1.0 / b0;
         } else if (this.worldObj.isAABBInMaterial(axisalignedbb, Material.lava)) {
            d0 += 1.0 / b0;
         }
      }

      double d3 = Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
      if (d3 > 0.2625) {
         double d4 = Math.cos(this.rotationYaw * Math.PI / 180.0);
         double d5 = Math.sin(this.rotationYaw * Math.PI / 180.0);
      }

      if (this.worldObj.isRemote) {
         if (this.boatPosRotationIncrements > 0) {
            double d4 = this.posX + (this.boatX - this.posX) / this.boatPosRotationIncrements;
            double d5 = this.posY + (this.boatY - this.posY) / this.boatPosRotationIncrements;
            double d11 = this.posZ + (this.boatZ - this.posZ) / this.boatPosRotationIncrements;
            double d10 = MathHelper.wrapAngleTo180_double(this.boatYaw - this.rotationYaw);
            this.rotationYaw = (float)(this.rotationYaw + d10 / this.boatPosRotationIncrements);
            this.rotationPitch = (float)(this.rotationPitch + (this.boatPitch - this.rotationPitch) / this.boatPosRotationIncrements);
            this.boatPosRotationIncrements--;
            this.setPosition(d4, d5, d11);
            this.setRotation(this.rotationYaw, this.rotationPitch);
         } else {
            double d4 = this.posX + this.motionX;
            double d5 = this.posY + this.motionY;
            double d11 = this.posZ + this.motionZ;
            this.setPosition(d4, d5, d11);
            if (this.onGround) {
               float groundSpeed = 0.9F;
               this.motionX *= 0.9F;
               this.motionZ *= 0.9F;
            }

            this.motionX *= 0.99;
            this.motionY *= 0.95;
            this.motionZ *= 0.99;
         }
      } else {
         if (d0 < 1.0) {
            double d4 = d0 * 2.0 - 1.0;
            this.motionY += 0.04 * d4;
         } else {
            if (this.motionY < 0.0) {
               this.motionY /= 2.0;
            }

            this.motionY += 0.007;
         }

         double d4 = Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
         if (d4 > 0.35) {
            double d5 = 0.35 / d4;
            this.motionX *= d5;
            this.motionZ *= d5;
            d4 = 0.35;
         }

         if (d4 > d3 && this.speedMultiplier < 0.35) {
            this.speedMultiplier = this.speedMultiplier + (0.35 - this.speedMultiplier) / 35.0;
            if (this.speedMultiplier > 0.35) {
               this.speedMultiplier = 0.35;
            }
         } else {
            this.speedMultiplier = this.speedMultiplier - (this.speedMultiplier - 0.07) / 35.0;
            if (this.speedMultiplier < 0.07) {
               this.speedMultiplier = 0.07;
            }
         }

         if (this.onGround) {
            float groundSpeed = 0.9F;
            this.motionX *= 0.9F;
            this.motionZ *= 0.9F;
         }

         this.moveEntity(this.motionX, this.motionY, this.motionZ);
         this.motionX *= 0.99;
         this.motionY *= 0.95;
         this.motionZ *= 0.99;
         this.rotationPitch = 0.0F;
         double d5 = this.rotationYaw;
         double d11 = this.prevPosX - this.posX;
         double d10 = this.prevPosZ - this.posZ;
         if (d11 * d11 + d10 * d10 > 0.001) {
            d5 = (float)(Math.atan2(d10, d11) * 180.0 / Math.PI);
         }

         double d12 = MathHelper.wrapAngleTo180_double(d5 - this.rotationYaw);
         if (d12 > 5.0) {
            d12 = 5.0;
         }

         if (d12 < -5.0) {
            d12 = -5.0;
         }

         this.rotationYaw = (float)(this.rotationYaw + d12);
         this.setRotation(this.rotationYaw, this.rotationPitch);
         if (!this.worldObj.isRemote) {
            List list = this.worldObj.getEntitiesWithinAABBExcludingEntity(this, this.boundingBox.expand(0.2, 0.0, 0.2));
            if (list != null && !list.isEmpty()) {
               for (int l = 0; l < list.size(); l++) {
                  Entity entity = (Entity)list.get(l);
                  if (entity.canBePushed() && entity instanceof MCH_EntityContainer) {
                     entity.applyEntityCollision(this);
                  }
               }
            }

            if (MCH_Config.Collision_DestroyBlock.prmBool) {
               for (int l = 0; l < 4; l++) {
                  int i1 = MathHelper.floor_double(this.posX + (l % 2 - 0.5) * 0.8);
                  int j1 = MathHelper.floor_double(this.posZ + (l / 2 - 0.5) * 0.8);

                  for (int k1 = 0; k1 < 2; k1++) {
                     int l1 = MathHelper.floor_double(this.posY) + k1;
                     if (W_WorldFunc.isEqualBlock(this.worldObj, i1, l1, j1, W_Block.getSnowLayer())) {
                        this.worldObj.setBlockToAir(i1, l1, j1);
                     } else if (W_WorldFunc.isEqualBlock(this.worldObj, i1, l1, j1, W_Blocks.waterlily)) {
                        W_WorldFunc.destroyBlock(this.worldObj, i1, l1, j1, true);
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   protected void writeEntityToNBT(NBTTagCompound par1NBTTagCompound) {
      super.writeEntityToNBT(par1NBTTagCompound);
   }

   @Override
   protected void readEntityFromNBT(NBTTagCompound par1NBTTagCompound) {
      super.readEntityFromNBT(par1NBTTagCompound);
   }

   @SideOnly(Side.CLIENT)
   public float getShadowSize() {
      return 2.0F;
   }

   @Override
   public boolean interactFirst(EntityPlayer player) {
      if (player != null) {
         this.openInventory(player);
      }

      return true;
   }

   public void setDamageTaken(int par1) {
      this.dataWatcher.updateObject(19, par1);
   }

   public int getDamageTaken() {
      return this.dataWatcher.getWatchableObjectInt(19);
   }

   public void setTimeSinceHit(int par1) {
      this.dataWatcher.updateObject(17, par1);
   }

   public int getTimeSinceHit() {
      return this.dataWatcher.getWatchableObjectInt(17);
   }

   public void setForwardDirection(int par1) {
      this.dataWatcher.updateObject(18, par1);
   }

   public int getForwardDirection() {
      return this.dataWatcher.getWatchableObjectInt(18);
   }

   @Override
   public boolean canRideAircraft(MCH_EntityAircraft ac, int seatID, MCH_SeatRackInfo info) {
      for (String s : info.names) {
         if (s.equalsIgnoreCase("container")) {
            return ac.ridingEntity == null && this.ridingEntity == null;
         }
      }

      return false;
   }

   @Override
   public boolean isSkipNormalRender() {
      return this.ridingEntity instanceof MCH_EntitySeat;
   }
}
