/*
 * Decompiled with CFR 0.152.
 */
package mcheli.helicopter;

import mcheli.MCH_Config;
import mcheli.MCH_Key;
import mcheli.MCH_Lib;
import mcheli.MCH_ViewEntityDummy;
import mcheli.aircraft.MCH_AircraftClientTickHandler;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_EntitySeat;
import mcheli.aircraft.MCH_PacketPlayerControlBase;
import mcheli.aircraft.MCH_SeatInfo;
import mcheli.helicopter.MCH_EntityHeli;
import mcheli.helicopter.MCH_HeliPacketPlayerControl;
import mcheli.uav.MCH_EntityUavStation;
import mcheli.wrapper.W_Entity;
import mcheli.wrapper.W_Network;
import mcheli.wrapper.W_PacketBase;
import mcheli.wrapper.W_Reflection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/*
 * Exception performing whole class analysis ignored.
 */
public class MCH_ClientHeliTickHandler
extends MCH_AircraftClientTickHandler {
    public MCH_Key KeySwitchMode;
    public MCH_Key KeySwitchHovering;
    public MCH_Key KeyZoom;
    public MCH_Key[] Keys;

    public MCH_ClientHeliTickHandler(Minecraft minecraft, MCH_Config config) {
        super(minecraft, config);
        this.updateKeybind(config);
    }

    public void updateKeybind(MCH_Config config) {
        super.updateKeybind(config);
        this.KeySwitchMode = new MCH_Key(MCH_Config.KeySwitchMode.prmInt);
        this.KeySwitchHovering = new MCH_Key(MCH_Config.KeySwitchHovering.prmInt);
        this.KeyZoom = new MCH_Key(MCH_Config.KeyZoom.prmInt);
        this.Keys = new MCH_Key[]{this.KeyUp, this.KeyDown, this.KeyRight, this.KeyLeft, this.KeySwitchMode, this.KeySwitchHovering, this.KeyUseWeapon, this.KeySwWeaponMode, this.KeySwitchWeapon1, this.KeySwitchWeapon2, this.KeyZoom, this.KeyCameraMode, this.KeyUnmount, this.KeyUnmountForce, this.KeyFlare, this.KeyExtra, this.KeyFreeLook, this.KeyGUI, this.KeyGearUpDown, this.KeyPutToRack, this.KeyDownFromRack};
    }

    protected void update(EntityPlayer player, MCH_EntityHeli heli, boolean isPilot) {
        MCH_SeatInfo seatInfo;
        if (heli.getIsGunnerMode((Entity)player) && (seatInfo = heli.getSeatInfo((Entity)player)) != null) {
            MCH_ClientHeliTickHandler.setRotLimitPitch((float)seatInfo.minPitch, (float)seatInfo.maxPitch, (Entity)player);
        }
        heli.updateCameraRotate(player.field_70177_z, player.field_70125_A);
        heli.updateRadar(5);
    }

    protected void onTick(boolean inGUI) {
        for (MCH_Key k : this.Keys) {
            k.update();
        }
        this.isBeforeRiding = this.isRiding;
        EntityClientPlayerMP player = this.mc.field_71439_g;
        MCH_EntityHeli heli = null;
        boolean isPilot = true;
        if (player != null) {
            MCH_EntityUavStation uavStation;
            if (player.field_70154_o instanceof MCH_EntityHeli) {
                heli = (MCH_EntityHeli)player.field_70154_o;
            } else if (player.field_70154_o instanceof MCH_EntitySeat) {
                MCH_EntitySeat seat = (MCH_EntitySeat)player.field_70154_o;
                if (seat.getParent() instanceof MCH_EntityHeli) {
                    isPilot = false;
                    heli = (MCH_EntityHeli)seat.getParent();
                }
            } else if (player.field_70154_o instanceof MCH_EntityUavStation && (uavStation = (MCH_EntityUavStation)player.field_70154_o).getControlAircract() instanceof MCH_EntityHeli) {
                heli = (MCH_EntityHeli)uavStation.getControlAircract();
            }
        }
        if (heli != null && heli.getAcInfo() != null) {
            this.update((EntityPlayer)player, heli, isPilot);
            MCH_ViewEntityDummy viewEntityDummy = MCH_ViewEntityDummy.getInstance((World)this.mc.field_71441_e);
            viewEntityDummy.update(heli.camera);
            if (!inGUI) {
                if (!heli.isDestroyed()) {
                    this.playerControl((EntityPlayer)player, heli, isPilot);
                }
            } else {
                this.playerControlInGUI((EntityPlayer)player, heli, isPilot);
            }
            boolean hideHand = true;
            if (isPilot && heli.isAlwaysCameraView() || heli.getIsGunnerMode((Entity)player)) {
                MCH_Lib.setRenderViewEntity((EntityLivingBase)viewEntityDummy);
            } else {
                MCH_Lib.setRenderViewEntity((EntityLivingBase)player);
                if (!isPilot && heli.getCurrentWeaponID((Entity)player) < 0) {
                    hideHand = false;
                }
            }
            if (hideHand) {
                MCH_Lib.disableFirstPersonItemRender((ItemStack)player.func_71045_bC());
            }
            this.isRiding = true;
        } else {
            this.isRiding = false;
        }
        if ((this.isBeforeRiding || !this.isRiding) && this.isBeforeRiding && !this.isRiding) {
            W_Reflection.setCameraRoll((float)0.0f);
            MCH_Lib.enableFirstPersonItemRender();
            MCH_Lib.setRenderViewEntity((EntityLivingBase)player);
        }
    }

    protected void playerControlInGUI(EntityPlayer player, MCH_EntityHeli heli, boolean isPilot) {
        this.commonPlayerControlInGUI(player, (MCH_EntityAircraft)heli, isPilot, (MCH_PacketPlayerControlBase)new MCH_HeliPacketPlayerControl());
    }

    protected void playerControl(EntityPlayer player, MCH_EntityHeli heli, boolean isPilot) {
        MCH_HeliPacketPlayerControl pc = new MCH_HeliPacketPlayerControl();
        boolean send = false;
        send = this.commonPlayerControl(player, (MCH_EntityAircraft)heli, isPilot, (MCH_PacketPlayerControlBase)pc);
        if (isPilot) {
            if (this.KeyExtra.isKeyDown()) {
                if (heli.getTowChainEntity() != null) {
                    MCH_ClientHeliTickHandler.playSoundOK();
                    pc.unhitchChainId = W_Entity.getEntityId((Entity)heli.getTowChainEntity());
                    send = true;
                } else if (heli.canSwitchFoldBlades()) {
                    if (heli.isFoldBlades()) {
                        heli.unfoldBlades();
                        pc.switchFold = 0;
                    } else {
                        heli.foldBlades();
                        pc.switchFold = 1;
                    }
                    send = true;
                    MCH_ClientHeliTickHandler.playSoundOK();
                } else {
                    MCH_ClientHeliTickHandler.playSoundNG();
                }
            }
            if (this.KeySwitchHovering.isKeyDown()) {
                if (heli.canSwitchHoveringMode()) {
                    pc.switchMode = (byte)(heli.isHoveringMode() ? 2 : 3);
                    heli.switchHoveringMode(!heli.isHoveringMode());
                    send = true;
                } else {
                    MCH_ClientHeliTickHandler.playSoundNG();
                }
            } else if (this.KeySwitchMode.isKeyDown()) {
                if (heli.canSwitchGunnerMode()) {
                    pc.switchMode = heli.getIsGunnerMode((Entity)player) ? (byte)0 : 1;
                    heli.switchGunnerMode(!heli.getIsGunnerMode((Entity)player));
                    send = true;
                } else {
                    MCH_ClientHeliTickHandler.playSoundNG();
                }
            }
        } else if (this.KeySwitchMode.isKeyDown()) {
            if (heli.canSwitchGunnerModeOtherSeat(player)) {
                heli.switchGunnerModeOtherSeat(player);
                send = true;
            } else {
                MCH_ClientHeliTickHandler.playSoundNG();
            }
        }
        if (this.KeyZoom.isKeyDown()) {
            boolean isUav;
            boolean bl = isUav = heli.isUAV() && !heli.getAcInfo().haveHatch();
            if (heli.getIsGunnerMode((Entity)player) || isUav) {
                heli.zoomCamera();
                MCH_ClientHeliTickHandler.playSound((String)"zoom", (float)0.5f, (float)1.0f);
            } else if (isPilot && heli.getAcInfo().haveHatch()) {
                if (heli.canFoldHatch()) {
                    pc.switchHatch = (byte)2;
                    send = true;
                } else if (heli.canUnfoldHatch()) {
                    pc.switchHatch = 1;
                    send = true;
                } else {
                    MCH_ClientHeliTickHandler.playSoundNG();
                }
            }
        }
        if (send) {
            W_Network.sendToServer((W_PacketBase)pc);
        }
    }
}

