/*
 * Decompiled with CFR 0.152.
 */
package mcheli.weapon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.weapon.MCH_EntityBaseBullet;
import mcheli.weapon.MCH_EntityBomb;
import mcheli.weapon.MCH_RenderBulletBase;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

@SideOnly(value=Side.CLIENT)
public class MCH_RenderBomb
extends MCH_RenderBulletBase {
    public MCH_RenderBomb() {
        this.field_76989_e = 0.5f;
    }

    public void renderBullet(Entity entity, double posX, double posY, double posZ, float yaw, float partialTickTime) {
        if (!(entity instanceof MCH_EntityBomb)) {
            return;
        }
        MCH_EntityBomb bomb = (MCH_EntityBomb)entity;
        if (bomb.getInfo() == null) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glTranslated((double)posX, (double)posY, (double)posZ);
        GL11.glRotatef((float)(-entity.field_70177_z), (float)0.0f, (float)1.0f, (float)0.0f);
        GL11.glRotatef((float)(-entity.field_70125_A), (float)-1.0f, (float)0.0f, (float)0.0f);
        if (bomb.isBomblet > 0 || bomb.getInfo().bomblet <= 0 || bomb.getInfo().bombletSTime > 0) {
            this.renderModel((MCH_EntityBaseBullet)bomb);
        }
        GL11.glPopMatrix();
    }

    protected ResourceLocation func_110775_a(Entity entity) {
        return TEX_DEFAULT;
    }
}

