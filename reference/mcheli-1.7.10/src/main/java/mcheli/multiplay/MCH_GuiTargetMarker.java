package mcheli.multiplay;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import mcheli.MCH_Config;
import mcheli.MCH_MarkEntityPos;
import mcheli.MCH_ServerSettings;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_EntitySeat;
import mcheli.gui.MCH_Gui;
import mcheli.particles.MCH_ParticlesUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

@SideOnly(Side.CLIENT)
public class MCH_GuiTargetMarker extends MCH_Gui {
   private static FloatBuffer matModel = BufferUtils.createFloatBuffer(16);
   private static FloatBuffer matProjection = BufferUtils.createFloatBuffer(16);
   private static IntBuffer matViewport = BufferUtils.createIntBuffer(16);
   private static ArrayList<MCH_MarkEntityPos> entityPos = new ArrayList<>();
   private static HashMap<Integer, Integer> spotedEntity = new HashMap<>();
   private static Minecraft s_minecraft;
   private static int spotedEntityCountdown = 0;

   public MCH_GuiTargetMarker(Minecraft minecraft) {
      super(minecraft);
      s_minecraft = minecraft;
   }

   @Override
   public void initGui() {
      super.initGui();
   }

   @Override
   public boolean doesGuiPauseGame() {
      return false;
   }

   @Override
   public boolean isDrawGui(EntityPlayer player) {
      return player != null && player.worldObj != null;
   }

   public static void onClientTick() {
      if (!Minecraft.getMinecraft().isGamePaused()) {
         spotedEntityCountdown++;
      }

      if (spotedEntityCountdown >= 20) {
         spotedEntityCountdown = 0;

         for (Integer key : spotedEntity.keySet()) {
            int count = spotedEntity.get(key);
            if (count > 0) {
               spotedEntity.put(key, count - 1);
            }
         }

         Iterator<Integer> i = spotedEntity.values().iterator();

         while (i.hasNext()) {
            if (i.next() <= 0) {
               i.remove();
            }
         }
      }
   }

   public static boolean isSpotedEntity(Entity entity) {
      int entityId = entity.getEntityId();

      for (int key : spotedEntity.keySet()) {
         if (key == entityId) {
            return true;
         }
      }

      return false;
   }

   public static void addSpotedEntity(int entityId, int count) {
      if (spotedEntity.containsKey(entityId)) {
         int now = spotedEntity.get(entityId);
         if (count > now) {
            spotedEntity.put(entityId, count);
         }
      } else {
         spotedEntity.put(entityId, count);
      }
   }

   public static void addMarkEntityPos(int reserve, Entity entity, double x, double y, double z) {
      addMarkEntityPos(reserve, entity, x, y, z, false);
   }

   public static void addMarkEntityPos(int reserve, Entity entity, double x, double y, double z, boolean nazo) {
      if (isEnableEntityMarker()) {
         MCH_TargetType spotType = MCH_TargetType.NONE;
         EntityPlayer clientPlayer = s_minecraft.thePlayer;
         if (entity instanceof MCH_EntityAircraft) {
            MCH_EntityAircraft ac = (MCH_EntityAircraft)entity;
            if (ac.isMountedEntity(clientPlayer)) {
               return;
            }

            if (ac.isMountedSameTeamEntity(clientPlayer)) {
               spotType = MCH_TargetType.SAME_TEAM_PLAYER;
            }
         } else if (entity instanceof EntityPlayer) {
            if (entity == clientPlayer || entity.ridingEntity instanceof MCH_EntitySeat || entity.ridingEntity instanceof MCH_EntityAircraft) {
               return;
            }

            if (clientPlayer.getTeam() != null && clientPlayer.isOnSameTeam((EntityLivingBase)entity)) {
               spotType = MCH_TargetType.SAME_TEAM_PLAYER;
            }
         }

         if (spotType == MCH_TargetType.NONE && isSpotedEntity(entity)) {
            spotType = MCH_Multiplay.canSpotEntity(
               clientPlayer, clientPlayer.posX, clientPlayer.posY + clientPlayer.getEyeHeight(), clientPlayer.posZ, entity, false
            );
         }

         if (reserve == 100) {
            spotType = MCH_TargetType.POINT;
         }

         if (spotType != MCH_TargetType.NONE) {
            MCH_MarkEntityPos e = new MCH_MarkEntityPos(spotType.ordinal(), entity);
            GL11.glGetFloat(2982, matModel);
            GL11.glGetFloat(2983, matProjection);
            GL11.glGetInteger(2978, matViewport);
            if (nazo) {
               GLU.gluProject((float)z, (float)y, (float)x, matModel, matProjection, matViewport, e.pos);
               float yy = e.pos.get(1);
               GLU.gluProject((float)x, (float)y, (float)z, matModel, matProjection, matViewport, e.pos);
               e.pos.put(1, yy);
            } else {
               GLU.gluProject((float)x, (float)y, (float)z, matModel, matProjection, matViewport, e.pos);
            }

            entityPos.add(e);
         }
      }
   }

   public static void clearMarkEntityPos() {
      entityPos.clear();
   }

   public static boolean isEnableEntityMarker() {
      return MCH_Config.DisplayEntityMarker.prmBool
         && (Minecraft.getMinecraft().isSingleplayer() || MCH_ServerSettings.enableEntityMarker)
         && MCH_Config.EntityMarkerSize.prmDouble > 0.0;
   }

   @Override
   public void drawGui(EntityPlayer player, boolean isThirdPersonView) {
      GL11.glLineWidth(scaleFactor * 2);
      if (this.isDrawGui(player)) {
         GL11.glDisable(3042);
         if (isEnableEntityMarker()) {
            this.drawMark();
         }
      }
   }

   void drawMark() {
      int[] COLOR_TABLE = new int[]{0, -808464433, -805371904, -805306624, -822018049, -805351649, -65536, 0};
      int scale = scaleFactor > 0 ? scaleFactor : 2;
      GL11.glEnable(3042);
      GL11.glDisable(3553);
      GL11.glBlendFunc(770, 771);
      GL11.glColor4b((byte)-1, (byte)-1, (byte)-1, (byte)-1);
      GL11.glDepthMask(false);
      int DW = this.mc.displayWidth;
      int DH = this.mc.displayHeight;
      int DSW = this.mc.displayWidth / scale;
      int DSH = this.mc.displayHeight / scale;
      double x = 9999.0;
      double z = 9999.0;
      double y = 9999.0;
      Tessellator tessellator = Tessellator.instance;

      for (int i = 0; i < 2; i++) {
         if (i == 0) {
            tessellator.startDrawing(i == 0 ? 4 : 1);
         }

         for (MCH_MarkEntityPos e : entityPos) {
            int color = COLOR_TABLE[e.type];
            x = e.pos.get(0) / scale;
            z = e.pos.get(2);
            y = e.pos.get(1) / scale;
            if (z < 1.0) {
               y = DSH - y;
            } else if (x < DW / 2) {
               x = 10000.0;
            } else if (x >= DW / 2) {
               x = -10000.0;
            }

            if (i == 0) {
               double size = MCH_Config.EntityMarkerSize.prmDouble;
               if (e.type < MCH_TargetType.POINT.ordinal() && z < 1.0 && x >= 0.0 && x <= DSW && y >= 0.0 && y <= DSH) {
                  this.drawTriangle1(tessellator, x, y, size, color);
               }
            } else if (e.type == MCH_TargetType.POINT.ordinal() && e.entity != null) {
               double MARK_SIZE = MCH_Config.BlockMarkerSize.prmDouble;
               if (z < 1.0 && x >= 0.0 && x <= DSW - 20 && y >= 0.0 && y <= DSH - 40) {
                  double dist = this.mc.thePlayer.getDistanceToEntity(e.entity);
                  GL11.glEnable(3553);
                  this.drawCenteredString(String.format("%.0fm", dist), (int)x, (int)(y + MARK_SIZE * 1.1 + 16.0), color);
                  if (x >= DSW / 2 - 20 && x <= DSW / 2 + 20 && y >= DSH / 2 - 20 && y <= DSH / 2 + 20) {
                     this.drawString(String.format("x : %.0f", e.entity.posX), (int)(x + MARK_SIZE + 18.0), (int)y - 12, color);
                     this.drawString(String.format("y : %.0f", e.entity.posY), (int)(x + MARK_SIZE + 18.0), (int)y - 4, color);
                     this.drawString(String.format("z : %.0f", e.entity.posZ), (int)(x + MARK_SIZE + 18.0), (int)y + 4, color);
                  }

                  GL11.glDisable(3553);
                  tessellator.startDrawing(1);
                  drawRhombus(tessellator, 15, x, y, this.zLevel, MARK_SIZE, color);
               } else {
                  tessellator.startDrawing(1);
                  double S = 30.0;
                  if (x < S) {
                     drawRhombus(tessellator, 1, S, DSH / 2, this.zLevel, MARK_SIZE, color);
                  } else if (x > DSW - S) {
                     drawRhombus(tessellator, 4, DSW - S, DSH / 2, this.zLevel, MARK_SIZE, color);
                  }

                  if (y < S) {
                     drawRhombus(tessellator, 8, DSW / 2, S, this.zLevel, MARK_SIZE, color);
                  } else if (y > DSH - S * 2.0) {
                     drawRhombus(tessellator, 2, DSW / 2, DSH - S * 2.0, this.zLevel, MARK_SIZE, color);
                  }
               }

               tessellator.draw();
            }
         }

         if (i == 0) {
            tessellator.draw();
         }
      }

      GL11.glDepthMask(true);
      GL11.glEnable(3553);
      GL11.glDisable(3042);
   }

   public static void drawRhombus(Tessellator tessellator, int dir, double x, double y, double z, double size, int color) {
      size *= 2.0;
      tessellator.setColorRGBA_I(16777215 & color, color >> 24 & 0xFF);
      double M = size / 3.0;
      if ((dir & 1) != 0) {
         tessellator.addVertex(x - size, y, z);
         tessellator.addVertex(x - size + M, y - M, z);
         tessellator.addVertex(x - size, y, z);
         tessellator.addVertex(x - size + M, y + M, z);
      }

      if ((dir & 4) != 0) {
         tessellator.addVertex(x + size, y, z);
         tessellator.addVertex(x + size - M, y - M, z);
         tessellator.addVertex(x + size, y, z);
         tessellator.addVertex(x + size - M, y + M, z);
      }

      if ((dir & 8) != 0) {
         tessellator.addVertex(x, y - size, z);
         tessellator.addVertex(x + M, y - size + M, z);
         tessellator.addVertex(x, y - size, z);
         tessellator.addVertex(x - M, y - size + M, z);
      }

      if ((dir & 2) != 0) {
         tessellator.addVertex(x, y + size, z);
         tessellator.addVertex(x + M, y + size - M, z);
         tessellator.addVertex(x, y + size, z);
         tessellator.addVertex(x - M, y + size - M, z);
      }
   }

   public void drawTriangle1(Tessellator tessellator, double x, double y, double size, int color) {
      tessellator.setColorRGBA_I(16777215 & color, color >> 24 & 0xFF);
      tessellator.addVertex(x + size / 2.0, y - 10.0 - size, this.zLevel);
      tessellator.addVertex(x - size / 2.0, y - 10.0 - size, this.zLevel);
      tessellator.addVertex(x + 0.0, y - 10.0, this.zLevel);
   }

   public void drawTriangle2(Tessellator tessellator, double x, double y, double size, int color) {
      tessellator.setColorRGBA_I(8355711 & color, color >> 24 & 0xFF);
      tessellator.addVertex(x + size / 2.0, y - 10.0 - size, this.zLevel);
      tessellator.addVertex(x - size / 2.0, y - 10.0 - size, this.zLevel);
      tessellator.addVertex(x - size / 2.0, y - 10.0 - size, this.zLevel);
      tessellator.addVertex(x + 0.0, y - 10.0, this.zLevel);
      tessellator.addVertex(x + 0.0, y - 10.0, this.zLevel);
      tessellator.addVertex(x + size / 2.0, y - 10.0 - size, this.zLevel);
   }

   public static void markPoint(int px, int py, int pz) {
      EntityPlayer player = Minecraft.getMinecraft().thePlayer;
      if (player != null && player.worldObj != null) {
         if (py < 1000) {
            MCH_ParticlesUtil.spawnMarkPoint(player, 0.5 + px, 1.0 + py, 0.5 + pz);
         } else {
            MCH_ParticlesUtil.clearMarkPoint();
         }
      }
   }
}
