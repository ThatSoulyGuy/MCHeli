/*
 * Decompiled with CFR 0.152.
 */
package mcheli;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import mcheli.MCH_ClientCommonTickHandler;
import mcheli.MCH_ClientEventHook;
import mcheli.MCH_CommonProxy;
import mcheli.MCH_Config;
import mcheli.MCH_InvisibleItemRender;
import mcheli.MCH_Lib;
import mcheli.MCH_MOD;
import mcheli.MCH_ModelManager;
import mcheli.MCH_RenderNull;
import mcheli.MCH_ViewEntityDummy;
import mcheli.aircraft.MCH_AircraftInfo;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_EntityHide;
import mcheli.aircraft.MCH_EntitySeat;
import mcheli.aircraft.MCH_RenderAircraft;
import mcheli.aircraft.MCH_SoundUpdater;
import mcheli.block.MCH_DraftingTableItemRender;
import mcheli.block.MCH_DraftingTableRenderer;
import mcheli.block.MCH_DraftingTableTileEntity;
import mcheli.chain.MCH_EntityChain;
import mcheli.chain.MCH_RenderChain;
import mcheli.command.MCH_GuiTitle;
import mcheli.container.MCH_EntityContainer;
import mcheli.container.MCH_RenderContainer;
import mcheli.debug.MCH_RenderTest;
import mcheli.flare.MCH_EntityFlare;
import mcheli.flare.MCH_RenderFlare;
import mcheli.gltd.MCH_EntityGLTD;
import mcheli.gltd.MCH_ItemGLTDRender;
import mcheli.gltd.MCH_RenderGLTD;
import mcheli.helicopter.MCH_EntityHeli;
import mcheli.helicopter.MCH_HeliInfo;
import mcheli.helicopter.MCH_HeliInfoManager;
import mcheli.helicopter.MCH_RenderHeli;
import mcheli.hud.MCH_HudManager;
import mcheli.lweapon.MCH_ItemLightWeaponRender;
import mcheli.multiplay.MCH_MultiplayClient;
import mcheli.parachute.MCH_EntityParachute;
import mcheli.parachute.MCH_RenderParachute;
import mcheli.particles.MCH_ParticlesUtil;
import mcheli.plane.MCP_EntityPlane;
import mcheli.plane.MCP_PlaneInfo;
import mcheli.plane.MCP_PlaneInfoManager;
import mcheli.plane.MCP_RenderPlane;
import mcheli.tank.MCH_EntityTank;
import mcheli.tank.MCH_RenderTank;
import mcheli.tank.MCH_TankInfo;
import mcheli.tank.MCH_TankInfoManager;
import mcheli.throwable.MCH_EntityThrowable;
import mcheli.throwable.MCH_RenderThrowable;
import mcheli.throwable.MCH_ThrowableInfo;
import mcheli.throwable.MCH_ThrowableInfoManager;
import mcheli.tool.MCH_ItemRenderWrench;
import mcheli.tool.rangefinder.MCH_ItemRenderRangeFinder;
import mcheli.uav.MCH_EntityUavStation;
import mcheli.uav.MCH_RenderUavStation;
import mcheli.vehicle.MCH_EntityVehicle;
import mcheli.vehicle.MCH_RenderVehicle;
import mcheli.vehicle.MCH_VehicleInfo;
import mcheli.vehicle.MCH_VehicleInfoManager;
import mcheli.weapon.MCH_BulletModel;
import mcheli.weapon.MCH_DefaultBulletModels;
import mcheli.weapon.MCH_EntityA10;
import mcheli.weapon.MCH_EntityAAMissile;
import mcheli.weapon.MCH_EntityASMissile;
import mcheli.weapon.MCH_EntityATMissile;
import mcheli.weapon.MCH_EntityBomb;
import mcheli.weapon.MCH_EntityBullet;
import mcheli.weapon.MCH_EntityCartridge;
import mcheli.weapon.MCH_EntityDispensedItem;
import mcheli.weapon.MCH_EntityMarkerRocket;
import mcheli.weapon.MCH_EntityRocket;
import mcheli.weapon.MCH_EntityTorpedo;
import mcheli.weapon.MCH_EntityTvMissile;
import mcheli.weapon.MCH_RenderA10;
import mcheli.weapon.MCH_RenderAAMissile;
import mcheli.weapon.MCH_RenderASMissile;
import mcheli.weapon.MCH_RenderBomb;
import mcheli.weapon.MCH_RenderBullet;
import mcheli.weapon.MCH_RenderCartridge;
import mcheli.weapon.MCH_RenderNone;
import mcheli.weapon.MCH_RenderTvMissile;
import mcheli.weapon.MCH_WeaponInfo;
import mcheli.weapon.MCH_WeaponInfoManager;
import mcheli.wrapper.W_Item;
import mcheli.wrapper.W_McClient;
import mcheli.wrapper.W_MinecraftForgeClient;
import mcheli.wrapper.W_Reflection;
import mcheli.wrapper.W_TickHandler;
import mcheli.wrapper.W_TickRegistry;
import mcheli.wrapper.modelloader.W_ModelCustom;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.model.IModelCustom;
import net.minecraftforge.common.MinecraftForge;

/*
 * Exception performing whole class analysis ignored.
 */
public class MCH_ClientProxy
extends MCH_CommonProxy {
    public String lastLoadHUDPath = "";

    public String getDataDir() {
        return Minecraft.func_71410_x().field_71412_D.getPath();
    }

    public void registerRenderer() {
        RenderingRegistry.registerEntityRenderingHandler(MCH_EntitySeat.class, (Render)new MCH_RenderTest(0.0f, 0.0f, 0.0f, "seat"));
        RenderingRegistry.registerEntityRenderingHandler(MCH_EntityHeli.class, (Render)new MCH_RenderHeli());
        RenderingRegistry.registerEntityRenderingHandler(MCP_EntityPlane.class, (Render)new MCP_RenderPlane());
        RenderingRegistry.registerEntityRenderingHandler(MCH_EntityTank.class, (Render)new MCH_RenderTank());
        RenderingRegistry.registerEntityRenderingHandler(MCH_EntityGLTD.class, (Render)new MCH_RenderGLTD());
        RenderingRegistry.registerEntityRenderingHandler(MCH_EntityChain.class, (Render)new MCH_RenderChain());
        RenderingRegistry.registerEntityRenderingHandler(MCH_EntityParachute.class, (Render)new MCH_RenderParachute());
        RenderingRegistry.registerEntityRenderingHandler(MCH_EntityContainer.class, (Render)new MCH_RenderContainer());
        RenderingRegistry.registerEntityRenderingHandler(MCH_EntityVehicle.class, (Render)new MCH_RenderVehicle());
        RenderingRegistry.registerEntityRenderingHandler(MCH_EntityUavStation.class, (Render)new MCH_RenderUavStation());
        RenderingRegistry.registerEntityRenderingHandler(MCH_EntityCartridge.class, (Render)new MCH_RenderCartridge());
        RenderingRegistry.registerEntityRenderingHandler(MCH_EntityHide.class, (Render)new MCH_RenderNull());
        RenderingRegistry.registerEntityRenderingHandler(MCH_ViewEntityDummy.class, (Render)new MCH_RenderNull());
        RenderingRegistry.registerEntityRenderingHandler(MCH_EntityRocket.class, (Render)new MCH_RenderBullet());
        RenderingRegistry.registerEntityRenderingHandler(MCH_EntityTvMissile.class, (Render)new MCH_RenderTvMissile());
        RenderingRegistry.registerEntityRenderingHandler(MCH_EntityBullet.class, (Render)new MCH_RenderBullet());
        RenderingRegistry.registerEntityRenderingHandler(MCH_EntityA10.class, (Render)new MCH_RenderA10());
        RenderingRegistry.registerEntityRenderingHandler(MCH_EntityAAMissile.class, (Render)new MCH_RenderAAMissile());
        RenderingRegistry.registerEntityRenderingHandler(MCH_EntityASMissile.class, (Render)new MCH_RenderASMissile());
        RenderingRegistry.registerEntityRenderingHandler(MCH_EntityATMissile.class, (Render)new MCH_RenderTvMissile());
        RenderingRegistry.registerEntityRenderingHandler(MCH_EntityTorpedo.class, (Render)new MCH_RenderBullet());
        RenderingRegistry.registerEntityRenderingHandler(MCH_EntityBomb.class, (Render)new MCH_RenderBomb());
        RenderingRegistry.registerEntityRenderingHandler(MCH_EntityMarkerRocket.class, (Render)new MCH_RenderBullet());
        RenderingRegistry.registerEntityRenderingHandler(MCH_EntityDispensedItem.class, (Render)new MCH_RenderNone());
        RenderingRegistry.registerEntityRenderingHandler(MCH_EntityFlare.class, (Render)new MCH_RenderFlare());
        RenderingRegistry.registerEntityRenderingHandler(MCH_EntityThrowable.class, (Render)new MCH_RenderThrowable());
        W_MinecraftForgeClient.registerItemRenderer((Item)MCH_MOD.itemJavelin, (IItemRenderer)new MCH_ItemLightWeaponRender());
        W_MinecraftForgeClient.registerItemRenderer((Item)MCH_MOD.itemStinger, (IItemRenderer)new MCH_ItemLightWeaponRender());
        W_MinecraftForgeClient.registerItemRenderer((Item)MCH_MOD.invisibleItem, (IItemRenderer)new MCH_InvisibleItemRender());
        W_MinecraftForgeClient.registerItemRenderer((Item)MCH_MOD.itemGLTD, (IItemRenderer)new MCH_ItemGLTDRender());
        W_MinecraftForgeClient.registerItemRenderer((Item)MCH_MOD.itemWrench, (IItemRenderer)new MCH_ItemRenderWrench());
        W_MinecraftForgeClient.registerItemRenderer((Item)MCH_MOD.itemRangeFinder, (IItemRenderer)new MCH_ItemRenderRangeFinder());
    }

    public void registerBlockRenderer() {
        ClientRegistry.bindTileEntitySpecialRenderer(MCH_DraftingTableTileEntity.class, (TileEntitySpecialRenderer)new MCH_DraftingTableRenderer());
        W_MinecraftForgeClient.registerItemRenderer((Item)W_Item.getItemFromBlock((Block)MCH_MOD.blockDraftingTable), (IItemRenderer)new MCH_DraftingTableItemRender());
    }

    public void registerModels() {
        MCH_ModelManager.setForceReloadMode((boolean)true);
        MCH_RenderAircraft.debugModel = MCH_ModelManager.load((String)"box");
        MCH_ModelManager.load((String)"a-10");
        MCH_RenderGLTD.model = MCH_ModelManager.load((String)"gltd");
        MCH_ModelManager.load((String)"chain");
        MCH_ModelManager.load((String)"container");
        MCH_ModelManager.load((String)"parachute1");
        MCH_ModelManager.load((String)"parachute2");
        MCH_ModelManager.load((String)"lweapons", (String)"fim92");
        MCH_ModelManager.load((String)"lweapons", (String)"fgm148");
        for (String s : MCH_RenderUavStation.MODEL_NAME) {
            MCH_ModelManager.load((String)s);
        }
        MCH_ModelManager.load((String)"wrench");
        MCH_ModelManager.load((String)"rangefinder");
        MCH_HeliInfoManager.getInstance();
        for (String key : MCH_HeliInfoManager.map.keySet()) {
            this.registerModelsHeli(key, false);
        }
        for (String key : MCP_PlaneInfoManager.map.keySet()) {
            this.registerModelsPlane(key, false);
        }
        MCH_TankInfoManager.getInstance();
        for (String key : MCH_TankInfoManager.map.keySet()) {
            this.registerModelsTank(key, false);
        }
        for (String key : MCH_VehicleInfoManager.map.keySet()) {
            this.registerModelsVehicle(key, false);
        }
        MCH_ClientProxy.registerModels_Bullet();
        MCH_DefaultBulletModels.Bullet = this.loadBulletModel("bullet");
        MCH_DefaultBulletModels.AAMissile = this.loadBulletModel("aamissile");
        MCH_DefaultBulletModels.ATMissile = this.loadBulletModel("asmissile");
        MCH_DefaultBulletModels.ASMissile = this.loadBulletModel("asmissile");
        MCH_DefaultBulletModels.Bomb = this.loadBulletModel("bomb");
        MCH_DefaultBulletModels.Rocket = this.loadBulletModel("rocket");
        MCH_DefaultBulletModels.Torpedo = this.loadBulletModel("torpedo");
        for (MCH_ThrowableInfo wi : MCH_ThrowableInfoManager.getValues()) {
            wi.model = MCH_ModelManager.load((String)"throwable", (String)wi.name);
        }
        MCH_ModelManager.load((String)"blocks", (String)"drafting_table");
    }

    public static void registerModels_Bullet() {
        for (MCH_WeaponInfo wi : MCH_WeaponInfoManager.getValues()) {
            IModelCustom m = null;
            if (!wi.bulletModelName.isEmpty() && (m = MCH_ModelManager.load((String)"bullets", (String)wi.bulletModelName)) != null) {
                wi.bulletModel = new MCH_BulletModel(wi.bulletModelName, m);
            }
            if (!wi.bombletModelName.isEmpty() && (m = MCH_ModelManager.load((String)"bullets", (String)wi.bombletModelName)) != null) {
                wi.bombletModel = new MCH_BulletModel(wi.bombletModelName, m);
            }
            if (wi.cartridge == null || wi.cartridge.name.isEmpty()) continue;
            wi.cartridge.model = MCH_ModelManager.load((String)"bullets", (String)wi.cartridge.name);
            if (wi.cartridge.model != null) continue;
            wi.cartridge = null;
        }
    }

    public void registerModelsHeli(String name, boolean reload) {
        MCH_ModelManager.setForceReloadMode((boolean)reload);
        MCH_HeliInfo info = (MCH_HeliInfo)MCH_HeliInfoManager.map.get(name);
        info.model = MCH_ModelManager.load((String)"helicopters", (String)info.name);
        for (MCH_HeliInfo.Rotor rotor : info.rotorList) {
            rotor.model = this.loadPartModel("helicopters", info.name, info.model, rotor.modelName);
        }
        this.registerCommonPart("helicopters", (MCH_AircraftInfo)info);
        MCH_ModelManager.setForceReloadMode((boolean)false);
    }

    public void registerModelsPlane(String name, boolean reload) {
        MCH_ModelManager.setForceReloadMode((boolean)reload);
        MCP_PlaneInfo info = (MCP_PlaneInfo)MCP_PlaneInfoManager.map.get(name);
        info.model = MCH_ModelManager.load((String)"planes", (String)info.name);
        for (MCH_AircraftInfo.DrawnPart n : info.nozzles) {
            n.model = this.loadPartModel("planes", info.name, info.model, n.modelName);
        }
        for (MCP_PlaneInfo.Rotor r : info.rotorList) {
            r.model = this.loadPartModel("planes", info.name, info.model, r.modelName);
            for (MCP_PlaneInfo.Blade b : r.blades) {
                b.model = this.loadPartModel("planes", info.name, info.model, b.modelName);
            }
        }
        for (MCP_PlaneInfo.Wing w : info.wingList) {
            w.model = this.loadPartModel("planes", info.name, info.model, w.modelName);
            if (w.pylonList == null) continue;
            for (MCP_PlaneInfo.Pylon p : w.pylonList) {
                p.model = this.loadPartModel("planes", info.name, info.model, p.modelName);
            }
        }
        this.registerCommonPart("planes", (MCH_AircraftInfo)info);
        MCH_ModelManager.setForceReloadMode((boolean)false);
    }

    public void registerModelsVehicle(String name, boolean reload) {
        MCH_ModelManager.setForceReloadMode((boolean)reload);
        MCH_VehicleInfo info = (MCH_VehicleInfo)MCH_VehicleInfoManager.map.get(name);
        info.model = MCH_ModelManager.load((String)"vehicles", (String)info.name);
        for (MCH_VehicleInfo.VPart vp : info.partList) {
            vp.model = this.loadPartModel("vehicles", info.name, info.model, vp.modelName);
            if (vp.child == null) continue;
            this.registerVCPModels(info, vp);
        }
        this.registerCommonPart("vehicles", (MCH_AircraftInfo)info);
        MCH_ModelManager.setForceReloadMode((boolean)false);
    }

    public void registerModelsTank(String name, boolean reload) {
        MCH_ModelManager.setForceReloadMode((boolean)reload);
        MCH_TankInfo info = (MCH_TankInfo)MCH_TankInfoManager.map.get(name);
        info.model = MCH_ModelManager.load((String)"tanks", (String)info.name);
        this.registerCommonPart("tanks", (MCH_AircraftInfo)info);
        MCH_ModelManager.setForceReloadMode((boolean)false);
    }

    private MCH_BulletModel loadBulletModel(String name) {
        IModelCustom m = MCH_ModelManager.load((String)"bullets", (String)name);
        return m != null ? new MCH_BulletModel(name, m) : null;
    }

    private IModelCustom loadPartModel(String path, String name, IModelCustom body, String part) {
        if (body instanceof W_ModelCustom && ((W_ModelCustom)body).containsPart("$" + part)) {
            return null;
        }
        return MCH_ModelManager.load((String)path, (String)(name + "_" + part));
    }

    private void registerCommonPart(String path, MCH_AircraftInfo info) {
        for (MCH_AircraftInfo.Hatch h : info.hatchList) {
            h.model = this.loadPartModel(path, info.name, info.model, h.modelName);
        }
        for (MCH_AircraftInfo.Camera c : info.cameraList) {
            c.model = this.loadPartModel(path, info.name, info.model, c.modelName);
        }
        for (MCH_AircraftInfo.Camera c : info.partThrottle) {
            c.model = this.loadPartModel(path, info.name, info.model, c.modelName);
        }
        for (MCH_AircraftInfo.Camera c : info.partRotPart) {
            c.model = this.loadPartModel(path, info.name, info.model, c.modelName);
        }
        for (MCH_AircraftInfo.PartWeapon p : info.partWeapon) {
            p.model = this.loadPartModel(path, info.name, info.model, p.modelName);
            for (MCH_AircraftInfo.PartWeaponChild wc : p.child) {
                wc.model = this.loadPartModel(path, info.name, info.model, wc.modelName);
            }
        }
        for (MCH_AircraftInfo.Camera c : info.canopyList) {
            c.model = this.loadPartModel(path, info.name, info.model, c.modelName);
        }
        for (MCH_AircraftInfo.LandingGear n : info.landingGear) {
            n.model = this.loadPartModel(path, info.name, info.model, n.modelName);
        }
        for (MCH_AircraftInfo.WeaponBay w : info.partWeaponBay) {
            w.model = this.loadPartModel(path, info.name, info.model, w.modelName);
        }
        for (MCH_AircraftInfo.Camera c : info.partCrawlerTrack) {
            c.model = this.loadPartModel(path, info.name, info.model, c.modelName);
        }
        for (MCH_AircraftInfo.Camera c : info.partTrackRoller) {
            c.model = this.loadPartModel(path, info.name, info.model, c.modelName);
        }
        for (MCH_AircraftInfo.Camera c : info.partWheel) {
            c.model = this.loadPartModel(path, info.name, info.model, c.modelName);
        }
        for (MCH_AircraftInfo.Camera c : info.partSteeringWheel) {
            c.model = this.loadPartModel(path, info.name, info.model, c.modelName);
        }
    }

    private void registerVCPModels(MCH_VehicleInfo info, MCH_VehicleInfo.VPart vp) {
        for (MCH_VehicleInfo.VPart vcp : vp.child) {
            vcp.model = this.loadPartModel("vehicles", info.name, info.model, vcp.modelName);
            if (vcp.child == null) continue;
            this.registerVCPModels(info, vcp);
        }
    }

    public void registerClientTick() {
        Minecraft mc = Minecraft.func_71410_x();
        MCH_ClientCommonTickHandler.instance = new MCH_ClientCommonTickHandler(mc, MCH_MOD.config);
        W_TickRegistry.registerTickHandler((W_TickHandler)MCH_ClientCommonTickHandler.instance, (Side)Side.CLIENT);
    }

    public boolean isRemote() {
        return true;
    }

    public String side() {
        return "Client";
    }

    public MCH_SoundUpdater CreateSoundUpdater(MCH_EntityAircraft aircraft) {
        if (aircraft == null || !aircraft.field_70170_p.field_72995_K) {
            return null;
        }
        return new MCH_SoundUpdater(Minecraft.func_71410_x(), aircraft, (EntityPlayerSP)Minecraft.func_71410_x().field_71439_g);
    }

    public void registerSounds() {
        W_McClient.addSound((String)"alert.ogg");
        W_McClient.addSound((String)"locked.ogg");
        W_McClient.addSound((String)"gltd.ogg");
        W_McClient.addSound((String)"zoom.ogg");
        W_McClient.addSound((String)"ng.ogg");
        W_McClient.addSound((String)"a-10_snd.ogg");
        W_McClient.addSound((String)"gau-8_snd.ogg");
        W_McClient.addSound((String)"hit.ogg");
        W_McClient.addSound((String)"helidmg.ogg");
        W_McClient.addSound((String)"heli.ogg");
        W_McClient.addSound((String)"plane.ogg");
        W_McClient.addSound((String)"plane_cc.ogg");
        W_McClient.addSound((String)"plane_cv.ogg");
        W_McClient.addSound((String)"chain.ogg");
        W_McClient.addSound((String)"chain_ct.ogg");
        W_McClient.addSound((String)"eject_seat.ogg");
        W_McClient.addSound((String)"fim92_snd.ogg");
        W_McClient.addSound((String)"fim92_reload.ogg");
        W_McClient.addSound((String)"lockon.ogg");
        for (MCH_WeaponInfo info : MCH_WeaponInfoManager.getValues()) {
            W_McClient.addSound((String)(info.soundFileName + ".ogg"));
        }
        for (MCH_WeaponInfo info : MCP_PlaneInfoManager.map.values()) {
            if (info.soundMove.isEmpty()) continue;
            W_McClient.addSound((String)(info.soundMove + ".ogg"));
        }
        for (MCH_WeaponInfo info : MCH_HeliInfoManager.map.values()) {
            if (info.soundMove.isEmpty()) continue;
            W_McClient.addSound((String)(info.soundMove + ".ogg"));
        }
        for (MCH_WeaponInfo info : MCH_TankInfoManager.map.values()) {
            if (info.soundMove.isEmpty()) continue;
            W_McClient.addSound((String)(info.soundMove + ".ogg"));
        }
        for (MCH_WeaponInfo info : MCH_VehicleInfoManager.map.values()) {
            if (info.soundMove.isEmpty()) continue;
            W_McClient.addSound((String)(info.soundMove + ".ogg"));
        }
    }

    public MCH_Config loadConfig(String fileName) {
        this.lastConfigFileName = fileName;
        MCH_Config config = new MCH_Config(Minecraft.func_71410_x().field_71412_D.getPath(), "/" + fileName);
        config.load();
        config.write();
        return config;
    }

    public MCH_Config reconfig() {
        MCH_Lib.DbgLog((boolean)false, (String)"MCH_ClientProxy.reconfig()", (Object[])new Object[0]);
        MCH_Config config = this.loadConfig(this.lastConfigFileName);
        MCH_ClientCommonTickHandler.instance.updatekeybind(config);
        return config;
    }

    public void loadHUD(String path) {
        this.lastLoadHUDPath = path;
        MCH_HudManager.load((String)path);
    }

    public void reloadHUD() {
        this.loadHUD(this.lastLoadHUDPath);
    }

    public Entity getClientPlayer() {
        return Minecraft.func_71410_x().field_71439_g;
    }

    public void init() {
        MinecraftForge.EVENT_BUS.register((Object)new MCH_ParticlesUtil());
        MinecraftForge.EVENT_BUS.register((Object)new MCH_ClientEventHook());
    }

    public void setCreativeDigDelay(int n) {
        W_Reflection.setCreativeDigSpeed((int)n);
    }

    public boolean isFirstPerson() {
        return Minecraft.func_71410_x().field_71474_y.field_74320_O == 0;
    }

    public int getNewRenderType() {
        return RenderingRegistry.getNextAvailableRenderId();
    }

    public boolean isSinglePlayer() {
        return Minecraft.func_71410_x().func_71356_B();
    }

    public void readClientModList() {
        try {
            Minecraft mc = Minecraft.func_71410_x();
            MCH_MultiplayClient.readModList((String)mc.func_110432_I().func_148255_b());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void printChatMessage(IChatComponent chat, int showTime, int pos) {
        ((MCH_GuiTitle)MCH_ClientCommonTickHandler.instance.gui_Title).setupTitle(chat, showTime, pos);
    }

    public void hitBullet() {
        MCH_ClientCommonTickHandler.instance.gui_Common.hitBullet();
    }

    public void clientLocked() {
        MCH_ClientCommonTickHandler.isLocked = true;
    }
}

