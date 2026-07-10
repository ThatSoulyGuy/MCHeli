package mcheli;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.aircraft.MCH_AircraftCommonGui;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class MCH_GuiCommon extends MCH_AircraftCommonGui {
   public int hitCount = 0;

   public MCH_GuiCommon(Minecraft minecraft) {
      super(minecraft);
   }

   @Override
   public boolean isDrawGui(EntityPlayer player) {
      return true;
   }

   @Override
   public void drawGui(EntityPlayer player, boolean isThirdPersonView) {
      GL11.glLineWidth(scaleFactor);
      this.drawHitBullet(this.hitCount, 15, -805306369);
   }

   @Override
   public void onTick() {
      super.onTick();
      if (this.hitCount > 0) {
         this.hitCount--;
      }
   }

   public void hitBullet() {
      this.hitCount = 15;
   }
}
