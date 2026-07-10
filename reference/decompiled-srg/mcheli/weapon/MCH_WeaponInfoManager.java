/*
 * Decompiled with CFR 0.152.
 */
package mcheli.weapon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;
import mcheli.MCH_Lib;
import mcheli.MCH_MOD;
import mcheli.weapon.MCH_WeaponInfo;
import mcheli.wrapper.W_Item;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/*
 * Exception performing whole class analysis ignored.
 */
public class MCH_WeaponInfoManager {
    private static MCH_WeaponInfoManager instance = new MCH_WeaponInfoManager();
    private static HashMap<String, MCH_WeaponInfo> map;
    private static String lastPath;

    private MCH_WeaponInfoManager() {
        map = new HashMap();
    }

    public static boolean reload() {
        boolean ret = false;
        try {
            map.clear();
            ret = MCH_WeaponInfoManager.load((String)lastPath);
            MCH_WeaponInfoManager.setRoundItems();
            MCH_MOD.proxy.registerModels();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return ret;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static boolean load(String path) {
        lastPath = path;
        File dir = new File(path = path.replace('\\', '/'));
        File[] files = dir.listFiles((FileFilter)new /* Unavailable Anonymous Inner Class!! */);
        if (files == null || files.length <= 0) {
            return false;
        }
        for (File f : files) {
            BufferedReader br = null;
            int line = 0;
            try {
                String str;
                String name = f.getName().toLowerCase();
                name = name.substring(0, name.length() - 4);
                if (map.containsKey(name)) continue;
                br = new BufferedReader(new FileReader(f));
                MCH_WeaponInfo info = new MCH_WeaponInfo(name);
                while ((str = br.readLine()) != null) {
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
                try {
                    if (br != null) {
                        br.close();
                    }
                }
                catch (Exception e) {}
            }
        }
        MCH_Lib.Log((String)"[mcheli] Read %d weapons", (Object[])new Object[]{map.size()});
        return map.size() > 0;
    }

    public static void setRoundItems() {
        for (MCH_WeaponInfo w : map.values()) {
            for (MCH_WeaponInfo.RoundItem r : w.roundItems) {
                Item item = W_Item.getItemByName((String)r.itemName);
                r.itemStack = new ItemStack(item, 1, r.damage);
            }
        }
    }

    public static MCH_WeaponInfo get(String name) {
        return (MCH_WeaponInfo)map.get(name);
    }

    public static boolean contains(String name) {
        return map.containsKey(name);
    }

    public static Set<String> getKeySet() {
        return map.keySet();
    }

    public static Collection<MCH_WeaponInfo> getValues() {
        return map.values();
    }
}

