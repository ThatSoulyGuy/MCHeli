package mcheli.vehicle;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.MCH_Config;
import mcheli.MCH_KeyName;
import mcheli.aircraft.MCH_AircraftCommonGui;
import mcheli.weapon.MCH_WeaponSet;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class MCH_GuiVehicle extends MCH_AircraftCommonGui {
   static final int COLOR1 = -14066;
   static final int COLOR2 = -2161656;

   public MCH_GuiVehicle(Minecraft minecraft) {
      super(minecraft);
   }

   @Override
   public boolean isDrawGui(EntityPlayer player) {
      return player.ridingEntity != null && player.ridingEntity instanceof MCH_EntityVehicle;
   }

   @Override
   public void drawGui(EntityPlayer player, boolean isThirdPersonView) {
      if (player.ridingEntity != null && player.ridingEntity instanceof MCH_EntityVehicle) {
         MCH_EntityVehicle vehicle = (MCH_EntityVehicle)player.ridingEntity;
         if (!vehicle.isDestroyed()) {
            int seatID = vehicle.getSeatIdByEntity(player);
            GL11.glLineWidth(scaleFactor);
            if (vehicle.getCameraMode(player) == 1) {
               this.drawNightVisionNoise();
            }

            if (vehicle.getIsGunnerMode(player) && vehicle.getTVMissile() != null) {
               this.drawTvMissileNoise(vehicle, vehicle.getTVMissile());
            }

            this.drawDebugtInfo(vehicle);
            if (!isThirdPersonView || MCH_Config.DisplayHUDThirdPerson.prmBool) {
               this.drawHud(vehicle, player, seatID);
               this.drawKeyBind(vehicle, player);
            }

            this.drawHitBullet(vehicle, -14066, seatID);
         }
      }
   }

   public void drawKeyBind(MCH_EntityVehicle vehicle, EntityPlayer player) {
      if (!MCH_Config.HideKeybind.prmBool) {
         MCH_VehicleInfo info = vehicle.getVehicleInfo();
         if (info != null) {
            int colorActive = -1342177281;
            int colorInactive = -1349546097;
            int RX = this.centerX + 120;
            int LX = this.centerX - 200;
            if (vehicle.haveFlare()) {
               int c = vehicle.isFlarePreparation() ? colorInactive : colorActive;
               String msg = "Flare : " + MCH_KeyName.getDescOrName(MCH_Config.KeyFlare.prmInt);
               this.drawString(msg, RX, this.centerY - 50, c);
            }

            if (vehicle.getSizeInventory() > 0) {
            }

            if (vehicle.getTowChainEntity() != null && !vehicle.getTowChainEntity().isDead) {
               String msg = "Drop  : " + MCH_KeyName.getDescOrName(MCH_Config.KeyExtra.prmInt);
               this.drawString(msg, RX, this.centerY - 30, colorActive);
            }

            if (vehicle.camera.getCameraZoom() > 1.0F) {
               String msg = "Zoom : " + MCH_KeyName.getDescOrName(MCH_Config.KeyZoom.prmInt);
               this.drawString(msg, LX, this.centerY - 80, colorActive);
            }

            MCH_WeaponSet ws = vehicle.getCurrentWeapon(player);
            if (vehicle.getWeaponNum() > 1) {
               String msg = "Weapon : " + MCH_KeyName.getDescOrName(MCH_Config.KeySwitchWeapon2.prmInt);
               this.drawString(msg, LX, this.centerY - 70, colorActive);
            }

            if (ws.getCurrentWeapon().numMode > 0) {
               String msg = "WeaponMode : " + MCH_KeyName.getDescOrName(MCH_Config.KeySwWeaponMode.prmInt);
               this.drawString(msg, LX, this.centerY - 60, colorActive);
            }

            if (info.isEnableNightVision) {
               String msg = "CameraMode : " + MCH_KeyName.getDescOrName(MCH_Config.KeyCameraMode.prmInt);
               this.drawString(msg, LX, this.centerY - 50, colorActive);
            }

            String msg = "Dismount all : LShift";
            this.drawString(msg, LX, this.centerY - 40, colorActive);
            if (vehicle.getSeatNum() >= 2) {
               msg = "Dismount : " + MCH_KeyName.getDescOrName(MCH_Config.KeyUnmount.prmInt);
               this.drawString(msg, LX, this.centerY - 30, colorActive);
            }
         }
      }
   }
}
