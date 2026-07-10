package mcheli.block;

import mcheli.MCH_ModelManager;
import mcheli.wrapper.W_McClient;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import net.minecraftforge.client.IItemRenderer.ItemRendererHelper;
import org.lwjgl.opengl.GL11;

public class MCH_DraftingTableItemRender implements IItemRenderer {
   public boolean handleRenderType(ItemStack item, ItemRenderType type) {
      switch (type) {
         case ENTITY:
         case EQUIPPED:
         case EQUIPPED_FIRST_PERSON:
         case INVENTORY:
            return true;
         default:
            return false;
      }
   }

   public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
      return true;
   }

   public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
      GL11.glPushMatrix();
      W_McClient.MOD_bindTexture("textures/blocks/drafting_table.png");
      GL11.glEnable(32826);
      switch (type) {
         case ENTITY:
            GL11.glTranslatef(0.0F, 0.5F, 0.0F);
            GL11.glScalef(1.5F, 1.5F, 1.5F);
            break;
         case EQUIPPED:
            GL11.glTranslatef(0.0F, 0.0F, 0.5F);
            GL11.glScalef(1.0F, 1.0F, 1.0F);
            break;
         case EQUIPPED_FIRST_PERSON:
            GL11.glTranslatef(0.75F, 0.0F, 0.0F);
            GL11.glScalef(1.0F, 1.0F, 1.0F);
            GL11.glRotatef(90.0F, 0.0F, -1.0F, 0.0F);
            break;
         case INVENTORY:
            float INV_SIZE = 0.75F;
            GL11.glTranslatef(0.0F, -0.5F, 0.0F);
            GL11.glScalef(0.75F, 0.75F, 0.75F);
      }

      MCH_ModelManager.render("blocks", "drafting_table");
      GL11.glPopMatrix();
      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      GL11.glEnable(3042);
   }
}
