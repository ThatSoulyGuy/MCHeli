package mcheli.aircraft;

import mcheli.MCH_Lib;
import mcheli.wrapper.W_BlockDispenser;
import net.minecraft.dispenser.BehaviorDefaultDispenseItem;
import net.minecraft.dispenser.IBlockSource;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

public class MCH_ItemAircraftDispenseBehavior extends BehaviorDefaultDispenseItem {
   public ItemStack dispenseStack(IBlockSource bs, ItemStack itemStack) {
      EnumFacing enumfacing = W_BlockDispenser.getFacing(bs.getBlockMetadata());
      double x = bs.getX() + enumfacing.getFrontOffsetX() * 2.0;
      double y = bs.getY() + enumfacing.getFrontOffsetY() * 2.0;
      double z = bs.getZ() + enumfacing.getFrontOffsetZ() * 2.0;
      if (itemStack.getItem() instanceof MCH_ItemAircraft) {
         MCH_EntityAircraft ac = ((MCH_ItemAircraft)itemStack.getItem()).onTileClick(itemStack, bs.getWorld(), 0.0F, (int)x, (int)y, (int)z);
         if (ac != null && !ac.isUAV()) {
            if (!bs.getWorld().isRemote) {
               ac.getAcDataFromItem(itemStack);
               bs.getWorld().spawnEntityInWorld(ac);
            }

            itemStack.splitStack(1);
            MCH_Lib.DbgLog(bs.getWorld(), "dispenseStack:x=%.1f,y=%.1f,z=%.1f;dir=%s:item=" + itemStack.getDisplayName(), x, y, z, enumfacing.toString());
         }
      }

      return itemStack;
   }
}
