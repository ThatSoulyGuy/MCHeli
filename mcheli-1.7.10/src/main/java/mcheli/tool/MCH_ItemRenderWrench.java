package mcheli.tool;

import mcheli.MCH_ModelManager;
import mcheli.wrapper.W_McClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import net.minecraftforge.client.IItemRenderer.ItemRendererHelper;
import org.lwjgl.opengl.GL11;

public class MCH_ItemRenderWrench implements IItemRenderer {
   public boolean handleRenderType(ItemStack item, ItemRenderType type) {
      return type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON;
   }

   public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
      return type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON;
   }

   public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
      GL11.glPushMatrix();
      W_McClient.MOD_bindTexture("textures/wrench.png");
      float size = 1.0F;
      switch (type) {
         case EQUIPPED:
            size = 2.2F;
            GL11.glScalef(size, size, size);
            GL11.glRotatef(-130.0F, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(-40.0F, 1.0F, 0.0F, 0.0F);
            GL11.glTranslatef(0.1F, 0.5F, -0.1F);
            break;
         case EQUIPPED_FIRST_PERSON:
            int useFrame = MCH_ItemWrench.getUseAnimCount(item) - 8;
            if (useFrame < 0) {
               useFrame = -useFrame;
            }

            size = 2.2F;
            if (data.length >= 2 && data[1] instanceof EntityPlayer) {
               EntityPlayer player = (EntityPlayer)data[1];
               if (player.getItemInUseCount() > 0) {
                  float x = 0.8567F;
                  float y = -0.0298F;
                  float z = 0.0F;
                  GL11.glTranslatef(-x, -y, -z);
                  GL11.glRotatef(useFrame + 20, 1.0F, 0.0F, 0.0F);
                  GL11.glTranslatef(x, y, z);
               }
            }

            GL11.glScalef(size, size, size);
            GL11.glRotatef(-200.0F, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(-60.0F, 1.0F, 0.0F, 0.0F);
            GL11.glRotatef(0.0F, 0.0F, 0.0F, 1.0F);
            GL11.glTranslatef(-0.2F, 0.5F, -0.1F);
      }

      MCH_ModelManager.render("wrench");
      GL11.glPopMatrix();
   }
}
