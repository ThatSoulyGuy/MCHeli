package mcheli.agnostic.weapon;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.function.IntPredicate;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import org.junit.jupiter.api.Test;

/**
 * {@link SeatWeaponSync} against private oracles: verbatim copies of the pre-split {@code AbstractMchVehicle} bodies
 * (baseline {@code publishSeatWeapons}:3512-3558, {@code packSeatWeaponHud}:3562-3567, {@code publishAllAmmo}:3571-3585)
 * with {@code this.weapons} and {@code seatCount()} turned into parameters, and
 * {@code ClientboundSeatWeaponPayload.quantise} inlined. The loadouts are built the way
 * {@code AmmoEconomyAndSeatEligibilityTest} and {@code WeaponModeAndSwitchTest} build theirs, then driven through
 * seeded random sequences of ticks, shots, switches, mode changes, crew changes and resupply.
 */
class SeatWeaponSyncTest {

    private static final int STEPS = 12_000;

    // ---- oracles (baseline bodies, entity reads turned into parameters) ----

    /** Baseline :3513-3516. */
    private static int[] oracleSelection(VehicleWeapons weapons, int seatCount) {
        int[] selection = new int[seatCount];
        for (int seat = 0; seat < selection.length; seat++) {
            selection[seat] = weapons.selected(seat) != null ? weapons.selectedIndex(seat) : -1;
        }
        return selection;
    }

    /** Baseline :3517-3525; returns {modes, {anyMode ? 1 : 0}}. */
    private static int[][] oracleModes(VehicleWeapons weapons) {
        int[] modes = new int[weapons.size()];
        boolean anyMode = false;
        for (int i = 0; i < modes.length; i++) {
            modes[i] = weapons.get(i).mode();
            anyMode |= modes[i] != 0;
        }
        if (!anyMode) {
            modes = new int[0];
        }
        return new int[][] {modes, {anyMode ? 1 : 0}};
    }

    /** Baseline :3572-3582 (the CompoundTag as a map, so the loop stays verbatim); returns the spent mask. */
    private static int oracleSpent(VehicleWeapons weapons, java.util.Map<String, Integer> t) {
        int spent = 0;
        for (int i = 0; i < weapons.size(); i++) {
            WeaponSlot w = weapons.get(i);
            if (w.hasEconomy()) {
                t.put(Integer.toString(i), w.totalAmmo());
            }
            if (i < 32 && w.reloading()) {
                spent |= 1 << i; // this slot just fired and is reloading -> its missile part hides
            }
        }
        return spent;
    }

    /** ClientboundSeatWeaponPayload.quantise, verbatim. */
    private static byte quantise(float f) {
        return (byte) Math.round(Math.max(0.0F, Math.min(1.0F, f)) * 255.0F);
    }

    /** Baseline :3562-3567, the slot reads turned into parameters. */
    private static long oraclePack(int seat, int magazine, float reloadFraction, float heatFraction, boolean prep) {
        long reload = quantise(reloadFraction) & 0xFFL;
        long heat = quantise(heatFraction) & 0xFFL;
        return (magazine & 0xFFFFFFFFL) | reload << 32 | heat << 40 | (prep ? 1L : 0L) << 48
            | (seat & 0x7FL) << 49;
    }

    // ---- fixtures ----

    private static MCH_WeaponInfo info(String name, String type, String... kv) {
        MCH_WeaponInfo w = new MCH_WeaponInfo(name);
        w.loadItemData("type", type);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            w.loadItemData(kv[i], kv[i + 1]);
        }
        return w;
    }

    /** A crewed loadout: the pilot's two guns, a gunner's borrowable rocket pod and missile, a gunner-only gun. With
     *  {@code fixMode} it also carries a TV missile whose FixMode keeps a non-zero mode at all times. */
    private static VehicleWeapons crewLoadout(boolean fixMode, int seatCount) {
        MCH_WeaponInfoManager.getInstance().clear(); // keep every AddWeapon name, as the fixture tests do
        MCH_AircraftInfo ac = new mcheli.agnostic.tank.MCH_TankInfo(fixMode ? "synctank_fix" : "synctank");
        // Config seat numbers are 1-based (AddWeapon seatID = s[7] - 1).
        ac.loadItemData("addweapon", "gun_a, 0,0,0, 0,0, true,1");
        ac.loadItemData("addweapon", "m230, 0,0,0, 0,0, true,1");
        ac.loadItemData("addweapon", "crv7, 0,0,0, 0,0, true,2");
        ac.loadItemData("addweapon", "agm114, 0,0,0, 0,0, true,2");
        ac.loadItemData("addweapon", "gun_b, 0,0,0, 0,0, false,3");
        if (fixMode) {
            ac.loadItemData("addweapon", "tv, 0,0,0, 0,0, true,1");
        }
        java.util.Map<String, MCH_WeaponInfo> infos = new java.util.HashMap<>();
        infos.put("gun_a", info("gun_a", "MachineGun1", "round", "5", "maxammo", "20", "delay", "0",
            "reloadtime", "8", "suppliednum", "4"));
        infos.put("m230", info("m230", "MachineGun2", "explosion", "2", "modenum", "2", "displayname", "M230",
            "delay", "1", "round", "3", "reloadtime", "4", "maxheatcount", "6", "heatcount", "2"));
        infos.put("crv7", info("crv7", "Rocket", "modenum", "2", "piercing", "3", "round", "2", "delay", "2",
            "reloadtime", "6", "maxammo", "12", "group", "Pod"));
        infos.put("agm114", info("agm114", "ATMissile", "modenum", "2", "round", "1", "delay", "3",
            "reloadtime", "10", "maxammo", "8", "group", "Pod"));
        infos.put("gun_b", info("gun_b", "MachineGun1", "delay", "0", "round", "4", "reloadtime", "3"));
        infos.put("tv", info("tv", "TVMissile", "modenum", "2", "fixmode", "1", "round", "1", "reloadtime", "5"));
        return VehicleWeapons.build(ac, infos::get, seatCount);
    }

    /** {@code n} single-round missile slots (alternating names, so no two consecutive lines group). */
    private static VehicleWeapons railLoadout(int n) {
        MCH_WeaponInfoManager.getInstance().clear();
        MCH_AircraftInfo ac = new mcheli.agnostic.tank.MCH_TankInfo("railtank");
        for (int i = 0; i < n; i++) {
            ac.loadItemData("addweapon", (i % 2 == 0 ? "ra" : "rb") + ", 0,0,0, 0,0, true,1");
        }
        MCH_WeaponInfo ra = info("ra", "ATMissile", "round", "1", "maxammo", "10", "reloadtime", "50");
        MCH_WeaponInfo rb = info("rb", "ATMissile", "round", "1", "maxammo", "10", "reloadtime", "50");
        return VehicleWeapons.build(ac, name -> "ra".equals(name) ? ra : "rb".equals(name) ? rb : null, 1);
    }

    // ---- the seeded comparisons ----

    @Test
    void matchesTheBaselineOnSeededLoadoutSequences() {
        long seed = 0x5EA7L;
        for (int variant = 0; variant < 4; variant++) {
            int seats = 1 + variant;                   // 1..4 seats
            boolean fixMode = variant % 2 == 1;
            VehicleWeapons w = crewLoadout(fixMode, seats);
            assertTrue(w.size() >= 5, "the fixture loadout builds");
            run(w, seats, new Random(seed + variant));
        }
    }

    private static void run(VehicleWeapons w, int seats, Random r) {
        int emptyModes = 0;
        int nonEmptyModes = 0;
        int spentSeen = 0;
        w.initSelection(seat -> false);
        for (int step = 0; step < STEPS; step++) {
            int mask = r.nextInt(16);
            IntPredicate manned = seat -> seat >= 0 && (mask >> seat & 1) != 0;
            int seat = r.nextInt(seats + 2) - 1;       // includes -1 and one past the end
            WeaponSlot slot = w.get(r.nextInt(w.size()));
            switch (r.nextInt(12)) {
                case 0, 1, 2 -> w.tick();
                case 3 -> w.cycle(seat, r.nextBoolean() ? 1 : -1, manned, r.nextBoolean());
                case 4 -> {
                    if (slot.canFire()) {
                        slot.fireOneShot(r.nextInt(4) == 0);
                        w.shareGroupCooldown(slot);
                    }
                }
                case 5 -> slot.switchMode();
                case 6 -> w.refreshEligibility(manned);
                case 7 -> w.setSelectedIndex(seat, r.nextInt(w.size() + 4) - 2);
                case 8 -> slot.setRestAllAmmo(r.nextInt(31));
                case 9 -> {
                    if (r.nextBoolean()) {
                        slot.reloadMag();
                    } else {
                        w.reloadAll();
                    }
                }
                case 10 -> slot.reloadOnSwitchAway();
                default -> {
                    if (r.nextBoolean()) {
                        slot.onSwitchedTo(r.nextBoolean());
                    } else {
                        w.onMount(r.nextBoolean());
                    }
                }
            }

            for (int sc : new int[] {0, 1, seats, seats + 2}) {
                assertArrayEquals(oracleSelection(w, sc), SeatWeaponSync.selection(w, sc), "selection, step " + step);
            }
            int[][] om = oracleModes(w);
            int[] modes = SeatWeaponSync.modes(w);
            assertArrayEquals(om[0], modes, "modes, step " + step);
            assertEquals(om[1][0] == 1, modes.length != 0, "the m entry is written exactly when modes is non-empty");
            if (modes.length == 0) {
                emptyModes++;
            } else {
                nonEmptyModes++;
            }
            int spent = SeatWeaponSync.spentMask(w);
            assertEquals(oracleSpent(w, new java.util.HashMap<>()), spent, "spent mask, step " + step);
            if (spent != 0) {
                spentSeen++;
            }
            for (int s = 0; s < seats; s++) {
                WeaponSlot sel = w.selected(s);
                if (sel == null) {
                    continue;
                }
                long expect = oraclePack(s, sel.magazine(), sel.reloadFraction(), sel.heatFraction(),
                    sel.inPreparation());
                long reload = quantise(sel.reloadFraction()) & 0xFFL;
                long heat = quantise(sel.heatFraction()) & 0xFFL;
                assertEquals(expect, SeatWeaponSync.packNotice(s, sel.magazine(), reload, heat, sel.inPreparation()),
                    "notice, step " + step + " seat " + s);
            }
        }
        assertTrue(spentSeen > 0, "the sequence reached a reloading slot");
        if (w.size() == 5) {
            assertTrue(emptyModes > 0 && nonEmptyModes > 0, "both the empty and the written m array came up");
        }
    }

    @Test
    void packMatchesTheBaselineOnSeededValues() {
        Random r = new Random(0xBEEFL);
        float[] specials = {0.0F, 1.0F, -0.0F, -0.25F, 1.75F, 0.5F, 0.001961F, 0.998F, Float.NaN,
            Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (int i = 0; i < 50_000; i++) {
            int seat = r.nextInt(10) == 0 ? r.nextInt() : r.nextInt(300) - 10;
            int magazine = switch (r.nextInt(4)) {
                case 0 -> -1;
                case 1 -> r.nextInt();
                default -> r.nextInt(200);
            };
            float rf = r.nextInt(8) == 0 ? specials[r.nextInt(specials.length)] : r.nextFloat() * 1.5F - 0.25F;
            float hf = r.nextInt(8) == 0 ? specials[r.nextInt(specials.length)] : r.nextFloat() * 1.5F - 0.25F;
            boolean prep = r.nextBoolean();
            long expect = oraclePack(seat, magazine, rf, hf, prep);
            assertEquals(expect, SeatWeaponSync.packNotice(seat, magazine, quantise(rf) & 0xFFL,
                quantise(hf) & 0xFFL, prep), "masked bytes, i=" + i);
            assertEquals(expect, SeatWeaponSync.packNotice(seat, magazine, quantise(rf), quantise(hf), prep),
                "sign-extended bytes, i=" + i);
        }
    }

    // ---- hand-traced edges ----

    @Test
    void selectionReadsMinusOneWithoutASelectedWeapon() {
        VehicleWeapons w = crewLoadout(false, 2);
        int last = w.size() - 1;
        w.setSelectedIndex(0, last);
        assertEquals(last, w.selectedIndex(0));
        // Baseline :3515: the index is published only when selected() is non-null. Shrinking the slot list leaves the
        // stored index past the end: selected() is null, so the seat reads -1 although an index is still set.
        w.all().remove(last);
        assertNull(w.selected(0));
        assertEquals(last, w.selectedIndex(0));
        assertEquals(-1, SeatWeaponSync.selection(w, 2)[0]);
        assertArrayEquals(oracleSelection(w, 2), SeatWeaponSync.selection(w, 2));
        // Seats past the loadout's own seat count read -1; seatCount 0 is an empty array.
        int[] wide = SeatWeaponSync.selection(w, 5);
        assertEquals(-1, wide[2]);
        assertEquals(-1, wide[4]);
        assertEquals(0, SeatWeaponSync.selection(w, 0).length);
    }

    @Test
    void modesAreEmptyUntilOneIsNonZero() {
        VehicleWeapons w = crewLoadout(false, 1);
        assertEquals(0, SeatWeaponSync.modes(w).length, "every mode 0: no m entry (baseline :3523-3525)");
        assertTrue(w.get(1).switchMode(), "the M230 has an HE mode");
        int[] modes = SeatWeaponSync.modes(w);
        assertEquals(w.size(), modes.length, "one non-zero mode: the whole array is written");
        assertEquals(1, modes[1]);
        assertEquals(0, modes[0]);
        assertEquals(0, SeatWeaponSync.modes(VehicleWeapons.build(null, n -> null, 1)).length, "an empty loadout");
        VehicleWeapons fix = crewLoadout(true, 1);
        assertEquals(fix.size(), SeatWeaponSync.modes(fix).length, "a FixMode weapon keeps the array written");
    }

    @Test
    void spentMaskHasOneBitPerReloadingSlotBelow32() {
        VehicleWeapons w = railLoadout(34);
        assertEquals(34, w.size(), "34 slots");
        assertEquals(0, SeatWeaponSync.spentMask(w), "nothing fired");
        WeaponSlot s33 = w.get(33);
        s33.setRestAllAmmo(10);
        s33.reloadMag();
        s33.fireOneShot(false);
        assertTrue(s33.reloading());
        assertEquals(0, SeatWeaponSync.spentMask(w), "slot 33 has no bit (baseline :3580: i < 32)");
        WeaponSlot s31 = w.get(31);
        s31.setRestAllAmmo(10);
        s31.reloadMag();
        s31.fireOneShot(false);
        assertEquals(Integer.MIN_VALUE, SeatWeaponSync.spentMask(w), "slot 31 is the sign bit");
        for (int i = 0; i < 34; i++) {
            WeaponSlot s = w.get(i);
            if (!s.reloading()) {
                s.setRestAllAmmo(10);
                s.reloadMag();
                s.fireOneShot(false);
            }
        }
        assertEquals(-1, SeatWeaponSync.spentMask(w), "every slot below 32 reloading: all 32 bits");
        assertEquals(oracleSpent(w, new java.util.HashMap<>()), SeatWeaponSync.spentMask(w));
    }

    @Test
    void packLayout() {
        assertEquals(0xFFFFFFFFL, SeatWeaponSync.packNotice(0, -1, 0, 0, false), "an endless magazine (-1)");
        assertEquals(0x7FL << 49, SeatWeaponSync.packNotice(0x7F, 0, 0, 0, false));
        assertEquals(0L, SeatWeaponSync.packNotice(0x80, 0, 0, 0, false), "seat & 0x7F");
        assertEquals(72L << 49, SeatWeaponSync.packNotice(200, 0, 0, 0, false));
        assertEquals(0xFFL << 32 | 0xFFL << 40, SeatWeaponSync.packNotice(0, 0, (byte) -1, (byte) -1, false),
            "a sign-extended 255 byte packs as 0xFF");
        assertEquals(1L << 48, SeatWeaponSync.packNotice(0, 0, 0, 0, true), "the preparation flag");
        assertEquals(5L | 0x80L << 32 | 0x40L << 40 | 1L << 48 | 1L << 49,
            SeatWeaponSync.packNotice(1, 5, 0x80, 0x40, true));
        assertEquals(oraclePack(3, 7, 0.5F, 0.25F, false), SeatWeaponSync.packNotice(3, 7, quantise(0.5F) & 0xFFL,
            quantise(0.25F) & 0xFFL, false));
    }
}
