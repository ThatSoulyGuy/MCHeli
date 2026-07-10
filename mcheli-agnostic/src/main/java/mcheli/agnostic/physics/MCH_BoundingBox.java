package mcheli.agnostic.physics;

import mcheli.agnostic.value.AABB;
import mcheli.agnostic.value.Vec3d;

/**
 * A per-part hitbox that follows a vehicle's position/orientation. Coercion note: the reference
 * mutated a {@code final AxisAlignedBB}/{@code Vec3} in place each tick; the agnostic value types are
 * immutable, so {@link #updatePosition} REASSIGNS fresh {@link AABB}/{@link Vec3d} instances instead —
 * the fields are mutable, the values are not (per the port's mutability policy).
 */
public class MCH_BoundingBox {
    public AABB boundingBox;
    public AABB backupBoundingBox;
    public final double offsetX;
    public final double offsetY;
    public final double offsetZ;
    public final float width;
    public final float height;
    public Vec3d rotatedOffset;
    public Vec3d nowPos;
    public Vec3d prevPos;
    public final float damegeFactor;

    public MCH_BoundingBox(double x, double y, double z, float w, float h, float df) {
        this.offsetX = x;
        this.offsetY = y;
        this.offsetZ = z;
        this.width = w;
        this.height = h;
        this.damegeFactor = df;
        this.boundingBox = box(x, y, z, w, h);
        this.backupBoundingBox = box(x, y, z, w, h);
        this.nowPos = new Vec3d(x, y, z);
        this.prevPos = new Vec3d(x, y, z);
        this.updatePosition(0.0, 0.0, 0.0, 0.0F, 0.0F, 0.0F);
    }

    private static AABB box(double x, double y, double z, float w, float h) {
        return new AABB(x - w / 2.0F, y - h / 2.0F, z - w / 2.0F, x + w / 2.0F, y + h / 2.0F, z + w / 2.0F);
    }

    public MCH_BoundingBox copy() {
        return new MCH_BoundingBox(this.offsetX, this.offsetY, this.offsetZ, this.width, this.height, this.damegeFactor);
    }

    public void updatePosition(double posX, double posY, double posZ, float yaw, float pitch, float roll) {
        // Reference: MCH_Lib.RotVec3(v, -yaw, -pitch, -roll) == rotateAroundZ(-roll) then X(-pitch) then Y(-yaw),
        // each converted degrees->radians with float precision (preserved here for bit-fidelity).
        this.rotatedOffset = new Vec3d(this.offsetX, this.offsetY, this.offsetZ)
            .rotateAroundZ(-roll / 180.0F * (float) Math.PI)
            .rotateAroundX(-pitch / 180.0F * (float) Math.PI)
            .rotateAroundY(-yaw / 180.0F * (float) Math.PI);
        float w = this.width;
        float h = this.height;
        double x = posX + this.rotatedOffset.x();
        double y = posY + this.rotatedOffset.y();
        double z = posZ + this.rotatedOffset.z();
        this.prevPos = this.nowPos;
        this.nowPos = new Vec3d(x, y, z);
        this.backupBoundingBox = this.boundingBox;
        this.boundingBox = box(x, y, z, w, h);
    }
}
