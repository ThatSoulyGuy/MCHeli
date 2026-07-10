package mcheli.wrapper;

import net.minecraft.util.AxisAlignedBB;

public class W_AxisAlignedBB {
   public static AxisAlignedBB getAABB(double d0, double d1, double d2, double d3, double d4, double d5) {
      return AxisAlignedBB.getBoundingBox(d0, d1, d2, d3, d4, d5);
   }
}
