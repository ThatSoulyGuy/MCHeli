package mcheli.agnostic.vehicle;

/**
 * The entity-radar sweep angle (reference {@code MCH_EntityAircraft.updateRadar}, 1.7.10
 * {@code mcheli/aircraft/MCH_EntityAircraft.java}:2560-2567, and {@code getRadarRot}). ZERO Minecraft imports. The
 * sweep advances a per-type speed each client tick (heli/vehicle 5, tank/plane 10; both divide 360 so the sweep lands
 * exactly on 0, where the re-scan fires).
 *
 * <p>Transliterated from the port's own client tick (AbstractMchVehicle {@code advanceRadar}, baseline lines 1849-1856,
 * after its mounted/ridden gate, which stays in the dependent layer with the entity scan) and {@code radarRotInterp}
 * (1909-1916).
 */
public final class RadarSweep {

    private int radarRotate;                 // sweep angle 0..360, advances the sweep speed per client tick
    private int prevRadarRotate;             // last tick's sweep, for smooth interpolation of the sweep line

    /** Advance the sweep by {@code speed} degrees; returns true when it lands on 0 (the caller re-scans then). */
    public boolean advance(int speed) {
        this.prevRadarRotate = this.radarRotate;
        this.radarRotate += speed;
        if (this.radarRotate >= 360) {
            this.radarRotate = 0;
        }
        return this.radarRotate == 0;
    }

    /** Interpolated radar sweep angle for the HUD sweep line (reference {@code getRadarRot}: smooth the tick sweep,
     *  adding 360 across the 355→0 wrap so it never spins backwards). */
    public float interp(float partialTick) {
        float rot = this.radarRotate;
        float prev = this.prevRadarRotate;
        if (rot < prev) {
            rot += 360.0F;
        }
        return prev + (rot - prev) * partialTick;
    }

    /** The sweep angle this tick (degrees, 0..359). */
    public int rotate() { return this.radarRotate; }
}
