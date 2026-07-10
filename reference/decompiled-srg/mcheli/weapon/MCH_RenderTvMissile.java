/*
 * Decompiled with CFR 0.152.
 */
package mcheli.weapon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_EntitySeat;
import mcheli.uav.MCH_EntityUavStation;
import mcheli.weapon.MCH_EntityBaseBullet;
import mcheli.weapon.MCH_RenderBulletBase;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

@SideOnly(value=Side.CLIENT)
public class MCH_RenderTvMissile
extends MCH_RenderBulletBase {
    public MCH_RenderTvMissile() {
        this.field_76989_e = 0.5f;
    }

    public void renderBullet(Entity entity, double posX, double posY, double posZ, float par8, float par9) {
        MCH_EntityAircraft ac = null;
        Entity ridingEntity = Minecraft.func_71410_x().field_71439_g.field_70154_o;
        if (ridingEntity instanceof MCH_EntityAircraft) {
            ac = (MCH_EntityAircraft)ridingEntity;
        } else if (ridingEntity instanceof MCH_EntitySeat) {
            ac = ((MCH_EntitySeat)ridingEntity).getParent();
        } else if (ridingEntity instanceof MCH_EntityUavStation) {
            ac = ((MCH_EntityUavStation)ridingEntity).getControlAircract();
        }
        if (ac != null && !ac.isRenderBullet(entity, (Entity)Minecraft.func_71410_x().field_71439_g)) {
            return;
        }
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

