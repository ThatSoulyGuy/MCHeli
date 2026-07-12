package mcheli.agnostic.aircraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import mcheli.agnostic.info.MCH_BaseInfo;
import mcheli.agnostic.info.MCH_MobDropOption;
import mcheli.agnostic.math.MchMath;
import mcheli.agnostic.physics.MCH_BoundingBox;
import mcheli.agnostic.spi.ItemHandle;
import mcheli.agnostic.spi.ModelHandle;
import mcheli.agnostic.value.Vec3d;

/**
 * Agnostic port of {@code mcheli.aircraft.MCH_AircraftInfo}: the parsed definition of one vehicle
 * (helicopter / plane / tank / ...). Pure Java — the reference's platform surfaces are coerced:
 * <ul>
 *   <li>{@code net.minecraft.util.Vec3} -&gt; {@link Vec3d}</li>
 *   <li>{@code net.minecraftforge.client.model.IModelCustom} -&gt; {@link ModelHandle} (carried, never dereferenced here)</li>
 *   <li>{@code net.minecraft.item.Item} -&gt; {@link ItemHandle}; the reference's {@code getItemStack()} (a
 *       {@code net.minecraft.item.ItemStack}) is dropped — dependent callers build stacks from {@link #getItem()}</li>
 *   <li>{@code net.minecraft.item.crafting.IRecipe} list dropped — recipes compile dependent-side; {@link #recipeString} stays</li>
 *   <li>{@code mcheli.hud.MCH_Hud} -&gt; HUD <em>name</em> Strings ({@link #hudList}, {@link #hudTvMissile}); the
 *       dependent layer resolves names to huds (mapping unknown names to the NoDisp sentinel)</li>
 *   <li>{@code MathHelper.wrapAngleTo180_float} -&gt; {@link MchMath#wrapAngleTo180}</li>
 *   <li>{@code MCH_WeaponInfoManager.contains(type)} -&gt; overridable {@link #isWeaponTypeAvailable(String)} seam</li>
 *   <li>the client-only ({@code MCH_MOD.proxy.isRemote()}) gate on the {@code HUD} directive is dropped — parsing is
 *       side-agnostic; the dependent layer decides whether to consume the parsed HUD names</li>
 * </ul>
 */
public abstract class MCH_AircraftInfo extends MCH_BaseInfo {
   public final String name;
   public String displayName;
   public HashMap<String, String> displayNameLang;
   public int itemID;
   public List<String> recipeString;
   public boolean isShapedRecipe;
   public String category;
   public boolean isEnableGunnerMode;
   public int cameraZoom;
   public boolean isEnableConcurrentGunnerMode;
   public boolean isEnableNightVision;
   public boolean isEnableEntityRadar;
   public boolean isEnableEjectionSeat;
   public boolean isEnableParachuting;
   public MCH_AircraftInfo.Flare flare;
   public float bodyHeight;
   public float bodyWidth;
   public boolean isFloat;
   public float floatOffset;
   public float gravity;
   public float gravityInWater;
   public int maxHp;
   public float armorMinDamage;
   public float armorMaxDamage;
   public float armorDamageFactor;
   public boolean enableBack;
   public int inventorySize;
   public boolean isUAV;
   public boolean isSmallUAV;
   public boolean isTargetDrone;
   public float autoPilotRot;
   public float onGroundPitch;
   public boolean canMoveOnGround;
   public boolean canRotOnGround;
   public List<MCH_AircraftInfo.WeaponSet> weaponSetList;
   public List<MCH_SeatInfo> seatList;
   public List<Integer[]> exclusionSeatList;
   public List<String> hudList;
   public String hudTvMissile;
   public float damageFactor;
   public float submergedDamageHeight;
   public boolean regeneration;
   public List<MCH_BoundingBox> extraBoundingBox;
   public List<MCH_AircraftInfo.Wheel> wheels;
   public int maxFuel;
   public float fuelConsumption;
   public float fuelSupplyRange;
   public float ammoSupplyRange;
   public float repairOtherVehiclesRange;
   public int repairOtherVehiclesValue;
   public float stealth;
   public boolean canRide;
   public float entityWidth;
   public float entityHeight;
   public float entityPitch;
   public float entityRoll;
   public float stepHeight;
   public List<MCH_SeatRackInfo> entityRackList;
   public int mobSeatNum;
   public int entityRackNum;
   public MCH_MobDropOption mobDropOption;
   public List<MCH_AircraftInfo.RepellingHook> repellingHooks;
   public List<MCH_AircraftInfo.RideRack> rideRacks;
   public List<MCH_AircraftInfo.ParticleSplash> particleSplashs;
   public List<MCH_AircraftInfo.SearchLight> searchLights;
   public float rotorSpeed;
   public boolean enableSeaSurfaceParticle;
   public float pivotTurnThrottle;
   public float trackRollerRot;
   public float partWheelRot;
   public float onGroundPitchFactor;
   public float onGroundRollFactor;
   public Vec3d turretPosition;
   public boolean defaultFreelook;
   public Vec3d unmountPosition;
   public float markerWidth;
   public float markerHeight;
   public float bbZmin;
   public float bbZmax;
   public float bbZ;
   public boolean alwaysCameraView;
   public List<MCH_AircraftInfo.CameraPosition> cameraPosition;
   public float cameraRotationSpeed;
   public float speed;
   public float motionFactor;
   public float mobilityYaw;
   public float mobilityPitch;
   public float mobilityRoll;
   public float mobilityYawOnGround;
   public float minRotationPitch;
   public float maxRotationPitch;
   public float minRotationRoll;
   public float maxRotationRoll;
   public boolean limitRotation;
   public float throttleUpDown;
   public float throttleUpDownOnEntity;
   private List<String> textureNameList;
   public int textureCount;
   public float particlesScale;
   public boolean hideEntity;
   public boolean smoothShading;
   public String soundMove;
   public float soundRange;
   public float soundVolume;
   public float soundPitch;
   public ModelHandle model;
   public List<MCH_AircraftInfo.Hatch> hatchList;
   public List<MCH_AircraftInfo.Camera> cameraList;
   public List<MCH_AircraftInfo.PartWeapon> partWeapon;
   public List<MCH_AircraftInfo.WeaponBay> partWeaponBay;
   public List<MCH_AircraftInfo.Canopy> canopyList;
   public List<MCH_AircraftInfo.LandingGear> landingGear;
   public List<MCH_AircraftInfo.Throttle> partThrottle;
   public List<MCH_AircraftInfo.RotPart> partRotPart;
   public List<MCH_AircraftInfo.CrawlerTrack> partCrawlerTrack;
   public List<MCH_AircraftInfo.TrackRoller> partTrackRoller;
   public List<MCH_AircraftInfo.PartWheel> partWheel;
   public List<MCH_AircraftInfo.PartWheel> partSteeringWheel;
   public List<MCH_AircraftInfo.Hatch> lightHatchList;
   private String lastWeaponType = "";
   private int lastWeaponIndex = -1;
   private MCH_AircraftInfo.PartWeapon lastWeaponPart;

   public abstract ItemHandle getItem();

   public abstract String getDirectoryName();

   public abstract String getKindName();

   /**
    * Coercion seam for the reference's {@code MCH_WeaponInfoManager.contains(type)} gate. The agnostic layer has
    * no weapon registry yet, so it accepts every weapon type during parse; the dependent layer overrides this to
    * consult the real registry (and later passes prune weapons that never resolve).
    */
   protected boolean isWeaponTypeAvailable(String type) {
      return true;
   }

   public MCH_AircraftInfo(String s) {
      this.name = s;
      this.displayName = this.name;
      this.displayNameLang = new HashMap<>();
      this.itemID = 0;
      this.recipeString = new ArrayList<>();
      this.isShapedRecipe = true;
      this.category = "zzz";
      this.isEnableGunnerMode = false;
      this.isEnableConcurrentGunnerMode = false;
      this.isEnableNightVision = false;
      this.isEnableEntityRadar = false;
      this.isEnableEjectionSeat = false;
      this.isEnableParachuting = false;
      this.flare = new MCH_AircraftInfo.Flare();
      this.weaponSetList = new ArrayList<>();
      this.seatList = new ArrayList<>();
      this.exclusionSeatList = new ArrayList<>();
      this.hudList = new ArrayList<>();
      this.hudTvMissile = null;
      this.bodyHeight = 0.7F;
      this.bodyWidth = 2.0F;
      this.isFloat = false;
      this.floatOffset = 0.0F;
      this.gravity = -0.04F;
      this.gravityInWater = -0.04F;
      this.maxHp = 50;
      this.damageFactor = 0.2F;
      this.submergedDamageHeight = 0.0F;
      this.inventorySize = 0;
      this.armorDamageFactor = 1.0F;
      this.armorMaxDamage = 100000.0F;
      this.armorMinDamage = 0.0F;
      this.enableBack = false;
      this.isUAV = false;
      this.isSmallUAV = false;
      this.isTargetDrone = false;
      this.autoPilotRot = -0.6F;
      this.regeneration = false;
      this.onGroundPitch = 0.0F;
      this.canMoveOnGround = true;
      this.canRotOnGround = true;
      this.cameraZoom = this.getDefaultMaxZoom();
      this.extraBoundingBox = new ArrayList<>();
      this.maxFuel = 0;
      this.fuelConsumption = 1.0F;
      this.fuelSupplyRange = 0.0F;
      this.ammoSupplyRange = 0.0F;
      this.repairOtherVehiclesRange = 0.0F;
      this.repairOtherVehiclesValue = 10;
      this.stealth = 0.0F;
      this.canRide = true;
      this.entityWidth = 1.0F;
      this.entityHeight = 1.0F;
      this.entityPitch = 0.0F;
      this.entityRoll = 0.0F;
      this.stepHeight = this.getDefaultStepHeight();
      this.entityRackList = new ArrayList<>();
      this.mobSeatNum = 0;
      this.entityRackNum = 0;
      this.mobDropOption = new MCH_MobDropOption();
      this.repellingHooks = new ArrayList<>();
      this.rideRacks = new ArrayList<>();
      this.particleSplashs = new ArrayList<>();
      this.searchLights = new ArrayList<>();
      this.markerHeight = 1.0F;
      this.markerWidth = 2.0F;
      this.bbZmax = 1.0F;
      this.bbZmin = -1.0F;
      this.rotorSpeed = this.getDefaultRotorSpeed();
      this.wheels = this.getDefaultWheelList();
      this.onGroundPitchFactor = 0.0F;
      this.onGroundRollFactor = 0.0F;
      this.turretPosition = Vec3d.ZERO;
      this.defaultFreelook = false;
      this.unmountPosition = null;
      this.cameraPosition = new ArrayList<>();
      this.alwaysCameraView = false;
      this.cameraRotationSpeed = 1000.0F;
      this.speed = 0.1F;
      this.motionFactor = 0.96F;
      this.mobilityYaw = 1.0F;
      this.mobilityPitch = 1.0F;
      this.mobilityRoll = 1.0F;
      this.mobilityYawOnGround = 1.0F;
      this.minRotationPitch = this.getMinRotationPitch();
      this.maxRotationPitch = this.getMaxRotationPitch();
      this.minRotationRoll = this.getMinRotationPitch();
      this.maxRotationRoll = this.getMaxRotationPitch();
      this.limitRotation = false;
      this.throttleUpDown = 1.0F;
      this.throttleUpDownOnEntity = 2.0F;
      this.pivotTurnThrottle = 0.0F;
      this.trackRollerRot = 30.0F;
      this.partWheelRot = 30.0F;
      this.textureNameList = new ArrayList<>();
      this.textureNameList.add(this.name);
      this.textureCount = 0;
      this.particlesScale = 1.0F;
      this.enableSeaSurfaceParticle = false;
      this.hideEntity = false;
      this.smoothShading = true;
      this.soundMove = "";
      this.soundPitch = 1.0F;
      this.soundVolume = 1.0F;
      this.soundRange = this.getDefaultSoundRange();
      this.model = null;
      this.hatchList = new ArrayList<>();
      this.cameraList = new ArrayList<>();
      this.partWeapon = new ArrayList<>();
      this.lastWeaponPart = null;
      this.partWeaponBay = new ArrayList<>();
      this.canopyList = new ArrayList<>();
      this.landingGear = new ArrayList<>();
      this.partThrottle = new ArrayList<>();
      this.partRotPart = new ArrayList<>();
      this.partCrawlerTrack = new ArrayList<>();
      this.partTrackRoller = new ArrayList<>();
      this.partWheel = new ArrayList<>();
      this.partSteeringWheel = new ArrayList<>();
      this.lightHatchList = new ArrayList<>();
   }

   public float getDefaultSoundRange() {
      return 100.0F;
   }

   public List<MCH_AircraftInfo.Wheel> getDefaultWheelList() {
      return new ArrayList<>();
   }

   public float getDefaultRotorSpeed() {
      return 0.0F;
   }

   protected float getDefaultStepHeight() {
      return 0.0F;
   }

   public boolean haveRepellingHook() {
      return this.repellingHooks.size() > 0;
   }

   public boolean haveFlare() {
      return this.flare.types.length > 0;
   }

   public boolean haveCanopy() {
      return this.canopyList.size() > 0;
   }

   public boolean haveLandingGear() {
      return this.landingGear.size() > 0;
   }

   public abstract String getDefaultHudName(int var1);

   @Override
   public boolean isValidData() throws Exception {
      if (this.cameraPosition.size() <= 0) {
         this.cameraPosition.add(new MCH_AircraftInfo.CameraPosition());
      }

      this.bbZ = (this.bbZmax + this.bbZmin) / 2.0F;
      if (this.isTargetDrone) {
         this.isUAV = true;
      }

      if (this.isEnableParachuting && this.repellingHooks.size() > 0) {
         this.isEnableParachuting = false;
         this.repellingHooks.clear();
      }

      if (this.isUAV) {
         this.alwaysCameraView = true;
         if (this.seatList.size() == 0) {
            MCH_SeatInfo s = new MCH_SeatInfo(new Vec3d(0.0, 0.0, 0.0), false);
            this.seatList.add(s);
         }
      }

      this.mobSeatNum = this.seatList.size();
      this.entityRackNum = this.entityRackList.size();
      if (this.getNumSeat() < 1) {
         throw new Exception();
      }

      if (this.getNumHud() < this.getNumSeat()) {
         for (int i = this.getNumHud(); i < this.getNumSeat(); i++) {
            this.hudList.add(this.getDefaultHudName(i));
         }
      }

      if (this.getNumSeat() == 1 && this.getNumHud() == 1) {
         this.hudList.add(this.getDefaultHudName(1));
      }

      for (MCH_SeatRackInfo ei : this.entityRackList) {
         this.seatList.add(ei);
      }

      this.entityRackList.clear();
      if (this.hudTvMissile == null) {
         this.hudTvMissile = "tv_missile";
      }

      if (this.textureNameList.size() < 1) {
         throw new Exception();
      }

      if (this.itemID <= 0) {
      }

      for (int i = 0; i < this.partWeaponBay.size(); i++) {
         MCH_AircraftInfo.WeaponBay wb = this.partWeaponBay.get(i);
         String[] weaponNames = wb.weaponName.split("\\s*/\\s*");
         if (weaponNames.length <= 0) {
            this.partWeaponBay.remove(i);
         } else {
            List<Integer> list = new ArrayList<>();

            for (String s : weaponNames) {
               int id = this.getWeaponIdByName(s);
               if (id >= 0) {
                  list.add(id);
               }
            }

            if (list.size() <= 0) {
               this.partWeaponBay.remove(i);
            } else {
               this.partWeaponBay.get(i).weaponIds = list.toArray(new Integer[0]);
            }
         }
      }

      return true;
   }

   public int getInfo_MaxSeatNum() {
      return 30;
   }

   public int getNumSeatAndRack() {
      return this.seatList.size();
   }

   public int getNumSeat() {
      return this.mobSeatNum;
   }

   public int getNumRack() {
      return this.entityRackNum;
   }

   public int getNumHud() {
      return this.hudList.size();
   }

   public float getMaxSpeed() {
      return 0.8F;
   }

   public float getMinRotationPitch() {
      return -89.9F;
   }

   public float getMaxRotationPitch() {
      return 80.0F;
   }

   public float getMinRotationRoll() {
      return -80.0F;
   }

   public float getMaxRotationRoll() {
      return 80.0F;
   }

   public int getDefaultMaxZoom() {
      return 1;
   }

   public boolean haveHatch() {
      return this.hatchList.size() > 0;
   }

   public boolean havePartCamera() {
      return this.cameraList.size() > 0;
   }

   public boolean havePartThrottle() {
      return this.partThrottle.size() > 0;
   }

   public MCH_AircraftInfo.WeaponSet getWeaponSetById(int id) {
      return id >= 0 && id < this.weaponSetList.size() ? this.weaponSetList.get(id) : null;
   }

   public MCH_AircraftInfo.Weapon getWeaponById(int id) {
      MCH_AircraftInfo.WeaponSet ws = this.getWeaponSetById(id);
      return ws != null ? ws.weapons.get(0) : null;
   }

   public int getWeaponIdByName(String s) {
      for (int i = 0; i < this.weaponSetList.size(); i++) {
         if (this.weaponSetList.get(i).type.equalsIgnoreCase(s)) {
            return i;
         }
      }

      return -1;
   }

   public MCH_AircraftInfo.Weapon getWeaponByName(String s) {
      for (int i = 0; i < this.weaponSetList.size(); i++) {
         if (this.weaponSetList.get(i).type.equalsIgnoreCase(s)) {
            return this.getWeaponById(i);
         }
      }

      return null;
   }

   public int getWeaponNum() {
      return this.weaponSetList.size();
   }

   @Override
   public void loadItemData(String item, String data) {
      if (item.compareTo("displayname") == 0) {
         this.displayName = data.trim();
      } else if (item.compareTo("adddisplayname") == 0) {
         String[] s = data.split("\\s*,\\s*");
         if (s != null && s.length == 2) {
            this.displayNameLang.put(s[0].trim(), s[1].trim());
         }
      } else if (item.equalsIgnoreCase("Category")) {
         this.category = data.toUpperCase().replaceAll("[,;:]", ".").replaceAll("[ \t]", "");
      } else if (item.equalsIgnoreCase("CanRide")) {
         this.canRide = this.toBool(data, true);
      } else if (item.equalsIgnoreCase("MaxFuel")) {
         this.maxFuel = this.toInt(data, 0, 100000000);
      } else if (item.equalsIgnoreCase("FuelConsumption")) {
         this.fuelConsumption = this.toFloat(data, 0.0F, 10000.0F);
      } else if (item.equalsIgnoreCase("FuelSupplyRange")) {
         this.fuelSupplyRange = this.toFloat(data, 0.0F, 1000.0F);
      } else if (item.equalsIgnoreCase("AmmoSupplyRange")) {
         this.ammoSupplyRange = this.toFloat(data, 0.0F, 1000.0F);
      } else if (item.equalsIgnoreCase("RepairOtherVehicles")) {
         String[] s = this.splitParam(data);
         if (s.length >= 1) {
            this.repairOtherVehiclesRange = this.toFloat(s[0], 0.0F, 1000.0F);
            if (s.length >= 2) {
               this.repairOtherVehiclesValue = this.toInt(s[1], 0, 10000000);
            }
         }
      } else if (item.compareTo("itemid") == 0) {
         this.itemID = this.toInt(data, 0, 65535);
      } else if (item.compareTo("addtexture") == 0) {
         this.textureNameList.add(data.toLowerCase());
      } else if (item.compareTo("particlesscale") == 0) {
         this.particlesScale = this.toFloat(data, 0.0F, 50.0F);
      } else if (item.equalsIgnoreCase("EnableSeaSurfaceParticle")) {
         this.enableSeaSurfaceParticle = this.toBool(data);
      } else if (item.equalsIgnoreCase("AddParticleSplash")) {
         String[] s = this.splitParam(data);
         if (s.length >= 3) {
            Vec3d v = this.toVec3(s[0], s[1], s[2]);
            int num = s.length >= 4 ? this.toInt(s[3], 1, 100) : 2;
            float size = s.length >= 5 ? this.toFloat(s[4]) : 2.0F;
            float acc = s.length >= 6 ? this.toFloat(s[5]) : 1.0F;
            int age = s.length >= 7 ? this.toInt(s[6], 1, 100000) : 80;
            float motionY = s.length >= 8 ? this.toFloat(s[7]) : 0.01F;
            float gravity = s.length >= 9 ? this.toFloat(s[8]) : 0.0F;
            this.particleSplashs.add(new MCH_AircraftInfo.ParticleSplash(v, num, size, acc, age, motionY, gravity));
         }
      } else if (item.equalsIgnoreCase("AddSearchLight") || item.equalsIgnoreCase("AddFixedSearchLight") || item.equalsIgnoreCase("AddSteeringSearchLight")) {
         String[] s = this.splitParam(data);
         if (s.length >= 7) {
            Vec3d v = this.toVec3(s[0], s[1], s[2]);
            int cs = this.hex2dec(s[3]);
            int ce = this.hex2dec(s[4]);
            float h = this.toFloat(s[5]);
            float w = this.toFloat(s[6]);
            float yaw = s.length >= 8 ? this.toFloat(s[7]) : 0.0F;
            float pitch = s.length >= 9 ? this.toFloat(s[8]) : 0.0F;
            float stRot = s.length >= 10 ? this.toFloat(s[9]) : 0.0F;
            boolean fixDir = !item.equalsIgnoreCase("AddSearchLight");
            boolean steering = item.equalsIgnoreCase("AddSteeringSearchLight");
            this.searchLights.add(new MCH_AircraftInfo.SearchLight(v, cs, ce, h, w, fixDir, yaw, pitch, steering, stRot));
         }
      } else if (item.equalsIgnoreCase("AddPartLightHatch")) {
         String[] s = this.splitParam(data);
         if (s.length >= 6) {
            float mx = s.length >= 7 ? this.toFloat(s[6], -1800.0F, 1800.0F) : 90.0F;
            this.lightHatchList
               .add(
                  new MCH_AircraftInfo.Hatch(
                     this.toFloat(s[0]),
                     this.toFloat(s[1]),
                     this.toFloat(s[2]),
                     this.toFloat(s[3]),
                     this.toFloat(s[4]),
                     this.toFloat(s[5]),
                     mx,
                     "light_hatch" + this.lightHatchList.size(),
                     false
                  )
               );
         }
      } else if (item.equalsIgnoreCase("AddRepellingHook")) {
         String[] s = this.splitParam(data);
         if (s != null && s.length >= 3) {
            int inv = s.length >= 4 ? this.toInt(s[3], 1, 100000) : 10;
            this.repellingHooks.add(new MCH_AircraftInfo.RepellingHook(this.toVec3(s[0], s[1], s[2]), inv));
         }
      } else if (item.equalsIgnoreCase("AddRack")) {
         String[] s = data.toLowerCase().split("\\s*,\\s*");
         if (s != null && s.length >= 7) {
            String[] names = s[0].split("\\s*/\\s*");
            float range = s.length >= 8 ? this.toFloat(s[7]) : 6.0F;
            float para = s.length >= 9 ? this.toFloat(s[8], 0.0F, 1000000.0F) : 20.0F;
            float yaw = s.length >= 10 ? this.toFloat(s[9]) : 0.0F;
            float pitch = s.length >= 11 ? this.toFloat(s[10]) : 0.0F;
            boolean rs = s.length >= 12 ? this.toBool(s[11]) : false;
            this.entityRackList
               .add(
                  new MCH_SeatRackInfo(
                     names,
                     this.toDouble(s[1]),
                     this.toDouble(s[2]),
                     this.toDouble(s[3]),
                     new MCH_AircraftInfo.CameraPosition(this.toVec3(s[4], s[5], s[6]).add(0.0, 1.5, 0.0)),
                     range,
                     para,
                     yaw,
                     pitch,
                     rs
                  )
               );
         }
      } else if (item.equalsIgnoreCase("RideRack")) {
         String[] s = this.splitParam(data);
         if (s.length >= 2) {
            MCH_AircraftInfo.RideRack r = new MCH_AircraftInfo.RideRack(s[0].trim().toLowerCase(), this.toInt(s[1], 1, 10000));
            this.rideRacks.add(r);
         }
      } else if (item.equalsIgnoreCase("AddSeat") || item.equalsIgnoreCase("AddGunnerSeat") || item.equalsIgnoreCase("AddFixRotSeat")) {
         if (this.seatList.size() >= this.getInfo_MaxSeatNum()) {
            return;
         }

         String[] s = this.splitParam(data);
         if (s.length < 3) {
            return;
         }

         Vec3d p = this.toVec3(s[0], s[1], s[2]);
         if (item.equalsIgnoreCase("AddSeat")) {
            boolean rs = s.length >= 4 ? this.toBool(s[3]) : false;
            MCH_SeatInfo seat = new MCH_SeatInfo(p, rs);
            this.seatList.add(seat);
         } else {
            MCH_SeatInfo seat;
            if (s.length >= 6) {
               MCH_AircraftInfo.CameraPosition c = new MCH_AircraftInfo.CameraPosition(this.toVec3(s[3], s[4], s[5]));
               boolean sg = s.length >= 7 ? this.toBool(s[6]) : false;
               if (item.equalsIgnoreCase("AddGunnerSeat")) {
                  if (s.length >= 9) {
                     float minPitch = this.toFloat(s[7], -90.0F, 90.0F);
                     float maxPitch = this.toFloat(s[8], -90.0F, 90.0F);
                     if (minPitch > maxPitch) {
                        float t = minPitch;
                        minPitch = maxPitch;
                        maxPitch = t;
                     }

                     boolean rs = s.length >= 10 ? this.toBool(s[9]) : false;
                     seat = new MCH_SeatInfo(p, true, c, true, sg, false, 0.0F, 0.0F, minPitch, maxPitch, rs);
                  } else {
                     seat = new MCH_SeatInfo(p, true, c, true, sg, false, 0.0F, 0.0F, false);
                  }
               } else {
                  boolean fixRot = s.length >= 9;
                  float fixYaw = fixRot ? this.toFloat(s[7]) : 0.0F;
                  float fixPitch = fixRot ? this.toFloat(s[8]) : 0.0F;
                  boolean rs = s.length >= 10 ? this.toBool(s[9]) : false;
                  seat = new MCH_SeatInfo(p, true, c, true, sg, fixRot, fixYaw, fixPitch, rs);
               }
            } else {
               seat = new MCH_SeatInfo(p, true, new MCH_AircraftInfo.CameraPosition(), false, false, false, 0.0F, 0.0F, false);
            }

            this.seatList.add(seat);
         }
      } else if (item.equalsIgnoreCase("SetWheelPos")) {
         String[] s = this.splitParam(data);
         if (s.length >= 4) {
            float x = Math.abs(this.toFloat(s[0]));
            float y = this.toFloat(s[1]);
            this.wheels.clear();

            for (int i = 2; i < s.length; i++) {
               this.wheels.add(new MCH_AircraftInfo.Wheel(new Vec3d(x, y, this.toFloat(s[i]))));
            }

            Collections.sort(this.wheels, new Comparator<MCH_AircraftInfo.Wheel>() {
               public int compare(MCH_AircraftInfo.Wheel arg0, MCH_AircraftInfo.Wheel arg1) {
                  return arg0.pos.z() > arg1.pos.z() ? -1 : 1;
               }
            });
         }
      } else if (item.equalsIgnoreCase("ExclusionSeat")) {
         String[] s = this.splitParam(data);
         if (s.length >= 2) {
            Integer[] a = new Integer[s.length];

            for (int i = 0; i < a.length; i++) {
               a[i] = this.toInt(s[i], 1, 10000) - 1;
            }

            this.exclusionSeatList.add(a);
         }
      } else if (item.equalsIgnoreCase("HUD")) {
         this.hudList.clear();
         String[] ss = data.split("\\s*,\\s*");

         for (String s : ss) {
            this.hudList.add(s);
         }
      } else if (item.compareTo("enablenightvision") == 0) {
         this.isEnableNightVision = this.toBool(data);
      } else if (item.compareTo("enableentityradar") == 0) {
         this.isEnableEntityRadar = this.toBool(data);
      } else if (item.equalsIgnoreCase("EnableEjectionSeat")) {
         this.isEnableEjectionSeat = this.toBool(data);
      } else if (item.equalsIgnoreCase("EnableParachuting")) {
         this.isEnableParachuting = this.toBool(data);
      } else if (item.equalsIgnoreCase("MobDropOption")) {
         String[] s = this.splitParam(data);
         if (s.length >= 3) {
            this.mobDropOption.pos = this.toVec3(s[0], s[1], s[2]);
            this.mobDropOption.interval = s.length >= 4 ? this.toInt(s[3]) : 12;
         }
      } else if (item.equalsIgnoreCase("Width")) {
         this.bodyWidth = this.toFloat(data, 0.1F, 1000.0F);
      } else if (item.equalsIgnoreCase("Height")) {
         this.bodyHeight = this.toFloat(data, 0.1F, 1000.0F);
      } else if (item.compareTo("float") == 0) {
         this.isFloat = this.toBool(data);
      } else if (item.compareTo("floatoffset") == 0) {
         this.floatOffset = -this.toFloat(data);
      } else if (item.compareTo("gravity") == 0) {
         this.gravity = this.toFloat(data, -50.0F, 50.0F);
      } else if (item.compareTo("gravityinwater") == 0) {
         this.gravityInWater = this.toFloat(data, -50.0F, 50.0F);
      } else if (item.compareTo("cameraposition") == 0) {
         String[] s = data.split("\\s*,\\s*");
         if (s.length >= 3) {
            this.alwaysCameraView = s.length >= 4 ? this.toBool(s[3]) : false;
            boolean fixRot = s.length >= 5;
            float yaw = s.length >= 5 ? this.toFloat(s[4]) : 0.0F;
            float pitch = s.length >= 6 ? this.toFloat(s[5]) : 0.0F;
            this.cameraPosition.add(new MCH_AircraftInfo.CameraPosition(this.toVec3(s[0], s[1], s[2]), fixRot, yaw, pitch));
         }
      } else if (item.equalsIgnoreCase("UnmountPosition")) {
         String[] s = data.split("\\s*,\\s*");
         if (s.length >= 3) {
            this.unmountPosition = this.toVec3(s[0], s[1], s[2]);
         }
      } else if (item.equalsIgnoreCase("TurretPosition")) {
         String[] s = data.split("\\s*,\\s*");
         if (s.length >= 3) {
            this.turretPosition = this.toVec3(s[0], s[1], s[2]);
         }
      } else if (item.equalsIgnoreCase("CameraRotationSpeed")) {
         this.cameraRotationSpeed = this.toFloat(data, 0.0F, 10000.0F);
      } else if (item.compareTo("regeneration") == 0) {
         this.regeneration = this.toBool(data);
      } else if (item.compareTo("speed") == 0) {
         this.speed = this.toFloat(data, 0.0F, this.getMaxSpeed());
      } else if (item.equalsIgnoreCase("EnableBack")) {
         this.enableBack = this.toBool(data);
      } else if (item.equalsIgnoreCase("MotionFactor")) {
         this.motionFactor = this.toFloat(data, 0.0F, 1.0F);
      } else if (item.equalsIgnoreCase("MobilityYawOnGround")) {
         this.mobilityYawOnGround = this.toFloat(data, 0.0F, 100.0F);
      } else if (item.equalsIgnoreCase("MobilityYaw")) {
         this.mobilityYaw = this.toFloat(data, 0.0F, 100.0F);
      } else if (item.equalsIgnoreCase("MobilityPitch")) {
         this.mobilityPitch = this.toFloat(data, 0.0F, 100.0F);
      } else if (item.equalsIgnoreCase("MobilityRoll")) {
         this.mobilityRoll = this.toFloat(data, 0.0F, 100.0F);
      } else if (item.equalsIgnoreCase("MinRotationPitch")) {
         this.limitRotation = true;
         this.minRotationPitch = this.toFloat(data, this.getMinRotationPitch(), 0.0F);
      } else if (item.equalsIgnoreCase("MaxRotationPitch")) {
         this.limitRotation = true;
         this.maxRotationPitch = this.toFloat(data, 0.0F, this.getMaxRotationPitch());
      } else if (item.equalsIgnoreCase("MinRotationRoll")) {
         this.limitRotation = true;
         this.minRotationRoll = this.toFloat(data, this.getMinRotationRoll(), 0.0F);
      } else if (item.equalsIgnoreCase("MaxRotationRoll")) {
         this.limitRotation = true;
         this.maxRotationRoll = this.toFloat(data, 0.0F, this.getMaxRotationRoll());
      } else if (item.compareTo("throttleupdown") == 0) {
         this.throttleUpDown = this.toFloat(data, 0.0F, 3.0F);
      } else if (item.equalsIgnoreCase("ThrottleUpDownOnEntity")) {
         this.throttleUpDownOnEntity = this.toFloat(data, 0.0F, 100000.0F);
      } else if (item.equalsIgnoreCase("Stealth")) {
         this.stealth = this.toFloat(data, 0.0F, 1.0F);
      } else if (item.equalsIgnoreCase("EntityWidth")) {
         this.entityWidth = this.toFloat(data, -100.0F, 100.0F);
      } else if (item.equalsIgnoreCase("EntityHeight")) {
         this.entityHeight = this.toFloat(data, -100.0F, 100.0F);
      } else if (item.equalsIgnoreCase("EntityPitch")) {
         this.entityPitch = this.toFloat(data, -360.0F, 360.0F);
      } else if (item.equalsIgnoreCase("EntityRoll")) {
         this.entityRoll = this.toFloat(data, -360.0F, 360.0F);
      } else if (item.equalsIgnoreCase("StepHeight")) {
         this.stepHeight = this.toFloat(data, 0.0F, 1000.0F);
      } else if (item.equalsIgnoreCase("CanMoveOnGround")) {
         this.canMoveOnGround = this.toBool(data);
      } else if (item.equalsIgnoreCase("CanRotOnGround")) {
         this.canRotOnGround = this.toBool(data);
      } else if (item.equalsIgnoreCase("AddWeapon") || item.equalsIgnoreCase("AddTurretWeapon")) {
         String[] s = data.split("\\s*,\\s*");
         String type = s[0].toLowerCase();
         if (s.length >= 4 && this.isWeaponTypeAvailable(type)) {
            float y = s.length >= 5 ? this.toFloat(s[4]) : 0.0F;
            float p = s.length >= 6 ? this.toFloat(s[5]) : 0.0F;
            boolean canUsePilot = s.length >= 7 ? this.toBool(s[6]) : true;
            int seatID = s.length >= 8 ? this.toInt(s[7], 1, this.getInfo_MaxSeatNum()) - 1 : 0;
            if (seatID <= 0) {
               canUsePilot = true;
            }

            float dfy = s.length >= 9 ? this.toFloat(s[8]) : 0.0F;
            dfy = MchMath.wrapAngleTo180(dfy);
            float mny = s.length >= 10 ? this.toFloat(s[9]) : 0.0F;
            float mxy = s.length >= 11 ? this.toFloat(s[10]) : 0.0F;
            float mnp = s.length >= 12 ? this.toFloat(s[11]) : 0.0F;
            float mxp = s.length >= 13 ? this.toFloat(s[12]) : 0.0F;
            MCH_AircraftInfo.Weapon e = new MCH_AircraftInfo.Weapon(
               this.toFloat(s[1]),
               this.toFloat(s[2]),
               this.toFloat(s[3]),
               y,
               p,
               canUsePilot,
               seatID,
               dfy,
               mny,
               mxy,
               mnp,
               mxp,
               item.equalsIgnoreCase("AddTurretWeapon")
            );
            if (type.compareTo(this.lastWeaponType) != 0) {
               this.weaponSetList.add(new MCH_AircraftInfo.WeaponSet(type));
               this.lastWeaponIndex++;
               this.lastWeaponType = type;
            }

            this.weaponSetList.get(this.lastWeaponIndex).weapons.add(e);
         }
      } else if (item.equalsIgnoreCase("AddPartWeapon")
         || item.equalsIgnoreCase("AddPartRotWeapon")
         || item.equalsIgnoreCase("AddPartTurretWeapon")
         || item.equalsIgnoreCase("AddPartTurretRotWeapon")
         || item.equalsIgnoreCase("AddPartWeaponMissile")) {
         String[] s = data.split("\\s*,\\s*");
         if (s.length >= 7) {
            float rx = 0.0F;
            float ry = 0.0F;
            float rz = 0.0F;
            float rb = 0.0F;
            boolean isRot = item.equalsIgnoreCase("AddPartRotWeapon") || item.equalsIgnoreCase("AddPartTurretRotWeapon");
            boolean isMissile = item.equalsIgnoreCase("AddPartWeaponMissile");
            boolean turret = item.equalsIgnoreCase("AddPartTurretWeapon") || item.equalsIgnoreCase("AddPartTurretRotWeapon");
            if (isRot) {
               rx = s.length >= 10 ? this.toFloat(s[7]) : 0.0F;
               ry = s.length >= 10 ? this.toFloat(s[8]) : 0.0F;
               rz = s.length >= 10 ? this.toFloat(s[9]) : -1.0F;
            } else {
               rb = s.length >= 8 ? this.toFloat(s[7]) : 0.0F;
            }

            MCH_AircraftInfo.PartWeapon w = new MCH_AircraftInfo.PartWeapon(
               this.splitParamSlash(s[0].toLowerCase().trim()),
               isRot,
               isMissile,
               this.toBool(s[1]),
               this.toBool(s[2]),
               this.toBool(s[3]),
               this.toFloat(s[4]),
               this.toFloat(s[5]),
               this.toFloat(s[6]),
               "weapon" + this.partWeapon.size(),
               rx,
               ry,
               rz,
               rb,
               turret
            );
            this.lastWeaponPart = w;
            this.partWeapon.add(w);
         }
      } else if (item.equalsIgnoreCase("AddPartWeaponChild")) {
         String[] s = data.split("\\s*,\\s*");
         if (s.length >= 5 && this.lastWeaponPart != null) {
            float rb = s.length >= 6 ? this.toFloat(s[5]) : 0.0F;
            MCH_AircraftInfo.PartWeaponChild w = new MCH_AircraftInfo.PartWeaponChild(
               this.lastWeaponPart.name,
               this.toBool(s[0]),
               this.toBool(s[1]),
               this.toFloat(s[2]),
               this.toFloat(s[3]),
               this.toFloat(s[4]),
               this.lastWeaponPart.modelName + "_" + this.lastWeaponPart.child.size(),
               0.0F,
               0.0F,
               0.0F,
               rb
            );
            this.lastWeaponPart.child.add(w);
         }
      } else if (item.compareTo("addrecipe") == 0 || item.compareTo("addshapelessrecipe") == 0) {
         this.isShapedRecipe = item.compareTo("addrecipe") == 0;
         this.recipeString.add(data.toUpperCase());
      } else if (item.compareTo("maxhp") == 0) {
         this.maxHp = this.toInt(data, 1, 100000);
      } else if (item.compareTo("inventorysize") == 0) {
         this.inventorySize = this.toInt(data, 0, 54);
      } else if (item.compareTo("damagefactor") == 0) {
         this.damageFactor = this.toFloat(data, 0.0F, 1.0F);
      } else if (item.equalsIgnoreCase("SubmergedDamageHeight")) {
         this.submergedDamageHeight = this.toFloat(data, -1000.0F, 1000.0F);
      } else if (item.equalsIgnoreCase("ArmorDamageFactor")) {
         this.armorDamageFactor = this.toFloat(data, 0.0F, 10000.0F);
      } else if (item.equalsIgnoreCase("ArmorMinDamage")) {
         this.armorMinDamage = this.toFloat(data, 0.0F, 1000000.0F);
      } else if (item.equalsIgnoreCase("ArmorMaxDamage")) {
         this.armorMaxDamage = this.toFloat(data, 0.0F, 1000000.0F);
      } else if (item.equalsIgnoreCase("FlareType")) {
         String[] s = data.split("\\s*,\\s*");
         this.flare.types = new int[s.length];

         for (int i = 0; i < s.length; i++) {
            this.flare.types[i] = this.toInt(s[i], 1, 10);
         }
      } else if (item.equalsIgnoreCase("FlareOption")) {
         String[] s = this.splitParam(data);
         if (s.length >= 3) {
            this.flare.pos = this.toVec3(s[0], s[1], s[2]);
         }
      } else if (item.equalsIgnoreCase("Sound")) {
         this.soundMove = data.toLowerCase();
      } else if (item.equalsIgnoreCase("SoundRange")) {
         this.soundRange = this.toFloat(data, 1.0F, 1000.0F);
      } else if (item.equalsIgnoreCase("SoundVolume")) {
         this.soundVolume = this.toFloat(data, 0.0F, 10.0F);
      } else if (item.equalsIgnoreCase("SoundPitch")) {
         this.soundPitch = this.toFloat(data, 0.0F, 10.0F);
      } else if (item.equalsIgnoreCase("UAV")) {
         this.isUAV = this.toBool(data);
         this.isSmallUAV = false;
      } else if (item.equalsIgnoreCase("SmallUAV")) {
         this.isUAV = this.toBool(data);
         this.isSmallUAV = true;
      } else if (item.equalsIgnoreCase("TargetDrone")) {
         this.isTargetDrone = this.toBool(data);
      } else if (item.compareTo("autopilotrot") == 0) {
         this.autoPilotRot = this.toFloat(data, -5.0F, 5.0F);
      } else if (item.compareTo("ongroundpitch") == 0) {
         this.onGroundPitch = -this.toFloat(data, -90.0F, 90.0F);
      } else if (item.compareTo("enablegunnermode") == 0) {
         this.isEnableGunnerMode = this.toBool(data);
      } else if (item.compareTo("hideentity") == 0) {
         this.hideEntity = this.toBool(data);
      } else if (item.equalsIgnoreCase("SmoothShading")) {
         this.smoothShading = this.toBool(data);
      } else if (item.compareTo("concurrentgunnermode") == 0) {
         this.isEnableConcurrentGunnerMode = this.toBool(data);
      } else if (item.equalsIgnoreCase("AddPartWeaponBay") || item.equalsIgnoreCase("AddPartSlideWeaponBay")) {
         boolean slide = item.equalsIgnoreCase("AddPartSlideWeaponBay");
         String[] s = data.split("\\s*,\\s*");
         MCH_AircraftInfo.WeaponBay n = null;
         if (slide) {
            if (s.length >= 4) {
               n = new MCH_AircraftInfo.WeaponBay(
                  s[0].trim().toLowerCase(),
                  this.toFloat(s[1]),
                  this.toFloat(s[2]),
                  this.toFloat(s[3]),
                  0.0F,
                  0.0F,
                  0.0F,
                  90.0F,
                  "wb" + this.partWeaponBay.size(),
                  slide
               );
               this.partWeaponBay.add(n);
            }
         } else if (s.length >= 7) {
            float mx = s.length >= 8 ? this.toFloat(s[7], -180.0F, 180.0F) : 90.0F;
            n = new MCH_AircraftInfo.WeaponBay(
               s[0].trim().toLowerCase(),
               this.toFloat(s[1]),
               this.toFloat(s[2]),
               this.toFloat(s[3]),
               this.toFloat(s[4]),
               this.toFloat(s[5]),
               this.toFloat(s[6]),
               mx / 90.0F,
               "wb" + this.partWeaponBay.size(),
               slide
            );
            this.partWeaponBay.add(n);
         }
      } else if (item.compareTo("addparthatch") == 0 || item.compareTo("addpartslidehatch") == 0) {
         boolean slide = item.compareTo("addpartslidehatch") == 0;
         String[] s = data.split("\\s*,\\s*");
         MCH_AircraftInfo.Hatch n = null;
         if (slide) {
            if (s.length >= 3) {
               n = new MCH_AircraftInfo.Hatch(
                  this.toFloat(s[0]), this.toFloat(s[1]), this.toFloat(s[2]), 0.0F, 0.0F, 0.0F, 90.0F, "hatch" + this.hatchList.size(), slide
               );
               this.hatchList.add(n);
            }
         } else if (s.length >= 6) {
            float mx = s.length >= 7 ? this.toFloat(s[6], -180.0F, 180.0F) : 90.0F;
            n = new MCH_AircraftInfo.Hatch(
               this.toFloat(s[0]),
               this.toFloat(s[1]),
               this.toFloat(s[2]),
               this.toFloat(s[3]),
               this.toFloat(s[4]),
               this.toFloat(s[5]),
               mx,
               "hatch" + this.hatchList.size(),
               slide
            );
            this.hatchList.add(n);
         }
      } else if (item.compareTo("addpartcanopy") == 0 || item.compareTo("addpartslidecanopy") == 0) {
         String[] s = data.split("\\s*,\\s*");
         boolean slide = item.compareTo("addpartslidecanopy") == 0;
         int canopyNum = this.canopyList.size();
         if (canopyNum > 0) {
            canopyNum--;
         }

         if (slide) {
            if (s.length >= 3) {
               MCH_AircraftInfo.Canopy c = new MCH_AircraftInfo.Canopy(
                  this.toFloat(s[0]), this.toFloat(s[1]), this.toFloat(s[2]), 0.0F, 0.0F, 0.0F, 90.0F, "canopy" + canopyNum, slide
               );
               this.canopyList.add(c);
               if (canopyNum == 0) {
                  c = new MCH_AircraftInfo.Canopy(this.toFloat(s[0]), this.toFloat(s[1]), this.toFloat(s[2]), 0.0F, 0.0F, 0.0F, 90.0F, "canopy", slide);
                  this.canopyList.add(c);
               }
            }
         } else if (s.length >= 6) {
            float mx = s.length >= 7 ? this.toFloat(s[6], -180.0F, 180.0F) : 90.0F;
            mx /= 90.0F;
            MCH_AircraftInfo.Canopy c = new MCH_AircraftInfo.Canopy(
               this.toFloat(s[0]),
               this.toFloat(s[1]),
               this.toFloat(s[2]),
               this.toFloat(s[3]),
               this.toFloat(s[4]),
               this.toFloat(s[5]),
               mx,
               "canopy" + canopyNum,
               slide
            );
            this.canopyList.add(c);
            if (canopyNum == 0) {
               c = new MCH_AircraftInfo.Canopy(
                  this.toFloat(s[0]), this.toFloat(s[1]), this.toFloat(s[2]), this.toFloat(s[3]), this.toFloat(s[4]), this.toFloat(s[5]), mx, "canopy", slide
               );
               this.canopyList.add(c);
            }
         }
      } else if (item.equalsIgnoreCase("AddPartLG")
         || item.equalsIgnoreCase("AddPartSlideRotLG")
         || item.equalsIgnoreCase("AddPartLGRev")
         || item.equalsIgnoreCase("AddPartLGHatch")) {
         String[] s = data.split("\\s*,\\s*");
         if (!item.equalsIgnoreCase("AddPartSlideRotLG") && s.length >= 6) {
            float maxRot = s.length >= 7 ? this.toFloat(s[6], -180.0F, 180.0F) : 90.0F;
            maxRot /= 90.0F;
            MCH_AircraftInfo.LandingGear n = new MCH_AircraftInfo.LandingGear(
               this.toFloat(s[0]),
               this.toFloat(s[1]),
               this.toFloat(s[2]),
               this.toFloat(s[3]),
               this.toFloat(s[4]),
               this.toFloat(s[5]),
               "lg" + this.landingGear.size(),
               maxRot,
               item.equalsIgnoreCase("AddPartLgRev"),
               item.equalsIgnoreCase("AddPartLGHatch")
            );
            if (s.length >= 8) {
               n.enableRot2 = true;
               n.maxRotFactor2 = s.length >= 11 ? this.toFloat(s[10], -180.0F, 180.0F) : 90.0F;
               n.maxRotFactor2 /= 90.0F;
               n.rot2 = new Vec3d(this.toFloat(s[7]), this.toFloat(s[8]), this.toFloat(s[9]));
            }

            this.landingGear.add(n);
         }

         if (item.equalsIgnoreCase("AddPartSlideRotLG") && s.length >= 9) {
            float maxRot = s.length >= 10 ? this.toFloat(s[9], -180.0F, 180.0F) : 90.0F;
            maxRot /= 90.0F;
            MCH_AircraftInfo.LandingGear n = new MCH_AircraftInfo.LandingGear(
               this.toFloat(s[3]),
               this.toFloat(s[4]),
               this.toFloat(s[5]),
               this.toFloat(s[6]),
               this.toFloat(s[7]),
               this.toFloat(s[8]),
               "lg" + this.landingGear.size(),
               maxRot,
               false,
               false
            );
            n.slide = new Vec3d(this.toFloat(s[0]), this.toFloat(s[1]), this.toFloat(s[2]));
            this.landingGear.add(n);
         }
      } else if (item.equalsIgnoreCase("AddPartThrottle")) {
         String[] s = data.split("\\s*,\\s*");
         if (s.length >= 7) {
            float x = s.length >= 8 ? this.toFloat(s[7]) : 0.0F;
            float y = s.length >= 9 ? this.toFloat(s[8]) : 0.0F;
            float z = s.length >= 10 ? this.toFloat(s[9]) : 0.0F;
            MCH_AircraftInfo.Throttle c = new MCH_AircraftInfo.Throttle(
               this.toFloat(s[0]),
               this.toFloat(s[1]),
               this.toFloat(s[2]),
               this.toFloat(s[3]),
               this.toFloat(s[4]),
               this.toFloat(s[5]),
               this.toFloat(s[6]),
               "throttle" + this.partThrottle.size(),
               x,
               y,
               z
            );
            this.partThrottle.add(c);
         }
      } else if (item.equalsIgnoreCase("AddPartRotation")) {
         String[] s = data.split("\\s*,\\s*");
         if (s.length >= 7) {
            boolean always = s.length >= 8 ? this.toBool(s[7]) : true;
            MCH_AircraftInfo.RotPart c = new MCH_AircraftInfo.RotPart(
               this.toFloat(s[0]),
               this.toFloat(s[1]),
               this.toFloat(s[2]),
               this.toFloat(s[3]),
               this.toFloat(s[4]),
               this.toFloat(s[5]),
               this.toFloat(s[6]),
               always,
               "rotpart" + this.partThrottle.size()
            );
            this.partRotPart.add(c);
         }
      } else if (item.compareTo("addpartcamera") == 0) {
         String[] s = data.split("\\s*,\\s*");
         if (s.length >= 3) {
            boolean ys = s.length >= 4 ? this.toBool(s[3]) : true;
            boolean ps = s.length >= 5 ? this.toBool(s[4]) : false;
            MCH_AircraftInfo.Camera c = new MCH_AircraftInfo.Camera(
               this.toFloat(s[0]), this.toFloat(s[1]), this.toFloat(s[2]), 0.0F, -1.0F, 0.0F, "camera" + this.cameraList.size(), ys, ps
            );
            this.cameraList.add(c);
         }
      } else if (item.equalsIgnoreCase("AddPartWheel")) {
         String[] s = this.splitParam(data);
         if (s.length >= 3) {
            float rd = s.length >= 4 ? this.toFloat(s[3], -1800.0F, 1800.0F) : 0.0F;
            float rx = s.length >= 7 ? this.toFloat(s[4]) : 0.0F;
            float ry = s.length >= 7 ? this.toFloat(s[5]) : 1.0F;
            float rz = s.length >= 7 ? this.toFloat(s[6]) : 0.0F;
            float px = s.length >= 10 ? this.toFloat(s[7]) : this.toFloat(s[0]);
            float py = s.length >= 10 ? this.toFloat(s[8]) : this.toFloat(s[1]);
            float pz = s.length >= 10 ? this.toFloat(s[9]) : this.toFloat(s[2]);
            this.partWheel
               .add(
                  new MCH_AircraftInfo.PartWheel(
                     this.toFloat(s[0]), this.toFloat(s[1]), this.toFloat(s[2]), rx, ry, rz, rd, px, py, pz, "wheel" + this.partWheel.size()
                  )
               );
         }
      } else if (item.equalsIgnoreCase("AddPartSteeringWheel")) {
         String[] s = this.splitParam(data);
         if (s.length >= 7) {
            this.partSteeringWheel
               .add(
                  new MCH_AircraftInfo.PartWheel(
                     this.toFloat(s[0]),
                     this.toFloat(s[1]),
                     this.toFloat(s[2]),
                     this.toFloat(s[3]),
                     this.toFloat(s[4]),
                     this.toFloat(s[5]),
                     this.toFloat(s[6]),
                     "steering_wheel" + this.partSteeringWheel.size()
                  )
               );
         }
      } else if (item.equalsIgnoreCase("AddTrackRoller")) {
         String[] s = this.splitParam(data);
         if (s.length >= 3) {
            this.partTrackRoller
               .add(new MCH_AircraftInfo.TrackRoller(this.toFloat(s[0]), this.toFloat(s[1]), this.toFloat(s[2]), "track_roller" + this.partTrackRoller.size()));
         }
      } else if (item.equalsIgnoreCase("AddCrawlerTrack")) {
         this.partCrawlerTrack.add(this.createCrawlerTrack(data, "crawler_track" + this.partCrawlerTrack.size()));
      } else if (item.equalsIgnoreCase("PivotTurnThrottle")) {
         this.pivotTurnThrottle = this.toFloat(data, 0.0F, 1.0F);
      } else if (item.equalsIgnoreCase("TrackRollerRot")) {
         this.trackRollerRot = this.toFloat(data, -10000.0F, 10000.0F);
      } else if (item.equalsIgnoreCase("PartWheelRot")) {
         this.partWheelRot = this.toFloat(data, -10000.0F, 10000.0F);
      } else if (item.compareTo("camerazoom") == 0) {
         this.cameraZoom = this.toInt(data, 1, 10);
      } else if (item.equalsIgnoreCase("DefaultFreelook")) {
         this.defaultFreelook = this.toBool(data);
      } else if (item.equalsIgnoreCase("BoundingBox")) {
         String[] s = data.split("\\s*,\\s*");
         if (s.length >= 5) {
            float df = s.length >= 6 ? this.toFloat(s[5]) : 1.0F;
            MCH_BoundingBox c = new MCH_BoundingBox(this.toFloat(s[0]), this.toFloat(s[1]), this.toFloat(s[2]), this.toFloat(s[3]), this.toFloat(s[4]), df);
            this.extraBoundingBox.add(c);
            if (c.boundingBox.maxY() > this.markerHeight) {
               this.markerHeight = (float)c.boundingBox.maxY();
            }

            this.markerWidth = (float)Math.max(this.markerWidth, Math.abs(c.boundingBox.maxX()) / 2.0);
            this.markerWidth = (float)Math.max(this.markerWidth, Math.abs(c.boundingBox.minX()) / 2.0);
            this.markerWidth = (float)Math.max(this.markerWidth, Math.abs(c.boundingBox.maxZ()) / 2.0);
            this.markerWidth = (float)Math.max(this.markerWidth, Math.abs(c.boundingBox.minZ()) / 2.0);
            this.bbZmin = (float)Math.min(this.bbZmin, c.boundingBox.minZ());
            this.bbZmax = (float)Math.min(this.bbZmax, c.boundingBox.maxZ());
         }
      } else if (item.equalsIgnoreCase("RotorSpeed")) {
         this.rotorSpeed = this.toFloat(data, -10000.0F, 10000.0F);
         if (this.rotorSpeed > 0.01) {
            this.rotorSpeed = (float)(this.rotorSpeed - 0.01);
         }

         if (this.rotorSpeed < -0.01) {
            this.rotorSpeed = (float)(this.rotorSpeed + 0.01);
         }
      } else if (item.equalsIgnoreCase("OnGroundPitchFactor")) {
         this.onGroundPitchFactor = this.toFloat(data, 0.0F, 180.0F);
      } else if (item.equalsIgnoreCase("OnGroundRollFactor")) {
         this.onGroundRollFactor = this.toFloat(data, 0.0F, 180.0F);
      }
   }

   public MCH_AircraftInfo.CrawlerTrack createCrawlerTrack(String data, String name) {
      String[] s = this.splitParam(data);
      int PC = s.length - 3;
      boolean REV = this.toBool(s[0]);
      float LEN = this.toFloat(s[1], 0.001F, 1000.0F) * 0.9F;
      float Z = this.toFloat(s[2]);
      if (PC < 4) {
         return null;
      }

      double[] cx = new double[PC];
      double[] cy = new double[PC];

      for (int i = 0; i < PC; i++) {
         int idx = !REV ? i : PC - i - 1;
         String[] xy = this.splitParamSlash(s[3 + idx]);
         cx[i] = this.toFloat(xy[0]);
         cy[i] = this.toFloat(xy[1]);
      }

      List<MCH_AircraftInfo.CrawlerTrackPrm> lp = new ArrayList<>();
      lp.add(new MCH_AircraftInfo.CrawlerTrackPrm((float)cx[0], (float)cy[0]));
      double dist = 0.0;

      for (int i = 0; i < PC; i++) {
         double x = cx[(i + 1) % PC] - cx[i];
         double y = cy[(i + 1) % PC] - cy[i];
         dist += Math.sqrt(x * x + y * y);
         double dist2 = dist;

         for (int j = 1; dist >= LEN; j++) {
            lp.add(new MCH_AircraftInfo.CrawlerTrackPrm((float)(cx[i] + x * (LEN * j / dist2)), (float)(cy[i] + y * (LEN * j / dist2))));
            dist -= LEN;
         }
      }

      for (int i = 0; i < lp.size(); i++) {
         MCH_AircraftInfo.CrawlerTrackPrm pp = lp.get((i + lp.size() - 1) % lp.size());
         MCH_AircraftInfo.CrawlerTrackPrm cp = lp.get(i);
         MCH_AircraftInfo.CrawlerTrackPrm np = lp.get((i + 1) % lp.size());
         float pr = (float)(Math.atan2(pp.x - cp.x, pp.y - cp.y) * 180.0 / Math.PI);
         float nr = (float)(Math.atan2(np.x - cp.x, np.y - cp.y) * 180.0 / Math.PI);
         float ppr = (pr + 360.0F) % 360.0F;
         float nnr = nr + 180.0F;
         if ((nnr < ppr - 0.3 || nnr > ppr + 0.3) && nnr - ppr < 100.0F && nnr - ppr > -100.0F) {
            nnr = (nnr + ppr) / 2.0F;
         }

         cp.r = nnr;
      }

      MCH_AircraftInfo.CrawlerTrack c = new MCH_AircraftInfo.CrawlerTrack(name);
      c.len = LEN;
      c.cx = cx;
      c.cy = cy;
      c.lp = lp;
      c.z = Z;
      c.side = Z >= 0.0F ? 1 : 0;
      return c;
   }

   public String getTextureName() {
      String s = this.textureNameList.get(this.textureCount);
      this.textureCount = (this.textureCount + 1) % this.textureNameList.size();
      return s;
   }

   /** Texture (skin) name at {@code index}, wrapping — no side effect, unlike {@link #getTextureName()} which
    *  advances the internal counter. Index 0 is the default skin (the vehicle name); the rest are {@code AddTexture}. */
   public String getTextureName(int index) {
      return this.textureNameList.isEmpty()
         ? this.name
         : this.textureNameList.get(Math.floorMod(index, this.textureNameList.size()));
   }

   /** Number of selectable skins (default + all {@code AddTexture}). */
   public int getTextureNameCount() {
      return this.textureNameList.size();
   }

   public String getNextTextureName(String base) {
      if (this.textureNameList.size() >= 2) {
         for (int i = 0; i < this.textureNameList.size(); i++) {
            String s = this.textureNameList.get(i);
            if (s.equalsIgnoreCase(base)) {
               i = (i + 1) % this.textureNameList.size();
               return this.textureNameList.get(i);
            }
         }
      }

      return base;
   }

   @Override
   public void preReload() {
      this.textureNameList.clear();
      this.textureNameList.add(this.name);
      this.cameraList.clear();
      this.cameraPosition.clear();
      this.canopyList.clear();
      this.flare = new MCH_AircraftInfo.Flare();
      this.hatchList.clear();
      this.hudList.clear();
      this.landingGear.clear();
      this.particleSplashs.clear();
      this.searchLights.clear();
      this.partThrottle.clear();
      this.partRotPart.clear();
      this.partCrawlerTrack.clear();
      this.partTrackRoller.clear();
      this.partWheel.clear();
      this.partSteeringWheel.clear();
      this.lightHatchList.clear();
      this.partWeapon.clear();
      this.partWeaponBay.clear();
      this.repellingHooks.clear();
      this.rideRacks.clear();
      this.seatList.clear();
      this.exclusionSeatList.clear();
      this.entityRackList.clear();
      this.extraBoundingBox.clear();
      this.weaponSetList.clear();
      this.lastWeaponIndex = -1;
      this.lastWeaponType = "";
      this.lastWeaponPart = null;
      this.wheels.clear();
      this.unmountPosition = null;
   }

   public static String[] getCannotReloadItem() {
      return new String[]{
         "DisplayName", "AddDisplayName", "ItemID", "AddRecipe", "AddShapelessRecipe", "InventorySize", "Sound", "UAV", "SmallUAV", "TargetDrone", "Category"
      };
   }

   @Override
   public boolean canReloadItem(String item) {
      String[] ignoreItems = getCannotReloadItem();

      for (String s : ignoreItems) {
         if (s.equalsIgnoreCase(item)) {
            return false;
         }
      }

      return true;
   }

   public static class Camera extends MCH_AircraftInfo.DrawnPart {
      public final boolean yawSync;
      public final boolean pitchSync;

      public Camera(float px, float py, float pz, float rx, float ry, float rz, String name, boolean ys, boolean ps) {
         super(px, py, pz, rx, ry, rz, name);
         this.yawSync = ys;
         this.pitchSync = ps;
      }
   }

   public static class CameraPosition {
      public final Vec3d pos;
      public final boolean fixRot;
      public final float yaw;
      public final float pitch;

      public CameraPosition(Vec3d vec3, boolean fixRot, float yaw, float pitch) {
         this.pos = vec3;
         this.fixRot = fixRot;
         this.yaw = yaw;
         this.pitch = pitch;
      }

      public CameraPosition(Vec3d vec3) {
         this(vec3, false, 0.0F, 0.0F);
      }

      public CameraPosition() {
         this(new Vec3d(0.0, 0.0, 0.0));
      }
   }

   public static class Canopy extends MCH_AircraftInfo.DrawnPart {
      public final float maxRotFactor;
      public final boolean isSlide;

      public Canopy(float px, float py, float pz, float rx, float ry, float rz, float mr, String name, boolean slide) {
         super(px, py, pz, rx, ry, rz, name);
         this.maxRotFactor = mr;
         this.isSlide = slide;
      }
   }

   public static class CrawlerTrack extends MCH_AircraftInfo.DrawnPart {
      public float len = 0.35F;
      public double[] cx;
      public double[] cy;
      public List<MCH_AircraftInfo.CrawlerTrackPrm> lp;
      public float z;
      public int side;

      public CrawlerTrack(String name) {
         super(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, name);
      }
   }

   public static class CrawlerTrackPrm {
      float x;
      float y;
      float nx;
      float ny;
      float r;

      public CrawlerTrackPrm(float x, float y) {
         this.x = x;
         this.y = y;
      }
   }

   public static class DrawnPart {
      public final Vec3d pos;
      public final Vec3d rot;
      public final String modelName;
      public ModelHandle model;

      public DrawnPart(float px, float py, float pz, float rx, float ry, float rz, String name) {
         this.pos = new Vec3d(px, py, pz);
         this.rot = new Vec3d(rx, ry, rz);
         this.modelName = name;
         this.model = null;
      }
   }

   public static class Flare {
      public int[] types = new int[0];
      public Vec3d pos = new Vec3d(0.0, 0.0, 0.0);
   }

   public static class Hatch extends MCH_AircraftInfo.DrawnPart {
      public final float maxRotFactor;
      public final float maxRot;
      public final boolean isSlide;

      public Hatch(float px, float py, float pz, float rx, float ry, float rz, float mr, String name, boolean slide) {
         super(px, py, pz, rx, ry, rz, name);
         this.maxRot = mr;
         this.maxRotFactor = this.maxRot / 90.0F;
         this.isSlide = slide;
      }
   }

   public static class LandingGear extends MCH_AircraftInfo.DrawnPart {
      public Vec3d slide = null;
      public final float maxRotFactor;
      public boolean enableRot2;
      public Vec3d rot2;
      public float maxRotFactor2;
      public final boolean reverse;
      public final boolean hatch;

      public LandingGear(float x, float y, float z, float rx, float ry, float rz, String model, float maxRotF, boolean rev, boolean isHatch) {
         super(x, y, z, rx, ry, rz, model);
         this.maxRotFactor = maxRotF;
         this.enableRot2 = false;
         this.rot2 = new Vec3d(0.0, 0.0, 0.0);
         this.maxRotFactor2 = 0.0F;
         this.reverse = rev;
         this.hatch = isHatch;
      }
   }

   public static class PartWeapon extends MCH_AircraftInfo.DrawnPart {
      public final String[] name;
      public final boolean rotBarrel;
      public final boolean isMissile;
      public final boolean hideGM;
      public final boolean yaw;
      public final boolean pitch;
      public final float recoilBuf;
      public List<MCH_AircraftInfo.PartWeaponChild> child;
      public final boolean turret;

      public PartWeapon(
         String[] name,
         boolean rotBrl,
         boolean missile,
         boolean hgm,
         boolean y,
         boolean p,
         float px,
         float py,
         float pz,
         String modelName,
         float rx,
         float ry,
         float rz,
         float rb,
         boolean turret
      ) {
         super(px, py, pz, rx, ry, rz, modelName);
         this.name = name;
         this.rotBarrel = rotBrl;
         this.isMissile = missile;
         this.hideGM = hgm;
         this.yaw = y;
         this.pitch = p;
         this.recoilBuf = rb;
         this.child = new ArrayList<>();
         this.turret = turret;
      }
   }

   public static class PartWeaponChild extends MCH_AircraftInfo.DrawnPart {
      public final String[] name;
      public final boolean yaw;
      public final boolean pitch;
      public final float recoilBuf;

      public PartWeaponChild(String[] name, boolean y, boolean p, float px, float py, float pz, String modelName, float rx, float ry, float rz, float rb) {
         super(px, py, pz, rx, ry, rz, modelName);
         this.name = name;
         this.yaw = y;
         this.pitch = p;
         this.recoilBuf = rb;
      }
   }

   public static class PartWheel extends MCH_AircraftInfo.DrawnPart {
      final float rotDir;
      final Vec3d pos2;

      public PartWheel(float px, float py, float pz, float rx, float ry, float rz, float rd, float px2, float py2, float pz2, String name) {
         super(px, py, pz, rx, ry, rz, name);
         this.rotDir = rd;
         this.pos2 = new Vec3d(px2, py2, pz2);
      }

      public PartWheel(float px, float py, float pz, float rx, float ry, float rz, float rd, String name) {
         this(px, py, pz, rx, ry, rz, rd, px, py, pz, name);
      }
   }

   public static class ParticleSplash {
      public final int num;
      public final float acceleration;
      public final float size;
      public final Vec3d pos;
      public final int age;
      public final float motionY;
      public final float gravity;

      public ParticleSplash(Vec3d v, int nm, float siz, float acc, int ag, float my, float gr) {
         this.num = nm;
         this.pos = v;
         this.size = siz;
         this.acceleration = acc;
         this.age = ag;
         this.motionY = my;
         this.gravity = gr;
      }
   }

   public static class RepellingHook {
      final Vec3d pos;
      final int interval;

      public RepellingHook(Vec3d pos, int inv) {
         this.pos = pos;
         this.interval = inv;
      }
   }

   public static class RideRack {
      public final String name;
      public final int rackID;

      public RideRack(String n, int id) {
         this.name = n;
         this.rackID = id;
      }
   }

   public static class RotPart extends MCH_AircraftInfo.DrawnPart {
      public final float rotSpeed;
      public final boolean rotAlways;

      public RotPart(float px, float py, float pz, float rx, float ry, float rz, float mr, boolean a, String name) {
         super(px, py, pz, rx, ry, rz, name);
         this.rotSpeed = mr;
         this.rotAlways = a;
      }
   }

   public static class SearchLight {
      public final int colorStart;
      public final int colorEnd;
      public final Vec3d pos;
      public final float height;
      public final float width;
      public final float angle;
      public final boolean fixDir;
      public final float yaw;
      public final float pitch;
      public final boolean steering;
      public final float stRot;

      public SearchLight(Vec3d pos, int cs, int ce, float h, float w, boolean fix, float y, float p, boolean st, float stRot) {
         this.colorStart = cs;
         this.colorEnd = ce;
         this.pos = pos;
         this.height = h;
         this.width = w;
         this.angle = (float)(Math.atan2(w / 2.0F, h) * 180.0 / Math.PI);
         this.fixDir = fix;
         this.steering = st;
         this.yaw = y;
         this.pitch = p;
         this.stRot = stRot;
      }
   }

   public static class Throttle extends MCH_AircraftInfo.DrawnPart {
      public final Vec3d slide;
      public final float rot2;

      public Throttle(float px, float py, float pz, float rx, float ry, float rz, float rot, String name, float px2, float py2, float pz2) {
         super(px, py, pz, rx, ry, rz, name);
         this.rot2 = rot;
         this.slide = new Vec3d(px2, py2, pz2);
      }
   }

   public static class TrackRoller extends MCH_AircraftInfo.DrawnPart {
      final int side;

      public TrackRoller(float px, float py, float pz, String name) {
         super(px, py, pz, 0.0F, 0.0F, 0.0F, name);
         this.side = px >= 0.0F ? 1 : 0;
      }
   }

   public static class Weapon {
      public final Vec3d pos;
      public final float yaw;
      public final float pitch;
      public final boolean canUsePilot;
      public final int seatID;
      public final float defaultYaw;
      public final float minYaw;
      public final float maxYaw;
      public final float minPitch;
      public final float maxPitch;
      public final boolean turret;

      public Weapon(
         float x,
         float y,
         float z,
         float yaw,
         float pitch,
         boolean canPirot,
         int seatId,
         float defy,
         float mny,
         float mxy,
         float mnp,
         float mxp,
         boolean turret
      ) {
         this.pos = new Vec3d(x, y, z);
         this.yaw = yaw;
         this.pitch = pitch;
         this.canUsePilot = canPirot;
         this.seatID = seatId;
         this.defaultYaw = defy;
         this.minYaw = mny;
         this.maxYaw = mxy;
         this.minPitch = mnp;
         this.maxPitch = mxp;
         this.turret = turret;
      }
   }

   public static class WeaponBay extends MCH_AircraftInfo.DrawnPart {
      public final float maxRotFactor;
      public final boolean isSlide;
      private final String weaponName;
      public Integer[] weaponIds;

      public WeaponBay(String wn, float px, float py, float pz, float rx, float ry, float rz, float mr, String name, boolean slide) {
         super(px, py, pz, rx, ry, rz, name);
         this.maxRotFactor = mr;
         this.isSlide = slide;
         this.weaponName = wn;
         this.weaponIds = new Integer[0];
      }
   }

   public static class WeaponSet {
      public final String type;
      public ArrayList<MCH_AircraftInfo.Weapon> weapons;

      public WeaponSet(String t) {
         this.type = t;
         this.weapons = new ArrayList<>();
      }
   }

   public static class Wheel {
      public final float size;
      public final Vec3d pos;

      public Wheel(Vec3d v, float sz) {
         this.pos = v;
         this.size = sz;
      }

      public Wheel(Vec3d v) {
         this(v, 1.0F);
      }
   }
}
