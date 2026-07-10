package mcheli.dependent.entity;

import mcheli.agnostic.plane.MCP_PlaneInfo;
import mcheli.agnostic.sim.AircraftFlightController;
import mcheli.agnostic.sim.AircraftSimState;
import mcheli.agnostic.sim.ControlInput;
import mcheli.agnostic.sim.FlightModel;
import mcheli.agnostic.sim.PlaneFlightModel;
import mcheli.agnostic.sim.PlaneState;
import mcheli.agnostic.spi.EntityRef;
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
 * Demo fixed-wing plane: a rideable {@link Entity} whose per-tick movement runs the REAL ported
 * {@code PlaneFlightModel} (vectored-thrust aerodynamics) via {@link AircraftFlightController} through
 * {@link NeoEntityRef}. It exercises the plane path: {@code rot2Vec3(yaw, pitch−10°)} thrust (built-in lift +
 * forward drive), the {@code −0.047·(1−throttle)} sink, the {@code currentSpeed} spool, sweep-wing {@code maxSpeed}
 * (trivial here), and the {@code motionFactor} horizontal drag (planes drag by {@code motionFactor}, not 0.99).
 *
 * <p>Implements {@link RollHolder} (the plane model damps roll on the ground / in water). Throttle starts at 0
 * (reference default): a pilotless plane makes no thrust and sinks; a ridden one holding throttle spools up, flies
 * FORWARD and stays aloft. Control input is a hard-coded throttle-up (the analog of the vehicle's DRIVE_FORWARD)
 * until the player-input packet lands.
 */
public class MchDemoPlane extends Entity implements RollHolder {

    private final EntityRef ref = new NeoEntityRef(this);

    private static final FlightModel MODEL = new PlaneFlightModel();
    /** Hold throttle up every tick — spools the engine so the plane accelerates along its nose. */
    private static final ControlInput THROTTLE_UP = ControlInput.NONE.withThrottleUp();

    private final MCP_PlaneInfo info = buildInfo();
    private final PlaneState planeState = new DemoPlaneState();
    private final AircraftSimState simState = new AircraftSimState(0.07);

    private float rollAngle;
    private float prevRollAngle;

    private static MCP_PlaneInfo buildInfo() {
        MCP_PlaneInfo pi = new MCP_PlaneInfo("demo_plane");
        pi.speed = 0.4F;            // horizontal speed cap (blocks/tick) — planes are fast
        pi.gravity = -0.04F;        // cancelled by the model's +0.04 term; net lift/sink comes from thrust
        pi.gravityInWater = -0.04F;
        pi.motionFactor = 0.96F;    // horizontal drag (also the post-move X/Z drag for planes)
        pi.throttleUpDown = 1.0F;
        pi.onGroundPitch = 0.0F;
        pi.isFloat = false;
        pi.maxFuel = 0;             // canUseFuel() true (see DemoPlaneState)
        pi.isVariableSweepWing = false;
        return pi;
    }

    public MchDemoPlane(EntityType<? extends MchDemoPlane> type, Level level) {
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

            AircraftFlightController.tickServer(this.ref, this.info, this.simState, THROTTLE_UP, this.planeState, MODEL);
        }
    }
}
