package mcheli.aircraft;

import java.util.Iterator;
import mcheli.MCH_ClientCommonTickHandler;
import mcheli.MCH_ClientEventHook;
import mcheli.MCH_Config;
import mcheli.MCH_Lib;
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

public abstract class MCH_RenderAircraft extends W_Render {
   public static boolean renderingEntity = false;
   public static IModelCustom debugModel = null;

   public void doRender(Entity entity, double posX, double posY, double posZ, float par8, float tickTime) {
      MCH_EntityAircraft ac = (MCH_EntityAircraft)entity;
      MCH_AircraftInfo info = ac.getAcInfo();
      if (info != null) {
         GL11.glPushMatrix();
         float yaw = this.calcRot(ac.getRotYaw(), ac.prevRotationYaw, tickTime);
         float pitch = ac.calcRotPitch(tickTime);
         float roll = this.calcRot(ac.getRotRoll(), ac.prevRotationRoll, tickTime);
         if (MCH_Config.EnableModEntityRender.prmBool) {
            this.renderRiddenEntity(ac, tickTime, yaw, pitch + info.entityPitch, roll + info.entityRoll, info.entityWidth, info.entityHeight);
         }

         if (!shouldSkipRender(entity)) {
            this.setCommonRenderParam(info.smoothShading, ac.getBrightnessForRender(tickTime));
            if (ac.isDestroyed()) {
               GL11.glColor4f(0.15F, 0.15F, 0.15F, 1.0F);
            } else {
               GL11.glColor4f(0.75F, 0.75F, 0.75F, 1.0F);
            }

            this.renderAircraft(ac, posX, posY, posZ, yaw, pitch, roll, tickTime);
            this.renderCommonPart(ac, info, posX, posY, posZ, tickTime);
            renderLight(posX, posY, posZ, tickTime, ac, info);
            this.restoreCommonRenderParam();
         }

         GL11.glPopMatrix();
         MCH_GuiTargetMarker.addMarkEntityPos(1, entity, posX, posY + info.markerHeight, posZ);
         MCH_ClientLightWeaponTickHandler.markEntity(entity, posX, posY, posZ);
         renderEntityMarker(ac);
      }
   }

   public static boolean shouldSkipRender(Entity entity) {
      if (entity instanceof MCH_IEntityCanRideAircraft) {
         MCH_IEntityCanRideAircraft e = (MCH_IEntityCanRideAircraft)entity;
         if (e.isSkipNormalRender()) {
            return !renderingEntity;
         }
      } else if ((
            entity.getClass().toString().indexOf("flansmod.common.driveables.EntityPlane") > 0
               || entity.getClass().toString().indexOf("flansmod.common.driveables.EntityVehicle") > 0
         )
         && entity.ridingEntity instanceof MCH_EntitySeat) {
         return !renderingEntity;
      }

      return false;
   }

   public void doRenderShadowAndFire(Entity entity, double p_76979_2_, double p_76979_4_, double p_76979_6_, float p_76979_8_, float p_76979_9_) {
      if (entity.canRenderOnFire()) {
         this.renderEntityOnFire(entity, p_76979_2_, p_76979_4_, p_76979_6_, p_76979_9_);
      }
   }

   private void renderEntityOnFire(Entity entity, double x, double y, double z, float tick) {
      GL11.glDisable(2896);
      IIcon iicon = Blocks.fire.getFireIcon(0);
      IIcon iicon1 = Blocks.fire.getFireIcon(1);
      GL11.glPushMatrix();
      GL11.glTranslatef((float)x, (float)y, (float)z);
      float f1 = entity.width * 1.4F;
      GL11.glScalef(f1 * 2.0F, f1 * 2.0F, f1 * 2.0F);
      Tessellator tessellator = Tessellator.instance;
      float f2 = 1.5F;
      float f3 = 0.0F;
      float f4 = entity.height / f1;
      float f5 = (float)(entity.posY + entity.boundingBox.minY);
      GL11.glRotatef(-this.renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
      GL11.glTranslatef(0.0F, 0.0F, -0.3F + (int)f4 * 0.02F);
      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      float f6 = 0.0F;
      int i = 0;
      tessellator.startDrawingQuads();

      while (f4 > 0.0F) {
         IIcon iicon2 = i % 2 == 0 ? iicon : iicon1;
         this.bindTexture(TextureMap.locationBlocksTexture);
         float f7 = iicon2.getMinU();
         float f8 = iicon2.getMinV();
         float f9 = iicon2.getMaxU();
         float f10 = iicon2.getMaxV();
         if (i / 2 % 2 == 0) {
            float f11 = f9;
            f9 = f7;
            f7 = f11;
         }

         tessellator.addVertexWithUV(f2 - f3, 0.0F - f5, f6, f9, f10);
         tessellator.addVertexWithUV(-f2 - f3, 0.0F - f5, f6, f7, f10);
         tessellator.addVertexWithUV(-f2 - f3, 1.4F - f5, f6, f7, f8);
         tessellator.addVertexWithUV(f2 - f3, 1.4F - f5, f6, f9, f8);
         f4 -= 0.45F;
         f5 -= 0.45F;
         f2 *= 0.9F;
         f6 += 0.03F;
         i++;
      }

      tessellator.draw();
      GL11.glPopMatrix();
      GL11.glEnable(2896);
   }

   public static void renderLight(double x, double y, double z, float tickTime, MCH_EntityAircraft ac, MCH_AircraftInfo info) {
      if (ac.haveSearchLight()) {
         if (ac.isSearchLightON()) {
            Entity entity = ac.getEntityBySeatId(1);
            if (entity != null) {
               ac.lastSearchLightYaw = entity.rotationYaw;
               ac.lastSearchLightPitch = entity.rotationPitch;
            } else {
               entity = ac.getEntityBySeatId(0);
               if (entity != null) {
                  ac.lastSearchLightYaw = entity.rotationYaw;
                  ac.lastSearchLightPitch = entity.rotationPitch;
               }
            }

            float yaw = ac.lastSearchLightYaw;
            float pitch = ac.lastSearchLightPitch;
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(3553);
            GL11.glShadeModel(7425);
            GL11.glEnable(3042);
            GL11.glBlendFunc(770, 1);
            GL11.glDisable(3008);
            GL11.glDisable(2884);
            GL11.glDepthMask(false);
            float rot = ac.prevRotYawWheel + (ac.rotYawWheel - ac.prevRotYawWheel) * tickTime;

            for (MCH_AircraftInfo.SearchLight sl : info.searchLights) {
               GL11.glPushMatrix();
               GL11.glTranslated(sl.pos.xCoord, sl.pos.yCoord, sl.pos.zCoord);
               if (!sl.fixDir) {
                  GL11.glRotatef(yaw - ac.getRotYaw() + sl.yaw, 0.0F, -1.0F, 0.0F);
                  GL11.glRotatef(pitch + 90.0F - ac.getRotPitch() + sl.pitch, 1.0F, 0.0F, 0.0F);
               } else {
                  float stRot = 0.0F;
                  if (sl.steering) {
                     stRot = -rot * sl.stRot;
                  }

                  GL11.glRotatef(0.0F + sl.yaw + stRot, 0.0F, -1.0F, 0.0F);
                  GL11.glRotatef(90.0F + sl.pitch, 1.0F, 0.0F, 0.0F);
               }

               float height = sl.height;
               float width = sl.width / 2.0F;
               Tessellator tessellator = Tessellator.instance;
               tessellator.startDrawing(6);
               tessellator.setColorRGBA_I(16777215 & sl.colorStart, sl.colorStart >> 24 & 0xFF);
               tessellator.addVertex(0.0, 0.0, 0.0);
               tessellator.setColorRGBA_I(16777215 & sl.colorEnd, sl.colorEnd >> 24 & 0xFF);
               int VNUM = 24;

               for (int i = 0; i < 25; i++) {
                  float angle = (float)(15.0 * i / 180.0 * Math.PI);
                  tessellator.addVertex(MathHelper.sin(angle) * width, height, MathHelper.cos(angle) * width);
               }

               tessellator.draw();
               GL11.glPopMatrix();
            }

            GL11.glDepthMask(true);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(3553);
            GL11.glEnable(3008);
            GL11.glBlendFunc(770, 771);
            RenderHelper.enableStandardItemLighting();
         }
      }
   }

   protected void bindTexture(String path, MCH_EntityAircraft ac) {
      if (ac == MCH_ClientCommonTickHandler.ridingAircraft) {
         int bk = MCH_ClientCommonTickHandler.cameraMode;
         MCH_ClientCommonTickHandler.cameraMode = 0;
         super.bindTexture(new ResourceLocation(W_MOD.DOMAIN, path));
         MCH_ClientCommonTickHandler.cameraMode = bk;
      } else {
         super.bindTexture(new ResourceLocation(W_MOD.DOMAIN, path));
      }
   }

   public void renderRiddenEntity(MCH_EntityAircraft ac, float tickTime, float yaw, float pitch, float roll, float width, float height) {
      MCH_ClientEventHook.setCancelRender(false);
      GL11.glPushMatrix();
      this.renderEntitySimple(ac, ac.riddenByEntity, tickTime, yaw, pitch, roll, width, height);

      for (MCH_EntitySeat s : ac.getSeats()) {
         if (s != null) {
            this.renderEntitySimple(ac, s.riddenByEntity, tickTime, yaw, pitch, roll, width, height);
         }
      }

      GL11.glPopMatrix();
      MCH_ClientEventHook.setCancelRender(true);
   }

   public void renderEntitySimple(MCH_EntityAircraft ac, Entity entity, float tickTime, float yaw, float pitch, float roll, float width, float height) {
      if (entity != null) {
         boolean isPilot = ac.isPilot(entity);
         boolean isClientPlayer = W_Lib.isClientPlayer(entity);
         if (!isClientPlayer || !W_Lib.isFirstPerson() || isClientPlayer && isPilot && ac.getCameraId() > 0) {
            GL11.glPushMatrix();
            if (entity.ticksExisted == 0) {
               entity.lastTickPosX = entity.posX;
               entity.lastTickPosY = entity.posY;
               entity.lastTickPosZ = entity.posZ;
            }

            double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * tickTime;
            double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * tickTime;
            double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * tickTime;
            float f1 = entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * tickTime;
            int i = entity.getBrightnessForRender(tickTime);
            if (entity.isBurning()) {
               i = 15728880;
            }

            int j = i % 65536;
            int k = i / 65536;
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, j / 1.0F, k / 1.0F);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            double dx = x - RenderManager.renderPosX;
            double dy = y - RenderManager.renderPosY;
            double dz = z - RenderManager.renderPosZ;
            GL11.glTranslated(dx, dy, dz);
            GL11.glRotatef(yaw, 0.0F, -1.0F, 0.0F);
            GL11.glRotatef(pitch, 1.0F, 0.0F, 0.0F);
            GL11.glRotatef(roll, 0.0F, 0.0F, 1.0F);
            GL11.glScaled(width, height, width);
            GL11.glRotatef(-yaw, 0.0F, -1.0F, 0.0F);
            GL11.glTranslated(-dx, -dy, -dz);
            boolean bk = renderingEntity;
            renderingEntity = true;
            Entity ridingEntity = entity.ridingEntity;
            if (!W_Lib.isEntityLivingBase(entity) && !(entity instanceof MCH_IEntityCanRideAircraft)) {
               entity.ridingEntity = null;
            }

            EntityLivingBase entityLiving = entity instanceof EntityLivingBase ? (EntityLivingBase)entity : null;
            float bkYaw = 0.0F;
            float bkPrevYaw = 0.0F;
            float bkPitch = 0.0F;
            float bkPrevPitch = 0.0F;
            if (isPilot && entityLiving != null) {
               entityLiving.renderYawOffset = ac.getRotYaw();
               entityLiving.prevRenderYawOffset = ac.getRotYaw();
               if (ac.getCameraId() > 0) {
                  entityLiving.rotationYawHead = ac.getRotYaw();
                  entityLiving.prevRotationYawHead = ac.getRotYaw();
                  bkPitch = entityLiving.rotationPitch;
                  bkPrevPitch = entityLiving.prevRotationPitch;
                  entityLiving.rotationPitch = ac.getRotPitch();
                  entityLiving.prevRotationPitch = ac.getRotPitch();
               }
            }

            W_EntityRenderer.renderEntityWithPosYaw(this.renderManager, entity, dx, dy, dz, f1, tickTime, false);
            if (isPilot && entityLiving != null && ac.getCameraId() > 0) {
               entityLiving.rotationPitch = bkPitch;
               entityLiving.prevRotationPitch = bkPrevPitch;
            }

            entity.ridingEntity = ridingEntity;
            renderingEntity = bk;
            GL11.glPopMatrix();
         }
      }
   }

   public static void Test_Material(int light, float a, float b, float c) {
      GL11.glMaterial(1032, light, setColorBuffer(a, b, c, 1.0F));
   }

   public static void Test_Light(int light, float a, float b, float c) {
      GL11.glLight(16384, light, setColorBuffer(a, b, c, 1.0F));
      GL11.glLight(16385, light, setColorBuffer(a, b, c, 1.0F));
   }

   public abstract void renderAircraft(MCH_EntityAircraft var1, double var2, double var4, double var6, float var8, float var9, float var10, float var11);

   public float calcRot(float rot, float prevRot, float tickTime) {
      rot = MathHelper.wrapAngleTo180_float(rot);
      prevRot = MathHelper.wrapAngleTo180_float(prevRot);
      if (rot - prevRot < -180.0F) {
         prevRot -= 360.0F;
      } else if (prevRot - rot < -180.0F) {
         prevRot += 360.0F;
      }

      return prevRot + (rot - prevRot) * tickTime;
   }

   public void renderDebugHitBox(MCH_EntityAircraft e, double x, double y, double z, float yaw, float pitch) {
      if (MCH_Config.TestMode.prmBool && debugModel != null) {
         GL11.glPushMatrix();
         GL11.glTranslated(x, y, z);
         GL11.glScalef(e.width, e.height, e.width);
         this.bindTexture("textures/hit_box.png");
         debugModel.renderAll();
         GL11.glPopMatrix();
         GL11.glPushMatrix();
         GL11.glTranslated(x, y, z);

         for (MCH_BoundingBox bb : e.extraBoundingBox) {
            GL11.glPushMatrix();
            GL11.glTranslated(bb.rotatedOffset.xCoord, bb.rotatedOffset.yCoord, bb.rotatedOffset.zCoord);
            GL11.glPushMatrix();
            GL11.glScalef(bb.width, bb.height, bb.width);
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
      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      float f1 = 0.080000006F;
      String s = String.format("%.2f", bb.damegeFactor);
      GL11.glPushMatrix();
      GL11.glTranslatef(0.0F, 0.5F + (float)(bb.offsetY * 0.0 + bb.height), 0.0F);
      GL11.glNormal3f(0.0F, 1.0F, 0.0F);
      GL11.glRotatef(-this.renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
      GL11.glRotatef(this.renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
      GL11.glScalef(-f1, -f1, f1);
      GL11.glDisable(2896);
      GL11.glEnable(3042);
      OpenGlHelper.glBlendFunc(770, 771, 1, 0);
      GL11.glDisable(3553);
      FontRenderer fontrenderer = this.getFontRendererFromRenderManager();
      Tessellator tessellator = Tessellator.instance;
      tessellator.startDrawingQuads();
      int i = fontrenderer.getStringWidth(s) / 2;
      tessellator.setColorRGBA_F(0.0F, 0.0F, 0.0F, 0.4F);
      tessellator.addVertex(-i - 1, -1.0, 0.1);
      tessellator.addVertex(-i - 1, 8.0, 0.1);
      tessellator.addVertex(i + 1, 8.0, 0.1);
      tessellator.addVertex(i + 1, -1.0, 0.1);
      tessellator.draw();
      GL11.glEnable(3553);
      GL11.glDepthMask(false);
      int color = bb.damegeFactor < 1.0F ? 65535 : (bb.damegeFactor > 1.0F ? 16711680 : 16777215);
      fontrenderer.drawString(s, -fontrenderer.getStringWidth(s) / 2, 0, -1073741824 | color);
      GL11.glDepthMask(true);
      GL11.glEnable(2896);
      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      GL11.glPopMatrix();
   }

   public void renderDebugPilotSeat(MCH_EntityAircraft e, double x, double y, double z, float yaw, float pitch, float roll) {
      if (MCH_Config.TestMode.prmBool && debugModel != null) {
         GL11.glPushMatrix();
         MCH_SeatInfo seat = e.getSeatInfo(0);
         GL11.glTranslated(x, y, z);
         GL11.glRotatef(yaw, 0.0F, -1.0F, 0.0F);
         GL11.glRotatef(pitch, 1.0F, 0.0F, 0.0F);
         GL11.glRotatef(roll, 0.0F, 0.0F, 1.0F);
         GL11.glTranslated(seat.pos.xCoord, seat.pos.yCoord, seat.pos.zCoord);
         GL11.glScalef(1.0F, 1.0F, 1.0F);
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
      renderRope(ac, info, x, y, z, tickTime);
      renderWeapon(ac, info, tickTime);
      renderRotPart(ac, info, tickTime);
      renderHatch(ac, info, tickTime);
      renderTrackRoller(ac, info, tickTime);
      renderCrawlerTrack(ac, info, tickTime);
      renderSteeringWheel(ac, info, tickTime);
      renderLightHatch(ac, info, tickTime);
      renderWheel(ac, info, tickTime);
      renderThrottle(ac, info, tickTime);
      renderCamera(ac, info, tickTime);
      renderLandingGear(ac, info, tickTime);
      renderWeaponBay(ac, info, tickTime);
      renderCanopy(ac, info, tickTime);
   }

   public static void renderLightHatch(MCH_EntityAircraft ac, MCH_AircraftInfo info, float tickTime) {
      if (info.lightHatchList.size() > 0) {
         float rot = ac.prevRotLightHatch + (ac.rotLightHatch - ac.prevRotLightHatch) * tickTime;

         for (MCH_AircraftInfo.Hatch t : info.lightHatchList) {
            GL11.glPushMatrix();
            GL11.glTranslated(t.pos.xCoord, t.pos.yCoord, t.pos.zCoord);
            GL11.glRotated(rot * t.maxRot, t.rot.xCoord, t.rot.yCoord, t.rot.zCoord);
            GL11.glTranslated(-t.pos.xCoord, -t.pos.yCoord, -t.pos.zCoord);
            renderPart(t.model, info.model, t.modelName);
            GL11.glPopMatrix();
         }
      }
   }

   public static void renderSteeringWheel(MCH_EntityAircraft ac, MCH_AircraftInfo info, float tickTime) {
      if (info.partSteeringWheel.size() > 0) {
         float rot = ac.prevRotYawWheel + (ac.rotYawWheel - ac.prevRotYawWheel) * tickTime;

         for (MCH_AircraftInfo.PartWheel t : info.partSteeringWheel) {
            GL11.glPushMatrix();
            GL11.glTranslated(t.pos.xCoord, t.pos.yCoord, t.pos.zCoord);
            GL11.glRotated(rot * t.rotDir, t.rot.xCoord, t.rot.yCoord, t.rot.zCoord);
            GL11.glTranslated(-t.pos.xCoord, -t.pos.yCoord, -t.pos.zCoord);
            renderPart(t.model, info.model, t.modelName);
            GL11.glPopMatrix();
         }
      }
   }

   public static void renderWheel(MCH_EntityAircraft ac, MCH_AircraftInfo info, float tickTime) {
      if (info.partWheel.size() > 0) {
         float yaw = ac.prevRotYawWheel + (ac.rotYawWheel - ac.prevRotYawWheel) * tickTime;

         for (MCH_AircraftInfo.PartWheel t : info.partWheel) {
            GL11.glPushMatrix();
            GL11.glTranslated(t.pos2.xCoord, t.pos2.yCoord, t.pos2.zCoord);
            GL11.glRotated(yaw * t.rotDir, t.rot.xCoord, t.rot.yCoord, t.rot.zCoord);
            GL11.glTranslated(-t.pos2.xCoord, -t.pos2.yCoord, -t.pos2.zCoord);
            GL11.glTranslated(t.pos.xCoord, t.pos.yCoord, t.pos.zCoord);
            GL11.glRotatef(ac.prevRotWheel + (ac.rotWheel - ac.prevRotWheel) * tickTime, 1.0F, 0.0F, 0.0F);
            GL11.glTranslated(-t.pos.xCoord, -t.pos.yCoord, -t.pos.zCoord);
            renderPart(t.model, info.model, t.modelName);
            GL11.glPopMatrix();
         }
      }
   }

   public static void renderRotPart(MCH_EntityAircraft ac, MCH_AircraftInfo info, float tickTime) {
      if (ac.haveRotPart()) {
         for (int i = 0; i < ac.rotPartRotation.length; i++) {
            float rot = ac.rotPartRotation[i];
            float prevRot = ac.prevRotPartRotation[i];
            if (prevRot > rot) {
               rot += 360.0F;
            }

            rot = MCH_Lib.smooth(rot, prevRot, tickTime);
            MCH_AircraftInfo.RotPart h = info.partRotPart.get(i);
            GL11.glPushMatrix();
            GL11.glTranslated(h.pos.xCoord, h.pos.yCoord, h.pos.zCoord);
            GL11.glRotatef(rot, (float)h.rot.xCoord, (float)h.rot.yCoord, (float)h.rot.zCoord);
            GL11.glTranslated(-h.pos.xCoord, -h.pos.yCoord, -h.pos.zCoord);
            renderPart(h.model, info.model, h.modelName);
            GL11.glPopMatrix();
         }
      }
   }

   public static void renderWeapon(MCH_EntityAircraft ac, MCH_AircraftInfo info, float tickTime) {
      MCH_WeaponSet beforeWs = null;
      Entity e = ac.getRiddenByEntity();
      int weaponIndex = 0;
      int cnt = 0;
      Iterator i$ = info.partWeapon.iterator();

      while (true) {
         MCH_WeaponSet ws;
         MCH_AircraftInfo.PartWeapon w;
         while (true) {
            if (!i$.hasNext()) {
               return;
            }

            w = (MCH_AircraftInfo.PartWeapon)i$.next();
            ws = ac.getWeaponByName(w.name[0]);
            if (ws != null && ws.getFirstWeapon().onTurret) {
               boolean onTurret = true;
            } else {
               boolean onTurret = false;
            }

            if (ws != beforeWs) {
               weaponIndex = 0;
               beforeWs = ws;
            }

            float rotYaw = 0.0F;
            float prevYaw = 0.0F;
            float rotPitch = 0.0F;
            float prevPitch = 0.0F;
            if (!w.hideGM || !W_Lib.isFirstPerson()) {
               break;
            }

            if (ws == null) {
               if (ac.isMountedEntity(MCH_Lib.getClientPlayer())) {
                  continue;
               }
               break;
            } else {
               boolean hide = false;

               for (String s : w.name) {
                  if (W_Lib.isClientPlayer(ac.getWeaponUserByWeaponName(s))) {
                     hide = true;
                     break;
                  }
               }

               if (!hide) {
                  break;
               }
            }
         }

         GL11.glPushMatrix();
         if (w.turret) {
            GL11.glTranslated(info.turretPosition.xCoord, info.turretPosition.yCoord, info.turretPosition.zCoord);
            float ty = MCH_Lib.smooth(ac.getLastRiderYaw() - ac.getRotYaw(), ac.prevLastRiderYaw - ac.prevRotationYaw, tickTime);
            GL11.glRotatef(ty, 0.0F, -1.0F, 0.0F);
            GL11.glTranslated(-info.turretPosition.xCoord, -info.turretPosition.yCoord, -info.turretPosition.zCoord);
         }

         GL11.glTranslated(w.pos.xCoord, w.pos.yCoord, w.pos.zCoord);
         if (w.yaw) {
            float var22;
            float var23;
            if (ws != null) {
               var22 = ws.rotationYaw - ws.defaultRotationYaw;
               var23 = ws.prevRotationYaw - ws.defaultRotationYaw;
            } else if (e != null) {
               var22 = e.rotationYaw - ac.getRotYaw();
               var23 = e.prevRotationYaw - ac.prevRotationYaw;
            } else {
               var22 = ac.getLastRiderYaw() - ac.rotationYaw;
               var23 = ac.prevLastRiderYaw - ac.prevRotationYaw;
            }

            if (var22 - var23 > 180.0F) {
               var23 += 360.0F;
            } else if (var22 - var23 < -180.0F) {
               var23 -= 360.0F;
            }

            GL11.glRotatef(var23 + (var22 - var23) * tickTime, 0.0F, -1.0F, 0.0F);
         }

         if (w.turret) {
            float ty = MCH_Lib.smooth(ac.getLastRiderYaw() - ac.getRotYaw(), ac.prevLastRiderYaw - ac.prevRotationYaw, tickTime);
            ty -= ws.rotationTurretYaw;
            GL11.glRotatef(-ty, 0.0F, -1.0F, 0.0F);
         }

         boolean rev_sign = false;
         if (ws != null && (int)ws.defaultRotationYaw != 0) {
            float t = MathHelper.wrapAngleTo180_float(ws.defaultRotationYaw);
            rev_sign = t >= 45.0F && t <= 135.0F || t <= -45.0F && t >= -135.0F;
            GL11.glRotatef(-ws.defaultRotationYaw, 0.0F, -1.0F, 0.0F);
         }

         if (w.pitch) {
            float var24;
            float var25;
            if (ws != null) {
               var24 = ws.rotationPitch;
               var25 = ws.prevRotationPitch;
            } else if (e != null) {
               var24 = e.rotationPitch;
               var25 = e.prevRotationPitch;
            } else {
               var24 = ac.getLastRiderPitch();
               var25 = ac.prevLastRiderPitch;
            }

            if (rev_sign) {
               var24 = -var24;
               var25 = -var25;
            }

            GL11.glRotatef(var25 + (var24 - var25) * tickTime, 1.0F, 0.0F, 0.0F);
         }

         if (ws != null && w.recoilBuf != 0.0F) {
            MCH_WeaponSet.Recoil r = ws.recoilBuf[0];
            if (w.name.length > 1) {
               for (String wnm : w.name) {
                  MCH_WeaponSet tws = ac.getWeaponByName(wnm);
                  if (tws != null && tws.recoilBuf[0].recoilBuf > r.recoilBuf) {
                     r = tws.recoilBuf[0];
                  }
               }
            }

            float recoilBuf = r.prevRecoilBuf + (r.recoilBuf - r.prevRecoilBuf) * tickTime;
            GL11.glTranslated(0.0, 0.0, w.recoilBuf * recoilBuf);
         }

         if (ws != null) {
            GL11.glRotatef(ws.defaultRotationYaw, 0.0F, -1.0F, 0.0F);
            if (w.rotBarrel) {
               float rotBrl = ws.prevRotBarrel + (ws.rotBarrel - ws.prevRotBarrel) * tickTime;
               GL11.glRotatef(rotBrl, (float)w.rot.xCoord, (float)w.rot.yCoord, (float)w.rot.zCoord);
            }
         }

         GL11.glTranslated(-w.pos.xCoord, -w.pos.yCoord, -w.pos.zCoord);
         if (!w.isMissile || !ac.isWeaponNotCooldown(ws, weaponIndex)) {
            renderPart(w.model, info.model, w.modelName);

            for (MCH_AircraftInfo.PartWeaponChild wc : w.child) {
               GL11.glPushMatrix();
               renderWeaponChild(ac, info, wc, ws, e, tickTime);
               GL11.glPopMatrix();
            }
         }

         GL11.glPopMatrix();
         weaponIndex++;
         cnt++;
      }
   }

   public static void renderWeaponChild(
      MCH_EntityAircraft ac, MCH_AircraftInfo info, MCH_AircraftInfo.PartWeaponChild w, MCH_WeaponSet ws, Entity e, float tickTime
   ) {
      float rotYaw = 0.0F;
      float prevYaw = 0.0F;
      float rotPitch = 0.0F;
      float prevPitch = 0.0F;
      GL11.glTranslated(w.pos.xCoord, w.pos.yCoord, w.pos.zCoord);
      if (w.yaw) {
         if (ws != null) {
            rotYaw = ws.rotationYaw - ws.defaultRotationYaw;
            prevYaw = ws.prevRotationYaw - ws.defaultRotationYaw;
         } else if (e != null) {
            rotYaw = e.rotationYaw - ac.getRotYaw();
            prevYaw = e.prevRotationYaw - ac.prevRotationYaw;
         } else {
            rotYaw = ac.getLastRiderYaw() - ac.rotationYaw;
            prevYaw = ac.prevLastRiderYaw - ac.prevRotationYaw;
         }

         if (rotYaw - prevYaw > 180.0F) {
            prevYaw += 360.0F;
         } else if (rotYaw - prevYaw < -180.0F) {
            prevYaw -= 360.0F;
         }

         GL11.glRotatef(prevYaw + (rotYaw - prevYaw) * tickTime, 0.0F, -1.0F, 0.0F);
      }

      boolean rev_sign = false;
      if (ws != null && (int)ws.defaultRotationYaw != 0) {
         float t = MathHelper.wrapAngleTo180_float(ws.defaultRotationYaw);
         rev_sign = t >= 45.0F && t <= 135.0F || t <= -45.0F && t >= -135.0F;
         GL11.glRotatef(-ws.defaultRotationYaw, 0.0F, -1.0F, 0.0F);
      }

      if (w.pitch) {
         if (ws != null) {
            rotPitch = ws.rotationPitch;
            prevPitch = ws.prevRotationPitch;
         } else if (e != null) {
            rotPitch = e.rotationPitch;
            prevPitch = e.prevRotationPitch;
         } else {
            rotPitch = ac.getLastRiderPitch();
            prevPitch = ac.prevLastRiderPitch;
         }

         if (rev_sign) {
            rotPitch = -rotPitch;
            prevPitch = -prevPitch;
         }

         GL11.glRotatef(prevPitch + (rotPitch - prevPitch) * tickTime, 1.0F, 0.0F, 0.0F);
      }

      if (ws != null && w.recoilBuf != 0.0F) {
         MCH_WeaponSet.Recoil r = ws.recoilBuf[0];
         if (w.name.length > 1) {
            for (String wnm : w.name) {
               MCH_WeaponSet tws = ac.getWeaponByName(wnm);
               if (tws != null && tws.recoilBuf[0].recoilBuf > r.recoilBuf) {
                  r = tws.recoilBuf[0];
               }
            }
         }

         float recoilBuf = r.prevRecoilBuf + (r.recoilBuf - r.prevRecoilBuf) * tickTime;
         GL11.glTranslated(0.0, 0.0, -w.recoilBuf * recoilBuf);
      }

      if (ws != null) {
         GL11.glRotatef(ws.defaultRotationYaw, 0.0F, -1.0F, 0.0F);
      }

      GL11.glTranslated(-w.pos.xCoord, -w.pos.yCoord, -w.pos.zCoord);
      renderPart(w.model, info.model, w.modelName);
   }

   public static void renderTrackRoller(MCH_EntityAircraft ac, MCH_AircraftInfo info, float tickTime) {
      if (info.partTrackRoller.size() > 0) {
         float[] rot = ac.rotTrackRoller;
         float[] prevRot = ac.prevRotTrackRoller;

         for (MCH_AircraftInfo.TrackRoller t : info.partTrackRoller) {
            GL11.glPushMatrix();
            GL11.glTranslated(t.pos.xCoord, t.pos.yCoord, t.pos.zCoord);
            GL11.glRotatef(prevRot[t.side] + (rot[t.side] - prevRot[t.side]) * tickTime, 1.0F, 0.0F, 0.0F);
            GL11.glTranslated(-t.pos.xCoord, -t.pos.yCoord, -t.pos.zCoord);
            renderPart(t.model, info.model, t.modelName);
            GL11.glPopMatrix();
         }
      }
   }

   public static void renderCrawlerTrack(MCH_EntityAircraft ac, MCH_AircraftInfo info, float tickTime) {
      if (info.partCrawlerTrack.size() > 0) {
         int prevWidth = GL11.glGetInteger(2833);
         Tessellator tessellator = Tessellator.instance;

         for (MCH_AircraftInfo.CrawlerTrack c : info.partCrawlerTrack) {
            GL11.glPointSize(c.len * 20.0F);
            if (MCH_Config.TestMode.prmBool) {
               GL11.glDisable(3553);
               GL11.glDisable(3042);
               tessellator.startDrawing(0);

               for (int i = 0; i < c.cx.length; i++) {
                  tessellator.setColorRGBA((int)(255.0F / c.cx.length * i), 80, 255 - (int)(255.0F / c.cx.length * i), 255);
                  tessellator.addVertex(c.z, c.cx[i], c.cy[i]);
               }

               tessellator.draw();
            }

            GL11.glEnable(3553);
            GL11.glEnable(3042);
            int L = c.lp.size() - 1;
            double rc = ac != null ? ac.rotCrawlerTrack[c.side] : 0.0;
            double pc = ac != null ? ac.prevRotCrawlerTrack[c.side] : 0.0;

            for (int i = 0; i < L; i++) {
               MCH_AircraftInfo.CrawlerTrackPrm cp = c.lp.get(i);
               MCH_AircraftInfo.CrawlerTrackPrm np = c.lp.get((i + 1) % L);
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
               GL11.glTranslated(0.0, x, y);
               GL11.glRotatef((float)r, -1.0F, 0.0F, 0.0F);
               renderPart(c.model, info.model, c.modelName);
               GL11.glPopMatrix();
            }
         }

         GL11.glEnable(3042);
         GL11.glPointSize(prevWidth);
      }
   }

   public static void renderHatch(MCH_EntityAircraft ac, MCH_AircraftInfo info, float tickTime) {
      if (info.haveHatch() && ac.partHatch != null) {
         float rot = ac.getHatchRotation();
         float prevRot = ac.getPrevHatchRotation();

         for (MCH_AircraftInfo.Hatch h : info.hatchList) {
            GL11.glPushMatrix();
            if (h.isSlide) {
               float r = ac.partHatch.rotation / ac.partHatch.rotationMax;
               float pr = ac.partHatch.prevRotation / ac.partHatch.rotationMax;
               float f = pr + (r - pr) * tickTime;
               GL11.glTranslated(h.pos.xCoord * f, h.pos.yCoord * f, h.pos.zCoord * f);
            } else {
               GL11.glTranslated(h.pos.xCoord, h.pos.yCoord, h.pos.zCoord);
               GL11.glRotatef(
                  (prevRot + (rot - prevRot) * tickTime) * h.maxRotFactor, (float)h.rot.xCoord, (float)h.rot.yCoord, (float)h.rot.zCoord
               );
               GL11.glTranslated(-h.pos.xCoord, -h.pos.yCoord, -h.pos.zCoord);
            }

            renderPart(h.model, info.model, h.modelName);
            GL11.glPopMatrix();
         }
      }
   }

   public static void renderThrottle(MCH_EntityAircraft ac, MCH_AircraftInfo info, float tickTime) {
      if (info.havePartThrottle()) {
         float throttle = MCH_Lib.smooth((float)ac.getCurrentThrottle(), (float)ac.getPrevCurrentThrottle(), tickTime);

         for (MCH_AircraftInfo.Throttle h : info.partThrottle) {
            GL11.glPushMatrix();
            GL11.glTranslated(h.pos.xCoord, h.pos.yCoord, h.pos.zCoord);
            GL11.glRotatef(throttle * h.rot2, (float)h.rot.xCoord, (float)h.rot.yCoord, (float)h.rot.zCoord);
            GL11.glTranslated(-h.pos.xCoord, -h.pos.yCoord, -h.pos.zCoord);
            GL11.glTranslated(h.slide.xCoord * throttle, h.slide.yCoord * throttle, h.slide.zCoord * throttle);
            renderPart(h.model, info.model, h.modelName);
            GL11.glPopMatrix();
         }
      }
   }

   public static void renderWeaponBay(MCH_EntityAircraft ac, MCH_AircraftInfo info, float tickTime) {
      for (int i = 0; i < info.partWeaponBay.size(); i++) {
         MCH_AircraftInfo.WeaponBay w = info.partWeaponBay.get(i);
         MCH_EntityAircraft.WeaponBay ws = ac.weaponBays[i];
         GL11.glPushMatrix();
         if (w.isSlide) {
            float r = ws.rot / 90.0F;
            float pr = ws.prevRot / 90.0F;
            float f = pr + (r - pr) * tickTime;
            GL11.glTranslated(w.pos.xCoord * f, w.pos.yCoord * f, w.pos.zCoord * f);
         } else {
            GL11.glTranslated(w.pos.xCoord, w.pos.yCoord, w.pos.zCoord);
            GL11.glRotatef(
               (ws.prevRot + (ws.rot - ws.prevRot) * tickTime) * w.maxRotFactor,
               (float)w.rot.xCoord,
               (float)w.rot.yCoord,
               (float)w.rot.zCoord
            );
            GL11.glTranslated(-w.pos.xCoord, -w.pos.yCoord, -w.pos.zCoord);
         }

         renderPart(w.model, info.model, w.modelName);
         GL11.glPopMatrix();
      }
   }

   public static void renderCamera(MCH_EntityAircraft ac, MCH_AircraftInfo info, float tickTime) {
      if (info.havePartCamera()) {
         float rotYaw = ac.camera.partRotationYaw;
         float prevRotYaw = ac.camera.prevPartRotationYaw;
         float rotPitch = ac.camera.partRotationPitch;
         float prevRotPitch = ac.camera.prevPartRotationPitch;
         float yaw = prevRotYaw + (rotYaw - prevRotYaw) * tickTime - ac.getRotYaw();
         float pitch = prevRotPitch + (rotPitch - prevRotPitch) * tickTime - ac.getRotPitch();

         for (MCH_AircraftInfo.Camera c : info.cameraList) {
            GL11.glPushMatrix();
            GL11.glTranslated(c.pos.xCoord, c.pos.yCoord, c.pos.zCoord);
            if (c.yawSync) {
               GL11.glRotatef(yaw, 0.0F, -1.0F, 0.0F);
            }

            if (c.pitchSync) {
               GL11.glRotatef(pitch, 1.0F, 0.0F, 0.0F);
            }

            GL11.glTranslated(-c.pos.xCoord, -c.pos.yCoord, -c.pos.zCoord);
            renderPart(c.model, info.model, c.modelName);
            GL11.glPopMatrix();
         }
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
               GL11.glTranslated(c.pos.xCoord * f, c.pos.yCoord * f, c.pos.zCoord * f);
            } else {
               GL11.glTranslated(c.pos.xCoord, c.pos.yCoord, c.pos.zCoord);
               GL11.glRotatef(
                  (prevRot + (rot - prevRot) * tickTime) * c.maxRotFactor, (float)c.rot.xCoord, (float)c.rot.yCoord, (float)c.rot.zCoord
               );
               GL11.glTranslated(-c.pos.xCoord, -c.pos.yCoord, -c.pos.zCoord);
            }

            renderPart(c.model, info.model, c.modelName);
            GL11.glPopMatrix();
         }
      }
   }

   public static void renderLandingGear(MCH_EntityAircraft ac, MCH_AircraftInfo info, float tickTime) {
      if (info.haveLandingGear() && ac.partLandingGear != null) {
         float rot = ac.getLandingGearRotation();
         float prevRot = ac.getPrevLandingGearRotation();
         float revR = 90.0F - rot;
         float revPr = 90.0F - prevRot;
         float rot1 = prevRot + (rot - prevRot) * tickTime;
         float rot1Rev = revPr + (revR - revPr) * tickTime;
         float rotHatch = 90.0F * MathHelper.sin(rot1 * 2.0F * (float) Math.PI / 180.0F) * 3.0F;
         if (rotHatch > 90.0F) {
            rotHatch = 90.0F;
         }

         for (MCH_AircraftInfo.LandingGear n : info.landingGear) {
            GL11.glPushMatrix();
            GL11.glTranslated(n.pos.xCoord, n.pos.yCoord, n.pos.zCoord);
            if (!n.reverse) {
               if (!n.hatch) {
                  GL11.glRotatef(rot1 * n.maxRotFactor, (float)n.rot.xCoord, (float)n.rot.yCoord, (float)n.rot.zCoord);
               } else {
                  GL11.glRotatef(rotHatch * n.maxRotFactor, (float)n.rot.xCoord, (float)n.rot.yCoord, (float)n.rot.zCoord);
               }
            } else {
               GL11.glRotatef(rot1Rev * n.maxRotFactor, (float)n.rot.xCoord, (float)n.rot.yCoord, (float)n.rot.zCoord);
            }

            if (n.enableRot2) {
               if (!n.reverse) {
                  GL11.glRotatef(rot1 * n.maxRotFactor2, (float)n.rot2.xCoord, (float)n.rot2.yCoord, (float)n.rot2.zCoord);
               } else {
                  GL11.glRotatef(rot1Rev * n.maxRotFactor2, (float)n.rot2.xCoord, (float)n.rot2.yCoord, (float)n.rot2.zCoord);
               }
            }

            GL11.glTranslated(-n.pos.xCoord, -n.pos.yCoord, -n.pos.zCoord);
            if (n.slide != null) {
               float f = rot / 90.0F;
               if (n.reverse) {
                  f = 1.0F - f;
               }

               GL11.glTranslated(f * n.slide.xCoord, f * n.slide.yCoord, f * n.slide.zCoord);
            }

            renderPart(n.model, info.model, n.modelName);
            GL11.glPopMatrix();
         }
      }
   }

   public static void renderEntityMarker(Entity entity) {
      Entity player = Minecraft.getMinecraft().thePlayer;
      if (player != null) {
         if (!W_Entity.isEqual(player, entity)) {
            MCH_EntityAircraft ac = null;
            if (player.ridingEntity instanceof MCH_EntityAircraft) {
               ac = (MCH_EntityAircraft)player.ridingEntity;
            } else if (player.ridingEntity instanceof MCH_EntitySeat) {
               ac = ((MCH_EntitySeat)player.ridingEntity).getParent();
            } else if (player.ridingEntity instanceof MCH_EntityUavStation) {
               ac = ((MCH_EntityUavStation)player.ridingEntity).getControlAircract();
            }

            if (ac != null) {
               if (!W_Entity.isEqual(ac, entity)) {
                  MCH_WeaponGuidanceSystem gs = ac.getCurrentWeapon(player).getCurrentWeapon().getGuidanceSystem();
                  if (gs != null && gs.canLockEntity(entity)) {
                     RenderManager rm = RenderManager.instance;
                     double dist = entity.getDistanceSqToEntity(rm.livingPlayer);
                     double x = entity.posX - RenderManager.renderPosX;
                     double y = entity.posY - RenderManager.renderPosY;
                     double z = entity.posZ - RenderManager.renderPosZ;
                     if (dist < 10000.0) {
                        float scl = 0.02666667F;
                        GL11.glPushMatrix();
                        GL11.glTranslatef((float)x, (float)y + entity.height + 0.5F, (float)z);
                        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
                        GL11.glRotatef(-rm.playerViewY, 0.0F, 1.0F, 0.0F);
                        GL11.glRotatef(rm.playerViewX, 1.0F, 0.0F, 0.0F);
                        GL11.glScalef(-0.02666667F, -0.02666667F, 0.02666667F);
                        GL11.glDisable(2896);
                        GL11.glTranslatef(0.0F, 9.374999F, 0.0F);
                        GL11.glDepthMask(false);
                        GL11.glEnable(3042);
                        GL11.glBlendFunc(770, 771);
                        GL11.glDisable(3553);
                        int prevWidth = GL11.glGetInteger(2849);
                        float size = Math.max(entity.width, entity.height) * 20.0F;
                        if (entity instanceof MCH_EntityAircraft) {
                           size *= 2.0F;
                        }

                        Tessellator tessellator = Tessellator.instance;
                        tessellator.startDrawing(2);
                        tessellator.setBrightness(240);
                        boolean isLockEntity = gs.isLockingEntity(entity);
                        if (isLockEntity) {
                           GL11.glLineWidth(MCH_Gui.scaleFactor * 1.5F);
                           tessellator.setColorRGBA_F(1.0F, 0.0F, 0.0F, 1.0F);
                        } else {
                           GL11.glLineWidth(MCH_Gui.scaleFactor);
                           tessellator.setColorRGBA_F(1.0F, 0.3F, 0.0F, 8.0F);
                        }

                        tessellator.addVertex(-size - 1.0F, 0.0, 0.0);
                        tessellator.addVertex(-size - 1.0F, size * 2.0F, 0.0);
                        tessellator.addVertex(size + 1.0F, size * 2.0F, 0.0);
                        tessellator.addVertex(size + 1.0F, 0.0, 0.0);
                        tessellator.draw();
                        GL11.glPopMatrix();
                        if (!ac.isUAV() && isLockEntity && Minecraft.getMinecraft().gameSettings.thirdPersonView == 0) {
                           GL11.glPushMatrix();
                           tessellator.startDrawing(1);
                           GL11.glLineWidth(1.0F);
                           tessellator.setColorRGBA_F(1.0F, 0.0F, 0.0F, 1.0F);
                           tessellator.addVertex(x, y + entity.height / 2.0F, z);
                           tessellator.addVertex(
                              ac.lastTickPosX - RenderManager.renderPosX,
                              ac.lastTickPosY - RenderManager.renderPosY - 1.0,
                              ac.lastTickPosZ - RenderManager.renderPosZ
                           );
                           tessellator.setBrightness(240);
                           tessellator.draw();
                           GL11.glPopMatrix();
                        }

                        GL11.glLineWidth(prevWidth);
                        GL11.glEnable(3553);
                        GL11.glDepthMask(true);
                        GL11.glEnable(2896);
                        GL11.glDisable(3042);
                        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                     }
                  }
               }
            }
         }
      }
   }

   public static void renderRope(MCH_EntityAircraft ac, MCH_AircraftInfo info, double x, double y, double z, float tickTime) {
      GL11.glPushMatrix();
      Tessellator tessellator = Tessellator.instance;
      if (ac.isRepelling()) {
         GL11.glDisable(3553);
         GL11.glDisable(2896);

         for (int i = 0; i < info.repellingHooks.size(); i++) {
            tessellator.startDrawing(3);
            tessellator.setColorOpaque_I(0);
            tessellator.addVertex(
               info.repellingHooks.get(i).pos.xCoord, info.repellingHooks.get(i).pos.yCoord, info.repellingHooks.get(i).pos.zCoord
            );
            tessellator.addVertex(
               info.repellingHooks.get(i).pos.xCoord,
               info.repellingHooks.get(i).pos.yCoord + ac.ropesLength,
               info.repellingHooks.get(i).pos.zCoord
            );
            tessellator.draw();
         }

         GL11.glEnable(2896);
         GL11.glEnable(3553);
      }

      GL11.glPopMatrix();
   }
}
