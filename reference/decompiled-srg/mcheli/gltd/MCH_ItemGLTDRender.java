/*
 * Decompiled with CFR 0.152.
 */
package mcheli.gltd;

import mcheli.gltd.MCH_ItemGLTDRender;
import mcheli.gltd.MCH_RenderGLTD;
import mcheli.wrapper.W_McClient;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import org.lwjgl.opengl.GL11;

public class MCH_ItemGLTDRender
implements IItemRenderer {
    public boolean handleRenderType(ItemStack item, IItemRenderer.ItemRenderType type) {
        return type == IItemRenderer.ItemRenderType.EQUIPPED || type == IItemRenderer.ItemRenderType.EQUIPPED_FIRST_PERSON || type == IItemRenderer.ItemRenderType.ENTITY;
    }

    public boolean shouldUseRenderHelper(IItemRenderer.ItemRenderType type, ItemStack item, IItemRenderer.ItemRendererHelper helper) {
        return type == IItemRenderer.ItemRenderType.ENTITY;
    }

    public void renderItem(IItemRenderer.ItemRenderType type, ItemStack item, Object ... data) {
        GL11.glPushMatrix();
        GL11.glEnable((int)2884);
        W_McClient.MOD_bindTexture((String)"textures/gltd.png");
        switch (1.$SwitchMap$net$minecraftforge$client$IItemRenderer$ItemRenderType[type.ordinal()]) {
            case 1: {
                GL11.glEnable((int)32826);
                GL11.glEnable((int)2903);
                GL11.glScalef((float)1.0f, (float)1.0f, (float)1.0f);
                MCH_RenderGLTD.model.renderAll();
                GL11.glDisable((int)32826);
                break;
            }
            case 2: {
                GL11.glEnable((int)32826);
                GL11.glEnable((int)2903);
                GL11.glTranslatef((float)0.0f, (float)0.005f, (float)-0.165f);
                GL11.glRotatef((float)-10.0f, (float)0.0f, (float)0.0f, (float)1.0f);
                GL11.glRotatef((float)-10.0f, (float)1.0f, (float)0.0f, (float)0.0f);
                MCH_RenderGLTD.model.renderAll();
                GL11.glDisable((int)32826);
                break;
            }
            case 3: {
                GL11.glEnable((int)32826);
                GL11.glEnable((int)2903);
                GL11.glTranslatef((float)0.3f, (float)0.5f, (float)-0.5f);
                GL11.glScalef((float)0.5f, (float)0.5f, (float)0.5f);
                GL11.glRotatef((float)10.0f, (float)0.0f, (float)0.0f, (float)1.0f);
                GL11.glRotatef((float)50.0f, (float)0.0f, (float)1.0f, (float)0.0f);
                GL11.glRotatef((float)-10.0f, (float)1.0f, (float)0.0f, (float)0.0f);
                MCH_RenderGLTD.model.renderAll();
                GL11.glDisable((int)32826);
                break;
            }
        }
        GL11.glPopMatrix();
    }
}

