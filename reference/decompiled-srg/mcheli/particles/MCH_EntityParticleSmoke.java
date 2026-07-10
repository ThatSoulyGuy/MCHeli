/*
 * Decompiled with CFR 0.152.
 */
package mcheli.particles;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.particles.MCH_EntityParticleBase;
import mcheli.wrapper.W_McClient;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

public class MCH_EntityParticleSmoke
extends MCH_EntityParticleBase {
    public MCH_EntityParticleSmoke(World par1World, double x, double y, double z, double mx, double my, double mz) {
        super(par1World, x, y, z, mx, my, mz);
        this.field_70553_i = this.field_70551_j = this.field_70146_Z.nextFloat() * 0.3f + 0.7f;
        this.field_70552_h = this.field_70551_j;
        this.setParticleScale(this.field_70146_Z.nextFloat() * 0.5f + 5.0f);
        this.setParticleMaxAge((int)(16.0 / ((double)this.field_70146_Z.nextFloat() * 0.8 + 0.2)) + 2);
    }

    public void func_70071_h_() {
        this.field_70169_q = this.field_70165_t;
        this.field_70167_r = this.field_70163_u;
        this.field_70166_s = this.field_70161_v;
        if (this.field_70546_d < this.field_70547_e) {
            this.func_70536_a((int)(8.0 * (double)this.field_70546_d / (double)this.field_70547_e));
            ++this.field_70546_d;
        } else {
            this.func_70106_y();
            return;
        }
        if (this.diffusible && this.field_70544_f < this.particleMaxScale) {
            this.field_70544_f += 0.8f;
        }
        if (this.toWhite) {
            float mn = this.getMinColor();
            float mx = this.getMaxColor();
            float dist = mx - mn;
            if ((double)dist > 0.2) {
                this.field_70552_h += (mx - this.field_70552_h) * 0.016f;
                this.field_70553_i += (mx - this.field_70553_i) * 0.016f;
                this.field_70551_j += (mx - this.field_70551_j) * 0.016f;
            }
        }
        this.effectWind();
        this.field_70181_x = (double)this.field_70546_d / (double)this.field_70547_e > (double)this.moutionYUpAge ? (this.field_70181_x += 0.02) : (this.field_70181_x += (double)this.gravity);
        this.func_70091_d(this.field_70159_w, this.field_70181_x, this.field_70179_y);
        if (this.diffusible) {
            this.field_70159_w *= 0.96;
            this.field_70179_y *= 0.96;
            this.field_70181_x *= 0.96;
        } else {
            this.field_70159_w *= 0.9;
            this.field_70179_y *= 0.9;
        }
    }

    public float getMinColor() {
        return this.min(this.min(this.field_70551_j, this.field_70553_i), this.field_70552_h);
    }

    public float getMaxColor() {
        return this.max(this.max(this.field_70551_j, this.field_70553_i), this.field_70552_h);
    }

    public float min(float a, float b) {
        return a < b ? a : b;
    }

    public float max(float a, float b) {
        return a > b ? a : b;
    }

    public void effectWind() {
        if (this.isEffectedWind) {
            int range = 15;
            List list = this.field_70170_p.func_72872_a(MCH_EntityAircraft.class, this.func_70046_E().func_72314_b(15.0, 15.0, 15.0));
            for (int i = 0; i < list.size(); ++i) {
                MCH_EntityAircraft ac = (MCH_EntityAircraft)list.get(i);
                if (!(ac.getThrottle() > (double)0.1f)) continue;
                float dist = this.func_70032_d((Entity)ac);
                double vel = (23.0 - (double)dist) * (double)0.01f * ac.getThrottle();
                double mx = ac.field_70165_t - this.field_70165_t;
                double mz = ac.field_70161_v - this.field_70161_v;
                this.field_70159_w -= mx * vel;
                this.field_70179_y -= mz * vel;
            }
        }
    }

    public int func_70537_b() {
        return 3;
    }

    @SideOnly(value=Side.CLIENT)
    public int func_70070_b(float p_70070_1_) {
        double y = this.field_70163_u;
        this.field_70163_u += 3000.0;
        int i = super.func_70070_b(p_70070_1_);
        this.field_70163_u = y;
        return i;
    }

    public void func_70539_a(Tessellator par1Tessellator, float par2, float par3, float par4, float par5, float par6, float par7) {
        W_McClient.MOD_bindTexture((String)"textures/particles/smoke.png");
        GL11.glEnable((int)3042);
        int srcBlend = GL11.glGetInteger((int)3041);
        int dstBlend = GL11.glGetInteger((int)3040);
        GL11.glBlendFunc((int)770, (int)771);
        GL11.glColor4f((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        GL11.glDisable((int)2896);
        GL11.glDisable((int)2884);
        float f6 = (float)this.field_94054_b / 8.0f;
        float f7 = f6 + 0.125f;
        float f8 = 0.0f;
        float f9 = 1.0f;
        float f10 = 0.1f * this.field_70544_f;
        float f11 = (float)(this.field_70169_q + (this.field_70165_t - this.field_70169_q) * (double)par2 - field_70556_an);
        float f12 = (float)(this.field_70167_r + (this.field_70163_u - this.field_70167_r) * (double)par2 - field_70554_ao);
        float f13 = (float)(this.field_70166_s + (this.field_70161_v - this.field_70166_s) * (double)par2 - field_70555_ap);
        par1Tessellator.func_78382_b();
        par1Tessellator.func_78369_a(this.field_70552_h, this.field_70553_i, this.field_70551_j, this.field_82339_as);
        par1Tessellator.func_78380_c(this.func_70070_b(par2));
        par1Tessellator.func_78375_b(0.0f, 1.0f, 0.0f);
        par1Tessellator.func_78374_a((double)(f11 - par3 * f10 - par6 * f10), (double)(f12 - par4 * f10), (double)(f13 - par5 * f10 - par7 * f10), (double)f7, (double)f9);
        par1Tessellator.func_78374_a((double)(f11 - par3 * f10 + par6 * f10), (double)(f12 + par4 * f10), (double)(f13 - par5 * f10 + par7 * f10), (double)f7, (double)f8);
        par1Tessellator.func_78374_a((double)(f11 + par3 * f10 + par6 * f10), (double)(f12 + par4 * f10), (double)(f13 + par5 * f10 + par7 * f10), (double)f6, (double)f8);
        par1Tessellator.func_78374_a((double)(f11 + par3 * f10 - par6 * f10), (double)(f12 - par4 * f10), (double)(f13 + par5 * f10 - par7 * f10), (double)f6, (double)f9);
        par1Tessellator.func_78381_a();
        GL11.glEnable((int)2884);
        GL11.glEnable((int)2896);
        GL11.glBlendFunc((int)srcBlend, (int)dstBlend);
        GL11.glDisable((int)3042);
    }
}

