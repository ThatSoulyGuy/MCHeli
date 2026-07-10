package mcheli.chain;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.MCH_Lib;
import mcheli.MCH_ModelManager;
import mcheli.wrapper.W_Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class MCH_RenderChain extends W_Render {
   public void doRender(Entity e, double posX, double posY, double posZ, float par8, float par9) {
      if (e instanceof MCH_EntityChain) {
         MCH_EntityChain chain = (MCH_EntityChain)e;
         if (chain.towedEntity != null && chain.towEntity != null) {
            GL11.glPushMatrix();
            GL11.glEnable(2884);
            GL11.glColor4f(0.5F, 0.5F, 0.5F, 1.0F);
            GL11.glTranslated(
               chain.towedEntity.lastTickPosX - RenderManager.renderPosX,
               chain.towedEntity.lastTickPosY - RenderManager.renderPosY,
               chain.towedEntity.lastTickPosZ - RenderManager.renderPosZ
            );
            this.bindTexture("textures/chain.png");
            double dx = chain.towEntity.lastTickPosX - chain.towedEntity.lastTickPosX;
            double dy = chain.towEntity.lastTickPosY - chain.towedEntity.lastTickPosY;
            double dz = chain.towEntity.lastTickPosZ - chain.towedEntity.lastTickPosZ;
            double diff = Math.sqrt(dx * dx + dy * dy + dz * dz);
            float CHAIN_LEN = 0.95F;
            double x = dx * 0.95F / diff;
            double y = dy * 0.95F / diff;
            double z = dz * 0.95F / diff;

            while (diff > 0.95F) {
               GL11.glTranslated(x, y, z);
               GL11.glPushMatrix();
               Vec3 v = MCH_Lib.getYawPitchFromVec(x, y, z);
               GL11.glRotatef((float)v.yCoord, 0.0F, -1.0F, 0.0F);
               GL11.glRotatef((float)v.zCoord, 0.0F, 0.0F, 1.0F);
               MCH_ModelManager.render("chain");
               GL11.glPopMatrix();
               diff -= 0.95F;
            }

            GL11.glPopMatrix();
         }
      }
   }

   @Override
   protected ResourceLocation getEntityTexture(Entity entity) {
      return TEX_DEFAULT;
   }
}
