/*
 * Decompiled with CFR 0.152.
 */
package mcheli.wrapper;

import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import mcheli.MCH_Config;
import mcheli.MCH_Lib;
import mcheli.wrapper.W_Reflection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

/*
 * Exception performing whole class analysis ignored.
 */
public class W_EntityRenderer {
    public static void setItemRenderer(Minecraft mc, ItemRenderer ir) {
        W_Reflection.setItemRenderer((ItemRenderer)ir);
    }

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static boolean isShaderSupport() {
        if (!OpenGlHelper.field_148824_g) return false;
        if (MCH_Config.DisableShader.prmBool) return false;
        return true;
    }

    public static void activateShader(String n) {
        W_EntityRenderer.activateShader((ResourceLocation)new ResourceLocation("mcheli", "shaders/post/" + n + ".json"));
    }

    public static void activateShader(ResourceLocation r) {
        Minecraft mc = Minecraft.func_71410_x();
        try {
            mc.field_71460_t.field_147707_d = new ShaderGroup(mc.func_110434_K(), mc.func_110442_L(), mc.func_147110_a(), r);
            mc.field_71460_t.field_147707_d.func_148026_a(mc.field_71443_c, mc.field_71440_d);
        }
        catch (IOException ioexception) {
            ioexception.printStackTrace();
        }
        catch (JsonSyntaxException jsonsyntaxexception) {
            MCH_Lib.Log((String)("Failed to load shader: " + r), (Object[])new Object[0]);
            jsonsyntaxexception.printStackTrace();
        }
    }

    public static void deactivateShader() {
        Minecraft.func_71410_x().field_71460_t.func_147703_b();
    }

    public static void renderEntityWithPosYaw(RenderManager rm, Entity par1Entity, double par2, double par4, double par6, float par8, float par9, boolean b) {
        rm.func_147939_a(par1Entity, par2, par4, par6, par8, par9, b);
    }
}

