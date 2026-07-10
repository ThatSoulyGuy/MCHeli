/*
 * Decompiled with CFR 0.152.
 */
package mcheli.uav;

import java.util.List;
import mcheli.MCH_Lib;
import mcheli.uav.MCH_EntityUavStation;
import mcheli.wrapper.W_Item;
import mcheli.wrapper.W_MovingObjectPosition;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class MCH_ItemUavStation
extends W_Item {
    public static int UAV_STATION_KIND_NUM = 2;
    public final int UavStationKind;

    public MCH_ItemUavStation(int par1, int kind) {
        super(par1);
        this.field_77777_bU = 1;
        this.UavStationKind = kind;
    }

    public MCH_EntityUavStation createUavStation(World world, double x, double y, double z, int kind) {
        MCH_EntityUavStation uavst = new MCH_EntityUavStation(world);
        uavst.func_70107_b(x, y + (double)uavst.field_70129_M, z);
        uavst.field_70169_q = x;
        uavst.field_70167_r = y;
        uavst.field_70166_s = z;
        uavst.setKind(kind);
        return uavst;
    }

    public ItemStack func_77659_a(ItemStack par1ItemStack, World par2World, EntityPlayer par3EntityPlayer) {
        int i;
        float f8;
        float f6;
        double d3;
        float f5;
        float f = 1.0f;
        float f1 = par3EntityPlayer.field_70127_C + (par3EntityPlayer.field_70125_A - par3EntityPlayer.field_70127_C) * f;
        float f2 = par3EntityPlayer.field_70126_B + (par3EntityPlayer.field_70177_z - par3EntityPlayer.field_70126_B) * f;
        double d0 = par3EntityPlayer.field_70169_q + (par3EntityPlayer.field_70165_t - par3EntityPlayer.field_70169_q) * (double)f;
        double d1 = par3EntityPlayer.field_70167_r + (par3EntityPlayer.field_70163_u - par3EntityPlayer.field_70167_r) * (double)f + 1.62 - (double)par3EntityPlayer.field_70129_M;
        double d2 = par3EntityPlayer.field_70166_s + (par3EntityPlayer.field_70161_v - par3EntityPlayer.field_70166_s) * (double)f;
        Vec3 vec3 = W_WorldFunc.getWorldVec3((World)par2World, (double)d0, (double)d1, (double)d2);
        float f3 = MathHelper.func_76134_b((float)(-f2 * ((float)Math.PI / 180) - (float)Math.PI));
        float f4 = MathHelper.func_76126_a((float)(-f2 * ((float)Math.PI / 180) - (float)Math.PI));
        float f7 = f4 * (f5 = -MathHelper.func_76134_b((float)(-f1 * ((float)Math.PI / 180))));
        Vec3 vec31 = vec3.func_72441_c((double)f7 * (d3 = 5.0), (double)(f6 = MathHelper.func_76126_a((float)(-f1 * ((float)Math.PI / 180)))) * d3, (double)(f8 = f3 * f5) * d3);
        MovingObjectPosition movingobjectposition = W_WorldFunc.clip((World)par2World, (Vec3)vec3, (Vec3)vec31, (boolean)true);
        if (movingobjectposition == null) {
            return par1ItemStack;
        }
        Vec3 vec32 = par3EntityPlayer.func_70676_i(f);
        boolean flag = false;
        float f9 = 1.0f;
        List list = par2World.func_72839_b((Entity)par3EntityPlayer, par3EntityPlayer.field_70121_D.func_72321_a(vec32.field_72450_a * d3, vec32.field_72448_b * d3, vec32.field_72449_c * d3).func_72314_b((double)f9, (double)f9, (double)f9));
        for (i = 0; i < list.size(); ++i) {
            float f10;
            AxisAlignedBB axisalignedbb;
            Entity entity = (Entity)list.get(i);
            if (!entity.func_70067_L() || !(axisalignedbb = entity.field_70121_D.func_72314_b((double)(f10 = entity.func_70111_Y()), (double)f10, (double)f10)).func_72318_a(vec3)) continue;
            flag = true;
        }
        if (flag) {
            return par1ItemStack;
        }
        if (W_MovingObjectPosition.isHitTypeTile((MovingObjectPosition)movingobjectposition)) {
            i = movingobjectposition.field_72311_b;
            int j = movingobjectposition.field_72312_c;
            int k = movingobjectposition.field_72309_d;
            MCH_EntityUavStation entityUavSt = this.createUavStation(par2World, (double)((float)i + 0.5f), (double)((float)j + 1.0f), (double)((float)k + 0.5f), this.UavStationKind);
            int rot = (int)(MCH_Lib.getRotate360((double)par3EntityPlayer.field_70177_z) + 45.0);
            entityUavSt.field_70177_z = rot / 90 * 90 - 180;
            entityUavSt.initUavPostion();
            if (!par2World.func_72945_a((Entity)entityUavSt, entityUavSt.field_70121_D.func_72314_b(-0.1, -0.1, -0.1)).isEmpty()) {
                return par1ItemStack;
            }
            if (!par2World.field_72995_K) {
                par2World.func_72838_d((Entity)entityUavSt);
            }
            if (!par3EntityPlayer.field_71075_bZ.field_75098_d) {
                --par1ItemStack.field_77994_a;
            }
        }
        return par1ItemStack;
    }
}

