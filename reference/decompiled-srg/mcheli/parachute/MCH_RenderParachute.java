/*
 * Decompiled with CFR 0.152.
 */
package mcheli.parachute;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.Random;
import mcheli.MCH_Config;
import mcheli.MCH_ModelManager;
import mcheli.parachute.MCH_EntityParachute;
import mcheli.wrapper.W_Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

@SideOnly(value=Side.CLIENT)
public class MCH_RenderParachute
extends W_Render {
    public static final Random rand = new Random();

    public MCH_RenderParachute() {
        this.field_76989_e = 0.5f;
    }

    public void func_76986_a(Entity entity, double posX, double posY, double posZ, float par8, float tickTime) {
        if (!(entity instanceof MCH_EntityParachute)) {
            return;
        }
        MCH_EntityParachute parachute = (MCH_EntityParachute)entity;
        int type = parachute.getType();
        if (type <= 0) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glEnable((int)2884);
        GL11.glTranslated((double)posX, (double)posY, (double)posZ);
        float prevYaw = entity.field_70126_B;
        if (entity.field_70177_z - prevYaw < -180.0f) {
            prevYaw -= 360.0f;
        } else if (prevYaw - entity.field_70177_z < -180.0f) {
            prevYaw += 360.0f;
        }
        float yaw = prevYaw + (entity.field_70177_z - prevYaw) * tickTime;
        GL11.glRotatef((float)yaw, (float)0.0f, (float)-1.0f, (float)0.0f);
        GL11.glColor4f((float)0.75f, (float)0.75f, (float)0.75f, (float)1.0f);
        GL11.glEnable((int)3042);
        int srcBlend = GL11.glGetInteger((int)3041);
        int dstBlend = GL11.glGetInteger((int)3040);
        GL11.glBlendFunc((int)770, (int)771);
        if (MCH_Config.SmoothShading.prmBool) {
            GL11.glShadeModel((int)7425);
        }
        switch (type) {
            case 1: {
                this.bindTexture("textures/parachute1.png");
                MCH_ModelManager.render((String)"parachute1");
                break;
            }
            case 2: {
                this.bindTexture("textures/parachute2.png");
                if (parachute.isOpenParachute()) {
                    MCH_ModelManager.renderPart((String)"parachute2", (String)"$parachute");
                    break;
                }
                MCH_ModelManager.renderPart((String)"parachute2", (String)"$seat");
                break;
            }
            case 3: {
                this.bindTexture("textures/parachute2.png");
                MCH_ModelManager.renderPart((String)"parachute2", (String)"$parachute");
            }
        }
        GL11.glBlendFunc((int)srcBlend, (int)dstBlend);
        GL11.glDisable((int)3042);
        GL11.glShadeModel((int)7424);
        GL11.glPopMatrix();
    }

    protected ResourceLocation func_110775_a(Entity entity) {
        return TEX_DEFAULT;
    }
}

