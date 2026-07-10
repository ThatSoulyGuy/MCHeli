/*
 * Decompiled with CFR 0.152.
 */
package mcheli.tool.rangefinder;

import mcheli.MCH_ModelManager;
import mcheli.tool.rangefinder.MCH_ItemRangeFinder;
import mcheli.tool.rangefinder.MCH_ItemRenderRangeFinder;
import mcheli.wrapper.W_McClient;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import org.lwjgl.opengl.GL11;

public class MCH_ItemRenderRangeFinder
implements IItemRenderer {
    public boolean handleRenderType(ItemStack item, IItemRenderer.ItemRenderType type) {
        return type == IItemRenderer.ItemRenderType.EQUIPPED || type == IItemRenderer.ItemRenderType.EQUIPPED_FIRST_PERSON || type == IItemRenderer.ItemRenderType.ENTITY;
    }

    public boolean shouldUseRenderHelper(IItemRenderer.ItemRenderType type, ItemStack item, IItemRenderer.ItemRendererHelper helper) {
        return type == IItemRenderer.ItemRenderType.EQUIPPED || type == IItemRenderer.ItemRenderType.EQUIPPED_FIRST_PERSON || type == IItemRenderer.ItemRenderType.ENTITY;
    }

    public void renderItem(IItemRenderer.ItemRenderType type, ItemStack item, Object ... data) {
        GL11.glPushMatrix();
        W_McClient.MOD_bindTexture((String)"textures/rangefinder.png");
        float size = 1.0f;
        switch (1.$SwitchMap$net$minecraftforge$client$IItemRenderer$ItemRenderType[type.ordinal()]) {
            case 1: {
                size = 2.2f;
                GL11.glScalef((float)size, (float)size, (float)size);
                GL11.glRotatef((float)-130.0f, (float)0.0f, (float)1.0f, (float)0.0f);
                GL11.glRotatef((float)70.0f, (float)1.0f, (float)0.0f, (float)0.0f);
                GL11.glRotatef((float)5.0f, (float)0.0f, (float)0.0f, (float)1.0f);
                GL11.glTranslatef((float)0.0f, (float)0.0f, (float)-0.0f);
                MCH_ModelManager.render((String)"rangefinder");
                break;
            }
            case 2: {
                size = 2.2f;
                GL11.glScalef((float)size, (float)size, (float)size);
                GL11.glRotatef((float)-130.0f, (float)0.0f, (float)1.0f, (float)0.0f);
                GL11.glRotatef((float)70.0f, (float)1.0f, (float)0.0f, (float)0.0f);
                GL11.glRotatef((float)5.0f, (float)0.0f, (float)0.0f, (float)1.0f);
                if (Minecraft.func_71410_x().field_71439_g.func_71057_bx() > 0) {
                    GL11.glTranslatef((float)0.4f, (float)-0.35f, (float)-0.3f);
                } else {
                    GL11.glTranslatef((float)0.2f, (float)-0.35f, (float)-0.3f);
                }
                MCH_ModelManager.render((String)"rangefinder");
                break;
            }
            case 3: {
                if (MCH_ItemRangeFinder.isUsingScope((EntityPlayer)Minecraft.func_71410_x().field_71439_g)) break;
                size = 2.2f;
                GL11.glScalef((float)size, (float)size, (float)size);
                GL11.glRotatef((float)-210.0f, (float)0.0f, (float)1.0f, (float)0.0f);
                GL11.glRotatef((float)-10.0f, (float)1.0f, (float)0.0f, (float)0.0f);
                GL11.glRotatef((float)-10.0f, (float)0.0f, (float)0.0f, (float)1.0f);
                GL11.glTranslatef((float)0.06f, (float)0.53f, (float)-0.1f);
                MCH_ModelManager.render((String)"rangefinder");
                break;
            }
        }
        GL11.glPopMatrix();
    }
}

