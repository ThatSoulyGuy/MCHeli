/*
 * Decompiled with CFR 0.152.
 */
package mcheli.throwable;

import mcheli.MCH_Lib;
import mcheli.throwable.MCH_EntityThrowable;
import mcheli.throwable.MCH_ItemThrowable;
import mcheli.throwable.MCH_ThrowableInfo;
import mcheli.throwable.MCH_ThrowableInfoManager;
import mcheli.wrapper.W_BlockDispenser;
import net.minecraft.dispenser.BehaviorDefaultDispenseItem;
import net.minecraft.dispenser.IBlockSource;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

public class MCH_ItemThrowableDispenseBehavior
extends BehaviorDefaultDispenseItem {
    public ItemStack func_82487_b(IBlockSource bs, ItemStack itemStack) {
        MCH_ThrowableInfo info;
        EnumFacing enumfacing = W_BlockDispenser.getFacing((int)bs.func_82620_h());
        double x = bs.func_82615_a() + (double)enumfacing.func_82601_c() * 2.0;
        double y = bs.func_82617_b() + (double)enumfacing.func_96559_d() * 2.0;
        double z = bs.func_82616_c() + (double)enumfacing.func_82599_e() * 2.0;
        if (itemStack.func_77973_b() instanceof MCH_ItemThrowable && (info = MCH_ThrowableInfoManager.get((Item)itemStack.func_77973_b())) != null) {
            bs.func_82618_k().func_72980_b(x, y, z, "random.bow", 0.5f, 0.4f / (bs.func_82618_k().field_73012_v.nextFloat() * 0.4f + 0.8f), false);
            if (!bs.func_82618_k().field_72995_K) {
                MCH_Lib.DbgLog((World)bs.func_82618_k(), (String)"MCH_ItemThrowableDispenseBehavior.dispenseStack(%s)", (Object[])new Object[]{info.name});
                MCH_EntityThrowable entity = new MCH_EntityThrowable(bs.func_82618_k(), x, y, z);
                entity.field_70159_w = (double)enumfacing.func_82601_c() * (double)info.dispenseAcceleration;
                entity.field_70181_x = (double)enumfacing.func_96559_d() * (double)info.dispenseAcceleration;
                entity.field_70179_y = (double)enumfacing.func_82599_e() * (double)info.dispenseAcceleration;
                entity.setInfo(info);
                bs.func_82618_k().func_72838_d((Entity)entity);
                itemStack.func_77979_a(1);
            }
        }
        return itemStack;
    }
}

