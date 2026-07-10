package mcheli.hud;

import java.util.ArrayList;
import java.util.List;
import mcheli.MCH_BaseInfo;
import mcheli.MCH_Lib;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.wrapper.W_ScaledResolution;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;

public class MCH_Hud extends MCH_BaseInfo {
   public static final MCH_Hud NoDisp = new MCH_Hud("none", "none");
   public final String name;
   public final String fileName;
   private List<MCH_HudItem> list;
   public boolean isWaitEndif;
   private boolean isDrawing;
   public boolean isIfFalse;
   public boolean exit;

   public MCH_Hud(String name, String fname) {
      this.name = name;
      this.fileName = fname;
      this.list = new ArrayList<>();
      this.isDrawing = false;
      this.isIfFalse = false;
      this.exit = false;
   }

   public void checkData() {
      for (MCH_HudItem hud : this.list) {
         hud.parent = this;
      }

      if (this.isWaitEndif) {
         throw new RuntimeException("Endif not found!");
      }
   }

   @Override
   public void loadItemData(int fileLine, String item, String data) {
      String[] prm = data.split("\\s*,\\s*");
      if (prm != null && prm.length != 0) {
         if (item.equalsIgnoreCase("If")) {
            if (this.isWaitEndif) {
               throw new RuntimeException("Endif not found!");
            }

            this.list.add(new MCH_HudItemConditional(fileLine, false, prm[0]));
            this.isWaitEndif = true;
         } else if (item.equalsIgnoreCase("Endif")) {
            if (!this.isWaitEndif) {
               throw new RuntimeException("IF in a pair can not be found!");
            }

            this.list.add(new MCH_HudItemConditional(fileLine, true, ""));
            this.isWaitEndif = false;
         } else if (item.equalsIgnoreCase("DrawString") || item.equalsIgnoreCase("DrawCenteredString")) {
            if (prm.length >= 3) {
               String s = prm[2];
               if (s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
                  s = s.substring(1, s.length() - 1);
                  this.list.add(new MCH_HudItemString(fileLine, prm[0], prm[1], s, prm, item.equalsIgnoreCase("DrawCenteredString")));
               }
            }
         } else if (item.equalsIgnoreCase("Exit")) {
            this.list.add(new MCH_HudItemExit(fileLine));
         } else if (item.equalsIgnoreCase("Color")) {
            if (prm.length == 1) {
               MCH_HudItemColor c = MCH_HudItemColor.createByParams(fileLine, new String[]{prm[0]});
               if (c != null) {
                  this.list.add(c);
               }
            } else if (prm.length == 4) {
               String[] s = new String[]{prm[0], prm[1], prm[2], prm[3]};
               MCH_HudItemColor c = MCH_HudItemColor.createByParams(fileLine, s);
               if (c != null) {
                  this.list.add(c);
               }
            }
         } else if (item.equalsIgnoreCase("DrawTexture")) {
            if (prm.length >= 9 && prm.length <= 10) {
               String rot = prm.length == 10 ? prm[9] : "0";
               this.list.add(new MCH_HudItemTexture(fileLine, prm[0], prm[1], prm[2], prm[3], prm[4], prm[5], prm[6], prm[7], prm[8], rot));
            }
         } else if (item.equalsIgnoreCase("DrawRect")) {
            if (prm.length == 4) {
               this.list.add(new MCH_HudItemRect(fileLine, prm[0], prm[1], prm[2], prm[3]));
            }
         } else if (item.equalsIgnoreCase("DrawLine")) {
            int len = prm.length;
            if (len >= 4 && len % 2 == 0) {
               this.list.add(new MCH_HudItemLine(fileLine, prm));
            }
         } else if (item.equalsIgnoreCase("DrawLineStipple")) {
            int len = prm.length;
            if (len >= 6 && len % 2 == 0) {
               this.list.add(new MCH_HudItemLineStipple(fileLine, prm));
            }
         } else if (item.equalsIgnoreCase("Call")) {
            int len = prm.length;
            if (len == 1) {
               this.list.add(new MCH_HudItemCall(fileLine, prm[0]));
            }
         } else if (item.equalsIgnoreCase("DrawEntityRadar") || item.equalsIgnoreCase("DrawEnemyRadar")) {
            if (prm.length == 5) {
               this.list.add(new MCH_HudItemRadar(fileLine, item.equalsIgnoreCase("DrawEntityRadar"), prm[0], prm[1], prm[2], prm[3], prm[4]));
            }
         } else if (item.equalsIgnoreCase("DrawGraduationYaw")
            || item.equalsIgnoreCase("DrawGraduationPitch1")
            || item.equalsIgnoreCase("DrawGraduationPitch2")
            || item.equalsIgnoreCase("DrawGraduationPitch3")) {
            if (prm.length == 4) {
               int type = -1;
               if (item.equalsIgnoreCase("DrawGraduationYaw")) {
                  type = 0;
               }

               if (item.equalsIgnoreCase("DrawGraduationPitch1")) {
                  type = 1;
               }

               if (item.equalsIgnoreCase("DrawGraduationPitch2")) {
                  type = 2;
               }

               if (item.equalsIgnoreCase("DrawGraduationPitch3")) {
                  type = 3;
               }

               this.list.add(new MCH_HudItemGraduation(fileLine, type, prm[0], prm[1], prm[2], prm[3]));
            }
         } else if (item.equalsIgnoreCase("DrawCameraRot") && prm.length == 2) {
            this.list.add(new MCH_HudItemCameraRot(fileLine, prm[0], prm[1]));
         }
      }
   }

   public void draw(MCH_EntityAircraft ac, EntityPlayer player, float partialTicks) {
      MCH_HudItem.ac = ac;
      MCH_HudItem.player = player;
      MCH_HudItem.partialTicks = partialTicks;
      ScaledResolution scaledresolution = new W_ScaledResolution(MCH_HudItem.mc, MCH_HudItem.mc.displayWidth, MCH_HudItem.mc.displayHeight);
      MCH_HudItem.scaleFactor = scaledresolution.getScaleFactor();
      if (MCH_HudItem.scaleFactor <= 0) {
         MCH_HudItem.scaleFactor = 1;
      }

      MCH_HudItem.width = (double)MCH_HudItem.mc.displayWidth / MCH_HudItem.scaleFactor;
      MCH_HudItem.height = (double)MCH_HudItem.mc.displayHeight / MCH_HudItem.scaleFactor;
      MCH_HudItem.centerX = MCH_HudItem.width / 2.0;
      MCH_HudItem.centerY = MCH_HudItem.height / 2.0;
      this.isIfFalse = false;
      this.isDrawing = false;
      this.exit = false;
      if (ac != null && ac.getAcInfo() != null && player != null) {
         MCH_HudItem.update();
         this.drawItems();
         MCH_HudItem.drawVarMap();
      }
   }

   protected void drawItems() {
      if (!this.isDrawing) {
         this.isDrawing = true;

         for (MCH_HudItem hud : this.list) {
            int line = -1;

            try {
               line = hud.fileLine;
               if (hud.canExecute()) {
                  hud.execute();
                  if (this.exit) {
                     break;
                  }
               }
            } catch (Exception e) {
               MCH_Lib.Log("#### Draw HUD Error!!!: line=%d, file=%s", line, this.fileName);
               e.printStackTrace();
               throw new RuntimeException(e);
            }
         }

         this.exit = false;
         this.isIfFalse = false;
         this.isDrawing = false;
      }
   }
}
