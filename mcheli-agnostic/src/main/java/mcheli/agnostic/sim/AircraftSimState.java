package mcheli.agnostic.sim;

/**
 * Mutable per-vehicle simulation state that the 1.7.10 code kept as private {@code Entity} fields — the
 * authoritative sim's own memory, independent of Minecraft. Throttle state, the {@code currentSpeed} spool
 * envelope (idle 0.07), the plane {@code throttleBack} reverse accumulator, cosmetic rotor spin, and the
 * per-tick prev-throttle snapshot. Roll is NOT here (it goes through {@code EntityRef.roll()}/{@code setRoll()}
 * so the host can render/sync it); the synced throttle mirror is the host adapter's job.
 */
public final class AircraftSimState {
    public double currentThrottle;
    public double prevCurrentThrottle;
    public float throttleBack;   // FLOAT in the reference (MCP_EntityPlane uses `(float)(throttleBack * ...)`)
    public double currentSpeed;
    public double addkeyRotValue;
    // rotationRotor/prevRotationRotor are DOUBLE in the reference (MCH_EntityHeli) — a float accumulator would
    // round the rotor angle every tick and diverge over time (cosmetic, but a real bit-fidelity divergence).
    public double rotationRotor;
    public double prevRotationRotor;
    public int countOnUpdate;

    public AircraftSimState() {
        this(0.0);
    }

    /** Vehicle and heli entities initialise {@code currentSpeed} to 0.07 in their ctor; base is 0.0. */
    public AircraftSimState(double initialCurrentSpeed) {
        this.currentSpeed = initialCurrentSpeed;
    }

    public double getCurrentThrottle() { return this.currentThrottle; }
    public void setCurrentThrottle(double t) { this.currentThrottle = t; }
    public void addCurrentThrottle(double d) { this.currentThrottle += d; }
    public double getPrevCurrentThrottle() { return this.prevCurrentThrottle; }

    /** Snapshot taken once per game tick in the reference's base {@code onUpdate} (before onUpdateAircraft). */
    public void snapshotPreTick() {
        this.prevCurrentThrottle = this.currentThrottle;
    }
}
