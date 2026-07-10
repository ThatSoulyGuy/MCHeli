/*
 * Decompiled with CFR 0.152.
 */
package mcheli;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

/*
 * Exception performing whole class analysis ignored.
 */
public class MCH_RenderLib {
    public static void drawLine(Vec3[] points, int color) {
        MCH_RenderLib.drawLine((Vec3[])points, (int)color, (int)1, (int)1);
    }

    public static void drawLine(Vec3[] points, int color, int mode, int width) {
        int prevWidth = GL11.glGetInteger((int)2849);
        GL11.glDisable((int)3553);
        GL11.glEnable((int)3042);
        GL11.glBlendFunc((int)770, (int)771);
        GL11.glColor4ub((byte)((byte)(color >> 16 & 0xFF)), (byte)((byte)(color >> 8 & 0xFF)), (byte)((byte)(color >> 0 & 0xFF)), (byte)((byte)(color >> 24 & 0xFF)));
        GL11.glLineWidth((float)width);
        Tessellator tessellator = Tessellator.field_78398_a;
        tessellator.func_78371_b(mode);
        for (Vec3 v : points) {
            tessellator.func_78377_a(v.field_72450_a, v.field_72448_b, v.field_72449_c);
        }
        tessellator.func_78381_a();
        GL11.glEnable((int)3553);
        GL11.glDisable((int)3042);
        GL11.glColor4b((byte)-1, (byte)-1, (byte)-1, (byte)-1);
        GL11.glLineWidth((float)prevWidth);
    }
}

