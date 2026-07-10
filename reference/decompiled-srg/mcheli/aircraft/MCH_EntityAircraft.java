/*
 * Decompiled with CFR 0.152.
 */
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
import mcheli.aircraft.MCH_AircraftBoundingBox;
import mcheli.aircraft.MCH_AircraftGuiContainer;
import mcheli.aircraft.MCH_AircraftInfo;
import mcheli.aircraft.MCH_AircraftInventory;
import mcheli.aircraft.MCH_BoundingBox;
import mcheli.aircraft.MCH_DummyCommandSender;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_EntityHide;
import mcheli.aircraft.MCH_EntityHitBox;
import mcheli.aircraft.MCH_EntitySeat;
import mcheli.aircraft.MCH_IEntityCanRideAircraft;
import mcheli.aircraft.MCH_ItemFuel;
import mcheli.aircraft.MCH_MissileDetector;
import mcheli.aircraft.MCH_PacketIndNotifyAmmoNum;
import mcheli.aircraft.MCH_PacketIndRotation;
import mcheli.aircraft.MCH_PacketNotifyAmmoNum;
import mcheli.aircraft.MCH_PacketNotifyClientSetting;
import mcheli.aircraft.MCH_PacketNotifyWeaponID;
import mcheli.aircraft.MCH_PacketSeatListRequest;
import mcheli.aircraft.MCH_Parts;
import mcheli.aircraft.MCH_Radar;
import mcheli.aircraft.MCH_SeatInfo;
import mcheli.aircraft.MCH_SeatRackInfo;
import mcheli.aircraft.MCH_SoundUpdater;
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
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.stats.Achievement;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ReportedException;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/*
 * Exception performing whole class analysis ignored.
 */
public abstract class MCH_EntityAircraft
extends W_EntityContainer
implements MCH_IEntityLockChecker,
MCH_IEntityCanRideAircraft,
IEntityAdditionalSpawnData {
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
    public boolean isRequestedSyncStatus = false;
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
    public float throttleBack = 0.0f;
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
    public List<UnmountReserve> listUnmountReserve = new ArrayList();
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
    public WeaponBay[] weaponBays;
    public float[] rotPartRotation;
    public float[] prevRotPartRotation;
    public float[] rotCrawlerTrack = new float[2];
    public float[] prevRotCrawlerTrack = new float[2];
    public float[] throttleCrawlerTrack = new float[2];
    public float[] rotTrackRoller = new float[2];
    public float[] prevRotTrackRoller = new float[2];
    public float rotWheel = 0.0f;
    public float prevRotWheel = 0.0f;
    public float rotYawWheel = 0.0f;
    public float prevRotYawWheel = 0.0f;
    private boolean isParachuting;
    public float ropesLength = 0.0f;
    private MCH_Queue<Vec3> prevPosition;
    private int tickRepelling;
    private int lastUsedRopeIndex;
    private boolean dismountedUserCtrl;
    public float lastSearchLightYaw;
    public float lastSearchLightPitch;
    public float rotLightHatch = 0.0f;
    public float prevRotLightHatch = 0.0f;
    public int recoilCount = 0;
    public float recoilYaw = 0.0f;
    public float recoilValue = 0.0f;
    public int brightnessHigh = 240;
    public int brightnessLow = 240;
    public final HashMap<Entity, Integer> noCollisionEntities = new HashMap();
    private double lastCalcLandInDistanceCount;
    private double lastLandInDistance;
    private static final MCH_EntitySeat[] seatsDummy = new MCH_EntitySeat[0];
    private boolean switchSeat = false;

    public MCH_EntityAircraft(World world) {
        super(world);
        this.setAcInfo(null);
        this.commonStatus = 0;
        this.dropContentsWhenDead = false;
        this.field_70158_ak = true;
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
        this.field_70155_l = MCH_Config.RenderDistanceWeight.prmDouble;
        this.setCommonUniqueId("");
        this.seatSearchCount = 0;
        this.seatsInfo = null;
        this.seats = new MCH_EntitySeat[0];
        this.pilotSeat = new MCH_EntityHitBox(world, this, 1.0f, 1.0f);
        this.pilotSeat.parent = this;
        this.partEntities = new Entity[]{this.pilotSeat};
        this.setTextureName("");
        this.camera = new MCH_Camera(world, (Entity)this, this.field_70165_t, this.field_70163_u, this.field_70161_v);
        this.setCameraId(0);
        this.lastRiddenByEntity = null;
        this.lastRidingEntity = null;
        this.soundUpdater = MCH_MOD.proxy.CreateSoundUpdater(this);
        this.countOnUpdate = 0;
        this.setTowChainEntity(null);
        this.dummyWeapon = new MCH_WeaponSet((MCH_WeaponBase)new MCH_WeaponDummy(this.field_70170_p, Vec3.func_72443_a((double)0.0, (double)0.0, (double)0.0), 0.0f, 0.0f, "", null));
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
        this.weaponBays = new WeaponBay[0];
        this.rotPartRotation = new float[0];
        this.prevRotPartRotation = new float[0];
        this.lastRiderYaw = 0.0f;
        this.prevLastRiderYaw = 0.0f;
        this.lastRiderPitch = 0.0f;
        this.prevLastRiderPitch = 0.0f;
        this.rotationRoll = 0.0f;
        this.prevRotationRoll = 0.0f;
        this.lowPassPartialTicks = new MCH_LowPassFilterFloat(10);
        this.extraBoundingBox = new MCH_BoundingBox[0];
        W_Reflection.setBoundingBox((Entity)this, (AxisAlignedBB)new MCH_AircraftBoundingBox(this));
        this.lastBBDamageFactor = 1.0f;
        this.inventory = new MCH_AircraftInventory(this);
        this.fuelConsumption = 0.0;
        this.fuelSuppliedCount = 0;
        this.canRideRackStatus = false;
        this.isParachuting = false;
        this.prevPosition = new MCH_Queue(10, (Object)Vec3.func_72443_a((double)0.0, (double)0.0, (double)0.0));
        this.lastSearchLightPitch = 0.0f;
        this.lastSearchLightYaw = 0.0f;
    }

    protected void func_70088_a() {
        super.func_70088_a();
        this.func_70096_w().func_75682_a(20, (Object)"");
        this.func_70096_w().func_75682_a(19, (Object)new Integer(0));
        this.func_70096_w().func_75682_a(23, (Object)new Integer(0));
        this.func_70096_w().func_75682_a(24, (Object)new Integer(0));
        this.func_70096_w().func_75682_a(25, (Object)new Integer(0));
        this.func_70096_w().func_75682_a(21, (Object)"");
        this.func_70096_w().func_75682_a(22, (Object)new Integer(0));
        this.func_70096_w().func_75682_a(26, (Object)new Short(0));
        this.func_70096_w().func_75682_a(27, (Object)new String(""));
        this.func_70096_w().func_75682_a(29, (Object)new Integer(0));
        this.func_70096_w().func_75682_a(31, (Object)new Integer(0));
        if (!this.field_70170_p.field_72995_K) {
            this.setCommonStatus(3, MCH_Config.InfinityAmmo.prmBool);
            this.setCommonStatus(4, MCH_Config.InfinityFuel.prmBool);
        }
        this.getEntityData().func_74778_a("EntityType", this.getEntityType());
    }

    public float getServerRoll() {
        return this.func_70096_w().func_75693_b(26);
    }

    public float getRotYaw() {
        return this.field_70177_z;
    }

    public float getRotPitch() {
        return this.field_70125_A;
    }

    public float getRotRoll() {
        return this.rotationRoll;
    }

    public void setRotYaw(float f) {
        this.field_70177_z = f;
    }

    public void setRotPitch(float f) {
        this.field_70125_A = f;
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
            this.setRotPitch(pitch += ogp, "applyOnGroundPitch");
        }
        this.setRotRoll(this.getRotRoll() * factor);
    }

    public float calcRotYaw(float partialTicks) {
        return this.field_70126_B + (this.getRotYaw() - this.field_70126_B) * partialTicks;
    }

    public float calcRotPitch(float partialTicks) {
        return this.field_70127_C + (this.getRotPitch() - this.field_70127_C) * partialTicks;
    }

    public float calcRotRoll(float partialTicks) {
        return this.prevRotationRoll + (this.getRotRoll() - this.prevRotationRoll) * partialTicks;
    }

    protected void func_70101_b(float y, float p) {
        this.setRotYaw(y % 360.0f);
        this.setRotPitch(p % 360.0f);
    }

    public boolean isInfinityAmmo(Entity player) {
        return this.isCreative(player) || this.getCommonStatus(3);
    }

    public boolean isInfinityFuel(Entity player, boolean checkOtherSeet) {
        if (this.isCreative(player) || this.getCommonStatus(4)) {
            return true;
        }
        if (checkOtherSeet) {
            for (MCH_EntitySeat seat : this.getSeats()) {
                if (seat == null || !this.isCreative(seat.field_70153_n)) continue;
                return true;
            }
        }
        return false;
    }

    public void setCommand(String s, EntityPlayer player) {
        if (!this.field_70170_p.field_72995_K && MCH_Command.canUseCommand((Entity)player)) {
            this.setCommandForce(s);
        }
    }

    public void setCommandForce(String s) {
        if (!this.field_70170_p.field_72995_K) {
            this.func_70096_w().func_75692_b(27, (Object)s);
        }
    }

    public String getCommand() {
        return this.func_70096_w().func_75681_e(27);
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
            this.func_70096_w().func_75692_b(20, (Object)String.valueOf(s));
            this.changeType(s);
            this.initRotationYaw(this.getRotYaw());
        }
    }

    public String getTypeName() {
        return this.func_70096_w().func_75681_e(20);
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
        if (!this.field_70170_p.field_72995_K) {
            if (uavSt != null) {
                this.func_70096_w().func_75692_b(22, (Object)W_Entity.getEntityId((Entity)uavSt));
            } else {
                this.func_70096_w().func_75692_b(22, (Object)0);
            }
        }
    }

    public float getStealth() {
        return this.getAcInfo() != null ? this.getAcInfo().stealth : 0.0f;
    }

    public MCH_AircraftInventory getGuiInventory() {
        return this.inventory;
    }

    public void openGui(EntityPlayer player) {
        if (!this.field_70170_p.field_72995_K) {
            player.openGui((Object)MCH_MOD.instance, 1, this.field_70170_p, (int)this.field_70165_t, (int)this.field_70163_u, (int)this.field_70161_v);
        }
    }

    public MCH_EntityUavStation getUavStation() {
        return this.isUAV() ? this.uavStation : null;
    }

    public static MCH_EntityAircraft getAircraft_RiddenOrControl(Entity rider) {
        if (rider != null) {
            if (rider.field_70154_o instanceof MCH_EntityAircraft) {
                return (MCH_EntityAircraft)rider.field_70154_o;
            }
            if (rider.field_70154_o instanceof MCH_EntitySeat) {
                return ((MCH_EntitySeat)rider.field_70154_o).getParent();
            }
            if (rider.field_70154_o instanceof MCH_EntityUavStation) {
                MCH_EntityUavStation uavStation = (MCH_EntityUavStation)rider.field_70154_o;
                return uavStation.getControlAircract();
            }
        }
        return null;
    }

    public boolean isCreative(Entity entity) {
        return entity instanceof EntityPlayer && ((EntityPlayer)entity).field_71075_bZ.field_75098_d;
    }

    public Entity getRiddenByEntity() {
        if (this.isUAV() && this.uavStation != null) {
            return this.uavStation.field_70153_n;
        }
        return this.field_70153_n;
    }

    public boolean getCommonStatus(int bit) {
        return (this.commonStatus >> bit & 1) != 0;
    }

    public void setCommonStatus(int bit, boolean b) {
        this.setCommonStatus(bit, b, false);
    }

    public void setCommonStatus(int bit, boolean b, boolean writeClient) {
        if (!this.field_70170_p.field_72995_K || writeClient) {
            int bofore = this.commonStatus;
            int mask = 1 << bit;
            this.commonStatus = b ? (this.commonStatus |= mask) : (this.commonStatus &= ~mask);
            if (bofore != this.commonStatus) {
                this.func_70096_w().func_75692_b(23, (Object)this.commonStatus);
            }
        }
    }

    public double getThrottle() {
        return 0.05 * (double)this.func_70096_w().func_75679_c(29);
    }

    public void setThrottle(double t) {
        int n = (int)(t * 20.0);
        if (n == 0 && t > 0.0) {
            n = 1;
        }
        this.func_70096_w().func_75692_b(29, (Object)n);
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
        this.func_70096_w().func_75692_b(19, (Object)par1);
    }

    public int getDamageTaken() {
        return this.func_70096_w().func_75679_c(19);
    }

    public void destroyAircraft() {
        this.setSearchLight(false);
        this.switchHoveringMode(false);
        this.switchGunnerMode(false);
        for (int i = 0; i < this.getSeatNum() + 1; ++i) {
            Entity e = this.getEntityBySeatId(i);
            if (!(e instanceof EntityPlayer)) continue;
            this.switchCameraMode((EntityPlayer)e, 0);
        }
        if (this.isTargetDrone()) {
            this.setDespawnCount(50);
        } else {
            this.setDespawnCount(500);
        }
        this.rotDestroyedPitch = this.field_70146_Z.nextFloat() - 0.5f;
        this.rotDestroyedRoll = (this.field_70146_Z.nextFloat() - 0.5f) * 0.5f;
        this.rotDestroyedYaw = 0.0f;
        if (this.isUAV() && this.getRiddenByEntity() != null) {
            this.getRiddenByEntity().func_70078_a(null);
        }
        if (!this.field_70170_p.field_72995_K) {
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

    @SideOnly(value=Side.CLIENT)
    public int func_70070_b(float par1) {
        int j;
        if (this.haveSearchLight() && this.isSearchLightON()) {
            return 0xF000F0;
        }
        int i = MathHelper.func_76128_c((double)this.field_70165_t);
        if (this.field_70170_p.func_72899_e(i, 0, j = MathHelper.func_76128_c((double)this.field_70161_v))) {
            float fo;
            double d0 = (this.field_70121_D.field_72337_e - this.field_70121_D.field_72338_b) * 0.66;
            float f = fo = this.getAcInfo() != null ? this.getAcInfo().submergedDamageHeight : 0.0f;
            if (this.canFloatWater()) {
                fo = this.getAcInfo().floatOffset;
                if (fo < 0.0f) {
                    fo = -fo;
                }
                fo += 1.0f;
            }
            int k = MathHelper.func_76128_c((double)(this.field_70163_u + (double)fo - (double)this.field_70129_M + d0));
            int val = this.field_70170_p.func_72802_i(i, k, j, 0);
            int low = val & 0xFFFF;
            int high = val >> 16 & 0xFFFF;
            if (high < this.brightnessHigh) {
                if (this.brightnessHigh > 0 && this.getCountOnUpdate() % 2 == 0) {
                    --this.brightnessHigh;
                }
            } else if (high > this.brightnessHigh) {
                this.brightnessHigh += 4;
                if (this.brightnessHigh > 240) {
                    this.brightnessHigh = 240;
                }
            }
            return this.brightnessHigh << 16 | low;
        }
        return 0;
    }

    public MCH_AircraftInfo.CameraPosition getCameraPosInfo() {
        if (this.getAcInfo() == null) {
            return null;
        }
        Entity player = MCH_Lib.getClientPlayer();
        int sid = this.getSeatIdByEntity(player);
        if (sid == 0 && this.canSwitchCameraPos() && this.getCameraId() > 0 && this.getCameraId() < this.getAcInfo().cameraPosition.size()) {
            return (MCH_AircraftInfo.CameraPosition)this.getAcInfo().cameraPosition.get(this.getCameraId());
        }
        if (sid > 0 && sid < this.getSeatsInfo().length && this.getSeatsInfo()[sid].invCamPos) {
            return this.getSeatsInfo()[sid].getCamPos();
        }
        return (MCH_AircraftInfo.CameraPosition)this.getAcInfo().cameraPosition.get(0);
    }

    public int getCameraId() {
        return this.cameraId;
    }

    public void setCameraId(int cameraId) {
        MCH_Lib.DbgLog((boolean)true, (String)"MCH_EntityAircraft.setCameraId %d -> %d", (Object[])new Object[]{this.cameraId, cameraId});
        this.cameraId = cameraId;
    }

    public boolean canSwitchCameraPos() {
        return this.getCameraPosNum() >= 2;
    }

    public int getCameraPosNum() {
        if (this.getAcInfo() != null) {
            return this.getAcInfo().cameraPosition.size();
        }
        return 1;
    }

    public void onAcInfoReloaded() {
        if (this.getAcInfo() == null) {
            return;
        }
        this.func_70105_a(this.getAcInfo().bodyWidth, this.getAcInfo().bodyHeight);
    }

    public void writeSpawnData(ByteBuf buffer) {
        if (this.getAcInfo() != null) {
            buffer.writeFloat(this.getAcInfo().bodyHeight);
            buffer.writeFloat(2.0f);
        } else {
            buffer.writeFloat(this.field_70131_O);
            buffer.writeFloat(this.field_70130_N);
        }
    }

    public void readSpawnData(ByteBuf additionalData) {
        try {
            float height = additionalData.readFloat();
            float width = additionalData.readFloat();
            this.func_70105_a(width, height);
        }
        catch (Exception e) {
            MCH_Lib.Log((Entity)this, (String)"readSpawnData error!", (Object[])new Object[0]);
            e.printStackTrace();
        }
    }

    protected void func_70037_a(NBTTagCompound nbt) {
        this.setDespawnCount(nbt.func_74762_e("AcDespawnCount"));
        this.setTextureName(nbt.func_74779_i("TextureName"));
        this.setCommonUniqueId(nbt.func_74779_i("AircraftUniqueId"));
        this.setRotRoll(nbt.func_74760_g("AcRoll"));
        this.prevRotationRoll = this.getRotRoll();
        this.prevLastRiderYaw = this.lastRiderYaw = nbt.func_74760_g("AcLastRYaw");
        this.prevLastRiderPitch = this.lastRiderPitch = nbt.func_74760_g("AcLastRPitch");
        this.setPartStatus(nbt.func_74762_e("PartStatus"));
        this.setTypeName(nbt.func_74779_i("TypeName"));
        super.func_70037_a(nbt);
        this.getGuiInventory().readEntityFromNBT(nbt);
        this.setCommandForce(nbt.func_74779_i("AcCommand"));
        this.setFuel(nbt.func_74762_e("AcFuel"));
        int[] wa_list = nbt.func_74759_k("AcWeaponsAmmo");
        for (int i = 0; i < wa_list.length; ++i) {
            this.getWeapon(i).setRestAllAmmoNum(wa_list[i]);
            this.getWeapon(i).reloadMag();
        }
        if (this.getDespawnCount() > 0) {
            this.setDamageTaken(this.getMaxHP());
        } else if (nbt.func_74764_b("AcDamage")) {
            this.setDamageTaken(nbt.func_74762_e("AcDamage"));
        }
        if (this.haveSearchLight() && nbt.func_74764_b("SearchLight")) {
            this.setSearchLight(nbt.func_74767_n("SearchLight"));
        }
        this.dismountedUserCtrl = nbt.func_74767_n("AcDismounted");
    }

    protected void func_70014_b(NBTTagCompound nbt) {
        nbt.func_74778_a("TextureName", this.getTextureName());
        nbt.func_74778_a("AircraftUniqueId", this.getCommonUniqueId());
        nbt.func_74778_a("TypeName", this.getTypeName());
        nbt.func_74768_a("PartStatus", this.getPartStatus() & this.getLastPartStatusMask());
        nbt.func_74768_a("AcFuel", this.getFuel());
        nbt.func_74768_a("AcDespawnCount", this.getDespawnCount());
        nbt.func_74776_a("AcRoll", this.getRotRoll());
        nbt.func_74757_a("SearchLight", this.isSearchLightON());
        nbt.func_74776_a("AcLastRYaw", this.getLastRiderYaw());
        nbt.func_74776_a("AcLastRPitch", this.getLastRiderPitch());
        nbt.func_74778_a("AcCommand", this.getCommand());
        super.func_70014_b(nbt);
        this.getGuiInventory().writeEntityToNBT(nbt);
        int[] wa_list = new int[this.getWeaponNum()];
        for (int i = 0; i < wa_list.length; ++i) {
            wa_list[i] = this.getWeapon(i).getRestAllAmmoNum() + this.getWeapon(i).getAmmoNum();
        }
        nbt.func_74782_a("AcWeaponsAmmo", (NBTBase)W_NBTTag.newTagIntArray((String)"AcWeaponsAmmo", (int[])wa_list));
        nbt.func_74768_a("AcDamage", this.getDamageTaken());
        nbt.func_74757_a("AcDismounted", this.dismountedUserCtrl);
    }

    public boolean func_70097_a(DamageSource damageSource, float org_damage) {
        float damage = org_damage;
        float damageFactor = this.lastBBDamageFactor;
        this.lastBBDamageFactor = 1.0f;
        if (this.func_85032_ar()) {
            return false;
        }
        if (this.field_70128_L) {
            return false;
        }
        if (this.timeSinceHit > 0) {
            return false;
        }
        String dmt = damageSource.func_76355_l();
        if (dmt.equalsIgnoreCase("inFire")) {
            return false;
        }
        if (dmt.equalsIgnoreCase("cactus")) {
            return false;
        }
        if (this.field_70170_p.field_72995_K) {
            return true;
        }
        damage = MCH_Config.applyDamageByExternal((Entity)this, (DamageSource)damageSource, (float)damage);
        if (!MCH_Multiplay.canAttackEntity((DamageSource)damageSource, (Entity)this)) {
            return false;
        }
        if (dmt.equalsIgnoreCase("lava")) {
            damage *= (float)(this.field_70146_Z.nextInt(8) + 2);
            this.timeSinceHit = 2;
        }
        if (dmt.startsWith("explosion")) {
            this.timeSinceHit = 1;
        } else if (this.isMountedEntity(damageSource.func_76346_g())) {
            return false;
        }
        if (dmt.equalsIgnoreCase("onFire")) {
            this.timeSinceHit = 10;
        }
        boolean isCreative = false;
        boolean isSneaking = false;
        Entity entity = damageSource.func_76346_g();
        boolean isDamegeSourcePlayer = false;
        boolean playDamageSound = false;
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer)entity;
            isCreative = player.field_71075_bZ.field_75098_d;
            isSneaking = player.func_70093_af();
            if (dmt.equalsIgnoreCase("player")) {
                if (isCreative) {
                    isDamegeSourcePlayer = true;
                } else if (!MCH_Config.PreventingBroken.prmBool) {
                    if (MCH_Config.BreakableOnlyPickaxe.prmBool) {
                        if (player.func_71045_bC() != null && player.func_71045_bC().func_77973_b() instanceof ItemPickaxe) {
                            isDamegeSourcePlayer = true;
                        }
                    } else {
                        isDamegeSourcePlayer = !this.isRidePlayer();
                    }
                }
            }
            W_WorldFunc.MOD_playSoundAtEntity((Entity)this, (String)"hit", (float)1.0f, (float)1.0f);
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
                    if (damageFactor <= 1.0f) {
                        damage *= damageFactor;
                    }
                    damage *= acInfo.armorDamageFactor;
                    if ((damage -= acInfo.armorMinDamage) <= 0.0f) {
                        MCH_Lib.DbgLog((World)this.field_70170_p, (String)"MCH_EntityAircraft.attackEntityFrom:no damage=%.1f -> %.1f(factor=%.2f):%s", (Object[])new Object[]{Float.valueOf(org_damage), Float.valueOf(damage), Float.valueOf(damageFactor), dmt});
                        return false;
                    }
                    if (damageFactor > 1.0f) {
                        damage *= damageFactor;
                    }
                }
                MCH_Lib.DbgLog((World)this.field_70170_p, (String)"MCH_EntityAircraft.attackEntityFrom:damage=%.1f(factor=%.2f):%s", (Object[])new Object[]{Float.valueOf(damage), Float.valueOf(damageFactor), dmt});
                this.setDamageTaken(this.getDamageTaken() + (int)damage);
            }
            this.func_70018_K();
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
                        MCH_DummyCommandSender.execCommand((String)cmd);
                    }
                    if (dmt.equalsIgnoreCase("inWall")) {
                        this.explosionByCrash(0.0);
                        this.damageSinceDestroyed = this.getMaxHP();
                    } else {
                        MCH_Explosion.newExplosion((World)this.field_70170_p, null, (Entity)entity, (double)this.field_70165_t, (double)this.field_70163_u, (double)this.field_70161_v, (float)2.0f, (float)2.0f, (boolean)true, (boolean)true, (boolean)true, (boolean)true, (int)5);
                    }
                } else {
                    if (this.getAcInfo() != null && this.getAcInfo().getItem() != null) {
                        if (isCreative) {
                            if (MCH_Config.DropItemInCreativeMode.prmBool && !isSneaking) {
                                this.dropItemWithOffset(this.getAcInfo().getItem(), 1, 0.0f);
                            }
                            if (!MCH_Config.DropItemInCreativeMode.prmBool && isSneaking) {
                                this.dropItemWithOffset(this.getAcInfo().getItem(), 1, 0.0f);
                            }
                        } else {
                            this.dropItemWithOffset(this.getAcInfo().getItem(), 1, 0.0f);
                        }
                    }
                    this.setDead(true);
                }
            }
        } else if (isDamegeSourcePlayer && isCreative) {
            this.setDead(true);
        }
        if (playDamageSound) {
            W_WorldFunc.MOD_playSoundAtEntity((Entity)this, (String)"helidmg", (float)1.0f, (float)(0.9f + this.field_70146_Z.nextFloat() * 0.1f));
        }
        return true;
    }

    public boolean isExploded() {
        return this.isDestroyed() && this.damageSinceDestroyed > this.getMaxHP() / 10 + 1;
    }

    public void destruct() {
        if (this.getRiddenByEntity() != null) {
            this.getRiddenByEntity().func_70078_a(null);
        }
        this.setDead(true);
    }

    public EntityItem func_70099_a(ItemStack is, float par2) {
        if (is.field_77994_a == 0) {
            return null;
        }
        this.setAcDataToItem(is);
        return super.func_70099_a(is, par2);
    }

    public void setAcDataToItem(ItemStack is) {
        if (!is.func_77942_o()) {
            is.func_77982_d(new NBTTagCompound());
        }
        NBTTagCompound nbt = is.func_77978_p();
        nbt.func_74778_a("MCH_Command", this.getCommand());
        if (MCH_Config.ItemFuel.prmBool) {
            nbt.func_74768_a("MCH_Fuel", this.getFuel());
        }
        if (MCH_Config.ItemDamage.prmBool) {
            is.func_77964_b(this.getDamageTaken());
        }
    }

    public void getAcDataFromItem(ItemStack is) {
        if (!is.func_77942_o()) {
            return;
        }
        NBTTagCompound nbt = is.func_77978_p();
        this.setCommandForce(nbt.func_74779_i("MCH_Command"));
        if (MCH_Config.ItemFuel.prmBool) {
            this.setFuel(nbt.func_74762_e("MCH_Fuel"));
        }
        if (MCH_Config.ItemDamage.prmBool) {
            this.setDamageTaken(is.func_77960_j());
        }
    }

    public boolean func_70300_a(EntityPlayer player) {
        if (this.isUAV()) {
            return super.func_70300_a(player);
        }
        if (!this.field_70128_L) {
            if (this.getSeatIdByEntity((Entity)player) >= 0) {
                return player.func_70068_e((Entity)this) <= 4096.0;
            }
            return player.func_70068_e((Entity)this) <= 64.0;
        }
        return false;
    }

    public void func_70108_f(Entity par1Entity) {
    }

    public void func_70024_g(double par1, double par3, double par5) {
    }

    public void func_70016_h(double par1, double par3, double par5) {
        this.velocityX = this.field_70159_w = par1;
        this.velocityY = this.field_70181_x = par3;
        this.velocityZ = this.field_70179_y = par5;
    }

    public void onFirstUpdate() {
        if (!this.field_70170_p.field_72995_K) {
            this.setCommonStatus(3, MCH_Config.InfinityAmmo.prmBool);
            this.setCommonStatus(4, MCH_Config.InfinityFuel.prmBool);
        }
    }

    public void onRidePilotFirstUpdate() {
        Entity pilot;
        if (this.field_70170_p.field_72995_K && W_Lib.isClientPlayer((Entity)this.getRiddenByEntity())) {
            this.updateClientSettings(0);
        }
        if ((pilot = this.getRiddenByEntity()) != null) {
            pilot.field_70177_z = this.getLastRiderYaw();
            pilot.field_70125_A = this.getLastRiderPitch();
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
        return !this.field_70128_L && this.getRiddenByEntity() != null && !this.isDestroyed();
    }

    public boolean canUpdateYaw(Entity player) {
        if (this.getRidingEntity() != null) {
            return false;
        }
        if (this.getCountOnUpdate() < 30) {
            return false;
        }
        return MCH_Lib.getBlockIdY((Entity)this, (int)3, (int)-2) == 0;
    }

    public boolean canUpdatePitch(Entity player) {
        if (this.getCountOnUpdate() < 30) {
            return false;
        }
        return MCH_Lib.getBlockIdY((Entity)this, (int)3, (int)-2) == 0;
    }

    public boolean canUpdateRoll(Entity player) {
        if (this.getRidingEntity() != null) {
            return false;
        }
        if (this.getCountOnUpdate() < 30) {
            return false;
        }
        return MCH_Lib.getBlockIdY((Entity)this, (int)3, (int)-2) == 0;
    }

    public boolean isOverridePlayerYaw() {
        return !this.isFreeLookMode();
    }

    public boolean isOverridePlayerPitch() {
        return !this.isFreeLookMode();
    }

    public double getAddRotationYawLimit() {
        return this.getAcInfo() != null ? 40.0 * (double)this.getAcInfo().mobilityYaw : 40.0;
    }

    public double getAddRotationPitchLimit() {
        return this.getAcInfo() != null ? 40.0 * (double)this.getAcInfo().mobilityPitch : 40.0;
    }

    public double getAddRotationRollLimit() {
        return this.getAcInfo() != null ? 40.0 * (double)this.getAcInfo().mobilityRoll : 40.0;
    }

    public float getYawFactor() {
        return 1.0f;
    }

    public float getPitchFactor() {
        return 1.0f;
    }

    public float getRollFactor() {
        return 1.0f;
    }

    public abstract void onUpdateAngles(float var1);

    public float getControlRotYaw(float mouseX, float mouseY, float tick) {
        return 0.0f;
    }

    public float getControlRotPitch(float mouseX, float mouseY, float tick) {
        return 0.0f;
    }

    public float getControlRotRoll(float mouseX, float mouseY, float tick) {
        return 0.0f;
    }

    public void setAngles(Entity player, boolean fixRot, float fixYaw, float fixPitch, float deltaX, float deltaY, float x, float y, float partialTicks) {
        double limit;
        if (partialTicks < 0.03f) {
            partialTicks = 0.4f;
        }
        if (partialTicks > 0.9f) {
            partialTicks = 0.6f;
        }
        this.lowPassPartialTicks.put(partialTicks);
        partialTicks = this.lowPassPartialTicks.getAvg();
        float ac_pitch = this.getRotPitch();
        float ac_yaw = this.getRotYaw();
        float ac_roll = this.getRotRoll();
        if (this.isFreeLookMode()) {
            y = 0.0f;
            x = 0.0f;
        }
        float yaw = 0.0f;
        float pitch = 0.0f;
        float roll = 0.0f;
        if (this.canUpdateYaw(player)) {
            limit = this.getAddRotationYawLimit();
            yaw = this.getControlRotYaw(x, y, partialTicks);
            if ((double)yaw < -limit) {
                yaw = (float)(-limit);
            }
            if ((double)yaw > limit) {
                yaw = (float)limit;
            }
            yaw = (float)((double)(yaw * this.getYawFactor()) * 0.06 * (double)partialTicks);
        }
        if (this.canUpdatePitch(player)) {
            limit = this.getAddRotationPitchLimit();
            pitch = this.getControlRotPitch(x, y, partialTicks);
            if ((double)pitch < -limit) {
                pitch = (float)(-limit);
            }
            if ((double)pitch > limit) {
                pitch = (float)limit;
            }
            pitch = (float)((double)(-pitch * this.getPitchFactor()) * 0.06 * (double)partialTicks);
        }
        if (this.canUpdateRoll(player)) {
            limit = this.getAddRotationRollLimit();
            roll = this.getControlRotRoll(x, y, partialTicks);
            if ((double)roll < -limit) {
                roll = (float)(-limit);
            }
            if ((double)roll > limit) {
                roll = (float)limit;
            }
            roll = roll * this.getRollFactor() * 0.06f * partialTicks;
        }
        MCH_Math.FMatrix m_add = MCH_Math.newMatrix();
        MCH_Math.MatTurnZ((MCH_Math.FMatrix)m_add, (float)(roll / 180.0f * (float)Math.PI));
        MCH_Math.MatTurnX((MCH_Math.FMatrix)m_add, (float)(pitch / 180.0f * (float)Math.PI));
        MCH_Math.MatTurnY((MCH_Math.FMatrix)m_add, (float)(yaw / 180.0f * (float)Math.PI));
        MCH_Math.MatTurnZ((MCH_Math.FMatrix)m_add, (float)((float)((double)(this.getRotRoll() / 180.0f) * Math.PI)));
        MCH_Math.MatTurnX((MCH_Math.FMatrix)m_add, (float)((float)((double)(this.getRotPitch() / 180.0f) * Math.PI)));
        MCH_Math.MatTurnY((MCH_Math.FMatrix)m_add, (float)((float)((double)(this.getRotYaw() / 180.0f) * Math.PI)));
        MCH_Math.FVector3D v = MCH_Math.MatrixToEuler((MCH_Math.FMatrix)m_add);
        if (this.getAcInfo().limitRotation) {
            v.x = MCH_Lib.RNG((float)v.x, (float)this.getAcInfo().minRotationPitch, (float)this.getAcInfo().maxRotationPitch);
            v.z = MCH_Lib.RNG((float)v.z, (float)this.getAcInfo().minRotationRoll, (float)this.getAcInfo().maxRotationRoll);
        }
        if (v.z > 180.0f) {
            v.z -= 360.0f;
        }
        if (v.z < -180.0f) {
            v.z += 360.0f;
        }
        this.setRotYaw(v.y);
        this.setRotPitch(v.x);
        this.setRotRoll(v.z);
        this.onUpdateAngles(partialTicks);
        if (this.getAcInfo().limitRotation) {
            v.x = MCH_Lib.RNG((float)this.getRotPitch(), (float)this.getAcInfo().minRotationPitch, (float)this.getAcInfo().maxRotationPitch);
            v.z = MCH_Lib.RNG((float)this.getRotRoll(), (float)this.getAcInfo().minRotationRoll, (float)this.getAcInfo().maxRotationRoll);
            this.setRotPitch(v.x);
            this.setRotRoll(v.z);
        }
        float RV = 180.0f;
        if (MathHelper.func_76135_e((float)this.getRotPitch()) > 90.0f) {
            MCH_Lib.DbgLog((boolean)true, (String)"MCH_EntityAircraft.setAngles Error:Pitch=%.1f", (Object[])new Object[]{Float.valueOf(this.getRotPitch())});
        }
        if (this.getRotRoll() > 180.0f) {
            this.setRotRoll(this.getRotRoll() - 360.0f);
        }
        if (this.getRotRoll() < -180.0f) {
            this.setRotRoll(this.getRotRoll() + 360.0f);
        }
        this.prevRotationRoll = this.getRotRoll();
        this.field_70127_C = this.getRotPitch();
        if (this.getRidingEntity() == null) {
            this.field_70126_B = this.getRotYaw();
        }
        if (this.isOverridePlayerYaw() || fixRot) {
            if (this.getRidingEntity() == null) {
                player.field_70126_B = this.getRotYaw() + (fixRot ? fixYaw : 0.0f);
            } else {
                if (this.getRotYaw() - player.field_70177_z > 180.0f) {
                    player.field_70126_B += 360.0f;
                }
                if (this.getRotYaw() - player.field_70177_z < -180.0f) {
                    player.field_70126_B -= 360.0f;
                }
            }
            player.field_70177_z = this.getRotYaw() + (fixRot ? fixYaw : 0.0f);
        } else {
            player.func_70082_c(deltaX, 0.0f);
        }
        if (this.isOverridePlayerPitch() || fixRot) {
            player.field_70127_C = this.getRotPitch() + (fixRot ? fixPitch : 0.0f);
            player.field_70125_A = this.getRotPitch() + (fixRot ? fixPitch : 0.0f);
        } else {
            player.func_70082_c(0.0f, deltaY);
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
                float angle;
                Vec3 pos = this.getTransformedPosition(sl.pos);
                double dist = entity.func_70092_e(pos.field_72450_a, pos.field_72448_b, pos.field_72449_c);
                if (!(dist > 2.0) || !(dist < (double)(sl.height * sl.height + 20.0f))) continue;
                double cx = entity.field_70165_t - pos.field_72450_a;
                double cy = entity.field_70163_u - pos.field_72448_b;
                double cz = entity.field_70161_v - pos.field_72449_c;
                double h = 0.0;
                double v = 0.0;
                if (!sl.fixDir) {
                    Vec3 vv = MCH_Lib.RotVec3((double)0.0, (double)0.0, (double)1.0, (float)(-this.lastSearchLightYaw + sl.yaw), (float)(-this.lastSearchLightPitch + sl.pitch), (float)(-this.getRotRoll()));
                    h = MCH_Lib.getPosAngle((double)vv.field_72450_a, (double)vv.field_72449_c, (double)cx, (double)cz);
                    v = Math.atan2(cy, Math.sqrt(cx * cx + cz * cz)) * 180.0 / Math.PI;
                    v = Math.abs(v + (double)this.lastSearchLightPitch + (double)sl.pitch);
                } else {
                    float stRot = 0.0f;
                    if (sl.steering) {
                        stRot = this.rotYawWheel * sl.stRot;
                    }
                    Vec3 vv = MCH_Lib.RotVec3((double)0.0, (double)0.0, (double)1.0, (float)(-this.getRotYaw() + sl.yaw + stRot), (float)(-this.getRotPitch() + sl.pitch), (float)(-this.getRotRoll()));
                    h = MCH_Lib.getPosAngle((double)vv.field_72450_a, (double)vv.field_72449_c, (double)cx, (double)cz);
                    v = Math.atan2(cy, Math.sqrt(cx * cx + cz * cz)) * 180.0 / Math.PI;
                    v = Math.abs(v + (double)this.getRotPitch() + (double)sl.pitch);
                }
                if (!(h < (double)(angle = sl.angle * 3.0f)) || !(v < (double)angle)) continue;
                float value = 0.0f;
                if (h + v < (double)angle) {
                    value = (float)(1440.0 * (1.0 - (h + v) / (double)angle));
                }
                return value <= 240.0f ? value : 240.0f;
            }
        }
        return 0.0f;
    }

    public abstract void onUpdateAircraft();

    public void func_70071_h_() {
        Entity e;
        if (this.getCountOnUpdate() < 2) {
            this.prevPosition.clear((Object)Vec3.func_72443_a((double)this.field_70165_t, (double)this.field_70163_u, (double)this.field_70161_v));
        }
        this.prevCurrentThrottle = this.getCurrentThrottle();
        this.lastBBDamageFactor = 1.0f;
        this.updateControl();
        this.checkServerNoMove();
        this.onUpdate_RidingEntity();
        Iterator itr = this.listUnmountReserve.iterator();
        while (itr.hasNext()) {
            UnmountReserve ur = (UnmountReserve)itr.next();
            if (ur.entity != null && !ur.entity.field_70128_L) {
                ur.entity.func_70107_b(ur.posX, ur.posY, ur.posZ);
                ur.entity.field_70143_R = this.field_70143_R;
            }
            if (ur.cnt > 0) {
                --ur.cnt;
            }
            if (ur.cnt != 0) continue;
            itr.remove();
        }
        if (this.isDestroyed() && this.getCountOnUpdate() % 20 == 0) {
            for (int sid = 0; sid < this.getSeatNum() + 1; ++sid) {
                Entity entity = this.getEntityBySeatId(sid);
                if (entity == null || sid == 0 && this.isUAV()) continue;
                if (!(MCH_Config.applyDamageVsEntity((Entity)entity, (DamageSource)DamageSource.field_76372_a, (float)1.0f) > 0.0f)) continue;
                entity.func_70015_d(5);
            }
        }
        if ((this.aircraftRotChanged || this.aircraftRollRev) && this.field_70170_p.field_72995_K && this.getRiddenByEntity() != null) {
            MCH_PacketIndRotation.send((MCH_EntityAircraft)this);
            this.aircraftRotChanged = false;
            this.aircraftRollRev = false;
        }
        if (!this.field_70170_p.field_72995_K && (int)this.prevRotationRoll != (int)this.getRotRoll()) {
            float roll = MathHelper.func_76142_g((float)this.getRotRoll());
            this.func_70096_w().func_75692_b(26, (Object)new Short((short)roll));
        }
        this.prevRotationRoll = this.getRotRoll();
        if (!this.field_70170_p.field_72995_K && this.isTargetDrone() && !this.isDestroyed() && this.getCountOnUpdate() > 20 && !this.canUseFuel()) {
            this.setDamageTaken(this.getMaxHP());
            this.destroyAircraft();
            MCH_Explosion.newExplosion((World)this.field_70170_p, null, null, (double)this.field_70165_t, (double)this.field_70163_u, (double)this.field_70161_v, (float)2.0f, (float)2.0f, (boolean)true, (boolean)true, (boolean)true, (boolean)true, (int)5);
        }
        if (this.field_70170_p.field_72995_K && this.getAcInfo() != null && this.getHP() <= 0 && this.getDespawnCount() <= 0) {
            this.destroyAircraft();
        }
        if (!this.field_70170_p.field_72995_K && this.getDespawnCount() > 0) {
            this.setDespawnCount(this.getDespawnCount() - 1);
            if (this.getDespawnCount() <= 1) {
                this.setDead(true);
            }
        }
        super.func_70071_h_();
        if (this.func_70021_al() != null) {
            for (Entity e2 : this.func_70021_al()) {
                if (e2 == null) continue;
                e2.func_70071_h_();
            }
        }
        this.updateNoCollisionEntities();
        this.updateUAV();
        this.supplyFuel();
        this.supplyAmmoToOtherAircraft();
        this.updateFuel();
        this.repairOtherAircraft();
        if (this.modeSwitchCooldown > 0) {
            --this.modeSwitchCooldown;
        }
        if (this.lastRiddenByEntity == null && this.getRiddenByEntity() != null) {
            this.onRidePilotFirstUpdate();
        }
        if (this.countOnUpdate == 0) {
            this.onFirstUpdate();
        }
        ++this.countOnUpdate;
        if (this.countOnUpdate >= 1000000) {
            this.countOnUpdate = 1;
        }
        if (this.field_70170_p.field_72995_K) {
            this.commonStatus = this.func_70096_w().func_75679_c(23);
        }
        this.field_70143_R = 0.0f;
        if (this.field_70153_n != null) {
            this.field_70153_n.field_70143_R = 0.0f;
        }
        if (this.missileDetector != null) {
            this.missileDetector.update();
        }
        if (this.soundUpdater != null) {
            this.soundUpdater.update();
        }
        if (this.getTowChainEntity() != null && this.getTowChainEntity().field_70128_L) {
            this.setTowChainEntity(null);
        }
        this.updateSupplyAmmo();
        this.autoRepair();
        int ft = this.getFlareTick();
        this.flareDv.update();
        if (!this.field_70170_p.field_72995_K && this.getFlareTick() == 0 && ft != 0) {
            this.setCommonStatus(0, false);
        }
        if ((e = this.getRiddenByEntity()) != null && !e.field_70128_L && !this.isDestroyed()) {
            this.lastRiderYaw = e.field_70177_z;
            this.prevLastRiderYaw = e.field_70126_B;
            this.lastRiderPitch = e.field_70125_A;
            this.prevLastRiderPitch = e.field_70127_C;
        } else if (this.getTowedChainEntity() != null || this.field_70154_o != null) {
            this.lastRiderYaw = this.field_70177_z;
            this.prevLastRiderYaw = this.field_70126_B;
            this.lastRiderPitch = this.field_70125_A;
            this.prevLastRiderPitch = this.field_70127_C;
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
        boolean prevOnGround = this.field_70122_E;
        double prevMotionY = this.field_70181_x;
        this.onUpdateAircraft();
        if (this.getAcInfo() != null) {
            this.updateParts(this.getPartStatus());
        }
        if (this.recoilCount > 0) {
            --this.recoilCount;
        }
        if (!W_Entity.isEqual((Entity)MCH_MOD.proxy.getClientPlayer(), (Entity)this.getRiddenByEntity())) {
            this.updateRecoil(1.0f);
        }
        if (!this.field_70170_p.field_72995_K && this.isDestroyed() && !this.isExploded() && !prevOnGround && this.field_70122_E && prevMotionY < -0.2) {
            this.explosionByCrash(prevMotionY);
            this.damageSinceDestroyed = this.getMaxHP();
        }
        this.onUpdate_PartRotation();
        this.onUpdate_ParticleSmoke();
        this.updateSeatsPosition(this.field_70165_t, this.field_70163_u, this.field_70161_v, false);
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
        this.prevPosition.put((Object)Vec3.func_72443_a((double)this.field_70165_t, (double)this.field_70163_u, (double)this.field_70161_v));
    }

    private void updateNoCollisionEntities() {
        if (this.field_70170_p.field_72995_K) {
            return;
        }
        if (this.getCountOnUpdate() % 10 != 0) {
            return;
        }
        for (int i = 0; i < 1 + this.getSeatNum(); ++i) {
            Entity e = this.getEntityBySeatId(i);
            if (e == null) continue;
            this.noCollisionEntities.put(e, 8);
        }
        if (this.getTowChainEntity() != null && this.getTowChainEntity().towedEntity != null) {
            this.noCollisionEntities.put(this.getTowChainEntity().towedEntity, 60);
        }
        if (this.getTowedChainEntity() != null && this.getTowedChainEntity().towEntity != null) {
            this.noCollisionEntities.put(this.getTowedChainEntity().towEntity, 60);
        }
        if (this.field_70154_o instanceof MCH_EntitySeat) {
            MCH_EntityAircraft ac = ((MCH_EntitySeat)this.field_70154_o).getParent();
            if (ac != null) {
                this.noCollisionEntities.put(ac, 60);
            }
        } else if (this.field_70154_o != null) {
            this.noCollisionEntities.put(this.field_70154_o, 60);
        }
        for (Entity key : this.noCollisionEntities.keySet()) {
            this.noCollisionEntities.put(key, (Integer)this.noCollisionEntities.get(key) - 1);
        }
        Iterator key = this.noCollisionEntities.values().iterator();
        while (key.hasNext()) {
            if ((Integer)key.next() > 0) continue;
            key.remove();
        }
    }

    public void updateControl() {
        if (!this.field_70170_p.field_72995_K) {
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
            float pitch = MathHelper.func_76134_b((float)((float)((double)(this.recoilYaw - this.getRotRoll()) * Math.PI / 180.0)));
            float roll = MathHelper.func_76126_a((float)((float)((double)(this.recoilYaw - this.getRotRoll()) * Math.PI / 180.0)));
            float recoil = MathHelper.func_76134_b((float)((float)((double)(this.recoilCount * 6) * Math.PI / 180.0))) * this.recoilValue;
            this.setRotPitch(this.getRotPitch() + recoil * pitch * partialTicks);
            this.setRotRoll(this.getRotRoll() + recoil * roll * partialTicks);
        }
    }

    private void updatePartLightHatch() {
        this.prevRotLightHatch = this.rotLightHatch;
        this.rotLightHatch = this.isSearchLightON() ? (float)((double)this.rotLightHatch + 0.5) : (float)((double)this.rotLightHatch - 0.5);
        if (this.rotLightHatch > 1.0f) {
            this.rotLightHatch = 1.0f;
        }
        if (this.rotLightHatch < 0.0f) {
            this.rotLightHatch = 0.0f;
        }
    }

    public void updateExtraBoundingBox() {
        for (MCH_BoundingBox bb : this.extraBoundingBox) {
            bb.updatePosition(this.field_70165_t, this.field_70163_u, this.field_70161_v, this.getRotYaw(), this.getRotPitch(), this.getRotRoll());
        }
    }

    public void updatePartWheel() {
        if (!this.field_70170_p.field_72995_K) {
            return;
        }
        if (this.getAcInfo() == null) {
            return;
        }
        this.prevRotWheel = this.rotWheel;
        this.prevRotYawWheel = this.rotYawWheel;
        float LEN = 1.0f;
        float MIN = 0.0f;
        double throttle = this.getCurrentThrottle();
        double pivotTurnThrottle = this.getAcInfo().pivotTurnThrottle;
        pivotTurnThrottle = pivotTurnThrottle <= 0.0 ? 1.0 : (pivotTurnThrottle *= (double)0.1f);
        boolean localMoveLeft = this.moveLeft;
        boolean localMoveRight = this.moveRight;
        if (this.getAcInfo().enableBack && (double)this.throttleBack > 0.01 && throttle <= 0.0) {
            throttle = -this.throttleBack * 15.0f;
        }
        if (localMoveLeft && !localMoveRight) {
            this.rotYawWheel += 0.1f;
            if (this.rotYawWheel > 1.0f) {
                this.rotYawWheel = 1.0f;
            }
        } else if (!localMoveLeft && localMoveRight) {
            this.rotYawWheel -= 0.1f;
            if (this.rotYawWheel < -1.0f) {
                this.rotYawWheel = -1.0f;
            }
        } else {
            this.rotYawWheel *= 0.9f;
        }
        this.rotWheel = (float)((double)this.rotWheel + throttle * (double)this.getAcInfo().partWheelRot);
        if (this.rotWheel >= 360.0f) {
            this.rotWheel -= 360.0f;
            this.prevRotWheel -= 360.0f;
        } else if (this.rotWheel < 0.0f) {
            this.rotWheel += 360.0f;
            this.prevRotWheel += 360.0f;
        }
    }

    public void updatePartCrawlerTrack() {
        if (!this.field_70170_p.field_72995_K) {
            return;
        }
        if (this.getAcInfo() == null) {
            return;
        }
        this.prevRotTrackRoller[0] = this.rotTrackRoller[0];
        this.prevRotTrackRoller[1] = this.rotTrackRoller[1];
        this.prevRotCrawlerTrack[0] = this.rotCrawlerTrack[0];
        this.prevRotCrawlerTrack[1] = this.rotCrawlerTrack[1];
        float LEN = 1.0f;
        float MIN = 0.0f;
        double throttle = this.getCurrentThrottle();
        double pivotTurnThrottle = this.getAcInfo().pivotTurnThrottle;
        pivotTurnThrottle = pivotTurnThrottle <= 0.0 ? 1.0 : (pivotTurnThrottle *= (double)0.1f);
        boolean localMoveLeft = this.moveLeft;
        boolean localMoveRight = this.moveRight;
        int dir = 1;
        if (this.getAcInfo().enableBack && this.throttleBack > 0.0f && throttle <= 0.0) {
            throttle = -this.throttleBack * 5.0f;
            if (localMoveLeft != localMoveRight) {
                boolean tmp = localMoveLeft;
                localMoveLeft = localMoveRight;
                localMoveRight = tmp;
                dir = -1;
            }
        }
        if (localMoveLeft && !localMoveRight) {
            throttle = 0.2 * (double)dir;
            this.throttleCrawlerTrack[0] = (float)((double)this.throttleCrawlerTrack[0] + throttle);
            this.throttleCrawlerTrack[1] = (float)((double)this.throttleCrawlerTrack[1] - pivotTurnThrottle * throttle);
        } else if (!localMoveLeft && localMoveRight) {
            throttle = 0.2 * (double)dir;
            this.throttleCrawlerTrack[0] = (float)((double)this.throttleCrawlerTrack[0] - pivotTurnThrottle * throttle);
            this.throttleCrawlerTrack[1] = (float)((double)this.throttleCrawlerTrack[1] + throttle);
        } else {
            if (throttle > 0.2) {
                throttle = 0.2;
            }
            if (throttle < -0.2) {
                throttle = -0.2;
            }
            this.throttleCrawlerTrack[0] = (float)((double)this.throttleCrawlerTrack[0] + throttle);
            this.throttleCrawlerTrack[1] = (float)((double)this.throttleCrawlerTrack[1] + throttle);
        }
        int i = 0;
        while (i < 2) {
            if (this.throttleCrawlerTrack[i] < -0.72f) {
                this.throttleCrawlerTrack[i] = -0.72f;
            } else if (this.throttleCrawlerTrack[i] > 0.72f) {
                this.throttleCrawlerTrack[i] = 0.72f;
            }
            int n = i;
            this.rotTrackRoller[n] = this.rotTrackRoller[n] + this.throttleCrawlerTrack[i] * this.getAcInfo().trackRollerRot;
            if (this.rotTrackRoller[i] >= 360.0f) {
                int n2 = i;
                this.rotTrackRoller[n2] = this.rotTrackRoller[n2] - 360.0f;
                int n3 = i;
                this.prevRotTrackRoller[n3] = this.prevRotTrackRoller[n3] - 360.0f;
            } else if (this.rotTrackRoller[i] < 0.0f) {
                int n4 = i;
                this.rotTrackRoller[n4] = this.rotTrackRoller[n4] + 360.0f;
                int n5 = i;
                this.prevRotTrackRoller[n5] = this.prevRotTrackRoller[n5] + 360.0f;
            }
            int n6 = i;
            this.rotCrawlerTrack[n6] = this.rotCrawlerTrack[n6] - this.throttleCrawlerTrack[i];
            while (this.rotCrawlerTrack[i] >= 1.0f) {
                int n7 = i;
                this.rotCrawlerTrack[n7] = this.rotCrawlerTrack[n7] - 1.0f;
                int n8 = i;
                this.prevRotCrawlerTrack[n8] = this.prevRotCrawlerTrack[n8] - 1.0f;
            }
            while (this.rotCrawlerTrack[i] < 0.0f) {
                int n9 = i;
                this.rotCrawlerTrack[n9] = this.rotCrawlerTrack[n9] + 1.0f;
            }
            while (this.prevRotCrawlerTrack[i] < 0.0f) {
                int n10 = i;
                this.prevRotCrawlerTrack[n10] = this.prevRotCrawlerTrack[n10] + 1.0f;
            }
            int n11 = i++;
            this.throttleCrawlerTrack[n11] = (float)((double)this.throttleCrawlerTrack[n11] * 0.75);
        }
    }

    public void checkServerNoMove() {
        if (!this.field_70170_p.field_72995_K) {
            double moti = this.field_70159_w * this.field_70159_w + this.field_70181_x * this.field_70181_x + this.field_70179_y * this.field_70179_y;
            if (moti < 1.0E-4) {
                if (this.serverNoMoveCount < 20) {
                    ++this.serverNoMoveCount;
                    if (this.serverNoMoveCount >= 20) {
                        this.serverNoMoveCount = 0;
                        if (this.field_70170_p instanceof WorldServer) {
                            ((WorldServer)this.field_70170_p).func_73039_n().func_151247_a((Entity)this, (Packet)new S12PacketEntityVelocity(this.func_145782_y(), 0.0, 0.0, 0.0));
                        }
                    }
                }
            } else {
                this.serverNoMoveCount = 0;
            }
        }
    }

    public boolean haveRotPart() {
        return this.field_70170_p.field_72995_K && this.getAcInfo() != null && this.rotPartRotation.length > 0 && this.rotPartRotation.length == this.getAcInfo().partRotPart.size();
    }

    public void onUpdate_PartRotation() {
        if (this.haveRotPart()) {
            for (int i = 0; i < this.rotPartRotation.length; ++i) {
                this.prevRotPartRotation[i] = this.rotPartRotation[i];
                if ((this.isDestroyed() || !((MCH_AircraftInfo.RotPart)this.getAcInfo().partRotPart.get((int)i)).rotAlways) && this.getRiddenByEntity() == null) continue;
                int n = i;
                this.rotPartRotation[n] = this.rotPartRotation[n] + ((MCH_AircraftInfo.RotPart)this.getAcInfo().partRotPart.get((int)i)).rotSpeed;
                if (this.rotPartRotation[i] < 0.0f) {
                    int n2 = i;
                    this.rotPartRotation[n2] = this.rotPartRotation[n2] + 360.0f;
                }
                if (!(this.rotPartRotation[i] >= 360.0f)) continue;
                int n3 = i;
                this.rotPartRotation[n3] = this.rotPartRotation[n3] - 360.0f;
            }
        }
    }

    public void onRideEntity(Entity ridingEntity) {
    }

    public int getAlt(double px, double py, double pz) {
        int i;
        for (i = 0; i < 256 && !(py - (double)i <= 0.0) && (py - (double)i >= 256.0 || 0 == W_WorldFunc.getBlockId((World)this.field_70170_p, (int)((int)px), (int)((int)py - i), (int)((int)pz))); ++i) {
        }
        return i;
    }

    public boolean canRepelling(Entity entity) {
        return this.isRepelling() && this.tickRepelling > 50;
    }

    private void onUpdate_Repelling() {
        if (this.getAcInfo() != null && this.getAcInfo().haveRepellingHook()) {
            if (this.isRepelling()) {
                int alt = this.getAlt(this.field_70165_t, this.field_70163_u, this.field_70161_v);
                if (this.ropesLength > -50.0f && this.ropesLength > (float)(-alt)) {
                    this.ropesLength = (float)((double)this.ropesLength - (this.field_70170_p.field_72995_K ? (double)0.3f : 0.25));
                }
            } else {
                this.ropesLength = 0.0f;
            }
        }
        this.onUpdate_UnmountCrewRepelling();
    }

    private void onUpdate_UnmountCrewRepelling() {
        if (this.getAcInfo() == null) {
            return;
        }
        if (!this.isRepelling()) {
            this.tickRepelling = 0;
            return;
        }
        if (this.tickRepelling < 60) {
            ++this.tickRepelling;
            return;
        }
        if (this.field_70170_p.field_72995_K) {
            return;
        }
        block0: for (int ropeIdx = 0; ropeIdx < this.getAcInfo().repellingHooks.size(); ++ropeIdx) {
            MCH_AircraftInfo.RepellingHook hook = (MCH_AircraftInfo.RepellingHook)this.getAcInfo().repellingHooks.get(ropeIdx);
            if (this.getCountOnUpdate() % hook.interval != 0) continue;
            for (int i = 1; i < this.getSeatNum(); ++i) {
                MCH_EntitySeat seat = this.getSeat(i);
                if (seat == null || seat.field_70153_n == null || W_EntityPlayer.isPlayer((Entity)seat.field_70153_n) || this.getSeatInfo(i + 1) instanceof MCH_SeatRackInfo) continue;
                Entity entity = seat.field_70153_n;
                Vec3 dropPos = this.getTransformedPosition(hook.pos, (Vec3)this.prevPosition.oldest());
                seat.field_70165_t = dropPos.field_72450_a;
                seat.field_70163_u = dropPos.field_72448_b - 2.0;
                seat.field_70161_v = dropPos.field_72449_c;
                entity.func_70078_a(null);
                this.unmountEntityRepelling(entity, dropPos, ropeIdx);
                this.lastUsedRopeIndex = ropeIdx;
                continue block0;
            }
        }
    }

    public void unmountEntityRepelling(Entity entity, Vec3 dropPos, int ropeIdx) {
        entity.field_70165_t = dropPos.field_72450_a;
        entity.field_70163_u = dropPos.field_72448_b - 2.0;
        entity.field_70161_v = dropPos.field_72449_c;
        MCH_EntityHide hideEntity = new MCH_EntityHide(this.field_70170_p, entity.field_70165_t, entity.field_70163_u, entity.field_70161_v);
        hideEntity.setParent(this, entity, ropeIdx);
        entity.field_70159_w = 0.0;
        hideEntity.field_70159_w = 0.0;
        entity.field_70181_x = 0.0;
        hideEntity.field_70181_x = 0.0;
        entity.field_70179_y = 0.0;
        hideEntity.field_70179_y = 0.0;
        entity.field_70143_R = 0.0f;
        hideEntity.field_70143_R = 0.0f;
        this.field_70170_p.func_72838_d((Entity)hideEntity);
    }

    private void onUpdate_UnmountCrew() {
        if (this.getAcInfo() == null) {
            return;
        }
        if (this.isParachuting) {
            if (MCH_Lib.getBlockIdY((Entity)this, (int)3, (int)-10) != 0) {
                this.stopUnmountCrew();
            } else if (!(this.haveHatch() && !(this.getHatchRotation() > 89.0f) || this.getCountOnUpdate() % this.getAcInfo().mobDropOption.interval != 0 || this.unmountCrew(true))) {
                this.stopUnmountCrew();
            }
        }
    }

    public void unmountAircraft() {
        Vec3 v = Vec3.func_72443_a((double)this.field_70165_t, (double)this.field_70163_u, (double)this.field_70161_v);
        if (this.field_70154_o instanceof MCH_EntitySeat) {
            MCH_EntityAircraft ac = ((MCH_EntitySeat)this.field_70154_o).getParent();
            MCH_SeatInfo seatInfo = ac.getSeatInfo((Entity)this);
            if (seatInfo instanceof MCH_SeatRackInfo) {
                v = ((MCH_SeatRackInfo)seatInfo).getEntryPos();
                v = ac.getTransformedPosition(v);
            }
        } else if (this.field_70154_o instanceof EntityMinecartEmpty) {
            this.dismountedUserCtrl = true;
        }
        this.func_70012_b(v.field_72450_a, v.field_72448_b, v.field_72449_c, this.getRotYaw(), this.getRotPitch());
        this.func_70078_a(null);
        this.func_70012_b(v.field_72450_a, v.field_72448_b, v.field_72449_c, this.getRotYaw(), this.getRotPitch());
    }

    public boolean canUnmount(Entity entity) {
        if (this.getAcInfo() == null) {
            return false;
        }
        if (!this.getAcInfo().isEnableParachuting) {
            return false;
        }
        if (this.getSeatIdByEntity(entity) <= 1) {
            return false;
        }
        return !this.haveHatch() || !(this.getHatchRotation() < 89.0f);
    }

    public void unmount(Entity entity) {
        if (this.getAcInfo() == null) {
            return;
        }
        if (this.canRepelling(entity) && this.getAcInfo().haveRepellingHook()) {
            MCH_EntitySeat seat = this.getSeatByEntity(entity);
            if (seat != null) {
                this.lastUsedRopeIndex = (this.lastUsedRopeIndex + 1) % this.getAcInfo().repellingHooks.size();
                Vec3 dropPos = this.getTransformedPosition(((MCH_AircraftInfo.RepellingHook)this.getAcInfo().repellingHooks.get((int)this.lastUsedRopeIndex)).pos, (Vec3)this.prevPosition.oldest());
                dropPos = dropPos.func_72441_c(0.0, -2.0, 0.0);
                seat.field_70165_t = dropPos.field_72450_a;
                seat.field_70163_u = dropPos.field_72448_b;
                seat.field_70161_v = dropPos.field_72449_c;
                entity.func_70078_a(null);
                entity.field_70165_t = dropPos.field_72450_a;
                entity.field_70163_u = dropPos.field_72448_b;
                entity.field_70161_v = dropPos.field_72449_c;
                this.unmountEntityRepelling(entity, dropPos, this.lastUsedRopeIndex);
            } else {
                MCH_Lib.Log((Entity)this, (String)("Error:MCH_EntityAircraft.unmount seat=null : " + entity), (Object[])new Object[0]);
            }
        } else if (this.canUnmount(entity)) {
            MCH_EntitySeat seat = this.getSeatByEntity(entity);
            if (seat != null) {
                Vec3 dropPos = this.getTransformedPosition(this.getAcInfo().mobDropOption.pos, (Vec3)this.prevPosition.oldest());
                seat.field_70165_t = dropPos.field_72450_a;
                seat.field_70163_u = dropPos.field_72448_b;
                seat.field_70161_v = dropPos.field_72449_c;
                entity.func_70078_a(null);
                entity.field_70165_t = dropPos.field_72450_a;
                entity.field_70163_u = dropPos.field_72448_b;
                entity.field_70161_v = dropPos.field_72449_c;
                this.dropEntityParachute(entity);
            } else {
                MCH_Lib.Log((Entity)this, (String)("Error:MCH_EntityAircraft.unmount seat=null : " + entity), (Object[])new Object[0]);
            }
        }
    }

    public boolean canParachuting(Entity entity) {
        if (this.getAcInfo() != null && this.getAcInfo().isEnableParachuting && this.getSeatIdByEntity(entity) > 1 && MCH_Lib.getBlockIdY((Entity)this, (int)3, (int)-13) == 0) {
            if (this.haveHatch() && this.getHatchRotation() > 89.0f) {
                return this.getSeatIdByEntity(entity) > 1;
            }
            return this.getSeatIdByEntity(entity) > 1;
        }
        return false;
    }

    public void onUpdate_RidingEntity() {
        if (!this.field_70170_p.field_72995_K && this.waitMountEntity == 0 && this.getCountOnUpdate() > 20 && this.canMountWithNearEmptyMinecart()) {
            this.mountWithNearEmptyMinecart();
        }
        if (this.waitMountEntity > 0) {
            --this.waitMountEntity;
        }
        if (!this.field_70170_p.field_72995_K && this.getRidingEntity() != null) {
            float dist;
            this.setRotRoll(this.getRotRoll() * 0.9f);
            this.setRotPitch(this.getRotPitch() * 0.95f);
            Entity re = this.getRidingEntity();
            float target = MathHelper.func_76142_g((float)(re.field_70177_z + 90.0f));
            if (target - this.field_70177_z > 180.0f) {
                target -= 360.0f;
            }
            if (target - this.field_70177_z < -180.0f) {
                target += 360.0f;
            }
            if (this.field_70173_aa % 2 == 0) {
                // empty if block
            }
            if ((double)(dist = 50.0f * (float)re.func_70092_e(re.field_70169_q, re.field_70167_r, re.field_70166_s)) > 0.001) {
                dist = MathHelper.func_76133_a((double)dist);
                float distYaw = MCH_Lib.RNG((float)(target - this.field_70177_z), (float)(-dist), (float)dist);
                this.field_70177_z += distYaw;
            }
            double bkPosX = this.field_70165_t;
            double bkPosY = this.field_70163_u;
            double bkPosZ = this.field_70161_v;
            if (this.getRidingEntity().field_70128_L) {
                this.func_70078_a(null);
                this.waitMountEntity = 20;
            } else if (this.getCurrentThrottle() > 0.8) {
                this.field_70159_w = this.getRidingEntity().field_70159_w;
                this.field_70181_x = this.getRidingEntity().field_70181_x;
                this.field_70179_y = this.getRidingEntity().field_70179_y;
                this.func_70078_a(null);
                this.waitMountEntity = 20;
            }
            this.field_70165_t = bkPosX;
            this.field_70163_u = bkPosY;
            this.field_70161_v = bkPosZ;
        }
    }

    public void explosionByCrash(double prevMotionY) {
        float exp;
        float f = exp = this.getAcInfo() != null ? (float)this.getAcInfo().maxFuel / 400.0f : 2.0f;
        if (exp < 1.0f) {
            exp = 1.0f;
        }
        if (exp > 15.0f) {
            exp = 15.0f;
        }
        MCH_Lib.DbgLog((World)this.field_70170_p, (String)"OnGroundAfterDestroyed:motionY=%.3f", (Object[])new Object[]{Float.valueOf((float)prevMotionY)});
        MCH_Explosion.newExplosion((World)this.field_70170_p, null, null, (double)this.field_70165_t, (double)this.field_70163_u, (double)this.field_70161_v, (float)exp, (float)(exp >= 2.0f ? exp * 0.5f : 1.0f), (boolean)true, (boolean)true, (boolean)true, (boolean)true, (int)5);
    }

    public void onUpdate_CollisionGroundDamage() {
        if (this.isDestroyed()) {
            return;
        }
        if (MCH_Lib.getBlockIdY((Entity)this, (int)3, (int)-3) > 0 && !this.field_70170_p.field_72995_K) {
            float roll = MathHelper.func_76135_e((float)MathHelper.func_76142_g((float)this.getRotRoll()));
            float pitch = MathHelper.func_76135_e((float)MathHelper.func_76142_g((float)this.getRotPitch()));
            if (roll > this.getGiveDamageRot() || pitch > this.getGiveDamageRot()) {
                float dmg = MathHelper.func_76135_e((float)roll) + MathHelper.func_76135_e((float)pitch);
                dmg = dmg < 90.0f ? (dmg *= 0.4f * (float)this.func_70011_f(this.field_70169_q, this.field_70167_r, this.field_70166_s)) : (dmg *= 0.4f);
                if (dmg > 1.0f && this.field_70146_Z.nextInt(4) == 0) {
                    this.func_70097_a(DamageSource.field_76368_d, dmg);
                }
            }
        }
        if (this.getCountOnUpdate() % 30 == 0 && (this.getAcInfo() == null || !this.getAcInfo().isFloat) && MCH_Lib.isBlockInWater((World)this.field_70170_p, (int)((int)(this.field_70165_t + 0.5)), (int)((int)(this.field_70163_u + 1.5 + (double)this.getAcInfo().submergedDamageHeight)), (int)((int)(this.field_70161_v + 0.5)))) {
            int hp = this.getMaxHP() / 10;
            if (hp <= 0) {
                hp = 1;
            }
            this.attackEntityFrom(DamageSource.field_76368_d, hp);
        }
    }

    public float getGiveDamageRot() {
        return 40.0f;
    }

    public void applyServerPositionAndRotation() {
        double rpinc = this.aircraftPosRotInc;
        double yaw = MathHelper.func_76138_g((double)(this.aircraftYaw - (double)this.getRotYaw()));
        double roll = MathHelper.func_76138_g((double)((double)this.getServerRoll() - (double)this.getRotRoll()));
        if (!(this.isDestroyed() || W_Lib.isClientPlayer((Entity)this.getRiddenByEntity()) && this.getRidingEntity() == null)) {
            this.setRotYaw((float)((double)this.getRotYaw() + yaw / rpinc));
            this.setRotPitch((float)((double)this.getRotPitch() + (this.aircraftPitch - (double)this.getRotPitch()) / rpinc));
            this.setRotRoll((float)((double)this.getRotRoll() + roll / rpinc));
        }
        this.func_70107_b(this.field_70165_t + (this.aircraftX - this.field_70165_t) / rpinc, this.field_70163_u + (this.aircraftY - this.field_70163_u) / rpinc, this.field_70161_v + (this.aircraftZ - this.field_70161_v) / rpinc);
        this.func_70101_b(this.getRotYaw(), this.getRotPitch());
        --this.aircraftPosRotInc;
    }

    protected void autoRepair() {
        if (this.timeSinceHit > 0) {
            --this.timeSinceHit;
        }
        if (this.getMaxHP() <= 0) {
            return;
        }
        if (!this.isDestroyed()) {
            if (this.getDamageTaken() > this.beforeDamageTaken) {
                this.repairCount = 600;
            } else if (this.repairCount > 0) {
                --this.repairCount;
            } else {
                this.repairCount = 40;
                double hpp = (double)this.getHP() / (double)this.getMaxHP();
                if (hpp >= MCH_Config.AutoRepairHP.prmDouble) {
                    this.repair(this.getMaxHP() / 100);
                }
            }
        }
        this.beforeDamageTaken = this.getDamageTaken();
    }

    public boolean repair(int tpd) {
        int damage;
        if (tpd < 1) {
            tpd = 1;
        }
        if ((damage = this.getDamageTaken()) > 0) {
            if (!this.field_70170_p.field_72995_K) {
                this.setDamageTaken(damage - tpd);
            }
            return true;
        }
        return false;
    }

    public void repairOtherAircraft() {
        float range;
        float f = range = this.getAcInfo() != null ? this.getAcInfo().repairOtherVehiclesRange : 0.0f;
        if (range <= 0.0f) {
            return;
        }
        if (!this.field_70170_p.field_72995_K && this.getCountOnUpdate() % 20 == 0) {
            List list = this.field_70170_p.func_72872_a(MCH_EntityAircraft.class, this.func_70046_E().func_72314_b((double)range, (double)range, (double)range));
            for (int i = 0; i < list.size(); ++i) {
                MCH_EntityAircraft ac = (MCH_EntityAircraft)list.get(i);
                if (W_Entity.isEqual((Entity)this, (Entity)ac) || ac.getHP() >= ac.getMaxHP()) continue;
                ac.setDamageTaken(ac.getDamageTaken() - this.getAcInfo().repairOtherVehiclesValue);
            }
        }
    }

    protected void regenerationMob() {
        if (this.isDestroyed()) {
            return;
        }
        if (this.field_70170_p.field_72995_K) {
            return;
        }
        if (this.getAcInfo() != null && this.getAcInfo().regeneration && this.getRiddenByEntity() != null) {
            MCH_EntitySeat[] st;
            for (MCH_EntitySeat s : st = this.getSeats()) {
                PotionEffect pe;
                Entity e;
                if (s == null || s.field_70128_L || !W_Lib.isEntityLivingBase((Entity)(e = s.field_70153_n)) || e.field_70128_L || (pe = W_Entity.getActivePotionEffect((Entity)e, (Potion)Potion.field_76428_l)) != null && (pe == null || pe.func_76459_b() >= 500)) continue;
                W_Entity.addPotionEffect((Entity)e, (PotionEffect)new PotionEffect(Potion.field_76428_l.field_76415_H, 250, 0, true));
            }
        }
    }

    public double getWaterDepth() {
        int b0 = 5;
        double d0 = 0.0;
        for (int i = 0; i < b0; ++i) {
            double d1 = this.field_70121_D.field_72338_b + (this.field_70121_D.field_72337_e - this.field_70121_D.field_72338_b) * (double)(i + 0) / (double)b0 - 0.125;
            double d2 = this.field_70121_D.field_72338_b + (this.field_70121_D.field_72337_e - this.field_70121_D.field_72338_b) * (double)(i + 1) / (double)b0 - 0.125;
            AxisAlignedBB axisalignedbb = W_AxisAlignedBB.getAABB((double)this.field_70121_D.field_72340_a, (double)(d1 += (double)this.getAcInfo().floatOffset), (double)this.field_70121_D.field_72339_c, (double)this.field_70121_D.field_72336_d, (double)(d2 += (double)this.getAcInfo().floatOffset), (double)this.field_70121_D.field_72334_f);
            if (!this.field_70170_p.func_72830_b(axisalignedbb, Material.field_151586_h)) continue;
            d0 += 1.0 / (double)b0;
        }
        return d0;
    }

    public int getCountOnUpdate() {
        return this.countOnUpdate;
    }

    public boolean canSupply() {
        if (!this.canFloatWater()) {
            return MCH_Lib.getBlockIdY((Entity)this, (int)1, (int)-3) != 0 && !this.func_70090_H();
        }
        return MCH_Lib.getBlockIdY((Entity)this, (int)1, (int)-3) != 0;
    }

    public void setFuel(int fuel) {
        if (!this.field_70170_p.field_72995_K) {
            if (fuel < 0) {
                fuel = 0;
            }
            if (fuel > this.getMaxFuel()) {
                fuel = this.getMaxFuel();
            }
            if (fuel != this.getFuel()) {
                this.func_70096_w().func_75692_b(25, (Object)fuel);
            }
        }
    }

    public int getFuel() {
        return this.func_70096_w().func_75679_c(25);
    }

    public float getFuelP() {
        int m = this.getMaxFuel();
        if (m == 0) {
            return 0.0f;
        }
        return (float)this.getFuel() / (float)m;
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
        float range;
        float f = range = this.getAcInfo() != null ? this.getAcInfo().fuelSupplyRange : 0.0f;
        if (range <= 0.0f) {
            return;
        }
        if (!this.field_70170_p.field_72995_K && this.getCountOnUpdate() % 10 == 0) {
            List list = this.field_70170_p.func_72872_a(MCH_EntityAircraft.class, this.func_70046_E().func_72314_b((double)range, (double)range, (double)range));
            for (int i = 0; i < list.size(); ++i) {
                MCH_EntityAircraft ac = (MCH_EntityAircraft)list.get(i);
                if (W_Entity.isEqual((Entity)this, (Entity)ac)) continue;
                if ((!this.field_70122_E || ac.canSupply()) && ac.getFuel() < ac.getMaxFuel()) {
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

    public void updateFuel() {
        if (this.getMaxFuel() == 0) {
            return;
        }
        if (this.fuelSuppliedCount > 0) {
            --this.fuelSuppliedCount;
        }
        if (!this.isDestroyed() && !this.field_70170_p.field_72995_K) {
            if (this.getCountOnUpdate() % 20 == 0 && this.getFuel() > 1 && this.getThrottle() > 0.0 && this.fuelSuppliedCount <= 0) {
                double t = this.getThrottle() * 1.4;
                if (t > 1.0) {
                    t = 1.0;
                }
                this.fuelConsumption += t * (double)this.getAcInfo().fuelConsumption * (double)this.getFuelConsumptionFactor();
                if (this.fuelConsumption > 1.0) {
                    int f = (int)this.fuelConsumption;
                    this.fuelConsumption -= (double)f;
                    this.setFuel(this.getFuel() - f);
                }
            }
            int curFuel = this.getFuel();
            if (this.canSupply() && this.getCountOnUpdate() % 10 == 0 && curFuel < this.getMaxFuel()) {
                for (int i = 0; i < 3; ++i) {
                    ItemStack fuel;
                    if (curFuel >= this.getMaxFuel() || (fuel = this.getGuiInventory().getFuelSlotItemStack(i)) == null || !(fuel.func_77973_b() instanceof MCH_ItemFuel) || fuel.func_77960_j() >= fuel.func_77958_k()) continue;
                    int fc = this.getMaxFuel() - curFuel;
                    if (fc > 100) {
                        fc = 100;
                    }
                    if (fuel.func_77960_j() > fuel.func_77958_k() - fc) {
                        fc = fuel.func_77958_k() - fuel.func_77960_j();
                    }
                    fuel.func_77964_b(fuel.func_77960_j() + fc);
                    curFuel += fc;
                }
                if (this.getFuel() != curFuel) {
                    MCH_Achievement.addStat((Entity)this.field_70153_n, (Achievement)MCH_Achievement.supplyFuel, (int)1);
                }
                this.setFuel(curFuel);
            }
        }
    }

    public float getFuelConsumptionFactor() {
        return 1.0f;
    }

    public void updateSupplyAmmo() {
        if (!this.field_70170_p.field_72995_K) {
            boolean isReloading = false;
            if (this.getRiddenByEntity() instanceof EntityPlayer && !this.getRiddenByEntity().field_70128_L && ((EntityPlayer)this.getRiddenByEntity()).field_71070_bA instanceof MCH_AircraftGuiContainer) {
                isReloading = true;
            }
            this.setCommonStatus(2, isReloading);
            if (!this.isDestroyed() && this.beforeSupplyAmmo && !isReloading) {
                this.reloadAllWeapon();
                MCH_PacketNotifyAmmoNum.sendAllAmmoNum((MCH_EntityAircraft)this, null);
            }
            this.beforeSupplyAmmo = isReloading;
        }
        if (this.getCommonStatus(2)) {
            this.supplyAmmoWait = 20;
        }
        if (this.supplyAmmoWait > 0) {
            --this.supplyAmmoWait;
        }
    }

    public void supplyAmmo(int weaponID) {
        if (this.field_70170_p.field_72995_K) {
            MCH_WeaponSet ws = this.getWeapon(weaponID);
            ws.supplyRestAllAmmo();
        } else {
            EntityPlayer player;
            MCH_Achievement.addStat((Entity)this.field_70153_n, (Achievement)MCH_Achievement.supplyAmmo, (int)1);
            if (this.getRiddenByEntity() instanceof EntityPlayer && this.canPlayerSupplyAmmo(player = (EntityPlayer)this.getRiddenByEntity(), weaponID)) {
                MCH_WeaponSet ws = this.getWeapon(weaponID);
                block0: for (MCH_WeaponInfo.RoundItem ri : ws.getInfo().roundItems) {
                    int num = ri.num;
                    for (int i = 0; i < player.field_71071_by.field_70462_a.length; ++i) {
                        ItemStack itemStack = player.field_71071_by.field_70462_a[i];
                        if (itemStack != null && itemStack.func_77969_a(ri.itemStack)) {
                            if (itemStack.func_77973_b() == W_Item.getItemByName((String)"water_bucket") || itemStack.func_77973_b() == W_Item.getItemByName((String)"lava_bucket")) {
                                if (itemStack.field_77994_a == 1) {
                                    player.field_71071_by.func_70299_a(i, new ItemStack(W_Item.getItemByName((String)"bucket"), 1));
                                    --num;
                                }
                            } else if (itemStack.field_77994_a > num) {
                                itemStack.field_77994_a -= num;
                                num = 0;
                            } else {
                                num -= itemStack.field_77994_a;
                                itemStack.field_77994_a = 0;
                                player.field_71071_by.field_70462_a[i] = null;
                            }
                        }
                        if (num <= 0) continue block0;
                    }
                }
                ws.supplyRestAllAmmo();
            }
        }
    }

    public void supplyAmmoToOtherAircraft() {
        float range;
        float f = range = this.getAcInfo() != null ? this.getAcInfo().ammoSupplyRange : 0.0f;
        if (range <= 0.0f) {
            return;
        }
        if (!this.field_70170_p.field_72995_K && this.getCountOnUpdate() % 40 == 0) {
            List list = this.field_70170_p.func_72872_a(MCH_EntityAircraft.class, this.func_70046_E().func_72314_b((double)range, (double)range, (double)range));
            for (int i = 0; i < list.size(); ++i) {
                MCH_EntityAircraft ac = (MCH_EntityAircraft)list.get(i);
                if (W_Entity.isEqual((Entity)this, (Entity)ac) || !ac.canSupply()) continue;
                for (int wid = 0; wid < ac.getWeaponNum(); ++wid) {
                    MCH_WeaponSet ws = ac.getWeapon(wid);
                    int num = ws.getRestAllAmmoNum() + ws.getAmmoNum();
                    if (num >= ws.getAllAmmoNum()) continue;
                    int ammo = ws.getAllAmmoNum() / 10;
                    if (ammo < 1) {
                        ammo = 1;
                    }
                    ws.setRestAllAmmoNum(num + ammo);
                    EntityPlayer player = ac.getEntityByWeaponId(wid);
                    if (num == ws.getRestAllAmmoNum() + ws.getAmmoNum()) continue;
                    if (ws.getAmmoNum() <= 0) {
                        ws.reloadMag();
                    }
                    MCH_PacketNotifyAmmoNum.sendAmmoNum((MCH_EntityAircraft)ac, (EntityPlayer)player, (int)wid);
                }
            }
        }
    }

    public boolean canPlayerSupplyAmmo(EntityPlayer player, int weaponId) {
        if (MCH_Lib.getBlockIdY((Entity)this, (int)1, (int)-3) == 0) {
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
            for (ItemStack itemStack : player.field_71071_by.field_70462_a) {
                if (itemStack != null && itemStack.func_77969_a(ri.itemStack)) {
                    num -= itemStack.field_77994_a;
                }
                if (num <= 0) break;
            }
            if (num <= 0) continue;
            return false;
        }
        return true;
    }

    public MCH_EntityAircraft setTextureName(String name) {
        if (name != null && !name.isEmpty()) {
            this.func_70096_w().func_75692_b(21, (Object)String.valueOf(name));
        }
        return this;
    }

    public String getTextureName() {
        return this.func_70096_w().func_75681_e(21);
    }

    public void switchNextTextureName() {
        if (this.getAcInfo() != null) {
            this.setTextureName(this.getAcInfo().getNextTextureName(this.getTextureName()));
        }
    }

    public void zoomCamera() {
        if (this.canZoom()) {
            float z = this.camera.getCameraZoom();
            if ((double)z >= (double)this.getZoomMax() - 0.01) {
                z = 1.0f;
            } else if ((z *= 2.0f) >= (float)this.getZoomMax()) {
                z = this.getZoomMax();
            }
            this.camera.setCameraZoom((double)z <= (double)this.getZoomMax() + 0.01 ? z : 1.0f);
        }
    }

    public int getZoomMax() {
        return this.getAcInfo() != null ? this.getAcInfo().cameraZoom : 1;
    }

    public boolean canZoom() {
        return this.getZoomMax() > 1;
    }

    public boolean canSwitchCameraMode() {
        if (this.isDestroyed()) {
            return false;
        }
        return this.getAcInfo() != null && this.getAcInfo().isEnableNightVision;
    }

    public boolean canSwitchCameraMode(int seatID) {
        if (this.isDestroyed()) {
            return false;
        }
        return this.canSwitchCameraMode() && this.camera.isValidUid(seatID);
    }

    public int getCameraMode(EntityPlayer player) {
        return this.camera.getMode(this.getSeatIdByEntity((Entity)player));
    }

    public String getCameraModeName(EntityPlayer player) {
        return this.camera.getModeName(this.getSeatIdByEntity((Entity)player));
    }

    public void switchCameraMode(EntityPlayer player) {
        this.switchCameraMode(player, this.camera.getMode(this.getSeatIdByEntity((Entity)player)) + 1);
    }

    public void switchCameraMode(EntityPlayer player, int mode) {
        this.camera.setMode(this.getSeatIdByEntity((Entity)player), mode);
    }

    public void updateCameraViewers() {
        for (int i = 0; i < this.getSeatNum() + 1; ++i) {
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
                this.entityRadar.updateXZ((Entity)this, 64);
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

    public void func_70091_d(double par1, double par3, double par5) {
        double d11;
        double d10;
        double d12;
        int j;
        if (this.getAcInfo() == null) {
            return;
        }
        this.field_70170_p.field_72984_F.func_76320_a("move");
        this.field_70139_V *= 0.4f;
        double d3 = this.field_70165_t;
        double d4 = this.field_70163_u;
        double d5 = this.field_70161_v;
        double d6 = par1;
        double d7 = par3;
        double d8 = par5;
        AxisAlignedBB axisalignedbb = this.field_70121_D.func_72329_c();
        List list = MCH_EntityAircraft.getCollidingBoundingBoxes((Entity)this, (AxisAlignedBB)this.field_70121_D.func_72321_a(par1, par3, par5));
        for (int i = 0; i < list.size(); ++i) {
            par3 = ((AxisAlignedBB)list.get(i)).func_72323_b(this.field_70121_D, par3);
        }
        this.field_70121_D.func_72317_d(0.0, par3, 0.0);
        if (!this.field_70135_K && d7 != par3) {
            par5 = 0.0;
            par3 = 0.0;
            par1 = 0.0;
        }
        boolean flag1 = this.field_70122_E || d7 != par3 && d7 < 0.0;
        for (j = 0; j < list.size(); ++j) {
            par1 = ((AxisAlignedBB)list.get(j)).func_72316_a(this.field_70121_D, par1);
        }
        this.field_70121_D.func_72317_d(par1, 0.0, 0.0);
        if (!this.field_70135_K && d6 != par1) {
            par5 = 0.0;
            par3 = 0.0;
            par1 = 0.0;
        }
        for (j = 0; j < list.size(); ++j) {
            par5 = ((AxisAlignedBB)list.get(j)).func_72322_c(this.field_70121_D, par5);
        }
        this.field_70121_D.func_72317_d(0.0, 0.0, par5);
        if (!this.field_70135_K && d8 != par5) {
            par5 = 0.0;
            par3 = 0.0;
            par1 = 0.0;
        }
        if (this.field_70138_W > 0.0f && flag1 && this.field_70139_V < 0.05f && (d6 != par1 || d8 != par5)) {
            int k;
            d12 = par1;
            d10 = par3;
            d11 = par5;
            par1 = d6;
            par3 = this.field_70138_W;
            par5 = d8;
            AxisAlignedBB axisalignedbb1 = this.field_70121_D.func_72329_c();
            this.field_70121_D.func_72328_c(axisalignedbb);
            list = MCH_EntityAircraft.getCollidingBoundingBoxes((Entity)this, (AxisAlignedBB)this.field_70121_D.func_72321_a(d6, par3, d8));
            for (k = 0; k < list.size(); ++k) {
                par3 = ((AxisAlignedBB)list.get(k)).func_72323_b(this.field_70121_D, par3);
            }
            this.field_70121_D.func_72317_d(0.0, par3, 0.0);
            if (!this.field_70135_K && d7 != par3) {
                par5 = 0.0;
                par3 = 0.0;
                par1 = 0.0;
            }
            for (k = 0; k < list.size(); ++k) {
                par1 = ((AxisAlignedBB)list.get(k)).func_72316_a(this.field_70121_D, par1);
            }
            this.field_70121_D.func_72317_d(par1, 0.0, 0.0);
            if (!this.field_70135_K && d6 != par1) {
                par5 = 0.0;
                par3 = 0.0;
                par1 = 0.0;
            }
            for (k = 0; k < list.size(); ++k) {
                par5 = ((AxisAlignedBB)list.get(k)).func_72322_c(this.field_70121_D, par5);
            }
            this.field_70121_D.func_72317_d(0.0, 0.0, par5);
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
                par3 = -this.field_70138_W;
                for (k = 0; k < list.size(); ++k) {
                    par3 = ((AxisAlignedBB)list.get(k)).func_72323_b(this.field_70121_D, par3);
                }
                this.field_70121_D.func_72317_d(0.0, par3, 0.0);
            }
            if (d12 * d12 + d11 * d11 >= par1 * par1 + par5 * par5) {
                par1 = d12;
                par3 = d10;
                par5 = d11;
                this.field_70121_D.func_72328_c(axisalignedbb1);
            }
        }
        this.field_70170_p.field_72984_F.func_76319_b();
        this.field_70170_p.field_72984_F.func_76320_a("rest");
        this.field_70165_t = (this.field_70121_D.field_72340_a + this.field_70121_D.field_72336_d) / 2.0;
        this.field_70163_u = this.field_70121_D.field_72338_b + (double)this.field_70129_M - (double)this.field_70139_V;
        this.field_70161_v = (this.field_70121_D.field_72339_c + this.field_70121_D.field_72334_f) / 2.0;
        this.field_70123_F = d6 != par1 || d8 != par5;
        this.field_70124_G = d7 != par3;
        this.field_70122_E = d7 != par3 && d7 < 0.0;
        this.field_70132_H = this.field_70123_F || this.field_70124_G;
        this.func_70064_a(par3, this.field_70122_E);
        if (d6 != par1) {
            this.field_70159_w = 0.0;
        }
        if (d7 != par3) {
            this.field_70181_x = 0.0;
        }
        if (d8 != par5) {
            this.field_70179_y = 0.0;
        }
        d12 = this.field_70165_t - d3;
        d10 = this.field_70163_u - d4;
        d11 = this.field_70161_v - d5;
        try {
            this.doBlockCollisions();
        }
        catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.func_85055_a((Throwable)throwable, (String)"Checking entity tile collision");
            CrashReportCategory crashreportcategory = crashreport.func_85058_a("Entity being checked for collision");
            this.func_85029_a(crashreportcategory);
            throw new ReportedException(crashreport);
        }
        this.field_70170_p.field_72984_F.func_76319_b();
    }

    public static List getCollidingBoundingBoxes(Entity par1Entity, AxisAlignedBB par2AxisAlignedBB) {
        ArrayList<AxisAlignedBB> collidingBoundingBoxes = new ArrayList<AxisAlignedBB>();
        collidingBoundingBoxes.clear();
        int i = MathHelper.func_76128_c((double)par2AxisAlignedBB.field_72340_a);
        int j = MathHelper.func_76128_c((double)(par2AxisAlignedBB.field_72336_d + 1.0));
        int k = MathHelper.func_76128_c((double)par2AxisAlignedBB.field_72338_b);
        int l = MathHelper.func_76128_c((double)(par2AxisAlignedBB.field_72337_e + 1.0));
        int i1 = MathHelper.func_76128_c((double)par2AxisAlignedBB.field_72339_c);
        int j1 = MathHelper.func_76128_c((double)(par2AxisAlignedBB.field_72334_f + 1.0));
        for (int k1 = i; k1 < j; ++k1) {
            for (int l1 = i1; l1 < j1; ++l1) {
                if (!par1Entity.field_70170_p.func_72899_e(k1, 64, l1)) continue;
                for (int i2 = k - 1; i2 < l; ++i2) {
                    Block block = W_WorldFunc.getBlock((World)par1Entity.field_70170_p, (int)k1, (int)i2, (int)l1);
                    if (block == null) continue;
                    block.func_149743_a(par1Entity.field_70170_p, k1, i2, l1, par2AxisAlignedBB, collidingBoundingBoxes, par1Entity);
                }
            }
        }
        double d0 = 0.25;
        List list = par1Entity.field_70170_p.func_72839_b(par1Entity, par2AxisAlignedBB.func_72314_b(d0, d0, d0));
        for (int j2 = 0; j2 < list.size(); ++j2) {
            Entity entity = (Entity)list.get(j2);
            if (W_Lib.isEntityLivingBase((Entity)entity) || entity instanceof MCH_EntitySeat || entity instanceof MCH_EntityHitBox) continue;
            AxisAlignedBB axisalignedbb1 = entity.func_70046_E();
            if (axisalignedbb1 != null && axisalignedbb1.func_72326_a(par2AxisAlignedBB)) {
                collidingBoundingBoxes.add(axisalignedbb1);
            }
            if ((axisalignedbb1 = par1Entity.func_70114_g(entity)) == null || !axisalignedbb1.func_72326_a(par2AxisAlignedBB)) continue;
            collidingBoundingBoxes.add(axisalignedbb1);
        }
        return collidingBoundingBoxes;
    }

    protected void onUpdate_updateBlock() {
        if (!MCH_Config.Collision_DestroyBlock.prmBool) {
            return;
        }
        for (int l = 0; l < 4; ++l) {
            int i1 = MathHelper.func_76128_c((double)(this.field_70165_t + ((double)(l % 2) - 0.5) * 0.8));
            int j1 = MathHelper.func_76128_c((double)(this.field_70161_v + ((double)(l / 2) - 0.5) * 0.8));
            for (int k1 = 0; k1 < 2; ++k1) {
                int l1 = MathHelper.func_76128_c((double)this.field_70163_u) + k1;
                Block block = W_WorldFunc.getBlock((World)this.field_70170_p, (int)i1, (int)l1, (int)j1);
                if (W_Block.isNull((Block)block)) continue;
                if (block == W_Block.getSnowLayer()) {
                    this.field_70170_p.func_147468_f(i1, l1, j1);
                }
                if (block != W_Blocks.field_150392_bi && block != W_Blocks.field_150414_aQ) continue;
                W_WorldFunc.destroyBlock((World)this.field_70170_p, (int)i1, (int)l1, (int)j1, (boolean)false);
            }
        }
    }

    public void onUpdate_ParticleSmoke() {
        if (!this.field_70170_p.field_72995_K) {
            return;
        }
        if (this.getCurrentThrottle() <= (double)0.1f) {
            return;
        }
        float yaw = this.getRotYaw();
        float pitch = this.getRotPitch();
        float roll = this.getRotRoll();
        MCH_WeaponSet ws = this.getCurrentWeapon(this.getRiddenByEntity());
        if (!(ws.getFirstWeapon() instanceof MCH_WeaponSmoke)) {
            return;
        }
        for (int i = 0; i < ws.getWeaponNum(); ++i) {
            MCH_WeaponInfo wi;
            MCH_WeaponBase wb = ws.getWeapon(i);
            if (wb == null || (wi = wb.getInfo()) == null) continue;
            Vec3 rot = MCH_Lib.RotVec3((double)0.0, (double)0.0, (double)1.0, (float)(-yaw - 180.0f + wb.fixRotationYaw), (float)(pitch - wb.fixRotationPitch), (float)roll);
            if ((double)this.field_70146_Z.nextFloat() > this.getCurrentThrottle() * 1.5) continue;
            Vec3 pos = MCH_Lib.RotVec3((Vec3)wb.position, (float)(-yaw), (float)(-pitch), (float)(-roll));
            double x = this.field_70165_t + pos.field_72450_a + rot.field_72450_a;
            double y = this.field_70163_u + pos.field_72448_b + rot.field_72448_b;
            double z = this.field_70161_v + pos.field_72449_c + rot.field_72449_c;
            for (int smk = 0; smk < wi.smokeNum; ++smk) {
                float c = this.field_70146_Z.nextFloat() * 0.05f;
                int maxAge = (int)(this.field_70146_Z.nextDouble() * (double)wi.smokeMaxAge);
                MCH_ParticleParam prm = new MCH_ParticleParam(this.field_70170_p, "smoke", x, y, z);
                prm.setMotion(rot.field_72450_a * (double)wi.acceleration + (this.field_70146_Z.nextDouble() - 0.5) * 0.2, rot.field_72448_b * (double)wi.acceleration + (this.field_70146_Z.nextDouble() - 0.5) * 0.2, rot.field_72449_c * (double)wi.acceleration + (this.field_70146_Z.nextDouble() - 0.5) * 0.2);
                prm.size = ((float)this.field_70146_Z.nextInt(5) + 5.0f) * wi.smokeSize;
                prm.setColor(wi.color.a + this.field_70146_Z.nextFloat() * 0.05f, wi.color.r + c, wi.color.g + c, wi.color.b + c);
                prm.age = maxAge;
                prm.toWhite = true;
                prm.diffusible = true;
                MCH_ParticlesUtil.spawnParticle((MCH_ParticleParam)prm);
            }
        }
    }

    protected void onUpdate_ParticleSandCloud(boolean seaOnly) {
        int y;
        if (seaOnly && !this.getAcInfo().enableSeaSurfaceParticle) {
            return;
        }
        double particlePosY = (int)this.field_70163_u;
        boolean b = false;
        float scale = this.getAcInfo().particlesScale * 3.0f;
        if (seaOnly) {
            scale *= 2.0f;
        }
        double throttle = this.getCurrentThrottle();
        if ((throttle *= 2.0) > 1.0) {
            throttle = 1.0;
        }
        int count = seaOnly ? (int)(scale * 7.0f) : 0;
        int rangeY = (int)(scale * 10.0f) + 1;
        for (y = 0; y < rangeY && !b; ++y) {
            block1: for (int x = -1; x <= 1; ++x) {
                for (int z = -1; z <= 1; ++z) {
                    Block block = W_WorldFunc.getBlock((World)this.field_70170_p, (int)((int)(this.field_70165_t + 0.5) + x), (int)((int)(this.field_70163_u + 0.5) - y), (int)((int)(this.field_70161_v + 0.5) + z));
                    if (b || block == null) continue;
                    if (Block.func_149680_a((Block)block, (Block)Blocks.field_150350_a)) continue;
                    if (seaOnly && W_Block.isEqual((Block)block, (Block)W_Block.getWater())) {
                        --count;
                    }
                    if (count > 0) continue;
                    particlePosY = this.field_70163_u + 1.0 + (double)(scale / 5.0f) - (double)y;
                    b = true;
                    x += 100;
                    continue block1;
                }
            }
        }
        double pn = (double)(rangeY - y + 1) / (5.0 * (double)scale) / 2.0;
        if (b && this.getAcInfo().particlesScale > 0.01f) {
            for (int k = 0; k < (int)(throttle * 6.0 * pn); ++k) {
                float r = (float)(this.field_70146_Z.nextDouble() * Math.PI * 2.0);
                double dx = MathHelper.func_76134_b((float)r);
                double dz = MathHelper.func_76126_a((float)r);
                MCH_ParticleParam prm = new MCH_ParticleParam(this.field_70170_p, "smoke", this.field_70165_t + dx * (double)scale * 3.0, particlePosY + (this.field_70146_Z.nextDouble() - 0.5) * (double)scale, this.field_70161_v + dz * (double)scale * 3.0, (double)scale * (dx * 0.3), (double)scale * -0.4 * 0.05, (double)scale * (dz * 0.3), scale * 5.0f);
                prm.setColor(prm.a * 0.6f, prm.r, prm.g, prm.b);
                prm.age = (int)(10.0f * scale);
                prm.motionYUpAge = seaOnly ? 0.2f : 0.1f;
                MCH_ParticlesUtil.spawnParticle((MCH_ParticleParam)prm);
            }
        }
    }

    protected boolean func_70041_e_() {
        return false;
    }

    public AxisAlignedBB func_70114_g(Entity par1Entity) {
        return par1Entity.field_70121_D;
    }

    public AxisAlignedBB func_70046_E() {
        return this.field_70121_D;
    }

    public boolean func_70104_M() {
        return false;
    }

    public double func_70042_X() {
        return 0.0;
    }

    public float func_70053_R() {
        return 2.0f;
    }

    public boolean func_70067_L() {
        return !this.field_70128_L;
    }

    public boolean useFlare(int type) {
        if (this.getAcInfo() == null || !this.getAcInfo().haveFlare()) {
            return false;
        }
        for (int i : this.getAcInfo().flare.types) {
            if (i != type) continue;
            this.setCommonStatus(0, true);
            if (!this.flareDv.use(type)) continue;
            return true;
        }
        return false;
    }

    public int getCurrentFlareType() {
        if (!this.haveFlare()) {
            return 0;
        }
        return this.getAcInfo().flare.types[this.currentFlareIndex];
    }

    public void nextFlareType() {
        if (this.haveFlare()) {
            this.currentFlareIndex = (this.currentFlareIndex + 1) % this.getAcInfo().flare.types.length;
        }
    }

    public boolean canUseFlare() {
        if (this.getAcInfo() == null || !this.getAcInfo().haveFlare()) {
            return false;
        }
        if (this.getCommonStatus(0)) {
            return false;
        }
        return this.flareDv.tick == 0;
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
        if (MCH_EntityAircraft.isEqual((Entity)this.getRiddenByEntity(), (Entity)entity)) {
            return 0;
        }
        for (int i = 0; i < this.getSeats().length; ++i) {
            MCH_EntitySeat seat = this.getSeats()[i];
            if (seat == null || !MCH_EntityAircraft.isEqual((Entity)seat.field_70153_n, (Entity)entity)) continue;
            return i + 1;
        }
        return -1;
    }

    public MCH_EntitySeat getSeatByEntity(Entity entity) {
        int idx = this.getSeatIdByEntity(entity);
        if (idx > 0) {
            return this.getSeat(idx - 1);
        }
        return null;
    }

    public Entity getEntityBySeatId(int id) {
        if (id == 0) {
            return this.getRiddenByEntity();
        }
        if (--id < 0 || id >= this.getSeats().length) {
            return null;
        }
        return this.seats[id] != null ? this.seats[id].field_70153_n : null;
    }

    public EntityPlayer getEntityByWeaponId(int id) {
        if (id >= 0 && id < this.getWeaponNum()) {
            for (int i = 0; i < this.currentWeaponID.length; ++i) {
                Entity e;
                if (this.currentWeaponID[i] != id || !((e = this.getEntityBySeatId(i)) instanceof EntityPlayer)) continue;
                return (EntityPlayer)e;
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
        if (weapon != null && (entity = this.getEntityBySeatId(this.getWeaponSeatID(null, weapon))) == null && weapon.canUsePilot) {
            entity = this.getRiddenByEntity();
        }
        return entity;
    }

    protected void newSeats(int seatsNum) {
        if (seatsNum >= 2) {
            if (this.seats != null) {
                for (int i = 0; i < this.seats.length; ++i) {
                    if (this.seats[i] == null) continue;
                    this.seats[i].func_70106_y();
                    this.seats[i] = null;
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
            MCH_Lib.DbgLog((World)this.field_70170_p, (String)("MCH_EntityAircraft.setSeat SeatID=" + idx + " / seat[]" + (this.seats[idx] != null) + " / " + (seat.field_70153_n != null)), (Object[])new Object[0]);
            if (this.seats[idx] == null || this.seats[idx].field_70153_n != null) {
                // empty if block
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
        if (this.pilotSeat != null && !this.pilotSeat.field_70128_L) {
            this.pilotSeat.field_70169_q = this.pilotSeat.field_70165_t;
            this.pilotSeat.field_70167_r = this.pilotSeat.field_70163_u;
            this.pilotSeat.field_70166_s = this.pilotSeat.field_70161_v;
            this.pilotSeat.func_70107_b(px, py, pz);
            if (info != null && info.length > 0 && info[0] != null) {
                Vec3 v = this.getTransformedPosition(info[0].pos.field_72450_a, info[0].pos.field_72448_b, info[0].pos.field_72449_c, px, py, pz, info[0].rotSeat);
                this.pilotSeat.func_70107_b(v.field_72450_a, v.field_72448_b, v.field_72449_c);
            }
            this.pilotSeat.field_70125_A = this.getRotPitch();
            this.pilotSeat.field_70177_z = this.getRotYaw();
            if (setPrevPos) {
                this.pilotSeat.field_70169_q = this.pilotSeat.field_70165_t;
                this.pilotSeat.field_70167_r = this.pilotSeat.field_70163_u;
                this.pilotSeat.field_70166_s = this.pilotSeat.field_70161_v;
            }
        }
        int i = 0;
        for (MCH_EntitySeat seat : this.seats) {
            ++i;
            if (seat == null || seat.field_70128_L) continue;
            float offsetY = 0.0f;
            if (seat.field_70153_n != null) {
                if (W_Lib.isClientPlayer((Entity)seat.field_70153_n)) {
                    offsetY = 1.0f;
                } else if (seat.field_70153_n.field_70131_O >= 1.0f) {
                    offsetY = -seat.field_70153_n.field_70131_O + 1.0f;
                }
            }
            seat.field_70169_q = seat.field_70165_t;
            seat.field_70167_r = seat.field_70163_u;
            seat.field_70166_s = seat.field_70161_v;
            MCH_SeatInfo si = i < info.length ? info[i] : info[0];
            Vec3 v = this.getTransformedPosition(si.pos.field_72450_a, si.pos.field_72448_b + (double)offsetY, si.pos.field_72449_c, px, py, pz, si.rotSeat);
            seat.func_70107_b(v.field_72450_a, v.field_72448_b, v.field_72449_c);
            seat.field_70125_A = this.getRotPitch();
            seat.field_70177_z = this.getRotYaw();
            if (setPrevPos) {
                seat.field_70169_q = seat.field_70165_t;
                seat.field_70167_r = seat.field_70163_u;
                seat.field_70166_s = seat.field_70161_v;
            }
            if (si instanceof MCH_SeatRackInfo) {
                seat.updateRotation(((MCH_SeatRackInfo)si).fixYaw + this.getRotYaw(), ((MCH_SeatRackInfo)si).fixPitch);
            }
            seat.updatePosition();
        }
    }

    public int getClientPositionDelayCorrection() {
        return 7;
    }

    public void func_70056_a(double par1, double par3, double par5, float par7, float par8, int par9) {
        this.aircraftPosRotInc = par9 + this.getClientPositionDelayCorrection();
        this.aircraftX = par1;
        this.aircraftY = par3;
        this.aircraftZ = par5;
        this.aircraftYaw = par7;
        this.aircraftPitch = par8;
        this.field_70159_w = this.velocityX;
        this.field_70181_x = this.velocityY;
        this.field_70179_y = this.velocityZ;
    }

    public void updateRiderPosition(double px, double py, double pz) {
        MCH_SeatInfo[] info = this.getSeatsInfo();
        if (this.field_70153_n != null && !this.field_70153_n.field_70128_L) {
            float riddenEntityYOffset = this.field_70153_n.field_70129_M;
            float offset = 0.0f;
            if (this.field_70153_n instanceof EntityPlayer && !W_Lib.isClientPlayer((Entity)this.field_70153_n)) {
                offset -= 1.62f;
            }
            Vec3 v = info != null && info.length > 0 ? this.getTransformedPosition(info[0].pos.field_72450_a, info[0].pos.field_72448_b + (double)riddenEntityYOffset - 0.5, info[0].pos.field_72449_c, px, py, pz, info[0].rotSeat) : this.getTransformedPosition(0.0, (double)(riddenEntityYOffset - 1.0f), 0.0);
            this.field_70153_n.field_70129_M = 0.0f;
            this.field_70153_n.func_70107_b(v.field_72450_a, v.field_72448_b, v.field_72449_c);
            this.field_70153_n.field_70129_M = riddenEntityYOffset;
        }
    }

    public void func_70043_V() {
        this.updateRiderPosition(this.field_70165_t, this.field_70163_u, this.field_70161_v);
    }

    public Vec3 calcOnTurretPos(Vec3 pos) {
        float ry = this.getLastRiderYaw();
        if (this.getRiddenByEntity() != null) {
            ry = this.getRiddenByEntity().field_70177_z;
        }
        Vec3 tpos = this.getAcInfo().turretPosition.func_72441_c(0.0, pos.field_72448_b, 0.0);
        Vec3 v = pos.func_72441_c(-tpos.field_72450_a, -tpos.field_72448_b, -tpos.field_72449_c);
        v = MCH_Lib.RotVec3((Vec3)v, (float)(-ry), (float)0.0f, (float)0.0f);
        Vec3 vv = MCH_Lib.RotVec3((Vec3)tpos, (float)(-this.getRotYaw()), (float)(-this.getRotPitch()), (float)(-this.getRotRoll()));
        v.field_72450_a += vv.field_72450_a;
        v.field_72448_b += vv.field_72448_b;
        v.field_72449_c += vv.field_72449_c;
        return v;
    }

    public float getLastRiderYaw() {
        return this.lastRiderYaw;
    }

    public float getLastRiderPitch() {
        return this.lastRiderPitch;
    }

    @SideOnly(value=Side.CLIENT)
    public void setupAllRiderRenderPosition(float tick, EntityPlayer player) {
        double x = this.field_70142_S + (this.field_70165_t - this.field_70142_S) * (double)tick;
        double y = this.field_70137_T + (this.field_70163_u - this.field_70137_T) * (double)tick;
        double z = this.field_70136_U + (this.field_70161_v - this.field_70136_U) * (double)tick;
        this.updateRiderPosition(x, y, z);
        this.updateSeatsPosition(x, y, z, true);
        for (int i = 0; i < this.getSeatNum() + 1; ++i) {
            Entity e = this.getEntityBySeatId(i);
            if (e == null) continue;
            e.field_70142_S = e.field_70165_t;
            e.field_70137_T = e.field_70163_u;
            e.field_70136_U = e.field_70161_v;
        }
        if (this.getTVMissile() != null && W_Lib.isClientPlayer((Entity)this.getTVMissile().shootingEntity)) {
            MCH_EntityTvMissile tv = this.getTVMissile();
            x = tv.field_70169_q + (tv.field_70165_t - tv.field_70169_q) * (double)tick;
            y = tv.field_70167_r + (tv.field_70163_u - tv.field_70167_r) * (double)tick;
            z = tv.field_70166_s + (tv.field_70161_v - tv.field_70166_s) * (double)tick;
            MCH_ViewEntityDummy.setCameraPosition((double)x, (double)y, (double)z);
        } else {
            MCH_AircraftInfo.CameraPosition cpi = this.getCameraPosInfo();
            if (cpi != null && cpi.pos != null) {
                MCH_SeatInfo seatInfo = this.getSeatInfo((Entity)player);
                Vec3 v = seatInfo != null && seatInfo.rotSeat ? this.calcOnTurretPos(cpi.pos) : MCH_Lib.RotVec3((Vec3)cpi.pos, (float)(-this.getRotYaw()), (float)(-this.getRotPitch()), (float)(-this.getRotRoll()));
                MCH_ViewEntityDummy.setCameraPosition((double)(x + v.field_72450_a), (double)(y + v.field_72448_b), (double)(z + v.field_72449_c));
                if (cpi.fixRot) {
                    // empty if block
                }
            }
        }
    }

    public Vec3 getTurretPos(Vec3 pos, boolean turret) {
        if (turret) {
            float ry = this.getLastRiderYaw();
            if (this.getRiddenByEntity() != null) {
                ry = this.getRiddenByEntity().field_70177_z;
            }
            Vec3 tpos = this.getAcInfo().turretPosition.func_72441_c(0.0, pos.field_72448_b, 0.0);
            Vec3 v = pos.func_72441_c(-tpos.field_72450_a, -tpos.field_72448_b, -tpos.field_72449_c);
            v = MCH_Lib.RotVec3((Vec3)v, (float)(-ry), (float)0.0f, (float)0.0f);
            Vec3 vv = MCH_Lib.RotVec3((Vec3)tpos, (float)(-this.getRotYaw()), (float)(-this.getRotPitch()), (float)(-this.getRotRoll()));
            v.field_72450_a += vv.field_72450_a;
            v.field_72448_b += vv.field_72448_b;
            v.field_72449_c += vv.field_72449_c;
            return v;
        }
        return Vec3.func_72443_a((double)0.0, (double)0.0, (double)0.0);
    }

    public Vec3 getTransformedPosition(Vec3 v) {
        return this.getTransformedPosition(v.field_72450_a, v.field_72448_b, v.field_72449_c);
    }

    public Vec3 getTransformedPosition(double x, double y, double z) {
        return this.getTransformedPosition(x, y, z, this.field_70165_t, this.field_70163_u, this.field_70161_v);
    }

    public Vec3 getTransformedPosition(Vec3 v, Vec3 pos) {
        return this.getTransformedPosition(v.field_72450_a, v.field_72448_b, v.field_72449_c, pos.field_72450_a, pos.field_72448_b, pos.field_72449_c);
    }

    public Vec3 getTransformedPosition(Vec3 v, double px, double py, double pz) {
        return this.getTransformedPosition(v.field_72450_a, v.field_72448_b, v.field_72449_c, this.field_70165_t, this.field_70163_u, this.field_70161_v);
    }

    public Vec3 getTransformedPosition(double x, double y, double z, double px, double py, double pz) {
        Vec3 v = MCH_Lib.RotVec3((double)x, (double)y, (double)z, (float)(-this.getRotYaw()), (float)(-this.getRotPitch()), (float)(-this.getRotRoll()));
        return v.func_72441_c(px, py, pz);
    }

    public Vec3 getTransformedPosition(double x, double y, double z, double px, double py, double pz, boolean rotSeat) {
        if (rotSeat && this.getAcInfo() != null) {
            MCH_AircraftInfo info = this.getAcInfo();
            Vec3 tv = MCH_Lib.RotVec3((double)(x - info.turretPosition.field_72450_a), (double)(y - info.turretPosition.field_72448_b), (double)(z - info.turretPosition.field_72449_c), (float)(-this.getLastRiderYaw() + this.getRotYaw()), (float)0.0f, (float)0.0f);
            x = tv.field_72450_a + info.turretPosition.field_72450_a;
            y = tv.field_72448_b + info.turretPosition.field_72450_a;
            z = tv.field_72449_c + info.turretPosition.field_72450_a;
        }
        Vec3 v = MCH_Lib.RotVec3((double)x, (double)y, (double)z, (float)(-this.getRotYaw()), (float)(-this.getRotPitch()), (float)(-this.getRotRoll()));
        return v.func_72441_c(px, py, pz);
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
        if (index >= 0 && seats != null && index < seats.length) {
            return seats[index];
        }
        return null;
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
            for (int i = 0; i < v.length; ++i) {
                v[i] = (MCH_SeatInfo)this.getAcInfo().seatList.get(i);
            }
            this.setSeatsInfo(v);
        }
    }

    public void createSeats(String uuid) {
        if (this.field_70170_p.field_72995_K) {
            return;
        }
        if (uuid.isEmpty()) {
            return;
        }
        this.setCommonUniqueId(uuid);
        this.seats = new MCH_EntitySeat[this.getSeatNum()];
        for (int i = 0; i < this.seats.length; ++i) {
            this.seats[i] = new MCH_EntitySeat(this.field_70170_p, this.field_70165_t, this.field_70163_u, this.field_70161_v);
            this.seats[i].parentUniqueID = this.getCommonUniqueId();
            this.seats[i].seatID = i;
            this.seats[i].setParent(this);
            this.field_70170_p.func_72838_d((Entity)this.seats[i]);
        }
    }

    public boolean interactFirstSeat(EntityPlayer player) {
        if (this.getSeats() == null) {
            return false;
        }
        int seatId = 1;
        for (MCH_EntitySeat seat : this.getSeats()) {
            if (seat != null && seat.field_70153_n == null && !this.isMountedEntity((Entity)player) && this.canRideSeatOrRack(seatId, (Entity)player)) {
                if (this.field_70170_p.field_72995_K) break;
                player.func_70078_a((Entity)seat);
                break;
            }
            ++seatId;
        }
        return true;
    }

    public void onMountPlayerSeat(MCH_EntitySeat seat, Entity entity) {
        if (seat == null || !(entity instanceof EntityPlayer)) {
            return;
        }
        if (this.field_70170_p.field_72995_K && MCH_Lib.getClientPlayer() == entity) {
            this.switchGunnerFreeLookMode(false);
        }
        this.initCurrentWeapon(entity);
        MCH_Lib.DbgLog((World)this.field_70170_p, (String)"onMountEntitySeat:%d", (Object[])new Object[]{W_Entity.getEntityId((Entity)entity)});
        Entity pilot = this.getRiddenByEntity();
        int sid = this.getSeatIdByEntity(entity);
        if (!(sid != 1 || this.getAcInfo() != null && this.getAcInfo().isEnableConcurrentGunnerMode)) {
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
                MCH_Lib.DbgLog((World)this.field_70170_p, (String)"onMountEntitySeat:%d:->%d", (Object[])new Object[]{W_Entity.getEntityId((Entity)pilot), next});
                if (next >= 0) {
                    this.switchWeapon(pilot, next);
                }
            }
        }
        if (this.field_70170_p.field_72995_K) {
            this.updateClientSettings(sid);
        }
    }

    public MCH_WeaponInfo getWeaponInfoById(int id) {
        MCH_WeaponSet ws;
        if (id >= 0 && (ws = this.getWeapon(id)) != null) {
            return ws.getInfo();
        }
        return null;
    }

    public abstract boolean canMountWithNearEmptyMinecart();

    protected void mountWithNearEmptyMinecart() {
        List list;
        if (this.getRidingEntity() != null) {
            return;
        }
        int d = 2;
        if (this.dismountedUserCtrl) {
            d = 6;
        }
        if ((list = this.field_70170_p.func_72839_b((Entity)this, this.field_70121_D.func_72314_b((double)d, (double)d, (double)d))) != null && !list.isEmpty()) {
            for (int i = 0; i < list.size(); ++i) {
                Entity entity = (Entity)list.get(i);
                if (!(entity instanceof EntityMinecartEmpty)) continue;
                if (this.dismountedUserCtrl) {
                    return;
                }
                if (entity.field_70153_n != null || !entity.func_70104_M()) continue;
                this.waitMountEntity = 20;
                MCH_Lib.DbgLog((boolean)this.field_70170_p.field_72995_K, (String)("MCH_EntityAircraft.mountWithNearEmptyMinecart:" + entity), (Object[])new Object[0]);
                this.func_70078_a(entity);
                return;
            }
        }
        this.dismountedUserCtrl = false;
    }

    public boolean isRidePlayer() {
        if (this.getRiddenByEntity() instanceof EntityPlayer) {
            return true;
        }
        for (MCH_EntitySeat seat : this.getSeats()) {
            if (seat == null || !(seat.field_70153_n instanceof EntityPlayer)) continue;
            return true;
        }
        return false;
    }

    public void onUnmountPlayerSeat(MCH_EntitySeat seat, Entity entity) {
        MCH_Lib.DbgLog((World)this.field_70170_p, (String)"onUnmountPlayerSeat:%d", (Object[])new Object[]{W_Entity.getEntityId((Entity)entity)});
        int sid = this.getSeatIdByEntity(entity);
        this.camera.initCamera(sid, entity);
        MCH_SeatInfo seatInfo = this.getSeatInfo(seat.seatID + 1);
        if (seatInfo != null) {
            this.setUnmountPosition(entity, Vec3.func_72443_a((double)seatInfo.pos.field_72450_a, (double)0.0, (double)seatInfo.pos.field_72449_c));
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
        for (int i = 0; i < this.seats.length; ++i) {
            if (this.seats[i] != null) {
                if (this.seats[i].field_70128_L) continue;
                this.seats[i].field_70143_R = 0.0f;
                continue;
            }
            b = true;
        }
        if (b) {
            if (this.seatSearchCount > 40) {
                if (this.field_70170_p.field_72995_K) {
                    MCH_PacketSeatListRequest.requestSeatList((MCH_EntityAircraft)this);
                } else {
                    this.searchSeat();
                }
                this.seatSearchCount = 0;
            }
            ++this.seatSearchCount;
        }
    }

    public void searchSeat() {
        List list = this.field_70170_p.func_72872_a(MCH_EntitySeat.class, this.field_70121_D.func_72314_b(60.0, 60.0, 60.0));
        for (int i = 0; i < list.size(); ++i) {
            MCH_EntitySeat seat = (MCH_EntitySeat)list.get(i);
            if (seat.field_70128_L || !seat.parentUniqueID.equals(this.getCommonUniqueId()) || seat.seatID < 0 || seat.seatID >= this.getSeatNum() || this.seats[seat.seatID] != null) continue;
            this.seats[seat.seatID] = seat;
            seat.setParent(this);
        }
    }

    public String getCommonUniqueId() {
        return this.commonUniqueId;
    }

    public void setCommonUniqueId(String uniqId) {
        this.commonUniqueId = uniqId;
    }

    public void func_70106_y() {
        this.setDead(false);
    }

    public void setDead(boolean dropItems) {
        this.dropContentsWhenDead = dropItems;
        super.func_70106_y();
        if (this.getRiddenByEntity() != null) {
            this.getRiddenByEntity().func_70078_a(null);
        }
        this.getGuiInventory().setDead();
        for (MCH_EntitySeat mCH_EntitySeat : this.seats) {
            if (mCH_EntitySeat == null) continue;
            mCH_EntitySeat.func_70106_y();
        }
        if (this.soundUpdater != null) {
            this.soundUpdater.update();
        }
        if (this.getTowChainEntity() != null) {
            this.getTowChainEntity().func_70106_y();
            this.setTowChainEntity(null);
        }
        for (MCH_EntitySeat mCH_EntitySeat : this.func_70021_al()) {
            if (mCH_EntitySeat == null) continue;
            mCH_EntitySeat.func_70106_y();
        }
        MCH_Lib.DbgLog((World)this.field_70170_p, (String)("setDead:" + (this.getAcInfo() != null ? this.getAcInfo().name : "null")), (Object[])new Object[0]);
    }

    public void unmountEntity() {
        if (!this.isRidePlayer()) {
            this.switchHoveringMode(false);
        }
        this.throttleUp = false;
        this.throttleDown = false;
        this.moveRight = false;
        this.moveLeft = false;
        Entity rByEntity = null;
        if (this.field_70153_n != null) {
            rByEntity = this.field_70153_n;
            this.camera.initCamera(0, rByEntity);
            if (!this.field_70170_p.field_72995_K) {
                this.field_70153_n.func_70078_a(null);
            }
        } else if (this.lastRiddenByEntity != null && (rByEntity = this.lastRiddenByEntity) instanceof EntityPlayer) {
            this.camera.initCamera(0, rByEntity);
        }
        MCH_Lib.DbgLog((World)this.field_70170_p, (String)("unmountEntity:" + rByEntity), (Object[])new Object[0]);
        if (!this.isRidePlayer()) {
            this.switchGunnerMode(false);
        }
        this.setCommonStatus(1, false);
        if (!this.isUAV()) {
            this.setUnmountPosition(rByEntity, this.getSeatsInfo()[0].pos);
        } else if (rByEntity != null && rByEntity.field_70154_o instanceof MCH_EntityUavStation) {
            rByEntity.func_70078_a(null);
        }
        this.field_70153_n = null;
        this.lastRiddenByEntity = null;
        if (this.cs_dismountAll) {
            this.unmountCrew(false);
        }
    }

    public Entity getRidingEntity() {
        return this.field_70154_o;
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
        if (this.getAcInfo() == null) {
            return;
        }
        if (this.getAcInfo().haveRepellingHook()) {
            if (!this.isRepelling()) {
                if (MCH_Lib.getBlockIdY((Entity)this, (int)3, (int)-4) > 0) {
                    this.unmountCrew(false);
                } else if (this.canStartRepelling()) {
                    this.startRepelling();
                }
            } else {
                this.stopRepelling();
            }
        } else if (this.isParachuting) {
            this.stopUnmountCrew();
        } else if (this.getAcInfo().isEnableParachuting && MCH_Lib.getBlockIdY((Entity)this, (int)3, (int)-10) == 0) {
            this.startUnmountCrew();
        } else {
            this.unmountCrew(false);
        }
    }

    public boolean isRepelling() {
        return this.getCommonStatus(5);
    }

    public void setRepellingStat(boolean b) {
        this.setCommonStatus(5, b);
    }

    public Vec3 getRopePos(int ropeIndex) {
        if (this.getAcInfo() != null && this.getAcInfo().haveRepellingHook() && ropeIndex < this.getAcInfo().repellingHooks.size()) {
            return this.getTransformedPosition(((MCH_AircraftInfo.RepellingHook)this.getAcInfo().repellingHooks.get((int)ropeIndex)).pos);
        }
        return Vec3.func_72443_a((double)this.field_70165_t, (double)this.field_70163_u, (double)this.field_70161_v);
    }

    private void startRepelling() {
        MCH_Lib.DbgLog((World)this.field_70170_p, (String)"MCH_EntityAircraft.startRepelling()", (Object[])new Object[0]);
        this.setRepellingStat(true);
        this.throttleUp = false;
        this.throttleDown = false;
        this.moveLeft = false;
        this.moveRight = false;
        this.tickRepelling = 0;
    }

    private void stopRepelling() {
        MCH_Lib.DbgLog((World)this.field_70170_p, (String)"MCH_EntityAircraft.stopRepelling()", (Object[])new Object[0]);
        this.setRepellingStat(false);
    }

    public static float abs(float p_76135_0_) {
        return p_76135_0_ >= 0.0f ? p_76135_0_ : -p_76135_0_;
    }

    public static double abs(double p_76135_0_) {
        return p_76135_0_ >= 0.0 ? p_76135_0_ : -p_76135_0_;
    }

    public boolean canStartRepelling() {
        Vec3 v;
        return this.getAcInfo().haveRepellingHook() && this.isHovering() && MCH_EntityAircraft.abs((float)this.getRotPitch()) < 3.0f && MCH_EntityAircraft.abs((float)this.getRotRoll()) < 3.0f && (v = ((Vec3)this.prevPosition.oldest()).func_72441_c(-this.field_70165_t, -this.field_70163_u, -this.field_70161_v)).func_72433_c() < 0.3;
    }

    public boolean unmountCrew(boolean unmountParachute) {
        boolean ret = false;
        MCH_SeatInfo[] pos = this.getSeatsInfo();
        for (int i = 0; i < this.seats.length; ++i) {
            Vec3 dropPos;
            Entity entity;
            if (this.seats[i] == null || this.seats[i].field_70153_n == null || (entity = this.seats[i].field_70153_n) instanceof EntityPlayer || pos[i + 1] instanceof MCH_SeatRackInfo) continue;
            if (unmountParachute) {
                if (this.getSeatIdByEntity(entity) <= 1) continue;
                ret = true;
                dropPos = this.getTransformedPosition(this.getAcInfo().mobDropOption.pos, (Vec3)this.prevPosition.oldest());
                this.seats[i].field_70165_t = dropPos.field_72450_a;
                this.seats[i].field_70163_u = dropPos.field_72448_b;
                this.seats[i].field_70161_v = dropPos.field_72449_c;
                entity.func_70078_a(null);
                entity.field_70165_t = dropPos.field_72450_a;
                entity.field_70163_u = dropPos.field_72448_b;
                entity.field_70161_v = dropPos.field_72449_c;
                this.dropEntityParachute(entity);
                break;
            }
            ret = true;
            dropPos = pos[i + 1].pos;
            this.setUnmountPosition((Entity)this.seats[i], pos[i + 1].pos);
            entity.func_70078_a(null);
            this.setUnmountPosition(entity, pos[i + 1].pos);
        }
        return ret;
    }

    public void setUnmountPosition(Entity rByEntity, Vec3 pos) {
        if (rByEntity != null) {
            Vec3 v;
            MCH_AircraftInfo info = this.getAcInfo();
            if (info != null && info.unmountPosition != null) {
                v = this.getTransformedPosition(info.unmountPosition);
            } else {
                double x = pos.field_72450_a;
                x = x >= 0.0 ? x + 3.0 : x - 3.0;
                v = this.getTransformedPosition(x, 2.0, pos.field_72449_c);
            }
            rByEntity.func_70107_b(v.field_72450_a, v.field_72448_b, v.field_72449_c);
            this.listUnmountReserve.add(new UnmountReserve(this, rByEntity, v.field_72450_a, v.field_72448_b, v.field_72449_c));
        }
    }

    public boolean unmountEntityFromSeat(Entity entity) {
        if (entity == null || this.seats == null || this.seats.length == 0) {
            return false;
        }
        for (MCH_EntitySeat seat : this.seats) {
            if (seat == null || seat.field_70153_n == null || !W_Entity.isEqual((Entity)seat.field_70153_n, (Entity)entity)) continue;
            entity.func_70078_a(null);
        }
        return false;
    }

    public void ejectSeat(Entity entity) {
        int sid = this.getSeatIdByEntity(entity);
        if (sid < 0 || sid > 1) {
            return;
        }
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

    public void ejectSeatSub(Entity entity, int sid) {
        Vec3 v;
        Vec3 pos;
        Vec3 vec3 = pos = this.getSeatInfo(sid) != null ? this.getSeatInfo((int)sid).pos : null;
        if (pos != null) {
            v = this.getTransformedPosition(pos.field_72450_a, pos.field_72448_b + 2.0, pos.field_72449_c);
            entity.func_70107_b(v.field_72450_a, v.field_72448_b, v.field_72449_c);
        }
        v = MCH_Lib.RotVec3((double)0.0, (double)2.0, (double)0.0, (float)(-this.getRotYaw()), (float)(-this.getRotPitch()), (float)(-this.getRotRoll()));
        entity.field_70159_w = this.field_70159_w + v.field_72450_a + ((double)this.field_70146_Z.nextFloat() - 0.5) * 0.1;
        entity.field_70181_x = this.field_70181_x + v.field_72448_b;
        entity.field_70179_y = this.field_70179_y + v.field_72449_c + ((double)this.field_70146_Z.nextFloat() - 0.5) * 0.1;
        MCH_EntityParachute parachute = new MCH_EntityParachute(this.field_70170_p, entity.field_70165_t, entity.field_70163_u, entity.field_70161_v);
        parachute.field_70177_z = entity.field_70177_z;
        parachute.field_70159_w = entity.field_70159_w;
        parachute.field_70181_x = entity.field_70181_x;
        parachute.field_70179_y = entity.field_70179_y;
        parachute.field_70143_R = entity.field_70143_R;
        parachute.user = entity;
        parachute.setType(2);
        this.field_70170_p.func_72838_d((Entity)parachute);
        if (this.getAcInfo().haveCanopy() && this.isCanopyClose()) {
            this.openCanopy_EjectSeat();
        }
        W_WorldFunc.MOD_playSoundAtEntity((Entity)entity, (String)"eject_seat", (float)5.0f, (float)1.0f);
    }

    public boolean canEjectSeat(Entity entity) {
        int sid = this.getSeatIdByEntity(entity);
        if (sid == 0 && this.isUAV()) {
            return false;
        }
        return sid >= 0 && sid < 2 && this.getAcInfo() != null && this.getAcInfo().isEnableEjectionSeat;
    }

    public int getNumEjectionSeat() {
        return 0;
    }

    public int getMountedEntityNum() {
        int num = 0;
        if (this.field_70153_n != null && !this.field_70153_n.field_70128_L) {
            ++num;
        }
        if (this.seats != null && this.seats.length > 0) {
            for (MCH_EntitySeat seat : this.seats) {
                if (seat == null || seat.field_70153_n == null || seat.field_70153_n.field_70128_L) continue;
                ++num;
            }
        }
        return num;
    }

    public void mountMobToSeats() {
        List list = this.field_70170_p.func_72872_a(W_Lib.getEntityLivingBaseClass(), this.field_70121_D.func_72314_b(3.0, 2.0, 3.0));
        block0: for (int i = 0; i < list.size(); ++i) {
            Entity entity = (Entity)list.get(i);
            if (entity instanceof EntityPlayer || entity.field_70154_o != null) continue;
            int sid = 1;
            for (MCH_EntitySeat seat : this.getSeats()) {
                if (seat != null && seat.field_70153_n == null && !this.isMountedEntity(entity) && this.canRideSeatOrRack(sid, entity)) {
                    if (this.getSeatInfo(sid) instanceof MCH_SeatRackInfo) continue block0;
                    entity.func_70078_a((Entity)seat);
                }
                ++sid;
            }
        }
    }

    public void mountEntityToRack() {
        if (!MCH_Config.EnablePutRackInFlying.prmBool) {
            if (this.getCurrentThrottle() > 0.3) {
                return;
            }
            Block block = MCH_Lib.getBlockY((Entity)this, (int)1, (int)-3, (boolean)true);
            if (block == null || W_Block.isEqual((Block)block, (Block)Blocks.field_150350_a)) {
                return;
            }
        }
        int countRideEntity = 0;
        block0: for (int sid = 0; sid < this.getSeatNum(); ++sid) {
            MCH_EntitySeat seat = this.getSeat(sid);
            if (!(this.getSeatInfo(1 + sid) instanceof MCH_SeatRackInfo) || seat == null || seat.field_70153_n != null) continue;
            MCH_SeatRackInfo info = (MCH_SeatRackInfo)this.getSeatInfo(1 + sid);
            Vec3 v = MCH_Lib.RotVec3((double)info.getEntryPos().field_72450_a, (double)info.getEntryPos().field_72448_b, (double)info.getEntryPos().field_72449_c, (float)(-this.getRotYaw()), (float)(-this.getRotPitch()), (float)(-this.getRotRoll()));
            v.field_72450_a += this.field_70165_t;
            v.field_72448_b += this.field_70163_u;
            v.field_72449_c += this.field_70161_v;
            AxisAlignedBB bb = AxisAlignedBB.func_72330_a((double)v.field_72450_a, (double)v.field_72448_b, (double)v.field_72449_c, (double)v.field_72450_a, (double)v.field_72448_b, (double)v.field_72449_c);
            float range = info.range;
            List list = this.field_70170_p.func_72839_b((Entity)this, bb.func_72314_b((double)range, (double)range, (double)range));
            for (int i = 0; i < list.size(); ++i) {
                NBTTagCompound nbt;
                Entity entity = (Entity)list.get(i);
                if (!this.canRideSeatOrRack(1 + sid, entity)) continue;
                if (entity instanceof MCH_IEntityCanRideAircraft) {
                    if (!((MCH_IEntityCanRideAircraft)entity).canRideAircraft(this, sid, info)) continue;
                    MCH_Lib.DbgLog((World)this.field_70170_p, (String)"MCH_EntityAircraft.mountEntityToRack:%d:%s", (Object[])new Object[]{sid, entity});
                    entity.func_70078_a((Entity)seat);
                    ++countRideEntity;
                    continue block0;
                }
                if (entity.field_70154_o != null || !(nbt = entity.getEntityData()).func_74764_b("CanMountEntity") || !nbt.func_74767_n("CanMountEntity")) continue;
                MCH_Lib.DbgLog((World)this.field_70170_p, (String)"MCH_EntityAircraft.mountEntityToRack:%d:%s:%s", (Object[])new Object[]{sid, entity, entity.getClass()});
                entity.func_70078_a((Entity)seat);
                ++countRideEntity;
                continue block0;
            }
        }
        if (countRideEntity > 0) {
            W_WorldFunc.DEF_playSoundEffect((World)this.field_70170_p, (double)this.field_70165_t, (double)this.field_70163_u, (double)this.field_70161_v, (String)"random.click", (float)1.0f, (float)1.0f);
        }
    }

    public void unmountEntityFromRack() {
        for (int sid = this.getSeatNum() - 1; sid >= 0; --sid) {
            MCH_EntitySeat seat = this.getSeat(sid);
            if (!(this.getSeatInfo(sid + 1) instanceof MCH_SeatRackInfo) || seat == null || seat.field_70153_n == null) continue;
            MCH_SeatRackInfo info = (MCH_SeatRackInfo)this.getSeatInfo(sid + 1);
            Entity entity = seat.field_70153_n;
            Vec3 pos = info.getEntryPos();
            if (entity instanceof MCH_EntityAircraft) {
                pos = pos.field_72449_c >= (double)this.getAcInfo().bbZ ? pos.func_72441_c(0.0, 0.0, 12.0) : pos.func_72441_c(0.0, 0.0, -12.0);
            }
            Vec3 v = MCH_Lib.RotVec3((double)pos.field_72450_a, (double)pos.field_72448_b, (double)pos.field_72449_c, (float)(-this.getRotYaw()), (float)(-this.getRotPitch()), (float)(-this.getRotRoll()));
            seat.field_70165_t = entity.field_70165_t = this.field_70165_t + v.field_72450_a;
            seat.field_70163_u = entity.field_70163_u = this.field_70163_u + v.field_72448_b;
            seat.field_70161_v = entity.field_70161_v = this.field_70161_v + v.field_72449_c;
            UnmountReserve ur = new UnmountReserve(this, entity, entity.field_70165_t, entity.field_70163_u, entity.field_70161_v);
            ur.cnt = 8;
            this.listUnmountReserve.add(ur);
            entity.func_70078_a(null);
            if (MCH_Lib.getBlockIdY((Entity)this, (int)3, (int)-20) > 0) {
                MCH_Lib.DbgLog((World)this.field_70170_p, (String)"MCH_EntityAircraft.unmountEntityFromRack:%d:%s", (Object[])new Object[]{sid, entity});
                break;
            }
            MCH_Lib.DbgLog((World)this.field_70170_p, (String)"MCH_EntityAircraft.unmountEntityFromRack:%d Parachute:%s", (Object[])new Object[]{sid, entity});
            this.dropEntityParachute(entity);
            break;
        }
    }

    public void dropEntityParachute(Entity entity) {
        entity.field_70159_w = this.field_70159_w;
        entity.field_70181_x = this.field_70181_x;
        entity.field_70179_y = this.field_70179_y;
        MCH_EntityParachute parachute = new MCH_EntityParachute(this.field_70170_p, entity.field_70165_t, entity.field_70163_u, entity.field_70161_v);
        parachute.field_70177_z = entity.field_70177_z;
        parachute.field_70159_w = entity.field_70159_w;
        parachute.field_70181_x = entity.field_70181_x;
        parachute.field_70179_y = entity.field_70179_y;
        parachute.field_70143_R = entity.field_70143_R;
        parachute.user = entity;
        parachute.setType(3);
        this.field_70170_p.func_72838_d((Entity)parachute);
    }

    public void rideRack() {
        if (this.field_70154_o != null) {
            return;
        }
        AxisAlignedBB bb = this.func_70046_E();
        List list = this.field_70170_p.func_72839_b((Entity)this, bb.func_72314_b(60.0, 60.0, 60.0));
        for (int i = 0; i < list.size(); ++i) {
            MCH_EntityAircraft ac;
            Entity entity = (Entity)list.get(i);
            if (!(entity instanceof MCH_EntityAircraft) || (ac = (MCH_EntityAircraft)entity).getAcInfo() == null) continue;
            for (int sid = 0; sid < ac.getSeatNum(); ++sid) {
                MCH_SeatInfo seatInfo = ac.getSeatInfo(1 + sid);
                if (!(seatInfo instanceof MCH_SeatRackInfo) || !ac.canRideSeatOrRack(1 + sid, entity)) continue;
                MCH_SeatRackInfo info = (MCH_SeatRackInfo)seatInfo;
                MCH_EntitySeat seat = ac.getSeat(sid);
                if (seat == null || seat.field_70153_n != null) continue;
                Vec3 v = ac.getTransformedPosition(info.getEntryPos());
                float r = info.range;
                if (!(this.field_70165_t >= v.field_72450_a - (double)r) || !(this.field_70165_t <= v.field_72450_a + (double)r) || !(this.field_70163_u >= v.field_72448_b - (double)r) || !(this.field_70163_u <= v.field_72448_b + (double)r) || !(this.field_70161_v >= v.field_72449_c - (double)r) || !(this.field_70161_v <= v.field_72449_c + (double)r) || !this.canRideAircraft(ac, sid, info)) continue;
                W_WorldFunc.DEF_playSoundEffect((World)this.field_70170_p, (double)this.field_70165_t, (double)this.field_70163_u, (double)this.field_70161_v, (String)"random.click", (float)1.0f, (float)1.0f);
                this.func_70078_a((Entity)seat);
                return;
            }
        }
    }

    public boolean canPutToRack() {
        for (int i = 0; i < this.getSeatNum(); ++i) {
            MCH_EntitySeat seat = this.getSeat(i);
            MCH_SeatInfo seatInfo = this.getSeatInfo(i + 1);
            if (seat == null || seat.field_70153_n != null || !(seatInfo instanceof MCH_SeatRackInfo)) continue;
            return true;
        }
        return false;
    }

    public boolean canDownFromRack() {
        for (int i = 0; i < this.getSeatNum(); ++i) {
            MCH_EntitySeat seat = this.getSeat(i);
            MCH_SeatInfo seatInfo = this.getSeatInfo(i + 1);
            if (seat == null || seat.field_70153_n == null || !(seatInfo instanceof MCH_SeatRackInfo)) continue;
            return true;
        }
        return false;
    }

    public void checkRideRack() {
        if (this.getCountOnUpdate() % 10 != 0) {
            return;
        }
        this.canRideRackStatus = false;
        if (this.field_70154_o != null) {
            return;
        }
        AxisAlignedBB bb = this.func_70046_E();
        List list = this.field_70170_p.func_72839_b((Entity)this, bb.func_72314_b(60.0, 60.0, 60.0));
        for (int i = 0; i < list.size(); ++i) {
            MCH_EntityAircraft ac;
            Entity entity = (Entity)list.get(i);
            if (!(entity instanceof MCH_EntityAircraft) || (ac = (MCH_EntityAircraft)entity).getAcInfo() == null) continue;
            for (int sid = 0; sid < ac.getSeatNum(); ++sid) {
                boolean rz;
                MCH_SeatInfo seatInfo = ac.getSeatInfo(1 + sid);
                if (!(seatInfo instanceof MCH_SeatRackInfo)) continue;
                MCH_SeatRackInfo info = (MCH_SeatRackInfo)seatInfo;
                MCH_EntitySeat seat = ac.getSeat(sid);
                if (seat == null || seat.field_70153_n != null) continue;
                Vec3 v = ac.getTransformedPosition(info.getEntryPos());
                float r = info.range;
                boolean rx = this.field_70165_t >= v.field_72450_a - (double)r && this.field_70165_t <= v.field_72450_a + (double)r;
                boolean ry = this.field_70163_u >= v.field_72448_b - (double)r && this.field_70163_u <= v.field_72448_b + (double)r;
                boolean bl = rz = this.field_70161_v >= v.field_72449_c - (double)r && this.field_70161_v <= v.field_72449_c + (double)r;
                if (!(this.field_70165_t >= v.field_72450_a - (double)r) || !(this.field_70165_t <= v.field_72450_a + (double)r) || !(this.field_70163_u >= v.field_72448_b - (double)r) || !(this.field_70163_u <= v.field_72448_b + (double)r) || !(this.field_70161_v >= v.field_72449_c - (double)r) || !(this.field_70161_v <= v.field_72449_c + (double)r) || !this.canRideAircraft(ac, sid, info)) continue;
                this.canRideRackStatus = true;
                return;
            }
        }
    }

    public boolean canRideRack() {
        return this.field_70154_o == null && this.canRideRackStatus;
    }

    public boolean canRideAircraft(MCH_EntityAircraft ac, int seatID, MCH_SeatRackInfo info) {
        if (this.getAcInfo() == null) {
            return false;
        }
        if (ac.field_70154_o != null) {
            return false;
        }
        if (this.field_70154_o != null) {
            return false;
        }
        boolean canRide = false;
        for (String string : info.names) {
            if (!string.equalsIgnoreCase(this.getAcInfo().name) && !string.equalsIgnoreCase(this.getAcInfo().getKindName())) continue;
            canRide = true;
            break;
        }
        if (!canRide) {
            for (MCH_AircraftInfo.RideRack rr : this.getAcInfo().rideRacks) {
                MCH_EntitySeat mCH_EntitySeat;
                int id = ac.getAcInfo().getNumSeat() - 1 + (rr.rackID - 1);
                if (id != seatID || !rr.name.equalsIgnoreCase(ac.getAcInfo().name) || (mCH_EntitySeat = ac.getSeat(ac.getAcInfo().getNumSeat() - 1 + rr.rackID - 1)) == null || mCH_EntitySeat.field_70153_n != null) continue;
                canRide = true;
                break;
            }
            if (!canRide) {
                return false;
            }
        }
        for (String string : this.getSeats()) {
            if (string == null || !(((MCH_EntitySeat)string).field_70153_n instanceof MCH_IEntityCanRideAircraft)) continue;
            return false;
        }
        return true;
    }

    public boolean isMountedEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        return this.isMountedEntity(W_Entity.getEntityId((Entity)entity));
    }

    public EntityPlayer getFirstMountPlayer() {
        if (this.getRiddenByEntity() instanceof EntityPlayer) {
            return (EntityPlayer)this.getRiddenByEntity();
        }
        for (MCH_EntitySeat seat : this.getSeats()) {
            if (seat == null || !(seat.field_70153_n instanceof EntityPlayer)) continue;
            return (EntityPlayer)seat.field_70153_n;
        }
        return null;
    }

    public boolean isMountedSameTeamEntity(EntityLivingBase player) {
        if (player == null || player.func_96124_cp() == null) {
            return false;
        }
        if (this.field_70153_n instanceof EntityLivingBase && player.func_142014_c((EntityLivingBase)this.field_70153_n)) {
            return true;
        }
        for (MCH_EntitySeat seat : this.getSeats()) {
            if (seat == null || !(seat.field_70153_n instanceof EntityLivingBase) || !player.func_142014_c((EntityLivingBase)seat.field_70153_n)) continue;
            return true;
        }
        return false;
    }

    public boolean isMountedOtherTeamEntity(EntityLivingBase player) {
        if (player == null) {
            return false;
        }
        EntityLivingBase target = null;
        if (this.field_70153_n instanceof EntityLivingBase) {
            target = (EntityLivingBase)this.field_70153_n;
            if (player.func_96124_cp() != null && target.func_96124_cp() != null && !player.func_142014_c(target)) {
                return true;
            }
        }
        for (MCH_EntitySeat seat : this.getSeats()) {
            if (seat == null || !(seat.field_70153_n instanceof EntityLivingBase)) continue;
            target = (EntityLivingBase)seat.field_70153_n;
            if (player.func_96124_cp() == null || target.func_96124_cp() == null || player.func_142014_c(target)) continue;
            return true;
        }
        return false;
    }

    public boolean isMountedEntity(int entityId) {
        if (W_Entity.getEntityId((Entity)this.field_70153_n) == entityId) {
            return true;
        }
        for (MCH_EntitySeat seat : this.getSeats()) {
            if (seat == null || seat.field_70153_n == null || W_Entity.getEntityId((Entity)seat.field_70153_n) != entityId) continue;
            return true;
        }
        return false;
    }

    public void onInteractFirst(EntityPlayer player) {
    }

    public boolean checkTeam(EntityPlayer player) {
        for (int i = 0; i < 1 + this.getSeatNum(); ++i) {
            EntityPlayer riddenPlayer;
            Entity entity = this.getEntityBySeatId(i);
            if (!(entity instanceof EntityPlayer) || (riddenPlayer = (EntityPlayer)entity).func_96124_cp() == null || riddenPlayer.func_142014_c((EntityLivingBase)player)) continue;
            return false;
        }
        return true;
    }

    public boolean interactFirst(EntityPlayer player, boolean ss) {
        this.switchSeat = ss;
        boolean ret = this.func_130002_c(player);
        this.switchSeat = false;
        return ret;
    }

    public boolean func_130002_c(EntityPlayer player) {
        if (this.isDestroyed()) {
            return false;
        }
        if (this.getAcInfo() == null) {
            return false;
        }
        if (!this.checkTeam(player)) {
            return false;
        }
        ItemStack itemStack = player.func_71045_bC();
        if (itemStack != null && itemStack.func_77973_b() instanceof MCH_ItemWrench) {
            if (!this.field_70170_p.field_72995_K && player.func_70093_af()) {
                this.switchNextTextureName();
            }
            return false;
        }
        if (player.func_70093_af()) {
            super.openInventory(player);
            return false;
        }
        if (!this.getAcInfo().canRide) {
            return false;
        }
        if (this.field_70153_n != null || this.isUAV()) {
            return this.interactFirstSeat(player);
        }
        if (player.field_70154_o instanceof MCH_EntitySeat) {
            return false;
        }
        if (!this.canRideSeatOrRack(0, (Entity)player)) {
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
        this.field_70153_n = null;
        this.lastRiddenByEntity = null;
        this.initRadar();
        if (!this.field_70170_p.field_72995_K) {
            player.func_70078_a((Entity)this);
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
            MCH_Achievement.addStat((Entity)this.field_70153_n, (Achievement)MCH_Achievement.rideValkyries, (int)1);
        }
        this.onInteractFirst(player);
        return true;
    }

    public boolean canRideSeatOrRack(int seatId, Entity entity) {
        if (this.getAcInfo() == null) {
            return false;
        }
        for (Integer[] a : this.getAcInfo().exclusionSeatList) {
            if (!Arrays.asList(a).contains(seatId)) continue;
            Integer[] arr$ = a;
            int len$ = arr$.length;
            for (int i$ = 0; i$ < len$; ++i$) {
                int id = arr$[i$];
                if (this.getEntityBySeatId(id) == null) continue;
                return false;
            }
        }
        return true;
    }

    public void updateClientSettings(int seatId) {
        this.cs_dismountAll = MCH_Config.DismountAll.prmBool;
        this.cs_heliAutoThrottleDown = MCH_Config.AutoThrottleDownHeli.prmBool;
        this.cs_planeAutoThrottleDown = MCH_Config.AutoThrottleDownPlane.prmBool;
        this.cs_tankAutoThrottleDown = MCH_Config.AutoThrottleDownTank.prmBool;
        this.camera.setShaderSupport(seatId, Boolean.valueOf(W_EntityRenderer.isShaderSupport()));
        MCH_PacketNotifyClientSetting.send();
    }

    public boolean canLockEntity(Entity entity) {
        return !this.isMountedEntity(entity);
    }

    public void switchNextSeat(Entity entity) {
        if (entity == null) {
            return;
        }
        if (this.seats == null || this.seats.length <= 0) {
            return;
        }
        if (!this.isMountedEntity(entity)) {
            return;
        }
        boolean isFound = false;
        int sid = 1;
        for (MCH_EntitySeat seat : this.seats) {
            if (seat == null) continue;
            if (this.getSeatInfo(sid) instanceof MCH_SeatRackInfo) break;
            if (W_Entity.isEqual((Entity)seat.field_70153_n, (Entity)entity)) {
                isFound = true;
            } else if (isFound && seat.field_70153_n == null) {
                entity.func_70078_a((Entity)seat);
                return;
            }
            ++sid;
        }
        sid = 1;
        for (MCH_EntitySeat seat : this.seats) {
            if (seat != null && seat.field_70153_n == null) {
                if (this.getSeatInfo(sid) instanceof MCH_SeatRackInfo) break;
                entity.func_70078_a((Entity)seat);
                this.onMountPlayerSeat(seat, entity);
                return;
            }
            ++sid;
        }
    }

    public void switchPrevSeat(Entity entity) {
        MCH_EntitySeat seat;
        int i;
        if (entity == null) {
            return;
        }
        if (this.seats == null || this.seats.length <= 0) {
            return;
        }
        if (!this.isMountedEntity(entity)) {
            return;
        }
        boolean isFound = false;
        for (i = this.seats.length - 1; i >= 0; --i) {
            seat = this.seats[i];
            if (seat == null) continue;
            if (W_Entity.isEqual((Entity)seat.field_70153_n, (Entity)entity)) {
                isFound = true;
                continue;
            }
            if (!isFound || seat.field_70153_n != null) continue;
            entity.func_70078_a((Entity)seat);
            return;
        }
        for (i = this.seats.length - 1; i >= 0; --i) {
            seat = this.seats[i];
            if (this.getSeatInfo(i + 1) instanceof MCH_SeatRackInfo || seat == null || seat.field_70153_n != null) continue;
            entity.func_70078_a((Entity)seat);
            return;
        }
    }

    public Entity[] func_70021_al() {
        return this.partEntities;
    }

    public float getSoundVolume() {
        return 1.0f;
    }

    public float getSoundPitch() {
        return 1.0f;
    }

    public abstract String getDefaultSoundName();

    public String getSoundName() {
        if (this.getAcInfo() == null) {
            return "";
        }
        return !this.getAcInfo().soundMove.isEmpty() ? this.getAcInfo().soundMove : this.getDefaultSoundName();
    }

    public boolean isSkipNormalRender() {
        return this.field_70154_o instanceof MCH_EntitySeat;
    }

    public boolean isRenderBullet(Entity entity, Entity rider) {
        return !this.isCameraView(rider) || !W_Entity.isEqual((Entity)this.getTVMissile(), (Entity)entity) || !W_Entity.isEqual((Entity)this.getTVMissile().shootingEntity, (Entity)rider);
    }

    public boolean isCameraView(Entity entity) {
        return this.getIsGunnerMode(entity) || this.isUAV();
    }

    public void updateCamera(double x, double y, double z) {
        if (!this.field_70170_p.field_72995_K) {
            return;
        }
        if (this.getTVMissile() != null) {
            this.camera.setPosition(this.TVmissile.field_70165_t, this.TVmissile.field_70163_u, this.TVmissile.field_70161_v);
            this.camera.setCameraZoom(1.0f);
            this.TVmissile.isSpawnParticle = !this.isMissileCameraMode(this.TVmissile.shootingEntity);
        } else {
            this.setTVMissile(null);
            MCH_AircraftInfo.CameraPosition cpi = this.getCameraPosInfo();
            Vec3 cp = cpi != null ? cpi.pos : Vec3.func_72443_a((double)0.0, (double)0.0, (double)0.0);
            Vec3 v = MCH_Lib.RotVec3((Vec3)cp, (float)(-this.getRotYaw()), (float)(-this.getRotPitch()), (float)(-this.getRotRoll()));
            this.camera.setPosition(x + v.field_72450_a, y + v.field_72448_b, z + v.field_72449_c);
        }
    }

    public void updateCameraRotate(float yaw, float pitch) {
        this.camera.prevRotationYaw = this.camera.rotationYaw;
        this.camera.prevRotationPitch = this.camera.rotationPitch;
        this.camera.rotationYaw = yaw;
        this.camera.rotationPitch = pitch;
    }

    public void updatePartCameraRotate() {
        if (this.field_70170_p.field_72995_K) {
            Entity e = this.getEntityBySeatId(1);
            if (e == null) {
                e = this.getRiddenByEntity();
            }
            if (e != null) {
                this.camera.partRotationYaw = e.field_70177_z;
                float pitch = e.field_70125_A;
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
        return this.TVmissile != null && !this.TVmissile.field_70128_L ? this.TVmissile : null;
    }

    public MCH_WeaponSet[] createWeapon(int seat_num) {
        this.currentWeaponID = new int[seat_num];
        for (int i = 0; i < this.currentWeaponID.length; ++i) {
            this.currentWeaponID[i] = -1;
        }
        if (this.getAcInfo() == null || this.getAcInfo().weaponSetList.size() <= 0 || seat_num <= 0) {
            return new MCH_WeaponSet[]{this.dummyWeapon};
        }
        MCH_WeaponSet[] weaponSetArray = new MCH_WeaponSet[this.getAcInfo().weaponSetList.size()];
        for (int i = 0; i < this.getAcInfo().weaponSetList.size(); ++i) {
            MCH_AircraftInfo.WeaponSet ws = (MCH_AircraftInfo.WeaponSet)this.getAcInfo().weaponSetList.get(i);
            MCH_WeaponBase[] wb = new MCH_WeaponBase[ws.weapons.size()];
            for (int j = 0; j < ws.weapons.size(); ++j) {
                wb[j] = MCH_WeaponCreator.createWeapon((World)this.field_70170_p, (String)ws.type, (Vec3)((MCH_AircraftInfo.Weapon)ws.weapons.get((int)j)).pos, (float)((MCH_AircraftInfo.Weapon)ws.weapons.get((int)j)).yaw, (float)((MCH_AircraftInfo.Weapon)ws.weapons.get((int)j)).pitch, (MCH_IEntityLockChecker)this, (boolean)((MCH_AircraftInfo.Weapon)ws.weapons.get((int)j)).turret);
                wb[j].aircraft = this;
            }
            if (wb.length <= 0 || wb[0] == null) continue;
            float defYaw = ((MCH_AircraftInfo.Weapon)ws.weapons.get((int)0)).defaultYaw;
            weaponSetArray[i] = new MCH_WeaponSet(wb);
            weaponSetArray[i].prevRotationYaw = defYaw;
            weaponSetArray[i].rotationYaw = defYaw;
            weaponSetArray[i].defaultRotationYaw = defYaw;
        }
        return weaponSetArray;
    }

    public void switchWeapon(Entity entity, int id) {
        int sid = this.getSeatIdByEntity(entity);
        if (!this.isValidSeatID(sid)) {
            return;
        }
        int beforeWeaponID = this.currentWeaponID[sid];
        if (this.getWeaponNum() <= 0 || this.currentWeaponID.length <= 0) {
            return;
        }
        if (id < 0) {
            this.currentWeaponID[sid] = -1;
        }
        if (id >= this.getWeaponNum()) {
            id = this.getWeaponNum() - 1;
        }
        MCH_Lib.DbgLog((World)this.field_70170_p, (String)("switchWeapon:" + W_Entity.getEntityId((Entity)entity) + " -> " + id), (Object[])new Object[0]);
        this.getCurrentWeapon(entity).reload();
        this.currentWeaponID[sid] = id;
        MCH_WeaponSet ws = this.getCurrentWeapon(entity);
        ws.onSwitchWeapon(this.field_70170_p.field_72995_K, this.isInfinityAmmo(entity));
        if (!this.field_70170_p.field_72995_K) {
            MCH_PacketNotifyWeaponID.send((Entity)this, (int)sid, (int)id, (int)ws.getAmmoNum(), (int)ws.getRestAllAmmoNum());
        }
    }

    public void updateWeaponID(int sid, int id) {
        if (sid < 0 || sid >= this.currentWeaponID.length) {
            return;
        }
        if (this.getWeaponNum() <= 0 || this.currentWeaponID.length <= 0) {
            return;
        }
        if (id < 0) {
            this.currentWeaponID[sid] = -1;
        }
        if (id >= this.getWeaponNum()) {
            id = this.getWeaponNum() - 1;
        }
        MCH_Lib.DbgLog((World)this.field_70170_p, (String)("switchWeapon:seatID=" + sid + ", WeaponID=" + id), (Object[])new Object[0]);
        this.currentWeaponID[sid] = id;
    }

    public void updateWeaponRestAmmo(int id, int num) {
        if (id < this.getWeaponNum()) {
            this.getWeapon(id).setRestAllAmmoNum(num);
        }
    }

    public MCH_WeaponSet getWeaponByName(String name) {
        for (MCH_WeaponSet ws : this.weapons) {
            if (!ws.isEqual(name)) continue;
            return ws;
        }
        return null;
    }

    public int getWeaponIdByName(String name) {
        int id = 0;
        for (MCH_WeaponSet ws : this.weapons) {
            if (ws.isEqual(name)) {
                return id;
            }
            ++id;
        }
        return -1;
    }

    public void reloadAllWeapon() {
        for (int i = 0; i < this.getWeaponNum(); ++i) {
            this.getWeapon(i).reloadMag();
        }
    }

    public MCH_WeaponSet getFirstSeatWeapon() {
        if (this.currentWeaponID != null && this.currentWeaponID.length > 0 && this.currentWeaponID[0] >= 0) {
            return this.getWeapon(this.currentWeaponID[0]);
        }
        return this.getWeapon(0);
    }

    public void initCurrentWeapon(Entity entity) {
        int sid = this.getSeatIdByEntity(entity);
        MCH_Lib.DbgLog((World)this.field_70170_p, (String)("initCurrentWeapon:" + W_Entity.getEntityId((Entity)entity) + ":%d"), (Object[])new Object[]{sid});
        if (sid < 0 || sid >= this.currentWeaponID.length) {
            return;
        }
        this.currentWeaponID[sid] = -1;
        if (entity instanceof EntityPlayer) {
            this.currentWeaponID[sid] = this.getNextWeaponID(entity, 1);
            this.switchWeapon(entity, this.getCurrentWeaponID(entity));
            if (this.field_70170_p.field_72995_K) {
                MCH_PacketIndNotifyAmmoNum.send((MCH_EntityAircraft)this, (int)-1);
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
        if (id < 0 || this.weapons.length <= 0 || id >= this.weapons.length) {
            return this.dummyWeapon;
        }
        return this.weapons[id];
    }

    public int getWeaponIDBySeatID(int sid) {
        if (sid < 0 || sid >= this.currentWeaponID.length) {
            return -1;
        }
        return this.currentWeaponID[sid];
    }

    public double getLandInDistance(Entity user) {
        if (this.lastCalcLandInDistanceCount != (double)this.getCountOnUpdate() && this.getCountOnUpdate() % 5 == 0) {
            MCH_WeaponSet currentWs;
            this.lastCalcLandInDistanceCount = this.getCountOnUpdate();
            MCH_WeaponParam prm = new MCH_WeaponParam();
            prm.setPosition(this.field_70165_t, this.field_70163_u, this.field_70161_v);
            prm.entity = this;
            prm.user = user;
            prm.isInfinity = this.isInfinityAmmo(prm.user);
            if (prm.user != null && (currentWs = this.getCurrentWeapon(prm.user)) != null) {
                int sid = this.getSeatIdByEntity(prm.user);
                if (this.getAcInfo().getWeaponSetById(sid) != null) {
                    prm.isTurret = ((MCH_AircraftInfo.Weapon)this.getAcInfo().getWeaponSetById((int)sid).weapons.get((int)0)).turret;
                }
                this.lastLandInDistance = currentWs.getLandInDistance(prm);
            }
        }
        return this.lastLandInDistance;
    }

    public boolean useCurrentWeapon(Entity user) {
        MCH_WeaponParam prm = new MCH_WeaponParam();
        prm.setPosition(this.field_70165_t, this.field_70163_u, this.field_70161_v);
        prm.entity = this;
        prm.user = user;
        return this.useCurrentWeapon(prm);
    }

    public boolean useCurrentWeapon(MCH_WeaponParam prm) {
        MCH_WeaponSet currentWs;
        prm.isInfinity = this.isInfinityAmmo(prm.user);
        if (prm.user != null && (currentWs = this.getCurrentWeapon(prm.user)) != null && currentWs.canUse()) {
            int sid = this.getSeatIdByEntity(prm.user);
            if (this.getAcInfo().getWeaponSetById(sid) != null) {
                prm.isTurret = ((MCH_AircraftInfo.Weapon)this.getAcInfo().getWeaponSetById((int)sid).weapons.get((int)0)).turret;
            }
            int lastUsedIndex = currentWs.getCurrentWeaponIndex();
            if (currentWs.use(prm)) {
                for (MCH_WeaponSet ws : this.weapons) {
                    if (ws == currentWs || ws.getInfo().group.isEmpty() || !ws.getInfo().group.equals(currentWs.getInfo().group)) continue;
                    ws.waitAndReloadByOther(prm.reload);
                }
                if (!this.field_70170_p.field_72995_K) {
                    int shift = 0;
                    for (MCH_WeaponSet ws : this.weapons) {
                        if (ws == currentWs) break;
                        shift += ws.getWeaponNum();
                    }
                    this.useWeaponStat |= (shift += lastUsedIndex) < 32 ? 1 << shift : 0;
                }
                return true;
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
        int i;
        if (this.getAcInfo() == null) {
            return -1;
        }
        int sid = this.getSeatIdByEntity(entity);
        if (sid < 0) {
            return -1;
        }
        int id = this.getCurrentWeaponID(entity);
        for (i = 0; i < this.getWeaponNum(); ++i) {
            MCH_WeaponInfo wi;
            int wpsid;
            id = step >= 0 ? (id + 1) % this.getWeaponNum() : (id > 0 ? id - 1 : this.getWeaponNum() - 1);
            MCH_AircraftInfo.Weapon w = this.getAcInfo().getWeaponById(id);
            if (w != null && (wpsid = this.getWeaponSeatID(wi = this.getWeaponInfoById(id), w)) < this.getSeatNum() + 1 + 1 && (wpsid == sid || sid == 0 && w.canUsePilot && !(this.getEntityBySeatId(wpsid) instanceof EntityPlayer))) break;
        }
        if (i >= this.getWeaponNum()) {
            return -1;
        }
        MCH_Lib.DbgLog((World)this.field_70170_p, (String)"getNextWeaponID:%d:->%d", (Object[])new Object[]{W_Entity.getEntityId((Entity)entity), id});
        return id;
    }

    public int getWeaponSeatID(MCH_WeaponInfo wi, MCH_AircraftInfo.Weapon w) {
        if (wi != null && (wi.target & 0xC3) == 0 && wi.type.isEmpty() && (MCH_MOD.proxy.isSinglePlayer() || MCH_Config.TestMode.prmBool)) {
            return 1000;
        }
        return w.seatID;
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
            if (i >= 32) break;
            for (int wi = 0; wi < w.getWeaponNum() && i < 32; ++i, ++wi) {
                stat |= w.isUsed(wi) ? 1 << i : 0;
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
            if (ws == checkWs) break;
            shift += ws.getWeaponNum();
        }
        return (shift += index) < 32 ? (this.useWeaponStat & 1 << shift) != 0 : false;
    }

    public void updateWeapons() {
        if (this.getAcInfo() == null) {
            return;
        }
        if (this.getAcInfo().getWeaponNum() <= 0) {
            return;
        }
        int prevUseWeaponStat = this.useWeaponStat;
        if (!this.field_70170_p.field_72995_K) {
            this.useWeaponStat |= this.getUsedWeaponStat();
            this.func_70096_w().func_75692_b(24, (Object)new Integer(this.useWeaponStat));
            this.useWeaponStat = 0;
        } else {
            this.useWeaponStat = this.func_70096_w().func_75679_c(24);
        }
        float yaw = MathHelper.func_76142_g((float)this.getRotYaw());
        float pitch = MathHelper.func_76142_g((float)this.getRotPitch());
        int id = 0;
        for (int wid = 0; wid < this.weapons.length; ++wid) {
            MCH_WeaponSet w = this.weapons[wid];
            boolean isLongDelay = false;
            if (w.getFirstWeapon() != null) {
                isLongDelay = w.isLongDelayWeapon();
            }
            boolean isSelected = false;
            for (int swid : this.currentWeaponID) {
                if (swid != wid) continue;
                isSelected = true;
                break;
            }
            boolean isWpnUsed = false;
            for (int index = 0; index < w.getWeaponNum(); ++index) {
                float recoil;
                boolean isUsed;
                boolean isPrevUsed = id < 32 && (prevUseWeaponStat & 1 << id) != 0;
                boolean bl = isUsed = id < 32 && (this.useWeaponStat & 1 << id) != 0;
                if (isLongDelay && isPrevUsed && isUsed) {
                    isUsed = false;
                }
                isWpnUsed |= isUsed;
                if (!isPrevUsed && isUsed && (recoil = w.getInfo().recoil) > 0.0f) {
                    this.recoilCount = 30;
                    this.recoilValue = recoil;
                    this.recoilYaw = w.rotationYaw;
                }
                if (this.field_70170_p.field_72995_K && isUsed) {
                    Vec3 wrv = MCH_Lib.RotVec3((double)0.0, (double)0.0, (double)-1.0, (float)(-w.rotationYaw - yaw), (float)(-w.rotationPitch));
                    Vec3 spv = w.getCurrentWeapon().getShotPos((Entity)this);
                    this.spawnParticleMuzzleFlash(this.field_70170_p, w.getInfo(), this.field_70165_t + spv.field_72450_a, this.field_70163_u + spv.field_72448_b, this.field_70161_v + spv.field_72449_c, wrv);
                }
                w.updateWeapon((Entity)this, isUsed, index);
                ++id;
            }
            w.update((Entity)this, isSelected, isWpnUsed);
            MCH_AircraftInfo.Weapon wi = this.getAcInfo().getWeaponById(wid);
            if (wi == null || this.isDestroyed()) continue;
            Entity entity = this.getEntityBySeatId(this.getWeaponSeatID(this.getWeaponInfoById(wid), wi));
            if (wi.canUsePilot && !(entity instanceof EntityPlayer)) {
                entity = this.getEntityBySeatId(0);
            }
            if (entity instanceof EntityPlayer) {
                if ((int)wi.minYaw != 0 || (int)wi.maxYaw != 0) {
                    float ty = wi.turret ? MathHelper.func_76142_g((float)this.getLastRiderYaw()) - yaw : 0.0f;
                    float ey = MathHelper.func_76142_g((float)(entity.field_70177_z - yaw - wi.defaultYaw - ty));
                    if (Math.abs((int)wi.minYaw) < 360 && Math.abs((int)wi.maxYaw) < 360) {
                        float wy;
                        float targetYaw = MCH_Lib.RNG((float)ey, (float)wi.minYaw, (float)wi.maxYaw);
                        if (targetYaw < (wy = w.rotationYaw - wi.defaultYaw - ty)) {
                            wy = wy - targetYaw > 15.0f ? (wy -= 15.0f) : targetYaw;
                        } else if (targetYaw > wy) {
                            wy = targetYaw - wy > 15.0f ? (wy += 15.0f) : targetYaw;
                        }
                        w.rotationYaw = wy + wi.defaultYaw + ty;
                    } else {
                        w.rotationYaw = ey + ty;
                    }
                }
                float ep = MathHelper.func_76142_g((float)(entity.field_70125_A - pitch));
                w.rotationPitch = MCH_Lib.RNG((float)ep, (float)wi.minPitch, (float)wi.maxPitch);
                w.rotationTurretYaw = 0.0f;
                continue;
            }
            w.rotationTurretYaw = this.getLastRiderYaw() - this.getRotYaw();
            if (this.getTowedChainEntity() == null && this.field_70154_o == null) continue;
            w.rotationYaw = 0.0f;
        }
        this.updateWeaponBay();
        if (this.hitStatus > 0) {
            --this.hitStatus;
        }
    }

    public void updateWeaponsRotation() {
        if (this.getAcInfo() == null) {
            return;
        }
        if (this.getAcInfo().getWeaponNum() <= 0) {
            return;
        }
        if (this.isDestroyed()) {
            return;
        }
        float yaw = MathHelper.func_76142_g((float)this.getRotYaw());
        float pitch = MathHelper.func_76142_g((float)this.getRotPitch());
        for (int wid = 0; wid < this.weapons.length; ++wid) {
            MCH_WeaponSet w = this.weapons[wid];
            MCH_AircraftInfo.Weapon wi = this.getAcInfo().getWeaponById(wid);
            if (wi != null) {
                Entity entity = this.getEntityBySeatId(this.getWeaponSeatID(this.getWeaponInfoById(wid), wi));
                if (wi.canUsePilot && !(entity instanceof EntityPlayer)) {
                    entity = this.getEntityBySeatId(0);
                }
                if (entity instanceof EntityPlayer) {
                    if ((int)wi.minYaw != 0 || (int)wi.maxYaw != 0) {
                        float ty = wi.turret ? MathHelper.func_76142_g((float)this.getLastRiderYaw()) - yaw : 0.0f;
                        float ey = MathHelper.func_76142_g((float)(entity.field_70177_z - yaw - wi.defaultYaw - ty));
                        if (Math.abs((int)wi.minYaw) < 360 && Math.abs((int)wi.maxYaw) < 360) {
                            float wy;
                            float targetYaw = MCH_Lib.RNG((float)ey, (float)wi.minYaw, (float)wi.maxYaw);
                            if (targetYaw < (wy = w.rotationYaw - wi.defaultYaw - ty)) {
                                wy = wy - targetYaw > 15.0f ? (wy -= 15.0f) : targetYaw;
                            } else if (targetYaw > wy) {
                                wy = targetYaw - wy > 15.0f ? (wy += 15.0f) : targetYaw;
                            }
                            w.rotationYaw = wy + wi.defaultYaw + ty;
                        } else {
                            w.rotationYaw = ey + ty;
                        }
                    }
                    float ep = MathHelper.func_76142_g((float)(entity.field_70125_A - pitch));
                    w.rotationPitch = MCH_Lib.RNG((float)ep, (float)wi.minPitch, (float)wi.maxPitch);
                    w.rotationTurretYaw = 0.0f;
                } else {
                    w.rotationTurretYaw = this.getLastRiderYaw() - this.getRotYaw();
                }
            }
            w.prevRotationYaw = w.rotationYaw;
        }
    }

    private void spawnParticleMuzzleFlash(World w, MCH_WeaponInfo wi, double px, double py, double pz, Vec3 wrv) {
        if (wi.listMuzzleFlashSmoke != null) {
            for (MCH_WeaponInfo.MuzzleFlash mf : wi.listMuzzleFlashSmoke) {
                double x = px + -wrv.field_72450_a * (double)mf.dist;
                double y = py + -wrv.field_72448_b * (double)mf.dist;
                double z = pz + -wrv.field_72449_c * (double)mf.dist;
                MCH_ParticleParam p = new MCH_ParticleParam(w, "smoke", px, py, pz);
                p.size = mf.size;
                for (int i = 0; i < mf.num; ++i) {
                    p.a = mf.a * 0.9f + w.field_73012_v.nextFloat() * 0.1f;
                    float color = w.field_73012_v.nextFloat() * 0.1f;
                    p.r = color + mf.r * 0.9f;
                    p.g = color + mf.g * 0.9f;
                    p.b = color + mf.b * 0.9f;
                    p.age = (int)((double)mf.age + 0.1 * (double)mf.age * (double)w.field_73012_v.nextFloat());
                    p.posX = x + (w.field_73012_v.nextDouble() - 0.5) * (double)mf.range;
                    p.posY = y + (w.field_73012_v.nextDouble() - 0.5) * (double)mf.range;
                    p.posZ = z + (w.field_73012_v.nextDouble() - 0.5) * (double)mf.range;
                    p.motionX = w.field_73012_v.nextDouble() * (p.posX < x ? -0.2 : 0.2);
                    p.motionY = w.field_73012_v.nextDouble() * (p.posY < y ? -0.03 : 0.03);
                    p.motionZ = w.field_73012_v.nextDouble() * (p.posZ < z ? -0.2 : 0.2);
                    MCH_ParticlesUtil.spawnParticle((MCH_ParticleParam)p);
                }
            }
        }
        if (wi.listMuzzleFlash != null) {
            for (MCH_WeaponInfo.MuzzleFlash mf : wi.listMuzzleFlash) {
                float color = this.field_70146_Z.nextFloat() * 0.1f + 0.9f;
                MCH_ParticlesUtil.spawnParticleExplode((World)this.field_70170_p, (double)(px + -wrv.field_72450_a * (double)mf.dist), (double)(py + -wrv.field_72448_b * (double)mf.dist), (double)(pz + -wrv.field_72449_c * (double)mf.dist), (float)mf.size, (float)(color * mf.r), (float)(color * mf.g), (float)(color * mf.b), (float)mf.a, (int)(mf.age + w.field_73012_v.nextInt(3)));
            }
        }
    }

    private void updateWeaponBay() {
        for (int i = 0; i < this.weaponBays.length; ++i) {
            WeaponBay wb = this.weaponBays[i];
            MCH_AircraftInfo.WeaponBay info = (MCH_AircraftInfo.WeaponBay)this.getAcInfo().partWeaponBay.get(i);
            boolean isSelected = false;
            Integer[] arr$ = info.weaponIds;
            int len$ = arr$.length;
            for (int i$ = 0; i$ < len$; ++i$) {
                int wid = arr$[i$];
                for (int sid = 0; sid < this.currentWeaponID.length; ++sid) {
                    if (wid != this.currentWeaponID[sid] || this.getEntityBySeatId(sid) == null) continue;
                    isSelected = true;
                }
            }
            wb.prevRot = wb.rot;
            if (isSelected) {
                if (wb.rot < 90.0f) {
                    wb.rot += 3.0f;
                }
                if (!(wb.rot >= 90.0f)) continue;
                wb.rot = 90.0f;
                continue;
            }
            if (wb.rot > 0.0f) {
                wb.rot -= 3.0f;
            }
            if (!(wb.rot <= 0.0f)) continue;
            wb.rot = 0.0f;
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
        this.field_70177_z = yaw;
        this.field_70126_B = yaw;
        this.lastRiderYaw = yaw;
        this.lastSearchLightYaw = yaw;
        for (MCH_WeaponSet w : this.weapons) {
            w.rotationYaw = w.defaultRotationYaw;
            w.rotationPitch = 0.0f;
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
            this.field_70138_W = info.stepHeight;
        }
    }

    public MCH_BoundingBox[] createExtraBoundingBox() {
        MCH_BoundingBox[] ar = new MCH_BoundingBox[this.getAcInfo().extraBoundingBox.size()];
        int i = 0;
        for (MCH_BoundingBox bb : this.getAcInfo().extraBoundingBox) {
            ar[i] = bb.copy();
            ++i;
        }
        return ar;
    }

    public Entity[] createParts() {
        Entity[] list = new Entity[]{this.partEntities[0]};
        return list;
    }

    public void updateUAV() {
        double udz;
        double udx;
        if (!this.isUAV()) {
            return;
        }
        if (this.field_70170_p.field_72995_K) {
            int eid = this.func_70096_w().func_75679_c(22);
            if (eid > 0) {
                Entity uavEntity;
                if (this.uavStation == null && (uavEntity = this.field_70170_p.func_73045_a(eid)) instanceof MCH_EntityUavStation) {
                    this.uavStation = (MCH_EntityUavStation)uavEntity;
                    this.uavStation.setControlAircract(this);
                }
            } else if (this.uavStation != null) {
                this.uavStation.setControlAircract(null);
                this.uavStation = null;
            }
        } else if (this.uavStation != null && (udx = this.field_70165_t - this.uavStation.field_70165_t) * udx + (udz = this.field_70161_v - this.uavStation.field_70161_v) * udz > 15129.0) {
            this.uavStation.setControlAircract(null);
            this.setUavStation(null);
            this.attackEntityFrom(DamageSource.field_76380_i, this.getMaxHP() + 10);
        }
        if (this.uavStation != null && this.uavStation.field_70128_L) {
            this.uavStation = null;
        }
    }

    public void switchGunnerMode(boolean mode) {
        boolean debug_bk_mode = this.isGunnerMode;
        Entity pilot = this.getEntityBySeatId(0);
        if (!mode || this.canSwitchGunnerMode()) {
            if (this.isGunnerMode && !mode) {
                this.setCurrentThrottle(this.beforeHoverThrottle);
                this.isGunnerMode = false;
                this.camera.setCameraZoom(1.0f);
                this.getCurrentWeapon(pilot).onSwitchWeapon(this.field_70170_p.field_72995_K, this.isInfinityAmmo(pilot));
            } else if (!this.isGunnerMode && mode) {
                this.beforeHoverThrottle = this.getCurrentThrottle();
                this.isGunnerMode = true;
                this.camera.setCameraZoom(1.0f);
                this.getCurrentWeapon(pilot).onSwitchWeapon(this.field_70170_p.field_72995_K, this.isInfinityAmmo(pilot));
            }
        }
        MCH_Lib.DbgLog((World)this.field_70170_p, (String)"switchGunnerMode %s->%s", (Object[])new Object[]{debug_bk_mode ? "ON" : "OFF", mode ? "ON" : "OFF"});
    }

    public boolean canSwitchGunnerMode() {
        if (this.getAcInfo() == null || !this.getAcInfo().isEnableGunnerMode) {
            return false;
        }
        if (!this.isCanopyClose()) {
            return false;
        }
        if (!this.getAcInfo().isEnableConcurrentGunnerMode && this.getEntityBySeatId(1) instanceof EntityPlayer) {
            return false;
        }
        return !this.isHoveringMode();
    }

    public boolean canSwitchGunnerModeOtherSeat(EntityPlayer player) {
        MCH_SeatInfo info;
        int sid = this.getSeatIdByEntity((Entity)player);
        if (sid > 0 && (info = this.getSeatInfo(sid)) != null) {
            return info.gunner && info.switchgunner;
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
            if (this.field_70153_n != null) {
                this.field_70153_n.field_70125_A = 0.0f;
                this.field_70153_n.field_70127_C = 0.0f;
            }
        }
    }

    public boolean canSwitchHoveringMode() {
        if (this.getAcInfo() == null) {
            return false;
        }
        return !this.isGunnerMode;
    }

    public boolean isHovering() {
        return this.isGunnerMode || this.isHoveringMode();
    }

    public boolean getIsGunnerMode(Entity entity) {
        if (this.getAcInfo() == null) {
            return false;
        }
        int id = this.getSeatIdByEntity(entity);
        if (id < 0) {
            return false;
        }
        if (id == 0 && this.getAcInfo().isEnableGunnerMode) {
            return this.isGunnerMode;
        }
        MCH_SeatInfo[] st = this.getSeatsInfo();
        if (id < st.length && st[id].gunner) {
            if (this.field_70170_p.field_72995_K && st[id].switchgunner) {
                return this.isGunnerModeOtherSeat;
            }
            return true;
        }
        return false;
    }

    public boolean isPilot(Entity player) {
        return W_Entity.isEqual((Entity)this.getRiddenByEntity(), (Entity)player);
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
        MCH_SeatInfo seatInfo = this.getSeatInfo((Entity)player);
        return seatInfo != null && seatInfo.fixRot && this.getIsGunnerMode((Entity)player);
    }

    public boolean isGunnerLookMode(EntityPlayer player) {
        if (this.isPilot((Entity)player)) {
            return false;
        }
        return this.isGunnerFreeLookMode;
    }

    public void switchGunnerFreeLookMode(boolean b) {
        this.isGunnerFreeLookMode = b;
    }

    public void switchGunnerFreeLookMode() {
        this.switchGunnerFreeLookMode(!this.isGunnerFreeLookMode);
    }

    public void updateParts(int stat) {
        MCH_Parts[] parts;
        if (this.isDestroyed()) {
            return;
        }
        for (MCH_Parts p : parts = new MCH_Parts[]{this.partHatch, this.partCanopy, this.partLandingGear}) {
            if (p == null) continue;
            p.updateStatusClient(stat);
            p.update();
        }
        if (!this.isDestroyed() && !this.field_70170_p.field_72995_K && this.partLandingGear != null) {
            int blockId = 0;
            if (!this.isLandingGearFolded() && this.partLandingGear.getFactor() <= 0.1f) {
                blockId = MCH_Lib.getBlockIdY((Entity)this, (int)3, (int)-20);
                if ((!(this.getCurrentThrottle() > (double)0.8f) || this.field_70122_E || blockId != 0) && this.getAcInfo().isFloat && (this.func_70090_H() || MCH_Lib.getBlockY((Entity)this, (int)3, (int)-20, (boolean)true) == W_Block.getWater())) {
                    this.partLandingGear.setStatusServer(true);
                }
            } else if (this.isLandingGearFolded() && this.partLandingGear.getFactor() >= 0.9f) {
                blockId = MCH_Lib.getBlockIdY((Entity)this, (int)3, (int)-10);
                if (this.getCurrentThrottle() < (double)this.getUnfoldLandingGearThrottle() && blockId != 0) {
                    boolean unfold = true;
                    if (this.getAcInfo().isFloat && W_Block.isEqual((int)(blockId = MCH_Lib.getBlockIdY((World)this.field_70170_p, (double)this.field_70165_t, (double)(this.field_70163_u + 1.0 + (double)this.getAcInfo().floatOffset), (double)this.field_70161_v, (int)1, (int)-150, (boolean)true)), (Block)W_Block.getWater())) {
                        unfold = false;
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

    public float getUnfoldLandingGearThrottle() {
        return 0.8f;
    }

    private int getPartStatus() {
        return this.func_70096_w().func_75679_c(31);
    }

    private void setPartStatus(int n) {
        this.func_70096_w().func_75692_b(31, (Object)n);
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

    protected WeaponBay[] createWeaponBays() {
        WeaponBay[] wbs = new WeaponBay[this.getAcInfo().partWeaponBay.size()];
        for (int i = 0; i < wbs.length; ++i) {
            wbs[i] = new WeaponBay(this);
        }
        return wbs;
    }

    protected MCH_Parts createHatch() {
        MCH_Parts hatch = null;
        if (this.getAcInfo().haveHatch()) {
            hatch = new MCH_Parts((Entity)this, 4, 31, "Hatch");
            hatch.rotationMax = 90.0f;
            hatch.rotationInv = 1.5f;
            hatch.soundEndSwichOn.setPrm("plane_cc", 1.0f, 1.0f);
            hatch.soundEndSwichOff.setPrm("plane_cc", 1.0f, 1.0f);
            hatch.soundSwitching.setPrm("plane_cv", 1.0f, 0.5f);
        }
        return hatch;
    }

    public boolean haveHatch() {
        return this.partHatch != null;
    }

    public boolean canFoldHatch() {
        if (this.partHatch == null || this.modeSwitchCooldown > 0) {
            return false;
        }
        return this.partHatch.isOFF();
    }

    public boolean canUnfoldHatch() {
        if (this.partHatch == null || this.modeSwitchCooldown > 0) {
            return false;
        }
        return this.partHatch.isON();
    }

    public void foldHatch(boolean fold) {
        this.foldHatch(fold, false);
    }

    public void foldHatch(boolean fold, boolean force) {
        if (this.partHatch == null) {
            return;
        }
        if (!force && this.modeSwitchCooldown > 0) {
            return;
        }
        this.partHatch.setStatusServer(fold);
        this.modeSwitchCooldown = 20;
        if (!fold) {
            this.stopUnmountCrew();
        }
    }

    public float getHatchRotation() {
        return this.partHatch != null ? this.partHatch.rotation : 0.0f;
    }

    public float getPrevHatchRotation() {
        return this.partHatch != null ? this.partHatch.prevRotation : 0.0f;
    }

    public void foldLandingGear() {
        if (this.partLandingGear == null || this.getModeSwitchCooldown() > 0) {
            return;
        }
        this.partLandingGear.setStatusServer(true);
        this.setModeSwitchCooldown(20);
    }

    public void unfoldLandingGear() {
        if (this.partLandingGear == null || this.getModeSwitchCooldown() > 0) {
            return;
        }
        if (this.isLandingGearFolded()) {
            this.partLandingGear.setStatusServer(false);
            this.setModeSwitchCooldown(20);
        }
    }

    public boolean canFoldLandingGear() {
        if (this.getLandingGearRotation() >= 1.0f) {
            return false;
        }
        Block block = MCH_Lib.getBlockY((Entity)this, (int)3, (int)-10, (boolean)true);
        return !this.isLandingGearFolded() && block == W_Blocks.field_150350_a;
    }

    public boolean canUnfoldLandingGear() {
        if (this.getLandingGearRotation() < 89.0f) {
            return false;
        }
        return this.isLandingGearFolded();
    }

    public boolean isLandingGearFolded() {
        return this.partLandingGear != null ? this.partLandingGear.getStatus() : false;
    }

    protected MCH_Parts createLandingGear() {
        MCH_Parts lg = null;
        if (this.getAcInfo().haveLandingGear()) {
            lg = new MCH_Parts((Entity)this, 2, 31, "LandingGear");
            lg.rotationMax = 90.0f;
            lg.rotationInv = 2.5f;
            lg.soundStartSwichOn.setPrm("plane_cc", 1.0f, 0.5f);
            lg.soundEndSwichOn.setPrm("plane_cc", 1.0f, 0.5f);
            lg.soundStartSwichOff.setPrm("plane_cc", 1.0f, 0.5f);
            lg.soundEndSwichOff.setPrm("plane_cc", 1.0f, 0.5f);
            lg.soundSwitching.setPrm("plane_cv", 1.0f, 0.75f);
        }
        return lg;
    }

    public float getLandingGearRotation() {
        return this.partLandingGear != null ? this.partLandingGear.rotation : 0.0f;
    }

    public float getPrevLandingGearRotation() {
        return this.partLandingGear != null ? this.partLandingGear.prevRotation : 0.0f;
    }

    public int getVtolMode() {
        return 0;
    }

    public void openCanopy() {
        if (this.partCanopy == null || this.getModeSwitchCooldown() > 0) {
            return;
        }
        this.partCanopy.setStatusServer(true);
        this.setModeSwitchCooldown(20);
    }

    public void openCanopy_EjectSeat() {
        if (this.partCanopy == null) {
            return;
        }
        this.partCanopy.setStatusServer(true, false);
        this.setModeSwitchCooldown(40);
    }

    public void closeCanopy() {
        if (this.partCanopy == null || this.getModeSwitchCooldown() > 0) {
            return;
        }
        if (this.getCanopyStat()) {
            this.partCanopy.setStatusServer(false);
            this.setModeSwitchCooldown(20);
        }
    }

    public boolean getCanopyStat() {
        return this.partCanopy != null ? this.partCanopy.getStatus() : false;
    }

    public boolean isCanopyClose() {
        if (this.partCanopy == null) {
            return true;
        }
        return !this.getCanopyStat() && this.getCanopyRotation() <= 0.01f;
    }

    public float getCanopyRotation() {
        return this.partCanopy != null ? this.partCanopy.rotation : 0.0f;
    }

    public float getPrevCanopyRotation() {
        return this.partCanopy != null ? this.partCanopy.prevRotation : 0.0f;
    }

    protected MCH_Parts createCanopy() {
        MCH_Parts canopy = null;
        if (this.getAcInfo().haveCanopy()) {
            canopy = new MCH_Parts((Entity)this, 0, 31, "Canopy");
            canopy.rotationMax = 90.0f;
            canopy.rotationInv = 3.5f;
            canopy.soundEndSwichOn.setPrm("plane_cc", 1.0f, 1.0f);
            canopy.soundEndSwichOff.setPrm("plane_cc", 1.0f, 1.0f);
        }
        return canopy;
    }

    public boolean hasBrake() {
        return false;
    }

    public void setBrake(boolean b) {
        if (!this.field_70170_p.field_72995_K) {
            this.setCommonStatus(11, b);
        }
    }

    public boolean getBrake() {
        return this.getCommonStatus(11);
    }

    public int func_70302_i_() {
        return this.getAcInfo() != null ? this.getAcInfo().inventorySize : 0;
    }

    public String getInvName() {
        if (this.getAcInfo() == null) {
            return super.getInvName();
        }
        String s = this.getAcInfo().displayName;
        return s.length() <= 32 ? s : s.substring(0, 31);
    }

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
}

