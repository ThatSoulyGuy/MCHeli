/*
 * Decompiled with CFR 0.152.
 */
package mcheli.tank;

import mcheli.MCH_Lib;
import mcheli.aircraft.MCH_AircraftInfo;
import mcheli.aircraft.MCH_ItemAircraft;
import mcheli.tank.MCH_EntityTank;
import mcheli.tank.MCH_TankInfo;
import mcheli.tank.MCH_TankInfoManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class MCH_ItemTank
extends MCH_ItemAircraft {
    public MCH_ItemTank(int par1) {
        super(par1);
        this.field_77777_bU = 1;
    }

    public MCH_AircraftInfo getAircraftInfo() {
        return MCH_TankInfoManager.getFromItem((Item)this);
    }

    public MCH_EntityTank createAircraft(World world, double x, double y, double z, ItemStack itemStack) {
        MCH_TankInfo info = MCH_TankInfoManager.getFromItem((Item)this);
        if (info == null) {
            MCH_Lib.Log((World)world, (String)"##### MCH_EntityTank Tank info null %s", (Object[])new Object[]{this.func_77658_a()});
            return null;
        }
        MCH_EntityTank tank = new MCH_EntityTank(world);
        tank.func_70107_b(x, y + (double)tank.field_70129_M, z);
        tank.field_70169_q = x;
        tank.field_70167_r = y;
        tank.field_70166_s = z;
        tank.camera.setPosition(x, y, z);
        tank.setTypeName(info.name);
        if (!world.field_72995_K) {
            tank.setTextureName(info.getTextureName());
        }
        return tank;
    }
}

