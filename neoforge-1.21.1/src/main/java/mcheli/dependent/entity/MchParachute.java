package mcheli.dependent.entity;

import javax.annotation.Nullable;
import mcheli.agnostic.parachute.ParachuteFlight;
import mcheli.agnostic.value.Vec3d;
import mcheli.dependent.particle.MchSmokeOptions;
import mcheli.dependent.port.NeoWorldView;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The parachute, the port of {@code parachute/MCH_EntityParachute}: a 1.5 x 0.6 entity its rider hangs under, falling
 * at a steady 0.3 a tick that keeps the fall distance near 2. Four kinds by {@link #DATA_TYPE} (the reference
 * DataWatcher 31, {@link ParachuteFlight}{@code .TYPE_*}): 1 the hand-held parachute ({@code MchParachuteItem}), 2 the
 * ejection seat, which rises on its launch and opens only once it falls, 3 the crew drop, 0 none (draws nothing).
 *
 * <p>The server simulates it (reference {@code onUpdateServer}, :213-317, the arithmetic in {@link ParachuteFlight});
 * the rider never controls it, so {@code getControllingPassenger} stays vanilla's {@code null}. A living rider steers
 * forward with W along its own yaw. It ends after its 6th tick on the ground, on touching water (an ejection seat with
 * a rider over a pool of more than 37 water cells leaves its rider in a boat), or one tick after its rider is removed.
 *
 * <p>Collision is the reference's boat-style solid box ({@code getBoundingBox}, :83-85, and
 * {@code getCollisionBox(other)} = the other's box, :79-81): other entities collide with it, and it lands on any
 * entity below it, mobs and items included, but never on its own rider. It cannot be hurt (:96-98), right-clicking it
 * does nothing (vanilla's {@code interact} pass, reference {@code interactFirst} false, :399-401), and it makes no step
 * sounds (:62-64).
 *
 * <p>Deliberate divergence: the network cadence is vanilla's 3-tick update with vanilla-style client interpolation (as
 * the port's vehicles), not the reference's 10-tick update (MCH_MOD:216) with its own 10-step lerp and client
 * extrapolation (:105-122, :164-187). The client motion is the synced velocity, which is all the open test and the
 * smoke trail read.
 *
 * <p>Position: a 1.21 entity position is its box bottom; the 1.7.10 {@code posY} was the box bottom + 0.3
 * ({@code yOffset}, :45-46). Wherever the reference read {@code posY}, this reads {@code getY() + }{@link #Y_OFFSET}.
 */
public class MchParachute extends Entity {

    /** Reference DataWatcher 31 (:67-77): the byte TYPE. Id 8, the first after vanilla {@code Entity}'s own. */
    private static final EntityDataAccessor<Byte> DATA_TYPE =
        SynchedEntityData.defineId(MchParachute.class, EntityDataSerializers.BYTE);

    /** The 1.7.10 {@code yOffset} (:46, half the 0.6 height): the reference {@code posY} above the box bottom. */
    public static final double Y_OFFSET = 0.3;

    /** The entity that mounts on the first server tick (reference {@code user}, :39, :138-142); transient. */
    @Nullable
    private Entity user;
    /** The server steering multiplier and ground count (reference :26, :40); transient, as in the reference. */
    private final ParachuteFlight flight = new ParachuteFlight();
    /** Server: the rider was removed (died, despawned) while riding; the parachute follows it next tick. */
    private boolean riderRemoved;

    // Client interpolation toward the tracker's position (vanilla-style, see the class comment).
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private float lerpYRot;
    private float lerpXRot;

    public MchParachute(EntityType<? extends MchParachute> type, Level level) {
        super(type, level);
        this.blocksBuilding = true; // reference preventEntitySpawning (:44)
    }

    /**
     * Spawn a parachute whose box bottom is at {@code (x, yBottom, z)} (the reference {@code (world, x, y, z)}
     * constructor, :51-60, which put the box bottom at {@code y}), with the given yaw, motion and fall distance, and
     * {@code user} to mount it on its first tick. The hand-held item, the ejection seat, the crew drop and seat
     * parachuting all spawn through this.
     */
    public static MchParachute deploy(ServerLevel level, double x, double yBottom, double z, int type,
                                      @Nullable Entity user, float yaw, Vec3 motion, float fallDistance) {
        MchParachute p = new MchParachute(MchRegistries.PARACHUTE.get(), level);
        p.setPos(x, yBottom, z);
        p.xo = x;
        p.yo = yBottom;
        p.zo = z;
        p.setYRot(yaw);
        p.yRotO = yaw;
        p.setDeltaMovement(motion);
        p.fallDistance = fallDistance;
        p.user = user;
        p.setParachuteType(type);
        level.addFreshEntity(p);
        return p;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TYPE, (byte) 0);
    }

    /** The kind ({@link ParachuteFlight}{@code .TYPE_*}), synced (reference {@code getType}, :75-77). */
    public int parachuteType() {
        return this.entityData.get(DATA_TYPE);
    }

    public void setParachuteType(int type) {
        this.entityData.set(DATA_TYPE, (byte) type);
    }

    /** Reference {@code isOpenParachute} (:372-374), on either side; a client reads the synced velocity. */
    public boolean isOpenParachute() {
        return ParachuteFlight.isOpen(parachuteType(), getDeltaMovement().y);
    }

    /** The server steering multiplier (0.07 to 0.35); a client always reads 0.07. */
    public double speedMultiplier() {
        return this.flight.speedMultiplier();
    }

    /** Diagnostics: the entity that mounts on the first server tick it is free (reference {@code user}), until then;
     *  null afterwards, on a client, and for a parachute deployed with nobody. */
    @Nullable
    public Entity pendingUser() {
        return this.user;
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 m = getDeltaMovement();
        // Reference :134-136, both sides, with the motion the last tick left.
        this.fallDistance = ParachuteFlight.dampedFallDistance(ParachuteFlight.isOpen(parachuteType(), m.y), m.y,
            this.tickCount, this.fallDistance);

        // Reference :138-142: the spawner's entity mounts on the first server tick it is free.
        if (!this.level().isClientSide && this.user != null) {
            if (this.user.isRemoved()) {
                this.user = null;
            } else if (this.user.getVehicle() == null) {
                this.user.startRiding(this, true); // forced: a rider that just left a vehicle is on boarding cooldown
                setYRot(this.user.getYRot());
                this.yRotO = getYRot();
                this.user = null;
            }
        }

        // Reference :147-155 (xo/yo/zo, the reference prevPos, were set by the level before this tick). Deliberate
        // divergence: the reference also killed the client copy on touching water; here the client waits for the
        // server's removal, which follows within the tick (the client runs no physics that could leave it behind).
        if (!this.level().isClientSide && inWaterBand()) {
            onWaterSetBoat();
            ejectPassengers(); // a type 1 or 3 rider (or one without a boat) is left in the water
            discard();
            return;
        }

        if (this.level().isClientSide) {
            tickClient();
        } else {
            tickServer();
        }
    }

    /** Reference {@code onUpdateServer} (:213-317). */
    private void tickServer() {
        int type = parachuteType();
        Entity rider = getFirstPassenger();
        boolean living = rider instanceof LivingEntity;
        float forward = rider instanceof LivingEntity le ? le.zza : 0.0F; // reference moveForward
        float riderYaw = rider != null ? rider.getYRot() : 0.0F;
        Vec3 m = getDeltaMovement();
        ParachuteFlight.Step step = this.flight.preMove(type, this.tickCount, onGround(), new Vec3d(m.x, m.y, m.z),
            living, forward, riderYaw);
        setDeltaMovement(step.motion().x(), step.motion().y(), step.motion().z());
        if (step.landed()) {
            landAndRemove();
            return;
        }

        move(MoverType.SELF, getDeltaMovement());
        Vec3 after = getDeltaMovement();
        Vec3d damped = ParachuteFlight.postMove(type, this.tickCount, new Vec3d(after.x, after.y, after.z));
        setDeltaMovement(damped.x(), damped.y(), damped.z());
        setXRot(0.0F);
        setYRot(ParachuteFlight.nextYaw(getYRot(), rider != null, riderYaw, this.xo, this.zo, getX(), getZ()));

        // Reference :303-311: push every other parachute in the box grown by 0.2, and be pushed back.
        Entity riderNow = getFirstPassenger();
        for (Entity e : this.level().getEntities(this, getBoundingBox().inflate(0.2, 0.0, 0.2),
                o -> o instanceof MchParachute && o != riderNow)) {
            double[] d = ParachuteFlight.pushApart(e.getX() - getX(), e.getZ() - getZ());
            e.push(d[0], 0.0, d[1]);
            push(-d[0], 0.0, -d[1]);
        }

        // Reference :313-316: a removed rider (dead, despawned) takes the parachute with it. In 1.21 a removed entity
        // has already left its vehicle by now (Entity.setRemoved stops riding), so removePassenger noted it.
        if (this.riderRemoved || riderNow != null && riderNow.isRemoved()) {
            ejectPassengers();
            discard();
        }
    }

    /**
     * Reference {@code onGroundAndDead} (:319-323) on the 6th ground tick: the rider gets off and the parachute is
     * gone. The reference first raised {@code posY} by 1.2 and re-placed the rider there; here vanilla's dismount
     * puts the rider on top of the box (0.6 up), a cosmetic difference of a few tenths of a block.
     */
    private void landAndRemove() {
        ejectPassengers();
        discard();
    }

    /**
     * Reference {@code onWaterSetBoat} (:325-370): only an ejection seat with a rider. The probe cell is the reference
     * {@code (int)(pos + 0.5)} with floor rounding and the reference {@code posY} (box bottom + 0.3); see
     * {@link ParachuteFlight#boatY}. The boat stands with its box bottom one above the water cell, at the cell
     * corner, as the reference placed it.
     */
    private void onWaterSetBoat() {
        Entity rider = getFirstPassenger();
        if (parachuteType() != ParachuteFlight.TYPE_SEAT || rider == null) {
            return;
        }
        int px = Mth.floor(getX() + 0.5);
        int py = Mth.floor(getY() + Y_OFFSET + 0.5);
        int pz = Mth.floor(getZ() + 0.5);
        int waterY = ParachuteFlight.boatY(new NeoWorldView(this.level()), px, py, pz);
        if (waterY == Integer.MIN_VALUE) {
            return;
        }
        Boat boat = new Boat(this.level(), px, waterY + 1.0, pz);
        // Reference :362 set the boat's yaw to the parachute's minus 90: the 1.7.10 boat's yaw was 90 degrees off the
        // entity convention (EntityBoat headed by atan2(dz, dx); ItemBoat placed a boat at the player's yaw - 90). The
        // 1.21 boat uses the entity convention, so the same heading is the parachute's own yaw.
        boat.setYRot(getYRot());
        boat.yRotO = getYRot();
        this.level().addFreshEntity(boat);
        rider.startRiding(boat, true);
    }

    /**
     * Reference :147-152: the 1.7.10 {@code World.isAABBInMaterial(water)} over the band from 0.125 below the box
     * bottom to 0.005 below it, which also tests each water cell's surface height ({@code y + 1 - meta/8}, i.e.
     * {@code y + amount/8} in 1.21) against the band's bottom. Not {@code NeoWorldView.isAABBInWater}, which skips the
     * height test.
     */
    private boolean inWaterBand() {
        AABB b = getBoundingBox();
        double y1 = b.minY + (b.maxY - b.minY) * 0.0 / 5.0 - 0.125;
        double y2 = b.minY + (b.maxY - b.minY) * 1.0 / 5.0 - 0.125;
        int x0 = Mth.floor(b.minX);
        int x1 = Mth.floor(b.maxX + 1.0);
        int yA = Mth.floor(y1);
        int yB = Mth.floor(y2 + 1.0);
        int z0 = Mth.floor(b.minZ);
        int z1 = Mth.floor(b.maxZ + 1.0);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = x0; x < x1; x++) {
            for (int y = yA; y < yB; y++) {
                for (int z = z0; z < z1; z++) {
                    FluidState f = this.level().getFluidState(p.set(x, y, z));
                    if (f.is(FluidTags.WATER) && y + f.getAmount() / 8.0 >= y1) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Reference {@code onUpdateClient} (:164-211), with vanilla-style interpolation (see the class comment). */
    private void tickClient() {
        if (this.lerpSteps > 0) {
            lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ, this.lerpYRot,
                this.lerpXRot);
            this.lerpSteps--;
            Entity rider = getFirstPassenger();
            if (rider != null) {
                setYRot(rider.yRotO % 360.0F); // reference :175-177 (setRotation's % 360)
            }
        }

        // Reference :188-210: a rising, closed seat trails smoke along the last move.
        Vec3 m = getDeltaMovement();
        if (!isOpenParachute() && m.y > 0.01) {
            float color = 0.6F + this.random.nextFloat() * 0.2F;
            double dx = this.xo - getX();
            double dy = this.yo - getY();
            double dz = this.zo - getZ();
            int num = 1 + (int) ((float) Math.sqrt(dx * dx + dy * dy + dz * dz) * 2.0); // MathHelper.sqrt_double
            for (double i = 0.0; i < num; i++) {
                double px = this.xo + (getX() - this.xo) * (i / num) * 0.8;
                double py = this.yo + Y_OFFSET + (getY() - this.yo) * (i / num) * 0.8;
                double pz = this.zo + (getZ() - this.zo) * (i / num) * 0.8;
                // Deliberate divergence: reference bug. :203-205 wrote all three lines to motionX, so every puff moved
                // along X by the Z motion. The puff takes the two horizontal lines on their own axes; the vertical
                // line (motionY * -0.5) stays out, as it effectively was, since a plain puff never damps a vertical
                // motion and would sink a block a tick.
                double vx = m.x * 0.5 + (this.random.nextDouble() - 0.5) * 0.5;
                double vz = m.z * 0.5 + (this.random.nextDouble() - 0.5) * 0.5;
                float a = Math.min(1.0F, 0.8F + this.random.nextFloat()); // GL clamped the 1.7.10 alpha at 1
                // Reference size 5 is the half-size 0.1 * 5; maxAge 0 picks the reference smoke's random life.
                this.level().addParticle(new MchSmokeOptions(0.5F, MchVehicleMath.packArgb(a, color, color, color), 0,
                    MchSmokeOptions.PLAIN), px, py, pz, vx, 0.0, vz);
            }
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
     * Reference {@code updateRiderPosition} (:376-383): 0.1 forward along the parachute's yaw, a player's feet 0.5
     * below the box bottom (on the 1.7.10 server a player's {@code getYOffset} was -0.5) and a mob's at the box bottom
     * ({@code posY - 0.3 + getYOffset()}). The feet are placed exactly, without vanilla's passenger attachment points,
     * as {@code AbstractMchVehicle.positionRider} does.
     */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (!hasPassenger(passenger)) {
            return;
        }
        double x = -Math.sin(getYRot() * Math.PI / 180.0) * 0.1;
        double z = Math.cos(getYRot() * Math.PI / 180.0) * 0.1;
        move.accept(passenger, getX() + x, getY() + (passenger instanceof Player ? -0.5 : 0.0), getZ() + z);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!this.level().isClientSide && passenger.isRemoved()) {
            this.riderRemoved = true; // reference :313 riddenByEntity.isDead, see tickServer
        }
    }

    /**
     * The push a nearby mob gives this parachute (vanilla {@code LivingEntity.pushEntities} calls it): the 1.7.10
     * {@code applyEntityCollision}, which pushed the parachute whoever rode it. Vanilla's own {@code push(Entity)}
     * skips a ridden entity; the other entity keeps vanilla's rule.
     */
    @Override
    public void push(Entity other) {
        if (isPassengerOfSameVehicle(other) || other.noPhysics || this.noPhysics) {
            return;
        }
        double[] d = ParachuteFlight.pushApart(other.getX() - getX(), other.getZ() - getZ());
        if (d[0] == 0.0 && d[1] == 0.0) {
            return;
        }
        push(-d[0], 0.0, -d[1]);
        if (!other.isVehicle() && other.isPushable()) {
            other.push(d[0], 0.0, d[1]);
        }
    }

    /**
     * The canopy reaches about 4.2 out and 8.3 up from the origin (measured from {@code models/parachute2.mqo}), drawn
     * 0.3 above the box bottom, so the small box would cull it while it is still on screen.
     */
    @Override
    public AABB getBoundingBoxForCulling() {
        AABB b = getBoundingBox();
        return new AABB(getX() - 4.5, b.minY - 0.5, getZ() - 4.5, getX() + 4.5, b.minY + 9.0, getZ() + 4.5);
    }

    /** Reference {@code attackEntityFrom} false (:96-98). */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    /** Reference {@code canBeCollidedWith = !isDead} (:100-102): a target for clicks, rounds and arrows. */
    @Override public boolean isPickable() { return !isRemoved(); }
    /** Reference {@code getBoundingBox} (:83-85): solid to other entities, like a boat. */
    @Override public boolean canBeCollidedWith() { return true; }
    /** Reference {@code getCollisionBox(other)} = the other's box (:79-81): it lands on any entity but its rider. */
    @Override public boolean canCollideWith(Entity other) { return !isPassengerOfSameVehicle(other); }
    /** Reference {@code canBePushed} (:87-89). */
    @Override public boolean isPushable() { return true; }
    /** Reference {@code canTriggerWalking} false (:62-64). */
    @Override protected Entity.MovementEmission getMovementEmission() { return Entity.MovementEmission.NONE; }

    /**
     * The parachute itself never rides anything. A 1.21 moving minecart mounts every pushable non-player entity it
     * brushes ({@code AbstractMinecart.tick}'s unforced {@code startRiding}), so a parachute coming down by a rail
     * would ride off on a cart with its rider and never land; the 1.7.10 cart took only living entities
     * ({@code EntityMinecart.applyEntityCollision}). A forced mount ({@code /ride}, a passenger restored from a save)
     * skips this test.
     */
    @Override protected boolean canRide(Entity vehicle) { return false; }

    /** Reference {@code readEntityFromNBT} (:389-391). */
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setParachuteType(tag.getByte("ParachuteModelType"));
    }

    /** Reference {@code writeEntityToNBT} (:385-387). */
    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putByte("ParachuteModelType", (byte) parachuteType());
    }
}
