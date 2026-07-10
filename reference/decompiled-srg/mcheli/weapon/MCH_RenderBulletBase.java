/*
 * Decompiled with CFR 0.152.
 */
package mcheli.weapon;

import mcheli.MCH_Color;
import mcheli.weapon.MCH_BulletModel;
import mcheli.weapon.MCH_EntityBaseBullet;
import mcheli.wrapper.W_Block;
import mcheli.wrapper.W_Render;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

public abstract class MCH_RenderBulletBase
extends W_Render {
    public void func_76986_a(Entity e, double var2, double var4, double var6, float var8, float var9) {
        if (e instanceof MCH_EntityBaseBullet && ((MCH_EntityBaseBullet)e).getInfo() != null) {
            MCH_Color c = ((MCH_EntityBaseBullet)e).getInfo().color;
            for (int y = 0; y < 3; ++y) {
                Block b = W_WorldFunc.getBlock((World)e.field_70170_p, (int)((int)(e.field_70165_t + 0.5)), (int)((int)(e.field_70163_u + 1.5 - (double)y)), (int)((int)(e.field_70161_v + 0.5)));
                if (b == null || b != W_Block.getWater()) continue;
                c = ((MCH_EntityBaseBullet)e).getInfo().colorInWater;
                break;
            }
            GL11.glColor4f((float)c.r, (float)c.g, (float)c.b, (float)c.a);
        } else {
            GL11.glColor4f((float)0.75f, (float)0.75f, (float)0.75f, (float)1.0f);
        }
        GL11.glAlphaFunc((int)516, (float)0.001f);
        GL11.glEnable((int)2884);
        GL11.glEnable((int)3042);
        int srcBlend = GL11.glGetInteger((int)3041);
        int dstBlend = GL11.glGetInteger((int)3040);
        GL11.glBlendFunc((int)770, (int)771);
        this.renderBullet(e, var2, var4, var6, var8, var9);
        GL11.glColor4f((float)0.75f, (float)0.75f, (float)0.75f, (float)1.0f);
        GL11.glBlendFunc((int)srcBlend, (int)dstBlend);
        GL11.glDisable((int)3042);
    }

    public void renderModel(MCH_EntityBaseBullet e) {
        MCH_BulletModel model = e.getBulletModel();
        if (model != null) {
            this.bindTexture("textures/bullets/" + model.name + ".png");
            model.model.renderAll();
        }
    }

    public abstract void renderBullet(Entity var1, double var2, double var4, double var6, float var8, float var9);
}

