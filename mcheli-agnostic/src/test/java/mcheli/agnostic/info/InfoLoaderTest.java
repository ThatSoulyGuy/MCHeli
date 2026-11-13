package mcheli.agnostic.info;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import mcheli.agnostic.helicopter.MCH_HeliInfo;
import mcheli.agnostic.spi.Logger;
import mcheli.agnostic.spi.ModelHandle;
import mcheli.agnostic.spi.ResourceSource;
import org.junit.jupiter.api.Test;

/**
 * {@link MCH_InfoManagerBase}'s file-name normalization (shared with the dependent item registry) and its
 * load-failure report.
 */
class InfoLoaderTest {

    /** Records what the loader logs. */
    static final class RecordingLogger implements Logger {
        final List<String> warns = new ArrayList<>();
        final List<String> debugs = new ArrayList<>();
        public void info(String f, Object... a) { }
        public void warn(String f, Object... a) { warns.add(String.format(f, a)); }
        public void debug(String f, Object... a) { debugs.add(String.format(f, a)); }
        public void error(String m, Throwable t) { }
    }

    static final class HeliManager extends MCH_InfoManagerBase {
        final Map<String, MCH_BaseInfo> map = new LinkedHashMap<>();
        @Override public MCH_BaseInfo newInfo(String name) { return new MCH_HeliInfo(name); }
        @Override public Map getMap() { return map; }
    }

    static ResourceSource memSource(String dir, Map<String, String> files) {
        return new ResourceSource() {
            @Override public List<String> list(String d) { return d.equals(dir) ? new ArrayList<>(files.keySet()) : List.of(); }
            @Override public Reader openUtf8(String path) {
                String f = path.substring(path.indexOf('/') + 1);
                return path.startsWith(dir + "/") && files.containsKey(f) ? new StringReader(files.get(f)) : null;
            }
            @Override public boolean exists(String path) { return openUtf8(path) != null; }
            @Override public ModelHandle loadModel(String path) { return null; }
        };
    }

    @Test
    void definitionNameIsTheLowerCasedBaseNameOfATxtFile() {
        assertEquals("ah-64", MCH_InfoManagerBase.definitionName("ah-64.txt"));
        assertEquals("ah-64", MCH_InfoManagerBase.definitionName("AH-64.TXT")); // suffix in any case, as the reference
        assertEquals("a", MCH_InfoManagerBase.definitionName("a.txt"));
        assertNull(MCH_InfoManagerBase.definitionName(".txt"));
        assertNull(MCH_InfoManagerBase.definitionName("readme.md"));
        assertNull(MCH_InfoManagerBase.definitionName(null));
    }

    @Test
    void definitionNameIgnoresTheDefaultLocale() {
        Locale saved = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR")); // "I".toLowerCase() is the dotless "ı" here
            assertEquals("tiger", MCH_InfoManagerBase.definitionName("TIGER.TXT"));
        } finally {
            Locale.setDefault(saved);
        }
    }

    @Test
    void upperCaseFilesLoadUnderTheNormalizedName() {
        HeliManager mgr = new HeliManager();
        Map<String, String> files = new LinkedHashMap<>();
        files.put("Mi-24.TXT", "AddSeat = 0, 0, 0\n");
        assertTrue(mgr.load(memSource("helicopters", files), new RecordingLogger(), "helicopters"));
        assertTrue(mgr.getMap().containsKey("mi-24"));
    }

    @Test
    void loadFailureReportsTheLineTheExceptionAndTheStackTrace() {
        HeliManager mgr = new HeliManager();
        Map<String, String> files = new LinkedHashMap<>();
        files.put("bad.txt", "AddSeat = 0, 0, 0\nMaxHp = 1.0f\n");
        files.put("noseat.txt", "DisplayName = Nobody\n");
        RecordingLogger log = new RecordingLogger();
        mgr.load(memSource("helicopters", files), log, "helicopters");

        assertTrue(mgr.getMap().isEmpty());
        assertEquals(2, log.warns.size());
        String bad = log.warns.get(0);
        assertTrue(bad.contains("bad.txt") && bad.contains("line=2"), bad);
        assertTrue(bad.contains("NumberFormatException") && bad.contains("1.0f"), bad); // the reference dropped this
        String noSeat = log.warns.get(1);
        assertTrue(noSeat.contains("noseat.txt") && !noSeat.contains("line="), noSeat); // failed in isValidData
        assertTrue(noSeat.contains("AddSeat"), noSeat);
        assertEquals(2, log.debugs.size());
        assertTrue(log.debugs.get(0).contains("\tat "), "the stack trace goes to the debug level");
    }
}
