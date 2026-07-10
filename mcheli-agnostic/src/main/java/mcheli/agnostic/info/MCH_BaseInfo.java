package mcheli.agnostic.info;

import java.io.Reader;
import mcheli.agnostic.spi.Logger;
import mcheli.agnostic.spi.ResourceSource;
import mcheli.agnostic.value.Vec3d;

/**
 * Base of every vehicle/weapon definition holder: the {@code key = value} parse helpers plus the
 * reload loop. Platform contact is carried by the injected {@link ResourceSource}/{@link Logger}
 * (set by {@link MCH_InfoManagerBase}); {@link #toVec3} yields an agnostic {@link Vec3d}.
 */
public class MCH_BaseInfo {
    public String filePath;
    public ResourceSource res;
    public Logger log;

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

    public Vec3d toVec3(String x, String y, String z) {
        return new Vec3d(this.toDouble(x), this.toDouble(y), this.toDouble(z));
    }

    public int toInt(String s) {
        return Integer.parseInt(s);
    }

    public int toInt(String s, int min, int max) {
        int f = Integer.parseInt(s);
        return f < min ? min : (f > max ? max : f);
    }

    public int hex2dec(String s) {
        return !s.startsWith("0x") && !s.startsWith("0X") && s.indexOf(0) != 35 ? (int) (Long.decode("0x" + s) & -1L) : (int) (Long.decode(s) & -1L);
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

        try {
            Reader reader = info.res != null ? info.res.openUtf8(info.filePath) : null;
            if (reader != null && inFile.open(reader)) {
                info.preReload();

                String str;
                while ((str = inFile.readLine()) != null) {
                    line++;
                    str = str.trim();
                    int eqIdx = str.indexOf('=');
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
            if (info.log != null) {
                if (line > 0) {
                    info.log.warn("### Reload failed %s : line=%d", info.filePath, line);
                } else {
                    info.log.warn("### Reload failed %s", info.filePath);
                }
            }
        } finally {
            inFile.close();
        }

        return true;
    }
}
