package mcheli.gltd;

import mcheli.wrapper.W_McClient;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import net.minecraftforge.client.IItemRenderer.ItemRendererHelper;
import org.lwjgl.opengl.GL11;

public class MCH_ItemGLTDRender implements IItemRenderer {
   public boolean handleRenderType(ItemStack item, ItemRenderType type) {
      return type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON || type == ItemRenderType.ENTITY;
   }

   public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
      return type == ItemRenderType.ENTITY;
   }

   public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
      GL11.glPushMatrix();
      GL11.glEnable(2884);
      W_McClient.MOD_bindTexture("textures/gltd.png");
      switch (type) {
         case ENTITY:
            GL11.glEnable(32826);
            GL11.glEnable(2903);
            GL11.glScalef(1.0F, 1.0F, 1.0F);
            MCH_RenderGLTD.model.renderAll();
            GL11.glDisable(32826);
            break;
         case EQUIPPED:
            GL11.glEnable(32826);
            GL11.glEnable(2903);
            GL11.glTranslatef(0.0F, 0.005F, -0.165F);
            GL11.glRotatef(-10.0F, 0.0F, 0.0F, 1.0F);
            GL11.glRotatef(-10.0F, 1.0F, 0.0F, 0.0F);
            MCH_RenderGLTD.model.renderAll();
            GL11.glDisable(32826);
            break;
         case EQUIPPED_FIRST_PERSON:
            GL11.glEnable(32826);
            GL11.glEnable(2903);
            GL11.glTranslatef(0.3F, 0.5F, -0.5F);
            GL11.glScalef(0.5F, 0.5F, 0.5F);
            GL11.glRotatef(10.0F, 0.0F, 0.0F, 1.0F);
            GL11.glRotatef(50.0F, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(-10.0F, 1.0F, 0.0F, 0.0F);
            MCH_RenderGLTD.model.renderAll();
            GL11.glDisable(32826);
         case INVENTORY:
         case FIRST_PERSON_MAP:
      }

      GL11.glPopMatrix();
   }
}
