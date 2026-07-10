/*
 * Decompiled with CFR 0.152.
 */
package mcheli.plane;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.aircraft.MCH_AircraftInfo;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_RenderAircraft;
import mcheli.plane.MCP_EntityPlane;
import mcheli.plane.MCP_PlaneInfo;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModelCustom;
import org.lwjgl.opengl.GL11;

/*
 * Exception performing whole class analysis ignored.
 */
@SideOnly(value=Side.CLIENT)
public class MCP_RenderPlane
extends MCH_RenderAircraft {
    public MCP_RenderPlane() {
        this.field_76989_e = 2.0f;
    }

    public void renderAircraft(MCH_EntityAircraft entity, double posX, double posY, double posZ, float yaw, float pitch, float roll, float tickTime) {
        MCP_EntityPlane plane;
        MCP_PlaneInfo planeInfo = null;
        if (entity != null && entity instanceof MCP_EntityPlane) {
            plane = (MCP_EntityPlane)entity;
            planeInfo = plane.getPlaneInfo();
            if (planeInfo == null) {
                return;
            }
        } else {
            return;
        }
        this.renderDebugHitBox((MCH_EntityAircraft)plane, posX, posY, posZ, yaw, pitch);
        this.renderDebugPilotSeat((MCH_EntityAircraft)plane, posX, posY, posZ, yaw, pitch, roll);
        GL11.glTranslated((double)posX, (double)posY, (double)posZ);
        GL11.glRotatef((float)yaw, (float)0.0f, (float)-1.0f, (float)0.0f);
        GL11.glRotatef((float)pitch, (float)1.0f, (float)0.0f, (float)0.0f);
        GL11.glRotatef((float)roll, (float)0.0f, (float)0.0f, (float)1.0f);
        this.bindTexture("textures/planes/" + plane.getTextureName() + ".png", (MCH_EntityAircraft)plane);
        if (planeInfo.haveNozzle() && plane.partNozzle != null) {
            this.renderNozzle(plane, planeInfo, tickTime);
        }
        if (planeInfo.haveWing() && plane.partWing != null) {
            this.renderWing(plane, planeInfo, tickTime);
        }
        if (planeInfo.haveRotor() && plane.partNozzle != null) {
            this.renderRotor(plane, planeInfo, tickTime);
        }
        MCP_RenderPlane.renderBody((IModelCustom)planeInfo.model);
    }

    public void renderRotor(MCP_EntityPlane plane, MCP_PlaneInfo planeInfo, float tickTime) {
        float rot = plane.getNozzleRotation();
        float prevRot = plane.getPrevNozzleRotation();
        for (MCP_PlaneInfo.Rotor r : planeInfo.rotorList) {
            GL11.glPushMatrix();
            GL11.glTranslated((double)r.pos.field_72450_a, (double)r.pos.field_72448_b, (double)r.pos.field_72449_c);
            GL11.glRotatef((float)((prevRot + (rot - prevRot) * tickTime) * r.maxRotFactor), (float)((float)r.rot.field_72450_a), (float)((float)r.rot.field_72448_b), (float)((float)r.rot.field_72449_c));
            GL11.glTranslated((double)(-r.pos.field_72450_a), (double)(-r.pos.field_72448_b), (double)(-r.pos.field_72449_c));
            MCP_RenderPlane.renderPart((IModelCustom)r.model, (IModelCustom)planeInfo.model, (String)r.modelName);
            for (MCP_PlaneInfo.Blade b : r.blades) {
                float br = plane.prevRotationRotor;
                GL11.glPushMatrix();
                GL11.glTranslated((double)b.pos.field_72450_a, (double)b.pos.field_72448_b, (double)b.pos.field_72449_c);
                GL11.glRotatef((float)(br += (plane.rotationRotor - plane.prevRotationRotor) * tickTime), (float)((float)b.rot.field_72450_a), (float)((float)b.rot.field_72448_b), (float)((float)b.rot.field_72449_c));
                GL11.glTranslated((double)(-b.pos.field_72450_a), (double)(-b.pos.field_72448_b), (double)(-b.pos.field_72449_c));
                for (int i = 0; i < b.numBlade; ++i) {
                    GL11.glTranslated((double)b.pos.field_72450_a, (double)b.pos.field_72448_b, (double)b.pos.field_72449_c);
                    GL11.glRotatef((float)b.rotBlade, (float)((float)b.rot.field_72450_a), (float)((float)b.rot.field_72448_b), (float)((float)b.rot.field_72449_c));
                    GL11.glTranslated((double)(-b.pos.field_72450_a), (double)(-b.pos.field_72448_b), (double)(-b.pos.field_72449_c));
                    MCP_RenderPlane.renderPart((IModelCustom)b.model, (IModelCustom)planeInfo.model, (String)b.modelName);
                }
                GL11.glPopMatrix();
            }
            GL11.glPopMatrix();
        }
    }

    public void renderWing(MCP_EntityPlane plane, MCP_PlaneInfo planeInfo, float tickTime) {
        float rot = plane.getWingRotation();
        float prevRot = plane.getPrevWingRotation();
        for (MCP_PlaneInfo.Wing w : planeInfo.wingList) {
            GL11.glPushMatrix();
            GL11.glTranslated((double)w.pos.field_72450_a, (double)w.pos.field_72448_b, (double)w.pos.field_72449_c);
            GL11.glRotatef((float)((prevRot + (rot - prevRot) * tickTime) * w.maxRotFactor), (float)((float)w.rot.field_72450_a), (float)((float)w.rot.field_72448_b), (float)((float)w.rot.field_72449_c));
            GL11.glTranslated((double)(-w.pos.field_72450_a), (double)(-w.pos.field_72448_b), (double)(-w.pos.field_72449_c));
            MCP_RenderPlane.renderPart((IModelCustom)w.model, (IModelCustom)planeInfo.model, (String)w.modelName);
            if (w.pylonList != null) {
                for (MCP_PlaneInfo.Pylon p : w.pylonList) {
                    GL11.glPushMatrix();
                    GL11.glTranslated((double)p.pos.field_72450_a, (double)p.pos.field_72448_b, (double)p.pos.field_72449_c);
                    GL11.glRotatef((float)((prevRot + (rot - prevRot) * tickTime) * p.maxRotFactor), (float)((float)p.rot.field_72450_a), (float)((float)p.rot.field_72448_b), (float)((float)p.rot.field_72449_c));
                    GL11.glTranslated((double)(-p.pos.field_72450_a), (double)(-p.pos.field_72448_b), (double)(-p.pos.field_72449_c));
                    MCP_RenderPlane.renderPart((IModelCustom)p.model, (IModelCustom)planeInfo.model, (String)p.modelName);
                    GL11.glPopMatrix();
                }
            }
            GL11.glPopMatrix();
        }
    }

    public void renderNozzle(MCP_EntityPlane plane, MCP_PlaneInfo planeInfo, float tickTime) {
        float rot = plane.getNozzleRotation();
        float prevRot = plane.getPrevNozzleRotation();
        for (MCH_AircraftInfo.DrawnPart n : planeInfo.nozzles) {
            GL11.glPushMatrix();
            GL11.glTranslated((double)n.pos.field_72450_a, (double)n.pos.field_72448_b, (double)n.pos.field_72449_c);
            GL11.glRotatef((float)(prevRot + (rot - prevRot) * tickTime), (float)((float)n.rot.field_72450_a), (float)((float)n.rot.field_72448_b), (float)((float)n.rot.field_72449_c));
            GL11.glTranslated((double)(-n.pos.field_72450_a), (double)(-n.pos.field_72448_b), (double)(-n.pos.field_72449_c));
            MCP_RenderPlane.renderPart((IModelCustom)n.model, (IModelCustom)planeInfo.model, (String)n.modelName);
            GL11.glPopMatrix();
        }
    }

    protected ResourceLocation func_110775_a(Entity entity) {
        return TEX_DEFAULT;
    }
}

