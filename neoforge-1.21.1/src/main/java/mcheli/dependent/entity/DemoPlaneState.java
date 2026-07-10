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
    private boolean gunnerMode;

    @Override public boolean isDestroyed() { return false; }
    @Override public boolean isGunnerMode() { return gunnerMode; }
    @Override public boolean isTargetDrone() { return false; }
    @Override public boolean canUseFuel() { return true; }       // maxFuel <= 0
    @Override public boolean canUseWing() { return true; }        // partWing == null
    @Override public boolean isCanopyClose() { return true; }     // partCanopy == null
    @Override public void switchGunnerMode(boolean on) { this.gunnerMode = on; }

    @Override public float getNozzleRotation() { return 0.0F; }   // partNozzle == null -> level thrust
    @Override public int getVtolMode() { return 0; }              // nozzle rotation <= 0.005 -> mode 0
    @Override public boolean hasVariableSweepPart() { return false; }
    @Override public float sweepPartFactor() { return 0.0F; }     // unused while hasVariableSweepPart() == false
}
