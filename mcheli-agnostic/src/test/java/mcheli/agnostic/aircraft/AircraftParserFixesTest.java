package mcheli.agnostic.aircraft;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import mcheli.agnostic.helicopter.MCH_HeliInfo;
import mcheli.agnostic.spi.Logger;
import mcheli.agnostic.spi.ModelHandle;
import mcheli.agnostic.spi.ResourceSource;
import mcheli.agnostic.vehicle.MCH_VehicleInfo;
import mcheli.agnostic.weapon.MCH_WeaponInfoManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code MCH_AircraftInfo} parser behaviour, in three groups:
 * <ul>
 *   <li>five 1.7.10 parser bugs the port used to carry over verbatim, now fixed (see the comment at each fix site):
 *       every-bay weapon-bay validation, unusable crawler-track lines, the bounding-box forward extent, {@code #RRGGBB}
 *       colour literals and the per-line recipe kind. Each of these tests FAILS against the reference behaviour;</li>
 *   <li>the restored reference {@code AddWeapon} weapon-type gate (MCH_AircraftInfo:790), which the port had dropped;</li>
 *   <li>two reference quirks pinned ON PURPOSE as kept for parity, so these two tests PASS against the reference:
 *       rotation parts named {@code "rotpart" + partThrottle.size()} (1.7.10 pack models are authored against those
 *       names) and roll limits seeded from the pitch getters (MCH_AircraftInfo:243-244, flight parity). Both were
 *       briefly "fixed" and then reverted; a test changing them must change the reference-parity decision first.</li>
 * </ul>
 */
class AircraftParserFixesTest {

    static final Logger NOP = new Logger() {
        public void info(String f, Object... a) { }
        public void warn(String f, Object... a) { }
        public void debug(String f, Object... a) { }
        public void error(String m, Throwable t) { }
    };

    /** In-memory ResourceSource: one directory, file name -> content. */
    static ResourceSource memSource(String dir, String file, String content) {
        return new ResourceSource() {
            @Override public List<String> list(String d) { return d.equals(dir) ? List.of(file) : List.of(); }
            @Override public Reader openUtf8(String path) { return path.equals(dir + "/" + file) ? new StringReader(content) : null; }
            @Override public boolean exists(String path) { return path.equals(dir + "/" + file); }
            @Override public ModelHandle loadModel(String path) { return null; }
        };
    }

    // The weapon registry is a process-wide singleton: start and end every test with it unloaded.
    @BeforeEach
    @AfterEach
    void unloadWeapons() {
        MCH_WeaponInfoManager.getInstance().clear();
    }

    private static MCH_HeliInfo heli(String name) {
        MCH_HeliInfo h = new MCH_HeliInfo(name);
        h.loadItemData("addseat", "0, 0, 0");
        return h;
    }

    @Test
    void weaponBayValidationChecksEveryBay() throws Exception {
        MCH_HeliInfo h = heli("bays");
        h.loadItemData("addweapon", "gun1, 0, 0, 0");
        h.loadItemData("addpartweaponbay", "nosuch_a, 0, 0, 0, 1, 0, 0");
        h.loadItemData("addpartweaponbay", "nosuch_b, 0, 0, 0, 1, 0, 0");
        h.loadItemData("addpartweaponbay", "gun1, 0, 0, 0, 1, 0, 0");
        h.isValidData();

        // reference: removing wb0 slid wb1 into slot 0, which the i++ then skipped, so wb1 survived with no weapons
        assertEquals(1, h.partWeaponBay.size());
        assertEquals("wb2", h.partWeaponBay.get(0).modelName);
        assertArrayEquals(new Integer[]{0}, h.partWeaponBay.get(0).weaponIds);
    }

    @Test
    void unusableCrawlerTrackLinesAreIgnored() {
        MCH_HeliInfo h = heli("tracks");
        // fewer than 3 fields: the reference threw ArrayIndexOutOfBounds and the whole file failed to load
        assertDoesNotThrow(() -> h.loadItemData("addcrawlertrack", "false, 0.5"));
        // only 3 points: the reference added a null track (the renderer then dereferenced it)
        h.loadItemData("addcrawlertrack", "false, 0.5, 1, 0/0, 1/0, 1/1");
        // a point that is not x/y
        assertDoesNotThrow(() -> h.loadItemData("addcrawlertrack", "false, 0.5, 1, 0/0, 1/0, 1/1, 5"));
        assertTrue(h.partCrawlerTrack.isEmpty());

        h.loadItemData("addcrawlertrack", "false, 0.5, 1, 0/0, 1/0, 1/1, 0/1");
        assertEquals(1, h.partCrawlerTrack.size());
        assertEquals("crawler_track0", h.partCrawlerTrack.get(0).modelName);
        assertFalse(h.partCrawlerTrack.get(0).lp.isEmpty());
    }

    @Test
    void rotationPartsKeepTheReferenceThrottleBasedNames() {
        MCH_HeliInfo h = heli("rot");
        h.loadItemData("addpartthrottle", "0, 0, 0, 1, 0, 0, 30");
        h.loadItemData("addpartrotation", "0, 0, 0, 0, 1, 0, 1.0");
        h.loadItemData("addpartrotation", "0, 1, 0, 0, 1, 0, 2.0");
        // reference: "rotpart" + partThrottle.size(); 1.7.10 pack models are authored against these names
        assertEquals("rotpart1", h.partRotPart.get(0).modelName);
        assertEquals("rotpart1", h.partRotPart.get(1).modelName);
    }

    @Test
    void boundingBoxMidpointUsesTheForwardExtent() throws Exception {
        MCH_HeliInfo h = heli("bb");
        h.loadItemData("boundingbox", "0, 0, 5, 2, 1"); // z spans 4..6
        h.isValidData();
        assertEquals(-1.0f, h.bbZmin, 1e-6f);
        assertEquals(6.0f, h.bbZmax, 1e-6f); // reference Math.min kept the 1.0 seed
        assertEquals(2.5f, h.bbZ, 1e-6f);
    }

    @Test
    void rollLimitsAreSeededFromThePitchGettersAsInTheReference() {
        // reference MCH_AircraftInfo:243-244 seeds roll from the pitch getters; kept for flight parity
        MCH_HeliInfo h = new MCH_HeliInfo("h");
        assertEquals(h.getMinRotationPitch(), h.minRotationRoll, 0.0f);
        assertEquals(h.getMaxRotationPitch(), h.maxRotationRoll, 0.0f);

        MCH_VehicleInfo v = new MCH_VehicleInfo("v");
        assertEquals(v.getMinRotationPitch(), v.minRotationRoll, 0.0f);
        assertEquals(v.getMaxRotationPitch(), v.maxRotationRoll, 0.0f);
    }

    @Test
    void hashColourLiteralsParse() {
        MCH_HeliInfo h = heli("light");
        assertEquals(0xFF8000, h.hex2dec("#FF8000"));
        assertEquals(0x10, h.hex2dec("0x10"));
        assertEquals(0xff, h.hex2dec("ff"));
        // reference: "#..." became "0x#..." and the NumberFormatException dropped the whole vehicle file
        assertDoesNotThrow(() -> h.loadItemData("addsearchlight", "0, 0, 0, #FFFFFF, #000000, 10, 2"));
        assertEquals(1, h.searchLights.size());
        assertEquals(0xFFFFFF, h.searchLights.get(0).colorStart);
    }

    @Test
    void recipeKindIsTrackedPerLine() {
        MCH_HeliInfo h = heli("recipes");
        h.loadItemData("addrecipe", "\"X\", X, iron_ingot");
        h.loadItemData("addshapelessrecipe", "iron_ingot, gold_ingot");
        assertEquals(2, h.recipeString.size());
        assertTrue(h.isShapedRecipe(0));
        assertFalse(h.isShapedRecipe(1));
        assertFalse(h.isShapedRecipe, "the legacy flag still holds the LAST directive's kind, as in the reference");
    }

    @Test
    void addWeaponDropsUnknownTypesOnceWeaponsAreLoaded() throws Exception {
        MCH_WeaponInfoManager.getInstance().load(memSource("weapons", "gun1.txt", "Type = MachineGun1\n"), NOP, "weapons");
        assertTrue(MCH_WeaponInfoManager.isLoaded());
        assertTrue(MCH_WeaponInfoManager.contains("gun1"));

        MCH_HeliInfo h = heli("filter");
        h.loadItemData("addweapon", "gun1, 0, 0, 0");
        h.loadItemData("addweapon", "targeting_pod1, 0, 0, 0"); // ah-64.txt:67 names a weapon that has no file
        h.loadItemData("addweapon", "GUN1, 1, 0, 0");
        h.isValidData();

        // reference MCH_AircraftInfo:790: the unknown mount never takes a slot, so the two gun1 lines group together
        assertEquals(1, h.getWeaponNum());
        assertEquals("gun1", h.weaponSetList.get(0).type);
        assertEquals(2, h.weaponSetList.get(0).weapons.size());
        assertEquals(-1, h.getWeaponIdByName("targeting_pod1"));
    }

    @Test
    void addWeaponKeepsEveryTypeBeforeWeaponsLoad() {
        assertFalse(MCH_WeaponInfoManager.isLoaded());
        MCH_HeliInfo h = heli("isolated");
        h.loadItemData("addweapon", "gun1, 0, 0, 0");
        h.loadItemData("addweapon", "targeting_pod1, 0, 0, 0");
        h.loadItemData("addweapon", "gun1, 1, 0, 0");
        assertEquals(3, h.getWeaponNum());
    }
}
