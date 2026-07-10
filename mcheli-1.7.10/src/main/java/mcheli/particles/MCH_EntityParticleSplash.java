package mcheli.particles;

import mcheli.wrapper.W_Block;
import mcheli.wrapper.W_McClient;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.World;

public class MCH_EntityParticleSplash extends MCH_EntityParticleBase {
   public MCH_EntityParticleSplash(World par1World, double x, double y, double z, double mx, double my, double mz) {
      super(par1World, x, y, z, mx, my, mz);
      this.particleRed = this.particleGreen = this.particleBlue = this.rand.nextFloat() * 0.3F + 0.7F;
      this.setParticleScale(this.rand.nextFloat() * 0.5F + 5.0F);
      this.setParticleMaxAge((int)(80.0 / (this.rand.nextFloat() * 0.8 + 0.2)) + 2);
   }

   public void onUpdate() {
      this.prevPosX = this.posX;
      this.prevPosY = this.posY;
      this.prevPosZ = this.posZ;
      if (this.particleAge < this.particleMaxAge) {
         this.setParticleTextureIndex((int)(8.0 * this.particleAge / this.particleMaxAge));
         this.particleAge++;
      } else {
         this.setDead();
      }

      this.motionY -= 0.06F;
      Block block = W_WorldFunc.getBlock(this.worldObj, (int)(this.posX + 0.5), (int)(this.posY + 0.5), (int)(this.posZ + 0.5));
      boolean beforeInWater = W_Block.isEqualTo(block, W_Block.getWater());
      this.moveEntity(this.motionX, this.motionY, this.motionZ);
      block = W_WorldFunc.getBlock(this.worldObj, (int)(this.posX + 0.5), (int)(this.posY + 0.5), (int)(this.posZ + 0.5));
      boolean nowInWater = W_Block.isEqualTo(block, W_Block.getWater());
      if (this.motionY < -0.6 && !beforeInWater && nowInWater) {
         double p = -this.motionY * 10.0;

         for (int i = 0; i < p; i++) {
            this.worldObj
               .spawnParticle(
                  "splash",
                  this.posX + 0.5 + (this.rand.nextDouble() - 0.5) * 2.0,
                  this.posY + this.rand.nextDouble(),
                  this.posZ + 0.5 + (this.rand.nextDouble() - 0.5) * 2.0,
                  (this.rand.nextDouble() - 0.5) * 2.0,
                  4.0,
                  (this.rand.nextDouble() - 0.5) * 2.0
               );
            this.worldObj
               .spawnParticle(
                  "bubble",
                  this.posX + 0.5 + (this.rand.nextDouble() - 0.5) * 2.0,
                  this.posY - this.rand.nextDouble(),
                  this.posZ + 0.5 + (this.rand.nextDouble() - 0.5) * 2.0,
                  (this.rand.nextDouble() - 0.5) * 2.0,
                  -0.5,
                  (this.rand.nextDouble() - 0.5) * 2.0
               );
         }
      } else if (this.onGround) {
         this.setDead();
      }

      this.motionX *= 0.9;
      this.motionZ *= 0.9;
   }

   public void renderParticle(Tessellator par1Tessellator, float par2, float par3, float par4, float par5, float par6, float par7) {
      W_McClient.MOD_bindTexture("textures/particles/smoke.png");
      float f6 = this.particleTextureIndexX / 8.0F;
      float f7 = f6 + 0.125F;
      float f8 = 0.0F;
      float f9 = 1.0F;
      float f10 = 0.1F * this.particleScale;
      float f11 = (float)(this.prevPosX + (this.posX - this.prevPosX) * par2 - interpPosX);
      float f12 = (float)(this.prevPosY + (this.posY - this.prevPosY) * par2 - interpPosY);
      float f13 = (float)(this.prevPosZ + (this.posZ - this.prevPosZ) * par2 - interpPosZ);
      float f14 = 1.0F;
      par1Tessellator.setColorRGBA_F(this.particleRed * f14, this.particleGreen * f14, this.particleBlue * f14, this.particleAlpha);
      par1Tessellator.addVertexWithUV(f11 - par3 * f10 - par6 * f10, f12 - par4 * f10, f13 - par5 * f10 - par7 * f10, f7, f9);
      par1Tessellator.addVertexWithUV(f11 - par3 * f10 + par6 * f10, f12 + par4 * f10, f13 - par5 * f10 + par7 * f10, f7, f8);
      par1Tessellator.addVertexWithUV(f11 + par3 * f10 + par6 * f10, f12 + par4 * f10, f13 + par5 * f10 + par7 * f10, f6, f8);
      par1Tessellator.addVertexWithUV(f11 + par3 * f10 - par6 * f10, f12 - par4 * f10, f13 + par5 * f10 - par7 * f10, f6, f9);
   }
}
