package mcheli.agnostic.vehicle;

/**
 * The client weapon recoil kick (reference {@code MCH_EntityAircraft.updateRecoil}, 1.7.10
 * {@code mcheli/aircraft/MCH_EntityAircraft.java}:1602-1606, with the {@code recoilCount} countdown at :1516-1517).
 * ZERO Minecraft imports. A client watches the synced recoil trigger (a shot counter) for a change and runs its own
 * 30-tick kick from the synced recoil value and yaw.
 *
 * <p>Transliterated from the port's own client tick (AbstractMchVehicle {@code advanceRecoil}, baseline lines
 * 1931-1948) and its envelope ({@code recoilEnvelope}, 1970-1976). The pitch/roll split ({@code cos}/{@code sin} of the
 * yaw) stays in the dependent layer.
 */
public final class RecoilKick {

    /** Peak-kick multiplier on the config {@code Recoil} value. The reference ACCUMULATES its per-tick impulse over the
     *  30→12 window (a small per-tick value integrates into a much larger displacement the flight model then returns);
     *  this port applies a single non-accumulating visual offset, so it multiplies up to a comparable felt jolt. Tune
     *  here if the kick reads too strong/weak. */
    public static final float RECOIL_GAIN = 3.0F;

    private int lastRecoilSeq = -1;
    private int clientRecoilCount;
    private int prevClientRecoilCount;
    private float clientRecoilValue;
    private float clientRecoilYaw;

    /**
     * Advance the client recoil kick each client tick: a new shot (seq bump) restarts the 30-tick countdown; else decay.
     *
     * @param seq   the synced recoil trigger
     * @param value the synced recoil magnitude (degrees); adopted only when a kick starts
     * @param yaw   the synced recoil direction (weapon aim relative to the hull); adopted only when a kick starts
     */
    public void observe(int seq, float value, float yaw) {
        if (seq != this.lastRecoilSeq) {
            // First tick tracking this entity (lastRecoilSeq still -1): ADOPT the already-synced seq without firing a
            // kick, else a vehicle that fired before we came into render range plays a phantom recoil on our first tick.
            boolean firstObserve = this.lastRecoilSeq < 0;
            this.lastRecoilSeq = seq;
            if (seq != 0 && !firstObserve) {
                this.clientRecoilCount = 30;
                this.clientRecoilValue = value;
                this.clientRecoilYaw = yaw;
            }
        }
        this.prevClientRecoilCount = this.clientRecoilCount;
        if (this.clientRecoilCount > 0) {
            this.clientRecoilCount--;
        }
    }

    /** The kick magnitude (degrees) this frame: full at the shot, easing to 0 over the count 30..12, then 0. */
    public float envelope(float partialTick) {
        float start = this.prevClientRecoilCount;
        float end = this.clientRecoilCount;
        float count = start + partialTick * (end - start); // Mth.lerp(partialTick, prev, count), inlined
        if (count < 12.0F) {
            return 0.0F; // reference gate: the kick applies over count 30..12 only
        }
        return (count - 12.0F) / 18.0F * this.clientRecoilValue * RECOIL_GAIN; // full kick at the shot, easing to 0
    }

    /** The direction of the current kick (degrees, weapon aim relative to the hull). */
    public float yaw() { return this.clientRecoilYaw; }
    /** Ticks left of the kick countdown (for tests). */
    public int count() { return this.clientRecoilCount; }
}
