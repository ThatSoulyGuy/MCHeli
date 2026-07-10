package mcheli.vehicle;

import mcheli.MCH_Config;
import mcheli.MCH_Key;
import mcheli.MCH_Lib;
import mcheli.MCH_ViewEntityDummy;
import mcheli.aircraft.MCH_AircraftClientTickHandler;
import mcheli.aircraft.MCH_EntitySeat;
import mcheli.wrapper.W_Entity;
import mcheli.wrapper.W_Network;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

public class MCH_ClientVehicleTickHandler extends MCH_AircraftClientTickHandler {
   public MCH_Key KeySwitchMode;
   public MCH_Key KeySwitchHovering;
   public MCH_Key KeyZoom;
   public MCH_Key KeyExtra;
   public MCH_Key[] Keys;

   public MCH_ClientVehicleTickHandler(Minecraft minecraft, MCH_Config config) {
      super(minecraft, config);
      this.updateKeybind(config);
   }

   @Override
   public void updateKeybind(MCH_Config config) {
      super.updateKeybind(config);
      this.KeySwitchMode = new MCH_Key(MCH_Config.KeySwitchMode.prmInt);
      this.KeySwitchHovering = new MCH_Key(MCH_Config.KeySwitchHovering.prmInt);
      this.KeyZoom = new MCH_Key(MCH_Config.KeyZoom.prmInt);
      this.KeyExtra = new MCH_Key(MCH_Config.KeyExtra.prmInt);
      this.Keys = new MCH_Key[]{
         this.KeyUp,
         this.KeyDown,
         this.KeyRight,
         this.KeyLeft,
         this.KeySwitchMode,
         this.KeySwitchHovering,
         this.KeyUseWeapon,
         this.KeySwWeaponMode,
         this.KeySwitchWeapon1,
         this.KeySwitchWeapon2,
         this.KeyZoom,
         this.KeyCameraMode,
         this.KeyUnmount,
         this.KeyUnmountForce,
         this.KeyFlare,
         this.KeyExtra,
         this.KeyGUI
      };
   }

   protected void update(EntityPlayer player, MCH_EntityVehicle vehicle, MCH_VehicleInfo info) {
      if (info != null) {
         setRotLimitPitch(info.minRotationPitch, info.maxRotationPitch, player);
      }

      vehicle.updateCameraRotate(player.rotationYaw, player.rotationPitch);
      vehicle.updateRadar(5);
   }

   @Override
   protected void onTick(boolean inGUI) {
      for (MCH_Key k : this.Keys) {
         k.update();
      }

      this.isBeforeRiding = this.isRiding;
      EntityPlayer player = this.mc.thePlayer;
      MCH_EntityVehicle vehicle = null;
      boolean isPilot = true;
      if (player != null) {
         if (player.ridingEntity instanceof MCH_EntityVehicle) {
            vehicle = (MCH_EntityVehicle)player.ridingEntity;
         } else if (player.ridingEntity instanceof MCH_EntitySeat) {
            MCH_EntitySeat seat = (MCH_EntitySeat)player.ridingEntity;
            if (seat.getParent() instanceof MCH_EntityVehicle) {
               isPilot = false;
               vehicle = (MCH_EntityVehicle)seat.getParent();
            }
         }
      }

      if (vehicle != null && vehicle.getAcInfo() != null) {
         MCH_Lib.disableFirstPersonItemRender(player.getCurrentEquippedItem());
         this.update(player, vehicle, vehicle.getVehicleInfo());
         MCH_ViewEntityDummy viewEntityDummy = MCH_ViewEntityDummy.getInstance(this.mc.theWorld);
         viewEntityDummy.update(vehicle.camera);
         if (!inGUI) {
            if (!vehicle.isDestroyed()) {
               this.playerControl(player, vehicle, isPilot);
            }
         } else {
            this.playerControlInGUI(player, vehicle, isPilot);
         }

         MCH_Lib.setRenderViewEntity(viewEntityDummy);
         this.isRiding = true;
      } else {
         this.isRiding = false;
      }

      if ((this.isBeforeRiding || !this.isRiding) && this.isBeforeRiding && !this.isRiding) {
         MCH_Lib.enableFirstPersonItemRender();
         MCH_Lib.setRenderViewEntity(player);
      }
   }

   protected void playerControlInGUI(EntityPlayer player, MCH_EntityVehicle vehicle, boolean isPilot) {
      this.commonPlayerControlInGUI(player, vehicle, isPilot, new MCH_PacketVehiclePlayerControl());
   }

   protected void playerControl(EntityPlayer player, MCH_EntityVehicle vehicle, boolean isPilot) {
      MCH_PacketVehiclePlayerControl pc = new MCH_PacketVehiclePlayerControl();
      boolean send = false;
      send = this.commonPlayerControl(player, vehicle, isPilot, pc);
      if (this.KeyExtra.isKeyDown()) {
         if (vehicle.getTowChainEntity() != null) {
            playSoundOK();
            pc.unhitchChainId = W_Entity.getEntityId(vehicle.getTowChainEntity());
            send = true;
         } else {
            playSoundNG();
         }
      }

      if (!this.KeySwitchHovering.isKeyDown() && this.KeySwitchMode.isKeyDown()) {
      }

      if (this.KeyZoom.isKeyDown()) {
         if (vehicle.canZoom()) {
            vehicle.zoomCamera();
            playSound("zoom", 0.5F, 1.0F);
         } else if (vehicle.getAcInfo().haveHatch()) {
            if (vehicle.canFoldHatch()) {
               pc.switchHatch = 2;
               send = true;
            } else if (vehicle.canUnfoldHatch()) {
               pc.switchHatch = 1;
               send = true;
            } else {
               playSoundNG();
            }
         }
      }

      if (send) {
         W_Network.sendToServer(pc);
      }
   }
}
