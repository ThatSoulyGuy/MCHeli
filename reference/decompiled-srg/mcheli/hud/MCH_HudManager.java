/*
 * Decompiled with CFR 0.152.
 */
package mcheli.hud;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;
import mcheli.MCH_InputFile;
import mcheli.MCH_Lib;
import mcheli.hud.MCH_Hud;
import mcheli.hud.MCH_HudItem;
import net.minecraft.client.Minecraft;

public class MCH_HudManager {
    private static MCH_HudManager instance = new MCH_HudManager();
    private static HashMap<String, MCH_Hud> map;

    private MCH_HudManager() {
        map = new HashMap();
    }

    public static boolean load(String path) {
        MCH_HudItem.mc = Minecraft.func_71410_x();
        map.clear();
        path = path.replace('\\', '/');
        File dir = new File(path);
        File[] files = dir.listFiles((FileFilter)new /* Unavailable Anonymous Inner Class!! */);
        if (files == null || files.length <= 0) {
            return false;
        }
        for (File f : files) {
            MCH_InputFile inFile = new MCH_InputFile();
            int line = 0;
            try {
                String str;
                String name = f.getName().toLowerCase();
                name = name.substring(0, name.length() - 4);
                if (map.containsKey(name) || !inFile.openUTF8(f)) continue;
                MCH_Hud info = new MCH_Hud(name, f.getPath());
                while ((str = inFile.br.readLine()) != null) {
                    int eqIdx;
                    ++line;
                    if ((str = str.trim()).equalsIgnoreCase("endif")) {
                        str = "endif=0";
                    }
                    if (str.equalsIgnoreCase("exit")) {
                        str = "exit=0";
                    }
                    if ((eqIdx = str.indexOf(61)) < 0 || str.length() <= eqIdx + 1) continue;
                    info.loadItemData(line, str.substring(0, eqIdx).trim().toLowerCase(), str.substring(eqIdx + 1).trim());
                }
                info.checkData();
                map.put(name, info);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            catch (Exception e) {
                MCH_Lib.Log((String)"### HUD file error! %s Line=%d", (Object[])new Object[]{f.getName(), line});
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            finally {
                inFile.close();
            }
        }
        MCH_Lib.Log((String)"Read %d HUD", (Object[])new Object[]{map.size()});
        return map.size() > 0;
    }

    public static MCH_Hud get(String name) {
        return (MCH_Hud)map.get(name.toLowerCase());
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

