package mcheli.tool.rangefinder;

import mcheli.MCH_ModelManager;
import mcheli.wrapper.W_McClient;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import net.minecraftforge.client.IItemRenderer.ItemRendererHelper;
import org.lwjgl.opengl.GL11;

public class MCH_ItemRenderRangeFinder implements IItemRenderer {
   public boolean handleRenderType(ItemStack item, ItemRenderType type) {
      return type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON || type == ItemRenderType.ENTITY;
   }

   public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
      return type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON || type == ItemRenderType.ENTITY;
   }

   public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
      GL11.glPushMatrix();
      W_McClient.MOD_bindTexture("textures/rangefinder.png");
      float size = 1.0F;
      switch (type) {
         case ENTITY:
            size = 2.2F;
            GL11.glScalef(size, size, size);
            GL11.glRotatef(-130.0F, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(70.0F, 1.0F, 0.0F, 0.0F);
            GL11.glRotatef(5.0F, 0.0F, 0.0F, 1.0F);
            GL11.glTranslatef(0.0F, 0.0F, -0.0F);
            MCH_ModelManager.render("rangefinder");
            break;
         case EQUIPPED:
            size = 2.2F;
            GL11.glScalef(size, size, size);
            GL11.glRotatef(-130.0F, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(70.0F, 1.0F, 0.0F, 0.0F);
            GL11.glRotatef(5.0F, 0.0F, 0.0F, 1.0F);
            if (Minecraft.getMinecraft().thePlayer.getItemInUseDuration() > 0) {
               GL11.glTranslatef(0.4F, -0.35F, -0.3F);
            } else {
               GL11.glTranslatef(0.2F, -0.35F, -0.3F);
            }

            MCH_ModelManager.render("rangefinder");
            break;
         case EQUIPPED_FIRST_PERSON:
            if (!MCH_ItemRangeFinder.isUsingScope(Minecraft.getMinecraft().thePlayer)) {
               size = 2.2F;
               GL11.glScalef(size, size, size);
               GL11.glRotatef(-210.0F, 0.0F, 1.0F, 0.0F);
               GL11.glRotatef(-10.0F, 1.0F, 0.0F, 0.0F);
               GL11.glRotatef(-10.0F, 0.0F, 0.0F, 1.0F);
               GL11.glTranslatef(0.06F, 0.53F, -0.1F);
               MCH_ModelManager.render("rangefinder");
            }
      }

      GL11.glPopMatrix();
   }
}
