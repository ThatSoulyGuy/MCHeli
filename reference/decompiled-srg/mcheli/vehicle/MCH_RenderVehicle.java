/*
 * Decompiled with CFR 0.152.
 */
package mcheli.vehicle;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.MCH_Lib;
import mcheli.MCH_ModelManager;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_RenderAircraft;
import mcheli.vehicle.MCH_EntityVehicle;
import mcheli.vehicle.MCH_VehicleInfo;
import mcheli.weapon.MCH_WeaponSet;
import mcheli.wrapper.W_Lib;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModelCustom;
import org.lwjgl.opengl.GL11;

/*
 * Exception performing whole class analysis ignored.
 */
@SideOnly(value=Side.CLIENT)
public class MCH_RenderVehicle
extends MCH_RenderAircraft {
    public MCH_RenderVehicle() {
        this.field_76989_e = 2.0f;
    }

    public void renderAircraft(MCH_EntityAircraft entity, double posX, double posY, double posZ, float yaw, float pitch, float roll, float tickTime) {
        MCH_EntityVehicle vehicle;
        MCH_VehicleInfo vehicleInfo = null;
        if (entity != null && entity instanceof MCH_EntityVehicle) {
            vehicle = (MCH_EntityVehicle)entity;
            vehicleInfo = vehicle.getVehicleInfo();
            if (vehicleInfo == null) {
                return;
            }
        } else {
            return;
        }
        if (vehicle.field_70153_n != null && !vehicle.isDestroyed()) {
            vehicle.isUsedPlayer = true;
            vehicle.lastRiderYaw = vehicle.field_70153_n.field_70177_z;
            vehicle.lastRiderPitch = vehicle.field_70153_n.field_70125_A;
        } else if (!vehicle.isUsedPlayer) {
            vehicle.lastRiderYaw = vehicle.field_70177_z;
            vehicle.lastRiderPitch = vehicle.field_70125_A;
        }
        this.renderDebugHitBox((MCH_EntityAircraft)vehicle, posX, posY, posZ, yaw, pitch);
        this.renderDebugPilotSeat((MCH_EntityAircraft)vehicle, posX, posY, posZ, yaw, pitch, roll);
        GL11.glTranslated((double)posX, (double)posY, (double)posZ);
        GL11.glRotatef((float)yaw, (float)0.0f, (float)-1.0f, (float)0.0f);
        GL11.glRotatef((float)pitch, (float)1.0f, (float)0.0f, (float)0.0f);
        this.bindTexture("textures/vehicles/" + vehicle.getTextureName() + ".png", (MCH_EntityAircraft)vehicle);
        MCH_RenderVehicle.renderBody((IModelCustom)vehicleInfo.model);
        MCH_WeaponSet ws = vehicle.getFirstSeatWeapon();
        this.drawPart(vehicle, vehicleInfo, yaw, pitch, ws, tickTime);
    }

    public void drawPart(MCH_EntityVehicle vehicle, MCH_VehicleInfo info, float yaw, float pitch, MCH_WeaponSet ws, float tickTime) {
        float rotBrl = ws.prevRotBarrel + (ws.rotBarrel - ws.prevRotBarrel) * tickTime;
        int index = 0;
        for (MCH_VehicleInfo.VPart vp : info.partList) {
            index = this.drawPart(vp, vehicle, info, yaw, pitch, rotBrl, tickTime, ws, index);
        }
    }

    int drawPart(MCH_VehicleInfo.VPart vp, MCH_EntityVehicle vehicle, MCH_VehicleInfo info, float yaw, float pitch, float rotBrl, float tickTime, MCH_WeaponSet ws, int index) {
        GL11.glPushMatrix();
        float recoilBuf = 0.0f;
        if (index < ws.getWeaponNum()) {
            MCH_WeaponSet.Recoil r = ws.recoilBuf[index];
            recoilBuf = r.prevRecoilBuf + (r.recoilBuf - r.prevRecoilBuf) * tickTime;
        }
        int bkIndex = index;
        if (vp.rotPitch || vp.rotYaw || vp.type == 1) {
            GL11.glTranslated((double)vp.pos.field_72450_a, (double)vp.pos.field_72448_b, (double)vp.pos.field_72449_c);
            if (vp.rotYaw) {
                GL11.glRotatef((float)(-vehicle.lastRiderYaw + yaw), (float)0.0f, (float)1.0f, (float)0.0f);
            }
            if (vp.rotPitch) {
                float p = MCH_Lib.RNG((float)vehicle.lastRiderPitch, (float)info.minRotationPitch, (float)info.maxRotationPitch);
                GL11.glRotatef((float)(p - pitch), (float)1.0f, (float)0.0f, (float)0.0f);
            }
            if (vp.type == 1) {
                GL11.glRotatef((float)rotBrl, (float)0.0f, (float)0.0f, (float)-1.0f);
            }
            GL11.glTranslated((double)(-vp.pos.field_72450_a), (double)(-vp.pos.field_72448_b), (double)(-vp.pos.field_72449_c));
        }
        if (vp.type == 2) {
            GL11.glTranslated((double)0.0, (double)0.0, (double)(-vp.recoilBuf * recoilBuf));
        }
        if (vp.type == 2 || vp.type == 3) {
            ++index;
        }
        if (vp.child != null) {
            for (MCH_VehicleInfo.VPart vcp : vp.child) {
                index = this.drawPart(vcp, vehicle, info, yaw, pitch, rotBrl, recoilBuf, ws, index);
            }
        }
        if (!(!vp.drawFP && W_Lib.isClientPlayer((Entity)vehicle.field_70153_n) && W_Lib.isFirstPerson() || vp.type == 3 && vehicle.isWeaponNotCooldown(ws, bkIndex))) {
            MCH_RenderVehicle.renderPart((IModelCustom)vp.model, (IModelCustom)info.model, (String)vp.modelName);
            MCH_ModelManager.render((String)"vehicles", (String)vp.modelName);
        }
        GL11.glPopMatrix();
        return index;
    }

    protected ResourceLocation func_110775_a(Entity entity) {
        return TEX_DEFAULT;
    }
}

