package mcheli.agnostic.aircraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mcheli.agnostic.info.MCH_BaseInfo;
import mcheli.agnostic.info.MCH_InfoManagerBase;
import mcheli.agnostic.spi.ItemHandle;
import mcheli.agnostic.spi.Logger;
import mcheli.agnostic.spi.ModelHandle;
import mcheli.agnostic.spi.ResourceSource;
import mcheli.agnostic.value.Vec3d;
import org.junit.jupiter.api.Test;

/**
 * Drives a real-shaped, multi-directive helicopter definition end-to-end through the coerced
 * {@link MCH_AircraftInfo} — seats, weapon-set grouping, camera position, bounding box, HUD-name
 * padding, flare, wheels and an entity rack — proving the 1738-line linchpin parses with ZERO Minecraft.
 */
class AircraftInfoParsingTest {

    /** Minimal concrete aircraft info: fills the abstract seams with test stubs. */
    static final class TestAircraftInfo extends MCH_AircraftInfo {
        TestAircraftInfo(String name) { super(name); }
        @Override public ItemHandle getItem() { return () -> "test_heli"; }
        @Override public String getDirectoryName() { return "helicopters"; }
        @Override public String getKindName() { return "Heli"; }
        @Override public String getDefaultHudName(int i) { return "hud" + i; }
    }

    static final class TestAircraftManager extends MCH_InfoManagerBase {
        final Map<String, MCH_BaseInfo> map = new LinkedHashMap<>();
        @Override public MCH_BaseInfo newInfo(String name) { return new TestAircraftInfo(name); }
        @Override public Map getMap() { return map; }
    }

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
    void parsesFullAircraftDefinition() {
        String cfg = """
            DisplayName = Test Apache
            MaxHp = 250
            Speed = 2.0
            Width = 3.5
            AddSeat = 0, 1.0, 2.0
            AddGunnerSeat = 0, 0.5, -1.0, 0, 1.2, 0.5
            AddWeapon = coax, 0, 0, 1
            AddWeapon = coax, 0.5, 0, 1
            AddTurretWeapon = cannon, 0, 0.2, 2
            CameraPosition = 0, 1.5, 3
            BoundingBox = 0, 2, 0, 3, 4
            HUD = cockpit
            FlareType = 1, 2
            AddPartWheel = 1, -1, 2
            AddRack = zombie/skeleton, 0, 0, -2, 0, 1, -3, 6, 20
            """;

        TestAircraftManager mgr = new TestAircraftManager();
        boolean ok = mgr.load(memSource("helicopters", "test-heli.txt", cfg), NOP, "helicopters");

        assertTrue(ok, "manager.load should succeed");
        MCH_AircraftInfo info = (MCH_AircraftInfo) mgr.getMap().get("test-heli");
        assertNotNull(info, "definition should be registered under its file name");

        // scalars (Speed is clamped to getMaxSpeed()=0.8)
        assertEquals("Test Apache", info.displayName);
        assertEquals(250, info.maxHp);
        assertEquals(0.8f, info.speed, 1e-6f);
        assertEquals(3.5f, info.bodyWidth, 1e-6f);

        // seats: 1 pilot + 1 gunner counted as seats; the rack is appended afterward
        assertEquals(2, info.getNumSeat());
        assertEquals(1, info.getNumRack());
        assertEquals(3, info.getNumSeatAndRack());
        assertInstanceOf(MCH_SeatRackInfo.class, info.seatList.get(2));
        assertArrayEquals(new String[]{"zombie", "skeleton"}, ((MCH_SeatRackInfo) info.seatList.get(2)).names);

        // weapon-set grouping: two "coax" weapons collapse into one set; "cannon" is a second set
        assertEquals(2, info.getWeaponNum());
        assertEquals("coax", info.weaponSetList.get(0).type);
        assertEquals(2, info.weaponSetList.get(0).weapons.size());
        assertEquals("cannon", info.weaponSetList.get(1).type);
        assertEquals(1, info.weaponSetList.get(1).weapons.size());

        // camera position (Vec3d coercion)
        assertEquals(1, info.cameraPosition.size());
        assertEquals(new Vec3d(0.0, 1.5, 3.0), info.cameraPosition.get(0).pos);

        // bounding box drives marker + bbZ (note: the reference uses min() for bbZmax — preserved)
        assertEquals(4.0f, info.markerHeight, 1e-6f);
        assertEquals(-0.25f, info.bbZ, 1e-6f);

        // HUD names: one parsed name, padded to seat count with getDefaultHudName(1)
        assertEquals(List.of("cockpit", "hud1"), info.hudList);
        assertEquals("tv_missile", info.hudTvMissile);

        // flare + wheels + defaults
        assertTrue(info.haveFlare());
        assertArrayEquals(new int[]{1, 2}, info.flare.types);
        assertEquals(1, info.partWheel.size());
        assertEquals(Vec3d.ZERO, info.turretPosition);

        // the concrete seam still resolves
        assertEquals("test_heli", info.getItem().name());
    }
}
