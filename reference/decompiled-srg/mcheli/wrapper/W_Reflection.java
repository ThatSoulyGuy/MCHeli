/*
 * Decompiled with CFR 0.152.
 */
package mcheli.wrapper;

import cpw.mods.fml.common.ObfuscationReflectionHelper;
import java.util.List;
import java.util.Queue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.NetworkSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;

/*
 * Exception performing whole class analysis ignored.
 */
public class W_Reflection {
    public static RenderManager getRenderManager(Render render) {
        try {
            return (RenderManager)ObfuscationReflectionHelper.getPrivateValue(Render.class, (Object)render, (String[])new String[]{"field_76990_c", "renderManager"});
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void restoreDefaultThirdPersonDistance() {
        W_Reflection.setThirdPersonDistance((float)4.0f);
    }

    public static void setThirdPersonDistance(float dist) {
        if ((double)dist < 0.1) {
            return;
        }
        try {
            Minecraft mc = Minecraft.func_71410_x();
            ObfuscationReflectionHelper.setPrivateValue(EntityRenderer.class, (Object)mc.field_71460_t, (Object)Float.valueOf(dist), (String[])new String[]{"field_78490_B", "thirdPersonDistance"});
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static float getThirdPersonDistance() {
        try {
            Minecraft mc = Minecraft.func_71410_x();
            return ((Float)ObfuscationReflectionHelper.getPrivateValue(EntityRenderer.class, (Object)mc.field_71460_t, (String[])new String[]{"field_78490_B", "thirdPersonDistance"})).floatValue();
        }
        catch (Exception e) {
            e.printStackTrace();
            return 4.0f;
        }
    }

    public static void setCameraRoll(float roll) {
        try {
            roll = MathHelper.func_76142_g((float)roll);
            Minecraft mc = Minecraft.func_71410_x();
            ObfuscationReflectionHelper.setPrivateValue(EntityRenderer.class, (Object)Minecraft.func_71410_x().field_71460_t, (Object)Float.valueOf(roll), (String[])new String[]{"field_78495_O", "camRoll"});
            ObfuscationReflectionHelper.setPrivateValue(EntityRenderer.class, (Object)Minecraft.func_71410_x().field_71460_t, (Object)Float.valueOf(roll), (String[])new String[]{"field_78505_P", "prevCamRoll"});
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static float getPrevCameraRoll() {
        try {
            Minecraft mc = Minecraft.func_71410_x();
            return ((Float)ObfuscationReflectionHelper.getPrivateValue(EntityRenderer.class, (Object)Minecraft.func_71410_x().field_71460_t, (String[])new String[]{"field_78505_P", "prevCamRoll"})).floatValue();
        }
        catch (Exception e) {
            e.printStackTrace();
            return 0.0f;
        }
    }

    public static void restoreCameraZoom() {
        W_Reflection.setCameraZoom((float)1.0f);
    }

    public static void setCameraZoom(float zoom) {
        try {
            Minecraft mc = Minecraft.func_71410_x();
            ObfuscationReflectionHelper.setPrivateValue(EntityRenderer.class, (Object)mc.field_71460_t, (Object)Float.valueOf(zoom), (String[])new String[]{"field_78503_V", "cameraZoom"});
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setItemRenderer(ItemRenderer r) {
        try {
            Minecraft mc = Minecraft.func_71410_x();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setCreativeDigSpeed(int n) {
        try {
            Minecraft mc = Minecraft.func_71410_x();
            ObfuscationReflectionHelper.setPrivateValue(PlayerControllerMP.class, (Object)mc.field_71442_b, (Object)n, (String[])new String[]{"field_78781_i", "blockHitDelay"});
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static ItemRenderer getItemRenderer() {
        return Minecraft.func_71410_x().field_71460_t.field_78516_c;
    }

    public static void setItemRenderer_ItemToRender(ItemStack itemToRender) {
        try {
            ObfuscationReflectionHelper.setPrivateValue(ItemRenderer.class, (Object)W_Reflection.getItemRenderer(), (Object)itemToRender, (String[])new String[]{"field_78453_b", "itemToRender"});
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static ItemStack getItemRenderer_ItemToRender() {
        try {
            ItemStack itemstack = (ItemStack)ObfuscationReflectionHelper.getPrivateValue(ItemRenderer.class, (Object)W_Reflection.getItemRenderer(), (String[])new String[]{"field_78453_b", "itemToRender"});
            return itemstack;
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void setItemRendererProgress(float equippedProgress) {
        try {
            ObfuscationReflectionHelper.setPrivateValue(ItemRenderer.class, (Object)W_Reflection.getItemRenderer(), (Object)Float.valueOf(equippedProgress), (String[])new String[]{"field_78454_c", "equippedProgress"});
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setBoundingBox(Entity entity, AxisAlignedBB bb) {
        try {
            ObfuscationReflectionHelper.setPrivateValue(Entity.class, (Object)entity, (Object)bb, (String[])new String[]{"field_70121_D", "boundingBox"});
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List getNetworkManagers() {
        try {
            List list = (List)ObfuscationReflectionHelper.getPrivateValue(NetworkSystem.class, (Object)MinecraftServer.func_71276_C().func_147137_ag(), (String[])new String[]{"field_151272_f", "networkManagers"});
            return list;
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Queue getReceivedPacketsQueue(NetworkManager nm) {
        try {
            Queue queue = (Queue)ObfuscationReflectionHelper.getPrivateValue(NetworkManager.class, (Object)nm, (String[])new String[]{"field_150748_i", "receivedPacketsQueue"});
            return queue;
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Queue getSendPacketsQueue(NetworkManager nm) {
        try {
            Queue queue = (Queue)ObfuscationReflectionHelper.getPrivateValue(NetworkManager.class, (Object)nm, (String[])new String[]{"field_150745_j", "outboundPacketsQueue"});
            return queue;
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

