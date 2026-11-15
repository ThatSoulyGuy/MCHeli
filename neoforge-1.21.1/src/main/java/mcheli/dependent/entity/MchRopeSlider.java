package mcheli.dependent.entity;

import javax.annotation.Nullable;
import mcheli.agnostic.vehicle.Repelling;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The rope slider, the port of {@code aircraft/MCH_EntityHide}: an invisible 1 x 1 entity a crew member rides down one
 * of a repelling vehicle's ropes. It slides at a steady 0.3 a tick with no fall damage, held under its hook (X/Z pinned
 * to the parent's {@link AbstractMchVehicle#ropePos} every tick, the vehicle's CURRENT pose, as {@code getRopePos}), and
 * lets its rider go on the ground, at the rope's end ({@link Repelling#sliderReleased}), or as soon as the vehicle
 * stops repelling. It ends with its rider.
 *
 * <p>Synced: the rope index ({@link #DATA_ROPE_INDEX}, the reference DataWatcher 30) and the parent vehicle's entity id
 * ({@link #DATA_PARENT}, DataWatcher 31), from which a client resolves the parent. The parent, the rider still to mount
 * and the client interpolation are transient. The entity is saved with no fields of its own (the reference wrote
 * none, :97-101): a 1.21 passenger is only saved through a vehicle that is, so a slider that did not save would delete
 * its rider when the chunk unloads. After a reload it has no parent, so it falls and lets go on landing (the
 * reference's {@code ac == null} path).
 *
 * <p>Collision is the reference's ({@code getBoundingBox}, :76-78, and its own {@code getCollidingBoundingBoxes},
 * :245-288): other entities collide with it, and it lands on blocks and on every entity that is not living (the
 * reference also skipped its seats and hitboxes, which the port does not have as entities); it passes through mobs and
 * players. It cannot be hurt (:88-91), right-clicking it does nothing (vanilla's {@code interact} pass, reference
 * {@code interactFirst} false, :108-111), and it makes no step sounds (:68-70). Vanilla does not push a ridden entity,
 * where the 1.7.10 one was pushed while ridden; under a rope it is re-pinned every tick anyway.
 *
 * <p>Deliberate divergence: the network cadence is vanilla's 3-tick update with vanilla-style client interpolation (as
 * the port's vehicles and the parachute), not the reference's 10-tick update (MCH_MOD:221) with its own 10-step lerp
 * and client extrapolation (:126-139, :199-219).
 *
 * <p>Position: a 1.21 entity position is its box bottom; the 1.7.10 {@code posY} was the box bottom + 0.5
 * ({@code yOffset}, :43). Wherever the reference read {@code posY}, this reads {@code getY() + }{@link #Y_OFFSET}.
 */
public class MchRopeSlider extends Entity {

    /** Reference DataWatcher 30 (:56, :113-123): which of the parent's ropes it slides down, -1 none. Id 8, the first after
     *  vanilla {@code Entity}'s own. */
    private static final EntityDataAccessor<Integer> DATA_ROPE_INDEX =
        SynchedEntityData.defineId(MchRopeSlider.class, EntityDataSerializers.INT);
    /** Reference DataWatcher 31 (:59, :152-154): the parent vehicle's entity id, 0 none. Id 9. */
    private static final EntityDataAccessor<Integer> DATA_PARENT =
        SynchedEntityData.defineId(MchRopeSlider.class, EntityDataSerializers.INT);

    /** The 1.7.10 {@code yOffset} (:43, half the height): the reference {@code posY} above the box bottom. */
    public static final double Y_OFFSET = 0.5;
    /** A mob's feet above the box bottom: the reference rider sat at {@code posY + getMountedYOffset() (-0.3) +
     *  getYOffset()} (:84-86, the default {@code updateRiderPosition}), and a mob's {@code getYOffset} was 0. */
    public static final double MOB_FEET = Y_OFFSET - 0.3;
    /** A player's feet above the box bottom (negative: below it): a 1.7.10 server player's {@code getYOffset} was -0.5. */
    public static final double PLAYER_FEET = Y_OFFSET - 0.3 - 0.5;

    /** The entity that mounts on the first server tick (reference {@code user}, :25, :150-156); transient. */
    @Nullable
    private Entity user;
    /** The vehicle whose rope this is (reference {@code ac}); transient: a client resolves it from
     *  {@link #DATA_PARENT}, and a reloaded slider has none. */
    @Nullable
    private AbstractMchVehicle parent;
    /** Server: the rider was removed (died, despawned) while riding; the slider follows it next tick. */
    private boolean riderRemoved;
    /** Server: the slider itself is letting its rider go, who stays where he hangs ({@link #release}). */
    private boolean releasing;

    // Client interpolation toward the tracker's position (vanilla-style, see the class comment).
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private float lerpYRot;
    private float lerpXRot;

    public MchRopeSlider(EntityType<? extends MchRopeSlider> type, Level level) {
        super(type, level);
        this.blocksBuilding = true; // reference preventEntitySpawning (:42)
    }

    /**
     * Spawn a slider for rope {@code ropeIndex} of {@code parent} whose box bottom is at {@code (x, yBottom, z)}, at
     * rest with no fall distance (reference {@code unmountEntityRepelling}, EA:1879-1890: the 1.7.10 slider's
     * {@code posY} was the rider's feet, its box bottom 0.5 lower), and {@code user} to mount it on its first tick. The
     * parent's id is synced from the spawn on (the reference wrote it on the first tick, :152-154, which no player could
     * tell apart).
     */
    public static MchRopeSlider deploy(ServerLevel level, double x, double yBottom, double z, AbstractMchVehicle parent,
                                       @Nullable Entity user, int ropeIndex) {
        MchRopeSlider s = new MchRopeSlider(MchRegistries.ROPE_SLIDER.get(), level);
        s.setPos(x, yBottom, z);
        s.xo = x;
        s.yo = yBottom;
        s.zo = z;
        s.setDeltaMovement(Vec3.ZERO);
        s.fallDistance = 0.0F;
        s.parent = parent;
        s.user = user;
        s.entityData.set(DATA_ROPE_INDEX, ropeIndex);
        s.entityData.set(DATA_PARENT, parent.getId());
        level.addFreshEntity(s);
        return s;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ROPE_INDEX, -1);
        builder.define(DATA_PARENT, 0);
    }

    /** Which of the parent's ropes this slides down, -1 none (synced; reference {@code getRopeIndex}, :117-119). */
    public int ropeIndex() {
        return this.entityData.get(DATA_ROPE_INDEX);
    }

    /** The synced parent vehicle id, 0 none (diagnostics). */
    public int parentId() {
        return this.entityData.get(DATA_PARENT);
    }

    /** The vehicle whose rope this is, or null (none, not resolved yet on a client, or after a reload). */
    @Nullable
    public AbstractMchVehicle parentVehicle() {
        return this.parent;
    }

    /** Diagnostics: the entity that mounts on the first server tick it is free, until then; null afterwards and on a
     *  client. */
    @Nullable
    public Entity pendingUser() {
        return this.user;
    }

    /** Reference {@code onUpdate} (:149-197). */
    @Override
    public void tick() {
        super.tick();
        // Reference :150-156: the spawner's entity mounts on the first server tick it is free.
        if (!this.level().isClientSide && this.user != null) {
            if (this.user.isRemoved()) {
                this.user = null;
            } else if (this.user.getVehicle() == null) {
                this.user.startRiding(this, true); // forced: a rider that just left a vehicle is on boarding cooldown
                this.user = null;
            }
        }
        // Reference :158-166: a client finds the parent by its synced id.
        if (this.level().isClientSide && this.parent == null) {
            int id = this.entityData.get(DATA_PARENT);
            if (id > 0 && this.level().getEntity(id) instanceof AbstractMchVehicle v) {
                this.parent = v;
            }
        }
        // Defensive: a removed parent (unloaded, discarded) is forgotten; the reference kept pinning to a dead aircraft.
        if (this.parent != null && this.parent.isRemoved()) {
            this.parent = null;
        }

        // Reference :171-175: never any fall damage on the rope, for the slider or its rider.
        this.fallDistance = 0.0F;
        Entity rider = getFirstPassenger();
        if (rider != null) {
            rider.fallDistance = 0.0F;
        }

        if (this.parent != null) {
            // Reference :177-180: the vehicle stopped repelling, so every slider lets go where it is. A client waits
            // for the server's removal (the reference also killed its client copy).
            if (!this.level().isClientSide && !this.parent.isRepelling()) {
                release(false);
                return;
            }
            // Reference :183-188: held under the hook, the vehicle's current pose.
            int idx = ropeIndex();
            if (idx >= 0) {
                Vec3 r = this.parent.ropePos(idx);
                setPos(r.x, getY(), r.z);
            }
        }

        if (this.level().isClientSide) {
            tickClient();
        } else {
            tickServer();
        }
    }

    /** Reference {@code onUpdateServer} (:221-243). */
    private void tickServer() {
        Vec3 m = getDeltaMovement();
        setDeltaMovement(m.x, m.y - (onGround() ? 0.01 : 0.03), m.z);
        if (onGround()) {
            release(true); // reference onGroundAndDead
            return;
        }
        move(MoverType.SELF, getDeltaMovement());
        Vec3 a = getDeltaMovement();
        setDeltaMovement(a.x * 0.95, a.y * 0.9, a.z * 0.95);
        // Reference :230-236: at the rope's end, the server's rope length.
        int idx = ropeIndex();
        if (this.parent != null && idx >= 0
                && Repelling.sliderReleased(getY() + Y_OFFSET, this.parent.ropePos(idx).y, this.parent.ropesLength())) {
            release(true); // reference onGroundAndDead
            return;
        }
        // Reference :238-241: a removed rider (dead, despawned) takes the slider with it. In 1.21 a removed entity has
        // already left its vehicle by now (Entity.setRemoved stops riding), so removePassenger noted it.
        Entity rider = getFirstPassenger();
        if (this.riderRemoved || rider != null && rider.isRemoved()) {
            ejectPassengers();
            discard();
        }
    }

    /**
     * Let the rider go and end. {@code lift} is the reference {@code onGroundAndDead} (:440-444), on the ground and at
     * the rope's end: the slider first rises 0.5 and puts its rider there. The rider then stays where it hangs (the
     * 1.7.10 rider of a dead entity kept its place), rather than vanilla's dismount to the top of the box.
     */
    private void release(boolean lift) {
        Entity rider = getFirstPassenger();
        if (lift) {
            setPos(getX(), getY() + 0.5, getZ());
            if (rider != null) {
                positionRider(rider);
            }
        }
        this.releasing = true;
        ejectPassengers();
        discard();
    }

    /** Vanilla's dismount point (the top of the box, as a 1.7.10 {@code mountEntity(null)} put a rider who got off
     *  himself), except while the slider lets its rider go, who stays where he hangs. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return this.releasing ? passenger.position() : super.getDismountLocationForPassenger(passenger);
    }

    /** Reference {@code onUpdateClient} (:199-219), with vanilla-style interpolation (see the class comment). */
    private void tickClient() {
        if (this.lerpSteps > 0) {
            lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ, this.lerpYRot,
                this.lerpXRot);
            this.lerpSteps--;
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpXRot = xRot;
        this.lerpSteps = steps + 2;
    }

    @Override public double lerpTargetX() { return this.lerpSteps > 0 ? this.lerpX : getX(); }
    @Override public double lerpTargetY() { return this.lerpSteps > 0 ? this.lerpY : getY(); }
    @Override public double lerpTargetZ() { return this.lerpSteps > 0 ? this.lerpZ : getZ(); }
    @Override public float lerpTargetXRot() { return this.lerpSteps > 0 ? this.lerpXRot : getXRot(); }
    @Override public float lerpTargetYRot() { return this.lerpSteps > 0 ? this.lerpYRot : getYRot(); }

    /**
     * Reference {@code updateRiderPosition} (the 1.7.10 default; the class's own {@code _updateRiderPosition}, :446-453,
     * was never called): straight under the slider, a mob's feet 0.2 above the box bottom and a player's 0.3 below it
     * ({@link #MOB_FEET}, {@link #PLAYER_FEET}). The feet are placed exactly, without vanilla's passenger attachment
     * points, as the parachute does.
     */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (!hasPassenger(passenger)) {
            return;
        }
        move.accept(passenger, getX(), getY() + (passenger instanceof Player ? PLAYER_FEET : MOB_FEET), getZ());
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!this.level().isClientSide && passenger.isRemoved()) {
            this.riderRemoved = true; // reference :238 riddenByEntity.isDead, see tickServer
        }
    }

    /** Reference {@code attackEntityFrom} false (:88-91). */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    /** Reference {@code canBeCollidedWith = !isDead} (:93-95): a target for clicks, rounds and arrows. */
    @Override public boolean isPickable() { return !isRemoved(); }
    /** Reference {@code getBoundingBox} (:76-78): solid to other entities, like a boat. */
    @Override public boolean canBeCollidedWith() { return true; }
    /** Reference {@code getCollidingBoundingBoxes} (:268-283): every entity that is not living, and never its rider. */
    @Override public boolean canCollideWith(Entity other) {
        return !(other instanceof LivingEntity) && !isPassengerOfSameVehicle(other);
    }
    /** Reference {@code canBePushed} (:80-82). */
    @Override public boolean isPushable() { return true; }
    /** Reference {@code canTriggerWalking} false (:68-70). */
    @Override protected Entity.MovementEmission getMovementEmission() { return Entity.MovementEmission.NONE; }
    /**
     * The slider itself never rides anything: a 1.21 moving minecart mounts every pushable non-player entity it
     * brushes (an unforced {@code startRiding}), where the 1.7.10 cart took only living entities. A forced mount skips
     * this test.
     */
    @Override protected boolean canRide(Entity vehicle) { return false; }

    /** Reference {@code readEntityFromNBT} (:100-101): nothing. */
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    /** Reference {@code writeEntityToNBT} (:97-98): nothing. */
    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
