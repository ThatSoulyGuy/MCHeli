/*
 * Decompiled with CFR 0.152.
 */
package mcheli.weapon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.MCH_Lib;
import mcheli.weapon.MCH_EntityAAMissile;
import mcheli.weapon.MCH_EntityBaseBullet;
import mcheli.weapon.MCH_RenderBulletBase;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

@SideOnly(value=Side.CLIENT)
public class MCH_RenderAAMissile
extends MCH_RenderBulletBase {
    public MCH_RenderAAMissile() {
        this.field_76989_e = 0.5f;
    }

    public void renderBullet(Entity entity, double posX, double posY, double posZ, float par8, float par9) {
        if (!(entity instanceof MCH_EntityAAMissile)) {
            return;
        }
        MCH_EntityAAMissile aam = (MCH_EntityAAMissile)entity;
        double mx = aam.prevMotionX + (aam.field_70159_w - aam.prevMotionX) * (double)par9;
        double my = aam.prevMotionY + (aam.field_70181_x - aam.prevMotionY) * (double)par9;
        double mz = aam.prevMotionZ + (aam.field_70179_y - aam.prevMotionZ) * (double)par9;
        GL11.glPushMatrix();
        GL11.glTranslated((double)posX, (double)posY, (double)posZ);
        Vec3 v = MCH_Lib.getYawPitchFromVec((double)mx, (double)my, (double)mz);
        GL11.glRotatef((float)((float)v.field_72448_b - 90.0f), (float)0.0f, (float)-1.0f, (float)0.0f);
        GL11.glRotatef((float)((float)v.field_72449_c), (float)-1.0f, (float)0.0f, (float)0.0f);
        this.renderModel((MCH_EntityBaseBullet)aam);
        GL11.glPopMatrix();
    }

    protected ResourceLocation func_110775_a(Entity entity) {
        return TEX_DEFAULT;
    }
}

