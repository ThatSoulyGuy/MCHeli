/*
 * Decompiled with CFR 0.152.
 */
package mcheli.weapon;

import mcheli.MCH_Lib;
import mcheli.weapon.MCH_EntityTorpedo;
import mcheli.weapon.MCH_WeaponBase;
import mcheli.weapon.MCH_WeaponInfo;
import mcheli.weapon.MCH_WeaponParam;
import mcheli.wrapper.W_MovingObjectPosition;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class MCH_WeaponTorpedo
extends MCH_WeaponBase {
    public MCH_WeaponTorpedo(World w, Vec3 v, float yaw, float pitch, String nm, MCH_WeaponInfo wi) {
        super(w, v, yaw, pitch, nm, wi);
        this.acceleration = 0.5f;
        this.explosionPower = 8;
        this.power = 35;
        this.interval = -100;
        if (w.field_72995_K) {
            this.interval -= 10;
        }
    }

    public boolean shot(MCH_WeaponParam prm) {
        if (this.getInfo() != null) {
            if (this.getInfo().isGuidedTorpedo) {
                return this.shotGuided(prm);
            }
            return this.shotNoGuided(prm);
        }
        return false;
    }

    protected boolean shotNoGuided(MCH_WeaponParam prm) {
        if (this.worldObj.field_72995_K) {
            return true;
        }
        float yaw = prm.rotYaw;
        float pitch = prm.rotPitch;
        double mx = -MathHelper.func_76126_a((float)(yaw / 180.0f * (float)Math.PI)) * MathHelper.func_76134_b((float)(pitch / 180.0f * (float)Math.PI));
        double mz = MathHelper.func_76134_b((float)(yaw / 180.0f * (float)Math.PI)) * MathHelper.func_76134_b((float)(pitch / 180.0f * (float)Math.PI));
        double my = -MathHelper.func_76126_a((float)(pitch / 180.0f * (float)Math.PI));
        mx = mx * (double)this.getInfo().acceleration + prm.entity.field_70159_w;
        my = my * (double)this.getInfo().acceleration + prm.entity.field_70181_x;
        mz = mz * (double)this.getInfo().acceleration + prm.entity.field_70179_y;
        this.acceleration = MathHelper.func_76133_a((double)(mx * mx + my * my + mz * mz));
        MCH_EntityTorpedo e = new MCH_EntityTorpedo(this.worldObj, prm.posX, prm.posY, prm.posZ, mx, my, mz, yaw, 0.0f, (double)this.acceleration);
        e.setName(this.name);
        e.setParameterFromWeapon((MCH_WeaponBase)this, prm.entity, prm.user);
        e.field_70159_w = mx;
        e.field_70181_x = my;
        e.field_70179_y = mz;
        e.accelerationInWater = this.getInfo() != null ? (double)this.getInfo().accelerationInWater : 1.0;
        this.worldObj.func_72838_d((Entity)e);
        this.playSound(prm.entity);
        return true;
    }

    protected boolean shotGuided(MCH_WeaponParam prm) {
        float yaw = prm.user.field_70177_z;
        float pitch = prm.user.field_70125_A;
        Vec3 v = MCH_Lib.RotVec3((double)0.0, (double)0.0, (double)1.0, (float)(-yaw), (float)(-pitch), (float)(-prm.rotRoll));
        double tX = v.field_72450_a;
        double tZ = v.field_72449_c;
        double tY = v.field_72448_b;
        double dist = MathHelper.func_76133_a((double)(tX * tX + tY * tY + tZ * tZ));
        if (this.worldObj.field_72995_K) {
            tX = tX * 100.0 / dist;
            tY = tY * 100.0 / dist;
            tZ = tZ * 100.0 / dist;
        } else {
            tX = tX * 150.0 / dist;
            tY = tY * 150.0 / dist;
            tZ = tZ * 150.0 / dist;
        }
        Vec3 src = W_WorldFunc.getWorldVec3((World)this.worldObj, (double)prm.user.field_70165_t, (double)prm.user.field_70163_u, (double)prm.user.field_70161_v);
        Vec3 dst = W_WorldFunc.getWorldVec3((World)this.worldObj, (double)(prm.user.field_70165_t + tX), (double)(prm.user.field_70163_u + tY), (double)(prm.user.field_70161_v + tZ));
        MovingObjectPosition m = W_WorldFunc.clip((World)this.worldObj, (Vec3)src, (Vec3)dst);
        if (m != null && W_MovingObjectPosition.isHitTypeTile((MovingObjectPosition)m) && MCH_Lib.isBlockInWater((World)this.worldObj, (int)m.field_72311_b, (int)m.field_72312_c, (int)m.field_72309_d)) {
            if (!this.worldObj.field_72995_K) {
                double mx = -MathHelper.func_76126_a((float)(yaw / 180.0f * (float)Math.PI)) * MathHelper.func_76134_b((float)(pitch / 180.0f * (float)Math.PI));
                double mz = MathHelper.func_76134_b((float)(yaw / 180.0f * (float)Math.PI)) * MathHelper.func_76134_b((float)(pitch / 180.0f * (float)Math.PI));
                double my = -MathHelper.func_76126_a((float)(pitch / 180.0f * (float)Math.PI));
                mx = mx * (double)this.getInfo().acceleration + prm.entity.field_70159_w;
                my = my * (double)this.getInfo().acceleration + prm.entity.field_70181_x;
                mz = mz * (double)this.getInfo().acceleration + prm.entity.field_70179_y;
                this.acceleration = MathHelper.func_76133_a((double)(mx * mx + my * my + mz * mz));
                MCH_EntityTorpedo e = new MCH_EntityTorpedo(this.worldObj, prm.posX, prm.posY, prm.posZ, prm.entity.field_70159_w, prm.entity.field_70181_x, prm.entity.field_70179_y, yaw, 0.0f, (double)this.acceleration);
                e.setName(this.name);
                e.setParameterFromWeapon((MCH_WeaponBase)this, prm.entity, prm.user);
                e.targetPosX = m.field_72307_f.field_72450_a;
                e.targetPosY = m.field_72307_f.field_72448_b;
                e.targetPosZ = m.field_72307_f.field_72449_c;
                e.field_70159_w = mx;
                e.field_70181_x = my;
                e.field_70179_y = mz;
                e.accelerationInWater = this.getInfo() != null ? (double)this.getInfo().accelerationInWater : 1.0;
                this.worldObj.func_72838_d((Entity)e);
                this.playSound(prm.entity);
            }
            return true;
        }
        return false;
    }
}

