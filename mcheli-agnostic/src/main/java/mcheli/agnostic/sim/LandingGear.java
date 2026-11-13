package mcheli.agnostic.sim;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.value.Vec3d;

/**
 * The retractable landing gear of an aircraft whose config declares {@code AddPartLG*} parts — the port of the
 * reference gear {@code MCH_Parts} ({@code MCH_EntityAircraft.createLandingGear}: rotationMax 90, rotationInv 2.5 per
 * tick), its {@code canFold/canUnfold/fold/unfoldLandingGear} gates and the automatic retract/extend rules of
 * {@code MCH_EntityAircraft.updateParts} (:5417-5446).
 *
 * <p>The status (up or down) is server-authoritative and synced by the host; the angle ramps by {@link #STEP} per tick
 * on both sides, so only the status needs syncing (reference {@code updateStatusClient} + {@code update}). Angle 0 is
 * gear down, 90 is gear up. The manual switches are gated by the host's {@link ModeSwitchCooldown}, the one 20-tick
 * {@code modeSwitchCooldown} the reference shares between the gear, canopy, hatch, wing and VTOL switches.
 */
public final class LandingGear {
    /** Reference {@code rotationMax}: fully retracted. */
    public static final float MAX = 90.0F;
    /** Reference {@code rotationInv}: degrees moved per tick. */
    public static final float STEP = 2.5F;

    /** What {@link #update()} reports, so the host plays the reference end-of-travel sound. */
    public enum Event { NONE, RETRACTED, DEPLOYED }

    private boolean folded;
    private float rotation;
    private float prevRotation;

    /** The status: true = gear up (reference {@code isLandingGearFolded}). */
    public boolean isFolded() { return this.folded; }
    /** Current angle, 0 (down) .. 90 (up) (reference {@code getLandingGearRotation}). */
    public float rotation() { return this.rotation; }
    /** Previous tick's angle, for render interpolation. */
    public float prevRotation() { return this.prevRotation; }
    /** Reference {@code MCH_Parts.getFactor}: 0 down .. 1 up. */
    public float factor() { return this.rotation / MAX; }

    /** Set the status (server write, or the client's copy of the synced status). True when it changed — the reference
     *  {@code setStatusServer} plays the start/switching sounds exactly then. */
    public boolean setFolded(boolean up) {
        if (this.folded == up) {
            return false;
        }
        this.folded = up;
        return true;
    }

    /** Reference {@code MCH_Parts.update}: one tick of travel toward the status, reporting when an end is reached. */
    public Event update() {
        this.prevRotation = this.rotation;
        if (this.folded) {
            if (this.rotation < MAX) {
                this.rotation += STEP;
                if (this.rotation >= MAX) {
                    return Event.RETRACTED;
                }
            }
        } else if (this.rotation > 0.0F) {
            this.rotation -= STEP;
            if (this.rotation <= 0.0F) {
                return Event.DEPLOYED;
            }
        }
        return Event.NONE;
    }

    /** Reference {@code canFoldLandingGear}: fully down, and nothing collidable within 10 blocks below. */
    public boolean canFold(EntityRef self) {
        if (this.rotation >= 1.0F) {
            return false;
        }
        return !this.folded && GroundProbe.blockIdInColumn(self, 3, -10) == 0;
    }

    /** Reference {@code canUnfoldLandingGear}: fully up. */
    public boolean canUnfold() {
        return this.rotation >= 89.0F && this.folded;
    }

    /** Reference {@code foldLandingGear}: retract, unless any switch of the vehicle is still cooling down ({@code cd},
     *  the host's shared cooldown). True when the status changed. */
    public boolean fold(ModeSwitchCooldown cd) {
        if (!cd.ready()) {
            return false;
        }
        cd.start(); // the reference restarts it even when the gear was already up
        return setFolded(true);
    }

    /** Reference {@code unfoldLandingGear}: extend a retracted gear, unless any switch is still cooling down. */
    public boolean unfold(ModeSwitchCooldown cd) {
        if (!cd.ready() || !this.folded) {
            return false;
        }
        cd.start();
        return setFolded(false);
    }

    /**
     * Reference {@code updateParts}' automatic gear, for an intact craft on the server. A floatplane that is in or over
     * water (first collidable block within 20 below is water) retracts its gear unless it is flying fast (throttle over
     * 0.8 with nothing within 20 below). A retracted gear extends again once the throttle is under
     * {@code unfoldThrottle} within 10 blocks of the ground — except a floatplane over water (scan 150 down from its
     * float line) — or when a VTOL reaches full hover near the ground. Neither rule touches the cooldown.
     *
     * <p>Runs every server tick, so the column probes are evaluated only when their answer can matter (the reference
     * computed them up front): the 3x3x20 probe only for a floatplane that is not flying fast, the 3x3x10 probe only
     * below the unfold throttle or in full VTOL hover. The outcome is identical.
     *
     * @return true when the status changed
     */
    public boolean autoSwitch(EntityRef self, MCH_AircraftInfo info, double throttle, float unfoldThrottle, int vtolMode) {
        if (!this.folded && factor() <= 0.1F) {
            if (info.isFloat
                && (!(throttle > 0.8F) || self.onGround() || GroundProbe.blockIdInColumn(self, 3, -20) != 0)) {
                Vec3d p = self.position();
                if (self.isInWater() || GroundProbe.waterFirstInColumn(self.world(), p.x(), p.y(), p.z(), 3, -20)) {
                    return setFolded(true);
                }
            }
        } else if (this.folded && factor() >= 0.9F) {
            boolean slow = throttle < unfoldThrottle;
            if ((slow || vtolMode == 2) && GroundProbe.blockIdInColumn(self, 3, -10) != 0) {
                if (slow && info.isFloat) {
                    // A floatplane over water keeps its gear up (and, being slow, skips the VTOL rule like the reference).
                    Vec3d p = self.position();
                    if (GroundProbe.waterFirstInColumn(self.world(), p.x(), p.y() + 1.0 + info.floatOffset, p.z(), 1, -150)) {
                        return false;
                    }
                }
                return setFolded(false);
            }
        }
        return false;
    }
}
