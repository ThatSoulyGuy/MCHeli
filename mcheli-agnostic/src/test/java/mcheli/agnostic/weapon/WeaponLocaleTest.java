package mcheli.agnostic.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import mcheli.agnostic.info.MCH_BaseInfo;
import mcheli.agnostic.info.MCH_InfoManagerBase;
import mcheli.agnostic.spi.Logger;
import mcheli.agnostic.spi.ModelHandle;
import mcheli.agnostic.spi.ResourceSource;
import mcheli.agnostic.spi.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The weapon parser and the weapon-type switches under a Turkish default locale, where {@code "I".toLowerCase()} is
 * the dotless {@code "\u0131"}: a pack spelling {@code AAMISSILE} must still reach the {@code aamissile} branch. Every
 * lookup name is lower-cased with {@link Locale#ROOT} (the reference used the default locale).
 */
class WeaponLocaleTest {

    private Locale saved;

    @BeforeEach
    void turkish() {
        this.saved = Locale.getDefault();
        Locale.setDefault(Locale.of("tr", "TR"));
        assertEquals("aam\u0131ss\u0131le", "AAMISSILE".toLowerCase(), "the default locale maps I to the dotless i");
    }

    @AfterEach
    void restore() {
        Locale.setDefault(this.saved);
    }

    /** A weapon manager of its own: the shared singleton would mark the weapons loaded for every later test. */
    private static final class Manager extends MCH_InfoManagerBase {
        final Map<String, MCH_BaseInfo> map = new LinkedHashMap<>();
        @Override public MCH_BaseInfo newInfo(String name) { return new MCH_WeaponInfo(name); }
        @Override public Map getMap() { return map; }
    }

    private static final Logger QUIET = new Logger() {
        public void info(String f, Object... a) { }
        public void warn(String f, Object... a) { throw new AssertionError(String.format(f, a)); }
        public void debug(String f, Object... a) { }
        public void error(String m, Throwable t) { throw new AssertionError(m, t); }
    };

    private static ResourceSource weapons(Map<String, String> files) {
        return new ResourceSource() {
            @Override public List<String> list(String d) { return d.equals("weapons") ? new ArrayList<>(files.keySet()) : List.of(); }
            @Override public Reader openUtf8(String path) {
                String f = path.substring(path.indexOf('/') + 1);
                return path.startsWith("weapons/") && files.containsKey(f) ? new StringReader(files.get(f)) : null;
            }
            @Override public boolean exists(String path) { return openUtf8(path) != null; }
            @Override public ModelHandle loadModel(String path) { return null; }
        };
    }

    @Test
    void weaponFileWithUpperCaseIParsesToTheRootLowerCaseNames() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("AIM-9L.TXT", String.join("\n",
            "DisplayName = AIM-9L",
            "TYPE = AAMISSILE",
            "Group = MISSILE",
            "Sound = MISSILE_SND",
            "Sight = MISSILESIGHT",
            "ModelBullet = AIM-9",
            "ModelBomblet = BOMBLET_MINI",
            "TrajectoryParticle = FIREWORKSSPARK",
            "SetCartridge = CARTRIDGE_IR, 0.1",
            "DamageFactor = HELICOPTER, 0.5",
            "Item = 1, IRON_INGOT",
            "DispenseItem = FLINT_AND_STEEL",
            ""));
        Manager mgr = new Manager();
        assertTrue(mgr.load(weapons(files), QUIET, "weapons"));
        MCH_WeaponInfo w = (MCH_WeaponInfo) mgr.getMap().get("aim-9l");
        assertNotNull(w, "the file name lower-cases with Locale.ROOT too");

        assertEquals("aamissile", w.type);
        assertEquals("missile", w.group);
        assertEquals("missile_snd", w.soundFileName);
        assertEquals(MCH_SightType.LOCK, w.sight);
        assertEquals("aim-9", w.bulletModelName);
        assertEquals("bomblet_mini", w.bombletModelName);
        assertEquals("fireworksspark", w.trajectoryParticleName);
        assertNotNull(w.cartridge);
        assertEquals("cartridge_ir", w.cartridge.name);
        assertEquals(0.5F, w.damageFactor.getDamageFactor(Role.HELICOPTER), 1e-6F, "HELICOPTER reaches its branch");
        assertEquals("iron_ingot", w.roundItems.get(0).itemName);
        assertEquals("flint_and_steel", w.dispenseItemName);
    }

    @Test
    void weaponTypeSwitchesIgnoreTheDefaultLocale() {
        assertTrue(VehicleWeapons.isFireable("AAMISSILE"));
        assertTrue(VehicleWeapons.isFireable("MACHINEGUN1"));
        assertTrue(VehicleWeapons.isFireable("DISPENSER"));
        // Not a locale check (the pod falls to the default branch under either mapping): it documents the exclusion.
        assertFalse(VehicleWeapons.isFireable("targetingpod"));
        assertTrue(MCH_WeaponBallistics.isBulletOrRocket("MACHINEGUN2"));
        assertTrue(MCH_WeaponBallistics.isTrailingType("TVMISSILE"));
        assertEquals(3.5F, MCH_WeaponBallistics.trailSizeMultiplier("AAMISSILE"));
        assertEquals(5.0F, MCH_WeaponBallistics.trailSizeMultiplier("ASMISSILE"));
        assertEquals("atmissile", MCH_WeaponBallistics.defaultBulletModel("ATMISSILE"));
        assertEquals(" [HE]", MCH_WeaponMode.suffix("MACHINEGUN2", 1));
    }
}
