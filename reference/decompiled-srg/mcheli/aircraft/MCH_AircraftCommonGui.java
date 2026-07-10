/*
 * Decompiled with CFR 0.152.
 */
package mcheli.aircraft;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.MCH_Config;
import mcheli.MCH_KeyName;
import mcheli.MCH_Lib;
import mcheli.aircraft.MCH_AircraftInfo;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.gui.MCH_Gui;
import mcheli.hud.MCH_Hud;
import mcheli.weapon.MCH_EntityTvMissile;
import mcheli.weapon.MCH_WeaponSet;
import mcheli.wrapper.W_McClient;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

@SideOnly(value=Side.CLIENT)
public abstract class MCH_AircraftCommonGui
extends MCH_Gui {
    public MCH_AircraftCommonGui(Minecraft minecraft) {
        super(minecraft);
    }

    public void drawHud(MCH_EntityAircraft ac, EntityPlayer player, int seatId) {
        MCH_AircraftInfo info = ac.getAcInfo();
        if (info == null) {
            return;
        }
        if (ac.isMissileCameraMode((Entity)player) && ac.getTVMissile() != null && info.hudTvMissile != null) {
            info.hudTvMissile.draw(ac, player, this.smoothCamPartialTicks);
        } else {
            MCH_Hud hud;
            if (seatId < 0) {
                return;
            }
            if (seatId < info.hudList.size() && (hud = (MCH_Hud)info.hudList.get(seatId)) != null) {
                hud.draw(ac, player, this.smoothCamPartialTicks);
            }
        }
    }

    public void drawDebugtInfo(MCH_EntityAircraft ac) {
        if (MCH_Config.DebugLog) {
            int n = this.centerX - 100;
        }
    }

    public void drawNightVisionNoise() {
        GL11.glEnable((int)3042);
        GL11.glColor4f((float)0.0f, (float)1.0f, (float)0.0f, (float)0.3f);
        int srcBlend = GL11.glGetInteger((int)3041);
        int dstBlend = GL11.glGetInteger((int)3040);
        GL11.glBlendFunc((int)1, (int)1);
        W_McClient.MOD_bindTexture((String)"textures/gui/alpha.png");
        this.drawTexturedModalRectRotate(0.0, 0.0, (double)this.field_146294_l, (double)this.field_146295_m, (double)this.rand.nextInt(256), (double)this.rand.nextInt(256), 256.0, 256.0, 0.0f);
        GL11.glBlendFunc((int)srcBlend, (int)dstBlend);
        GL11.glDisable((int)3042);
    }

    public void drawHitBullet(int hs, int hsMax, int color) {
        if (hs > 0) {
            int cx = this.centerX;
            int cy = this.centerY;
            int IVX = 10;
            int IVY = 10;
            int SZX = 5;
            int SZY = 5;
            double[] ls = new double[]{cx - IVX, cy - IVY, cx - SZX, cy - SZY, cx - IVX, cy + IVY, cx - SZX, cy + SZY, cx + IVX, cy - IVY, cx + SZX, cy - SZY, cx + IVX, cy + IVY, cx + SZX, cy + SZY};
            color = MCH_Config.hitMarkColorRGB;
            int alpha = hs * (256 / hsMax);
            this.drawLine(ls, color |= (int)(MCH_Config.hitMarkColorAlpha * (float)alpha) << 24);
        }
    }

    public void drawHitBullet(MCH_EntityAircraft ac, int color, int seatID) {
        this.drawHitBullet(ac.getHitStatus(), ac.getMaxHitStatus(), color);
    }

    protected void drawTvMissileNoise(MCH_EntityAircraft ac, MCH_EntityTvMissile tvmissile) {
        GL11.glEnable((int)3042);
        GL11.glColor4f((float)0.5f, (float)0.5f, (float)0.5f, (float)0.4f);
        int srcBlend = GL11.glGetInteger((int)3041);
        int dstBlend = GL11.glGetInteger((int)3040);
        GL11.glBlendFunc((int)1, (int)1);
        W_McClient.MOD_bindTexture((String)"textures/gui/noise.png");
        this.drawTexturedModalRectRotate(0.0, 0.0, (double)this.field_146294_l, (double)this.field_146295_m, (double)this.rand.nextInt(256), (double)this.rand.nextInt(256), 256.0, 256.0, 0.0f);
        GL11.glBlendFunc((int)srcBlend, (int)dstBlend);
        GL11.glDisable((int)3042);
    }

    public void drawKeyBind(MCH_EntityAircraft ac, MCH_AircraftInfo info, EntityPlayer player, int seatID, int RX, int LX, int colorActive, int colorInactive) {
        int c;
        String msg;
        block24: {
            block23: {
                msg = "";
                c = 0;
                if (seatID == 0 && ac.canPutToRack()) {
                    msg = "PutRack : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyPutToRack.prmInt);
                    this.drawString(msg, LX, this.centerY - 10, colorActive);
                }
                if (seatID == 0 && ac.canDownFromRack()) {
                    msg = "DownRack : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyDownFromRack.prmInt);
                    this.drawString(msg, LX, this.centerY - 0, colorActive);
                }
                if (seatID == 0 && ac.canRideRack()) {
                    msg = "RideRack : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyPutToRack.prmInt);
                    this.drawString(msg, LX, this.centerY + 10, colorActive);
                }
                if (seatID == 0 && ac.field_70154_o != null) {
                    msg = "DismountRack : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyDownFromRack.prmInt);
                    this.drawString(msg, LX, this.centerY + 10, colorActive);
                }
                if (seatID > 0 && ac.getSeatNum() > 1) break block23;
                if (!Keyboard.isKeyDown((int)MCH_Config.KeyFreeLook.prmInt)) break block24;
            }
            int n = c = seatID == 0 ? -208 : colorActive;
            String sk = seatID == 0 ? MCH_KeyName.getDescOrName((int)MCH_Config.KeyFreeLook.prmInt) + " + " : "";
            msg = "NextSeat : " + sk + MCH_KeyName.getDescOrName((int)MCH_Config.KeyGUI.prmInt);
            this.drawString(msg, RX, this.centerY - 70, c);
            msg = "PrevSeat : " + sk + MCH_KeyName.getDescOrName((int)MCH_Config.KeyExtra.prmInt);
            this.drawString(msg, RX, this.centerY - 60, c);
        }
        if (seatID >= 0 && seatID <= 1 && ac.haveFlare()) {
            c = ac.isFlarePreparation() ? colorInactive : colorActive;
            msg = "Flare : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyFlare.prmInt);
            this.drawString(msg, RX, this.centerY - 50, c);
        }
        if (seatID == 0 && info.haveLandingGear()) {
            if (ac.canFoldLandingGear()) {
                msg = "Gear Up : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyGearUpDown.prmInt);
                this.drawString(msg, RX, this.centerY - 40, colorActive);
            } else if (ac.canUnfoldLandingGear()) {
                msg = "Gear Down : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyGearUpDown.prmInt);
                this.drawString(msg, RX, this.centerY - 40, colorActive);
            }
        }
        MCH_WeaponSet ws = ac.getCurrentWeapon((Entity)player);
        if (ac.getWeaponNum() > 1) {
            msg = "Weapon : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeySwitchWeapon2.prmInt);
            this.drawString(msg, LX, this.centerY - 70, colorActive);
        }
        if (ws.getCurrentWeapon().numMode > 0) {
            msg = "WeaponMode : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeySwWeaponMode.prmInt);
            this.drawString(msg, LX, this.centerY - 60, colorActive);
        }
        if (ac.canSwitchSearchLight((Entity)player)) {
            msg = "SearchLight : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyCameraMode.prmInt);
            this.drawString(msg, LX, this.centerY - 50, colorActive);
        } else if (ac.canSwitchCameraMode(seatID)) {
            msg = "CameraMode : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyCameraMode.prmInt);
            this.drawString(msg, LX, this.centerY - 50, colorActive);
        }
        if (seatID == 0 && ac.getSeatNum() >= 1) {
            int color = colorActive;
            if (info.isEnableParachuting && MCH_Lib.getBlockIdY((Entity)ac, (int)3, (int)-10) == 0) {
                msg = "Parachuting : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyUnmount.prmInt);
            } else if (ac.canStartRepelling()) {
                msg = "Repelling : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyUnmount.prmInt);
                color = -256;
            } else {
                msg = "Dismount : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyUnmount.prmInt);
            }
            this.drawString(msg, LX, this.centerY - 30, color);
        }
        if (seatID == 0 && ac.canSwitchFreeLook() || seatID > 0 && ac.canSwitchGunnerModeOtherSeat(player)) {
            msg = "FreeLook : " + MCH_KeyName.getDescOrName((int)MCH_Config.KeyFreeLook.prmInt);
            this.drawString(msg, LX, this.centerY - 20, colorActive);
        }
    }
}

