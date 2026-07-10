package mcheli;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import java.io.File;
import mcheli.aircraft.MCH_EntityHide;
import mcheli.aircraft.MCH_EntityHitBox;
import mcheli.aircraft.MCH_EntitySeat;
import mcheli.aircraft.MCH_ItemAircraft;
import mcheli.aircraft.MCH_ItemFuel;
import mcheli.block.MCH_DraftingTableBlock;
import mcheli.block.MCH_DraftingTableTileEntity;
import mcheli.chain.MCH_EntityChain;
import mcheli.chain.MCH_ItemChain;
import mcheli.command.MCH_Command;
import mcheli.container.MCH_EntityContainer;
import mcheli.container.MCH_ItemContainer;
import mcheli.flare.MCH_EntityFlare;
import mcheli.gltd.MCH_EntityGLTD;
import mcheli.gltd.MCH_ItemGLTD;
import mcheli.gui.MCH_GuiCommonHandler;
import mcheli.helicopter.MCH_EntityHeli;
import mcheli.helicopter.MCH_HeliInfo;
import mcheli.helicopter.MCH_HeliInfoManager;
import mcheli.helicopter.MCH_ItemHeli;
import mcheli.lweapon.MCH_ItemLightWeaponBase;
import mcheli.lweapon.MCH_ItemLightWeaponBullet;
import mcheli.parachute.MCH_EntityParachute;
import mcheli.parachute.MCH_ItemParachute;
import mcheli.plane.MCP_EntityPlane;
import mcheli.plane.MCP_ItemPlane;
import mcheli.plane.MCP_PlaneInfo;
import mcheli.plane.MCP_PlaneInfoManager;
import mcheli.tank.MCH_EntityTank;
import mcheli.tank.MCH_ItemTank;
import mcheli.tank.MCH_TankInfo;
import mcheli.tank.MCH_TankInfoManager;
import mcheli.throwable.MCH_EntityThrowable;
import mcheli.throwable.MCH_ItemThrowable;
import mcheli.throwable.MCH_ThrowableInfo;
import mcheli.throwable.MCH_ThrowableInfoManager;
import mcheli.tool.MCH_ItemWrench;
import mcheli.tool.rangefinder.MCH_ItemRangeFinder;
import mcheli.uav.MCH_EntityUavStation;
import mcheli.uav.MCH_ItemUavStation;
import mcheli.vehicle.MCH_EntityVehicle;
import mcheli.vehicle.MCH_ItemVehicle;
import mcheli.vehicle.MCH_VehicleInfo;
import mcheli.vehicle.MCH_VehicleInfoManager;
import mcheli.weapon.MCH_EntityA10;
import mcheli.weapon.MCH_EntityAAMissile;
import mcheli.weapon.MCH_EntityASMissile;
import mcheli.weapon.MCH_EntityATMissile;
import mcheli.weapon.MCH_EntityBomb;
import mcheli.weapon.MCH_EntityBullet;
import mcheli.weapon.MCH_EntityDispensedItem;
import mcheli.weapon.MCH_EntityMarkerRocket;
import mcheli.weapon.MCH_EntityRocket;
import mcheli.weapon.MCH_EntityTorpedo;
import mcheli.weapon.MCH_EntityTvMissile;
import mcheli.weapon.MCH_WeaponInfoManager;
import mcheli.wrapper.NetworkMod;
import mcheli.wrapper.W_Item;
import mcheli.wrapper.W_ItemList;
import mcheli.wrapper.W_LanguageRegistry;
import mcheli.wrapper.W_NetworkRegistry;
import net.minecraft.command.CommandHandler;
import net.minecraft.item.Item;
import net.minecraft.item.Item.ToolMaterial;
import net.minecraftforge.common.MinecraftForge;

@Mod(modid = "mcheli", name = "mcheli", dependencies = "required-after:Forge@[10.13.2.1230,)")
@NetworkMod(clientSideRequired = true, serverSideRequired = false)
public class MCH_MOD {
   public static final String MOD_ID = "mcheli";
   public static final String DOMAIN = "mcheli";
   public static final String MCVER = "1.7.10";
   public static String VER = "";
   public static final String MOD_CH = "MCHeli_CH";
   @Instance("mcheli")
   public static MCH_MOD instance;
   @SidedProxy(clientSide = "mcheli.MCH_ClientProxy", serverSide = "mcheli.MCH_CommonProxy")
   public static MCH_CommonProxy proxy;
   public static MCH_PacketHandler packetHandler = new MCH_PacketHandler();
   public static MCH_Config config;
   public static String sourcePath;
   public static MCH_InvisibleItem invisibleItem;
   public static MCH_ItemGLTD itemGLTD;
   public static MCH_ItemLightWeaponBullet itemStingerBullet;
   public static MCH_ItemLightWeaponBase itemStinger;
   public static MCH_ItemLightWeaponBullet itemJavelinBullet;
   public static MCH_ItemLightWeaponBase itemJavelin;
   public static MCH_ItemUavStation[] itemUavStation;
   public static MCH_ItemParachute itemParachute;
   public static MCH_ItemContainer itemContainer;
   public static MCH_ItemChain itemChain;
   public static MCH_ItemFuel itemFuel;
   public static MCH_ItemWrench itemWrench;
   public static MCH_ItemRangeFinder itemRangeFinder;
   public static MCH_CreativeTabs creativeTabs;
   public static MCH_CreativeTabs creativeTabsHeli;
   public static MCH_CreativeTabs creativeTabsPlane;
   public static MCH_CreativeTabs creativeTabsTank;
   public static MCH_CreativeTabs creativeTabsVehicle;
   public static MCH_DraftingTableBlock blockDraftingTable;
   public static MCH_DraftingTableBlock blockDraftingTableLit;
   public static Item sampleHelmet;

   @EventHandler
   public void PreInit(FMLPreInitializationEvent evt) {
      VER = Loader.instance().activeModContainer().getVersion();
      MCH_Lib.init();
      MCH_Lib.Log("MC Ver:1.7.10 MOD Ver:" + VER + "");
      MCH_Lib.Log("Start load...");
      sourcePath = Loader.instance().activeModContainer().getSource().getPath();
      MCH_Lib.Log("SourcePath: " + sourcePath);
      MCH_Lib.Log("CurrentDirectory:" + new File(".").getAbsolutePath());
      proxy.init();
      creativeTabs = new MCH_CreativeTabs("MC Heli Item");
      creativeTabsHeli = new MCH_CreativeTabs("MC Heli Helicopters");
      creativeTabsPlane = new MCH_CreativeTabs("MC Heli Planes");
      creativeTabsTank = new MCH_CreativeTabs("MC Heli Tanks");
      creativeTabsVehicle = new MCH_CreativeTabs("MC Heli Vehicles");
      W_ItemList.init();
      config = proxy.loadConfig("config/mcheli.cfg");
      proxy.loadHUD(sourcePath + "/assets/" + "mcheli" + "/hud");
      MCH_WeaponInfoManager.load(sourcePath + "/assets/" + "mcheli" + "/weapons");
      MCH_HeliInfoManager.getInstance().load(sourcePath + "/assets/" + "mcheli" + "/", "helicopters");
      MCP_PlaneInfoManager.getInstance().load(sourcePath + "/assets/" + "mcheli" + "/", "planes");
      MCH_TankInfoManager.getInstance().load(sourcePath + "/assets/" + "mcheli" + "/", "tanks");
      MCH_VehicleInfoManager.getInstance().load(sourcePath + "/assets/" + "mcheli" + "/", "vehicles");
      MCH_ThrowableInfoManager.load(sourcePath + "/assets/" + "mcheli" + "/throwable");
      MCH_SoundsJson.update(sourcePath + "/assets/" + "mcheli" + "/");
      MCH_Lib.Log("Register item");
      this.registerItemRangeFinder();
      this.registerItemWrench();
      this.registerItemFuel();
      this.registerItemGLTD();
      this.registerItemChain();
      this.registerItemParachute();
      this.registerItemContainer();
      this.registerItemUavStation();
      this.registerItemInvisible();
      registerItemThrowable();
      this.registerItemLightWeaponBullet();
      this.registerItemLightWeapon();
      registerItemAircraft();
      blockDraftingTable = new MCH_DraftingTableBlock(MCH_Config.BlockID_DraftingTableOFF.prmInt, false);
      blockDraftingTable.setBlockName("drafting_table");
      blockDraftingTable.setCreativeTab(creativeTabs);
      blockDraftingTableLit = new MCH_DraftingTableBlock(MCH_Config.BlockID_DraftingTableON.prmInt, true);
      blockDraftingTableLit.setBlockName("lit_drafting_table");
      GameRegistry.registerBlock(blockDraftingTable, "drafting_table");
      GameRegistry.registerBlock(blockDraftingTableLit, "lit_drafting_table");
      W_LanguageRegistry.addName(blockDraftingTable, "Drafting Table");
      W_LanguageRegistry.addNameForObject(blockDraftingTable, "ja_JP", "製図台");
      MCH_Achievement.PreInit();
      MCH_Lib.Log("Register system");
      W_NetworkRegistry.registerChannel(packetHandler, "MCHeli_CH");
      MinecraftForge.EVENT_BUS.register(new MCH_EventHook());
      proxy.registerClientTick();
      W_NetworkRegistry.registerGuiHandler(this, new MCH_GuiCommonHandler());
      MCH_Lib.Log("Register entity");
      this.registerEntity();
      MCH_Lib.Log("Register renderer");
      proxy.registerRenderer();
      MCH_Lib.Log("Register models");
      proxy.registerModels();
      MCH_Lib.Log("Register Sounds");
      proxy.registerSounds();
      W_LanguageRegistry.updateLang(sourcePath + "/assets/" + "mcheli" + "/lang/");
      MCH_Lib.Log("End load");
   }

   @EventHandler
   public void init(FMLInitializationEvent evt) {
      GameRegistry.registerTileEntity(MCH_DraftingTableTileEntity.class, "drafting_table");
      proxy.registerBlockRenderer();
   }

   @EventHandler
   public void postInit(FMLPostInitializationEvent evt) {
      creativeTabs.setFixedIconItem(MCH_Config.CreativeTabIcon.prmString);
      creativeTabsHeli.setFixedIconItem(MCH_Config.CreativeTabIconHeli.prmString);
      creativeTabsPlane.setFixedIconItem(MCH_Config.CreativeTabIconPlane.prmString);
      creativeTabsTank.setFixedIconItem(MCH_Config.CreativeTabIconTank.prmString);
      creativeTabsVehicle.setFixedIconItem(MCH_Config.CreativeTabIconVehicle.prmString);
      MCH_ItemRecipe.registerItemRecipe();
      MCH_WeaponInfoManager.setRoundItems();
      proxy.readClientModList();
   }

   @EventHandler
   public void onStartServer(FMLServerStartingEvent event) {
      proxy.registerServerTick();
   }

   public void registerEntity() {
      EntityRegistry.registerModEntity(MCH_EntitySeat.class, "MCH.E.Seat", 100, this, 200, 10, true);
      EntityRegistry.registerModEntity(MCH_EntityHeli.class, "MCH.E.Heli", 101, this, 200, 10, true);
      EntityRegistry.registerModEntity(MCH_EntityGLTD.class, "MCH.E.GLTD", 102, this, 200, 10, true);
      EntityRegistry.registerModEntity(MCP_EntityPlane.class, "MCH.E.Plane", 103, this, 200, 10, true);
      EntityRegistry.registerModEntity(MCH_EntityChain.class, "MCH.E.Chain", 104, this, 200, 10, true);
      EntityRegistry.registerModEntity(MCH_EntityHitBox.class, "MCH.E.PSeat", 105, this, 200, 10, true);
      EntityRegistry.registerModEntity(MCH_EntityParachute.class, "MCH.E.Parachute", 106, this, 200, 10, true);
      EntityRegistry.registerModEntity(MCH_EntityContainer.class, "MCH.E.Container", 107, this, 200, 10, true);
      EntityRegistry.registerModEntity(MCH_EntityVehicle.class, "MCH.E.Vehicle", 108, this, 200, 10, true);
      EntityRegistry.registerModEntity(MCH_EntityUavStation.class, "MCH.E.UavStation", 109, this, 200, 10, true);
      EntityRegistry.registerModEntity(MCH_EntityHitBox.class, "MCH.E.HitBox", 110, this, 200, 10, true);
      EntityRegistry.registerModEntity(MCH_EntityHide.class, "MCH.E.Hide", 111, this, 200, 10, true);
      EntityRegistry.registerModEntity(MCH_EntityTank.class, "MCH.E.Tank", 112, this, 200, 10, true);
      EntityRegistry.registerModEntity(MCH_EntityRocket.class, "MCH.E.Rocket", 200, this, 530, 5, true);
      EntityRegistry.registerModEntity(MCH_EntityTvMissile.class, "MCH.E.TvMissle", 201, this, 530, 5, true);
      EntityRegistry.registerModEntity(MCH_EntityBullet.class, "MCH.E.Bullet", 202, this, 530, 5, true);
      EntityRegistry.registerModEntity(MCH_EntityA10.class, "MCH.E.A10", 203, this, 530, 5, true);
      EntityRegistry.registerModEntity(MCH_EntityAAMissile.class, "MCH.E.AAM", 204, this, 530, 5, true);
      EntityRegistry.registerModEntity(MCH_EntityASMissile.class, "MCH.E.ASM", 205, this, 530, 5, true);
      EntityRegistry.registerModEntity(MCH_EntityTorpedo.class, "MCH.E.Torpedo", 206, this, 530, 5, true);
      EntityRegistry.registerModEntity(MCH_EntityATMissile.class, "MCH.E.ATMissle", 207, this, 530, 5, true);
      EntityRegistry.registerModEntity(MCH_EntityBomb.class, "MCH.E.Bomb", 208, this, 530, 5, true);
      EntityRegistry.registerModEntity(MCH_EntityMarkerRocket.class, "MCH.E.MkRocket", 209, this, 530, 5, true);
      EntityRegistry.registerModEntity(MCH_EntityDispensedItem.class, "MCH.E.DispItem", 210, this, 530, 5, true);
      EntityRegistry.registerModEntity(MCH_EntityFlare.class, "MCH.E.Flare", 300, this, 330, 10, true);
      EntityRegistry.registerModEntity(MCH_EntityThrowable.class, "MCH.E.Throwable", 400, this, 330, 10, true);
   }

   @EventHandler
   public void registerCommand(FMLServerStartedEvent e) {
      CommandHandler handler = (CommandHandler)FMLCommonHandler.instance().getSidedDelegate().getServer().getCommandManager();
      handler.registerCommand(new MCH_Command());
   }

   private void registerItemRangeFinder() {
      String name = "rangefinder";
      MCH_ItemRangeFinder item = new MCH_ItemRangeFinder(MCH_Config.ItemID_RangeFinder.prmInt);
      itemRangeFinder = item;
      registerItem(item, "rangefinder", creativeTabs);
      W_LanguageRegistry.addName(item, "Laser Rangefinder");
      W_LanguageRegistry.addNameForObject(item, "ja_JP", "レーザー レンジ ファインダー");
   }

   private void registerItemWrench() {
      String name = "wrench";
      MCH_ItemWrench item = new MCH_ItemWrench(MCH_Config.ItemID_Wrench.prmInt, ToolMaterial.IRON);
      itemWrench = item;
      registerItem(item, "wrench", creativeTabs);
      W_LanguageRegistry.addName(item, "Wrench");
      W_LanguageRegistry.addNameForObject(item, "ja_JP", "レンチ");
   }

   public void registerItemInvisible() {
      String name = "internal";
      MCH_InvisibleItem item = new MCH_InvisibleItem(MCH_Config.ItemID_InvisibleItem.prmInt);
      invisibleItem = item;
      registerItem(item, "internal", null);
   }

   public void registerItemUavStation() {
      String[] dispName = new String[]{"UAV Station", "Portable UAV Controller"};
      String[] localName = new String[]{"UAVステーション", "携帯UAV制御端末"};
      itemUavStation = new MCH_ItemUavStation[MCH_ItemUavStation.UAV_STATION_KIND_NUM];
      String name = "uav_station";

      for (int i = 0; i < itemUavStation.length; i++) {
         String nn = i > 0 ? "" + (i + 1) : "";
         MCH_ItemUavStation item = new MCH_ItemUavStation(MCH_Config.ItemID_UavStation[i].prmInt, 1 + i);
         itemUavStation[i] = item;
         registerItem(item, "uav_station" + nn, creativeTabs);
         W_LanguageRegistry.addName(item, dispName[i]);
         W_LanguageRegistry.addNameForObject(item, "ja_JP", localName[i]);
      }
   }

   public void registerItemParachute() {
      String name = "parachute";
      MCH_ItemParachute item = new MCH_ItemParachute(MCH_Config.ItemID_Parachute.prmInt);
      itemParachute = item;
      registerItem(item, "parachute", creativeTabs);
      W_LanguageRegistry.addName(item, "Parachute");
      W_LanguageRegistry.addNameForObject(item, "ja_JP", "パラシュート");
   }

   public void registerItemContainer() {
      String name = "container";
      MCH_ItemContainer item = new MCH_ItemContainer(MCH_Config.ItemID_Container.prmInt);
      itemContainer = item;
      registerItem(item, "container", creativeTabs);
      W_LanguageRegistry.addName(item, "Container");
      W_LanguageRegistry.addNameForObject(item, "ja_JP", "コンテナ");
   }

   public void registerItemLightWeapon() {
      String name = "fim92";
      MCH_ItemLightWeaponBase item = new MCH_ItemLightWeaponBase(MCH_Config.ItemID_Stinger.prmInt, itemStingerBullet);
      itemStinger = item;
      registerItem(item, name, creativeTabs);
      W_LanguageRegistry.addName(item, "FIM-92 Stinger");
      name = "fgm148";
      item = new MCH_ItemLightWeaponBase(MCH_Config.ItemID_Stinger.prmInt, itemJavelinBullet);
      itemJavelin = item;
      registerItem(item, name, creativeTabs);
      W_LanguageRegistry.addName(item, "FGM-148 Javelin");
   }

   public void registerItemLightWeaponBullet() {
      String name = "fim92_bullet";
      MCH_ItemLightWeaponBullet item = new MCH_ItemLightWeaponBullet(MCH_Config.ItemID_StingerMissile.prmInt);
      itemStingerBullet = item;
      registerItem(item, name, creativeTabs);
      W_LanguageRegistry.addName(item, "FIM-92 Stinger missile");
      name = "fgm148_bullet";
      item = new MCH_ItemLightWeaponBullet(MCH_Config.ItemID_StingerMissile.prmInt);
      itemJavelinBullet = item;
      registerItem(item, name, creativeTabs);
      W_LanguageRegistry.addName(item, "FGM-148 Javelin missile");
   }

   public void registerItemChain() {
      String name = "chain";
      MCH_ItemChain item = new MCH_ItemChain(MCH_Config.ItemID_Chain.prmInt);
      itemChain = item;
      registerItem(item, "chain", creativeTabs);
      W_LanguageRegistry.addName(item, "Chain");
      W_LanguageRegistry.addNameForObject(item, "ja_JP", "鎖");
   }

   public void registerItemFuel() {
      String name = "fuel";
      MCH_ItemFuel item = new MCH_ItemFuel(MCH_Config.ItemID_Fuel.prmInt);
      itemFuel = item;
      registerItem(item, "fuel", creativeTabs);
      W_LanguageRegistry.addName(item, "Fuel");
      W_LanguageRegistry.addNameForObject(item, "ja_JP", "燃料");
   }

   public void registerItemGLTD() {
      String name = "gltd";
      MCH_ItemGLTD item = new MCH_ItemGLTD(MCH_Config.ItemID_GLTD.prmInt);
      itemGLTD = item;
      registerItem(item, "gltd", creativeTabs);
      W_LanguageRegistry.addName(item, "GLTD:Target Designator");
      W_LanguageRegistry.addNameForObject(item, "ja_JP", "GLTD:レーザー目標指示装置");
   }

   public static void registerItem(W_Item item, String name, MCH_CreativeTabs ct) {
      item.setUnlocalizedName("mcheli:" + name);
      item.setTexture(name);
      if (ct != null) {
         item.setCreativeTab(ct);
         ct.addIconItem(item);
      }

      GameRegistry.registerItem(item, name);
   }

   public static void registerItemThrowable() {
      for (String name : MCH_ThrowableInfoManager.getKeySet()) {
         MCH_ThrowableInfo info = MCH_ThrowableInfoManager.get(name);
         info.item = new MCH_ItemThrowable(info.itemID);
         info.item.setMaxStackSize(info.stackSize);
         registerItem(info.item, name, creativeTabs);
         MCH_ItemThrowable.registerDispenseBehavior(info.item);
         info.itemID = W_Item.getIdFromItem(info.item) - 256;
         W_LanguageRegistry.addName(info.item, info.displayName);

         for (String lang : info.displayNameLang.keySet()) {
            W_LanguageRegistry.addNameForObject(info.item, lang, info.displayNameLang.get(lang));
         }
      }
   }

   public static void registerItemAircraft() {
      for (String name : MCH_HeliInfoManager.map.keySet()) {
         MCH_HeliInfo info = MCH_HeliInfoManager.map.get(name);
         info.item = new MCH_ItemHeli(info.itemID);
         info.item.setMaxDamage(info.maxHp);
         if (info.canRide || !(info.ammoSupplyRange > 0.0F) && !(info.fuelSupplyRange > 0.0F)) {
            registerItem(info.item, name, creativeTabsHeli);
         } else {
            registerItem(info.item, name, creativeTabs);
         }

         MCH_ItemAircraft.registerDispenseBehavior(info.item);
         info.itemID = W_Item.getIdFromItem(info.item) - 256;
         W_LanguageRegistry.addName(info.item, info.displayName);

         for (String lang : info.displayNameLang.keySet()) {
            W_LanguageRegistry.addNameForObject(info.item, lang, info.displayNameLang.get(lang));
         }
      }

      for (String name : MCP_PlaneInfoManager.map.keySet()) {
         MCP_PlaneInfo info = MCP_PlaneInfoManager.map.get(name);
         info.item = new MCP_ItemPlane(info.itemID);
         info.item.setMaxDamage(info.maxHp);
         if (info.canRide || !(info.ammoSupplyRange > 0.0F) && !(info.fuelSupplyRange > 0.0F)) {
            registerItem(info.item, name, creativeTabsPlane);
         } else {
            registerItem(info.item, name, creativeTabs);
         }

         MCH_ItemAircraft.registerDispenseBehavior(info.item);
         info.itemID = W_Item.getIdFromItem(info.item) - 256;
         W_LanguageRegistry.addName(info.item, info.displayName);

         for (String lang : info.displayNameLang.keySet()) {
            W_LanguageRegistry.addNameForObject(info.item, lang, info.displayNameLang.get(lang));
         }
      }

      for (String name : MCH_TankInfoManager.map.keySet()) {
         MCH_TankInfo info = MCH_TankInfoManager.map.get(name);
         info.item = new MCH_ItemTank(info.itemID);
         info.item.setMaxDamage(info.maxHp);
         if (info.canRide || !(info.ammoSupplyRange > 0.0F) && !(info.fuelSupplyRange > 0.0F)) {
            registerItem(info.item, name, creativeTabsTank);
         } else {
            registerItem(info.item, name, creativeTabs);
         }

         MCH_ItemAircraft.registerDispenseBehavior(info.item);
         info.itemID = W_Item.getIdFromItem(info.item) - 256;
         W_LanguageRegistry.addName(info.item, info.displayName);

         for (String lang : info.displayNameLang.keySet()) {
            W_LanguageRegistry.addNameForObject(info.item, lang, info.displayNameLang.get(lang));
         }
      }

      for (String name : MCH_VehicleInfoManager.map.keySet()) {
         MCH_VehicleInfo info = MCH_VehicleInfoManager.map.get(name);
         info.item = new MCH_ItemVehicle(info.itemID);
         info.item.setMaxDamage(info.maxHp);
         if (info.canRide || !(info.ammoSupplyRange > 0.0F) && !(info.fuelSupplyRange > 0.0F)) {
            registerItem(info.item, name, creativeTabsVehicle);
         } else {
            registerItem(info.item, name, creativeTabs);
         }

         MCH_ItemAircraft.registerDispenseBehavior(info.item);
         info.itemID = W_Item.getIdFromItem(info.item) - 256;
         W_LanguageRegistry.addName(info.item, info.displayName);

         for (String lang : info.displayNameLang.keySet()) {
            W_LanguageRegistry.addNameForObject(info.item, lang, info.displayNameLang.get(lang));
         }
      }
   }
}
