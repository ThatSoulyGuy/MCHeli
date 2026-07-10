/*
 * Decompiled with CFR 0.152.
 */
package mcheli.wrapper;

import mcheli.wrapper.W_TextureUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class W_TextureUtil {
    private static W_TextureUtil instance = new W_TextureUtil();

    private W_TextureUtil() {
    }

    private TextureParam newParam() {
        return new TextureParam(this);
    }

    public static TextureParam getTextureInfo(String domain, String name) {
        TextureManager textureManager = Minecraft.func_71410_x().func_110434_K();
        ResourceLocation r = new ResourceLocation(domain, name);
        textureManager.func_110577_a(r);
        TextureParam info = instance.newParam();
        info.width = GL11.glGetTexLevelParameteri((int)3553, (int)0, (int)4096);
        info.height = GL11.glGetTexLevelParameteri((int)3553, (int)0, (int)4097);
        return info;
    }
}

