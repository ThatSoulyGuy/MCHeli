/*
 * Decompiled with CFR 0.152.
 */
package mcheli.weapon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.weapon.MCH_EntityBaseBullet;
import mcheli.weapon.MCH_RenderBulletBase;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

@SideOnly(value=Side.CLIENT)
public class MCH_RenderASMissile
extends MCH_RenderBulletBase {
    public MCH_RenderASMissile() {
        this.field_76989_e = 0.5f;
    }

    public void renderBullet(Entity entity, double posX, double posY, double posZ, float yaw, float partialTickTime) {
        if (entity instanceof MCH_EntityBaseBullet) {
            MCH_EntityBaseBullet bullet = (MCH_EntityBaseBullet)entity;
            GL11.glPushMatrix();
            GL11.glTranslated((double)posX, (double)posY, (double)posZ);
            GL11.glRotatef((float)(-entity.field_70177_z), (float)0.0f, (float)1.0f, (float)0.0f);
            GL11.glRotatef((float)(-entity.field_70125_A), (float)-1.0f, (float)0.0f, (float)0.0f);
            this.renderModel(bullet);
            GL11.glPopMatrix();
        }
    }

    protected ResourceLocation func_110775_a(Entity entity) {
        return TEX_DEFAULT;
    }
}

