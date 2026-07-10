/*
 * Decompiled with CFR 0.152.
 */
package mcheli.wrapper;

import java.util.ArrayList;
import java.util.HashMap;
import mcheli.MCH_Lib;
import mcheli.MCH_OutputFile;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.stats.Achievement;

/*
 * Exception performing whole class analysis ignored.
 */
public class W_LanguageRegistry {
    private static HashMap<String, ArrayList<String>> map = new HashMap();

    public static void addName(Object objectToName, String name) {
        W_LanguageRegistry.addNameForObject((Object)objectToName, (String)"en_US", (String)name);
    }

    public static void addNameForObject(Object o, String lang, String name) {
        W_LanguageRegistry.addNameForObject((Object)o, (String)lang, (String)name, (String)"", (String)"");
    }

    public static void addNameForObject(Object o, String lang, String name, String key, String desc) {
        if (o == null) {
            return;
        }
        if (!map.containsKey(lang)) {
            map.put(lang, new ArrayList());
        }
        if (o instanceof Item) {
            ((ArrayList)map.get(lang)).add(((Item)o).func_77658_a() + ".name=" + name);
        }
        if (o instanceof Block) {
            ((ArrayList)map.get(lang)).add(((Block)o).func_149739_a() + ".name=" + name);
        } else if (o instanceof Achievement) {
            ((ArrayList)map.get(lang)).add("achievement." + key + "=" + name);
            ((ArrayList)map.get(lang)).add("achievement." + key + ".desc=" + desc);
        }
    }

    public static void updateLang(String filePath) {
        for (String key : map.keySet()) {
            ArrayList list = (ArrayList)map.get(key);
            MCH_OutputFile file = new MCH_OutputFile();
            if (!file.openUTF8(filePath + key + ".lang")) continue;
            for (String s : list) {
                file.writeLine(s);
            }
            MCH_Lib.Log((String)("[mcheli] Update lang:" + file.file.getAbsolutePath()), (Object[])new Object[0]);
            file.close();
        }
        map = null;
    }
}

