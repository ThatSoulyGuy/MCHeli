package mcheli.dependent.entity;

import mcheli.agnostic.helicopter.MCH_HeliInfo;
import mcheli.agnostic.sim.AircraftFlightController;
import mcheli.agnostic.sim.AircraftSimState;
import mcheli.agnostic.sim.ControlInput;
import mcheli.agnostic.sim.FlightModel;
import mcheli.agnostic.sim.HeliFlightModel;
import mcheli.agnostic.sim.HeliState;
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
 * Demo helicopter: a rideable {@link Entity} whose per-tick movement runs the REAL ported {@code HeliFlightModel}
 * (the collective/cyclic flight model) via {@link AircraftFlightController}, reached through {@link NeoEntityRef}.
 * Where {@link MchDemoVehicle} exercises only the ground path (gravity + forward thrust + friction), this exercises
 * the flight physics that actually matters and was previously unproven at runtime: collective LIFT, the
 * {@code currentSpeed} spool envelope, ground-effect probing (GroundProbe → {@code NeoWorldView}), buoyancy branch
 * selection, hover jitter ({@code world().random()} → {@code NeoRandomSource}), and the 0.95/0.99 post-move drag.
 * Right-click to mount; with a rider aboard and collective held, the rotor spools up and it climbs.
 *
 * <p>Implements {@link RollHolder}: unlike the ground vehicle, the heli model reads and writes MCHeli's custom roll
 * axis (the {@code |pitch|+|roll|} collective falloff and the on-ground roll damping), so roll must persist on the
 * entity for the physics to be faithful. Control input is a hard-coded collective-up — the heli analog of the
 * vehicle's {@code DRIVE_FORWARD} — until the player-input packet lands. Throttle starts at 0 (the reference
 * default), so a PILOTLESS heli develops no collective lift and stays under gravity, and only a ridden one climbs —
 * exactly as the reference gates it (the rider/blade/canopy/fuel gate decides spool-up vs. spin-down).
 */
public class MchDemoHeli extends Entity implements RollHolder {

    /** The agnostic view of this entity, reused each tick so the physics never sees a Minecraft type. */
    private final EntityRef ref = new NeoEntityRef(this);

    private static final FlightModel MODEL = new HeliFlightModel();
    /** Hold collective up (throttleUp) every tick — the heli analog of the vehicle's hard-coded DRIVE_FORWARD. */
    private static final ControlInput COLLECTIVE_UP = ControlInput.NONE.withThrottleUp();

    private final MCH_HeliInfo info = buildInfo();
    private final HeliState heliState = new DemoHeliState();
    // Throttle starts at 0 (reference MCH_EntityAircraft ctor): a PILOTLESS heli produces no collective lift and
    // gravity pins it — faithful. With a rider aboard, the hard-coded collective-up spools the rotor (0.02/tick)
    // and, once past the gravity break-even (~throttle 0.73), it climbs.
    private final AircraftSimState simState = new AircraftSimState(0.07); // heli idles currentSpeed at 0.07

    // MCHeli's custom roll axis (not on vanilla Entity) — persisted here so the heli model's roll reads/writes survive.
    private float rollAngle;
    private float prevRollAngle;

    private static MCH_HeliInfo buildInfo() {
        MCH_HeliInfo hi = new MCH_HeliInfo("demo_heli");
        hi.speed = 0.35F;           // horizontal cruise-speed cap (blocks/tick)
        hi.gravity = -0.04F;        // base gravity; collective near full throttle (~0.055) overcomes it -> climb
        hi.gravityInWater = -0.04F;
        hi.rotorSpeed = 0.5F;       // cosmetic rotor-spin rate
        hi.throttleUpDown = 1.0F;   // collective spool-rate multiplier
        hi.onGroundPitch = 0.0F;    // no resting nose-down attitude
        hi.isFloat = false;         // no buoyancy for the demo (dp stays 0 -> gravity+collective branch)
        hi.maxFuel = 0;             // no fuel tank -> canUseFuel() true (see DemoHeliState)
        return hi;
    }

    public MchDemoHeli(EntityType<? extends MchDemoHeli> type, Level level) {
        super(type, level);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { /* no synced slots yet */ }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { /* nothing to persist yet */ }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { /* nothing to persist yet */ }

    @Override public boolean isPickable() { return !this.isRemoved(); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return true; }

    // RollHolder: NeoEntityRef routes EntityRef.roll()/prevRoll()/setRoll() here.
    @Override public float getRollAngle() { return this.rollAngle; }
    @Override public float getPrevRollAngle() { return this.prevRollAngle; }
    @Override public void setRollAngle(float roll) { this.rollAngle = roll; }

    // Same server-authoritative rationale as MchDemoVehicle: deliberately do NOT override
    // getControllingPassenger()->Player, or the 1.21.1 client vehicle-move packet would clobber the
    // server-side physics every tick and make a human rider rubber-band.

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS; // let the client predict the mount
        }
        return player.startRiding(this) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    public void tick() {
        super.tick();
        // Server-authoritative: physics runs only on the server; the entity tracker syncs the result to clients.
        if (!this.level().isClientSide) {
            this.xo = this.getX();
            this.yo = this.getY();
            this.zo = this.getZ();
            this.yRotO = this.getYRot();
            this.xRotO = this.getXRot();
            this.prevRollAngle = this.rollAngle;   // snapshot the custom roll axis, mirroring vanilla yaw/pitch

            // Real ported flight: snapshot -> collective/cyclic throttle+control -> force integration ->
            // collision move -> post-move damp, delegating to HeliFlightModel through the NeoEntityRef port.
            AircraftFlightController.tickServer(this.ref, this.info, this.simState, COLLECTIVE_UP, this.heliState, MODEL);
        }
    }
}
