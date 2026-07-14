package mcheli.dependent.entity;

import mcheli.agnostic.sim.HeliState;

/**
 * Demo {@link HeliState} for {@link MchDemoHeli}: supplies the gameplay booleans the ported {@code HeliFlightModel}
 * gates on. This is <em>not</em> a guessed placeholder — for a minimal heli (no rotor parts, no canopy, no fuel
 * tank) the reference {@code MCH_EntityHeli}/{@code MCH_EntityAircraft} evaluate these predicates to exactly these
 * constants:
 * <ul>
 *   <li>{@code canUseBlades()} → {@code true} when {@code rotors.length <= 0} (MCH_EntityHeli);</li>
 *   <li>{@code isCanopyClose()} → {@code true} when {@code partCanopy == null} (MCH_EntityAircraft);</li>
 *   <li>{@code canUseFuel(*)} → {@code true} when {@code getMaxFuel() <= 0} (MCH_EntityAircraft);</li>
 *   <li>{@code isHovering()} = {@code isGunnerMode || isHoveringMode()} (MCH_EntityAircraft).</li>
 * </ul>
 * Hover/gunner mode stay mutable (the reference's {@code switch*Mode} setters); the real fuel/blade/canopy wiring
 * arrives when those subsystems are ported, at which point this seam is replaced by one reading the live entity.
 */
public final class DemoHeliState implements HeliState {
    /** Bound to the live entity's {@code isDestroyed()} — a destroyed heli loses collective lift (HeliFlightModel gates
     *  on {@code !isDestroyed()}) and falls, which is the mechanism the wreck relies on. */
    private final java.util.function.BooleanSupplier destroyed;
    private boolean hoveringMode;

    private final AbstractMchVehicle owner;
    public DemoHeliState(AbstractMchVehicle owner) { this.owner = owner; this.destroyed = owner::isDestroyed; }

    @Override public boolean isDestroyed() { return this.destroyed.getAsBoolean(); }

    // isHovering() = isGunnerMode || isHoveringMode() — reference MCH_EntityAircraft.isHovering(). Gunner mode is
    // server-authoritative + synced on the entity (reference isGunnerMode); the toggle path is on the entity.
    @Override public boolean isHovering() { return this.owner.isGunnerModeActive() || hoveringMode; }
    @Override public boolean isHoveringMode() { return hoveringMode; }
    @Override public boolean isGunnerMode() { return this.owner.isGunnerModeActive(); }

    // maxFuel <= 0 -> fuel never gates the control path (reference canUseFuel).
    @Override public boolean canUseFuel(boolean checkOtherSeat) { return this.owner.canUseFuel(checkOtherSeat); }
    @Override public boolean canUseFuel() { return this.owner.canUseFuel(false); }
    // rotors.length <= 0 -> blades always usable (reference MCH_EntityHeli.canUseBlades).
    @Override public boolean canUseBlades() { return true; }
    // partCanopy == null -> always "closed" (reference MCH_EntityAircraft.isCanopyClose).
    @Override public boolean isCanopyClose() { return true; }

    // switchHoveringMode: the reference MCH_EntityHeli.canSwitchHoveringMode() requires !isGunnerMode AND
    // canUseBlades() AND |wrapped pitch| < 40 AND |wrapped roll| < 40 — an ATTITUDE gate. This stateless demo seam
    // carries no attitude, so only the !gunnerMode term is reproduced; the blade term collapses to true (no rotors)
    // and the attitude term is moot here because the ONLY caller is HeliFlightModel's fuel-out branch, which never
    // fires while canUseFuel(true) is unconditionally true for a fuel-less heli. Whatever live-entity wiring
    // replaces this seam MUST honor the full attitude gate. switchGunnerMode is likewise ungated here.
    @Override public void switchHoveringMode(boolean on) { if (!this.owner.isGunnerModeActive()) { this.hoveringMode = on; } }
    @Override public void switchGunnerMode(boolean on) { /* authoritative toggle lives on the entity */ }
}
