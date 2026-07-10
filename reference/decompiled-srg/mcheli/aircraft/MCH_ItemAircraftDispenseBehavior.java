/*
 * Decompiled with CFR 0.152.
 */
package mcheli.aircraft;

import mcheli.MCH_Lib;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_ItemAircraft;
import mcheli.wrapper.W_BlockDispenser;
import net.minecraft.dispenser.BehaviorDefaultDispenseItem;
import net.minecraft.dispenser.IBlockSource;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

public class MCH_ItemAircraftDispenseBehavior
extends BehaviorDefaultDispenseItem {
    public ItemStack func_82487_b(IBlockSource bs, ItemStack itemStack) {
        MCH_EntityAircraft ac;
        EnumFacing enumfacing = W_BlockDispenser.getFacing((int)bs.func_82620_h());
        double x = bs.func_82615_a() + (double)enumfacing.func_82601_c() * 2.0;
        double y = bs.func_82617_b() + (double)enumfacing.func_96559_d() * 2.0;
        double z = bs.func_82616_c() + (double)enumfacing.func_82599_e() * 2.0;
        if (itemStack.func_77973_b() instanceof MCH_ItemAircraft && (ac = ((MCH_ItemAircraft)itemStack.func_77973_b()).onTileClick(itemStack, bs.func_82618_k(), 0.0f, (int)x, (int)y, (int)z)) != null && !ac.isUAV()) {
            if (!bs.func_82618_k().field_72995_K) {
                ac.getAcDataFromItem(itemStack);
                bs.func_82618_k().func_72838_d((Entity)ac);
            }
            itemStack.func_77979_a(1);
            MCH_Lib.DbgLog((World)bs.func_82618_k(), (String)("dispenseStack:x=%.1f,y=%.1f,z=%.1f;dir=%s:item=" + itemStack.func_82833_r()), (Object[])new Object[]{x, y, z, enumfacing.toString()});
        }
        return itemStack;
    }
}

