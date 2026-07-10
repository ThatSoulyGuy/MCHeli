/*
 * Decompiled with CFR 0.152.
 */
package mcheli.plane;

import com.google.common.io.ByteArrayDataInput;
import mcheli.aircraft.MCH_EntitySeat;
import mcheli.plane.MCP_EntityPlane;
import mcheli.plane.MCP_PlanePacketPlayerControl;
import mcheli.uav.MCH_EntityUavStation;
import mcheli.weapon.MCH_WeaponParam;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

public class MCP_PlanePacketHandler {
    public static void onPacket_PlayerControl(EntityPlayer player, ByteArrayDataInput data) {
        MCH_EntityUavStation uavStation;
        if (player.field_70170_p.field_72995_K) {
            return;
        }
        MCP_PlanePacketPlayerControl pc = new MCP_PlanePacketPlayerControl();
        pc.readData(data);
        boolean isPilot = true;
        MCP_EntityPlane plane = null;
        if (player.field_70154_o instanceof MCP_EntityPlane) {
            plane = (MCP_EntityPlane)player.field_70154_o;
        } else if (player.field_70154_o instanceof MCH_EntitySeat) {
            if (((MCH_EntitySeat)player.field_70154_o).getParent() instanceof MCP_EntityPlane) {
                plane = (MCP_EntityPlane)((MCH_EntitySeat)player.field_70154_o).getParent();
            }
        } else if (player.field_70154_o instanceof MCH_EntityUavStation && (uavStation = (MCH_EntityUavStation)player.field_70154_o).getControlAircract() instanceof MCP_EntityPlane) {
            plane = (MCP_EntityPlane)uavStation.getControlAircract();
        }
        if (plane == null) {
            return;
        }
        MCP_EntityPlane ac = plane;
        if (pc.isUnmount == 1) {
            ac.unmountEntity();
        } else if (pc.isUnmount == 2) {
            ac.unmountCrew();
        } else if (pc.ejectSeat) {
            ac.ejectSeat((Entity)player);
        } else {
            if (pc.switchVtol == 0) {
                plane.swithVtolMode(false);
            }
            if (pc.switchVtol == 1) {
                plane.swithVtolMode(true);
            }
            if (pc.switchMode == 0) {
                ac.switchGunnerMode(false);
            }
            if (pc.switchMode == 1) {
                ac.switchGunnerMode(true);
            }
            if (pc.switchMode == 2) {
                ac.switchHoveringMode(false);
            }
            if (pc.switchMode == 3) {
                ac.switchHoveringMode(true);
            }
            if (pc.switchSearchLight) {
                ac.setSearchLight(!ac.isSearchLightON());
            }
            if (pc.switchCameraMode > 0) {
                ac.switchCameraMode(player, pc.switchCameraMode - 1);
            }
            if (pc.switchWeapon >= 0) {
                ac.switchWeapon((Entity)player, (int)pc.switchWeapon);
            }
            if (pc.useWeapon) {
                MCH_WeaponParam prm = new MCH_WeaponParam();
                prm.entity = plane;
                prm.user = player;
                prm.setPosAndRot(pc.useWeaponPosX, pc.useWeaponPosY, pc.useWeaponPosZ, 0.0f, 0.0f);
                prm.option1 = pc.useWeaponOption1;
                prm.option2 = pc.useWeaponOption2;
                ac.useCurrentWeapon(prm);
            }
            if (ac.isPilot((Entity)player)) {
                ac.throttleUp = pc.throttleUp;
                ac.throttleDown = pc.throttleDown;
                ac.moveLeft = pc.moveLeft;
                ac.moveRight = pc.moveRight;
            }
            if (pc.useFlareType > 0) {
                ac.useFlare((int)pc.useFlareType);
            }
            if (pc.openGui) {
                ac.openGui(player);
            }
            if (pc.switchHatch > 0) {
                if (ac.getAcInfo().haveHatch()) {
                    ac.foldHatch(pc.switchHatch == 2);
                } else {
                    plane.foldWing(pc.switchHatch == 2);
                }
            }
            if (pc.switchFreeLook > 0) {
                ac.switchFreeLookMode(pc.switchFreeLook == 1);
            }
            if (pc.switchGear == 1) {
                ac.foldLandingGear();
            }
            if (pc.switchGear == 2) {
                ac.unfoldLandingGear();
            }
            if (pc.putDownRack == 1) {
                ac.mountEntityToRack();
            }
            if (pc.putDownRack == 2) {
                ac.unmountEntityFromRack();
            }
            if (pc.putDownRack == 3) {
                ac.rideRack();
            }
            if (pc.isUnmount == 3) {
                ac.unmountAircraft();
            }
        }
    }
}

