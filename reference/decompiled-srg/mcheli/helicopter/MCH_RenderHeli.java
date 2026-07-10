/*
 * Decompiled with CFR 0.152.
 */
package mcheli.helicopter;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.aircraft.MCH_Blade;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_RenderAircraft;
import mcheli.aircraft.MCH_Rotor;
import mcheli.helicopter.MCH_EntityHeli;
import mcheli.helicopter.MCH_HeliInfo;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModelCustom;
import org.lwjgl.opengl.GL11;

/*
 * Exception performing whole class analysis ignored.
 */
@SideOnly(value=Side.CLIENT)
public class MCH_RenderHeli
extends MCH_RenderAircraft {
    public MCH_RenderHeli() {
        this.field_76989_e = 2.0f;
    }

    public void renderAircraft(MCH_EntityAircraft entity, double posX, double posY, double posZ, float yaw, float pitch, float roll, float tickTime) {
        MCH_EntityHeli heli;
        MCH_HeliInfo heliInfo = null;
        if (entity != null && entity instanceof MCH_EntityHeli) {
            heli = (MCH_EntityHeli)entity;
            heliInfo = heli.getHeliInfo();
            if (heliInfo == null) {
                return;
            }
        } else {
            return;
        }
        this.renderDebugHitBox((MCH_EntityAircraft)heli, posX, posY, posZ, yaw, pitch);
        this.renderDebugPilotSeat((MCH_EntityAircraft)heli, posX, posY, posZ, yaw, pitch, roll);
        GL11.glTranslated((double)posX, (double)posY, (double)posZ);
        GL11.glRotatef((float)yaw, (float)0.0f, (float)-1.0f, (float)0.0f);
        GL11.glRotatef((float)pitch, (float)1.0f, (float)0.0f, (float)0.0f);
        GL11.glRotatef((float)roll, (float)0.0f, (float)0.0f, (float)1.0f);
        this.bindTexture("textures/helicopters/" + heli.getTextureName() + ".png", (MCH_EntityAircraft)heli);
        MCH_RenderHeli.renderBody((IModelCustom)heliInfo.model);
        this.drawModelBlade(heli, heliInfo, tickTime);
    }

    public void drawModelBlade(MCH_EntityHeli heli, MCH_HeliInfo info, float tickTime) {
        for (int i = 0; i < heli.rotors.length && i < info.rotorList.size(); ++i) {
            MCH_HeliInfo.Rotor rotorInfo = (MCH_HeliInfo.Rotor)info.rotorList.get(i);
            MCH_Rotor rotor = heli.rotors[i];
            GL11.glPushMatrix();
            if (rotorInfo.oldRenderMethod) {
                GL11.glTranslated((double)rotorInfo.pos.field_72450_a, (double)rotorInfo.pos.field_72448_b, (double)rotorInfo.pos.field_72449_c);
            }
            for (MCH_Blade b : rotor.blades) {
                GL11.glPushMatrix();
                float rot = b.getRotation();
                float prevRot = b.getPrevRotation();
                if (rot - prevRot < -180.0f) {
                    prevRot -= 360.0f;
                } else if (prevRot - rot < -180.0f) {
                    prevRot += 360.0f;
                }
                if (!rotorInfo.oldRenderMethod) {
                    GL11.glTranslated((double)rotorInfo.pos.field_72450_a, (double)rotorInfo.pos.field_72448_b, (double)rotorInfo.pos.field_72449_c);
                }
                GL11.glRotatef((float)(prevRot + (rot - prevRot) * tickTime), (float)((float)rotorInfo.rot.field_72450_a), (float)((float)rotorInfo.rot.field_72448_b), (float)((float)rotorInfo.rot.field_72449_c));
                if (!rotorInfo.oldRenderMethod) {
                    GL11.glTranslated((double)(-rotorInfo.pos.field_72450_a), (double)(-rotorInfo.pos.field_72448_b), (double)(-rotorInfo.pos.field_72449_c));
                }
                MCH_RenderHeli.renderPart((IModelCustom)rotorInfo.model, (IModelCustom)info.model, (String)rotorInfo.modelName);
                GL11.glPopMatrix();
            }
            GL11.glPopMatrix();
        }
    }

    protected ResourceLocation func_110775_a(Entity entity) {
        return TEX_DEFAULT;
    }
}

