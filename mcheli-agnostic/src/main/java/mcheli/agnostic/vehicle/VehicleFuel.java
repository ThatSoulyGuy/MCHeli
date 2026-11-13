package mcheli.agnostic.vehicle;

/**
 * Fuel economy math of {@code MCH_EntityAircraft}: the throttle burn ({@code updateFuel} :2289-2308), the engine gate
 * ({@code canUseFuel} :2252-2258) and the three supply pulses (fuel aura {@code supplyFuel} :2264-2287, fuel-can siphon
 * :2310-2335, ammo aura {@code supplyAmmoToOtherAircraft} :2418-2451). One instance per vehicle holds the burn's
 * fractional carry; everything else is static. Pure, so the rates are unit-tested.
 */
public final class VehicleFuel {

    /** Ticks between burn samples (reference {@code getCountOnUpdate() % 20}). */
    public static final int BURN_INTERVAL = 20;
    /** Ticks a nearby supplier pauses the burn for (reference {@code fuelSuppliedCount = 40}). */
    public static final int SUPPLIED_PAUSE = 40;
    /** Ticks between fuel-aura pulses, and the units one pulse gives at most. */
    public static final int SUPPLY_INTERVAL = 10;
    public static final int SUPPLY_PER_PULSE = 30;
    /** Ticks between fuel-can siphons, and the units one can gives up per siphon at most. */
    public static final int SIPHON_INTERVAL = 10;
    public static final int SIPHON_PER_CAN = 100;
    /** Ticks between ammo-aura pulses. */
    public static final int AMMO_SUPPLY_INTERVAL = 40;

    private double consumption; // fractional carry, so a burn rate below 1 unit per sample still drains over time

    /** True when this tick takes a burn sample: once a second, with fuel above the 1-unit floor, under power, and not
     *  paused by a supplier. */
    public static boolean burnsThisTick(int tick, int fuel, double throttle, int suppliedCount) {
        return tick % BURN_INTERVAL == 0 && fuel > 1 && throttle > 0.0 && suppliedCount <= 0;
    }

    /**
     * Add one burn sample and return the whole fuel units to remove now (0 while the carry is below one unit). The
     * sample is {@code min(throttle * 1.4, 1) * fuelConsumption * factor}; the carry keeps the fraction.
     *
     * @param factor the category's {@code getFuelConsumptionFactor} (1.0 for every reference category)
     */
    public int burn(double throttle, float fuelConsumption, float factor) {
        this.consumption += Math.min(throttle * 1.4, 1.0) * fuelConsumption * factor;
        if (this.consumption > 1.0) {
            int f = (int) this.consumption;
            this.consumption -= f;
            return f;
        }
        return 0;
    }

    /** The fractional carry, for tests and diagnostics. */
    public double carry() {
        return this.consumption;
    }

    /** Reference {@code canUseFuel}: a tankless config always runs; otherwise the floor is fuel {@code > 1}, not 0. */
    public static boolean canUseFuel(int maxFuel, int fuel, boolean infinity) {
        return maxFuel <= 0 || fuel > 1 || infinity;
    }

    /** Fuel fraction 0..1 for the HUD gauge; 0 when the config declares no tank (reference {@code getFuelP}). */
    public static float fraction(int fuel, int maxFuel) {
        return maxFuel <= 0 ? 0.0F : fuel / (float) maxFuel;
    }

    /** Units one fuel-aura pulse adds to a receiver (reference {@code supplyFuel}: up to 30, never past full). */
    public static int supplyPulse(int fuel, int maxFuel) {
        return Math.max(0, Math.min(SUPPLY_PER_PULSE, maxFuel - fuel));
    }

    /** Units the tank wants from one fuel can this siphon (reference: up to 100, never past full); the can then gives
     *  at most what it still holds. */
    public static int siphonWant(int fuel, int maxFuel) {
        return Math.max(0, Math.min(SIPHON_PER_CAN, maxFuel - fuel));
    }

    /** Rounds one ammo-aura pulse adds to a weapon's total (reference: a tenth of its capacity, at least 1). */
    public static int ammoSupplyPulse(int maxAmmo) {
        return Math.max(1, maxAmmo / 10);
    }
}
