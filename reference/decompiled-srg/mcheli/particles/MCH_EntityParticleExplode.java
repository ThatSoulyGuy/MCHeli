/*
 * Decompiled with CFR 0.152.
 */
package mcheli.particles;

import mcheli.particles.MCH_EntityParticleBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

public class MCH_EntityParticleExplode
extends MCH_EntityParticleBase {
    private static final ResourceLocation texture = new ResourceLocation("textures/entity/explosion.png");
    private int nowCount;
    private int endCount;
    private TextureManager theRenderEngine;
    private float size;

    public MCH_EntityParticleExplode(World w, double x, double y, double z, double size, double age, double mz) {
        super(w, x, y, z, 0.0, 0.0, 0.0);
        this.theRenderEngine = Minecraft.func_71410_x().field_71446_o;
        this.endCount = 1 + (int)age;
        this.size = (float)size;
    }

    public void func_70539_a(Tessellator tessellator, float p_70539_2_, float p_70539_3_, float p_70539_4_, float p_70539_5_, float p_70539_6_, float p_70539_7_) {
        int i = (int)(((float)this.nowCount + p_70539_2_) * 15.0f / (float)this.endCount);
        if (i <= 15) {
            GL11.glEnable((int)3042);
            int srcBlend = GL11.glGetInteger((int)3041);
            int dstBlend = GL11.glGetInteger((int)3040);
            GL11.glBlendFunc((int)770, (int)771);
            GL11.glDisable((int)2884);
            this.theRenderEngine.func_110577_a(texture);
            float f6 = (float)(i % 4) / 4.0f;
            float f7 = f6 + 0.24975f;
            float f8 = (float)(i / 4) / 4.0f;
            float f9 = f8 + 0.24975f;
            float f10 = 2.0f * this.size;
            float f11 = (float)(this.field_70169_q + (this.field_70165_t - this.field_70169_q) * (double)p_70539_2_ - field_70556_an);
            float f12 = (float)(this.field_70167_r + (this.field_70163_u - this.field_70167_r) * (double)p_70539_2_ - field_70554_ao);
            float f13 = (float)(this.field_70166_s + (this.field_70161_v - this.field_70166_s) * (double)p_70539_2_ - field_70555_ap);
            GL11.glColor4f((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
            RenderHelper.func_74518_a();
            tessellator.func_78382_b();
            tessellator.func_78369_a(this.field_70552_h, this.field_70553_i, this.field_70551_j, this.field_82339_as);
            tessellator.func_78375_b(0.0f, 1.0f, 0.0f);
            tessellator.func_78380_c(0xF000F0);
            tessellator.func_78374_a((double)(f11 - p_70539_3_ * f10 - p_70539_6_ * f10), (double)(f12 - p_70539_4_ * f10), (double)(f13 - p_70539_5_ * f10 - p_70539_7_ * f10), (double)f7, (double)f9);
            tessellator.func_78374_a((double)(f11 - p_70539_3_ * f10 + p_70539_6_ * f10), (double)(f12 + p_70539_4_ * f10), (double)(f13 - p_70539_5_ * f10 + p_70539_7_ * f10), (double)f7, (double)f8);
            tessellator.func_78374_a((double)(f11 + p_70539_3_ * f10 + p_70539_6_ * f10), (double)(f12 + p_70539_4_ * f10), (double)(f13 + p_70539_5_ * f10 + p_70539_7_ * f10), (double)f6, (double)f8);
            tessellator.func_78374_a((double)(f11 + p_70539_3_ * f10 - p_70539_6_ * f10), (double)(f12 - p_70539_4_ * f10), (double)(f13 + p_70539_5_ * f10 - p_70539_7_ * f10), (double)f6, (double)f9);
            tessellator.func_78381_a();
            GL11.glPolygonOffset((float)0.0f, (float)0.0f);
            GL11.glEnable((int)2896);
            GL11.glEnable((int)2884);
            GL11.glBlendFunc((int)srcBlend, (int)dstBlend);
            GL11.glDisable((int)3042);
        }
    }

    public int func_70070_b(float p_70070_1_) {
        return 0xF000F0;
    }

    public void func_70071_h_() {
        this.field_70169_q = this.field_70165_t;
        this.field_70167_r = this.field_70163_u;
        this.field_70166_s = this.field_70161_v;
        ++this.nowCount;
        if (this.nowCount == this.endCount) {
            this.func_70106_y();
        }
    }

    public int func_70537_b() {
        return 3;
    }
}

