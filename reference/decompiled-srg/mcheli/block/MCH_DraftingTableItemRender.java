/*
 * Decompiled with CFR 0.152.
 */
package mcheli.block;

import mcheli.MCH_ModelManager;
import mcheli.block.MCH_DraftingTableItemRender;
import mcheli.wrapper.W_McClient;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import org.lwjgl.opengl.GL11;

public class MCH_DraftingTableItemRender
implements IItemRenderer {
    public boolean handleRenderType(ItemStack item, IItemRenderer.ItemRenderType type) {
        switch (1.$SwitchMap$net$minecraftforge$client$IItemRenderer$ItemRenderType[type.ordinal()]) {
            case 1: 
            case 2: 
            case 3: 
            case 4: {
                return true;
            }
        }
        return false;
    }

    public boolean shouldUseRenderHelper(IItemRenderer.ItemRenderType type, ItemStack item, IItemRenderer.ItemRendererHelper helper) {
        return true;
    }

    public void renderItem(IItemRenderer.ItemRenderType type, ItemStack item, Object ... data) {
        GL11.glPushMatrix();
        W_McClient.MOD_bindTexture((String)"textures/blocks/drafting_table.png");
        GL11.glEnable((int)32826);
        switch (1.$SwitchMap$net$minecraftforge$client$IItemRenderer$ItemRenderType[type.ordinal()]) {
            case 1: {
                GL11.glTranslatef((float)0.0f, (float)0.5f, (float)0.0f);
                GL11.glScalef((float)1.5f, (float)1.5f, (float)1.5f);
                break;
            }
            case 4: {
                float INV_SIZE = 0.75f;
                GL11.glTranslatef((float)0.0f, (float)-0.5f, (float)0.0f);
                GL11.glScalef((float)0.75f, (float)0.75f, (float)0.75f);
                break;
            }
            case 2: {
                GL11.glTranslatef((float)0.0f, (float)0.0f, (float)0.5f);
                GL11.glScalef((float)1.0f, (float)1.0f, (float)1.0f);
                break;
            }
            case 3: {
                GL11.glTranslatef((float)0.75f, (float)0.0f, (float)0.0f);
                GL11.glScalef((float)1.0f, (float)1.0f, (float)1.0f);
                GL11.glRotatef((float)90.0f, (float)0.0f, (float)-1.0f, (float)0.0f);
                break;
            }
        }
        MCH_ModelManager.render((String)"blocks", (String)"drafting_table");
        GL11.glPopMatrix();
        GL11.glColor4f((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        GL11.glEnable((int)3042);
    }
}

