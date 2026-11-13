package mcheli.agnostic.weapon;

import java.util.function.IntSupplier;

/**
 * The gatling barrel spin — the EXACT reference accumulation ({@code MCH_WeaponSet.update}, 1.7.10
 * {@code mcheli/weapon/MCH_WeaponSet.java}:266-274): near-instant spin-up to 74 deg/tick while a weapon recently fired
 * (the synced firing flag), ~50-tick coast-down. ZERO Minecraft imports. The 360 wrap subtracts from prev in lockstep
 * so the interpolation seam stays valid.
 *
 * <p>Transliterated from the port's own client tick (AbstractMchVehicle {@code updateWheelTrackPhase}, baseline lines
 * 5120-5137). The RNG draw (reference {@code rand.nextInt(3)}) stays in the dependent layer, passed in as a supplier
 * that is called exactly when the barrel spins up ({@code firing && speed < 75}), so the client draw count and order
 * are unchanged.
 */
public final class BarrelSpin {

    private float barrelSpin, prevBarrelSpin, barrelSpeed; // deg: gatling barrel angle + its ramped spin rate (deg/tick)

    /**
     * One client tick of the barrel.
     *
     * @param firing a weapon fired within the last few ticks (the synced firing flag)
     * @param rand3  the spin-up jitter, {@code nextInt(3)}; called at most once, only while spinning up
     */
    public void tick(boolean firing, IntSupplier rand3) {
        this.prevBarrelSpin = this.barrelSpin;
        if (firing && this.barrelSpeed < 75.0F) {
            this.barrelSpeed += 25.0F + rand3.getAsInt();
            if (this.barrelSpeed > 74.0F) {
                this.barrelSpeed = 74.0F;
            }
        }
        this.barrelSpin += this.barrelSpeed;
        if (this.barrelSpin >= 360.0F) {
            this.barrelSpin -= 360.0F;
            this.prevBarrelSpin -= 360.0F;
        }
        if (this.barrelSpeed > 0.0F) {
            this.barrelSpeed -= 1.48F;
            if (this.barrelSpeed < 0.0F) {
                this.barrelSpeed = 0.0F;
            }
        }
    }

    /** The barrel angle (degrees). */
    public float barrelSpin() { return this.barrelSpin; }
    /** Last tick's barrel angle (shifted by -360 on a wrap tick), for render interpolation. */
    public float prevBarrelSpin() { return this.prevBarrelSpin; }
    /** The current spin rate (degrees per tick). */
    public float barrelSpeed() { return this.barrelSpeed; }
}
