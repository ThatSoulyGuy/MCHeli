/*
 * Decompiled with CFR 0.152.
 */
package mcheli.weapon;

import java.util.List;
import mcheli.MCH_Config;
import mcheli.MCH_Lib;
import mcheli.weapon.MCH_BulletModel;
import mcheli.weapon.MCH_DefaultBulletModels;
import mcheli.weapon.MCH_EntityBaseBullet;
import mcheli.wrapper.W_MovingObjectPosition;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class MCH_EntityBullet
extends MCH_EntityBaseBullet {
    public MCH_EntityBullet(World par1World) {
        super(par1World);
    }

    public MCH_EntityBullet(World par1World, double pX, double pY, double pZ, double targetX, double targetY, double targetZ, float yaw, float pitch, double acceleration) {
        super(par1World, pX, pY, pZ, targetX, targetY, targetZ, yaw, pitch, acceleration);
    }

    public void func_70071_h_() {
        float pDist;
        super.func_70071_h_();
        if (!this.field_70128_L && !this.field_70170_p.field_72995_K && this.getCountOnUpdate() > 1 && this.getInfo() != null && this.explosionPower > 0 && (double)(pDist = this.getInfo().proximityFuseDist) > 0.1) {
            float rng = (pDist += 1.0f) + MathHelper.func_76135_e((float)this.getInfo().acceleration);
            List list = this.field_70170_p.func_72839_b((Entity)this, this.field_70121_D.func_72314_b((double)rng, (double)rng, (double)rng));
            for (int i = 0; i < list.size(); ++i) {
                Entity entity1 = (Entity)list.get(i);
                if (!this.canBeCollidedEntity(entity1) || !(entity1.func_70068_e((Entity)this) < (double)(pDist * pDist))) continue;
                MCH_Lib.DbgLog((World)this.field_70170_p, (String)("MCH_EntityBullet.onUpdate:proximityFuse:" + entity1), (Object[])new Object[0]);
                this.field_70165_t = (entity1.field_70165_t + this.field_70165_t) / 2.0;
                this.field_70163_u = (entity1.field_70163_u + this.field_70163_u) / 2.0;
                this.field_70161_v = (entity1.field_70161_v + this.field_70161_v) / 2.0;
                MovingObjectPosition mop = W_MovingObjectPosition.newMOP((int)((int)this.field_70165_t), (int)((int)this.field_70163_u), (int)((int)this.field_70161_v), (int)0, (Vec3)W_WorldFunc.getWorldVec3EntityPos((Entity)this), (boolean)false);
                this.onImpact(mop, 1.0f);
                break;
            }
        }
    }

    protected void onUpdateCollided() {
        Vec3 vec31;
        Vec3 vec3;
        double mx = this.field_70159_w * this.accelerationFactor;
        double my = this.field_70181_x * this.accelerationFactor;
        double mz = this.field_70179_y * this.accelerationFactor;
        float damageFactor = 1.0f;
        MovingObjectPosition m = null;
        for (int i = 0; i < 5; ++i) {
            vec3 = W_WorldFunc.getWorldVec3((World)this.field_70170_p, (double)this.field_70165_t, (double)this.field_70163_u, (double)this.field_70161_v);
            vec31 = W_WorldFunc.getWorldVec3((World)this.field_70170_p, (double)(this.field_70165_t + mx), (double)(this.field_70163_u + my), (double)(this.field_70161_v + mz));
            m = W_WorldFunc.clip((World)this.field_70170_p, (Vec3)vec3, (Vec3)vec31);
            boolean continueClip = false;
            if (this.shootingEntity != null && W_MovingObjectPosition.isHitTypeTile((MovingObjectPosition)m)) {
                Block block = W_WorldFunc.getBlock((World)this.field_70170_p, (int)m.field_72311_b, (int)m.field_72312_c, (int)m.field_72309_d);
                if (MCH_Config.bulletBreakableBlocks.contains(block)) {
                    W_WorldFunc.destroyBlock((World)this.field_70170_p, (int)m.field_72311_b, (int)m.field_72312_c, (int)m.field_72309_d, (boolean)true);
                    continueClip = true;
                }
            }
            if (!continueClip) break;
        }
        vec3 = W_WorldFunc.getWorldVec3((World)this.field_70170_p, (double)this.field_70165_t, (double)this.field_70163_u, (double)this.field_70161_v);
        vec31 = W_WorldFunc.getWorldVec3((World)this.field_70170_p, (double)(this.field_70165_t + mx), (double)(this.field_70163_u + my), (double)(this.field_70161_v + mz));
        if (this.getInfo().delayFuse > 0) {
            if (m != null) {
                this.boundBullet(m.field_72310_e);
                if (this.delayFuse == 0) {
                    this.delayFuse = this.getInfo().delayFuse;
                }
            }
            return;
        }
        if (m != null) {
            vec31 = W_WorldFunc.getWorldVec3((World)this.field_70170_p, (double)m.field_72307_f.field_72450_a, (double)m.field_72307_f.field_72448_b, (double)m.field_72307_f.field_72449_c);
        }
        Entity entity = null;
        List list = this.field_70170_p.func_72839_b((Entity)this, this.field_70121_D.func_72321_a(mx, my, mz).func_72314_b(21.0, 21.0, 21.0));
        double d0 = 0.0;
        for (int j = 0; j < list.size(); ++j) {
            double d1;
            float f;
            AxisAlignedBB axisalignedbb;
            MovingObjectPosition m1;
            Entity entity1 = (Entity)list.get(j);
            if (!this.canBeCollidedEntity(entity1) || (m1 = (axisalignedbb = entity1.field_70121_D.func_72314_b((double)(f = 0.3f), (double)f, (double)f)).func_72327_a(vec3, vec31)) == null || !((d1 = vec3.func_72438_d(m1.field_72307_f)) < d0) && d0 != 0.0) continue;
            entity = entity1;
            d0 = d1;
        }
        if (entity != null) {
            m = new MovingObjectPosition(entity);
        }
        if (m != null) {
            this.onImpact(m, damageFactor);
        }
    }

    public MCH_BulletModel getDefaultBulletModel() {
        return MCH_DefaultBulletModels.Bullet;
    }
}

