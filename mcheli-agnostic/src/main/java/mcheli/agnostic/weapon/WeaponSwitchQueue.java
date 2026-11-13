package mcheli.agnostic.weapon;

import java.util.HashMap;
import java.util.Map;

/**
 * The per-seat queue of pending weapon-cycle steps. ZERO Minecraft imports. Each one-shot switch payload adds ±1 to its
 * seat's net step count, bounded at one full lap of the loadout so a packet flood cannot queue unbounded work, and the
 * whole queue is drained on the next server tick, so two presses that batch into a single tick both count instead of
 * coalescing to one. Opposite presses cancel to a net 0, which stays queued until the drain (the caller skips it).
 *
 * <p>Transliterated from the port's own server code (AbstractMchVehicle {@code queueWeaponSwitch}, baseline lines
 * 1663-1670, and the drain loop of {@code tickWeapons}, baseline lines 3815-3818) with the int {@code Mth.clamp} body
 * inlined. The lap size (the loadout's slot count) stays in the dependent layer and is passed in.
 */
public final class WeaponSwitchQueue {

    /** seat → net pending cycle steps (sign = direction). */
    private final Map<Integer, Integer> pending = new HashMap<>();

    /**
     * Queue one cycle step for {@code seat}: +1 for a {@code direction >= 0}, else -1, merged into the seat's net count
     * clamped to {@code [-lap, lap]}.
     */
    public void queue(int seat, int direction, int lap) {
        this.pending.merge(seat, direction >= 0 ? 1 : -1, (a, b) -> Math.min(Math.max(a + b, -lap), lap));
    }

    /**
     * Take every seat's net count, in the queue's iteration order (a {@link HashMap} copy, as the baseline loop
     * iterated), and empty the queue. A net 0 is included; the caller skips it.
     */
    public Map<Integer, Integer> drain() {
        Map<Integer, Integer> out = new HashMap<>(this.pending);
        this.pending.clear();
        return out;
    }

    /** True when nothing is queued (for the tests). */
    public boolean isEmpty() {
        return this.pending.isEmpty();
    }
}
