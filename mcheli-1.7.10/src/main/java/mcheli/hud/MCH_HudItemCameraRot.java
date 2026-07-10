package mcheli.hud;

import mcheli.MCH_Camera;
import mcheli.MCH_Lib;
import net.minecraft.entity.Entity;

public class MCH_HudItemCameraRot extends MCH_HudItem {
   private final String drawPosX;
   private final String drawPosY;

   public MCH_HudItemCameraRot(int fileLine, String posx, String posy) {
      super(fileLine);
      this.drawPosX = toFormula(posx);
      this.drawPosY = toFormula(posy);
   }

   @Override
   public void execute() {
      this.drawCommonGunnerCamera(ac, ac.camera, colorSetting, centerX + calc(this.drawPosX), centerY + calc(this.drawPosY));
   }

   private void drawCommonGunnerCamera(Entity ac, MCH_Camera camera, int color, double posX, double posY) {
      if (camera != null) {
         double centerX = posX;
         double centerY = posY;
         int WW = 20;
         int WH = 10;
         int LW = 1;
         double[] line = new double[]{
            centerX - 21.0, centerY - 11.0, centerX + 21.0, centerY - 11.0, centerX + 21.0, centerY + 11.0, centerX - 21.0, centerY + 11.0
         };
         this.drawLine(line, color, 2);
         line = new double[]{
            centerX - 21.0,
            centerY,
            centerX,
            centerY,
            centerX + 21.0,
            centerY,
            centerX,
            centerY,
            centerX,
            centerY - 11.0,
            centerX,
            centerY,
            centerX,
            centerY + 11.0,
            centerX,
            centerY
         };
         this.drawLineStipple(line, color, 1, 52428);
         float pitch = camera.rotationPitch;
         if (pitch < -30.0F) {
            pitch = -30.0F;
         }

         if (pitch > 70.0F) {
            pitch = 70.0F;
         }

         pitch -= 20.0F;
         pitch = (float)(pitch * 0.16);
         float heliYaw = ac.prevRotationYaw + (ac.rotationYaw - ac.prevRotationYaw) / 2.0F;
         float cameraYaw = camera.prevRotationYaw + (camera.rotationYaw - camera.prevRotationYaw) / 2.0F;
         float yaw = (float)MCH_Lib.getRotateDiff(ac.rotationYaw, camera.rotationYaw);
         yaw *= 2.0F;
         if (yaw < -50.0F) {
            yaw = -50.0F;
         }

         if (yaw > 50.0F) {
            yaw = 50.0F;
         }

         yaw = (float)(yaw * 0.34);
         line = new double[]{
            centerX + yaw - 3.0,
            centerY + pitch - 2.0,
            centerX + yaw + 3.0,
            centerY + pitch - 2.0,
            centerX + yaw + 3.0,
            centerY + pitch + 2.0,
            centerX + yaw - 3.0,
            centerY + pitch + 2.0
         };
         this.drawLine(line, color, 2);
      }
   }
}
