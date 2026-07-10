package mcheli.particles;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.wrapper.W_McClient;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

public class MCH_EntityParticleSmoke extends MCH_EntityParticleBase {
   public MCH_EntityParticleSmoke(World par1World, double x, double y, double z, double mx, double my, double mz) {
      super(par1World, x, y, z, mx, my, mz);
      this.particleRed = this.particleGreen = this.particleBlue = this.rand.nextFloat() * 0.3F + 0.7F;
      this.setParticleScale(this.rand.nextFloat() * 0.5F + 5.0F);
      this.setParticleMaxAge((int)(16.0 / (this.rand.nextFloat() * 0.8 + 0.2)) + 2);
   }

   public void onUpdate() {
      this.prevPosX = this.posX;
      this.prevPosY = this.posY;
      this.prevPosZ = this.posZ;
      if (this.particleAge < this.particleMaxAge) {
         this.setParticleTextureIndex((int)(8.0 * this.particleAge / this.particleMaxAge));
         this.particleAge++;
         if (this.diffusible && this.particleScale < this.particleMaxScale) {
            this.particleScale += 0.8F;
         }

         if (this.toWhite) {
            float mn = this.getMinColor();
            float mx = this.getMaxColor();
            float dist = mx - mn;
            if (dist > 0.2) {
               this.particleRed = this.particleRed + (mx - this.particleRed) * 0.016F;
               this.particleGreen = this.particleGreen + (mx - this.particleGreen) * 0.016F;
               this.particleBlue = this.particleBlue + (mx - this.particleBlue) * 0.016F;
            }
         }

         this.effectWind();
         if ((double)this.particleAge / this.particleMaxAge > this.moutionYUpAge) {
            this.motionY += 0.02;
         } else {
            this.motionY = this.motionY + this.gravity;
         }

         this.moveEntity(this.motionX, this.motionY, this.motionZ);
         if (this.diffusible) {
            this.motionX *= 0.96;
            this.motionZ *= 0.96;
            this.motionY *= 0.96;
         } else {
            this.motionX *= 0.9;
            this.motionZ *= 0.9;
         }
      } else {
         this.setDead();
      }
   }

   public float getMinColor() {
      return this.min(this.min(this.particleBlue, this.particleGreen), this.particleRed);
   }

   public float getMaxColor() {
      return this.max(this.max(this.particleBlue, this.particleGreen), this.particleRed);
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
         List list = this.worldObj.getEntitiesWithinAABB(MCH_EntityAircraft.class, this.getBoundingBox().expand(15.0, 15.0, 15.0));

         for (int i = 0; i < list.size(); i++) {
            MCH_EntityAircraft ac = (MCH_EntityAircraft)list.get(i);
            if (ac.getThrottle() > 0.1F) {
               float dist = this.getDistanceToEntity(ac);
               double vel = (23.0 - dist) * 0.01F * ac.getThrottle();
               double mx = ac.posX - this.posX;
               double mz = ac.posZ - this.posZ;
               this.motionX -= mx * vel;
               this.motionZ -= mz * vel;
            }
         }
      }
   }

   @Override
   public int getFXLayer() {
      return 3;
   }

   @SideOnly(Side.CLIENT)
   public int getBrightnessForRender(float p_70070_1_) {
      double y = this.posY;
      this.posY += 3000.0;
      int i = super.getBrightnessForRender(p_70070_1_);
      this.posY = y;
      return i;
   }

   public void renderParticle(Tessellator par1Tessellator, float par2, float par3, float par4, float par5, float par6, float par7) {
      W_McClient.MOD_bindTexture("textures/particles/smoke.png");
      GL11.glEnable(3042);
      int srcBlend = GL11.glGetInteger(3041);
      int dstBlend = GL11.glGetInteger(3040);
      GL11.glBlendFunc(770, 771);
      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      GL11.glDisable(2896);
      GL11.glDisable(2884);
      float f6 = this.particleTextureIndexX / 8.0F;
      float f7 = f6 + 0.125F;
      float f8 = 0.0F;
      float f9 = 1.0F;
      float f10 = 0.1F * this.particleScale;
      float f11 = (float)(this.prevPosX + (this.posX - this.prevPosX) * par2 - interpPosX);
      float f12 = (float)(this.prevPosY + (this.posY - this.prevPosY) * par2 - interpPosY);
      float f13 = (float)(this.prevPosZ + (this.posZ - this.prevPosZ) * par2 - interpPosZ);
      par1Tessellator.startDrawingQuads();
      par1Tessellator.setColorRGBA_F(this.particleRed, this.particleGreen, this.particleBlue, this.particleAlpha);
      par1Tessellator.setBrightness(this.getBrightnessForRender(par2));
      par1Tessellator.setNormal(0.0F, 1.0F, 0.0F);
      par1Tessellator.addVertexWithUV(f11 - par3 * f10 - par6 * f10, f12 - par4 * f10, f13 - par5 * f10 - par7 * f10, f7, f9);
      par1Tessellator.addVertexWithUV(f11 - par3 * f10 + par6 * f10, f12 + par4 * f10, f13 - par5 * f10 + par7 * f10, f7, f8);
      par1Tessellator.addVertexWithUV(f11 + par3 * f10 + par6 * f10, f12 + par4 * f10, f13 + par5 * f10 + par7 * f10, f6, f8);
      par1Tessellator.addVertexWithUV(f11 + par3 * f10 - par6 * f10, f12 - par4 * f10, f13 + par5 * f10 - par7 * f10, f6, f9);
      par1Tessellator.draw();
      GL11.glEnable(2884);
      GL11.glEnable(2896);
      GL11.glBlendFunc(srcBlend, dstBlend);
      GL11.glDisable(3042);
   }
}
