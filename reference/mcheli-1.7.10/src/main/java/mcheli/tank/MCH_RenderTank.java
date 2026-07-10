package mcheli.tank;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.MCH_Config;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_RenderAircraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class MCH_RenderTank extends MCH_RenderAircraft {
   public MCH_RenderTank() {
      this.shadowSize = 2.0F;
   }

   @Override
   public void renderAircraft(MCH_EntityAircraft entity, double posX, double posY, double posZ, float yaw, float pitch, float roll, float tickTime) {
      MCH_TankInfo tankInfo = null;
      if (entity != null && entity instanceof MCH_EntityTank) {
         MCH_EntityTank tank = (MCH_EntityTank)entity;
         tankInfo = tank.getTankInfo();
         if (tankInfo != null) {
            this.renderWheel(tank, posX, posY, posZ);
            this.renderDebugHitBox(tank, posX, posY, posZ, yaw, pitch);
            this.renderDebugPilotSeat(tank, posX, posY, posZ, yaw, pitch, roll);
            GL11.glTranslated(posX, posY, posZ);
            GL11.glRotatef(yaw, 0.0F, -1.0F, 0.0F);
            GL11.glRotatef(pitch, 1.0F, 0.0F, 0.0F);
            GL11.glRotatef(roll, 0.0F, 0.0F, 1.0F);
            this.bindTexture("textures/tanks/" + tank.getTextureName() + ".png", tank);
            renderBody(tankInfo.model);
         }
      }
   }

   public void renderWheel(MCH_EntityTank tank, double posX, double posY, double posZ) {
      if (MCH_Config.TestMode.prmBool) {
         if (debugModel != null) {
            GL11.glColor4f(0.75F, 0.75F, 0.75F, 0.5F);

            for (MCH_EntityWheel w : tank.WheelMng.wheels) {
               GL11.glPushMatrix();
               GL11.glTranslated(
                  w.posX - tank.posX + posX, w.posY - tank.posY + posY + 0.25, w.posZ - tank.posZ + posZ
               );
               GL11.glScalef(w.width, w.height / 2.0F, w.width);
               this.bindTexture("textures/seat_pilot.png");
               debugModel.renderAll();
               GL11.glPopMatrix();
            }

            GL11.glColor4f(0.75F, 0.75F, 0.75F, 1.0F);
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawing(1);
            Vec3 wp = tank.getTransformedPosition(tank.WheelMng.weightedCenter);
            wp.xCoord = wp.xCoord - tank.posX;
            wp.yCoord = wp.yCoord - tank.posY;
            wp.zCoord = wp.zCoord - tank.posZ;

            for (int i = 0; i < tank.WheelMng.wheels.length / 2; i++) {
               tessellator.setColorRGBA_I(((i & 4) > 0 ? 16711680 : 0) | ((i & 2) > 0 ? 65280 : 0) | ((i & 1) > 0 ? 255 : 0), 192);
               MCH_EntityWheel w1 = tank.WheelMng.wheels[i * 2 + 0];
               MCH_EntityWheel w2 = tank.WheelMng.wheels[i * 2 + 1];
               if (w1.isPlus) {
                  tessellator.addVertex(
                     w2.posX - tank.posX + posX, w2.posY - tank.posY + posY, w2.posZ - tank.posZ + posZ
                  );
                  tessellator.addVertex(
                     w1.posX - tank.posX + posX, w1.posY - tank.posY + posY, w1.posZ - tank.posZ + posZ
                  );
                  tessellator.addVertex(
                     w1.posX - tank.posX + posX, w1.posY - tank.posY + posY, w1.posZ - tank.posZ + posZ
                  );
                  tessellator.addVertex(posX + wp.xCoord, posY + wp.yCoord, posZ + wp.zCoord);
                  tessellator.addVertex(posX + wp.xCoord, posY + wp.yCoord, posZ + wp.zCoord);
                  tessellator.addVertex(
                     w2.posX - tank.posX + posX, w2.posY - tank.posY + posY, w2.posZ - tank.posZ + posZ
                  );
               } else {
                  tessellator.addVertex(
                     w1.posX - tank.posX + posX, w1.posY - tank.posY + posY, w1.posZ - tank.posZ + posZ
                  );
                  tessellator.addVertex(
                     w2.posX - tank.posX + posX, w2.posY - tank.posY + posY, w2.posZ - tank.posZ + posZ
                  );
                  tessellator.addVertex(
                     w2.posX - tank.posX + posX, w2.posY - tank.posY + posY, w2.posZ - tank.posZ + posZ
                  );
                  tessellator.addVertex(posX + wp.xCoord, posY + wp.yCoord, posZ + wp.zCoord);
                  tessellator.addVertex(posX + wp.xCoord, posY + wp.yCoord, posZ + wp.zCoord);
                  tessellator.addVertex(
                     w1.posX - tank.posX + posX, w1.posY - tank.posY + posY, w1.posZ - tank.posZ + posZ
                  );
               }
            }

            tessellator.draw();
         }
      }
   }

   @Override
   protected ResourceLocation getEntityTexture(Entity entity) {
      return TEX_DEFAULT;
   }
}
