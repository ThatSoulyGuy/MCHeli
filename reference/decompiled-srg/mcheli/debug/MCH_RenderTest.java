/*
 * Decompiled with CFR 0.152.
 */
package mcheli.debug;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.MCH_Config;
import mcheli.debug.MCH_ModelTest;
import mcheli.wrapper.W_Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

@SideOnly(value=Side.CLIENT)
public class MCH_RenderTest
extends W_Render {
    protected MCH_ModelTest model;
    private float offsetX;
    private float offsetY;
    private float offsetZ;
    private String textureName;

    public MCH_RenderTest(float x, float y, float z, String texture_name) {
        this.offsetX = x;
        this.offsetY = y;
        this.offsetZ = z;
        this.textureName = texture_name;
        this.model = new MCH_ModelTest();
    }

    public void func_76986_a(Entity e, double posX, double posY, double posZ, float par8, float par9) {
        if (!MCH_Config.TestMode.prmBool) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glTranslated((double)(posX + (double)this.offsetX), (double)(posY + (double)this.offsetY), (double)(posZ + (double)this.offsetZ));
        GL11.glScalef((float)e.field_70130_N, (float)e.field_70131_O, (float)e.field_70130_N);
        GL11.glColor4f((float)0.5f, (float)0.5f, (float)0.5f, (float)1.0f);
        float prevYaw = e.field_70177_z - e.field_70126_B < -180.0f ? e.field_70126_B - 360.0f : (e.field_70126_B - e.field_70177_z < -180.0f ? e.field_70126_B + 360.0f : e.field_70126_B);
        float yaw = -(prevYaw + (e.field_70177_z - prevYaw) * par9) - 180.0f;
        float pitch = -(e.field_70127_C + (e.field_70125_A - e.field_70127_C) * par9);
        GL11.glRotatef((float)yaw, (float)0.0f, (float)1.0f, (float)0.0f);
        GL11.glRotatef((float)pitch, (float)1.0f, (float)0.0f, (float)0.0f);
        this.bindTexture("textures/" + this.textureName + ".png");
        this.model.renderModel(0.0, 0.0, 0.1f);
        GL11.glPopMatrix();
    }

    protected ResourceLocation func_110775_a(Entity entity) {
        return TEX_DEFAULT;
    }
}

