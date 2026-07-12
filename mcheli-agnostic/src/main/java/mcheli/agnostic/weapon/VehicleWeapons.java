package mcheli.agnostic.weapon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;

/**
 * A vehicle's live, config-driven weapon loadout: the ordered list of {@link WeaponSlot selectable weapons} plus the
 * currently-selected index. This is the agnostic distillation of the {@code MCH_EntityAircraft} weapon block —
 * {@code weapons[]} + a pilot {@code currentWeaponID}, {@code getNextWeaponID}, {@code switchWeapon},
 * {@code useCurrentWeapon} — scoped to a single controlling seat (the demo craft's pilot).
 *
 * <p><b>How the loadout is built</b> (see {@link #build}): the reference groups <em>consecutive</em> same-type
 * {@code AddWeapon} lines into one {@code MCH_AircraftInfo.WeaponSet} (type + mount list); each such group whose type
 * resolves to a loaded, {@linkplain #isFireable fireable} weapon and has at least one pilot-usable mount becomes one
 * selectable {@link WeaponSlot}. So an AH-64 yields {@code m230 -> hydra70 -> hydra70_mpsm -> aim92 -> agm114 ->
 * agm114tv} (its targeting pods / CAS call are utility types, filtered out).
 *
 * <p>Selection wraps around ({@link #cycle}); because the list is pre-filtered to fireable, pilot-usable weapons, a
 * plain modulo cycle reproduces the reference's per-seat "next weapon" behaviour without needing the runtime
 * seat-eligibility filter (single controlling seat).
 */
public final class VehicleWeapons {

    private final List<WeaponSlot> slots;
    private int selected;

    private VehicleWeapons(List<WeaponSlot> slots) {
        this.slots = slots;
        this.selected = slots.isEmpty() ? -1 : 0;
    }

    /**
     * Build the selectable loadout from a parsed vehicle config.
     *
     * @param info     the vehicle definition (its {@code weaponSetList} is the source of truth), or {@code null}
     * @param resolver weapon-name → {@link MCH_WeaponInfo} (typically {@code MCH_WeaponInfoManager::get}); a name that
     *                 resolves to {@code null} (weapon not loaded) is skipped
     */
    public static VehicleWeapons build(MCH_AircraftInfo info, Function<String, MCH_WeaponInfo> resolver) {
        List<WeaponSlot> slots = new ArrayList<>();
        if (info != null) {
            for (MCH_AircraftInfo.WeaponSet ws : info.weaponSetList) {
                MCH_WeaponInfo wi = resolver.apply(ws.type);
                if (wi == null || !isFireable(wi.type)) {
                    continue;
                }
                List<MCH_AircraftInfo.Weapon> pilotMounts = new ArrayList<>();
                for (MCH_AircraftInfo.Weapon m : ws.weapons) {
                    if (m.canUsePilot) {
                        pilotMounts.add(m);
                    }
                }
                if (!pilotMounts.isEmpty()) {
                    slots.add(new WeaponSlot(ws.type, wi, pilotMounts));
                }
            }
        }
        return new VehicleWeapons(slots);
    }

    public boolean isEmpty() {
        return this.slots.isEmpty();
    }

    public int size() {
        return this.slots.size();
    }

    public int selectedIndex() {
        return this.selected;
    }

    /** The currently-selected weapon, or {@code null} if this vehicle has no fireable weapons. */
    public WeaponSlot selected() {
        return (this.selected >= 0 && this.selected < this.slots.size()) ? this.slots.get(this.selected) : null;
    }

    public WeaponSlot get(int i) {
        return this.slots.get(i);
    }

    /** Select an explicit index (bounds-checked; ignored if out of range). Resets the old weapon's barrel index. */
    public void select(int i) {
        if (i >= 0 && i < this.slots.size() && i != this.selected) {
            WeaponSlot prev = selected();
            if (prev != null) {
                prev.onDeselect();
            }
            this.selected = i;
        }
    }

    /** Wrap-around cycle: {@code step >= 0} → next, {@code step < 0} → previous. No-op with 0/1 weapons. */
    public void cycle(int step) {
        if (this.slots.size() <= 1) {
            return;
        }
        WeaponSlot prev = selected();
        if (prev != null) {
            prev.onDeselect();
        }
        int n = this.slots.size();
        int dir = step >= 0 ? 1 : -1;
        this.selected = ((this.selected + dir) % n + n) % n;
    }

    /** Advance every weapon's fire-control counters one tick. */
    public void tick() {
        for (WeaponSlot s : this.slots) {
            s.tick();
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
