/*
 * Decompiled with CFR 0.152.
 */
package mcheli.wrapper;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

public abstract class W_GuiContainer
extends GuiContainer {
    public W_GuiContainer(Container par1Container) {
        super(par1Container);
    }

    public void drawItemStack(ItemStack item, int x, int y) {
        if (item == null) {
            return;
        }
        if (item.func_77973_b() == null) {
            return;
        }
        FontRenderer font = item.func_77973_b().getFontRenderer(item);
        if (font == null) {
            font = this.field_146289_q;
        }
        GL11.glEnable((int)2929);
        GL11.glEnable((int)2896);
        field_146296_j.func_82406_b(font, this.field_146297_k.func_110434_K(), item, x, y);
        field_146296_j.func_94148_a(font, this.field_146297_k.func_110434_K(), item, x, y, null);
        this.field_73735_i = 0.0f;
        W_GuiContainer.field_146296_j.field_77023_b = 0.0f;
    }

    public void drawString(String s, int x, int y, int color) {
        this.func_73731_b(this.field_146289_q, s, x, y, color);
    }

    public void drawCenteredString(String s, int x, int y, int color) {
        this.func_73732_a(this.field_146289_q, s, x, y, color);
    }

    public int getStringWidth(String s) {
        return this.field_146289_q.func_78256_a(s);
    }
}

