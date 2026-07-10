/*
 * Decompiled with CFR 0.152.
 */
package mcheli.chain;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.MCH_Lib;
import mcheli.MCH_ModelManager;
import mcheli.chain.MCH_EntityChain;
import mcheli.wrapper.W_Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

@SideOnly(value=Side.CLIENT)
public class MCH_RenderChain
extends W_Render {
    public void func_76986_a(Entity e, double posX, double posY, double posZ, float par8, float par9) {
        double diff;
        if (!(e instanceof MCH_EntityChain)) {
            return;
        }
        MCH_EntityChain chain = (MCH_EntityChain)e;
        if (chain.towedEntity == null || chain.towEntity == null) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glEnable((int)2884);
        GL11.glColor4f((float)0.5f, (float)0.5f, (float)0.5f, (float)1.0f);
        GL11.glTranslated((double)(chain.towedEntity.field_70142_S - RenderManager.field_78725_b), (double)(chain.towedEntity.field_70137_T - RenderManager.field_78726_c), (double)(chain.towedEntity.field_70136_U - RenderManager.field_78723_d));
        this.bindTexture("textures/chain.png");
        double dx = chain.towEntity.field_70142_S - chain.towedEntity.field_70142_S;
        double dy = chain.towEntity.field_70137_T - chain.towedEntity.field_70137_T;
        double dz = chain.towEntity.field_70136_U - chain.towedEntity.field_70136_U;
        float CHAIN_LEN = 0.95f;
        double x = dx * (double)0.95f / diff;
        double y = dy * (double)0.95f / diff;
        double z = dz * (double)0.95f / diff;
        for (diff = Math.sqrt(dx * dx + dy * dy + dz * dz); diff > (double)0.95f; diff -= (double)0.95f) {
            GL11.glTranslated((double)x, (double)y, (double)z);
            GL11.glPushMatrix();
            Vec3 v = MCH_Lib.getYawPitchFromVec((double)x, (double)y, (double)z);
            GL11.glRotatef((float)((float)v.field_72448_b), (float)0.0f, (float)-1.0f, (float)0.0f);
            GL11.glRotatef((float)((float)v.field_72449_c), (float)0.0f, (float)0.0f, (float)1.0f);
            MCH_ModelManager.render((String)"chain");
            GL11.glPopMatrix();
        }
        GL11.glPopMatrix();
    }

    protected ResourceLocation func_110775_a(Entity entity) {
        return TEX_DEFAULT;
    }
}

