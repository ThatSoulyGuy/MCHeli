package mcheli.agnostic.aircraft;

import java.util.function.BooleanSupplier;

/**
 * The decision half of the reference {@code MCH_MissileDetector.update} (1.7.10 {@code mcheli/aircraft/
 * MCH_MissileDetector.java}:25-75): the 20-tick spacing between inbound-missile tones and the choice between the missile
 * decoy and the RWR alert. ZERO Minecraft imports. The bullet scans ({@code destroyMissile}, {@code isLockedByMissile}),
 * the decoy itself and the tone packets stay in the dependent layer, which feeds this class plain values.
 *
 * <p>Transliterated from the port's own server tick (AbstractMchVehicle {@code tickFlares}, baseline lines 2642-2660),
 * which runs only while the config declares flares (reference {@code if (this.ac.haveFlare())}, :26):
 * <ol>
 *   <li>the cooldown counts down first (reference {@code alertCount--}, :27-29);</li>
 *   <li>while the decoy window is open, the vehicle is intact and a rider is aboard, the decoy runs (reference
 *       {@code isFlareUsing() -> destroyMissile()}, :59-60) and the cooldown is not touched;</li>
 *   <li>otherwise, intact, crewed and with the cooldown at 0, the missile scan is asked; a hit sets the cooldown to
 *       {@link #ALERT_INTERVAL} and raises the alert (reference :62-64). The scan is evaluated only at that point, so
 *       a gated tick costs no entity search.</li>
 * </ol>
 */
public final class MCH_MissileDetector {

    /** Ticks between two inbound-missile alerts (reference {@code alertCount = 20}, :63). */
    public static final int ALERT_INTERVAL = 20;

    /** What the dependent side does this tick. */
    public enum Action {
        /** Nothing: no rider, a wreck, the cooldown still running, or no missile homing on the vehicle. */
        NONE,
        /** The decoy window is open: clear and discard every guided round homing on the vehicle. */
        DECOY,
        /** A guided round is homing on the vehicle: send the RWR alert tone to the crew. */
        ALERT
    }

    private int alertCount; // reference MCH_MissileDetector.alertCount

    /**
     * One server tick of the detector.
     *
     * @param using           the flare decoy window is open (reference {@code isFlareUsing})
     * @param destroyed       the vehicle is a wreck
     * @param crew            a rider sits in seat 0 or seat 1
     * @param lockedByMissile the missile scan (reference {@code isLockedByMissile}); called at most once, and only when
     *                        every other alert condition holds
     */
    public Action tick(boolean using, boolean destroyed, boolean crew, BooleanSupplier lockedByMissile) {
        if (this.alertCount > 0) {
            this.alertCount--;
        }
        if (using && !destroyed && crew) {
            return Action.DECOY;
        } else if (!destroyed && crew && this.alertCount == 0 && lockedByMissile.getAsBoolean()) {
            this.alertCount = ALERT_INTERVAL;
            return Action.ALERT;
        }
        return Action.NONE;
    }

    /** Ticks left before the next alert may sound (0 = ready). */
    public int alertCount() {
        return this.alertCount;
    }
}
