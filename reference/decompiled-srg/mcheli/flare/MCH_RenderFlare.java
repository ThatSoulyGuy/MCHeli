/*
 * Decompiled with CFR 0.152.
 */
package mcheli.flare;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.flare.MCH_ModelFlare;
import mcheli.wrapper.W_Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

@SideOnly(value=Side.CLIENT)
public class MCH_RenderFlare
extends W_Render {
    protected MCH_ModelFlare model = new MCH_ModelFlare();

    public void func_76986_a(Entity entity, double posX, double posY, double posZ, float yaw, float partialTickTime) {
        GL11.glPushMatrix();
        GL11.glEnable((int)2884);
        double x = entity.field_70169_q + entity.field_70159_w * (double)partialTickTime;
        double y = entity.field_70167_r + entity.field_70181_x * (double)partialTickTime;
        double z = entity.field_70166_s + entity.field_70179_y * (double)partialTickTime;
        GL11.glTranslated((double)posX, (double)posY, (double)posZ);
        GL11.glRotatef((float)(-entity.field_70177_z), (float)0.0f, (float)1.0f, (float)0.0f);
        GL11.glRotatef((float)entity.field_70125_A, (float)1.0f, (float)0.0f, (float)0.0f);
        GL11.glRotatef((float)45.0f, (float)0.0f, (float)0.0f, (float)1.0f);
        GL11.glColor4f((float)1.0f, (float)1.0f, (float)0.5f, (float)1.0f);
        this.bindTexture("textures/flare.png");
        this.model.renderModel(0.0, 0.0, 0.0625f);
        GL11.glPopMatrix();
    }

    protected ResourceLocation func_110775_a(Entity entity) {
        return TEX_DEFAULT;
    }
}

