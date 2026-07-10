package mcheli.aircraft;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public class MCH_AircraftBoundingBox extends AxisAlignedBB {
   private final MCH_EntityAircraft ac;

   protected MCH_AircraftBoundingBox(MCH_EntityAircraft ac) {
      super(
         ac.boundingBox.minX,
         ac.boundingBox.minY,
         ac.boundingBox.minZ,
         ac.boundingBox.maxX,
         ac.boundingBox.maxY,
         ac.boundingBox.maxZ
      );
      this.ac = ac;
   }

   public AxisAlignedBB NewAABB(double p_72324_1_, double p_72324_3_, double p_72324_5_, double p_72324_7_, double p_72324_9_, double p_72324_11_) {
      return new MCH_AircraftBoundingBox(this.ac).setBounds(p_72324_1_, p_72324_3_, p_72324_5_, p_72324_7_, p_72324_9_, p_72324_11_);
   }

   public double getDistSq(AxisAlignedBB a1, AxisAlignedBB a2) {
      double x1 = (a1.maxX + a1.minX) / 2.0;
      double y1 = (a1.maxY + a1.minY) / 2.0;
      double z1 = (a1.maxZ + a1.minZ) / 2.0;
      double x2 = (a2.maxX + a2.minX) / 2.0;
      double y2 = (a2.maxY + a2.minY) / 2.0;
      double z2 = (a2.maxZ + a2.minZ) / 2.0;
      double dx = x1 - x2;
      double dy = y1 - y2;
      double dz = z1 - z2;
      return dx * dx + dy * dy + dz * dz;
   }

   public boolean intersectsWith(AxisAlignedBB aabb) {
      boolean ret = false;
      double dist = 1.0E7;
      this.ac.lastBBDamageFactor = 1.0F;
      if (super.intersectsWith(aabb)) {
         dist = this.getDistSq(aabb, this);
         ret = true;
      }

      for (MCH_BoundingBox bb : this.ac.extraBoundingBox) {
         if (bb.boundingBox.intersectsWith(aabb)) {
            double dist2 = this.getDistSq(aabb, this);
            if (dist2 < dist) {
               dist = dist2;
               this.ac.lastBBDamageFactor = bb.damegeFactor;
            }

            ret = true;
         }
      }

      return ret;
   }

   public AxisAlignedBB expand(double p_72314_1_, double p_72314_3_, double p_72314_5_) {
      double d3 = this.minX - p_72314_1_;
      double d4 = this.minY - p_72314_3_;
      double d5 = this.minZ - p_72314_5_;
      double d6 = this.maxX + p_72314_1_;
      double d7 = this.maxY + p_72314_3_;
      double d8 = this.maxZ + p_72314_5_;
      return this.NewAABB(d3, d4, d5, d6, d7, d8);
   }

   public AxisAlignedBB func_111270_a(AxisAlignedBB p_111270_1_) {
      double d0 = Math.min(this.minX, p_111270_1_.minX);
      double d1 = Math.min(this.minY, p_111270_1_.minY);
      double d2 = Math.min(this.minZ, p_111270_1_.minZ);
      double d3 = Math.max(this.maxX, p_111270_1_.maxX);
      double d4 = Math.max(this.maxY, p_111270_1_.maxY);
      double d5 = Math.max(this.maxZ, p_111270_1_.maxZ);
      return this.NewAABB(d0, d1, d2, d3, d4, d5);
   }

   public AxisAlignedBB addCoord(double p_72321_1_, double p_72321_3_, double p_72321_5_) {
      double d3 = this.minX;
      double d4 = this.minY;
      double d5 = this.minZ;
      double d6 = this.maxX;
      double d7 = this.maxY;
      double d8 = this.maxZ;
      if (p_72321_1_ < 0.0) {
         d3 += p_72321_1_;
      }

      if (p_72321_1_ > 0.0) {
         d6 += p_72321_1_;
      }

      if (p_72321_3_ < 0.0) {
         d4 += p_72321_3_;
      }

      if (p_72321_3_ > 0.0) {
         d7 += p_72321_3_;
      }

      if (p_72321_5_ < 0.0) {
         d5 += p_72321_5_;
      }

      if (p_72321_5_ > 0.0) {
         d8 += p_72321_5_;
      }

      return this.NewAABB(d3, d4, d5, d6, d7, d8);
   }

   public AxisAlignedBB contract(double p_72331_1_, double p_72331_3_, double p_72331_5_) {
      double d3 = this.minX + p_72331_1_;
      double d4 = this.minY + p_72331_3_;
      double d5 = this.minZ + p_72331_5_;
      double d6 = this.maxX - p_72331_1_;
      double d7 = this.maxY - p_72331_3_;
      double d8 = this.maxZ - p_72331_5_;
      return this.NewAABB(d3, d4, d5, d6, d7, d8);
   }

   public AxisAlignedBB copy() {
      return this.NewAABB(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
   }

   public AxisAlignedBB getOffsetBoundingBox(double x, double y, double z) {
      return this.NewAABB(
         this.minX + x, this.minY + y, this.minZ + z, this.maxX + x, this.maxY + y, this.maxZ + z
      );
   }

   public MovingObjectPosition calculateIntercept(Vec3 v1, Vec3 v2) {
      this.ac.lastBBDamageFactor = 1.0F;
      MovingObjectPosition mop = super.calculateIntercept(v1, v2);
      double dist = 1.0E7;
      if (mop != null) {
         dist = v1.distanceTo(mop.hitVec);
      }

      for (MCH_BoundingBox bb : this.ac.extraBoundingBox) {
         MovingObjectPosition mop2 = bb.boundingBox.calculateIntercept(v1, v2);
         if (mop2 != null) {
            double dist2 = v1.distanceTo(mop2.hitVec);
            if (dist2 < dist) {
               mop = mop2;
               dist = dist2;
               this.ac.lastBBDamageFactor = bb.damegeFactor;
            }
         }
      }

      return mop;
   }
}
