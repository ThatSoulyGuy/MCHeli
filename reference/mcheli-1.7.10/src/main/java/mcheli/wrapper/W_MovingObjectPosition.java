package mcheli.wrapper;

import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public class W_MovingObjectPosition {
   public static boolean isHitTypeEntity(MovingObjectPosition m) {
      return m == null ? false : m.typeOfHit == MovingObjectType.ENTITY;
   }

   public static boolean isHitTypeTile(MovingObjectPosition m) {
      return m == null ? false : m.typeOfHit == MovingObjectType.BLOCK;
   }

   public static MovingObjectPosition newMOP(int p1, int p2, int p3, int p4, Vec3 p5, boolean p6) {
      return new MovingObjectPosition(p1, p2, p3, p4, p5, p6);
   }
}
