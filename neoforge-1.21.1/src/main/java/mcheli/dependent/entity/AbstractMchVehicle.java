package mcheli.dependent.entity;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.sim.ControlInput;
import mcheli.agnostic.sim.RotationSolver;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.util.MCH_LowPassFilterFloat;
import mcheli.dependent.control.MchControlState;
import mcheli.dependent.control.MchControllable;
import mcheli.dependent.port.NeoEntityRef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Shared base for the demo vehicles. Owns the agnostic {@link EntityRef} view, the rider {@link MchControlState},
 * MCHeli's custom roll axis, client-side position/rotation interpolation, and — for aircraft — the client-authoritative
 * mouse rotation hooks. Subclasses supply the per-type flight model / info / sim-state via
 * {@link #tickPhysics(ControlInput)}, and (for mouse-steerable types) a {@link #controlMapping()} +
 * {@link #rotationInfo()}.
 *
 * <p><b>Authority split (matches the reference).</b> POSITION is server-authoritative (physics runs on the server;
 * clients interpolate). ROTATION is CLIENT-authoritative for the local rider: their client runs the ported
 * {@link RotationSolver} from the mouse and ships the result via {@code ServerboundRotationPayload}; the server stores
 * it (physics reads it) and syncs it to OTHER clients (yaw/pitch via the entity tracker, roll via {@link #DATA_ROLL}).
 * So the local rider's rotation is NOT lerped from the server round-trip (that would fight their own computation) —
 * only their position lerps.
 */
public abstract class AbstractMchVehicle extends Entity implements MchControllable, RollHolder {

    /** Synced roll (server -> clients) so non-piloting clients see banking; not a vanilla axis. */
    private static final EntityDataAccessor<Float> DATA_ROLL =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.FLOAT);
    /** Spooled engine power 0..1 (server-authored, synced) so clients can drive rotor RPM / sounds. */
    private static final EntityDataAccessor<Float> DATA_THROTTLE =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.FLOAT);
    private float enginePower; // server-side spool accumulator
    /** Selected paint scheme index (synced) — 0 = default skin, then each {@code AddTexture}. */
    private static final EntityDataAccessor<Integer> DATA_SKIN =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.INT);
    /** Landing-gear retract angle 0 (deployed, on ground) .. 90 (retracted, airborne), synced + interpolated. */
    private static final EntityDataAccessor<Float> DATA_GEAR =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.FLOAT);
    private float gearAngle;
    private float prevGearAngle;

    /** The agnostic view of this entity, reused every tick so the physics never sees a Minecraft type. */
    protected final EntityRef ref = new NeoEntityRef(this);
    private final MchControlState controlState = new MchControlState();

    // MCHeli custom roll axis. The authoritative local value; the server also publishes it to DATA_ROLL for others.
    private float rollAngle;
    private float prevRollAngle;

    // Client-side interpolation state (vanilla minecart pattern).
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private float lerpYRot;
    private float lerpXRot;

    // Client rotation state (only used on the local rider's client): the setAngles partial-ticks low-pass buffer.
    private final MCH_LowPassFilterFloat rotLowPass = new MCH_LowPassFilterFloat(10);
    // Set by the CLIENT-only MchClientRotation handler when THIS is the local player's ridden aircraft (so the
    // client-authoritative rotation is not lerped back from the server). Never referenced on the dedicated server,
    // and — crucially — this class references NO client-only types (NeoForge's RuntimeDistCleaner forbids that).
    private boolean localRotationOwned;

    /** CLIENT: called by MchClientRotation to mark/unmark this as the local rider's mouse-controlled aircraft. */
    public void setLocalRotationOwned(boolean owned) { this.localRotationOwned = owned; }

    // SERVER: the rider's last-sent client-authoritative rotation, re-asserted after each physics tick (see tick())
    // so the flight model's on-ground/water attitude damping — which the pilot's client never applies — cannot
    // desync observers from the pilot. Cleared when the aircraft loses its rider (rotation reverts to the physics).
    private float pendingYaw;
    private float pendingPitch;
    private float pendingRoll;
    private boolean riderOwnsRotation;

    protected AbstractMchVehicle(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override public MchControlState getControlState() { return this.controlState; }
    @Override public float getRollAngle() { return this.rollAngle; }
    @Override public float getPrevRollAngle() { return this.prevRollAngle; }

    @Override
    public void setRollAngle(float roll) {
        this.rollAngle = roll;
        if (!this.level().isClientSide) {
            this.entityData.set(DATA_ROLL, roll); // publish to non-piloting clients
        }
    }

    /**
     * Model-space (blocks) offset of the riding pilot's FEET, before the craft's yaw/pitch/roll rotation — or
     * {@code null} to keep the vanilla "top of hitbox" attachment. A subclass returns its config seat minus 0.5 in Y:
     * the reference places the pilot eye at {@code seatY + 1.62 - 0.5}, and a 1.21.1 player's eye is {@code feet +
     * 1.62}, so {@code feet = seat - 0.5}. This lifts the first-person camera into the cockpit (the ridden player's
     * position IS the camera-entity position).
     */
    protected Vec3 pilotFeetOffset() {
        return null;
    }

    /** Public view of {@link #pilotFeetOffset()} for the client-only cockpit-camera Mixin (a different package). */
    public Vec3 cockpitFeetOffset() {
        return pilotFeetOffset();
    }

    /**
     * Seats the pilot at {@link #pilotFeetOffset()} rotated into world space by the craft's FULL orientation
     * (yaw about -Y, pitch about X, roll about Z — the same {@code Ry(-yaw)·Rx(pitch)·Rz(roll)} as the model
     * renderer), so the eye stays in the cockpit as the aircraft banks. Vanilla passenger attachments rotate by yaw
     * only, which is why this override (not {@code EntityType.passengerAttachments}) is required. JOML math is common
     * to both sides, so this is dist-safe. {@code getPassengerRidingPosition} adds {@code this.position()} on top.
     */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        Vec3 feet = pilotFeetOffset();
        if (feet == null) {
            return super.getPassengerAttachmentPoint(passenger, dimensions, partialTick);
        }
        org.joml.Vector3f v = new org.joml.Vector3f((float) feet.x, (float) feet.y, (float) feet.z);
        v.rotate(new org.joml.Quaternionf()
            .rotateY((float) Math.toRadians(-this.getYRot()))
            .rotateX((float) Math.toRadians(this.getXRot()))
            .rotateZ((float) Math.toRadians(this.getRollAngle())));
        return new Vec3(v.x, v.y, v.z);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ROLL, 0.0F);
        builder.define(DATA_THROTTLE, 0.0F);
        builder.define(DATA_SKIN, 0);
        builder.define(DATA_GEAR, 0.0F);
    }

    /** Landing-gear retract angle 0..90 (deployed..retracted), synced; the renderer interpolates with the prev. */
    public float getGearAngle() { return this.gearAngle; }
    public float getPrevGearAngle() { return this.prevGearAngle; }

    /** Spooled engine power 0..1 (synced). Full when a rider holds throttle-up, idles at 0.5 while ridden, 0 when
     *  abandoned — clients read it for rotor RPM / sound. */
    public float getEnginePower() { return this.entityData.get(DATA_THROTTLE); }

    /** Selected paint-scheme index (synced). The renderer resolves it to a texture via {@link #skinTextureName()}. */
    public int getSkinIndex() { return this.entityData.get(DATA_SKIN); }

    /** Number of selectable skins for this vehicle (subclasses report their config's texture count). */
    protected int skinCount() { return 1; }

    /** Current skin's texture NAME (e.g. {@code ah-64-us-1}) → {@code textures/<dir>/<name>.png}, or null for the
     *  renderer's default. Subclasses map {@link #getSkinIndex()} through their config texture list. */
    public String skinTextureName() { return null; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { /* nothing to persist yet */ }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { /* nothing to persist yet */ }

    @Override public boolean isPickable() { return !this.isRemoved(); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return true; }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            // Sneak + use cycles the paint scheme (a demo stand-in for MCHeli's wrench skin-swap).
            if (!this.level().isClientSide) {
                int count = Math.max(1, this.skinCount());
                this.entityData.set(DATA_SKIN, (this.getSkinIndex() + 1) % count);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS; // let the client predict the mount
        }
        return player.startRiding(this) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    // ---- mouse rotation seams (aircraft override these; ground vehicle/tank leave them null) ----

    /** The per-vehicle mouse->rotation mapping, or null if this vehicle has no mouse rotation. */
    protected RotationSolver.ControlMapping controlMapping() { return null; }

    /** The aircraft info fed to {@link RotationSolver}, or null if this vehicle has no mouse rotation. */
    protected MCH_AircraftInfo rotationInfo() { return null; }

    /** SERVER-side rotation mapping (the tank's keyboard hull-yaw runs on the server, not via the mouse hijack), or
     *  null. Applied before {@link #tickPhysics(ControlInput)} so the physics reads the new heading. */
    protected RotationSolver.ControlMapping serverRotationMapping() { return null; }

    /** True if this vehicle's orientation is driven by the rider's mouse (heli/plane). */
    public boolean supportsMouseRotation() { return controlMapping() != null; }

    /**
     * CLIENT: run the ported setAngles pipeline from the rider's virtual-stick {@link ControlInput}, updating this
     * entity's yaw/pitch/roll locally. Called only on the local rider's client (see {@code MchClientRotation}).
     */
    public void applyClientRotation(ControlInput in) {
        RotationSolver.applyControl(this.ref, rotationInfo(), in, this.rotLowPass, controlMapping());
    }

    /** SERVER: apply the rider's client-computed rotation (from {@code ServerboundRotationPayload}). */
    public void applyServerRotation(float yaw, float pitch, float roll) {
        this.pendingYaw = yaw;
        this.pendingPitch = pitch;
        this.pendingRoll = roll;
        this.riderOwnsRotation = true;
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.setRollAngle(roll); // -> DATA_ROLL sync to other clients
    }

    // ---- client interpolation ----

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yaw;
        this.lerpXRot = pitch;
        this.lerpSteps = steps + 2;
    }

    @Override public double lerpTargetX() { return this.lerpSteps > 0 ? this.lerpX : this.getX(); }
    @Override public double lerpTargetY() { return this.lerpSteps > 0 ? this.lerpY : this.getY(); }
    @Override public double lerpTargetZ() { return this.lerpSteps > 0 ? this.lerpZ : this.getZ(); }
    @Override public float lerpTargetXRot() { return this.lerpSteps > 0 ? this.lerpXRot : this.getXRot(); }
    @Override public float lerpTargetYRot() { return this.lerpSteps > 0 ? this.lerpYRot : this.getYRot(); }

    @Override
    public void tick() {
        super.tick();
        this.prevRollAngle = this.rollAngle; // snapshot for render interpolation (both sides)
        this.prevGearAngle = this.gearAngle;

        if (this.level().isClientSide) {
            boolean localRider = this.localRotationOwned;
            if (this.lerpSteps > 0) {
                // Local rider owns rotation -> lerp POSITION only (rotation target = current, so it is unchanged).
                // Others lerp position + the synced server rotation.
                float tYaw = localRider ? this.getYRot() : this.lerpYRot;
                float tPitch = localRider ? this.getXRot() : this.lerpXRot;
                this.lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ, tYaw, tPitch);
                this.lerpSteps--;
            }
            if (!localRider) {
                this.rollAngle = this.entityData.get(DATA_ROLL); // read the synced roll so banking is visible
            }
            this.gearAngle = this.entityData.get(DATA_GEAR); // read synced gear so the retract animates
            return;
        }

        // Server-authoritative physics. Snapshot prev pos/rot before moving.
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();

        if (this.getPassengers().isEmpty()) {
            this.controlState.clearMomentary(); // an abandoned vehicle holds no keys and coasts
            this.riderOwnsRotation = false;     // ... and yields rotation back to the physics
        }
        ControlInput in = this.controlState.snapshot(1.0F); // server tick -> partialTicks = 1.0F

        // Server-side rotation (tank hull yaw) runs BEFORE the physics so the thrust reads the new heading.
        RotationSolver.ControlMapping serverMap = this.serverRotationMapping();
        if (serverMap != null) {
            RotationSolver.applyControl(this.ref, this.rotationInfo(), in, this.rotLowPass, serverMap);
        }
        this.tickPhysics(in);

        if (this.riderOwnsRotation) {
            // Client-authoritative rotation wins: re-assert the rider's angles over the flight model's on-ground/
            // water attitude damping (the physics still USED the rotation for thrust this tick; only its WRITES are
            // overridden). Keeps observers in sync with the pilot, whose client ignores the server rotation.
            this.setYRot(this.pendingYaw);
            this.setXRot(this.pendingPitch);
            this.setRollAngle(this.pendingRoll);
        }

        // Engine power: full while a rider holds throttle-up, idle (0.5) while ridden, spooling to 0 when abandoned.
        // Gradual so rotor RPM / sound ramp up and down instead of snapping.
        float target = this.getPassengers().isEmpty() ? 0.0F : (this.controlState.throttleUp ? 1.0F : 0.5F);
        this.enginePower += (target - this.enginePower) * 0.05F;
        this.entityData.set(DATA_THROTTLE, this.enginePower);

        // Landing gear: deployed (0) on the ground, retracting toward 90 once airborne. Cosmetic; planes with an
        // AddPartLG list animate it, others just carry the value.
        float gearTarget = this.onGround() ? 0.0F : 90.0F;
        this.gearAngle += (gearTarget - this.gearAngle) * 0.12F;
        this.entityData.set(DATA_GEAR, this.gearAngle);
    }

    /** Run the per-type flight controller for one server tick with the rider's control input. */
    protected abstract void tickPhysics(ControlInput in);
}
