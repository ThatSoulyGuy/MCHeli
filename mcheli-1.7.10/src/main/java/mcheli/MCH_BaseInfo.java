package mcheli;

import java.io.BufferedReader;
import java.io.File;
import net.minecraft.util.Vec3;

public class MCH_BaseInfo {
   public String filePath;

   public boolean toBool(String s) {
      return s.equalsIgnoreCase("true");
   }

   public boolean toBool(String s, boolean defaultValue) {
      if (s.equalsIgnoreCase("true")) {
         return true;
      } else {
         return s.equalsIgnoreCase("false") ? false : defaultValue;
      }
   }

   public float toFloat(String s) {
      return Float.parseFloat(s);
   }

   public float toFloat(String s, float min, float max) {
      float f = Float.parseFloat(s);
      return f < min ? min : (f > max ? max : f);
   }

   public double toDouble(String s) {
      return Double.parseDouble(s);
   }

   public Vec3 toVec3(String x, String y, String z) {
      return Vec3.createVectorHelper(this.toDouble(x), this.toDouble(y), this.toDouble(z));
   }

   public int toInt(String s) {
      return Integer.parseInt(s);
   }

   public int toInt(String s, int min, int max) {
      int f = Integer.parseInt(s);
      return f < min ? min : (f > max ? max : f);
   }

   public int hex2dec(String s) {
      return !s.startsWith("0x") && !s.startsWith("0X") && s.indexOf(0) != 35 ? (int)(Long.decode("0x" + s) & -1L) : (int)(Long.decode(s) & -1L);
   }

   public String[] splitParam(String data) {
      return data.split("\\s*,\\s*");
   }

   public String[] splitParamSlash(String data) {
      return data.split("\\s*/\\s*");
   }

   public boolean isValidData() throws Exception {
      return true;
   }

   public void loadItemData(String item, String data) {
   }

   public void loadItemData(int fileLine, String item, String data) {
   }

   public void preReload() {
   }

   public void postReload() {
   }

   public boolean canReloadItem(String item) {
      return false;
   }

   public boolean reload() {
      return this.reload(this);
   }

   private boolean reload(MCH_BaseInfo info) {
      int line = 0;
      MCH_InputFile inFile = new MCH_InputFile();
      BufferedReader br = null;
      File f = new File(info.filePath);

      try {
         if (inFile.openUTF8(f)) {
            info.preReload();

            String str;
            while ((str = inFile.br.readLine()) != null) {
               line++;
               str = str.trim();
               int eqIdx = str.indexOf(61);
               if (eqIdx >= 0 && str.length() > eqIdx + 1) {
                  String item = str.substring(0, eqIdx).trim().toLowerCase();
                  if (info.canReloadItem(item)) {
                     info.loadItemData(item, str.substring(eqIdx + 1).trim());
                  }
               }
            }

            line = 0;
            info.isValidData();
            info.postReload();
         }
      } catch (Exception e) {
         if (line > 0) {
            MCH_Lib.Log("### Load failed %s : line=%d", f.getName(), line);
         } else {
            MCH_Lib.Log("### Load failed %s", f.getName());
         }

         e.printStackTrace();
      } finally {
         inFile.close();
      }

      return true;
   }
}
