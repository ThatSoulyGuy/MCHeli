package mcheli.dependent.entity;

import mcheli.agnostic.sim.TankState;

/**
 * The entity-backed {@link TankState} for {@link MchTank}: the gameplay predicates {@code TankFlightModel} gates on, each
 * read live from the entity (the reference {@code MCH_EntityTank} / {@code MCH_EntityAircraft} methods): the canopy part
 * ({@code isCanopyClose()}, {@code true} without one), the fuel tank ({@code canUseFuel()}, {@code true} when
 * {@code MaxFuel} ≤ 0, under the InfinityFuel option or for a creative pilot), the config's target-drone flag, the
 * seat-0 pilot and the synced gunner mode, which the flight model may switch off ({@code switchGunnerMode}, out of
 * fuel). (Brake is rider input via {@code ControlInput.brake()}, not state.)
 */
public final class TankStateAdapter implements TankState {
    /** Bound to the live entity's {@code isDestroyed()} — a destroyed tank stops driving (TankFlightModel gates on
     *  {@code !isDestroyed()}) and coasts to a wreck. */
    private final java.util.function.BooleanSupplier destroyed;

    private final AbstractMchVehicle owner;
    public TankStateAdapter(AbstractMchVehicle owner) { this.owner = owner; this.destroyed = owner::isDestroyed; }

    @Override public boolean isDestroyed() { return this.destroyed.getAsBoolean(); }
    @Override public boolean isExploded() { return this.owner.isExploded(); }
    // The SEAT-0 pilot, alive (reference getRiddenByEntity() != null && !isDead) — not passengers().get(0).
    @Override public boolean hasPilot() {
        net.minecraft.world.entity.Entity p = this.owner.pilot();
        return p != null && p.isAlive();
    }
    // Gunner mode is server-authoritative + synced on the entity (reference isGunnerMode); the toggle path is
    // AbstractMchVehicle.toggleGunnerMode. In gunner mode TankFlightModel stops steering and levels the hull.
    @Override public boolean isGunnerMode() { return this.owner.isGunnerModeActive(); }
    @Override public boolean isTargetDrone() { return this.owner.isTargetDrone(); }
    @Override public boolean canUseFuel() { return this.owner.canUseFuel(false); }     // true when MaxFuel <= 0
    @Override public boolean isCanopyClose() { return this.owner.isCanopyClose(); } // true w/o a canopy
    @Override public void switchGunnerMode(boolean on) { this.owner.switchGunnerMode(on); } // fuel-out exit, restores throttle
}
