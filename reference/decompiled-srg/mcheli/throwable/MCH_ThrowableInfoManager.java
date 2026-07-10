/*
 * Decompiled with CFR 0.152.
 */
package mcheli.throwable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import mcheli.MCH_InputFile;
import mcheli.MCH_Lib;
import mcheli.throwable.MCH_ThrowableInfo;
import net.minecraft.item.Item;

public class MCH_ThrowableInfoManager {
    private static MCH_ThrowableInfoManager instance = new MCH_ThrowableInfoManager();
    private static HashMap<String, MCH_ThrowableInfo> map = new LinkedHashMap();

    private MCH_ThrowableInfoManager() {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static boolean load(String path) {
        File dir = new File(path = path.replace('\\', '/'));
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
                MCH_ThrowableInfo info = new MCH_ThrowableInfo(name);
                while ((str = inFile.br.readLine()) != null) {
                    ++line;
                    int eqIdx = (str = str.trim()).indexOf(61);
                    if (eqIdx < 0 || str.length() <= eqIdx + 1) continue;
                    info.loadItemData(str.substring(0, eqIdx).trim().toLowerCase(), str.substring(eqIdx + 1).trim());
                }
                info.checkData();
                map.put(name, info);
            }
            catch (IOException e) {
                if (line > 0) {
                    MCH_Lib.Log((String)"### Load failed %s : line=%d", (Object[])new Object[]{f.getName(), line});
                } else {
                    MCH_Lib.Log((String)"### Load failed %s", (Object[])new Object[]{f.getName()});
                }
                e.printStackTrace();
            }
            finally {
                inFile.close();
            }
        }
        MCH_Lib.Log((String)"Read %d throwable", (Object[])new Object[]{map.size()});
        return map.size() > 0;
    }

    public static MCH_ThrowableInfo get(String name) {
        return (MCH_ThrowableInfo)map.get(name);
    }

    public static MCH_ThrowableInfo get(Item item) {
        for (MCH_ThrowableInfo info : map.values()) {
            if (info.item != item) continue;
            return info;
        }
        return null;
    }

    public static boolean contains(String name) {
        return map.containsKey(name);
    }

    public static Set<String> getKeySet() {
        return map.keySet();
    }

    public static Collection<MCH_ThrowableInfo> getValues() {
        return map.values();
    }
}

