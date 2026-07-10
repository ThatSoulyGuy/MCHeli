/*
 * Decompiled with CFR 0.152.
 */
package mcheli.wrapper;

import mcheli.wrapper.W_MOD;
import mcheli.wrapper.W_Sound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

/*
 * Exception performing whole class analysis ignored.
 */
public class W_McClient {
    public static void DEF_playSoundFX(String name, float volume, float pitch) {
        Minecraft.func_71410_x().func_147118_V().func_147682_a((ISound)new W_Sound(new ResourceLocation(name), volume, pitch));
    }

    public static void MOD_playSoundFX(String name, float volume, float pitch) {
        W_McClient.DEF_playSoundFX((String)(W_MOD.DOMAIN + ":" + name), (float)volume, (float)pitch);
    }

    public static void addSound(String name) {
        Minecraft mc = Minecraft.func_71410_x();
    }

    public static void DEF_bindTexture(String tex) {
        Minecraft.func_71410_x().field_71446_o.func_110577_a(new ResourceLocation(tex));
    }

    public static void MOD_bindTexture(String tex) {
        Minecraft.func_71410_x().field_71446_o.func_110577_a(new ResourceLocation(W_MOD.DOMAIN, tex));
    }

    public static boolean isGamePaused() {
        Minecraft mc = Minecraft.func_71410_x();
        return mc.func_147113_T();
    }

    public static Entity getRenderEntity() {
        return Minecraft.func_71410_x().field_71451_h;
    }

    public static void setRenderEntity(EntityLivingBase entity) {
        Minecraft.func_71410_x().field_71451_h = entity;
    }
}

