/*
 * Decompiled with CFR 0.152.
 */
package mcheli.container;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.Random;
import mcheli.MCH_Lib;
import mcheli.MCH_ModelManager;
import mcheli.aircraft.MCH_RenderAircraft;
import mcheli.wrapper.W_Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

@SideOnly(value=Side.CLIENT)
public class MCH_RenderContainer
extends W_Render {
    public static final Random rand = new Random();

    public MCH_RenderContainer() {
        this.field_76989_e = 0.5f;
    }

    public void func_76986_a(Entity entity, double posX, double posY, double posZ, float par8, float tickTime) {
        if (MCH_RenderAircraft.shouldSkipRender((Entity)entity)) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glEnable((int)2884);
        GL11.glTranslated((double)posX, (double)(posY - 0.2), (double)posZ);
        float yaw = MCH_Lib.smoothRot((float)entity.field_70177_z, (float)entity.field_70126_B, (float)tickTime);
        float pitch = MCH_Lib.smoothRot((float)entity.field_70125_A, (float)entity.field_70127_C, (float)tickTime);
        GL11.glRotatef((float)yaw, (float)0.0f, (float)-1.0f, (float)0.0f);
        GL11.glRotatef((float)pitch, (float)1.0f, (float)0.0f, (float)0.0f);
        GL11.glColor4f((float)0.75f, (float)0.75f, (float)0.75f, (float)1.0f);
        this.bindTexture("textures/container.png");
        MCH_ModelManager.render((String)"container");
        GL11.glPopMatrix();
    }

    protected ResourceLocation func_110775_a(Entity entity) {
        return TEX_DEFAULT;
    }
}

