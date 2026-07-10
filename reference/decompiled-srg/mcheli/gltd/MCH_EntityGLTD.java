/*
 * Decompiled with CFR 0.152.
 */
package mcheli.gltd;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.MCH_Camera;
import mcheli.MCH_Config;
import mcheli.MCH_MOD;
import mcheli.multiplay.MCH_Multiplay;
import mcheli.weapon.MCH_WeaponCAS;
import mcheli.weapon.MCH_WeaponInfo;
import mcheli.weapon.MCH_WeaponInfoManager;
import mcheli.wrapper.W_Block;
import mcheli.wrapper.W_Entity;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class MCH_EntityGLTD
extends W_Entity {
    private boolean field_70279_a = true;
    private double speedMultiplier = 0.07;
    private int gltdPosRotInc;
    private double gltdX;
    private double gltdY;
    private double gltdZ;
    private double gltdYaw;
    private double gltdPitch;
    @SideOnly(value=Side.CLIENT)
    private double velocityX;
    @SideOnly(value=Side.CLIENT)
    private double velocityY;
    @SideOnly(value=Side.CLIENT)
    private double velocityZ;
    public final MCH_Camera camera;
    public boolean zoomDir;
    public final MCH_WeaponCAS weaponCAS;
    public int countWait;
    public boolean isUsedPlayer;
    public float renderRotaionYaw;
    public float renderRotaionPitch;
    public int retryRiddenByEntityCheck;
    public Entity lastRiddenByEntity;

    public MCH_EntityGLTD(World world) {
        super(world);
        this.field_70156_m = true;
        this.func_70105_a(0.5f, 0.5f);
        this.field_70129_M = this.field_70131_O / 2.0f;
        this.camera = new MCH_Camera(world, (Entity)this);
        MCH_WeaponInfo wi = MCH_WeaponInfoManager.get((String)"a10gau8");
        this.weaponCAS = new MCH_WeaponCAS(world, Vec3.func_72443_a((double)0.0, (double)0.0, (double)0.0), 0.0f, 0.0f, "a10gau8", wi);
        this.weaponCAS.interval = this.weaponCAS.interval + (this.weaponCAS.interval > 0 ? 150 : -150);
        this.weaponCAS.displayName = "A-10 GAU-8 Avenger";
        this.field_70158_ak = true;
        this.countWait = 0;
        this.retryRiddenByEntityCheck = 0;
        this.lastRiddenByEntity = null;
        this.isUsedPlayer = false;
        this.renderRotaionYaw = 0.0f;
        this.renderRotaionYaw = 0.0f;
        this.renderRotaionPitch = 0.0f;
        this.zoomDir = true;
        this.field_70155_l = 2.0;
    }

    public MCH_EntityGLTD(World par1World, double x, double y, double z) {
        this(par1World);
        this.func_70107_b(x, y + (double)this.field_70129_M, z);
        this.field_70159_w = 0.0;
        this.field_70181_x = 0.0;
        this.field_70179_y = 0.0;
        this.field_70169_q = x;
        this.field_70167_r = y;
        this.field_70166_s = z;
        this.camera.setPosition(x, y, z);
    }

    protected boolean func_70041_e_() {
        return false;
    }

    protected void func_70088_a() {
        this.field_70180_af.func_75682_a(17, (Object)new Integer(0));
        this.field_70180_af.func_75682_a(18, (Object)new Integer(1));
        this.field_70180_af.func_75682_a(19, (Object)new Integer(0));
    }

    public AxisAlignedBB func_70114_g(Entity par1Entity) {
        return par1Entity.field_70121_D;
    }

    public AxisAlignedBB func_70046_E() {
        return this.field_70121_D;
    }

    public boolean func_70104_M() {
        return false;
    }

    public double func_70042_X() {
        return (double)this.field_70131_O * 0.0 - 0.3;
    }

    public boolean func_70097_a(DamageSource ds, float damage) {
        if (this.func_85032_ar()) {
            return false;
        }
        if (!this.field_70170_p.field_72995_K && !this.field_70128_L) {
            boolean flag;
            damage = MCH_Config.applyDamageByExternal((Entity)this, (DamageSource)ds, (float)damage);
            if (!MCH_Multiplay.canAttackEntity((DamageSource)ds, (Entity)this)) {
                return false;
            }
            this.setForwardDirection(-this.getForwardDirection());
            this.setTimeSinceHit(10);
            this.setDamageTaken((int)((float)this.getDamageTaken() + damage * 100.0f));
            this.func_70018_K();
            boolean bl = flag = ds.func_76346_g() instanceof EntityPlayer && ((EntityPlayer)ds.func_76346_g()).field_71075_bZ.field_75098_d;
            if (flag || (float)this.getDamageTaken() > 40.0f) {
                this.camera.initCamera(0, this.field_70153_n);
                if (this.field_70153_n != null) {
                    this.field_70153_n.func_70078_a((Entity)this);
                }
                if (!flag) {
                    this.dropItemWithOffset((Item)MCH_MOD.itemGLTD, 1, 0.0f);
                }
                W_WorldFunc.MOD_playSoundEffect((World)this.field_70170_p, (double)this.field_70165_t, (double)this.field_70163_u, (double)this.field_70161_v, (String)"hit", (float)1.0f, (float)1.0f);
                this.func_70106_y();
            }
            return true;
        }
        return true;
    }

    @SideOnly(value=Side.CLIENT)
    public void func_70057_ab() {
    }

    public boolean func_70067_L() {
        return !this.field_70128_L;
    }

    @SideOnly(value=Side.CLIENT)
    public void func_70056_a(double par1, double par3, double par5, float par7, float par8, int par9) {
        if (this.field_70279_a) {
            this.gltdPosRotInc = par9 + 5;
        } else {
            double x = par1 - this.field_70165_t;
            double y = par3 - this.field_70163_u;
            double z = par5 - this.field_70161_v;
            if (x * x + y * y + z * z <= 1.0) {
                return;
            }
            this.gltdPosRotInc = 3;
        }
        this.gltdX = par1;
        this.gltdY = par3;
        this.gltdZ = par5;
        this.gltdYaw = par7;
        this.gltdPitch = par8;
        this.field_70159_w = this.velocityX;
        this.field_70181_x = this.velocityY;
        this.field_70179_y = this.velocityZ;
    }

    @SideOnly(value=Side.CLIENT)
    public void func_70016_h(double x, double y, double z) {
        this.velocityX = this.field_70159_w = x;
        this.velocityY = this.field_70181_x = y;
        this.velocityZ = this.field_70179_y = z;
    }

    public void func_70071_h_() {
        super.func_70071_h_();
        if (this.getTimeSinceHit() > 0) {
            this.setTimeSinceHit(this.getTimeSinceHit() - 1);
        }
        if ((float)this.getDamageTaken() > 0.0f) {
            this.setDamageTaken(this.getDamageTaken() - 1);
        }
        this.field_70169_q = this.field_70165_t;
        this.field_70167_r = this.field_70163_u;
        this.field_70166_s = this.field_70161_v;
        double d3 = Math.sqrt(this.field_70159_w * this.field_70159_w + this.field_70179_y * this.field_70179_y);
        if (this.field_70153_n != null) {
            this.camera.updateViewer(0, this.field_70153_n);
        }
        if (this.field_70170_p.field_72995_K && this.field_70279_a) {
            if (this.gltdPosRotInc > 0) {
                double d4 = this.field_70165_t + (this.gltdX - this.field_70165_t) / (double)this.gltdPosRotInc;
                double d5 = this.field_70163_u + (this.gltdY - this.field_70163_u) / (double)this.gltdPosRotInc;
                double d11 = this.field_70161_v + (this.gltdZ - this.field_70161_v) / (double)this.gltdPosRotInc;
                double d10 = MathHelper.func_76138_g((double)(this.gltdYaw - (double)this.field_70177_z));
                this.field_70177_z = (float)((double)this.field_70177_z + d10 / (double)this.gltdPosRotInc);
                this.field_70125_A = (float)((double)this.field_70125_A + (this.gltdPitch - (double)this.field_70125_A) / (double)this.gltdPosRotInc);
                --this.gltdPosRotInc;
                this.func_70107_b(d4, d5, d11);
                this.func_70101_b(this.field_70177_z, this.field_70125_A);
            } else {
                double d4 = this.field_70165_t + this.field_70159_w;
                double d5 = this.field_70163_u + this.field_70181_x;
                double d11 = this.field_70161_v + this.field_70179_y;
                this.func_70107_b(d4, d5, d11);
                if (this.field_70122_E) {
                    this.field_70159_w *= 0.5;
                    this.field_70181_x *= 0.5;
                    this.field_70179_y *= 0.5;
                }
                this.field_70159_w *= 0.99;
                this.field_70181_x *= 0.95;
                this.field_70179_y *= 0.99;
            }
        } else {
            double d12;
            double d5;
            this.field_70181_x -= 0.04;
            double d4 = Math.sqrt(this.field_70159_w * this.field_70159_w + this.field_70179_y * this.field_70179_y);
            if (d4 > 0.35) {
                d5 = 0.35 / d4;
                this.field_70159_w *= d5;
                this.field_70179_y *= d5;
                d4 = 0.35;
            }
            if (d4 > d3 && this.speedMultiplier < 0.35) {
                this.speedMultiplier += (0.35 - this.speedMultiplier) / 35.0;
                if (this.speedMultiplier > 0.35) {
                    this.speedMultiplier = 0.35;
                }
            } else {
                this.speedMultiplier -= (this.speedMultiplier - 0.07) / 35.0;
                if (this.speedMultiplier < 0.07) {
                    this.speedMultiplier = 0.07;
                }
            }
            if (this.field_70122_E) {
                this.field_70159_w *= 0.5;
                this.field_70181_x *= 0.5;
                this.field_70179_y *= 0.5;
            }
            this.func_70091_d(this.field_70159_w, this.field_70181_x, this.field_70179_y);
            this.field_70159_w *= 0.99;
            this.field_70181_x *= 0.95;
            this.field_70179_y *= 0.99;
            this.field_70125_A = 0.0f;
            d5 = this.field_70177_z;
            double d11 = this.field_70169_q - this.field_70165_t;
            double d10 = this.field_70166_s - this.field_70161_v;
            if (d11 * d11 + d10 * d10 > 0.001) {
                d5 = (float)(Math.atan2(d10, d11) * 180.0 / Math.PI);
            }
            if ((d12 = MathHelper.func_76138_g((double)(d5 - (double)this.field_70177_z))) > 20.0) {
                d12 = 20.0;
            }
            if (d12 < -20.0) {
                d12 = -20.0;
            }
            this.field_70177_z = (float)((double)this.field_70177_z + d12);
            this.func_70101_b(this.field_70177_z, this.field_70125_A);
            if (!this.field_70170_p.field_72995_K) {
                if (MCH_Config.Collision_DestroyBlock.prmBool) {
                    for (int l = 0; l < 4; ++l) {
                        int i1 = MathHelper.func_76128_c((double)(this.field_70165_t + ((double)(l % 2) - 0.5) * 0.8));
                        int j1 = MathHelper.func_76128_c((double)(this.field_70161_v + ((double)(l / 2) - 0.5) * 0.8));
                        for (int k1 = 0; k1 < 2; ++k1) {
                            int l1 = MathHelper.func_76128_c((double)this.field_70163_u) + k1;
                            if (!W_WorldFunc.isEqualBlock((World)this.field_70170_p, (int)i1, (int)l1, (int)j1, (Block)W_Block.getSnowLayer())) continue;
                            this.field_70170_p.func_147468_f(i1, l1, j1);
                        }
                    }
                }
                if (this.field_70153_n != null && this.field_70153_n.field_70128_L) {
                    this.field_70153_n = null;
                }
            }
        }
        this.updateCameraPosition(false);
        if (this.countWait > 0) {
            --this.countWait;
        }
        if (this.countWait < 0) {
            ++this.countWait;
        }
        this.weaponCAS.update(this.countWait);
        if (this.lastRiddenByEntity != null && this.field_70153_n == null) {
            if (this.retryRiddenByEntityCheck < 3) {
                ++this.retryRiddenByEntityCheck;
                this.setUnmoundPosition(this.lastRiddenByEntity);
            } else {
                this.unmountEntity();
            }
        } else {
            this.retryRiddenByEntityCheck = 0;
        }
        if (this.field_70153_n != null) {
            this.lastRiddenByEntity = this.field_70153_n;
        }
    }

    public void setUnmoundPosition(Entity e) {
        if (e == null) {
            return;
        }
        float yaw = this.field_70177_z;
        double d0 = Math.sin((double)yaw * Math.PI / 180.0) * 1.2;
        double d1 = -Math.cos((double)yaw * Math.PI / 180.0) * 1.2;
        e.func_70107_b(this.field_70165_t + d0, this.field_70163_u + this.func_70042_X() + e.func_70033_W() + 1.0, this.field_70161_v + d1);
        e.field_70142_S = e.field_70169_q = e.field_70165_t;
        e.field_70137_T = e.field_70167_r = e.field_70163_u;
        e.field_70136_U = e.field_70166_s = e.field_70161_v;
    }

    public void unmountEntity() {
        this.camera.setMode(0, 0);
        this.camera.setCameraZoom(1.0f);
        if (!this.field_70170_p.field_72995_K) {
            if (this.field_70153_n != null) {
                if (!this.field_70153_n.field_70128_L) {
                    this.field_70153_n.func_70078_a(null);
                }
            } else if (this.lastRiddenByEntity != null && !this.lastRiddenByEntity.field_70128_L) {
                this.camera.updateViewer(0, this.lastRiddenByEntity);
                this.setUnmoundPosition(this.lastRiddenByEntity);
            }
        }
        this.field_70153_n = null;
        this.lastRiddenByEntity = null;
    }

    public void updateCameraPosition(boolean foreceUpdate) {
        if (foreceUpdate || this.field_70153_n != null && this.camera != null) {
            double x = -Math.sin((double)this.field_70177_z * Math.PI / 180.0) * 0.6;
            double z = Math.cos((double)this.field_70177_z * Math.PI / 180.0) * 0.6;
            this.camera.setPosition(this.field_70165_t + x, this.field_70163_u + 0.7, this.field_70161_v + z);
        }
    }

    @SideOnly(value=Side.CLIENT)
    public void zoomCamera(float f) {
        float z = this.camera.getCameraZoom();
        if ((z += f) < 1.0f) {
            z = 1.0f;
        }
        if (z > 10.0f) {
            z = 10.0f;
        }
        this.camera.setCameraZoom(z);
    }

    public void func_70043_V() {
        if (this.field_70153_n != null) {
            double x = Math.sin((double)this.field_70177_z * Math.PI / 180.0) * 0.5;
            double z = -Math.cos((double)this.field_70177_z * Math.PI / 180.0) * 0.5;
            this.field_70153_n.func_70107_b(this.field_70165_t + x, this.field_70163_u + this.func_70042_X() + this.field_70153_n.func_70033_W(), this.field_70161_v + z);
        }
    }

    public void switchWeapon(int id) {
    }

    public boolean useCurrentWeapon(int option1, int option2) {
        if (this.countWait == 0 && this.field_70153_n != null && this.weaponCAS.shot(this.field_70153_n, this.camera.posX, this.camera.posY, this.camera.posZ, option1, option2)) {
            this.countWait = this.weaponCAS.interval;
            if (this.field_70170_p.field_72995_K) {
                this.countWait += this.countWait > 0 ? 10 : -10;
            } else {
                W_WorldFunc.MOD_playSoundEffect((World)this.field_70170_p, (double)this.field_70165_t, (double)this.field_70163_u, (double)this.field_70161_v, (String)"gltd", (float)0.5f, (float)1.0f);
            }
            return true;
        }
        return false;
    }

    protected void func_70014_b(NBTTagCompound par1NBTTagCompound) {
    }

    protected void func_70037_a(NBTTagCompound par1NBTTagCompound) {
    }

    @SideOnly(value=Side.CLIENT)
    public float func_70053_R() {
        return 0.0f;
    }

    public boolean func_130002_c(EntityPlayer player) {
        if (this.field_70153_n != null && this.field_70153_n instanceof EntityPlayer && this.field_70153_n != player) {
            return true;
        }
        player.field_70177_z = MathHelper.func_76142_g((float)this.field_70177_z);
        player.field_70125_A = MathHelper.func_76142_g((float)this.field_70125_A);
        if (!this.field_70170_p.field_72995_K) {
            player.func_70078_a((Entity)this);
        } else {
            this.zoomDir = true;
            this.camera.setCameraZoom(1.0f);
            if (this.countWait > 0) {
                this.countWait = -this.countWait;
            }
            if (this.countWait > -60) {
                this.countWait = -60;
            }
        }
        this.updateCameraPosition(true);
        return true;
    }

    public void setDamageTaken(int par1) {
        this.field_70180_af.func_75692_b(19, (Object)par1);
    }

    public int getDamageTaken() {
        return this.field_70180_af.func_75679_c(19);
    }

    public void setTimeSinceHit(int par1) {
        this.field_70180_af.func_75692_b(17, (Object)par1);
    }

    public int getTimeSinceHit() {
        return this.field_70180_af.func_75679_c(17);
    }

    public void setForwardDirection(int par1) {
        this.field_70180_af.func_75692_b(18, (Object)par1);
    }

    public int getForwardDirection() {
        return 0;
    }

    @SideOnly(value=Side.CLIENT)
    public void func_70270_d(boolean par1) {
        this.field_70279_a = par1;
    }
}

