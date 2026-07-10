package mcheli.wrapper;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

public abstract class W_GuiContainer extends GuiContainer {
   public W_GuiContainer(Container par1Container) {
      super(par1Container);
   }

   public void drawItemStack(ItemStack item, int x, int y) {
      if (item != null) {
         if (item.getItem() != null) {
            FontRenderer font = item.getItem().getFontRenderer(item);
            if (font == null) {
               font = this.fontRendererObj;
            }

            GL11.glEnable(2929);
            GL11.glEnable(2896);
            itemRender.renderItemAndEffectIntoGUI(font, this.mc.getTextureManager(), item, x, y);
            itemRender.renderItemOverlayIntoGUI(font, this.mc.getTextureManager(), item, x, y, null);
            this.zLevel = 0.0F;
            itemRender.zLevel = 0.0F;
         }
      }
   }

   public void drawString(String s, int x, int y, int color) {
      this.drawString(this.fontRendererObj, s, x, y, color);
   }

   public void drawCenteredString(String s, int x, int y, int color) {
      this.drawCenteredString(this.fontRendererObj, s, x, y, color);
   }

   public int getStringWidth(String s) {
      return this.fontRendererObj.getStringWidth(s);
   }
}
