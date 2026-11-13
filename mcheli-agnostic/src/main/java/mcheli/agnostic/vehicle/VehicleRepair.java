package mcheli.agnostic.vehicle;

/**
 * Self-repair, area repair and medevac regeneration rates of {@code MCH_EntityAircraft}: {@code autoRepair}
 * (:2120-2142), {@code repair} (:2144-2159), {@code repairOtherAircraft} (:2161-2175) and {@code regenerationMob}
 * (:2177-2197). One instance per vehicle holds the auto-repair state machine; the rest is static. Pure, so the timing
 * is unit-tested.
 */
public final class VehicleRepair {

    /** Ticks without a hit before auto-repair starts (reference {@code repairCount = 600}, 30 s). */
    public static final int HIT_PAUSE = 600;
    /** Ticks between auto-repair pulses once it runs (reference {@code repairCount = 40}, 2 s). */
    public static final int PULSE_INTERVAL = 40;
    /** Ticks between area-repair pulses (reference {@code getCountOnUpdate() % 20}). */
    public static final int REPAIR_OTHER_INTERVAL = 20;
    /** Length of the Regeneration effect a medevac grants (reference {@code new PotionEffect(regeneration, 250, 0)}). */
    public static final int REGENERATION_TICKS = 250;
    /** A rider's Regeneration is refreshed while it has less than this left (reference {@code getDuration() < 500}). */
    public static final int REGENERATION_REFRESH_BELOW = 500;

    private int repairCount;
    private int beforeDamageTaken;

    /**
     * One tick of the reference {@code autoRepair}. A rise in damage restarts the 30-second pause; after it, every 2
     * seconds the vehicle mends 1% of its max HP (at least 1) while its HP fraction is at least {@code minHpFraction}
     * (server option {@code AutoRepairHP}, default 0.5). A wreck never mends but still tracks its damage.
     *
     * <p>The reference repairs inside the pulse branch and only then stores {@code beforeDamageTaken =
     * getDamageTaken()}, i.e. the POST-repair damage. The caller here mends after this returns, so the stored value is
     * the damage the caller's {@code repair(amount)} leaves behind ({@code damageTaken - amount} floored at 0, nothing
     * when there is no damage, as the reference {@code repair}/{@code setDamageTaken} do). Without that, a hit no
     * larger than one pulse landing on the next tick would not read as a rise and would not restart the pause.
     *
     * @return the HP to mend this tick (pass it to {@code repair}), or 0 for none
     */
    public int autoRepairTick(int damageTaken, int maxHp, boolean destroyed, double minHpFraction) {
        if (maxHp <= 0) {
            return 0;
        }
        int amount = 0;
        if (!destroyed) {
            if (damageTaken > this.beforeDamageTaken) {
                this.repairCount = HIT_PAUSE;
            } else if (this.repairCount > 0) {
                this.repairCount--;
            } else {
                this.repairCount = PULSE_INTERVAL;
                double hpp = (double) (maxHp - damageTaken) / maxHp;
                if (hpp >= minHpFraction) {
                    amount = repairStep(maxHp / 100);
                }
            }
        }
        this.beforeDamageTaken = amount > 0 && damageTaken > 0 ? Math.max(0, damageTaken - amount) : damageTaken;
        return amount;
    }

    /** Ticks left before the next auto-repair step, for tests and diagnostics. */
    public int repairCount() {
        return this.repairCount;
    }

    /** Reference {@code repair(tpd)}: a repair always mends at least 1 HP. */
    public static int repairStep(int tpd) {
        return Math.max(1, tpd);
    }

    /** True when an area repairer ({@code RepairOtherVehicles} range {@code > 0}) pulses this tick. */
    public static boolean repairsOtherThisTick(int tick, float range) {
        return range > 0.0F && tick % REPAIR_OTHER_INTERVAL == 0;
    }

    /** True when a rider's Regeneration should be refreshed; {@code remaining < 0} means it has none. */
    public static boolean refreshesRegeneration(int remaining) {
        return remaining < REGENERATION_REFRESH_BELOW;
    }
}
