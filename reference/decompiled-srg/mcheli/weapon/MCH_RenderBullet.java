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
public class MCH_RenderBullet
extends MCH_RenderBulletBase {
    public void renderBullet(Entity entity, double posX, double posY, double posZ, float yaw, float tickTime) {
        MCH_EntityBaseBullet blt = (MCH_EntityBaseBullet)entity;
        GL11.glPushMatrix();
        double x = entity.field_70169_q + entity.field_70159_w * (double)tickTime;
        double y = entity.field_70167_r + entity.field_70181_x * (double)tickTime;
        double z = entity.field_70166_s + entity.field_70179_y * (double)tickTime;
        GL11.glTranslated((double)posX, (double)posY, (double)posZ);
        GL11.glRotatef((float)(-entity.field_70177_z), (float)0.0f, (float)1.0f, (float)0.0f);
        GL11.glRotatef((float)entity.field_70125_A, (float)1.0f, (float)0.0f, (float)0.0f);
        this.renderModel(blt);
        GL11.glPopMatrix();
    }

    protected ResourceLocation func_110775_a(Entity entity) {
        return TEX_DEFAULT;
    }
}

