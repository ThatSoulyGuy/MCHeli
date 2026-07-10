package mcheli;

import java.util.ArrayList;
import java.util.List;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_EntitySeat;
import mcheli.aircraft.MCH_RenderAircraft;
import mcheli.lweapon.MCH_ClientLightWeaponTickHandler;
import mcheli.multiplay.MCH_GuiTargetMarker;
import mcheli.particles.MCH_ParticlesUtil;
import mcheli.tool.rangefinder.MCH_ItemRangeFinder;
import mcheli.wrapper.W_ClientEventHook;
import mcheli.wrapper.W_Reflection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderLivingEvent.Specials.Post;
import net.minecraftforge.client.event.RenderLivingEvent.Specials.Pre;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.WorldEvent.Unload;
import org.lwjgl.opengl.GL11;

public class MCH_ClientEventHook extends W_ClientEventHook {
   MCH_TextureManagerDummy dummyTextureManager = null;
   public static List<MCH_EntityAircraft> haveSearchLightAircraft = new ArrayList<>();
   private static final ResourceLocation ir_strobe = new ResourceLocation("mcheli", "textures/ir_strobe.png");
   private static boolean cancelRender = true;

   @Override
   public void renderLivingEventSpecialsPre(Pre event) {
      if (MCH_Config.DisableRenderLivingSpecials.prmBool) {
         MCH_EntityAircraft ac = MCH_EntityAircraft.getAircraft_RiddenOrControl(Minecraft.getMinecraft().thePlayer);
         if (ac != null && ac.isMountedEntity(event.entity)) {
            event.setCanceled(true);
            return;
         }
      }
   }

   @Override
   public void renderLivingEventSpecialsPost(Post event) {
   }

   private void renderIRStrobe(EntityLivingBase entity, Post event) {
      int cm = MCH_ClientCommonTickHandler.cameraMode;
      if (cm != 0) {
         int ticks = entity.ticksExisted % 20;
         if (ticks < 4) {
            float alpha = ticks != 2 && ticks != 1 ? 0.5F : 1.0F;
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            if (player != null) {
               if (player.isOnSameTeam(entity)) {
                  int j = 240;
                  int k = 240;
                  OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, j / 1.0F, k / 1.0F);
                  GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                  float f1 = 0.080000006F;
                  GL11.glPushMatrix();
                  GL11.glTranslated(event.x, event.y + (float)(entity.height * 0.75), event.z);
                  GL11.glNormal3f(0.0F, 1.0F, 0.0F);
                  GL11.glRotatef(-RenderManager.instance.playerViewY, 0.0F, 1.0F, 0.0F);
                  GL11.glRotatef(RenderManager.instance.playerViewX, 1.0F, 0.0F, 0.0F);
                  GL11.glScalef(-f1, -f1, f1);
                  GL11.glEnable(3042);
                  OpenGlHelper.glBlendFunc(770, 771, 1, 0);
                  GL11.glEnable(3553);
                  RenderManager.instance.renderEngine.bindTexture(ir_strobe);
                  GL11.glAlphaFunc(516, 0.003921569F);
                  Tessellator tessellator = Tessellator.instance;
                  tessellator.startDrawingQuads();
                  tessellator.setColorRGBA_F(1.0F, 1.0F, 1.0F, alpha * (cm == 1 ? 0.9F : 0.5F));
                  int i = (int)Math.max(entity.width, entity.height) * 20;
                  tessellator.addVertexWithUV(-i, -i, 0.1, 0.0, 0.0);
                  tessellator.addVertexWithUV(-i, i, 0.1, 0.0, 1.0);
                  tessellator.addVertexWithUV(i, i, 0.1, 1.0, 1.0);
                  tessellator.addVertexWithUV(i, -i, 0.1, 1.0, 0.0);
                  tessellator.draw();
                  GL11.glEnable(2896);
                  GL11.glPopMatrix();
               }
            }
         }
      }
   }

   @Override
   public void mouseEvent(MouseEvent event) {
      if (MCH_ClientTickHandlerBase.updateMouseWheel(event.dwheel)) {
         event.setCanceled(true);
      }
   }

   public static void setCancelRender(boolean cancel) {
      cancelRender = cancel;
   }

   @Override
   public void renderLivingEventPre(net.minecraftforge.client.event.RenderLivingEvent.Pre event) {
      for (MCH_EntityAircraft ac : haveSearchLightAircraft) {
         OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, ac.getSearchLightValue(event.entity), 240.0F);
      }

      if (!MCH_Config.EnableModEntityRender.prmBool
         || !cancelRender
         || !(event.entity.ridingEntity instanceof MCH_EntityAircraft) && !(event.entity.ridingEntity instanceof MCH_EntitySeat)) {
         if (MCH_Config.EnableReplaceTextureManager.prmBool) {
            RenderManager rm = W_Reflection.getRenderManager(event.renderer);
            if (rm != null && !(rm.renderEngine instanceof MCH_TextureManagerDummy)) {
               if (this.dummyTextureManager == null) {
                  this.dummyTextureManager = new MCH_TextureManagerDummy(rm.renderEngine);
               }

               rm.renderEngine = this.dummyTextureManager;
            }
         }
      } else {
         event.setCanceled(true);
      }
   }

   @Override
   public void renderLivingEventPost(net.minecraftforge.client.event.RenderLivingEvent.Post event) {
      MCH_RenderAircraft.renderEntityMarker(event.entity);
      MCH_GuiTargetMarker.addMarkEntityPos(2, event.entity, event.x, event.y + event.entity.height + 0.5, event.z);
      MCH_ClientLightWeaponTickHandler.markEntity(event.entity, event.x, event.y + event.entity.height / 2.0F, event.z);
   }

   @Override
   public void renderPlayerPre(net.minecraftforge.client.event.RenderPlayerEvent.Pre event) {
      if (event.entity != null) {
         if (event.entity.ridingEntity instanceof MCH_EntityAircraft) {
            MCH_EntityAircraft v = (MCH_EntityAircraft)event.entity.ridingEntity;
            if (v.getAcInfo() != null && v.getAcInfo().hideEntity) {
               event.setCanceled(true);
               return;
            }
         }
      }
   }

   @Override
   public void renderPlayerPost(net.minecraftforge.client.event.RenderPlayerEvent.Post event) {
   }

   @Override
   public void worldEventUnload(Unload event) {
      MCH_ViewEntityDummy.onUnloadWorld();
   }

   @Override
   public void entityJoinWorldEvent(EntityJoinWorldEvent event) {
      if (event.entity.isEntityEqual(MCH_Lib.getClientPlayer())) {
         MCH_Lib.DbgLog(true, "MCH_ClientEventHook.entityJoinWorldEvent : " + event.entity);
         MCH_ItemRangeFinder.mode = Minecraft.getMinecraft().isSingleplayer() ? 1 : 0;
         MCH_ParticlesUtil.clearMarkPoint();
      }
   }
}
