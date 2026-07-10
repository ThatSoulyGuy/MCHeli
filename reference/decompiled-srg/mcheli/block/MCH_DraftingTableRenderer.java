/*
 * Decompiled with CFR 0.152.
 */
package mcheli.block;

import mcheli.MCH_Config;
import mcheli.MCH_ModelManager;
import mcheli.wrapper.W_McClient;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.opengl.GL11;

public class MCH_DraftingTableRenderer
extends TileEntitySpecialRenderer {
    public void func_147500_a(TileEntity tile, double posX, double posY, double posZ, float var8) {
        GL11.glPushMatrix();
        GL11.glEnable((int)2884);
        GL11.glTranslated((double)(posX + 0.5), (double)posY, (double)(posZ + 0.5));
        float yaw = -tile.func_145832_p() * 45 + 180;
        GL11.glRotatef((float)yaw, (float)0.0f, (float)1.0f, (float)0.0f);
        RenderHelper.func_74519_b();
        GL11.glColor4f((float)0.75f, (float)0.75f, (float)0.75f, (float)1.0f);
        GL11.glEnable((int)3042);
        int srcBlend = GL11.glGetInteger((int)3041);
        int dstBlend = GL11.glGetInteger((int)3040);
        GL11.glBlendFunc((int)770, (int)771);
        if (MCH_Config.SmoothShading.prmBool) {
            GL11.glShadeModel((int)7425);
        }
        W_McClient.MOD_bindTexture((String)"textures/blocks/drafting_table.png");
        MCH_ModelManager.render((String)"blocks", (String)"drafting_table");
        GL11.glBlendFunc((int)srcBlend, (int)dstBlend);
        GL11.glDisable((int)3042);
        GL11.glShadeModel((int)7424);
        GL11.glPopMatrix();
    }
}

