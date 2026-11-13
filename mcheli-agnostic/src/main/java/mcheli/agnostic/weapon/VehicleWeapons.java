package mcheli.agnostic.weapon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntPredicate;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;

/**
 * A vehicle's live, config-driven weapon loadout: every {@link WeaponSlot selectable weapon} plus the weapon each SEAT
 * currently has selected. This is the agnostic distillation of the {@code MCH_EntityAircraft} weapon block —
 * {@code weapons[]} + a per-seat {@code currentWeaponID}, {@code getNextWeaponID}, {@code switchWeapon},
 * {@code useCurrentWeapon}.
 *
 * <p><b>How the loadout is built</b> (see {@link #build}): the reference groups <em>consecutive</em> same-type
 * {@code AddWeapon} lines into one {@code MCH_AircraftInfo.WeaponSet} (type + mount list); each such group whose type
 * resolves to a loaded, {@linkplain #isFireable selectable} weapon becomes one {@link WeaponSlot}. ALL mounts are kept —
 * a slot is NOT pre-filtered to pilot-usable positions, because a gunner seat's weapon must exist for that gunner.
 *
 * <p><b>Who may select what</b> ({@link #eligible}, reference {@code MCH_EntityAircraft:4840}): a seat may select its
 * own weapons ({@code slot.seatID() == seat}); the PILOT may additionally <em>borrow</em> a gunner's weapon
 * ({@code canUsePilot}) but only while that gunner seat is EMPTY — so the eligibility set changes as crew board and
 * leave, and the pilot is bumped off a borrowed weapon the moment its gunner arrives.
 */
public final class VehicleWeapons {

    private final List<WeaponSlot> slots;
    /** Selected slot index per seat, or -1 when that seat has nothing eligible. */
    private final int[] selectedBySeat;

    private VehicleWeapons(List<WeaponSlot> slots, int seatCount) {
        this.slots = slots;
        this.selectedBySeat = new int[Math.max(1, seatCount)];
        java.util.Arrays.fill(this.selectedBySeat, -1);
    }

    /**
     * Build the loadout from a parsed vehicle config.
     *
     * @param info      the vehicle definition (its {@code weaponSetList} is the source of truth), or {@code null}
     * @param resolver  weapon-name → {@link MCH_WeaponInfo}; a name that resolves to {@code null} is skipped
     * @param seatCount the vehicle's seat count (per-seat selection is sized from it)
     */
    public static VehicleWeapons build(MCH_AircraftInfo info, Function<String, MCH_WeaponInfo> resolver, int seatCount) {
        List<WeaponSlot> slots = new ArrayList<>();
        if (info != null) {
            for (MCH_AircraftInfo.WeaponSet ws : info.weaponSetList) {
                MCH_WeaponInfo wi = resolver.apply(ws.type);
                if (wi == null || !isFireable(wi.type) || ws.weapons == null || ws.weapons.isEmpty()) {
                    continue;
                }
                slots.add(new WeaponSlot(ws.type, wi, new ArrayList<>(ws.weapons)));
            }
        }
        return new VehicleWeapons(slots, seatCount);
    }

    /** Legacy single-seat build (pilot only) — kept so existing callers/self-tests compile. */
    public static VehicleWeapons build(MCH_AircraftInfo info, Function<String, MCH_WeaponInfo> resolver) {
        return build(info, resolver, 1);
    }

    public boolean isEmpty() {
        return this.slots.isEmpty();
    }

    public int size() {
        return this.slots.size();
    }

    public WeaponSlot get(int i) {
        return this.slots.get(i);
    }

    public List<WeaponSlot> all() {
        return this.slots;
    }

    private int seats() {
        return this.selectedBySeat.length;
    }

    /**
     * May {@code seat} select {@code slot} right now? The seat's own weapons always; plus, for the PILOT (seat 0), a
     * {@code canUsePilot} weapon whose gunner seat is currently EMPTY (reference {@code MCH_EntityAircraft:4840} —
     * the borrow is DYNAMIC, re-evaluated as crew board/leave).
     *
     * @param seatHasPlayer seat index → is a player sitting there
     */
    public boolean eligible(WeaponSlot slot, int seat, IntPredicate seatHasPlayer) {
        int owner = slot.seatID();
        if (owner == seat) {
            return true;
        }
        return seat == 0 && slot.canUsePilot() && !seatHasPlayer.test(owner);
    }

    /** The weapon {@code seat} currently has selected, or null. */
    public WeaponSlot selected(int seat) {
        if (seat < 0 || seat >= seats()) {
            return null;
        }
        int i = this.selectedBySeat[seat];
        return (i >= 0 && i < this.slots.size()) ? this.slots.get(i) : null;
    }

    public int selectedIndex(int seat) {
        return (seat >= 0 && seat < seats()) ? this.selectedBySeat[seat] : -1;
    }

    /** Cycle {@code seat} to its next/previous ELIGIBLE weapon (no infinite-ammo refill); see
     *  {@link #cycle(int, int, IntPredicate, boolean)}. */
    public void cycle(int seat, int step, IntPredicate seatHasPlayer) {
        cycle(seat, step, seatHasPlayer, false);
    }

    /**
     * Cycle {@code seat} to its next/previous ELIGIBLE weapon, skipping ones it may not use — the reference
     * {@code getNextWeaponID} + {@code switchWeapon}. The weapon switched away from reloads a partial magazine
     * ({@link WeaponSlot#reloadOnSwitchAway}) and the one switched to starts the switch lockout
     * ({@link WeaponSlot#onSwitchedTo}). With only one eligible weapon the "switch" lands on the same weapon, which is
     * the reference's manual reload.
     *
     * @param infinityAmmo the operator is creative or the server grants infinite ammo (refills the new magazine)
     */
    public void cycle(int seat, int step, IntPredicate seatHasPlayer, boolean infinityAmmo) {
        int cand = nextEligible(seat, step, seatHasPlayer);
        if (cand < 0) {
            return;
        }
        WeaponSlot prev = selected(seat);
        if (prev != null) {
            prev.onDeselect();
            prev.reloadOnSwitchAway();
        }
        this.selectedBySeat[seat] = cand;
        this.slots.get(cand).onSwitchedTo(infinityAmmo);
    }

    /** The slot index a {@link #cycle} of {@code seat} would land on, or -1 if the seat may use nothing (reference
     *  {@code getNextWeaponID}). The client uses it to decide whether a mouse-wheel turn is a weapon switch. */
    public int nextEligible(int seat, int step, IntPredicate seatHasPlayer) {
        if (seat < 0 || seat >= seats() || this.slots.isEmpty()) {
            return -1;
        }
        int n = this.slots.size();
        int dir = step >= 0 ? 1 : -1;
        int cur = this.selectedBySeat[seat];
        for (int k = 1; k <= n; k++) {
            int cand = (((cur < 0 ? -1 : cur) + dir * k) % n + n) % n;
            if (eligible(this.slots.get(cand), seat, seatHasPlayer)) {
                return cand;
            }
        }
        return -1;
    }

    /** CLIENT mirror: adopt the server's synced selection for {@code seat} without any switch side effects. */
    public void setSelectedIndex(int seat, int index) {
        if (seat >= 0 && seat < seats()) {
            this.selectedBySeat[seat] = index >= 0 && index < this.slots.size() ? index : -1;
        }
    }

    /**
     * Re-evaluate every seat's selection against the CURRENT crew (call on any mount/dismount/seat-switch): a seat
     * holding a weapon it may no longer use is bumped to its next eligible one — the reference's
     * {@code onMountPlayerSeat} auto-bump of a pilot who was borrowing the arriving gunner's weapon
     * ({@code MCH_EntityAircraft:3443-3453}); a seat with nothing selected picks its first eligible weapon.
     */
    public void refreshEligibility(IntPredicate seatHasPlayer) {
        for (int seat = 0; seat < seats(); seat++) {
            WeaponSlot cur = selected(seat);
            if (cur != null && eligible(cur, seat, seatHasPlayer)) {
                continue;
            }
            if (cur != null) {
                cur.onDeselect();
                cur.reloadOnSwitchAway(); // the bump is a reference switchWeapon: the old weapon reloads ...
            }
            this.selectedBySeat[seat] = -1;
            for (int i = 0; i < this.slots.size(); i++) {
                if (eligible(this.slots.get(i), seat, seatHasPlayer)) {
                    this.selectedBySeat[seat] = i;
                    if (cur != null) {
                        this.slots.get(i).onSwitchedTo(false); // ... and the new one starts the switch lockout
                    }
                    break;
                }
            }
        }
    }

    // ---- seat-0 (pilot) convenience views: the single-seat callers still in flight during the multi-seat migration.
    //      With no crew aboard, seat 0 is eligible for its own weapons plus every canUsePilot one — the old behaviour.

    private static final IntPredicate NO_CREW = seat -> false;

    /** The PILOT's selected weapon. */
    public WeaponSlot selected() {
        return selected(0);
    }

    public int selectedIndex() {
        return selectedIndex(0);
    }

    /** Cycle the PILOT's weapon (crew-aware overload: {@link #cycle(int, int, IntPredicate)}). */
    public void cycle(int step) {
        cycle(0, step, NO_CREW);
    }

    /** Seed each seat's selection when the loadout is first built. */
    public void initSelection(IntPredicate seatHasPlayer) {
        refreshEligibility(seatHasPlayer);
    }

    /**
     * {@code fired} just fired a shot (call after {@link WeaponSlot#fireOneShot}) — reference
     * {@code MCH_EntityAircraft.useCurrentWeapon}: every OTHER weapon with the same non-empty config {@code Group} waits
     * out its delay too, and reloads when that shot emptied {@code fired}'s magazine ({@code prm.reload}), so a tank
     * cannot fire its main gun again by switching to another round type.
     */
    public void shareGroupCooldown(WeaponSlot fired) {
        String group = fired.info.group;
        if (group == null || group.isEmpty()) {
            return;
        }
        boolean reload = fired.reloading();
        for (WeaponSlot s : this.slots) {
            if (s != fired && group.equals(s.info.group)) {
                s.waitAndReloadByOther(reload);
            }
        }
    }

    /** Advance every weapon's fire-control counters one tick. */
    public void tick() {
        for (WeaponSlot s : this.slots) {
            s.tick();
        }
    }

    /** Every weapon that tracks a finite reserve — what an ammo-supply aura tops up. */
    public List<WeaponSlot> economySlots() {
        List<WeaponSlot> out = new ArrayList<>();
        for (WeaponSlot s : this.slots) {
            if (s.hasEconomy()) {
                out.add(s);
            }
        }
        return out;
    }

    /** Pull every weapon's reserve into its magazine (reference {@code reloadAllWeapons}, {@code :4683-4687}). */
    public void reloadAll() {
        for (WeaponSlot s : this.slots) {
            s.reloadMag();
        }
    }

    /** A creative rider boarding (or any rider, with the server's infinite ammo) tops every magazine (reference
     *  {@code MCH_WeaponSet:196-199}, whose {@code isCreative} is {@code isInfinityAmmo}). */
    public void onMount(boolean creative) {
        for (WeaponSlot s : this.slots) {
            s.onMount(creative);
        }
    }

    // ---- saved ammo (the vehicle's AcWeaponsAmmo): keyed by weapon, not by slot position ----

    /** The save key of every slot, in slot order ({@link #ammoKeys(List)} of the slot weapon names). */
    public List<String> ammoKeys() {
        List<String> names = new ArrayList<>(this.slots.size());
        for (WeaponSlot s : this.slots) {
            names.add(s.weaponName);
        }
        return ammoKeys(names);
    }

    /**
     * Stable save keys for a slot list: the weapon name, and {@code name#n} for its n-th repeat ({@code n >= 1}). A
     * config can list one weapon in two separate groups (only CONSECUTIVE {@code AddWeapon} lines share a set), so the
     * name alone does not always identify a slot. A slot keeps its key when the slots around it are added or removed,
     * so saved ammo stays on its weapon when the selectable-type set changes.
     */
    public static List<String> ammoKeys(List<String> weaponNames) {
        List<String> keys = new ArrayList<>(weaponNames.size());
        java.util.Map<String, Integer> seen = new java.util.HashMap<>();
        for (String n : weaponNames) {
            String name = n == null ? "" : n.toLowerCase(java.util.Locale.ROOT);
            int k = seen.merge(name, 1, Integer::sum) - 1;
            keys.add(k == 0 ? name : name + "#" + k);
        }
        return keys;
    }

    /**
     * Migrate a legacy {@code AcWeaponsAmmo} array, which was stored by SLOT POSITION, to save keys. The positions are
     * those of the loadout the save was written with: the selectable set before smoke, dummy and guided torpedoes
     * became slots ({@link #isLegacyFireable}). Entries past that loadout are dropped.
     */
    public static java.util.Map<String, Integer> legacyAmmoByKey(MCH_AircraftInfo info,
                                                                 Function<String, MCH_WeaponInfo> resolver,
                                                                 int[] legacy) {
        java.util.Map<String, Integer> out = new java.util.HashMap<>();
        if (info == null || legacy == null) {
            return out;
        }
        List<String> names = new ArrayList<>();
        for (MCH_AircraftInfo.WeaponSet ws : info.weaponSetList) {
            MCH_WeaponInfo wi = resolver.apply(ws.type);
            if (wi != null && isLegacyFireable(wi) && ws.weapons != null && !ws.weapons.isEmpty()) {
                names.add(ws.type);
            }
        }
        List<String> keys = ammoKeys(names);
        for (int i = 0; i < Math.min(keys.size(), legacy.length); i++) {
            out.put(keys.get(i), legacy[i]);
        }
        return out;
    }

    /** The slot filter of saves written before smoke and dummy weapons were selectable and guided torpedoes were
     *  ported: {@link #isFireable} without {@code smoke}/{@code dummy}, and no {@code GuidedTorpedo} torpedo. */
    static boolean isLegacyFireable(MCH_WeaponInfo wi) {
        String t = wi.type == null ? "" : wi.type.toLowerCase(java.util.Locale.ROOT);
        return isFireable(t) && !t.equals("smoke") && !t.equals("dummy")
            && !(wi.isGuidedTorpedo && t.equals("torpedo"));
    }

    /**
     * Whether a weapon {@code type} is SELECTABLE in this port (the reference {@code MCH_WeaponCreator} type list). Guns,
     * rockets, marker rockets, guided missiles (AA/AT lock-on homing, AS ground-point homing, TV operator-steered), dropped
     * bombs, torpedoes (water-run, or homing on an aimed water point when {@code GuidedTorpedo}), CAS (called-in bombing
     * run) and dispensers (item-use on impact) fire projectiles. Smoke ({@code MCH_WeaponSmoke}) and dummy
     * ({@code MCH_WeaponDummy}) are selectable and fire nothing, as in the reference: a selected smoke weapon trails
     * coloured smoke while the throttle is up, and a dummy is an empty "safe" slot ({@code none},
     * {@code dummy_bay_closed}). Only the targeting pod is left out: it needs the unported multiplay spotting system.
     */
    public static boolean isFireable(String type) {
        if (type == null) {
            return false;
        }
        switch (type.toLowerCase()) {
            case "machinegun1":
            case "machinegun2":
            case "rocket":
            case "mkrocket":
            case "aamissile":
            case "asmissile":
            case "atmissile":
            case "tvmissile":
            case "bomb": // dropped munition — released at hull velocity + config gravity (fireSelectedWeapon bomb branch)
            case "torpedo": // forward-launched round that self-steers underwater (MchBullet water-run: level + cruise)
            case "cas": // close air support — aim at the ground to call in a bombing run (fireSelectedWeapon cas branch)
            case "dispenser": // drops a round that "uses" its configured item across a radius on impact (MchBullet dispenseAt)
            case "smoke": // selectable, fires nothing; trails coloured smoke while selected (AbstractMchVehicle weapon smoke)
            case "dummy": // selectable, fires nothing (reference MCH_WeaponDummy.shot returns false)
                return true;
            default:
                return false;
        }
    }
}
