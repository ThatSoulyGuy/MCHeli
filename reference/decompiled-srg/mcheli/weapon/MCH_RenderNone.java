/*
 * Decompiled with CFR 0.152.
 */
package mcheli.weapon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.weapon.MCH_RenderBulletBase;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

@SideOnly(value=Side.CLIENT)
public class MCH_RenderNone
extends MCH_RenderBulletBase {
    public void renderBullet(Entity entity, double posX, double posY, double posZ, float yaw, float partialTickTime) {
    }

    protected ResourceLocation func_110775_a(Entity entity) {
        return TEX_DEFAULT;
    }
}

