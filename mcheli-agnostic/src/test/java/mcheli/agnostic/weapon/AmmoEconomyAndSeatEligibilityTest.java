package mcheli.agnostic.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import org.junit.jupiter.api.Test;

/**
 * The ammo RESERVE economy (#37) and the per-seat weapon eligibility rules (#36) — both pure, zero Minecraft.
 *
 * <p>Reference behaviours pinned here: a weapon with a finite {@code MaxAmmo} spawns DRY; the reserve clamps against
 * {@code maxAmmo − the CURRENT magazine} (so loaded rounds count against the cap); a supply pulse deliberately
 * RE-ABSORBS the magazine before adding {@code suppliedNum}; creative fire refills the RESERVE (not the magazine) so
 * the configured reload cadence still plays; and the pilot may borrow a gunner's {@code canUsePilot} weapon only
 * while that gunner seat is EMPTY (dynamic — the pilot is bumped the moment the gunner boards).
 */
class AmmoEconomyAndSeatEligibilityTest {

    /** A weapon with a finite reserve: round (mag) = 5, maxAmmo = 20, suppliedNum = 4. */
    private static MCH_WeaponInfo economyWeapon() {
        MCH_WeaponInfo w = new MCH_WeaponInfo("testgun");
        w.loadItemData("type", "MachineGun1");
        w.loadItemData("round", "5");
        w.loadItemData("maxammo", "20");
        w.loadItemData("suppliednum", "4");
        w.loadItemData("delay", "2");
        w.loadItemData("reloadtime", "10");
        return w;
    }

    private static MCH_AircraftInfo.Weapon mount(boolean canUsePilot, int seatId) {
        return new MCH_AircraftInfo.Weapon(
            0f, 0f, 0f, 0f, 0f, canUsePilot, seatId, 0f, -360f, 360f, -90f, 90f, false);
    }

    private static WeaponSlot slot(MCH_WeaponInfo info, boolean canUsePilot, int seatId) {
        List<MCH_AircraftInfo.Weapon> mounts = new ArrayList<>();
        mounts.add(mount(canUsePilot, seatId));
        return new WeaponSlot(info.name, info, mounts);
    }

    @Test
    void economyWeaponSpawnsDryAndCannotFire() {
        WeaponSlot s = slot(economyWeapon(), true, 0);
        assertTrue(s.hasEconomy(), "MaxAmmo>0 => the weapon tracks a reserve");
        assertEquals(0, s.magazine(), "a fresh vehicle spawns with an EMPTY magazine");
        assertEquals(0, s.restAllAmmo(), "...and an EMPTY reserve");
        assertFalse(s.canFire(), "a dry weapon cannot fire until it is resupplied");
    }

    @Test
    void reserveClampsAgainstMaxAmmoMinusMagazine() {
        WeaponSlot s = slot(economyWeapon(), true, 0);
        s.setRestAllAmmo(1000);
        assertEquals(20, s.restAllAmmo(), "empty mag => the reserve may hold the whole maxAmmo");

        s.reloadMag();                                   // pulls 5 into the magazine
        assertEquals(5, s.magazine());
        assertEquals(15, s.restAllAmmo());
        assertEquals(20, s.totalAmmo(), "magazine + reserve == maxAmmo");

        s.setRestAllAmmo(1000);                          // re-clamp with rounds LOADED
        assertEquals(15, s.restAllAmmo(), "loaded rounds count against the cap: clamp is maxAmmo - magazine");
        assertEquals(20, s.totalAmmo(), "total never exceeds maxAmmo");
    }

    @Test
    void supplyPulseReabsorbsTheMagazineThenAddsSuppliedNum() {
        WeaponSlot s = slot(economyWeapon(), true, 0);
        s.setRestAllAmmo(5);
        s.reloadMag();                                   // mag 5, reserve 0
        assertEquals(5, s.magazine());
        assertEquals(0, s.restAllAmmo());

        s.supplyRestAllAmmo();                           // reference: reserve = mag + reserve + suppliedNum
        assertEquals(9, s.restAllAmmo(), "the magazine is deliberately re-absorbed (5) before adding suppliedNum (4)");
        assertEquals(14, s.totalAmmo(), "nothing is lost: 5 loaded + 9 reserve");
    }

    @Test
    void firingDrainsTheMagazineAndCreativeRefillsTheReserveNotTheMagazine() {
        WeaponSlot s = slot(economyWeapon(), true, 0);
        s.setRestAllAmmo(20);
        s.reloadMag();                                   // mag 5, reserve 15
        for (int i = 0; i < 5; i++) {
            assertTrue(s.canFire(), "shot " + i + " should be allowed");
            s.fireOneShot(false);
            for (int t = 0; t < 3; t++) {
                s.tick();                                // clear the per-shot delay
            }
        }
        assertEquals(0, s.magazine(), "the magazine empties");
        assertTrue(s.reloading(), "emptying the magazine starts a reload");
        for (int t = 0; t < 12; t++) {
            s.tick();
        }
        assertEquals(5, s.magazine(), "the reload pulls from the reserve");
        assertEquals(10, s.restAllAmmo());

        // Creative: the decrement still happens; the RESERVE is topped at mag-empty so the reload cadence still plays.
        WeaponSlot c = slot(economyWeapon(), true, 0);
        assertEquals(0, c.restAllAmmo());
        c.setRestAllAmmo(5);
        c.reloadMag();                                   // mag 5, reserve 0
        for (int i = 0; i < 5; i++) {
            c.fireOneShot(true);
            for (int t = 0; t < 3; t++) {
                c.tick();
            }
        }
        assertTrue(c.restAllAmmo() >= 5, "creative refilled the RESERVE at mag-empty, got " + c.restAllAmmo());
        assertEquals(0, c.magazine(), "creative does NOT skip the magazine decrement");
    }

    @Test
    void noEconomyWeaponKeepsThePreEconomyRefillBehaviour() {
        MCH_WeaponInfo w = new MCH_WeaponInfo("nolimit");
        w.loadItemData("type", "MachineGun1");
        w.loadItemData("round", "3");
        w.loadItemData("delay", "1");
        w.loadItemData("reloadtime", "4");
        // no MaxAmmo => no reserve tracked
        WeaponSlot s = slot(w, true, 0);
        assertFalse(s.hasEconomy());
        assertEquals(3, s.magazine(), "without an economy the weapon starts LOADED (unchanged behaviour)");
        assertEquals(-1, s.restAllAmmo(), "reserve is meaningless without an economy");
        assertTrue(s.canFire());
        for (int i = 0; i < 3; i++) {
            s.fireOneShot(false);
            s.tick();
            s.tick();
        }
        for (int t = 0; t < 6; t++) {
            s.tick();
        }
        assertEquals(3, s.magazine(), "it simply refills on reload — never runs permanently dry");
    }

    // ---- per-seat eligibility (#36) ----

    @Test
    void pilotBorrowsAGunnerWeaponOnlyWhileThatSeatIsEmpty() {
        MCH_WeaponInfo info = economyWeapon();
        WeaponSlot pilotGun = slot(info, true, 0);   // the pilot's own gun
        WeaponSlot gunnerGun = slot(info, true, 2);  // seat 2's gun, but pilot-usable
        WeaponSlot gunnerOnly = slot(info, false, 3);// seat 3's gun, NOT pilot-usable

        VehicleWeapons w = VehicleWeapons.build(null, n -> null, 4);
        // build() from a null info yields no slots, so exercise eligible() directly (it is the rule under test).
        IntPredicate noCrew = seat -> false;
        IntPredicate seat2Manned = seat -> seat == 2;

        assertTrue(w.eligible(pilotGun, 0, noCrew), "the pilot always owns a seat-0 weapon");
        assertTrue(w.eligible(gunnerGun, 0, noCrew), "the pilot BORROWS a canUsePilot gunner weapon while seat 2 is empty");
        assertFalse(w.eligible(gunnerGun, 0, seat2Manned), "the pilot is BUMPED off it once seat 2 is manned");
        assertTrue(w.eligible(gunnerGun, 2, seat2Manned), "seat 2 owns its own weapon");
        assertFalse(w.eligible(gunnerOnly, 0, noCrew), "a NOT-canUsePilot weapon is never borrowable by the pilot");
        assertTrue(w.eligible(gunnerOnly, 3, noCrew), "...but its own seat may always use it");
        assertFalse(w.eligible(pilotGun, 2, noCrew), "a gunner may not use the pilot's weapon");
    }

    @Test
    void refreshEligibilityBumpsAndReseatsSelections() {
        // The AddWeapon parse drops names the weapon manager does not know once it has loaded; another test may have
        // left it loaded, so reset it first to keep both mounts (the vehicle-only parse every unit test relies on).
        MCH_WeaponInfoManager.getInstance().clear();
        MCH_AircraftInfo ac = new mcheli.agnostic.tank.MCH_TankInfo("crewtank");
        // Two consecutive AddWeapon groups: one pilot gun, one seat-1 gun that is ALSO pilot-usable. The config seat
        // number is 1-based (MCH_AircraftInfo AddWeapon: seatID = s[7] - 1), so "2" is gunner seat 1.
        ac.loadItemData("addweapon", "testgun, 0,0,0, 0,0, true,1");
        ac.loadItemData("addweapon", "borrowgun, 0,0,0, 0,0, true,2");
        MCH_WeaponInfo a = economyWeapon();
        MCH_WeaponInfo b = new MCH_WeaponInfo("borrowgun");
        b.loadItemData("type", "MachineGun1");
        b.loadItemData("round", "5");

        VehicleWeapons w = VehicleWeapons.build(ac, n -> "testgun".equals(n) ? a : "borrowgun".equals(n) ? b : null, 2);
        assertNotNull(w);
        assertEquals(2, w.size(), "one slot per AddWeapon group");
        assertEquals(0, w.get(0).seatID());
        assertEquals(1, w.get(1).seatID());

        w.refreshEligibility(seat -> false);            // nobody aboard but the pilot
        assertEquals(0, w.selectedIndex(0), "the pilot gets his own gun first");
        assertEquals(1, w.selectedIndex(1), "seat 1 is pre-seeded with its own gun");

        // The pilot borrows seat 1's gun while seat 1 is empty ...
        w.cycle(0, 1, seat -> false);
        assertEquals(1, w.selectedIndex(0), "the pilot may select the empty gunner seat's canUsePilot gun");

        // ... and is bumped back to his own the moment a gunner boards seat 1, who keeps that gun.
        w.refreshEligibility(seat -> seat == 1);
        assertEquals(0, w.selectedIndex(0), "the pilot never keeps a weapon whose gunner seat is now manned");
        assertEquals(1, w.selectedIndex(1), "the gunner has his own gun");
    }

    @Test
    void savedAmmoIsKeyedByWeaponAndALegacyArrayMapsThroughTheOldSlotOrder() {
        MCH_WeaponInfoManager.getInstance().clear(); // keep every AddWeapon name, as in the test above
        MCH_AircraftInfo ac = new mcheli.agnostic.tank.MCH_TankInfo("savetank");
        // smoke, gun a, a guided torpedo, gun b, gun a AGAIN (its own group: not consecutive), a dummy.
        ac.loadItemData("addweapon", "smoke_red, 0,0,0, 0,0, true,1");
        ac.loadItemData("addweapon", "gun_a, 0,0,0, 0,0, true,1");
        ac.loadItemData("addweapon", "mk46, 0,0,0, 0,0, true,1");
        ac.loadItemData("addweapon", "gun_b, 0,0,0, 0,0, true,1");
        ac.loadItemData("addweapon", "gun_a, 0,0,0, 0,0, true,1");
        ac.loadItemData("addweapon", "none, 0,0,0, 0,0, true,1");
        java.util.Map<String, MCH_WeaponInfo> infos = new java.util.HashMap<>();
        infos.put("smoke_red", typed("smoke_red", "smoke"));
        infos.put("gun_a", typed("gun_a", "machinegun1"));
        MCH_WeaponInfo mk46 = typed("mk46", "torpedo");
        mk46.loadItemData("guidedtorpedo", "true");
        infos.put("mk46", mk46);
        infos.put("gun_b", typed("gun_b", "machinegun1"));
        infos.put("none", typed("none", "dummy"));

        VehicleWeapons w = VehicleWeapons.build(ac, infos::get, 1);
        assertEquals(List.of("smoke_red", "gun_a", "mk46", "gun_b", "gun_a#1", "none"), w.ammoKeys(),
            "one key per slot; a repeated weapon is told apart by its occurrence");

        // A save from before smoke, dummy and guided torpedoes were slots held [gun_a, gun_b, gun_a] by position.
        java.util.Map<String, Integer> migrated =
            VehicleWeapons.legacyAmmoByKey(ac, infos::get, new int[] {100, 200, 300, 999});
        assertEquals(java.util.Map.of("gun_a", 100, "gun_b", 200, "gun_a#1", 300), migrated,
            "legacy values land on the weapons they were saved for, not on the slots now at those positions");
        assertTrue(VehicleWeapons.legacyAmmoByKey(null, infos::get, new int[] {1}).isEmpty());
    }

    private static MCH_WeaponInfo typed(String name, String type) {
        MCH_WeaponInfo w = new MCH_WeaponInfo(name);
        w.loadItemData("type", type);
        return w;
    }
}
