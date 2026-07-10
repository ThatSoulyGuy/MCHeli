/*
 * Decompiled with CFR 0.152.
 */
package mcheli;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import mcheli.MCH_Config;
import mcheli.MCH_ConfigPrm;
import mcheli.MCH_InputFile;
import mcheli.MCH_Lib;
import mcheli.MCH_MOD;
import mcheli.MCH_OutputFile;
import mcheli.helicopter.MCH_EntityHeli;
import mcheli.plane.MCP_EntityPlane;
import mcheli.tank.MCH_EntityTank;
import mcheli.vehicle.MCH_EntityVehicle;
import mcheli.wrapper.W_Block;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;

public class MCH_Config {
    public static String mcPath;
    public static String configFilePath;
    public static boolean DebugLog;
    public static String configVer;
    public static int hitMarkColorRGB;
    public static float hitMarkColorAlpha;
    public static List<Block> bulletBreakableBlocks;
    public static final List<Block> dummyBreakableBlocks;
    public static final List<Material> dummyBreakableMaterials;
    public static List<Block> carNoBreakableBlocks;
    public static List<Block> carBreakableBlocks;
    public static List<Material> carBreakableMaterials;
    public static List<Block> tankNoBreakableBlocks;
    public static List<Block> tankBreakableBlocks;
    public static List<Material> tankBreakableMaterials;
    public static MCH_ConfigPrm KeyUp;
    public static MCH_ConfigPrm KeyDown;
    public static MCH_ConfigPrm KeyRight;
    public static MCH_ConfigPrm KeyLeft;
    public static MCH_ConfigPrm KeySwitchMode;
    public static MCH_ConfigPrm KeySwitchHovering;
    public static MCH_ConfigPrm KeyAttack;
    public static MCH_ConfigPrm KeyUseWeapon;
    public static MCH_ConfigPrm KeySwitchWeapon1;
    public static MCH_ConfigPrm KeySwitchWeapon2;
    public static MCH_ConfigPrm KeySwWeaponMode;
    public static MCH_ConfigPrm KeyZoom;
    public static MCH_ConfigPrm KeyCameraMode;
    public static MCH_ConfigPrm KeyUnmount;
    public static MCH_ConfigPrm KeyFlare;
    public static MCH_ConfigPrm KeyExtra;
    public static MCH_ConfigPrm KeyCameraDistUp;
    public static MCH_ConfigPrm KeyCameraDistDown;
    public static MCH_ConfigPrm KeyFreeLook;
    public static MCH_ConfigPrm KeyGUI;
    public static MCH_ConfigPrm KeyGearUpDown;
    public static MCH_ConfigPrm KeyPutToRack;
    public static MCH_ConfigPrm KeyDownFromRack;
    public static MCH_ConfigPrm KeyScoreboard;
    public static MCH_ConfigPrm KeyMultiplayManager;
    public static List<MCH_ConfigPrm> DamageVs;
    public static List<String> IgnoreBulletHitList;
    public static MCH_ConfigPrm IgnoreBulletHitItem;
    public static DamageFactor[] DamageFactorList;
    public static DamageFactor DamageVsEntity;
    public static DamageFactor DamageVsLiving;
    public static DamageFactor DamageVsPlayer;
    public static DamageFactor DamageVsMCHeliAircraft;
    public static DamageFactor DamageVsMCHeliTank;
    public static DamageFactor DamageVsMCHeliVehicle;
    public static DamageFactor DamageVsMCHeliOther;
    public static DamageFactor DamageAircraftByExternal;
    public static DamageFactor DamageTankByExternal;
    public static DamageFactor DamageVehicleByExternal;
    public static DamageFactor DamageOtherByExternal;
    public static List<MCH_ConfigPrm> CommandPermission;
    public static List<CommandPermission> CommandPermissionList;
    public static MCH_ConfigPrm TestMode;
    public static MCH_ConfigPrm EnableCommand;
    public static MCH_ConfigPrm PlaceableOnSpongeOnly;
    public static MCH_ConfigPrm HideKeybind;
    public static MCH_ConfigPrm ItemDamage;
    public static MCH_ConfigPrm ItemFuel;
    public static MCH_ConfigPrm AutoRepairHP;
    public static MCH_ConfigPrm Collision_DestroyBlock;
    public static MCH_ConfigPrm Explosion_DestroyBlock;
    public static MCH_ConfigPrm Explosion_FlamingBlock;
    public static MCH_ConfigPrm BulletBreakableBlock;
    public static MCH_ConfigPrm Collision_Car_BreakableBlock;
    public static MCH_ConfigPrm Collision_Car_NoBreakableBlock;
    public static MCH_ConfigPrm Collision_Car_BreakableMaterial;
    public static MCH_ConfigPrm Collision_Tank_BreakableBlock;
    public static MCH_ConfigPrm Collision_Tank_NoBreakableBlock;
    public static MCH_ConfigPrm Collision_Tank_BreakableMaterial;
    public static MCH_ConfigPrm Collision_EntityDamage;
    public static MCH_ConfigPrm Collision_EntityTankDamage;
    public static MCH_ConfigPrm LWeaponAutoFire;
    public static MCH_ConfigPrm DismountAll;
    public static MCH_ConfigPrm MountMinecartHeli;
    public static MCH_ConfigPrm MountMinecartPlane;
    public static MCH_ConfigPrm MountMinecartVehicle;
    public static MCH_ConfigPrm MountMinecartTank;
    public static MCH_ConfigPrm AutoThrottleDownHeli;
    public static MCH_ConfigPrm AutoThrottleDownPlane;
    public static MCH_ConfigPrm AutoThrottleDownTank;
    public static MCH_ConfigPrm DisableItemRender;
    public static MCH_ConfigPrm RenderDistanceWeight;
    public static MCH_ConfigPrm MobRenderDistanceWeight;
    public static MCH_ConfigPrm CreativeTabIcon;
    public static MCH_ConfigPrm CreativeTabIconHeli;
    public static MCH_ConfigPrm CreativeTabIconPlane;
    public static MCH_ConfigPrm CreativeTabIconTank;
    public static MCH_ConfigPrm CreativeTabIconVehicle;
    public static MCH_ConfigPrm DisableShader;
    public static MCH_ConfigPrm AliveTimeOfCartridge;
    public static MCH_ConfigPrm InfinityAmmo;
    public static MCH_ConfigPrm InfinityFuel;
    public static MCH_ConfigPrm HitMarkColor;
    public static MCH_ConfigPrm SmoothShading;
    public static MCH_ConfigPrm EnableModEntityRender;
    public static MCH_ConfigPrm DisableRenderLivingSpecials;
    public static MCH_ConfigPrm PreventingBroken;
    public static MCH_ConfigPrm DropItemInCreativeMode;
    public static MCH_ConfigPrm BreakableOnlyPickaxe;
    public static MCH_ConfigPrm InvertMouse;
    public static MCH_ConfigPrm MouseSensitivity;
    public static MCH_ConfigPrm MouseControlStickModeHeli;
    public static MCH_ConfigPrm MouseControlStickModePlane;
    public static MCH_ConfigPrm MouseControlFlightSimMode;
    public static MCH_ConfigPrm SwitchWeaponWithMouseWheel;
    public static MCH_ConfigPrm AllPlaneSpeed;
    public static MCH_ConfigPrm AllHeliSpeed;
    public static MCH_ConfigPrm AllTankSpeed;
    public static MCH_ConfigPrm HurtResistantTime;
    public static MCH_ConfigPrm DisplayHUDThirdPerson;
    public static MCH_ConfigPrm DisableCameraDistChange;
    public static MCH_ConfigPrm EnableReplaceTextureManager;
    public static MCH_ConfigPrm DisplayEntityMarker;
    public static MCH_ConfigPrm EntityMarkerSize;
    public static MCH_ConfigPrm BlockMarkerSize;
    public static MCH_ConfigPrm DisplayMarkThroughWall;
    public static MCH_ConfigPrm ReplaceRenderViewEntity;
    public static MCH_ConfigPrm StingerLockRange;
    public static MCH_ConfigPrm DefaultExplosionParticle;
    public static MCH_ConfigPrm RangeFinderSpotDist;
    public static MCH_ConfigPrm RangeFinderSpotTime;
    public static MCH_ConfigPrm RangeFinderConsume;
    public static MCH_ConfigPrm EnablePutRackInFlying;
    public static MCH_ConfigPrm EnableDebugBoundingBox;
    public static MCH_ConfigPrm ItemID_Fuel;
    public static MCH_ConfigPrm ItemID_GLTD;
    public static MCH_ConfigPrm ItemID_Chain;
    public static MCH_ConfigPrm ItemID_Parachute;
    public static MCH_ConfigPrm ItemID_Container;
    public static MCH_ConfigPrm ItemID_Stinger;
    public static MCH_ConfigPrm ItemID_StingerMissile;
    public static MCH_ConfigPrm[] ItemID_UavStation;
    public static MCH_ConfigPrm ItemID_InvisibleItem;
    public static MCH_ConfigPrm ItemID_DraftingTable;
    public static MCH_ConfigPrm ItemID_Wrench;
    public static MCH_ConfigPrm ItemID_RangeFinder;
    public static MCH_ConfigPrm BlockID_DraftingTableOFF;
    public static MCH_ConfigPrm BlockID_DraftingTableON;
    public static MCH_ConfigPrm ItemRecipe_Fuel;
    public static MCH_ConfigPrm ItemRecipe_GLTD;
    public static MCH_ConfigPrm ItemRecipe_Chain;
    public static MCH_ConfigPrm ItemRecipe_Parachute;
    public static MCH_ConfigPrm ItemRecipe_Container;
    public static MCH_ConfigPrm ItemRecipe_Stinger;
    public static MCH_ConfigPrm ItemRecipe_StingerMissile;
    public static MCH_ConfigPrm ItemRecipe_Javelin;
    public static MCH_ConfigPrm ItemRecipe_JavelinMissile;
    public static MCH_ConfigPrm[] ItemRecipe_UavStation;
    public static MCH_ConfigPrm ItemRecipe_DraftingTable;
    public static MCH_ConfigPrm ItemRecipe_Wrench;
    public static MCH_ConfigPrm ItemRecipe_RangeFinder;
    public static MCH_ConfigPrm[] KeyConfig;
    public static MCH_ConfigPrm[] General;
    public final String destroyBlockNames = "glass_pane, stained_glass_pane, tallgrass, double_plant, yellow_flower, red_flower, vine, wheat, reeds, waterlily";

    public MCH_Config(String minecraftPath, String cfgFile) {
        mcPath = minecraftPath;
        configFilePath = mcPath + cfgFile;
        DebugLog = false;
        configVer = "0.0.0";
        bulletBreakableBlocks = new ArrayList();
        carBreakableBlocks = new ArrayList();
        carNoBreakableBlocks = new ArrayList();
        carBreakableMaterials = new ArrayList();
        tankBreakableBlocks = new ArrayList();
        tankNoBreakableBlocks = new ArrayList();
        tankBreakableMaterials = new ArrayList();
        KeyUp = new MCH_ConfigPrm("KeyUp", 17);
        KeyDown = new MCH_ConfigPrm("KeyDown", 31);
        KeyRight = new MCH_ConfigPrm("KeyRight", 32);
        KeyLeft = new MCH_ConfigPrm("KeyLeft", 30);
        KeySwitchMode = new MCH_ConfigPrm("KeySwitchGunner", 35);
        KeySwitchHovering = new MCH_ConfigPrm("KeySwitchHovering", 57);
        KeyAttack = new MCH_ConfigPrm("KeyAttack", -100);
        KeyUseWeapon = new MCH_ConfigPrm("KeyUseWeapon", -99);
        KeySwitchWeapon1 = new MCH_ConfigPrm("KeySwitchWeapon1", -98);
        KeySwitchWeapon2 = new MCH_ConfigPrm("KeySwitchWeapon2", 34);
        KeySwWeaponMode = new MCH_ConfigPrm("KeySwitchWeaponMode", 45);
        KeyZoom = new MCH_ConfigPrm("KeyZoom", 44);
        KeyCameraMode = new MCH_ConfigPrm("KeyCameraMode", 46);
        KeyUnmount = new MCH_ConfigPrm("KeyUnmountMob", 21);
        KeyFlare = new MCH_ConfigPrm("KeyFlare", 47);
        KeyExtra = new MCH_ConfigPrm("KeyExtra", 33);
        KeyCameraDistUp = new MCH_ConfigPrm("KeyCameraDistanceUp", 201);
        KeyCameraDistDown = new MCH_ConfigPrm("KeyCameraDistanceDown", 209);
        KeyFreeLook = new MCH_ConfigPrm("KeyFreeLook", 29);
        KeyGUI = new MCH_ConfigPrm("KeyGUI", 19);
        KeyGearUpDown = new MCH_ConfigPrm("KeyGearUpDown", 48);
        KeyPutToRack = new MCH_ConfigPrm("KeyPutToRack", 36);
        KeyDownFromRack = new MCH_ConfigPrm("KeyDownFromRack", 22);
        KeyScoreboard = new MCH_ConfigPrm("KeyScoreboard", 38);
        KeyMultiplayManager = new MCH_ConfigPrm("KeyMultiplayManager", 50);
        MCH_ConfigPrm[] mCH_ConfigPrmArray = new MCH_ConfigPrm[23];
        mCH_ConfigPrmArray[0] = KeyUp;
        mCH_ConfigPrmArray[1] = KeyDown;
        mCH_ConfigPrmArray[2] = KeyRight;
        mCH_ConfigPrmArray[3] = KeyLeft;
        mCH_ConfigPrmArray[4] = KeySwitchMode;
        mCH_ConfigPrmArray[5] = KeySwitchHovering;
        mCH_ConfigPrmArray[6] = KeySwitchWeapon1;
        mCH_ConfigPrmArray[7] = KeySwitchWeapon2;
        mCH_ConfigPrmArray[8] = KeySwWeaponMode;
        mCH_ConfigPrmArray[9] = KeyZoom;
        mCH_ConfigPrmArray[10] = KeyCameraMode;
        mCH_ConfigPrmArray[11] = KeyUnmount;
        mCH_ConfigPrmArray[12] = KeyFlare;
        mCH_ConfigPrmArray[13] = KeyExtra;
        mCH_ConfigPrmArray[14] = KeyCameraDistUp;
        mCH_ConfigPrmArray[15] = KeyCameraDistDown;
        mCH_ConfigPrmArray[16] = KeyFreeLook;
        mCH_ConfigPrmArray[17] = KeyGUI;
        mCH_ConfigPrmArray[18] = KeyGearUpDown;
        mCH_ConfigPrmArray[19] = KeyPutToRack;
        mCH_ConfigPrmArray[20] = KeyDownFromRack;
        mCH_ConfigPrmArray[21] = KeyScoreboard;
        mCH_ConfigPrmArray[22] = KeyMultiplayManager;
        KeyConfig = mCH_ConfigPrmArray;
        DamageVs = new ArrayList();
        CommandPermission = new ArrayList();
        CommandPermissionList = new ArrayList();
        IgnoreBulletHitList = new ArrayList();
        IgnoreBulletHitItem = new MCH_ConfigPrm("IgnoreBulletHit", "");
        TestMode = new MCH_ConfigPrm("TestMode", false);
        EnableCommand = new MCH_ConfigPrm("EnableCommand", true);
        PlaceableOnSpongeOnly = new MCH_ConfigPrm("PlaceableOnSpongeOnly", false);
        HideKeybind = new MCH_ConfigPrm("HideKeybind", false);
        ItemDamage = new MCH_ConfigPrm("ItemDamage", true);
        ItemFuel = new MCH_ConfigPrm("ItemFuel", true);
        AutoRepairHP = new MCH_ConfigPrm("AutoRepairHP", 0.5);
        Collision_DestroyBlock = new MCH_ConfigPrm("Collision_DestroyBlock", true);
        Explosion_DestroyBlock = new MCH_ConfigPrm("Explosion_DestroyBlock", true);
        Explosion_FlamingBlock = new MCH_ConfigPrm("Explosion_FlamingBlock", true);
        Collision_Car_BreakableBlock = new MCH_ConfigPrm("Collision_Car_BreakableBlock", "double_plant, glass_pane,stained_glass_pane");
        Collision_Car_NoBreakableBlock = new MCH_ConfigPrm("Collision_Car_NoBreakBlock", "torch");
        Collision_Car_BreakableMaterial = new MCH_ConfigPrm("Collision_Car_BreakableMaterial", "cactus, cake, gourd, leaves, vine, plants");
        Collision_Tank_BreakableBlock = new MCH_ConfigPrm("Collision_Tank_BreakableBlock", "nether_brick_fence");
        MCH_Config.Collision_Tank_BreakableBlock.validVer = "1.0.0";
        Collision_Tank_NoBreakableBlock = new MCH_ConfigPrm("Collision_Tank_NoBreakBlock", "torch, glowstone");
        Collision_Tank_BreakableMaterial = new MCH_ConfigPrm("Collision_Tank_BreakableMaterial", "cactus, cake, carpet, circuits, glass, gourd, leaves, vine, wood, plants");
        Collision_EntityDamage = new MCH_ConfigPrm("Collision_EntityDamage", true);
        Collision_EntityTankDamage = new MCH_ConfigPrm("Collision_EntityTankDamage", false);
        LWeaponAutoFire = new MCH_ConfigPrm("LWeaponAutoFire", false);
        DismountAll = new MCH_ConfigPrm("DismountAll", false);
        MountMinecartHeli = new MCH_ConfigPrm("MountMinecartHeli", true);
        MountMinecartPlane = new MCH_ConfigPrm("MountMinecartPlane", true);
        MountMinecartVehicle = new MCH_ConfigPrm("MountMinecartVehicle", false);
        MountMinecartTank = new MCH_ConfigPrm("MountMinecartTank", true);
        AutoThrottleDownHeli = new MCH_ConfigPrm("AutoThrottleDownHeli", true);
        AutoThrottleDownPlane = new MCH_ConfigPrm("AutoThrottleDownPlane", false);
        AutoThrottleDownTank = new MCH_ConfigPrm("AutoThrottleDownTank", false);
        DisableItemRender = new MCH_ConfigPrm("DisableItemRender", 1);
        MCH_Config.DisableItemRender.desc = ";DisableItemRender = 0 ~ 3 (1 = Recommended)";
        RenderDistanceWeight = new MCH_ConfigPrm("RenderDistanceWeight", 10.0);
        MobRenderDistanceWeight = new MCH_ConfigPrm("MobRenderDistanceWeight", 1.0);
        CreativeTabIcon = new MCH_ConfigPrm("CreativeTabIconItem", "fuel");
        CreativeTabIconHeli = new MCH_ConfigPrm("CreativeTabIconHeli", "ah-64");
        CreativeTabIconPlane = new MCH_ConfigPrm("CreativeTabIconPlane", "f22a");
        CreativeTabIconTank = new MCH_ConfigPrm("CreativeTabIconTank", "merkava_mk4");
        CreativeTabIconVehicle = new MCH_ConfigPrm("CreativeTabIconVehicle", "mk15");
        DisableShader = new MCH_ConfigPrm("DisableShader", false);
        AliveTimeOfCartridge = new MCH_ConfigPrm("AliveTimeOfCartridge", 200);
        InfinityAmmo = new MCH_ConfigPrm("InfinityAmmo", false);
        InfinityFuel = new MCH_ConfigPrm("InfinityFuel", false);
        HitMarkColor = new MCH_ConfigPrm("HitMarkColor", "255, 255, 0, 0");
        MCH_Config.HitMarkColor.desc = ";HitMarkColor = Alpha, Red, Green, Blue";
        SmoothShading = new MCH_ConfigPrm("SmoothShading", true);
        BulletBreakableBlock = new MCH_ConfigPrm("BulletBreakableBlocks", "glass_pane, stained_glass_pane, tallgrass, double_plant, yellow_flower, red_flower, vine, wheat, reeds, waterlily");
        MCH_Config.BulletBreakableBlock.validVer = "0.10.4";
        EnableModEntityRender = new MCH_ConfigPrm("EnableModEntityRender", true);
        DisableRenderLivingSpecials = new MCH_ConfigPrm("DisableRenderLivingSpecials", true);
        PreventingBroken = new MCH_ConfigPrm("PreventingBroken", false);
        DropItemInCreativeMode = new MCH_ConfigPrm("DropItemInCreativeMode", false);
        BreakableOnlyPickaxe = new MCH_ConfigPrm("BreakableOnlyPickaxe", false);
        InvertMouse = new MCH_ConfigPrm("InvertMouse", false);
        MouseSensitivity = new MCH_ConfigPrm("MouseSensitivity", 10.0);
        MouseControlStickModeHeli = new MCH_ConfigPrm("MouseControlStickModeHeli", false);
        MouseControlStickModePlane = new MCH_ConfigPrm("MouseControlStickModePlane", false);
        MouseControlFlightSimMode = new MCH_ConfigPrm("MouseControlFlightSimMode", false);
        MCH_Config.MouseControlFlightSimMode.desc = ";MouseControlFlightSimMode = true ( Yaw:key, Roll=mouse )";
        SwitchWeaponWithMouseWheel = new MCH_ConfigPrm("SwitchWeaponWithMouseWheel", true);
        AllHeliSpeed = new MCH_ConfigPrm("AllHeliSpeed", 1.0);
        AllPlaneSpeed = new MCH_ConfigPrm("AllPlaneSpeed", 1.0);
        AllTankSpeed = new MCH_ConfigPrm("AllTankSpeed", 1.0);
        HurtResistantTime = new MCH_ConfigPrm("HurtResistantTime", 0.0);
        DisplayHUDThirdPerson = new MCH_ConfigPrm("DisplayHUDThirdPerson", false);
        DisableCameraDistChange = new MCH_ConfigPrm("DisableThirdPersonCameraDistChange", false);
        EnableReplaceTextureManager = new MCH_ConfigPrm("EnableReplaceTextureManager", true);
        DisplayEntityMarker = new MCH_ConfigPrm("DisplayEntityMarker", true);
        DisplayMarkThroughWall = new MCH_ConfigPrm("DisplayMarkThroughWall", true);
        EntityMarkerSize = new MCH_ConfigPrm("EntityMarkerSize", 10.0);
        BlockMarkerSize = new MCH_ConfigPrm("BlockMarkerSize", 10.0);
        ReplaceRenderViewEntity = new MCH_ConfigPrm("ReplaceRenderViewEntity", true);
        StingerLockRange = new MCH_ConfigPrm("StingerLockRange", 320.0);
        MCH_Config.StingerLockRange.validVer = "1.0.0";
        DefaultExplosionParticle = new MCH_ConfigPrm("DefaultExplosionParticle", false);
        RangeFinderSpotDist = new MCH_ConfigPrm("RangeFinderSpotDist", 400);
        RangeFinderSpotTime = new MCH_ConfigPrm("RangeFinderSpotTime", 15);
        RangeFinderConsume = new MCH_ConfigPrm("RangeFinderConsume", true);
        EnablePutRackInFlying = new MCH_ConfigPrm("EnablePutRackInFlying", true);
        EnableDebugBoundingBox = new MCH_ConfigPrm("EnableDebugBoundingBox", true);
        hitMarkColorAlpha = 1.0f;
        hitMarkColorRGB = 0xFF0000;
        ItemRecipe_Fuel = new MCH_ConfigPrm("ItemRecipe_Fuel", "\"ICI\", \"III\", I, iron_ingot, C, coal");
        ItemRecipe_GLTD = new MCH_ConfigPrm("ItemRecipe_GLTD", "\" B \", \"IDI\", \"IRI\", B, iron_block, I, iron_ingot, D, diamond, R, redstone");
        ItemRecipe_Chain = new MCH_ConfigPrm("ItemRecipe_Chain", "\"I I\", \"III\", \"I I\", I, iron_ingot");
        ItemRecipe_Parachute = new MCH_ConfigPrm("ItemRecipe_Parachute", "\"WWW\", \"S S\", \" W \", W, wool, S, string");
        ItemRecipe_Container = new MCH_ConfigPrm("ItemRecipe_Container", "\"CCI\", C, chest, I, iron_ingot");
        ItemRecipe_UavStation = new MCH_ConfigPrm[]{new MCH_ConfigPrm("ItemRecipe_UavStation", "\"III\", \"IDI\", \"IRI\", I, iron_ingot, D, diamond, R, redstone_block"), new MCH_ConfigPrm("ItemRecipe_UavStation2", "\"IDI\", \"IRI\", I, iron_ingot, D, diamond, R, redstone")};
        ItemRecipe_DraftingTable = new MCH_ConfigPrm("ItemRecipe_DraftingTable", "\"R  \", \"PCP\", \"F F\", R, redstone, C, crafting_table, P, planks, F, fence");
        ItemRecipe_Wrench = new MCH_ConfigPrm("ItemRecipe_Wrench", "\" I \", \" II\", \"I  \", I, iron_ingot");
        ItemRecipe_RangeFinder = new MCH_ConfigPrm("ItemRecipe_RangeFinder", "\"III\", \"RGR\", \"III\", I, iron_ingot, G, glass, R, redstone");
        ItemRecipe_Stinger = new MCH_ConfigPrm("ItemRecipe_Stinger", "\"G  \", \"III\", \"RI \", G, glass, I, iron_ingot, R, redstone");
        ItemRecipe_StingerMissile = new MCH_ConfigPrm("ItemRecipe_StingerMissile", "\"R  \", \" I \", \"  G\", G, gunpowder, I, iron_ingot, R, redstone");
        ItemRecipe_Javelin = new MCH_ConfigPrm("ItemRecipe_Javelin", "\"III\", \"GR \", G, glass, I, iron_ingot, R, redstone");
        ItemRecipe_JavelinMissile = new MCH_ConfigPrm("ItemRecipe_JavelinMissile", "\" R \", \" I \", \" G \", G, gunpowder, I, iron_ingot, R, redstone");
        ItemID_GLTD = new MCH_ConfigPrm("ItemID_GLTD", 28799);
        ItemID_Chain = new MCH_ConfigPrm("ItemID_Chain", 28798);
        ItemID_Parachute = new MCH_ConfigPrm("ItemID_Parachute", 28797);
        ItemID_Container = new MCH_ConfigPrm("ItemID_Container", 28796);
        ItemID_UavStation = new MCH_ConfigPrm[]{new MCH_ConfigPrm("ItemID_UavStation", 28795), new MCH_ConfigPrm("ItemID_UavStation2", 28790)};
        ItemID_InvisibleItem = new MCH_ConfigPrm("ItemID_Internal", 28794);
        ItemID_Fuel = new MCH_ConfigPrm("ItemID_Fuel", 28793);
        ItemID_DraftingTable = new MCH_ConfigPrm("ItemID_DraftingTable", 28792);
        ItemID_Wrench = new MCH_ConfigPrm("ItemID_Wrench", 28791);
        ItemID_RangeFinder = new MCH_ConfigPrm("ItemID_RangeFinder", 28789);
        ItemID_Stinger = new MCH_ConfigPrm("ItemID_Stinger", 28900);
        ItemID_StingerMissile = new MCH_ConfigPrm("ItemID_StingerMissile", 28901);
        BlockID_DraftingTableOFF = new MCH_ConfigPrm("BlockID_DraftingTable", 3450);
        BlockID_DraftingTableON = new MCH_ConfigPrm("BlockID_DraftingTableON", 3451);
        MCH_ConfigPrm[] mCH_ConfigPrmArray2 = new MCH_ConfigPrm[86];
        mCH_ConfigPrmArray2[0] = TestMode;
        mCH_ConfigPrmArray2[1] = EnableCommand;
        mCH_ConfigPrmArray2[2] = null;
        mCH_ConfigPrmArray2[3] = PlaceableOnSpongeOnly;
        mCH_ConfigPrmArray2[4] = ItemDamage;
        mCH_ConfigPrmArray2[5] = ItemFuel;
        mCH_ConfigPrmArray2[6] = AutoRepairHP;
        mCH_ConfigPrmArray2[7] = Explosion_DestroyBlock;
        mCH_ConfigPrmArray2[8] = Explosion_FlamingBlock;
        mCH_ConfigPrmArray2[9] = BulletBreakableBlock;
        mCH_ConfigPrmArray2[10] = Collision_DestroyBlock;
        mCH_ConfigPrmArray2[11] = Collision_Car_BreakableBlock;
        mCH_ConfigPrmArray2[12] = Collision_Car_BreakableMaterial;
        mCH_ConfigPrmArray2[13] = Collision_Tank_BreakableBlock;
        mCH_ConfigPrmArray2[14] = Collision_Tank_BreakableMaterial;
        mCH_ConfigPrmArray2[15] = Collision_EntityDamage;
        mCH_ConfigPrmArray2[16] = Collision_EntityTankDamage;
        mCH_ConfigPrmArray2[17] = InfinityAmmo;
        mCH_ConfigPrmArray2[18] = InfinityFuel;
        mCH_ConfigPrmArray2[19] = DismountAll;
        mCH_ConfigPrmArray2[20] = MountMinecartHeli;
        mCH_ConfigPrmArray2[21] = MountMinecartPlane;
        mCH_ConfigPrmArray2[22] = MountMinecartVehicle;
        mCH_ConfigPrmArray2[23] = MountMinecartTank;
        mCH_ConfigPrmArray2[24] = PreventingBroken;
        mCH_ConfigPrmArray2[25] = DropItemInCreativeMode;
        mCH_ConfigPrmArray2[26] = BreakableOnlyPickaxe;
        mCH_ConfigPrmArray2[27] = AllHeliSpeed;
        mCH_ConfigPrmArray2[28] = AllPlaneSpeed;
        mCH_ConfigPrmArray2[29] = AllTankSpeed;
        mCH_ConfigPrmArray2[30] = HurtResistantTime;
        mCH_ConfigPrmArray2[31] = StingerLockRange;
        mCH_ConfigPrmArray2[32] = RangeFinderSpotDist;
        mCH_ConfigPrmArray2[33] = RangeFinderSpotTime;
        mCH_ConfigPrmArray2[34] = RangeFinderConsume;
        mCH_ConfigPrmArray2[35] = EnablePutRackInFlying;
        mCH_ConfigPrmArray2[36] = EnableDebugBoundingBox;
        mCH_ConfigPrmArray2[37] = null;
        mCH_ConfigPrmArray2[38] = InvertMouse;
        mCH_ConfigPrmArray2[39] = MouseSensitivity;
        mCH_ConfigPrmArray2[40] = MouseControlStickModeHeli;
        mCH_ConfigPrmArray2[41] = MouseControlStickModePlane;
        mCH_ConfigPrmArray2[42] = MouseControlFlightSimMode;
        mCH_ConfigPrmArray2[43] = AutoThrottleDownHeli;
        mCH_ConfigPrmArray2[44] = AutoThrottleDownPlane;
        mCH_ConfigPrmArray2[45] = AutoThrottleDownTank;
        mCH_ConfigPrmArray2[46] = SwitchWeaponWithMouseWheel;
        mCH_ConfigPrmArray2[47] = LWeaponAutoFire;
        mCH_ConfigPrmArray2[48] = DisableItemRender;
        mCH_ConfigPrmArray2[49] = HideKeybind;
        mCH_ConfigPrmArray2[50] = RenderDistanceWeight;
        mCH_ConfigPrmArray2[51] = MobRenderDistanceWeight;
        mCH_ConfigPrmArray2[52] = CreativeTabIcon;
        mCH_ConfigPrmArray2[53] = CreativeTabIconHeli;
        mCH_ConfigPrmArray2[54] = CreativeTabIconPlane;
        mCH_ConfigPrmArray2[55] = CreativeTabIconTank;
        mCH_ConfigPrmArray2[56] = CreativeTabIconVehicle;
        mCH_ConfigPrmArray2[57] = DisableShader;
        mCH_ConfigPrmArray2[58] = DefaultExplosionParticle;
        mCH_ConfigPrmArray2[59] = AliveTimeOfCartridge;
        mCH_ConfigPrmArray2[60] = HitMarkColor;
        mCH_ConfigPrmArray2[61] = SmoothShading;
        mCH_ConfigPrmArray2[62] = EnableModEntityRender;
        mCH_ConfigPrmArray2[63] = DisableRenderLivingSpecials;
        mCH_ConfigPrmArray2[64] = DisplayHUDThirdPerson;
        mCH_ConfigPrmArray2[65] = DisableCameraDistChange;
        mCH_ConfigPrmArray2[66] = EnableReplaceTextureManager;
        mCH_ConfigPrmArray2[67] = DisplayEntityMarker;
        mCH_ConfigPrmArray2[68] = EntityMarkerSize;
        mCH_ConfigPrmArray2[69] = BlockMarkerSize;
        mCH_ConfigPrmArray2[70] = ReplaceRenderViewEntity;
        mCH_ConfigPrmArray2[71] = null;
        mCH_ConfigPrmArray2[72] = ItemRecipe_Fuel;
        mCH_ConfigPrmArray2[73] = ItemRecipe_GLTD;
        mCH_ConfigPrmArray2[74] = ItemRecipe_Chain;
        mCH_ConfigPrmArray2[75] = ItemRecipe_Parachute;
        mCH_ConfigPrmArray2[76] = ItemRecipe_Container;
        mCH_ConfigPrmArray2[77] = ItemRecipe_UavStation[0];
        mCH_ConfigPrmArray2[78] = ItemRecipe_UavStation[1];
        mCH_ConfigPrmArray2[79] = ItemRecipe_DraftingTable;
        mCH_ConfigPrmArray2[80] = ItemRecipe_Wrench;
        mCH_ConfigPrmArray2[81] = ItemRecipe_RangeFinder;
        mCH_ConfigPrmArray2[82] = ItemRecipe_Stinger;
        mCH_ConfigPrmArray2[83] = ItemRecipe_StingerMissile;
        mCH_ConfigPrmArray2[84] = ItemRecipe_Javelin;
        mCH_ConfigPrmArray2[85] = ItemRecipe_JavelinMissile;
        General = mCH_ConfigPrmArray2;
        DamageVsEntity = new DamageFactor(this, "DamageVsEntity");
        DamageVsLiving = new DamageFactor(this, "DamageVsLiving");
        DamageVsPlayer = new DamageFactor(this, "DamageVsPlayer");
        DamageVsMCHeliAircraft = new DamageFactor(this, "DamageVsMCHeliAircraft");
        DamageVsMCHeliTank = new DamageFactor(this, "DamageVsMCHeliTank");
        DamageVsMCHeliVehicle = new DamageFactor(this, "DamageVsMCHeliVehicle");
        DamageVsMCHeliOther = new DamageFactor(this, "DamageVsMCHeliOther");
        DamageAircraftByExternal = new DamageFactor(this, "DamageMCHeliAircraftByExternal");
        DamageTankByExternal = new DamageFactor(this, "DamageMCHeliTankByExternal");
        DamageVehicleByExternal = new DamageFactor(this, "DamageMCHeliVehicleByExternal");
        DamageOtherByExternal = new DamageFactor(this, "DamageMCHeliOtherByExternal");
        DamageFactor[] damageFactorArray = new DamageFactor[11];
        damageFactorArray[0] = DamageVsEntity;
        damageFactorArray[1] = DamageVsLiving;
        damageFactorArray[2] = DamageVsPlayer;
        damageFactorArray[3] = DamageVsMCHeliAircraft;
        damageFactorArray[4] = DamageVsMCHeliTank;
        damageFactorArray[5] = DamageVsMCHeliVehicle;
        damageFactorArray[6] = DamageVsMCHeliOther;
        damageFactorArray[7] = DamageAircraftByExternal;
        damageFactorArray[8] = DamageTankByExternal;
        damageFactorArray[9] = DamageVehicleByExternal;
        damageFactorArray[10] = DamageOtherByExternal;
        DamageFactorList = damageFactorArray;
    }

    public void setBlockListFromString(List<Block> list, String str) {
        String[] s;
        list.clear();
        for (String blockName : s = str.split("\\s*,\\s*")) {
            Block b = W_Block.getBlockFromName((String)blockName);
            if (b == null) continue;
            list.add(b);
        }
    }

    public void setMaterialListFromString(List<Material> list, String str) {
        String[] s;
        list.clear();
        for (String name : s = str.split("\\s*,\\s*")) {
            Material m = MCH_Lib.getMaterialFromName((String)name);
            if (m == null) continue;
            list.add(m);
        }
    }

    public void correctionParameter() {
        String[] s = MCH_Config.HitMarkColor.prmString.split("\\s*,\\s*");
        if (s.length == 4) {
            hitMarkColorAlpha = (float)this.toInt255(s[0]) / 255.0f;
            hitMarkColorRGB = this.toInt255(s[1]) << 16 | this.toInt255(s[2]) << 8 | this.toInt255(s[3]);
        }
        MCH_Config.AllHeliSpeed.prmDouble = MCH_Lib.RNG((double)MCH_Config.AllHeliSpeed.prmDouble, (double)0.0, (double)1000.0);
        MCH_Config.AllPlaneSpeed.prmDouble = MCH_Lib.RNG((double)MCH_Config.AllPlaneSpeed.prmDouble, (double)0.0, (double)1000.0);
        MCH_Config.AllTankSpeed.prmDouble = MCH_Lib.RNG((double)MCH_Config.AllTankSpeed.prmDouble, (double)0.0, (double)1000.0);
        this.setBlockListFromString(bulletBreakableBlocks, MCH_Config.BulletBreakableBlock.prmString);
        this.setBlockListFromString(carBreakableBlocks, MCH_Config.Collision_Car_BreakableBlock.prmString);
        this.setBlockListFromString(carNoBreakableBlocks, MCH_Config.Collision_Car_NoBreakableBlock.prmString);
        this.setMaterialListFromString(carBreakableMaterials, MCH_Config.Collision_Car_BreakableMaterial.prmString);
        this.setBlockListFromString(tankBreakableBlocks, MCH_Config.Collision_Tank_BreakableBlock.prmString);
        this.setBlockListFromString(tankNoBreakableBlocks, MCH_Config.Collision_Tank_NoBreakableBlock.prmString);
        this.setMaterialListFromString(tankBreakableMaterials, MCH_Config.Collision_Tank_BreakableMaterial.prmString);
        if (MCH_Config.EntityMarkerSize.prmDouble < 0.0) {
            MCH_Config.EntityMarkerSize.prmDouble = 0.0;
        }
        if (MCH_Config.BlockMarkerSize.prmDouble < 0.0) {
            MCH_Config.BlockMarkerSize.prmDouble = 0.0;
        }
        if (MCH_Config.HurtResistantTime.prmDouble < 0.0) {
            MCH_Config.HurtResistantTime.prmDouble = 0.0;
        }
        if (MCH_Config.HurtResistantTime.prmDouble > 10000.0) {
            MCH_Config.HurtResistantTime.prmDouble = 10000.0;
        }
        if (MCH_Config.MobRenderDistanceWeight.prmDouble < 0.1) {
            MCH_Config.MobRenderDistanceWeight.prmDouble = 0.1;
        } else if (MCH_Config.MobRenderDistanceWeight.prmDouble > 10.0) {
            MCH_Config.MobRenderDistanceWeight.prmDouble = 10.0;
        }
        for (MCH_ConfigPrm p : CommandPermission) {
            CommandPermission cpm = new CommandPermission(this, p.prmString);
            if (cpm.name.isEmpty()) continue;
            CommandPermissionList.add(cpm);
        }
        if (IgnoreBulletHitList.size() <= 0) {
            IgnoreBulletHitList.add("flansmod.common.guns.EntityBullet");
            IgnoreBulletHitList.add("flansmod.common.guns.EntityGrenade");
        }
        boolean isNoDamageVsSetting = DamageVs.size() <= 0;
        for (MCH_ConfigPrm p : DamageVs) {
            for (DamageFactor df : DamageFactorList) {
                if (!p.name.equals(df.itemName)) continue;
                df.list.add(this.newDamageEntity(p.prmString));
            }
        }
        for (DamageFactor df : DamageFactorList) {
            if (df.list.size() <= 0) {
                DamageVs.add(new MCH_ConfigPrm(df.itemName, "1.0"));
                continue;
            }
            boolean foundCommon = false;
            for (DamageEntity n : df.list) {
                if (!n.name.isEmpty()) continue;
                foundCommon = true;
                break;
            }
            if (foundCommon) continue;
            DamageVs.add(new MCH_ConfigPrm(df.itemName, "1.0"));
        }
        if (isNoDamageVsSetting) {
            DamageVs.add(new MCH_ConfigPrm("DamageVsEntity", "3.0, flansmod"));
            DamageVs.add(new MCH_ConfigPrm("DamageMCHeliAircraftByExternal", "0.5, flansmod"));
            DamageVs.add(new MCH_ConfigPrm("DamageMCHeliVehicleByExternal", "0.5, flansmod"));
        }
    }

    public DamageEntity newDamageEntity(String s) {
        String[] splt = s.split("\\s*,\\s*");
        if (splt.length == 1) {
            return new DamageEntity(this, Double.parseDouble(splt[0]), "");
        }
        if (splt.length == 2) {
            return new DamageEntity(this, Double.parseDouble(splt[0]), splt[1]);
        }
        return new DamageEntity(this, 1.0, "");
    }

    public static float applyDamageByExternal(Entity target, DamageSource ds, float damage) {
        List list = target instanceof MCH_EntityHeli || target instanceof MCP_EntityPlane ? MCH_Config.DamageAircraftByExternal.list : (target instanceof MCH_EntityTank ? MCH_Config.DamageTankByExternal.list : (target instanceof MCH_EntityVehicle ? MCH_Config.DamageVehicleByExternal.list : MCH_Config.DamageOtherByExternal.list));
        Entity attacker = ds.func_76346_g();
        Entity attackerSource = ds.func_76364_f();
        for (DamageEntity de : list) {
            if (!de.name.isEmpty() && (attacker == null || attacker.getClass().toString().indexOf(de.name) <= 0) && (attackerSource == null || attackerSource.getClass().toString().indexOf(de.name) <= 0)) continue;
            damage = (float)((double)damage * de.factor);
        }
        return damage;
    }

    public static float applyDamageVsEntity(Entity target, DamageSource ds, float damage) {
        if (target == null) {
            return damage;
        }
        String targetName = target.getClass().toString();
        List list = target instanceof MCH_EntityHeli || target instanceof MCP_EntityPlane ? MCH_Config.DamageVsMCHeliAircraft.list : (target instanceof MCH_EntityTank ? MCH_Config.DamageVsMCHeliTank.list : (target instanceof MCH_EntityVehicle ? MCH_Config.DamageVsMCHeliVehicle.list : (targetName.indexOf("mcheli.") > 0 ? MCH_Config.DamageVsMCHeliOther.list : (target instanceof EntityPlayer ? MCH_Config.DamageVsPlayer.list : (target instanceof EntityLivingBase ? MCH_Config.DamageVsLiving.list : MCH_Config.DamageVsEntity.list)))));
        for (DamageEntity de : list) {
            if (!de.name.isEmpty() && targetName.indexOf(de.name) <= 0) continue;
            damage = (float)((double)damage * de.factor);
        }
        return damage;
    }

    public static List<Block> getBreakableBlockListFromType(int n) {
        if (n == 2) {
            return tankBreakableBlocks;
        }
        if (n == 1) {
            return carBreakableBlocks;
        }
        return dummyBreakableBlocks;
    }

    public static List<Block> getNoBreakableBlockListFromType(int n) {
        if (n == 2) {
            return tankNoBreakableBlocks;
        }
        if (n == 1) {
            return carNoBreakableBlocks;
        }
        return dummyBreakableBlocks;
    }

    public static List<Material> getBreakableMaterialListFromType(int n) {
        if (n == 2) {
            return tankBreakableMaterials;
        }
        if (n == 1) {
            return carBreakableMaterials;
        }
        return dummyBreakableMaterials;
    }

    public int toInt255(String s) {
        int a = Integer.valueOf(s);
        return a < 0 ? 0 : (a > 255 ? 255 : a);
    }

    public void load() {
        MCH_InputFile file = new MCH_InputFile();
        if (file.open(configFilePath)) {
            String str = file.readLine();
            while (str != null) {
                if (str.trim().equalsIgnoreCase("McHeliOutputDebugLog")) {
                    DebugLog = true;
                } else {
                    this.readConfigData(str);
                }
                str = file.readLine();
            }
            file.close();
            MCH_Lib.Log((String)("loaded " + file.file.getAbsolutePath()), (Object[])new Object[0]);
        } else {
            MCH_Lib.Log((String)("" + new File(configFilePath).getAbsolutePath() + " not found."), (Object[])new Object[0]);
        }
        this.correctionParameter();
    }

    private void readConfigData(String str) {
        String[] s = str.split("=");
        if (s.length != 2) {
            return;
        }
        s[0] = s[0].trim();
        s[1] = s[1].trim();
        if (s[0].equalsIgnoreCase("MOD_Version")) {
            configVer = s[1];
            return;
        }
        if (s[0].equalsIgnoreCase("CommandPermission")) {
            CommandPermission.add(new MCH_ConfigPrm("CommandPermission", s[1]));
        }
        for (DamageFactor damageFactor : DamageFactorList) {
            if (!damageFactor.itemName.equalsIgnoreCase(s[0])) continue;
            DamageVs.add(new MCH_ConfigPrm(damageFactor.itemName, s[1]));
        }
        if (IgnoreBulletHitItem.compare(s[0])) {
            IgnoreBulletHitList.add(s[1]);
        }
        for (DamageFactor damageFactor : KeyConfig) {
            if (damageFactor == null || !damageFactor.compare(s[0])) continue;
            if (!damageFactor.isValidVer(configVer)) continue;
            damageFactor.setPrm(s[1]);
            return;
        }
        for (DamageFactor damageFactor : General) {
            if (damageFactor == null || !damageFactor.compare(s[0])) continue;
            if (!damageFactor.isValidVer(configVer)) continue;
            damageFactor.setPrm(s[1]);
            return;
        }
    }

    public void write() {
        MCH_OutputFile file = new MCH_OutputFile();
        if (file.open(configFilePath)) {
            this.writeConfigData(file.pw);
            file.close();
            MCH_Lib.Log((String)("update " + file.file.getAbsolutePath()), (Object[])new Object[0]);
        } else {
            MCH_Lib.Log((String)("" + new File(configFilePath).getAbsolutePath() + " cannot open."), (Object[])new Object[0]);
        }
    }

    private void writeConfigData(PrintWriter pw) {
        pw.println("[General]");
        pw.println("MOD_Name = mcheli");
        pw.println("MOD_Version = " + MCH_MOD.VER);
        pw.println("MOD_MC_Version = 1.7.10");
        pw.println();
        if (DebugLog) {
            pw.println("McHeliOutputDebugLog");
            pw.println();
        }
        for (MCH_ConfigPrm p : General) {
            if (p != null) {
                if (!p.desc.isEmpty()) {
                    pw.println(p.desc);
                }
                pw.println(p.name + " = " + p);
                continue;
            }
            pw.println("");
        }
        pw.println();
        for (MCH_ConfigPrm p : DamageVs) {
            pw.println(p.name + " = " + p);
        }
        pw.println();
        for (String s : IgnoreBulletHitList) {
            pw.println(MCH_Config.IgnoreBulletHitItem.name + " = " + s);
        }
        pw.println();
        pw.println(";CommandPermission = commandName(eg, modlist, status, fill...):playerName1, playerName2, playerName3...");
        if (CommandPermission.size() == 0) {
            pw.println(";CommandPermission = modlist :example1, example2");
            pw.println(";CommandPermission = status :  example2");
        }
        for (MCH_ConfigPrm p : CommandPermission) {
            pw.println(p.name + " = " + p);
        }
        pw.println();
        pw.println();
        pw.println("[Key config]");
        pw.println("http://minecraft.gamepedia.com/Key_codes");
        pw.println();
        for (MCH_ConfigPrm p : KeyConfig) {
            pw.println(p.name + " = " + p);
        }
    }

    static {
        dummyBreakableBlocks = new ArrayList();
        dummyBreakableMaterials = new ArrayList();
    }
}

