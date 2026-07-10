package mcheli;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
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
import mcheli.wrapper.W_TickRegistry;
import mcheli.wrapper.modelloader.W_ModelCustom;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.model.IModelCustom;
import net.minecraftforge.common.MinecraftForge;

public class MCH_ClientProxy extends MCH_CommonProxy {
   public String lastLoadHUDPath = "";

   @Override
   public String getDataDir() {
      return Minecraft.getMinecraft().mcDataDir.getPath();
   }

   @Override
   public void registerRenderer() {
      RenderingRegistry.registerEntityRenderingHandler(MCH_EntitySeat.class, new MCH_RenderTest(0.0F, 0.0F, 0.0F, "seat"));
      RenderingRegistry.registerEntityRenderingHandler(MCH_EntityHeli.class, new MCH_RenderHeli());
      RenderingRegistry.registerEntityRenderingHandler(MCP_EntityPlane.class, new MCP_RenderPlane());
      RenderingRegistry.registerEntityRenderingHandler(MCH_EntityTank.class, new MCH_RenderTank());
      RenderingRegistry.registerEntityRenderingHandler(MCH_EntityGLTD.class, new MCH_RenderGLTD());
      RenderingRegistry.registerEntityRenderingHandler(MCH_EntityChain.class, new MCH_RenderChain());
      RenderingRegistry.registerEntityRenderingHandler(MCH_EntityParachute.class, new MCH_RenderParachute());
      RenderingRegistry.registerEntityRenderingHandler(MCH_EntityContainer.class, new MCH_RenderContainer());
      RenderingRegistry.registerEntityRenderingHandler(MCH_EntityVehicle.class, new MCH_RenderVehicle());
      RenderingRegistry.registerEntityRenderingHandler(MCH_EntityUavStation.class, new MCH_RenderUavStation());
      RenderingRegistry.registerEntityRenderingHandler(MCH_EntityCartridge.class, new MCH_RenderCartridge());
      RenderingRegistry.registerEntityRenderingHandler(MCH_EntityHide.class, new MCH_RenderNull());
      RenderingRegistry.registerEntityRenderingHandler(MCH_ViewEntityDummy.class, new MCH_RenderNull());
      RenderingRegistry.registerEntityRenderingHandler(MCH_EntityRocket.class, new MCH_RenderBullet());
      RenderingRegistry.registerEntityRenderingHandler(MCH_EntityTvMissile.class, new MCH_RenderTvMissile());
      RenderingRegistry.registerEntityRenderingHandler(MCH_EntityBullet.class, new MCH_RenderBullet());
      RenderingRegistry.registerEntityRenderingHandler(MCH_EntityA10.class, new MCH_RenderA10());
      RenderingRegistry.registerEntityRenderingHandler(MCH_EntityAAMissile.class, new MCH_RenderAAMissile());
      RenderingRegistry.registerEntityRenderingHandler(MCH_EntityASMissile.class, new MCH_RenderASMissile());
      RenderingRegistry.registerEntityRenderingHandler(MCH_EntityATMissile.class, new MCH_RenderTvMissile());
      RenderingRegistry.registerEntityRenderingHandler(MCH_EntityTorpedo.class, new MCH_RenderBullet());
      RenderingRegistry.registerEntityRenderingHandler(MCH_EntityBomb.class, new MCH_RenderBomb());
      RenderingRegistry.registerEntityRenderingHandler(MCH_EntityMarkerRocket.class, new MCH_RenderBullet());
      RenderingRegistry.registerEntityRenderingHandler(MCH_EntityDispensedItem.class, new MCH_RenderNone());
      RenderingRegistry.registerEntityRenderingHandler(MCH_EntityFlare.class, new MCH_RenderFlare());
      RenderingRegistry.registerEntityRenderingHandler(MCH_EntityThrowable.class, new MCH_RenderThrowable());
      W_MinecraftForgeClient.registerItemRenderer(MCH_MOD.itemJavelin, new MCH_ItemLightWeaponRender());
      W_MinecraftForgeClient.registerItemRenderer(MCH_MOD.itemStinger, new MCH_ItemLightWeaponRender());
      W_MinecraftForgeClient.registerItemRenderer(MCH_MOD.invisibleItem, new MCH_InvisibleItemRender());
      W_MinecraftForgeClient.registerItemRenderer(MCH_MOD.itemGLTD, new MCH_ItemGLTDRender());
      W_MinecraftForgeClient.registerItemRenderer(MCH_MOD.itemWrench, new MCH_ItemRenderWrench());
      W_MinecraftForgeClient.registerItemRenderer(MCH_MOD.itemRangeFinder, new MCH_ItemRenderRangeFinder());
   }

   @Override
   public void registerBlockRenderer() {
      ClientRegistry.bindTileEntitySpecialRenderer(MCH_DraftingTableTileEntity.class, new MCH_DraftingTableRenderer());
      W_MinecraftForgeClient.registerItemRenderer(W_Item.getItemFromBlock(MCH_MOD.blockDraftingTable), new MCH_DraftingTableItemRender());
   }

   @Override
   public void registerModels() {
      MCH_ModelManager.setForceReloadMode(true);
      MCH_RenderAircraft.debugModel = MCH_ModelManager.load("box");
      MCH_ModelManager.load("a-10");
      MCH_RenderGLTD.model = MCH_ModelManager.load("gltd");
      MCH_ModelManager.load("chain");
      MCH_ModelManager.load("container");
      MCH_ModelManager.load("parachute1");
      MCH_ModelManager.load("parachute2");
      MCH_ModelManager.load("lweapons", "fim92");
      MCH_ModelManager.load("lweapons", "fgm148");

      for (String s : MCH_RenderUavStation.MODEL_NAME) {
         MCH_ModelManager.load(s);
      }

      MCH_ModelManager.load("wrench");
      MCH_ModelManager.load("rangefinder");
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

      registerModels_Bullet();
      MCH_DefaultBulletModels.Bullet = this.loadBulletModel("bullet");
      MCH_DefaultBulletModels.AAMissile = this.loadBulletModel("aamissile");
      MCH_DefaultBulletModels.ATMissile = this.loadBulletModel("asmissile");
      MCH_DefaultBulletModels.ASMissile = this.loadBulletModel("asmissile");
      MCH_DefaultBulletModels.Bomb = this.loadBulletModel("bomb");
      MCH_DefaultBulletModels.Rocket = this.loadBulletModel("rocket");
      MCH_DefaultBulletModels.Torpedo = this.loadBulletModel("torpedo");

      for (MCH_ThrowableInfo wi : MCH_ThrowableInfoManager.getValues()) {
         wi.model = MCH_ModelManager.load("throwable", wi.name);
      }

      MCH_ModelManager.load("blocks", "drafting_table");
   }

   public static void registerModels_Bullet() {
      for (MCH_WeaponInfo wi : MCH_WeaponInfoManager.getValues()) {
         IModelCustom m = null;
         if (!wi.bulletModelName.isEmpty()) {
            m = MCH_ModelManager.load("bullets", wi.bulletModelName);
            if (m != null) {
               wi.bulletModel = new MCH_BulletModel(wi.bulletModelName, m);
            }
         }

         if (!wi.bombletModelName.isEmpty()) {
            m = MCH_ModelManager.load("bullets", wi.bombletModelName);
            if (m != null) {
               wi.bombletModel = new MCH_BulletModel(wi.bombletModelName, m);
            }
         }

         if (wi.cartridge != null && !wi.cartridge.name.isEmpty()) {
            wi.cartridge.model = MCH_ModelManager.load("bullets", wi.cartridge.name);
            if (wi.cartridge.model == null) {
               wi.cartridge = null;
            }
         }
      }
   }

   @Override
   public void registerModelsHeli(String name, boolean reload) {
      MCH_ModelManager.setForceReloadMode(reload);
      MCH_HeliInfo info = MCH_HeliInfoManager.map.get(name);
      info.model = MCH_ModelManager.load("helicopters", info.name);

      for (MCH_HeliInfo.Rotor rotor : info.rotorList) {
         rotor.model = this.loadPartModel("helicopters", info.name, info.model, rotor.modelName);
      }

      this.registerCommonPart("helicopters", info);
      MCH_ModelManager.setForceReloadMode(false);
   }

   @Override
   public void registerModelsPlane(String name, boolean reload) {
      MCH_ModelManager.setForceReloadMode(reload);
      MCP_PlaneInfo info = MCP_PlaneInfoManager.map.get(name);
      info.model = MCH_ModelManager.load("planes", info.name);

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
         if (w.pylonList != null) {
            for (MCP_PlaneInfo.Pylon p : w.pylonList) {
               p.model = this.loadPartModel("planes", info.name, info.model, p.modelName);
            }
         }
      }

      this.registerCommonPart("planes", info);
      MCH_ModelManager.setForceReloadMode(false);
   }

   @Override
   public void registerModelsVehicle(String name, boolean reload) {
      MCH_ModelManager.setForceReloadMode(reload);
      MCH_VehicleInfo info = MCH_VehicleInfoManager.map.get(name);
      info.model = MCH_ModelManager.load("vehicles", info.name);

      for (MCH_VehicleInfo.VPart vp : info.partList) {
         vp.model = this.loadPartModel("vehicles", info.name, info.model, vp.modelName);
         if (vp.child != null) {
            this.registerVCPModels(info, vp);
         }
      }

      this.registerCommonPart("vehicles", info);
      MCH_ModelManager.setForceReloadMode(false);
   }

   @Override
   public void registerModelsTank(String name, boolean reload) {
      MCH_ModelManager.setForceReloadMode(reload);
      MCH_TankInfo info = MCH_TankInfoManager.map.get(name);
      info.model = MCH_ModelManager.load("tanks", info.name);
      this.registerCommonPart("tanks", info);
      MCH_ModelManager.setForceReloadMode(false);
   }

   private MCH_BulletModel loadBulletModel(String name) {
      IModelCustom m = MCH_ModelManager.load("bullets", name);
      return m != null ? new MCH_BulletModel(name, m) : null;
   }

   private IModelCustom loadPartModel(String path, String name, IModelCustom body, String part) {
      return body instanceof W_ModelCustom && ((W_ModelCustom)body).containsPart("$" + part) ? null : MCH_ModelManager.load(path, name + "_" + part);
   }

   private void registerCommonPart(String path, MCH_AircraftInfo info) {
      for (MCH_AircraftInfo.Hatch h : info.hatchList) {
         h.model = this.loadPartModel(path, info.name, info.model, h.modelName);
      }

      for (MCH_AircraftInfo.Camera c : info.cameraList) {
         c.model = this.loadPartModel(path, info.name, info.model, c.modelName);
      }

      for (MCH_AircraftInfo.Throttle c : info.partThrottle) {
         c.model = this.loadPartModel(path, info.name, info.model, c.modelName);
      }

      for (MCH_AircraftInfo.RotPart c : info.partRotPart) {
         c.model = this.loadPartModel(path, info.name, info.model, c.modelName);
      }

      for (MCH_AircraftInfo.PartWeapon p : info.partWeapon) {
         p.model = this.loadPartModel(path, info.name, info.model, p.modelName);

         for (MCH_AircraftInfo.PartWeaponChild wc : p.child) {
            wc.model = this.loadPartModel(path, info.name, info.model, wc.modelName);
         }
      }

      for (MCH_AircraftInfo.Canopy c : info.canopyList) {
         c.model = this.loadPartModel(path, info.name, info.model, c.modelName);
      }

      for (MCH_AircraftInfo.DrawnPart n : info.landingGear) {
         n.model = this.loadPartModel(path, info.name, info.model, n.modelName);
      }

      for (MCH_AircraftInfo.WeaponBay w : info.partWeaponBay) {
         w.model = this.loadPartModel(path, info.name, info.model, w.modelName);
      }

      for (MCH_AircraftInfo.CrawlerTrack c : info.partCrawlerTrack) {
         c.model = this.loadPartModel(path, info.name, info.model, c.modelName);
      }

      for (MCH_AircraftInfo.TrackRoller c : info.partTrackRoller) {
         c.model = this.loadPartModel(path, info.name, info.model, c.modelName);
      }

      for (MCH_AircraftInfo.PartWheel c : info.partWheel) {
         c.model = this.loadPartModel(path, info.name, info.model, c.modelName);
      }

      for (MCH_AircraftInfo.PartWheel c : info.partSteeringWheel) {
         c.model = this.loadPartModel(path, info.name, info.model, c.modelName);
      }
   }

   private void registerVCPModels(MCH_VehicleInfo info, MCH_VehicleInfo.VPart vp) {
      for (MCH_VehicleInfo.VPart vcp : vp.child) {
         vcp.model = this.loadPartModel("vehicles", info.name, info.model, vcp.modelName);
         if (vcp.child != null) {
            this.registerVCPModels(info, vcp);
         }
      }
   }

   @Override
   public void registerClientTick() {
      Minecraft mc = Minecraft.getMinecraft();
      MCH_ClientCommonTickHandler.instance = new MCH_ClientCommonTickHandler(mc, MCH_MOD.config);
      W_TickRegistry.registerTickHandler(MCH_ClientCommonTickHandler.instance, Side.CLIENT);
   }

   @Override
   public boolean isRemote() {
      return true;
   }

   @Override
   public String side() {
      return "Client";
   }

   @Override
   public MCH_SoundUpdater CreateSoundUpdater(MCH_EntityAircraft aircraft) {
      return aircraft != null && aircraft.worldObj.isRemote
         ? new MCH_SoundUpdater(Minecraft.getMinecraft(), aircraft, Minecraft.getMinecraft().thePlayer)
         : null;
   }

   @Override
   public void registerSounds() {
      W_McClient.addSound("alert.ogg");
      W_McClient.addSound("locked.ogg");
      W_McClient.addSound("gltd.ogg");
      W_McClient.addSound("zoom.ogg");
      W_McClient.addSound("ng.ogg");
      W_McClient.addSound("a-10_snd.ogg");
      W_McClient.addSound("gau-8_snd.ogg");
      W_McClient.addSound("hit.ogg");
      W_McClient.addSound("helidmg.ogg");
      W_McClient.addSound("heli.ogg");
      W_McClient.addSound("plane.ogg");
      W_McClient.addSound("plane_cc.ogg");
      W_McClient.addSound("plane_cv.ogg");
      W_McClient.addSound("chain.ogg");
      W_McClient.addSound("chain_ct.ogg");
      W_McClient.addSound("eject_seat.ogg");
      W_McClient.addSound("fim92_snd.ogg");
      W_McClient.addSound("fim92_reload.ogg");
      W_McClient.addSound("lockon.ogg");

      for (MCH_WeaponInfo info : MCH_WeaponInfoManager.getValues()) {
         W_McClient.addSound(info.soundFileName + ".ogg");
      }

      for (MCH_AircraftInfo info : MCP_PlaneInfoManager.map.values()) {
         if (!info.soundMove.isEmpty()) {
            W_McClient.addSound(info.soundMove + ".ogg");
         }
      }

      for (MCH_AircraftInfo info : MCH_HeliInfoManager.map.values()) {
         if (!info.soundMove.isEmpty()) {
            W_McClient.addSound(info.soundMove + ".ogg");
         }
      }

      for (MCH_AircraftInfo info : MCH_TankInfoManager.map.values()) {
         if (!info.soundMove.isEmpty()) {
            W_McClient.addSound(info.soundMove + ".ogg");
         }
      }

      for (MCH_AircraftInfo info : MCH_VehicleInfoManager.map.values()) {
         if (!info.soundMove.isEmpty()) {
            W_McClient.addSound(info.soundMove + ".ogg");
         }
      }
   }

   @Override
   public MCH_Config loadConfig(String fileName) {
      this.lastConfigFileName = fileName;
      MCH_Config config = new MCH_Config(Minecraft.getMinecraft().mcDataDir.getPath(), "/" + fileName);
      config.load();
      config.write();
      return config;
   }

   @Override
   public MCH_Config reconfig() {
      MCH_Lib.DbgLog(false, "MCH_ClientProxy.reconfig()");
      MCH_Config config = this.loadConfig(this.lastConfigFileName);
      MCH_ClientCommonTickHandler.instance.updatekeybind(config);
      return config;
   }

   @Override
   public void loadHUD(String path) {
      this.lastLoadHUDPath = path;
      MCH_HudManager.load(path);
   }

   @Override
   public void reloadHUD() {
      this.loadHUD(this.lastLoadHUDPath);
   }

   @Override
   public Entity getClientPlayer() {
      return Minecraft.getMinecraft().thePlayer;
   }

   @Override
   public void init() {
      MinecraftForge.EVENT_BUS.register(new MCH_ParticlesUtil());
      MinecraftForge.EVENT_BUS.register(new MCH_ClientEventHook());
   }

   @Override
   public void setCreativeDigDelay(int n) {
      W_Reflection.setCreativeDigSpeed(n);
   }

   @Override
   public boolean isFirstPerson() {
      return Minecraft.getMinecraft().gameSettings.thirdPersonView == 0;
   }

   @Override
   public int getNewRenderType() {
      return RenderingRegistry.getNextAvailableRenderId();
   }

   @Override
   public boolean isSinglePlayer() {
      return Minecraft.getMinecraft().isSingleplayer();
   }

   @Override
   public void readClientModList() {
      try {
         Minecraft mc = Minecraft.getMinecraft();
         MCH_MultiplayClient.readModList(mc.getSession().getPlayerID());
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   @Override
   public void printChatMessage(IChatComponent chat, int showTime, int pos) {
      ((MCH_GuiTitle)MCH_ClientCommonTickHandler.instance.gui_Title).setupTitle(chat, showTime, pos);
   }

   @Override
   public void hitBullet() {
      MCH_ClientCommonTickHandler.instance.gui_Common.hitBullet();
   }

   @Override
   public void clientLocked() {
      MCH_ClientCommonTickHandler.isLocked = true;
   }
}
