/*
 * Decompiled with CFR 0.152.
 */
package mcheli.tool;

import mcheli.MCH_ModelManager;
import mcheli.tool.MCH_ItemRenderWrench;
import mcheli.tool.MCH_ItemWrench;
import mcheli.wrapper.W_McClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import org.lwjgl.opengl.GL11;

public class MCH_ItemRenderWrench
implements IItemRenderer {
    public boolean handleRenderType(ItemStack item, IItemRenderer.ItemRenderType type) {
        return type == IItemRenderer.ItemRenderType.EQUIPPED || type == IItemRenderer.ItemRenderType.EQUIPPED_FIRST_PERSON;
    }

    public boolean shouldUseRenderHelper(IItemRenderer.ItemRenderType type, ItemStack item, IItemRenderer.ItemRendererHelper helper) {
        return type == IItemRenderer.ItemRenderType.EQUIPPED || type == IItemRenderer.ItemRenderType.EQUIPPED_FIRST_PERSON;
    }

    public void renderItem(IItemRenderer.ItemRenderType type, ItemStack item, Object ... data) {
        GL11.glPushMatrix();
        W_McClient.MOD_bindTexture((String)"textures/wrench.png");
        float size = 1.0f;
        switch (1.$SwitchMap$net$minecraftforge$client$IItemRenderer$ItemRenderType[type.ordinal()]) {
            case 1: {
                size = 2.2f;
                GL11.glScalef((float)size, (float)size, (float)size);
                GL11.glRotatef((float)-130.0f, (float)0.0f, (float)1.0f, (float)0.0f);
                GL11.glRotatef((float)-40.0f, (float)1.0f, (float)0.0f, (float)0.0f);
                GL11.glTranslatef((float)0.1f, (float)0.5f, (float)-0.1f);
                break;
            }
            case 2: {
                EntityPlayer player;
                int useFrame = MCH_ItemWrench.getUseAnimCount((ItemStack)item) - 8;
                if (useFrame < 0) {
                    useFrame = -useFrame;
                }
                size = 2.2f;
                if (data.length >= 2 && data[1] instanceof EntityPlayer && (player = (EntityPlayer)data[1]).func_71052_bv() > 0) {
                    float x = 0.8567f;
                    float y = -0.0298f;
                    float z = 0.0f;
                    GL11.glTranslatef((float)(-x), (float)(-y), (float)(-z));
                    GL11.glRotatef((float)(useFrame + 20), (float)1.0f, (float)0.0f, (float)0.0f);
                    GL11.glTranslatef((float)x, (float)y, (float)z);
                }
                GL11.glScalef((float)size, (float)size, (float)size);
                GL11.glRotatef((float)-200.0f, (float)0.0f, (float)1.0f, (float)0.0f);
                GL11.glRotatef((float)-60.0f, (float)1.0f, (float)0.0f, (float)0.0f);
                GL11.glRotatef((float)0.0f, (float)0.0f, (float)0.0f, (float)1.0f);
                GL11.glTranslatef((float)-0.2f, (float)0.5f, (float)-0.1f);
                break;
            }
        }
        MCH_ModelManager.render((String)"wrench");
        GL11.glPopMatrix();
    }
}

