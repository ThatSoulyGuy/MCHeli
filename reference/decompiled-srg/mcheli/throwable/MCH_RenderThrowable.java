/*
 * Decompiled with CFR 0.152.
 */
package mcheli.throwable;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.throwable.MCH_EntityThrowable;
import mcheli.throwable.MCH_ThrowableInfo;
import mcheli.wrapper.W_Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

@SideOnly(value=Side.CLIENT)
public class MCH_RenderThrowable
extends W_Render {
    public MCH_RenderThrowable() {
        this.field_76989_e = 0.0f;
    }

    public void func_76986_a(Entity entity, double posX, double posY, double posZ, float par8, float tickTime) {
        MCH_EntityThrowable throwable = (MCH_EntityThrowable)entity;
        MCH_ThrowableInfo info = throwable.getInfo();
        if (info == null) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glTranslated((double)posX, (double)posY, (double)posZ);
        GL11.glRotatef((float)entity.field_70177_z, (float)0.0f, (float)-1.0f, (float)0.0f);
        GL11.glRotatef((float)entity.field_70125_A, (float)1.0f, (float)0.0f, (float)0.0f);
        this.setCommonRenderParam(true, entity.func_70070_b(tickTime));
        if (info.model != null) {
            this.bindTexture("textures/throwable/" + info.name + ".png");
            info.model.renderAll();
        }
        this.restoreCommonRenderParam();
        GL11.glPopMatrix();
    }

    protected ResourceLocation func_110775_a(Entity entity) {
        return TEX_DEFAULT;
    }
}

