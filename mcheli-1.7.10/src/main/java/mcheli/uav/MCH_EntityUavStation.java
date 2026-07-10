package mcheli.uav;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import mcheli.MCH_Config;
import mcheli.MCH_Explosion;
import mcheli.MCH_Lib;
import mcheli.MCH_MOD;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.helicopter.MCH_HeliInfo;
import mcheli.helicopter.MCH_HeliInfoManager;
import mcheli.helicopter.MCH_ItemHeli;
import mcheli.multiplay.MCH_Multiplay;
import mcheli.plane.MCP_ItemPlane;
import mcheli.plane.MCP_PlaneInfo;
import mcheli.plane.MCP_PlaneInfoManager;
import mcheli.tank.MCH_ItemTank;
import mcheli.tank.MCH_TankInfo;
import mcheli.tank.MCH_TankInfoManager;
import mcheli.wrapper.W_Entity;
import mcheli.wrapper.W_EntityContainer;
import mcheli.wrapper.W_EntityPlayer;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public class MCH_EntityUavStation extends W_EntityContainer {
   protected static final int DATAWT_ID_KIND = 27;
   protected static final int DATAWT_ID_LAST_AC = 28;
   protected static final int DATAWT_ID_UAV_X = 29;
   protected static final int DATAWT_ID_UAV_Y = 30;
   protected static final int DATAWT_ID_UAV_Z = 31;
   protected Entity lastRiddenByEntity;
   public boolean isRequestedSyncStatus;
   @SideOnly(Side.CLIENT)
   protected double velocityX;
   @SideOnly(Side.CLIENT)
   protected double velocityY;
   @SideOnly(Side.CLIENT)
   protected double velocityZ;
   protected int aircraftPosRotInc;
   protected double aircraftX;
   protected double aircraftY;
   protected double aircraftZ;
   protected double aircraftYaw;
   protected double aircraftPitch;
   private MCH_EntityAircraft controlAircraft;
   private MCH_EntityAircraft lastControlAircraft;
   private String loadedLastControlAircraftGuid;
   public int posUavX;
   public int posUavY;
   public int posUavZ;
   public float rotCover;
   public float prevRotCover;

   public MCH_EntityUavStation(World world) {
      super(world);
      this.dropContentsWhenDead = false;
      this.preventEntitySpawning = true;
      this.setSize(2.0F, 0.7F);
      this.yOffset = this.height / 2.0F;
      this.motionX = 0.0;
      this.motionY = 0.0;
      this.motionZ = 0.0;
      this.ignoreFrustumCheck = true;
      this.lastRiddenByEntity = null;
      this.aircraftPosRotInc = 0;
      this.aircraftX = 0.0;
      this.aircraftY = 0.0;
      this.aircraftZ = 0.0;
      this.aircraftYaw = 0.0;
      this.aircraftPitch = 0.0;
      this.posUavX = 0;
      this.posUavY = 0;
      this.posUavZ = 0;
      this.rotCover = 0.0F;
      this.prevRotCover = 0.0F;
      this.setControlAircract(null);
      this.setLastControlAircraft(null);
      this.loadedLastControlAircraftGuid = "";
   }

   @Override
   protected void entityInit() {
      super.entityInit();
      this.getDataWatcher().addObject(27, (byte)0);
      this.getDataWatcher().addObject(28, 0);
      this.getDataWatcher().addObject(29, 0);
      this.getDataWatcher().addObject(30, 0);
      this.getDataWatcher().addObject(31, 0);
      this.setOpen(true);
   }

   public int getStatus() {
      return this.getDataWatcher().getWatchableObjectByte(27);
   }

   public void setStatus(int n) {
      if (!this.worldObj.isRemote) {
         MCH_Lib.DbgLog(this.worldObj, "MCH_EntityUavStation.setStatus(%d)", n);
         this.getDataWatcher().updateObject(27, (byte)n);
      }
   }

   public int getKind() {
      return 127 & this.getStatus();
   }

   public void setKind(int n) {
      this.setStatus(this.getStatus() & 128 | n);
   }

   public boolean isOpen() {
      return (this.getStatus() & 128) != 0;
   }

   public void setOpen(boolean b) {
      this.setStatus((b ? 128 : 0) | this.getStatus() & 127);
   }

   public MCH_EntityAircraft getControlAircract() {
      return this.controlAircraft;
   }

   public void setControlAircract(MCH_EntityAircraft ac) {
      this.controlAircraft = ac;
      if (ac != null && !ac.isDead) {
         this.setLastControlAircraft(ac);
      }
   }

   public void setUavPosition(int x, int y, int z) {
      if (!this.worldObj.isRemote) {
         this.posUavX = x;
         this.posUavY = y;
         this.posUavZ = z;
         this.getDataWatcher().updateObject(29, x);
         this.getDataWatcher().updateObject(30, y);
         this.getDataWatcher().updateObject(31, z);
      }
   }

   public void updateUavPosition() {
      this.posUavX = this.getDataWatcher().getWatchableObjectInt(29);
      this.posUavY = this.getDataWatcher().getWatchableObjectInt(30);
      this.posUavZ = this.getDataWatcher().getWatchableObjectInt(31);
   }

   @Override
   protected void writeEntityToNBT(NBTTagCompound nbt) {
      super.writeEntityToNBT(nbt);
      nbt.setInteger("UavStatus", this.getStatus());
      nbt.setInteger("PosUavX", this.posUavX);
      nbt.setInteger("PosUavY", this.posUavY);
      nbt.setInteger("PosUavZ", this.posUavZ);
      String s = "";
      if (this.getLastControlAircraft() != null && !this.getLastControlAircraft().isDead) {
         s = this.getLastControlAircraft().getCommonUniqueId();
      }

      if (s.isEmpty()) {
         s = this.loadedLastControlAircraftGuid;
      }

      nbt.setString("LastCtrlAc", s);
   }

   @Override
   protected void readEntityFromNBT(NBTTagCompound nbt) {
      super.readEntityFromNBT(nbt);
      this.setUavPosition(nbt.getInteger("PosUavX"), nbt.getInteger("PosUavY"), nbt.getInteger("PosUavZ"));
      if (nbt.hasKey("UavStatus")) {
         this.setStatus(nbt.getInteger("UavStatus"));
      } else {
         this.setKind(1);
      }

      this.loadedLastControlAircraftGuid = nbt.getString("LastCtrlAc");
   }

   public void initUavPostion() {
      int rt = (int)(MCH_Lib.getRotate360(this.rotationYaw + 45.0F) / 90.0);
      int D = 12;
      this.posUavX = rt != 0 && rt != 3 ? -12 : 12;
      this.posUavZ = rt != 0 && rt != 1 ? -12 : 12;
      this.posUavY = 2;
      this.setUavPosition(this.posUavX, this.posUavY, this.posUavZ);
   }

   @Override
   public void setDead() {
      super.setDead();
   }

   @Override
   public boolean attackEntityFrom(DamageSource damageSource, float damage) {
      if (this.isEntityInvulnerable()) {
         return false;
      }

      if (this.isDead) {
         return true;
      }

      if (this.worldObj.isRemote) {
         return true;
      }

      String dmt = damageSource.getDamageType();
      damage = MCH_Config.applyDamageByExternal(this, damageSource, damage);
      if (!MCH_Multiplay.canAttackEntity(damageSource, this)) {
         return false;
      }

      boolean isCreative = false;
      Entity entity = damageSource.getEntity();
      boolean isDamegeSourcePlayer = false;
      if (entity instanceof EntityPlayer) {
         isCreative = ((EntityPlayer)entity).capabilities.isCreativeMode;
         if (dmt.compareTo("player") == 0) {
            isDamegeSourcePlayer = true;
         }

         W_WorldFunc.MOD_playSoundAtEntity(this, "hit", 1.0F, 1.0F);
      } else {
         W_WorldFunc.MOD_playSoundAtEntity(this, "helidmg", 1.0F, 0.9F + this.rand.nextFloat() * 0.1F);
      }

      this.setBeenAttacked();
      if (damage > 0.0F) {
         if (this.riddenByEntity != null) {
            this.riddenByEntity.mountEntity(this);
         }

         this.dropContentsWhenDead = true;
         this.setDead();
         if (!isDamegeSourcePlayer) {
            MCH_Explosion.newExplosion(
               this.worldObj,
               null,
               this.riddenByEntity,
               this.posX,
               this.posY,
               this.posZ,
               1.0F,
               0.0F,
               true,
               true,
               false,
               false,
               0
            );
         }

         if (!isCreative) {
            int kind = this.getKind();
            if (kind > 0) {
               this.dropItemWithOffset(MCH_MOD.itemUavStation[kind - 1], 1, 0.0F);
            }
         }
      }

      return true;
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
      return false;
   }

   public double getMountedYOffset() {
      if (this.getKind() == 2 && this.riddenByEntity != null) {
         double px = -Math.sin(this.rotationYaw * Math.PI / 180.0) * 0.9;
         double pz = Math.cos(this.rotationYaw * Math.PI / 180.0) * 0.9;
         int x = (int)(this.posX + px);
         int y = (int)(this.posY - 0.5);
         int z = (int)(this.posZ + pz);
         Block block = this.worldObj.getBlock(x, y, z);
         return block.isOpaqueCube() ? -0.4 : -0.9;
      } else {
         return 0.35;
      }
   }

   @SideOnly(Side.CLIENT)
   public float getShadowSize() {
      return 2.0F;
   }

   public boolean canBeCollidedWith() {
      return !this.isDead;
   }

   public void applyEntityCollision(Entity par1Entity) {
   }

   public void addVelocity(double par1, double par3, double par5) {
   }

   @SideOnly(Side.CLIENT)
   public void setVelocity(double par1, double par3, double par5) {
      this.velocityX = this.motionX = par1;
      this.velocityY = this.motionY = par3;
      this.velocityZ = this.motionZ = par5;
   }

   public void onUpdate() {
      super.onUpdate();
      this.prevRotCover = this.rotCover;
      if (this.isOpen()) {
         if (this.rotCover < 1.0F) {
            this.rotCover += 0.1F;
         } else {
            this.rotCover = 1.0F;
         }
      } else if (this.rotCover > 0.0F) {
         this.rotCover -= 0.1F;
      } else {
         this.rotCover = 0.0F;
      }

      if (this.riddenByEntity == null) {
         if (this.lastRiddenByEntity != null) {
            this.unmountEntity(true);
         }

         this.setControlAircract(null);
      }

      int uavStationKind = this.getKind();
      if (this.ticksExisted < 30 && uavStationKind > 0 && uavStationKind != 1 && uavStationKind == 2) {
      }

      if (this.worldObj.isRemote && !this.isRequestedSyncStatus) {
         this.isRequestedSyncStatus = true;
      }

      this.prevPosX = this.posX;
      this.prevPosY = this.posY;
      this.prevPosZ = this.posZ;
      if (this.getControlAircract() != null && this.getControlAircract().isDead) {
         this.setControlAircract(null);
      }

      if (this.getLastControlAircraft() != null && this.getLastControlAircraft().isDead) {
         this.setLastControlAircraft(null);
      }

      if (this.worldObj.isRemote) {
         this.onUpdate_Client();
      } else {
         this.onUpdate_Server();
      }

      this.lastRiddenByEntity = this.riddenByEntity;
   }

   public MCH_EntityAircraft getLastControlAircraft() {
      return this.lastControlAircraft;
   }

   public MCH_EntityAircraft getAndSearchLastControlAircraft() {
      if (this.getLastControlAircraft() == null) {
         int id = this.getLastControlAircraftEntityId();
         if (id > 0) {
            Entity entity = this.worldObj.getEntityByID(id);
            if (entity instanceof MCH_EntityAircraft) {
               MCH_EntityAircraft ac = (MCH_EntityAircraft)entity;
               if (ac.isUAV()) {
                  this.setLastControlAircraft(ac);
               }
            }
         }
      }

      return this.getLastControlAircraft();
   }

   public void setLastControlAircraft(MCH_EntityAircraft ac) {
      MCH_Lib.DbgLog(this.worldObj, "MCH_EntityUavStation.setLastControlAircraft:" + ac);
      this.lastControlAircraft = ac;
   }

   public Integer getLastControlAircraftEntityId() {
      return this.getDataWatcher().getWatchableObjectInt(28);
   }

   public void setLastControlAircraftEntityId(int s) {
      if (!this.worldObj.isRemote) {
         this.getDataWatcher().updateObject(28, s);
      }
   }

   public void searchLastControlAircraft() {
      if (!this.loadedLastControlAircraftGuid.isEmpty()) {
         List list = this.worldObj.getEntitiesWithinAABB(MCH_EntityAircraft.class, this.getBoundingBox().expand(120.0, 120.0, 120.0));
         if (list != null) {
            for (int i = 0; i < list.size(); i++) {
               MCH_EntityAircraft ac = (MCH_EntityAircraft)list.get(i);
               if (ac.getCommonUniqueId().equals(this.loadedLastControlAircraftGuid)) {
                  String n = ac.getAcInfo() != null ? ac.getAcInfo().displayName : "no info : " + ac;
                  MCH_Lib.DbgLog(this.worldObj, "MCH_EntityUavStation.searchLastControlAircraft:found" + n);
                  this.setLastControlAircraft(ac);
                  this.setLastControlAircraftEntityId(W_Entity.getEntityId(ac));
                  this.loadedLastControlAircraftGuid = "";
                  return;
               }
            }
         }
      }
   }

   protected void onUpdate_Client() {
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
         this.motionY *= 0.96;
         this.motionX = 0.0;
         this.motionZ = 0.0;
      }

      this.updateUavPosition();
   }

   private void onUpdate_Server() {
      this.motionY -= 0.03;
      this.moveEntity(0.0, this.motionY, 0.0);
      this.motionY *= 0.96;
      this.motionX = 0.0;
      this.motionZ = 0.0;
      this.setRotation(this.rotationYaw, this.rotationPitch);
      if (this.riddenByEntity != null) {
         if (this.riddenByEntity.isDead) {
            this.unmountEntity(true);
            this.riddenByEntity = null;
         } else {
            ItemStack item = this.getStackInSlot(0);
            if (item != null && item.stackSize > 0) {
               this.handleItem(this.riddenByEntity, item);
               if (item.stackSize == 0) {
                  this.setInventorySlotContents(0, null);
               }
            }
         }
      }

      if (this.getLastControlAircraft() == null && this.ticksExisted % 40 == 0) {
         this.searchLastControlAircraft();
      }
   }

   public void setPositionAndRotation2(double par1, double par3, double par5, float par7, float par8, int par9) {
      this.aircraftPosRotInc = par9 + 8;
      this.aircraftX = par1;
      this.aircraftY = par3;
      this.aircraftZ = par5;
      this.aircraftYaw = par7;
      this.aircraftPitch = par8;
      this.motionX = this.velocityX;
      this.motionY = this.velocityY;
      this.motionZ = this.velocityZ;
   }

   public void updateRiderPosition() {
      if (this.riddenByEntity != null) {
         double x = -Math.sin(this.rotationYaw * Math.PI / 180.0) * 0.9;
         double z = Math.cos(this.rotationYaw * Math.PI / 180.0) * 0.9;
         this.riddenByEntity
            .setPosition(this.posX + x, this.posY + this.getMountedYOffset() + this.riddenByEntity.getYOffset(), this.posZ + z);
      }
   }

   public void controlLastAircraft(Entity user) {
      if (this.getLastControlAircraft() != null && !this.getLastControlAircraft().isDead) {
         this.getLastControlAircraft().setUavStation(this);
         this.setControlAircract(this.getLastControlAircraft());
         W_EntityPlayer.closeScreen(user);
      }
   }

   public void handleItem(Entity user, ItemStack itemStack) {
      if (user != null && !user.isDead && itemStack != null && itemStack.stackSize == 1) {
         if (!this.worldObj.isRemote) {
            MCH_EntityAircraft ac = null;
            double x = this.posX + this.posUavX;
            double y = this.posY + this.posUavY;
            double z = this.posZ + this.posUavZ;
            if (y <= 1.0) {
               y = 2.0;
            }

            Item item = itemStack.getItem();
            if (item instanceof MCP_ItemPlane) {
               MCP_PlaneInfo pi = MCP_PlaneInfoManager.getFromItem(item);
               if (pi != null && pi.isUAV) {
                  if (!pi.isSmallUAV && this.getKind() == 2) {
                     ac = null;
                  } else {
                     ac = ((MCP_ItemPlane)item).createAircraft(this.worldObj, x, y, z, itemStack);
                  }
               }
            }

            if (item instanceof MCH_ItemHeli) {
               MCH_HeliInfo hi = MCH_HeliInfoManager.getFromItem(item);
               if (hi != null && hi.isUAV) {
                  if (!hi.isSmallUAV && this.getKind() == 2) {
                     ac = null;
                  } else {
                     ac = ((MCH_ItemHeli)item).createAircraft(this.worldObj, x, y, z, itemStack);
                  }
               }
            }

            if (item instanceof MCH_ItemTank) {
               MCH_TankInfo hi = MCH_TankInfoManager.getFromItem(item);
               if (hi != null && hi.isUAV) {
                  if (!hi.isSmallUAV && this.getKind() == 2) {
                     ac = null;
                  } else {
                     ac = ((MCH_ItemTank)item).createAircraft(this.worldObj, x, y, z, itemStack);
                  }
               }
            }

            if (ac != null) {
               ac.rotationYaw = this.rotationYaw - 180.0F;
               ac.prevRotationYaw = ac.rotationYaw;
               user.rotationYaw = this.rotationYaw - 180.0F;
               if (this.worldObj.getCollidingBoundingBoxes(ac, ac.boundingBox.expand(-0.1, -0.1, -0.1)).isEmpty()) {
                  itemStack.stackSize--;
                  MCH_Lib.DbgLog(this.worldObj, "Create UAV: %s : %s", item.getUnlocalizedName(), item);
                  user.rotationYaw = this.rotationYaw - 180.0F;
                  if (!ac.isTargetDrone()) {
                     ac.setUavStation(this);
                     this.setControlAircract(ac);
                  }

                  this.worldObj.spawnEntityInWorld(ac);
                  if (!ac.isTargetDrone()) {
                     ac.setFuel((int)(ac.getMaxFuel() * 0.05F));
                     W_EntityPlayer.closeScreen(user);
                  } else {
                     ac.setFuel(ac.getMaxFuel());
                  }
               } else {
                  ac.setDead();
               }
            }
         }
      }
   }

   public void _setInventorySlotContents(int par1, ItemStack itemStack) {
      super.setInventorySlotContents(par1, itemStack);
   }

   @Override
   public boolean interactFirst(EntityPlayer player) {
      int kind = this.getKind();
      if (kind <= 0) {
         return false;
      }

      if (this.riddenByEntity != null) {
         return false;
      }

      if (kind == 2) {
         if (player.isSneaking()) {
            this.setOpen(!this.isOpen());
            return false;
         }

         if (!this.isOpen()) {
            return false;
         }
      }

      this.riddenByEntity = null;
      this.lastRiddenByEntity = null;
      if (!this.worldObj.isRemote) {
         player.mountEntity(this);
         player.openGui(MCH_MOD.instance, 0, player.worldObj, (int)this.posX, (int)this.posY, (int)this.posZ);
      }

      return true;
   }

   @Override
   public int getSizeInventory() {
      return 1;
   }

   @Override
   public int getInventoryStackLimit() {
      return 1;
   }

   public void unmountEntity(boolean unmountAllEntity) {
      Entity rByEntity = null;
      if (this.riddenByEntity != null) {
         if (!this.worldObj.isRemote) {
            rByEntity = this.riddenByEntity;
            this.riddenByEntity.mountEntity(null);
         }
      } else if (this.lastRiddenByEntity != null) {
         rByEntity = this.lastRiddenByEntity;
      }

      if (this.getControlAircract() != null) {
         this.getControlAircract().setUavStation(null);
      }

      this.setControlAircract(null);
      if (this.worldObj.isRemote) {
         W_EntityPlayer.closeScreen(rByEntity);
      }

      this.riddenByEntity = null;
      this.lastRiddenByEntity = null;
   }
}
