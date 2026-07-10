/*
 * Decompiled with CFR 0.152.
 */
package mcheli.particles;

import mcheli.particles.MCH_EntityParticleBase;
import mcheli.wrapper.W_Block;
import mcheli.wrapper.W_McClient;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.World;

public class MCH_EntityParticleSplash
extends MCH_EntityParticleBase {
    public MCH_EntityParticleSplash(World par1World, double x, double y, double z, double mx, double my, double mz) {
        super(par1World, x, y, z, mx, my, mz);
        this.field_70553_i = this.field_70551_j = this.field_70146_Z.nextFloat() * 0.3f + 0.7f;
        this.field_70552_h = this.field_70551_j;
        this.setParticleScale(this.field_70146_Z.nextFloat() * 0.5f + 5.0f);
        this.setParticleMaxAge((int)(80.0 / ((double)this.field_70146_Z.nextFloat() * 0.8 + 0.2)) + 2);
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
        }
        this.field_70181_x -= (double)0.06f;
        Block block = W_WorldFunc.getBlock((World)this.field_70170_p, (int)((int)(this.field_70165_t + 0.5)), (int)((int)(this.field_70163_u + 0.5)), (int)((int)(this.field_70161_v + 0.5)));
        boolean beforeInWater = W_Block.func_149680_a((Block)block, (Block)W_Block.getWater());
        this.func_70091_d(this.field_70159_w, this.field_70181_x, this.field_70179_y);
        block = W_WorldFunc.getBlock((World)this.field_70170_p, (int)((int)(this.field_70165_t + 0.5)), (int)((int)(this.field_70163_u + 0.5)), (int)((int)(this.field_70161_v + 0.5)));
        boolean nowInWater = W_Block.func_149680_a((Block)block, (Block)W_Block.getWater());
        if (this.field_70181_x < -0.6 && !beforeInWater && nowInWater) {
            double p = -this.field_70181_x * 10.0;
            int i = 0;
            while ((double)i < p) {
                this.field_70170_p.func_72869_a("splash", this.field_70165_t + 0.5 + (this.field_70146_Z.nextDouble() - 0.5) * 2.0, this.field_70163_u + this.field_70146_Z.nextDouble(), this.field_70161_v + 0.5 + (this.field_70146_Z.nextDouble() - 0.5) * 2.0, (this.field_70146_Z.nextDouble() - 0.5) * 2.0, 4.0, (this.field_70146_Z.nextDouble() - 0.5) * 2.0);
                this.field_70170_p.func_72869_a("bubble", this.field_70165_t + 0.5 + (this.field_70146_Z.nextDouble() - 0.5) * 2.0, this.field_70163_u - this.field_70146_Z.nextDouble(), this.field_70161_v + 0.5 + (this.field_70146_Z.nextDouble() - 0.5) * 2.0, (this.field_70146_Z.nextDouble() - 0.5) * 2.0, -0.5, (this.field_70146_Z.nextDouble() - 0.5) * 2.0);
                ++i;
            }
        } else if (this.field_70122_E) {
            this.func_70106_y();
        }
        this.field_70159_w *= 0.9;
        this.field_70179_y *= 0.9;
    }

    public void func_70539_a(Tessellator par1Tessellator, float par2, float par3, float par4, float par5, float par6, float par7) {
        W_McClient.MOD_bindTexture((String)"textures/particles/smoke.png");
        float f6 = (float)this.field_94054_b / 8.0f;
        float f7 = f6 + 0.125f;
        float f8 = 0.0f;
        float f9 = 1.0f;
        float f10 = 0.1f * this.field_70544_f;
        float f11 = (float)(this.field_70169_q + (this.field_70165_t - this.field_70169_q) * (double)par2 - field_70556_an);
        float f12 = (float)(this.field_70167_r + (this.field_70163_u - this.field_70167_r) * (double)par2 - field_70554_ao);
        float f13 = (float)(this.field_70166_s + (this.field_70161_v - this.field_70166_s) * (double)par2 - field_70555_ap);
        float f14 = 1.0f;
        par1Tessellator.func_78369_a(this.field_70552_h * f14, this.field_70553_i * f14, this.field_70551_j * f14, this.field_82339_as);
        par1Tessellator.func_78374_a((double)(f11 - par3 * f10 - par6 * f10), (double)(f12 - par4 * f10), (double)(f13 - par5 * f10 - par7 * f10), (double)f7, (double)f9);
        par1Tessellator.func_78374_a((double)(f11 - par3 * f10 + par6 * f10), (double)(f12 + par4 * f10), (double)(f13 - par5 * f10 + par7 * f10), (double)f7, (double)f8);
        par1Tessellator.func_78374_a((double)(f11 + par3 * f10 + par6 * f10), (double)(f12 + par4 * f10), (double)(f13 + par5 * f10 + par7 * f10), (double)f6, (double)f8);
        par1Tessellator.func_78374_a((double)(f11 + par3 * f10 - par6 * f10), (double)(f12 - par4 * f10), (double)(f13 + par5 * f10 - par7 * f10), (double)f6, (double)f9);
    }
}

