package mcheli.agnostic.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import org.junit.jupiter.api.Test;

/**
 * Pins the weapon fire-mode switch against the reference {@code MCH_WeaponBase.switchMode} /
 * {@code MCH_WeaponSet.switchMode} and the per-type {@code getName()} suffixes.
 */
class WeaponModeTest {

    private static MCH_WeaponInfo info(String name, String type, int modeNum, int explosion) {
        MCH_WeaponInfo w = new MCH_WeaponInfo(name);
        w.loadItemData("type", type);
        w.loadItemData("modenum", Integer.toString(modeNum));
        w.loadItemData("explosion", Integer.toString(explosion));
        w.loadItemData("delay", "2");
        return w;
    }

    private static WeaponSlot slot(MCH_WeaponInfo info) {
        List<MCH_AircraftInfo.Weapon> mounts = new ArrayList<>();
        mounts.add(new MCH_AircraftInfo.Weapon(
            0f, 0f, 0f, 0f, 0f, true, 0, 0f, -360f, 360f, -90f, 90f, false));
        return new WeaponSlot(info.name, info, mounts);
    }

    @Test
    void m230CyclesNormalAndHe() {
        WeaponSlot s = slot(info("m230", "MachineGun2", 2, 1));
        assertTrue(WeaponSlot.hasSwitchableModes(s.info));
        assertEquals(0, s.currentMode());
        assertEquals("", WeaponSlot.modeSuffix(s.info, s.selectedMode()));
        assertTrue(s.switchMode());
        assertEquals(1, s.currentMode());
        assertEquals(" [HE]", WeaponSlot.modeSuffix(s.info, s.selectedMode()));
        assertTrue(s.switchMode());
        assertEquals(0, s.currentMode()); // wraps
    }

    @Test
    void switchBlocksTheTriggerBriefly() {
        WeaponSlot s = slot(info("m230", "MachineGun2", 2, 1));
        assertTrue(s.canFire());
        s.switchMode();
        assertFalse(s.canFire());
        for (int i = 0; i < WeaponSlot.MODE_SWITCH_DELAY; i++) {
            s.tick();
        }
        assertTrue(s.canFire());
    }

    @Test
    void nonExplosiveMachineGun2HasNoModes() {
        // reference MCH_WeaponMachineGun2.modifyParameters: explosionPower == 0 -> numMode = 0
        WeaponSlot s = slot(info("gun", "MachineGun2", 2, 0));
        assertFalse(WeaponSlot.hasSwitchableModes(s.info));
        assertFalse(s.switchMode());
        assertEquals(0, s.currentMode());
    }

    @Test
    void singleModeWeaponNeverSwitches() {
        WeaponSlot s = slot(info("105mm", "MachineGun2", 1, 3));
        assertFalse(WeaponSlot.hasSwitchableModes(s.info));
        assertFalse(s.switchMode());
        assertTrue(s.canFire()); // no switch delay when nothing changed
    }

    @Test
    void fixModeWins() {
        MCH_WeaponInfo i = info("agm114tv", "TVMissile", 2, 4);
        i.loadItemData("fixmode", "1");
        WeaponSlot s = slot(i);
        assertFalse(WeaponSlot.hasSwitchableModes(i));
        assertFalse(s.switchMode());
        assertEquals(1, s.currentMode());
        assertEquals("", WeaponSlot.modeSuffix(i, s.selectedMode()));
    }

    @Test
    void outOfRangeFixModeIsIgnored() {
        // reference checkData: FixMode >= ModeNum is reset to 0 — an AT missile with FixMode 1 but no ModeNum flies direct
        MCH_WeaponInfo i = info("atm", "ATMissile", 0, 2);
        i.loadItemData("fixmode", "1");
        WeaponSlot s = slot(i);
        assertEquals(0, WeaponSlot.fixModeOf(i));
        assertEquals(0, s.currentMode());
        assertEquals("", WeaponSlot.modeSuffix(i, s.selectedMode()));
    }

    @Test
    void suffixesPerType() {
        assertEquals(" [HEIAP]", WeaponSlot.modeSuffix(info("crv7", "Rocket", 2, 3), 1));
        assertEquals("", WeaponSlot.modeSuffix(info("crv7", "Rocket", 2, 3), 0));
        assertEquals(" [TA]", WeaponSlot.modeSuffix(info("agm114", "ATMissile", 2, 4), 1));
        assertEquals("", WeaponSlot.modeSuffix(info("agm114", "ATMissile", 2, 4), 0));
        assertEquals(" [TV]", WeaponSlot.modeSuffix(info("agm114tv", "TVMissile", 2, 4), 0));
        assertEquals("", WeaponSlot.modeSuffix(info("agm114tv", "TVMissile", 2, 4), 1));
        assertEquals("", WeaponSlot.modeSuffix(info("hydra", "MachineGun1", 2, 0), 1));
    }
}
