/*
 * Decompiled with CFR 0.152.
 */
package mcheli;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.wrapper.W_Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

@SideOnly(value=Side.CLIENT)
public class MCH_RenderNull
extends W_Render {
    public MCH_RenderNull() {
        this.field_76989_e = 0.0f;
    }

    public void func_76986_a(Entity entity, double posX, double posY, double posZ, float par8, float tickTime) {
    }

    protected ResourceLocation func_110775_a(Entity entity) {
        return TEX_DEFAULT;
    }
}

