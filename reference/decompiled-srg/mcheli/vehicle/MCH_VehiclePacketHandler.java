/*
 * Decompiled with CFR 0.152.
 */
package mcheli.vehicle;

import com.google.common.io.ByteArrayDataInput;
import mcheli.chain.MCH_EntityChain;
import mcheli.vehicle.MCH_EntityVehicle;
import mcheli.vehicle.MCH_PacketVehiclePlayerControl;
import mcheli.weapon.MCH_WeaponParam;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

public class MCH_VehiclePacketHandler {
    public static void onPacket_PlayerControl(EntityPlayer player, ByteArrayDataInput data) {
        if (!(player.field_70154_o instanceof MCH_EntityVehicle)) {
            return;
        }
        if (player.field_70170_p.field_72995_K) {
            return;
        }
        MCH_PacketVehiclePlayerControl pc = new MCH_PacketVehiclePlayerControl();
        pc.readData(data);
        MCH_EntityVehicle vehicle = (MCH_EntityVehicle)player.field_70154_o;
        if (pc.isUnmount == 1) {
            vehicle.unmountEntity();
        } else if (pc.isUnmount == 2) {
            vehicle.unmountCrew();
        } else {
            Entity e;
            if (pc.switchSearchLight) {
                vehicle.setSearchLight(!vehicle.isSearchLightON());
            }
            if (pc.switchCameraMode > 0) {
                vehicle.switchCameraMode(player, pc.switchCameraMode - 1);
            }
            if (pc.switchWeapon >= 0) {
                vehicle.switchWeapon((Entity)player, (int)pc.switchWeapon);
            }
            if (pc.useWeapon) {
                MCH_WeaponParam prm = new MCH_WeaponParam();
                prm.entity = vehicle;
                prm.user = player;
                prm.setPosAndRot(pc.useWeaponPosX, pc.useWeaponPosY, pc.useWeaponPosZ, 0.0f, 0.0f);
                prm.option1 = pc.useWeaponOption1;
                prm.option2 = pc.useWeaponOption2;
                vehicle.useCurrentWeapon(prm);
            }
            if (vehicle.isPilot((Entity)player)) {
                vehicle.throttleUp = pc.throttleUp;
                vehicle.throttleDown = pc.throttleDown;
                vehicle.moveLeft = pc.moveLeft;
                vehicle.moveRight = pc.moveRight;
            }
            if (pc.useFlareType > 0) {
                vehicle.useFlare((int)pc.useFlareType);
            }
            if (pc.unhitchChainId >= 0 && (e = player.field_70170_p.func_73045_a(pc.unhitchChainId)) instanceof MCH_EntityChain) {
                e.func_70106_y();
            }
            if (pc.openGui) {
                vehicle.openGui(player);
            }
            if (pc.switchHatch > 0) {
                vehicle.foldHatch(pc.switchHatch == 2);
            }
            if (pc.isUnmount == 3) {
                vehicle.unmountAircraft();
            }
        }
    }
}

