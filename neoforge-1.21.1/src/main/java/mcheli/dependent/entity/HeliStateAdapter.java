package mcheli.dependent.entity;

import mcheli.agnostic.sim.HeliState;

/**
 * The entity-backed {@link HeliState} for {@link MchHelicopter}: supplies the gameplay booleans the ported {@code HeliFlightModel}
 * gates on. This is <em>not</em> a guessed placeholder — for a minimal heli (no rotor parts, no canopy, no fuel
 * tank) the reference {@code MCH_EntityHeli}/{@code MCH_EntityAircraft} evaluate these predicates to exactly these
 * constants:
 * <ul>
 *   <li>{@code canUseBlades()} → {@code true} when {@code rotors.length <= 0} (MCH_EntityHeli);</li>
 *   <li>{@code isCanopyClose()} → the live canopy part ({@code true} when {@code partCanopy == null});</li>
 *   <li>{@code canUseFuel(*)} → {@code true} when {@code getMaxFuel() <= 0} (MCH_EntityAircraft);</li>
 *   <li>{@code isHovering()} = {@code isGunnerMode || isHoveringMode()} (MCH_EntityAircraft).</li>
 * </ul>
 * Hover mode, gunner mode, fuel, the pilot seat and the blade fold are read from (and switched on) the live entity.
 */
public final class HeliStateAdapter implements HeliState {
    /** Bound to the live entity's {@code isDestroyed()} — a destroyed heli loses collective lift (HeliFlightModel gates
     *  on {@code !isDestroyed()}) and falls, which is the mechanism the wreck relies on. */
    private final java.util.function.BooleanSupplier destroyed;

    private final AbstractMchVehicle owner;
    public HeliStateAdapter(AbstractMchVehicle owner) { this.owner = owner; this.destroyed = owner::isDestroyed; }

    @Override public boolean isDestroyed() { return this.destroyed.getAsBoolean(); }
    @Override public boolean isExploded() { return this.owner.isExploded(); }

    // isHovering() = isGunnerMode || isHoveringMode() — reference MCH_EntityAircraft.isHovering(). Both modes are
    // server-authoritative + synced on the entity (reference isGunnerMode / isHoveringMode), so the client reads them too.
    @Override public boolean isHovering() { return isGunnerMode() || isHoveringMode(); }
    @Override public boolean isHoveringMode() { return this.owner instanceof MchHelicopter h && h.isHoveringMode(); }
    @Override public boolean isGunnerMode() { return this.owner.isGunnerModeActive(); }

    // maxFuel <= 0 -> fuel never gates the control path (reference canUseFuel).
    @Override public boolean canUseFuel(boolean checkOtherSeat) { return this.owner.canUseFuel(checkOtherSeat); }
    @Override public boolean canUseFuel() { return this.owner.canUseFuel(false); }
    // Blades gate lift: true for a heli with no foldable rotor, false while folded / mid-fold. The fold-aware value
    // lives on the entity (MchHelicopter.canUseBlades); routing it here makes the sim wind the rotor down + skip
    // flight when the blades are stowed (reference MCH_EntityHeli.canUseBlades feeding onUpdate_Control).
    @Override public boolean canUseBlades() { return this.owner.canUseBlades(); }
    // Folded + grounded -> the sim's parked-taxi nudge (onUpdate_ControlFoldBladeAndOnGround) may run.
    @Override public boolean isFoldedOnGround() { return this.owner.isFoldedOnGround(); }
    // Folded (on the ground or not) -> the on-ground A/D hull yaw of onUpdateAngles (MCH_EntityHeli:449-457).
    @Override public boolean isBladeFolded() { return this.owner instanceof MchHelicopter h && h.isFolded(); }
    // Powered flight requires the SEAT-0 pilot (reference getRiddenByEntity), not merely any rider — else a heli
    // abandoned by its pilot but still holding a gunner would hover forever instead of settling.
    @Override public boolean hasPilot() { return this.owner.pilot() != null; }
    // Reference MCH_EntityAircraft.isCanopyClose: no canopy, or closed and at rest — lift waits for a closing canopy.
    @Override public boolean isCanopyClose() { return this.owner.isCanopyClose(); }

    // The flight model's fuel-out exits: the entity applies the full reference gates (canSwitchHoveringMode's gunner,
    // blade and attitude terms; canSwitchGunnerMode only when entering) and restores the pre-mode throttle.
    @Override public void switchHoveringMode(boolean on) {
        if (this.owner instanceof MchHelicopter h) {
            h.switchHoveringMode(on);
        }
    }
    @Override public void switchGunnerMode(boolean on) { this.owner.switchGunnerMode(on); }
}
