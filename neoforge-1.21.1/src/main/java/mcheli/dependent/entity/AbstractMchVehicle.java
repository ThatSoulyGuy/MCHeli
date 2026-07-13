package mcheli.dependent.entity;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.aircraft.MCH_SeatInfo;
import mcheli.agnostic.physics.MCH_BoundingBox;
import mcheli.agnostic.sim.ControlInput;
import mcheli.agnostic.sim.RotationSolver;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.util.MCH_LowPassFilterFloat;
import mcheli.agnostic.weapon.MCH_WeaponBallistics;
import mcheli.agnostic.weapon.MCH_WeaponInfo;
import mcheli.agnostic.weapon.MCH_WeaponInfoManager;
import mcheli.agnostic.weapon.VehicleWeapons;
import mcheli.agnostic.weapon.WeaponSlot;
import mcheli.dependent.control.MchControlState;
import mcheli.dependent.control.MchControllable;
import mcheli.dependent.particle.MuzzleFxOptions;
import mcheli.dependent.port.NeoEntityRef;
import mcheli.dependent.registry.MchSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
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
    /** Index of the selected weapon in {@link #weapons} (synced for HUD/feedback); -1 == vehicle has no weapons. */
    private static final EntityDataAccessor<Integer> DATA_WEAPON =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.INT);
    /** Rounds left in the selected weapon's current magazine (synced for the HUD ammo counter); -1 == none/infinite. */
    private static final EntityDataAccessor<Integer> DATA_AMMO =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.INT);
    /** Reload/cooldown progress 0..1 of the selected weapon (synced for the HUD cooldown bar); 0 == ready to fire. */
    private static final EntityDataAccessor<Float> DATA_RELOAD =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.FLOAT);
    /** True while a weapon RECENTLY fired (within {@code firedCooldownTicks}) — synced so the gatling barrel spins on
     *  every client (the port of the reference {@code useWeaponStat}/{@code MCH_WeaponSet.isUsed} bit). */
    private static final EntityDataAccessor<Boolean> DATA_FIRING =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.BOOLEAN);
    /** Accumulated damage (reference {@code damageTaken}); HP = maxHp - this. The single health value that must sync
     *  (the HUD renders client-side and derives hp/hp_rto/HP_PER from it); maxHp is config the client already holds. */
    private static final EntityDataAccessor<Integer> DATA_DAMAGE_TAKEN =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.INT);
    /** Destroyed/wreck flag (reference {@code isDestroyed()==despawnCount>0}); synced so the client renderer darkens
     *  and tumbles the wreck and gates interaction. Set true once, never cleared (a wreck stays a wreck until discard). */
    private static final EntityDataAccessor<Boolean> DATA_DESTROYED =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.BOOLEAN);
    /** The config name that identifies WHICH vehicle this is (e.g. {@code ah-64}) — synced so the client resolves the
     *  same {@link MCH_AircraftInfo} (model, texture, HP, weapons, hitboxes, seats). One entity CLASS per category
     *  serves every vehicle in that category; this string selects the specific one (reference DataWatcher slot 20). */
    private static final EntityDataAccessor<String> DATA_CONFIG =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.STRING);
    /** Cached resolved config, invalidated whenever {@link #DATA_CONFIG} changes (server set or client sync). */
    private MCH_AircraftInfo resolvedInfo;

    /** Cached lowest model vertex Y (&le;0) for the current config; the renderer lifts the MODEL by {@code -minY} (the
     *  {@code #5} ground-offset fix), so the rider seat + cockpit camera must lift by the same amount to stay ON the
     *  model. Resolved lazily from the parsed model (both sides, classpath-cached), invalidated on config change. */
    private float modelMinY;
    private boolean minYResolved;

    /** Cached lower-cased {@code $weaponN} groups the config hides in the first-person gunner view (HideGM). */
    private java.util.Set<String> hideGmGroups;

    // ---- HP / armor / destruction (server-only transients; NOT synced) ----
    /** Per-part weak-point/armor multiplier for the NEXT hit — set by {@link MchBullet} immediately before {@code hurt()}
     *  (the port's stand-in for the reference {@code lastBBDamageFactor} bounding-box side effect); read+reset in hurt()
     *  and re-zeroed each server tick so a stale factor never leaks. Explosions leave it at 1.0 (whole-body armor). */
    public float lastBBDamageFactor = 1.0F;
    /** MCHeli's OWN damage cooldown (2/1/10/20 for lava/explosion/onFire/destroy). Distinct from vanilla
     *  {@code invulnerableTime} — this class extends {@link Entity} (not LivingEntity), which has no i-frame gate. */
    private int mchTimeSinceHit;
    /** Wreck lifetime countdown (500 ticks ≈ 25 s), decremented each server tick once destroyed, then {@code discard()}. */
    private int despawnCount;
    /** Drives the {@code isExploded()} one-shot gate so a tumbling wreck crash-detonates exactly once. */
    private int damageSinceDestroyed;
    /** Wreck tumble seeds (reference {@code rotDestroyed*}); applied to yaw/pitch/roll each tick while destroyed. */
    private float rotDestroyedYaw;
    private float rotDestroyedPitch;
    private float rotDestroyedRoll;
    /** Wreck yaw-spin scale, seeded from the engine power at death then decayed slowly (reference uses the flight
     *  {@code currentThrottle}, which decays only 0.00125/tick — NOT the fast-spooling abandoned-engine power). */
    private float wreckThrottle;
    /** Lazily-built per-entity copy of {@code info.extraBoundingBox}, refreshed to the live pose each server tick. */
    private MCH_BoundingBox[] extraBoxes;

    // ---- client-only running-gear + gun animation (accumulated from actual forward movement + yaw rate, like the rotor;
    //      NOT synced — position/rotation already interpolate on every client). Drives $wheelN spin/steer, per-side
    //      $track_rollerN spin + crawler-belt scroll (differential on pivot turns), and the $partN gatling barrel. ----
    private float wheelSpin, prevWheelSpin;         // deg [0,360): road-wheel spin about the X axle
    private float wheelSteer, prevWheelSteer;       // [-1,1]: steered-wheel yaw fraction (× the wheel's rotDir degrees)
    private final float[] trackScroll = new float[2], prevTrackScroll = new float[2]; // [0,1) belt scroll per side (0=left,1=right)
    private final float[] rollerSpin = new float[2], prevRollerSpin = new float[2];   // deg [0,360) roller spin per side
    private float barrelSpin, prevBarrelSpin, barrelSpeed; // deg: gatling barrel angle + its ramped spin rate (deg/tick)
    private int firedCooldownTicks;                 // SERVER: ticks since the last shot (publishes DATA_FIRING while >0)
    private double lastTrackX, lastTrackZ;          // last position for the per-tick forward-distance delta
    private float lastTrackYaw;                     // last yaw for the per-tick yaw-rate (track/wheel differential)
    private boolean trackInit;

    private static final float STEER_GAIN = 8.0F;     // yaw-rate (rad/tick) -> [-1,1] steer fraction

    public float wheelSpin() { return this.wheelSpin; }
    public float prevWheelSpin() { return this.prevWheelSpin; }
    public float wheelSteer() { return this.wheelSteer; }
    public float prevWheelSteer() { return this.prevWheelSteer; }
    public float trackScroll(int side) { return this.trackScroll[side & 1]; }
    public float prevTrackScroll(int side) { return this.prevTrackScroll[side & 1]; }
    public float rollerSpin(int side) { return this.rollerSpin[side & 1]; }
    public float prevRollerSpin(int side) { return this.prevRollerSpin[side & 1]; }
    public float barrelSpin() { return this.barrelSpin; }
    public float prevBarrelSpin() { return this.prevBarrelSpin; }
    /** True (synced) while a weapon fired within the last few ticks — drives the gatling barrel spin on every client. */
    public boolean isFiringRecently() { return this.entityData.get(DATA_FIRING); }

    // CLIENT-only aim latch (reference isUsedPlayer/lastRiderYaw/lastRiderPitch, MCH_RenderVehicle:28-35): while ridden
    // the renderer stores the aim here; after dismount the turret/guns HOLD this pose instead of snapping to default.
    private float latchedAimYaw, latchedAimPitch;
    private boolean aimLatched;
    public void latchAim(float yaw, float pitch) { this.latchedAimYaw = yaw; this.latchedAimPitch = pitch; this.aimLatched = true; }
    public boolean hasAimLatch() { return this.aimLatched; }
    public float latchedAimYaw() { return this.latchedAimYaw; }
    public float latchedAimPitch() { return this.latchedAimPitch; }

    // Config-driven weapon loadout (server-authoritative), built lazily from weaponHostInfo(). Net pending weapon-cycle
    // steps: each one-shot switch payload adds ±1, and the whole accumulator is drained on the next server tick, so two
    // presses that batch into a single tick both count instead of coalescing to one.
    private VehicleWeapons weapons;
    private int switchAccum;

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
     * Model-space (blocks) offset of the riding pilot's FEET, before the craft's yaw/pitch/roll rotation — read from
     * the vehicle's CONFIG pilot seat ({@code AddSeat} / {@code seatList[0]}) so every vehicle seats its rider in its
     * own cockpit. {@code feet = seat − 0.5Y}: the reference places the pilot eye at {@code seatY + 1.62 − 0.5}, and a
     * 1.21.1 player's eye is {@code feet + 1.62}, so {@code feet = seat − 0.5}. Returns null (vanilla top-of-hitbox
     * attachment) only when the config has no seat.
     */
    protected Vec3 pilotFeetOffset() {
        MCH_AircraftInfo info = weaponHostInfo();
        if (info == null || info.seatList == null || info.seatList.isEmpty()) {
            return null;
        }
        mcheli.agnostic.value.Vec3d seat = info.seatList.get(0).pos;
        return new Vec3(seat.x(), seat.y() - 0.5, seat.z());
    }


    /**
     * Seats the pilot faithfully — the 1.21.1 port of {@code MCH_EntityAircraft.updateRiderPosition} +
     * {@code getTransformedPosition(...,rotSeat)}. Three composed stages on the config seat's FEET offset
     * ({@code seatY − 0.5}):
     * <ol>
     *   <li><b>rotSeat orbit</b> — a turret-mounted gunner seat (config {@code rotSeat}, e.g. the m1a2's
     *       {@code AddGunnerSeat}) orbits {@code turretPosition} in the X-Z plane by the turret yaw
     *       ({@code riderHeadYaw − bodyYaw}), the SAME {@code Ry(-turretYaw)} the turret renderer applies — so the
     *       rider stays welded to the aiming turret instead of appearing detached;</li>
     *   <li><b>body orientation</b> — {@code Ry(-yaw)·Rx(pitch)·Rz(roll)}, interpolated to match the model renderer
     *       and cockpit camera so a banking craft's seat never drifts;</li>
     *   <li><b>{@code -minY} lift</b> — world-vertical, matching the renderer's model lift, so the rider sits ON the
     *       lifted model rather than {@code |minY|} below it.</li>
     * </ol>
     * Vanilla passenger attachments rotate by yaw only, which is why this override is required. JOML math is common to
     * both sides, so this is dist-safe. {@code getPassengerRidingPosition} adds {@code this.position()} on top.
     */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        MCH_AircraftInfo info = weaponHostInfo();
        if (info == null || info.seatList == null || info.seatList.isEmpty()) {
            return super.getPassengerAttachmentPoint(passenger, dimensions, partialTick);
        }
        MCH_SeatInfo seat = info.seatList.get(0);
        org.joml.Vector3f p = new org.joml.Vector3f(
            (float) seat.pos.x(), (float) (seat.pos.y() - 0.5), (float) seat.pos.z());

        // (1) rotSeat: orbit the seat about turretPosition by the turret yaw, keeping Y (Ry never touches Y). The turret
        // yaw comes from the rider's HEAD yaw (getYHeadRot), exactly as MchTankRenderer/MchGroundVehicleRenderer drive
        // the turret mesh, so seat and turret rotate together.
        if (seat.rotSeat) {
            float headYaw = passenger.getYHeadRot();
            float headYawO = passenger instanceof net.minecraft.world.entity.LivingEntity le ? le.yHeadRotO : headYaw;
            float turretYaw = shortLerpDeg(Mth.wrapDegrees(headYaw - this.getYRot()),
                Mth.wrapDegrees(headYawO - this.yRotO), partialTick);
            org.joml.Vector3f tp = new org.joml.Vector3f(
                (float) info.turretPosition.x(), (float) info.turretPosition.y(), (float) info.turretPosition.z());
            // DELIBERATE divergence: the reference (getTransformedPosition:3335-3337) re-adds turretPosition.xCoord to
            // ALL THREE components (a copy-paste bug — a no-op only when turretPosition is (0,0,0)). The intent is the
            // pure X-Z orbit below; only the merkava_mk4 (TurretPosition z=0.25, rider hidden) differs, by 0.25.
            p.sub(tp).rotateY((float) Math.toRadians(-turretYaw)).add(tp);
        }

        // (2) body orientation, interpolated to match the model renderer + cockpit camera exactly.
        p.rotate(bodyRotation(partialTick));

        // (3) -minY consistency lift (world-vertical, AFTER the body rotation) so the rider matches the lifted model.
        return new Vec3(p.x, (double) p.y - modelMinY(), p.z);
    }

    /** Public view of {@link #getPassengerAttachmentPoint} for the client per-frame rider re-weld (a different package). */
    public Vec3 riderAttachment(Entity passenger, float partialTick) {
        return getPassengerAttachmentPoint(passenger, passenger.getDimensions(passenger.getPose()), partialTick);
    }

    /** Positions the rider at {@code vehiclePos + attachment} EXACTLY — vanilla {@code positionRider} additionally
     *  subtracts the passenger's own vehicle-attachment point ({@code (0,0.6,0)} for players), which sat every rider
     *  0.6 below the reference seat (feet must land at {@code seat.y − 0.5}, {@code updateRiderPosition:3190-3212}). */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (!this.hasPassenger(passenger)) {
            return;
        }
        Vec3 att = getPassengerAttachmentPoint(passenger, passenger.getDimensions(passenger.getPose()), 1.0F);
        move.accept(passenger, this.getX() + att.x, this.getY() + att.y, this.getZ() + att.z);
    }

    /** Stage-2 body rotation {@code Ry(-yaw)·Rx(pitch)·Rz(roll)}, interpolated — the single transform shared by the seat
     *  attachment, the config camera eye, and dismount placement (reference {@code MCH_Lib.RotVec3(-yaw,-pitch,-roll)}:
     *  MC {@code rotateAroundX(a) ≡ JOML rotateX(-a)}, so the reference triple maps EXACTLY to this quaternion). */
    private org.joml.Quaternionf bodyRotation(float partialTick) {
        float yaw = Mth.rotLerp(partialTick, this.yRotO, this.getYRot());
        float pitch = Mth.lerp(partialTick, this.xRotO, this.getXRot());
        float roll = shortLerpDeg(this.getRollAngle(), this.getPrevRollAngle(), partialTick);
        return new org.joml.Quaternionf()
            .rotateY((float) Math.toRadians(-yaw))
            .rotateX((float) Math.toRadians(pitch))
            .rotateZ((float) Math.toRadians(roll));
    }

    /**
     * True when the first-person view renders from the config {@code CameraPosition} instead of the rider's own eye —
     * the reference's render-view-dummy switch: heli/plane/tank use it when the config's {@code CameraPosition} 4th
     * param ({@code alwaysCameraView}) is true (forced true for UAVs); emplacements ALWAYS
     * ({@code MCH_ClientVehicleTickHandler:98} — see the {@link MchGroundVehicle} override). Gunner mode / cameraId
     * cycling are unported seams.
     */
    public boolean usesConfigCameraEye() {
        MCH_AircraftInfo info = weaponHostInfo();
        return info != null && info.alwaysCameraView;
    }

    /** Reference applies camera ROLL (faded by look-away) only for heli/plane/tank riders
     *  ({@code MCH_ClientCommonTickHandler:334-343,:408-415}) — emplacement riders never get camera roll. */
    public boolean cameraRollFade() {
        return true;
    }

    /** Every-tick rider look-pitch clamp {min,max} or null. Emplacements clamp ALWAYS while ridden to the VEHICLE-level
     *  {@code MinRotationPitch/MaxRotationPitch} ({@code MCH_ClientVehicleTickHandler:54-57}); tanks clamp in first
     *  person relative to the hull ({@code MCH_EntityTank.setAngles:1119-1126}); per-seat gunner-mode clamps are a
     *  later seam. */
    public float[] riderPitchClampNow() {
        return null;
    }

    /** True when {@link #riderPitchClampNow} applies only in FIRST person (the reference tank clamp). */
    public boolean riderPitchClampFirstPersonOnly() {
        return false;
    }

    /** True when the clamp range is relative to the hull pitch PROJECTED onto the look direction — reference
     *  {@code MCH_EntityTank.setAngles}: {@code proj = hullPitch·cos(yawDiff) − hullRoll·sin(yawDiff)}. */
    public boolean riderPitchClampHullRelative() {
        return false;
    }

    /**
     * World-space first-person eye for the LOCAL rider, or null when the reference renders from the player itself and
     * the vanilla camera already answers (a free-look vehicle without {@code alwaysCameraView}). Reference:
     * {@code setupAllRiderRenderPosition:3266-3276} (config camera), {@code calcOnTurretPos:3218-3232} (rotSeat camera
     * orbit — pivot {@code (turretX, turretY+camY, turretZ)}, offset rotated by the rider's ABSOLUTE look yaw only,
     * pivot body-rotated: deliberately different math from the seat orbit), {@code updateRiderPosition} (seat eye).
     * {@code CameraPosition} IS the eye — never add the player eye height on top. Both paths carry the port's
     * {@code -minY} lift ({@code CameraPosition} is authored in the same model space as the seats).
     */
    public Vec3 firstPersonEye(Entity rider, float partialTick) {
        MCH_AircraftInfo info = weaponHostInfo();
        if (info == null) {
            return null;
        }
        double vx = Mth.lerp((double) partialTick, this.xo, this.getX());
        double vy = Mth.lerp((double) partialTick, this.yo, this.getY());
        double vz = Mth.lerp((double) partialTick, this.zo, this.getZ());
        org.joml.Quaternionf body = bodyRotation(partialTick);

        if (usesConfigCameraEye() && !info.cameraPosition.isEmpty()) {
            // getCameraPosInfo:685-699 — seat 0, cameraId 0 -> cameraPosition[0]; per-seat camPos/invCamPos never
            // applies to seat 0 (sid>0 only) — a multi-seat seam.
            mcheli.agnostic.value.Vec3d cp = info.cameraPosition.get(0).pos;
            MCH_SeatInfo seat0 = info.seatList == null || info.seatList.isEmpty() ? null : info.seatList.get(0);
            org.joml.Vector3f off;
            if (seat0 != null && seat0.rotSeat) {
                org.joml.Vector3f tp = new org.joml.Vector3f(
                    (float) info.turretPosition.x(),
                    (float) (info.turretPosition.y() + cp.y()),
                    (float) info.turretPosition.z());
                float riderYaw = Mth.rotLerp(partialTick, rider.yRotO, rider.getYRot());
                off = new org.joml.Vector3f((float) cp.x(), (float) cp.y(), (float) cp.z())
                    .sub(tp)
                    .rotateY((float) Math.toRadians(-riderYaw))
                    .add(tp.rotate(body));
            } else {
                off = new org.joml.Vector3f((float) cp.x(), (float) cp.y(), (float) cp.z()).rotate(body);
            }
            return new Vec3(vx + off.x, vy + (double) off.y - modelMinY(), vz + off.z);
        }

        // Seat eye — for BOTH locked view and free-look-without-config-camera: eye = attachment (rotSeat-orbited,
        // body-rotated, -minY-lifted) + Rbody·(0,eyeHeight,0). The eye height rotates WITH the hull (the reference
        // 1.7.10 client-player posY IS the eye and rides getTransformedPosition, so the full 1.62 is body-rotated —
        // a vanilla world-Y eye height hangs off the hull axis on a pitched/rolled tank). The camera mixin overrides
        // POSITION only for a free-look vehicle; the rotation stays the player's own look.
        Vec3 att = getPassengerAttachmentPoint(rider, rider.getDimensions(rider.getPose()), partialTick);
        org.joml.Vector3f up = new org.joml.Vector3f(0.0F, rider.getEyeHeight(), 0.0F).rotate(body);
        return new Vec3(vx + att.x + up.x, vy + att.y + up.y, vz + att.z + up.z);
    }

    /** Config-driven dismount — reference {@code setUnmountPosition:3787-3802}: the config {@code UnmountPosition}
     *  body-transformed, else {@code (seat.x ± 3 outward, 2.0, seat.z)}. */
    @Override
    public Vec3 getDismountLocationForPassenger(net.minecraft.world.entity.LivingEntity rider) {
        MCH_AircraftInfo info = weaponHostInfo();
        if (info == null || info.seatList == null || info.seatList.isEmpty()) {
            return super.getDismountLocationForPassenger(rider);
        }
        org.joml.Vector3f local;
        if (info.unmountPosition != null) {
            local = new org.joml.Vector3f((float) info.unmountPosition.x(),
                (float) info.unmountPosition.y(), (float) info.unmountPosition.z());
        } else {
            mcheli.agnostic.value.Vec3d s = info.seatList.get(0).pos;
            float x = s.x() >= 0 ? (float) s.x() + 3.0F : (float) s.x() - 3.0F;
            local = new org.joml.Vector3f(x, 2.0F, (float) s.z());
        }
        local.rotate(bodyRotation(1.0F));
        return this.position().add(local.x, local.y, local.z);
    }

    /** Lowest model vertex Y (&le;0) for the current config — the value the renderer lifts the model by ({@code -minY}).
     *  Sourced once from the parsed model via the classpath-cached {@link NeoResourceSource} (works both sides), then
     *  cached until the config changes; 0 when there is no model. Public so the cockpit {@code CameraMixin} can apply
     *  the same lift to the first-person eye. */
    public float modelMinY() {
        if (!this.minYResolved) {
            this.modelMinY = 0.0F;
            String name = configName();
            if (name != null && !name.isEmpty()) {
                mcheli.agnostic.spi.ModelHandle h =
                    new mcheli.dependent.port.NeoResourceSource().loadModel(modelDir() + "/" + name);
                if (h instanceof mcheli.agnostic.model.MchModel m) {
                    this.modelMinY = m.minY;
                }
            }
            this.minYResolved = true;
        }
        return this.modelMinY;
    }

    /** The model resource sub-directory for this category ({@code helicopters}/{@code planes}/{@code tanks}/{@code
     *  vehicles}), mirroring the renderer's {@code categoryDir}, so {@link #modelMinY()} resolves the same model file. */
    protected abstract String modelDir();

    /** True when the config hides the seated rider ({@code HideEntity=true}: the Phalanx dome / closed tank turret). The
     *  reference makes the pilot fully invisible; the port's client render hook suppresses the rider accordingly. */
    public boolean hidesRider() {
        MCH_AircraftInfo info = weaponHostInfo();
        return info != null && info.hideEntity;
    }

    /** Lower-cased {@code $weaponN} model groups the config hides in the FIRST-PERSON gunner view ({@code HideGM} weapon
     *  parts — e.g. the ec665 chin gun that would fill the cockpit). Cached; empty when no weapon is marked HideGM. The
     *  base renderer excludes these only for the LOCAL pilot in first person. */
    public java.util.Set<String> firstPersonHiddenGroups() {
        if (this.hideGmGroups == null) {
            java.util.Set<String> s = new java.util.HashSet<>();
            MCH_AircraftInfo info = weaponHostInfo();
            if (info != null && info.partWeapon != null) {
                for (MCH_AircraftInfo.PartWeapon pw : info.partWeapon) {
                    // Reference hides a HideGM part ONLY for the weapon's OPERATOR (getWeaponUserByWeaponName): the lone
                    // pilot operates a weapon iff it is canUsePilot. A gunner-seat gun (canUsePilot=false) stays visible.
                    if (pw != null && pw.hideGM) {
                        MCH_AircraftInfo.Weapon wn = info.getWeaponByName(pw.name[0]);
                        if (wn != null && wn.canUsePilot) {
                            s.add(("$" + pw.modelName).toLowerCase(java.util.Locale.ROOT));
                        }
                    }
                }
            }
            this.hideGmGroups = s;
        }
        return this.hideGmGroups;
    }

    /** Short-path angle interpolation in degrees — byte-for-byte {@code MchModelEntityRenderer.calcRot} /
     *  {@code CameraMixin.mcheli$shortLerp} (inlined here so this dist-shared class references no client-only type). */
    private static float shortLerpDeg(float rot, float prevRot, float t) {
        rot = Mth.wrapDegrees(rot);
        prevRot = Mth.wrapDegrees(prevRot);
        if (rot - prevRot < -180.0F) {
            prevRot -= 360.0F;
        } else if (prevRot - rot < -180.0F) {
            prevRot += 360.0F;
        }
        return prevRot + (rot - prevRot) * t;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ROLL, 0.0F);
        builder.define(DATA_THROTTLE, 0.0F);
        builder.define(DATA_SKIN, 0);
        builder.define(DATA_GEAR, 0.0F);
        builder.define(DATA_WEAPON, -1);
        builder.define(DATA_AMMO, -1);
        builder.define(DATA_RELOAD, 0.0F);
        builder.define(DATA_FIRING, false);
        builder.define(DATA_DAMAGE_TAKEN, 0);
        builder.define(DATA_DESTROYED, false);
        builder.define(DATA_CONFIG, "");
    }

    /** The config name identifying this specific vehicle (synced), or "" before it is assigned at spawn. */
    public String configName() { return this.entityData.get(DATA_CONFIG); }

    /** SERVER: assign the vehicle's config by name (call BEFORE {@code addFreshEntity} so the spawn packet carries it,
     *  and before the first tick so the lazy weapon/hitbox caches build against the right config). Invalidates caches. */
    public void setConfigName(String name) {
        if (!this.level().isClientSide) {
            this.entityData.set(DATA_CONFIG, name == null ? "" : name);
        }
        invalidateConfigCaches();
    }

    /** Resolve the concrete config for {@code name} from this category's manager, NEVER null (a bad/unknown name falls
     *  back to a category default so the physics/HP/render never NPE). Subclasses (heli/plane/tank/vehicle) implement it. */
    protected abstract MCH_AircraftInfo resolveInfo(String name);

    /** Drop every config-derived cache so the next access rebuilds against the current {@link #configName()}. */
    private void invalidateConfigCaches() {
        this.resolvedInfo = null;
        this.weapons = null;
        this.hudWeapons = null;
        this.extraBoxes = null;
        this.minYResolved = false;
        this.hideGmGroups = null;
        onConfigChanged();
    }

    /** Hook for subclasses to drop their own config-dependent state (e.g. the control mapping) on a config change. */
    protected void onConfigChanged() {}

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_CONFIG.equals(key)) {
            invalidateConfigCaches(); // the client learned WHICH vehicle this is -> rebuild info/model/weapons/hitboxes
        }
    }

    /** The parsed config for this vehicle (from the synced {@link #configName()}), resolved+cached via the category
     *  manager. Never null — drives HP, weapons, hitboxes, seats, model, texture. */
    protected MCH_AircraftInfo weaponHostInfo() {
        String name = configName();
        if (this.resolvedInfo == null || !name.equals(this.resolvedInfo.name)) {
            this.resolvedInfo = resolveInfo(name);
        }
        return this.resolvedInfo;
    }

    /** Index of the selected weapon (synced), or -1 if this vehicle has no fireable weapons. */
    public int getSelectedWeaponIndex() { return this.entityData.get(DATA_WEAPON); }

    // Client-side lazy weapon list, used only to resolve the synced selected index to a display NAME for the HUD.
    private VehicleWeapons hudWeapons;

    /** The selected weapon's slot (from the synced index), for HUD name/ammo display; null if none. */
    private WeaponSlot selectedHudSlot() {
        if (weaponHostInfo() == null) {
            return null;
        }
        if (this.hudWeapons == null) {
            this.hudWeapons = VehicleWeapons.build(weaponHostInfo(), MCH_WeaponInfoManager::get);
        }
        int idx = getSelectedWeaponIndex();
        return idx >= 0 && idx < this.hudWeapons.size() ? this.hudWeapons.get(idx) : null;
    }

    /** The selected weapon's display name (for the HUD), resolved from the synced index; "" if none. */
    public String selectedWeaponName() {
        WeaponSlot slot = selectedHudSlot();
        if (slot == null) {
            return "";
        }
        return slot.info.displayName != null && !slot.info.displayName.isEmpty() ? slot.info.displayName : slot.weaponName;
    }

    /** Rounds in the selected weapon's current magazine (synced); -1 == no weapon or infinite ammo. */
    public int getSelectedAmmo() { return this.entityData.get(DATA_AMMO); }

    /** The selected weapon's reserve capacity ({@code MaxAmmo}) from config, for the HUD's "mag / reserve"; -1 none. */
    public int getSelectedMaxAmmo() {
        WeaponSlot slot = selectedHudSlot();
        return slot != null ? slot.info.maxAmmo : -1;
    }

    /** The selected weapon's reload/cooldown progress 0..1 (synced), for the HUD {@code reload_time}/{@code reloading}
     *  bar; 0 == ready to fire. */
    public float getSelectedReload() { return this.entityData.get(DATA_RELOAD); }

    /** The selected weapon's remaining cooldown in SECONDS — the synced fraction × the config interval (delay/reload)
     *  ÷ 20 tps — for the HUD's {@code RELOAD_SEC} readout. Computed client-side from the config interval. */
    public float getSelectedReloadSeconds() {
        WeaponSlot slot = selectedHudSlot();
        return slot != null ? getSelectedReload() * slot.reloadIntervalTicks() / 20.0F : 0.0F;
    }

    /** The HUD config name for a seat (0 = pilot), from this vehicle's {@code HUD =} config list, or null. */
    public String hudName(int seat) {
        MCH_AircraftInfo info = weaponHostInfo();
        if (info == null || info.hudList == null || seat < 0 || seat >= info.hudList.size()) {
            return null;
        }
        return info.hudList.get(seat);
    }

    /** SERVER: queue a weapon cycle (+1 next / -1 previous), applied on the next tick. Called by the switch payload;
     *  accumulates so multiple presses in one tick window each register. */
    public void queueWeaponSwitch(int direction) {
        this.switchAccum += direction >= 0 ? 1 : -1;
    }

    /** Landing-gear retract angle 0..90 (deployed..retracted), synced; the renderer interpolates with the prev. */
    public float getGearAngle() { return this.gearAngle; }
    public float getPrevGearAngle() { return this.prevGearAngle; }

    /** Spooled engine power 0..1 (synced). Full when a rider holds throttle-up, idles at 0.5 while ridden, 0 when
     *  abandoned — clients read it for rotor RPM / sound. */
    public float getEnginePower() { return this.entityData.get(DATA_THROTTLE); }

    /** Selected paint-scheme index (synced). The renderer resolves it to a texture via {@link #skinTextureName()}. */
    public int getSkinIndex() { return this.entityData.get(DATA_SKIN); }

    /** Number of selectable skins for this vehicle, from its config texture list. */
    protected int skinCount() {
        MCH_AircraftInfo info = weaponHostInfo();
        return info != null ? Math.max(1, info.getTextureNameCount()) : 1;
    }

    /** Current skin's texture NAME (e.g. {@code ah-64-us-1}) → {@code textures/<dir>/<name>.png}, mapped from the
     *  synced skin index through the config texture list; null keeps the renderer's default. */
    public String skinTextureName() {
        MCH_AircraftInfo info = weaponHostInfo();
        return info != null ? info.getTextureName(getSkinIndex()) : null;
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        // Restore WHICH vehicle this is first (rebuilds info/weapons/hitboxes), then its HP + wreck state.
        setConfigName(tag.getString("Config"));
        setDamageTaken(tag.getInt("AcDamage"));
        this.entityData.set(DATA_DESTROYED, tag.getBoolean("Destroyed"));
        this.despawnCount = tag.getInt("Despawn");
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("Config", configName());
        tag.putInt("AcDamage", getDamageTaken());
        tag.putBoolean("Destroyed", isDestroyed());
        tag.putInt("Despawn", this.despawnCount);
    }

    /** The AABB used for frustum culling. The collision box is a small faithful 2.0×0.7 core, but the MODEL spans the
     *  full rotor/wing (up to ~19 blocks), so inflate generously — else the vehicle pops out of view whenever its tiny
     *  core box leaves the frustum even though the model is on screen. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(16.0);
    }

    @Override public boolean isPickable() { return !this.isRemoved(); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return true; }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (isDestroyed()) {
            return InteractionResult.PASS; // a wreck can't be mounted or skinned (reference interactFirst gates on it)
        }
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

    /** The aircraft info fed to {@link RotationSolver} — this vehicle's live config. */
    protected MCH_AircraftInfo rotationInfo() { return weaponHostInfo(); }

    /** Optional SERVER-side rotation mapping applied before {@link #tickPhysics(ControlInput)} (so the physics reads
     *  the new heading), or null. Currently unused — the tank's hull-yaw moved to the CLIENT ({@link #controlMapping()}
     *  + {@code locksViewToVehicle()==false}) to match the reference and so the turn reaches the driver's own client;
     *  kept as a hook for any future purely server-authoritative rotation. */
    protected RotationSolver.ControlMapping serverRotationMapping() { return null; }

    /** True if this vehicle's orientation is driven by the rider's mouse (heli/plane). */
    public boolean supportsMouseRotation() { return controlMapping() != null; }

    /** True if the rider's VIEW is locked to the vehicle heading (the mouse-hijack aircraft). Ground vehicles (tank)
     *  return false: they steer with A/D but keep the free vanilla mouse-look (the reference aims the turret/camera
     *  independently of the hull), so the client turns the hull WITHOUT the camera lock / cockpit-parent Mixin. */
    public boolean locksViewToVehicle() { return true; }

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
            if (localRider) {
                // Owned rotation is advanced LIVE by MchClientRotation on the render frame. Snapshot the previous
                // yaw/pitch each tick so the renderer's interpolation tracks the turn — the model yaw comes from
                // vanilla's rotLerp(yRotO, getYRot()) and the pitch from lerp(xRotO, getXRot()); those "old" values
                // were only snapshotted server-side (below), so on the client they stayed pinned at the spawn value.
                // (Roll already snapshots prevRollAngle above for both sides.) A first-person aircraft hides this
                // because its cockpit camera never shows the model's own yaw; a free-look tank does not, so its hull
                // looked like it never turned even though getYRot() was changing.
                this.yRotO = this.getYRot();
                this.xRotO = this.getXRot();
            }
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
            updateWheelTrackPhase();
            return;
        }

        // Server-authoritative physics. Snapshot prev pos/rot before moving.
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();

        // HP/wreck bookkeeping (pre-physics): the wreck's ground-slam detonation reads the onGround TRANSITION and the
        // descent speed — deltaMovement.y is zeroed by ground contact during the move, so it must be sampled here.
        boolean prevOnGround = this.onGround();
        double prevMotionY = this.getDeltaMovement().y;
        if (this.mchTimeSinceHit > 0) this.mchTimeSinceHit--;   // MCHeli's own damage cooldown (reference :2121)
        updateExtraBoxes();                                     // refresh the part hitboxes to the current pose

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

        // No vanilla fall damage (reference zeroes fallDistance for the craft AND its riders every tick) — the demo
        // vehicle is a non-Living Entity so it never falls-damages itself, but this keeps a dismounting pilot safe.
        this.fallDistance = 0.0F;
        for (Entity p : this.getPassengers()) {
            p.fallDistance = 0.0F;
        }

        if (isDestroyed()) {
            // Wreck: the flight model produced no lift (its !isDestroyed gate is now false), so it falls; add the
            // per-type tumble, crash-detonate once on the ground-slam, and burn any lingering riders, then despawn.
            this.wreckThrottle = Math.max(0.0F, this.wreckThrottle - 0.00125F); // slow decay (reference currentThrottle)
            applyWreckTumble();
            if (!isExploded() && !prevOnGround && this.onGround() && prevMotionY < -0.2) {
                explosionByCrash(prevMotionY);
                this.damageSinceDestroyed = getMaxHp();
            }
            if (this.tickCount % 20 == 0) {
                for (Entity p : this.getPassengers()) {
                    if (!p.fireImmune()) p.setRemainingFireTicks(100); // 5 s (reference setFire(5))
                }
            }
            if (--this.despawnCount <= 1) {
                this.discard();
                return;
            }
        } else {
            // Alive: attitude-crash into terrain + water-submersion damage (both route through hurt(inWall)).
            collisionGroundDamage();
        }
        this.lastBBDamageFactor = 1.0F; // clear any unread part factor so it can't leak to a later hit (reference :1363)

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

        // Weapons: config-driven loadout (built lazily), cycled by the switch payload and fired while held.
        tickWeapons();
    }

    // ---- config-driven weapons ----

    /** Server tick for the weapon system: build the loadout once, advance fire-control counters, apply a queued
     *  switch, and fire the selected weapon while the trigger is held. */
    private void tickWeapons() {
        if (this.weapons == null) {
            this.weapons = VehicleWeapons.build(weaponHostInfo(), MCH_WeaponInfoManager::get);
            this.entityData.set(DATA_WEAPON, this.weapons.selectedIndex());
        }
        this.weapons.tick();
        if (this.firedCooldownTicks > 0) {
            this.firedCooldownTicks--;
        }

        if (this.switchAccum != 0) {
            int pending = this.switchAccum;
            this.switchAccum = 0;
            if (!this.weapons.isEmpty()) {
                int dir = pending > 0 ? 1 : -1;
                for (int i = 0; i < Math.abs(pending); i++) {
                    this.weapons.cycle(dir);
                }
                this.entityData.set(DATA_WEAPON, this.weapons.selectedIndex());
                notifyPilotWeapon();
            }
        }

        if (this.controlState.fire && !this.getPassengers().isEmpty()) {
            fireSelectedWeapon();
        }

        // Publish the selected weapon's current magazine for the HUD ammo counter.
        WeaponSlot sel = this.weapons.selected();
        this.entityData.set(DATA_AMMO, sel != null ? sel.magazine() : -1);
        this.entityData.set(DATA_RELOAD, sel != null ? sel.reloadFraction() : 0.0F);
        // Publish the "recently fired" bit (the reference useWeaponStat) so the gatling barrel spins on every client.
        this.entityData.set(DATA_FIRING, this.firedCooldownTicks > 0);
    }

    /**
     * Fire ONE shot of the selected weapon this tick, if its fire-control allows. Picks the next mount round-robin,
     * places the muzzle and aims it in world space with the port's orientation convention
     * ({@code Ry(-yaw)·Rx(pitch)·Rz(roll)}, forward = model +Z — the same transform that seats the pilot and renders
     * the model, so muzzles line up with the guns), then spawns a {@link MchBullet} carrying the weapon's real stats
     * and plays the weapon's own report.
     */
    private void fireSelectedWeapon() {
        WeaponSlot slot = this.weapons.selected();
        if (slot == null || !slot.canFire()) {
            return;
        }
        MCH_AircraftInfo.Weapon mount = slot.fireOneShot();
        MCH_WeaponInfo wi = slot.info;
        if (firesRotBarrel(slot)) {
            this.firedCooldownTicks = 4; // reference MCH_WeaponSet.WAIT_CLEAR_USED_COUNT — barrel stays "used" ~4 ticks
        }

        float yaw = this.getYRot();
        float pitch = this.getXRot();
        float roll = this.getRollAngle();

        // Turret vehicles (the tank: free mouse-look, hull steered separately) aim the gun where the RIDER LOOKS, not
        // where the hull points — the reference fires turret weapons along getLastRiderYaw()/Pitch(). Aircraft keep the
        // hull angles (the mouse already aims the whole airframe). getYHeadRot() is the rider's free look yaw.
        if (!this.locksViewToVehicle()) {
            net.minecraft.world.entity.Entity gunner = this.getFirstPassenger();
            if (gunner != null) {
                yaw = gunner.getYHeadRot();
                pitch = gunner.getXRot();
            }
        }

        // Muzzle world position = vehiclePos + R(aim) · mountLocalOffset (turret-mounted muzzle follows the aim).
        org.joml.Quaternionf qBody = new org.joml.Quaternionf()
            .rotateY((float) Math.toRadians(-yaw))
            .rotateX((float) Math.toRadians(pitch))
            .rotateZ((float) Math.toRadians(roll));
        org.joml.Vector3f mpos = new org.joml.Vector3f((float) mount.pos.x(), (float) mount.pos.y(), (float) mount.pos.z());
        qBody.transform(mpos);
        Vec3 muzzle = this.position().add(mpos.x, mpos.y, mpos.z);

        // Per-shot dispersion: the reference perturbs the firing yaw AND pitch by (rand-0.5)*accuracy degrees on each
        // shot (MCH_WeaponSet.use), so sustained fire scatters over a cone instead of a perfect laser.
        float accuracy = wi.accuracy;
        float spreadYaw = accuracy > 0.0F ? (this.random.nextFloat() - 0.5F) * accuracy : 0.0F;
        float spreadPitch = accuracy > 0.0F ? (this.random.nextFloat() - 0.5F) * accuracy : 0.0F;

        // Direction = R(vehicle yaw+mountYaw+spread, pitch+mountPitch+spread, roll) · forward(+Z). The mount's fixed aim
        // offset (from AddWeapon) plus the dispersion are summed onto the body angles as the reference does before
        // RotVec3(0,0,1,...).
        org.joml.Vector3f fwd = new org.joml.Vector3f(0.0F, 0.0F, 1.0F);
        new org.joml.Quaternionf()
            .rotateY((float) Math.toRadians(-(yaw + mount.yaw + spreadYaw)))
            .rotateX((float) Math.toRadians(pitch + mount.pitch + spreadPitch))
            .rotateZ((float) Math.toRadians(roll))
            .transform(fwd);
        Vec3 dir = new Vec3(fwd.x, fwd.y, fwd.z);

        // Ballistics + model, all from config.
        boolean bulletOrRocket = MCH_WeaponBallistics.isBulletOrRocket(wi.type);
        float speed = MCH_WeaponBallistics.initialSpeed(wi.acceleration);
        float accFactor = MCH_WeaponBallistics.accelerationFactor(wi.acceleration, bulletOrRocket);
        float gravity = wi.gravity; // 0 for guns/rockets; negative for bombs
        String model = (wi.bulletModelName != null && !wi.bulletModelName.isEmpty())
            ? wi.bulletModelName : MCH_WeaponBallistics.defaultBulletModel(wi.type);
        int color = packColor(wi.color);

        MchBullet.spawnWeapon(this.level(), muzzle, dir, speed, accFactor, gravity, wi.power, 600, this, model, color, wi);

        // Report: the weapon's own sound at its configured volume, with the reference pitch jitter.
        SoundEvent report = MchSounds.byName(wi.soundFileName);
        if (report != null) {
            float pitchR = wi.soundPitch * (1.0F - wi.soundPitchRandom) + this.random.nextFloat() * wi.soundPitchRandom;
            this.level().playSound(null, muzzle.x, muzzle.y, muzzle.z, report, SoundSource.NEUTRAL,
                wi.soundVolume, pitchR);
        }

        spawnFireCosmetics(muzzle, dir, wi, yaw + mount.yaw, pitch + mount.pitch);
    }

    /**
     * Config-driven fire cosmetics at the barrel — nothing hardcoded per weapon:
     * <ul>
     *   <li><b>Muzzle flash</b>: one {@link MuzzleFxOptions} particle per {@code AddMuzzleFlash} entry, at that entry's
     *       distance, in its exact colour, sized {@code 2·flash.size}, living {@code 1+flash.age} (so the M230's orange
     *       {@code 254,159,84} shows). A weapon with no flash config shows none.</li>
     *   <li><b>Muzzle smoke</b>: {@code num} particles per {@code AddMuzzleFlashSmoke} entry, colour/size/age/count all
     *       from the entry, jittered by its {@code range}.</li>
     *   <li><b>Cartridge</b>: the {@code SetCartridge} model shell, ejected per its config direction/speed.</li>
     * </ul>
     * The reference spawns these client-side; here the server broadcasts them ({@link ServerLevel#sendParticles} /
     * a normal entity) so every nearby player sees them.
     */
    private void spawnFireCosmetics(Vec3 muzzle, Vec3 dir, MCH_WeaponInfo wi, float gunYaw, float gunPitch) {
        if (this.level() instanceof ServerLevel sl) {
            if (wi.listMuzzleFlash != null) {
                for (MCH_WeaponInfo.MuzzleFlash mf : wi.listMuzzleFlash) {
                    Vec3 p = muzzle.add(dir.scale(mf.dist));
                    MuzzleFxOptions fx = new MuzzleFxOptions(packArgb(mf.a, mf.r, mf.g, mf.b), 2.0F * mf.size, 1 + mf.age);
                    sl.sendParticles(fx, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
            if (wi.listMuzzleFlashSmoke != null) {
                for (MCH_WeaponInfo.MuzzleFlash mf : wi.listMuzzleFlashSmoke) {
                    Vec3 p = muzzle.add(dir.scale(mf.dist));
                    double spread = mf.range * 0.5;
                    MuzzleFxOptions fx = new MuzzleFxOptions(packArgb(mf.a, mf.r, mf.g, mf.b), 0.1F * mf.size,
                        Math.max(1, mf.age));
                    // Outward drift ~0.2 (reference motionX/Z = rand*±0.2) so the smoke billows instead of sitting.
                    sl.sendParticles(fx, p.x, p.y, p.z, Math.max(1, mf.num), spread, spread * 0.15, spread, 0.2);
                }
            }
        }
        // Cartridge shell — every parameter from the weapon's SetCartridge config.
        if (wi.cartridge != null) {
            MchCartridge.spawn(this.level(), wi.cartridge, muzzle, gunYaw, gunPitch, this.getDeltaMovement());
        }
    }

    /** Pack four 0..1 colour channels into 0xAARRGGBB (config muzzle-flash colours are already normalized). */
    private static int packArgb(float a, float r, float g, float b) {
        return ((Math.round(a * 255.0F) & 0xFF) << 24) | ((Math.round(r * 255.0F) & 0xFF) << 16)
            | ((Math.round(g * 255.0F) & 0xFF) << 8) | (Math.round(b * 255.0F) & 0xFF);
    }

    /** Pack an {@link mcheli.agnostic.math.MCH_Color} (0..1 floats) into 0xAARRGGBB for the bullet tint. */
    private static int packColor(mcheli.agnostic.math.MCH_Color c) {
        int a = Math.round(c.a * 255.0F) & 0xFF;
        int r = Math.round(c.r * 255.0F) & 0xFF;
        int g = Math.round(c.g * 255.0F) & 0xFF;
        int b = Math.round(c.b * 255.0F) & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Tell the pilot (action-bar) which weapon is now selected. */
    private void notifyPilotWeapon() {
        WeaponSlot slot = this.weapons.selected();
        if (slot == null || this.getPassengers().isEmpty()) {
            return;
        }
        String label = slot.info.displayName != null && !slot.info.displayName.isEmpty()
            ? slot.info.displayName : slot.weaponName;
        Entity rider = this.getPassengers().get(0);
        if (rider instanceof Player p) {
            p.displayClientMessage(Component.literal("Weapon: " + label), true);
        }
    }

    // ==== HP / armor / destruction (faithful port of MCH_EntityAircraft.attackEntityFrom + destroyAircraft) ====

    /** Public read of this vehicle's parsed config (armor/HP/hitboxes), or null; for HUD + diagnostics/self-test. */
    public MCH_AircraftInfo hostInfo() { return weaponHostInfo(); }

    /** Config max HP ({@code MaxHP}), or 100 when this vehicle has no parsed info (reference {@code getMaxHP}). */
    public int getMaxHp() {
        MCH_AircraftInfo info = weaponHostInfo();
        return info != null ? info.maxHp : 100;
    }

    /** Accumulated damage (synced). HP is derived, never stored directly (reference {@code damageTaken}). */
    public int getDamageTaken() { return this.entityData.get(DATA_DAMAGE_TAKEN); }

    /** SERVER: set the accumulated damage, clamped to {@code [0, maxHp]} (reference {@code setDamageTaken}). */
    public void setDamageTaken(int v) {
        if (!this.level().isClientSide) {
            this.entityData.set(DATA_DAMAGE_TAKEN, Mth.clamp(v, 0, getMaxHp()));
        }
    }

    /** Current hit points = {@code maxHp - damageTaken}, floored at 0 (reference {@code getHP}). */
    public int getHp() { return Math.max(0, getMaxHp() - getDamageTaken()); }

    /** True once the craft is a wreck (synced). Weapon damage no-ops from here; only crash/despawn act (reference). */
    public boolean isDestroyed() { return this.entityData.get(DATA_DESTROYED); }

    /** True after a wreck has already crash-detonated — the one-shot gate on {@link #explosionByCrash} (reference). */
    public boolean isExploded() { return isDestroyed() && this.damageSinceDestroyed > getMaxHp() / 10 + 1; }

    /** SERVER: stash the per-part weak-point factor for the imminent {@code hurt()} call (called by {@link MchBullet}). */
    public void setLastBBDamageFactor(float f) { this.lastBBDamageFactor = f; }

    /**
     * The faithful port of {@code MCH_EntityAircraft.attackEntityFrom} (its exact order is load-bearing). All incoming
     * damage — weapons, explosions, lava/fire, crash, water submersion — funnels here. The per-part armor formula
     * asymmetry (a {@code factor<=1} armor plate multiplies BEFORE {@code armorDamageFactor}/{@code armorMinDamage};
     * a {@code factor>1} weak-point multiplies AFTER the min-subtraction, so it can't be zeroed) must not be
     * "simplified". lava (×2..9) and onFire BYPASS the armor block; inFire/cactus do no damage.
     */
    @Override
    public boolean hurt(DamageSource src, float amount) {
        float factor = this.lastBBDamageFactor;   // capture the per-part factor, then reset (reference :809-810)
        this.lastBBDamageFactor = 1.0F;

        if (isInvulnerableTo(src) || isRemoved()) return false;
        if (this.mchTimeSinceHit > 0) return false;                              // MCHeli's own cooldown (reference :819)
        if (src.is(DamageTypes.IN_FIRE) || src.is(DamageTypes.CACTUS)) return false; // no damage (reference :824,:828)
        // Client no-op BEFORE any state mutation (reference returns at isRemote :832, before the cooldown/RNG below) —
        // otherwise a client-side lava/fire baseTick would burn the entity RNG and latch the server-only cooldown.
        if (this.level().isClientSide) return true;

        boolean lava = src.is(DamageTypes.LAVA);
        boolean onFire = src.is(DamageTypes.ON_FIRE);
        if (lava) {
            amount *= this.random.nextInt(8) + 2;                                // ×2..9 (reference :841)
            this.mchTimeSinceHit = 2;
        }
        if (src.is(DamageTypeTags.IS_EXPLOSION)) {
            this.mchTimeSinceHit = 1;                                            // (reference :846)
        } else if (src.getEntity() != null && this.hasPassenger(src.getEntity())) {
            return false;   // a rider can't damage the craft they ride (reference isMountedEntity guard :848)
        }
        if (onFire) this.mchTimeSinceHit = 10;                                    // (reference :852)

        boolean playerBreak = isPlayerBreak(src);
        MCH_AircraftInfo info = weaponHostInfo();

        if (!isDestroyed()) {
            if (!playerBreak && !lava && !onFire && info != null) {               // ARMOR FORMULA (reference :884-908)
                if (amount > info.armorMaxDamage) amount = info.armorMaxDamage;   // (a) cap
                if (factor <= 1.0F) amount *= factor;                            // (b) armor plate, applied BEFORE
                amount *= info.armorDamageFactor;                                // (c)
                amount -= info.armorMinDamage;                                   // (d)
                if (amount <= 0.0F) return false;                               // (e) fully absorbed
                if (factor > 1.0F) amount *= factor;                            // (f) weak-point, applied AFTER
            }
            if (!playerBreak) setDamageTaken(getDamageTaken() + (int) amount);   // (int) truncation (reference :911)
            markHurt();                                                          // client hit-wobble (reference :914)
            if (getDamageTaken() >= getMaxHp() || playerBreak) {
                if (!playerBreak) {
                    setDamageTaken(getMaxHp());
                    destroyVehicle(src.getEntity(), src.is(DamageTypes.IN_WALL));
                    this.mchTimeSinceHit = 20;
                } else {
                    dropSelfItem();   // creative/pickaxe salvage: drop the spawn item, no wreck (reference :937-952)
                    discard();
                }
            }
        } else if (playerBreak) {
            discard();               // a creative player clears an existing wreck (reference :955-957)
        }
        return true;
    }

    /** Demo salvage gate: a creative player's melee "break" instantly reclaims an intact vehicle (reference
     *  {@code isDamegeSourcePlayer}, simplified to the creative-hand case). */
    private boolean isPlayerBreak(DamageSource src) {
        return src.getEntity() instanceof Player p && p.getAbilities().instabuild && src.is(DamageTypes.PLAYER_ATTACK);
    }

    /** The spawn item dropped on creative salvage — this vehicle's own registered spawn item (by config name), or
     *  null if none is registered (e.g. the fallback config). */
    protected Item dropItem() {
        return mcheli.dependent.registry.MchRegistries.spawnItemFor(configName());
    }

    private void dropSelfItem() {
        Item it = dropItem();
        if (it != null && this.level() instanceof ServerLevel) {
            this.level().addFreshEntity(new ItemEntity(this.level(), getX(), getY(), getZ(), new ItemStack(it)));
        }
    }

    /**
     * Turn the craft into a wreck (reference {@code destroyAircraft} + the destruction branch of attackEntityFrom):
     * flip the synced destroyed flag, arm the despawn countdown, seed the tumble, eject riders, and spawn the death
     * blast — {@link #explosionByCrash} for a terrain ("inWall") kill, else a fixed power-2 explosion.
     */
    protected void destroyVehicle(Entity killer, boolean terrainKill) {
        if (this.level().isClientSide) return;
        this.entityData.set(DATA_DESTROYED, true);
        this.despawnCount = 500;
        this.wreckThrottle = getEnginePower(); // capture before ejection spools the engine down
        seedWreckTumble();
        ejectPassengers();
        if (this.level() instanceof ServerLevel sl) {
            if (terrainKill) {
                explosionByCrash(0.0);
                this.damageSinceDestroyed = getMaxHp();
            } else {
                MchExplosion.explode(sl, position(), 2, 2, true, true, 5, null, killer);
            }
        }
    }

    /** Seed the wreck tumble (reference heli default). Subclasses override: the tank zeroes it (no tumble), the plane
     *  rolls further into its existing bank. */
    protected void seedWreckTumble() {
        this.rotDestroyedPitch = this.random.nextFloat() - 0.5F;
        this.rotDestroyedRoll = (this.random.nextFloat() - 0.5F) * 0.5F;
        this.rotDestroyedYaw = 0.0F;
    }

    /** Apply one tick of wreck tumble (reference heli). Overridable: the plane gates roll differently, the tank no-ops.
     *  The pitch/roll tumble freezes once a block is within 3 below (reference {@code getBlockIdY(this,3,-3)==0}), so the
     *  hull settles before impact — NOT the vanilla {@code onGround()} flag, which only trips on literal contact. */
    protected void applyWreckTumble() {
        if (this.rotDestroyedYaw < 15.0F) this.rotDestroyedYaw += 0.3F;
        setYRot(getYRot() + this.rotDestroyedYaw * this.wreckThrottle);
        if (!solidBlockBelow()) {
            if (Math.abs(Mth.wrapDegrees(getXRot())) < 10.0F) setXRot(getXRot() + this.rotDestroyedPitch);
            setRollAngle(getRollAngle() + this.rotDestroyedRoll);
        }
    }

    /** Read-only accessors for subclass tumble overrides. */
    protected float rotDestroyedRoll() { return this.rotDestroyedRoll; }
    protected float rotDestroyedPitch() { return this.rotDestroyedPitch; }
    protected void setRotDestroyedRoll(float r) { this.rotDestroyedRoll = r; }
    protected void setRotDestroyedPitch(float p) { this.rotDestroyedPitch = p; }
    protected void setRotDestroyedYaw(float y) { this.rotDestroyedYaw = y; }

    /** A fuel-scaled AOE fireball at the wreck (reference {@code explosionByCrash}); demo {@code maxFuel=0} → power 1. */
    protected void explosionByCrash(double prevMotionY) {
        if (!(this.level() instanceof ServerLevel sl)) return;
        MCH_AircraftInfo info = weaponHostInfo();
        // Reference clamps maxFuel/400 to [1,15]; with no config it defaults the blast to 2.0 (not the 0-fuel floor of 1).
        float exp = info != null ? Mth.clamp(info.maxFuel / 400.0F, 1.0F, 15.0F) : 2.0F;
        int power = Math.round(exp);
        int block = Math.round(exp >= 2.0F ? exp * 0.5F : 1.0F);
        MchExplosion.explode(sl, position(), power, block, true, true, 5, null, null);
    }

    // ---- per-part hitboxes (weak points / armored zones) ----

    /** Build (once) and refresh the per-entity extra hitboxes to the live pose (reference {@code updateExtraBoundingBox}). */
    private void updateExtraBoxes() {
        MCH_AircraftInfo info = weaponHostInfo();
        if (this.extraBoxes == null) {
            if (info == null || info.extraBoundingBox == null || info.extraBoundingBox.isEmpty()) {
                this.extraBoxes = new MCH_BoundingBox[0];
            } else {
                this.extraBoxes = new MCH_BoundingBox[info.extraBoundingBox.size()];
                for (int i = 0; i < this.extraBoxes.length; i++) {
                    this.extraBoxes[i] = info.extraBoundingBox.get(i).copy();
                }
            }
        }
        for (MCH_BoundingBox bb : this.extraBoxes) {
            bb.updatePosition(getX(), getY(), getZ(), getYRot(), getXRot(), getRollAngle());
        }
    }

    /** The tight core collision box — the reference entity box {@code setSize(bodyWidth, bodyHeight)} (defaults 2.0×0.7),
     *  NOT the inflated vanilla selection AABB (which the port sizes to the whole visual model) and NOT the render-only
     *  {@code entityWidth/entityHeight}. Used as the factor-1.0 hull for the ray query + the projectile hit test. */
    private AABB coreBox() {
        MCH_AircraftInfo info = weaponHostInfo();
        float w = info != null && info.bodyWidth > 0.0F ? info.bodyWidth : 2.0F;
        float h = info != null && info.bodyHeight > 0.0F ? info.bodyHeight : 0.7F;
        return new AABB(getX() - w / 2.0, getY(), getZ() - w / 2.0, getX() + w / 2.0, getY() + h, getZ() + w / 2.0);
    }

    private static AABB mcBox(MCH_BoundingBox bb) {
        mcheli.agnostic.value.AABB a = bb.boundingBox;
        return new AABB(a.minX(), a.minY(), a.minZ(), a.maxX(), a.maxY(), a.maxZ());
    }

    /**
     * The per-part weak-point/armor factor of the box a bullet segment strikes NEAREST its origin, else 1.0 (the plain
     * hull) — the port of {@code MCH_AircraftBoundingBox.calculateIntercept}. The FULL segment {@code [from, to]} (the
     * bullet's whole trajectory) is tested against the core box AND every extra box, nearest-to-origin wins. The caller
     * MUST pass the full ray, NOT the point where it entered the vanilla selection AABB — that would truncate the
     * segment before it reaches any interior zone, silently resolving them all to 1.0.
     */
    public float boundingBoxDamageFactorAt(Vec3 from, Vec3 to) {
        if (this.extraBoxes == null || this.extraBoxes.length == 0) return 1.0F;
        float factor = 1.0F;
        double best = Double.MAX_VALUE;
        java.util.Optional<Vec3> coreHit = coreBox().clip(from, to);
        if (coreHit.isPresent()) best = from.distanceToSqr(coreHit.get());
        for (MCH_BoundingBox bb : this.extraBoxes) {
            java.util.Optional<Vec3> h = mcBox(bb).clip(from, to);
            if (h.isPresent()) {
                double d = from.distanceToSqr(h.get());
                if (d < best) {
                    best = d;
                    factor = bb.damegeFactor;
                }
            }
        }
        return factor;
    }

    /**
     * Nearest point where the segment {@code [from, to]} enters this vehicle's core box OR any extra part box, else
     * {@code null}. The projectile hit test uses this so a round that clips only a part box PROTRUDING beyond the
     * vanilla selection AABB (wingtip / tail / hull overhang) still registers a hit — the reference feeds the extra
     * boxes into the bullet's own intercept, so protruding zones are hittable. Returns null if the segment misses all.
     */
    public Vec3 clipParts(Vec3 from, Vec3 to) {
        Vec3 best = null;
        double bestSq = Double.MAX_VALUE;
        java.util.Optional<Vec3> coreHit = coreBox().clip(from, to);
        if (coreHit.isPresent()) {
            bestSq = from.distanceToSqr(coreHit.get());
            best = coreHit.get();
        }
        if (this.extraBoxes != null) {
            for (MCH_BoundingBox bb : this.extraBoxes) {
                java.util.Optional<Vec3> h = mcBox(bb).clip(from, to);
                if (h.isPresent()) {
                    double d = from.distanceToSqr(h.get());
                    if (d < bestSq) {
                        bestSq = d;
                        best = h.get();
                    }
                }
            }
        }
        return best;
    }

    /** Test/diagnostic hook: rebuild (if needed) + reposition the part hitboxes to the current pose without a tick. */
    public void refreshExtraBoxes() { updateExtraBoxes(); }

    /** Wreck lifetime countdown (500 at destruction, →0 then discard); exposed for diagnostics/self-test. */
    public int despawnCount() { return this.despawnCount; }

    // ---- environmental / crash damage (reference onUpdate_CollisionGroundDamage) ----

    /** Attitude beyond which a near-ground craft takes crash damage (reference {@code getGiveDamageRot}). Aircraft use
     *  40°; a TANK overrides to 91° (it only self-damages when essentially flipped, so it can climb steep terrain). */
    protected float giveDamageRot() { return 40.0F; }

    /** Attitude-crash (tilted steeply near the ground) + water-submersion damage, alive craft only (server). */
    private void collisionGroundDamage() {
        MCH_AircraftInfo info = weaponHostInfo();
        if (solidBlockBelow()) {
            float roll = Math.abs(Mth.wrapDegrees(getRollAngle()));
            float pitch = Math.abs(Mth.wrapDegrees(getXRot()));
            if (roll > giveDamageRot() || pitch > giveDamageRot()) {              // reference getGiveDamageRot()
                float dmg = roll + pitch;
                double dist = position().distanceTo(new Vec3(this.xo, this.yo, this.zo)); // distance moved this tick
                dmg = dmg < 90.0F ? (float) (dmg * 0.4F * dist) : dmg * 0.4F;
                if (dmg > 1.0F && this.random.nextInt(4) == 0) {
                    hurt(damageSources().inWall(), dmg);
                }
            }
        }
        if (this.tickCount % 30 == 0 && (info == null || !info.isFloat) && waterAtProbe()) {
            hurt(damageSources().inWall(), Math.max(1, getMaxHp() / 10));
        }
    }

    /** A collidable block within a 3×3 footprint up to 3 blocks below (reference {@code getBlockIdY(this,3,-3)}). The
     *  footprint is centered with {@code (int)} casts to match the reference {@code getBlockY} (and {@link #waterAtProbe},
     *  which uses the same convention) rather than {@code Mth.floor}, so the columns line up on positive coordinates. */
    protected boolean solidBlockBelow() {
        int cx = (int) (getX() + 0.5);
        int y0 = (int) (getY() + 0.5);
        int cz = (int) (getZ() + 0.5);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy < 3; dy++) {
                    BlockPos pos = new BlockPos(cx + dx, y0 - dy, cz + dz);
                    if (!this.level().getBlockState(pos).getCollisionShape(this.level(), pos).isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Water at the submersion probe point {@code y = posY + 1.5 + submergedDamageHeight} or its 6 neighbors
     *  (reference {@code MCH_Lib.isBlockInWater}). {@code (int)} casts match the reference (not {@code Mth.floor}). */
    private boolean waterAtProbe() {
        MCH_AircraftInfo info = weaponHostInfo();
        int bx = (int) (getX() + 0.5);
        int by = (int) (getY() + 1.5 + (info != null ? info.submergedDamageHeight : 0.0F));
        int bz = (int) (getZ() + 0.5);
        if (by <= this.level().getMinBuildHeight()) return false;
        int[][] off = {{0, 0, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, 1, 0}};
        for (int[] o : off) {
            if (this.level().getFluidState(new BlockPos(bx + o[0], by + o[1], bz + o[2])).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    /** CLIENT: advance the wheel-spin + track-scroll phase from the SIGNED forward distance moved this tick (so parked
     *  wheels/tracks stop and reverse scrolls backward). Cosmetic + client-local, exactly like the rotor. */
    private void updateWheelTrackPhase() {
        // Gatling barrel — the EXACT reference accumulation (MCH_WeaponSet.update): near-instant spin-up to 74 deg/tick
        // while a weapon recently fired (synced DATA_FIRING), ~50-tick coast-down. The 360 wrap subtracts from prev in
        // lockstep so the interpolation seam stays valid.
        this.prevBarrelSpin = this.barrelSpin;
        if (isFiringRecently() && this.barrelSpeed < 75.0F) {
            this.barrelSpeed += 25.0F + this.random.nextInt(3);
            if (this.barrelSpeed > 74.0F) {
                this.barrelSpeed = 74.0F;
            }
        }
        this.barrelSpin += this.barrelSpeed;
        if (this.barrelSpin >= 360.0F) {
            this.barrelSpin -= 360.0F;
            this.prevBarrelSpin -= 360.0F;
        }
        if (this.barrelSpeed > 0.0F) {
            this.barrelSpeed -= 1.48F;
            if (this.barrelSpeed < 0.0F) {
                this.barrelSpeed = 0.0F;
            }
        }

        MCH_AircraftInfo info = weaponHostInfo();
        if (info == null || (info.partWheel.isEmpty() && info.partCrawlerTrack.isEmpty() && info.partTrackRoller.isEmpty())) {
            return;
        }
        this.prevWheelSpin = this.wheelSpin;
        this.prevWheelSteer = this.wheelSteer;
        this.prevTrackScroll[0] = this.trackScroll[0];
        this.prevTrackScroll[1] = this.trackScroll[1];
        this.prevRollerSpin[0] = this.rollerSpin[0];
        this.prevRollerSpin[1] = this.rollerSpin[1];
        if (!this.trackInit) {
            this.lastTrackX = getX();
            this.lastTrackZ = getZ();
            this.lastTrackYaw = getYRot();
            this.trackInit = true;
        }
        double dx = getX() - this.lastTrackX;
        double dz = getZ() - this.lastTrackZ;
        this.lastTrackX = getX();
        this.lastTrackZ = getZ();
        // Forward distance = motion projected onto the hull-forward vector (yaw about -Y -> forward = (-sin, 0, cos)).
        double yr = Math.toRadians(getYRot());
        double fwd = -dx * Math.sin(yr) + dz * Math.cos(yr);
        // Yaw rate (rad/tick): drives the wheel STEER and the per-side track DIFFERENTIAL (a point at lateral offset x
        // gains forward speed -yawRate·x, so on a pivot the two tracks scroll opposite while the hull barely moves).
        double yawRate = Math.toRadians(Mth.wrapDegrees(getYRot() - this.lastTrackYaw));
        this.lastTrackYaw = getYRot();

        this.wheelSpin = wrap360(this.wheelSpin + (float) (fwd * 220.0)); // ~0.52-block-radius wheel: 220°/block
        // Reference rotYawWheel is +1 for a LEFT turn; MC yaw DECREASES on a left turn, so negate the yaw rate.
        float steerTarget = Mth.clamp((float) (-yawRate * STEER_GAIN), -1.0F, 1.0F);
        this.wheelSteer += (steerTarget - this.wheelSteer) * 0.3F; // smoothed toward the turn direction

        float w = halfTrackWidth(info);
        float linkLen = info.partCrawlerTrack.isEmpty() ? 0.37F : Math.max(0.05F, info.partCrawlerTrack.get(0).len);
        for (int side = 0; side < 2; side++) {
            // side 1 = +X = the hull's LEFT (at yaw 0 the hull faces +Z and +X is east = left); side 0 = -X = right —
            // matching the parse (TrackRoller/CrawlerTrack side = x/z >= 0 ? 1 : 0). Yaw INCREASES on a right turn and
            // omega = -yawRate*Y, so a point at lateral offset x gains forward speed +yawRate*x: on a right turn the
            // LEFT (+X) track runs faster — the sign below must be +.
            float sideX = side == 1 ? w : -w;
            float sideSpeed = (float) (fwd + yawRate * sideX); // blocks/tick along this track
            this.rollerSpin[side] = wrap360(this.rollerSpin[side] + sideSpeed * 220.0F); // rollers spin +
            this.trackScroll[side] = wrap01(this.trackScroll[side] - sideSpeed / linkLen); // belt scrolls - (reference sign)
        }
    }

    /** Half the track gauge (lateral distance from centre to a track), for the per-side differential. Some configs
     *  (KV-2) declare their crawler tracks at Z = 0.00 (the offset lives in the mesh), so a near-zero source falls
     *  through to the rollers, then the wheels, then a 1-block default — else the differential silently dies. */
    private static float halfTrackWidth(MCH_AircraftInfo info) {
        if (!info.partCrawlerTrack.isEmpty()) {
            float z = Math.abs(info.partCrawlerTrack.get(0).z);
            if (z > 0.01F) {
                return z;
            }
        }
        if (!info.partTrackRoller.isEmpty()) {
            float x = Math.abs((float) info.partTrackRoller.get(0).pos.x());
            if (x > 0.01F) {
                return x;
            }
        }
        if (!info.partWheel.isEmpty()) {
            float x = Math.abs((float) info.partWheel.get(0).pos.x());
            if (x > 0.01F) {
                return x;
            }
        }
        return 1.0F;
    }

    private static float wrap360(float a) {
        a %= 360.0F;
        return a < 0.0F ? a + 360.0F : a;
    }
    private static float wrap01(float a) {
        a %= 1.0F;
        return a < 0.0F ? a + 1.0F : a;
    }

    /**
     * True when the FIRED weapon drives a spinning gatling barrel, so only that weapon arms the barrel latch — the
     * reference keeps a per-weapon {@code useWeaponStat} bit and spins only the fired weapon's {@code rotBarrel}
     * (firing a Hydra pod must not spin the A-10's GAU-8). Matches the slot's weapon name against the {@code rotBarrel}
     * {@code AddPartWeapon}s; an emplacement's barrel is a {@code type==1} {@code VPart} instead, whose reference spin
     * source is the first seat weapon — so any fire arms it there.
     */
    private boolean firesRotBarrel(WeaponSlot slot) {
        MCH_AircraftInfo info = weaponHostInfo();
        if (info == null) {
            return false;
        }
        if (info.partWeapon != null) {
            for (MCH_AircraftInfo.PartWeapon pw : info.partWeapon) {
                if (pw != null && pw.rotBarrel && pw.name != null) {
                    for (String nm : pw.name) {
                        if (nm != null && nm.equalsIgnoreCase(slot.weaponName)) {
                            return true;
                        }
                    }
                }
            }
        }
        if (info instanceof mcheli.agnostic.vehicle.MCH_VehicleInfo vi) {
            return hasType1Part(vi.partList);
        }
        return false;
    }

    private static boolean hasType1Part(java.util.List<mcheli.agnostic.vehicle.MCH_VehicleInfo.VPart> parts) {
        if (parts != null) {
            for (mcheli.agnostic.vehicle.MCH_VehicleInfo.VPart p : parts) {
                if (p != null && (p.type == 1 || hasType1Part(p.child))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Run the per-type flight controller for one server tick with the rider's control input. */
    protected abstract void tickPhysics(ControlInput in);
}
