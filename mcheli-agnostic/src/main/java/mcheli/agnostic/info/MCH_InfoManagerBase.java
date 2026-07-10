package mcheli.agnostic.info;

import java.io.Reader;
import java.util.List;
import java.util.Map;
import mcheli.agnostic.spi.Logger;
import mcheli.agnostic.spi.ResourceSource;

/**
 * Base class for the per-type definition managers (helicopters, planes, tanks, ...). Walks a
 * definition directory via {@link ResourceSource}, parses each {@code key = value} .txt into a
 * {@link MCH_BaseInfo}, and caches it by name.
 */
public abstract class MCH_InfoManagerBase {
    public abstract MCH_BaseInfo newInfo(String name);

    public abstract Map getMap();

    /** Load every {@code *.txt} definition under {@code type} (e.g. "helicopters"). */
    public boolean load(ResourceSource res, Logger log, String type) {
        List<String> files = res.list(type);
        if (files == null || files.isEmpty()) {
            return false;
        }
        for (String fileName : files) {
            String lower = fileName.toLowerCase();
            if (lower.length() < 5 || !lower.endsWith(".txt")) {
                continue;
            }
            String name = lower.substring(0, lower.length() - 4);
            if (this.getMap().containsKey(name)) {
                continue;
            }
            int line = 0;
            MCH_InputFile inFile = new MCH_InputFile();
            try {
                String path = type + "/" + fileName;
                Reader reader = res.openUtf8(path);
                if (reader != null && inFile.open(reader)) {
                    MCH_BaseInfo info = this.newInfo(name);
                    info.res = res;
                    info.log = log;
                    info.filePath = path;

                    String str;
                    while ((str = inFile.readLine()) != null) {
                        line++;
                        str = str.trim();
                        int eqIdx = str.indexOf('=');
                        if (eqIdx >= 0 && str.length() > eqIdx + 1) {
                            info.loadItemData(str.substring(0, eqIdx).trim().toLowerCase(), str.substring(eqIdx + 1).trim());
                        }
                    }

                    line = 0;
                    if (info.isValidData()) {
                        this.getMap().put(name, info);
                    }
                }
            } catch (Exception e) {
                if (line > 0) {
                    log.warn("### Load failed %s : line=%d", fileName, line);
                } else {
                    log.warn("### Load failed %s", fileName);
                }
            } finally {
                inFile.close();
            }
        }

        log.info("Read %d %s", this.getMap().size(), type);
        return this.getMap().size() > 0;
    }
}
