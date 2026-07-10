/*
 * Decompiled with CFR 0.152.
 */
package mcheli.particles;

import java.util.ArrayList;
import java.util.List;
import mcheli.wrapper.W_Blocks;
import mcheli.wrapper.W_EntityFX;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ReportedException;
import net.minecraft.world.World;

public abstract class MCH_EntityParticleBase
extends W_EntityFX {
    public boolean isEffectedWind;
    public boolean diffusible;
    public boolean toWhite;
    public float particleMaxScale;
    public float gravity;
    public float moutionYUpAge;

    public MCH_EntityParticleBase(World par1World, double x, double y, double z, double mx, double my, double mz) {
        super(par1World, x, y, z, mx, my, mz);
        this.field_70159_w = mx;
        this.field_70181_x = my;
        this.field_70179_y = mz;
        this.isEffectedWind = false;
        this.particleMaxScale = this.field_70544_f;
    }

    public MCH_EntityParticleBase setParticleScale(float scale) {
        this.field_70544_f = scale;
        return this;
    }

    public void setParticleMaxAge(int age) {
        this.field_70547_e = age;
    }

    public void func_70536_a(int par1) {
        this.field_94054_b = par1 % 8;
        this.field_94055_c = par1 / 8;
    }

    public int func_70537_b() {
        return 2;
    }

    public void func_70091_d(double par1, double par3, double par5) {
        if (this.field_70145_X) {
            this.field_70121_D.func_72317_d(par1, par3, par5);
            this.field_70165_t = (this.field_70121_D.field_72340_a + this.field_70121_D.field_72336_d) / 2.0;
            this.field_70163_u = this.field_70121_D.field_72338_b + (double)this.field_70129_M - (double)this.field_70139_V;
            this.field_70161_v = (this.field_70121_D.field_72339_c + this.field_70121_D.field_72334_f) / 2.0;
        } else {
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
            boolean flag = false;
            List list = this.field_70170_p.func_72945_a((Entity)this, this.field_70121_D.func_72321_a(par1, par3, par5));
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
            double d12 = this.field_70165_t - d3;
            double d10 = this.field_70163_u - d4;
            double d11 = this.field_70161_v - d5;
            try {
                this.doBlockCollisions();
            }
            catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.func_85055_a((Throwable)throwable, (String)"Checking entity block collision");
                CrashReportCategory crashreportcategory = crashreport.func_85058_a("Entity being checked for collision");
                this.func_85029_a(crashreportcategory);
                throw new ReportedException(crashreport);
            }
            this.field_70170_p.field_72984_F.func_76319_b();
        }
    }

    public List getCollidingBoundingBoxes(Entity par1Entity, AxisAlignedBB par2AxisAlignedBB) {
        ArrayList collidingBoundingBoxes = new ArrayList();
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
                    Block block = k1 >= -30000000 && k1 < 30000000 && l1 >= -30000000 && l1 < 30000000 ? W_WorldFunc.getBlock((World)this.field_70170_p, (int)k1, (int)i2, (int)l1) : W_Blocks.field_150348_b;
                    block.func_149743_a(this.field_70170_p, k1, i2, l1, par2AxisAlignedBB, collidingBoundingBoxes, par1Entity);
                }
            }
        }
        return collidingBoundingBoxes;
    }
}

