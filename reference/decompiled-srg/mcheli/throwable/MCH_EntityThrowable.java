/*
 * Decompiled with CFR 0.152.
 */
package mcheli.throwable;

import mcheli.MCH_Explosion;
import mcheli.MCH_Lib;
import mcheli.particles.MCH_ParticleParam;
import mcheli.particles.MCH_ParticlesUtil;
import mcheli.throwable.MCH_ThrowableInfo;
import mcheli.throwable.MCH_ThrowableInfoManager;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

public class MCH_EntityThrowable
extends EntityThrowable {
    private static final int DATAID_NAME = 31;
    private int countOnUpdate;
    private MCH_ThrowableInfo throwableInfo;
    public double boundPosX;
    public double boundPosY;
    public double boundPosZ;
    public MovingObjectPosition lastOnImpact;
    public int noInfoCount;

    public MCH_EntityThrowable(World par1World) {
        super(par1World);
        this.init();
    }

    public MCH_EntityThrowable(World par1World, EntityLivingBase par2EntityLivingBase, float acceleration) {
        super(par1World, par2EntityLivingBase);
        this.field_70159_w *= (double)acceleration;
        this.field_70181_x *= (double)acceleration;
        this.field_70179_y *= (double)acceleration;
        this.init();
    }

    public MCH_EntityThrowable(World par1World, double par2, double par4, double par6) {
        super(par1World, par2, par4, par6);
        this.init();
    }

    public MCH_EntityThrowable(World p_i1777_1_, double x, double y, double z, float yaw, float pitch) {
        this(p_i1777_1_);
        this.func_70105_a(0.25f, 0.25f);
        this.func_70012_b(x, y, z, yaw, pitch);
        this.field_70165_t -= (double)(MathHelper.func_76134_b((float)(this.field_70177_z / 180.0f * (float)Math.PI)) * 0.16f);
        this.field_70163_u -= (double)0.1f;
        this.field_70161_v -= (double)(MathHelper.func_76126_a((float)(this.field_70177_z / 180.0f * (float)Math.PI)) * 0.16f);
        this.func_70107_b(this.field_70165_t, this.field_70163_u, this.field_70161_v);
        this.field_70129_M = 0.0f;
        float f = 0.4f;
        this.field_70159_w = -MathHelper.func_76126_a((float)(this.field_70177_z / 180.0f * (float)Math.PI)) * MathHelper.func_76134_b((float)(this.field_70125_A / 180.0f * (float)Math.PI)) * f;
        this.field_70179_y = MathHelper.func_76134_b((float)(this.field_70177_z / 180.0f * (float)Math.PI)) * MathHelper.func_76134_b((float)(this.field_70125_A / 180.0f * (float)Math.PI)) * f;
        this.field_70181_x = -MathHelper.func_76126_a((float)((this.field_70125_A + this.func_70183_g()) / 180.0f * (float)Math.PI)) * f;
        this.func_70186_c(this.field_70159_w, this.field_70181_x, this.field_70179_y, this.func_70182_d(), 1.0f);
    }

    public void init() {
        this.lastOnImpact = null;
        this.countOnUpdate = 0;
        this.setInfo(null);
        this.noInfoCount = 0;
        this.func_70096_w().func_75682_a(31, (Object)new String(""));
    }

    public void func_70106_y() {
        String s = this.getInfo() != null ? this.getInfo().name : "null";
        MCH_Lib.DbgLog((World)this.field_70170_p, (String)"MCH_EntityThrowable.setDead(%s)", (Object[])new Object[]{s});
        super.func_70106_y();
    }

    public void func_70071_h_() {
        this.boundPosX = this.field_70165_t;
        this.boundPosY = this.field_70163_u;
        this.boundPosZ = this.field_70161_v;
        if (this.getInfo() != null) {
            Block block = W_WorldFunc.getBlock((World)this.field_70170_p, (int)((int)(this.field_70165_t + 0.5)), (int)((int)this.field_70163_u), (int)((int)(this.field_70161_v + 0.5)));
            Material mat = W_WorldFunc.getBlockMaterial((World)this.field_70170_p, (int)((int)(this.field_70165_t + 0.5)), (int)((int)this.field_70163_u), (int)((int)(this.field_70161_v + 0.5)));
            this.field_70181_x = block != null && mat == Material.field_151586_h ? (this.field_70181_x += (double)this.getInfo().gravityInWater) : (this.field_70181_x += (double)this.getInfo().gravity);
        }
        super.func_70071_h_();
        if (this.lastOnImpact != null) {
            this.boundBullet(this.lastOnImpact);
            this.func_70107_b(this.boundPosX + this.field_70159_w, this.boundPosY + this.field_70181_x, this.boundPosZ + this.field_70179_y);
            this.lastOnImpact = null;
        }
        ++this.countOnUpdate;
        if (this.countOnUpdate >= 0x7FFFFFF0) {
            this.func_70106_y();
            return;
        }
        if (this.getInfo() == null) {
            String s = this.func_70096_w().func_75681_e(31);
            if (!s.isEmpty()) {
                this.setInfo(MCH_ThrowableInfoManager.get((String)s));
            }
            if (this.getInfo() == null) {
                ++this.noInfoCount;
                if (this.noInfoCount > 10) {
                    this.func_70106_y();
                }
                return;
            }
        }
        if (this.field_70128_L) {
            return;
        }
        if (!this.field_70170_p.field_72995_K) {
            if (this.countOnUpdate == this.getInfo().timeFuse && this.getInfo().explosion > 0) {
                MCH_Explosion.newExplosion((World)this.field_70170_p, null, null, (double)this.field_70165_t, (double)this.field_70163_u, (double)this.field_70161_v, (float)this.getInfo().explosion, (float)this.getInfo().explosion, (boolean)true, (boolean)true, (boolean)false, (boolean)true, (int)0);
                this.func_70106_y();
                return;
            }
            if (this.countOnUpdate >= this.getInfo().aliveTime) {
                this.func_70106_y();
                return;
            }
        } else if (this.countOnUpdate >= this.getInfo().timeFuse && this.getInfo().explosion <= 0) {
            for (int i = 0; i < this.getInfo().smokeNum; ++i) {
                float y = this.getInfo().smokeVelocityVertical >= 0.0f ? 0.2f : -0.2f;
                float r = this.getInfo().smokeColor.r * 0.9f + this.field_70146_Z.nextFloat() * 0.1f;
                float g = this.getInfo().smokeColor.g * 0.9f + this.field_70146_Z.nextFloat() * 0.1f;
                float b = this.getInfo().smokeColor.b * 0.9f + this.field_70146_Z.nextFloat() * 0.1f;
                if (this.getInfo().smokeColor.r == this.getInfo().smokeColor.g) {
                    g = r;
                }
                if (this.getInfo().smokeColor.r == this.getInfo().smokeColor.b) {
                    b = r;
                }
                if (this.getInfo().smokeColor.g == this.getInfo().smokeColor.b) {
                    b = g;
                }
                this.spawnParticle("explode", 4, this.getInfo().smokeSize + this.field_70146_Z.nextFloat() * this.getInfo().smokeSize / 3.0f, r, g, b, this.getInfo().smokeVelocityHorizontal * (this.field_70146_Z.nextFloat() - 0.5f), this.getInfo().smokeVelocityVertical * this.field_70146_Z.nextFloat(), this.getInfo().smokeVelocityHorizontal * (this.field_70146_Z.nextFloat() - 0.5f));
            }
        }
    }

    public void spawnParticle(String name, int num, float size, float r, float g, float b, float mx, float my, float mz) {
        if (this.field_70170_p.field_72995_K) {
            if (name.isEmpty() || num < 1) {
                return;
            }
            double x = (this.field_70165_t - this.field_70169_q) / (double)num;
            double y = (this.field_70163_u - this.field_70167_r) / (double)num;
            double z = (this.field_70161_v - this.field_70166_s) / (double)num;
            for (int i = 0; i < num; ++i) {
                MCH_ParticleParam prm = new MCH_ParticleParam(this.field_70170_p, "smoke", this.field_70169_q + x * (double)i, 1.0 + this.field_70167_r + y * (double)i, this.field_70166_s + z * (double)i);
                prm.setMotion((double)mx, (double)my, (double)mz);
                prm.size = size;
                prm.setColor(1.0f, r, g, b);
                prm.isEffectWind = true;
                prm.toWhite = true;
                MCH_ParticlesUtil.spawnParticle((MCH_ParticleParam)prm);
            }
        }
    }

    protected float func_70185_h() {
        return 0.0f;
    }

    public void boundBullet(MovingObjectPosition m) {
        float bound = this.getInfo().bound;
        switch (m.field_72310_e) {
            case 0: 
            case 1: {
                this.field_70159_w *= (double)0.9f;
                this.field_70179_y *= (double)0.9f;
                this.boundPosY = m.field_72307_f.field_72448_b;
                if (m.field_72310_e == 0 && this.field_70181_x > 0.0 || m.field_72310_e == 1 && this.field_70181_x < 0.0) {
                    this.field_70181_x = -this.field_70181_x * (double)bound;
                    break;
                }
                this.field_70181_x = 0.0;
                break;
            }
            case 2: {
                if (!(this.field_70179_y > 0.0)) break;
                this.field_70179_y = -this.field_70179_y * (double)bound;
                break;
            }
            case 3: {
                if (!(this.field_70179_y < 0.0)) break;
                this.field_70179_y = -this.field_70179_y * (double)bound;
                break;
            }
            case 4: {
                if (!(this.field_70159_w > 0.0)) break;
                this.field_70159_w = -this.field_70159_w * (double)bound;
                break;
            }
            case 5: {
                if (!(this.field_70159_w < 0.0)) break;
                this.field_70159_w = -this.field_70159_w * (double)bound;
            }
        }
    }

    protected void func_70184_a(MovingObjectPosition m) {
        if (this.getInfo() != null) {
            this.lastOnImpact = m;
        }
    }

    public MCH_ThrowableInfo getInfo() {
        return this.throwableInfo;
    }

    public void setInfo(MCH_ThrowableInfo info) {
        this.throwableInfo = info;
        if (info != null && !this.field_70170_p.field_72995_K) {
            this.func_70096_w().func_75692_b(31, (Object)new String(info.name));
        }
    }
}

