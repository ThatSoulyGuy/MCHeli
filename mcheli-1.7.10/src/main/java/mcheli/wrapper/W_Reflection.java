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

public class W_Reflection {
   public static RenderManager getRenderManager(Render render) {
      try {
         return (RenderManager)ObfuscationReflectionHelper.getPrivateValue(Render.class, render, new String[]{"field_76990_c", "renderManager"});
      } catch (Exception e) {
         e.printStackTrace();
         return null;
      }
   }

   public static void restoreDefaultThirdPersonDistance() {
      setThirdPersonDistance(4.0F);
   }

   public static void setThirdPersonDistance(float dist) {
      if (!(dist < 0.1)) {
         try {
            Minecraft mc = Minecraft.getMinecraft();
            ObfuscationReflectionHelper.setPrivateValue(EntityRenderer.class, mc.entityRenderer, dist, new String[]{"field_78490_B", "thirdPersonDistance"});
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   }

   public static float getThirdPersonDistance() {
      try {
         Minecraft mc = Minecraft.getMinecraft();
         return (Float)ObfuscationReflectionHelper.getPrivateValue(EntityRenderer.class, mc.entityRenderer, new String[]{"field_78490_B", "thirdPersonDistance"});
      } catch (Exception e) {
         e.printStackTrace();
         return 4.0F;
      }
   }

   public static void setCameraRoll(float roll) {
      try {
         roll = MathHelper.wrapAngleTo180_float(roll);
         Minecraft mc = Minecraft.getMinecraft();
         ObfuscationReflectionHelper.setPrivateValue(
            EntityRenderer.class, Minecraft.getMinecraft().entityRenderer, roll, new String[]{"field_78495_O", "camRoll"}
         );
         ObfuscationReflectionHelper.setPrivateValue(
            EntityRenderer.class, Minecraft.getMinecraft().entityRenderer, roll, new String[]{"field_78505_P", "prevCamRoll"}
         );
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static float getPrevCameraRoll() {
      try {
         Minecraft mc = Minecraft.getMinecraft();
         return (Float)ObfuscationReflectionHelper.getPrivateValue(
            EntityRenderer.class, Minecraft.getMinecraft().entityRenderer, new String[]{"field_78505_P", "prevCamRoll"}
         );
      } catch (Exception e) {
         e.printStackTrace();
         return 0.0F;
      }
   }

   public static void restoreCameraZoom() {
      setCameraZoom(1.0F);
   }

   public static void setCameraZoom(float zoom) {
      try {
         Minecraft mc = Minecraft.getMinecraft();
         ObfuscationReflectionHelper.setPrivateValue(EntityRenderer.class, mc.entityRenderer, zoom, new String[]{"field_78503_V", "cameraZoom"});
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static void setItemRenderer(ItemRenderer r) {
      try {
         Minecraft mc = Minecraft.getMinecraft();
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static void setCreativeDigSpeed(int n) {
      try {
         Minecraft mc = Minecraft.getMinecraft();
         ObfuscationReflectionHelper.setPrivateValue(PlayerControllerMP.class, mc.playerController, n, new String[]{"field_78781_i", "blockHitDelay"});
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static ItemRenderer getItemRenderer() {
      return Minecraft.getMinecraft().entityRenderer.itemRenderer;
   }

   public static void setItemRenderer_ItemToRender(ItemStack itemToRender) {
      try {
         ObfuscationReflectionHelper.setPrivateValue(ItemRenderer.class, getItemRenderer(), itemToRender, new String[]{"field_78453_b", "itemToRender"});
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static ItemStack getItemRenderer_ItemToRender() {
      try {
         return (ItemStack)ObfuscationReflectionHelper.getPrivateValue(ItemRenderer.class, getItemRenderer(), new String[]{"field_78453_b", "itemToRender"});
      } catch (Exception e) {
         e.printStackTrace();
         return null;
      }
   }

   public static void setItemRendererProgress(float equippedProgress) {
      try {
         ObfuscationReflectionHelper.setPrivateValue(ItemRenderer.class, getItemRenderer(), equippedProgress, new String[]{"field_78454_c", "equippedProgress"});
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static void setBoundingBox(Entity entity, AxisAlignedBB bb) {
      try {
         ObfuscationReflectionHelper.setPrivateValue(Entity.class, entity, bb, new String[]{"field_70121_D", "boundingBox"});
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static List getNetworkManagers() {
      try {
         return (List)ObfuscationReflectionHelper.getPrivateValue(
            NetworkSystem.class, MinecraftServer.getServer().func_147137_ag(), new String[]{"field_151272_f", "networkManagers"}
         );
      } catch (Exception e) {
         e.printStackTrace();
         return null;
      }
   }

   public static Queue getReceivedPacketsQueue(NetworkManager nm) {
      try {
         return (Queue)ObfuscationReflectionHelper.getPrivateValue(NetworkManager.class, nm, new String[]{"field_150748_i", "receivedPacketsQueue"});
      } catch (Exception e) {
         e.printStackTrace();
         return null;
      }
   }

   public static Queue getSendPacketsQueue(NetworkManager nm) {
      try {
         return (Queue)ObfuscationReflectionHelper.getPrivateValue(NetworkManager.class, nm, new String[]{"field_150745_j", "outboundPacketsQueue"});
      } catch (Exception e) {
         e.printStackTrace();
         return null;
      }
   }
}
