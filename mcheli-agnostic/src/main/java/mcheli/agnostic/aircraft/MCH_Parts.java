package mcheli.agnostic.aircraft;

/**
 * The open/close animator behind every switchable vehicle part (canopy, hatch, wing fold, …) — the agnostic half of
 * the reference {@code MCH_Parts}. The reference packed each part's ON/OFF status into one bit of DataWatcher 31 and
 * ramped {@code rotation} toward {@code rotationMax} (ON) or 0 (OFF) by {@code rotationInv} every tick on BOTH sides,
 * so only the status bit needs syncing; the dependent layer owns the bit, the sync and the sounds, and feeds the
 * status in through {@link #setStatus}. ZERO Minecraft imports.
 *
 * <p>{@link #update()} reports the reference's end-of-travel sound points ({@code soundEndSwichOn/Off}) as an
 * {@link Event}; the start/switching sounds belong to the status change itself ({@code setStatusServer}), which the
 * caller sees as {@link #setStatus} returning true.
 */
public final class MCH_Parts {

    /** What {@link #update()} crossed this tick (reference {@code MCH_Parts.update} sound points). */
    public enum Event { NONE, END_ON, END_OFF }

    public final String partName;
    public final float rotationMax;
    public final float rotationInv;
    public float prevRotation;
    public float rotation;
    private boolean status;

    /** @param rotationMax the ON angle (90 for every reference part); @param rotationInv degrees per tick
     *  (canopy 3.5, hatch 1.5, landing gear and wing 2.5, nozzle 1.5 — reference {@code create*}). */
    public MCH_Parts(String partName, float rotationMax, float rotationInv) {
        this.partName = partName;
        this.rotationMax = rotationMax;
        this.rotationInv = rotationInv;
    }

    public boolean getStatus() {
        return this.status;
    }

    /** Take a new status (the server's switch, or the synced bit on a client). Returns true if it changed, which is
     *  where the reference played {@code soundSwitching} and {@code soundStartSwichOn/Off}. */
    public boolean setStatus(boolean on) {
        if (this.status == on) {
            return false;
        }
        this.status = on;
        return true;
    }

    /** Fully OFF and at rest (reference {@code isOFF}: {@code !status && rotation <= 0.02}). */
    public boolean isOFF() {
        return !this.status && this.rotation <= 0.02F;
    }

    /** Fully ON and at rest (reference {@code isON}: {@code status && rotation >= rotationMax - 0.02}). */
    public boolean isON() {
        return this.status && this.rotation >= this.rotationMax - 0.02F;
    }

    /** One tick of travel (reference {@code MCH_Parts.update}). The reference overshoots its last step (90.5 for a
     *  3.5° canopy) and never clamps; that is kept, since the renderers multiply the raw rotation. */
    public Event update() {
        this.prevRotation = this.rotation;
        if (this.status) {
            if (this.rotation < this.rotationMax) {
                this.rotation += this.rotationInv;
                if (this.rotation >= this.rotationMax) {
                    return Event.END_ON;
                }
            }
        } else if (this.rotation > 0.0F) {
            this.rotation -= this.rotationInv;
            if (this.rotation <= 0.0F) {
                return Event.END_OFF;
            }
        }
        return Event.NONE;
    }

    /** Jump straight to the end of travel without animating (reference {@code forceSwitch}: used for a part that
     *  spawns already ON, e.g. the default-VTOL nozzle). The reference always snapped to {@code rotationMax}, even for
     *  {@code onoff == false}; only {@code true} was ever passed, and OFF snaps to 0 here. */
    public void forceSwitch(boolean onoff) {
        this.status = onoff;
        this.rotation = this.prevRotation = onoff ? this.rotationMax : 0.0F;
    }

    /** Travel fraction 0..1 (reference {@code getFactor}). */
    public float getFactor() {
        return this.rotationMax > 0.0F ? this.rotation / this.rotationMax : 0.0F;
    }

    /** The rotation interpolated for rendering. */
    public float lerp(float partialTick) {
        return this.prevRotation + (this.rotation - this.prevRotation) * partialTick;
    }
}
