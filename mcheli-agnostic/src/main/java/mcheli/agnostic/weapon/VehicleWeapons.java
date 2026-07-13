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
 * resolves to a loaded, {@linkplain #isFireable fireable} weapon becomes one {@link WeaponSlot}. ALL mounts are kept —
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

    /** Cycle {@code seat} to its next/previous ELIGIBLE weapon, skipping ones it may not use. */
    public void cycle(int seat, int step, IntPredicate seatHasPlayer) {
        if (seat < 0 || seat >= seats() || this.slots.isEmpty()) {
            return;
        }
        int n = this.slots.size();
        int dir = step >= 0 ? 1 : -1;
        int cur = this.selectedBySeat[seat];
        for (int k = 1; k <= n; k++) {
            int cand = (((cur < 0 ? -1 : cur) + dir * k) % n + n) % n;
            if (eligible(this.slots.get(cand), seat, seatHasPlayer)) {
                WeaponSlot prev = selected(seat);
                if (prev != null && cand != cur) {
                    prev.onDeselect();
                }
                this.selectedBySeat[seat] = cand;
                return;
            }
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
            }
            this.selectedBySeat[seat] = -1;
            for (int i = 0; i < this.slots.size(); i++) {
                if (eligible(this.slots.get(i), seat, seatHasPlayer)) {
                    this.selectedBySeat[seat] = i;
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

    /** A creative rider boarding tops every magazine (reference {@code MCH_WeaponSet:196-199}). */
    public void onMount(boolean creative) {
        for (WeaponSlot s : this.slots) {
            s.onMount(creative);
        }
    }

    /**
     * Whether a weapon {@code type} spawns a straight/ballistic projectile this port fires today. Guided-missile
     * <em>guidance</em> (AA/AS/AT/TV homing + lock-on) is a deferred sub-port — those types are still listed and fired
     * here (as unguided projectiles with their real stats/sound/model), so the loadout is complete; utility/complex
     * types (targeting pod, dispenser, CAS air-strike call, smoke, dummy, bomb) are excluded from the selectable list.
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
                return true;
            default:
                return false;
        }
    }
}
