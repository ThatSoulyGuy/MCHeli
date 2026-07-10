/*
 * Decompiled with CFR 0.152.
 */
package mcheli.aircraft;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.List;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_EntityHitBox;
import mcheli.aircraft.MCH_EntitySeat;
import mcheli.wrapper.W_Entity;
import mcheli.wrapper.W_Lib;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ReportedException;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class MCH_EntityHide
extends W_Entity {
    private MCH_EntityAircraft ac;
    private Entity user;
    private int paraPosRotInc;
    private double paraX;
    private double paraY;
    private double paraZ;
    private double paraYaw;
    private double paraPitch;
    @SideOnly(value=Side.CLIENT)
    private double velocityX;
    @SideOnly(value=Side.CLIENT)
    private double velocityY;
    @SideOnly(value=Side.CLIENT)
    private double velocityZ;

    public MCH_EntityHide(World par1World) {
        super(par1World);
        this.func_70105_a(1.0f, 1.0f);
        this.field_70156_m = true;
        this.field_70129_M = this.field_70131_O / 2.0f;
        this.user = null;
        this.field_70179_y = 0.0;
        this.field_70181_x = 0.0;
        this.field_70159_w = 0.0;
    }

    public MCH_EntityHide(World par1World, double x, double y, double z) {
        this(par1World);
        this.field_70165_t = x;
        this.field_70163_u = y;
        this.field_70161_v = z;
    }

    protected void func_70088_a() {
        super.func_70088_a();
        this.createRopeIndex(-1);
        this.func_70096_w().func_75682_a(31, (Object)new Integer(0));
    }

    public void setParent(MCH_EntityAircraft ac, Entity user, int ropeIdx) {
        this.ac = ac;
        this.setRopeIndex(ropeIdx);
        this.user = user;
    }

    protected boolean func_70041_e_() {
        return false;
    }

    public AxisAlignedBB func_70114_g(Entity par1Entity) {
        return par1Entity.field_70121_D;
    }

    public AxisAlignedBB func_70046_E() {
        return this.field_70121_D;
    }

    public boolean func_70104_M() {
        return true;
    }

    public double func_70042_X() {
        return (double)this.field_70131_O * 0.0 - 0.3;
    }

    public boolean func_70097_a(DamageSource par1DamageSource, float par2) {
        return false;
    }

    public boolean func_70067_L() {
        return !this.field_70128_L;
    }

    protected void func_70014_b(NBTTagCompound nbt) {
    }

    protected void func_70037_a(NBTTagCompound nbt) {
    }

    @SideOnly(value=Side.CLIENT)
    public float func_70053_R() {
        return 0.0f;
    }

    public boolean func_130002_c(EntityPlayer par1EntityPlayer) {
        return false;
    }

    public void createRopeIndex(int defaultValue) {
        this.func_70096_w().func_75682_a(30, (Object)new Integer(defaultValue));
    }

    public int getRopeIndex() {
        return this.func_70096_w().func_75679_c(30);
    }

    public void setRopeIndex(int value) {
        this.func_70096_w().func_75692_b(30, (Object)new Integer(value));
    }

    @SideOnly(value=Side.CLIENT)
    public void func_70056_a(double par1, double par3, double par5, float par7, float par8, int par9) {
        this.paraPosRotInc = par9 + 10;
        this.paraX = par1;
        this.paraY = par3;
        this.paraZ = par5;
        this.paraYaw = par7;
        this.paraPitch = par8;
        this.field_70159_w = this.velocityX;
        this.field_70181_x = this.velocityY;
        this.field_70179_y = this.velocityZ;
    }

    @SideOnly(value=Side.CLIENT)
    public void func_70016_h(double par1, double par3, double par5) {
        this.velocityX = this.field_70159_w = par1;
        this.velocityY = this.field_70181_x = par3;
        this.velocityZ = this.field_70179_y = par5;
    }

    public void func_70106_y() {
        super.func_70106_y();
    }

    public void func_70071_h_() {
        Entity entity;
        int id;
        super.func_70071_h_();
        if (this.user != null && !this.field_70170_p.field_72995_K) {
            if (this.ac != null) {
                this.func_70096_w().func_75692_b(31, (Object)new Integer(this.ac.func_145782_y()));
            }
            this.user.func_70078_a((Entity)this);
            this.user = null;
        }
        if (this.ac == null && this.field_70170_p.field_72995_K && (id = this.func_70096_w().func_75679_c(31)) > 0 && (entity = this.field_70170_p.func_73045_a(id)) instanceof MCH_EntityAircraft) {
            this.ac = (MCH_EntityAircraft)entity;
        }
        this.field_70169_q = this.field_70165_t;
        this.field_70167_r = this.field_70163_u;
        this.field_70166_s = this.field_70161_v;
        this.field_70143_R = 0.0f;
        if (this.field_70153_n != null) {
            this.field_70153_n.field_70143_R = 0.0f;
        }
        if (this.ac != null) {
            if (!this.ac.isRepelling()) {
                this.func_70106_y();
            }
            if ((id = this.getRopeIndex()) >= 0) {
                Vec3 v = this.ac.getRopePos(id);
                this.field_70165_t = v.field_72450_a;
                this.field_70161_v = v.field_72449_c;
            }
        }
        this.func_70107_b(this.field_70165_t, this.field_70163_u, this.field_70161_v);
        if (this.field_70170_p.field_72995_K) {
            this.onUpdateClient();
        } else {
            this.onUpdateServer();
        }
    }

    public void onUpdateClient() {
        if (this.paraPosRotInc > 0) {
            double x = this.field_70165_t + (this.paraX - this.field_70165_t) / (double)this.paraPosRotInc;
            double y = this.field_70163_u + (this.paraY - this.field_70163_u) / (double)this.paraPosRotInc;
            double z = this.field_70161_v + (this.paraZ - this.field_70161_v) / (double)this.paraPosRotInc;
            double yaw = MathHelper.func_76138_g((double)(this.paraYaw - (double)this.field_70177_z));
            this.field_70177_z = (float)((double)this.field_70177_z + yaw / (double)this.paraPosRotInc);
            this.field_70125_A = (float)((double)this.field_70125_A + (this.paraPitch - (double)this.field_70125_A) / (double)this.paraPosRotInc);
            --this.paraPosRotInc;
            this.func_70107_b(x, y, z);
            this.func_70101_b(this.field_70177_z, this.field_70125_A);
            if (this.field_70153_n != null) {
                this.func_70101_b(this.field_70153_n.field_70126_B, this.field_70125_A);
            }
        } else {
            this.func_70107_b(this.field_70165_t + this.field_70159_w, this.field_70163_u + this.field_70181_x, this.field_70161_v + this.field_70179_y);
            this.field_70159_w *= 0.99;
            this.field_70181_x *= 0.95;
            this.field_70179_y *= 0.99;
        }
    }

    public void onUpdateServer() {
        this.field_70181_x -= this.field_70122_E ? 0.01 : 0.03;
        if (this.field_70122_E) {
            this.onGroundAndDead();
            return;
        }
        this.func_70091_d(this.field_70159_w, this.field_70181_x, this.field_70179_y);
        this.field_70181_x *= 0.9;
        this.field_70159_w *= 0.95;
        this.field_70179_y *= 0.95;
        int id = this.getRopeIndex();
        if (this.ac != null && id >= 0) {
            Vec3 v = this.ac.getRopePos(id);
            if (Math.abs(this.field_70163_u - v.field_72448_b) > (double)(Math.abs(this.ac.ropesLength) + 5.0f)) {
                this.onGroundAndDead();
            }
        }
        if (this.field_70153_n != null && this.field_70153_n.field_70128_L) {
            this.field_70153_n = null;
            this.func_70106_y();
        }
    }

    public List getCollidingBoundingBoxes(Entity par1Entity, AxisAlignedBB par2AxisAlignedBB) {
        ArrayList<AxisAlignedBB> collidingBoundingBoxes = new ArrayList<AxisAlignedBB>();
        collidingBoundingBoxes.clear();
        int i = MathHelper.func_76128_c((double)par2AxisAlignedBB.field_72340_a);
        int j = MathHelper.func_76128_c((double)(par2AxisAlignedBB.field_72336_d + 1.0));
        int k = MathHelper.func_76128_c((double)par2AxisAlignedBB.field_72338_b);
        int l = MathHelper.func_76128_c((double)(par2AxisAlignedBB.field_72337_e + 1.0));
        int i1 = MathHelper.func_76128_c((double)par2AxisAlignedBB.field_72339_c);
        int j1 = MathHelper.func_76128_c((double)(par2AxisAlignedBB.field_72334_f + 1.0));
        for (int k1 = i; k1 < j; ++k1) {
            for (int l1 = i1; l1 < j1; ++l1) {
                if (!this.field_70170_p.func_72899_e(k1, 64, l1)) continue;
                for (int i2 = k - 1; i2 < l; ++i2) {
                    Block block = W_WorldFunc.getBlock((World)this.field_70170_p, (int)k1, (int)i2, (int)l1);
                    if (block == null) continue;
                    block.func_149743_a(this.field_70170_p, k1, i2, l1, par2AxisAlignedBB, collidingBoundingBoxes, par1Entity);
                }
            }
        }
        double d0 = 0.25;
        List list = this.field_70170_p.func_72839_b(par1Entity, par2AxisAlignedBB.func_72314_b(d0, d0, d0));
        for (int j2 = 0; j2 < list.size(); ++j2) {
            Entity entity = (Entity)list.get(j2);
            if (W_Lib.isEntityLivingBase((Entity)entity) || entity instanceof MCH_EntitySeat || entity instanceof MCH_EntityHitBox) continue;
            AxisAlignedBB axisalignedbb1 = entity.func_70046_E();
            if (axisalignedbb1 != null && axisalignedbb1.func_72326_a(par2AxisAlignedBB)) {
                collidingBoundingBoxes.add(axisalignedbb1);
            }
            if ((axisalignedbb1 = par1Entity.func_70114_g(entity)) == null || !axisalignedbb1.func_72326_a(par2AxisAlignedBB)) continue;
            collidingBoundingBoxes.add(axisalignedbb1);
        }
        return collidingBoundingBoxes;
    }

    public void func_70091_d(double par1, double par3, double par5) {
        double d11;
        double d10;
        double d12;
        int j;
        this.field_70170_p.field_72984_F.func_76320_a("move");
        this.field_70139_V *= 0.4f;
        double d3 = this.field_70165_t;
        double d4 = this.field_70163_u;
        double d5 = this.field_70161_v;
        double d6 = par1;
        double d7 = par3;
        double d8 = par5;
        AxisAlignedBB axisalignedbb = this.field_70121_D.func_72329_c();
        List list = this.getCollidingBoundingBoxes((Entity)this, this.field_70121_D.func_72321_a(par1, par3, par5));
        for (int i = 0; i < list.size(); ++i) {
            par3 = ((AxisAlignedBB)list.get(i)).func_72323_b(this.field_70121_D, par3);
        }
        this.field_70121_D.func_72317_d(0.0, par3, 0.0);
        if (!this.field_70135_K && d7 != par3) {
            par5 = 0.0;
            par3 = 0.0;
            par1 = 0.0;
        }
        boolean flag1 = this.field_70122_E || d7 != par3 && d7 < 0.0;
        for (j = 0; j < list.size(); ++j) {
            par1 = ((AxisAlignedBB)list.get(j)).func_72316_a(this.field_70121_D, par1);
        }
        this.field_70121_D.func_72317_d(par1, 0.0, 0.0);
        if (!this.field_70135_K && d6 != par1) {
            par5 = 0.0;
            par3 = 0.0;
            par1 = 0.0;
        }
        for (j = 0; j < list.size(); ++j) {
            par5 = ((AxisAlignedBB)list.get(j)).func_72322_c(this.field_70121_D, par5);
        }
        this.field_70121_D.func_72317_d(0.0, 0.0, par5);
        if (!this.field_70135_K && d8 != par5) {
            par5 = 0.0;
            par3 = 0.0;
            par1 = 0.0;
        }
        if (this.field_70138_W > 0.0f && flag1 && this.field_70139_V < 0.05f && (d6 != par1 || d8 != par5)) {
            int k;
            d12 = par1;
            d10 = par3;
            d11 = par5;
            par1 = d6;
            par3 = this.field_70138_W;
            par5 = d8;
            AxisAlignedBB axisalignedbb1 = this.field_70121_D.func_72329_c();
            this.field_70121_D.func_72328_c(axisalignedbb);
            list = this.getCollidingBoundingBoxes((Entity)this, this.field_70121_D.func_72321_a(d6, par3, d8));
            for (k = 0; k < list.size(); ++k) {
                par3 = ((AxisAlignedBB)list.get(k)).func_72323_b(this.field_70121_D, par3);
            }
            this.field_70121_D.func_72317_d(0.0, par3, 0.0);
            if (!this.field_70135_K && d7 != par3) {
                par5 = 0.0;
                par3 = 0.0;
                par1 = 0.0;
            }
            for (k = 0; k < list.size(); ++k) {
                par1 = ((AxisAlignedBB)list.get(k)).func_72316_a(this.field_70121_D, par1);
            }
            this.field_70121_D.func_72317_d(par1, 0.0, 0.0);
            if (!this.field_70135_K && d6 != par1) {
                par5 = 0.0;
                par3 = 0.0;
                par1 = 0.0;
            }
            for (k = 0; k < list.size(); ++k) {
                par5 = ((AxisAlignedBB)list.get(k)).func_72322_c(this.field_70121_D, par5);
            }
            this.field_70121_D.func_72317_d(0.0, 0.0, par5);
            if (!this.field_70135_K && d8 != par5) {
                par5 = 0.0;
                par3 = 0.0;
                par1 = 0.0;
            }
            if (!this.field_70135_K && d7 != par3) {
                par5 = 0.0;
                par3 = 0.0;
                par1 = 0.0;
            } else {
                par3 = -this.field_70138_W;
                for (k = 0; k < list.size(); ++k) {
                    par3 = ((AxisAlignedBB)list.get(k)).func_72323_b(this.field_70121_D, par3);
                }
                this.field_70121_D.func_72317_d(0.0, par3, 0.0);
            }
            if (d12 * d12 + d11 * d11 >= par1 * par1 + par5 * par5) {
                par1 = d12;
                par3 = d10;
                par5 = d11;
                this.field_70121_D.func_72328_c(axisalignedbb1);
            }
        }
        this.field_70170_p.field_72984_F.func_76319_b();
        this.field_70170_p.field_72984_F.func_76320_a("rest");
        this.field_70165_t = (this.field_70121_D.field_72340_a + this.field_70121_D.field_72336_d) / 2.0;
        this.field_70163_u = this.field_70121_D.field_72338_b + (double)this.field_70129_M - (double)this.field_70139_V;
        this.field_70161_v = (this.field_70121_D.field_72339_c + this.field_70121_D.field_72334_f) / 2.0;
        this.field_70123_F = d6 != par1 || d8 != par5;
        this.field_70124_G = d7 != par3;
        this.field_70122_E = d7 != par3 && d7 < 0.0;
        this.field_70132_H = this.field_70123_F || this.field_70124_G;
        this.func_70064_a(par3, this.field_70122_E);
        if (d6 != par1) {
            this.field_70159_w = 0.0;
        }
        if (d7 != par3) {
            this.field_70181_x = 0.0;
        }
        if (d8 != par5) {
            this.field_70179_y = 0.0;
        }
        d12 = this.field_70165_t - d3;
        d10 = this.field_70163_u - d4;
        d11 = this.field_70161_v - d5;
        try {
            this.doBlockCollisions();
        }
        catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.func_85055_a((Throwable)throwable, (String)"Checking entity tile collision");
            CrashReportCategory crashreportcategory = crashreport.func_85058_a("Entity being checked for collision");
            this.func_85029_a(crashreportcategory);
            throw new ReportedException(crashreport);
        }
        this.field_70170_p.field_72984_F.func_76319_b();
    }

    public void onGroundAndDead() {
        this.field_70163_u += 0.5;
        this.func_70043_V();
        this.func_70106_y();
    }

    public void _updateRiderPosition() {
        if (this.field_70153_n != null) {
            double x = -Math.sin((double)this.field_70177_z * Math.PI / 180.0) * 0.1;
            double z = Math.cos((double)this.field_70177_z * Math.PI / 180.0) * 0.1;
            this.field_70153_n.func_70107_b(this.field_70165_t + x, this.field_70163_u + this.func_70042_X() + this.field_70153_n.func_70033_W(), this.field_70161_v + z);
        }
    }
}

