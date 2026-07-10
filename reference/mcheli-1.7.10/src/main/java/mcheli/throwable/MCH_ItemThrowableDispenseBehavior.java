package mcheli.throwable;

import mcheli.MCH_Lib;
import mcheli.wrapper.W_BlockDispenser;
import net.minecraft.dispenser.BehaviorDefaultDispenseItem;
import net.minecraft.dispenser.IBlockSource;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

public class MCH_ItemThrowableDispenseBehavior extends BehaviorDefaultDispenseItem {
   public ItemStack dispenseStack(IBlockSource bs, ItemStack itemStack) {
      EnumFacing enumfacing = W_BlockDispenser.getFacing(bs.getBlockMetadata());
      double x = bs.getX() + enumfacing.getFrontOffsetX() * 2.0;
      double y = bs.getY() + enumfacing.getFrontOffsetY() * 2.0;
      double z = bs.getZ() + enumfacing.getFrontOffsetZ() * 2.0;
      if (itemStack.getItem() instanceof MCH_ItemThrowable) {
         MCH_ThrowableInfo info = MCH_ThrowableInfoManager.get(itemStack.getItem());
         if (info != null) {
            bs.getWorld().playSound(x, y, z, "random.bow", 0.5F, 0.4F / (bs.getWorld().rand.nextFloat() * 0.4F + 0.8F), false);
            if (!bs.getWorld().isRemote) {
               MCH_Lib.DbgLog(bs.getWorld(), "MCH_ItemThrowableDispenseBehavior.dispenseStack(%s)", info.name);
               MCH_EntityThrowable entity = new MCH_EntityThrowable(bs.getWorld(), x, y, z);
               entity.motionX = (double)enumfacing.getFrontOffsetX() * info.dispenseAcceleration;
               entity.motionY = (double)enumfacing.getFrontOffsetY() * info.dispenseAcceleration;
               entity.motionZ = (double)enumfacing.getFrontOffsetZ() * info.dispenseAcceleration;
               entity.setInfo(info);
               bs.getWorld().spawnEntityInWorld(entity);
               itemStack.splitStack(1);
            }
         }
      }

      return itemStack;
   }
}
