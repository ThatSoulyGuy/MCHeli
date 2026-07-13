package mcheli.dependent.entity;

import mcheli.agnostic.sim.TankState;

/**
 * Demo {@link TankState} for {@link MchDemoTank}: the gameplay predicates {@code TankFlightModel} gates on. For a
 * minimal tank (no canopy, no fuel tank) the reference {@code MCH_EntityTank} computes exactly these constants —
 * {@code isCanopyClose()} → {@code true} (partCanopy == null), {@code canUseFuel()} → {@code true} (maxFuel ≤ 0),
 * {@code isTargetDrone()} → {@code false}. Gunner mode stays mutable (reference {@code switchGunnerMode}); the real
 * canopy/fuel wiring replaces this seam when those subsystems are ported. (Brake is rider input via
 * {@code ControlInput.brake()}, not state.)
 */
public final class DemoTankState implements TankState {
    /** Bound to the live entity's {@code isDestroyed()} — a destroyed tank stops driving (TankFlightModel gates on
     *  {@code !isDestroyed()}) and coasts to a wreck. */
    private final java.util.function.BooleanSupplier destroyed;
    private boolean gunnerMode;

    public DemoTankState(java.util.function.BooleanSupplier destroyed) { this.destroyed = destroyed; }

    @Override public boolean isDestroyed() { return this.destroyed.getAsBoolean(); }
    @Override public boolean isGunnerMode() { return gunnerMode; }
    @Override public boolean isTargetDrone() { return false; }
    @Override public boolean canUseFuel() { return true; }     // maxFuel <= 0
    @Override public boolean isCanopyClose() { return true; }   // partCanopy == null
    @Override public void switchGunnerMode(boolean on) { this.gunnerMode = on; }
}
