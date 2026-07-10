/*
 * Decompiled with CFR 0.152.
 */
package mcheli;

import java.io.File;
import java.io.FileFilter;
import java.util.Map;
import mcheli.MCH_BaseInfo;
import mcheli.MCH_InputFile;
import mcheli.MCH_Lib;

public abstract class MCH_InfoManagerBase {
    public abstract MCH_BaseInfo newInfo(String var1);

    public abstract Map getMap();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean load(String path, String type) {
        File dir = new File((path = path.replace('\\', '/')) + type);
        File[] files = dir.listFiles((FileFilter)new /* Unavailable Anonymous Inner Class!! */);
        if (files == null || files.length <= 0) {
            return false;
        }
        for (File f : files) {
            int line = 0;
            MCH_InputFile inFile = new MCH_InputFile();
            Object br = null;
            try {
                String str;
                String name = f.getName().toLowerCase();
                name = name.substring(0, name.length() - 4);
                if (this.getMap().containsKey(name) || !inFile.openUTF8(f)) continue;
                MCH_BaseInfo info = this.newInfo(name);
                info.filePath = f.getCanonicalPath();
                while ((str = inFile.br.readLine()) != null) {
                    ++line;
                    int eqIdx = (str = str.trim()).indexOf(61);
                    if (eqIdx < 0 || str.length() <= eqIdx + 1) continue;
                    info.loadItemData(str.substring(0, eqIdx).trim().toLowerCase(), str.substring(eqIdx + 1).trim());
                }
                line = 0;
                if (!info.isValidData()) continue;
                this.getMap().put(name, info);
            }
            catch (Exception e) {
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
        MCH_Lib.Log((String)"Read %d %s", (Object[])new Object[]{this.getMap().size(), type});
        return this.getMap().size() > 0;
    }
}

