package mcheli.dependent.entity;

import mcheli.agnostic.sim.AircraftFlightController;
import mcheli.agnostic.sim.AircraftSimState;
import mcheli.agnostic.sim.ControlInput;
import mcheli.agnostic.sim.FlightModel;
import mcheli.agnostic.sim.TankFlightModel;
import mcheli.agnostic.sim.TankState;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.tank.MCH_TankInfo;
import mcheli.dependent.port.NeoEntityRef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Demo tank: a rideable {@link Entity} whose per-tick movement runs the REAL ported {@code TankFlightModel} via
 * {@link AircraftFlightController} through {@link NeoEntityRef}. The tank shares the plane's forward model
 * ({@code rot2Vec3(yaw, pitch−10°)} thrust) with weaker {@code throttle/8} lift, no VTOL/sweep, plus a brake — so a
 * throttled tank drives forward (and, off the ground, weakly holds altitude while a pilotless one sinks). It also
 * exercises the tank-specific trap the port preserves: {@code applyOnGroundPitch} is a no-op (the reference tank
 * overrides it), so on-ground attitude is NOT damped.
 *
 * <p>Implements {@link RollHolder} for uniformity with the other demos (the tank model itself leaves roll alone).
 * Throttle starts at 0 (reference default): a pilotless tank makes no thrust; a ridden one holding throttle drives
 * forward. Control input is a hard-coded throttle-up until the player-input packet lands. The {@code WheelMng}
 * terrain-follow is deferred (a future WheelTerrainSolver seam) — the hull still moves and rests on the ground via
 * the collision move.
 */
public class MchDemoTank extends Entity implements RollHolder {

    private final EntityRef ref = new NeoEntityRef(this);

    private static final FlightModel MODEL = new TankFlightModel();
    /** Hold throttle up every tick — spools the engine so the tank drives along its hull heading. */
    private static final ControlInput THROTTLE_UP = ControlInput.NONE.withThrottleUp();

    private final MCH_TankInfo info = buildInfo();
    private final TankState tankState = new DemoTankState();
    private final AircraftSimState simState = new AircraftSimState(0.07);

    private float rollAngle;
    private float prevRollAngle;

    private static MCH_TankInfo buildInfo() {
        MCH_TankInfo ti = new MCH_TankInfo("demo_tank");
        ti.speed = 0.2F;            // horizontal speed cap (blocks/tick) — tanks are slow
        ti.gravity = -0.04F;
        ti.gravityInWater = -0.04F;
        ti.motionFactor = 0.96F;
        ti.throttleUpDown = 1.0F;
        ti.onGroundPitch = 0.0F;
        ti.isFloat = false;
        ti.maxFuel = 0;             // canUseFuel() true (see DemoTankState)
        return ti;
    }

    public MchDemoTank(EntityType<? extends MchDemoTank> type, Level level) {
        super(type, level);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { }

    @Override public boolean isPickable() { return !this.isRemoved(); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return true; }

    @Override public float getRollAngle() { return this.rollAngle; }
    @Override public float getPrevRollAngle() { return this.prevRollAngle; }
    @Override public void setRollAngle(float roll) { this.rollAngle = roll; }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return player.startRiding(this) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            this.xo = this.getX();
            this.yo = this.getY();
            this.zo = this.getZ();
            this.yRotO = this.getYRot();
            this.xRotO = this.getXRot();
            this.prevRollAngle = this.rollAngle;

            AircraftFlightController.tickServer(this.ref, this.info, this.simState, THROTTLE_UP, this.tankState, MODEL);
        }
    }
}
