/*
 * Decompiled with CFR 0.152.
 */
package mcheli.vehicle;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.MCH_Config;
import mcheli.MCH_KeyName;
import mcheli.aircraft.MCH_AircraftCommonGui;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.vehicle.MCH_EntityVehicle;
import mcheli.vehicle.MCH_VehicleInfo;
import mcheli.weapon.MCH_WeaponSet;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

@SideOnly(value=Side.CLIENT)
public class MCH_GuiVehicle
extends MCH_AircraftCommonGui {
    static final int COLOR1 = -14066;
    static final int COLOR2 = -2161656;

    public MCH_GuiVehicle(Minecraft minecraft) {
        super(minecraft);
    }

    public boolean isDrawGui(EntityPlayer player) {
        return player.field_70154_o != null && player.field_70154_o instanceof MCH_EntityVehicle;
    }

    public void drawGui(EntityPlayer player, boolean isThirdPersonView) {
        int seatID;
        MCH_EntityVehicle vehicle;
        block8: {
            block7: {
                if (player.field_70154_o == null || !(player.field_70154_o instanceof MCH_EntityVehicle)) {
                    return;
                }
                vehicle = (MCH_EntityVehicle)player.field_70154_o;
                if (vehicle.isDestroyed()) {
                    return;
                }
                seatID = vehicle.getSeatIdByEntity((Entity)player);
                GL11.glLineWidth((float)scaleFactor);
                if (vehicle.getCameraMode(player) == 1) {
                    this.drawNightVisionNoise();
                }
                if (vehicle.getIsGunnerMode((Entity)player) && vehicle.getTVMissile() != null) {
                    this.drawTvMissileNoise((MCH_EntityAircraft)vehicle, vehicle.getTVMissile());
                }
                this.drawDebugtInfo((MCH_EntityAircraft)vehicle);
                if (!isThirdPersonView) break block7;
                if (!MCH_Config.DisplayHUDThirdPerson.prmBool) break block8;
            }
            this.drawHud((MCH_EntityAircraft)vehicle, player, seatID);
            this.drawKeyBind(vehicle, player);
        }
        this.drawHitBullet((MCH_EntityAircraft)vehicle, -14066, seatID);
    }

    public void drawKeyBind(MCH_EntityVehicle vehicle, EntityPlayer player) {
        String msg;
        if (MCH_Config.HideKeybind.prmBool) {
            return;
        }
        MCH_VehicleInfo info = vehicle.getVehicleInfo();
        if (info == null) {
            return;
        }
        int colorActive = -1342177281;
        int colorInactive = -1349546097;
        int RX = this.centerX + 120;
        int LX = this.centerX - 200;
        if (vehicle.haveFlare()) {
            int c = vehicle.isFlarePreparation() ? colorInactive : colorActive;
            msg = "Flare : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyFlare.prmInt);
            this.drawString(msg, RX, this.centerY - 50, c);
        }
        if (vehicle.func_70302_i_() > 0) {
            // empty if block
        }
        if (vehicle.getTowChainEntity() != null && !vehicle.getTowChainEntity().field_70128_L) {
            msg = "Drop  : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyExtra.prmInt);
            this.drawString(msg, RX, this.centerY - 30, colorActive);
        }
        if (vehicle.camera.getCameraZoom() > 1.0f) {
            msg = "Zoom : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyZoom.prmInt);
            this.drawString(msg, LX, this.centerY - 80, colorActive);
        }
        MCH_WeaponSet ws = vehicle.getCurrentWeapon((Entity)player);
        if (vehicle.getWeaponNum() > 1) {
            msg = "Weapon : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeySwitchWeapon2.prmInt);
            this.drawString(msg, LX, this.centerY - 70, colorActive);
        }
        if (ws.getCurrentWeapon().numMode > 0) {
            msg = "WeaponMode : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeySwWeaponMode.prmInt);
            this.drawString(msg, LX, this.centerY - 60, colorActive);
        }
        if (info.isEnableNightVision) {
            msg = "CameraMode : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyCameraMode.prmInt);
            this.drawString(msg, LX, this.centerY - 50, colorActive);
        }
        msg = "Dismount all : LShift";
        this.drawString(msg, LX, this.centerY - 40, colorActive);
        if (vehicle.getSeatNum() >= 2) {
            msg = "Dismount : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyUnmount.prmInt);
            this.drawString(msg, LX, this.centerY - 30, colorActive);
        }
    }
}

