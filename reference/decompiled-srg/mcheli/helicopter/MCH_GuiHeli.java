/*
 * Decompiled with CFR 0.152.
 */
package mcheli.helicopter;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.MCH_Config;
import mcheli.MCH_KeyName;
import mcheli.MCH_Lib;
import mcheli.aircraft.MCH_AircraftCommonGui;
import mcheli.aircraft.MCH_AircraftInfo;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.helicopter.MCH_EntityHeli;
import mcheli.helicopter.MCH_HeliInfo;
import mcheli.weapon.MCH_EntityTvMissile;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

@SideOnly(value=Side.CLIENT)
public class MCH_GuiHeli
extends MCH_AircraftCommonGui {
    public MCH_GuiHeli(Minecraft minecraft) {
        super(minecraft);
    }

    public boolean isDrawGui(EntityPlayer player) {
        return MCH_EntityAircraft.getAircraft_RiddenOrControl((Entity)player) instanceof MCH_EntityHeli;
    }

    public void drawGui(EntityPlayer player, boolean isThirdPersonView) {
        block21: {
            int seatID;
            MCH_EntityHeli heli;
            block23: {
                block22: {
                    block18: {
                        block20: {
                            block19: {
                                block17: {
                                    MCH_EntityAircraft ac;
                                    block16: {
                                        ac = MCH_EntityAircraft.getAircraft_RiddenOrControl((Entity)player);
                                        if (!(ac instanceof MCH_EntityHeli) || ac.isDestroyed()) {
                                            return;
                                        }
                                        heli = (MCH_EntityHeli)ac;
                                        seatID = ac.getSeatIdByEntity((Entity)player);
                                        GL11.glLineWidth((float)scaleFactor);
                                        if (heli.getCameraMode(player) == 1) {
                                            this.drawNightVisionNoise();
                                        }
                                        if (!isThirdPersonView) break block16;
                                        if (!MCH_Config.DisplayHUDThirdPerson.prmBool) break block17;
                                    }
                                    if (seatID == 0 && heli.getIsGunnerMode((Entity)player)) {
                                        this.drawHud(ac, player, 1);
                                    } else {
                                        this.drawHud(ac, player, seatID);
                                    }
                                }
                                this.drawDebugtInfo((MCH_EntityAircraft)heli);
                                if (heli.getIsGunnerMode((Entity)player)) break block18;
                                if (!isThirdPersonView) break block19;
                                if (!MCH_Config.DisplayHUDThirdPerson.prmBool) break block20;
                            }
                            this.drawKeyBind(heli, player, seatID);
                        }
                        this.drawHitBullet((MCH_EntityAircraft)heli, -14101432, seatID);
                        break block21;
                    }
                    if (!isThirdPersonView) break block22;
                    if (!MCH_Config.DisplayHUDThirdPerson.prmBool) break block23;
                }
                MCH_EntityTvMissile tvmissile = heli.getTVMissile();
                if (!heli.isMissileCameraMode((Entity)player)) {
                    this.drawKeyBind(heli, player, seatID);
                } else if (tvmissile != null) {
                    this.drawTvMissileNoise((MCH_EntityAircraft)heli, tvmissile);
                }
            }
            this.drawHitBullet((MCH_EntityAircraft)heli, -805306369, seatID);
        }
    }

    public void drawKeyBind(MCH_EntityHeli heli, EntityPlayer player, int seatID) {
        String msg;
        int c;
        if (MCH_Config.HideKeybind.prmBool) {
            return;
        }
        MCH_HeliInfo info = heli.getHeliInfo();
        if (info == null) {
            return;
        }
        int colorActive = -1342177281;
        int colorInactive = -1349546097;
        int RX = this.centerX + 120;
        int LX = this.centerX - 200;
        this.drawKeyBind((MCH_EntityAircraft)heli, (MCH_AircraftInfo)info, player, seatID, RX, LX, colorActive, colorInactive);
        if (seatID == 0 && info.isEnableGunnerMode) {
            if (!Keyboard.isKeyDown((int)MCH_Config.KeyFreeLook.prmInt)) {
                c = heli.isHoveringMode() ? colorInactive : colorActive;
                msg = (heli.getIsGunnerMode((Entity)player) ? "Normal" : "Gunner") + " : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeySwitchMode.prmInt);
                this.drawString(msg, RX, this.centerY - 70, c);
            }
        }
        if (seatID > 0 && heli.canSwitchGunnerModeOtherSeat(player)) {
            msg = (heli.getIsGunnerMode((Entity)player) ? "Normal" : "Camera") + " : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeySwitchMode.prmInt);
            this.drawString(msg, RX, this.centerY - 40, colorActive);
        }
        if (seatID == 0) {
            if (!Keyboard.isKeyDown((int)MCH_Config.KeyFreeLook.prmInt)) {
                c = heli.getIsGunnerMode((Entity)player) ? colorInactive : colorActive;
                msg = (heli.getIsGunnerMode((Entity)player) ? "Normal" : "Hovering") + " : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeySwitchHovering.prmInt);
                this.drawString(msg, RX, this.centerY - 60, c);
            }
        }
        if (seatID == 0) {
            if (heli.getTowChainEntity() != null && !heli.getTowChainEntity().field_70128_L) {
                msg = "Drop  : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyExtra.prmInt);
                this.drawString(msg, RX, this.centerY - 30, colorActive);
            } else if (info.isEnableFoldBlade && MCH_Lib.getBlockIdY((World)heli.field_70170_p, (double)heli.field_70165_t, (double)heli.field_70163_u, (double)heli.field_70161_v, (int)1, (int)-2, (boolean)true) > 0 && heli.getCurrentThrottle() <= 0.01) {
                msg = "FoldBlade  : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyExtra.prmInt);
                this.drawString(msg, RX, this.centerY - 30, colorActive);
            }
        }
        if ((heli.getIsGunnerMode((Entity)player) || heli.isUAV()) && info.cameraZoom > 1) {
            msg = "Zoom : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyZoom.prmInt);
            this.drawString(msg, LX, this.centerY - 80, colorActive);
        } else if (seatID == 0 && (heli.canFoldHatch() || heli.canUnfoldHatch())) {
            msg = "OpenHatch : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyZoom.prmInt);
            this.drawString(msg, LX, this.centerY - 80, colorActive);
        }
    }
}

