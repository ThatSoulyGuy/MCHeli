/*
 * Decompiled with CFR 0.152.
 */
package mcheli.weapon;

import mcheli.MCH_Explosion;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.helicopter.MCH_EntityHeli;
import mcheli.weapon.MCH_EntityBomb;
import mcheli.weapon.MCH_WeaponBase;
import mcheli.weapon.MCH_WeaponInfo;
import mcheli.weapon.MCH_WeaponParam;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class MCH_WeaponBomb
extends MCH_WeaponBase {
    public MCH_WeaponBomb(World w, Vec3 v, float yaw, float pitch, String nm, MCH_WeaponInfo wi) {
        super(w, v, yaw, pitch, nm, wi);
        this.acceleration = 0.5f;
        this.explosionPower = 9;
        this.power = 35;
        this.interval = -90;
        if (w.field_72995_K) {
            this.interval -= 10;
        }
    }

    public boolean shot(MCH_WeaponParam prm) {
        if (this.getInfo() != null && this.getInfo().destruct) {
            MCH_EntityAircraft ac;
            if (prm.entity instanceof MCH_EntityHeli && (ac = (MCH_EntityAircraft)prm.entity).isUAV() && ac.getSeatNum() == 0) {
                if (!this.worldObj.field_72995_K) {
                    MCH_Explosion.newExplosion((World)this.worldObj, null, (Entity)prm.user, (double)ac.field_70165_t, (double)ac.field_70163_u, (double)ac.field_70161_v, (float)this.getInfo().explosion, (float)this.getInfo().explosionBlock, (boolean)true, (boolean)true, (boolean)this.getInfo().flaming, (boolean)true, (int)0);
                    this.playSound(prm.entity);
                }
                ac.destruct();
            }
        } else if (!this.worldObj.field_72995_K) {
            this.playSound(prm.entity);
            MCH_EntityBomb e = new MCH_EntityBomb(this.worldObj, prm.posX, prm.posY, prm.posZ, prm.entity.field_70159_w, prm.entity.field_70181_x, prm.entity.field_70179_y, prm.entity.field_70177_z, 0.0f, (double)this.acceleration);
            e.setName(this.name);
            e.setParameterFromWeapon((MCH_WeaponBase)this, prm.entity, prm.user);
            e.field_70159_w = prm.entity.field_70159_w;
            e.field_70181_x = prm.entity.field_70181_x;
            e.field_70179_y = prm.entity.field_70179_y;
            this.worldObj.func_72838_d((Entity)e);
        }
        return true;
    }
}

