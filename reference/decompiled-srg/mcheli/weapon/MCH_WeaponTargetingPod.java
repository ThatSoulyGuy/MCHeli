/*
 * Decompiled with CFR 0.152.
 */
package mcheli.weapon;

import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.multiplay.MCH_Multiplay;
import mcheli.weapon.MCH_WeaponBase;
import mcheli.weapon.MCH_WeaponInfo;
import mcheli.weapon.MCH_WeaponParam;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class MCH_WeaponTargetingPod
extends MCH_WeaponBase {
    public MCH_WeaponTargetingPod(World w, Vec3 v, float yaw, float pitch, String nm, MCH_WeaponInfo wi) {
        super(w, v, yaw, pitch, nm, wi);
        this.interval = -90;
        if (w.field_72995_K) {
            this.interval -= 10;
        }
    }

    public boolean shot(MCH_WeaponParam prm) {
        if (!this.worldObj.field_72995_K) {
            MCH_WeaponInfo info = this.getInfo();
            if ((info.target & 0x40) != 0) {
                if (MCH_Multiplay.markPoint((EntityPlayer)((EntityPlayer)prm.user), (double)prm.posX, (double)prm.posY, (double)prm.posZ)) {
                    this.playSound(prm.user);
                } else {
                    this.playSound(prm.user, "ng");
                }
            } else if (MCH_Multiplay.spotEntity((EntityPlayer)((EntityPlayer)prm.user), (MCH_EntityAircraft)((MCH_EntityAircraft)prm.entity), (double)prm.posX, (double)prm.posY, (double)prm.posZ, (int)info.target, (float)info.length, (int)info.markTime, (float)info.angle)) {
                this.playSound(prm.entity);
            } else {
                this.playSound(prm.entity, "ng");
            }
        }
        return true;
    }
}

