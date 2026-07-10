/*
 * Decompiled with CFR 0.152.
 */
package mcheli.weapon;

import mcheli.MCH_Config;
import mcheli.particles.MCH_ParticleParam;
import mcheli.particles.MCH_ParticlesUtil;
import mcheli.weapon.MCH_BulletModel;
import mcheli.weapon.MCH_DefaultBulletModels;
import mcheli.weapon.MCH_EntityBaseBullet;
import mcheli.weapon.MCH_EntityBomb;
import mcheli.wrapper.W_Blocks;
import mcheli.wrapper.W_MovingObjectPosition;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

public class MCH_EntityMarkerRocket
extends MCH_EntityBaseBullet {
    public int countDown;

    public MCH_EntityMarkerRocket(World par1World) {
        super(par1World);
        this.setMarkerStatus(0);
        this.countDown = 0;
    }

    public MCH_EntityMarkerRocket(World par1World, double posX, double posY, double posZ, double targetX, double targetY, double targetZ, float yaw, float pitch, double acceleration) {
        super(par1World, posX, posY, posZ, targetX, targetY, targetZ, yaw, pitch, acceleration);
        this.setMarkerStatus(0);
        this.countDown = 0;
    }

    protected void func_70088_a() {
        super.func_70088_a();
        this.func_70096_w().func_75682_a(28, (Object)0);
    }

    public void setMarkerStatus(int n) {
        if (!this.field_70170_p.field_72995_K) {
            this.func_70096_w().func_75692_b(28, (Object)((byte)n));
        }
    }

    public int getMarkerStatus() {
        return this.func_70096_w().func_75683_a(28);
    }

    public boolean func_70097_a(DamageSource par1DamageSource, float par2) {
        return false;
    }

    public void func_70071_h_() {
        int status = this.getMarkerStatus();
        if (this.field_70170_p.field_72995_K) {
            if (this.getInfo() == null) {
                super.func_70071_h_();
            }
            if (this.getInfo() != null && !this.getInfo().disableSmoke && status != 0) {
                if (status == 1) {
                    super.func_70071_h_();
                    this.spawnParticle(this.getInfo().trajectoryParticleName, 3, 5.0f * this.getInfo().smokeSize * 0.5f);
                } else {
                    float gb = this.field_70146_Z.nextFloat() * 0.3f;
                    this.spawnParticle("explode", 5, (float)(10 + this.field_70146_Z.nextInt(4)), this.field_70146_Z.nextFloat() * 0.2f + 0.8f, gb, gb, (this.field_70146_Z.nextFloat() - 0.5f) * 0.7f, 0.3f + this.field_70146_Z.nextFloat() * 0.3f, (this.field_70146_Z.nextFloat() - 0.5f) * 0.7f);
                }
            }
        } else if (status == 0 || this.func_70090_H()) {
            this.func_70106_y();
        } else if (status == 1) {
            super.func_70071_h_();
        } else if (this.countDown > 0) {
            --this.countDown;
            if (this.countDown == 40) {
                int num = 6 + this.field_70146_Z.nextInt(2);
                for (int i = 0; i < num; ++i) {
                    MCH_EntityBomb e = new MCH_EntityBomb(this.field_70170_p, this.field_70165_t + (double)((this.field_70146_Z.nextFloat() - 0.5f) * 15.0f), (double)(260.0f + this.field_70146_Z.nextFloat() * 10.0f + (float)(i * 30)), this.field_70161_v + (double)((this.field_70146_Z.nextFloat() - 0.5f) * 15.0f), 0.0, -0.5, 0.0, 0.0f, 90.0f, 4.0);
                    e.setName(this.getName());
                    e.explosionPower = 3 + this.field_70146_Z.nextInt(2);
                    e.explosionPowerInWater = 0;
                    e.setPower(30);
                    e.piercing = 0;
                    e.shootingAircraft = this.shootingAircraft;
                    e.shootingEntity = this.shootingEntity;
                    this.field_70170_p.func_72838_d((Entity)e);
                }
            }
        } else {
            this.func_70106_y();
        }
    }

    public void spawnParticle(String name, int num, float size, float r, float g, float b, float mx, float my, float mz) {
        if (this.field_70170_p.field_72995_K) {
            if (name.isEmpty() || num < 1 || num > 50) {
                return;
            }
            double x = (this.field_70165_t - this.field_70169_q) / (double)num;
            double y = (this.field_70163_u - this.field_70167_r) / (double)num;
            double z = (this.field_70161_v - this.field_70166_s) / (double)num;
            for (int i = 0; i < num; ++i) {
                MCH_ParticleParam prm = new MCH_ParticleParam(this.field_70170_p, "smoke", this.field_70169_q + x * (double)i, this.field_70167_r + y * (double)i, this.field_70166_s + z * (double)i);
                prm.motionX = mx;
                prm.motionY = my;
                prm.motionZ = mz;
                prm.size = size + this.field_70146_Z.nextFloat();
                prm.setColor(1.0f, r, g, b);
                prm.isEffectWind = true;
                MCH_ParticlesUtil.spawnParticle((MCH_ParticleParam)prm);
            }
        }
    }

    protected void onImpact(MovingObjectPosition m, float damageFactor) {
        if (this.field_70170_p.field_72995_K) {
            return;
        }
        if (m.field_72308_g != null || W_MovingObjectPosition.isHitTypeEntity((MovingObjectPosition)m)) {
            return;
        }
        int x = m.field_72311_b;
        int y = m.field_72312_c;
        int z = m.field_72309_d;
        switch (m.field_72310_e) {
            case 0: {
                --y;
                break;
            }
            case 1: {
                ++y;
                break;
            }
            case 2: {
                --z;
                break;
            }
            case 3: {
                ++z;
                break;
            }
            case 4: {
                --x;
                break;
            }
            case 5: {
                ++x;
            }
        }
        if (this.field_70170_p.func_147437_c(x, y, z)) {
            if (MCH_Config.Explosion_FlamingBlock.prmBool) {
                W_WorldFunc.setBlock((World)this.field_70170_p, (int)x, (int)y, (int)z, (Block)W_Blocks.field_150480_ab);
            }
            int noAirBlockCount = 0;
            for (int i = y + 1; i < 256 && (this.field_70170_p.func_147437_c(x, i, z) || ++noAirBlockCount < 5); ++i) {
            }
            if (noAirBlockCount < 5) {
                this.setMarkerStatus(2);
                this.func_70107_b((double)x + 0.5, (double)y + 1.1, (double)z + 0.5);
                this.field_70169_q = this.field_70165_t;
                this.field_70167_r = this.field_70163_u;
                this.field_70166_s = this.field_70161_v;
                this.countDown = 100;
            } else {
                this.func_70106_y();
            }
        } else {
            this.func_70106_y();
        }
    }

    public MCH_BulletModel getDefaultBulletModel() {
        return MCH_DefaultBulletModels.Rocket;
    }
}

