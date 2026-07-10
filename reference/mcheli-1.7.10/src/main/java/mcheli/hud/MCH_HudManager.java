package mcheli.hud;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;
import mcheli.MCH_InputFile;
import mcheli.MCH_Lib;
import net.minecraft.client.Minecraft;

public class MCH_HudManager {
   private static MCH_HudManager instance = new MCH_HudManager();
   private static HashMap<String, MCH_Hud> map;

   private MCH_HudManager() {
      map = new HashMap<>();
   }

   public static boolean load(String path) {
      MCH_HudItem.mc = Minecraft.getMinecraft();
      map.clear();
      path = path.replace('\\', '/');
      File dir = new File(path);
      File[] files = dir.listFiles(new FileFilter() {
         @Override
         public boolean accept(File pathname) {
            String s = pathname.getName().toLowerCase();
            return pathname.isFile() && s.length() >= 5 && s.substring(s.length() - 4).compareTo(".txt") == 0;
         }
      });
      if (files != null && files.length > 0) {
         for (File f : files) {
            MCH_InputFile inFile = new MCH_InputFile();
            int line = 0;

            try {
               String name = f.getName().toLowerCase();
               name = name.substring(0, name.length() - 4);
               if (!map.containsKey(name) && inFile.openUTF8(f)) {
                  MCH_Hud info = new MCH_Hud(name, f.getPath());

                  String str;
                  while ((str = inFile.br.readLine()) != null) {
                     line++;
                     str = str.trim();
                     if (str.equalsIgnoreCase("endif")) {
                        str = "endif=0";
                     }

                     if (str.equalsIgnoreCase("exit")) {
                        str = "exit=0";
                     }

                     int eqIdx = str.indexOf(61);
                     if (eqIdx >= 0 && str.length() > eqIdx + 1) {
                        info.loadItemData(line, str.substring(0, eqIdx).trim().toLowerCase(), str.substring(eqIdx + 1).trim());
                     }
                  }

                  info.checkData();
                  map.put(name, info);
               }
            } catch (IOException e) {
               e.printStackTrace();
            } catch (Exception e) {
               MCH_Lib.Log("### HUD file error! %s Line=%d", f.getName(), line);
               e.printStackTrace();
               throw new RuntimeException(e);
            } finally {
               inFile.close();
            }
         }

         MCH_Lib.Log("Read %d HUD", map.size());
         return map.size() > 0;
      } else {
         return false;
      }
   }

   public static MCH_Hud get(String name) {
      return map.get(name.toLowerCase());
   }

   public static boolean contains(String name) {
      return map.containsKey(name);
   }

   public static Set<String> getKeySet() {
      return map.keySet();
   }

   public static Collection<MCH_Hud> getValues() {
      return map.values();
   }
}
