/*
 * Decompiled with CFR 0.152.
 */
package mcheli.aircraft;

import mcheli.MCH_Lib;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

public class MCH_BoundingBox {
    public final AxisAlignedBB boundingBox;
    public final AxisAlignedBB backupBoundingBox;
    public final double offsetX;
    public final double offsetY;
    public final double offsetZ;
    public final float width;
    public final float height;
    public Vec3 rotatedOffset;
    public Vec3 nowPos;
    public Vec3 prevPos;
    public final float damegeFactor;

    public MCH_BoundingBox(double x, double y, double z, float w, float h, float df) {
        this.offsetX = x;
        this.offsetY = y;
        this.offsetZ = z;
        this.width = w;
        this.height = h;
        this.damegeFactor = df;
        this.boundingBox = AxisAlignedBB.func_72330_a((double)(x - (double)(w / 2.0f)), (double)(y - (double)(h / 2.0f)), (double)(z - (double)(w / 2.0f)), (double)(x + (double)(w / 2.0f)), (double)(y + (double)(h / 2.0f)), (double)(z + (double)(w / 2.0f)));
        this.backupBoundingBox = AxisAlignedBB.func_72330_a((double)(x - (double)(w / 2.0f)), (double)(y - (double)(h / 2.0f)), (double)(z - (double)(w / 2.0f)), (double)(x + (double)(w / 2.0f)), (double)(y + (double)(h / 2.0f)), (double)(z + (double)(w / 2.0f)));
        this.nowPos = Vec3.func_72443_a((double)x, (double)y, (double)z);
        this.prevPos = Vec3.func_72443_a((double)x, (double)y, (double)z);
        this.updatePosition(0.0, 0.0, 0.0, 0.0f, 0.0f, 0.0f);
    }

    public MCH_BoundingBox copy() {
        return new MCH_BoundingBox(this.offsetX, this.offsetY, this.offsetZ, this.width, this.height, this.damegeFactor);
    }

    public void updatePosition(double posX, double posY, double posZ, float yaw, float pitch, float roll) {
        Vec3 v = Vec3.func_72443_a((double)this.offsetX, (double)this.offsetY, (double)this.offsetZ);
        this.rotatedOffset = MCH_Lib.RotVec3((Vec3)v, (float)(-yaw), (float)(-pitch), (float)(-roll));
        float w = this.width;
        float h = this.height;
        double x = posX + this.rotatedOffset.field_72450_a;
        double y = posY + this.rotatedOffset.field_72448_b;
        double z = posZ + this.rotatedOffset.field_72449_c;
        this.prevPos.field_72450_a = this.nowPos.field_72450_a;
        this.prevPos.field_72448_b = this.nowPos.field_72448_b;
        this.prevPos.field_72449_c = this.nowPos.field_72449_c;
        this.nowPos.field_72450_a = x;
        this.nowPos.field_72448_b = y;
        this.nowPos.field_72449_c = z;
        this.backupBoundingBox.func_72328_c(this.boundingBox);
        this.boundingBox.func_72324_b(x - (double)(w / 2.0f), y - (double)(h / 2.0f), z - (double)(w / 2.0f), x + (double)(w / 2.0f), y + (double)(h / 2.0f), z + (double)(w / 2.0f));
    }
}

