package mcheli.aircraft;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import mcheli.MCH_Achievement;
import mcheli.MCH_Camera;
import mcheli.MCH_Config;
import mcheli.MCH_Explosion;
import mcheli.MCH_Lib;
import mcheli.MCH_LowPassFilterFloat;
import mcheli.MCH_MOD;
import mcheli.MCH_Math;
import mcheli.MCH_Queue;
import mcheli.MCH_Vector2;
import mcheli.MCH_ViewEntityDummy;
import mcheli.chain.MCH_EntityChain;
import mcheli.command.MCH_Command;
import mcheli.flare.MCH_Flare;
import mcheli.multiplay.MCH_Multiplay;
import mcheli.parachute.MCH_EntityParachute;
import mcheli.particles.MCH_ParticleParam;
import mcheli.particles.MCH_ParticlesUtil;
import mcheli.tool.MCH_ItemWrench;
import mcheli.uav.MCH_EntityUavStation;
import mcheli.weapon.MCH_EntityTvMissile;
import mcheli.weapon.MCH_IEntityLockChecker;
import mcheli.weapon.MCH_WeaponBase;
import mcheli.weapon.MCH_WeaponCreator;
import mcheli.weapon.MCH_WeaponDummy;
import mcheli.weapon.MCH_WeaponInfo;
import mcheli.weapon.MCH_WeaponParam;
import mcheli.weapon.MCH_WeaponSet;
import mcheli.weapon.MCH_WeaponSmoke;
import mcheli.wrapper.W_AxisAlignedBB;
import mcheli.wrapper.W_Block;
import mcheli.wrapper.W_Blocks;
import mcheli.wrapper.W_Entity;
import mcheli.wrapper.W_EntityContainer;
import mcheli.wrapper.W_EntityPlayer;
import mcheli.wrapper.W_EntityRenderer;
import mcheli.wrapper.W_Item;
import mcheli.wrapper.W_Lib;
import mcheli.wrapper.W_NBTTag;
import mcheli.wrapper.W_Reflection;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityMinecartEmpty;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ReportedException;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

public abstract class MCH_EntityAircraft extends W_EntityContainer implements MCH_IEntityLockChecker, MCH_IEntityCanRideAircraft, IEntityAdditionalSpawnData {
   private static final int DATAWT_ID_DAMAGE = 19;
   private static final int DATAWT_ID_TYPE = 20;
   private static final int DATAWT_ID_TEXTURE_NAME = 21;
   private static final int DATAWT_ID_UAV_STATION = 22;
   private static final int DATAWT_ID_STATUS = 23;
   private static final int CMN_ID_FLARE = 0;
   private static final int CMN_ID_FREE_LOOK = 1;
   private static final int CMN_ID_RELOADING = 2;
   private static final int CMN_ID_INGINITY_AMMO = 3;
   private static final int CMN_ID_INGINITY_FUEL = 4;
   private static final int CMN_ID_RAPELLING = 5;
   private static final int CMN_ID_SEARCHLIGHT = 6;
   private static final int CMN_ID_CNTRL_LEFT = 7;
   private static final int CMN_ID_CNTRL_RIGHT = 8;
   private static final int CMN_ID_CNTRL_UP = 9;
   private static final int CMN_ID_CNTRL_DOWN = 10;
   private static final int CMN_ID_CNTRL_BRAKE = 11;
   private static final int DATAWT_ID_USE_WEAPON = 24;
   private static final int DATAWT_ID_FUEL = 25;
   private static final int DATAWT_ID_ROT_ROLL = 26;
   private static final int DATAWT_ID_COMMAND = 27;
   private static final int DATAWT_ID_THROTTLE = 29;
   protected static final int DATAWT_ID_FOLD_STAT = 30;
   protected static final int DATAWT_ID_PART_STAT = 31;
   protected static final int PART_ID_CANOPY = 0;
   protected static final int PART_ID_NOZZLE = 1;
   protected static final int PART_ID_LANDINGGEAR = 2;
   protected static final int PART_ID_WING = 3;
   protected static final int PART_ID_HATCH = 4;
   public static final byte LIMIT_GROUND_PITCH = 40;
   public static final byte LIMIT_GROUND_ROLL = 40;
   public boolean isRequestedSyncStatus;
   private MCH_AircraftInfo acInfo;
   private int commonStatus;
   private Entity[] partEntities;
   private MCH_EntityHitBox pilotSeat;
   private MCH_EntitySeat[] seats;
   private MCH_SeatInfo[] seatsInfo;
   private String commonUniqueId;
   private int seatSearchCount;
   protected double velocityX;
   protected double velocityY;
   protected double velocityZ;
   public boolean keepOnRideRotation;
   protected int aircraftPosRotInc;
   protected double aircraftX;
   protected double aircraftY;
   protected double aircraftZ;
   protected double aircraftYaw;
   protected double aircraftPitch;
   public boolean aircraftRollRev;
   public boolean aircraftRotChanged;
   public float rotationRoll;
   public float prevRotationRoll;
   private double currentThrottle;
   private double prevCurrentThrottle;
   public double currentSpeed;
   public int currentFuel;
   public float throttleBack = 0.0F;
   public double beforeHoverThrottle;
   public int waitMountEntity = 0;
   public boolean throttleUp = false;
   public boolean throttleDown = false;
   public boolean moveLeft = false;
   public boolean moveRight = false;
   public MCH_LowPassFilterFloat lowPassPartialTicks;
   private MCH_Radar entityRadar;
   private int radarRotate;
   private MCH_Flare flareDv;
   private int currentFlareIndex;
   protected MCH_WeaponSet[] weapons;
   protected int[] currentWeaponID;
   public float lastRiderYaw;
   public float prevLastRiderYaw;
   public float lastRiderPitch;
   public float prevLastRiderPitch;
   protected MCH_WeaponSet dummyWeapon;
   protected int useWeaponStat;
   protected int hitStatus;
   protected final MCH_SoundUpdater soundUpdater;
   protected Entity lastRiddenByEntity;
   protected Entity lastRidingEntity;
   public List<MCH_EntityAircraft.UnmountReserve> listUnmountReserve = new ArrayList<>();
   private int countOnUpdate;
   private MCH_EntityChain towChainEntity;
   private MCH_EntityChain towedChainEntity;
   public MCH_Camera camera;
   private int cameraId;
   protected boolean isGunnerMode = false;
   protected boolean isGunnerModeOtherSeat = false;
   private boolean isHoveringMode = false;
   public static final int CAMERA_PITCH_MIN = -30;
   public static final int CAMERA_PITCH_MAX = 70;
   private MCH_EntityTvMissile TVmissile;
   protected boolean isGunnerFreeLookMode = false;
   public final MCH_MissileDetector missileDetector;
   public int serverNoMoveCount = 0;
   public int repairCount;
   public int beforeDamageTaken;
   public int timeSinceHit;
   private int despawnCount;
   public float rotDestroyedYaw;
   public float rotDestroyedPitch;
   public float rotDestroyedRoll;
   public int damageSinceDestroyed;
   public boolean isFirstDamageSmoke = true;
   public Vec3[] prevDamageSmokePos = new Vec3[0];
   private MCH_EntityUavStation uavStation;
   public boolean cs_dismountAll;
   public boolean cs_heliAutoThrottleDown;
   public boolean cs_planeAutoThrottleDown;
   public boolean cs_tankAutoThrottleDown;
   public MCH_Parts partHatch;
   public MCH_Parts partCanopy;
   public MCH_Parts partLandingGear;
   public double prevRidingEntityPosX;
   public double prevRidingEntityPosY;
   public double prevRidingEntityPosZ;
   public boolean canRideRackStatus;
   private int modeSwitchCooldown;
   public MCH_BoundingBox[] extraBoundingBox;
   public float lastBBDamageFactor;
   private final MCH_AircraftInventory inventory;
   private double fuelConsumption;
   private int fuelSuppliedCount;
   private int supplyAmmoWait;
   private boolean beforeSupplyAmmo;
   public MCH_EntityAircraft.WeaponBay[] weaponBays;
   public float[] rotPartRotation;
   public float[] prevRotPartRotation;
   public float[] rotCrawlerTrack = new float[2];
   public float[] prevRotCrawlerTrack = new float[2];
   public float[] throttleCrawlerTrack = new float[2];
   public float[] rotTrackRoller = new float[2];
   public float[] prevRotTrackRoller = new float[2];
   public float rotWheel = 0.0F;
   public float prevRotWheel = 0.0F;
   public float rotYawWheel = 0.0F;
   public float prevRotYawWheel = 0.0F;
   private boolean isParachuting;
   public float ropesLength = 0.0F;
   private MCH_Queue<Vec3> prevPosition;
   private int tickRepelling;
   private int lastUsedRopeIndex;
   private boolean dismountedUserCtrl;
   public float lastSearchLightYaw;
   public float lastSearchLightPitch;
   public float rotLightHatch = 0.0F;
   public float prevRotLightHatch = 0.0F;
   public int recoilCount = 0;
   public float recoilYaw = 0.0F;
   public float recoilValue = 0.0F;
   public int brightnessHigh = 240;
   public int brightnessLow = 240;
   public final HashMap<Entity, Integer> noCollisionEntities = new HashMap<>();
   private double lastCalcLandInDistanceCount;
   private double lastLandInDistance;
   private static final MCH_EntitySeat[] seatsDummy = new MCH_EntitySeat[0];
   private boolean switchSeat = false;

   public MCH_EntityAircraft(World world) {
      super(world);
      this.isRequestedSyncStatus = false;
      this.setAcInfo(null);
      this.commonStatus = 0;
      this.dropContentsWhenDead = false;
      this.ignoreFrustumCheck = true;
      this.flareDv = new MCH_Flare(world, this);
      this.currentFlareIndex = 0;
      this.entityRadar = new MCH_Radar(world);
      this.radarRotate = 0;
      this.currentWeaponID = new int[0];
      this.aircraftPosRotInc = 0;
      this.aircraftX = 0.0;
      this.aircraftY = 0.0;
      this.aircraftZ = 0.0;
      this.aircraftYaw = 0.0;
      this.aircraftPitch = 0.0;
      this.currentSpeed = 0.0;
      this.setCurrentThrottle(0.0);
      this.currentFuel = 0;
      this.cs_dismountAll = false;
      this.cs_heliAutoThrottleDown = true;
      this.cs_planeAutoThrottleDown = false;
      this.renderDistanceWeight = MCH_Config.RenderDistanceWeight.prmDouble;
      this.setCommonUniqueId("");
      this.seatSearchCount = 0;
      this.seatsInfo = null;
      this.seats = new MCH_EntitySeat[0];
      this.pilotSeat = new MCH_EntityHitBox(world, this, 1.0F, 1.0F);
      this.pilotSeat.parent = this;
      this.partEntities = new Entity[]{this.pilotSeat};
      this.setTextureName("");
      this.camera = new MCH_Camera(world, this, this.posX, this.posY, this.posZ);
      this.setCameraId(0);
      this.lastRiddenByEntity = null;
      this.lastRidingEntity = null;
      this.soundUpdater = MCH_MOD.proxy.CreateSoundUpdater(this);
      this.countOnUpdate = 0;
      this.setTowChainEntity(null);
      this.dummyWeapon = new MCH_WeaponSet(new MCH_WeaponDummy(this.worldObj, Vec3.createVectorHelper(0.0, 0.0, 0.0), 0.0F, 0.0F, "", null));
      this.useWeaponStat = 0;
      this.hitStatus = 0;
      this.repairCount = 0;
      this.beforeDamageTaken = 0;
      this.timeSinceHit = 0;
      this.setDespawnCount(0);
      this.missileDetector = new MCH_MissileDetector(this, world);
      this.uavStation = null;
      this.modeSwitchCooldown = 0;
      this.partHatch = null;
      this.partCanopy = null;
      this.partLandingGear = null;
      this.weaponBays = new MCH_EntityAircraft.WeaponBay[0];
      this.rotPartRotation = new float[0];
      this.prevRotPartRotation = new float[0];
      this.lastRiderYaw = 0.0F;
      this.prevLastRiderYaw = 0.0F;
      this.lastRiderPitch = 0.0F;
      this.prevLastRiderPitch = 0.0F;
      this.rotationRoll = 0.0F;
      this.prevRotationRoll = 0.0F;
      this.lowPassPartialTicks = new MCH_LowPassFilterFloat(10);
      this.extraBoundingBox = new MCH_BoundingBox[0];
      W_Reflection.setBoundingBox(this, new MCH_AircraftBoundingBox(this));
      this.lastBBDamageFactor = 1.0F;
      this.inventory = new MCH_AircraftInventory(this);
      this.fuelConsumption = 0.0;
      this.fuelSuppliedCount = 0;
      this.canRideRackStatus = false;
      this.isParachuting = false;
      this.prevPosition = new MCH_Queue<>(10, Vec3.createVectorHelper(0.0, 0.0, 0.0));
      this.lastSearchLightYaw = this.lastSearchLightPitch = 0.0F;
   }

   @Override
   protected void entityInit() {
      super.entityInit();
      this.getDataWatcher().addObject(20, "");
      this.getDataWatcher().addObject(19, new Integer(0));
      this.getDataWatcher().addObject(23, new Integer(0));
      this.getDataWatcher().addObject(24, new Integer(0));
      this.getDataWatcher().addObject(25, new Integer(0));
      this.getDataWatcher().addObject(21, "");
      this.getDataWatcher().addObject(22, new Integer(0));
      this.getDataWatcher().addObject(26, new Short((short)0));
      this.getDataWatcher().addObject(27, new String(""));
      this.getDataWatcher().addObject(29, new Integer(0));
      this.getDataWatcher().addObject(31, new Integer(0));
      if (!this.worldObj.isRemote) {
         this.setCommonStatus(3, MCH_Config.InfinityAmmo.prmBool);
         this.setCommonStatus(4, MCH_Config.InfinityFuel.prmBool);
      }

      this.getEntityData().setString("EntityType", this.getEntityType());
   }

   public float getServerRoll() {
      return this.getDataWatcher().getWatchableObjectShort(26);
   }

   public float getRotYaw() {
      return this.rotationYaw;
   }

   public float getRotPitch() {
      return this.rotationPitch;
   }

   public float getRotRoll() {
      return this.rotationRoll;
   }

   public void setRotYaw(float f) {
      this.rotationYaw = f;
   }

   public void setRotPitch(float f) {
      this.rotationPitch = f;
   }

   public void setRotPitch(float f, String msg) {
      this.setRotPitch(f);
   }

   public void setRotRoll(float f) {
      this.rotationRoll = f;
   }

   public void applyOnGroundPitch(float factor) {
      if (this.getAcInfo() != null) {
         float ogp = this.getAcInfo().onGroundPitch;
         float pitch = this.getRotPitch();
         pitch -= ogp;
         pitch *= factor;
         pitch += ogp;
         this.setRotPitch(pitch, "applyOnGroundPitch");
      }

      this.setRotRoll(this.getRotRoll() * factor);
   }

   public float calcRotYaw(float partialTicks) {
      return this.prevRotationYaw + (this.getRotYaw() - this.prevRotationYaw) * partialTicks;
   }

   public float calcRotPitch(float partialTicks) {
      return this.prevRotationPitch + (this.getRotPitch() - this.prevRotationPitch) * partialTicks;
   }

   public float calcRotRoll(float partialTicks) {
      return this.prevRotationRoll + (this.getRotRoll() - this.prevRotationRoll) * partialTicks;
   }

   protected void setRotation(float y, float p) {
      this.setRotYaw(y % 360.0F);
      this.setRotPitch(p % 360.0F);
   }

   public boolean isInfinityAmmo(Entity player) {
      return this.isCreative(player) || this.getCommonStatus(3);
   }

   public boolean isInfinityFuel(Entity player, boolean checkOtherSeet) {
      if (!this.isCreative(player) && !this.getCommonStatus(4)) {
         if (checkOtherSeet) {
            for (MCH_EntitySeat seat : this.getSeats()) {
               if (seat != null && this.isCreative(seat.riddenByEntity)) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return true;
      }
   }

   public void setCommand(String s, EntityPlayer player) {
      if (!this.worldObj.isRemote && MCH_Command.canUseCommand(player)) {
         this.setCommandForce(s);
      }
   }

   public void setCommandForce(String s) {
      if (!this.worldObj.isRemote) {
         this.getDataWatcher().updateObject(27, s);
      }
   }

   public String getCommand() {
      return this.getDataWatcher().getWatchableObjectString(27);
   }

   public String getKindName() {
      return "";
   }

   public String getEntityType() {
      return "";
   }

   public void setTypeName(String s) {
      String beforeType = this.getTypeName();
      if (s != null && !s.isEmpty() && s.compareTo(beforeType) != 0) {
         this.getDataWatcher().updateObject(20, String.valueOf(s));
         this.changeType(s);
         this.initRotationYaw(this.getRotYaw());
      }
   }

   public String getTypeName() {
      return this.getDataWatcher().getWatchableObjectString(20);
   }

   public abstract void changeType(String var1);

   public boolean isTargetDrone() {
      return this.getAcInfo() != null && this.getAcInfo().isTargetDrone;
   }

   public boolean isUAV() {
      return this.getAcInfo() != null && this.getAcInfo().isUAV;
   }

   public boolean isSmallUAV() {
      return this.getAcInfo() != null && this.getAcInfo().isSmallUAV;
   }

   public boolean isAlwaysCameraView() {
      return this.getAcInfo() != null && this.getAcInfo().alwaysCameraView;
   }

   public void setUavStation(MCH_EntityUavStation uavSt) {
      this.uavStation = uavSt;
      if (!this.worldObj.isRemote) {
         if (uavSt != null) {
            this.getDataWatcher().updateObject(22, W_Entity.getEntityId(uavSt));
         } else {
            this.getDataWatcher().updateObject(22, 0);
         }
      }
   }

   public float getStealth() {
      return this.getAcInfo() != null ? this.getAcInfo().stealth : 0.0F;
   }

   public MCH_AircraftInventory getGuiInventory() {
      return this.inventory;
   }

   public void openGui(EntityPlayer player) {
      if (!this.worldObj.isRemote) {
         player.openGui(MCH_MOD.instance, 1, this.worldObj, (int)this.posX, (int)this.posY, (int)this.posZ);
      }
   }

   public MCH_EntityUavStation getUavStation() {
      return this.isUAV() ? this.uavStation : null;
   }

   public static MCH_EntityAircraft getAircraft_RiddenOrControl(Entity rider) {
      if (rider != null) {
         if (rider.ridingEntity instanceof MCH_EntityAircraft) {
            return (MCH_EntityAircraft)rider.ridingEntity;
         }

         if (rider.ridingEntity instanceof MCH_EntitySeat) {
            return ((MCH_EntitySeat)rider.ridingEntity).getParent();
         }

         if (rider.ridingEntity instanceof MCH_EntityUavStation) {
            MCH_EntityUavStation uavStation = (MCH_EntityUavStation)rider.ridingEntity;
            return uavStation.getControlAircract();
         }
      }

      return null;
   }

   public boolean isCreative(Entity entity) {
      return entity instanceof EntityPlayer && ((EntityPlayer)entity).capabilities.isCreativeMode;
   }

   public Entity getRiddenByEntity() {
      return this.isUAV() && this.uavStation != null ? this.uavStation.riddenByEntity : this.riddenByEntity;
   }

   public boolean getCommonStatus(int bit) {
      return (this.commonStatus >> bit & 1) != 0;
   }

   public void setCommonStatus(int bit, boolean b) {
      this.setCommonStatus(bit, b, false);
   }

   public void setCommonStatus(int bit, boolean b, boolean writeClient) {
      if (!this.worldObj.isRemote || writeClient) {
         int bofore = this.commonStatus;
         int mask = 1 << bit;
         if (b) {
            this.commonStatus |= mask;
         } else {
            this.commonStatus &= ~mask;
         }

         if (bofore != this.commonStatus) {
            this.getDataWatcher().updateObject(23, this.commonStatus);
         }
      }
   }

   public double getThrottle() {
      return 0.05 * this.getDataWatcher().getWatchableObjectInt(29);
   }

   public void setThrottle(double t) {
      int n = (int)(t * 20.0);
      if (n == 0 && t > 0.0) {
         n = 1;
      }

      this.getDataWatcher().updateObject(29, n);
   }

   public int getMaxHP() {
      return this.getAcInfo() != null ? this.getAcInfo().maxHp : 100;
   }

   public int getHP() {
      return this.getMaxHP() - this.getDamageTaken() >= 0 ? this.getMaxHP() - this.getDamageTaken() : 0;
   }

   public void setDamageTaken(int par1) {
      if (par1 < 0) {
         par1 = 0;
      }

      if (par1 > this.getMaxHP()) {
         par1 = this.getMaxHP();
      }

      this.getDataWatcher().updateObject(19, par1);
   }

   public int getDamageTaken() {
      return this.getDataWatcher().getWatchableObjectInt(19);
   }

   public void destroyAircraft() {
      this.setSearchLight(false);
      this.switchHoveringMode(false);
      this.switchGunnerMode(false);

      for (int i = 0; i < this.getSeatNum() + 1; i++) {
         Entity e = this.getEntityBySeatId(i);
         if (e instanceof EntityPlayer) {
            this.switchCameraMode((EntityPlayer)e, 0);
         }
      }

      if (this.isTargetDrone()) {
         this.setDespawnCount(50);
      } else {
         this.setDespawnCount(500);
      }

      this.rotDestroyedPitch = this.rand.nextFloat() - 0.5F;
      this.rotDestroyedRoll = (this.rand.nextFloat() - 0.5F) * 0.5F;
      this.rotDestroyedYaw = 0.0F;
      if (this.isUAV() && this.getRiddenByEntity() != null) {
         this.getRiddenByEntity().mountEntity(null);
      }

      if (!this.worldObj.isRemote) {
         this.ejectSeat(this.getRiddenByEntity());
         Entity entity = this.getEntityBySeatId(1);
         if (entity != null) {
            this.ejectSeat(entity);
         }
      }
   }

   public boolean isDestroyed() {
      return this.getDespawnCount() > 0;
   }

   public int getDespawnCount() {
      return this.despawnCount;
   }

   public void setDespawnCount(int despawnCount) {
      this.despawnCount = despawnCount;
   }

   public boolean isEntityRadarMounted() {
      return this.getAcInfo() != null ? this.getAcInfo().isEnableEntityRadar : false;
   }

   public boolean canFloatWater() {
      return this.getAcInfo() != null && this.getAcInfo().isFloat && !this.isDestroyed();
   }

   @SideOnly(Side.CLIENT)
   public int getBrightnessForRender(float par1) {
      if (this.haveSearchLight() && this.isSearchLightON()) {
         return 15728880;
      }

      int i = MathHelper.floor_double(this.posX);
      int j = MathHelper.floor_double(this.posZ);
      if (this.worldObj.blockExists(i, 0, j)) {
         double d0 = (this.boundingBox.maxY - this.boundingBox.minY) * 0.66;
         float fo = this.getAcInfo() != null ? this.getAcInfo().submergedDamageHeight : 0.0F;
         if (this.canFloatWater()) {
            fo = this.getAcInfo().floatOffset;
            if (fo < 0.0F) {
               fo = -fo;
            }

            fo++;
         }

         int k = MathHelper.floor_double(this.posY + fo - this.yOffset + d0);
         int val = this.worldObj.getLightBrightnessForSkyBlocks(i, k, j, 0);
         int low = val & 65535;
         int high = val >> 16 & 65535;
         if (high < this.brightnessHigh) {
            if (this.brightnessHigh > 0 && this.getCountOnUpdate() % 2 == 0) {
               this.brightnessHigh--;
            }
         } else if (high > this.brightnessHigh) {
            this.brightnessHigh += 4;
            if (this.brightnessHigh > 240) {
               this.brightnessHigh = 240;
            }
         }

         return this.brightnessHigh << 16 | low;
      } else {
         return 0;
      }
   }

   public MCH_AircraftInfo.CameraPosition getCameraPosInfo() {
      if (this.getAcInfo() == null) {
         return null;
      } else {
         Entity player = MCH_Lib.getClientPlayer();
         int sid = this.getSeatIdByEntity(player);
         if (sid == 0 && this.canSwitchCameraPos() && this.getCameraId() > 0 && this.getCameraId() < this.getAcInfo().cameraPosition.size()) {
            return this.getAcInfo().cameraPosition.get(this.getCameraId());
         } else {
            return sid > 0 && sid < this.getSeatsInfo().length && this.getSeatsInfo()[sid].invCamPos
               ? this.getSeatsInfo()[sid].getCamPos()
               : this.getAcInfo().cameraPosition.get(0);
         }
      }
   }

   public int getCameraId() {
      return this.cameraId;
   }

   public void setCameraId(int cameraId) {
      MCH_Lib.DbgLog(true, "MCH_EntityAircraft.setCameraId %d -> %d", this.cameraId, cameraId);
      this.cameraId = cameraId;
   }

   public boolean canSwitchCameraPos() {
      return this.getCameraPosNum() >= 2;
   }

   public int getCameraPosNum() {
      return this.getAcInfo() != null ? this.getAcInfo().cameraPosition.size() : 1;
   }

   public void onAcInfoReloaded() {
      if (this.getAcInfo() != null) {
         this.setSize(this.getAcInfo().bodyWidth, this.getAcInfo().bodyHeight);
      }
   }

   public void writeSpawnData(ByteBuf buffer) {
      if (this.getAcInfo() != null) {
         buffer.writeFloat(this.getAcInfo().bodyHeight);
         buffer.writeFloat(2.0F);
      } else {
         buffer.writeFloat(this.height);
         buffer.writeFloat(this.width);
      }
   }

   public void readSpawnData(ByteBuf additionalData) {
      try {
         float height = additionalData.readFloat();
         float width = additionalData.readFloat();
         this.setSize(width, height);
      } catch (Exception e) {
         MCH_Lib.Log(this, "readSpawnData error!");
         e.printStackTrace();
      }
   }

   @Override
   protected void readEntityFromNBT(NBTTagCompound nbt) {
      this.setDespawnCount(nbt.getInteger("AcDespawnCount"));
      this.setTextureName(nbt.getString("TextureName"));
      this.setCommonUniqueId(nbt.getString("AircraftUniqueId"));
      this.setRotRoll(nbt.getFloat("AcRoll"));
      this.prevRotationRoll = this.getRotRoll();
      this.prevLastRiderYaw = this.lastRiderYaw = nbt.getFloat("AcLastRYaw");
      this.prevLastRiderPitch = this.lastRiderPitch = nbt.getFloat("AcLastRPitch");
      this.setPartStatus(nbt.getInteger("PartStatus"));
      this.setTypeName(nbt.getString("TypeName"));
      super.readEntityFromNBT(nbt);
      this.getGuiInventory().readEntityFromNBT(nbt);
      this.setCommandForce(nbt.getString("AcCommand"));
      this.setFuel(nbt.getInteger("AcFuel"));
      int[] wa_list = nbt.getIntArray("AcWeaponsAmmo");

      for (int i = 0; i < wa_list.length; i++) {
         this.getWeapon(i).setRestAllAmmoNum(wa_list[i]);
         this.getWeapon(i).reloadMag();
      }

      if (this.getDespawnCount() > 0) {
         this.setDamageTaken(this.getMaxHP());
      } else if (nbt.hasKey("AcDamage")) {
         this.setDamageTaken(nbt.getInteger("AcDamage"));
      }

      if (this.haveSearchLight() && nbt.hasKey("SearchLight")) {
         this.setSearchLight(nbt.getBoolean("SearchLight"));
      }

      this.dismountedUserCtrl = nbt.getBoolean("AcDismounted");
   }

   @Override
   protected void writeEntityToNBT(NBTTagCompound nbt) {
      nbt.setString("TextureName", this.getTextureName());
      nbt.setString("AircraftUniqueId", this.getCommonUniqueId());
      nbt.setString("TypeName", this.getTypeName());
      nbt.setInteger("PartStatus", this.getPartStatus() & this.getLastPartStatusMask());
      nbt.setInteger("AcFuel", this.getFuel());
      nbt.setInteger("AcDespawnCount", this.getDespawnCount());
      nbt.setFloat("AcRoll", this.getRotRoll());
      nbt.setBoolean("SearchLight", this.isSearchLightON());
      nbt.setFloat("AcLastRYaw", this.getLastRiderYaw());
      nbt.setFloat("AcLastRPitch", this.getLastRiderPitch());
      nbt.setString("AcCommand", this.getCommand());
      super.writeEntityToNBT(nbt);
      this.getGuiInventory().writeEntityToNBT(nbt);
      int[] wa_list = new int[this.getWeaponNum()];

      for (int i = 0; i < wa_list.length; i++) {
         wa_list[i] = this.getWeapon(i).getRestAllAmmoNum() + this.getWeapon(i).getAmmoNum();
      }

      nbt.setTag("AcWeaponsAmmo", W_NBTTag.newTagIntArray("AcWeaponsAmmo", wa_list));
      nbt.setInteger("AcDamage", this.getDamageTaken());
      nbt.setBoolean("AcDismounted", this.dismountedUserCtrl);
   }

   @Override
   public boolean attackEntityFrom(DamageSource damageSource, float org_damage) {
      float damage = org_damage;
      float damageFactor = this.lastBBDamageFactor;
      this.lastBBDamageFactor = 1.0F;
      if (this.isEntityInvulnerable()) {
         return false;
      }

      if (this.isDead) {
         return false;
      }

      if (this.timeSinceHit > 0) {
         return false;
      }

      String dmt = damageSource.getDamageType();
      if (dmt.equalsIgnoreCase("inFire")) {
         return false;
      }

      if (dmt.equalsIgnoreCase("cactus")) {
         return false;
      }

      if (this.worldObj.isRemote) {
         return true;
      }

      damage = MCH_Config.applyDamageByExternal(this, damageSource, damage);
      if (!MCH_Multiplay.canAttackEntity(damageSource, this)) {
         return false;
      }

      if (dmt.equalsIgnoreCase("lava")) {
         damage *= this.rand.nextInt(8) + 2;
         this.timeSinceHit = 2;
      }

      if (dmt.startsWith("explosion")) {
         this.timeSinceHit = 1;
      } else if (this.isMountedEntity(damageSource.getEntity())) {
         return false;
      }

      if (dmt.equalsIgnoreCase("onFire")) {
         this.timeSinceHit = 10;
      }

      boolean isCreative = false;
      boolean isSneaking = false;
      Entity entity = damageSource.getEntity();
      boolean isDamegeSourcePlayer = false;
      boolean playDamageSound = false;
      if (entity instanceof EntityPlayer) {
         EntityPlayer player = (EntityPlayer)entity;
         isCreative = player.capabilities.isCreativeMode;
         isSneaking = player.isSneaking();
         if (dmt.equalsIgnoreCase("player")) {
            if (isCreative) {
               isDamegeSourcePlayer = true;
            } else if (!MCH_Config.PreventingBroken.prmBool) {
               if (MCH_Config.BreakableOnlyPickaxe.prmBool) {
                  if (player.getCurrentEquippedItem() != null && player.getCurrentEquippedItem().getItem() instanceof ItemPickaxe) {
                     isDamegeSourcePlayer = true;
                  }
               } else {
                  isDamegeSourcePlayer = !this.isRidePlayer();
               }
            }
         }

         W_WorldFunc.MOD_playSoundAtEntity(this, "hit", 1.0F, 1.0F);
      } else {
         playDamageSound = true;
      }

      if (!this.isDestroyed()) {
         if (!isDamegeSourcePlayer) {
            MCH_AircraftInfo acInfo = this.getAcInfo();
            if (acInfo != null && !dmt.equalsIgnoreCase("lava") && !dmt.equalsIgnoreCase("onFire")) {
               if (damage > acInfo.armorMaxDamage) {
                  damage = acInfo.armorMaxDamage;
               }

               if (damageFactor <= 1.0F) {
                  damage *= damageFactor;
               }

               damage *= acInfo.armorDamageFactor;
               damage -= acInfo.armorMinDamage;
               if (damage <= 0.0F) {
                  MCH_Lib.DbgLog(
                     this.worldObj, "MCH_EntityAircraft.attackEntityFrom:no damage=%.1f -> %.1f(factor=%.2f):%s", org_damage, damage, damageFactor, dmt
                  );
                  return false;
               }

               if (damageFactor > 1.0F) {
                  damage *= damageFactor;
               }
            }

            MCH_Lib.DbgLog(this.worldObj, "MCH_EntityAircraft.attackEntityFrom:damage=%.1f(factor=%.2f):%s", damage, damageFactor, dmt);
            this.setDamageTaken(this.getDamageTaken() + (int)damage);
         }

         this.setBeenAttacked();
         if (this.getDamageTaken() >= this.getMaxHP() || isDamegeSourcePlayer) {
            if (!isDamegeSourcePlayer) {
               this.setDamageTaken(this.getMaxHP());
               this.destroyAircraft();
               this.timeSinceHit = 20;
               String cmd = this.getCommand().trim();
               if (cmd.startsWith("/")) {
                  cmd = cmd.substring(1);
               }

               if (!cmd.isEmpty()) {
                  MCH_DummyCommandSender.execCommand(cmd);
               }

               if (dmt.equalsIgnoreCase("inWall")) {
                  this.explosionByCrash(0.0);
                  this.damageSinceDestroyed = this.getMaxHP();
               } else {
                  MCH_Explosion.newExplosion(
                     this.worldObj, null, entity, this.posX, this.posY, this.posZ, 2.0F, 2.0F, true, true, true, true, 5
                  );
               }
            } else {
               if (this.getAcInfo() != null && this.getAcInfo().getItem() != null) {
                  if (isCreative) {
                     if (MCH_Config.DropItemInCreativeMode.prmBool && !isSneaking) {
                        this.dropItemWithOffset(this.getAcInfo().getItem(), 1, 0.0F);
                     }

                     if (!MCH_Config.DropItemInCreativeMode.prmBool && isSneaking) {
                        this.dropItemWithOffset(this.getAcInfo().getItem(), 1, 0.0F);
                     }
                  } else {
                     this.dropItemWithOffset(this.getAcInfo().getItem(), 1, 0.0F);
                  }
               }

               this.setDead(true);
            }
         }
      } else if (isDamegeSourcePlayer && isCreative) {
         this.setDead(true);
      }

      if (playDamageSound) {
         W_WorldFunc.MOD_playSoundAtEntity(this, "helidmg", 1.0F, 0.9F + this.rand.nextFloat() * 0.1F);
      }

      return true;
   }

   public boolean isExploded() {
      return this.isDestroyed() && this.damageSinceDestroyed > this.getMaxHP() / 10 + 1;
   }

   public void destruct() {
      if (this.getRiddenByEntity() != null) {
         this.getRiddenByEntity().mountEntity(null);
      }

      this.setDead(true);
   }

   public EntityItem entityDropItem(ItemStack is, float par2) {
      if (is.stackSize == 0) {
         return null;
      }

      this.setAcDataToItem(is);
      return super.entityDropItem(is, par2);
   }

   public void setAcDataToItem(ItemStack is) {
      if (!is.hasTagCompound()) {
         is.setTagCompound(new NBTTagCompound());
      }

      NBTTagCompound nbt = is.getTagCompound();
      nbt.setString("MCH_Command", this.getCommand());
      if (MCH_Config.ItemFuel.prmBool) {
         nbt.setInteger("MCH_Fuel", this.getFuel());
      }

      if (MCH_Config.ItemDamage.prmBool) {
         is.setItemDamage(this.getDamageTaken());
      }
   }

   public void getAcDataFromItem(ItemStack is) {
      if (is.hasTagCompound()) {
         NBTTagCompound nbt = is.getTagCompound();
         this.setCommandForce(nbt.getString("MCH_Command"));
         if (MCH_Config.ItemFuel.prmBool) {
            this.setFuel(nbt.getInteger("MCH_Fuel"));
         }

         if (MCH_Config.ItemDamage.prmBool) {
            this.setDamageTaken(is.getItemDamage());
         }
      }
   }

   @Override
   public boolean isUseableByPlayer(EntityPlayer player) {
      if (this.isUAV()) {
         return super.isUseableByPlayer(player);
      } else if (!this.isDead) {
         return this.getSeatIdByEntity(player) >= 0 ? player.getDistanceSqToEntity(this) <= 4096.0 : player.getDistanceSqToEntity(this) <= 64.0;
      } else {
         return false;
      }
   }

   public void applyEntityCollision(Entity par1Entity) {
   }

   public void addVelocity(double par1, double par3, double par5) {
   }

   public void setVelocity(double par1, double par3, double par5) {
      this.velocityX = this.motionX = par1;
      this.velocityY = this.motionY = par3;
      this.velocityZ = this.motionZ = par5;
   }

   public void onFirstUpdate() {
      if (!this.worldObj.isRemote) {
         this.setCommonStatus(3, MCH_Config.InfinityAmmo.prmBool);
         this.setCommonStatus(4, MCH_Config.InfinityFuel.prmBool);
      }
   }

   public void onRidePilotFirstUpdate() {
      if (this.worldObj.isRemote && W_Lib.isClientPlayer(this.getRiddenByEntity())) {
         this.updateClientSettings(0);
      }

      Entity pilot = this.getRiddenByEntity();
      if (pilot != null) {
         pilot.rotationYaw = this.getLastRiderYaw();
         pilot.rotationPitch = this.getLastRiderPitch();
      }

      this.keepOnRideRotation = false;
      if (this.getAcInfo() != null) {
         this.switchFreeLookModeClient(this.getAcInfo().defaultFreelook);
      }
   }

   public double getCurrentThrottle() {
      return this.currentThrottle;
   }

   public void setCurrentThrottle(double throttle) {
      this.currentThrottle = throttle;
   }

   public void addCurrentThrottle(double throttle) {
      this.setCurrentThrottle(this.getCurrentThrottle() + throttle);
   }

   public double getPrevCurrentThrottle() {
      return this.prevCurrentThrottle;
   }

   public boolean canMouseRot() {
      return !this.isDead && this.getRiddenByEntity() != null && !this.isDestroyed();
   }

   public boolean canUpdateYaw(Entity player) {
      if (this.getRidingEntity() != null) {
         return false;
      } else {
         return this.getCountOnUpdate() < 30 ? false : MCH_Lib.getBlockIdY(this, 3, -2) == 0;
      }
   }

   public boolean canUpdatePitch(Entity player) {
      return this.getCountOnUpdate() < 30 ? false : MCH_Lib.getBlockIdY(this, 3, -2) == 0;
   }

   public boolean canUpdateRoll(Entity player) {
      if (this.getRidingEntity() != null) {
         return false;
      } else {
         return this.getCountOnUpdate() < 30 ? false : MCH_Lib.getBlockIdY(this, 3, -2) == 0;
      }
   }

   public boolean isOverridePlayerYaw() {
      return !this.isFreeLookMode();
   }

   public boolean isOverridePlayerPitch() {
      return !this.isFreeLookMode();
   }

   public double getAddRotationYawLimit() {
      return this.getAcInfo() != null ? 40.0 * this.getAcInfo().mobilityYaw : 40.0;
   }

   public double getAddRotationPitchLimit() {
      return this.getAcInfo() != null ? 40.0 * this.getAcInfo().mobilityPitch : 40.0;
   }

   public double getAddRotationRollLimit() {
      return this.getAcInfo() != null ? 40.0 * this.getAcInfo().mobilityRoll : 40.0;
   }

   public float getYawFactor() {
      return 1.0F;
   }

   public float getPitchFactor() {
      return 1.0F;
   }

   public float getRollFactor() {
      return 1.0F;
   }

   public abstract void onUpdateAngles(float var1);

   public float getControlRotYaw(float mouseX, float mouseY, float tick) {
      return 0.0F;
   }

   public float getControlRotPitch(float mouseX, float mouseY, float tick) {
      return 0.0F;
   }

   public float getControlRotRoll(float mouseX, float mouseY, float tick) {
      return 0.0F;
   }

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
      if (this.canUpdateYaw(player)) {
         double limit = this.getAddRotationYawLimit();
         yaw = this.getControlRotYaw(x, y, partialTicks);
         if (yaw < -limit) {
            yaw = (float)(-limit);
         }

         if (yaw > limit) {
            yaw = (float)limit;
         }

         yaw = (float)(yaw * this.getYawFactor() * 0.06 * partialTicks);
      }

      if (this.canUpdatePitch(player)) {
         double limit = this.getAddRotationPitchLimit();
         pitch = this.getControlRotPitch(x, y, partialTicks);
         if (pitch < -limit) {
            pitch = (float)(-limit);
         }

         if (pitch > limit) {
            pitch = (float)limit;
         }

         pitch = (float)(-pitch * this.getPitchFactor() * 0.06 * partialTicks);
      }

      if (this.canUpdateRoll(player)) {
         double limit = this.getAddRotationRollLimit();
         roll = this.getControlRotRoll(x, y, partialTicks);
         if (roll < -limit) {
            roll = (float)(-limit);
         }

         if (roll > limit) {
            roll = (float)limit;
         }

         roll = roll * this.getRollFactor() * 0.06F * partialTicks;
      }

      MCH_Math.FMatrix m_add = MCH_Math.newMatrix();
      MCH_Math.MatTurnZ(m_add, roll / 180.0F * (float) Math.PI);
      MCH_Math.MatTurnX(m_add, pitch / 180.0F * (float) Math.PI);
      MCH_Math.MatTurnY(m_add, yaw / 180.0F * (float) Math.PI);
      MCH_Math.MatTurnZ(m_add, (float)(this.getRotRoll() / 180.0F * Math.PI));
      MCH_Math.MatTurnX(m_add, (float)(this.getRotPitch() / 180.0F * Math.PI));
      MCH_Math.MatTurnY(m_add, (float)(this.getRotYaw() / 180.0F * Math.PI));
      MCH_Math.FVector3D v = MCH_Math.MatrixToEuler(m_add);
      if (this.getAcInfo().limitRotation) {
         v.x = MCH_Lib.RNG(v.x, this.getAcInfo().minRotationPitch, this.getAcInfo().maxRotationPitch);
         v.z = MCH_Lib.RNG(v.z, this.getAcInfo().minRotationRoll, this.getAcInfo().maxRotationRoll);
      }

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
         v.x = MCH_Lib.RNG(this.getRotPitch(), this.getAcInfo().minRotationPitch, this.getAcInfo().maxRotationPitch);
         v.z = MCH_Lib.RNG(this.getRotRoll(), this.getAcInfo().minRotationRoll, this.getAcInfo().maxRotationRoll);
         this.setRotPitch(v.x);
         this.setRotRoll(v.z);
      }

      float RV = 180.0F;
      if (MathHelper.abs(this.getRotPitch()) > 90.0F) {
         MCH_Lib.DbgLog(true, "MCH_EntityAircraft.setAngles Error:Pitch=%.1f", this.getRotPitch());
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

      if (!this.isOverridePlayerYaw() && !fixRot) {
         player.setAngles(deltaX, 0.0F);
      } else {
         if (this.getRidingEntity() == null) {
            player.prevRotationYaw = this.getRotYaw() + (fixRot ? fixYaw : 0.0F);
         } else {
            if (this.getRotYaw() - player.rotationYaw > 180.0F) {
               player.prevRotationYaw += 360.0F;
            }

            if (this.getRotYaw() - player.rotationYaw < -180.0F) {
               player.prevRotationYaw -= 360.0F;
            }
         }

         player.rotationYaw = this.getRotYaw() + (fixRot ? fixYaw : 0.0F);
      }

      if (!this.isOverridePlayerPitch() && !fixRot) {
         player.setAngles(0.0F, deltaY);
      } else {
         player.prevRotationPitch = this.getRotPitch() + (fixRot ? fixPitch : 0.0F);
         player.rotationPitch = this.getRotPitch() + (fixRot ? fixPitch : 0.0F);
      }

      if (this.getRidingEntity() == null && ac_yaw != this.getRotYaw() || ac_pitch != this.getRotPitch() || ac_roll != this.getRotRoll()) {
         this.aircraftRotChanged = true;
      }
   }

   public boolean canSwitchSearchLight(Entity entity) {
      return this.haveSearchLight() && this.getSeatIdByEntity(entity) <= 1;
   }

   public boolean isSearchLightON() {
      return this.getCommonStatus(6);
   }

   public void setSearchLight(boolean onoff) {
      this.setCommonStatus(6, onoff);
   }

   public boolean haveSearchLight() {
      return this.getAcInfo() != null && this.getAcInfo().searchLights.size() > 0;
   }

   public float getSearchLightValue(Entity entity) {
      if (this.haveSearchLight() && this.isSearchLightON()) {
         for (MCH_AircraftInfo.SearchLight sl : this.getAcInfo().searchLights) {
            Vec3 pos = this.getTransformedPosition(sl.pos);
            double dist = entity.getDistanceSq(pos.xCoord, pos.yCoord, pos.zCoord);
            if (dist > 2.0 && dist < sl.height * sl.height + 20.0F) {
               double cx = entity.posX - pos.xCoord;
               double cy = entity.posY - pos.yCoord;
               double cz = entity.posZ - pos.zCoord;
               double h = 0.0;
               double v = 0.0;
               if (!sl.fixDir) {
                  Vec3 vv = MCH_Lib.RotVec3(0.0, 0.0, 1.0, -this.lastSearchLightYaw + sl.yaw, -this.lastSearchLightPitch + sl.pitch, -this.getRotRoll());
                  h = MCH_Lib.getPosAngle(vv.xCoord, vv.zCoord, cx, cz);
                  v = Math.atan2(cy, Math.sqrt(cx * cx + cz * cz)) * 180.0 / Math.PI;
                  v = Math.abs(v + this.lastSearchLightPitch + sl.pitch);
               } else {
                  float stRot = 0.0F;
                  if (sl.steering) {
                     stRot = this.rotYawWheel * sl.stRot;
                  }

                  Vec3 vv = MCH_Lib.RotVec3(0.0, 0.0, 1.0, -this.getRotYaw() + sl.yaw + stRot, -this.getRotPitch() + sl.pitch, -this.getRotRoll());
                  h = MCH_Lib.getPosAngle(vv.xCoord, vv.zCoord, cx, cz);
                  v = Math.atan2(cy, Math.sqrt(cx * cx + cz * cz)) * 180.0 / Math.PI;
                  v = Math.abs(v + this.getRotPitch() + sl.pitch);
               }

               float angle = sl.angle * 3.0F;
               if (h < angle && v < angle) {
                  float value = 0.0F;
                  if (h + v < angle) {
                     value = (float)(1440.0 * (1.0 - (h + v) / angle));
                  }

                  return value <= 240.0F ? value : 240.0F;
               }
            }
         }
      }

      return 0.0F;
   }

   public abstract void onUpdateAircraft();

   public void onUpdate() {
      if (this.getCountOnUpdate() < 2) {
         this.prevPosition.clear(Vec3.createVectorHelper(this.posX, this.posY, this.posZ));
      }

      this.prevCurrentThrottle = this.getCurrentThrottle();
      this.lastBBDamageFactor = 1.0F;
      this.updateControl();
      this.checkServerNoMove();
      this.onUpdate_RidingEntity();
      Iterator<MCH_EntityAircraft.UnmountReserve> itr = this.listUnmountReserve.iterator();

      while (itr.hasNext()) {
         MCH_EntityAircraft.UnmountReserve ur = itr.next();
         if (ur.entity != null && !ur.entity.isDead) {
            ur.entity.setPosition(ur.posX, ur.posY, ur.posZ);
            ur.entity.fallDistance = this.fallDistance;
         }

         if (ur.cnt > 0) {
            ur.cnt--;
         }

         if (ur.cnt == 0) {
            itr.remove();
         }
      }

      if (this.isDestroyed() && this.getCountOnUpdate() % 20 == 0) {
         for (int sid = 0; sid < this.getSeatNum() + 1; sid++) {
            Entity entity = this.getEntityBySeatId(sid);
            if (entity != null && (sid != 0 || !this.isUAV()) && MCH_Config.applyDamageVsEntity(entity, DamageSource.inFire, 1.0F) > 0.0F) {
               entity.setFire(5);
            }
         }
      }

      if ((this.aircraftRotChanged || this.aircraftRollRev) && this.worldObj.isRemote && this.getRiddenByEntity() != null) {
         MCH_PacketIndRotation.send(this);
         this.aircraftRotChanged = false;
         this.aircraftRollRev = false;
      }

      if (!this.worldObj.isRemote && (int)this.prevRotationRoll != (int)this.getRotRoll()) {
         float roll = MathHelper.wrapAngleTo180_float(this.getRotRoll());
         this.getDataWatcher().updateObject(26, new Short((short)roll));
      }

      this.prevRotationRoll = this.getRotRoll();
      if (!this.worldObj.isRemote && this.isTargetDrone() && !this.isDestroyed() && this.getCountOnUpdate() > 20 && !this.canUseFuel()) {
         this.setDamageTaken(this.getMaxHP());
         this.destroyAircraft();
         MCH_Explosion.newExplosion(
            this.worldObj, null, null, this.posX, this.posY, this.posZ, 2.0F, 2.0F, true, true, true, true, 5
         );
      }

      if (this.worldObj.isRemote && this.getAcInfo() != null && this.getHP() <= 0 && this.getDespawnCount() <= 0) {
         this.destroyAircraft();
      }

      if (!this.worldObj.isRemote && this.getDespawnCount() > 0) {
         this.setDespawnCount(this.getDespawnCount() - 1);
         if (this.getDespawnCount() <= 1) {
            this.setDead(true);
         }
      }

      super.onUpdate();
      if (this.getParts() != null) {
         for (Entity e : this.getParts()) {
            if (e != null) {
               e.onUpdate();
            }
         }
      }

      this.updateNoCollisionEntities();
      this.updateUAV();
      this.supplyFuel();
      this.supplyAmmoToOtherAircraft();
      this.updateFuel();
      this.repairOtherAircraft();
      if (this.modeSwitchCooldown > 0) {
         this.modeSwitchCooldown--;
      }

      if (this.lastRiddenByEntity == null && this.getRiddenByEntity() != null) {
         this.onRidePilotFirstUpdate();
      }

      if (this.countOnUpdate == 0) {
         this.onFirstUpdate();
      }

      this.countOnUpdate++;
      if (this.countOnUpdate >= 1000000) {
         this.countOnUpdate = 1;
      }

      if (this.worldObj.isRemote) {
         this.commonStatus = this.getDataWatcher().getWatchableObjectInt(23);
      }

      this.fallDistance = 0.0F;
      if (this.riddenByEntity != null) {
         this.riddenByEntity.fallDistance = 0.0F;
      }

      if (this.missileDetector != null) {
         this.missileDetector.update();
      }

      if (this.soundUpdater != null) {
         this.soundUpdater.update();
      }

      if (this.getTowChainEntity() != null && this.getTowChainEntity().isDead) {
         this.setTowChainEntity(null);
      }

      this.updateSupplyAmmo();
      this.autoRepair();
      int ft = this.getFlareTick();
      this.flareDv.update();
      if (!this.worldObj.isRemote && this.getFlareTick() == 0 && ft != 0) {
         this.setCommonStatus(0, false);
      }

      Entity e = this.getRiddenByEntity();
      if (e != null && !e.isDead && !this.isDestroyed()) {
         this.lastRiderYaw = e.rotationYaw;
         this.prevLastRiderYaw = e.prevRotationYaw;
         this.lastRiderPitch = e.rotationPitch;
         this.prevLastRiderPitch = e.prevRotationPitch;
      } else if (this.getTowedChainEntity() != null || this.ridingEntity != null) {
         this.lastRiderYaw = this.rotationYaw;
         this.prevLastRiderYaw = this.prevRotationYaw;
         this.lastRiderPitch = this.rotationPitch;
         this.prevLastRiderPitch = this.prevRotationPitch;
      }

      this.updatePartCameraRotate();
      this.updatePartWheel();
      this.updatePartCrawlerTrack();
      this.updatePartLightHatch();
      this.regenerationMob();
      if (this.getRiddenByEntity() == null && this.lastRiddenByEntity != null) {
         this.unmountEntity();
      }

      this.updateExtraBoundingBox();
      boolean prevOnGround = this.onGround;
      double prevMotionY = this.motionY;
      this.onUpdateAircraft();
      if (this.getAcInfo() != null) {
         this.updateParts(this.getPartStatus());
      }

      if (this.recoilCount > 0) {
         this.recoilCount--;
      }

      if (!W_Entity.isEqual(MCH_MOD.proxy.getClientPlayer(), this.getRiddenByEntity())) {
         this.updateRecoil(1.0F);
      }

      if (!this.worldObj.isRemote && this.isDestroyed() && !this.isExploded() && !prevOnGround && this.onGround && prevMotionY < -0.2) {
         this.explosionByCrash(prevMotionY);
         this.damageSinceDestroyed = this.getMaxHP();
      }

      this.onUpdate_PartRotation();
      this.onUpdate_ParticleSmoke();
      this.updateSeatsPosition(this.posX, this.posY, this.posZ, false);
      this.updateHitBoxPosition();
      this.onUpdate_CollisionGroundDamage();
      this.onUpdate_UnmountCrew();
      this.onUpdate_Repelling();
      this.checkRideRack();
      if (this.lastRidingEntity == null && this.getRidingEntity() != null) {
         this.onRideEntity(this.getRidingEntity());
      }

      this.lastRiddenByEntity = this.getRiddenByEntity();
      this.lastRidingEntity = this.getRidingEntity();
      this.prevPosition.put(Vec3.createVectorHelper(this.posX, this.posY, this.posZ));
   }

   private void updateNoCollisionEntities() {
      if (!this.worldObj.isRemote) {
         if (this.getCountOnUpdate() % 10 == 0) {
            for (int i = 0; i < 1 + this.getSeatNum(); i++) {
               Entity e = this.getEntityBySeatId(i);
               if (e != null) {
                  this.noCollisionEntities.put(e, 8);
               }
            }

            if (this.getTowChainEntity() != null && this.getTowChainEntity().towedEntity != null) {
               this.noCollisionEntities.put(this.getTowChainEntity().towedEntity, 60);
            }

            if (this.getTowedChainEntity() != null && this.getTowedChainEntity().towEntity != null) {
               this.noCollisionEntities.put(this.getTowedChainEntity().towEntity, 60);
            }

            if (this.ridingEntity instanceof MCH_EntitySeat) {
               MCH_EntityAircraft ac = ((MCH_EntitySeat)this.ridingEntity).getParent();
               if (ac != null) {
                  this.noCollisionEntities.put(ac, 60);
               }
            } else if (this.ridingEntity != null) {
               this.noCollisionEntities.put(this.ridingEntity, 60);
            }

            for (Entity key : this.noCollisionEntities.keySet()) {
               this.noCollisionEntities.put(key, this.noCollisionEntities.get(key) - 1);
            }

            Iterator<Integer> key = this.noCollisionEntities.values().iterator();

            while (key.hasNext()) {
               if (key.next() <= 0) {
                  key.remove();
               }
            }
         }
      }
   }

   public void updateControl() {
      if (!this.worldObj.isRemote) {
         this.setCommonStatus(7, this.moveLeft);
         this.setCommonStatus(8, this.moveRight);
         this.setCommonStatus(9, this.throttleUp);
         this.setCommonStatus(10, this.throttleDown);
      } else if (MCH_MOD.proxy.getClientPlayer() != this.getRiddenByEntity()) {
         this.moveLeft = this.getCommonStatus(7);
         this.moveRight = this.getCommonStatus(8);
         this.throttleUp = this.getCommonStatus(9);
         this.throttleDown = this.getCommonStatus(10);
      }
   }

   public void updateRecoil(float partialTicks) {
      if (this.recoilCount > 0 && this.recoilCount >= 12) {
         float pitch = MathHelper.cos((float)((this.recoilYaw - this.getRotRoll()) * Math.PI / 180.0));
         float roll = MathHelper.sin((float)((this.recoilYaw - this.getRotRoll()) * Math.PI / 180.0));
         float recoil = MathHelper.cos((float)(this.recoilCount * 6 * Math.PI / 180.0)) * this.recoilValue;
         this.setRotPitch(this.getRotPitch() + recoil * pitch * partialTicks);
         this.setRotRoll(this.getRotRoll() + recoil * roll * partialTicks);
      }
   }

   private void updatePartLightHatch() {
      this.prevRotLightHatch = this.rotLightHatch;
      if (this.isSearchLightON()) {
         this.rotLightHatch = (float)(this.rotLightHatch + 0.5);
      } else {
         this.rotLightHatch = (float)(this.rotLightHatch - 0.5);
      }

      if (this.rotLightHatch > 1.0F) {
         this.rotLightHatch = 1.0F;
      }

      if (this.rotLightHatch < 0.0F) {
         this.rotLightHatch = 0.0F;
      }
   }

   public void updateExtraBoundingBox() {
      for (MCH_BoundingBox bb : this.extraBoundingBox) {
         bb.updatePosition(this.posX, this.posY, this.posZ, this.getRotYaw(), this.getRotPitch(), this.getRotRoll());
      }
   }

   public void updatePartWheel() {
      if (this.worldObj.isRemote) {
         if (this.getAcInfo() != null) {
            this.prevRotWheel = this.rotWheel;
            this.prevRotYawWheel = this.rotYawWheel;
            float LEN = 1.0F;
            float MIN = 0.0F;
            double throttle = this.getCurrentThrottle();
            double pivotTurnThrottle = this.getAcInfo().pivotTurnThrottle;
            if (pivotTurnThrottle <= 0.0) {
               pivotTurnThrottle = 1.0;
            } else {
               pivotTurnThrottle *= 0.1F;
            }

            boolean localMoveLeft = this.moveLeft;
            boolean localMoveRight = this.moveRight;
            if (this.getAcInfo().enableBack && this.throttleBack > 0.01 && throttle <= 0.0) {
               throttle = -this.throttleBack * 15.0F;
            }

            if (localMoveLeft && !localMoveRight) {
               this.rotYawWheel += 0.1F;
               if (this.rotYawWheel > 1.0F) {
                  this.rotYawWheel = 1.0F;
               }
            } else if (!localMoveLeft && localMoveRight) {
               this.rotYawWheel -= 0.1F;
               if (this.rotYawWheel < -1.0F) {
                  this.rotYawWheel = -1.0F;
               }
            } else {
               this.rotYawWheel *= 0.9F;
            }

            this.rotWheel = (float)(this.rotWheel + throttle * this.getAcInfo().partWheelRot);
            if (this.rotWheel >= 360.0F) {
               this.rotWheel -= 360.0F;
               this.prevRotWheel -= 360.0F;
            } else if (this.rotWheel < 0.0F) {
               this.rotWheel += 360.0F;
               this.prevRotWheel += 360.0F;
            }
         }
      }
   }

   public void updatePartCrawlerTrack() {
      if (this.worldObj.isRemote) {
         if (this.getAcInfo() != null) {
            this.prevRotTrackRoller[0] = this.rotTrackRoller[0];
            this.prevRotTrackRoller[1] = this.rotTrackRoller[1];
            this.prevRotCrawlerTrack[0] = this.rotCrawlerTrack[0];
            this.prevRotCrawlerTrack[1] = this.rotCrawlerTrack[1];
            float LEN = 1.0F;
            float MIN = 0.0F;
            double throttle = this.getCurrentThrottle();
            double pivotTurnThrottle = this.getAcInfo().pivotTurnThrottle;
            if (pivotTurnThrottle <= 0.0) {
               pivotTurnThrottle = 1.0;
            } else {
               pivotTurnThrottle *= 0.1F;
            }

            boolean localMoveLeft = this.moveLeft;
            boolean localMoveRight = this.moveRight;
            int dir = 1;
            if (this.getAcInfo().enableBack && this.throttleBack > 0.0F && throttle <= 0.0) {
               throttle = -this.throttleBack * 5.0F;
               if (localMoveLeft != localMoveRight) {
                  boolean tmp = localMoveLeft;
                  localMoveLeft = localMoveRight;
                  localMoveRight = tmp;
                  dir = -1;
               }
            }

            if (localMoveLeft && !localMoveRight) {
               throttle = 0.2 * dir;
               this.throttleCrawlerTrack[0] = (float)(this.throttleCrawlerTrack[0] + throttle);
               this.throttleCrawlerTrack[1] = (float)(this.throttleCrawlerTrack[1] - pivotTurnThrottle * throttle);
            } else if (!localMoveLeft && localMoveRight) {
               throttle = 0.2 * dir;
               this.throttleCrawlerTrack[0] = (float)(this.throttleCrawlerTrack[0] - pivotTurnThrottle * throttle);
               this.throttleCrawlerTrack[1] = (float)(this.throttleCrawlerTrack[1] + throttle);
            } else {
               if (throttle > 0.2) {
                  throttle = 0.2;
               }

               if (throttle < -0.2) {
                  throttle = -0.2;
               }

               this.throttleCrawlerTrack[0] = (float)(this.throttleCrawlerTrack[0] + throttle);
               this.throttleCrawlerTrack[1] = (float)(this.throttleCrawlerTrack[1] + throttle);
            }

            for (int i = 0; i < 2; i++) {
               if (this.throttleCrawlerTrack[i] < -0.72F) {
                  this.throttleCrawlerTrack[i] = -0.72F;
               } else if (this.throttleCrawlerTrack[i] > 0.72F) {
                  this.throttleCrawlerTrack[i] = 0.72F;
               }

               this.rotTrackRoller[i] = this.rotTrackRoller[i] + this.throttleCrawlerTrack[i] * this.getAcInfo().trackRollerRot;
               if (this.rotTrackRoller[i] >= 360.0F) {
                  this.rotTrackRoller[i] = this.rotTrackRoller[i] - 360.0F;
                  this.prevRotTrackRoller[i] = this.prevRotTrackRoller[i] - 360.0F;
               } else if (this.rotTrackRoller[i] < 0.0F) {
                  this.rotTrackRoller[i] = this.rotTrackRoller[i] + 360.0F;
                  this.prevRotTrackRoller[i] = this.prevRotTrackRoller[i] + 360.0F;
               }

               for (this.rotCrawlerTrack[i] = this.rotCrawlerTrack[i] - this.throttleCrawlerTrack[i];
                  this.rotCrawlerTrack[i] >= 1.0F;
                  this.prevRotCrawlerTrack[i]--
               ) {
                  this.rotCrawlerTrack[i]--;
               }

               while (this.rotCrawlerTrack[i] < 0.0F) {
                  this.rotCrawlerTrack[i]++;
               }

               while (this.prevRotCrawlerTrack[i] < 0.0F) {
                  this.prevRotCrawlerTrack[i]++;
               }

               this.throttleCrawlerTrack[i] = (float)(this.throttleCrawlerTrack[i] * 0.75);
            }
         }
      }
   }

   public void checkServerNoMove() {
      if (!this.worldObj.isRemote) {
         double moti = this.motionX * this.motionX + this.motionY * this.motionY + this.motionZ * this.motionZ;
         if (moti < 1.0E-4) {
            if (this.serverNoMoveCount < 20) {
               this.serverNoMoveCount++;
               if (this.serverNoMoveCount >= 20) {
                  this.serverNoMoveCount = 0;
                  if (this.worldObj instanceof WorldServer) {
                     ((WorldServer)this.worldObj).getEntityTracker().func_151247_a(this, new S12PacketEntityVelocity(this.getEntityId(), 0.0, 0.0, 0.0));
                  }
               }
            }
         } else {
            this.serverNoMoveCount = 0;
         }
      }
   }

   public boolean haveRotPart() {
      return this.worldObj.isRemote
         && this.getAcInfo() != null
         && this.rotPartRotation.length > 0
         && this.rotPartRotation.length == this.getAcInfo().partRotPart.size();
   }

   public void onUpdate_PartRotation() {
      if (this.haveRotPart()) {
         for (int i = 0; i < this.rotPartRotation.length; i++) {
            this.prevRotPartRotation[i] = this.rotPartRotation[i];
            if (!this.isDestroyed() && this.getAcInfo().partRotPart.get(i).rotAlways || this.getRiddenByEntity() != null) {
               this.rotPartRotation[i] = this.rotPartRotation[i] + this.getAcInfo().partRotPart.get(i).rotSpeed;
               if (this.rotPartRotation[i] < 0.0F) {
                  this.rotPartRotation[i] = this.rotPartRotation[i] + 360.0F;
               }

               if (this.rotPartRotation[i] >= 360.0F) {
                  this.rotPartRotation[i] = this.rotPartRotation[i] - 360.0F;
               }
            }
         }
      }
   }

   public void onRideEntity(Entity ridingEntity) {
   }

   public int getAlt(double px, double py, double pz) {
      int i = 0;

      while (i < 256 && !(py - i <= 0.0) && (py - i >= 256.0 || 0 == W_WorldFunc.getBlockId(this.worldObj, (int)px, (int)py - i, (int)pz))) {
         i++;
      }

      return i;
   }

   public boolean canRepelling(Entity entity) {
      return this.isRepelling() && this.tickRepelling > 50;
   }

   private void onUpdate_Repelling() {
      if (this.getAcInfo() != null && this.getAcInfo().haveRepellingHook()) {
         if (this.isRepelling()) {
            int alt = this.getAlt(this.posX, this.posY, this.posZ);
            if (this.ropesLength > -50.0F && this.ropesLength > -alt) {
               this.ropesLength = (float)(this.ropesLength - (this.worldObj.isRemote ? 0.3F : 0.25));
            }
         } else {
            this.ropesLength = 0.0F;
         }
      }

      this.onUpdate_UnmountCrewRepelling();
   }

   private void onUpdate_UnmountCrewRepelling() {
      if (this.getAcInfo() != null) {
         if (!this.isRepelling()) {
            this.tickRepelling = 0;
         } else if (this.tickRepelling < 60) {
            this.tickRepelling++;
         } else if (!this.worldObj.isRemote) {
            for (int ropeIdx = 0; ropeIdx < this.getAcInfo().repellingHooks.size(); ropeIdx++) {
               MCH_AircraftInfo.RepellingHook hook = this.getAcInfo().repellingHooks.get(ropeIdx);
               if (this.getCountOnUpdate() % hook.interval == 0) {
                  for (int i = 1; i < this.getSeatNum(); i++) {
                     MCH_EntitySeat seat = this.getSeat(i);
                     if (seat != null
                        && seat.riddenByEntity != null
                        && !W_EntityPlayer.isPlayer(seat.riddenByEntity)
                        && !(this.getSeatInfo(i + 1) instanceof MCH_SeatRackInfo)) {
                        Entity entity = seat.riddenByEntity;
                        Vec3 dropPos = this.getTransformedPosition(hook.pos, this.prevPosition.oldest());
                        seat.posX = dropPos.xCoord;
                        seat.posY = dropPos.yCoord - 2.0;
                        seat.posZ = dropPos.zCoord;
                        entity.mountEntity(null);
                        this.unmountEntityRepelling(entity, dropPos, ropeIdx);
                        this.lastUsedRopeIndex = ropeIdx;
                        break;
                     }
                  }
               }
            }
         }
      }
   }

   public void unmountEntityRepelling(Entity entity, Vec3 dropPos, int ropeIdx) {
      entity.posX = dropPos.xCoord;
      entity.posY = dropPos.yCoord - 2.0;
      entity.posZ = dropPos.zCoord;
      MCH_EntityHide hideEntity = new MCH_EntityHide(this.worldObj, entity.posX, entity.posY, entity.posZ);
      hideEntity.setParent(this, entity, ropeIdx);
      hideEntity.motionX = entity.motionX = 0.0;
      hideEntity.motionY = entity.motionY = 0.0;
      hideEntity.motionZ = entity.motionZ = 0.0;
      hideEntity.fallDistance = entity.fallDistance = 0.0F;
      this.worldObj.spawnEntityInWorld(hideEntity);
   }

   private void onUpdate_UnmountCrew() {
      if (this.getAcInfo() != null) {
         if (this.isParachuting) {
            if (MCH_Lib.getBlockIdY(this, 3, -10) != 0) {
               this.stopUnmountCrew();
            } else if ((!this.haveHatch() || this.getHatchRotation() > 89.0F)
               && this.getCountOnUpdate() % this.getAcInfo().mobDropOption.interval == 0
               && !this.unmountCrew(true)) {
               this.stopUnmountCrew();
            }
         }
      }
   }

   public void unmountAircraft() {
      Vec3 v = Vec3.createVectorHelper(this.posX, this.posY, this.posZ);
      if (this.ridingEntity instanceof MCH_EntitySeat) {
         MCH_EntityAircraft ac = ((MCH_EntitySeat)this.ridingEntity).getParent();
         MCH_SeatInfo seatInfo = ac.getSeatInfo(this);
         if (seatInfo instanceof MCH_SeatRackInfo) {
            v = ((MCH_SeatRackInfo)seatInfo).getEntryPos();
            v = ac.getTransformedPosition(v);
         }
      } else if (this.ridingEntity instanceof EntityMinecartEmpty) {
         this.dismountedUserCtrl = true;
      }

      this.setLocationAndAngles(v.xCoord, v.yCoord, v.zCoord, this.getRotYaw(), this.getRotPitch());
      this.mountEntity(null);
      this.setLocationAndAngles(v.xCoord, v.yCoord, v.zCoord, this.getRotYaw(), this.getRotPitch());
   }

   public boolean canUnmount(Entity entity) {
      if (this.getAcInfo() == null) {
         return false;
      } else if (!this.getAcInfo().isEnableParachuting) {
         return false;
      } else {
         return this.getSeatIdByEntity(entity) <= 1 ? false : !this.haveHatch() || !(this.getHatchRotation() < 89.0F);
      }
   }

   public void unmount(Entity entity) {
      if (this.getAcInfo() != null) {
         if (this.canRepelling(entity) && this.getAcInfo().haveRepellingHook()) {
            MCH_EntitySeat seat = this.getSeatByEntity(entity);
            if (seat != null) {
               this.lastUsedRopeIndex = (this.lastUsedRopeIndex + 1) % this.getAcInfo().repellingHooks.size();
               Vec3 dropPos = this.getTransformedPosition(this.getAcInfo().repellingHooks.get(this.lastUsedRopeIndex).pos, this.prevPosition.oldest());
               dropPos = dropPos.addVector(0.0, -2.0, 0.0);
               seat.posX = dropPos.xCoord;
               seat.posY = dropPos.yCoord;
               seat.posZ = dropPos.zCoord;
               entity.mountEntity(null);
               entity.posX = dropPos.xCoord;
               entity.posY = dropPos.yCoord;
               entity.posZ = dropPos.zCoord;
               this.unmountEntityRepelling(entity, dropPos, this.lastUsedRopeIndex);
            } else {
               MCH_Lib.Log(this, "Error:MCH_EntityAircraft.unmount seat=null : " + entity);
            }
         } else if (this.canUnmount(entity)) {
            MCH_EntitySeat seat = this.getSeatByEntity(entity);
            if (seat != null) {
               Vec3 dropPos = this.getTransformedPosition(this.getAcInfo().mobDropOption.pos, this.prevPosition.oldest());
               seat.posX = dropPos.xCoord;
               seat.posY = dropPos.yCoord;
               seat.posZ = dropPos.zCoord;
               entity.mountEntity(null);
               entity.posX = dropPos.xCoord;
               entity.posY = dropPos.yCoord;
               entity.posZ = dropPos.zCoord;
               this.dropEntityParachute(entity);
            } else {
               MCH_Lib.Log(this, "Error:MCH_EntityAircraft.unmount seat=null : " + entity);
            }
         }
      }
   }

   public boolean canParachuting(Entity entity) {
      if (this.getAcInfo() == null || !this.getAcInfo().isEnableParachuting || this.getSeatIdByEntity(entity) <= 1 || MCH_Lib.getBlockIdY(this, 3, -13) != 0) {
         return false;
      } else {
         return this.haveHatch() && this.getHatchRotation() > 89.0F ? this.getSeatIdByEntity(entity) > 1 : this.getSeatIdByEntity(entity) > 1;
      }
   }

   public void onUpdate_RidingEntity() {
      if (!this.worldObj.isRemote && this.waitMountEntity == 0 && this.getCountOnUpdate() > 20 && this.canMountWithNearEmptyMinecart()) {
         this.mountWithNearEmptyMinecart();
      }

      if (this.waitMountEntity > 0) {
         this.waitMountEntity--;
      }

      if (!this.worldObj.isRemote && this.getRidingEntity() != null) {
         this.setRotRoll(this.getRotRoll() * 0.9F);
         this.setRotPitch(this.getRotPitch() * 0.95F);
         Entity re = this.getRidingEntity();
         float target = MathHelper.wrapAngleTo180_float(re.rotationYaw + 90.0F);
         if (target - this.rotationYaw > 180.0F) {
            target -= 360.0F;
         }

         if (target - this.rotationYaw < -180.0F) {
            target += 360.0F;
         }

         if (this.ticksExisted % 2 == 0) {
         }

         float dist = 50.0F * (float)re.getDistanceSq(re.prevPosX, re.prevPosY, re.prevPosZ);
         if (dist > 0.001) {
            dist = MathHelper.sqrt_double(dist);
            float distYaw = MCH_Lib.RNG(target - this.rotationYaw, -dist, dist);
            this.rotationYaw += distYaw;
         }

         double bkPosX = this.posX;
         double bkPosY = this.posY;
         double bkPosZ = this.posZ;
         if (this.getRidingEntity().isDead) {
            this.mountEntity(null);
            this.waitMountEntity = 20;
         } else if (this.getCurrentThrottle() > 0.8) {
            this.motionX = this.getRidingEntity().motionX;
            this.motionY = this.getRidingEntity().motionY;
            this.motionZ = this.getRidingEntity().motionZ;
            this.mountEntity(null);
            this.waitMountEntity = 20;
         }

         this.posX = bkPosX;
         this.posY = bkPosY;
         this.posZ = bkPosZ;
      }
   }

   public void explosionByCrash(double prevMotionY) {
      float exp = this.getAcInfo() != null ? this.getAcInfo().maxFuel / 400.0F : 2.0F;
      if (exp < 1.0F) {
         exp = 1.0F;
      }

      if (exp > 15.0F) {
         exp = 15.0F;
      }

      MCH_Lib.DbgLog(this.worldObj, "OnGroundAfterDestroyed:motionY=%.3f", (float)prevMotionY);
      MCH_Explosion.newExplosion(
         this.worldObj,
         null,
         null,
         this.posX,
         this.posY,
         this.posZ,
         exp,
         exp >= 2.0F ? exp * 0.5F : 1.0F,
         true,
         true,
         true,
         true,
         5
      );
   }

   public void onUpdate_CollisionGroundDamage() {
      if (!this.isDestroyed()) {
         if (MCH_Lib.getBlockIdY(this, 3, -3) > 0 && !this.worldObj.isRemote) {
            float roll = MathHelper.abs(MathHelper.wrapAngleTo180_float(this.getRotRoll()));
            float pitch = MathHelper.abs(MathHelper.wrapAngleTo180_float(this.getRotPitch()));
            if (roll > this.getGiveDamageRot() || pitch > this.getGiveDamageRot()) {
               float dmg = MathHelper.abs(roll) + MathHelper.abs(pitch);
               if (dmg < 90.0F) {
                  dmg *= 0.4F * (float)this.getDistance(this.prevPosX, this.prevPosY, this.prevPosZ);
               } else {
                  dmg *= 0.4F;
               }

               if (dmg > 1.0F && this.rand.nextInt(4) == 0) {
                  this.attackEntityFrom(DamageSource.inWall, dmg);
               }
            }
         }

         if (this.getCountOnUpdate() % 30 == 0
            && (this.getAcInfo() == null || !this.getAcInfo().isFloat)
            && MCH_Lib.isBlockInWater(
               this.worldObj,
               (int)(this.posX + 0.5),
               (int)(this.posY + 1.5 + this.getAcInfo().submergedDamageHeight),
               (int)(this.posZ + 0.5)
            )) {
            int hp = this.getMaxHP() / 10;
            if (hp <= 0) {
               hp = 1;
            }

            this.attackEntityFrom(DamageSource.inWall, hp);
         }
      }
   }

   public float getGiveDamageRot() {
      return 40.0F;
   }

   public void applyServerPositionAndRotation() {
      double rpinc = this.aircraftPosRotInc;
      double yaw = MathHelper.wrapAngleTo180_double(this.aircraftYaw - this.getRotYaw());
      double roll = MathHelper.wrapAngleTo180_double((double)this.getServerRoll() - this.getRotRoll());
      if (!this.isDestroyed() && (!W_Lib.isClientPlayer(this.getRiddenByEntity()) || this.getRidingEntity() != null)) {
         this.setRotYaw((float)(this.getRotYaw() + yaw / rpinc));
         this.setRotPitch((float)(this.getRotPitch() + (this.aircraftPitch - this.getRotPitch()) / rpinc));
         this.setRotRoll((float)(this.getRotRoll() + roll / rpinc));
      }

      this.setPosition(
         this.posX + (this.aircraftX - this.posX) / rpinc,
         this.posY + (this.aircraftY - this.posY) / rpinc,
         this.posZ + (this.aircraftZ - this.posZ) / rpinc
      );
      this.setRotation(this.getRotYaw(), this.getRotPitch());
      this.aircraftPosRotInc--;
   }

   protected void autoRepair() {
      if (this.timeSinceHit > 0) {
         this.timeSinceHit--;
      }

      if (this.getMaxHP() > 0) {
         if (!this.isDestroyed()) {
            if (this.getDamageTaken() > this.beforeDamageTaken) {
               this.repairCount = 600;
            } else if (this.repairCount > 0) {
               this.repairCount--;
            } else {
               this.repairCount = 40;
               double hpp = (double)this.getHP() / this.getMaxHP();
               if (hpp >= MCH_Config.AutoRepairHP.prmDouble) {
                  this.repair(this.getMaxHP() / 100);
               }
            }
         }

         this.beforeDamageTaken = this.getDamageTaken();
      }
   }

   public boolean repair(int tpd) {
      if (tpd < 1) {
         tpd = 1;
      }

      int damage = this.getDamageTaken();
      if (damage > 0) {
         if (!this.worldObj.isRemote) {
            this.setDamageTaken(damage - tpd);
         }

         return true;
      } else {
         return false;
      }
   }

   public void repairOtherAircraft() {
      float range = this.getAcInfo() != null ? this.getAcInfo().repairOtherVehiclesRange : 0.0F;
      if (!(range <= 0.0F)) {
         if (!this.worldObj.isRemote && this.getCountOnUpdate() % 20 == 0) {
            List list = this.worldObj.getEntitiesWithinAABB(MCH_EntityAircraft.class, this.getBoundingBox().expand(range, range, range));

            for (int i = 0; i < list.size(); i++) {
               MCH_EntityAircraft ac = (MCH_EntityAircraft)list.get(i);
               if (!W_Entity.isEqual(this, ac) && ac.getHP() < ac.getMaxHP()) {
                  ac.setDamageTaken(ac.getDamageTaken() - this.getAcInfo().repairOtherVehiclesValue);
               }
            }
         }
      }
   }

   protected void regenerationMob() {
      if (!this.isDestroyed()) {
         if (!this.worldObj.isRemote) {
            if (this.getAcInfo() != null && this.getAcInfo().regeneration && this.getRiddenByEntity() != null) {
               MCH_EntitySeat[] st = this.getSeats();

               for (MCH_EntitySeat s : st) {
                  if (s != null && !s.isDead) {
                     Entity e = s.riddenByEntity;
                     if (W_Lib.isEntityLivingBase(e) && !e.isDead) {
                        PotionEffect pe = W_Entity.getActivePotionEffect(e, Potion.regeneration);
                        if (pe == null || pe != null && pe.getDuration() < 500) {
                           W_Entity.addPotionEffect(e, new PotionEffect(Potion.regeneration.id, 250, 0, true));
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public double getWaterDepth() {
      byte b0 = 5;
      double d0 = 0.0;

      for (int i = 0; i < b0; i++) {
         double d1 = this.boundingBox.minY + (this.boundingBox.maxY - this.boundingBox.minY) * (i + 0) / b0 - 0.125;
         double d2 = this.boundingBox.minY + (this.boundingBox.maxY - this.boundingBox.minY) * (i + 1) / b0 - 0.125;
         d1 += this.getAcInfo().floatOffset;
         d2 += this.getAcInfo().floatOffset;
         AxisAlignedBB axisalignedbb = W_AxisAlignedBB.getAABB(
            this.boundingBox.minX, d1, this.boundingBox.minZ, this.boundingBox.maxX, d2, this.boundingBox.maxZ
         );
         if (this.worldObj.isAABBInMaterial(axisalignedbb, Material.water)) {
            d0 += 1.0 / b0;
         }
      }

      return d0;
   }

   public int getCountOnUpdate() {
      return this.countOnUpdate;
   }

   public boolean canSupply() {
      return this.canFloatWater() ? MCH_Lib.getBlockIdY(this, 1, -3) != 0 : MCH_Lib.getBlockIdY(this, 1, -3) != 0 && !this.isInWater();
   }

   public void setFuel(int fuel) {
      if (!this.worldObj.isRemote) {
         if (fuel < 0) {
            fuel = 0;
         }

         if (fuel > this.getMaxFuel()) {
            fuel = this.getMaxFuel();
         }

         if (fuel != this.getFuel()) {
            this.getDataWatcher().updateObject(25, fuel);
         }
      }
   }

   public int getFuel() {
      return this.getDataWatcher().getWatchableObjectInt(25);
   }

   public float getFuelP() {
      int m = this.getMaxFuel();
      return m == 0 ? 0.0F : (float)this.getFuel() / m;
   }

   public boolean canUseFuel(boolean checkOtherSeet) {
      return this.getMaxFuel() <= 0 || this.getFuel() > 1 || this.isInfinityFuel(this.getRiddenByEntity(), checkOtherSeet);
   }

   public boolean canUseFuel() {
      return this.canUseFuel(false);
   }

   public int getMaxFuel() {
      return this.getAcInfo() != null ? this.getAcInfo().maxFuel : 0;
   }

   public void supplyFuel() {
      float range = this.getAcInfo() != null ? this.getAcInfo().fuelSupplyRange : 0.0F;
      if (!(range <= 0.0F)) {
         if (!this.worldObj.isRemote && this.getCountOnUpdate() % 10 == 0) {
            List list = this.worldObj.getEntitiesWithinAABB(MCH_EntityAircraft.class, this.getBoundingBox().expand(range, range, range));

            for (int i = 0; i < list.size(); i++) {
               MCH_EntityAircraft ac = (MCH_EntityAircraft)list.get(i);
               if (!W_Entity.isEqual(this, ac)) {
                  if ((!this.onGround || ac.canSupply()) && ac.getFuel() < ac.getMaxFuel()) {
                     int fc = ac.getMaxFuel() - ac.getFuel();
                     if (fc > 30) {
                        fc = 30;
                     }

                     ac.setFuel(ac.getFuel() + fc);
                  }

                  ac.fuelSuppliedCount = 40;
               }
            }
         }
      }
   }

   public void updateFuel() {
      if (this.getMaxFuel() != 0) {
         if (this.fuelSuppliedCount > 0) {
            this.fuelSuppliedCount--;
         }

         if (!this.isDestroyed() && !this.worldObj.isRemote) {
            if (this.getCountOnUpdate() % 20 == 0 && this.getFuel() > 1 && this.getThrottle() > 0.0 && this.fuelSuppliedCount <= 0) {
               double t = this.getThrottle() * 1.4;
               if (t > 1.0) {
                  t = 1.0;
               }

               this.fuelConsumption = this.fuelConsumption + t * this.getAcInfo().fuelConsumption * this.getFuelConsumptionFactor();
               if (this.fuelConsumption > 1.0) {
                  int f = (int)this.fuelConsumption;
                  this.fuelConsumption -= f;
                  this.setFuel(this.getFuel() - f);
               }
            }

            int curFuel = this.getFuel();
            if (this.canSupply() && this.getCountOnUpdate() % 10 == 0 && curFuel < this.getMaxFuel()) {
               for (int i = 0; i < 3; i++) {
                  if (curFuel < this.getMaxFuel()) {
                     ItemStack fuel = this.getGuiInventory().getFuelSlotItemStack(i);
                     if (fuel != null && fuel.getItem() instanceof MCH_ItemFuel && fuel.getItemDamage() < fuel.getMaxDamage()) {
                        int fc = this.getMaxFuel() - curFuel;
                        if (fc > 100) {
                           fc = 100;
                        }

                        if (fuel.getItemDamage() > fuel.getMaxDamage() - fc) {
                           fc = fuel.getMaxDamage() - fuel.getItemDamage();
                        }

                        fuel.setItemDamage(fuel.getItemDamage() + fc);
                        curFuel += fc;
                     }
                  }
               }

               if (this.getFuel() != curFuel) {
                  MCH_Achievement.addStat(this.riddenByEntity, MCH_Achievement.supplyFuel, 1);
               }

               this.setFuel(curFuel);
            }
         }
      }
   }

   public float getFuelConsumptionFactor() {
      return 1.0F;
   }

   public void updateSupplyAmmo() {
      if (!this.worldObj.isRemote) {
         boolean isReloading = false;
         if (this.getRiddenByEntity() instanceof EntityPlayer
            && !this.getRiddenByEntity().isDead
            && ((EntityPlayer)this.getRiddenByEntity()).openContainer instanceof MCH_AircraftGuiContainer) {
            isReloading = true;
         }

         this.setCommonStatus(2, isReloading);
         if (!this.isDestroyed() && this.beforeSupplyAmmo && !isReloading) {
            this.reloadAllWeapon();
            MCH_PacketNotifyAmmoNum.sendAllAmmoNum(this, null);
         }

         this.beforeSupplyAmmo = isReloading;
      }

      if (this.getCommonStatus(2)) {
         this.supplyAmmoWait = 20;
      }

      if (this.supplyAmmoWait > 0) {
         this.supplyAmmoWait--;
      }
   }

   public void supplyAmmo(int weaponID) {
      if (this.worldObj.isRemote) {
         MCH_WeaponSet ws = this.getWeapon(weaponID);
         ws.supplyRestAllAmmo();
      } else {
         MCH_Achievement.addStat(this.riddenByEntity, MCH_Achievement.supplyAmmo, 1);
         if (this.getRiddenByEntity() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer)this.getRiddenByEntity();
            if (this.canPlayerSupplyAmmo(player, weaponID)) {
               MCH_WeaponSet ws = this.getWeapon(weaponID);

               for (MCH_WeaponInfo.RoundItem ri : ws.getInfo().roundItems) {
                  int num = ri.num;

                  for (int i = 0; i < player.inventory.mainInventory.length; i++) {
                     ItemStack itemStack = player.inventory.mainInventory[i];
                     if (itemStack != null && itemStack.isItemEqual(ri.itemStack)) {
                        if (itemStack.getItem() != W_Item.getItemByName("water_bucket") && itemStack.getItem() != W_Item.getItemByName("lava_bucket")
                           )
                         {
                           if (itemStack.stackSize > num) {
                              itemStack.stackSize -= num;
                              num = 0;
                           } else {
                              num -= itemStack.stackSize;
                              itemStack.stackSize = 0;
                              player.inventory.mainInventory[i] = null;
                           }
                        } else if (itemStack.stackSize == 1) {
                           player.inventory.setInventorySlotContents(i, new ItemStack(W_Item.getItemByName("bucket"), 1));
                           num--;
                        }
                     }

                     if (num <= 0) {
                        break;
                     }
                  }
               }

               ws.supplyRestAllAmmo();
            }
         }
      }
   }

   public void supplyAmmoToOtherAircraft() {
      float range = this.getAcInfo() != null ? this.getAcInfo().ammoSupplyRange : 0.0F;
      if (!(range <= 0.0F)) {
         if (!this.worldObj.isRemote && this.getCountOnUpdate() % 40 == 0) {
            List list = this.worldObj.getEntitiesWithinAABB(MCH_EntityAircraft.class, this.getBoundingBox().expand(range, range, range));

            for (int i = 0; i < list.size(); i++) {
               MCH_EntityAircraft ac = (MCH_EntityAircraft)list.get(i);
               if (!W_Entity.isEqual(this, ac) && ac.canSupply()) {
                  for (int wid = 0; wid < ac.getWeaponNum(); wid++) {
                     MCH_WeaponSet ws = ac.getWeapon(wid);
                     int num = ws.getRestAllAmmoNum() + ws.getAmmoNum();
                     if (num < ws.getAllAmmoNum()) {
                        int ammo = ws.getAllAmmoNum() / 10;
                        if (ammo < 1) {
                           ammo = 1;
                        }

                        ws.setRestAllAmmoNum(num + ammo);
                        EntityPlayer player = ac.getEntityByWeaponId(wid);
                        if (num != ws.getRestAllAmmoNum() + ws.getAmmoNum()) {
                           if (ws.getAmmoNum() <= 0) {
                              ws.reloadMag();
                           }

                           MCH_PacketNotifyAmmoNum.sendAmmoNum(ac, player, wid);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public boolean canPlayerSupplyAmmo(EntityPlayer player, int weaponId) {
      if (MCH_Lib.getBlockIdY(this, 1, -3) == 0) {
         return false;
      }

      if (!this.canSupply()) {
         return false;
      }

      MCH_WeaponSet ws = this.getWeapon(weaponId);
      if (ws.getRestAllAmmoNum() + ws.getAmmoNum() >= ws.getAllAmmoNum()) {
         return false;
      }

      for (MCH_WeaponInfo.RoundItem ri : ws.getInfo().roundItems) {
         int num = ri.num;

         for (ItemStack itemStack : player.inventory.mainInventory) {
            if (itemStack != null && itemStack.isItemEqual(ri.itemStack)) {
               num -= itemStack.stackSize;
            }

            if (num <= 0) {
               break;
            }
         }

         if (num > 0) {
            return false;
         }
      }

      return true;
   }

   public MCH_EntityAircraft setTextureName(String name) {
      if (name != null && !name.isEmpty()) {
         this.getDataWatcher().updateObject(21, String.valueOf(name));
      }

      return this;
   }

   public String getTextureName() {
      return this.getDataWatcher().getWatchableObjectString(21);
   }

   public void switchNextTextureName() {
      if (this.getAcInfo() != null) {
         this.setTextureName(this.getAcInfo().getNextTextureName(this.getTextureName()));
      }
   }

   public void zoomCamera() {
      if (this.canZoom()) {
         float z = this.camera.getCameraZoom();
         if (z >= this.getZoomMax() - 0.01) {
            z = 1.0F;
         } else {
            z *= 2.0F;
            if (z >= this.getZoomMax()) {
               z = this.getZoomMax();
            }
         }

         this.camera.setCameraZoom(z <= this.getZoomMax() + 0.01 ? z : 1.0F);
      }
   }

   public int getZoomMax() {
      return this.getAcInfo() != null ? this.getAcInfo().cameraZoom : 1;
   }

   public boolean canZoom() {
      return this.getZoomMax() > 1;
   }

   public boolean canSwitchCameraMode() {
      return this.isDestroyed() ? false : this.getAcInfo() != null && this.getAcInfo().isEnableNightVision;
   }

   public boolean canSwitchCameraMode(int seatID) {
      return this.isDestroyed() ? false : this.canSwitchCameraMode() && this.camera.isValidUid(seatID);
   }

   public int getCameraMode(EntityPlayer player) {
      return this.camera.getMode(this.getSeatIdByEntity(player));
   }

   public String getCameraModeName(EntityPlayer player) {
      return this.camera.getModeName(this.getSeatIdByEntity(player));
   }

   public void switchCameraMode(EntityPlayer player) {
      this.switchCameraMode(player, this.camera.getMode(this.getSeatIdByEntity(player)) + 1);
   }

   public void switchCameraMode(EntityPlayer player, int mode) {
      this.camera.setMode(this.getSeatIdByEntity(player), mode);
   }

   public void updateCameraViewers() {
      for (int i = 0; i < this.getSeatNum() + 1; i++) {
         this.camera.updateViewer(i, this.getEntityBySeatId(i));
      }
   }

   public void updateRadar(int radarSpeed) {
      if (this.isEntityRadarMounted()) {
         this.radarRotate += radarSpeed;
         if (this.radarRotate >= 360) {
            this.radarRotate = 0;
         }

         if (this.radarRotate == 0) {
            this.entityRadar.updateXZ(this, 64);
         }
      }
   }

   public int getRadarRotate() {
      return this.radarRotate;
   }

   public void initRadar() {
      this.entityRadar.clear();
      this.radarRotate = 0;
   }

   public ArrayList<MCH_Vector2> getRadarEntityList() {
      return this.entityRadar.getEntityList();
   }

   public ArrayList<MCH_Vector2> getRadarEnemyList() {
      return this.entityRadar.getEnemyList();
   }

   public void moveEntity(double par1, double par3, double par5) {
      if (this.getAcInfo() != null) {
         this.worldObj.theProfiler.startSection("move");
         this.ySize *= 0.4F;
         double d3 = this.posX;
         double d4 = this.posY;
         double d5 = this.posZ;
         double d6 = par1;
         double d7 = par3;
         double d8 = par5;
         AxisAlignedBB axisalignedbb = this.boundingBox.copy();
         List list = getCollidingBoundingBoxes(this, this.boundingBox.addCoord(par1, par3, par5));

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
            list = getCollidingBoundingBoxes(this, this.boundingBox.addCoord(d6, par3, d8));

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
   }

   public static List getCollidingBoundingBoxes(Entity par1Entity, AxisAlignedBB par2AxisAlignedBB) {
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
            if (par1Entity.worldObj.blockExists(k1, 64, l1)) {
               for (int i2 = k - 1; i2 < l; i2++) {
                  Block block = W_WorldFunc.getBlock(par1Entity.worldObj, k1, i2, l1);
                  if (block != null) {
                     block.addCollisionBoxesToList(par1Entity.worldObj, k1, i2, l1, par2AxisAlignedBB, collidingBoundingBoxes, par1Entity);
                  }
               }
            }
         }
      }

      double d0 = 0.25;
      List list = par1Entity.worldObj.getEntitiesWithinAABBExcludingEntity(par1Entity, par2AxisAlignedBB.expand(d0, d0, d0));

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

   protected void onUpdate_updateBlock() {
      if (MCH_Config.Collision_DestroyBlock.prmBool) {
         for (int l = 0; l < 4; l++) {
            int i1 = MathHelper.floor_double(this.posX + (l % 2 - 0.5) * 0.8);
            int j1 = MathHelper.floor_double(this.posZ + (l / 2 - 0.5) * 0.8);

            for (int k1 = 0; k1 < 2; k1++) {
               int l1 = MathHelper.floor_double(this.posY) + k1;
               Block block = W_WorldFunc.getBlock(this.worldObj, i1, l1, j1);
               if (!W_Block.isNull(block)) {
                  if (block == W_Block.getSnowLayer()) {
                     this.worldObj.setBlockToAir(i1, l1, j1);
                  }

                  if (block == W_Blocks.waterlily || block == W_Blocks.cake) {
                     W_WorldFunc.destroyBlock(this.worldObj, i1, l1, j1, false);
                  }
               }
            }
         }
      }
   }

   public void onUpdate_ParticleSmoke() {
      if (this.worldObj.isRemote) {
         if (!(this.getCurrentThrottle() <= 0.1F)) {
            float yaw = this.getRotYaw();
            float pitch = this.getRotPitch();
            float roll = this.getRotRoll();
            MCH_WeaponSet ws = this.getCurrentWeapon(this.getRiddenByEntity());
            if (ws.getFirstWeapon() instanceof MCH_WeaponSmoke) {
               for (int i = 0; i < ws.getWeaponNum(); i++) {
                  MCH_WeaponBase wb = ws.getWeapon(i);
                  if (wb != null) {
                     MCH_WeaponInfo wi = wb.getInfo();
                     if (wi != null) {
                        Vec3 rot = MCH_Lib.RotVec3(0.0, 0.0, 1.0, -yaw - 180.0F + wb.fixRotationYaw, pitch - wb.fixRotationPitch, roll);
                        if (!(this.rand.nextFloat() > this.getCurrentThrottle() * 1.5)) {
                           Vec3 pos = MCH_Lib.RotVec3(wb.position, -yaw, -pitch, -roll);
                           double x = this.posX + pos.xCoord + rot.xCoord;
                           double y = this.posY + pos.yCoord + rot.yCoord;
                           double z = this.posZ + pos.zCoord + rot.zCoord;

                           for (int smk = 0; smk < wi.smokeNum; smk++) {
                              float c = this.rand.nextFloat() * 0.05F;
                              int maxAge = (int)(this.rand.nextDouble() * wi.smokeMaxAge);
                              MCH_ParticleParam prm = new MCH_ParticleParam(this.worldObj, "smoke", x, y, z);
                              prm.setMotion(
                                 rot.xCoord * wi.acceleration + (this.rand.nextDouble() - 0.5) * 0.2,
                                 rot.yCoord * wi.acceleration + (this.rand.nextDouble() - 0.5) * 0.2,
                                 rot.zCoord * wi.acceleration + (this.rand.nextDouble() - 0.5) * 0.2
                              );
                              prm.size = (this.rand.nextInt(5) + 5.0F) * wi.smokeSize;
                              prm.setColor(wi.color.a + this.rand.nextFloat() * 0.05F, wi.color.r + c, wi.color.g + c, wi.color.b + c);
                              prm.age = maxAge;
                              prm.toWhite = true;
                              prm.diffusible = true;
                              MCH_ParticlesUtil.spawnParticle(prm);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   protected void onUpdate_ParticleSandCloud(boolean seaOnly) {
      if (!seaOnly || this.getAcInfo().enableSeaSurfaceParticle) {
         double particlePosY = (int)this.posY;
         boolean b = false;
         float scale = this.getAcInfo().particlesScale * 3.0F;
         if (seaOnly) {
            scale *= 2.0F;
         }

         double throttle = this.getCurrentThrottle();
         throttle *= 2.0;
         if (throttle > 1.0) {
            throttle = 1.0;
         }

         int count = seaOnly ? (int)(scale * 7.0F) : 0;
         int rangeY = (int)(scale * 10.0F) + 1;

         int y;
         for (y = 0; y < rangeY && !b; y++) {
            for (int x = -1; x <= 1; x++) {
               for (int z = -1; z <= 1; z++) {
                  Block block = W_WorldFunc.getBlock(
                     this.worldObj, (int)(this.posX + 0.5) + x, (int)(this.posY + 0.5) - y, (int)(this.posZ + 0.5) + z
                  );
                  if (!b && block != null && !Block.isEqualTo(block, Blocks.air)) {
                     if (seaOnly && W_Block.isEqual(block, W_Block.getWater())) {
                        count--;
                     }

                     if (count <= 0) {
                        particlePosY = this.posY + 1.0 + scale / 5.0F - y;
                        b = true;
                        x += 100;
                        break;
                     }
                  }
               }
            }
         }

         double pn = (rangeY - y + 1) / (5.0 * scale) / 2.0;
         if (b && this.getAcInfo().particlesScale > 0.01F) {
            for (int k = 0; k < (int)(throttle * 6.0 * pn); k++) {
               float r = (float)(this.rand.nextDouble() * Math.PI * 2.0);
               double dx = MathHelper.cos(r);
               double dz = MathHelper.sin(r);
               MCH_ParticleParam prm = new MCH_ParticleParam(
                  this.worldObj,
                  "smoke",
                  this.posX + dx * scale * 3.0,
                  particlePosY + (this.rand.nextDouble() - 0.5) * scale,
                  this.posZ + dz * scale * 3.0,
                  scale * (dx * 0.3),
                  scale * -0.4 * 0.05,
                  scale * (dz * 0.3),
                  scale * 5.0F
               );
               prm.setColor(prm.a * 0.6F, prm.r, prm.g, prm.b);
               prm.age = (int)(10.0F * scale);
               prm.motionYUpAge = seaOnly ? 0.2F : 0.1F;
               MCH_ParticlesUtil.spawnParticle(prm);
            }
         }
      }
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
      return 0.0;
   }

   public float getShadowSize() {
      return 2.0F;
   }

   public boolean canBeCollidedWith() {
      return !this.isDead;
   }

   public boolean useFlare(int type) {
      if (this.getAcInfo() != null && this.getAcInfo().haveFlare()) {
         for (int i : this.getAcInfo().flare.types) {
            if (i == type) {
               this.setCommonStatus(0, true);
               if (this.flareDv.use(type)) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public int getCurrentFlareType() {
      return !this.haveFlare() ? 0 : this.getAcInfo().flare.types[this.currentFlareIndex];
   }

   public void nextFlareType() {
      if (this.haveFlare()) {
         this.currentFlareIndex = (this.currentFlareIndex + 1) % this.getAcInfo().flare.types.length;
      }
   }

   public boolean canUseFlare() {
      if (this.getAcInfo() == null || !this.getAcInfo().haveFlare()) {
         return false;
      } else {
         return this.getCommonStatus(0) ? false : this.flareDv.tick == 0;
      }
   }

   public boolean isFlarePreparation() {
      return this.flareDv.isInPreparation();
   }

   public boolean isFlareUsing() {
      return this.flareDv.isUsing();
   }

   public int getFlareTick() {
      return this.flareDv.tick;
   }

   public boolean haveFlare() {
      return this.getAcInfo() != null && this.getAcInfo().haveFlare();
   }

   public boolean haveFlare(int seatID) {
      return this.haveFlare() && seatID >= 0 && seatID <= 1;
   }

   public MCH_EntitySeat[] getSeats() {
      return this.seats != null ? this.seats : seatsDummy;
   }

   public int getSeatIdByEntity(Entity entity) {
      if (entity == null) {
         return -1;
      }

      if (isEqual(this.getRiddenByEntity(), entity)) {
         return 0;
      }

      for (int i = 0; i < this.getSeats().length; i++) {
         MCH_EntitySeat seat = this.getSeats()[i];
         if (seat != null && isEqual(seat.riddenByEntity, entity)) {
            return i + 1;
         }
      }

      return -1;
   }

   public MCH_EntitySeat getSeatByEntity(Entity entity) {
      int idx = this.getSeatIdByEntity(entity);
      return idx > 0 ? this.getSeat(idx - 1) : null;
   }

   public Entity getEntityBySeatId(int id) {
      if (id == 0) {
         return this.getRiddenByEntity();
      } else {
         id--;
         if (id >= 0 && id < this.getSeats().length) {
            return this.seats[id] != null ? this.seats[id].riddenByEntity : null;
         } else {
            return null;
         }
      }
   }

   public EntityPlayer getEntityByWeaponId(int id) {
      if (id >= 0 && id < this.getWeaponNum()) {
         for (int i = 0; i < this.currentWeaponID.length; i++) {
            if (this.currentWeaponID[i] == id) {
               Entity e = this.getEntityBySeatId(i);
               if (e instanceof EntityPlayer) {
                  return (EntityPlayer)e;
               }
            }
         }
      }

      return null;
   }

   public Entity getWeaponUserByWeaponName(String name) {
      if (this.getAcInfo() == null) {
         return null;
      }

      MCH_AircraftInfo.Weapon weapon = this.getAcInfo().getWeaponByName(name);
      Entity entity = null;
      if (weapon != null) {
         entity = this.getEntityBySeatId(this.getWeaponSeatID(null, weapon));
         if (entity == null && weapon.canUsePilot) {
            entity = this.getRiddenByEntity();
         }
      }

      return entity;
   }

   protected void newSeats(int seatsNum) {
      if (seatsNum >= 2) {
         if (this.seats != null) {
            for (int i = 0; i < this.seats.length; i++) {
               if (this.seats[i] != null) {
                  this.seats[i].setDead();
                  this.seats[i] = null;
               }
            }
         }

         this.seats = new MCH_EntitySeat[seatsNum - 1];
      }
   }

   public MCH_EntitySeat getSeat(int idx) {
      return idx < this.seats.length ? this.seats[idx] : null;
   }

   public void setSeat(int idx, MCH_EntitySeat seat) {
      if (idx < this.seats.length) {
         MCH_Lib.DbgLog(
            this.worldObj, "MCH_EntityAircraft.setSeat SeatID=" + idx + " / seat[]" + (this.seats[idx] != null) + " / " + (seat.riddenByEntity != null)
         );
         if (this.seats[idx] != null && this.seats[idx].riddenByEntity != null) {
         }

         this.seats[idx] = seat;
      }
   }

   public boolean isValidSeatID(int seatID) {
      return seatID >= 0 && seatID < this.getSeatNum() + 1;
   }

   public void updateHitBoxPosition() {
   }

   public void updateSeatsPosition(double px, double py, double pz, boolean setPrevPos) {
      MCH_SeatInfo[] info = this.getSeatsInfo();
      if (this.pilotSeat != null && !this.pilotSeat.isDead) {
         this.pilotSeat.prevPosX = this.pilotSeat.posX;
         this.pilotSeat.prevPosY = this.pilotSeat.posY;
         this.pilotSeat.prevPosZ = this.pilotSeat.posZ;
         this.pilotSeat.setPosition(px, py, pz);
         if (info != null && info.length > 0 && info[0] != null) {
            Vec3 v = this.getTransformedPosition(info[0].pos.xCoord, info[0].pos.yCoord, info[0].pos.zCoord, px, py, pz, info[0].rotSeat);
            this.pilotSeat.setPosition(v.xCoord, v.yCoord, v.zCoord);
         }

         this.pilotSeat.rotationPitch = this.getRotPitch();
         this.pilotSeat.rotationYaw = this.getRotYaw();
         if (setPrevPos) {
            this.pilotSeat.prevPosX = this.pilotSeat.posX;
            this.pilotSeat.prevPosY = this.pilotSeat.posY;
            this.pilotSeat.prevPosZ = this.pilotSeat.posZ;
         }
      }

      int i = 0;

      for (MCH_EntitySeat seat : this.seats) {
         i++;
         if (seat != null && !seat.isDead) {
            float offsetY = 0.0F;
            if (seat.riddenByEntity != null) {
               if (W_Lib.isClientPlayer(seat.riddenByEntity)) {
                  offsetY = 1.0F;
               } else if (seat.riddenByEntity.height >= 1.0F) {
                  offsetY = -seat.riddenByEntity.height + 1.0F;
               }
            }

            seat.prevPosX = seat.posX;
            seat.prevPosY = seat.posY;
            seat.prevPosZ = seat.posZ;
            MCH_SeatInfo si = i < info.length ? info[i] : info[0];
            Vec3 v = this.getTransformedPosition(si.pos.xCoord, si.pos.yCoord + offsetY, si.pos.zCoord, px, py, pz, si.rotSeat);
            seat.setPosition(v.xCoord, v.yCoord, v.zCoord);
            seat.rotationPitch = this.getRotPitch();
            seat.rotationYaw = this.getRotYaw();
            if (setPrevPos) {
               seat.prevPosX = seat.posX;
               seat.prevPosY = seat.posY;
               seat.prevPosZ = seat.posZ;
            }

            if (si instanceof MCH_SeatRackInfo) {
               seat.updateRotation(((MCH_SeatRackInfo)si).fixYaw + this.getRotYaw(), ((MCH_SeatRackInfo)si).fixPitch);
            }

            seat.updatePosition();
         }
      }
   }

   public int getClientPositionDelayCorrection() {
      return 7;
   }

   public void setPositionAndRotation2(double par1, double par3, double par5, float par7, float par8, int par9) {
      this.aircraftPosRotInc = par9 + this.getClientPositionDelayCorrection();
      this.aircraftX = par1;
      this.aircraftY = par3;
      this.aircraftZ = par5;
      this.aircraftYaw = par7;
      this.aircraftPitch = par8;
      this.motionX = this.velocityX;
      this.motionY = this.velocityY;
      this.motionZ = this.velocityZ;
   }

   public void updateRiderPosition(double px, double py, double pz) {
      MCH_SeatInfo[] info = this.getSeatsInfo();
      if (this.riddenByEntity != null && !this.riddenByEntity.isDead) {
         float riddenEntityYOffset = this.riddenByEntity.yOffset;
         float offset = 0.0F;
         if (this.riddenByEntity instanceof EntityPlayer && !W_Lib.isClientPlayer(this.riddenByEntity)) {
            offset -= 1.62F;
         }

         Vec3 v;
         if (info != null && info.length > 0) {
            v = this.getTransformedPosition(
               info[0].pos.xCoord, info[0].pos.yCoord + riddenEntityYOffset - 0.5, info[0].pos.zCoord, px, py, pz, info[0].rotSeat
            );
         } else {
            v = this.getTransformedPosition(0.0, riddenEntityYOffset - 1.0F, 0.0);
         }

         this.riddenByEntity.yOffset = 0.0F;
         this.riddenByEntity.setPosition(v.xCoord, v.yCoord, v.zCoord);
         this.riddenByEntity.yOffset = riddenEntityYOffset;
      }
   }

   public void updateRiderPosition() {
      this.updateRiderPosition(this.posX, this.posY, this.posZ);
   }

   public Vec3 calcOnTurretPos(Vec3 pos) {
      float ry = this.getLastRiderYaw();
      if (this.getRiddenByEntity() != null) {
         ry = this.getRiddenByEntity().rotationYaw;
      }

      Vec3 tpos = this.getAcInfo().turretPosition.addVector(0.0, pos.yCoord, 0.0);
      Vec3 v = pos.addVector(-tpos.xCoord, -tpos.yCoord, -tpos.zCoord);
      v = MCH_Lib.RotVec3(v, -ry, 0.0F, 0.0F);
      Vec3 vv = MCH_Lib.RotVec3(tpos, -this.getRotYaw(), -this.getRotPitch(), -this.getRotRoll());
      v.xCoord = v.xCoord + vv.xCoord;
      v.yCoord = v.yCoord + vv.yCoord;
      v.zCoord = v.zCoord + vv.zCoord;
      return v;
   }

   public float getLastRiderYaw() {
      return this.lastRiderYaw;
   }

   public float getLastRiderPitch() {
      return this.lastRiderPitch;
   }

   @SideOnly(Side.CLIENT)
   public void setupAllRiderRenderPosition(float tick, EntityPlayer player) {
      double x = this.lastTickPosX + (this.posX - this.lastTickPosX) * tick;
      double y = this.lastTickPosY + (this.posY - this.lastTickPosY) * tick;
      double z = this.lastTickPosZ + (this.posZ - this.lastTickPosZ) * tick;
      this.updateRiderPosition(x, y, z);
      this.updateSeatsPosition(x, y, z, true);

      for (int i = 0; i < this.getSeatNum() + 1; i++) {
         Entity e = this.getEntityBySeatId(i);
         if (e != null) {
            e.lastTickPosX = e.posX;
            e.lastTickPosY = e.posY;
            e.lastTickPosZ = e.posZ;
         }
      }

      if (this.getTVMissile() != null && W_Lib.isClientPlayer(this.getTVMissile().shootingEntity)) {
         Entity tv = this.getTVMissile();
         x = tv.prevPosX + (tv.posX - tv.prevPosX) * tick;
         y = tv.prevPosY + (tv.posY - tv.prevPosY) * tick;
         z = tv.prevPosZ + (tv.posZ - tv.prevPosZ) * tick;
         MCH_ViewEntityDummy.setCameraPosition(x, y, z);
      } else {
         MCH_AircraftInfo.CameraPosition cpi = this.getCameraPosInfo();
         if (cpi != null && cpi.pos != null) {
            MCH_SeatInfo seatInfo = this.getSeatInfo(player);
            Vec3 v;
            if (seatInfo != null && seatInfo.rotSeat) {
               v = this.calcOnTurretPos(cpi.pos);
            } else {
               v = MCH_Lib.RotVec3(cpi.pos, -this.getRotYaw(), -this.getRotPitch(), -this.getRotRoll());
            }

            MCH_ViewEntityDummy.setCameraPosition(x + v.xCoord, y + v.yCoord, z + v.zCoord);
            if (cpi.fixRot) {
            }
         }
      }
   }

   public Vec3 getTurretPos(Vec3 pos, boolean turret) {
      if (turret) {
         float ry = this.getLastRiderYaw();
         if (this.getRiddenByEntity() != null) {
            ry = this.getRiddenByEntity().rotationYaw;
         }

         Vec3 tpos = this.getAcInfo().turretPosition.addVector(0.0, pos.yCoord, 0.0);
         Vec3 v = pos.addVector(-tpos.xCoord, -tpos.yCoord, -tpos.zCoord);
         v = MCH_Lib.RotVec3(v, -ry, 0.0F, 0.0F);
         Vec3 vv = MCH_Lib.RotVec3(tpos, -this.getRotYaw(), -this.getRotPitch(), -this.getRotRoll());
         v.xCoord = v.xCoord + vv.xCoord;
         v.yCoord = v.yCoord + vv.yCoord;
         v.zCoord = v.zCoord + vv.zCoord;
         return v;
      } else {
         return Vec3.createVectorHelper(0.0, 0.0, 0.0);
      }
   }

   public Vec3 getTransformedPosition(Vec3 v) {
      return this.getTransformedPosition(v.xCoord, v.yCoord, v.zCoord);
   }

   public Vec3 getTransformedPosition(double x, double y, double z) {
      return this.getTransformedPosition(x, y, z, this.posX, this.posY, this.posZ);
   }

   public Vec3 getTransformedPosition(Vec3 v, Vec3 pos) {
      return this.getTransformedPosition(v.xCoord, v.yCoord, v.zCoord, pos.xCoord, pos.yCoord, pos.zCoord);
   }

   public Vec3 getTransformedPosition(Vec3 v, double px, double py, double pz) {
      return this.getTransformedPosition(v.xCoord, v.yCoord, v.zCoord, this.posX, this.posY, this.posZ);
   }

   public Vec3 getTransformedPosition(double x, double y, double z, double px, double py, double pz) {
      Vec3 v = MCH_Lib.RotVec3(x, y, z, -this.getRotYaw(), -this.getRotPitch(), -this.getRotRoll());
      return v.addVector(px, py, pz);
   }

   public Vec3 getTransformedPosition(double x, double y, double z, double px, double py, double pz, boolean rotSeat) {
      if (rotSeat && this.getAcInfo() != null) {
         MCH_AircraftInfo info = this.getAcInfo();
         Vec3 tv = MCH_Lib.RotVec3(
            x - info.turretPosition.xCoord,
            y - info.turretPosition.yCoord,
            z - info.turretPosition.zCoord,
            -this.getLastRiderYaw() + this.getRotYaw(),
            0.0F,
            0.0F
         );
         x = tv.xCoord + info.turretPosition.xCoord;
         y = tv.yCoord + info.turretPosition.xCoord;
         z = tv.zCoord + info.turretPosition.xCoord;
      }

      Vec3 v = MCH_Lib.RotVec3(x, y, z, -this.getRotYaw(), -this.getRotPitch(), -this.getRotRoll());
      return v.addVector(px, py, pz);
   }

   protected MCH_SeatInfo[] getSeatsInfo() {
      if (this.seatsInfo != null) {
         return this.seatsInfo;
      }

      this.newSeatsPos();
      return this.seatsInfo;
   }

   public MCH_SeatInfo getSeatInfo(int index) {
      MCH_SeatInfo[] seats = this.getSeatsInfo();
      return index >= 0 && seats != null && index < seats.length ? seats[index] : null;
   }

   public MCH_SeatInfo getSeatInfo(Entity entity) {
      return this.getSeatInfo(this.getSeatIdByEntity(entity));
   }

   protected void setSeatsInfo(MCH_SeatInfo[] v) {
      this.seatsInfo = v;
   }

   public int getSeatNum() {
      if (this.getAcInfo() == null) {
         return 0;
      }

      int s = this.getAcInfo().getNumSeatAndRack();
      return s >= 1 ? s - 1 : 1;
   }

   protected void newSeatsPos() {
      if (this.getAcInfo() != null) {
         MCH_SeatInfo[] v = new MCH_SeatInfo[this.getAcInfo().getNumSeatAndRack()];

         for (int i = 0; i < v.length; i++) {
            v[i] = this.getAcInfo().seatList.get(i);
         }

         this.setSeatsInfo(v);
      }
   }

   public void createSeats(String uuid) {
      if (!this.worldObj.isRemote) {
         if (!uuid.isEmpty()) {
            this.setCommonUniqueId(uuid);
            this.seats = new MCH_EntitySeat[this.getSeatNum()];

            for (int i = 0; i < this.seats.length; i++) {
               this.seats[i] = new MCH_EntitySeat(this.worldObj, this.posX, this.posY, this.posZ);
               this.seats[i].parentUniqueID = this.getCommonUniqueId();
               this.seats[i].seatID = i;
               this.seats[i].setParent(this);
               this.worldObj.spawnEntityInWorld(this.seats[i]);
            }
         }
      }
   }

   public boolean interactFirstSeat(EntityPlayer player) {
      if (this.getSeats() == null) {
         return false;
      }

      int seatId = 1;

      for (MCH_EntitySeat seat : this.getSeats()) {
         if (seat != null && seat.riddenByEntity == null && !this.isMountedEntity(player) && this.canRideSeatOrRack(seatId, player)) {
            if (!this.worldObj.isRemote) {
               player.mountEntity(seat);
            }
            break;
         }

         seatId++;
      }

      return true;
   }

   public void onMountPlayerSeat(MCH_EntitySeat seat, Entity entity) {
      if (seat != null && entity instanceof EntityPlayer) {
         if (this.worldObj.isRemote && MCH_Lib.getClientPlayer() == entity) {
            this.switchGunnerFreeLookMode(false);
         }

         this.initCurrentWeapon(entity);
         MCH_Lib.DbgLog(this.worldObj, "onMountEntitySeat:%d", W_Entity.getEntityId(entity));
         Entity pilot = this.getRiddenByEntity();
         int sid = this.getSeatIdByEntity(entity);
         if (sid == 1 && (this.getAcInfo() == null || !this.getAcInfo().isEnableConcurrentGunnerMode)) {
            this.switchGunnerMode(false);
         }

         if (sid > 0) {
            this.isGunnerModeOtherSeat = true;
         }

         if (pilot != null && this.getAcInfo() != null) {
            int cwid = this.getCurrentWeaponID(pilot);
            MCH_AircraftInfo.Weapon w = this.getAcInfo().getWeaponById(cwid);
            if (w != null && this.getWeaponSeatID(this.getWeaponInfoById(cwid), w) == sid) {
               int next = this.getNextWeaponID(pilot, 1);
               MCH_Lib.DbgLog(this.worldObj, "onMountEntitySeat:%d:->%d", W_Entity.getEntityId(pilot), next);
               if (next >= 0) {
                  this.switchWeapon(pilot, next);
               }
            }
         }

         if (this.worldObj.isRemote) {
            this.updateClientSettings(sid);
         }
      }
   }

   public MCH_WeaponInfo getWeaponInfoById(int id) {
      if (id >= 0) {
         MCH_WeaponSet ws = this.getWeapon(id);
         if (ws != null) {
            return ws.getInfo();
         }
      }

      return null;
   }

   public abstract boolean canMountWithNearEmptyMinecart();

   protected void mountWithNearEmptyMinecart() {
      if (this.getRidingEntity() == null) {
         int d = 2;
         if (this.dismountedUserCtrl) {
            d = 6;
         }

         List list = this.worldObj.getEntitiesWithinAABBExcludingEntity(this, this.boundingBox.expand(d, d, d));
         if (list != null && !list.isEmpty()) {
            for (int i = 0; i < list.size(); i++) {
               Entity entity = (Entity)list.get(i);
               if (entity instanceof EntityMinecartEmpty) {
                  if (this.dismountedUserCtrl) {
                     return;
                  }

                  if (entity.riddenByEntity == null && entity.canBePushed()) {
                     this.waitMountEntity = 20;
                     MCH_Lib.DbgLog(this.worldObj.isRemote, "MCH_EntityAircraft.mountWithNearEmptyMinecart:" + entity);
                     this.mountEntity(entity);
                     return;
                  }
               }
            }
         }

         this.dismountedUserCtrl = false;
      }
   }

   public boolean isRidePlayer() {
      if (this.getRiddenByEntity() instanceof EntityPlayer) {
         return true;
      }

      for (MCH_EntitySeat seat : this.getSeats()) {
         if (seat != null && seat.riddenByEntity instanceof EntityPlayer) {
            return true;
         }
      }

      return false;
   }

   public void onUnmountPlayerSeat(MCH_EntitySeat seat, Entity entity) {
      MCH_Lib.DbgLog(this.worldObj, "onUnmountPlayerSeat:%d", W_Entity.getEntityId(entity));
      int sid = this.getSeatIdByEntity(entity);
      this.camera.initCamera(sid, entity);
      MCH_SeatInfo seatInfo = this.getSeatInfo(seat.seatID + 1);
      if (seatInfo != null) {
         this.setUnmountPosition(entity, Vec3.createVectorHelper(seatInfo.pos.xCoord, 0.0, seatInfo.pos.zCoord));
      }

      if (!this.isRidePlayer()) {
         this.switchGunnerMode(false);
         this.switchHoveringMode(false);
      }
   }

   public boolean isCreatedSeats() {
      return !this.getCommonUniqueId().isEmpty();
   }

   public void onUpdate_Seats() {
      boolean b = false;

      for (int i = 0; i < this.seats.length; i++) {
         if (this.seats[i] != null) {
            if (!this.seats[i].isDead) {
               this.seats[i].fallDistance = 0.0F;
            }
         } else {
            b = true;
         }
      }

      if (b) {
         if (this.seatSearchCount > 40) {
            if (this.worldObj.isRemote) {
               MCH_PacketSeatListRequest.requestSeatList(this);
            } else {
               this.searchSeat();
            }

            this.seatSearchCount = 0;
         }

         this.seatSearchCount++;
      }
   }

   public void searchSeat() {
      List list = this.worldObj.getEntitiesWithinAABB(MCH_EntitySeat.class, this.boundingBox.expand(60.0, 60.0, 60.0));

      for (int i = 0; i < list.size(); i++) {
         MCH_EntitySeat seat = (MCH_EntitySeat)list.get(i);
         if (!seat.isDead
            && seat.parentUniqueID.equals(this.getCommonUniqueId())
            && seat.seatID >= 0
            && seat.seatID < this.getSeatNum()
            && this.seats[seat.seatID] == null) {
            this.seats[seat.seatID] = seat;
            seat.setParent(this);
         }
      }
   }

   public String getCommonUniqueId() {
      return this.commonUniqueId;
   }

   public void setCommonUniqueId(String uniqId) {
      this.commonUniqueId = uniqId;
   }

   @Override
   public void setDead() {
      this.setDead(false);
   }

   public void setDead(boolean dropItems) {
      this.dropContentsWhenDead = dropItems;
      super.setDead();
      if (this.getRiddenByEntity() != null) {
         this.getRiddenByEntity().mountEntity(null);
      }

      this.getGuiInventory().setDead();

      for (MCH_EntitySeat s : this.seats) {
         if (s != null) {
            s.setDead();
         }
      }

      if (this.soundUpdater != null) {
         this.soundUpdater.update();
      }

      if (this.getTowChainEntity() != null) {
         this.getTowChainEntity().setDead();
         this.setTowChainEntity(null);
      }

      for (Entity e : this.getParts()) {
         if (e != null) {
            e.setDead();
         }
      }

      MCH_Lib.DbgLog(this.worldObj, "setDead:" + (this.getAcInfo() != null ? this.getAcInfo().name : "null"));
   }

   public void unmountEntity() {
      if (!this.isRidePlayer()) {
         this.switchHoveringMode(false);
      }

      this.moveLeft = this.moveRight = this.throttleDown = this.throttleUp = false;
      Entity rByEntity = null;
      if (this.riddenByEntity != null) {
         rByEntity = this.riddenByEntity;
         this.camera.initCamera(0, rByEntity);
         if (!this.worldObj.isRemote) {
            this.riddenByEntity.mountEntity(null);
         }
      } else if (this.lastRiddenByEntity != null) {
         rByEntity = this.lastRiddenByEntity;
         if (rByEntity instanceof EntityPlayer) {
            this.camera.initCamera(0, rByEntity);
         }
      }

      MCH_Lib.DbgLog(this.worldObj, "unmountEntity:" + rByEntity);
      if (!this.isRidePlayer()) {
         this.switchGunnerMode(false);
      }

      this.setCommonStatus(1, false);
      if (!this.isUAV()) {
         this.setUnmountPosition(rByEntity, this.getSeatsInfo()[0].pos);
      } else if (rByEntity != null && rByEntity.ridingEntity instanceof MCH_EntityUavStation) {
         rByEntity.mountEntity(null);
      }

      this.riddenByEntity = null;
      this.lastRiddenByEntity = null;
      if (this.cs_dismountAll) {
         this.unmountCrew(false);
      }
   }

   public Entity getRidingEntity() {
      return this.ridingEntity;
   }

   public void startUnmountCrew() {
      this.isParachuting = true;
      if (this.haveHatch()) {
         this.foldHatch(true, true);
      }
   }

   public void stopUnmountCrew() {
      this.isParachuting = false;
   }

   public void unmountCrew() {
      if (this.getAcInfo() != null) {
         if (this.getAcInfo().haveRepellingHook()) {
            if (!this.isRepelling()) {
               if (MCH_Lib.getBlockIdY(this, 3, -4) > 0) {
                  this.unmountCrew(false);
               } else if (this.canStartRepelling()) {
                  this.startRepelling();
               }
            } else {
               this.stopRepelling();
            }
         } else if (this.isParachuting) {
            this.stopUnmountCrew();
         } else if (this.getAcInfo().isEnableParachuting && MCH_Lib.getBlockIdY(this, 3, -10) == 0) {
            this.startUnmountCrew();
         } else {
            this.unmountCrew(false);
         }
      }
   }

   public boolean isRepelling() {
      return this.getCommonStatus(5);
   }

   public void setRepellingStat(boolean b) {
      this.setCommonStatus(5, b);
   }

   public Vec3 getRopePos(int ropeIndex) {
      return this.getAcInfo() != null && this.getAcInfo().haveRepellingHook() && ropeIndex < this.getAcInfo().repellingHooks.size()
         ? this.getTransformedPosition(this.getAcInfo().repellingHooks.get(ropeIndex).pos)
         : Vec3.createVectorHelper(this.posX, this.posY, this.posZ);
   }

   private void startRepelling() {
      MCH_Lib.DbgLog(this.worldObj, "MCH_EntityAircraft.startRepelling()");
      this.setRepellingStat(true);
      this.throttleUp = false;
      this.throttleDown = false;
      this.moveLeft = false;
      this.moveRight = false;
      this.tickRepelling = 0;
   }

   private void stopRepelling() {
      MCH_Lib.DbgLog(this.worldObj, "MCH_EntityAircraft.stopRepelling()");
      this.setRepellingStat(false);
   }

   public static float abs(float p_76135_0_) {
      return p_76135_0_ >= 0.0F ? p_76135_0_ : -p_76135_0_;
   }

   public static double abs(double p_76135_0_) {
      return p_76135_0_ >= 0.0 ? p_76135_0_ : -p_76135_0_;
   }

   public boolean canStartRepelling() {
      if (this.getAcInfo().haveRepellingHook() && this.isHovering() && abs(this.getRotPitch()) < 3.0F && abs(this.getRotRoll()) < 3.0F) {
         Vec3 v = this.prevPosition.oldest().addVector(-this.posX, -this.posY, -this.posZ);
         if (v.lengthVector() < 0.3) {
            return true;
         }
      }

      return false;
   }

   public boolean unmountCrew(boolean unmountParachute) {
      boolean ret = false;
      MCH_SeatInfo[] pos = this.getSeatsInfo();

      for (int i = 0; i < this.seats.length; i++) {
         if (this.seats[i] != null && this.seats[i].riddenByEntity != null) {
            Entity entity = this.seats[i].riddenByEntity;
            if (!(entity instanceof EntityPlayer) && !(pos[i + 1] instanceof MCH_SeatRackInfo)) {
               if (unmountParachute) {
                  if (this.getSeatIdByEntity(entity) > 1) {
                     ret = true;
                     Vec3 dropPos = this.getTransformedPosition(this.getAcInfo().mobDropOption.pos, this.prevPosition.oldest());
                     this.seats[i].posX = dropPos.xCoord;
                     this.seats[i].posY = dropPos.yCoord;
                     this.seats[i].posZ = dropPos.zCoord;
                     entity.mountEntity(null);
                     entity.posX = dropPos.xCoord;
                     entity.posY = dropPos.yCoord;
                     entity.posZ = dropPos.zCoord;
                     this.dropEntityParachute(entity);
                     break;
                  }
               } else {
                  ret = true;
                  Vec3 dropPos = pos[i + 1].pos;
                  this.setUnmountPosition(this.seats[i], pos[i + 1].pos);
                  entity.mountEntity(null);
                  this.setUnmountPosition(entity, pos[i + 1].pos);
               }
            }
         }
      }

      return ret;
   }

   public void setUnmountPosition(Entity rByEntity, Vec3 pos) {
      if (rByEntity != null) {
         MCH_AircraftInfo info = this.getAcInfo();
         Vec3 v;
         if (info != null && info.unmountPosition != null) {
            v = this.getTransformedPosition(info.unmountPosition);
         } else {
            double x = pos.xCoord;
            x = x >= 0.0 ? x + 3.0 : x - 3.0;
            v = this.getTransformedPosition(x, 2.0, pos.zCoord);
         }

         rByEntity.setPosition(v.xCoord, v.yCoord, v.zCoord);
         this.listUnmountReserve.add(new MCH_EntityAircraft.UnmountReserve(rByEntity, v.xCoord, v.yCoord, v.zCoord));
      }
   }

   public boolean unmountEntityFromSeat(Entity entity) {
      if (entity != null && this.seats != null && this.seats.length != 0) {
         for (MCH_EntitySeat seat : this.seats) {
            if (seat != null && seat.riddenByEntity != null && W_Entity.isEqual(seat.riddenByEntity, entity)) {
               entity.mountEntity(null);
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public void ejectSeat(Entity entity) {
      int sid = this.getSeatIdByEntity(entity);
      if (sid >= 0 && sid <= 1) {
         if (this.getGuiInventory().haveParachute()) {
            if (sid == 0) {
               this.getGuiInventory().consumeParachute();
               this.unmountEntity();
               this.ejectSeatSub(entity, 0);
               entity = this.getEntityBySeatId(1);
               if (entity instanceof EntityPlayer) {
                  entity = null;
               }
            }

            if (this.getGuiInventory().haveParachute() && entity != null) {
               this.getGuiInventory().consumeParachute();
               this.unmountEntityFromSeat(entity);
               this.ejectSeatSub(entity, 1);
            }
         }
      }
   }

   public void ejectSeatSub(Entity entity, int sid) {
      Vec3 pos = this.getSeatInfo(sid) != null ? this.getSeatInfo(sid).pos : null;
      if (pos != null) {
         Vec3 v = this.getTransformedPosition(pos.xCoord, pos.yCoord + 2.0, pos.zCoord);
         entity.setPosition(v.xCoord, v.yCoord, v.zCoord);
      }

      Vec3 v = MCH_Lib.RotVec3(0.0, 2.0, 0.0, -this.getRotYaw(), -this.getRotPitch(), -this.getRotRoll());
      entity.motionX = this.motionX + v.xCoord + (this.rand.nextFloat() - 0.5) * 0.1;
      entity.motionY = this.motionY + v.yCoord;
      entity.motionZ = this.motionZ + v.zCoord + (this.rand.nextFloat() - 0.5) * 0.1;
      MCH_EntityParachute parachute = new MCH_EntityParachute(this.worldObj, entity.posX, entity.posY, entity.posZ);
      parachute.rotationYaw = entity.rotationYaw;
      parachute.motionX = entity.motionX;
      parachute.motionY = entity.motionY;
      parachute.motionZ = entity.motionZ;
      parachute.fallDistance = entity.fallDistance;
      parachute.user = entity;
      parachute.setType(2);
      this.worldObj.spawnEntityInWorld(parachute);
      if (this.getAcInfo().haveCanopy() && this.isCanopyClose()) {
         this.openCanopy_EjectSeat();
      }

      W_WorldFunc.MOD_playSoundAtEntity(entity, "eject_seat", 5.0F, 1.0F);
   }

   public boolean canEjectSeat(Entity entity) {
      int sid = this.getSeatIdByEntity(entity);
      return sid == 0 && this.isUAV() ? false : sid >= 0 && sid < 2 && this.getAcInfo() != null && this.getAcInfo().isEnableEjectionSeat;
   }

   public int getNumEjectionSeat() {
      return 0;
   }

   public int getMountedEntityNum() {
      int num = 0;
      if (this.riddenByEntity != null && !this.riddenByEntity.isDead) {
         num++;
      }

      if (this.seats != null && this.seats.length > 0) {
         for (MCH_EntitySeat seat : this.seats) {
            if (seat != null && seat.riddenByEntity != null && !seat.riddenByEntity.isDead) {
               num++;
            }
         }
      }

      return num;
   }

   public void mountMobToSeats() {
      List list = this.worldObj.getEntitiesWithinAABB(W_Lib.getEntityLivingBaseClass(), this.boundingBox.expand(3.0, 2.0, 3.0));

      for (int i = 0; i < list.size(); i++) {
         Entity entity = (Entity)list.get(i);
         if (!(entity instanceof EntityPlayer) && entity.ridingEntity == null) {
            int sid = 1;

            for (MCH_EntitySeat seat : this.getSeats()) {
               if (seat != null && seat.riddenByEntity == null && !this.isMountedEntity(entity) && this.canRideSeatOrRack(sid, entity)) {
                  if (this.getSeatInfo(sid) instanceof MCH_SeatRackInfo) {
                     break;
                  }

                  entity.mountEntity(seat);
               }

               sid++;
            }
         }
      }
   }

   public void mountEntityToRack() {
      if (!MCH_Config.EnablePutRackInFlying.prmBool) {
         if (this.getCurrentThrottle() > 0.3) {
            return;
         }

         Block block = MCH_Lib.getBlockY(this, 1, -3, true);
         if (block == null || W_Block.isEqual(block, Blocks.air)) {
            return;
         }
      }

      int countRideEntity = 0;

      for (int sid = 0; sid < this.getSeatNum(); sid++) {
         MCH_EntitySeat seat = this.getSeat(sid);
         if (this.getSeatInfo(1 + sid) instanceof MCH_SeatRackInfo && seat != null && seat.riddenByEntity == null) {
            MCH_SeatRackInfo info = (MCH_SeatRackInfo)this.getSeatInfo(1 + sid);
            Vec3 v = MCH_Lib.RotVec3(
               info.getEntryPos().xCoord,
               info.getEntryPos().yCoord,
               info.getEntryPos().zCoord,
               -this.getRotYaw(),
               -this.getRotPitch(),
               -this.getRotRoll()
            );
            v.xCoord = v.xCoord + this.posX;
            v.yCoord = v.yCoord + this.posY;
            v.zCoord = v.zCoord + this.posZ;
            AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(v.xCoord, v.yCoord, v.zCoord, v.xCoord, v.yCoord, v.zCoord);
            float range = info.range;
            List list = this.worldObj.getEntitiesWithinAABBExcludingEntity(this, bb.expand(range, range, range));

            for (int i = 0; i < list.size(); i++) {
               Entity entity = (Entity)list.get(i);
               if (this.canRideSeatOrRack(1 + sid, entity)) {
                  if (entity instanceof MCH_IEntityCanRideAircraft) {
                     if (((MCH_IEntityCanRideAircraft)entity).canRideAircraft(this, sid, info)) {
                        MCH_Lib.DbgLog(this.worldObj, "MCH_EntityAircraft.mountEntityToRack:%d:%s", sid, entity);
                        entity.mountEntity(seat);
                        countRideEntity++;
                        break;
                     }
                  } else if (entity.ridingEntity == null) {
                     NBTTagCompound nbt = entity.getEntityData();
                     if (nbt.hasKey("CanMountEntity") && nbt.getBoolean("CanMountEntity")) {
                        MCH_Lib.DbgLog(this.worldObj, "MCH_EntityAircraft.mountEntityToRack:%d:%s:%s", sid, entity, entity.getClass());
                        entity.mountEntity(seat);
                        countRideEntity++;
                        break;
                     }
                  }
               }
            }
         }
      }

      if (countRideEntity > 0) {
         W_WorldFunc.DEF_playSoundEffect(this.worldObj, this.posX, this.posY, this.posZ, "random.click", 1.0F, 1.0F);
      }
   }

   public void unmountEntityFromRack() {
      for (int sid = this.getSeatNum() - 1; sid >= 0; sid--) {
         MCH_EntitySeat seat = this.getSeat(sid);
         if (this.getSeatInfo(sid + 1) instanceof MCH_SeatRackInfo && seat != null && seat.riddenByEntity != null) {
            MCH_SeatRackInfo info = (MCH_SeatRackInfo)this.getSeatInfo(sid + 1);
            Entity entity = seat.riddenByEntity;
            Vec3 pos = info.getEntryPos();
            if (entity instanceof MCH_EntityAircraft) {
               if (pos.zCoord >= this.getAcInfo().bbZ) {
                  pos = pos.addVector(0.0, 0.0, 12.0);
               } else {
                  pos = pos.addVector(0.0, 0.0, -12.0);
               }
            }

            Vec3 v = MCH_Lib.RotVec3(pos.xCoord, pos.yCoord, pos.zCoord, -this.getRotYaw(), -this.getRotPitch(), -this.getRotRoll());
            seat.posX = entity.posX = this.posX + v.xCoord;
            seat.posY = entity.posY = this.posY + v.yCoord;
            seat.posZ = entity.posZ = this.posZ + v.zCoord;
            MCH_EntityAircraft.UnmountReserve ur = new MCH_EntityAircraft.UnmountReserve(
               entity, entity.posX, entity.posY, entity.posZ
            );
            ur.cnt = 8;
            this.listUnmountReserve.add(ur);
            entity.mountEntity(null);
            if (MCH_Lib.getBlockIdY(this, 3, -20) > 0) {
               MCH_Lib.DbgLog(this.worldObj, "MCH_EntityAircraft.unmountEntityFromRack:%d:%s", sid, entity);
            } else {
               MCH_Lib.DbgLog(this.worldObj, "MCH_EntityAircraft.unmountEntityFromRack:%d Parachute:%s", sid, entity);
               this.dropEntityParachute(entity);
            }
            break;
         }
      }
   }

   public void dropEntityParachute(Entity entity) {
      entity.motionX = this.motionX;
      entity.motionY = this.motionY;
      entity.motionZ = this.motionZ;
      MCH_EntityParachute parachute = new MCH_EntityParachute(this.worldObj, entity.posX, entity.posY, entity.posZ);
      parachute.rotationYaw = entity.rotationYaw;
      parachute.motionX = entity.motionX;
      parachute.motionY = entity.motionY;
      parachute.motionZ = entity.motionZ;
      parachute.fallDistance = entity.fallDistance;
      parachute.user = entity;
      parachute.setType(3);
      this.worldObj.spawnEntityInWorld(parachute);
   }

   public void rideRack() {
      if (this.ridingEntity == null) {
         AxisAlignedBB bb = this.getBoundingBox();
         List list = this.worldObj.getEntitiesWithinAABBExcludingEntity(this, bb.expand(60.0, 60.0, 60.0));

         for (int i = 0; i < list.size(); i++) {
            Entity entity = (Entity)list.get(i);
            if (entity instanceof MCH_EntityAircraft) {
               MCH_EntityAircraft ac = (MCH_EntityAircraft)entity;
               if (ac.getAcInfo() != null) {
                  for (int sid = 0; sid < ac.getSeatNum(); sid++) {
                     MCH_SeatInfo seatInfo = ac.getSeatInfo(1 + sid);
                     if (seatInfo instanceof MCH_SeatRackInfo && ac.canRideSeatOrRack(1 + sid, entity)) {
                        MCH_SeatRackInfo info = (MCH_SeatRackInfo)seatInfo;
                        MCH_EntitySeat seat = ac.getSeat(sid);
                        if (seat != null && seat.riddenByEntity == null) {
                           Vec3 v = ac.getTransformedPosition(info.getEntryPos());
                           float r = info.range;
                           if (this.posX >= v.xCoord - r
                              && this.posX <= v.xCoord + r
                              && this.posY >= v.yCoord - r
                              && this.posY <= v.yCoord + r
                              && this.posZ >= v.zCoord - r
                              && this.posZ <= v.zCoord + r
                              && this.canRideAircraft(ac, sid, info)) {
                              W_WorldFunc.DEF_playSoundEffect(
                                 this.worldObj, this.posX, this.posY, this.posZ, "random.click", 1.0F, 1.0F
                              );
                              this.mountEntity(seat);
                              return;
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public boolean canPutToRack() {
      for (int i = 0; i < this.getSeatNum(); i++) {
         MCH_EntitySeat seat = this.getSeat(i);
         MCH_SeatInfo seatInfo = this.getSeatInfo(i + 1);
         if (seat != null && seat.riddenByEntity == null && seatInfo instanceof MCH_SeatRackInfo) {
            return true;
         }
      }

      return false;
   }

   public boolean canDownFromRack() {
      for (int i = 0; i < this.getSeatNum(); i++) {
         MCH_EntitySeat seat = this.getSeat(i);
         MCH_SeatInfo seatInfo = this.getSeatInfo(i + 1);
         if (seat != null && seat.riddenByEntity != null && seatInfo instanceof MCH_SeatRackInfo) {
            return true;
         }
      }

      return false;
   }

   public void checkRideRack() {
      if (this.getCountOnUpdate() % 10 == 0) {
         this.canRideRackStatus = false;
         if (this.ridingEntity == null) {
            AxisAlignedBB bb = this.getBoundingBox();
            List list = this.worldObj.getEntitiesWithinAABBExcludingEntity(this, bb.expand(60.0, 60.0, 60.0));

            for (int i = 0; i < list.size(); i++) {
               Entity entity = (Entity)list.get(i);
               if (entity instanceof MCH_EntityAircraft) {
                  MCH_EntityAircraft ac = (MCH_EntityAircraft)entity;
                  if (ac.getAcInfo() != null) {
                     for (int sid = 0; sid < ac.getSeatNum(); sid++) {
                        MCH_SeatInfo seatInfo = ac.getSeatInfo(1 + sid);
                        if (seatInfo instanceof MCH_SeatRackInfo) {
                           MCH_SeatRackInfo info = (MCH_SeatRackInfo)seatInfo;
                           MCH_EntitySeat seat = ac.getSeat(sid);
                           if (seat != null && seat.riddenByEntity == null) {
                              Vec3 v = ac.getTransformedPosition(info.getEntryPos());
                              float r = info.range;
                              if (this.posX >= v.xCoord - r && this.posX <= v.xCoord + r) {
                                 boolean rx = true;
                              } else {
                                 boolean rx = false;
                              }

                              if (this.posY >= v.yCoord - r && this.posY <= v.yCoord + r) {
                                 boolean ry = true;
                              } else {
                                 boolean ry = false;
                              }

                              if (this.posZ >= v.zCoord - r && this.posZ <= v.zCoord + r) {
                                 boolean rz = true;
                              } else {
                                 boolean rz = false;
                              }

                              if (this.posX >= v.xCoord - r
                                 && this.posX <= v.xCoord + r
                                 && this.posY >= v.yCoord - r
                                 && this.posY <= v.yCoord + r
                                 && this.posZ >= v.zCoord - r
                                 && this.posZ <= v.zCoord + r
                                 && this.canRideAircraft(ac, sid, info)) {
                                 this.canRideRackStatus = true;
                                 return;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public boolean canRideRack() {
      return this.ridingEntity == null && this.canRideRackStatus;
   }

   @Override
   public boolean canRideAircraft(MCH_EntityAircraft ac, int seatID, MCH_SeatRackInfo info) {
      if (this.getAcInfo() == null) {
         return false;
      }

      if (ac.ridingEntity != null) {
         return false;
      }

      if (this.ridingEntity != null) {
         return false;
      }

      boolean canRide = false;

      for (String s : info.names) {
         if (s.equalsIgnoreCase(this.getAcInfo().name) || s.equalsIgnoreCase(this.getAcInfo().getKindName())) {
            canRide = true;
            break;
         }
      }

      if (!canRide) {
         for (MCH_AircraftInfo.RideRack rr : this.getAcInfo().rideRacks) {
            int id = ac.getAcInfo().getNumSeat() - 1 + (rr.rackID - 1);
            if (id == seatID && rr.name.equalsIgnoreCase(ac.getAcInfo().name)) {
               MCH_EntitySeat seat = ac.getSeat(ac.getAcInfo().getNumSeat() - 1 + rr.rackID - 1);
               if (seat != null && seat.riddenByEntity == null) {
                  canRide = true;
                  break;
               }
            }
         }

         if (!canRide) {
            return false;
         }
      }

      for (MCH_EntitySeat seat : this.getSeats()) {
         if (seat != null && seat.riddenByEntity instanceof MCH_IEntityCanRideAircraft) {
            return false;
         }
      }

      return true;
   }

   public boolean isMountedEntity(Entity entity) {
      return entity == null ? false : this.isMountedEntity(W_Entity.getEntityId(entity));
   }

   public EntityPlayer getFirstMountPlayer() {
      if (this.getRiddenByEntity() instanceof EntityPlayer) {
         return (EntityPlayer)this.getRiddenByEntity();
      }

      for (MCH_EntitySeat seat : this.getSeats()) {
         if (seat != null && seat.riddenByEntity instanceof EntityPlayer) {
            return (EntityPlayer)seat.riddenByEntity;
         }
      }

      return null;
   }

   public boolean isMountedSameTeamEntity(EntityLivingBase player) {
      if (player != null && player.getTeam() != null) {
         if (this.riddenByEntity instanceof EntityLivingBase && player.isOnSameTeam((EntityLivingBase)this.riddenByEntity)) {
            return true;
         }

         for (MCH_EntitySeat seat : this.getSeats()) {
            if (seat != null && seat.riddenByEntity instanceof EntityLivingBase && player.isOnSameTeam((EntityLivingBase)seat.riddenByEntity)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public boolean isMountedOtherTeamEntity(EntityLivingBase player) {
      if (player == null) {
         return false;
      }

      EntityLivingBase target = null;
      if (this.riddenByEntity instanceof EntityLivingBase) {
         target = (EntityLivingBase)this.riddenByEntity;
         if (player.getTeam() != null && target.getTeam() != null && !player.isOnSameTeam(target)) {
            return true;
         }
      }

      for (MCH_EntitySeat seat : this.getSeats()) {
         if (seat != null && seat.riddenByEntity instanceof EntityLivingBase) {
            target = (EntityLivingBase)seat.riddenByEntity;
            if (player.getTeam() != null && target.getTeam() != null && !player.isOnSameTeam(target)) {
               return true;
            }
         }
      }

      return false;
   }

   public boolean isMountedEntity(int entityId) {
      if (W_Entity.getEntityId(this.riddenByEntity) == entityId) {
         return true;
      }

      for (MCH_EntitySeat seat : this.getSeats()) {
         if (seat != null && seat.riddenByEntity != null && W_Entity.getEntityId(seat.riddenByEntity) == entityId) {
            return true;
         }
      }

      return false;
   }

   public void onInteractFirst(EntityPlayer player) {
   }

   public boolean checkTeam(EntityPlayer player) {
      for (int i = 0; i < 1 + this.getSeatNum(); i++) {
         Entity entity = this.getEntityBySeatId(i);
         if (entity instanceof EntityPlayer) {
            EntityPlayer riddenPlayer = (EntityPlayer)entity;
            if (riddenPlayer.getTeam() != null && !riddenPlayer.isOnSameTeam(player)) {
               return false;
            }
         }
      }

      return true;
   }

   public boolean interactFirst(EntityPlayer player, boolean ss) {
      this.switchSeat = ss;
      boolean ret = this.interactFirst(player);
      this.switchSeat = false;
      return ret;
   }

   @Override
   public boolean interactFirst(EntityPlayer player) {
      if (this.isDestroyed()) {
         return false;
      }

      if (this.getAcInfo() == null) {
         return false;
      }

      if (!this.checkTeam(player)) {
         return false;
      }

      ItemStack itemStack = player.getCurrentEquippedItem();
      if (itemStack != null && itemStack.getItem() instanceof MCH_ItemWrench) {
         if (!this.worldObj.isRemote && player.isSneaking()) {
            this.switchNextTextureName();
         }

         return false;
      } else {
         if (player.isSneaking()) {
            super.openInventory(player);
            return false;
         }

         if (!this.getAcInfo().canRide) {
            return false;
         }

         if (this.riddenByEntity != null || this.isUAV()) {
            return this.interactFirstSeat(player);
         }

         if (player.ridingEntity instanceof MCH_EntitySeat) {
            return false;
         }

         if (!this.canRideSeatOrRack(0, player)) {
            return false;
         }

         if (!this.switchSeat) {
            if (this.getAcInfo().haveCanopy() && this.isCanopyClose()) {
               this.openCanopy();
               return false;
            }

            if (this.getModeSwitchCooldown() > 0) {
               return false;
            }
         }

         this.closeCanopy();
         this.riddenByEntity = null;
         this.lastRiddenByEntity = null;
         this.initRadar();
         if (!this.worldObj.isRemote) {
            player.mountEntity(this);
            if (!this.keepOnRideRotation) {
               this.mountMobToSeats();
            }
         } else {
            this.updateClientSettings(0);
         }

         this.setCameraId(0);
         this.initPilotWeapon();
         this.lowPassPartialTicks.clear();
         if (this.getAcInfo().name.equalsIgnoreCase("uh-1c")) {
            MCH_Achievement.addStat(this.riddenByEntity, MCH_Achievement.rideValkyries, 1);
         }

         this.onInteractFirst(player);
         return true;
      }
   }

   public boolean canRideSeatOrRack(int seatId, Entity entity) {
      if (this.getAcInfo() == null) {
         return false;
      }

      for (Integer[] a : this.getAcInfo().exclusionSeatList) {
         if (Arrays.asList(a).contains(seatId)) {
            Integer[] arr$ = a;
            int len$ = arr$.length;

            for (int i$ = 0; i$ < len$; i$++) {
               int id = arr$[i$];
               if (this.getEntityBySeatId(id) != null) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   public void updateClientSettings(int seatId) {
      this.cs_dismountAll = MCH_Config.DismountAll.prmBool;
      this.cs_heliAutoThrottleDown = MCH_Config.AutoThrottleDownHeli.prmBool;
      this.cs_planeAutoThrottleDown = MCH_Config.AutoThrottleDownPlane.prmBool;
      this.cs_tankAutoThrottleDown = MCH_Config.AutoThrottleDownTank.prmBool;
      this.camera.setShaderSupport(seatId, W_EntityRenderer.isShaderSupport());
      MCH_PacketNotifyClientSetting.send();
   }

   @Override
   public boolean canLockEntity(Entity entity) {
      return !this.isMountedEntity(entity);
   }

   public void switchNextSeat(Entity entity) {
      if (entity != null) {
         if (this.seats != null && this.seats.length > 0) {
            if (this.isMountedEntity(entity)) {
               boolean isFound = false;
               int sid = 1;

               for (MCH_EntitySeat seat : this.seats) {
                  if (seat != null) {
                     if (this.getSeatInfo(sid) instanceof MCH_SeatRackInfo) {
                        break;
                     }

                     if (W_Entity.isEqual(seat.riddenByEntity, entity)) {
                        isFound = true;
                     } else if (isFound && seat.riddenByEntity == null) {
                        entity.mountEntity(seat);
                        return;
                     }

                     sid++;
                  }
               }

               sid = 1;

               for (MCH_EntitySeat seat : this.seats) {
                  if (seat != null && seat.riddenByEntity == null) {
                     if (!(this.getSeatInfo(sid) instanceof MCH_SeatRackInfo)) {
                        entity.mountEntity(seat);
                        this.onMountPlayerSeat(seat, entity);
                        return;
                     }
                     break;
                  }

                  sid++;
               }
            }
         }
      }
   }

   public void switchPrevSeat(Entity entity) {
      if (entity != null) {
         if (this.seats != null && this.seats.length > 0) {
            if (this.isMountedEntity(entity)) {
               boolean isFound = false;

               for (int i = this.seats.length - 1; i >= 0; i--) {
                  MCH_EntitySeat seat = this.seats[i];
                  if (seat != null) {
                     if (W_Entity.isEqual(seat.riddenByEntity, entity)) {
                        isFound = true;
                     } else if (isFound && seat.riddenByEntity == null) {
                        entity.mountEntity(seat);
                        return;
                     }
                  }
               }

               for (int i = this.seats.length - 1; i >= 0; i--) {
                  MCH_EntitySeat seat = this.seats[i];
                  if (!(this.getSeatInfo(i + 1) instanceof MCH_SeatRackInfo) && seat != null && seat.riddenByEntity == null) {
                     entity.mountEntity(seat);
                     return;
                  }
               }
            }
         }
      }
   }

   public Entity[] getParts() {
      return this.partEntities;
   }

   public float getSoundVolume() {
      return 1.0F;
   }

   public float getSoundPitch() {
      return 1.0F;
   }

   public abstract String getDefaultSoundName();

   public String getSoundName() {
      if (this.getAcInfo() == null) {
         return "";
      } else {
         return !this.getAcInfo().soundMove.isEmpty() ? this.getAcInfo().soundMove : this.getDefaultSoundName();
      }
   }

   @Override
   public boolean isSkipNormalRender() {
      return this.ridingEntity instanceof MCH_EntitySeat;
   }

   public boolean isRenderBullet(Entity entity, Entity rider) {
      return !this.isCameraView(rider) || !W_Entity.isEqual(this.getTVMissile(), entity) || !W_Entity.isEqual(this.getTVMissile().shootingEntity, rider);
   }

   public boolean isCameraView(Entity entity) {
      return this.getIsGunnerMode(entity) || this.isUAV();
   }

   public void updateCamera(double x, double y, double z) {
      if (this.worldObj.isRemote) {
         if (this.getTVMissile() != null) {
            this.camera.setPosition(this.TVmissile.posX, this.TVmissile.posY, this.TVmissile.posZ);
            this.camera.setCameraZoom(1.0F);
            this.TVmissile.isSpawnParticle = !this.isMissileCameraMode(this.TVmissile.shootingEntity);
         } else {
            this.setTVMissile(null);
            MCH_AircraftInfo.CameraPosition cpi = this.getCameraPosInfo();
            Vec3 cp = cpi != null ? cpi.pos : Vec3.createVectorHelper(0.0, 0.0, 0.0);
            Vec3 v = MCH_Lib.RotVec3(cp, -this.getRotYaw(), -this.getRotPitch(), -this.getRotRoll());
            this.camera.setPosition(x + v.xCoord, y + v.yCoord, z + v.zCoord);
         }
      }
   }

   public void updateCameraRotate(float yaw, float pitch) {
      this.camera.prevRotationYaw = this.camera.rotationYaw;
      this.camera.prevRotationPitch = this.camera.rotationPitch;
      this.camera.rotationYaw = yaw;
      this.camera.rotationPitch = pitch;
   }

   public void updatePartCameraRotate() {
      if (this.worldObj.isRemote) {
         Entity e = this.getEntityBySeatId(1);
         if (e == null) {
            e = this.getRiddenByEntity();
         }

         if (e != null) {
            this.camera.partRotationYaw = e.rotationYaw;
            float pitch = e.rotationPitch;
            this.camera.prevPartRotationYaw = this.camera.partRotationYaw;
            this.camera.prevPartRotationPitch = this.camera.partRotationPitch;
            this.camera.partRotationPitch = pitch;
         }
      }
   }

   public void setTVMissile(MCH_EntityTvMissile entity) {
      this.TVmissile = entity;
   }

   public MCH_EntityTvMissile getTVMissile() {
      return this.TVmissile != null && !this.TVmissile.isDead ? this.TVmissile : null;
   }

   public MCH_WeaponSet[] createWeapon(int seat_num) {
      this.currentWeaponID = new int[seat_num];

      for (int i = 0; i < this.currentWeaponID.length; i++) {
         this.currentWeaponID[i] = -1;
      }

      if (this.getAcInfo() != null && this.getAcInfo().weaponSetList.size() > 0 && seat_num > 0) {
         MCH_WeaponSet[] weaponSetArray = new MCH_WeaponSet[this.getAcInfo().weaponSetList.size()];

         for (int i = 0; i < this.getAcInfo().weaponSetList.size(); i++) {
            MCH_AircraftInfo.WeaponSet ws = this.getAcInfo().weaponSetList.get(i);
            MCH_WeaponBase[] wb = new MCH_WeaponBase[ws.weapons.size()];

            for (int j = 0; j < ws.weapons.size(); j++) {
               wb[j] = MCH_WeaponCreator.createWeapon(
                  this.worldObj, ws.type, ws.weapons.get(j).pos, ws.weapons.get(j).yaw, ws.weapons.get(j).pitch, this, ws.weapons.get(j).turret
               );
               wb[j].aircraft = this;
            }

            if (wb.length > 0 && wb[0] != null) {
               float defYaw = ws.weapons.get(0).defaultYaw;
               weaponSetArray[i] = new MCH_WeaponSet(wb);
               weaponSetArray[i].prevRotationYaw = defYaw;
               weaponSetArray[i].rotationYaw = defYaw;
               weaponSetArray[i].defaultRotationYaw = defYaw;
            }
         }

         return weaponSetArray;
      } else {
         return new MCH_WeaponSet[]{this.dummyWeapon};
      }
   }

   public void switchWeapon(Entity entity, int id) {
      int sid = this.getSeatIdByEntity(entity);
      if (this.isValidSeatID(sid)) {
         int beforeWeaponID = this.currentWeaponID[sid];
         if (this.getWeaponNum() > 0 && this.currentWeaponID.length > 0) {
            if (id < 0) {
               this.currentWeaponID[sid] = -1;
            }

            if (id >= this.getWeaponNum()) {
               id = this.getWeaponNum() - 1;
            }

            MCH_Lib.DbgLog(this.worldObj, "switchWeapon:" + W_Entity.getEntityId(entity) + " -> " + id);
            this.getCurrentWeapon(entity).reload();
            this.currentWeaponID[sid] = id;
            MCH_WeaponSet ws = this.getCurrentWeapon(entity);
            ws.onSwitchWeapon(this.worldObj.isRemote, this.isInfinityAmmo(entity));
            if (!this.worldObj.isRemote) {
               MCH_PacketNotifyWeaponID.send(this, sid, id, ws.getAmmoNum(), ws.getRestAllAmmoNum());
            }
         }
      }
   }

   public void updateWeaponID(int sid, int id) {
      if (sid >= 0 && sid < this.currentWeaponID.length) {
         if (this.getWeaponNum() > 0 && this.currentWeaponID.length > 0) {
            if (id < 0) {
               this.currentWeaponID[sid] = -1;
            }

            if (id >= this.getWeaponNum()) {
               id = this.getWeaponNum() - 1;
            }

            MCH_Lib.DbgLog(this.worldObj, "switchWeapon:seatID=" + sid + ", WeaponID=" + id);
            this.currentWeaponID[sid] = id;
         }
      }
   }

   public void updateWeaponRestAmmo(int id, int num) {
      if (id < this.getWeaponNum()) {
         this.getWeapon(id).setRestAllAmmoNum(num);
      }
   }

   public MCH_WeaponSet getWeaponByName(String name) {
      for (MCH_WeaponSet ws : this.weapons) {
         if (ws.isEqual(name)) {
            return ws;
         }
      }

      return null;
   }

   public int getWeaponIdByName(String name) {
      int id = 0;

      for (MCH_WeaponSet ws : this.weapons) {
         if (ws.isEqual(name)) {
            return id;
         }

         id++;
      }

      return -1;
   }

   public void reloadAllWeapon() {
      for (int i = 0; i < this.getWeaponNum(); i++) {
         this.getWeapon(i).reloadMag();
      }
   }

   public MCH_WeaponSet getFirstSeatWeapon() {
      return this.currentWeaponID != null && this.currentWeaponID.length > 0 && this.currentWeaponID[0] >= 0
         ? this.getWeapon(this.currentWeaponID[0])
         : this.getWeapon(0);
   }

   public void initCurrentWeapon(Entity entity) {
      int sid = this.getSeatIdByEntity(entity);
      MCH_Lib.DbgLog(this.worldObj, "initCurrentWeapon:" + W_Entity.getEntityId(entity) + ":%d", sid);
      if (sid >= 0 && sid < this.currentWeaponID.length) {
         this.currentWeaponID[sid] = -1;
         if (entity instanceof EntityPlayer) {
            this.currentWeaponID[sid] = this.getNextWeaponID(entity, 1);
            this.switchWeapon(entity, this.getCurrentWeaponID(entity));
            if (this.worldObj.isRemote) {
               MCH_PacketIndNotifyAmmoNum.send(this, -1);
            }
         }
      }
   }

   public void initPilotWeapon() {
      this.currentWeaponID[0] = -1;
   }

   public MCH_WeaponSet getCurrentWeapon(Entity entity) {
      return this.getWeapon(this.getCurrentWeaponID(entity));
   }

   protected MCH_WeaponSet getWeapon(int id) {
      return id >= 0 && this.weapons.length > 0 && id < this.weapons.length ? this.weapons[id] : this.dummyWeapon;
   }

   public int getWeaponIDBySeatID(int sid) {
      return sid >= 0 && sid < this.currentWeaponID.length ? this.currentWeaponID[sid] : -1;
   }

   public double getLandInDistance(Entity user) {
      if (this.lastCalcLandInDistanceCount != this.getCountOnUpdate() && this.getCountOnUpdate() % 5 == 0) {
         this.lastCalcLandInDistanceCount = this.getCountOnUpdate();
         MCH_WeaponParam prm = new MCH_WeaponParam();
         prm.setPosition(this.posX, this.posY, this.posZ);
         prm.entity = this;
         prm.user = user;
         prm.isInfinity = this.isInfinityAmmo(prm.user);
         if (prm.user != null) {
            MCH_WeaponSet currentWs = this.getCurrentWeapon(prm.user);
            if (currentWs != null) {
               int sid = this.getSeatIdByEntity(prm.user);
               if (this.getAcInfo().getWeaponSetById(sid) != null) {
                  prm.isTurret = this.getAcInfo().getWeaponSetById(sid).weapons.get(0).turret;
               }

               this.lastLandInDistance = currentWs.getLandInDistance(prm);
            }
         }
      }

      return this.lastLandInDistance;
   }

   public boolean useCurrentWeapon(Entity user) {
      MCH_WeaponParam prm = new MCH_WeaponParam();
      prm.setPosition(this.posX, this.posY, this.posZ);
      prm.entity = this;
      prm.user = user;
      return this.useCurrentWeapon(prm);
   }

   public boolean useCurrentWeapon(MCH_WeaponParam prm) {
      prm.isInfinity = this.isInfinityAmmo(prm.user);
      if (prm.user != null) {
         MCH_WeaponSet currentWs = this.getCurrentWeapon(prm.user);
         if (currentWs != null && currentWs.canUse()) {
            int sid = this.getSeatIdByEntity(prm.user);
            if (this.getAcInfo().getWeaponSetById(sid) != null) {
               prm.isTurret = this.getAcInfo().getWeaponSetById(sid).weapons.get(0).turret;
            }

            int lastUsedIndex = currentWs.getCurrentWeaponIndex();
            if (currentWs.use(prm)) {
               for (MCH_WeaponSet ws : this.weapons) {
                  if (ws != currentWs && !ws.getInfo().group.isEmpty() && ws.getInfo().group.equals(currentWs.getInfo().group)) {
                     ws.waitAndReloadByOther(prm.reload);
                  }
               }

               if (!this.worldObj.isRemote) {
                  int shift = 0;

                  for (MCH_WeaponSet ws : this.weapons) {
                     if (ws == currentWs) {
                        break;
                     }

                     shift += ws.getWeaponNum();
                  }

                  shift += lastUsedIndex;
                  this.useWeaponStat |= shift < 32 ? 1 << shift : 0;
               }

               return true;
            }
         }
      }

      return false;
   }

   public void switchCurrentWeaponMode(Entity entity) {
      this.getCurrentWeapon(entity).switchMode();
   }

   public int getWeaponNum() {
      return this.weapons.length;
   }

   public int getCurrentWeaponID(Entity entity) {
      if (!(entity instanceof EntityPlayer)) {
         return -1;
      }

      int id = this.getSeatIdByEntity(entity);
      return id >= 0 && id < this.currentWeaponID.length ? this.currentWeaponID[id] : -1;
   }

   public int getNextWeaponID(Entity entity, int step) {
      if (this.getAcInfo() == null) {
         return -1;
      }

      int sid = this.getSeatIdByEntity(entity);
      if (sid < 0) {
         return -1;
      }

      int id = this.getCurrentWeaponID(entity);

      int i;
      for (i = 0; i < this.getWeaponNum(); i++) {
         if (step >= 0) {
            id = (id + 1) % this.getWeaponNum();
         } else {
            id = id > 0 ? id - 1 : this.getWeaponNum() - 1;
         }

         MCH_AircraftInfo.Weapon w = this.getAcInfo().getWeaponById(id);
         if (w != null) {
            MCH_WeaponInfo wi = this.getWeaponInfoById(id);
            int wpsid = this.getWeaponSeatID(wi, w);
            if (wpsid < this.getSeatNum() + 1 + 1 && (wpsid == sid || sid == 0 && w.canUsePilot && !(this.getEntityBySeatId(wpsid) instanceof EntityPlayer))) {
               break;
            }
         }
      }

      if (i >= this.getWeaponNum()) {
         return -1;
      }

      MCH_Lib.DbgLog(this.worldObj, "getNextWeaponID:%d:->%d", W_Entity.getEntityId(entity), id);
      return id;
   }

   public int getWeaponSeatID(MCH_WeaponInfo wi, MCH_AircraftInfo.Weapon w) {
      return wi == null || (wi.target & 195) != 0 || !wi.type.isEmpty() || !MCH_MOD.proxy.isSinglePlayer() && !MCH_Config.TestMode.prmBool ? w.seatID : 1000;
   }

   public boolean isMissileCameraMode(Entity entity) {
      return this.getTVMissile() != null && this.isCameraView(entity);
   }

   public boolean isPilotReloading() {
      return this.getCommonStatus(2) || this.supplyAmmoWait > 0;
   }

   public int getUsedWeaponStat() {
      if (this.getAcInfo() == null) {
         return 0;
      }

      if (this.getAcInfo().getWeaponNum() <= 0) {
         return 0;
      }

      int stat = 0;
      int i = 0;

      for (MCH_WeaponSet w : this.weapons) {
         if (i >= 32) {
            break;
         }

         for (int wi = 0; wi < w.getWeaponNum() && i < 32; wi++) {
            stat |= w.isUsed(wi) ? 1 << i : 0;
            i++;
         }
      }

      return stat;
   }

   public boolean isWeaponNotCooldown(MCH_WeaponSet checkWs, int index) {
      if (this.getAcInfo() == null) {
         return false;
      }

      if (this.getAcInfo().getWeaponNum() <= 0) {
         return false;
      }

      int shift = 0;

      for (MCH_WeaponSet ws : this.weapons) {
         if (ws == checkWs) {
            break;
         }

         shift += ws.getWeaponNum();
      }

      shift += index;
      return shift < 32 ? (this.useWeaponStat & 1 << shift) != 0 : false;
   }

   public void updateWeapons() {
      if (this.getAcInfo() != null) {
         if (this.getAcInfo().getWeaponNum() > 0) {
            int prevUseWeaponStat = this.useWeaponStat;
            if (!this.worldObj.isRemote) {
               this.useWeaponStat = this.useWeaponStat | this.getUsedWeaponStat();
               this.getDataWatcher().updateObject(24, new Integer(this.useWeaponStat));
               this.useWeaponStat = 0;
            } else {
               this.useWeaponStat = this.getDataWatcher().getWatchableObjectInt(24);
            }

            float yaw = MathHelper.wrapAngleTo180_float(this.getRotYaw());
            float pitch = MathHelper.wrapAngleTo180_float(this.getRotPitch());
            int id = 0;

            for (int wid = 0; wid < this.weapons.length; wid++) {
               MCH_WeaponSet w = this.weapons[wid];
               boolean isLongDelay = false;
               if (w.getFirstWeapon() != null) {
                  isLongDelay = w.isLongDelayWeapon();
               }

               boolean isSelected = false;

               for (int swid : this.currentWeaponID) {
                  if (swid == wid) {
                     isSelected = true;
                     break;
                  }
               }

               boolean isWpnUsed = false;

               for (int index = 0; index < w.getWeaponNum(); index++) {
                  boolean isPrevUsed = id < 32 && (prevUseWeaponStat & 1 << id) != 0;
                  boolean isUsed = id < 32 && (this.useWeaponStat & 1 << id) != 0;
                  if (isLongDelay && isPrevUsed && isUsed) {
                     isUsed = false;
                  }

                  isWpnUsed |= isUsed;
                  if (!isPrevUsed && isUsed) {
                     float recoil = w.getInfo().recoil;
                     if (recoil > 0.0F) {
                        this.recoilCount = 30;
                        this.recoilValue = recoil;
                        this.recoilYaw = w.rotationYaw;
                     }
                  }

                  if (this.worldObj.isRemote && isUsed) {
                     Vec3 wrv = MCH_Lib.RotVec3(0.0, 0.0, -1.0, -w.rotationYaw - yaw, -w.rotationPitch);
                     Vec3 spv = w.getCurrentWeapon().getShotPos(this);
                     this.spawnParticleMuzzleFlash(
                        this.worldObj,
                        w.getInfo(),
                        this.posX + spv.xCoord,
                        this.posY + spv.yCoord,
                        this.posZ + spv.zCoord,
                        wrv
                     );
                  }

                  w.updateWeapon(this, isUsed, index);
                  id++;
               }

               w.update(this, isSelected, isWpnUsed);
               MCH_AircraftInfo.Weapon wi = this.getAcInfo().getWeaponById(wid);
               if (wi != null && !this.isDestroyed()) {
                  Entity entity = this.getEntityBySeatId(this.getWeaponSeatID(this.getWeaponInfoById(wid), wi));
                  if (wi.canUsePilot && !(entity instanceof EntityPlayer)) {
                     entity = this.getEntityBySeatId(0);
                  }

                  if (entity instanceof EntityPlayer) {
                     if ((int)wi.minYaw != 0 || (int)wi.maxYaw != 0) {
                        float ty = wi.turret ? MathHelper.wrapAngleTo180_float(this.getLastRiderYaw()) - yaw : 0.0F;
                        float ey = MathHelper.wrapAngleTo180_float(entity.rotationYaw - yaw - wi.defaultYaw - ty);
                        if (Math.abs((int)wi.minYaw) < 360 && Math.abs((int)wi.maxYaw) < 360) {
                           float targetYaw = MCH_Lib.RNG(ey, wi.minYaw, wi.maxYaw);
                           float wy = w.rotationYaw - wi.defaultYaw - ty;
                           if (targetYaw < wy) {
                              if (wy - targetYaw > 15.0F) {
                                 wy -= 15.0F;
                              } else {
                                 wy = targetYaw;
                              }
                           } else if (targetYaw > wy) {
                              if (targetYaw - wy > 15.0F) {
                                 wy += 15.0F;
                              } else {
                                 wy = targetYaw;
                              }
                           }

                           w.rotationYaw = wy + wi.defaultYaw + ty;
                        } else {
                           w.rotationYaw = ey + ty;
                        }
                     }

                     float ep = MathHelper.wrapAngleTo180_float(entity.rotationPitch - pitch);
                     w.rotationPitch = MCH_Lib.RNG(ep, wi.minPitch, wi.maxPitch);
                     w.rotationTurretYaw = 0.0F;
                  } else {
                     w.rotationTurretYaw = this.getLastRiderYaw() - this.getRotYaw();
                     if (this.getTowedChainEntity() != null || this.ridingEntity != null) {
                        w.rotationYaw = 0.0F;
                     }
                  }
               }
            }

            this.updateWeaponBay();
            if (this.hitStatus > 0) {
               this.hitStatus--;
            }
         }
      }
   }

   public void updateWeaponsRotation() {
      if (this.getAcInfo() != null) {
         if (this.getAcInfo().getWeaponNum() > 0) {
            if (!this.isDestroyed()) {
               float yaw = MathHelper.wrapAngleTo180_float(this.getRotYaw());
               float pitch = MathHelper.wrapAngleTo180_float(this.getRotPitch());

               for (int wid = 0; wid < this.weapons.length; wid++) {
                  MCH_WeaponSet w = this.weapons[wid];
                  MCH_AircraftInfo.Weapon wi = this.getAcInfo().getWeaponById(wid);
                  if (wi != null) {
                     Entity entity = this.getEntityBySeatId(this.getWeaponSeatID(this.getWeaponInfoById(wid), wi));
                     if (wi.canUsePilot && !(entity instanceof EntityPlayer)) {
                        entity = this.getEntityBySeatId(0);
                     }

                     if (entity instanceof EntityPlayer) {
                        if ((int)wi.minYaw != 0 || (int)wi.maxYaw != 0) {
                           float ty = wi.turret ? MathHelper.wrapAngleTo180_float(this.getLastRiderYaw()) - yaw : 0.0F;
                           float ey = MathHelper.wrapAngleTo180_float(entity.rotationYaw - yaw - wi.defaultYaw - ty);
                           if (Math.abs((int)wi.minYaw) < 360 && Math.abs((int)wi.maxYaw) < 360) {
                              float targetYaw = MCH_Lib.RNG(ey, wi.minYaw, wi.maxYaw);
                              float wy = w.rotationYaw - wi.defaultYaw - ty;
                              if (targetYaw < wy) {
                                 if (wy - targetYaw > 15.0F) {
                                    wy -= 15.0F;
                                 } else {
                                    wy = targetYaw;
                                 }
                              } else if (targetYaw > wy) {
                                 if (targetYaw - wy > 15.0F) {
                                    wy += 15.0F;
                                 } else {
                                    wy = targetYaw;
                                 }
                              }

                              w.rotationYaw = wy + wi.defaultYaw + ty;
                           } else {
                              w.rotationYaw = ey + ty;
                           }
                        }

                        float ep = MathHelper.wrapAngleTo180_float(entity.rotationPitch - pitch);
                        w.rotationPitch = MCH_Lib.RNG(ep, wi.minPitch, wi.maxPitch);
                        w.rotationTurretYaw = 0.0F;
                     } else {
                        w.rotationTurretYaw = this.getLastRiderYaw() - this.getRotYaw();
                     }
                  }

                  w.prevRotationYaw = w.rotationYaw;
               }
            }
         }
      }
   }

   private void spawnParticleMuzzleFlash(World w, MCH_WeaponInfo wi, double px, double py, double pz, Vec3 wrv) {
      if (wi.listMuzzleFlashSmoke != null) {
         for (MCH_WeaponInfo.MuzzleFlash mf : wi.listMuzzleFlashSmoke) {
            double x = px + -wrv.xCoord * mf.dist;
            double y = py + -wrv.yCoord * mf.dist;
            double z = pz + -wrv.zCoord * mf.dist;
            MCH_ParticleParam p = new MCH_ParticleParam(w, "smoke", px, py, pz);
            p.size = mf.size;

            for (int i = 0; i < mf.num; i++) {
               p.a = mf.a * 0.9F + w.rand.nextFloat() * 0.1F;
               float color = w.rand.nextFloat() * 0.1F;
               p.r = color + mf.r * 0.9F;
               p.g = color + mf.g * 0.9F;
               p.b = color + mf.b * 0.9F;
               p.age = (int)(mf.age + 0.1 * mf.age * w.rand.nextFloat());
               p.posX = x + (w.rand.nextDouble() - 0.5) * mf.range;
               p.posY = y + (w.rand.nextDouble() - 0.5) * mf.range;
               p.posZ = z + (w.rand.nextDouble() - 0.5) * mf.range;
               p.motionX = w.rand.nextDouble() * (p.posX < x ? -0.2 : 0.2);
               p.motionY = w.rand.nextDouble() * (p.posY < y ? -0.03 : 0.03);
               p.motionZ = w.rand.nextDouble() * (p.posZ < z ? -0.2 : 0.2);
               MCH_ParticlesUtil.spawnParticle(p);
            }
         }
      }

      if (wi.listMuzzleFlash != null) {
         for (MCH_WeaponInfo.MuzzleFlash mf : wi.listMuzzleFlash) {
            float color = this.rand.nextFloat() * 0.1F + 0.9F;
            MCH_ParticlesUtil.spawnParticleExplode(
               this.worldObj,
               px + -wrv.xCoord * mf.dist,
               py + -wrv.yCoord * mf.dist,
               pz + -wrv.zCoord * mf.dist,
               mf.size,
               color * mf.r,
               color * mf.g,
               color * mf.b,
               mf.a,
               mf.age + w.rand.nextInt(3)
            );
         }
      }
   }

   private void updateWeaponBay() {
      for (int i = 0; i < this.weaponBays.length; i++) {
         MCH_EntityAircraft.WeaponBay wb = this.weaponBays[i];
         MCH_AircraftInfo.WeaponBay info = this.getAcInfo().partWeaponBay.get(i);
         boolean isSelected = false;
         Integer[] arr$ = info.weaponIds;
         int len$ = arr$.length;

         for (int i$ = 0; i$ < len$; i$++) {
            int wid = arr$[i$];

            for (int sid = 0; sid < this.currentWeaponID.length; sid++) {
               if (wid == this.currentWeaponID[sid] && this.getEntityBySeatId(sid) != null) {
                  isSelected = true;
               }
            }
         }

         wb.prevRot = wb.rot;
         if (isSelected) {
            if (wb.rot < 90.0F) {
               wb.rot += 3.0F;
            }

            if (wb.rot >= 90.0F) {
               wb.rot = 90.0F;
            }
         } else {
            if (wb.rot > 0.0F) {
               wb.rot -= 3.0F;
            }

            if (wb.rot <= 0.0F) {
               wb.rot = 0.0F;
            }
         }
      }
   }

   public int getHitStatus() {
      return this.hitStatus;
   }

   public int getMaxHitStatus() {
      return 15;
   }

   public void hitBullet() {
      this.hitStatus = this.getMaxHitStatus();
   }

   public void initRotationYaw(float yaw) {
      this.rotationYaw = yaw;
      this.prevRotationYaw = yaw;
      this.lastRiderYaw = yaw;
      this.lastSearchLightYaw = yaw;

      for (MCH_WeaponSet w : this.weapons) {
         w.rotationYaw = w.defaultRotationYaw;
         w.rotationPitch = 0.0F;
      }
   }

   public MCH_AircraftInfo getAcInfo() {
      return this.acInfo;
   }

   public abstract Item getItem();

   public void setAcInfo(MCH_AircraftInfo info) {
      this.acInfo = info;
      if (info != null) {
         this.partHatch = this.createHatch();
         this.partCanopy = this.createCanopy();
         this.partLandingGear = this.createLandingGear();
         this.weaponBays = this.createWeaponBays();
         this.rotPartRotation = new float[info.partRotPart.size()];
         this.prevRotPartRotation = new float[info.partRotPart.size()];
         this.extraBoundingBox = this.createExtraBoundingBox();
         this.partEntities = this.createParts();
         this.stepHeight = info.stepHeight;
      }
   }

   public MCH_BoundingBox[] createExtraBoundingBox() {
      MCH_BoundingBox[] ar = new MCH_BoundingBox[this.getAcInfo().extraBoundingBox.size()];
      int i = 0;

      for (MCH_BoundingBox bb : this.getAcInfo().extraBoundingBox) {
         ar[i] = bb.copy();
         i++;
      }

      return ar;
   }

   public Entity[] createParts() {
      return new Entity[]{this.partEntities[0]};
   }

   public void updateUAV() {
      if (this.isUAV()) {
         if (this.worldObj.isRemote) {
            int eid = this.getDataWatcher().getWatchableObjectInt(22);
            if (eid > 0) {
               if (this.uavStation == null) {
                  Entity uavEntity = this.worldObj.getEntityByID(eid);
                  if (uavEntity instanceof MCH_EntityUavStation) {
                     this.uavStation = (MCH_EntityUavStation)uavEntity;
                     this.uavStation.setControlAircract(this);
                  }
               }
            } else if (this.uavStation != null) {
               this.uavStation.setControlAircract(null);
               this.uavStation = null;
            }
         } else if (this.uavStation != null) {
            double udx = this.posX - this.uavStation.posX;
            double udz = this.posZ - this.uavStation.posZ;
            if (udx * udx + udz * udz > 15129.0) {
               this.uavStation.setControlAircract(null);
               this.setUavStation(null);
               this.attackEntityFrom(DamageSource.outOfWorld, this.getMaxHP() + 10);
            }
         }

         if (this.uavStation != null && this.uavStation.isDead) {
            this.uavStation = null;
         }
      }
   }

   public void switchGunnerMode(boolean mode) {
      boolean debug_bk_mode = this.isGunnerMode;
      Entity pilot = this.getEntityBySeatId(0);
      if (!mode || this.canSwitchGunnerMode()) {
         if (this.isGunnerMode && !mode) {
            this.setCurrentThrottle(this.beforeHoverThrottle);
            this.isGunnerMode = false;
            this.camera.setCameraZoom(1.0F);
            this.getCurrentWeapon(pilot).onSwitchWeapon(this.worldObj.isRemote, this.isInfinityAmmo(pilot));
         } else if (!this.isGunnerMode && mode) {
            this.beforeHoverThrottle = this.getCurrentThrottle();
            this.isGunnerMode = true;
            this.camera.setCameraZoom(1.0F);
            this.getCurrentWeapon(pilot).onSwitchWeapon(this.worldObj.isRemote, this.isInfinityAmmo(pilot));
         }
      }

      MCH_Lib.DbgLog(this.worldObj, "switchGunnerMode %s->%s", debug_bk_mode ? "ON" : "OFF", mode ? "ON" : "OFF");
   }

   public boolean canSwitchGunnerMode() {
      if (this.getAcInfo() == null || !this.getAcInfo().isEnableGunnerMode) {
         return false;
      } else if (!this.isCanopyClose()) {
         return false;
      } else {
         return !this.getAcInfo().isEnableConcurrentGunnerMode && this.getEntityBySeatId(1) instanceof EntityPlayer ? false : !this.isHoveringMode();
      }
   }

   public boolean canSwitchGunnerModeOtherSeat(EntityPlayer player) {
      int sid = this.getSeatIdByEntity(player);
      if (sid > 0) {
         MCH_SeatInfo info = this.getSeatInfo(sid);
         if (info != null) {
            return info.gunner && info.switchgunner;
         }
      }

      return false;
   }

   public void switchGunnerModeOtherSeat(EntityPlayer player) {
      this.isGunnerModeOtherSeat = !this.isGunnerModeOtherSeat;
   }

   public boolean isHoveringMode() {
      return this.isHoveringMode;
   }

   public void switchHoveringMode(boolean mode) {
      this.stopRepelling();
      if (this.canSwitchHoveringMode() && this.isHoveringMode() != mode) {
         if (mode) {
            this.beforeHoverThrottle = this.getCurrentThrottle();
         } else {
            this.setCurrentThrottle(this.beforeHoverThrottle);
         }

         this.isHoveringMode = mode;
         if (this.riddenByEntity != null) {
            this.riddenByEntity.rotationPitch = 0.0F;
            this.riddenByEntity.prevRotationPitch = 0.0F;
         }
      }
   }

   public boolean canSwitchHoveringMode() {
      return this.getAcInfo() == null ? false : !this.isGunnerMode;
   }

   public boolean isHovering() {
      return this.isGunnerMode || this.isHoveringMode();
   }

   public boolean getIsGunnerMode(Entity entity) {
      if (this.getAcInfo() == null) {
         return false;
      } else {
         int id = this.getSeatIdByEntity(entity);
         if (id < 0) {
            return false;
         } else if (id == 0 && this.getAcInfo().isEnableGunnerMode) {
            return this.isGunnerMode;
         } else {
            MCH_SeatInfo[] st = this.getSeatsInfo();
            if (id >= st.length || !st[id].gunner) {
               return false;
            } else {
               return this.worldObj.isRemote && st[id].switchgunner ? this.isGunnerModeOtherSeat : true;
            }
         }
      }
   }

   public boolean isPilot(Entity player) {
      return W_Entity.isEqual(this.getRiddenByEntity(), player);
   }

   public boolean canSwitchFreeLook() {
      return true;
   }

   public boolean isFreeLookMode() {
      return this.getCommonStatus(1) || this.isRepelling();
   }

   public void switchFreeLookMode(boolean b) {
      this.setCommonStatus(1, b);
   }

   public void switchFreeLookModeClient(boolean b) {
      this.setCommonStatus(1, b, true);
   }

   public boolean canSwitchGunnerFreeLook(EntityPlayer player) {
      MCH_SeatInfo seatInfo = this.getSeatInfo(player);
      return seatInfo != null && seatInfo.fixRot && this.getIsGunnerMode(player);
   }

   public boolean isGunnerLookMode(EntityPlayer player) {
      return this.isPilot(player) ? false : this.isGunnerFreeLookMode;
   }

   public void switchGunnerFreeLookMode(boolean b) {
      this.isGunnerFreeLookMode = b;
   }

   public void switchGunnerFreeLookMode() {
      this.switchGunnerFreeLookMode(!this.isGunnerFreeLookMode);
   }

   public void updateParts(int stat) {
      if (!this.isDestroyed()) {
         MCH_Parts[] parts = new MCH_Parts[]{this.partHatch, this.partCanopy, this.partLandingGear};

         for (MCH_Parts p : parts) {
            if (p != null) {
               p.updateStatusClient(stat);
               p.update();
            }
         }

         if (!this.isDestroyed() && !this.worldObj.isRemote && this.partLandingGear != null) {
            int blockId = 0;
            if (!this.isLandingGearFolded() && this.partLandingGear.getFactor() <= 0.1F) {
               blockId = MCH_Lib.getBlockIdY(this, 3, -20);
               if ((!(this.getCurrentThrottle() > 0.8F) || this.onGround || blockId != 0)
                  && this.getAcInfo().isFloat
                  && (this.isInWater() || MCH_Lib.getBlockY(this, 3, -20, true) == W_Block.getWater())) {
                  this.partLandingGear.setStatusServer(true);
               }
            } else if (this.isLandingGearFolded() && this.partLandingGear.getFactor() >= 0.9F) {
               blockId = MCH_Lib.getBlockIdY(this, 3, -10);
               if (this.getCurrentThrottle() < this.getUnfoldLandingGearThrottle() && blockId != 0) {
                  boolean unfold = true;
                  if (this.getAcInfo().isFloat) {
                     blockId = MCH_Lib.getBlockIdY(
                        this.worldObj, this.posX, this.posY + 1.0 + this.getAcInfo().floatOffset, this.posZ, 1, -150, true
                     );
                     if (W_Block.isEqual(blockId, W_Block.getWater())) {
                        unfold = false;
                     }
                  }

                  if (unfold) {
                     this.partLandingGear.setStatusServer(false);
                  }
               } else if (this.getVtolMode() == 2 && blockId != 0) {
                  this.partLandingGear.setStatusServer(false);
               }
            }
         }
      }
   }

   public float getUnfoldLandingGearThrottle() {
      return 0.8F;
   }

   private int getPartStatus() {
      return this.getDataWatcher().getWatchableObjectInt(31);
   }

   private void setPartStatus(int n) {
      this.getDataWatcher().updateObject(31, n);
   }

   protected void initPartRotation(float yaw, float pitch) {
      this.lastRiderYaw = yaw;
      this.prevLastRiderYaw = yaw;
      this.camera.partRotationYaw = yaw;
      this.camera.prevPartRotationYaw = yaw;
      this.lastSearchLightYaw = yaw;
   }

   public int getLastPartStatusMask() {
      return 24;
   }

   public int getModeSwitchCooldown() {
      return this.modeSwitchCooldown;
   }

   public void setModeSwitchCooldown(int n) {
      this.modeSwitchCooldown = n;
   }

   protected MCH_EntityAircraft.WeaponBay[] createWeaponBays() {
      MCH_EntityAircraft.WeaponBay[] wbs = new MCH_EntityAircraft.WeaponBay[this.getAcInfo().partWeaponBay.size()];

      for (int i = 0; i < wbs.length; i++) {
         wbs[i] = new MCH_EntityAircraft.WeaponBay();
      }

      return wbs;
   }

   protected MCH_Parts createHatch() {
      MCH_Parts hatch = null;
      if (this.getAcInfo().haveHatch()) {
         hatch = new MCH_Parts(this, 4, 31, "Hatch");
         hatch.rotationMax = 90.0F;
         hatch.rotationInv = 1.5F;
         hatch.soundEndSwichOn.setPrm("plane_cc", 1.0F, 1.0F);
         hatch.soundEndSwichOff.setPrm("plane_cc", 1.0F, 1.0F);
         hatch.soundSwitching.setPrm("plane_cv", 1.0F, 0.5F);
      }

      return hatch;
   }

   public boolean haveHatch() {
      return this.partHatch != null;
   }

   public boolean canFoldHatch() {
      return this.partHatch != null && this.modeSwitchCooldown <= 0 ? this.partHatch.isOFF() : false;
   }

   public boolean canUnfoldHatch() {
      return this.partHatch != null && this.modeSwitchCooldown <= 0 ? this.partHatch.isON() : false;
   }

   public void foldHatch(boolean fold) {
      this.foldHatch(fold, false);
   }

   public void foldHatch(boolean fold, boolean force) {
      if (this.partHatch != null) {
         if (force || this.modeSwitchCooldown <= 0) {
            this.partHatch.setStatusServer(fold);
            this.modeSwitchCooldown = 20;
            if (!fold) {
               this.stopUnmountCrew();
            }
         }
      }
   }

   public float getHatchRotation() {
      return this.partHatch != null ? this.partHatch.rotation : 0.0F;
   }

   public float getPrevHatchRotation() {
      return this.partHatch != null ? this.partHatch.prevRotation : 0.0F;
   }

   public void foldLandingGear() {
      if (this.partLandingGear != null && this.getModeSwitchCooldown() <= 0) {
         this.partLandingGear.setStatusServer(true);
         this.setModeSwitchCooldown(20);
      }
   }

   public void unfoldLandingGear() {
      if (this.partLandingGear != null && this.getModeSwitchCooldown() <= 0) {
         if (this.isLandingGearFolded()) {
            this.partLandingGear.setStatusServer(false);
            this.setModeSwitchCooldown(20);
         }
      }
   }

   public boolean canFoldLandingGear() {
      if (this.getLandingGearRotation() >= 1.0F) {
         return false;
      }

      Block block = MCH_Lib.getBlockY(this, 3, -10, true);
      return !this.isLandingGearFolded() && block == W_Blocks.air;
   }

   public boolean canUnfoldLandingGear() {
      return this.getLandingGearRotation() < 89.0F ? false : this.isLandingGearFolded();
   }

   public boolean isLandingGearFolded() {
      return this.partLandingGear != null ? this.partLandingGear.getStatus() : false;
   }

   protected MCH_Parts createLandingGear() {
      MCH_Parts lg = null;
      if (this.getAcInfo().haveLandingGear()) {
         lg = new MCH_Parts(this, 2, 31, "LandingGear");
         lg.rotationMax = 90.0F;
         lg.rotationInv = 2.5F;
         lg.soundStartSwichOn.setPrm("plane_cc", 1.0F, 0.5F);
         lg.soundEndSwichOn.setPrm("plane_cc", 1.0F, 0.5F);
         lg.soundStartSwichOff.setPrm("plane_cc", 1.0F, 0.5F);
         lg.soundEndSwichOff.setPrm("plane_cc", 1.0F, 0.5F);
         lg.soundSwitching.setPrm("plane_cv", 1.0F, 0.75F);
      }

      return lg;
   }

   public float getLandingGearRotation() {
      return this.partLandingGear != null ? this.partLandingGear.rotation : 0.0F;
   }

   public float getPrevLandingGearRotation() {
      return this.partLandingGear != null ? this.partLandingGear.prevRotation : 0.0F;
   }

   public int getVtolMode() {
      return 0;
   }

   public void openCanopy() {
      if (this.partCanopy != null && this.getModeSwitchCooldown() <= 0) {
         this.partCanopy.setStatusServer(true);
         this.setModeSwitchCooldown(20);
      }
   }

   public void openCanopy_EjectSeat() {
      if (this.partCanopy != null) {
         this.partCanopy.setStatusServer(true, false);
         this.setModeSwitchCooldown(40);
      }
   }

   public void closeCanopy() {
      if (this.partCanopy != null && this.getModeSwitchCooldown() <= 0) {
         if (this.getCanopyStat()) {
            this.partCanopy.setStatusServer(false);
            this.setModeSwitchCooldown(20);
         }
      }
   }

   public boolean getCanopyStat() {
      return this.partCanopy != null ? this.partCanopy.getStatus() : false;
   }

   public boolean isCanopyClose() {
      return this.partCanopy == null ? true : !this.getCanopyStat() && this.getCanopyRotation() <= 0.01F;
   }

   public float getCanopyRotation() {
      return this.partCanopy != null ? this.partCanopy.rotation : 0.0F;
   }

   public float getPrevCanopyRotation() {
      return this.partCanopy != null ? this.partCanopy.prevRotation : 0.0F;
   }

   protected MCH_Parts createCanopy() {
      MCH_Parts canopy = null;
      if (this.getAcInfo().haveCanopy()) {
         canopy = new MCH_Parts(this, 0, 31, "Canopy");
         canopy.rotationMax = 90.0F;
         canopy.rotationInv = 3.5F;
         canopy.soundEndSwichOn.setPrm("plane_cc", 1.0F, 1.0F);
         canopy.soundEndSwichOff.setPrm("plane_cc", 1.0F, 1.0F);
      }

      return canopy;
   }

   public boolean hasBrake() {
      return false;
   }

   public void setBrake(boolean b) {
      if (!this.worldObj.isRemote) {
         this.setCommonStatus(11, b);
      }
   }

   public boolean getBrake() {
      return this.getCommonStatus(11);
   }

   @Override
   public int getSizeInventory() {
      return this.getAcInfo() != null ? this.getAcInfo().inventorySize : 0;
   }

   @Override
   public String getInvName() {
      if (this.getAcInfo() == null) {
         return super.getInvName();
      }

      String s = this.getAcInfo().displayName;
      return s.length() <= 32 ? s : s.substring(0, 31);
   }

   @Override
   public boolean isInvNameLocalized() {
      return this.getAcInfo() != null;
   }

   public MCH_EntityChain getTowChainEntity() {
      return this.towChainEntity;
   }

   public void setTowChainEntity(MCH_EntityChain chainEntity) {
      this.towChainEntity = chainEntity;
   }

   public MCH_EntityChain getTowedChainEntity() {
      return this.towedChainEntity;
   }

   public void setTowedChainEntity(MCH_EntityChain towedChainEntity) {
      this.towedChainEntity = towedChainEntity;
   }

   protected class UnmountReserve {
      final Entity entity;
      final double posX;
      final double posY;
      final double posZ;
      int cnt = 5;

      public UnmountReserve(Entity e, double x, double y, double z) {
         this.entity = e;
         this.posX = x;
         this.posY = y;
         this.posZ = z;
      }
   }

   public class WeaponBay {
      public float rot = 0.0F;
      public float prevRot = 0.0F;
   }
}
