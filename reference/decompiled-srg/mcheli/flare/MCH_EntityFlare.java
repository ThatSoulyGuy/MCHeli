/*
 * Decompiled with CFR 0.152.
 */
package mcheli.flare;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import mcheli.particles.MCH_ParticleParam;
import mcheli.particles.MCH_ParticlesUtil;
import mcheli.wrapper.W_Entity;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class MCH_EntityFlare
extends W_Entity
implements IEntityAdditionalSpawnData {
    public double gravity = -0.013;
    public double airResistance = 0.992;
    public float size;
    public int fuseCount;

    public MCH_EntityFlare(World par1World) {
        super(par1World);
        this.func_70105_a(1.0f, 1.0f);
        this.field_70126_B = this.field_70177_z;
        this.field_70127_C = this.field_70125_A;
        this.size = 6.0f;
        this.fuseCount = 0;
    }

    public MCH_EntityFlare(World par1World, double pX, double pY, double pZ, double mX, double mY, double mZ, float size, int fuseCount) {
        this(par1World);
        this.func_70012_b(pX, pY, pZ, 0.0f, 0.0f);
        this.field_70129_M = 0.0f;
        this.field_70159_w = mX;
        this.field_70181_x = mY;
        this.field_70179_y = mZ;
        this.size = size;
        this.fuseCount = fuseCount;
    }

    public boolean func_85032_ar() {
        return true;
    }

    @SideOnly(value=Side.CLIENT)
    public boolean func_70112_a(double par1) {
        double d1 = this.field_70121_D.func_72320_b() * 4.0;
        return par1 < (d1 *= 64.0) * d1;
    }

    public void func_70106_y() {
        super.func_70106_y();
        if (this.fuseCount > 0 && this.field_70170_p.field_72995_K) {
            this.fuseCount = 0;
            int num = 20;
            for (int i = 0; i < 20; ++i) {
                double x = (this.field_70146_Z.nextDouble() - 0.5) * 10.0;
                double y = (this.field_70146_Z.nextDouble() - 0.5) * 10.0;
                double z = (this.field_70146_Z.nextDouble() - 0.5) * 10.0;
                MCH_ParticleParam prm = new MCH_ParticleParam(this.field_70170_p, "smoke", this.field_70165_t + x, this.field_70163_u + y, this.field_70161_v + z);
                prm.age = 200 + this.field_70146_Z.nextInt(100);
                prm.size = 20 + this.field_70146_Z.nextInt(25);
                prm.motionX = (this.field_70146_Z.nextDouble() - 0.5) * 0.45;
                prm.motionY = (this.field_70146_Z.nextDouble() - 0.5) * 0.01;
                prm.motionZ = (this.field_70146_Z.nextDouble() - 0.5) * 0.45;
                prm.a = this.field_70146_Z.nextFloat() * 0.1f + 0.85f;
                prm.b = this.field_70146_Z.nextFloat() * 0.2f + 0.5f;
                prm.g = prm.b + 0.05f;
                prm.r = prm.b + 0.1f;
                MCH_ParticlesUtil.spawnParticle((MCH_ParticleParam)prm);
            }
        }
    }

    public void writeSpawnData(ByteBuf buffer) {
        try {
            buffer.writeByte(this.fuseCount);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void readSpawnData(ByteBuf additionalData) {
        try {
            this.fuseCount = additionalData.readByte();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void func_70071_h_() {
        if (this.fuseCount > 0 && this.field_70173_aa >= this.fuseCount) {
            this.func_70106_y();
        } else if (!this.field_70170_p.field_72995_K && !this.field_70170_p.func_72899_e((int)this.field_70165_t, (int)this.field_70163_u, (int)this.field_70161_v)) {
            this.func_70106_y();
        } else if (this.field_70173_aa > 300 && !this.field_70170_p.field_72995_K) {
            this.func_70106_y();
        } else {
            super.func_70071_h_();
            if (!this.field_70170_p.field_72995_K) {
                this.onUpdateCollided();
            }
            this.field_70165_t += this.field_70159_w;
            this.field_70163_u += this.field_70181_x;
            this.field_70161_v += this.field_70179_y;
            if (this.field_70170_p.field_72995_K) {
                int num = 2;
                double x = (this.field_70165_t - this.field_70169_q) / 2.0;
                double y = (this.field_70163_u - this.field_70167_r) / 2.0;
                double z = (this.field_70161_v - this.field_70166_s) / 2.0;
                for (int i = 0; i < 2; ++i) {
                    MCH_ParticleParam prm = new MCH_ParticleParam(this.field_70170_p, "smoke", this.field_70169_q + x * (double)i, this.field_70167_r + y * (double)i, this.field_70166_s + z * (double)i);
                    prm.size = 6.0f + this.field_70146_Z.nextFloat();
                    if (this.size < 5.0f) {
                        prm.a = (float)((double)prm.a * 0.75);
                        if (this.field_70146_Z.nextInt(2) == 0) continue;
                    }
                    if (this.fuseCount > 0) {
                        prm.a = this.field_70146_Z.nextFloat() * 0.1f + 0.85f;
                        prm.b = this.field_70146_Z.nextFloat() * 0.1f + 0.5f;
                        prm.g = prm.b + 0.05f;
                        prm.r = prm.b + 0.1f;
                    }
                    MCH_ParticlesUtil.spawnParticle((MCH_ParticleParam)prm);
                }
            }
            this.field_70181_x += this.gravity;
            this.field_70159_w *= this.airResistance;
            this.field_70179_y *= this.airResistance;
            if (this.func_70090_H() && !this.field_70170_p.field_72995_K) {
                this.func_70106_y();
            }
            if (this.field_70122_E && !this.field_70170_p.field_72995_K) {
                this.func_70106_y();
            }
            this.func_70107_b(this.field_70165_t, this.field_70163_u, this.field_70161_v);
        }
    }

    protected void onUpdateCollided() {
        Vec3 vec3 = W_WorldFunc.getWorldVec3((World)this.field_70170_p, (double)this.field_70165_t, (double)this.field_70163_u, (double)this.field_70161_v);
        Vec3 vec31 = W_WorldFunc.getWorldVec3((World)this.field_70170_p, (double)(this.field_70165_t + this.field_70159_w), (double)(this.field_70163_u + this.field_70181_x), (double)(this.field_70161_v + this.field_70179_y));
        MovingObjectPosition mop = W_WorldFunc.clip((World)this.field_70170_p, (Vec3)vec3, (Vec3)vec31);
        vec3 = W_WorldFunc.getWorldVec3((World)this.field_70170_p, (double)this.field_70165_t, (double)this.field_70163_u, (double)this.field_70161_v);
        vec31 = W_WorldFunc.getWorldVec3((World)this.field_70170_p, (double)(this.field_70165_t + this.field_70159_w), (double)(this.field_70163_u + this.field_70181_x), (double)(this.field_70161_v + this.field_70179_y));
        if (mop != null) {
            vec31 = W_WorldFunc.getWorldVec3((World)this.field_70170_p, (double)mop.field_72307_f.field_72450_a, (double)mop.field_72307_f.field_72448_b, (double)mop.field_72307_f.field_72449_c);
            this.onImpact(mop);
        }
    }

    protected void onImpact(MovingObjectPosition par1MovingObjectPosition) {
        if (!this.field_70170_p.field_72995_K) {
            this.func_70106_y();
        }
    }

    public void func_70014_b(NBTTagCompound par1NBTTagCompound) {
        par1NBTTagCompound.func_74782_a("direction", (NBTBase)this.func_70087_a(new double[]{this.field_70159_w, this.field_70181_x, this.field_70179_y}));
    }

    public void func_70037_a(NBTTagCompound par1NBTTagCompound) {
        this.func_70106_y();
    }

    public boolean func_70067_L() {
        return true;
    }

    public float func_70111_Y() {
        return 1.0f;
    }

    public boolean func_70097_a(DamageSource par1DamageSource, float par2) {
        return false;
    }

    @SideOnly(value=Side.CLIENT)
    public float func_70053_R() {
        return 0.0f;
    }
}

