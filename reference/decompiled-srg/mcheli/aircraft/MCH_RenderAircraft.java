/*
 * Decompiled with CFR 0.152.
 */
package mcheli.aircraft;

import java.nio.FloatBuffer;
import mcheli.MCH_ClientCommonTickHandler;
import mcheli.MCH_ClientEventHook;
import mcheli.MCH_Config;
import mcheli.MCH_Lib;
import mcheli.aircraft.MCH_AircraftInfo;
import mcheli.aircraft.MCH_BoundingBox;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_EntitySeat;
import mcheli.aircraft.MCH_IEntityCanRideAircraft;
import mcheli.aircraft.MCH_SeatInfo;
import mcheli.gui.MCH_Gui;
import mcheli.lweapon.MCH_ClientLightWeaponTickHandler;
import mcheli.multiplay.MCH_GuiTargetMarker;
import mcheli.uav.MCH_EntityUavStation;
import mcheli.weapon.MCH_WeaponGuidanceSystem;
import mcheli.weapon.MCH_WeaponSet;
import mcheli.wrapper.W_Entity;
import mcheli.wrapper.W_EntityRenderer;
import mcheli.wrapper.W_Lib;
import mcheli.wrapper.W_MOD;
import mcheli.wrapper.W_Render;
import mcheli.wrapper.modelloader.W_ModelCustom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModelCustom;
import org.lwjgl.opengl.GL11;

/*
 * Exception performing whole class analysis ignored.
 */
public abstract class MCH_RenderAircraft
extends W_Render {
    public static boolean renderingEntity = false;
    public static IModelCustom debugModel = null;

    public void func_76986_a(Entity entity, double posX, double posY, double posZ, float par8, float tickTime) {
        MCH_EntityAircraft ac = (MCH_EntityAircraft)entity;
        MCH_AircraftInfo info = ac.getAcInfo();
        if (info != null) {
            GL11.glPushMatrix();
            float yaw = this.calcRot(ac.getRotYaw(), ac.field_70126_B, tickTime);
            float pitch = ac.calcRotPitch(tickTime);
            float roll = this.calcRot(ac.getRotRoll(), ac.prevRotationRoll, tickTime);
            if (MCH_Config.EnableModEntityRender.prmBool) {
                this.renderRiddenEntity(ac, tickTime, yaw, pitch + info.entityPitch, roll + info.entityRoll, info.entityWidth, info.entityHeight);
            }
            if (!MCH_RenderAircraft.shouldSkipRender((Entity)entity)) {
                this.setCommonRenderParam(info.smoothShading, ac.func_70070_b(tickTime));
                if (ac.isDestroyed()) {
                    GL11.glColor4f((float)0.15f, (float)0.15f, (float)0.15f, (float)1.0f);
                } else {
                    GL11.glColor4f((float)0.75f, (float)0.75f, (float)0.75f, (float)1.0f);
                }
                this.renderAircraft(ac, posX, posY, posZ, yaw, pitch, roll, tickTime);
                this.renderCommonPart(ac, info, posX, posY, posZ, tickTime);
                MCH_RenderAircraft.renderLight((double)posX, (double)posY, (double)posZ, (float)tickTime, (MCH_EntityAircraft)ac, (MCH_AircraftInfo)info);
                this.restoreCommonRenderParam();
            }
            GL11.glPopMatrix();
            MCH_GuiTargetMarker.addMarkEntityPos((int)1, (Entity)entity, (double)posX, (double)(posY + (double)info.markerHeight), (double)posZ);
            MCH_ClientLightWeaponTickHandler.markEntity((Entity)entity, (double)posX, (double)posY, (double)posZ);
            MCH_RenderAircraft.renderEntityMarker((Entity)ac);
        }
    }

    public static boolean shouldSkipRender(Entity entity) {
        MCH_IEntityCanRideAircraft e;
        if (entity instanceof MCH_IEntityCanRideAircraft ? (e = (MCH_IEntityCanRideAircraft)entity).isSkipNormalRender() : (entity.getClass().toString().indexOf("flansmod.common.driveables.EntityPlane") > 0 || entity.getClass().toString().indexOf("flansmod.common.driveables.EntityVehicle") > 0) && entity.field_70154_o instanceof MCH_EntitySeat) {
            return !renderingEntity;
        }
        return false;
    }

    public void func_76979_b(Entity entity, double p_76979_2_, double p_76979_4_, double p_76979_6_, float p_76979_8_, float p_76979_9_) {
        if (entity.func_90999_ad()) {
            this.renderEntityOnFire(entity, p_76979_2_, p_76979_4_, p_76979_6_, p_76979_9_);
        }
    }

    private void renderEntityOnFire(Entity entity, double x, double y, double z, float tick) {
        GL11.glDisable((int)2896);
        IIcon iicon = Blocks.field_150480_ab.func_149840_c(0);
        IIcon iicon1 = Blocks.field_150480_ab.func_149840_c(1);
        GL11.glPushMatrix();
        GL11.glTranslatef((float)((float)x), (float)((float)y), (float)((float)z));
        float f1 = entity.field_70130_N * 1.4f;
        GL11.glScalef((float)(f1 * 2.0f), (float)(f1 * 2.0f), (float)(f1 * 2.0f));
        Tessellator tessellator = Tessellator.field_78398_a;
        float f2 = 1.5f;
        float f3 = 0.0f;
        float f4 = entity.field_70131_O / f1;
        float f5 = (float)(entity.field_70163_u + entity.field_70121_D.field_72338_b);
        GL11.glRotatef((float)(-this.field_76990_c.field_78735_i), (float)0.0f, (float)1.0f, (float)0.0f);
        GL11.glTranslatef((float)0.0f, (float)0.0f, (float)(-0.3f + (float)((int)f4) * 0.02f));
        GL11.glColor4f((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        float f6 = 0.0f;
        int i = 0;
        tessellator.func_78382_b();
        while (f4 > 0.0f) {
            IIcon iicon2 = i % 2 == 0 ? iicon : iicon1;
            this.func_110776_a(TextureMap.field_110575_b);
            float f7 = iicon2.func_94209_e();
            float f8 = iicon2.func_94206_g();
            float f9 = iicon2.func_94212_f();
            float f10 = iicon2.func_94210_h();
            if (i / 2 % 2 == 0) {
                float f11 = f9;
                f9 = f7;
                f7 = f11;
            }
            tessellator.func_78374_a((double)(f2 - f3), (double)(0.0f - f5), (double)f6, (double)f9, (double)f10);
            tessellator.func_78374_a((double)(-f2 - f3), (double)(0.0f - f5), (double)f6, (double)f7, (double)f10);
            tessellator.func_78374_a((double)(-f2 - f3), (double)(1.4f - f5), (double)f6, (double)f7, (double)f8);
            tessellator.func_78374_a((double)(f2 - f3), (double)(1.4f - f5), (double)f6, (double)f9, (double)f8);
            f4 -= 0.45f;
            f5 -= 0.45f;
            f2 *= 0.9f;
            f6 += 0.03f;
            ++i;
        }
        tessellator.func_78381_a();
        GL11.glPopMatrix();
        GL11.glEnable((int)2896);
    }

    public static void renderLight(double x, double y, double z, float tickTime, MCH_EntityAircraft ac, MCH_AircraftInfo info) {
        if (!ac.haveSearchLight()) {
            return;
        }
        if (!ac.isSearchLightON()) {
            return;
        }
        Entity entity = ac.getEntityBySeatId(1);
        if (entity != null) {
            ac.lastSearchLightYaw = entity.field_70177_z;
            ac.lastSearchLightPitch = entity.field_70125_A;
        } else {
            entity = ac.getEntityBySeatId(0);
            if (entity != null) {
                ac.lastSearchLightYaw = entity.field_70177_z;
                ac.lastSearchLightPitch = entity.field_70125_A;
            }
        }
        float yaw = ac.lastSearchLightYaw;
        float pitch = ac.lastSearchLightPitch;
        RenderHelper.func_74518_a();
        GL11.glDisable((int)3553);
        GL11.glShadeModel((int)7425);
        GL11.glEnable((int)3042);
        GL11.glBlendFunc((int)770, (int)1);
        GL11.glDisable((int)3008);
        GL11.glDisable((int)2884);
        GL11.glDepthMask((boolean)false);
        float rot = ac.prevRotYawWheel + (ac.rotYawWheel - ac.prevRotYawWheel) * tickTime;
        for (MCH_AircraftInfo.SearchLight sl : info.searchLights) {
            GL11.glPushMatrix();
            GL11.glTranslated((double)sl.pos.field_72450_a, (double)sl.pos.field_72448_b, (double)sl.pos.field_72449_c);
            if (!sl.fixDir) {
                GL11.glRotatef((float)(yaw - ac.getRotYaw() + sl.yaw), (float)0.0f, (float)-1.0f, (float)0.0f);
                GL11.glRotatef((float)(pitch + 90.0f - ac.getRotPitch() + sl.pitch), (float)1.0f, (float)0.0f, (float)0.0f);
            } else {
                float stRot = 0.0f;
                if (sl.steering) {
                    stRot = -rot * sl.stRot;
                }
                GL11.glRotatef((float)(0.0f + sl.yaw + stRot), (float)0.0f, (float)-1.0f, (float)0.0f);
                GL11.glRotatef((float)(90.0f + sl.pitch), (float)1.0f, (float)0.0f, (float)0.0f);
            }
            float height = sl.height;
            float width = sl.width / 2.0f;
            Tessellator tessellator = Tessellator.field_78398_a;
            tessellator.func_78371_b(6);
            tessellator.func_78384_a(0xFFFFFF & sl.colorStart, sl.colorStart >> 24 & 0xFF);
            tessellator.func_78377_a(0.0, 0.0, 0.0);
            tessellator.func_78384_a(0xFFFFFF & sl.colorEnd, sl.colorEnd >> 24 & 0xFF);
            int VNUM = 24;
            for (int i = 0; i < 25; ++i) {
                float angle = (float)(15.0 * (double)i / 180.0 * Math.PI);
                tessellator.func_78377_a((double)(MathHelper.func_76126_a((float)angle) * width), (double)height, (double)(MathHelper.func_76134_b((float)angle) * width));
            }
            tessellator.func_78381_a();
            GL11.glPopMatrix();
        }
        GL11.glDepthMask((boolean)true);
        GL11.glColor4f((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        GL11.glEnable((int)3553);
        GL11.glEnable((int)3008);
        GL11.glBlendFunc((int)770, (int)771);
        RenderHelper.func_74519_b();
    }

    protected void bindTexture(String path, MCH_EntityAircraft ac) {
        if (ac == MCH_ClientCommonTickHandler.ridingAircraft) {
            int bk = MCH_ClientCommonTickHandler.cameraMode;
            MCH_ClientCommonTickHandler.cameraMode = 0;
            super.func_110776_a(new ResourceLocation(W_MOD.DOMAIN, path));
            MCH_ClientCommonTickHandler.cameraMode = bk;
        } else {
            super.func_110776_a(new ResourceLocation(W_MOD.DOMAIN, path));
        }
    }

    public void renderRiddenEntity(MCH_EntityAircraft ac, float tickTime, float yaw, float pitch, float roll, float width, float height) {
        MCH_ClientEventHook.setCancelRender((boolean)false);
        GL11.glPushMatrix();
        this.renderEntitySimple(ac, ac.field_70153_n, tickTime, yaw, pitch, roll, width, height);
        for (MCH_EntitySeat s : ac.getSeats()) {
            if (s == null) continue;
            this.renderEntitySimple(ac, s.field_70153_n, tickTime, yaw, pitch, roll, width, height);
        }
        GL11.glPopMatrix();
        MCH_ClientEventHook.setCancelRender((boolean)true);
    }

    public void renderEntitySimple(MCH_EntityAircraft ac, Entity entity, float tickTime, float yaw, float pitch, float roll, float width, float height) {
        if (entity != null) {
            boolean isPilot = ac.isPilot(entity);
            boolean isClientPlayer = W_Lib.isClientPlayer((Entity)entity);
            if (!isClientPlayer || !W_Lib.isFirstPerson() || isClientPlayer && isPilot && ac.getCameraId() > 0) {
                GL11.glPushMatrix();
                if (entity.field_70173_aa == 0) {
                    entity.field_70142_S = entity.field_70165_t;
                    entity.field_70137_T = entity.field_70163_u;
                    entity.field_70136_U = entity.field_70161_v;
                }
                double x = entity.field_70142_S + (entity.field_70165_t - entity.field_70142_S) * (double)tickTime;
                double y = entity.field_70137_T + (entity.field_70163_u - entity.field_70137_T) * (double)tickTime;
                double z = entity.field_70136_U + (entity.field_70161_v - entity.field_70136_U) * (double)tickTime;
                float f1 = entity.field_70126_B + (entity.field_70177_z - entity.field_70126_B) * tickTime;
                int i = entity.func_70070_b(tickTime);
                if (entity.func_70027_ad()) {
                    i = 0xF000F0;
                }
                int j = i % 65536;
                int k = i / 65536;
                OpenGlHelper.func_77475_a((int)OpenGlHelper.field_77476_b, (float)((float)j / 1.0f), (float)((float)k / 1.0f));
                GL11.glColor4f((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
                double dx = x - RenderManager.field_78725_b;
                double dy = y - RenderManager.field_78726_c;
                double dz = z - RenderManager.field_78723_d;
                GL11.glTranslated((double)dx, (double)dy, (double)dz);
                GL11.glRotatef((float)yaw, (float)0.0f, (float)-1.0f, (float)0.0f);
                GL11.glRotatef((float)pitch, (float)1.0f, (float)0.0f, (float)0.0f);
                GL11.glRotatef((float)roll, (float)0.0f, (float)0.0f, (float)1.0f);
                GL11.glScaled((double)width, (double)height, (double)width);
                GL11.glRotatef((float)(-yaw), (float)0.0f, (float)-1.0f, (float)0.0f);
                GL11.glTranslated((double)(-dx), (double)(-dy), (double)(-dz));
                boolean bk = renderingEntity;
                renderingEntity = true;
                Entity ridingEntity = entity.field_70154_o;
                if (!W_Lib.isEntityLivingBase((Entity)entity) && !(entity instanceof MCH_IEntityCanRideAircraft)) {
                    entity.field_70154_o = null;
                }
                EntityLivingBase entityLiving = entity instanceof EntityLivingBase ? (EntityLivingBase)entity : null;
                float bkYaw = 0.0f;
                float bkPrevYaw = 0.0f;
                float bkPitch = 0.0f;
                float bkPrevPitch = 0.0f;
                if (isPilot && entityLiving != null) {
                    entityLiving.field_70761_aq = ac.getRotYaw();
                    entityLiving.field_70760_ar = ac.getRotYaw();
                    if (ac.getCameraId() > 0) {
                        entityLiving.field_70759_as = ac.getRotYaw();
                        entityLiving.field_70758_at = ac.getRotYaw();
                        bkPitch = entityLiving.field_70125_A;
                        bkPrevPitch = entityLiving.field_70127_C;
                        entityLiving.field_70125_A = ac.getRotPitch();
                        entityLiving.field_70127_C = ac.getRotPitch();
                    }
                }
                W_EntityRenderer.renderEntityWithPosYaw((RenderManager)this.field_76990_c, (Entity)entity, (double)dx, (double)dy, (double)dz, (float)f1, (float)tickTime, (boolean)false);
                if (isPilot && entityLiving != null && ac.getCameraId() > 0) {
                    entityLiving.field_70125_A = bkPitch;
                    entityLiving.field_70127_C = bkPrevPitch;
                }
                entity.field_70154_o = ridingEntity;
                renderingEntity = bk;
                GL11.glPopMatrix();
            }
        }
    }

    public static void Test_Material(int light, float a, float b, float c) {
        GL11.glMaterial((int)1032, (int)light, (FloatBuffer)MCH_RenderAircraft.setColorBuffer((float)a, (float)b, (float)c, (float)1.0f));
    }

    public static void Test_Light(int light, float a, float b, float c) {
        GL11.glLight((int)16384, (int)light, (FloatBuffer)MCH_RenderAircraft.setColorBuffer((float)a, (float)b, (float)c, (float)1.0f));
        GL11.glLight((int)16385, (int)light, (FloatBuffer)MCH_RenderAircraft.setColorBuffer((float)a, (float)b, (float)c, (float)1.0f));
    }

    public abstract void renderAircraft(MCH_EntityAircraft var1, double var2, double var4, double var6, float var8, float var9, float var10, float var11);

    public float calcRot(float rot, float prevRot, float tickTime) {
        if ((rot = MathHelper.func_76142_g((float)rot)) - (prevRot = MathHelper.func_76142_g((float)prevRot)) < -180.0f) {
            prevRot -= 360.0f;
        } else if (prevRot - rot < -180.0f) {
            prevRot += 360.0f;
        }
        return prevRot + (rot - prevRot) * tickTime;
    }

    public void renderDebugHitBox(MCH_EntityAircraft e, double x, double y, double z, float yaw, float pitch) {
        if (MCH_Config.TestMode.prmBool && debugModel != null) {
            GL11.glPushMatrix();
            GL11.glTranslated((double)x, (double)y, (double)z);
            GL11.glScalef((float)e.field_70130_N, (float)e.field_70131_O, (float)e.field_70130_N);
            this.bindTexture("textures/hit_box.png");
            debugModel.renderAll();
            GL11.glPopMatrix();
            GL11.glPushMatrix();
            GL11.glTranslated((double)x, (double)y, (double)z);
            for (MCH_BoundingBox bb : e.extraBoundingBox) {
                GL11.glPushMatrix();
                GL11.glTranslated((double)bb.rotatedOffset.field_72450_a, (double)bb.rotatedOffset.field_72448_b, (double)bb.rotatedOffset.field_72449_c);
                GL11.glPushMatrix();
                GL11.glScalef((float)bb.width, (float)bb.height, (float)bb.width);
                this.bindTexture("textures/bounding_box.png");
                debugModel.renderAll();
                GL11.glPopMatrix();
                this.drawHitBoxDetail(bb);
                GL11.glPopMatrix();
            }
            GL11.glPopMatrix();
        }
    }

    public void drawHitBoxDetail(MCH_BoundingBox bb) {
        GL11.glColor4f((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        float f1 = 0.080000006f;
        String s = String.format("%.2f", Float.valueOf(bb.damegeFactor));
        GL11.glPushMatrix();
        GL11.glTranslatef((float)0.0f, (float)(0.5f + (float)(bb.offsetY * 0.0 + (double)bb.height)), (float)0.0f);
        GL11.glNormal3f((float)0.0f, (float)1.0f, (float)0.0f);
        GL11.glRotatef((float)(-this.field_76990_c.field_78735_i), (float)0.0f, (float)1.0f, (float)0.0f);
        GL11.glRotatef((float)this.field_76990_c.field_78732_j, (float)1.0f, (float)0.0f, (float)0.0f);
        GL11.glScalef((float)(-f1), (float)(-f1), (float)f1);
        GL11.glDisable((int)2896);
        GL11.glEnable((int)3042);
        OpenGlHelper.func_148821_a((int)770, (int)771, (int)1, (int)0);
        GL11.glDisable((int)3553);
        FontRenderer fontrenderer = this.func_76983_a();
        Tessellator tessellator = Tessellator.field_78398_a;
        tessellator.func_78382_b();
        int i = fontrenderer.func_78256_a(s) / 2;
        tessellator.func_78369_a(0.0f, 0.0f, 0.0f, 0.4f);
        tessellator.func_78377_a((double)(-i - 1), -1.0, 0.1);
        tessellator.func_78377_a((double)(-i - 1), 8.0, 0.1);
        tessellator.func_78377_a((double)(i + 1), 8.0, 0.1);
        tessellator.func_78377_a((double)(i + 1), -1.0, 0.1);
        tessellator.func_78381_a();
        GL11.glEnable((int)3553);
        GL11.glDepthMask((boolean)false);
        int color = bb.damegeFactor < 1.0f ? 65535 : (bb.damegeFactor > 1.0f ? 0xFF0000 : 0xFFFFFF);
        fontrenderer.func_78276_b(s, -fontrenderer.func_78256_a(s) / 2, 0, 0xC0000000 | color);
        GL11.glDepthMask((boolean)true);
        GL11.glEnable((int)2896);
        GL11.glColor4f((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        GL11.glPopMatrix();
    }

    public void renderDebugPilotSeat(MCH_EntityAircraft e, double x, double y, double z, float yaw, float pitch, float roll) {
        if (MCH_Config.TestMode.prmBool && debugModel != null) {
            GL11.glPushMatrix();
            MCH_SeatInfo seat = e.getSeatInfo(0);
            GL11.glTranslated((double)x, (double)y, (double)z);
            GL11.glRotatef((float)yaw, (float)0.0f, (float)-1.0f, (float)0.0f);
            GL11.glRotatef((float)pitch, (float)1.0f, (float)0.0f, (float)0.0f);
            GL11.glRotatef((float)roll, (float)0.0f, (float)0.0f, (float)1.0f);
            GL11.glTranslated((double)seat.pos.field_72450_a, (double)seat.pos.field_72448_b, (double)seat.pos.field_72449_c);
            GL11.glScalef((float)1.0f, (float)1.0f, (float)1.0f);
            this.bindTexture("textures/seat_pilot.png");
            debugModel.renderAll();
            GL11.glPopMatrix();
        }
    }

    public static void renderBody(IModelCustom model) {
        if (model != null) {
            if (model instanceof W_ModelCustom) {
                if (((W_ModelCustom)model).containsPart("$body")) {
                    model.renderPart("$body");
                } else {
                    model.renderAll();
                }
            } else {
                model.renderAll();
            }
        }
    }

    public static void renderPart(IModelCustom model, IModelCustom modelBody, String partName) {
        if (model != null) {
            model.renderAll();
        } else if (modelBody instanceof W_ModelCustom && ((W_ModelCustom)modelBody).containsPart("$" + partName)) {
            modelBody.renderPart("$" + partName);
        }
    }

    public void renderCommonPart(MCH_EntityAircraft ac, MCH_AircraftInfo info, double x, double y, double z, float tickTime) {
        MCH_RenderAircraft.renderRope((MCH_EntityAircraft)ac, (MCH_AircraftInfo)info, (double)x, (double)y, (double)z, (float)tickTime);
        MCH_RenderAircraft.renderWeapon((MCH_EntityAircraft)ac, (MCH_AircraftInfo)info, (float)tickTime);
        MCH_RenderAircraft.renderRotPart((MCH_EntityAircraft)ac, (MCH_AircraftInfo)info, (float)tickTime);
        MCH_RenderAircraft.renderHatch((MCH_EntityAircraft)ac, (MCH_AircraftInfo)info, (float)tickTime);
        MCH_RenderAircraft.renderTrackRoller((MCH_EntityAircraft)ac, (MCH_AircraftInfo)info, (float)tickTime);
        MCH_RenderAircraft.renderCrawlerTrack((MCH_EntityAircraft)ac, (MCH_AircraftInfo)info, (float)tickTime);
        MCH_RenderAircraft.renderSteeringWheel((MCH_EntityAircraft)ac, (MCH_AircraftInfo)info, (float)tickTime);
        MCH_RenderAircraft.renderLightHatch((MCH_EntityAircraft)ac, (MCH_AircraftInfo)info, (float)tickTime);
        MCH_RenderAircraft.renderWheel((MCH_EntityAircraft)ac, (MCH_AircraftInfo)info, (float)tickTime);
        MCH_RenderAircraft.renderThrottle((MCH_EntityAircraft)ac, (MCH_AircraftInfo)info, (float)tickTime);
        MCH_RenderAircraft.renderCamera((MCH_EntityAircraft)ac, (MCH_AircraftInfo)info, (float)tickTime);
        MCH_RenderAircraft.renderLandingGear((MCH_EntityAircraft)ac, (MCH_AircraftInfo)info, (float)tickTime);
        MCH_RenderAircraft.renderWeaponBay((MCH_EntityAircraft)ac, (MCH_AircraftInfo)info, (float)tickTime);
        MCH_RenderAircraft.renderCanopy((MCH_EntityAircraft)ac, (MCH_AircraftInfo)info, (float)tickTime);
    }

    public static void renderLightHatch(MCH_EntityAircraft ac, MCH_AircraftInfo info, float tickTime) {
        if (info.lightHatchList.size() <= 0) {
            return;
        }
        float rot = ac.prevRotLightHatch + (ac.rotLightHatch - ac.prevRotLightHatch) * tickTime;
        for (MCH_AircraftInfo.Hatch t : info.lightHatchList) {
            GL11.glPushMatrix();
            GL11.glTranslated((double)t.pos.field_72450_a, (double)t.pos.field_72448_b, (double)t.pos.field_72449_c);
            GL11.glRotated((double)(rot * t.maxRot), (double)t.rot.field_72450_a, (double)t.rot.field_72448_b, (double)t.rot.field_72449_c);
            GL11.glTranslated((double)(-t.pos.field_72450_a), (double)(-t.pos.field_72448_b), (double)(-t.pos.field_72449_c));
            MCH_RenderAircraft.renderPart((IModelCustom)t.model, (IModelCustom)info.model, (String)t.modelName);
            GL11.glPopMatrix();
        }
    }

    public static void renderSteeringWheel(MCH_EntityAircraft ac, MCH_AircraftInfo info, float tickTime) {
        if (info.partSteeringWheel.size() <= 0) {
            return;
        }
        float rot = ac.prevRotYawWheel + (ac.rotYawWheel - ac.prevRotYawWheel) * tickTime;
        for (MCH_AircraftInfo.PartWheel t : info.partSteeringWheel) {
            GL11.glPushMatrix();
            GL11.glTranslated((double)t.pos.field_72450_a, (double)t.pos.field_72448_b, (double)t.pos.field_72449_c);
            GL11.glRotated((double)(rot * t.rotDir), (double)t.rot.field_72450_a, (double)t.rot.field_72448_b, (double)t.rot.field_72449_c);
            GL11.glTranslated((double)(-t.pos.field_72450_a), (double)(-t.pos.field_72448_b), (double)(-t.pos.field_72449_c));
            MCH_RenderAircraft.renderPart((IModelCustom)t.model, (IModelCustom)info.model, (String)t.modelName);
            GL11.glPopMatrix();
        }
    }

    public static void renderWheel(MCH_EntityAircraft ac, MCH_AircraftInfo info, float tickTime) {
        if (info.partWheel.size() <= 0) {
            return;
        }
        float yaw = ac.prevRotYawWheel + (ac.rotYawWheel - ac.prevRotYawWheel) * tickTime;
        for (MCH_AircraftInfo.PartWheel t : info.partWheel) {
            GL11.glPushMatrix();
            GL11.glTranslated((double)t.pos2.field_72450_a, (double)t.pos2.field_72448_b, (double)t.pos2.field_72449_c);
            GL11.glRotated((double)(yaw * t.rotDir), (double)t.rot.field_72450_a, (double)t.rot.field_72448_b, (double)t.rot.field_72449_c);
            GL11.glTranslated((double)(-t.pos2.field_72450_a), (double)(-t.pos2.field_72448_b), (double)(-t.pos2.field_72449_c));
            GL11.glTranslated((double)t.pos.field_72450_a, (double)t.pos.field_72448_b, (double)t.pos.field_72449_c);
            GL11.glRotatef((float)(ac.prevRotWheel + (ac.rotWheel - ac.prevRotWheel) * tickTime), (float)1.0f, (float)0.0f, (float)0.0f);
            GL11.glTranslated((double)(-t.pos.field_72450_a), (double)(-t.pos.field_72448_b), (double)(-t.pos.field_72449_c));
            MCH_RenderAircraft.renderPart((IModelCustom)t.model, (IModelCustom)info.model, (String)t.modelName);
            GL11.glPopMatrix();
        }
    }

    public static void renderRotPart(MCH_EntityAircraft ac, MCH_AircraftInfo info, float tickTime) {
        if (!ac.haveRotPart()) {
            return;
        }
        for (int i = 0; i < ac.rotPartRotation.length; ++i) {
            float prevRot = ac.prevRotPartRotation[i];
            float rot = ac.rotPartRotation[i];
            if (prevRot > rot) {
                rot += 360.0f;
            }
            rot = MCH_Lib.smooth((float)rot, (float)prevRot, (float)tickTime);
            MCH_AircraftInfo.RotPart h = (MCH_AircraftInfo.RotPart)info.partRotPart.get(i);
            GL11.glPushMatrix();
            GL11.glTranslated((double)h.pos.field_72450_a, (double)h.pos.field_72448_b, (double)h.pos.field_72449_c);
            GL11.glRotatef((float)rot, (float)((float)h.rot.field_72450_a), (float)((float)h.rot.field_72448_b), (float)((float)h.rot.field_72449_c));
            GL11.glTranslated((double)(-h.pos.field_72450_a), (double)(-h.pos.field_72448_b), (double)(-h.pos.field_72449_c));
            MCH_RenderAircraft.renderPart((IModelCustom)h.model, (IModelCustom)info.model, (String)h.modelName);
            GL11.glPopMatrix();
        }
    }

    public static void renderWeapon(MCH_EntityAircraft ac, MCH_AircraftInfo info, float tickTime) {
        MCH_WeaponSet beforeWs = null;
        Entity e = ac.getRiddenByEntity();
        int weaponIndex = 0;
        int cnt = 0;
        for (MCH_AircraftInfo.PartWeapon w : info.partWeapon) {
            boolean onTurret;
            MCH_WeaponSet ws = ac.getWeaponByName(w.name[0]);
            boolean bl = onTurret = ws != null && ws.getFirstWeapon().onTurret;
            if (ws != beforeWs) {
                weaponIndex = 0;
                beforeWs = ws;
            }
            float rotYaw = 0.0f;
            float prevYaw = 0.0f;
            float rotPitch = 0.0f;
            float prevPitch = 0.0f;
            if (w.hideGM && W_Lib.isFirstPerson()) {
                if (ws != null) {
                    boolean hide = false;
                    for (String s : w.name) {
                        if (!W_Lib.isClientPlayer((Entity)ac.getWeaponUserByWeaponName(s))) continue;
                        hide = true;
                        break;
                    }
                    if (hide) {
                        continue;
                    }
                } else if (ac.isMountedEntity(MCH_Lib.getClientPlayer())) continue;
            }
            GL11.glPushMatrix();
            if (w.turret) {
                GL11.glTranslated((double)info.turretPosition.field_72450_a, (double)info.turretPosition.field_72448_b, (double)info.turretPosition.field_72449_c);
                float ty = MCH_Lib.smooth((float)(ac.getLastRiderYaw() - ac.getRotYaw()), (float)(ac.prevLastRiderYaw - ac.field_70126_B), (float)tickTime);
                GL11.glRotatef((float)ty, (float)0.0f, (float)-1.0f, (float)0.0f);
                GL11.glTranslated((double)(-info.turretPosition.field_72450_a), (double)(-info.turretPosition.field_72448_b), (double)(-info.turretPosition.field_72449_c));
            }
            GL11.glTranslated((double)w.pos.field_72450_a, (double)w.pos.field_72448_b, (double)w.pos.field_72449_c);
            if (w.yaw) {
                if (ws != null) {
                    rotYaw = ws.rotationYaw - ws.defaultRotationYaw;
                    prevYaw = ws.prevRotationYaw - ws.defaultRotationYaw;
                } else if (e != null) {
                    rotYaw = e.field_70177_z - ac.getRotYaw();
                    prevYaw = e.field_70126_B - ac.field_70126_B;
                } else {
                    rotYaw = ac.getLastRiderYaw() - ac.field_70177_z;
                    prevYaw = ac.prevLastRiderYaw - ac.field_70126_B;
                }
                if (rotYaw - prevYaw > 180.0f) {
                    prevYaw += 360.0f;
                } else if (rotYaw - prevYaw < -180.0f) {
                    prevYaw -= 360.0f;
                }
                GL11.glRotatef((float)(prevYaw + (rotYaw - prevYaw) * tickTime), (float)0.0f, (float)-1.0f, (float)0.0f);
            }
            if (w.turret) {
                float ty = MCH_Lib.smooth((float)(ac.getLastRiderYaw() - ac.getRotYaw()), (float)(ac.prevLastRiderYaw - ac.field_70126_B), (float)tickTime);
                GL11.glRotatef((float)(-(ty -= ws.rotationTurretYaw)), (float)0.0f, (float)-1.0f, (float)0.0f);
            }
            boolean rev_sign = false;
            if (ws != null && (int)ws.defaultRotationYaw != 0) {
                float t = MathHelper.func_76142_g((float)ws.defaultRotationYaw);
                rev_sign = t >= 45.0f && t <= 135.0f || t <= -45.0f && t >= -135.0f;
                GL11.glRotatef((float)(-ws.defaultRotationYaw), (float)0.0f, (float)-1.0f, (float)0.0f);
            }
            if (w.pitch) {
                if (ws != null) {
                    rotPitch = ws.rotationPitch;
                    prevPitch = ws.prevRotationPitch;
                } else if (e != null) {
                    rotPitch = e.field_70125_A;
                    prevPitch = e.field_70127_C;
                } else {
                    rotPitch = ac.getLastRiderPitch();
                    prevPitch = ac.prevLastRiderPitch;
                }
                if (rev_sign) {
                    rotPitch = -rotPitch;
                    prevPitch = -prevPitch;
                }
                GL11.glRotatef((float)(prevPitch + (rotPitch - prevPitch) * tickTime), (float)1.0f, (float)0.0f, (float)0.0f);
            }
            if (ws != null && w.recoilBuf != 0.0f) {
                MCH_WeaponSet.Recoil r = ws.recoilBuf[0];
                if (w.name.length > 1) {
                    for (String wnm : w.name) {
                        MCH_WeaponSet tws = ac.getWeaponByName(wnm);
                        if (tws == null || !(tws.recoilBuf[0].recoilBuf > r.recoilBuf)) continue;
                        r = tws.recoilBuf[0];
                    }
                }
                float recoilBuf = r.prevRecoilBuf + (r.recoilBuf - r.prevRecoilBuf) * tickTime;
                GL11.glTranslated((double)0.0, (double)0.0, (double)(w.recoilBuf * recoilBuf));
            }
            if (ws != null) {
                GL11.glRotatef((float)ws.defaultRotationYaw, (float)0.0f, (float)-1.0f, (float)0.0f);
                if (w.rotBarrel) {
                    float rotBrl = ws.prevRotBarrel + (ws.rotBarrel - ws.prevRotBarrel) * tickTime;
                    GL11.glRotatef((float)rotBrl, (float)((float)w.rot.field_72450_a), (float)((float)w.rot.field_72448_b), (float)((float)w.rot.field_72449_c));
                }
            }
            GL11.glTranslated((double)(-w.pos.field_72450_a), (double)(-w.pos.field_72448_b), (double)(-w.pos.field_72449_c));
            if (!w.isMissile || !ac.isWeaponNotCooldown(ws, weaponIndex)) {
                MCH_RenderAircraft.renderPart((IModelCustom)w.model, (IModelCustom)info.model, (String)w.modelName);
                for (MCH_AircraftInfo.PartWeaponChild wc : w.child) {
                    GL11.glPushMatrix();
                    MCH_RenderAircraft.renderWeaponChild((MCH_EntityAircraft)ac, (MCH_AircraftInfo)info, (MCH_AircraftInfo.PartWeaponChild)wc, (MCH_WeaponSet)ws, (Entity)e, (float)tickTime);
                    GL11.glPopMatrix();
                }
            }
            GL11.glPopMatrix();
            ++weaponIndex;
            ++cnt;
        }
    }

    public static void renderWeaponChild(MCH_EntityAircraft ac, MCH_AircraftInfo info, MCH_AircraftInfo.PartWeaponChild w, MCH_WeaponSet ws, Entity e, float tickTime) {
        float rotYaw = 0.0f;
        float prevYaw = 0.0f;
        float rotPitch = 0.0f;
        float prevPitch = 0.0f;
        GL11.glTranslated((double)w.pos.field_72450_a, (double)w.pos.field_72448_b, (double)w.pos.field_72449_c);
        if (w.yaw) {
            if (ws != null) {
                rotYaw = ws.rotationYaw - ws.defaultRotationYaw;
                prevYaw = ws.prevRotationYaw - ws.defaultRotationYaw;
            } else if (e != null) {
                rotYaw = e.field_70177_z - ac.getRotYaw();
                prevYaw = e.field_70126_B - ac.field_70126_B;
            } else {
                rotYaw = ac.getLastRiderYaw() - ac.field_70177_z;
                prevYaw = ac.prevLastRiderYaw - ac.field_70126_B;
            }
            if (rotYaw - prevYaw > 180.0f) {
                prevYaw += 360.0f;
            } else if (rotYaw - prevYaw < -180.0f) {
                prevYaw -= 360.0f;
            }
            GL11.glRotatef((float)(prevYaw + (rotYaw - prevYaw) * tickTime), (float)0.0f, (float)-1.0f, (float)0.0f);
        }
        boolean rev_sign = false;
        if (ws != null && (int)ws.defaultRotationYaw != 0) {
            float t = MathHelper.func_76142_g((float)ws.defaultRotationYaw);
            rev_sign = t >= 45.0f && t <= 135.0f || t <= -45.0f && t >= -135.0f;
            GL11.glRotatef((float)(-ws.defaultRotationYaw), (float)0.0f, (float)-1.0f, (float)0.0f);
        }
        if (w.pitch) {
            if (ws != null) {
                rotPitch = ws.rotationPitch;
                prevPitch = ws.prevRotationPitch;
            } else if (e != null) {
                rotPitch = e.field_70125_A;
                prevPitch = e.field_70127_C;
            } else {
                rotPitch = ac.getLastRiderPitch();
                prevPitch = ac.prevLastRiderPitch;
            }
            if (rev_sign) {
                rotPitch = -rotPitch;
                prevPitch = -prevPitch;
            }
            GL11.glRotatef((float)(prevPitch + (rotPitch - prevPitch) * tickTime), (float)1.0f, (float)0.0f, (float)0.0f);
        }
        if (ws != null && w.recoilBuf != 0.0f) {
            MCH_WeaponSet.Recoil r = ws.recoilBuf[0];
            if (w.name.length > 1) {
                for (String wnm : w.name) {
                    MCH_WeaponSet tws = ac.getWeaponByName(wnm);
                    if (tws == null || !(tws.recoilBuf[0].recoilBuf > r.recoilBuf)) continue;
                    r = tws.recoilBuf[0];
                }
            }
            float recoilBuf = r.prevRecoilBuf + (r.recoilBuf - r.prevRecoilBuf) * tickTime;
            GL11.glTranslated((double)0.0, (double)0.0, (double)(-w.recoilBuf * recoilBuf));
        }
        if (ws != null) {
            GL11.glRotatef((float)ws.defaultRotationYaw, (float)0.0f, (float)-1.0f, (float)0.0f);
        }
        GL11.glTranslated((double)(-w.pos.field_72450_a), (double)(-w.pos.field_72448_b), (double)(-w.pos.field_72449_c));
        MCH_RenderAircraft.renderPart((IModelCustom)w.model, (IModelCustom)info.model, (String)w.modelName);
    }

    public static void renderTrackRoller(MCH_EntityAircraft ac, MCH_AircraftInfo info, float tickTime) {
        if (info.partTrackRoller.size() <= 0) {
            return;
        }
        float[] rot = ac.rotTrackRoller;
        float[] prevRot = ac.prevRotTrackRoller;
        for (MCH_AircraftInfo.TrackRoller t : info.partTrackRoller) {
            GL11.glPushMatrix();
            GL11.glTranslated((double)t.pos.field_72450_a, (double)t.pos.field_72448_b, (double)t.pos.field_72449_c);
            GL11.glRotatef((float)(prevRot[t.side] + (rot[t.side] - prevRot[t.side]) * tickTime), (float)1.0f, (float)0.0f, (float)0.0f);
            GL11.glTranslated((double)(-t.pos.field_72450_a), (double)(-t.pos.field_72448_b), (double)(-t.pos.field_72449_c));
            MCH_RenderAircraft.renderPart((IModelCustom)t.model, (IModelCustom)info.model, (String)t.modelName);
            GL11.glPopMatrix();
        }
    }

    public static void renderCrawlerTrack(MCH_EntityAircraft ac, MCH_AircraftInfo info, float tickTime) {
        if (info.partCrawlerTrack.size() <= 0) {
            return;
        }
        int prevWidth = GL11.glGetInteger((int)2833);
        Tessellator tessellator = Tessellator.field_78398_a;
        for (MCH_AircraftInfo.CrawlerTrack c : info.partCrawlerTrack) {
            GL11.glPointSize((float)(c.len * 20.0f));
            if (MCH_Config.TestMode.prmBool) {
                GL11.glDisable((int)3553);
                GL11.glDisable((int)3042);
                tessellator.func_78371_b(0);
                for (int i = 0; i < c.cx.length; ++i) {
                    tessellator.func_78370_a((int)(255.0f / (float)c.cx.length * (float)i), 80, 255 - (int)(255.0f / (float)c.cx.length * (float)i), 255);
                    tessellator.func_78377_a((double)c.z, c.cx[i], c.cy[i]);
                }
                tessellator.func_78381_a();
            }
            GL11.glEnable((int)3553);
            GL11.glEnable((int)3042);
            int L = c.lp.size() - 1;
            double rc = ac != null ? (double)ac.rotCrawlerTrack[c.side] : 0.0;
            double pc = ac != null ? (double)ac.prevRotCrawlerTrack[c.side] : 0.0;
            for (int i = 0; i < L; ++i) {
                MCH_AircraftInfo.CrawlerTrackPrm cp = (MCH_AircraftInfo.CrawlerTrackPrm)c.lp.get(i);
                MCH_AircraftInfo.CrawlerTrackPrm np = (MCH_AircraftInfo.CrawlerTrackPrm)c.lp.get((i + 1) % L);
                double x1 = cp.x;
                double x2 = np.x;
                double r1 = cp.r;
                double y1 = cp.y;
                double y2 = np.y;
                double r2 = np.r;
                if (r2 - r1 < -180.0) {
                    r2 += 360.0;
                }
                if (r2 - r1 > 180.0) {
                    r2 -= 360.0;
                }
                double sx = x1 + (x2 - x1) * rc;
                double sy = y1 + (y2 - y1) * rc;
                double sr = r1 + (r2 - r1) * rc;
                double ex = x1 + (x2 - x1) * pc;
                double ey = y1 + (y2 - y1) * pc;
                double er = r1 + (r2 - r1) * pc;
                double x = sx + (ex - sx) * pc;
                double y = sy + (ey - sy) * pc;
                double r = sr + (er - sr) * pc;
                GL11.glPushMatrix();
                GL11.glTranslated((double)0.0, (double)x, (double)y);
                GL11.glRotatef((float)((float)r), (float)-1.0f, (float)0.0f, (float)0.0f);
                MCH_RenderAircraft.renderPart((IModelCustom)c.model, (IModelCustom)info.model, (String)c.modelName);
                GL11.glPopMatrix();
            }
        }
        GL11.glEnable((int)3042);
        GL11.glPointSize((float)prevWidth);
    }

    public static void renderHatch(MCH_EntityAircraft ac, MCH_AircraftInfo info, float tickTime) {
        if (!info.haveHatch() || ac.partHatch == null) {
            return;
        }
        float rot = ac.getHatchRotation();
        float prevRot = ac.getPrevHatchRotation();
        for (MCH_AircraftInfo.Hatch h : info.hatchList) {
            GL11.glPushMatrix();
            if (h.isSlide) {
                float r = ac.partHatch.rotation / ac.partHatch.rotationMax;
                float pr = ac.partHatch.prevRotation / ac.partHatch.rotationMax;
                float f = pr + (r - pr) * tickTime;
                GL11.glTranslated((double)(h.pos.field_72450_a * (double)f), (double)(h.pos.field_72448_b * (double)f), (double)(h.pos.field_72449_c * (double)f));
            } else {
                GL11.glTranslated((double)h.pos.field_72450_a, (double)h.pos.field_72448_b, (double)h.pos.field_72449_c);
                GL11.glRotatef((float)((prevRot + (rot - prevRot) * tickTime) * h.maxRotFactor), (float)((float)h.rot.field_72450_a), (float)((float)h.rot.field_72448_b), (float)((float)h.rot.field_72449_c));
                GL11.glTranslated((double)(-h.pos.field_72450_a), (double)(-h.pos.field_72448_b), (double)(-h.pos.field_72449_c));
            }
            MCH_RenderAircraft.renderPart((IModelCustom)h.model, (IModelCustom)info.model, (String)h.modelName);
            GL11.glPopMatrix();
        }
    }

    public static void renderThrottle(MCH_EntityAircraft ac, MCH_AircraftInfo info, float tickTime) {
        if (!info.havePartThrottle()) {
            return;
        }
        float throttle = MCH_Lib.smooth((float)((float)ac.getCurrentThrottle()), (float)((float)ac.getPrevCurrentThrottle()), (float)tickTime);
        for (MCH_AircraftInfo.Throttle h : info.partThrottle) {
            GL11.glPushMatrix();
            GL11.glTranslated((double)h.pos.field_72450_a, (double)h.pos.field_72448_b, (double)h.pos.field_72449_c);
            GL11.glRotatef((float)(throttle * h.rot2), (float)((float)h.rot.field_72450_a), (float)((float)h.rot.field_72448_b), (float)((float)h.rot.field_72449_c));
            GL11.glTranslated((double)(-h.pos.field_72450_a), (double)(-h.pos.field_72448_b), (double)(-h.pos.field_72449_c));
            GL11.glTranslated((double)(h.slide.field_72450_a * (double)throttle), (double)(h.slide.field_72448_b * (double)throttle), (double)(h.slide.field_72449_c * (double)throttle));
            MCH_RenderAircraft.renderPart((IModelCustom)h.model, (IModelCustom)info.model, (String)h.modelName);
            GL11.glPopMatrix();
        }
    }

    public static void renderWeaponBay(MCH_EntityAircraft ac, MCH_AircraftInfo info, float tickTime) {
        for (int i = 0; i < info.partWeaponBay.size(); ++i) {
            MCH_AircraftInfo.WeaponBay w = (MCH_AircraftInfo.WeaponBay)info.partWeaponBay.get(i);
            MCH_EntityAircraft.WeaponBay ws = ac.weaponBays[i];
            GL11.glPushMatrix();
            if (w.isSlide) {
                float r = ws.rot / 90.0f;
                float pr = ws.prevRot / 90.0f;
                float f = pr + (r - pr) * tickTime;
                GL11.glTranslated((double)(w.pos.field_72450_a * (double)f), (double)(w.pos.field_72448_b * (double)f), (double)(w.pos.field_72449_c * (double)f));
            } else {
                GL11.glTranslated((double)w.pos.field_72450_a, (double)w.pos.field_72448_b, (double)w.pos.field_72449_c);
                GL11.glRotatef((float)((ws.prevRot + (ws.rot - ws.prevRot) * tickTime) * w.maxRotFactor), (float)((float)w.rot.field_72450_a), (float)((float)w.rot.field_72448_b), (float)((float)w.rot.field_72449_c));
                GL11.glTranslated((double)(-w.pos.field_72450_a), (double)(-w.pos.field_72448_b), (double)(-w.pos.field_72449_c));
            }
            MCH_RenderAircraft.renderPart((IModelCustom)w.model, (IModelCustom)info.model, (String)w.modelName);
            GL11.glPopMatrix();
        }
    }

    public static void renderCamera(MCH_EntityAircraft ac, MCH_AircraftInfo info, float tickTime) {
        if (!info.havePartCamera()) {
            return;
        }
        float rotYaw = ac.camera.partRotationYaw;
        float prevRotYaw = ac.camera.prevPartRotationYaw;
        float rotPitch = ac.camera.partRotationPitch;
        float prevRotPitch = ac.camera.prevPartRotationPitch;
        float yaw = prevRotYaw + (rotYaw - prevRotYaw) * tickTime - ac.getRotYaw();
        float pitch = prevRotPitch + (rotPitch - prevRotPitch) * tickTime - ac.getRotPitch();
        for (MCH_AircraftInfo.Camera c : info.cameraList) {
            GL11.glPushMatrix();
            GL11.glTranslated((double)c.pos.field_72450_a, (double)c.pos.field_72448_b, (double)c.pos.field_72449_c);
            if (c.yawSync) {
                GL11.glRotatef((float)yaw, (float)0.0f, (float)-1.0f, (float)0.0f);
            }
            if (c.pitchSync) {
                GL11.glRotatef((float)pitch, (float)1.0f, (float)0.0f, (float)0.0f);
            }
            GL11.glTranslated((double)(-c.pos.field_72450_a), (double)(-c.pos.field_72448_b), (double)(-c.pos.field_72449_c));
            MCH_RenderAircraft.renderPart((IModelCustom)c.model, (IModelCustom)info.model, (String)c.modelName);
            GL11.glPopMatrix();
        }
    }

    public static void renderCanopy(MCH_EntityAircraft ac, MCH_AircraftInfo info, float tickTime) {
        if (info.haveCanopy() && ac.partCanopy != null) {
            float rot = ac.getCanopyRotation();
            float prevRot = ac.getPrevCanopyRotation();
            for (MCH_AircraftInfo.Canopy c : info.canopyList) {
                GL11.glPushMatrix();
                if (c.isSlide) {
                    float r = ac.partCanopy.rotation / ac.partCanopy.rotationMax;
                    float pr = ac.partCanopy.prevRotation / ac.partCanopy.rotationMax;
                    float f = pr + (r - pr) * tickTime;
                    GL11.glTranslated((double)(c.pos.field_72450_a * (double)f), (double)(c.pos.field_72448_b * (double)f), (double)(c.pos.field_72449_c * (double)f));
                } else {
                    GL11.glTranslated((double)c.pos.field_72450_a, (double)c.pos.field_72448_b, (double)c.pos.field_72449_c);
                    GL11.glRotatef((float)((prevRot + (rot - prevRot) * tickTime) * c.maxRotFactor), (float)((float)c.rot.field_72450_a), (float)((float)c.rot.field_72448_b), (float)((float)c.rot.field_72449_c));
                    GL11.glTranslated((double)(-c.pos.field_72450_a), (double)(-c.pos.field_72448_b), (double)(-c.pos.field_72449_c));
                }
                MCH_RenderAircraft.renderPart((IModelCustom)c.model, (IModelCustom)info.model, (String)c.modelName);
                GL11.glPopMatrix();
            }
        }
    }

    public static void renderLandingGear(MCH_EntityAircraft ac, MCH_AircraftInfo info, float tickTime) {
        if (info.haveLandingGear() && ac.partLandingGear != null) {
            float rot = ac.getLandingGearRotation();
            float prevRot = ac.getPrevLandingGearRotation();
            float revR = 90.0f - rot;
            float revPr = 90.0f - prevRot;
            float rot1 = prevRot + (rot - prevRot) * tickTime;
            float rot1Rev = revPr + (revR - revPr) * tickTime;
            float rotHatch = 90.0f * MathHelper.func_76126_a((float)(rot1 * 2.0f * (float)Math.PI / 180.0f)) * 3.0f;
            if (rotHatch > 90.0f) {
                rotHatch = 90.0f;
            }
            for (MCH_AircraftInfo.LandingGear n : info.landingGear) {
                GL11.glPushMatrix();
                GL11.glTranslated((double)n.pos.field_72450_a, (double)n.pos.field_72448_b, (double)n.pos.field_72449_c);
                if (!n.reverse) {
                    if (!n.hatch) {
                        GL11.glRotatef((float)(rot1 * n.maxRotFactor), (float)((float)n.rot.field_72450_a), (float)((float)n.rot.field_72448_b), (float)((float)n.rot.field_72449_c));
                    } else {
                        GL11.glRotatef((float)(rotHatch * n.maxRotFactor), (float)((float)n.rot.field_72450_a), (float)((float)n.rot.field_72448_b), (float)((float)n.rot.field_72449_c));
                    }
                } else {
                    GL11.glRotatef((float)(rot1Rev * n.maxRotFactor), (float)((float)n.rot.field_72450_a), (float)((float)n.rot.field_72448_b), (float)((float)n.rot.field_72449_c));
                }
                if (n.enableRot2) {
                    if (!n.reverse) {
                        GL11.glRotatef((float)(rot1 * n.maxRotFactor2), (float)((float)n.rot2.field_72450_a), (float)((float)n.rot2.field_72448_b), (float)((float)n.rot2.field_72449_c));
                    } else {
                        GL11.glRotatef((float)(rot1Rev * n.maxRotFactor2), (float)((float)n.rot2.field_72450_a), (float)((float)n.rot2.field_72448_b), (float)((float)n.rot2.field_72449_c));
                    }
                }
                GL11.glTranslated((double)(-n.pos.field_72450_a), (double)(-n.pos.field_72448_b), (double)(-n.pos.field_72449_c));
                if (n.slide != null) {
                    float f = rot / 90.0f;
                    if (n.reverse) {
                        f = 1.0f - f;
                    }
                    GL11.glTranslated((double)((double)f * n.slide.field_72450_a), (double)((double)f * n.slide.field_72448_b), (double)((double)f * n.slide.field_72449_c));
                }
                MCH_RenderAircraft.renderPart((IModelCustom)n.model, (IModelCustom)info.model, (String)n.modelName);
                GL11.glPopMatrix();
            }
        }
    }

    public static void renderEntityMarker(Entity entity) {
        EntityClientPlayerMP player = Minecraft.func_71410_x().field_71439_g;
        if (player == null) {
            return;
        }
        if (W_Entity.isEqual((Entity)player, (Entity)entity)) {
            return;
        }
        MCH_EntityAircraft ac = null;
        if (player.field_70154_o instanceof MCH_EntityAircraft) {
            ac = (MCH_EntityAircraft)player.field_70154_o;
        } else if (player.field_70154_o instanceof MCH_EntitySeat) {
            ac = ((MCH_EntitySeat)player.field_70154_o).getParent();
        } else if (player.field_70154_o instanceof MCH_EntityUavStation) {
            ac = ((MCH_EntityUavStation)player.field_70154_o).getControlAircract();
        }
        if (ac == null) {
            return;
        }
        if (W_Entity.isEqual((Entity)ac, (Entity)entity)) {
            return;
        }
        MCH_WeaponGuidanceSystem gs = ac.getCurrentWeapon((Entity)player).getCurrentWeapon().getGuidanceSystem();
        if (gs == null || !gs.canLockEntity(entity)) {
            return;
        }
        RenderManager rm = RenderManager.field_78727_a;
        double dist = entity.func_70068_e((Entity)rm.field_78734_h);
        double x = entity.field_70165_t - RenderManager.field_78725_b;
        double y = entity.field_70163_u - RenderManager.field_78726_c;
        double z = entity.field_70161_v - RenderManager.field_78723_d;
        if (dist < 10000.0) {
            float scl = 0.02666667f;
            GL11.glPushMatrix();
            GL11.glTranslatef((float)((float)x), (float)((float)y + entity.field_70131_O + 0.5f), (float)((float)z));
            GL11.glNormal3f((float)0.0f, (float)1.0f, (float)0.0f);
            GL11.glRotatef((float)(-rm.field_78735_i), (float)0.0f, (float)1.0f, (float)0.0f);
            GL11.glRotatef((float)rm.field_78732_j, (float)1.0f, (float)0.0f, (float)0.0f);
            GL11.glScalef((float)-0.02666667f, (float)-0.02666667f, (float)0.02666667f);
            GL11.glDisable((int)2896);
            GL11.glTranslatef((float)0.0f, (float)9.374999f, (float)0.0f);
            GL11.glDepthMask((boolean)false);
            GL11.glEnable((int)3042);
            GL11.glBlendFunc((int)770, (int)771);
            GL11.glDisable((int)3553);
            int prevWidth = GL11.glGetInteger((int)2849);
            float size = Math.max(entity.field_70130_N, entity.field_70131_O) * 20.0f;
            if (entity instanceof MCH_EntityAircraft) {
                size *= 2.0f;
            }
            Tessellator tessellator = Tessellator.field_78398_a;
            tessellator.func_78371_b(2);
            tessellator.func_78380_c(240);
            boolean isLockEntity = gs.isLockingEntity(entity);
            if (isLockEntity) {
                GL11.glLineWidth((float)((float)MCH_Gui.scaleFactor * 1.5f));
                tessellator.func_78369_a(1.0f, 0.0f, 0.0f, 1.0f);
            } else {
                GL11.glLineWidth((float)MCH_Gui.scaleFactor);
                tessellator.func_78369_a(1.0f, 0.3f, 0.0f, 8.0f);
            }
            tessellator.func_78377_a((double)(-size - 1.0f), 0.0, 0.0);
            tessellator.func_78377_a((double)(-size - 1.0f), (double)(size * 2.0f), 0.0);
            tessellator.func_78377_a((double)(size + 1.0f), (double)(size * 2.0f), 0.0);
            tessellator.func_78377_a((double)(size + 1.0f), 0.0, 0.0);
            tessellator.func_78381_a();
            GL11.glPopMatrix();
            if (!ac.isUAV() && isLockEntity && Minecraft.func_71410_x().field_71474_y.field_74320_O == 0) {
                GL11.glPushMatrix();
                tessellator.func_78371_b(1);
                GL11.glLineWidth((float)1.0f);
                tessellator.func_78369_a(1.0f, 0.0f, 0.0f, 1.0f);
                tessellator.func_78377_a(x, y + (double)(entity.field_70131_O / 2.0f), z);
                tessellator.func_78377_a(ac.field_70142_S - RenderManager.field_78725_b, ac.field_70137_T - RenderManager.field_78726_c - 1.0, ac.field_70136_U - RenderManager.field_78723_d);
                tessellator.func_78380_c(240);
                tessellator.func_78381_a();
                GL11.glPopMatrix();
            }
            GL11.glLineWidth((float)prevWidth);
            GL11.glEnable((int)3553);
            GL11.glDepthMask((boolean)true);
            GL11.glEnable((int)2896);
            GL11.glDisable((int)3042);
            GL11.glColor4f((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        }
    }

    public static void renderRope(MCH_EntityAircraft ac, MCH_AircraftInfo info, double x, double y, double z, float tickTime) {
        GL11.glPushMatrix();
        Tessellator tessellator = Tessellator.field_78398_a;
        if (ac.isRepelling()) {
            GL11.glDisable((int)3553);
            GL11.glDisable((int)2896);
            for (int i = 0; i < info.repellingHooks.size(); ++i) {
                tessellator.func_78371_b(3);
                tessellator.func_78378_d(0);
                tessellator.func_78377_a(((MCH_AircraftInfo.RepellingHook)info.repellingHooks.get((int)i)).pos.field_72450_a, ((MCH_AircraftInfo.RepellingHook)info.repellingHooks.get((int)i)).pos.field_72448_b, ((MCH_AircraftInfo.RepellingHook)info.repellingHooks.get((int)i)).pos.field_72449_c);
                tessellator.func_78377_a(((MCH_AircraftInfo.RepellingHook)info.repellingHooks.get((int)i)).pos.field_72450_a, ((MCH_AircraftInfo.RepellingHook)info.repellingHooks.get((int)i)).pos.field_72448_b + (double)ac.ropesLength, ((MCH_AircraftInfo.RepellingHook)info.repellingHooks.get((int)i)).pos.field_72449_c);
                tessellator.func_78381_a();
            }
            GL11.glEnable((int)2896);
            GL11.glEnable((int)3553);
        }
        GL11.glPopMatrix();
    }
}

