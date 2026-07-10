/*
 * Decompiled with CFR 0.152.
 */
package mcheli.tank;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.MCH_Config;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_RenderAircraft;
import mcheli.tank.MCH_EntityTank;
import mcheli.tank.MCH_EntityWheel;
import mcheli.tank.MCH_TankInfo;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.model.IModelCustom;
import org.lwjgl.opengl.GL11;

/*
 * Exception performing whole class analysis ignored.
 */
@SideOnly(value=Side.CLIENT)
public class MCH_RenderTank
extends MCH_RenderAircraft {
    public MCH_RenderTank() {
        this.field_76989_e = 2.0f;
    }

    public void renderAircraft(MCH_EntityAircraft entity, double posX, double posY, double posZ, float yaw, float pitch, float roll, float tickTime) {
        MCH_EntityTank tank;
        MCH_TankInfo tankInfo = null;
        if (entity != null && entity instanceof MCH_EntityTank) {
            tank = (MCH_EntityTank)entity;
            tankInfo = tank.getTankInfo();
            if (tankInfo == null) {
                return;
            }
        } else {
            return;
        }
        this.renderWheel(tank, posX, posY, posZ);
        this.renderDebugHitBox((MCH_EntityAircraft)tank, posX, posY, posZ, yaw, pitch);
        this.renderDebugPilotSeat((MCH_EntityAircraft)tank, posX, posY, posZ, yaw, pitch, roll);
        GL11.glTranslated((double)posX, (double)posY, (double)posZ);
        GL11.glRotatef((float)yaw, (float)0.0f, (float)-1.0f, (float)0.0f);
        GL11.glRotatef((float)pitch, (float)1.0f, (float)0.0f, (float)0.0f);
        GL11.glRotatef((float)roll, (float)0.0f, (float)0.0f, (float)1.0f);
        this.bindTexture("textures/tanks/" + tank.getTextureName() + ".png", (MCH_EntityAircraft)tank);
        MCH_RenderTank.renderBody((IModelCustom)tankInfo.model);
    }

    public void renderWheel(MCH_EntityTank tank, double posX, double posY, double posZ) {
        if (!MCH_Config.TestMode.prmBool) {
            return;
        }
        if (debugModel == null) {
            return;
        }
        GL11.glColor4f((float)0.75f, (float)0.75f, (float)0.75f, (float)0.5f);
        for (MCH_EntityWheel w : tank.WheelMng.wheels) {
            GL11.glPushMatrix();
            GL11.glTranslated((double)(w.field_70165_t - tank.field_70165_t + posX), (double)(w.field_70163_u - tank.field_70163_u + posY + 0.25), (double)(w.field_70161_v - tank.field_70161_v + posZ));
            GL11.glScalef((float)w.field_70130_N, (float)(w.field_70131_O / 2.0f), (float)w.field_70130_N);
            this.bindTexture("textures/seat_pilot.png");
            debugModel.renderAll();
            GL11.glPopMatrix();
        }
        GL11.glColor4f((float)0.75f, (float)0.75f, (float)0.75f, (float)1.0f);
        Tessellator tessellator = Tessellator.field_78398_a;
        tessellator.func_78371_b(1);
        Vec3 wp = tank.getTransformedPosition(tank.WheelMng.weightedCenter);
        wp.field_72450_a -= tank.field_70165_t;
        wp.field_72448_b -= tank.field_70163_u;
        wp.field_72449_c -= tank.field_70161_v;
        for (int i = 0; i < tank.WheelMng.wheels.length / 2; ++i) {
            tessellator.func_78384_a(((i & 4) > 0 ? 0xFF0000 : 0) | ((i & 2) > 0 ? 65280 : 0) | ((i & 1) > 0 ? 255 : 0), 192);
            MCH_EntityWheel w1 = tank.WheelMng.wheels[i * 2 + 0];
            MCH_EntityWheel w2 = tank.WheelMng.wheels[i * 2 + 1];
            if (w1.isPlus) {
                tessellator.func_78377_a(w2.field_70165_t - tank.field_70165_t + posX, w2.field_70163_u - tank.field_70163_u + posY, w2.field_70161_v - tank.field_70161_v + posZ);
                tessellator.func_78377_a(w1.field_70165_t - tank.field_70165_t + posX, w1.field_70163_u - tank.field_70163_u + posY, w1.field_70161_v - tank.field_70161_v + posZ);
                tessellator.func_78377_a(w1.field_70165_t - tank.field_70165_t + posX, w1.field_70163_u - tank.field_70163_u + posY, w1.field_70161_v - tank.field_70161_v + posZ);
                tessellator.func_78377_a(posX + wp.field_72450_a, posY + wp.field_72448_b, posZ + wp.field_72449_c);
                tessellator.func_78377_a(posX + wp.field_72450_a, posY + wp.field_72448_b, posZ + wp.field_72449_c);
                tessellator.func_78377_a(w2.field_70165_t - tank.field_70165_t + posX, w2.field_70163_u - tank.field_70163_u + posY, w2.field_70161_v - tank.field_70161_v + posZ);
                continue;
            }
            tessellator.func_78377_a(w1.field_70165_t - tank.field_70165_t + posX, w1.field_70163_u - tank.field_70163_u + posY, w1.field_70161_v - tank.field_70161_v + posZ);
            tessellator.func_78377_a(w2.field_70165_t - tank.field_70165_t + posX, w2.field_70163_u - tank.field_70163_u + posY, w2.field_70161_v - tank.field_70161_v + posZ);
            tessellator.func_78377_a(w2.field_70165_t - tank.field_70165_t + posX, w2.field_70163_u - tank.field_70163_u + posY, w2.field_70161_v - tank.field_70161_v + posZ);
            tessellator.func_78377_a(posX + wp.field_72450_a, posY + wp.field_72448_b, posZ + wp.field_72449_c);
            tessellator.func_78377_a(posX + wp.field_72450_a, posY + wp.field_72448_b, posZ + wp.field_72449_c);
            tessellator.func_78377_a(w1.field_70165_t - tank.field_70165_t + posX, w1.field_70163_u - tank.field_70163_u + posY, w1.field_70161_v - tank.field_70161_v + posZ);
        }
        tessellator.func_78381_a();
    }

    protected ResourceLocation func_110775_a(Entity entity) {
        return TEX_DEFAULT;
    }
}

