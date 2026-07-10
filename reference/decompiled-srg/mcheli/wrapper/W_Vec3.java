/*
 * Decompiled with CFR 0.152.
 */
package mcheli.wrapper;

import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

public class W_Vec3 {
    public static void rotateAroundZ(float par1, Vec3 vOut) {
        float f1 = MathHelper.func_76134_b((float)par1);
        float f2 = MathHelper.func_76126_a((float)par1);
        double d0 = vOut.field_72450_a * (double)f1 + vOut.field_72448_b * (double)f2;
        double d1 = vOut.field_72448_b * (double)f1 - vOut.field_72450_a * (double)f2;
        double d2 = vOut.field_72449_c;
        vOut.field_72450_a = d0;
        vOut.field_72448_b = d1;
        vOut.field_72449_c = d2;
    }
}

