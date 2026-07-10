/*
 * Decompiled with CFR 0.152.
 */
package mcheli;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import mcheli.MCH_Lib;

public class MCH_SoundsJson {
    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static boolean update(String path) {
        boolean result = true;
        path = path.replace('\\', '/');
        File dir = new File(path + "sounds");
        File[] files = dir.listFiles((FileFilter)new /* Unavailable Anonymous Inner Class!! */);
        int cnt = 0;
        PrintWriter pw = null;
        try {
            File file = new File(path + "sounds.json");
            pw = new PrintWriter(file);
            pw.println("{");
            if (files != null) {
                LinkedHashMap map = new LinkedHashMap();
                for (File f : files) {
                    String name = f.getName().toLowerCase();
                    int ei = name.lastIndexOf(".");
                    String key = name = name.substring(0, ei);
                    char c = key.charAt(key.length() - 1);
                    if (c >= '0' && c <= '9') {
                        key = key.substring(0, key.length() - 1);
                    }
                    if (!map.containsKey(key)) {
                        map.put(key, new ArrayList());
                    }
                    ((ArrayList)map.get(key)).add(name);
                }
                for (String key : map.keySet()) {
                    ++cnt;
                    ArrayList list = (ArrayList)map.get(key);
                    String line = "";
                    line = "\"" + key + "\": {\"category\": \"master\",\"sounds\": [";
                    for (int fi = 0; fi < list.size(); ++fi) {
                        line = line + (fi > 0 ? "," : "") + "\"" + (String)list.get(fi) + "\"";
                    }
                    line = line + "]}";
                    if (cnt < map.size()) {
                        line = line + ",";
                    }
                    pw.println(line);
                }
            }
            pw.println("}");
            pw.println("");
            result = true;
        }
        catch (IOException e) {
            result = false;
            e.printStackTrace();
        }
        finally {
            if (pw != null) {
                pw.close();
            }
        }
        if (result) {
            MCH_Lib.Log((String)"Update sounds.json. %d sounds", (Object[])new Object[]{cnt});
        } else {
            MCH_Lib.Log((String)"Failed sounds.json update! %d sounds", (Object[])new Object[]{cnt});
        }
        return result;
    }
}

