package mcheli.weapon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

@SideOnly(Side.CLIENT)
public class MCH_RenderNone extends MCH_RenderBulletBase {
   @Override
   public void renderBullet(Entity entity, double posX, double posY, double posZ, float yaw, float partialTickTime) {
   }

   @Override
   protected ResourceLocation getEntityTexture(Entity entity) {
      return TEX_DEFAULT;
   }
}
