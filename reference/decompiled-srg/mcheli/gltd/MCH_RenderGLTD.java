/*
 * Decompiled with CFR 0.152.
 */
package mcheli.gltd;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.Random;
import mcheli.MCH_RenderLib;
import mcheli.gltd.MCH_EntityGLTD;
import mcheli.wrapper.W_Lib;
import mcheli.wrapper.W_Render;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.model.IModelCustom;
import org.lwjgl.opengl.GL11;

@SideOnly(value=Side.CLIENT)
public class MCH_RenderGLTD
extends W_Render {
    public static final Random rand = new Random();
    public static IModelCustom model;

    public MCH_RenderGLTD() {
        this.field_76989_e = 0.5f;
        model = null;
    }

    public void func_76986_a(Entity entity, double posX, double posY, double posZ, float par8, float tickTime) {
        if (!(entity instanceof MCH_EntityGLTD)) {
            return;
        }
        MCH_EntityGLTD gltd = (MCH_EntityGLTD)entity;
        GL11.glPushMatrix();
        GL11.glTranslated((double)posX, (double)posY, (double)posZ);
        this.setCommonRenderParam(true, entity.func_70070_b(tickTime));
        this.bindTexture("textures/gltd.png");
        Minecraft mc = Minecraft.func_71410_x();
        boolean isNotRenderHead = false;
        if (gltd.field_70153_n != null) {
            gltd.isUsedPlayer = true;
            gltd.renderRotaionYaw = gltd.field_70153_n.field_70177_z;
            gltd.renderRotaionPitch = gltd.field_70153_n.field_70125_A;
            boolean bl = isNotRenderHead = mc.field_71474_y.field_74320_O == 0 && W_Lib.isClientPlayer((Entity)gltd.field_70153_n);
        }
        if (gltd.isUsedPlayer) {
            GL11.glPushMatrix();
            GL11.glRotatef((float)(-gltd.field_70177_z), (float)0.0f, (float)1.0f, (float)0.0f);
            model.renderPart("$body");
            GL11.glPopMatrix();
        } else {
            GL11.glRotatef((float)(-gltd.field_70177_z), (float)0.0f, (float)1.0f, (float)0.0f);
            model.renderPart("$body");
        }
        GL11.glTranslatef((float)0.0f, (float)0.45f, (float)0.0f);
        if (gltd.isUsedPlayer) {
            GL11.glRotatef((float)gltd.renderRotaionYaw, (float)0.0f, (float)-1.0f, (float)0.0f);
            GL11.glRotatef((float)gltd.renderRotaionPitch, (float)1.0f, (float)0.0f, (float)0.0f);
        }
        GL11.glTranslatef((float)0.0f, (float)-0.45f, (float)0.0f);
        if (!isNotRenderHead) {
            model.renderPart("$head");
        }
        GL11.glTranslatef((float)0.0f, (float)0.45f, (float)0.0f);
        this.restoreCommonRenderParam();
        GL11.glDisable((int)2896);
        Vec3[] v = new Vec3[]{Vec3.func_72443_a((double)0.0, (double)0.2, (double)0.0), Vec3.func_72443_a((double)0.0, (double)0.2, (double)100.0)};
        int a = rand.nextInt(64);
        MCH_RenderLib.drawLine((Vec3[])v, (int)(0x6080FF80 | a << 24));
        GL11.glEnable((int)2896);
        GL11.glPopMatrix();
    }

    protected ResourceLocation func_110775_a(Entity entity) {
        return TEX_DEFAULT;
    }
}

