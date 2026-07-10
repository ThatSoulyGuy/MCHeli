/*
 * Decompiled with CFR 0.152.
 */
package mcheli.tank;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.MCH_Config;
import mcheli.MCH_KeyName;
import mcheli.aircraft.MCH_AircraftCommonGui;
import mcheli.aircraft.MCH_AircraftInfo;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.tank.MCH_EntityTank;
import mcheli.tank.MCH_TankInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

@SideOnly(value=Side.CLIENT)
public class MCH_GuiTank
extends MCH_AircraftCommonGui {
    public MCH_GuiTank(Minecraft minecraft) {
        super(minecraft);
    }

    public boolean isDrawGui(EntityPlayer player) {
        return MCH_EntityAircraft.getAircraft_RiddenOrControl((Entity)player) instanceof MCH_EntityTank;
    }

    public void drawGui(EntityPlayer player, boolean isThirdPersonView) {
        int seatID;
        MCH_EntityTank tank;
        block13: {
            block12: {
                block11: {
                    MCH_EntityAircraft ac;
                    block10: {
                        ac = MCH_EntityAircraft.getAircraft_RiddenOrControl((Entity)player);
                        if (!(ac instanceof MCH_EntityTank) || ac.isDestroyed()) {
                            return;
                        }
                        tank = (MCH_EntityTank)ac;
                        seatID = ac.getSeatIdByEntity((Entity)player);
                        GL11.glLineWidth((float)scaleFactor);
                        if (tank.getCameraMode(player) == 1) {
                            this.drawNightVisionNoise();
                        }
                        if (!isThirdPersonView) break block10;
                        if (!MCH_Config.DisplayHUDThirdPerson.prmBool) break block11;
                    }
                    this.drawHud(ac, player, seatID);
                }
                this.drawDebugtInfo(tank);
                if (!isThirdPersonView) break block12;
                if (!MCH_Config.DisplayHUDThirdPerson.prmBool) break block13;
            }
            if (tank.getTVMissile() != null && (tank.getIsGunnerMode((Entity)player) || tank.isUAV())) {
                this.drawTvMissileNoise((MCH_EntityAircraft)tank, tank.getTVMissile());
            } else {
                this.drawKeybind(tank, player, seatID);
            }
        }
        this.drawHitBullet((MCH_EntityAircraft)tank, -14101432, seatID);
    }

    public void drawDebugtInfo(MCH_EntityTank ac) {
        if (MCH_Config.DebugLog) {
            int LX = this.centerX - 100;
            super.drawDebugtInfo((MCH_EntityAircraft)ac);
        }
    }

    public void drawKeybind(MCH_EntityTank tank, EntityPlayer player, int seatID) {
        String msg;
        if (MCH_Config.HideKeybind.prmBool) {
            return;
        }
        MCH_TankInfo info = tank.getTankInfo();
        if (info == null) {
            return;
        }
        int colorActive = -1342177281;
        int colorInactive = -1349546097;
        int RX = this.centerX + 120;
        int LX = this.centerX - 200;
        this.drawKeyBind((MCH_EntityAircraft)tank, (MCH_AircraftInfo)info, player, seatID, RX, LX, colorActive, colorInactive);
        if (seatID == 0 && tank.hasBrake()) {
            msg = "Brake : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeySwitchHovering.prmInt);
            this.drawString(msg, RX, this.centerY - 30, colorActive);
        }
        if (seatID > 0 && tank.canSwitchGunnerModeOtherSeat(player)) {
            msg = (tank.getIsGunnerMode((Entity)player) ? "Normal" : "Camera") + " : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeySwitchMode.prmInt);
            this.drawString(msg, RX, this.centerY - 40, colorActive);
        }
        if (tank.getIsGunnerMode((Entity)player) && info.cameraZoom > 1) {
            msg = "Zoom : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyZoom.prmInt);
            this.drawString(msg, LX, this.centerY - 80, colorActive);
        } else if (seatID == 0 && (tank.canFoldHatch() || tank.canUnfoldHatch())) {
            msg = "OpenHatch : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyZoom.prmInt);
            this.drawString(msg, LX, this.centerY - 80, colorActive);
        }
    }
}

