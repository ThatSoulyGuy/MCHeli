/*
 * Decompiled with CFR 0.152.
 */
package mcheli;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.IGuiHandler;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import java.io.File;
import mcheli.MCH_Achievement;
import mcheli.MCH_CommonProxy;
import mcheli.MCH_Config;
import mcheli.MCH_CreativeTabs;
import mcheli.MCH_EventHook;
import mcheli.MCH_InvisibleItem;
import mcheli.MCH_ItemRecipe;
import mcheli.MCH_Lib;
import mcheli.MCH_PacketHandler;
import mcheli.MCH_SoundsJson;
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
import mcheli.wrapper.W_PacketHandler;
import net.minecraft.block.Block;
import net.minecraft.command.CommandHandler;
import net.minecraft.command.ICommand;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraftforge.common.MinecraftForge;

/*
 * Exception performing whole class analysis ignored.
 */
@Mod(modid="mcheli", name="mcheli", dependencies="required-after:Forge@[10.13.2.1230,)")
@NetworkMod(clientSideRequired=true, serverSideRequired=false)
public class MCH_MOD {
    public static final String MOD_ID = "mcheli";
    public static final String DOMAIN = "mcheli";
    public static final String MCVER = "1.7.10";
    public static String VER = "";
    public static final String MOD_CH = "MCHeli_CH";
    @Mod.Instance(value="mcheli")
    public static MCH_MOD instance;
    @SidedProxy(clientSide="mcheli.MCH_ClientProxy", serverSide="mcheli.MCH_CommonProxy")
    public static MCH_CommonProxy proxy;
    public static MCH_PacketHandler packetHandler;
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

    @Mod.EventHandler
    public void PreInit(FMLPreInitializationEvent evt) {
        VER = Loader.instance().activeModContainer().getVersion();
        MCH_Lib.init();
        MCH_Lib.Log((String)("MC Ver:1.7.10 MOD Ver:" + VER + ""), (Object[])new Object[0]);
        MCH_Lib.Log((String)"Start load...", (Object[])new Object[0]);
        sourcePath = Loader.instance().activeModContainer().getSource().getPath();
        MCH_Lib.Log((String)("SourcePath: " + sourcePath), (Object[])new Object[0]);
        MCH_Lib.Log((String)("CurrentDirectory:" + new File(".").getAbsolutePath()), (Object[])new Object[0]);
        proxy.init();
        creativeTabs = new MCH_CreativeTabs("MC Heli Item");
        creativeTabsHeli = new MCH_CreativeTabs("MC Heli Helicopters");
        creativeTabsPlane = new MCH_CreativeTabs("MC Heli Planes");
        creativeTabsTank = new MCH_CreativeTabs("MC Heli Tanks");
        creativeTabsVehicle = new MCH_CreativeTabs("MC Heli Vehicles");
        W_ItemList.init();
        config = proxy.loadConfig("config/mcheli.cfg");
        proxy.loadHUD(sourcePath + "/assets/" + "mcheli" + "/hud");
        MCH_WeaponInfoManager.load((String)(sourcePath + "/assets/" + "mcheli" + "/weapons"));
        MCH_HeliInfoManager.getInstance().load(sourcePath + "/assets/" + "mcheli" + "/", "helicopters");
        MCP_PlaneInfoManager.getInstance().load(sourcePath + "/assets/" + "mcheli" + "/", "planes");
        MCH_TankInfoManager.getInstance().load(sourcePath + "/assets/" + "mcheli" + "/", "tanks");
        MCH_VehicleInfoManager.getInstance().load(sourcePath + "/assets/" + "mcheli" + "/", "vehicles");
        MCH_ThrowableInfoManager.load((String)(sourcePath + "/assets/" + "mcheli" + "/throwable"));
        MCH_SoundsJson.update((String)(sourcePath + "/assets/" + "mcheli" + "/"));
        MCH_Lib.Log((String)"Register item", (Object[])new Object[0]);
        this.registerItemRangeFinder();
        this.registerItemWrench();
        this.registerItemFuel();
        this.registerItemGLTD();
        this.registerItemChain();
        this.registerItemParachute();
        this.registerItemContainer();
        this.registerItemUavStation();
        this.registerItemInvisible();
        MCH_MOD.registerItemThrowable();
        this.registerItemLightWeaponBullet();
        this.registerItemLightWeapon();
        MCH_MOD.registerItemAircraft();
        blockDraftingTable = new MCH_DraftingTableBlock(MCH_Config.BlockID_DraftingTableOFF.prmInt, false);
        blockDraftingTable.func_149663_c("drafting_table");
        blockDraftingTable.func_149647_a((CreativeTabs)creativeTabs);
        blockDraftingTableLit = new MCH_DraftingTableBlock(MCH_Config.BlockID_DraftingTableON.prmInt, true);
        blockDraftingTableLit.func_149663_c("lit_drafting_table");
        GameRegistry.registerBlock((Block)blockDraftingTable, (String)"drafting_table");
        GameRegistry.registerBlock((Block)blockDraftingTableLit, (String)"lit_drafting_table");
        W_LanguageRegistry.addName((Object)blockDraftingTable, (String)"Drafting Table");
        W_LanguageRegistry.addNameForObject((Object)blockDraftingTable, (String)"ja_JP", (String)"\u88fd\u56f3\u53f0");
        MCH_Achievement.PreInit();
        MCH_Lib.Log((String)"Register system", (Object[])new Object[0]);
        W_NetworkRegistry.registerChannel((W_PacketHandler)packetHandler, (String)"MCHeli_CH");
        MinecraftForge.EVENT_BUS.register((Object)new MCH_EventHook());
        proxy.registerClientTick();
        W_NetworkRegistry.registerGuiHandler((Object)this, (IGuiHandler)new MCH_GuiCommonHandler());
        MCH_Lib.Log((String)"Register entity", (Object[])new Object[0]);
        this.registerEntity();
        MCH_Lib.Log((String)"Register renderer", (Object[])new Object[0]);
        proxy.registerRenderer();
        MCH_Lib.Log((String)"Register models", (Object[])new Object[0]);
        proxy.registerModels();
        MCH_Lib.Log((String)"Register Sounds", (Object[])new Object[0]);
        proxy.registerSounds();
        W_LanguageRegistry.updateLang((String)(sourcePath + "/assets/" + "mcheli" + "/lang/"));
        MCH_Lib.Log((String)"End load", (Object[])new Object[0]);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent evt) {
        GameRegistry.registerTileEntity(MCH_DraftingTableTileEntity.class, (String)"drafting_table");
        proxy.registerBlockRenderer();
    }

    @Mod.EventHandler
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

    @Mod.EventHandler
    public void onStartServer(FMLServerStartingEvent event) {
        proxy.registerServerTick();
    }

    public void registerEntity() {
        EntityRegistry.registerModEntity(MCH_EntitySeat.class, (String)"MCH.E.Seat", (int)100, (Object)this, (int)200, (int)10, (boolean)true);
        EntityRegistry.registerModEntity(MCH_EntityHeli.class, (String)"MCH.E.Heli", (int)101, (Object)this, (int)200, (int)10, (boolean)true);
        EntityRegistry.registerModEntity(MCH_EntityGLTD.class, (String)"MCH.E.GLTD", (int)102, (Object)this, (int)200, (int)10, (boolean)true);
        EntityRegistry.registerModEntity(MCP_EntityPlane.class, (String)"MCH.E.Plane", (int)103, (Object)this, (int)200, (int)10, (boolean)true);
        EntityRegistry.registerModEntity(MCH_EntityChain.class, (String)"MCH.E.Chain", (int)104, (Object)this, (int)200, (int)10, (boolean)true);
        EntityRegistry.registerModEntity(MCH_EntityHitBox.class, (String)"MCH.E.PSeat", (int)105, (Object)this, (int)200, (int)10, (boolean)true);
        EntityRegistry.registerModEntity(MCH_EntityParachute.class, (String)"MCH.E.Parachute", (int)106, (Object)this, (int)200, (int)10, (boolean)true);
        EntityRegistry.registerModEntity(MCH_EntityContainer.class, (String)"MCH.E.Container", (int)107, (Object)this, (int)200, (int)10, (boolean)true);
        EntityRegistry.registerModEntity(MCH_EntityVehicle.class, (String)"MCH.E.Vehicle", (int)108, (Object)this, (int)200, (int)10, (boolean)true);
        EntityRegistry.registerModEntity(MCH_EntityUavStation.class, (String)"MCH.E.UavStation", (int)109, (Object)this, (int)200, (int)10, (boolean)true);
        EntityRegistry.registerModEntity(MCH_EntityHitBox.class, (String)"MCH.E.HitBox", (int)110, (Object)this, (int)200, (int)10, (boolean)true);
        EntityRegistry.registerModEntity(MCH_EntityHide.class, (String)"MCH.E.Hide", (int)111, (Object)this, (int)200, (int)10, (boolean)true);
        EntityRegistry.registerModEntity(MCH_EntityTank.class, (String)"MCH.E.Tank", (int)112, (Object)this, (int)200, (int)10, (boolean)true);
        EntityRegistry.registerModEntity(MCH_EntityRocket.class, (String)"MCH.E.Rocket", (int)200, (Object)this, (int)530, (int)5, (boolean)true);
        EntityRegistry.registerModEntity(MCH_EntityTvMissile.class, (String)"MCH.E.TvMissle", (int)201, (Object)this, (int)530, (int)5, (boolean)true);
        EntityRegistry.registerModEntity(MCH_EntityBullet.class, (String)"MCH.E.Bullet", (int)202, (Object)this, (int)530, (int)5, (boolean)true);
        EntityRegistry.registerModEntity(MCH_EntityA10.class, (String)"MCH.E.A10", (int)203, (Object)this, (int)530, (int)5, (boolean)true);
        EntityRegistry.registerModEntity(MCH_EntityAAMissile.class, (String)"MCH.E.AAM", (int)204, (Object)this, (int)530, (int)5, (boolean)true);
        EntityRegistry.registerModEntity(MCH_EntityASMissile.class, (String)"MCH.E.ASM", (int)205, (Object)this, (int)530, (int)5, (boolean)true);
        EntityRegistry.registerModEntity(MCH_EntityTorpedo.class, (String)"MCH.E.Torpedo", (int)206, (Object)this, (int)530, (int)5, (boolean)true);
        EntityRegistry.registerModEntity(MCH_EntityATMissile.class, (String)"MCH.E.ATMissle", (int)207, (Object)this, (int)530, (int)5, (boolean)true);
        EntityRegistry.registerModEntity(MCH_EntityBomb.class, (String)"MCH.E.Bomb", (int)208, (Object)this, (int)530, (int)5, (boolean)true);
        EntityRegistry.registerModEntity(MCH_EntityMarkerRocket.class, (String)"MCH.E.MkRocket", (int)209, (Object)this, (int)530, (int)5, (boolean)true);
        EntityRegistry.registerModEntity(MCH_EntityDispensedItem.class, (String)"MCH.E.DispItem", (int)210, (Object)this, (int)530, (int)5, (boolean)true);
        EntityRegistry.registerModEntity(MCH_EntityFlare.class, (String)"MCH.E.Flare", (int)300, (Object)this, (int)330, (int)10, (boolean)true);
        EntityRegistry.registerModEntity(MCH_EntityThrowable.class, (String)"MCH.E.Throwable", (int)400, (Object)this, (int)330, (int)10, (boolean)true);
    }

    @Mod.EventHandler
    public void registerCommand(FMLServerStartedEvent e) {
        CommandHandler handler = (CommandHandler)FMLCommonHandler.instance().getSidedDelegate().getServer().func_71187_D();
        handler.func_71560_a((ICommand)new MCH_Command());
    }

    private void registerItemRangeFinder() {
        MCH_ItemRangeFinder item;
        String name = "rangefinder";
        itemRangeFinder = item = new MCH_ItemRangeFinder(MCH_Config.ItemID_RangeFinder.prmInt);
        MCH_MOD.registerItem((W_Item)item, (String)"rangefinder", (MCH_CreativeTabs)creativeTabs);
        W_LanguageRegistry.addName((Object)item, (String)"Laser Rangefinder");
        W_LanguageRegistry.addNameForObject((Object)item, (String)"ja_JP", (String)"\u30ec\u30fc\u30b6\u30fc \u30ec\u30f3\u30b8 \u30d5\u30a1\u30a4\u30f3\u30c0\u30fc");
    }

    private void registerItemWrench() {
        MCH_ItemWrench item;
        String name = "wrench";
        itemWrench = item = new MCH_ItemWrench(MCH_Config.ItemID_Wrench.prmInt, Item.ToolMaterial.IRON);
        MCH_MOD.registerItem((W_Item)item, (String)"wrench", (MCH_CreativeTabs)creativeTabs);
        W_LanguageRegistry.addName((Object)item, (String)"Wrench");
        W_LanguageRegistry.addNameForObject((Object)item, (String)"ja_JP", (String)"\u30ec\u30f3\u30c1");
    }

    public void registerItemInvisible() {
        MCH_InvisibleItem item;
        String name = "internal";
        invisibleItem = item = new MCH_InvisibleItem(MCH_Config.ItemID_InvisibleItem.prmInt);
        MCH_MOD.registerItem((W_Item)item, (String)"internal", null);
    }

    public void registerItemUavStation() {
        String[] dispName = new String[]{"UAV Station", "Portable UAV Controller"};
        String[] localName = new String[]{"UAV\u30b9\u30c6\u30fc\u30b7\u30e7\u30f3", "\u643a\u5e2fUAV\u5236\u5fa1\u7aef\u672b"};
        itemUavStation = new MCH_ItemUavStation[MCH_ItemUavStation.UAV_STATION_KIND_NUM];
        String name = "uav_station";
        for (int i = 0; i < itemUavStation.length; ++i) {
            MCH_ItemUavStation item;
            String nn = i > 0 ? "" + (i + 1) : "";
            MCH_MOD.itemUavStation[i] = item = new MCH_ItemUavStation(MCH_Config.ItemID_UavStation[i].prmInt, 1 + i);
            MCH_MOD.registerItem((W_Item)item, (String)("uav_station" + nn), (MCH_CreativeTabs)creativeTabs);
            W_LanguageRegistry.addName((Object)item, (String)dispName[i]);
            W_LanguageRegistry.addNameForObject((Object)item, (String)"ja_JP", (String)localName[i]);
        }
    }

    public void registerItemParachute() {
        MCH_ItemParachute item;
        String name = "parachute";
        itemParachute = item = new MCH_ItemParachute(MCH_Config.ItemID_Parachute.prmInt);
        MCH_MOD.registerItem((W_Item)item, (String)"parachute", (MCH_CreativeTabs)creativeTabs);
        W_LanguageRegistry.addName((Object)item, (String)"Parachute");
        W_LanguageRegistry.addNameForObject((Object)item, (String)"ja_JP", (String)"\u30d1\u30e9\u30b7\u30e5\u30fc\u30c8");
    }

    public void registerItemContainer() {
        MCH_ItemContainer item;
        String name = "container";
        itemContainer = item = new MCH_ItemContainer(MCH_Config.ItemID_Container.prmInt);
        MCH_MOD.registerItem((W_Item)item, (String)"container", (MCH_CreativeTabs)creativeTabs);
        W_LanguageRegistry.addName((Object)item, (String)"Container");
        W_LanguageRegistry.addNameForObject((Object)item, (String)"ja_JP", (String)"\u30b3\u30f3\u30c6\u30ca");
    }

    public void registerItemLightWeapon() {
        MCH_ItemLightWeaponBase item;
        String name = "fim92";
        itemStinger = item = new MCH_ItemLightWeaponBase(MCH_Config.ItemID_Stinger.prmInt, itemStingerBullet);
        MCH_MOD.registerItem((W_Item)item, (String)name, (MCH_CreativeTabs)creativeTabs);
        W_LanguageRegistry.addName((Object)item, (String)"FIM-92 Stinger");
        name = "fgm148";
        itemJavelin = item = new MCH_ItemLightWeaponBase(MCH_Config.ItemID_Stinger.prmInt, itemJavelinBullet);
        MCH_MOD.registerItem((W_Item)item, (String)name, (MCH_CreativeTabs)creativeTabs);
        W_LanguageRegistry.addName((Object)item, (String)"FGM-148 Javelin");
    }

    public void registerItemLightWeaponBullet() {
        MCH_ItemLightWeaponBullet item;
        String name = "fim92_bullet";
        itemStingerBullet = item = new MCH_ItemLightWeaponBullet(MCH_Config.ItemID_StingerMissile.prmInt);
        MCH_MOD.registerItem((W_Item)item, (String)name, (MCH_CreativeTabs)creativeTabs);
        W_LanguageRegistry.addName((Object)item, (String)"FIM-92 Stinger missile");
        name = "fgm148_bullet";
        itemJavelinBullet = item = new MCH_ItemLightWeaponBullet(MCH_Config.ItemID_StingerMissile.prmInt);
        MCH_MOD.registerItem((W_Item)item, (String)name, (MCH_CreativeTabs)creativeTabs);
        W_LanguageRegistry.addName((Object)item, (String)"FGM-148 Javelin missile");
    }

    public void registerItemChain() {
        MCH_ItemChain item;
        String name = "chain";
        itemChain = item = new MCH_ItemChain(MCH_Config.ItemID_Chain.prmInt);
        MCH_MOD.registerItem((W_Item)item, (String)"chain", (MCH_CreativeTabs)creativeTabs);
        W_LanguageRegistry.addName((Object)item, (String)"Chain");
        W_LanguageRegistry.addNameForObject((Object)item, (String)"ja_JP", (String)"\u9396");
    }

    public void registerItemFuel() {
        MCH_ItemFuel item;
        String name = "fuel";
        itemFuel = item = new MCH_ItemFuel(MCH_Config.ItemID_Fuel.prmInt);
        MCH_MOD.registerItem((W_Item)item, (String)"fuel", (MCH_CreativeTabs)creativeTabs);
        W_LanguageRegistry.addName((Object)item, (String)"Fuel");
        W_LanguageRegistry.addNameForObject((Object)item, (String)"ja_JP", (String)"\u71c3\u6599");
    }

    public void registerItemGLTD() {
        MCH_ItemGLTD item;
        String name = "gltd";
        itemGLTD = item = new MCH_ItemGLTD(MCH_Config.ItemID_GLTD.prmInt);
        MCH_MOD.registerItem((W_Item)item, (String)"gltd", (MCH_CreativeTabs)creativeTabs);
        W_LanguageRegistry.addName((Object)item, (String)"GLTD:Target Designator");
        W_LanguageRegistry.addNameForObject((Object)item, (String)"ja_JP", (String)"GLTD:\u30ec\u30fc\u30b6\u30fc\u76ee\u6a19\u6307\u793a\u88c5\u7f6e");
    }

    public static void registerItem(W_Item item, String name, MCH_CreativeTabs ct) {
        item.func_77655_b("mcheli:" + name);
        item.setTexture(name);
        if (ct != null) {
            item.func_77637_a((CreativeTabs)ct);
            ct.addIconItem((Item)item);
        }
        GameRegistry.registerItem((Item)item, (String)name);
    }

    public static void registerItemThrowable() {
        for (String name : MCH_ThrowableInfoManager.getKeySet()) {
            MCH_ThrowableInfo info = MCH_ThrowableInfoManager.get((String)name);
            info.item = new MCH_ItemThrowable(info.itemID);
            info.item.func_77625_d(info.stackSize);
            MCH_MOD.registerItem((W_Item)info.item, (String)name, (MCH_CreativeTabs)creativeTabs);
            MCH_ItemThrowable.registerDispenseBehavior((Item)info.item);
            info.itemID = W_Item.getIdFromItem((Item)info.item) - 256;
            W_LanguageRegistry.addName((Object)info.item, (String)info.displayName);
            for (String lang : info.displayNameLang.keySet()) {
                W_LanguageRegistry.addNameForObject((Object)info.item, (String)lang, (String)((String)info.displayNameLang.get(lang)));
            }
        }
    }

    public static void registerItemAircraft() {
        MCH_HeliInfo info;
        for (String name : MCH_HeliInfoManager.map.keySet()) {
            info = (MCH_HeliInfo)MCH_HeliInfoManager.map.get(name);
            info.item = new MCH_ItemHeli(info.itemID);
            info.item.func_77656_e(info.maxHp);
            if (!info.canRide && (info.ammoSupplyRange > 0.0f || info.fuelSupplyRange > 0.0f)) {
                MCH_MOD.registerItem((W_Item)info.item, (String)name, (MCH_CreativeTabs)creativeTabs);
            } else {
                MCH_MOD.registerItem((W_Item)info.item, (String)name, (MCH_CreativeTabs)creativeTabsHeli);
            }
            MCH_ItemAircraft.registerDispenseBehavior((Item)info.item);
            info.itemID = W_Item.getIdFromItem((Item)info.item) - 256;
            W_LanguageRegistry.addName((Object)info.item, (String)info.displayName);
            for (String lang : info.displayNameLang.keySet()) {
                W_LanguageRegistry.addNameForObject((Object)info.item, (String)lang, (String)((String)info.displayNameLang.get(lang)));
            }
        }
        for (String name : MCP_PlaneInfoManager.map.keySet()) {
            info = (MCP_PlaneInfo)MCP_PlaneInfoManager.map.get(name);
            info.item = new MCP_ItemPlane(info.itemID);
            info.item.func_77656_e(info.maxHp);
            if (!info.canRide && (info.ammoSupplyRange > 0.0f || info.fuelSupplyRange > 0.0f)) {
                MCH_MOD.registerItem((W_Item)info.item, (String)name, (MCH_CreativeTabs)creativeTabs);
            } else {
                MCH_MOD.registerItem((W_Item)info.item, (String)name, (MCH_CreativeTabs)creativeTabsPlane);
            }
            MCH_ItemAircraft.registerDispenseBehavior((Item)info.item);
            info.itemID = W_Item.getIdFromItem((Item)info.item) - 256;
            W_LanguageRegistry.addName((Object)info.item, (String)info.displayName);
            for (String lang : info.displayNameLang.keySet()) {
                W_LanguageRegistry.addNameForObject((Object)info.item, (String)lang, (String)((String)info.displayNameLang.get(lang)));
            }
        }
        for (String name : MCH_TankInfoManager.map.keySet()) {
            info = (MCH_TankInfo)MCH_TankInfoManager.map.get(name);
            info.item = new MCH_ItemTank(info.itemID);
            info.item.func_77656_e(info.maxHp);
            if (!info.canRide && (info.ammoSupplyRange > 0.0f || info.fuelSupplyRange > 0.0f)) {
                MCH_MOD.registerItem((W_Item)info.item, (String)name, (MCH_CreativeTabs)creativeTabs);
            } else {
                MCH_MOD.registerItem((W_Item)info.item, (String)name, (MCH_CreativeTabs)creativeTabsTank);
            }
            MCH_ItemAircraft.registerDispenseBehavior((Item)info.item);
            info.itemID = W_Item.getIdFromItem((Item)info.item) - 256;
            W_LanguageRegistry.addName((Object)info.item, (String)info.displayName);
            for (String lang : info.displayNameLang.keySet()) {
                W_LanguageRegistry.addNameForObject((Object)info.item, (String)lang, (String)((String)info.displayNameLang.get(lang)));
            }
        }
        for (String name : MCH_VehicleInfoManager.map.keySet()) {
            info = (MCH_VehicleInfo)MCH_VehicleInfoManager.map.get(name);
            info.item = new MCH_ItemVehicle(info.itemID);
            info.item.func_77656_e(info.maxHp);
            if (!info.canRide && (info.ammoSupplyRange > 0.0f || info.fuelSupplyRange > 0.0f)) {
                MCH_MOD.registerItem((W_Item)info.item, (String)name, (MCH_CreativeTabs)creativeTabs);
            } else {
                MCH_MOD.registerItem((W_Item)info.item, (String)name, (MCH_CreativeTabs)creativeTabsVehicle);
            }
            MCH_ItemAircraft.registerDispenseBehavior((Item)info.item);
            info.itemID = W_Item.getIdFromItem((Item)info.item) - 256;
            W_LanguageRegistry.addName((Object)info.item, (String)info.displayName);
            for (String lang : info.displayNameLang.keySet()) {
                W_LanguageRegistry.addNameForObject((Object)info.item, (String)lang, (String)((String)info.displayNameLang.get(lang)));
            }
        }
    }

    static {
        packetHandler = new MCH_PacketHandler();
    }
}

