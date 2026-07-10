/*
 * Decompiled with CFR 0.152.
 */
package mcheli.helicopter;

import com.google.common.io.ByteArrayDataInput;
import mcheli.MCH_Achievement;
import mcheli.MCH_Lib;
import mcheli.aircraft.MCH_EntitySeat;
import mcheli.chain.MCH_EntityChain;
import mcheli.container.MCH_EntityContainer;
import mcheli.helicopter.MCH_EntityHeli;
import mcheli.helicopter.MCH_HeliPacketPlayerControl;
import mcheli.uav.MCH_EntityUavStation;
import mcheli.weapon.MCH_WeaponParam;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.stats.Achievement;

public class MCH_HeliPacketHandler {
    public static void onPacket_PlayerControl(EntityPlayer player, ByteArrayDataInput data) {
        MCH_EntityUavStation uavStation;
        if (player.field_70170_p.field_72995_K) {
            return;
        }
        MCH_HeliPacketPlayerControl pc = new MCH_HeliPacketPlayerControl();
        pc.readData(data);
        MCH_EntityHeli heli = null;
        if (player.field_70154_o instanceof MCH_EntityHeli) {
            heli = (MCH_EntityHeli)player.field_70154_o;
        } else if (player.field_70154_o instanceof MCH_EntitySeat) {
            if (((MCH_EntitySeat)player.field_70154_o).getParent() instanceof MCH_EntityHeli) {
                heli = (MCH_EntityHeli)((MCH_EntitySeat)player.field_70154_o).getParent();
            }
        } else if (player.field_70154_o instanceof MCH_EntityUavStation && (uavStation = (MCH_EntityUavStation)player.field_70154_o).getControlAircract() instanceof MCH_EntityHeli) {
            heli = (MCH_EntityHeli)uavStation.getControlAircract();
        }
        if (heli == null) {
            return;
        }
        MCH_EntityHeli ac = heli;
        if (pc.isUnmount == 1) {
            ac.unmountEntity();
        } else if (pc.isUnmount == 2) {
            ac.unmountCrew();
        } else {
            Entity e;
            if (pc.switchFold == 0) {
                heli.setFoldBladeStat((byte)3);
            }
            if (pc.switchFold == 1) {
                heli.setFoldBladeStat((byte)1);
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
                prm.entity = ac;
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
            if (pc.unhitchChainId >= 0 && (e = player.field_70170_p.func_73045_a(pc.unhitchChainId)) instanceof MCH_EntityChain) {
                if (((MCH_EntityChain)e).towedEntity instanceof MCH_EntityContainer && MCH_Lib.getBlockIdY((Entity)heli, (int)3, (int)-20) == 0) {
                    MCH_Achievement.addStat((Entity)player, (Achievement)MCH_Achievement.reliefSupplies, (int)1);
                }
                e.func_70106_y();
            }
            if (pc.openGui) {
                ac.openGui(player);
            }
            if (pc.switchHatch > 0) {
                ac.foldHatch(pc.switchHatch == 2);
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

