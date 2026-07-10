/*
 * Decompiled with CFR 0.152.
 */
package mcheli.aircraft;

import mcheli.MCH_ModelManager;
import mcheli.aircraft.MCH_AircraftInfo;
import mcheli.aircraft.MCH_ItemAircraft;
import mcheli.aircraft.MCH_ItemAircraftRender;
import mcheli.wrapper.W_McClient;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import org.lwjgl.opengl.GL11;

public class MCH_ItemAircraftRender
implements IItemRenderer {
    float size = 0.1f;
    float x = 0.1f;
    float y = 0.1f;
    float z = 0.1f;

    public boolean handleRenderType(ItemStack item, IItemRenderer.ItemRenderType type) {
        if (item != null && item.func_77973_b() instanceof MCH_ItemAircraft) {
            MCH_AircraftInfo info = ((MCH_ItemAircraft)item.func_77973_b()).getAircraftInfo();
            if (info == null) {
                return false;
            }
            if (info != null && info.name.equalsIgnoreCase("mh-60l_dap")) {
                return type == IItemRenderer.ItemRenderType.EQUIPPED || type == IItemRenderer.ItemRenderType.EQUIPPED_FIRST_PERSON || type == IItemRenderer.ItemRenderType.ENTITY || type == IItemRenderer.ItemRenderType.INVENTORY;
            }
        }
        return false;
    }

    public boolean shouldUseRenderHelper(IItemRenderer.ItemRenderType type, ItemStack item, IItemRenderer.ItemRendererHelper helper) {
        return type == IItemRenderer.ItemRenderType.ENTITY || type == IItemRenderer.ItemRenderType.INVENTORY;
    }

    public void renderItem(IItemRenderer.ItemRenderType type, ItemStack item, Object ... data) {
        boolean isRender = true;
        GL11.glPushMatrix();
        GL11.glEnable((int)2884);
        W_McClient.MOD_bindTexture((String)"textures/helicopters/mh-60l_dap.png");
        switch (1.$SwitchMap$net$minecraftforge$client$IItemRenderer$ItemRenderType[type.ordinal()]) {
            case 1: {
                GL11.glEnable((int)32826);
                GL11.glEnable((int)2903);
                GL11.glScalef((float)0.1f, (float)0.1f, (float)0.1f);
                MCH_ModelManager.render((String)"helicopters", (String)"mh-60l_dap");
                GL11.glDisable((int)32826);
                break;
            }
            case 2: {
                GL11.glEnable((int)32826);
                GL11.glEnable((int)2903);
                GL11.glTranslatef((float)0.0f, (float)0.005f, (float)-0.165f);
                GL11.glScalef((float)0.1f, (float)0.1f, (float)0.1f);
                GL11.glRotatef((float)-10.0f, (float)0.0f, (float)0.0f, (float)1.0f);
                GL11.glRotatef((float)90.0f, (float)0.0f, (float)-1.0f, (float)0.0f);
                GL11.glRotatef((float)-50.0f, (float)1.0f, (float)0.0f, (float)0.0f);
                MCH_ModelManager.render((String)"helicopters", (String)"mh-60l_dap");
                GL11.glDisable((int)32826);
                break;
            }
            case 3: {
                GL11.glEnable((int)32826);
                GL11.glEnable((int)2903);
                GL11.glTranslatef((float)0.3f, (float)0.5f, (float)-0.5f);
                GL11.glScalef((float)0.1f, (float)0.1f, (float)0.1f);
                GL11.glRotatef((float)10.0f, (float)0.0f, (float)0.0f, (float)1.0f);
                GL11.glRotatef((float)140.0f, (float)0.0f, (float)1.0f, (float)0.0f);
                GL11.glRotatef((float)-10.0f, (float)1.0f, (float)0.0f, (float)0.0f);
                MCH_ModelManager.render((String)"helicopters", (String)"mh-60l_dap");
                GL11.glDisable((int)32826);
                break;
            }
            case 4: {
                GL11.glTranslatef((float)this.x, (float)this.y, (float)this.z);
                GL11.glScalef((float)this.size, (float)this.size, (float)this.size);
                MCH_ModelManager.render((String)"helicopters", (String)"mh-60l_dap");
                break;
            }
            default: {
                isRender = false;
            }
        }
        GL11.glPopMatrix();
    }
}

