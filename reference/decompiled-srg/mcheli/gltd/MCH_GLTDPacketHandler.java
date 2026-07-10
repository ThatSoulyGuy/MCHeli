/*
 * Decompiled with CFR 0.152.
 */
package mcheli.gltd;

import com.google.common.io.ByteArrayDataInput;
import mcheli.gltd.MCH_EntityGLTD;
import mcheli.gltd.MCH_PacketGLTDPlayerControl;
import net.minecraft.entity.player.EntityPlayer;

public class MCH_GLTDPacketHandler {
    public static void onPacket_GLTDPlayerControl(EntityPlayer player, ByteArrayDataInput data) {
        if (!(player.field_70154_o instanceof MCH_EntityGLTD)) {
            return;
        }
        if (player.field_70170_p.field_72995_K) {
            return;
        }
        MCH_PacketGLTDPlayerControl pc = new MCH_PacketGLTDPlayerControl();
        pc.readData(data);
        MCH_EntityGLTD gltd = (MCH_EntityGLTD)player.field_70154_o;
        if (pc.unmount) {
            if (gltd.field_70153_n != null) {
                gltd.field_70153_n.func_70078_a(null);
            }
        } else {
            if (pc.switchCameraMode >= 0) {
                gltd.camera.setMode(0, (int)pc.switchCameraMode);
            }
            if (pc.switchWeapon >= 0) {
                gltd.switchWeapon((int)pc.switchWeapon);
            }
            if (pc.useWeapon) {
                gltd.useCurrentWeapon(pc.useWeaponOption1, pc.useWeaponOption2);
            }
        }
    }
}

