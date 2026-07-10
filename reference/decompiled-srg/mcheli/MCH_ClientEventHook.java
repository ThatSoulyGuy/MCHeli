/*
 * Decompiled with CFR 0.152.
 */
package mcheli;

import java.util.ArrayList;
import java.util.List;
import mcheli.MCH_ClientCommonTickHandler;
import mcheli.MCH_ClientTickHandlerBase;
import mcheli.MCH_Config;
import mcheli.MCH_Lib;
import mcheli.MCH_TextureManagerDummy;
import mcheli.MCH_ViewEntityDummy;
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
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.WorldEvent;
import org.lwjgl.opengl.GL11;

public class MCH_ClientEventHook
extends W_ClientEventHook {
    MCH_TextureManagerDummy dummyTextureManager = null;
    public static List<MCH_EntityAircraft> haveSearchLightAircraft = new ArrayList();
    private static final ResourceLocation ir_strobe = new ResourceLocation("mcheli", "textures/ir_strobe.png");
    private static boolean cancelRender = true;

    public void renderLivingEventSpecialsPre(RenderLivingEvent.Specials.Pre event) {
        MCH_EntityAircraft ac;
        if (MCH_Config.DisableRenderLivingSpecials.prmBool && (ac = MCH_EntityAircraft.getAircraft_RiddenOrControl((Entity)Minecraft.func_71410_x().field_71439_g)) != null && ac.isMountedEntity((Entity)event.entity)) {
            event.setCanceled(true);
            return;
        }
    }

    public void renderLivingEventSpecialsPost(RenderLivingEvent.Specials.Post event) {
    }

    private void renderIRStrobe(EntityLivingBase entity, RenderLivingEvent.Specials.Post event) {
        int cm = MCH_ClientCommonTickHandler.cameraMode;
        if (cm == 0) {
            return;
        }
        int ticks = entity.field_70173_aa % 20;
        if (ticks >= 4) {
            return;
        }
        float alpha = ticks == 2 || ticks == 1 ? 1.0f : 0.5f;
        EntityClientPlayerMP player = Minecraft.func_71410_x().field_71439_g;
        if (player == null) {
            return;
        }
        if (!player.func_142014_c(entity)) {
            return;
        }
        int j = 240;
        int k = 240;
        OpenGlHelper.func_77475_a((int)OpenGlHelper.field_77476_b, (float)((float)j / 1.0f), (float)((float)k / 1.0f));
        GL11.glColor4f((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        float f1 = 0.080000006f;
        GL11.glPushMatrix();
        GL11.glTranslated((double)event.x, (double)(event.y + (double)((float)((double)entity.field_70131_O * 0.75))), (double)event.z);
        GL11.glNormal3f((float)0.0f, (float)1.0f, (float)0.0f);
        GL11.glRotatef((float)(-RenderManager.field_78727_a.field_78735_i), (float)0.0f, (float)1.0f, (float)0.0f);
        GL11.glRotatef((float)RenderManager.field_78727_a.field_78732_j, (float)1.0f, (float)0.0f, (float)0.0f);
        GL11.glScalef((float)(-f1), (float)(-f1), (float)f1);
        GL11.glEnable((int)3042);
        OpenGlHelper.func_148821_a((int)770, (int)771, (int)1, (int)0);
        GL11.glEnable((int)3553);
        RenderManager.field_78727_a.field_78724_e.func_110577_a(ir_strobe);
        GL11.glAlphaFunc((int)516, (float)0.003921569f);
        Tessellator tessellator = Tessellator.field_78398_a;
        tessellator.func_78382_b();
        tessellator.func_78369_a(1.0f, 1.0f, 1.0f, alpha * (cm == 1 ? 0.9f : 0.5f));
        int i = (int)Math.max(entity.field_70130_N, entity.field_70131_O) * 20;
        tessellator.func_78374_a((double)(-i), (double)(-i), 0.1, 0.0, 0.0);
        tessellator.func_78374_a((double)(-i), (double)i, 0.1, 0.0, 1.0);
        tessellator.func_78374_a((double)i, (double)i, 0.1, 1.0, 1.0);
        tessellator.func_78374_a((double)i, (double)(-i), 0.1, 1.0, 0.0);
        tessellator.func_78381_a();
        GL11.glEnable((int)2896);
        GL11.glPopMatrix();
    }

    public void mouseEvent(MouseEvent event) {
        if (MCH_ClientTickHandlerBase.updateMouseWheel((int)event.dwheel)) {
            event.setCanceled(true);
        }
    }

    public static void setCancelRender(boolean cancel) {
        cancelRender = cancel;
    }

    public void renderLivingEventPre(RenderLivingEvent.Pre event) {
        RenderManager rm;
        for (MCH_EntityAircraft ac : haveSearchLightAircraft) {
            OpenGlHelper.func_77475_a((int)OpenGlHelper.field_77476_b, (float)ac.getSearchLightValue((Entity)event.entity), (float)240.0f);
        }
        if (MCH_Config.EnableModEntityRender.prmBool && cancelRender && (event.entity.field_70154_o instanceof MCH_EntityAircraft || event.entity.field_70154_o instanceof MCH_EntitySeat)) {
            event.setCanceled(true);
            return;
        }
        if (MCH_Config.EnableReplaceTextureManager.prmBool && (rm = W_Reflection.getRenderManager((Render)event.renderer)) != null && !(rm.field_78724_e instanceof MCH_TextureManagerDummy)) {
            if (this.dummyTextureManager == null) {
                this.dummyTextureManager = new MCH_TextureManagerDummy(rm.field_78724_e);
            }
            rm.field_78724_e = this.dummyTextureManager;
        }
    }

    public void renderLivingEventPost(RenderLivingEvent.Post event) {
        MCH_RenderAircraft.renderEntityMarker((Entity)event.entity);
        MCH_GuiTargetMarker.addMarkEntityPos((int)2, (Entity)event.entity, (double)event.x, (double)(event.y + (double)event.entity.field_70131_O + 0.5), (double)event.z);
        MCH_ClientLightWeaponTickHandler.markEntity((Entity)event.entity, (double)event.x, (double)(event.y + (double)(event.entity.field_70131_O / 2.0f)), (double)event.z);
    }

    public void renderPlayerPre(RenderPlayerEvent.Pre event) {
        MCH_EntityAircraft v;
        if (event.entity == null) {
            return;
        }
        if (event.entity.field_70154_o instanceof MCH_EntityAircraft && (v = (MCH_EntityAircraft)event.entity.field_70154_o).getAcInfo() != null && v.getAcInfo().hideEntity) {
            event.setCanceled(true);
            return;
        }
    }

    public void renderPlayerPost(RenderPlayerEvent.Post event) {
    }

    public void worldEventUnload(WorldEvent.Unload event) {
        MCH_ViewEntityDummy.onUnloadWorld();
    }

    public void entityJoinWorldEvent(EntityJoinWorldEvent event) {
        if (event.entity.func_70028_i(MCH_Lib.getClientPlayer())) {
            MCH_Lib.DbgLog((boolean)true, (String)("MCH_ClientEventHook.entityJoinWorldEvent : " + event.entity), (Object[])new Object[0]);
            MCH_ItemRangeFinder.mode = Minecraft.func_71410_x().func_71356_B() ? 1 : 0;
            MCH_ParticlesUtil.clearMarkPoint();
        }
    }
}

