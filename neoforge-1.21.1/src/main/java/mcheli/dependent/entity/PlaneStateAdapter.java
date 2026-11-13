package mcheli.dependent.entity;

import mcheli.agnostic.sim.PlaneState;

/**
 * The entity-backed {@link PlaneState} for {@link MchPlane}: supplies the gameplay predicates {@code PlaneFlightModel} gates
 * on, each read live from the entity (the reference {@code MCP_EntityPlane} / {@code MCH_EntityAircraft} methods):
 * <ul>
 *   <li>{@code canUseWing()} → the folding / variable-sweep wing part ({@code true} without one, MCP_EntityPlane:1012);</li>
 *   <li>{@code getNozzleRotation()} / {@code getVtolMode()} → the VTOL nozzle ({@code 0} for a fixed-wing plane,
 *       MCP_EntityPlane:942-954);</li>
 *   <li>{@code hasVariableSweepPart()} / {@code sweepPartFactor()} → the wing, else the hatch part, and its travel;</li>
 *   <li>{@code isCanopyClose()} → the canopy part ({@code true} without one); {@code canUseFuel()} → the fuel tank
 *       ({@code true} when {@code MaxFuel} ≤ 0, under the InfinityFuel option or for a creative pilot);</li>
 *   <li>{@code hasPilot()}, {@code isGunnerMode()} → the seat-0 rider and the synced gunner mode, which the flight
 *       model may also switch off ({@code switchGunnerMode}, out of fuel); {@code tryFoldLandingGear()} → the gear.</li>
 * </ul>
 */
public final class PlaneStateAdapter implements PlaneState {
    /** Bound to the live entity's {@code isDestroyed()} — a destroyed plane loses wing thrust (PlaneFlightModel gates
     *  on {@code !isDestroyed()}) and falls into its death spiral. */
    private final java.util.function.BooleanSupplier destroyed;

    private final AbstractMchVehicle owner;
    public PlaneStateAdapter(AbstractMchVehicle owner) { this.owner = owner; this.destroyed = owner::isDestroyed; }

    @Override public boolean isDestroyed() { return this.destroyed.getAsBoolean(); }
    @Override public boolean isExploded() { return this.owner.isExploded(); }
    // The SEAT-0 pilot, alive (reference getRiddenByEntity() != null && !isDead) — not passengers().get(0).
    @Override public boolean hasPilot() {
        net.minecraft.world.entity.Entity p = this.owner.pilot();
        return p != null && p.isAlive();
    }
    // Gunner mode is server-authoritative + synced on the entity (reference isGunnerMode); toggle via the entity.
    @Override public boolean isGunnerMode() { return this.owner.isGunnerModeActive(); }
    // isHovering = isGunnerMode || isHoveringMode; a plane has no hovering mode, so it collapses to gunner mode.
    @Override public boolean isHovering() { return this.owner.isGunnerModeActive(); }
    @Override public boolean isTargetDrone() { return this.owner.isTargetDrone(); }
    @Override public boolean canUseFuel() { return this.owner.canUseFuel(false); }       // true when MaxFuel <= 0
    @Override public boolean canUseWing() { return this.owner.canUseWing(); }       // MCP_EntityPlane:1012 (true w/o a wing)
    @Override public boolean isCanopyClose() { return this.owner.isCanopyClose(); } // true w/o a canopy
    @Override public void switchGunnerMode(boolean on) { this.owner.switchGunnerMode(on); } // fuel-out exit, restores throttle
    // Target-drone level-off retracts the gear (reference MCP_EntityPlane:723-725).
    @Override public void tryFoldLandingGear() {
        if (this.owner.canFoldLandingGear()) {
            this.owner.foldLandingGear();
        }
    }

    // VTOL (#28): the nozzle ramp + mode live on the entity (MchPlane); a non-plane owner keeps the fixed-wing defaults.
    @Override public float getNozzleRotation() { return this.owner instanceof MchPlane p ? p.getNozzleRotation() : 0.0F; }
    @Override public int getVtolMode() { return this.owner instanceof MchPlane p ? p.getVtolMode() : 0; }
    @Override public boolean hasVariableSweepPart() { return this.owner.hasSweepPart(); } // partWing, else partHatch
    @Override public float sweepPartFactor() { return this.owner.sweepPartFactor(); }
}
