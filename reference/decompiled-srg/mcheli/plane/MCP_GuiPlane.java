/*
 * Decompiled with CFR 0.152.
 */
package mcheli.plane;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.MCH_Config;
import mcheli.MCH_KeyName;
import mcheli.aircraft.MCH_AircraftCommonGui;
import mcheli.aircraft.MCH_AircraftInfo;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.plane.MCP_EntityPlane;
import mcheli.plane.MCP_PlaneInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

@SideOnly(value=Side.CLIENT)
public class MCP_GuiPlane
extends MCH_AircraftCommonGui {
    public MCP_GuiPlane(Minecraft minecraft) {
        super(minecraft);
    }

    public boolean isDrawGui(EntityPlayer player) {
        return MCH_EntityAircraft.getAircraft_RiddenOrControl((Entity)player) instanceof MCP_EntityPlane;
    }

    public void drawGui(EntityPlayer player, boolean isThirdPersonView) {
        int seatID;
        MCP_EntityPlane plane;
        block15: {
            block14: {
                block13: {
                    MCH_EntityAircraft ac;
                    block12: {
                        ac = MCH_EntityAircraft.getAircraft_RiddenOrControl((Entity)player);
                        if (!(ac instanceof MCP_EntityPlane) || ac.isDestroyed()) {
                            return;
                        }
                        plane = (MCP_EntityPlane)ac;
                        seatID = ac.getSeatIdByEntity((Entity)player);
                        GL11.glLineWidth((float)scaleFactor);
                        if (plane.getCameraMode(player) == 1) {
                            this.drawNightVisionNoise();
                        }
                        if (!isThirdPersonView) break block12;
                        if (!MCH_Config.DisplayHUDThirdPerson.prmBool) break block13;
                    }
                    if (seatID == 0 && plane.getIsGunnerMode((Entity)player)) {
                        this.drawHud(ac, player, 1);
                    } else {
                        this.drawHud(ac, player, seatID);
                    }
                }
                this.drawDebugtInfo((MCH_EntityAircraft)plane);
                if (!isThirdPersonView) break block14;
                if (!MCH_Config.DisplayHUDThirdPerson.prmBool) break block15;
            }
            if (plane.getTVMissile() != null && (plane.getIsGunnerMode((Entity)player) || plane.isUAV())) {
                this.drawTvMissileNoise((MCH_EntityAircraft)plane, plane.getTVMissile());
            } else {
                this.drawKeybind(plane, player, seatID);
            }
        }
        this.drawHitBullet((MCH_EntityAircraft)plane, -14101432, seatID);
    }

    public void drawKeybind(MCP_EntityPlane plane, EntityPlayer player, int seatID) {
        String msg;
        if (MCH_Config.HideKeybind.prmBool) {
            return;
        }
        MCP_PlaneInfo info = plane.getPlaneInfo();
        if (info == null) {
            return;
        }
        int colorActive = -1342177281;
        int colorInactive = -1349546097;
        int RX = this.centerX + 120;
        int LX = this.centerX - 200;
        this.drawKeyBind((MCH_EntityAircraft)plane, (MCH_AircraftInfo)info, player, seatID, RX, LX, colorActive, colorInactive);
        if (seatID == 0 && info.isEnableGunnerMode) {
            if (!Keyboard.isKeyDown((int)MCH_Config.KeyFreeLook.prmInt)) {
                int c = plane.isHoveringMode() ? colorInactive : colorActive;
                msg = (plane.getIsGunnerMode((Entity)player) ? "Normal" : "Gunner") + " : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeySwitchMode.prmInt);
                this.drawString(msg, RX, this.centerY - 70, c);
            }
        }
        if (seatID > 0 && plane.canSwitchGunnerModeOtherSeat(player)) {
            msg = (plane.getIsGunnerMode((Entity)player) ? "Normal" : "Camera") + " : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeySwitchMode.prmInt);
            this.drawString(msg, RX, this.centerY - 40, colorActive);
        }
        if (seatID == 0 && info.isEnableVtol) {
            int stat;
            if (!Keyboard.isKeyDown((int)MCH_Config.KeyFreeLook.prmInt) && (stat = plane.getVtolMode()) != 1) {
                msg = (stat == 0 ? "VTOL : " : "Normal : ") + MCH_KeyName.getDescOrName((int)MCH_Config.KeyExtra.prmInt);
                this.drawString(msg, RX, this.centerY - 60, colorActive);
            }
        }
        if (plane.canEjectSeat((Entity)player)) {
            msg = "Eject seat: " + MCH_KeyName.getDescOrName((int)MCH_Config.KeySwitchHovering.prmInt);
            this.drawString(msg, RX, this.centerY - 30, colorActive);
        }
        if (plane.getIsGunnerMode((Entity)player) && info.cameraZoom > 1) {
            msg = "Zoom : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyZoom.prmInt);
            this.drawString(msg, LX, this.centerY - 80, colorActive);
        } else if (seatID == 0) {
            if (plane.canFoldWing() || plane.canUnfoldWing()) {
                msg = "FoldWing : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyZoom.prmInt);
                this.drawString(msg, LX, this.centerY - 80, colorActive);
            } else if (plane.canFoldHatch() || plane.canUnfoldHatch()) {
                msg = "OpenHatch : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyZoom.prmInt);
                this.drawString(msg, LX, this.centerY - 80, colorActive);
            }
        }
    }
}

