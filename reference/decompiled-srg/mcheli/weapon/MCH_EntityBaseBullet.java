/*
 * Decompiled with CFR 0.152.
 */
package mcheli.weapon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import mcheli.MCH_Achievement;
import mcheli.MCH_Config;
import mcheli.MCH_Explosion;
import mcheli.MCH_Lib;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_EntityHitBox;
import mcheli.aircraft.MCH_EntitySeat;
import mcheli.aircraft.MCH_PacketNotifyHitBullet;
import mcheli.chain.MCH_EntityChain;
import mcheli.particles.MCH_ParticleParam;
import mcheli.particles.MCH_ParticlesUtil;
import mcheli.weapon.MCH_BulletModel;
import mcheli.weapon.MCH_EntityBullet;
import mcheli.weapon.MCH_EntityRocket;
import mcheli.weapon.MCH_WeaponBase;
import mcheli.weapon.MCH_WeaponInfo;
import mcheli.weapon.MCH_WeaponInfoManager;
import mcheli.wrapper.W_Entity;
import mcheli.wrapper.W_EntityPlayer;
import mcheli.wrapper.W_MovingObjectPosition;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.stats.Achievement;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public abstract class MCH_EntityBaseBullet
extends W_Entity {
    public static final int DATAWT_RESERVE1 = 26;
    public static final int DATAWT_TARGET_ENTITY = 27;
    public static final int DATAWT_MARKER_STAT = 28;
    public static final int DATAWT_NAME = 29;
    public static final int DATAWT_BULLET_MODEL = 30;
    public static final int DATAWT_BOMBLET_FLAG = 31;
    public Entity shootingEntity;
    public Entity shootingAircraft;
    private int countOnUpdate = 0;
    public int explosionPower;
    public int explosionPowerInWater;
    private int power;
    public double acceleration;
    public double accelerationFactor;
    public Entity targetEntity;
    public int piercing;
    public int delayFuse;
    public int sprinkleTime;
    public byte isBomblet;
    private MCH_WeaponInfo weaponInfo;
    private MCH_BulletModel model;
    public double prevPosX2;
    public double prevPosY2;
    public double prevPosZ2;
    public double prevMotionX;
    public double prevMotionY;
    public double prevMotionZ;

    public MCH_EntityBaseBullet(World par1World) {
        super(par1World);
        this.func_70105_a(1.0f, 1.0f);
        this.field_70126_B = this.field_70177_z;
        this.field_70127_C = this.field_70125_A;
        this.targetEntity = null;
        this.setPower(1);
        this.acceleration = 1.0;
        this.accelerationFactor = 1.0;
        this.piercing = 0;
        this.explosionPower = 0;
        this.explosionPowerInWater = 0;
        this.delayFuse = 0;
        this.sprinkleTime = 0;
        this.isBomblet = (byte)-1;
        this.weaponInfo = null;
        this.field_70158_ak = true;
        if (par1World.field_72995_K) {
            this.model = null;
        }
    }

    public MCH_EntityBaseBullet(World par1World, double px, double py, double pz, double mx, double my, double mz, float yaw, float pitch, double acceleration) {
        this(par1World);
        this.func_70105_a(1.0f, 1.0f);
        this.func_70012_b(px, py, pz, yaw, pitch);
        this.func_70107_b(px, py, pz);
        this.field_70126_B = yaw;
        this.field_70127_C = pitch;
        this.field_70129_M = 0.0f;
        if (acceleration > 3.9) {
            acceleration = 3.9;
        }
        double d = MathHelper.func_76133_a((double)(mx * mx + my * my + mz * mz));
        this.field_70159_w = mx * acceleration / d;
        this.field_70181_x = my * acceleration / d;
        this.field_70179_y = mz * acceleration / d;
        this.prevMotionX = this.field_70159_w;
        this.prevMotionY = this.field_70181_x;
        this.prevMotionZ = this.field_70179_y;
        this.acceleration = acceleration;
    }

    public void func_70012_b(double par1, double par3, double par5, float par7, float par8) {
        super.func_70012_b(par1, par3, par5, par7, par8);
        this.prevPosX2 = par1;
        this.prevPosY2 = par3;
        this.prevPosZ2 = par5;
    }

    protected void func_70088_a() {
        super.func_70088_a();
        this.func_70096_w().func_75682_a(27, (Object)0);
        this.func_70096_w().func_75682_a(29, (Object)String.valueOf(""));
        this.func_70096_w().func_75682_a(30, (Object)String.valueOf(""));
        this.func_70096_w().func_75682_a(31, (Object)0);
    }

    public void setName(String s) {
        if (s != null && !s.isEmpty()) {
            this.weaponInfo = MCH_WeaponInfoManager.get((String)s);
            if (this.weaponInfo != null) {
                if (!this.field_70170_p.field_72995_K) {
                    this.func_70096_w().func_75692_b(29, (Object)String.valueOf(s));
                }
                this.onSetWeasponInfo();
            }
        }
    }

    public String getName() {
        return this.func_70096_w().func_75681_e(29);
    }

    public MCH_WeaponInfo getInfo() {
        return this.weaponInfo;
    }

    public void onSetWeasponInfo() {
        if (!this.field_70170_p.field_72995_K) {
            this.isBomblet = 0;
        }
        if (this.getInfo().bomblet > 0) {
            this.sprinkleTime = this.getInfo().bombletSTime;
        }
        this.piercing = this.getInfo().piercing;
        if (this instanceof MCH_EntityBullet) {
            if (this.getInfo().acceleration > 4.0f) {
                this.accelerationFactor = this.getInfo().acceleration / 4.0f;
            }
        } else if (this instanceof MCH_EntityRocket && this.isBomblet == 0 && this.getInfo().acceleration > 4.0f) {
            this.accelerationFactor = this.getInfo().acceleration / 4.0f;
        }
    }

    public void func_70106_y() {
        super.func_70106_y();
    }

    public void setBomblet() {
        this.isBomblet = 1;
        this.sprinkleTime = 0;
        this.field_70180_af.func_75692_b(31, (Object)1);
    }

    public byte getBomblet() {
        return this.field_70180_af.func_75683_a(31);
    }

    public void setTargetEntity(Entity entity) {
        this.targetEntity = entity;
        if (!this.field_70170_p.field_72995_K) {
            if (entity != null) {
                this.func_70096_w().func_75692_b(27, (Object)W_Entity.getEntityId((Entity)entity));
            } else {
                this.func_70096_w().func_75692_b(27, (Object)0);
            }
        }
    }

    public int getTargetEntityID() {
        if (this.targetEntity != null) {
            return W_Entity.getEntityId((Entity)this.targetEntity);
        }
        return this.func_70096_w().func_75679_c(27);
    }

    public MCH_BulletModel getBulletModel() {
        if (this.getInfo() == null) {
            return null;
        }
        if (this.isBomblet < 0) {
            return null;
        }
        if (this.model == null) {
            this.model = this.isBomblet == 1 ? this.getInfo().bombletModel : this.getInfo().bulletModel;
            if (this.model == null) {
                this.model = this.getDefaultBulletModel();
            }
        }
        return this.model;
    }

    public abstract MCH_BulletModel getDefaultBulletModel();

    public void sprinkleBomblet() {
    }

    public void spawnParticle(String name, int num, float size) {
        block5: {
            if (!this.field_70170_p.field_72995_K) break block5;
            if (name.isEmpty() || num < 1 || num > 50) {
                return;
            }
            double x = (this.field_70165_t - this.field_70169_q) / (double)num;
            double y = (this.field_70163_u - this.field_70167_r) / (double)num;
            double z = (this.field_70161_v - this.field_70166_s) / (double)num;
            double x2 = (this.field_70169_q - this.prevPosX2) / (double)num;
            double y2 = (this.field_70167_r - this.prevPosY2) / (double)num;
            double z2 = (this.field_70166_s - this.prevPosZ2) / (double)num;
            if (name.equals("explode")) {
                for (int i = 0; i < num; ++i) {
                    MCH_ParticleParam prm = new MCH_ParticleParam(this.field_70170_p, "smoke", (this.field_70169_q + x * (double)i + (this.prevPosX2 + x2 * (double)i)) / 2.0, (this.field_70167_r + y * (double)i + (this.prevPosY2 + y2 * (double)i)) / 2.0, (this.field_70166_s + z * (double)i + (this.prevPosZ2 + z2 * (double)i)) / 2.0);
                    prm.size = size + this.field_70146_Z.nextFloat();
                    MCH_ParticlesUtil.spawnParticle((MCH_ParticleParam)prm);
                }
            } else {
                for (int i = 0; i < num; ++i) {
                    MCH_ParticlesUtil.DEF_spawnParticle((String)name, (double)((this.field_70169_q + x * (double)i + (this.prevPosX2 + x2 * (double)i)) / 2.0), (double)((this.field_70167_r + y * (double)i + (this.prevPosY2 + y2 * (double)i)) / 2.0), (double)((this.field_70166_s + z * (double)i + (this.prevPosZ2 + z2 * (double)i)) / 2.0), (double)0.0, (double)0.0, (double)0.0, (float)50.0f);
                }
            }
        }
    }

    public void DEF_spawnParticle(String name, int num, float size) {
        if (this.field_70170_p.field_72995_K) {
            if (name.isEmpty() || num < 1 || num > 50) {
                return;
            }
            double x = (this.field_70165_t - this.field_70169_q) / (double)num;
            double y = (this.field_70163_u - this.field_70167_r) / (double)num;
            double z = (this.field_70161_v - this.field_70166_s) / (double)num;
            double x2 = (this.field_70169_q - this.prevPosX2) / (double)num;
            double y2 = (this.field_70167_r - this.prevPosY2) / (double)num;
            double z2 = (this.field_70166_s - this.prevPosZ2) / (double)num;
            for (int i = 0; i < num; ++i) {
                MCH_ParticlesUtil.DEF_spawnParticle((String)name, (double)((this.field_70169_q + x * (double)i + (this.prevPosX2 + x2 * (double)i)) / 2.0), (double)((this.field_70167_r + y * (double)i + (this.prevPosY2 + y2 * (double)i)) / 2.0), (double)((this.field_70166_s + z * (double)i + (this.prevPosZ2 + z2 * (double)i)) / 2.0), (double)0.0, (double)0.0, (double)0.0, (float)150.0f);
            }
        }
    }

    public int getCountOnUpdate() {
        return this.countOnUpdate;
    }

    public void clearCountOnUpdate() {
        this.countOnUpdate = 0;
    }

    @SideOnly(value=Side.CLIENT)
    public boolean func_70112_a(double par1) {
        double d1 = this.field_70121_D.func_72320_b() * 4.0;
        return par1 < (d1 *= 64.0) * d1;
    }

    public void setParameterFromWeapon(MCH_WeaponBase w, Entity entity, Entity user) {
        this.explosionPower = w.explosionPower;
        this.explosionPowerInWater = w.explosionPowerInWater;
        this.setPower(w.power);
        this.piercing = w.piercing;
        this.shootingAircraft = entity;
        this.shootingEntity = user;
    }

    public void setParameterFromWeapon(MCH_EntityBaseBullet b, Entity entity, Entity user) {
        this.explosionPower = b.explosionPower;
        this.explosionPowerInWater = b.explosionPowerInWater;
        this.setPower(b.getPower());
        this.piercing = b.piercing;
        this.shootingAircraft = entity;
        this.shootingEntity = user;
    }

    public void setMotion(double targetX, double targetY, double targetZ) {
        double d6 = MathHelper.func_76133_a((double)(targetX * targetX + targetY * targetY + targetZ * targetZ));
        this.field_70159_w = targetX * this.acceleration / d6;
        this.field_70181_x = targetY * this.acceleration / d6;
        this.field_70179_y = targetZ * this.acceleration / d6;
    }

    public void guidanceToTarget(double targetPosX, double targetPosY, double targetPosZ) {
        this.guidanceToTarget(targetPosX, targetPosY, targetPosZ, 1.0f);
    }

    public void guidanceToTarget(double targetPosX, double targetPosY, double targetPosZ, float accelerationFactor) {
        double tx = targetPosX - this.field_70165_t;
        double ty = targetPosY - this.field_70163_u;
        double tz = targetPosZ - this.field_70161_v;
        double d = MathHelper.func_76133_a((double)(tx * tx + ty * ty + tz * tz));
        double mx = tx * this.acceleration / d;
        double my = ty * this.acceleration / d;
        double mz = tz * this.acceleration / d;
        this.field_70159_w = (this.field_70159_w * 6.0 + mx) / 7.0;
        this.field_70181_x = (this.field_70181_x * 6.0 + my) / 7.0;
        this.field_70179_y = (this.field_70179_y * 6.0 + mz) / 7.0;
        double a = (float)Math.atan2(this.field_70179_y, this.field_70159_w);
        this.field_70177_z = (float)(a * 180.0 / Math.PI) - 90.0f;
        double r = Math.sqrt(this.field_70159_w * this.field_70159_w + this.field_70179_y * this.field_70179_y);
        this.field_70125_A = -((float)(Math.atan2(this.field_70181_x, r) * 180.0 / Math.PI));
    }

    public boolean checkValid() {
        if (this.shootingEntity == null && this.shootingAircraft == null) {
            return false;
        }
        if (this.shootingEntity != null && this.shootingEntity.field_70128_L) {
            return false;
        }
        if (this.shootingAircraft == null || this.shootingAircraft.field_70128_L) {
            // empty if block
        }
        Entity shooter = this.shootingEntity != null ? this.shootingEntity : this.shootingAircraft;
        double x = this.field_70165_t - shooter.field_70165_t;
        double z = this.field_70161_v - shooter.field_70161_v;
        return x * x + z * z < 3.38724E7;
    }

    public float getGravity() {
        return this.getInfo() != null ? this.getInfo().gravity : 0.0f;
    }

    public float getGravityInWater() {
        return this.getInfo() != null ? this.getInfo().gravityInWater : 0.0f;
    }

    public void func_70071_h_() {
        int tgtEttId;
        if (this.field_70170_p.field_72995_K && this.countOnUpdate == 0 && (tgtEttId = this.getTargetEntityID()) > 0) {
            this.setTargetEntity(this.field_70170_p.func_73045_a(tgtEttId));
        }
        if (this.prevMotionX != this.field_70159_w || this.prevMotionY != this.field_70181_x || this.prevMotionZ != this.field_70179_y) {
            double a = (float)Math.atan2(this.field_70179_y, this.field_70159_w);
            this.field_70177_z = (float)(a * 180.0 / Math.PI) - 90.0f;
            double r = Math.sqrt(this.field_70159_w * this.field_70159_w + this.field_70179_y * this.field_70179_y);
            this.field_70125_A = -((float)(Math.atan2(this.field_70181_x, r) * 180.0 / Math.PI));
        }
        this.prevMotionX = this.field_70159_w;
        this.prevMotionY = this.field_70181_x;
        this.prevMotionZ = this.field_70179_y;
        ++this.countOnUpdate;
        if (this.countOnUpdate > 10000000) {
            this.clearCountOnUpdate();
        }
        this.prevPosX2 = this.field_70169_q;
        this.prevPosY2 = this.field_70167_r;
        this.prevPosZ2 = this.field_70166_s;
        super.func_70071_h_();
        if (this.getInfo() == null) {
            if (this.countOnUpdate >= 2) {
                MCH_Lib.Log((Entity)this, (String)"##### MCH_EntityBaseBullet onUpdate() Weapon info null %d, %s, Name=%s", (Object[])new Object[]{W_Entity.getEntityId((Entity)this), this.getEntityName(), this.getName()});
                this.func_70106_y();
                return;
            }
            this.setName(this.getName());
            if (this.getInfo() == null) {
                return;
            }
        }
        if (this.field_70170_p.field_72995_K && this.isBomblet < 0) {
            this.isBomblet = this.getBomblet();
        }
        if (!this.field_70170_p.field_72995_K) {
            if ((int)this.field_70163_u <= 255 && !this.field_70170_p.func_72899_e((int)this.field_70165_t, (int)this.field_70163_u, (int)this.field_70161_v)) {
                if (this.getInfo().delayFuse > 0) {
                    if (this.delayFuse == 0) {
                        this.delayFuse = this.getInfo().delayFuse;
                    }
                } else {
                    this.func_70106_y();
                    return;
                }
            }
            if (this.delayFuse > 0) {
                --this.delayFuse;
                if (this.delayFuse == 0) {
                    this.onUpdateTimeout();
                    this.func_70106_y();
                    return;
                }
            }
            if (!this.checkValid()) {
                this.func_70106_y();
                return;
            }
            if (this.getInfo().timeFuse > 0 && this.getCountOnUpdate() > this.getInfo().timeFuse) {
                this.onUpdateTimeout();
                this.func_70106_y();
                return;
            }
            if (this.getInfo().explosionAltitude > 0 && MCH_Lib.getBlockIdY((Entity)this, (int)3, (int)(-this.getInfo().explosionAltitude)) != 0) {
                MovingObjectPosition mop = new MovingObjectPosition((int)this.field_70165_t, (int)this.field_70163_u, (int)this.field_70161_v, 0, Vec3.func_72443_a((double)this.field_70165_t, (double)this.field_70163_u, (double)this.field_70161_v));
                this.onImpact(mop, 1.0f);
            }
        }
        this.field_70181_x = !this.func_70090_H() ? (this.field_70181_x += (double)this.getGravity()) : (this.field_70181_x += (double)this.getGravityInWater());
        if (!this.field_70128_L) {
            this.onUpdateCollided();
        }
        this.field_70165_t += this.field_70159_w * this.accelerationFactor;
        this.field_70163_u += this.field_70181_x * this.accelerationFactor;
        this.field_70161_v += this.field_70179_y * this.accelerationFactor;
        if (this.field_70170_p.field_72995_K) {
            this.updateSplash();
        }
        if (this.func_70090_H()) {
            float f3 = 0.25f;
            this.field_70170_p.func_72869_a("bubble", this.field_70165_t - this.field_70159_w * (double)f3, this.field_70163_u - this.field_70181_x * (double)f3, this.field_70161_v - this.field_70179_y * (double)f3, this.field_70159_w, this.field_70181_x, this.field_70179_y);
        }
        this.func_70107_b(this.field_70165_t, this.field_70163_u, this.field_70161_v);
    }

    public void updateSplash() {
        if (this.getInfo() == null) {
            return;
        }
        if (this.getInfo().power <= 0) {
            return;
        }
        if (!W_WorldFunc.isBlockWater((World)this.field_70170_p, (int)((int)(this.field_70169_q + 0.5)), (int)((int)(this.field_70167_r + 0.5)), (int)((int)(this.field_70166_s + 0.5))) && W_WorldFunc.isBlockWater((World)this.field_70170_p, (int)((int)(this.field_70165_t + 0.5)), (int)((int)(this.field_70163_u + 0.5)), (int)((int)(this.field_70161_v + 0.5)))) {
            double x = this.field_70165_t - this.field_70169_q;
            double y = this.field_70163_u - this.field_70167_r;
            double z = this.field_70161_v - this.field_70166_s;
            double d = Math.sqrt(x * x + y * y + z * z);
            if (d <= 0.15) {
                return;
            }
            x /= d;
            y /= d;
            z /= d;
            double px = this.field_70169_q;
            double py = this.field_70167_r;
            double pz = this.field_70166_s;
            int i = 0;
            while ((double)i <= d) {
                if (W_WorldFunc.isBlockWater((World)this.field_70170_p, (int)((int)((px += x) + 0.5)), (int)((int)((py += y) + 0.5)), (int)((int)((pz += z) + 0.5)))) {
                    float pwr = this.getInfo().power < 20 ? (float)this.getInfo().power : 20.0f;
                    int n = this.field_70146_Z.nextInt(1 + (int)pwr / 3) + (int)pwr / 2 + 1;
                    pwr *= 0.03f;
                    for (int j = 0; j < n; ++j) {
                        MCH_ParticleParam prm = new MCH_ParticleParam(this.field_70170_p, "splash", px, py + 0.5, pz, (double)pwr * (this.field_70146_Z.nextDouble() - 0.5) * 0.3, (double)pwr * (this.field_70146_Z.nextDouble() * 0.5 + 0.5) * 1.8, (double)pwr * (this.field_70146_Z.nextDouble() - 0.5) * 0.3, pwr * 5.0f);
                        MCH_ParticlesUtil.spawnParticle((MCH_ParticleParam)prm);
                    }
                    break;
                }
                ++i;
            }
        }
    }

    public void onUpdateTimeout() {
        if (this.func_70090_H()) {
            if (this.explosionPowerInWater > 0) {
                this.newExplosion(this.field_70165_t, this.field_70163_u, this.field_70161_v, (float)this.explosionPowerInWater, (float)this.explosionPowerInWater, true);
            }
        } else if (this.explosionPower > 0) {
            this.newExplosion(this.field_70165_t, this.field_70163_u, this.field_70161_v, (float)this.explosionPower, (float)this.getInfo().explosionBlock, false);
        } else if (this.explosionPower < 0) {
            this.playExplosionSound();
        }
    }

    public void onUpdateBomblet() {
        if (!this.field_70170_p.field_72995_K && this.sprinkleTime > 0 && !this.field_70128_L) {
            --this.sprinkleTime;
            if (this.sprinkleTime == 0) {
                for (int i = 0; i < this.getInfo().bomblet; ++i) {
                    this.sprinkleBomblet();
                }
                this.func_70106_y();
            }
        }
    }

    public void boundBullet(int sideHit) {
        switch (sideHit) {
            case 0: {
                if (!(this.field_70181_x > 0.0)) break;
                this.field_70181_x = -this.field_70181_x * (double)this.getInfo().bound;
                break;
            }
            case 1: {
                if (!(this.field_70181_x < 0.0)) break;
                this.field_70181_x = -this.field_70181_x * (double)this.getInfo().bound;
                break;
            }
            case 2: {
                if (this.field_70179_y > 0.0) {
                    this.field_70179_y = -this.field_70179_y * (double)this.getInfo().bound;
                    break;
                }
                this.field_70161_v += this.field_70179_y;
                break;
            }
            case 3: {
                if (this.field_70179_y < 0.0) {
                    this.field_70179_y = -this.field_70179_y * (double)this.getInfo().bound;
                    break;
                }
                this.field_70161_v += this.field_70179_y;
                break;
            }
            case 4: {
                if (this.field_70159_w > 0.0) {
                    this.field_70159_w = -this.field_70159_w * (double)this.getInfo().bound;
                    break;
                }
                this.field_70165_t += this.field_70159_w;
                break;
            }
            case 5: {
                if (this.field_70159_w < 0.0) {
                    this.field_70159_w = -this.field_70159_w * (double)this.getInfo().bound;
                    break;
                }
                this.field_70165_t += this.field_70159_w;
            }
        }
    }

    protected void onUpdateCollided() {
        Vec3 vec31;
        Vec3 vec3;
        float damageFator = 1.0f;
        double mx = this.field_70159_w * this.accelerationFactor;
        double my = this.field_70181_x * this.accelerationFactor;
        double mz = this.field_70179_y * this.accelerationFactor;
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
            this.onImpact(m, damageFator);
        }
    }

    public boolean canBeCollidedEntity(Entity entity) {
        if (entity instanceof MCH_EntityChain) {
            return false;
        }
        if (!entity.func_70067_L()) {
            return false;
        }
        if (entity instanceof MCH_EntityBaseBullet) {
            if (this.field_70170_p.field_72995_K) {
                return false;
            }
            MCH_EntityBaseBullet blt = (MCH_EntityBaseBullet)entity;
            if (W_Entity.isEqual((Entity)blt.shootingAircraft, (Entity)this.shootingAircraft)) {
                return false;
            }
            if (W_Entity.isEqual((Entity)blt.shootingEntity, (Entity)this.shootingEntity)) {
                return false;
            }
        }
        if (entity instanceof MCH_EntitySeat) {
            return false;
        }
        if (entity instanceof MCH_EntityHitBox) {
            return false;
        }
        if (W_Entity.isEqual((Entity)entity, (Entity)this.shootingEntity)) {
            return false;
        }
        if (this.shootingAircraft instanceof MCH_EntityAircraft) {
            if (W_Entity.isEqual((Entity)entity, (Entity)this.shootingAircraft)) {
                return false;
            }
            if (((MCH_EntityAircraft)this.shootingAircraft).isMountedEntity(entity)) {
                return false;
            }
        }
        for (String s : MCH_Config.IgnoreBulletHitList) {
            if (entity.getClass().getName().toLowerCase().indexOf(s.toLowerCase()) < 0) continue;
            return false;
        }
        return true;
    }

    public void notifyHitBullet() {
        if (this.shootingAircraft instanceof MCH_EntityAircraft && W_EntityPlayer.isPlayer((Entity)this.shootingEntity)) {
            MCH_PacketNotifyHitBullet.send((MCH_EntityAircraft)((MCH_EntityAircraft)this.shootingAircraft), (EntityPlayer)((EntityPlayer)this.shootingEntity));
        }
        if (W_EntityPlayer.isPlayer((Entity)this.shootingEntity)) {
            MCH_PacketNotifyHitBullet.send(null, (EntityPlayer)((EntityPlayer)this.shootingEntity));
        }
    }

    protected void onImpact(MovingObjectPosition m, float damageFactor) {
        if (!this.field_70170_p.field_72995_K) {
            if (m.field_72308_g != null) {
                this.onImpactEntity(m.field_72308_g, damageFactor);
                this.piercing = 0;
            }
            float expPower = (float)this.explosionPower * damageFactor;
            float expPowerInWater = (float)this.explosionPowerInWater * damageFactor;
            double dx = 0.0;
            double dy = 0.0;
            double dz = 0.0;
            if (this.piercing > 0) {
                --this.piercing;
                if (expPower > 0.0f) {
                    this.newExplosion(m.field_72307_f.field_72450_a + dx, m.field_72307_f.field_72448_b + dy, m.field_72307_f.field_72449_c + dz, 1.0f, 1.0f, false);
                }
            } else {
                if (expPowerInWater == 0.0f) {
                    if (this.getInfo().isFAE) {
                        this.newFAExplosion(this.field_70165_t, this.field_70163_u, this.field_70161_v, expPower, (float)this.getInfo().explosionBlock);
                    } else if (expPower > 0.0f) {
                        this.newExplosion(m.field_72307_f.field_72450_a + dx, m.field_72307_f.field_72448_b + dy, m.field_72307_f.field_72449_c + dz, expPower, (float)this.getInfo().explosionBlock, false);
                    } else if (expPower < 0.0f) {
                        this.playExplosionSound();
                    }
                } else if (m.field_72308_g != null) {
                    if (this.func_70090_H()) {
                        this.newExplosion(m.field_72307_f.field_72450_a + dx, m.field_72307_f.field_72448_b + dy, m.field_72307_f.field_72449_c + dz, expPowerInWater, expPowerInWater, true);
                    } else {
                        this.newExplosion(m.field_72307_f.field_72450_a + dx, m.field_72307_f.field_72448_b + dy, m.field_72307_f.field_72449_c + dz, expPower, (float)this.getInfo().explosionBlock, false);
                    }
                } else if (this.func_70090_H() || MCH_Lib.isBlockInWater((World)this.field_70170_p, (int)m.field_72311_b, (int)m.field_72312_c, (int)m.field_72309_d)) {
                    this.newExplosion((double)m.field_72311_b, (double)m.field_72312_c, (double)m.field_72309_d, expPowerInWater, expPowerInWater, true);
                } else if (expPower > 0.0f) {
                    this.newExplosion(m.field_72307_f.field_72450_a + dx, m.field_72307_f.field_72448_b + dy, m.field_72307_f.field_72449_c + dz, expPower, (float)this.getInfo().explosionBlock, false);
                } else if (expPower < 0.0f) {
                    this.playExplosionSound();
                }
                this.func_70106_y();
            }
        } else if (this.getInfo() != null && (this.getInfo().explosion == 0 || this.getInfo().modeNum >= 2) && W_MovingObjectPosition.isHitTypeTile((MovingObjectPosition)m)) {
            float p = this.getInfo().power;
            int i = 0;
            while ((float)i < p / 3.0f) {
                MCH_ParticlesUtil.spawnParticleTileCrack((World)this.field_70170_p, (int)m.field_72311_b, (int)m.field_72312_c, (int)m.field_72309_d, (double)(m.field_72307_f.field_72450_a + ((double)this.field_70146_Z.nextFloat() - 0.5) * (double)p / 10.0), (double)(m.field_72307_f.field_72448_b + 0.1), (double)(m.field_72307_f.field_72449_c + ((double)this.field_70146_Z.nextFloat() - 0.5) * (double)p / 10.0), (double)(-this.field_70159_w * (double)p / 2.0), (double)(p / 2.0f), (double)(-this.field_70179_y * (double)p / 2.0));
                ++i;
            }
        }
    }

    public void onImpactEntity(Entity entity, float damageFactor) {
        if (!entity.field_70128_L) {
            MCH_Lib.DbgLog((World)this.field_70170_p, (String)("MCH_EntityBaseBullet.onImpactEntity:Damage=%d:" + entity.getClass()), (Object[])new Object[]{this.getPower()});
            MCH_Lib.applyEntityHurtResistantTimeConfig((Entity)entity);
            DamageSource ds = DamageSource.func_76356_a((Entity)this, (Entity)this.shootingEntity);
            float damage = MCH_Config.applyDamageVsEntity((Entity)entity, (DamageSource)ds, (float)((float)this.getPower() * damageFactor));
            entity.func_70097_a(ds, damage *= this.getInfo() != null ? this.getInfo().getDamageFactor(entity) : 1.0f);
            if (this instanceof MCH_EntityBullet && entity instanceof EntityVillager && this.shootingEntity != null && this.shootingEntity.field_70154_o instanceof MCH_EntitySeat) {
                MCH_Achievement.addStat((Entity)this.shootingEntity, (Achievement)MCH_Achievement.aintWarHell, (int)1);
            }
            if (entity.field_70128_L) {
                // empty if block
            }
        }
        this.notifyHitBullet();
    }

    public void newFAExplosion(double x, double y, double z, float exp, float expBlock) {
        MCH_Explosion.ExplosionResult result = MCH_Explosion.newExplosion((World)this.field_70170_p, (Entity)this, (Entity)this.shootingEntity, (double)x, (double)y, (double)z, (float)exp, (float)expBlock, (boolean)true, (boolean)true, (boolean)this.getInfo().flaming, (boolean)false, (int)15);
        if (result != null && result.hitEntity) {
            this.notifyHitBullet();
        }
    }

    public void newExplosion(double x, double y, double z, float exp, float expBlock, boolean inWater) {
        MCH_Explosion.ExplosionResult result = !inWater ? MCH_Explosion.newExplosion((World)this.field_70170_p, (Entity)this, (Entity)this.shootingEntity, (double)x, (double)y, (double)z, (float)exp, (float)expBlock, (boolean)(this.isBomblet == 1 ? this.field_70146_Z.nextInt(3) == 0 : true), (boolean)true, (boolean)this.getInfo().flaming, (boolean)true, (int)0, this.getInfo() != null ? this.getInfo().damageFactor : null) : MCH_Explosion.newExplosionInWater((World)this.field_70170_p, (Entity)this, (Entity)this.shootingEntity, (double)x, (double)y, (double)z, (float)exp, (float)expBlock, (boolean)(this.isBomblet == 1 ? this.field_70146_Z.nextInt(3) == 0 : true), (boolean)true, (boolean)this.getInfo().flaming, (boolean)true, (int)0, this.getInfo() != null ? this.getInfo().damageFactor : null);
        if (result != null && result.hitEntity) {
            this.notifyHitBullet();
        }
    }

    public void playExplosionSound() {
        MCH_Explosion.playExplosionSound((World)this.field_70170_p, (double)this.field_70165_t, (double)this.field_70163_u, (double)this.field_70161_v);
    }

    public void func_70014_b(NBTTagCompound par1NBTTagCompound) {
        par1NBTTagCompound.func_74782_a("direction", (NBTBase)this.func_70087_a(new double[]{this.field_70159_w, this.field_70181_x, this.field_70179_y}));
        par1NBTTagCompound.func_74778_a("WeaponName", this.getName());
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

    public boolean func_70097_a(DamageSource ds, float par2) {
        if (this.func_85032_ar()) {
            return false;
        }
        if (!this.field_70170_p.field_72995_K && par2 > 0.0f && ds.func_76355_l().equalsIgnoreCase("thrown")) {
            this.func_70018_K();
            MovingObjectPosition m = new MovingObjectPosition((int)(this.field_70165_t + 0.5), (int)(this.field_70163_u + 0.5), (int)(this.field_70161_v + 0.5), 0, Vec3.func_72443_a((double)(this.field_70165_t + 0.5), (double)(this.field_70163_u + 0.5), (double)(this.field_70161_v + 0.5)));
            this.onImpact(m, 1.0f);
            return true;
        }
        return false;
    }

    @SideOnly(value=Side.CLIENT)
    public float func_70053_R() {
        return 0.0f;
    }

    public float func_70013_c(float par1) {
        return 1.0f;
    }

    @SideOnly(value=Side.CLIENT)
    public int func_70070_b(float par1) {
        return 0xF000F0;
    }

    public int getPower() {
        return this.power;
    }

    public void setPower(int power) {
        this.power = power;
    }
}

