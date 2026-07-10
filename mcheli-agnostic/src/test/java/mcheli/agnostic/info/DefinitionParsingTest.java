package mcheli.agnostic.info;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mcheli.agnostic.spi.Logger;
import mcheli.agnostic.spi.ModelHandle;
import mcheli.agnostic.spi.ResourceSource;
import mcheli.agnostic.value.Vec3d;
import org.junit.jupiter.api.Test;

/**
 * Proves the coerced definition parser (MCH_InfoManagerBase / MCH_BaseInfo / MCH_InputFile) loads a
 * real-shaped MCHeli config end-to-end with ZERO Minecraft — the payoff of the compiler-enforced
 * agnostic boundary, and the de-risking check for the ResourceSource / Logger / Vec3d ports.
 */
class DefinitionParsingTest {

    /** A concrete definition holder that parses a few AH-64-style directives. */
    static final class TestInfo extends MCH_BaseInfo {
        String displayName;
        int maxHp;
        Vec3d camera;

        @Override
        public void loadItemData(String item, String data) {
            switch (item) {
                case "displayname" -> displayName = data;
                case "maxhp" -> maxHp = toInt(data);
                case "cameraposition" -> {
                    String[] p = splitParam(data);
                    camera = toVec3(p[0], p[1], p[2]);
                }
                default -> { }
            }
        }
    }

    static final class TestManager extends MCH_InfoManagerBase {
        final Map<String, MCH_BaseInfo> map = new LinkedHashMap<>();
        @Override public MCH_BaseInfo newInfo(String name) { return new TestInfo(); }
        @Override public Map getMap() { return map; }
    }

    /** In-memory ResourceSource holding one definition file. */
    static ResourceSource memSource(String dir, String file, String content) {
        return new ResourceSource() {
            @Override public List<String> list(String d) { return d.equals(dir) ? List.of(file) : List.of(); }
            @Override public Reader openUtf8(String path) { return path.equals(dir + "/" + file) ? new StringReader(content) : null; }
            @Override public boolean exists(String path) { return path.equals(dir + "/" + file); }
            @Override public ModelHandle loadModel(String path) { return null; }
        };
    }

    static final Logger NOP = new Logger() {
        public void info(String f, Object... a) { }
        public void warn(String f, Object... a) { }
        public void debug(String f, Object... a) { }
        public void error(String m, Throwable t) { }
    };

    @Test
    void parsesDefinitionWithoutMinecraft() {
        String cfg = """
            DisplayName = AH-64D Apache Longbow
            MaxHp = 180
            CameraPosition = 0.0, 1.1, 4.5
            """;

        TestManager mgr = new TestManager();
        boolean ok = mgr.load(memSource("helicopters", "ah-64.txt", cfg), NOP, "helicopters");

        assertTrue(ok);
        assertEquals(1, mgr.getMap().size());
        TestInfo info = (TestInfo) mgr.getMap().get("ah-64");
        assertNotNull(info);
        assertEquals("AH-64D Apache Longbow", info.displayName);
        assertEquals(180, info.maxHp);
        assertEquals(new Vec3d(0.0, 1.1, 4.5), info.camera);
    }
}
