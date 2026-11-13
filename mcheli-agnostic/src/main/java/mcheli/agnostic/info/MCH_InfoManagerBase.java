package mcheli.agnostic.info;

import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.util.List;
import java.util.Locale;
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

    /**
     * The definition name a file contributes, or {@code null} if it is not a definition file. Reference
     * {@code MCH_InfoManagerBase.load}: a {@code .txt} suffix in any case, at least one character before it, and the
     * name is the lower-cased base name. This is the ONE normalization rule: the item registry mints spawn-item ids
     * through it too, so the manager key, the item id and the synced config name can never disagree.
     * {@link Locale#ROOT} keeps an uppercase {@code I} from becoming a dotless {@code ı} under a Turkish default locale
     * (the reference used the default locale).
     */
    public static String definitionName(String fileName) {
        if (fileName == null) {
            return null;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.length() < 5 || !lower.endsWith(".txt")) {
            return null;
        }
        return lower.substring(0, lower.length() - 4);
    }

    /** Load every {@code *.txt} definition under {@code type} (e.g. "helicopters"). */
    public boolean load(ResourceSource res, Logger log, String type) {
        List<String> files = res.list(type);
        if (files == null || files.isEmpty()) {
            return false;
        }
        for (String fileName : files) {
            String name = definitionName(fileName);
            if (name == null) {
                continue;
            }
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
                            info.loadItemData(str.substring(0, eqIdx).trim().toLowerCase(Locale.ROOT), str.substring(eqIdx + 1).trim());
                        }
                    }

                    line = 0;
                    if (info.isValidData()) {
                        this.getMap().put(name, info);
                    }
                }
            } catch (Exception e) {
                logLoadFailure(log, "Load", fileName, line, e);
            } finally {
                inFile.close();
            }
        }

        log.info("Read %d %s", this.getMap().size(), type);
        return this.getMap().size() > 0;
    }

    /**
     * Report a definition that failed to parse. The reference logged the file + line and then
     * {@code e.printStackTrace()} (MCH_InfoManagerBase:51-57); the port keeps the warning, adds the exception itself
     * (so a content-pack author sees e.g. {@code NumberFormatException: For input string: "1.0f"} without a debug log),
     * and sends the stack trace to the debug level instead of stderr.
     */
    static void logLoadFailure(Logger log, String what, String file, int line, Exception e) {
        if (log == null) {
            return;
        }
        if (line > 0) {
            log.warn("### %s failed %s : line=%d : %s", what, file, line, e);
        } else {
            log.warn("### %s failed %s : %s", what, file, e);
        }
        log.debug("%s", stackTrace(e));
    }

    private static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
