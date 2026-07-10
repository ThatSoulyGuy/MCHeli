/*
 * Decompiled with CFR 0.152.
 */
package mcheli.aircraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import mcheli.MCH_BaseInfo;
import mcheli.MCH_MOD;
import mcheli.aircraft.MCH_AircraftInfo;
import mcheli.aircraft.MCH_BoundingBox;
import mcheli.aircraft.MCH_MobDropOption;
import mcheli.aircraft.MCH_SeatInfo;
import mcheli.aircraft.MCH_SeatRackInfo;
import mcheli.hud.MCH_Hud;
import mcheli.hud.MCH_HudManager;
import mcheli.weapon.MCH_WeaponInfoManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.model.IModelCustom;

/*
 * Exception performing whole class analysis ignored.
 */
public abstract class MCH_AircraftInfo
extends MCH_BaseInfo {
    public final String name;
    public String displayName;
    public HashMap<String, String> displayNameLang;
    public int itemID;
    public List<String> recipeString;
    public List<IRecipe> recipe;
    public boolean isShapedRecipe;
    public String category;
    public boolean isEnableGunnerMode;
    public int cameraZoom;
    public boolean isEnableConcurrentGunnerMode;
    public boolean isEnableNightVision;
    public boolean isEnableEntityRadar;
    public boolean isEnableEjectionSeat;
    public boolean isEnableParachuting;
    public Flare flare;
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
    public List<WeaponSet> weaponSetList;
    public List<MCH_SeatInfo> seatList;
    public List<Integer[]> exclusionSeatList;
    public List<MCH_Hud> hudList;
    public MCH_Hud hudTvMissile;
    public float damageFactor;
    public float submergedDamageHeight;
    public boolean regeneration;
    public List<MCH_BoundingBox> extraBoundingBox;
    public List<Wheel> wheels;
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
    public List<RepellingHook> repellingHooks;
    public List<RideRack> rideRacks;
    public List<ParticleSplash> particleSplashs;
    public List<SearchLight> searchLights;
    public float rotorSpeed;
    public boolean enableSeaSurfaceParticle;
    public float pivotTurnThrottle;
    public float trackRollerRot;
    public float partWheelRot;
    public float onGroundPitchFactor;
    public float onGroundRollFactor;
    public Vec3 turretPosition;
    public boolean defaultFreelook;
    public Vec3 unmountPosition;
    public float markerWidth;
    public float markerHeight;
    public float bbZmin;
    public float bbZmax;
    public float bbZ;
    public boolean alwaysCameraView;
    public List<CameraPosition> cameraPosition;
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
    public IModelCustom model;
    public List<Hatch> hatchList;
    public List<Camera> cameraList;
    public List<PartWeapon> partWeapon;
    public List<WeaponBay> partWeaponBay;
    public List<Canopy> canopyList;
    public List<LandingGear> landingGear;
    public List<Throttle> partThrottle;
    public List<RotPart> partRotPart;
    public List<CrawlerTrack> partCrawlerTrack;
    public List<TrackRoller> partTrackRoller;
    public List<PartWheel> partWheel;
    public List<PartWheel> partSteeringWheel;
    public List<Hatch> lightHatchList;
    private String lastWeaponType = "";
    private int lastWeaponIndex = -1;
    private PartWeapon lastWeaponPart;

    public abstract Item getItem();

    public ItemStack getItemStack() {
        return new ItemStack(this.getItem());
    }

    public abstract String getDirectoryName();

    public abstract String getKindName();

    public MCH_AircraftInfo(String s) {
        this.displayName = this.name = s;
        this.displayNameLang = new HashMap();
        this.itemID = 0;
        this.recipeString = new ArrayList();
        this.recipe = new ArrayList();
        this.isShapedRecipe = true;
        this.category = "zzz";
        this.isEnableGunnerMode = false;
        this.isEnableConcurrentGunnerMode = false;
        this.isEnableNightVision = false;
        this.isEnableEntityRadar = false;
        this.isEnableEjectionSeat = false;
        this.isEnableParachuting = false;
        this.flare = new Flare(this);
        this.weaponSetList = new ArrayList();
        this.seatList = new ArrayList();
        this.exclusionSeatList = new ArrayList();
        this.hudList = new ArrayList();
        this.hudTvMissile = null;
        this.bodyHeight = 0.7f;
        this.bodyWidth = 2.0f;
        this.isFloat = false;
        this.floatOffset = 0.0f;
        this.gravity = -0.04f;
        this.gravityInWater = -0.04f;
        this.maxHp = 50;
        this.damageFactor = 0.2f;
        this.submergedDamageHeight = 0.0f;
        this.inventorySize = 0;
        this.armorDamageFactor = 1.0f;
        this.armorMaxDamage = 100000.0f;
        this.armorMinDamage = 0.0f;
        this.enableBack = false;
        this.isUAV = false;
        this.isSmallUAV = false;
        this.isTargetDrone = false;
        this.autoPilotRot = -0.6f;
        this.regeneration = false;
        this.onGroundPitch = 0.0f;
        this.canMoveOnGround = true;
        this.canRotOnGround = true;
        this.cameraZoom = this.getDefaultMaxZoom();
        this.extraBoundingBox = new ArrayList();
        this.maxFuel = 0;
        this.fuelConsumption = 1.0f;
        this.fuelSupplyRange = 0.0f;
        this.ammoSupplyRange = 0.0f;
        this.repairOtherVehiclesRange = 0.0f;
        this.repairOtherVehiclesValue = 10;
        this.stealth = 0.0f;
        this.canRide = true;
        this.entityWidth = 1.0f;
        this.entityHeight = 1.0f;
        this.entityPitch = 0.0f;
        this.entityRoll = 0.0f;
        this.stepHeight = this.getDefaultStepHeight();
        this.entityRackList = new ArrayList();
        this.mobSeatNum = 0;
        this.entityRackNum = 0;
        this.mobDropOption = new MCH_MobDropOption();
        this.repellingHooks = new ArrayList();
        this.rideRacks = new ArrayList();
        this.particleSplashs = new ArrayList();
        this.searchLights = new ArrayList();
        this.markerHeight = 1.0f;
        this.markerWidth = 2.0f;
        this.bbZmax = 1.0f;
        this.bbZmin = -1.0f;
        this.rotorSpeed = this.getDefaultRotorSpeed();
        this.wheels = this.getDefaultWheelList();
        this.onGroundPitchFactor = 0.0f;
        this.onGroundRollFactor = 0.0f;
        this.turretPosition = Vec3.func_72443_a((double)0.0, (double)0.0, (double)0.0);
        this.defaultFreelook = false;
        this.unmountPosition = null;
        this.cameraPosition = new ArrayList();
        this.alwaysCameraView = false;
        this.cameraRotationSpeed = 1000.0f;
        this.speed = 0.1f;
        this.motionFactor = 0.96f;
        this.mobilityYaw = 1.0f;
        this.mobilityPitch = 1.0f;
        this.mobilityRoll = 1.0f;
        this.mobilityYawOnGround = 1.0f;
        this.minRotationPitch = this.getMinRotationPitch();
        this.maxRotationPitch = this.getMaxRotationPitch();
        this.minRotationRoll = this.getMinRotationPitch();
        this.maxRotationRoll = this.getMaxRotationPitch();
        this.limitRotation = false;
        this.throttleUpDown = 1.0f;
        this.throttleUpDownOnEntity = 2.0f;
        this.pivotTurnThrottle = 0.0f;
        this.trackRollerRot = 30.0f;
        this.partWheelRot = 30.0f;
        this.textureNameList = new ArrayList();
        this.textureNameList.add(this.name);
        this.textureCount = 0;
        this.particlesScale = 1.0f;
        this.enableSeaSurfaceParticle = false;
        this.hideEntity = false;
        this.smoothShading = true;
        this.soundMove = "";
        this.soundPitch = 1.0f;
        this.soundVolume = 1.0f;
        this.soundRange = this.getDefaultSoundRange();
        this.model = null;
        this.hatchList = new ArrayList();
        this.cameraList = new ArrayList();
        this.partWeapon = new ArrayList();
        this.lastWeaponPart = null;
        this.partWeaponBay = new ArrayList();
        this.canopyList = new ArrayList();
        this.landingGear = new ArrayList();
        this.partThrottle = new ArrayList();
        this.partRotPart = new ArrayList();
        this.partCrawlerTrack = new ArrayList();
        this.partTrackRoller = new ArrayList();
        this.partWheel = new ArrayList();
        this.partSteeringWheel = new ArrayList();
        this.lightHatchList = new ArrayList();
    }

    public float getDefaultSoundRange() {
        return 100.0f;
    }

    public List<Wheel> getDefaultWheelList() {
        return new ArrayList<Wheel>();
    }

    public float getDefaultRotorSpeed() {
        return 0.0f;
    }

    private float getDefaultStepHeight() {
        return 0.0f;
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

    public boolean isValidData() throws Exception {
        if (this.cameraPosition.size() <= 0) {
            this.cameraPosition.add(new CameraPosition(this));
        }
        this.bbZ = (this.bbZmax + this.bbZmin) / 2.0f;
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
                MCH_SeatInfo s = new MCH_SeatInfo(Vec3.func_72443_a((double)0.0, (double)0.0, (double)0.0), false);
                this.seatList.add(s);
            }
        }
        this.mobSeatNum = this.seatList.size();
        this.entityRackNum = this.entityRackList.size();
        if (this.getNumSeat() < 1) {
            throw new Exception();
        }
        if (this.getNumHud() < this.getNumSeat()) {
            for (int i = this.getNumHud(); i < this.getNumSeat(); ++i) {
                this.hudList.add(MCH_HudManager.get((String)this.getDefaultHudName(i)));
            }
        }
        if (this.getNumSeat() == 1 && this.getNumHud() == 1) {
            this.hudList.add(MCH_HudManager.get((String)this.getDefaultHudName(1)));
        }
        for (MCH_SeatRackInfo ei : this.entityRackList) {
            this.seatList.add(ei);
        }
        this.entityRackList.clear();
        if (this.hudTvMissile == null) {
            this.hudTvMissile = MCH_HudManager.get((String)"tv_missile");
        }
        if (this.textureNameList.size() < 1) {
            throw new Exception();
        }
        if (this.itemID <= 0) {
            // empty if block
        }
        for (int i = 0; i < this.partWeaponBay.size(); ++i) {
            WeaponBay wb = (WeaponBay)this.partWeaponBay.get(i);
            String[] weaponNames = WeaponBay.access$000((WeaponBay)wb).split("\\s*/\\s*");
            if (weaponNames.length <= 0) {
                this.partWeaponBay.remove(i);
                continue;
            }
            ArrayList<Integer> list = new ArrayList<Integer>();
            for (String s : weaponNames) {
                int id = this.getWeaponIdByName(s);
                if (id < 0) continue;
                list.add(id);
            }
            if (list.size() <= 0) {
                this.partWeaponBay.remove(i);
                continue;
            }
            ((WeaponBay)this.partWeaponBay.get((int)i)).weaponIds = list.toArray(new Integer[0]);
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
        return 0.8f;
    }

    public float getMinRotationPitch() {
        return -89.9f;
    }

    public float getMaxRotationPitch() {
        return 80.0f;
    }

    public float getMinRotationRoll() {
        return -80.0f;
    }

    public float getMaxRotationRoll() {
        return 80.0f;
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

    public WeaponSet getWeaponSetById(int id) {
        return id >= 0 && id < this.weaponSetList.size() ? (WeaponSet)this.weaponSetList.get(id) : null;
    }

    public Weapon getWeaponById(int id) {
        WeaponSet ws = this.getWeaponSetById(id);
        return ws != null ? (Weapon)ws.weapons.get(0) : null;
    }

    public int getWeaponIdByName(String s) {
        for (int i = 0; i < this.weaponSetList.size(); ++i) {
            if (!((WeaponSet)this.weaponSetList.get((int)i)).type.equalsIgnoreCase(s)) continue;
            return i;
        }
        return -1;
    }

    public Weapon getWeaponByName(String s) {
        for (int i = 0; i < this.weaponSetList.size(); ++i) {
            if (!((WeaponSet)this.weaponSetList.get((int)i)).type.equalsIgnoreCase(s)) continue;
            return this.getWeaponById(i);
        }
        return null;
    }

    public int getWeaponNum() {
        return this.weaponSetList.size();
    }

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
            this.fuelConsumption = this.toFloat(data, 0.0f, 10000.0f);
        } else if (item.equalsIgnoreCase("FuelSupplyRange")) {
            this.fuelSupplyRange = this.toFloat(data, 0.0f, 1000.0f);
        } else if (item.equalsIgnoreCase("AmmoSupplyRange")) {
            this.ammoSupplyRange = this.toFloat(data, 0.0f, 1000.0f);
        } else if (item.equalsIgnoreCase("RepairOtherVehicles")) {
            String[] s = this.splitParam(data);
            if (s.length >= 1) {
                this.repairOtherVehiclesRange = this.toFloat(s[0], 0.0f, 1000.0f);
                if (s.length >= 2) {
                    this.repairOtherVehiclesValue = this.toInt(s[1], 0, 10000000);
                }
            }
        } else if (item.compareTo("itemid") == 0) {
            this.itemID = this.toInt(data, 0, 65535);
        } else if (item.compareTo("addtexture") == 0) {
            this.textureNameList.add(data.toLowerCase());
        } else if (item.compareTo("particlesscale") == 0) {
            this.particlesScale = this.toFloat(data, 0.0f, 50.0f);
        } else if (item.equalsIgnoreCase("EnableSeaSurfaceParticle")) {
            this.enableSeaSurfaceParticle = this.toBool(data);
        } else if (item.equalsIgnoreCase("AddParticleSplash")) {
            String[] s = this.splitParam(data);
            if (s.length >= 3) {
                Vec3 v = this.toVec3(s[0], s[1], s[2]);
                int num = s.length >= 4 ? this.toInt(s[3], 1, 100) : 2;
                float size = s.length >= 5 ? this.toFloat(s[4]) : 2.0f;
                float acc = s.length >= 6 ? this.toFloat(s[5]) : 1.0f;
                int age = s.length >= 7 ? this.toInt(s[6], 1, 100000) : 80;
                float motionY = s.length >= 8 ? this.toFloat(s[7]) : 0.01f;
                float gravity = s.length >= 9 ? this.toFloat(s[8]) : 0.0f;
                this.particleSplashs.add(new ParticleSplash(this, v, num, size, acc, age, motionY, gravity));
            }
        } else if (item.equalsIgnoreCase("AddSearchLight") || item.equalsIgnoreCase("AddFixedSearchLight") || item.equalsIgnoreCase("AddSteeringSearchLight")) {
            String[] s = this.splitParam(data);
            if (s.length >= 7) {
                Vec3 v = this.toVec3(s[0], s[1], s[2]);
                int cs = this.hex2dec(s[3]);
                int ce = this.hex2dec(s[4]);
                float h = this.toFloat(s[5]);
                float w = this.toFloat(s[6]);
                float yaw = s.length >= 8 ? this.toFloat(s[7]) : 0.0f;
                float pitch = s.length >= 9 ? this.toFloat(s[8]) : 0.0f;
                float stRot = s.length >= 10 ? this.toFloat(s[9]) : 0.0f;
                boolean fixDir = !item.equalsIgnoreCase("AddSearchLight");
                boolean steering = item.equalsIgnoreCase("AddSteeringSearchLight");
                this.searchLights.add(new SearchLight(this, v, cs, ce, h, w, fixDir, yaw, pitch, steering, stRot));
            }
        } else if (item.equalsIgnoreCase("AddPartLightHatch")) {
            String[] s = this.splitParam(data);
            if (s.length >= 6) {
                float mx = s.length >= 7 ? this.toFloat(s[6], -1800.0f, 1800.0f) : 90.0f;
                this.lightHatchList.add(new Hatch(this, this.toFloat(s[0]), this.toFloat(s[1]), this.toFloat(s[2]), this.toFloat(s[3]), this.toFloat(s[4]), this.toFloat(s[5]), mx, "light_hatch" + this.lightHatchList.size(), false));
            }
        } else if (item.equalsIgnoreCase("AddRepellingHook")) {
            String[] s = this.splitParam(data);
            if (s != null && s.length >= 3) {
                int inv = s.length >= 4 ? this.toInt(s[3], 1, 100000) : 10;
                this.repellingHooks.add(new RepellingHook(this, this.toVec3(s[0], s[1], s[2]), inv));
            }
        } else if (item.equalsIgnoreCase("AddRack")) {
            String[] s = data.toLowerCase().split("\\s*,\\s*");
            if (s != null && s.length >= 7) {
                String[] names = s[0].split("\\s*/\\s*");
                float range = s.length >= 8 ? this.toFloat(s[7]) : 6.0f;
                float para = s.length >= 9 ? this.toFloat(s[8], 0.0f, 1000000.0f) : 20.0f;
                float yaw = s.length >= 10 ? this.toFloat(s[9]) : 0.0f;
                float pitch = s.length >= 11 ? this.toFloat(s[10]) : 0.0f;
                boolean rs = s.length >= 12 ? this.toBool(s[11]) : false;
                this.entityRackList.add(new MCH_SeatRackInfo(names, this.toDouble(s[1]), this.toDouble(s[2]), this.toDouble(s[3]), new CameraPosition(this, this.toVec3(s[4], s[5], s[6]).func_72441_c(0.0, 1.5, 0.0)), range, para, yaw, pitch, rs));
            }
        } else if (item.equalsIgnoreCase("RideRack")) {
            String[] s = this.splitParam(data);
            if (s.length >= 2) {
                RideRack r = new RideRack(this, s[0].trim().toLowerCase(), this.toInt(s[1], 1, 10000));
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
            Vec3 p = this.toVec3(s[0], s[1], s[2]);
            if (item.equalsIgnoreCase("AddSeat")) {
                boolean rs = s.length >= 4 ? this.toBool(s[3]) : false;
                MCH_SeatInfo seat = new MCH_SeatInfo(p, rs);
                this.seatList.add(seat);
            } else {
                MCH_SeatInfo seat;
                if (s.length >= 6) {
                    boolean sg;
                    CameraPosition c = new CameraPosition(this, this.toVec3(s[3], s[4], s[5]));
                    boolean bl = sg = s.length >= 7 ? this.toBool(s[6]) : false;
                    if (item.equalsIgnoreCase("AddGunnerSeat")) {
                        if (s.length >= 9) {
                            float maxPitch;
                            float minPitch = this.toFloat(s[7], -90.0f, 90.0f);
                            if (minPitch > (maxPitch = this.toFloat(s[8], -90.0f, 90.0f))) {
                                float t = minPitch;
                                minPitch = maxPitch;
                                maxPitch = t;
                            }
                            boolean rs = s.length >= 10 ? this.toBool(s[9]) : false;
                            seat = new MCH_SeatInfo(p, true, c, true, sg, false, 0.0f, 0.0f, minPitch, maxPitch, rs);
                        } else {
                            seat = new MCH_SeatInfo(p, true, c, true, sg, false, 0.0f, 0.0f, false);
                        }
                    } else {
                        boolean fixRot = s.length >= 9;
                        float fixYaw = fixRot ? this.toFloat(s[7]) : 0.0f;
                        float fixPitch = fixRot ? this.toFloat(s[8]) : 0.0f;
                        boolean rs = s.length >= 10 ? this.toBool(s[9]) : false;
                        seat = new MCH_SeatInfo(p, true, c, true, sg, fixRot, fixYaw, fixPitch, rs);
                    }
                } else {
                    seat = new MCH_SeatInfo(p, true, new CameraPosition(this), false, false, false, 0.0f, 0.0f, false);
                }
                this.seatList.add(seat);
            }
        } else if (item.equalsIgnoreCase("SetWheelPos")) {
            String[] s = this.splitParam(data);
            if (s.length >= 4) {
                float x = Math.abs(this.toFloat(s[0]));
                float y = this.toFloat(s[1]);
                this.wheels.clear();
                for (int i = 2; i < s.length; ++i) {
                    this.wheels.add(new Wheel(this, Vec3.func_72443_a((double)x, (double)y, (double)this.toFloat(s[i]))));
                }
                Collections.sort(this.wheels, new /* Unavailable Anonymous Inner Class!! */);
            }
        } else if (item.equalsIgnoreCase("ExclusionSeat")) {
            String[] s = this.splitParam(data);
            if (s.length >= 2) {
                Integer[] a = new Integer[s.length];
                for (int i = 0; i < a.length; ++i) {
                    a[i] = this.toInt(s[i], 1, 10000) - 1;
                }
                this.exclusionSeatList.add(a);
            }
        } else if (MCH_MOD.proxy.isRemote() && item.equalsIgnoreCase("HUD")) {
            String[] ss;
            this.hudList.clear();
            for (String s : ss = data.split("\\s*,\\s*")) {
                MCH_Hud hud = MCH_HudManager.get((String)s);
                if (hud == null) {
                    hud = MCH_Hud.NoDisp;
                }
                this.hudList.add(hud);
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
            this.bodyWidth = this.toFloat(data, 0.1f, 1000.0f);
        } else if (item.equalsIgnoreCase("Height")) {
            this.bodyHeight = this.toFloat(data, 0.1f, 1000.0f);
        } else if (item.compareTo("float") == 0) {
            this.isFloat = this.toBool(data);
        } else if (item.compareTo("floatoffset") == 0) {
            this.floatOffset = -this.toFloat(data);
        } else if (item.compareTo("gravity") == 0) {
            this.gravity = this.toFloat(data, -50.0f, 50.0f);
        } else if (item.compareTo("gravityinwater") == 0) {
            this.gravityInWater = this.toFloat(data, -50.0f, 50.0f);
        } else if (item.compareTo("cameraposition") == 0) {
            String[] s = data.split("\\s*,\\s*");
            if (s.length >= 3) {
                this.alwaysCameraView = s.length >= 4 ? this.toBool(s[3]) : false;
                boolean fixRot = s.length >= 5;
                float yaw = s.length >= 5 ? this.toFloat(s[4]) : 0.0f;
                float pitch = s.length >= 6 ? this.toFloat(s[5]) : 0.0f;
                this.cameraPosition.add(new CameraPosition(this, this.toVec3(s[0], s[1], s[2]), fixRot, yaw, pitch));
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
            this.cameraRotationSpeed = this.toFloat(data, 0.0f, 10000.0f);
        } else if (item.compareTo("regeneration") == 0) {
            this.regeneration = this.toBool(data);
        } else if (item.compareTo("speed") == 0) {
            this.speed = this.toFloat(data, 0.0f, this.getMaxSpeed());
        } else if (item.equalsIgnoreCase("EnableBack")) {
            this.enableBack = this.toBool(data);
        } else if (item.equalsIgnoreCase("MotionFactor")) {
            this.motionFactor = this.toFloat(data, 0.0f, 1.0f);
        } else if (item.equalsIgnoreCase("MobilityYawOnGround")) {
            this.mobilityYawOnGround = this.toFloat(data, 0.0f, 100.0f);
        } else if (item.equalsIgnoreCase("MobilityYaw")) {
            this.mobilityYaw = this.toFloat(data, 0.0f, 100.0f);
        } else if (item.equalsIgnoreCase("MobilityPitch")) {
            this.mobilityPitch = this.toFloat(data, 0.0f, 100.0f);
        } else if (item.equalsIgnoreCase("MobilityRoll")) {
            this.mobilityRoll = this.toFloat(data, 0.0f, 100.0f);
        } else if (item.equalsIgnoreCase("MinRotationPitch")) {
            this.limitRotation = true;
            this.minRotationPitch = this.toFloat(data, this.getMinRotationPitch(), 0.0f);
        } else if (item.equalsIgnoreCase("MaxRotationPitch")) {
            this.limitRotation = true;
            this.maxRotationPitch = this.toFloat(data, 0.0f, this.getMaxRotationPitch());
        } else if (item.equalsIgnoreCase("MinRotationRoll")) {
            this.limitRotation = true;
            this.minRotationRoll = this.toFloat(data, this.getMinRotationRoll(), 0.0f);
        } else if (item.equalsIgnoreCase("MaxRotationRoll")) {
            this.limitRotation = true;
            this.maxRotationRoll = this.toFloat(data, 0.0f, this.getMaxRotationRoll());
        } else if (item.compareTo("throttleupdown") == 0) {
            this.throttleUpDown = this.toFloat(data, 0.0f, 3.0f);
        } else if (item.equalsIgnoreCase("ThrottleUpDownOnEntity")) {
            this.throttleUpDownOnEntity = this.toFloat(data, 0.0f, 100000.0f);
        } else if (item.equalsIgnoreCase("Stealth")) {
            this.stealth = this.toFloat(data, 0.0f, 1.0f);
        } else if (item.equalsIgnoreCase("EntityWidth")) {
            this.entityWidth = this.toFloat(data, -100.0f, 100.0f);
        } else if (item.equalsIgnoreCase("EntityHeight")) {
            this.entityHeight = this.toFloat(data, -100.0f, 100.0f);
        } else if (item.equalsIgnoreCase("EntityPitch")) {
            this.entityPitch = this.toFloat(data, -360.0f, 360.0f);
        } else if (item.equalsIgnoreCase("EntityRoll")) {
            this.entityRoll = this.toFloat(data, -360.0f, 360.0f);
        } else if (item.equalsIgnoreCase("StepHeight")) {
            this.stepHeight = this.toFloat(data, 0.0f, 1000.0f);
        } else if (item.equalsIgnoreCase("CanMoveOnGround")) {
            this.canMoveOnGround = this.toBool(data);
        } else if (item.equalsIgnoreCase("CanRotOnGround")) {
            this.canRotOnGround = this.toBool(data);
        } else if (item.equalsIgnoreCase("AddWeapon") || item.equalsIgnoreCase("AddTurretWeapon")) {
            String[] s = data.split("\\s*,\\s*");
            String type = s[0].toLowerCase();
            if (s.length >= 4 && MCH_WeaponInfoManager.contains((String)type)) {
                int seatID;
                float y = s.length >= 5 ? this.toFloat(s[4]) : 0.0f;
                float p = s.length >= 6 ? this.toFloat(s[5]) : 0.0f;
                boolean canUsePilot = s.length >= 7 ? this.toBool(s[6]) : true;
                int n = seatID = s.length >= 8 ? this.toInt(s[7], 1, this.getInfo_MaxSeatNum()) - 1 : 0;
                if (seatID <= 0) {
                    canUsePilot = true;
                }
                float dfy = s.length >= 9 ? this.toFloat(s[8]) : 0.0f;
                dfy = MathHelper.func_76142_g((float)dfy);
                float mny = s.length >= 10 ? this.toFloat(s[9]) : 0.0f;
                float mxy = s.length >= 11 ? this.toFloat(s[10]) : 0.0f;
                float mnp = s.length >= 12 ? this.toFloat(s[11]) : 0.0f;
                float mxp = s.length >= 13 ? this.toFloat(s[12]) : 0.0f;
                Weapon e = new Weapon(this, this.toFloat(s[1]), this.toFloat(s[2]), this.toFloat(s[3]), y, p, canUsePilot, seatID, dfy, mny, mxy, mnp, mxp, item.equalsIgnoreCase("AddTurretWeapon"));
                if (type.compareTo(this.lastWeaponType) != 0) {
                    this.weaponSetList.add(new WeaponSet(this, type));
                    ++this.lastWeaponIndex;
                    this.lastWeaponType = type;
                }
                ((WeaponSet)this.weaponSetList.get((int)this.lastWeaponIndex)).weapons.add(e);
            }
        } else if (item.equalsIgnoreCase("AddPartWeapon") || item.equalsIgnoreCase("AddPartRotWeapon") || item.equalsIgnoreCase("AddPartTurretWeapon") || item.equalsIgnoreCase("AddPartTurretRotWeapon") || item.equalsIgnoreCase("AddPartWeaponMissile")) {
            String[] s = data.split("\\s*,\\s*");
            if (s.length >= 7) {
                PartWeapon w;
                boolean turret;
                float rx = 0.0f;
                float ry = 0.0f;
                float rz = 0.0f;
                float rb = 0.0f;
                boolean isRot = item.equalsIgnoreCase("AddPartRotWeapon") || item.equalsIgnoreCase("AddPartTurretRotWeapon");
                boolean isMissile = item.equalsIgnoreCase("AddPartWeaponMissile");
                boolean bl = turret = item.equalsIgnoreCase("AddPartTurretWeapon") || item.equalsIgnoreCase("AddPartTurretRotWeapon");
                if (isRot) {
                    rx = s.length >= 10 ? this.toFloat(s[7]) : 0.0f;
                    ry = s.length >= 10 ? this.toFloat(s[8]) : 0.0f;
                    rz = s.length >= 10 ? this.toFloat(s[9]) : -1.0f;
                } else {
                    rb = s.length >= 8 ? this.toFloat(s[7]) : 0.0f;
                }
                this.lastWeaponPart = w = new PartWeapon(this, this.splitParamSlash(s[0].toLowerCase().trim()), isRot, isMissile, this.toBool(s[1]), this.toBool(s[2]), this.toBool(s[3]), this.toFloat(s[4]), this.toFloat(s[5]), this.toFloat(s[6]), "weapon" + this.partWeapon.size(), rx, ry, rz, rb, turret);
                this.partWeapon.add(w);
            }
        } else if (item.equalsIgnoreCase("AddPartWeaponChild")) {
            String[] s = data.split("\\s*,\\s*");
            if (s.length >= 5 && this.lastWeaponPart != null) {
                float rb = s.length >= 6 ? this.toFloat(s[5]) : 0.0f;
                PartWeaponChild w = new PartWeaponChild(this, this.lastWeaponPart.name, this.toBool(s[0]), this.toBool(s[1]), this.toFloat(s[2]), this.toFloat(s[3]), this.toFloat(s[4]), this.lastWeaponPart.modelName + "_" + this.lastWeaponPart.child.size(), 0.0f, 0.0f, 0.0f, rb);
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
            this.damageFactor = this.toFloat(data, 0.0f, 1.0f);
        } else if (item.equalsIgnoreCase("SubmergedDamageHeight")) {
            this.submergedDamageHeight = this.toFloat(data, -1000.0f, 1000.0f);
        } else if (item.equalsIgnoreCase("ArmorDamageFactor")) {
            this.armorDamageFactor = this.toFloat(data, 0.0f, 10000.0f);
        } else if (item.equalsIgnoreCase("ArmorMinDamage")) {
            this.armorMinDamage = this.toFloat(data, 0.0f, 1000000.0f);
        } else if (item.equalsIgnoreCase("ArmorMaxDamage")) {
            this.armorMaxDamage = this.toFloat(data, 0.0f, 1000000.0f);
        } else if (item.equalsIgnoreCase("FlareType")) {
            String[] s = data.split("\\s*,\\s*");
            this.flare.types = new int[s.length];
            for (int i = 0; i < s.length; ++i) {
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
            this.soundRange = this.toFloat(data, 1.0f, 1000.0f);
        } else if (item.equalsIgnoreCase("SoundVolume")) {
            this.soundVolume = this.toFloat(data, 0.0f, 10.0f);
        } else if (item.equalsIgnoreCase("SoundPitch")) {
            this.soundPitch = this.toFloat(data, 0.0f, 10.0f);
        } else if (item.equalsIgnoreCase("UAV")) {
            this.isUAV = this.toBool(data);
            this.isSmallUAV = false;
        } else if (item.equalsIgnoreCase("SmallUAV")) {
            this.isUAV = this.toBool(data);
            this.isSmallUAV = true;
        } else if (item.equalsIgnoreCase("TargetDrone")) {
            this.isTargetDrone = this.toBool(data);
        } else if (item.compareTo("autopilotrot") == 0) {
            this.autoPilotRot = this.toFloat(data, -5.0f, 5.0f);
        } else if (item.compareTo("ongroundpitch") == 0) {
            this.onGroundPitch = -this.toFloat(data, -90.0f, 90.0f);
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
            WeaponBay n = null;
            if (slide) {
                if (s.length >= 4) {
                    n = new WeaponBay(this, s[0].trim().toLowerCase(), this.toFloat(s[1]), this.toFloat(s[2]), this.toFloat(s[3]), 0.0f, 0.0f, 0.0f, 90.0f, "wb" + this.partWeaponBay.size(), slide);
                    this.partWeaponBay.add(n);
                }
            } else if (s.length >= 7) {
                float mx = s.length >= 8 ? this.toFloat(s[7], -180.0f, 180.0f) : 90.0f;
                n = new WeaponBay(this, s[0].trim().toLowerCase(), this.toFloat(s[1]), this.toFloat(s[2]), this.toFloat(s[3]), this.toFloat(s[4]), this.toFloat(s[5]), this.toFloat(s[6]), mx / 90.0f, "wb" + this.partWeaponBay.size(), slide);
                this.partWeaponBay.add(n);
            }
        } else if (item.compareTo("addparthatch") == 0 || item.compareTo("addpartslidehatch") == 0) {
            boolean slide = item.compareTo("addpartslidehatch") == 0;
            String[] s = data.split("\\s*,\\s*");
            Hatch n = null;
            if (slide) {
                if (s.length >= 3) {
                    n = new Hatch(this, this.toFloat(s[0]), this.toFloat(s[1]), this.toFloat(s[2]), 0.0f, 0.0f, 0.0f, 90.0f, "hatch" + this.hatchList.size(), slide);
                    this.hatchList.add(n);
                }
            } else if (s.length >= 6) {
                float mx = s.length >= 7 ? this.toFloat(s[6], -180.0f, 180.0f) : 90.0f;
                n = new Hatch(this, this.toFloat(s[0]), this.toFloat(s[1]), this.toFloat(s[2]), this.toFloat(s[3]), this.toFloat(s[4]), this.toFloat(s[5]), mx, "hatch" + this.hatchList.size(), slide);
                this.hatchList.add(n);
            }
        } else if (item.compareTo("addpartcanopy") == 0 || item.compareTo("addpartslidecanopy") == 0) {
            String[] s = data.split("\\s*,\\s*");
            boolean slide = item.compareTo("addpartslidecanopy") == 0;
            int canopyNum = this.canopyList.size();
            if (canopyNum > 0) {
                --canopyNum;
            }
            if (slide) {
                if (s.length >= 3) {
                    Canopy c = new Canopy(this, this.toFloat(s[0]), this.toFloat(s[1]), this.toFloat(s[2]), 0.0f, 0.0f, 0.0f, 90.0f, "canopy" + canopyNum, slide);
                    this.canopyList.add(c);
                    if (canopyNum == 0) {
                        c = new Canopy(this, this.toFloat(s[0]), this.toFloat(s[1]), this.toFloat(s[2]), 0.0f, 0.0f, 0.0f, 90.0f, "canopy", slide);
                        this.canopyList.add(c);
                    }
                }
            } else if (s.length >= 6) {
                float mx = s.length >= 7 ? this.toFloat(s[6], -180.0f, 180.0f) : 90.0f;
                Canopy c = new Canopy(this, this.toFloat(s[0]), this.toFloat(s[1]), this.toFloat(s[2]), this.toFloat(s[3]), this.toFloat(s[4]), this.toFloat(s[5]), mx /= 90.0f, "canopy" + canopyNum, slide);
                this.canopyList.add(c);
                if (canopyNum == 0) {
                    c = new Canopy(this, this.toFloat(s[0]), this.toFloat(s[1]), this.toFloat(s[2]), this.toFloat(s[3]), this.toFloat(s[4]), this.toFloat(s[5]), mx, "canopy", slide);
                    this.canopyList.add(c);
                }
            }
        } else if (item.equalsIgnoreCase("AddPartLG") || item.equalsIgnoreCase("AddPartSlideRotLG") || item.equalsIgnoreCase("AddPartLGRev") || item.equalsIgnoreCase("AddPartLGHatch")) {
            LandingGear n;
            float maxRot;
            String[] s = data.split("\\s*,\\s*");
            if (!item.equalsIgnoreCase("AddPartSlideRotLG") && s.length >= 6) {
                maxRot = s.length >= 7 ? this.toFloat(s[6], -180.0f, 180.0f) : 90.0f;
                n = new LandingGear(this, this.toFloat(s[0]), this.toFloat(s[1]), this.toFloat(s[2]), this.toFloat(s[3]), this.toFloat(s[4]), this.toFloat(s[5]), "lg" + this.landingGear.size(), maxRot /= 90.0f, item.equalsIgnoreCase("AddPartLgRev"), item.equalsIgnoreCase("AddPartLGHatch"));
                if (s.length >= 8) {
                    n.enableRot2 = true;
                    n.maxRotFactor2 = s.length >= 11 ? this.toFloat(s[10], -180.0f, 180.0f) : 90.0f;
                    n.maxRotFactor2 /= 90.0f;
                    n.rot2 = Vec3.func_72443_a((double)this.toFloat(s[7]), (double)this.toFloat(s[8]), (double)this.toFloat(s[9]));
                }
                this.landingGear.add(n);
            }
            if (item.equalsIgnoreCase("AddPartSlideRotLG") && s.length >= 9) {
                maxRot = s.length >= 10 ? this.toFloat(s[9], -180.0f, 180.0f) : 90.0f;
                n = new LandingGear(this, this.toFloat(s[3]), this.toFloat(s[4]), this.toFloat(s[5]), this.toFloat(s[6]), this.toFloat(s[7]), this.toFloat(s[8]), "lg" + this.landingGear.size(), maxRot /= 90.0f, false, false);
                n.slide = Vec3.func_72443_a((double)this.toFloat(s[0]), (double)this.toFloat(s[1]), (double)this.toFloat(s[2]));
                this.landingGear.add(n);
            }
        } else if (item.equalsIgnoreCase("AddPartThrottle")) {
            String[] s = data.split("\\s*,\\s*");
            if (s.length >= 7) {
                float x = s.length >= 8 ? this.toFloat(s[7]) : 0.0f;
                float y = s.length >= 9 ? this.toFloat(s[8]) : 0.0f;
                float z = s.length >= 10 ? this.toFloat(s[9]) : 0.0f;
                Throttle c = new Throttle(this, this.toFloat(s[0]), this.toFloat(s[1]), this.toFloat(s[2]), this.toFloat(s[3]), this.toFloat(s[4]), this.toFloat(s[5]), this.toFloat(s[6]), "throttle" + this.partThrottle.size(), x, y, z);
                this.partThrottle.add(c);
            }
        } else if (item.equalsIgnoreCase("AddPartRotation")) {
            String[] s = data.split("\\s*,\\s*");
            if (s.length >= 7) {
                boolean always = s.length >= 8 ? this.toBool(s[7]) : true;
                RotPart c = new RotPart(this, this.toFloat(s[0]), this.toFloat(s[1]), this.toFloat(s[2]), this.toFloat(s[3]), this.toFloat(s[4]), this.toFloat(s[5]), this.toFloat(s[6]), always, "rotpart" + this.partThrottle.size());
                this.partRotPart.add(c);
            }
        } else if (item.compareTo("addpartcamera") == 0) {
            String[] s = data.split("\\s*,\\s*");
            if (s.length >= 3) {
                boolean ys = s.length >= 4 ? this.toBool(s[3]) : true;
                boolean ps = s.length >= 5 ? this.toBool(s[4]) : false;
                Camera c = new Camera(this, this.toFloat(s[0]), this.toFloat(s[1]), this.toFloat(s[2]), 0.0f, -1.0f, 0.0f, "camera" + this.cameraList.size(), ys, ps);
                this.cameraList.add(c);
            }
        } else if (item.equalsIgnoreCase("AddPartWheel")) {
            String[] s = this.splitParam(data);
            if (s.length >= 3) {
                float rd = s.length >= 4 ? this.toFloat(s[3], -1800.0f, 1800.0f) : 0.0f;
                float rx = s.length >= 7 ? this.toFloat(s[4]) : 0.0f;
                float ry = s.length >= 7 ? this.toFloat(s[5]) : 1.0f;
                float rz = s.length >= 7 ? this.toFloat(s[6]) : 0.0f;
                float px = s.length >= 10 ? this.toFloat(s[7]) : this.toFloat(s[0]);
                float py = s.length >= 10 ? this.toFloat(s[8]) : this.toFloat(s[1]);
                float pz = s.length >= 10 ? this.toFloat(s[9]) : this.toFloat(s[2]);
                this.partWheel.add(new PartWheel(this, this.toFloat(s[0]), this.toFloat(s[1]), this.toFloat(s[2]), rx, ry, rz, rd, px, py, pz, "wheel" + this.partWheel.size()));
            }
        } else if (item.equalsIgnoreCase("AddPartSteeringWheel")) {
            String[] s = this.splitParam(data);
            if (s.length >= 7) {
                this.partSteeringWheel.add(new PartWheel(this, this.toFloat(s[0]), this.toFloat(s[1]), this.toFloat(s[2]), this.toFloat(s[3]), this.toFloat(s[4]), this.toFloat(s[5]), this.toFloat(s[6]), "steering_wheel" + this.partSteeringWheel.size()));
            }
        } else if (item.equalsIgnoreCase("AddTrackRoller")) {
            String[] s = this.splitParam(data);
            if (s.length >= 3) {
                this.partTrackRoller.add(new TrackRoller(this, this.toFloat(s[0]), this.toFloat(s[1]), this.toFloat(s[2]), "track_roller" + this.partTrackRoller.size()));
            }
        } else if (item.equalsIgnoreCase("AddCrawlerTrack")) {
            this.partCrawlerTrack.add(this.createCrawlerTrack(data, "crawler_track" + this.partCrawlerTrack.size()));
        } else if (item.equalsIgnoreCase("PivotTurnThrottle")) {
            this.pivotTurnThrottle = this.toFloat(data, 0.0f, 1.0f);
        } else if (item.equalsIgnoreCase("TrackRollerRot")) {
            this.trackRollerRot = this.toFloat(data, -10000.0f, 10000.0f);
        } else if (item.equalsIgnoreCase("PartWheelRot")) {
            this.partWheelRot = this.toFloat(data, -10000.0f, 10000.0f);
        } else if (item.compareTo("camerazoom") == 0) {
            this.cameraZoom = this.toInt(data, 1, 10);
        } else if (item.equalsIgnoreCase("DefaultFreelook")) {
            this.defaultFreelook = this.toBool(data);
        } else if (item.equalsIgnoreCase("BoundingBox")) {
            String[] s = data.split("\\s*,\\s*");
            if (s.length >= 5) {
                float df = s.length >= 6 ? this.toFloat(s[5]) : 1.0f;
                MCH_BoundingBox c = new MCH_BoundingBox((double)this.toFloat(s[0]), (double)this.toFloat(s[1]), (double)this.toFloat(s[2]), this.toFloat(s[3]), this.toFloat(s[4]), df);
                this.extraBoundingBox.add(c);
                if (c.boundingBox.field_72337_e > (double)this.markerHeight) {
                    this.markerHeight = (float)c.boundingBox.field_72337_e;
                }
                this.markerWidth = (float)Math.max((double)this.markerWidth, Math.abs(c.boundingBox.field_72336_d) / 2.0);
                this.markerWidth = (float)Math.max((double)this.markerWidth, Math.abs(c.boundingBox.field_72340_a) / 2.0);
                this.markerWidth = (float)Math.max((double)this.markerWidth, Math.abs(c.boundingBox.field_72334_f) / 2.0);
                this.markerWidth = (float)Math.max((double)this.markerWidth, Math.abs(c.boundingBox.field_72339_c) / 2.0);
                this.bbZmin = (float)Math.min((double)this.bbZmin, c.boundingBox.field_72339_c);
                this.bbZmax = (float)Math.min((double)this.bbZmax, c.boundingBox.field_72334_f);
            }
        } else if (item.equalsIgnoreCase("RotorSpeed")) {
            this.rotorSpeed = this.toFloat(data, -10000.0f, 10000.0f);
            if ((double)this.rotorSpeed > 0.01) {
                this.rotorSpeed = (float)((double)this.rotorSpeed - 0.01);
            }
            if ((double)this.rotorSpeed < -0.01) {
                this.rotorSpeed = (float)((double)this.rotorSpeed + 0.01);
            }
        } else if (item.equalsIgnoreCase("OnGroundPitchFactor")) {
            this.onGroundPitchFactor = this.toFloat(data, 0.0f, 180.0f);
        } else if (item.equalsIgnoreCase("OnGroundRollFactor")) {
            this.onGroundRollFactor = this.toFloat(data, 0.0f, 180.0f);
        }
    }

    public CrawlerTrack createCrawlerTrack(String data, String name) {
        int i;
        String[] s = this.splitParam(data);
        int PC = s.length - 3;
        boolean REV = this.toBool(s[0]);
        float LEN = this.toFloat(s[1], 0.001f, 1000.0f) * 0.9f;
        float Z = this.toFloat(s[2]);
        if (PC < 4) {
            return null;
        }
        double[] cx = new double[PC];
        double[] cy = new double[PC];
        for (int i2 = 0; i2 < PC; ++i2) {
            int idx = !REV ? i2 : PC - i2 - 1;
            String[] xy = this.splitParamSlash(s[3 + idx]);
            cx[i2] = this.toFloat(xy[0]);
            cy[i2] = this.toFloat(xy[1]);
        }
        ArrayList<CrawlerTrackPrm> lp = new ArrayList<CrawlerTrackPrm>();
        lp.add(new CrawlerTrackPrm(this, (float)cx[0], (float)cy[0]));
        double dist = 0.0;
        for (i = 0; i < PC; ++i) {
            double x = cx[(i + 1) % PC] - cx[i];
            double y = cy[(i + 1) % PC] - cy[i];
            double dist2 = dist += Math.sqrt(x * x + y * y);
            int j = 1;
            while (dist >= (double)LEN) {
                lp.add(new CrawlerTrackPrm(this, (float)(cx[i] + x * ((double)(LEN * (float)j) / dist2)), (float)(cy[i] + y * ((double)(LEN * (float)j) / dist2))));
                dist -= (double)LEN;
                ++j;
            }
        }
        for (i = 0; i < lp.size(); ++i) {
            float ppr;
            CrawlerTrackPrm pp = (CrawlerTrackPrm)lp.get((i + lp.size() - 1) % lp.size());
            CrawlerTrackPrm cp = (CrawlerTrackPrm)lp.get(i);
            CrawlerTrackPrm np = (CrawlerTrackPrm)lp.get((i + 1) % lp.size());
            float pr = (float)(Math.atan2(pp.x - cp.x, pp.y - cp.y) * 180.0 / Math.PI);
            float nr = (float)(Math.atan2(np.x - cp.x, np.y - cp.y) * 180.0 / Math.PI);
            float nnr = nr + 180.0f;
            if (((double)nnr < (double)(ppr = (pr + 360.0f) % 360.0f) - 0.3 || (double)nnr > (double)ppr + 0.3) && nnr - ppr < 100.0f && nnr - ppr > -100.0f) {
                nnr = (nnr + ppr) / 2.0f;
            }
            cp.r = nnr;
        }
        CrawlerTrack c = new CrawlerTrack(this, name);
        c.len = LEN;
        c.cx = cx;
        c.cy = cy;
        c.lp = lp;
        c.z = Z;
        c.side = Z >= 0.0f ? 1 : 0;
        return c;
    }

    public String getTextureName() {
        String s = (String)this.textureNameList.get(this.textureCount);
        this.textureCount = (this.textureCount + 1) % this.textureNameList.size();
        return s;
    }

    public String getNextTextureName(String base) {
        if (this.textureNameList.size() >= 2) {
            for (int i = 0; i < this.textureNameList.size(); ++i) {
                String s = (String)this.textureNameList.get(i);
                if (!s.equalsIgnoreCase(base)) continue;
                i = (i + 1) % this.textureNameList.size();
                return (String)this.textureNameList.get(i);
            }
        }
        return base;
    }

    public void preReload() {
        this.textureNameList.clear();
        this.textureNameList.add(this.name);
        this.cameraList.clear();
        this.cameraPosition.clear();
        this.canopyList.clear();
        this.flare = new Flare(this);
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
        return new String[]{"DisplayName", "AddDisplayName", "ItemID", "AddRecipe", "AddShapelessRecipe", "InventorySize", "Sound", "UAV", "SmallUAV", "TargetDrone", "Category"};
    }

    public boolean canReloadItem(String item) {
        String[] ignoreItems;
        for (String s : ignoreItems = MCH_AircraftInfo.getCannotReloadItem()) {
            if (!s.equalsIgnoreCase(item)) continue;
            return false;
        }
        return true;
    }
}

