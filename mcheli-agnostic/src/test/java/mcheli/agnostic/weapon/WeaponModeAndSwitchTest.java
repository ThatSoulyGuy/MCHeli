package mcheli.agnostic.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import org.junit.jupiter.api.Test;

/**
 * Weapon modes ({@code MCH_WeaponBase.switchMode} + the per-type {@code getName}/{@code shot} meaning), the switch and
 * mode lockouts ({@code MCH_WeaponSet.onSwitchWeapon}/{@code switchMode}) and the reload a switch triggers
 * ({@code MCH_EntityAircraft.switchWeapon} -> {@code MCH_WeaponSet.reload}). Zero Minecraft.
 */
class WeaponModeAndSwitchTest {

    private static MCH_WeaponInfo info(String name, String type, String... kv) {
        MCH_WeaponInfo w = new MCH_WeaponInfo(name);
        w.loadItemData("type", type);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            w.loadItemData(kv[i], kv[i + 1]);
        }
        return w;
    }

    private static WeaponSlot slot(MCH_WeaponInfo info, int seat) {
        List<MCH_AircraftInfo.Weapon> mounts = new ArrayList<>();
        mounts.add(new MCH_AircraftInfo.Weapon(0f, 0f, 0f, 0f, 0f, true, seat, 0f, -360f, 360f, -90f, 90f, false));
        return new WeaponSlot(info.name, info, mounts);
    }

    private static int ticksUntilFire(WeaponSlot s, int max) {
        for (int t = 0; t < max; t++) {
            if (s.canFire()) {
                return t;
            }
            s.tick();
        }
        return -1;
    }

    @Test
    void machineGun2CyclesBetweenTheApAndHeRound() {
        MCH_WeaponInfo w = info("m230", "MachineGun2", "explosion", "2", "modenum", "2", "displayname", "M230");
        WeaponSlot s = slot(w, 0);
        assertEquals(2, s.numMode());
        assertEquals(0, s.mode());
        assertEquals("M230", s.displayName());
        assertEquals(0, s.shotInfo().explosion, "mode 0 of a ModeNum>=2 cannon fires the non-explosive round");
        assertEquals(2, w.explosion, "the shared definition is untouched");

        assertTrue(s.switchMode());
        assertEquals(1, s.mode());
        assertEquals("M230 [HE]", s.displayName());
        assertEquals(2, s.shotInfo().explosion, "mode 1 is the HE round");

        assertTrue(s.switchMode());
        assertEquals(0, s.mode(), "modes wrap");
    }

    @Test
    void singleModeAndNonExplosiveCannonsDoNotSwitch() {
        WeaponSlot single = slot(info("105mm", "MachineGun2", "explosion", "3", "modenum", "1"), 0);
        assertFalse(single.switchMode());
        assertEquals(3, single.shotInfo().explosion, "ModeNum < 2: the round always explodes");
        assertSame(single.info, single.shotInfo(), "no variant is made when nothing changes");

        WeaponSlot inert = slot(info("mg", "MachineGun2", "modenum", "2"), 0);
        assertEquals(0, inert.numMode(), "MachineGun2.modifyParameters: no explosion, no HE mode");
        assertFalse(inert.switchMode());
    }

    @Test
    void fixModeWinsAndBlocksSwitching() {
        WeaponSlot s = slot(info("tv", "TVMissile", "modenum", "2", "fixmode", "1"), 0);
        assertEquals(1, s.mode());
        assertFalse(s.switchMode());
        assertEquals(1, s.mode());
        assertFalse(MCH_WeaponMode.isTvGuided(s.info, s.mode()));
    }

    @Test
    void rocketHeiapModeIsTheOnlyOneThatPierces() {
        WeaponSlot s = slot(info("crv7", "Rocket", "modenum", "2", "piercing", "3", "displayname", "CRV7"), 0);
        assertEquals(0, s.shotInfo().piercing);
        assertTrue(s.switchMode());
        assertEquals("CRV7 [HEIAP]", s.displayName());
        assertEquals(3, s.shotInfo().piercing);
    }

    @Test
    void missileModesMapToTheirGuidance() {
        MCH_WeaponInfo at = info("agm114", "ATMissile", "modenum", "2");
        assertFalse(MCH_WeaponMode.isTopAttack(at, 0));
        assertTrue(MCH_WeaponMode.isTopAttack(at, 1));
        assertEquals(" [TA]", MCH_WeaponMode.suffix("atmissile", 1));
        MCH_WeaponInfo tv = info("agm114tv", "TVMissile", "modenum", "2");
        assertTrue(MCH_WeaponMode.isTvGuided(tv, 0));
        assertFalse(MCH_WeaponMode.isTvGuided(tv, 1));
        assertEquals(" [TV]", MCH_WeaponMode.suffix("tvmissile", 0));
        assertEquals("", MCH_WeaponMode.suffix("tvmissile", 1));
    }

    @Test
    void modeChangeLocksTheTriggerFor15Ticks() {
        WeaponSlot s = slot(info("m230", "MachineGun2", "explosion", "2", "modenum", "2", "delay", "1"), 0);
        assertTrue(s.canFire());
        s.switchMode();
        assertTrue(s.inPreparation());
        assertEquals(WeaponSlot.MODE_LOCKOUT_TICKS, ticksUntilFire(s, 100));
    }

    @Test
    void switchingLocksTheNewWeaponAndKeepsALongerCooldown() {
        WeaponSlot quick = slot(info("gun", "MachineGun1", "delay", "1"), 0);
        quick.onSwitchedTo(false);
        assertTrue(quick.inPreparation(), "the lockout is a 'preparing' state for the HUD");
        assertEquals(WeaponSlot.SWITCH_LOCKOUT_TICKS, ticksUntilFire(quick, 100));

        WeaponSlot slow = slot(info("cannon", "MachineGun1", "delay", "60"), 0);
        slow.fireOneShot(false);              // countWait = 60
        slow.onSwitchedTo(false);             // a cooldown longer than the lockout becomes an equally long lockout
        assertEquals(60, ticksUntilFire(slow, 200));
    }

    @Test
    void switchingAwayReloadsAPartialMagazineAndCreativeRefillsOnSwitch() {
        MCH_WeaponInfo w = info("gun", "MachineGun1", "round", "5", "maxammo", "20", "delay", "0", "reloadtime", "8");
        WeaponSlot s = slot(w, 0);
        s.setRestAllAmmo(20);
        s.reloadMag();
        s.fireOneShot(false);
        assertEquals(4, s.magazine());
        s.reloadOnSwitchAway();
        assertTrue(s.reloading(), "a partial magazine reloads when the seat switches away");
        for (int t = 0; t < 8; t++) {
            s.tick();
        }
        assertEquals(5, s.magazine(), "the reload topped the magazine up from the reserve");
        assertEquals(19, s.totalAmmo(), "only the round fired was spent");

        WeaponSlot full = slot(w, 0);
        full.setRestAllAmmo(20);
        full.reloadMag();
        full.reloadOnSwitchAway();
        assertFalse(full.reloading(), "a full magazine does not reload");

        WeaponSlot dry = slot(w, 0);
        dry.onSwitchedTo(true);
        assertEquals(5, dry.magazine(), "an infinite-ammo operator switching to a weapon gets a full magazine");
    }

    @Test
    void weaponsOfOneGroupShareTheBarrelCooldown() {
        MCH_AircraftInfo ac = new mcheli.agnostic.tank.MCH_TankInfo("grouptank");
        MCH_WeaponInfoManager.getInstance().clear();
        ac.loadItemData("addweapon", "apfsds, 0,0,0, 0,0, true,0");
        ac.loadItemData("addweapon", "he, 0,0,0, 0,0, true,0");
        ac.loadItemData("addweapon", "coax, 0,0,0, 0,0, true,0");
        MCH_WeaponInfo ap = info("apfsds", "MachineGun2", "group", "MainGun", "delay", "60", "reloadtime", "100",
            "round", "1", "maxammo", "10");
        MCH_WeaponInfo he = info("he", "MachineGun2", "group", "MainGun", "delay", "60", "reloadtime", "100",
            "round", "1", "maxammo", "10");
        MCH_WeaponInfo coax = info("coax", "MachineGun1", "delay", "2");
        VehicleWeapons w = VehicleWeapons.build(ac,
            n -> "apfsds".equals(n) ? ap : "he".equals(n) ? he : "coax".equals(n) ? coax : null, 1);
        assertEquals(3, w.size());
        for (int i = 0; i < 2; i++) {
            w.get(i).setRestAllAmmo(10);
            w.get(i).reloadMag();
        }
        assertTrue(w.get(1).canFire());
        w.get(0).fireOneShot(false); // the one-round magazine empties -> a reload starts
        w.shareGroupCooldown(w.get(0));
        assertTrue(w.get(1).reloading(), "the HE round shares the barrel: it reloads with the APFSDS shot");
        assertFalse(w.get(1).canFire());
        assertTrue(w.get(2).canFire(), "a weapon outside the group is unaffected");
    }

    @Test
    void cyclingAppliesTheSwitchToBothWeaponsAndAOneWeaponSeatReloads() {
        MCH_AircraftInfo ac = new mcheli.agnostic.tank.MCH_TankInfo("modetank");
        MCH_WeaponInfoManager.getInstance().clear(); // keep the AddWeapon gate open regardless of test order
        ac.loadItemData("addweapon", "gun_a, 0,0,0, 0,0, true,0");
        ac.loadItemData("addweapon", "gun_b, 0,0,0, 0,0, true,0");
        MCH_WeaponInfo a = info("gun_a", "MachineGun1", "round", "5", "maxammo", "20", "delay", "0", "reloadtime", "8");
        MCH_WeaponInfo b = info("gun_b", "MachineGun1", "delay", "0");
        VehicleWeapons w = VehicleWeapons.build(ac, n -> "gun_a".equals(n) ? a : "gun_b".equals(n) ? b : null, 1);
        assertEquals(2, w.size());
        IntPredicate noCrew = seat -> false;
        w.initSelection(noCrew);
        assertEquals(0, w.selectedIndex(0));
        WeaponSlot gunA = w.get(0);
        gunA.setRestAllAmmo(20);
        gunA.reloadMag();
        gunA.fireOneShot(false);

        w.cycle(0, 1, noCrew);
        assertEquals(1, w.selectedIndex(0));
        assertTrue(gunA.reloading(), "the weapon switched away from reloads its partial magazine");
        assertFalse(w.get(1).canFire(), "the weapon switched to is locked out");
        assertEquals(0, w.nextEligible(0, 1, noCrew), "the next switch wraps back to the first weapon");
    }
}
