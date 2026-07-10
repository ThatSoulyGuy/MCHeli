/*
 * Decompiled with CFR 0.152.
 */
package mcheli.weapon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.weapon.MCH_EntityCartridge;
import mcheli.wrapper.W_Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

@SideOnly(value=Side.CLIENT)
public class MCH_RenderCartridge
extends W_Render {
    public MCH_RenderCartridge() {
        this.field_76989_e = 0.0f;
    }

    public void func_76986_a(Entity entity, double posX, double posY, double posZ, float par8, float tickTime) {
        MCH_EntityCartridge cartridge = null;
        cartridge = (MCH_EntityCartridge)entity;
        if (cartridge.model != null && !cartridge.texture_name.isEmpty()) {
            GL11.glPushMatrix();
            GL11.glTranslated((double)posX, (double)posY, (double)posZ);
            GL11.glScalef((float)cartridge.getScale(), (float)cartridge.getScale(), (float)cartridge.getScale());
            float prevYaw = cartridge.field_70126_B;
            if (cartridge.field_70177_z - prevYaw < -180.0f) {
                prevYaw -= 360.0f;
            } else if (prevYaw - cartridge.field_70177_z < -180.0f) {
                prevYaw += 360.0f;
            }
            float yaw = -(prevYaw + (cartridge.field_70177_z - prevYaw) * tickTime);
            float pitch = cartridge.field_70127_C + (cartridge.field_70125_A - cartridge.field_70127_C) * tickTime;
            GL11.glRotatef((float)yaw, (float)0.0f, (float)1.0f, (float)0.0f);
            GL11.glRotatef((float)pitch, (float)1.0f, (float)0.0f, (float)0.0f);
            this.bindTexture("textures/bullets/" + cartridge.texture_name + ".png");
            cartridge.model.renderAll();
            GL11.glPopMatrix();
        }
    }

    protected ResourceLocation func_110775_a(Entity entity) {
        return TEX_DEFAULT;
    }
}

