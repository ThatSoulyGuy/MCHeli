/*
 * Decompiled with CFR 0.152.
 */
package mcheli.tank;

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
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class MCH_EntityWheel
extends W_Entity {
    private MCH_EntityAircraft parents;
    public Vec3 pos;
    boolean isPlus;

    public MCH_EntityWheel(World w) {
        super(w);
        this.func_70105_a(1.0f, 1.0f);
        this.field_70138_W = 1.5f;
        this.field_70178_ae = true;
        this.isPlus = false;
    }

    public void setWheelPos(Vec3 pos, Vec3 weightedCenter) {
        this.pos = pos;
        this.isPlus = pos.field_72449_c >= weightedCenter.field_72449_c;
    }

    public void func_71027_c(int p_71027_1_) {
    }

    public MCH_EntityAircraft getParents() {
        return this.parents;
    }

    public void setParents(MCH_EntityAircraft parents) {
        this.parents = parents;
    }

    protected void func_70037_a(NBTTagCompound p_70037_1_) {
        this.func_70106_y();
    }

    protected void func_70014_b(NBTTagCompound p_70014_1_) {
    }

    public void func_70091_d(double parX, double parY, double parZ) {
        this.field_70170_p.field_72984_F.func_76320_a("move");
        this.field_70139_V *= 0.4f;
        double nowPosX = this.field_70165_t;
        double nowPosY = this.field_70163_u;
        double nowPosZ = this.field_70161_v;
        double mx = parX;
        double my = parY;
        double mz = parZ;
        AxisAlignedBB axisalignedbb = this.field_70121_D.func_72329_c();
        List list = this.getCollidingBoundingBoxes((Entity)this, this.field_70121_D.func_72321_a(parX, parY, parZ));
        for (int i = 0; i < list.size(); ++i) {
            parY = ((AxisAlignedBB)list.get(i)).func_72323_b(this.field_70121_D, parY);
        }
        this.field_70121_D.func_72317_d(0.0, parY, 0.0);
        boolean flag1 = this.field_70122_E || my != parY && my < 0.0;
        for (int i = 0; i < list.size(); ++i) {
            parX = ((AxisAlignedBB)list.get(i)).func_72316_a(this.field_70121_D, parX);
        }
        this.field_70121_D.func_72317_d(parX, 0.0, 0.0);
        for (int j = 0; j < list.size(); ++j) {
            parZ = ((AxisAlignedBB)list.get(j)).func_72322_c(this.field_70121_D, parZ);
        }
        this.field_70121_D.func_72317_d(0.0, 0.0, parZ);
        if (this.field_70138_W > 0.0f && flag1 && this.field_70139_V < 0.05f && (mx != parX || mz != parZ)) {
            int k;
            double bkParX = parX;
            double bkParY = parY;
            double bkParZ = parZ;
            parX = mx;
            parY = this.field_70138_W;
            parZ = mz;
            AxisAlignedBB axisalignedbb1 = this.field_70121_D.func_72329_c();
            this.field_70121_D.func_72328_c(axisalignedbb);
            list = this.getCollidingBoundingBoxes((Entity)this, this.field_70121_D.func_72321_a(mx, parY, mz));
            for (k = 0; k < list.size(); ++k) {
                parY = ((AxisAlignedBB)list.get(k)).func_72323_b(this.field_70121_D, parY);
            }
            this.field_70121_D.func_72317_d(0.0, parY, 0.0);
            for (k = 0; k < list.size(); ++k) {
                parX = ((AxisAlignedBB)list.get(k)).func_72316_a(this.field_70121_D, parX);
            }
            this.field_70121_D.func_72317_d(parX, 0.0, 0.0);
            for (k = 0; k < list.size(); ++k) {
                parZ = ((AxisAlignedBB)list.get(k)).func_72322_c(this.field_70121_D, parZ);
            }
            this.field_70121_D.func_72317_d(0.0, 0.0, parZ);
            parY = -this.field_70138_W;
            for (k = 0; k < list.size(); ++k) {
                parY = ((AxisAlignedBB)list.get(k)).func_72323_b(this.field_70121_D, parY);
            }
            this.field_70121_D.func_72317_d(0.0, parY, 0.0);
            if (bkParX * bkParX + bkParZ * bkParZ >= parX * parX + parZ * parZ) {
                parX = bkParX;
                parY = bkParY;
                parZ = bkParZ;
                this.field_70121_D.func_72328_c(axisalignedbb1);
            }
        }
        this.field_70170_p.field_72984_F.func_76319_b();
        this.field_70170_p.field_72984_F.func_76320_a("rest");
        this.field_70165_t = (this.field_70121_D.field_72340_a + this.field_70121_D.field_72336_d) / 2.0;
        this.field_70163_u = this.field_70121_D.field_72338_b + (double)this.field_70129_M - (double)this.field_70139_V;
        this.field_70161_v = (this.field_70121_D.field_72339_c + this.field_70121_D.field_72334_f) / 2.0;
        this.field_70123_F = mx != parX || mz != parZ;
        this.field_70124_G = my != parY;
        this.field_70122_E = my != parY && my < 0.0;
        this.field_70132_H = this.field_70123_F || this.field_70124_G;
        this.func_70064_a(parY, this.field_70122_E);
        if (mx != parX) {
            this.field_70159_w = 0.0;
        }
        if (my != parY) {
            this.field_70181_x = 0.0;
        }
        if (mz != parZ) {
            this.field_70179_y = 0.0;
        }
        try {
            this.doBlockCollisions();
        }
        catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.func_85055_a((Throwable)throwable, (String)"Checking entity tile collision");
            CrashReportCategory crashreportcategory = crashreport.func_85058_a("Entity being checked for collision");
            this.func_85029_a(crashreportcategory);
        }
        this.field_70170_p.field_72984_F.func_76319_b();
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
                if (!par1Entity.field_70170_p.func_72899_e(k1, 64, l1)) continue;
                for (int i2 = k - 1; i2 < l; ++i2) {
                    Block block = W_WorldFunc.getBlock((World)par1Entity.field_70170_p, (int)k1, (int)i2, (int)l1);
                    if (block == null) continue;
                    block.func_149743_a(par1Entity.field_70170_p, k1, i2, l1, par2AxisAlignedBB, collidingBoundingBoxes, par1Entity);
                }
            }
        }
        double d0 = 0.25;
        List list = par1Entity.field_70170_p.func_72839_b(par1Entity, par2AxisAlignedBB.func_72314_b(d0, d0, d0));
        for (int j2 = 0; j2 < list.size(); ++j2) {
            Entity entity = (Entity)list.get(j2);
            if (W_Lib.isEntityLivingBase((Entity)entity) || entity instanceof MCH_EntitySeat || entity instanceof MCH_EntityHitBox || entity == this.parents) continue;
            AxisAlignedBB axisalignedbb1 = entity.func_70046_E();
            if (axisalignedbb1 != null && axisalignedbb1.func_72326_a(par2AxisAlignedBB)) {
                collidingBoundingBoxes.add(axisalignedbb1);
            }
            if ((axisalignedbb1 = par1Entity.func_70114_g(entity)) == null || !axisalignedbb1.func_72326_a(par2AxisAlignedBB)) continue;
            collidingBoundingBoxes.add(axisalignedbb1);
        }
        return collidingBoundingBoxes;
    }
}

