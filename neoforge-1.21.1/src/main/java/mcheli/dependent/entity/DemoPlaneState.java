package mcheli.dependent.entity;

import mcheli.agnostic.sim.PlaneState;

/**
 * Demo {@link PlaneState} for {@link MchDemoPlane}: supplies the gameplay predicates {@code PlaneFlightModel} gates
 * on. Like {@link DemoHeliState} these are the EXACT values the reference {@code MCP_EntityPlane} computes for a
 * minimal plane — no wing part, no VTOL nozzle, no canopy, no fuel tank:
 * <ul>
 *   <li>{@code canUseWing()} → {@code true} when {@code partWing == null} (MCP_EntityPlane:1012);</li>
 *   <li>{@code getNozzleRotation()} → {@code 0} when {@code partNozzle == null} (MCP_EntityPlane:954), so
 *       {@code getVtolMode()} → {@code 0} (MCP_EntityPlane:942);</li>
 *   <li>{@code hasVariableSweepPart()} → {@code false} (no wing part), so {@code sweepPartFactor()} is unused;</li>
 *   <li>{@code isCanopyClose()} → {@code true} (partCanopy == null); {@code canUseFuel()} → {@code true} (maxFuel ≤ 0).</li>
 * </ul>
 * Gunner mode stays mutable (the reference {@code switchGunnerMode}); real wing/VTOL/canopy/fuel wiring replaces
 * this seam when those subsystems are ported.
 */
public final class DemoPlaneState implements PlaneState {
    /** Bound to the live entity's {@code isDestroyed()} — a destroyed plane loses wing thrust (PlaneFlightModel gates
     *  on {@code !isDestroyed()}) and falls into its death spiral. */
    private final java.util.function.BooleanSupplier destroyed;

    private final AbstractMchVehicle owner;
    public DemoPlaneState(AbstractMchVehicle owner) { this.owner = owner; this.destroyed = owner::isDestroyed; }

    @Override public boolean isDestroyed() { return this.destroyed.getAsBoolean(); }
    // Gunner mode is server-authoritative + synced on the entity (reference isGunnerMode); toggle via the entity.
    @Override public boolean isGunnerMode() { return this.owner.isGunnerModeActive(); }
    // isHovering = isGunnerMode || isHoveringMode; the demo plane has no hovering mode, so it collapses to gunner.
    @Override public boolean isHovering() { return this.owner.isGunnerModeActive(); }
    @Override public boolean isTargetDrone() { return this.owner.isTargetDrone(); }
    @Override public boolean canUseFuel() { return this.owner.canUseFuel(false); }       // maxFuel <= 0
    @Override public boolean canUseWing() { return true; }        // partWing == null
    @Override public boolean isCanopyClose() { return true; }     // partCanopy == null
    @Override public void switchGunnerMode(boolean on) { /* authoritative toggle lives on the entity */ }

    // VTOL (#28): the nozzle ramp + mode live on the entity (MchPlane); a non-plane owner keeps the fixed-wing defaults.
    @Override public float getNozzleRotation() { return this.owner instanceof MchPlane p ? p.getNozzleRotation() : 0.0F; }
    @Override public int getVtolMode() { return this.owner instanceof MchPlane p ? p.getVtolMode() : 0; }
    @Override public boolean hasVariableSweepPart() { return false; }
    @Override public float sweepPartFactor() { return 0.0F; }     // unused while hasVariableSweepPart() == false
}
