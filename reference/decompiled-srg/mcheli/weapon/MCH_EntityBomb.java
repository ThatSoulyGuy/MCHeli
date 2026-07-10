/*
 * Decompiled with CFR 0.152.
 */
package mcheli.weapon;

import java.util.List;
import mcheli.weapon.MCH_BulletModel;
import mcheli.weapon.MCH_DefaultBulletModels;
import mcheli.weapon.MCH_EntityBaseBullet;
import mcheli.wrapper.W_Lib;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class MCH_EntityBomb
extends MCH_EntityBaseBullet {
    public MCH_EntityBomb(World par1World) {
        super(par1World);
    }

    public MCH_EntityBomb(World par1World, double posX, double posY, double posZ, double targetX, double targetY, double targetZ, float yaw, float pitch, double acceleration) {
        super(par1World, posX, posY, posZ, targetX, targetY, targetZ, yaw, pitch, acceleration);
    }

    public void func_70071_h_() {
        super.func_70071_h_();
        if (!this.field_70170_p.field_72995_K && this.getInfo() != null) {
            List list;
            float dist;
            this.field_70159_w *= 0.999;
            this.field_70179_y *= 0.999;
            if (this.func_70090_H()) {
                this.field_70159_w *= (double)this.getInfo().velocityInWater;
                this.field_70181_x *= (double)this.getInfo().velocityInWater;
                this.field_70179_y *= (double)this.getInfo().velocityInWater;
            }
            if ((dist = this.getInfo().proximityFuseDist) > 0.1f && this.getCountOnUpdate() % 10 == 0 && (list = this.field_70170_p.func_72839_b((Entity)this, this.field_70121_D.func_72314_b((double)dist, (double)dist, (double)dist))) != null) {
                for (int i = 0; i < list.size(); ++i) {
                    Entity entity = (Entity)list.get(i);
                    if (!W_Lib.isEntityLivingBase((Entity)entity) || !this.canBeCollidedEntity(entity)) continue;
                    MovingObjectPosition m = new MovingObjectPosition((int)(this.field_70165_t + 0.5), (int)(this.field_70163_u + 0.5), (int)(this.field_70161_v + 0.5), 0, Vec3.func_72443_a((double)this.field_70165_t, (double)this.field_70163_u, (double)this.field_70161_v));
                    this.onImpact(m, 1.0f);
                    break;
                }
            }
        }
        this.onUpdateBomblet();
    }

    public void sprinkleBomblet() {
        if (!this.field_70170_p.field_72995_K) {
            MCH_EntityBomb e = new MCH_EntityBomb(this.field_70170_p, this.field_70165_t, this.field_70163_u, this.field_70161_v, this.field_70159_w, this.field_70181_x, this.field_70179_y, (float)this.field_70146_Z.nextInt(360), 0.0f, this.acceleration);
            e.setParameterFromWeapon((MCH_EntityBaseBullet)this, this.shootingAircraft, this.shootingEntity);
            e.setName(this.getName());
            float MOTION = 1.0f;
            float RANDOM = this.getInfo().bombletDiff;
            e.field_70159_w = this.field_70159_w * 1.0 + (double)((this.field_70146_Z.nextFloat() - 0.5f) * RANDOM);
            e.field_70181_x = this.field_70181_x * 1.0 / 2.0 + (double)((this.field_70146_Z.nextFloat() - 0.5f) * RANDOM / 2.0f);
            e.field_70179_y = this.field_70179_y * 1.0 + (double)((this.field_70146_Z.nextFloat() - 0.5f) * RANDOM);
            e.setBomblet();
            this.field_70170_p.func_72838_d((Entity)e);
        }
    }

    public MCH_BulletModel getDefaultBulletModel() {
        return MCH_DefaultBulletModels.Bomb;
    }
}

