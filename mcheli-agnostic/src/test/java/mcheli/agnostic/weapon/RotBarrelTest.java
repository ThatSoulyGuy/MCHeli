package mcheli.agnostic.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.helicopter.MCH_HeliInfo;
import mcheli.agnostic.plane.MCP_PlaneInfo;
import mcheli.agnostic.tank.MCH_TankInfo;
import mcheli.agnostic.vehicle.MCH_VehicleInfo;
import org.junit.jupiter.api.Test;

/**
 * {@link RotBarrel} against a private oracle: a verbatim copy of the dependent code it replaces (AbstractMchVehicle
 * {@code firesRotBarrel} and {@code hasType1Part}, baseline lines 5236-5267) with {@code weaponHostInfo()} and the
 * slot turned into parameters ({@code slot.weaponName} is the only slot field it read). Compared on seeded random
 * configs of all four kinds (null part entries, null and mixed-case names, nested vehicle part trees) and on parsed
 * fixtures.
 */
class RotBarrelTest {

    /** Baseline :5236-5256, verbatim except for the parameters. */
    private static boolean oracleFiresRotBarrel(MCH_AircraftInfo weaponHostInfo, String slotWeaponName) {
        MCH_AircraftInfo info = weaponHostInfo;
        if (info == null) {
            return false;
        }
        if (info.partWeapon != null) {
            for (MCH_AircraftInfo.PartWeapon pw : info.partWeapon) {
                if (pw != null && pw.rotBarrel && pw.name != null) {
                    for (String nm : pw.name) {
                        if (nm != null && nm.equalsIgnoreCase(slotWeaponName)) {
                            return true;
                        }
                    }
                }
            }
        }
        if (info instanceof mcheli.agnostic.vehicle.MCH_VehicleInfo vi) {
            return oracleHasType1Part(vi.partList);
        }
        return false;
    }

    /** Baseline :5258-5267, verbatim. */
    private static boolean oracleHasType1Part(java.util.List<mcheli.agnostic.vehicle.MCH_VehicleInfo.VPart> parts) {
        if (parts != null) {
            for (mcheli.agnostic.vehicle.MCH_VehicleInfo.VPart p : parts) {
                if (p != null && (p.type == 1 || oracleHasType1Part(p.child))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final String[] NAMES = {"gau-8", "GAU-8", "Gau-8", "m61a1", "hydra70", "m134", "2a42", ""};

    private static String name(Random r) {
        return r.nextInt(12) == 0 ? null : NAMES[r.nextInt(NAMES.length)];
    }

    private static MCH_AircraftInfo.PartWeapon partWeapon(Random r) {
        String[] names = null;
        if (r.nextInt(8) != 0) {
            names = new String[r.nextInt(4)];
            for (int i = 0; i < names.length; i++) {
                names[i] = name(r);
            }
        }
        return new MCH_AircraftInfo.PartWeapon(names, r.nextBoolean(), r.nextBoolean(), false, true, true,
            0.0F, 0.0F, 0.0F, "weapon", 0.0F, 0.0F, 0.0F, 0.0F, r.nextBoolean());
    }

    private static List<MCH_VehicleInfo.VPart> partTree(Random r, int depth) {
        if (r.nextInt(6) == 0) {
            return null;
        }
        List<MCH_VehicleInfo.VPart> parts = new ArrayList<>();
        int n = r.nextInt(4);
        for (int i = 0; i < n; i++) {
            if (r.nextInt(10) == 0) {
                parts.add(null);
                continue;
            }
            // Mostly type 0/2 parts, so a type 1 is rare and often deep.
            int type = r.nextInt(7) == 0 ? 1 : r.nextBoolean() ? 0 : 2;
            MCH_VehicleInfo.VPart p = new MCH_VehicleInfo.VPart(0.0F, 0.0F, 0.0F, "part", true, true, true, type, 0.0F);
            p.child = depth < 4 ? partTree(r, depth + 1) : null;
            parts.add(p);
        }
        return parts;
    }

    private static MCH_AircraftInfo randomInfo(Random r) {
        MCH_AircraftInfo info = switch (r.nextInt(5)) {
            case 0 -> new MCH_HeliInfo("h");
            case 1 -> new MCP_PlaneInfo("p");
            case 2 -> new MCH_TankInfo("t");
            case 3 -> new MCH_VehicleInfo("v");
            default -> null;
        };
        if (info == null) {
            return null;
        }
        if (r.nextInt(10) == 0) {
            info.partWeapon = null;
        } else {
            int n = r.nextInt(5);
            for (int i = 0; i < n; i++) {
                info.partWeapon.add(r.nextInt(10) == 0 ? null : partWeapon(r));
            }
        }
        if (info instanceof MCH_VehicleInfo vi) {
            vi.partList = partTree(r, 0);
        }
        return info;
    }

    @Test
    void matchesTheOracleOnSeededRandomConfigs() {
        for (long seed : new long[] {1L, 42L, 20261111L, -7L, 0xC0FFEEL}) {
            Random r = new Random(seed);
            int yes = 0;
            for (int step = 0; step < 12_000; step++) {
                MCH_AircraftInfo info = randomInfo(r);
                for (int k = 0; k < 3; k++) {
                    String weapon = name(r);
                    boolean expected = oracleFiresRotBarrel(info, weapon);
                    assertEquals(expected, RotBarrel.firesRotBarrel(info, weapon),
                        "seed " + seed + " step " + step + " weapon " + weapon);
                    yes += expected ? 1 : 0;
                }
                if (info instanceof MCH_VehicleInfo vi) {
                    assertEquals(oracleHasType1Part(vi.partList), RotBarrel.hasType1Part(vi.partList),
                        "hasType1Part seed " + seed + " step " + step);
                }
            }
            assertTrue(yes > 1000 && yes < 30_000, "both outcomes covered on seed " + seed + ": " + yes);
        }
    }

    @Test
    void aParsedRotBarrelPartWeaponNamesItsWeaponInAnyCase() {
        // The a-10's gun (a-10.txt:36) and a slash-named rotary pair; the parser lowercases the names.
        MCP_PlaneInfo a10 = new MCP_PlaneInfo("a-10");
        a10.loadItemData("addpartrotweapon", "GAU-8,   false, true,  true,    0,    2.077,  3.8802");
        a10.loadItemData("addpartweaponmissile", "aim9_l,  false, false, false,  -6.25, 1.40,  -3.13");
        a10.loadItemData("addpartturretrotweapon", "m134_l / M134_R, false, true, true, 0, 1, 2");
        assertTrue(RotBarrel.firesRotBarrel(a10, "gau-8"));
        assertTrue(RotBarrel.firesRotBarrel(a10, "GAU-8"));
        assertTrue(RotBarrel.firesRotBarrel(a10, "m134_r"));
        assertTrue(RotBarrel.firesRotBarrel(a10, "M134_L"));
        assertFalse(RotBarrel.firesRotBarrel(a10, "aim9_l"), "a non-rot part weapon never arms the latch");
        assertFalse(RotBarrel.firesRotBarrel(a10, "hydra70"), "a weapon with no part never arms the latch");
        assertFalse(RotBarrel.firesRotBarrel(a10, null));
        assertEquals(oracleFiresRotBarrel(a10, "gau-8"), RotBarrel.firesRotBarrel(a10, "gau-8"));
    }

    @Test
    void aNonRotPartWeaponOfTheSameNameDoesNotCount() {
        // The m1a2's main gun (m1a2.txt:98) is a plain AddPartWeapon.
        MCH_TankInfo m1 = new MCH_TankInfo("m1a2");
        m1.loadItemData("addpartweapon",
            "rehinmetall_apfsds / rehinmetall_he, false, true, false,  0.00, 2.10, 0.00, 0");
        assertFalse(RotBarrel.firesRotBarrel(m1, "rehinmetall_apfsds"));
        assertFalse(RotBarrel.firesRotBarrel(m1, "rehinmetall_he"));
    }

    @Test
    void aVehicleWithANestedType1PartArmsOnAnyFire() {
        MCH_VehicleInfo v = new MCH_VehicleInfo("phalanx");
        v.loadItemData("addpart", "true, true, false, 0, 0, 1, 0");
        v.loadItemData("addchildpart", "true, false, true, 2, 0, 1.5, 0");
        assertFalse(RotBarrel.hasType1Part(v.partList));
        assertFalse(RotBarrel.firesRotBarrel(v, "m61a1"));
        // A type-1 grandchild, below a type-2 child.
        v.partList.get(0).child.get(0).child = new ArrayList<>(List.of(
            new MCH_VehicleInfo.VPart(0.0F, 2.0F, 0.0F, "barrel", true, false, false, 1, 0.0F)));
        assertTrue(RotBarrel.hasType1Part(v.partList));
        assertTrue(RotBarrel.firesRotBarrel(v, "m61a1"));
        assertTrue(RotBarrel.firesRotBarrel(v, null), "any fire, whatever the weapon");
        // A parsed type-1 child.
        MCH_VehicleInfo w = new MCH_VehicleInfo("emplacement");
        w.loadItemData("addpart", "true, true, false, 0, 0, 1, 0");
        w.loadItemData("addchildpart", "true, false, true, 1, 0, 1.5, 0");
        assertTrue(RotBarrel.firesRotBarrel(w, "anything"));
        assertFalse(RotBarrel.hasType1Part(null));
    }

    @Test
    void nullEntriesAndANullConfigAreSkipped() {
        assertFalse(RotBarrel.firesRotBarrel(null, "gau-8"), "no config");
        MCH_HeliInfo h = new MCH_HeliInfo("h");
        h.partWeapon.add(null);
        h.partWeapon.add(new MCH_AircraftInfo.PartWeapon(null, true, false, false, true, true,
            0.0F, 0.0F, 0.0F, "weapon0", 0.0F, 0.0F, 0.0F, 0.0F, false));
        h.partWeapon.add(new MCH_AircraftInfo.PartWeapon(new String[] {null, "gau-8"}, true, false, false, true, true,
            0.0F, 0.0F, 0.0F, "weapon1", 0.0F, 0.0F, 0.0F, 0.0F, false));
        assertTrue(RotBarrel.firesRotBarrel(h, "Gau-8"), "a null name before a matching one is skipped");
        assertFalse(RotBarrel.firesRotBarrel(h, "m230"));
        h.partWeapon = null;
        assertFalse(RotBarrel.firesRotBarrel(h, "gau-8"), "no part-weapon list");
    }
}
