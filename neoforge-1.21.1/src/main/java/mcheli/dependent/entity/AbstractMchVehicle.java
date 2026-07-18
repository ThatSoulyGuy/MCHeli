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
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
    /** The real flight throttle (0 at rest, 1 spooled up) — the port of the reference {@code getCurrentThrottle()},
     *  synced for the CLIENT sound loop + rotor down-wash. Distinct from {@link #DATA_THROTTLE} (the rotor-spin signal
     *  that idles at 0.5 while merely ridden). */
    private static final EntityDataAccessor<Float> DATA_SIM_THROTTLE =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.FLOAT);
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
    /** Barrel heat 0..1 of the selected weapon (synced for the HUD overheat gauge); 1 == overheated (fire locked out). */
    private static final EntityDataAccessor<Float> DATA_HEAT =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.FLOAT);
    /** Flare dispenser state (synced for the HUD {@code can_flare} cue): 0 = idle/ready, 1 = a dispense window runs. */
    private static final EntityDataAccessor<Byte> DATA_FLARE_STATE =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.BYTE);
    /** Recoil trigger: bumped once per shot of a weapon with config {@code Recoil > 0}. A client watches it for a rising
     *  edge and runs its own 30-tick kick from {@link #DATA_RECOIL_VALUE}/{@link #DATA_RECOIL_YAW} (reference recoilCount). */
    private static final EntityDataAccessor<Integer> DATA_RECOIL_SEQ =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.INT);
    /** The firing weapon's config {@code Recoil} magnitude (degrees) for the kick. */
    private static final EntityDataAccessor<Float> DATA_RECOIL_VALUE =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.FLOAT);
    /** The firing weapon's aim yaw RELATIVE TO THE HULL — splits the kick into pitch (forward gun) vs roll (side gun). */
    private static final EntityDataAccessor<Float> DATA_RECOIL_YAW =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.FLOAT);
    /** True while a weapon RECENTLY fired (within {@code firedCooldownTicks}) — synced so the gatling barrel spins on
     *  every client (the port of the reference {@code useWeaponStat}/{@code MCH_WeaponSet.isUsed} bit). */
    private static final EntityDataAccessor<Boolean> DATA_FIRING =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.BOOLEAN);
    /** Fuel in the tank (synced for the HUD gauge). A vehicle with {@code MaxFuel = 0} never burns fuel. */
    private static final EntityDataAccessor<Integer> DATA_FUEL =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.INT);
    /** True while the PILOT has toggled gunner mode (reference {@code isGunnerMode}) — server-authoritative and synced:
     *  the flight sim reads it (a heli hovers, a plane/tank levels off and stops steering), and clients read it for the
     *  gunner camera + scope zoom + the seat-pitch view clamp. Only ever set for a pilot; gunner SEATS are "always in
     *  gunner mode" structurally (see {@link #isSeatGunnerMode}). */
    private static final EntityDataAccessor<Boolean> DATA_GUNNER_MODE =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.BOOLEAN);
    /** True while the PILOT has the resupply GUI open (plus the reference's 20-tick tail) — the port of
     *  {@code isPilotReloading}. The HUD blanks the weapon name/ammo while it is set, and the reference kills pilot
     *  control input for the same window. */
    private static final EntityDataAccessor<Boolean> DATA_RELOADING =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.BOOLEAN);
    /** Ticks left of the post-close tail (reference {@code supplyAmmoWait}, set to 20 while the GUI is open). */
    private int supplyAmmoWait;

    /** weaponIndex → TOTAL ammo (magazine + reserve) for every weapon, so the riding GUI can show the reserve of a
     *  weapon that is not the one selected. The HUD's {@link #DATA_AMMO} carries only the SELECTED weapon's magazine. */
    private static final EntityDataAccessor<CompoundTag> DATA_AMMO_ALL =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.COMPOUND_TAG);
    /** Per-weapon-slot "spent" bitmask (synced): bit i set while slot i is mid-reload, so a just-fired MISSILE part
     *  vanishes from its rack until it reloads — the reference {@code useWeaponStat} + {@code renderWeapon} isMissile
     *  gate. Up to 32 slots. */
    private static final EntityDataAccessor<Integer> DATA_WEAPON_SPENT =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.INT);
    /** seatIndex → passenger ENTITY ID. The client mirror of the server's seat map — the same information the reference
     *  shipped in {@code MCH_PacketSeatListResponse}, but auto-resynced. {@code getPassengers()} order is mount order
     *  and compacts on dismount, so it can NEVER be used as a seat map. */
    private static final EntityDataAccessor<CompoundTag> DATA_SEATS =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.COMPOUND_TAG);
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

    // Aim latch — the reference lastRiderYaw/lastRiderPitch (MCH_EntityAircraft:1486-1497, NBT "AcLastRYaw"): the
    // PILOT's look, refreshed only while a live pilot is aboard and the craft is intact, and HELD after dismount so
    // the turret/guns/crew-seats keep their last pose instead of snapping to hull-forward.
    private float lastRiderYaw, prevLastRiderYaw;
    private float lastRiderPitch, prevLastRiderPitch;
    private boolean aimLatched;

    // Turret slew (reference updateWeaponsRotation ±15°/tick): the shared turret ring EASES toward the operator's aim at
    // a capped rate instead of snapping. Tracked in WORLD space (so a hull turn does not drag it — only a change in the
    // operator's own look slews it), stepped once per tick on BOTH sides; turretOrbitYaw() derives the rel-hull angle.
    // render, the rotSeat crew orbit, and the ring-mounted muzzle + shot all read that one angle, so they stay locked.
    private static final float TURRET_SLEW_MAX = 15.0F;
    private float turretSlewYaw, prevTurretSlewYaw;
    private boolean turretSlewInit;

    /** Refresh the pilot-aim latch once per tick (call from the entity tick, both sides). */
    private void updateAimLatch() {
        Entity p = pilot();
        if (p != null && p.isAlive() && !isDestroyed()) {
            this.prevLastRiderYaw = this.aimLatched ? this.lastRiderYaw : p.getYRot();
            this.prevLastRiderPitch = this.aimLatched ? this.lastRiderPitch : p.getXRot();
            this.lastRiderYaw = p.getYRot();
            this.lastRiderPitch = p.getXRot();
            this.aimLatched = true;
        } else {
            this.prevLastRiderYaw = this.lastRiderYaw;   // no pilot: HOLD the last aim (reference latch)
            this.prevLastRiderPitch = this.lastRiderPitch;
        }
        updateTurretSlew();
    }

    /** Step the world-space turret yaw toward the operator's look by at most {@link #TURRET_SLEW_MAX}° this tick — the
     *  reference {@code updateWeaponsRotation} rate cap. Runs both sides from the (synced) operator look, so the render
     *  slew and the server's fire-time turret agree within a step. */
    private void updateTurretSlew() {
        float target = this.aimLatched ? this.lastRiderYaw : this.getYRot(); // operator world look, else hull-forward
        if (!this.turretSlewInit) {
            this.turretSlewYaw = this.prevTurretSlewYaw = Mth.wrapDegrees(target); // snap on the first tick (no start jerk)
            this.turretSlewInit = true;
            return;
        }
        this.prevTurretSlewYaw = this.turretSlewYaw;
        float diff = Mth.wrapDegrees(target - this.turretSlewYaw);
        if (diff > TURRET_SLEW_MAX) {
            this.turretSlewYaw = Mth.wrapDegrees(this.turretSlewYaw + TURRET_SLEW_MAX);
        } else if (diff < -TURRET_SLEW_MAX) {
            this.turretSlewYaw = Mth.wrapDegrees(this.turretSlewYaw - TURRET_SLEW_MAX);
        } else {
            this.turretSlewYaw = Mth.wrapDegrees(target);
        }
    }

    public boolean hasAimLatch() { return this.aimLatched; }

    /**
     * The TURRET yaw, relative to the hull — the ONE source shared by the rotSeat seat orbit, the weapon-ring in
     * {@code renderWeaponParts}, and the emplacement's VPart turret. All three must read this (textually equivalent)
     * or a crewed tank shears: seat, turret mesh and camera would each follow a different angle.
     */
    public float turretOrbitYaw(float partialTick) {
        if (!this.turretSlewInit) {
            return 0.0F; // never ticked -> hull-forward
        }
        // The rate-limited world-space turret yaw (updateTurretSlew), render-interpolated, expressed relative to the
        // interpolated hull. Deriving rel-hull here (rather than slewing a rel-hull value) keeps a hull turn from
        // dragging the turret: the world slew target is the operator's look, unaffected by the hull spinning under it.
        float worldYaw = shortLerpDeg(this.turretSlewYaw, this.prevTurretSlewYaw, partialTick);
        float hullYaw = Mth.rotLerp(partialTick, this.yRotO, this.getYRot());
        return Mth.wrapDegrees(worldYaw - hullYaw);
    }

    /** The pilot's look PITCH (absolute) — live while piloted, latched after dismount. The emplacement/turret elevation. */
    public float turretAimPitch(float partialTick) {
        Entity p = pilot();
        if (p != null) {
            return Mth.lerp(partialTick, p.xRotO, p.getXRot());
        }
        return this.aimLatched ? Mth.lerp(partialTick, this.prevLastRiderPitch, this.lastRiderPitch) : 0.0F;
    }

    // Config-driven weapon loadout (server-authoritative), built lazily from weaponHostInfo(). Net pending weapon-cycle
    // steps: each one-shot switch payload adds ±1, and the whole accumulator is drained on the next server tick, so two
    // presses that batch into a single tick both count instead of coalescing to one.
    private VehicleWeapons weapons;
    private final java.util.Map<Integer, Integer> switchBySeat = new java.util.HashMap<>();

    /** The agnostic view of this entity, reused every tick so the physics never sees a Minecraft type. */
    protected final EntityRef ref = new NeoEntityRef(this);

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

    // CLIENT: the pilot's selected config CameraPosition index (reference MCH_EntityAircraft.cameraId). Purely a local
    // view state — the reference only ever reads it through the client player (getCameraPosInfo), so it is never synced.
    // 0 = the default eye (cameraPosition[0]); >0 = an alternate configured viewpoint, cycled by the gunner/view key.
    private int viewCameraId;

    /** CLIENT: how many config {@code CameraPosition} entries this vehicle has (reference {@code getCameraPosNum}). */
    public int cameraPosCount() {
        MCH_AircraftInfo info = weaponHostInfo();
        return info != null && !info.cameraPosition.isEmpty() ? info.cameraPosition.size() : 1;
    }

    /** CLIENT: at least two configured viewpoints, so the view key can cycle them (reference {@code canSwitchCameraPos}). */
    public boolean canSwitchCameraPos() {
        return cameraPosCount() >= 2;
    }

    /** CLIENT: the pilot's currently-selected {@code CameraPosition} index, clamped into range. */
    public int getViewCameraId() {
        int n = cameraPosCount();
        return this.viewCameraId > 0 && this.viewCameraId < n ? this.viewCameraId : 0;
    }

    /** CLIENT: select a config {@code CameraPosition} index (out-of-range collapses to 0, the default eye). */
    public void setViewCameraId(int id) {
        this.viewCameraId = id > 0 && id < cameraPosCount() ? id : 0;
    }

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

    /** The PILOT's control state — what the physics reads. Gunners have their own (see {@link #controlState(int)}). */
    @Override public MchControlState getControlState() { return controlState(0); }

    /** Per-seat control state: every seat owns its own FIRE/free-look bits, but only seat 0's drive bits are honored
     *  (a gunner must not steer — the reference routes movement from the pilot packet alone). */
    private final java.util.Map<Integer, MchControlState> controlBySeat = new java.util.HashMap<>();

    public MchControlState controlState(int seat) {
        return this.controlBySeat.computeIfAbsent(seat, s -> new MchControlState());
    }

    /** Drop a seat's held keys when its occupant leaves (else the vehicle keeps firing/driving on the last packet). */
    public void clearSeatControls(int seat) {
        this.controlBySeat.remove(seat);
    }
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
        int sid = seatIndexOf(passenger);
        if (sid < 0) {
            sid = getPassengers().isEmpty() || getPassengers().get(0) == passenger ? 0 : -1;
        }
        if (sid < 0 || sid >= info.seatList.size()) {
            return super.getPassengerAttachmentPoint(passenger, dimensions, partialTick); // seat not resolved yet
        }
        MCH_SeatInfo seat = info.seatList.get(sid);
        // The reference genuinely uses DIFFERENT y conventions per path — do not unify. Pilot (updateRiderPosition
        // :3201): feet at seat.y − 0.5. Crew seats (updateSeatsPosition :3142-3148): the client player is offset +1.0
        // (eye convention → feet at seat.y − 0.62); a tall mob sits at seat.y + (1 − height); a small mob at seat.y.
        double sy;
        if (sid == 0) {
            sy = seat.pos.y() - 0.5;
        } else if (passenger instanceof Player) {
            sy = seat.pos.y() - 0.62;
        } else if (passenger.getBbHeight() >= 1.0F) {
            sy = seat.pos.y() + (1.0 - passenger.getBbHeight());
        } else {
            sy = seat.pos.y();
        }
        org.joml.Vector3f p = new org.joml.Vector3f(
            (float) seat.pos.x(), (float) sy, (float) seat.pos.z());

        // (1) rotSeat: orbit the seat about turretPosition by the TURRET yaw, keeping Y (Ry never touches Y). The yaw is
        // turretOrbitYaw() — the latched PILOT's look (reference lastRiderYaw), NOT this passenger's own head: a tank
        // crew seat rides the turret the PILOT is slewing. Same expression as the renderers' ring => no shear.
        if (seat.rotSeat) {
            float turretYaw = turretOrbitYaw(partialTick);
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

        // GUNNER CAMERA (getCameraPosInfo:685-699, the sid>0 branch): a gunner seat with its OWN camera position
        // (AddGunnerSeat sets invCamPos + a CameraPosition) renders from that camera while the seat is in gunner mode,
        // orbiting the turret if the seat is rotSeat. This is what detaches the view to the gun sight.
        int sid = seatIndexOf(rider);
        MCH_SeatInfo seat = info.seatList != null && sid > 0 && sid < info.seatList.size()
            ? info.seatList.get(sid) : null;
        if (seat != null && seat.invCamPos && seat.getCamPos() != null && isSeatGunnerMode(sid)) {
            return configCameraEye(seat.getCamPos().pos, seat.rotSeat, info, vx, vy, vz, body, partialTick);
        }

        // The pilot uses CameraPosition[camIdx] when the config forces the camera view (alwaysCameraView), when the pilot
        // has entered gunner mode, OR when they have cycled to an alternate viewpoint (cameraId>0). The reference renders
        // through the camera dummy for (isPilot && alwaysCameraView) || getIsGunnerMode || cameraId>0
        // (MCH_Client*TickHandler:~110), and getCameraPosInfo returns cameraPosition[cameraId] for seat 0. Gunner mode
        // resets cameraId to 0 (see the view-key handler), so it always shows cameraPosition[0].
        int camIdx = sid == 0 ? getViewCameraId() : 0;
        boolean pilotGunnerCam = sid == 0 && isSeatGunnerMode(0);
        if ((usesConfigCameraEye() || pilotGunnerCam || camIdx > 0) && !info.cameraPosition.isEmpty()) {
            MCH_SeatInfo seat0 = info.seatList == null || info.seatList.isEmpty() ? null : info.seatList.get(0);
            return configCameraEye(info.cameraPosition.get(pilotGunnerCam ? 0 : camIdx).pos,
                seat0 != null && seat0.rotSeat, info, vx, vy, vz, body, partialTick);
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

    /** The eye at a config {@code CameraPosition} {@code cp} — the shared body of {@link #firstPersonEye}'s pilot and
     *  gunner camera branches ({@code setupAllRiderRenderPosition:3266-3276} + {@code calcOnTurretPos:3218-3232}). A
     *  {@code rotSeat} camera orbits {@code turretPosition} by the rider's absolute look yaw; otherwise it is fixed to
     *  the hull. {@code cp} IS the eye (no player eye height added); both carry the port's {@code -minY} lift. */
    private Vec3 configCameraEye(mcheli.agnostic.value.Vec3d cp, boolean rotSeat, MCH_AircraftInfo info,
                                 double vx, double vy, double vz, org.joml.Quaternionf body, float partialTick) {
        org.joml.Vector3f off;
        if (rotSeat) {
            org.joml.Vector3f tp = new org.joml.Vector3f(
                (float) info.turretPosition.x(),
                (float) (info.turretPosition.y() + cp.y()),
                (float) info.turretPosition.z());
            // The orbit is the PILOT's turret yaw (reference calcOnTurretPos ry = getRiddenByEntity().rotationYaw), NOT
            // the camera-owner's — a gunner's cupola camera stays fixed to the turret and only its VIEW rotates, exactly
            // like the gunner's rotSeat SEAT (which also orbits by the pilot's look). For the pilot's own camera the two
            // are the same rider.
            float orbitYaw = cameraOrbitYaw(partialTick);
            off = new org.joml.Vector3f((float) cp.x(), (float) cp.y(), (float) cp.z())
                .sub(tp)
                .rotateY((float) Math.toRadians(-orbitYaw))
                .add(tp.rotate(body));
        } else {
            off = new org.joml.Vector3f((float) cp.x(), (float) cp.y(), (float) cp.z()).rotate(body);
        }
        return new Vec3(vx + off.x, vy + (double) off.y - modelMinY(), vz + off.z);
    }

    /** The camera turret-orbit yaw (reference {@code calcOnTurretPos} {@code ry}): the PILOT's interpolated body yaw, or
     *  the aim latch if no pilot rides. Both the pilot's own turret camera and a gunner's cupola camera orbit by this. */
    private float cameraOrbitYaw(float partialTick) {
        Entity p = pilot();
        if (p != null) {
            return Mth.rotLerp(partialTick, p.yRotO, p.getYRot());
        }
        return this.aimLatched ? Mth.rotLerp(partialTick, this.prevLastRiderYaw, this.lastRiderYaw) : this.getYRot();
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
        builder.define(DATA_SIM_THROTTLE, 0.0F);
        builder.define(DATA_SKIN, 0);
        builder.define(DATA_GEAR, 0.0F);
        builder.define(DATA_FLARE_STATE, (byte) 0);
        builder.define(DATA_GUNNER_MODE, false);
        builder.define(DATA_WEAPON, -1);
        builder.define(DATA_AMMO, -1);
        builder.define(DATA_RELOAD, 0.0F);
        builder.define(DATA_HEAT, 0.0F);
        builder.define(DATA_RECOIL_SEQ, 0);
        builder.define(DATA_RECOIL_VALUE, 0.0F);
        builder.define(DATA_RECOIL_YAW, 0.0F);
        builder.define(DATA_FIRING, false);
        builder.define(DATA_FUEL, 0);
        builder.define(DATA_SEATS, new CompoundTag());
        builder.define(DATA_AMMO_ALL, new CompoundTag());
        builder.define(DATA_WEAPON_SPENT, 0);
        builder.define(DATA_RELOADING, false);
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
        this.viewCameraId = 0; // a different config may have fewer camera positions — fall back to the default eye
        this.flareInfo.reset();       // a different config may declare different flare types
        this.currentFlareIndex = 0;
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

    /** The client-side weapon list (config-resolved, ammo lives in the synced fields), built lazily; null if no info. */
    private VehicleWeapons clientWeapons() {
        if (weaponHostInfo() == null) {
            return null;
        }
        if (this.hudWeapons == null) {
            this.hudWeapons = VehicleWeapons.build(weaponHostInfo(), MCH_WeaponInfoManager::get);
        }
        return this.hudWeapons;
    }

    /** The selected weapon's slot (from the synced index), for HUD name/ammo display; null if none. */
    private WeaponSlot selectedHudSlot() {
        VehicleWeapons vw = clientWeapons();
        if (vw == null) {
            return null;
        }
        int idx = getSelectedWeaponIndex();
        return idx >= 0 && idx < vw.size() ? vw.get(idx) : null;
    }

    /** True if the MISSILE part for the weapon {@code weaponName} should be hidden this frame — its round is spent and
     *  reloading (synced {@link #DATA_WEAPON_SPENT}), so the fired rail reads as empty (reference renderWeapon gate). */
    public boolean isMissilePartHidden(String weaponName) {
        if (weaponName == null) {
            return false;
        }
        int mask = this.entityData.get(DATA_WEAPON_SPENT);
        if (mask == 0) {
            return false;
        }
        VehicleWeapons vw = clientWeapons();
        if (vw == null) {
            return false;
        }
        for (int i = 0; i < vw.size() && i < 32; i++) {
            if (weaponName.equalsIgnoreCase(vw.get(i).weaponName)) {
                return (mask & 1 << i) != 0;
            }
        }
        return false;
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

    /** True if the selected weapon is a heat/IR-seeker (config {@code MaxHeatCount &gt; 0}) — the HUD's
     *  {@code is_heat_wpn} lock-cue gate (reference {@code MCH_HudItem}: {@code is_heat_wpn = wi.maxHeatCount &gt; 0}). */
    public boolean isSelectedWeaponHeat() {
        WeaponSlot slot = selectedHudSlot();
        return slot != null && slot.info.maxHeatCount > 0;
    }

    /** The selected weapon's aiming-reticle type for the HUD {@code sight_type} overlay (reference {@code MCH_HudItem}):
     *  a lock-on missile sight ({@code sight = missilesight}) = 2, a ballistic rocket/gun move-sight
     *  ({@code sight = movesight}) = 1, otherwise 0 — straight from the weapon's config, so the scope/lock reticle in
     *  {@code hud/sight.txt} shows for the ~160 sighted weapons. */
    public int selectedWeaponSightType() {
        WeaponSlot slot = selectedHudSlot();
        if (slot == null) {
            return 0;
        }
        return switch (slot.info.sight) {
            case LOCK -> 2;
            case ROCKET -> 1;
            default -> 0;
        };
    }

    /** CLIENT: the LOCAL player's selected weapon config (for the lock-on tracker), or null if no weapon selected. */
    public MCH_WeaponInfo selectedClientWeaponInfo() {
        WeaponSlot slot = selectedHudSlot();
        return slot != null ? slot.info : null;
    }

    /** The selected weapon's {@code DisplayMortarDistance} config flag — gates the HUD {@code mortar} range readout. */
    public boolean selectedWeaponDisplaysMortarDist() {
        WeaponSlot slot = selectedHudSlot();
        return slot != null && slot.info.displayMortarDistance;
    }

    /** The selected weapon's reload/cooldown progress 0..1 (synced), for the HUD {@code reload_time}/{@code reloading}
     *  bar; 0 == ready to fire. */
    public float getSelectedReload() { return this.entityData.get(DATA_RELOAD); }

    /** The selected weapon's barrel heat 0..1 (synced), for the HUD {@code wpn_heat} overheat gauge; 1 == overheated. */
    public float getSelectedHeat() { return this.entityData.get(DATA_HEAT); }

    /** The selected weapon's remaining cooldown in SECONDS — the synced fraction × the config interval (delay/reload)
     *  ÷ 20 tps — for the HUD's {@code RELOAD_SEC} readout. Computed client-side from the config interval. */
    public float getSelectedReloadSeconds() {
        WeaponSlot slot = selectedHudSlot();
        return slot != null ? getSelectedReload() * slot.reloadIntervalTicks() / 20.0F : 0.0F;
    }

    /** The HUD config name for a seat (0 = pilot), from this vehicle's {@code HUD =} config list, or null. A {@code none}
     *  (or blank) entry means the seat has NO cockpit HUD and maps to null, so the caller keeps the vanilla crosshair
     *  rather than suppressing it for an empty overlay (the reference has no HudManager entry for {@code none}). */
    public String hudName(int seat) {
        MCH_AircraftInfo info = weaponHostInfo();
        if (info == null || info.hudList == null || seat < 0 || seat >= info.hudList.size()) {
            return null;
        }
        String name = info.hudList.get(seat);
        return name == null || name.isEmpty() || name.equalsIgnoreCase("none") ? null : name;
    }

    /** SERVER: queue a weapon cycle (+1 next / -1 previous), applied on the next tick. Called by the switch payload;
     *  accumulates so multiple presses in one tick window each register. */
    public void queueWeaponSwitch(Entity sender, int direction) {
        int seat = seatIndexOf(sender);
        if (seat < 0) {
            return;
        }
        this.switchBySeat.merge(seat, direction >= 0 ? 1 : -1, Integer::sum);
    }

    /** Landing-gear retract angle 0..90 (deployed..retracted), synced; the renderer interpolates with the prev. */
    public float getGearAngle() { return this.gearAngle; }
    public float getPrevGearAngle() { return this.prevGearAngle; }

    /** Spooled engine power 0..1 (synced). Full when a rider holds throttle-up, idles at 0.5 while ridden, 0 when
     *  abandoned — clients read it for rotor RPM / sound. */
    public float getEnginePower() { return this.entityData.get(DATA_THROTTLE); }

    /** The real flight throttle 0..1 (synced) — the port of {@code getCurrentThrottle()}: 0 at rest, rising as you spool
     *  up. Drives the engine sound loop + rotor down-wash (NOT {@link #getEnginePower}, which idles at 0.5 when ridden). */
    public float getThrottleInput() { return this.entityData.get(DATA_SIM_THROTTLE); }

    // ---- Rotor / propeller spin (reference rotationRotor) — CLIENT-only, driven by the CONFIG rotorSpeed ----
    private float rotorSpin;
    private float prevRotorSpin;

    /** Advance the rotor/propeller spin angle this client tick — the port of the reference {@code rotationRotor +=
     *  throttle × info.rotorSpeed} ({@code MCP_EntityPlane}:173, heli equivalent). The per-tick rate is the config
     *  {@code RotorSpeed} (heli 79.99, prop plane 47.94; 0 for jets/tanks so nothing spins) scaled by the engine power,
     *  so a running engine idles the blades and full throttle spins them fast. NOTHING is hardcoded — the rate is the
     *  vehicle's own config value. */
    private void advanceRotorSpin() {
        MCH_AircraftInfo info = weaponHostInfo();
        float speed = info != null ? info.rotorSpeed : 0.0F;
        this.prevRotorSpin = this.rotorSpin;
        if (speed != 0.0F) {
            this.rotorSpin = (this.rotorSpin + getEnginePower() * speed) % 360.0F;
        }
    }

    /** Current rotor/propeller spin angle (degrees), for the renderer. */
    public float rotorSpin() { return this.rotorSpin; }
    /** Previous-tick rotor/propeller spin angle, for render interpolation. */
    public float prevRotorSpin() { return this.prevRotorSpin; }

    /** Whether the rotor can currently generate lift — the port of {@code MCH_EntityHeli.canUseBlades}. True for every
     *  vehicle without a foldable rotor; {@link MchHelicopter} overrides it to gate lift while the blades are folded or
     *  mid-fold. The heli flight model reads this through {@link mcheli.agnostic.sim.HeliState#canUseBlades}. */
    public boolean canUseBlades() { return true; }

    /** Whether this is a fold-capable heli that is currently folded AND resting on the ground — gates the parked-taxi
     *  nudge in the heli flight model ({@code onUpdate_ControlFoldBladeAndOnGround}). False for everything else. */
    public boolean isFoldedOnGround() { return false; }

    // ---- Entity radar (reference MCH_Radar + MCH_EntityAircraft.updateRadar) — CLIENT-only, drives the HUD radar dial ----
    private static final double[] EMPTY_XZ = new double[0];
    private int radarRotate;                 // sweep angle 0..360, advances radarSweepSpeed() per client tick
    private int prevRadarRotate;             // last tick's sweep, for smooth interpolation of the sweep line
    private double[] radarEntityXZ = EMPTY_XZ; // neutral blips: relative (x0,z0,x1,z1,…) in blocks from this vehicle
    private double[] radarEnemyXZ = EMPTY_XZ;  // hostile blips (Monster), same layout

    /** True if this vehicle's config mounts an entity radar ({@code EnableEntityRadar}) — the HUD {@code have_radar} gate
     *  (reference {@code isEntityRadarMounted}). */
    public boolean isRadarMounted() {
        MCH_AircraftInfo info = weaponHostInfo();
        return info != null && info.isEnableEntityRadar;
    }

    /** Degrees the radar sweep advances per client tick — reference per-type {@code updateRadar} speed: heli/vehicle 5,
     *  tank/plane 10. Both divide 360 so the sweep lands exactly on 0 (where the re-scan fires). */
    protected int radarSweepSpeed() { return 5; }

    /** Advance the radar sweep and, once per full rotation, re-scan nearby entities. Only runs while the vehicle is
     *  ridden (the radar is a rider-only cockpit display), matching the reference calling {@code updateRadar} from the
     *  client tick handler for the ridden aircraft. */
    private void advanceRadar() {
        if (!isRadarMounted() || getPassengers().isEmpty()) {
            return;
        }
        this.prevRadarRotate = this.radarRotate;
        this.radarRotate += radarSweepSpeed();
        if (this.radarRotate >= 360) {
            this.radarRotate = 0;
        }
        if (this.radarRotate == 0) {
            scanRadar(64);
        }
    }

    /** Port of {@code MCH_Radar.updateXZ}: collect every living entity within {@code range} blocks (circular) as a blip
     *  at its position RELATIVE to this vehicle, split into hostile ({@code Monster}) and neutral, skipping any entity
     *  buried under 5+ solid blocks (the reference's crude line-of-sight cull). Client-only. */
    private void scanRadar(int range) {
        java.util.List<Double> ent = new java.util.ArrayList<>();
        java.util.List<Double> enm = new java.util.ArrayList<>();
        net.minecraft.world.phys.AABB box = this.getBoundingBox().inflate(range);
        net.minecraft.core.BlockPos.MutableBlockPos bp = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (Entity e : this.level().getEntities(this, box)) {
            if (!(e instanceof LivingEntity)) {
                continue;
            }
            double x = e.getX() - this.getX();
            double z = e.getZ() - this.getZ();
            if (x * x + z * z >= (double) range * range) {
                continue;
            }
            int ex = (int) Math.floor(e.getX());
            int ez = (int) Math.floor(e.getZ());
            int y = Math.max(1, 1 + (int) e.getY());
            int blocks = 0;
            for (; y < 200 && blocks < 5; y++) {
                if (!this.level().getBlockState(bp.set(ex, y, ez)).isAir()) {
                    blocks++;
                }
            }
            if (blocks >= 5) {
                continue; // buried under thick cover — the reference hides it from the radar
            }
            java.util.List<Double> dst = e instanceof net.minecraft.world.entity.monster.Monster ? enm : ent;
            dst.add(x);
            dst.add(z);
        }
        this.radarEntityXZ = toXZArray(ent);
        this.radarEnemyXZ = toXZArray(enm);
    }

    private static double[] toXZArray(java.util.List<Double> list) {
        if (list.isEmpty()) {
            return EMPTY_XZ;
        }
        double[] out = new double[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    /** Interpolated radar sweep angle for the HUD sweep line (reference {@code getRadarRot}: smooth the tick sweep,
     *  adding 360 across the 355→0 wrap so it never spins backwards). */
    public float radarRotInterp(float partialTick) {
        float rot = this.radarRotate;
        float prev = this.prevRadarRotate;
        if (rot < prev) {
            rot += 360.0F;
        }
        return prev + (rot - prev) * partialTick;
    }

    /** Neutral radar blips (relative x,z pairs in blocks) for the HUD {@code DrawEntityRadar}. */
    public double[] radarEntityXZ() { return this.radarEntityXZ; }
    /** Hostile radar blips (relative x,z pairs) for the HUD {@code DrawEnemyRadar}. */
    public double[] radarEnemyXZ() { return this.radarEnemyXZ; }

    // ---- Weapon recoil kick (reference MCH_EntityAircraft.updateRecoil) — CLIENT-only visual, driven by DATA_RECOIL_SEQ ----
    private int lastRecoilSeq = -1;
    private int clientRecoilCount;
    private int prevClientRecoilCount;
    private float clientRecoilValue;
    private float clientRecoilYaw;

    /** Advance the client recoil kick each client tick: a new shot (seq bump) restarts the 30-tick countdown; else decay. */
    private void advanceRecoil() {
        int seq = this.entityData.get(DATA_RECOIL_SEQ);
        if (seq != this.lastRecoilSeq) {
            // First tick tracking this entity (lastRecoilSeq still -1): ADOPT the already-synced seq without firing a
            // kick, else a vehicle that fired before we came into render range plays a phantom recoil on our first tick.
            boolean firstObserve = this.lastRecoilSeq < 0;
            this.lastRecoilSeq = seq;
            if (seq != 0 && !firstObserve) {
                this.clientRecoilCount = 30;
                this.clientRecoilValue = this.entityData.get(DATA_RECOIL_VALUE);
                this.clientRecoilYaw = this.entityData.get(DATA_RECOIL_YAW);
            }
        }
        this.prevClientRecoilCount = this.clientRecoilCount;
        if (this.clientRecoilCount > 0) {
            this.clientRecoilCount--;
        }
    }

    /** Recoil PITCH offset (degrees) this frame — a nose-up kick for a forward gun (reference splits the kick by the
     *  weapon aim: pitch = cos(recoilYaw), roll = sin(recoilYaw)), easing out over the count. Added to the model render
     *  and the first-person camera, NOT the physics — so it never fights the client-authored rotation sync. */
    public float recoilPitchDeg(float partialTick) {
        float k = recoilEnvelope(partialTick);
        return k == 0.0F ? 0.0F : -k * Mth.cos(this.clientRecoilYaw * Mth.DEG_TO_RAD);
    }

    /** Recoil ROLL offset (degrees) this frame — the side-gun component. */
    public float recoilRollDeg(float partialTick) {
        float k = recoilEnvelope(partialTick);
        return k == 0.0F ? 0.0F : k * Mth.sin(this.clientRecoilYaw * Mth.DEG_TO_RAD);
    }

    /** Peak-kick multiplier on the config {@code Recoil} value. The reference ACCUMULATES its per-tick impulse over the
     *  30→12 window (a small per-tick value integrates into a much larger displacement the flight model then returns);
     *  this port applies a single non-accumulating visual offset, so it multiplies up to a comparable felt jolt. Tune
     *  here if the kick reads too strong/weak. */
    private static final float RECOIL_GAIN = 3.0F;

    private float recoilEnvelope(float partialTick) {
        float count = Mth.lerp(partialTick, this.prevClientRecoilCount, this.clientRecoilCount);
        if (count < 12.0F) {
            return 0.0F; // reference gate: the kick applies over count 30..12 only
        }
        return (count - 12.0F) / 18.0F * this.clientRecoilValue * RECOIL_GAIN; // full kick at the shot, easing to 0
    }

    /** The value synced into {@link #getThrottleInput} for the sound loop — the flight throttle by default. A tank adds
     *  its maneuver-rev term (reference {@code soundVolumeTarget}) so it revs when turning/reversing, not just forward. */
    protected double soundThrottle() { return simThrottle(); }

    // ---- Engine loop sound (reference MCH_SoundUpdater + per-type getSoundVolume/getSoundPitch/getSoundName) ----

    /** The engine-loop sound basename — the config {@code Sound=} ({@code soundMove}) if set, else the per-type
     *  default; {@code ""} means NO engine loop (the reference {@code getSoundName}). */
    public String engineSoundName() {
        MCH_AircraftInfo info = weaponHostInfo();
        if (info != null && info.soundMove != null && !info.soundMove.isEmpty()) {
            return info.soundMove;
        }
        return defaultEngineSound();
    }

    /** Per-type default engine sound ({@code heli}/{@code plane}/{@code prop}); {@code ""} == silent (base + ground). */
    protected String defaultEngineSound() {
        return "";
    }

    /** Loop VOLUME 0..1 — the reference per-type {@code getSoundVolume() × info.soundVolume}. Base 0 (silent). */
    public float engineSoundVolume() {
        return 0.0F;
    }

    /** Loop PITCH multiplier — the reference per-type {@code getSoundPitch() × info.soundPitch}. Base 1.0. */
    public float engineSoundPitch() {
        return 1.0F;
    }

    /** Config {@code SoundVolume} (default 1.0) — the per-type volume/pitch multiply by this. */
    protected float configSoundVolume() {
        MCH_AircraftInfo info = weaponHostInfo();
        return info != null ? info.soundVolume : 1.0F;
    }

    /** Config {@code SoundPitch} (default 1.0). */
    protected float configSoundPitch() {
        MCH_AircraftInfo info = weaponHostInfo();
        return info != null ? info.soundPitch : 1.0F;
    }

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
        this.entityData.set(DATA_FUEL, tag.getInt("AcFuel"));
        if (tag.contains("FuelSlots")) {
            this.fuelInventory.fromTag(tag.getList("FuelSlots", net.minecraft.nbt.Tag.TAG_COMPOUND), this.registryAccess());
        }
        // Riders re-board AFTER load, so remember each one's seat by UUID and hand it back in addPassenger.
        this.restoredSeats.clear();
        for (net.minecraft.nbt.Tag e : tag.getList("Seats", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag s = (CompoundTag) e;
            this.restoredSeats.put(s.getUUID("Id"), s.getInt("Seat"));
        }
        this.savedAmmo = tag.contains("AcWeaponsAmmo") ? tag.getIntArray("AcWeaponsAmmo") : null;
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("Config", configName());
        tag.putInt("AcDamage", getDamageTaken());
        tag.putBoolean("Destroyed", isDestroyed());
        tag.putInt("Despawn", this.despawnCount);
        tag.putInt("AcFuel", getFuel());
        tag.put("FuelSlots", this.fuelInventory.createTag(this.registryAccess()));
        net.minecraft.nbt.ListTag seats = new net.minecraft.nbt.ListTag();
        for (java.util.Map.Entry<UUID, Integer> e : this.seatByUuid.entrySet()) {
            CompoundTag s = new CompoundTag();
            s.putUUID("Id", e.getKey());
            s.putInt("Seat", e.getValue());
            seats.add(s);
        }
        tag.put("Seats", seats);
        if (this.weapons != null) {
            int[] ammo = new int[this.weapons.size()];
            for (int i = 0; i < ammo.length; i++) {
                ammo[i] = this.weapons.get(i).totalAmmo(); // magazine + reserve; -1 for a weapon with no economy
            }
            tag.putIntArray("AcWeaponsAmmo", ammo);
        }
    }

    /** Ammo totals read from NBT, applied once the weapon list exists (it is built lazily on the first weapon tick). */
    private int[] savedAmmo;

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

    /**
     * Click a SPECIFIC seat to board it (the reference lets you right-click a seat's own 1×1 box, so a gunner can take
     * their station even on a pilotless craft). The nearest allowed free seat within 1 block of the hit point wins;
     * otherwise this falls through to {@link #interact} and the normal first-free-seat rule.
     */
    @Override
    public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
        if (isDestroyed() || player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        MCH_AircraftInfo info = weaponHostInfo();
        if (info == null || info.seatList == null) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide) {
            int best = -1;
            double bestD = 1.0; // the reference seat box is 1×1
            Vec3 hitWorld = position().add(hit);
            for (int i = 0; i < seatCount() && i < info.seatList.size(); i++) {
                if (i == 0 && !info.canRide) {
                    continue; // config CanRide=false (the fuel truck, the ammo box): the pilot seat is NOT boardable —
                              // firstFreeSeatFor honours this, but a clicked seat is taken as PREFERRED and would skip it
                }
                if (!canOccupySeat(i)) {
                    continue;
                }
                double d = position().add(seatOffsetWorld(i, player, 1.0F)).distanceTo(hitWorld);
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
            if (best >= 0) {
                this.pendingSeat.put(player.getUUID(), best);
                if (!player.startRiding(this)) {
                    this.pendingSeat.remove(player.getUUID());
                    return InteractionResult.PASS;
                }
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    /** World-space offset of seat {@code i} (the same transform the attachment uses) — for seat picking. */
    private Vec3 seatOffsetWorld(int seat, Entity rider, float partialTick) {
        MCH_AircraftInfo info = weaponHostInfo();
        MCH_SeatInfo si = info.seatList.get(seat);
        org.joml.Vector3f p = new org.joml.Vector3f((float) si.pos.x(), (float) si.pos.y(), (float) si.pos.z());
        if (si.rotSeat) {
            org.joml.Vector3f tp = new org.joml.Vector3f(
                (float) info.turretPosition.x(), (float) info.turretPosition.y(), (float) info.turretPosition.z());
            p.sub(tp).rotateY((float) Math.toRadians(-turretOrbitYaw(partialTick))).add(tp);
        }
        p.rotate(bodyRotation(partialTick));
        return new Vec3(p.x, (double) p.y - modelMinY(), p.z);
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
        updateAimLatch(); // the pilot-aim latch feeding the turret ring / rotSeat crew seats (both sides)

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
            advanceRotorSpin(); // heli rotor / plane propeller spin — CONFIG rotorSpeed, no hardcoded rate
            advanceRadar();     // entity-radar sweep + periodic entity scan (only while ridden), for the cockpit HUD
            advanceRecoil();    // weapon recoil kick countdown (model + first-person camera), from the synced trigger
            clientAmbientEffects(); // rotor down-wash, damage smoke — the reference's onUpdate_Client particle spawns
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

        if (pilot() == null) {
            getControlState().clearMomentary(); // no PILOT: the vehicle holds no drive keys and coasts (a lone gunner
            this.riderOwnsRotation = false;     // may still aim + fire, but never drives)
        }
        ControlInput in = getControlState().snapshot(1.0F); // the PILOT's keys; server tick -> partialTicks = 1.0F

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
        // Gradual so rotor RPM / sound ramp up and down instead of snapping. This is the ROTOR-SPIN signal (idles at 0.5
        // so a boarded heli's blades keep turning) — it is deliberately NOT the sound/particle signal (see below).
        float target = pilot() == null ? 0.0F : (getControlState().throttleUp ? 1.0F : 0.5F);
        this.enginePower += (target - this.enginePower) * 0.05F;
        this.entityData.set(DATA_THROTTLE, this.enginePower);
        // Sync the ACTUAL flight throttle (the port of the reference getCurrentThrottle) — 0 at rest, rising only as you
        // spool up. The sound loop volume/pitch and the rotor down-wash key off THIS, not the 0.5-idle rotor spin, so a
        // parked heli is silent with no dust until you throttle up (reference MCH_SoundUpdater / onUpdate_ParticleSandCloud).
        this.entityData.set(DATA_SIM_THROTTLE, (float) Math.max(0.0, Math.min(1.0, soundThrottle())));

        // Landing gear: deployed (0) on the ground, retracting toward 90 once airborne. Cosmetic; planes with an
        // AddPartLG list animate it, others just carry the value.
        float gearTarget = this.onGround() ? 0.0F : 90.0F;
        this.gearAngle += (gearTarget - this.gearAngle) * 0.12F;
        this.entityData.set(DATA_GEAR, this.gearAngle);

        // Fuel economy: burn with the sim throttle, and run any supply aura this config declares (reference order —
        // supply/fuel run alongside the weapon tick).
        updateFuel();
        siphonFuelSlots();   // fuel cans in the GUI slots -> the tank
        updateSupply();

        // Weapons: config-driven loadout (built lazily), cycled by the switch payload and fired while held.
        tickWeapons();

        // Flares/countermeasures: advance the dispense window, eject volleys, and decoy any missiles homing on us.
        tickFlares();
    }

    // ---- flares / countermeasures (#26) — server-authoritative dispenser + missile decoy ----

    private final mcheli.agnostic.aircraft.MCH_FlareInfo flareInfo = new mcheli.agnostic.aircraft.MCH_FlareInfo();
    private int currentFlareIndex; // which of the config's flare.types[] the next press dispenses

    /** This vehicle's config declares flares (reference {@code haveFlare}: FlareType set). */
    public boolean haveFlare() {
        MCH_AircraftInfo info = weaponHostInfo();
        return info != null && info.haveFlare();
    }

    /** The decoy-active burn window is open (reference {@code isFlareUsing}). */
    public boolean isFlareUsing() { return this.flareInfo.isUsing(); }

    /** Ready to dispense (has flares + the dispenser is idle). */
    public boolean canDeployFlare() { return haveFlare() && this.flareInfo.canUseFlare(); }

    /** HUD cooldown fraction 1..0 while a dispense window runs. */
    public float flareCooldownFraction() { return this.flareInfo.cooldownFraction(); }

    /** True if the flare dispenser is idle/ready — reads the SYNCED state, so it is correct on the client (the
     *  {@link #flareInfo} itself is only ticked server-side). */
    public boolean flareDispenserIdle() { return this.entityData.get(DATA_FLARE_STATE) == 0; }

    /** The flare type the next dispense will use (cycles the config's {@code FlareType} list). */
    public int currentFlareType() {
        MCH_AircraftInfo info = weaponHostInfo();
        if (info == null || info.flare.types.length == 0) {
            return 1;
        }
        return info.flare.types[Math.floorMod(this.currentFlareIndex, info.flare.types.length)];
    }

    /** Advance to the next configured flare type (reference {@code nextFlareType}). */
    public void nextFlareType() { this.currentFlareIndex++; }

    /** Begin a dispense of {@code type} (server-side; reference {@code useFlare}). */
    public void deployFlare(int type) { this.flareInfo.use(type); }

    /** Server tick: step the dispense schedule, eject each due volley, then run the missile decoy while flaring. */
    private void tickFlares() {
        if (this.level().isClientSide || !haveFlare()) {
            return;
        }
        this.flareInfo.tickDown();
        // ONE volley per qualifying tick (reference MCH_Flare.update uses an `if`, not a loop): tick is only stepped
        // by tickDown() above, so a `while` here would dump the WHOLE flare complement in a single tick instead of
        // streaming it across the window.
        if (this.flareInfo.shouldSpawnVolley()) {
            spawnFlareVolley();
            this.flareInfo.onVolleySpawned();
        }
        this.entityData.set(DATA_FLARE_STATE, (byte) (this.flareInfo.isPreparing() ? 1 : 0)); // sync the HUD cue
        // Decoy (reference MCH_MissileDetector.destroyMissile): while the burn window is active, null the target of
        // every guided round homing on us within SEARCH_RANGE and discard it (tickGuidance would self-discard on the
        // nulled target next tick; the explicit discard matches the reference for immediacy).
        if (this.flareInfo.isUsing() && !isDestroyed()) {
            double r = mcheli.agnostic.aircraft.MCH_FlareInfo.SEARCH_RANGE;
            AABB box = this.getBoundingBox().inflate(r, r, r);
            for (MchBullet b : this.level().getEntitiesOfClass(MchBullet.class, box)) {
                if (b.isGuided() && b.isHomingOn(this)) {
                    b.decoyClear();
                    b.discard();
                }
            }
        }
    }

    /** Eject one volley of {@code num} flares from the config {@code FlareOption} point, behind + below the hull, with
     *  the per-type spread velocity (reference {@code MCH_Flare.spawnFlare}). */
    private void spawnFlareVolley() {
        MCH_AircraftInfo info = weaponHostInfo();
        if (info == null) {
            return;
        }
        int type = this.flareInfo.flareType();
        int num = this.flareInfo.volleyNum();
        int fuse = this.flareInfo.fuseCount();
        boolean floaty = mcheli.agnostic.aircraft.MCH_FlareInfo.isFloatyType(type);

        // Emit point = FlareOption offset (model space) rotated by the hull, at the vehicle position.
        float hullYaw = Mth.rotLerp(1.0F, this.yRotO, getYRot());
        float roll = getRollAngle();
        org.joml.Quaternionf qHull = new org.joml.Quaternionf()
            .rotateY((float) Math.toRadians(-hullYaw))
            .rotateX((float) Math.toRadians(getXRot()))
            .rotateZ((float) Math.toRadians(roll));
        org.joml.Vector3f fpos = new org.joml.Vector3f(
            (float) info.flare.pos.x(), (float) info.flare.pos.y(), (float) info.flare.pos.z());
        qHull.transform(fpos);
        Vec3 mot = this.getDeltaMovement();
        double baseX = this.getX() + fpos.x - mot.x * 2.0;
        double baseY = this.getY() + fpos.y - mot.y * 2.0 - 1.0 + (type == 10 ? 2.0 : 0.0);
        double baseZ = this.getZ() + fpos.z - mot.z * 2.0;

        for (int i = 0; i < num; i++) {
            double[] v = mcheli.agnostic.aircraft.MCH_FlareInfo.volleyMotion(type, i, num, mot.x, mot.y, mot.z,
                hullYaw, this.random.nextFloat(), this.random.nextFloat(), this.random.nextFloat());
            MchFlare.spawn(this.level(), new Vec3(baseX, baseY, baseZ), new Vec3(v[0], v[1], v[2]), fuse, floaty);
        }
        // Dispense report (reference plays random.click on use + an aux pop per volley).
        this.level().playSound(null, baseX, baseY, baseZ, net.minecraft.sounds.SoundEvents.FIRECHARGE_USE,
            SoundSource.NEUTRAL, 0.6F, 1.4F);
    }

    // ---- config-driven weapons ----

    // ---- SEAT MAP (#36) — 1.21.1 multi-passenger replaces the reference's per-seat MCH_EntitySeat entities. Seat 0 is
    //      the pilot; the map is server truth, mirrored to clients via DATA_SEATS and persisted by rider UUID. ----

    private final java.util.Map<UUID, Integer> seatByUuid = new java.util.HashMap<>();   // server truth
    private final java.util.Map<UUID, Integer> pendingSeat = new java.util.HashMap<>();  // interactAt -> addPassenger
    private final java.util.Map<UUID, Integer> restoredSeats = new java.util.HashMap<>();// NBT -> addPassenger

    /** How many seats this config declares (rack slots are reserved in the index space but never boardable). */
    public int seatCount() {
        MCH_AircraftInfo info = weaponHostInfo();
        return info != null ? Math.max(1, info.getNumSeat()) : 1;
    }

    /** The seat {@code p} occupies, or -1. Server reads its own map; the client reads the synced mirror. A -1 on the
     *  client is a tolerated DATA_SEATS-vs-SetPassengers packet race — callers SKIP that rider for the frame. */
    public int seatIndexOf(Entity p) {
        if (p == null || !hasPassenger(p)) {
            return -1;
        }
        if (!this.level().isClientSide) {
            Integer s = this.seatByUuid.get(p.getUUID());
            return s != null ? s : -1;
        }
        CompoundTag t = this.entityData.get(DATA_SEATS);
        for (String k : t.getAllKeys()) {
            if (t.getInt(k) == p.getId()) {
                try {
                    return Integer.parseInt(k);
                } catch (NumberFormatException ignored) {
                    break;
                }
            }
        }
        // The synced map has not arrived for this rider yet (it lags the passenger list by a packet). Fall back to
        // MOUNT ORDER, which is exactly how seats are assigned anyway — so control and rendering never stall waiting
        // on a packet. The SERVER is authoritative (it checks its own map before honouring any drive bits), so a brief
        // client-side guess can only ever be cosmetic, and it self-corrects the instant DATA_SEATS lands.
        return getPassengers().indexOf(p);
    }

    /**
     * The PLAYER operating {@code w} right now, or null — the port of {@code getWeaponUserByWeaponName}
     * ({@code MCH_EntityAircraft:3061-3076}): the occupant of the weapon's own seat, else the pilot but ONLY when the
     * weapon is {@code canUsePilot}. This is what decides whether a gun aims (and whose look it follows), whether it
     * can be selected/fired, and — for the local player — whether {@code HideGM} hides it.
     */
    public Entity weaponOperator(MCH_AircraftInfo.Weapon w) {
        if (w == null) {
            return null;
        }
        Entity e = seatPassenger(w.seatID);
        if (!(e instanceof Player) && w.canUsePilot) {
            e = pilot();
        }
        return e instanceof Player ? e : null;
    }

    // ---- GUNNER MODE (#36b) — reference isGunnerMode (pilot toggle) + the structural gunner-seat mode ----

    /** The PILOT's toggled gunner mode (synced). The flight sim reads this; clients read it for camera/zoom. */
    public boolean isGunnerModeActive() {
        return this.entityData.get(DATA_GUNNER_MODE);
    }

    /**
     * Is {@code seat}'s occupant in gunner mode right now — the port of {@code getIsGunnerMode}
     * ({@code MCH_EntityAircraft:5349}): the pilot iff {@link #isGunnerModeActive}; a gunner SEAT is permanently in
     * gunner mode unless it is {@code switchgunner} (the switchable toggle is not ported — a switchable seat reads as
     * NOT in gunner mode until toggled, which the port leaves off). Drives the gunner camera + seat-pitch clamp.
     */
    public boolean isSeatGunnerMode(int seat) {
        MCH_AircraftInfo info = weaponHostInfo();
        if (info == null || seat < 0) {
            return false;
        }
        if (seat == 0) {
            return info.isEnableGunnerMode && isGunnerModeActive();
        }
        MCH_SeatInfo si = seat < info.seatList.size() ? info.seatList.get(seat) : null;
        return si != null && si.gunner && !si.switchgunner; // a dedicated gunner seat is always "in gunner mode"
    }

    /** May the pilot toggle gunner mode — the port of {@code canSwitchGunnerMode} ({@code MCH_EntityAircraft:5294}):
     *  the config must enable it, and (unless it enables CONCURRENT gunner mode) seat 1 must be empty so a seat-1 gunner
     *  and the pilot-gunner never fight over the same weapon. (Canopy/hovering gates collapse — the port has no canopy
     *  state and the manual-hover mode is unported.) */
    public boolean canSwitchGunnerMode() {
        MCH_AircraftInfo info = weaponHostInfo();
        if (info == null || !info.isEnableGunnerMode) {
            return false;
        }
        // Reference MCH_EntityAircraft:5300 blocks only on a PLAYER in seat 1 (a mob gunner does not contend for the
        // weapon), unless concurrent gunner mode is enabled. Per-type attitude/throttle gates are added in overrides.
        return info.isEnableConcurrentGunnerMode || !(seatPassenger(1) instanceof Player);
    }

    /** Toggle the pilot's gunner mode (server-authoritative). Only the pilot may, and only when {@link
     *  #canSwitchGunnerMode}. Turning it ON is always allowed OFF again (the gate only blocks entering). */
    public void toggleGunnerMode(Player player) {
        if (this.level().isClientSide || seatIndexOf(player) != 0) {
            return;
        }
        boolean now = isGunnerModeActive();
        if (!now && !canSwitchGunnerMode()) {
            return; // entering is gated; leaving is always allowed
        }
        this.entityData.set(DATA_GUNNER_MODE, !now);
    }

    /** Who is sitting in {@code seat}, or null. */
    public Entity seatPassenger(int seat) {
        for (Entity p : getPassengers()) {
            if (seatIndexOf(p) == seat) {
                return p;
            }
        }
        return null;
    }

    /** The PILOT (seat 0) — the ONLY rider who drives. Never use {@code getFirstPassenger()} for this: a gunner may
     *  legitimately board a pilotless craft, and passenger order is mount order. */
    public Entity pilot() {
        return seatPassenger(0);
    }

    // DELIBERATELY NOT overriding getControllingPassenger(): returning the pilot would flip this entity into vanilla's
    // client-authoritative boat/minecart regime — isControlledByLocalInstance() becomes true on the pilot's client, which
    // then ships ServerboundMoveVehiclePacket every tick and the server absMoveTo()s the vehicle onto it, OVERWRITING the
    // server physics (and arming the "flying a vehicle" kick on a dedicated server with allow-flight=false). This port is
    // server-authoritative for POSITION (see the class doc); pilot() is our own concept and must stay out of vanilla's.

    /** May {@code seat} be boarded right now? Honors the config's {@code ExclusionSeat} groups (at most ONE occupant
     *  across each group) and excludes rack slots (reference {@code canRideMob}: racks take neither players nor mobs). */
    public boolean canOccupySeat(int seat) {
        MCH_AircraftInfo info = weaponHostInfo();
        if (info == null || seat < 0 || seat >= seatCount()) {
            return false;
        }
        if (seatPassenger(seat) != null) {
            return false;
        }
        if (info.exclusionSeatList != null) {
            for (Integer[] group : info.exclusionSeatList) {
                boolean member = false;
                for (Integer id : group) {
                    if (id != null && id == seat) {
                        member = true;
                        break;
                    }
                }
                if (!member) {
                    continue;
                }
                for (Integer id : group) {
                    if (id != null && id != seat && seatPassenger(id) != null) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Seat choice for a boarding rider: an explicitly-clicked seat if free, else the pilot seat (players only, and only
     *  when the config allows riding), else the first free crew seat. -1 == the vehicle is full. */
    private int firstFreeSeatFor(Entity rider, int preferred) {
        MCH_AircraftInfo info = weaponHostInfo();
        if (preferred >= 0 && canOccupySeat(preferred)) {
            return preferred;
        }
        if (rider instanceof Player && info != null && info.canRide && canOccupySeat(0)) {
            return 0;
        }
        for (int i = 1; i < seatCount(); i++) {
            if (canOccupySeat(i)) {
                return i;
            }
        }
        return -1;
    }

    /** Programmatically request that {@code rider} take a specific {@code seat} on its NEXT boarding — the code-path
     *  equivalent of clicking that seat's box ({@link #interactAt}), for mounts that never go through an interact:
     *  scripted crew, NPCs, or the headless self-tests. Server-side; honored by {@link #firstFreeSeatFor} exactly like
     *  a clicked seat (so it can seat a non-player in the pilot seat, which the automatic rule reserves for players). */
    public void preferSeat(Entity rider, int seat) {
        if (!this.level().isClientSide) {
            this.pendingSeat.put(rider.getUUID(), seat);
        }
    }

    @Override
    protected boolean canAddPassenger(Entity p) {
        return firstFreeSeatFor(p, this.pendingSeat.getOrDefault(p.getUUID(), -1)) >= 0;
    }

    @Override
    protected void addPassenger(Entity p) {
        super.addPassenger(p);
        if (this.level().isClientSide) {
            return;
        }
        Integer saved = this.restoredSeats.remove(p.getUUID());
        int seat = (saved != null && canOccupySeat(saved))
            ? saved
            : firstFreeSeatFor(p, this.pendingSeat.getOrDefault(p.getUUID(), -1));
        this.pendingSeat.remove(p.getUUID());
        if (seat < 0) {
            return;
        }
        this.seatByUuid.put(p.getUUID(), seat);
        syncSeatTag();
        // A creative rider tops every magazine; the arriving crew member may also bump the pilot off a weapon he was
        // only borrowing while this seat was empty (reference onMountPlayerSeat:3443-3453).
        if (this.weapons != null) {
            this.weapons.onMount(p instanceof Player pl && pl.getAbilities().instabuild);
            this.weapons.refreshEligibility(s -> seatPassenger(s) instanceof Player);
            // The auto-bump may have moved the PILOT off a weapon he was only borrowing — republish, or his HUD keeps
            // naming a weapon he is no longer firing.
            this.entityData.set(DATA_WEAPON, this.weapons.selectedIndex(0));
        }
    }

    @Override
    protected void removePassenger(Entity p) {
        super.removePassenger(p);
        if (this.level().isClientSide) {
            return;
        }
        Integer seat = this.seatByUuid.remove(p.getUUID());
        syncSeatTag();
        if (seat != null) {
            clearSeatControls(seat);
        }
        resetGunnerModeIfNoPilot(); // the pilot left -> drop their gunner mode (reference switchGunnerMode(false) on unmount)
        if (this.weapons != null) {
            this.weapons.refreshEligibility(s -> seatPassenger(s) instanceof Player);
            this.entityData.set(DATA_WEAPON, this.weapons.selectedIndex(0)); // a departing gunner frees his weapon
        }
    }

    /** Is passenger seat {@code i} unoccupied? Racks are outside the {@code 0..seatCount()-1} range (the port captures
     *  {@code mobSeatNum} before racks are appended), so iterating this range is already rack-free. */
    private boolean isSeatEmpty(int i) {
        return i >= 0 && i < seatCount() && seatPassenger(i) == null;
    }

    /**
     * Move to the next-higher empty PASSENGER seat, wrapping — the port of {@code switchNextSeat}
     * ({@code MCH_EntityAircraft:4419}). Seats 1..N only (the pilot seat is reachable only via {@link #grabPilotSeat}).
     * A pilot caller (seat 0) lands in the lowest empty passenger seat, vacating seat 0.
     */
    public void switchSeatNext(Player player) {
        if (this.level().isClientSide || seatCount() <= 1) {
            return;
        }
        int cur = seatIndexOf(player);
        if (cur < 0) {
            return;
        }
        int to = nextSeatTarget(cur, seatCount(), this::isSeatEmpty);
        if (to >= 0) {
            moveToSeat(player, cur, to);
        }
    }

    /** The next-higher empty PASSENGER seat from {@code cur}, wrapping to the lowest empty; -1 if none. Seat 0 is never
     *  a next/prev target (only {@link #grabPilotSeat} reaches it). Pure so the seat-cycle logic can be unit-tested. */
    public static int nextSeatTarget(int cur, int count, java.util.function.IntPredicate empty) {
        for (int i = cur + 1; i < count; i++) {   // next-higher empty (for a pilot caller this scans all of 1..N-1)
            if (empty.test(i)) { return i; }
        }
        for (int i = 1; i < cur; i++) {           // wrap to lowest empty passenger seat
            if (empty.test(i)) { return i; }
        }
        return -1;
    }

    /** The next-lower empty PASSENGER seat from {@code cur}, wrapping to the highest empty; -1 if none. Pure/testable. */
    public static int prevSeatTarget(int cur, int count, java.util.function.IntPredicate empty) {
        for (int i = cur - 1; i >= 1; i--) {      // next-lower empty (excludes seat 0)
            if (empty.test(i)) { return i; }
        }
        for (int i = count - 1; i > cur; i--) {   // wrap to highest empty passenger seat
            if (empty.test(i)) { return i; }
        }
        return -1;
    }

    /**
     * Move to the next-lower empty PASSENGER seat, wrapping — the port of {@code switchPrevSeat}
     * ({@code MCH_EntityAircraft:4462}). A pilot caller lands in the highest empty passenger seat.
     */
    public void switchSeatPrev(Player player) {
        if (this.level().isClientSide || seatCount() <= 1) {
            return;
        }
        int cur = seatIndexOf(player);
        if (cur < 0) {
            return;
        }
        int to = prevSeatTarget(cur, seatCount(), this::isSeatEmpty);
        if (to >= 0) {
            moveToSeat(player, cur, to);
        }
    }

    /**
     * Take the pilot seat if it is free — the useful case of the reference's {@code switchSeat==3} path
     * ({@code MCH_AircraftPacketHandler:300-304}, which dismounts then re-boards as pilot when seat 0 is empty). A
     * gunner promotes to pilot; if the pilot seat is taken (or the config forbids riding), this is a no-op.
     */
    public void grabPilotSeat(Player player) {
        if (this.level().isClientSide) {
            return;
        }
        MCH_AircraftInfo info = weaponHostInfo();
        int cur = seatIndexOf(player);
        if (cur <= 0 || info == null || !info.canRide || !canOccupySeat(0)) {
            return;
        }
        moveToSeat(player, cur, 0);
    }

    /** Re-key a rider from {@code from} to {@code to} in the seat map, running the same side effects as boarding: clear
     *  the old seat's stale controls, resync the map, and refresh weapon eligibility + the HUD selection. The rider is
     *  ALREADY a passenger, so no vanilla remount happens (unlike the reference's seat-entity juggling). */
    private void moveToSeat(Player player, int from, int to) {
        this.seatByUuid.put(player.getUUID(), to);
        clearSeatControls(from);   // a key held in the old seat must not linger (e.g. the pilot's throttle on leaving)
        syncSeatTag();
        resetGunnerModeIfNoPilot(); // a pilot who switches to a gunner seat drops their gunner mode
        if (this.weapons != null) {
            this.weapons.refreshEligibility(s -> seatPassenger(s) instanceof Player);
            this.entityData.set(DATA_WEAPON, this.weapons.selectedIndex(0));
        }
    }

    /** Gunner mode belongs to the pilot; if seat 0 is now empty, drop it (reference resets {@code switchGunnerMode(false)}
     *  when the operator leaves — otherwise a pilotless heli would hover forever). */
    private void resetGunnerModeIfNoPilot() {
        if (seatPassenger(0) == null && isGunnerModeActive()) {
            this.entityData.set(DATA_GUNNER_MODE, false);
        }
    }

    /** Publish the seat map (seatIndex -> passenger entity id) to every tracking client. */
    private void syncSeatTag() {
        CompoundTag t = new CompoundTag();
        for (Entity p : getPassengers()) {
            Integer seat = this.seatByUuid.get(p.getUUID());
            if (seat != null) {
                t.putInt(Integer.toString(seat), p.getId());
            }
        }
        this.entityData.set(DATA_SEATS, t);
    }

    // ---- fuel & supply economy (#37) — reference MCH_EntityAircraft:2223-2308 (fuel), :2264-2451 (supply auras) ----

    private double fuelConsumptionAcc; // fractional carry, so a <1/sec burn rate still drains over time
    private int fuelSuppliedCount;     // ticks of consumption PAUSE granted by a nearby supplier (reference sets 40)

    /** The vehicle's 3 FUEL SLOTS (reference {@code MCH_AircraftInventory} SLOT_FUEL0..2). Drop a fuel can in and the
     *  tank siphons it. Persisted in NBT and dropped when the wreck despawns. */
    public static final int FUEL_SLOTS = 3;
    private final net.minecraft.world.SimpleContainer fuelInventory = new net.minecraft.world.SimpleContainer(FUEL_SLOTS);

    public net.minecraft.world.SimpleContainer fuelInventory() {
        return this.fuelInventory;
    }

    /**
     * Siphon the fuel slots into the tank — the port of {@code MCH_EntityAircraft.updateSupplyFuel} (:2310-2335): every
     * 10 ticks, while the vehicle {@link #canSupply() is on the ground} and the tank is not full, each can gives up to
     * {@code 100} units (capped by what the tank needs and what the can still holds).
     */
    private void siphonFuelSlots() {
        if (this.level().isClientSide || getMaxFuel() <= 0 || this.tickCount % 10 != 0) {
            return;
        }
        // The reference wraps BOTH the burn and this siphon in !isDestroyed() (MCH_EntityAircraft:2295): a wreck lies
        // grounded for ~25 s before it despawns, and without this gate canSupply() would stay true and drain the cans
        // it is about to drop.
        if (isDestroyed() || getFuel() >= getMaxFuel() || !canSupply()) {
            return;
        }
        int fuel = getFuel();
        for (int i = 0; i < FUEL_SLOTS && fuel < getMaxFuel(); i++) {
            ItemStack can = this.fuelInventory.getItem(i);
            if (can.getItem() instanceof mcheli.dependent.item.MchFuelItem) {
                int want = Math.min(100, getMaxFuel() - fuel);
                fuel += mcheli.dependent.item.MchFuelItem.drain(can, want);
            }
        }
        setFuel(fuel);
    }

    /**
     * Rearm ONE weapon by index from the riding GUI — the port of {@code supplyAmmo} / {@code canPlayerSupplyAmmo}: the
     * vehicle must be grounded ({@link #canSupply()}) and the weapon must have a finite reserve that is not already
     * full; the config's ammo items ({@code Item = count, name} — most bundled weapons need iron ingots + gunpowder)
     * are consumed from the player's main inventory first, regardless of game mode, as in the reference
     * ({@code canPlayerSupplyAmmo}/{@code supplyAmmo} have no creative bypass). A water/lava-bucket round hands back an
     * empty bucket. The index is the weapon the GUI is SHOWING, so it is bounds-checked here — a modified client
     * cannot address a weapon that does not exist. Returns true if a supply pulse was granted.
     */
    public boolean supplyAmmoFromPlayer(Player player, int weaponIndex) {
        if (this.level().isClientSide || this.weapons == null || !canSupply()) {
            return false;
        }
        if (weaponIndex < 0 || weaponIndex >= this.weapons.size()) {
            return false;
        }
        WeaponSlot slot = this.weapons.get(weaponIndex);
        if (!slot.hasEconomy() || slot.totalAmmo() >= slot.maxAmmo()) {
            return false;
        }
        java.util.List<mcheli.agnostic.weapon.MCH_WeaponInfo.RoundItem> cost = slot.info.roundItems;
        if (cost != null && !cost.isEmpty()) {
            for (mcheli.agnostic.weapon.MCH_WeaponInfo.RoundItem ri : cost) {
                if (countItem(player, ri) < ri.num) {
                    return false; // cannot afford this weapon's round item (no creative bypass — reference has none)
                }
            }
            for (mcheli.agnostic.weapon.MCH_WeaponInfo.RoundItem ri : cost) {
                takeItem(player, ri, ri.num);
            }
        }
        slot.supplyRestAllAmmo();
        if (slot.magazine() <= 0) {
            slot.reloadMag();
        }
        return true;
    }

    // The reference scans mainInventory only (indices 0..35): hotbar + main grid, never armor (36..39) or offhand (40).
    private static final int MAIN_INVENTORY_SIZE = 36;

    private static int countItem(Player player, mcheli.agnostic.weapon.MCH_WeaponInfo.RoundItem ri) {
        int n = 0;
        int size = Math.min(MAIN_INVENTORY_SIZE, player.getInventory().getContainerSize());
        for (int i = 0; i < size; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (matches(s, ri)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static void takeItem(Player player, mcheli.agnostic.weapon.MCH_WeaponInfo.RoundItem ri, int num) {
        int size = Math.min(MAIN_INVENTORY_SIZE, player.getInventory().getContainerSize());
        for (int i = 0; i < size && num > 0; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!matches(s, ri)) {
                continue;
            }
            // A water/lava-bucket round hands back an EMPTY bucket rather than destroying the stack (reference
            // MCH_EntityAircraft:2400-2402); those items are max-stack 1, so one slot yields one round.
            if (s.is(net.minecraft.world.item.Items.WATER_BUCKET) || s.is(net.minecraft.world.item.Items.LAVA_BUCKET)) {
                player.getInventory().setItem(i, new ItemStack(net.minecraft.world.item.Items.BUCKET));
                num--;
            } else {
                int take = Math.min(num, s.getCount());
                s.shrink(take);
                num -= take;
            }
        }
    }

    private static boolean matches(ItemStack s, mcheli.agnostic.weapon.MCH_WeaponInfo.RoundItem ri) {
        if (s.isEmpty() || ri.itemName == null) {
            return false;
        }
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath()
            .equalsIgnoreCase(ri.itemName);
    }

    /**
     * Track the pilot's resupply GUI — the port of {@code updateSupplyAmmo}:2345-2370. While the PILOT has the vehicle
     * menu open the vehicle is "reloading" (the HUD blanks its weapon readout), and the flag persists for a 20-tick
     * tail after the GUI closes so the readout does not flicker back mid-reload.
     */
    private void updateReloadingState() {
        boolean open = pilot() instanceof Player p && p.isAlive()
            && p.containerMenu instanceof mcheli.dependent.menu.MchVehicleMenu m && m.vehicle() == this;
        if (open) {
            this.supplyAmmoWait = 20;
        } else if (this.supplyAmmoWait > 0) {
            this.supplyAmmoWait--;
        }
        this.entityData.set(DATA_RELOADING, open || this.supplyAmmoWait > 0);
    }

    /** True while the pilot is in the resupply GUI (or within its 20-tick tail) — reference {@code isPilotReloading}. */
    public boolean isPilotReloading() { return this.entityData.get(DATA_RELOADING); }

    /** The selected weapon's RESERVE (reference {@code getRestAllAmmoNum}) = its total minus the magazine in the gun.
     *  -1 when the weapon has no finite reserve. CLIENT-readable (both halves are synced). */
    public int getSelectedRestAmmo() {
        int total = ammoOf(getSelectedWeaponIndex());
        return total < 0 ? -1 : Math.max(0, total - Math.max(0, getSelectedAmmo()));
    }

    /** Publish every weapon's TOTAL ammo (magazine + reserve) for the riding GUI. SynchedEntityData compares before it
     *  marks dirty, so an unchanged map costs no packets. */
    private void publishAllAmmo() {
        CompoundTag t = new CompoundTag();
        int spent = 0;
        for (int i = 0; i < this.weapons.size(); i++) {
            WeaponSlot w = this.weapons.get(i);
            if (w.hasEconomy()) {
                t.putInt(Integer.toString(i), w.totalAmmo());
            }
            if (i < 32 && w.reloading()) {
                spent |= 1 << i; // this slot just fired and is reloading -> its missile part hides
            }
        }
        this.entityData.set(DATA_AMMO_ALL, t);
        this.entityData.set(DATA_WEAPON_SPENT, spent);
    }

    /** Total ammo (magazine + reserve) of weapon {@code i} — CLIENT-readable, for the riding GUI. -1 == infinite. */
    public int ammoOf(int i) {
        CompoundTag t = this.entityData.get(DATA_AMMO_ALL);
        String k = Integer.toString(i);
        return t.contains(k) ? t.getInt(k) : -1;
    }

    /** The number of weapons this vehicle has (CLIENT-readable, from the config). */
    public int weaponCount() {
        return hudWeaponList() != null ? hudWeaponList().size() : 0;
    }

    /** Weapon {@code i}'s slot, resolved from the config — CLIENT-safe (ammo lives in {@link #ammoOf}). */
    public WeaponSlot weaponAt(int i) {
        VehicleWeapons list = hudWeaponList();
        return list != null && i >= 0 && i < list.size() ? list.get(i) : null;
    }

    private VehicleWeapons hudWeaponList() {
        if (weaponHostInfo() == null) {
            return null;
        }
        if (this.hudWeapons == null) {
            this.hudWeapons = VehicleWeapons.build(weaponHostInfo(), MCH_WeaponInfoManager::get);
        }
        return this.hudWeapons;
    }

    /**
     * Spill the fuel slots when the vehicle leaves the world for good — the port of {@code MCH_AircraftInventory.setDead}
     * (:63-75), which drops the inventory contents on death. Gated on the removal REASON so a chunk unload or a
     * dimension change (which also remove the entity) can never duplicate the cans.
     */
    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide
            && (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED)) {
            net.minecraft.world.Containers.dropContents(this.level(), this, this.fuelInventory);
        }
        super.remove(reason);
    }

    /** Pull every weapon's reserve into its magazine — the reference reloads all weapons on the GUI-CLOSE edge, and
     *  only while the vehicle is intact ({@code updateSupplyAmmo}:2355 gates on {@code !isDestroyed()}). */
    public void reloadAllWeapons() {
        if (this.weapons != null && !isDestroyed()) {
            this.weapons.reloadAll();
        }
    }

    public int getFuel() { return this.entityData.get(DATA_FUEL); }

    public void setFuel(int f) {
        if (!this.level().isClientSide) {
            this.entityData.set(DATA_FUEL, Math.max(0, Math.min(f, getMaxFuel())));
        }
    }

    public int getMaxFuel() {
        MCH_AircraftInfo info = weaponHostInfo();
        return info != null ? info.maxFuel : 0;
    }

    /** Fuel fraction 0..1 for the HUD gauge — faithfully 0 when the config declares no tank (reference
     *  {@code getFuelP}:2247-2250; the HUD's low-fuel warning gates on {@code maxFuel>0} so nothing false-alarms). */
    public float getFuelP() {
        int max = getMaxFuel();
        return max <= 0 ? 0.0F : getFuel() / (float) max;
    }

    /** A creative occupant (or a fuel-less config) makes fuel free — reference {@code isInfinityFuel}:402-416.
     *  {@code anySeat}: scan EVERY rider (the heli's hover gate uses this variant), else only the pilot. */
    public boolean isInfinityFuel(boolean anySeat) {
        if (anySeat) {
            for (Entity p : getPassengers()) {
                if (p instanceof Player pl && pl.getAbilities().instabuild) {
                    return true;
                }
            }
            return false;
        }
        Entity p = pilot();
        return p instanceof Player pl && pl.getAbilities().instabuild;
    }

    /** Can the engine still run? Reference {@code canUseFuel}:2252-2258 — note the floor is fuel {@code > 1}, not 0. */
    public boolean canUseFuel(boolean anySeat) {
        return getMaxFuel() <= 0 || getFuel() > 1 || isInfinityFuel(anySeat);
    }

    /** The flight-sim throttle actually driving the engine — NOT the display {@code enginePower} (which idles at 0.5
     *  while merely ridden and would burn fuel at rest). Overridden by the categories that own a sim state. */
    protected double simThrottle() {
        return 0.0;
    }

    /** Receivers must be on solid ground to take on fuel/ammo (reference {@code canSupply}:2223-2225) — a floating
     *  vehicle is exempt from the water rule. */
    public boolean canSupply() {
        MCH_AircraftInfo info = weaponHostInfo();
        boolean floats = info != null && info.isFloat;
        if (!floats && this.isInWater()) {
            return false;
        }
        // Reference MCH_Lib.getBlockIdY(this, 1, -3): start at (int)(posY + 0.5) and scan 3 blocks down for a
        // COLLIDABLE block (a plant/torch does not count as ground, and the probe is not shifted a block low).
        BlockPos from = BlockPos.containing(getX(), getY() + 0.5, getZ());
        for (int i = 0; i < 3; i++) {
            BlockPos at = from.below(i);
            if (!this.level().getBlockState(at).getCollisionShape(this.level(), at).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Burn fuel with the throttle (reference {@code updateFuel}:2289-2308): once a second, while actually under power
     *  and not currently being resupplied; the {@code >1} floor mirrors the reference exactly. */
    private void updateFuel() {
        if (getMaxFuel() == 0) {
            return;
        }
        if (this.fuelSuppliedCount > 0) {
            this.fuelSuppliedCount--;
        }
        if (isDestroyed()) {
            return;
        }
        MCH_AircraftInfo info = weaponHostInfo();
        double throttle = simThrottle();
        if (this.tickCount % 20 == 0 && getFuel() > 1 && throttle > 0.0 && this.fuelSuppliedCount <= 0 && info != null) {
            this.fuelConsumptionAcc += Math.min(throttle * 1.4, 1.0) * info.fuelConsumption;
            if (this.fuelConsumptionAcc > 1.0) {
                int burn = (int) this.fuelConsumptionAcc;
                this.fuelConsumptionAcc -= burn;
                setFuel(getFuel() - burn);
            }
        }
    }

    /** Fuel/ammo supply auras — a config with {@code FuelSupplyRange}/{@code AmmoSupplyRange} (the fuel truck, the
     *  ammo box) tops up nearby vehicles. The supplier is an infinite source (the reference never decrements it), and
     *  the {@code !onGround} term is SUPPLIER-side: an airborne tanker refuels flying receivers, a grounded one
     *  requires the receiver to be landed. Reference :2264-2287 / :2418-2451. */
    private void updateSupply() {
        MCH_AircraftInfo info = weaponHostInfo();
        if (info == null || this.level().isClientSide) {
            return; // NOTE: no isDestroyed gate — the reference keeps a wrecked truck/box supplying (only BURN stops)
        }
        if (info.fuelSupplyRange > 0.0F && this.tickCount % 10 == 0) {
            for (AbstractMchVehicle o : nearby(info.fuelSupplyRange)) {
                if ((!this.onGround() || o.canSupply()) && o.getFuel() < o.getMaxFuel()) {
                    o.setFuel(o.getFuel() + Math.min(30, o.getMaxFuel() - o.getFuel()));
                }
                o.fuelSuppliedCount = 40; // unconditional: even a full tank pauses its burn while in the aura
            }
        }
        if (info.ammoSupplyRange > 0.0F && this.tickCount % 40 == 0) {
            for (AbstractMchVehicle o : nearby(info.ammoSupplyRange)) {
                if (!o.canSupply() || o.weapons == null) {
                    continue;
                }
                for (WeaponSlot s : o.weapons.economySlots()) {
                    if (s.totalAmmo() < s.maxAmmo()) {
                        s.setRestAllAmmo(s.totalAmmo() + Math.max(1, s.maxAmmo() / 10));
                        if (s.magazine() <= 0) {
                            s.reloadMag();
                        }
                    }
                }
            }
        }
    }

    private java.util.List<AbstractMchVehicle> nearby(float range) {
        java.util.List<AbstractMchVehicle> out = this.level().getEntitiesOfClass(
            AbstractMchVehicle.class, getBoundingBox().inflate(range));
        out.remove(this);
        return out;
    }

    /** Server tick for the weapon system: build the loadout once, advance fire-control counters, apply a queued
     *  switch, and fire the selected weapon while the trigger is held. */
    private void tickWeapons() {
        if (this.weapons == null) {
            this.weapons = VehicleWeapons.build(weaponHostInfo(), MCH_WeaponInfoManager::get, seatCount());
            this.weapons.initSelection(seat -> seatPassenger(seat) instanceof Player);
            this.entityData.set(DATA_WEAPON, this.weapons.selectedIndex());
            // Restore the saved reserves now the slots exist (reference :795-801: set the total, then fill the mag).
            if (this.savedAmmo != null) {
                for (int i = 0; i < Math.min(this.savedAmmo.length, this.weapons.size()); i++) {
                    if (this.savedAmmo[i] >= 0) {
                        this.weapons.get(i).setRestAllAmmo(this.savedAmmo[i]);
                        this.weapons.get(i).reloadMag();
                    }
                }
                this.savedAmmo = null;
            }
        }
        this.weapons.tick();
        if (this.firedCooldownTicks > 0) {
            this.firedCooldownTicks--;
        }

        java.util.function.IntPredicate manned = s -> seatPassenger(s) instanceof Player;

        // Per-seat weapon switching: each seat cycles only through the weapons IT may use.
        for (java.util.Map.Entry<Integer, Integer> e : new java.util.HashMap<>(this.switchBySeat).entrySet()) {
            int seat = e.getKey();
            int pending = e.getValue();
            this.switchBySeat.remove(seat);
            if (pending == 0 || this.weapons.isEmpty() || !(seatPassenger(seat) instanceof Player)) {
                continue;
            }
            int dir = pending > 0 ? 1 : -1;
            for (int i = 0; i < Math.abs(pending); i++) {
                this.weapons.cycle(seat, dir, manned);
            }
            if (seat == 0) {
                this.entityData.set(DATA_WEAPON, this.weapons.selectedIndex(0));
            }
            notifySeatWeapon(seat);
        }

        // Every seat fires ITS OWN weapon while ITS trigger is held.
        for (Entity p : getPassengers()) {
            int seat = seatIndexOf(p);
            // No weapon may fire while the pilot is in the resupply GUI (or its 20-tick tail) — the reference gates
            // weapon use and weapon switching on isPilotReloading (MCH_AircraftClientTickHandler:212). Enforced on the
            // SERVER, which owns the ammo, so a client that keeps sending FIRE cannot shoot through a reload.
            if (seat >= 0 && controlState(seat).fire && !isPilotReloading()) {
                fireSelectedWeapon(seat);
            }
        }

        // Publish the PILOT's selected weapon for the HUD ammo counter (per-seat HUD lands with the gunner GUI).
        WeaponSlot sel = this.weapons.selected(0);
        this.entityData.set(DATA_AMMO, sel != null ? sel.magazine() : -1);
        this.entityData.set(DATA_RELOAD, sel != null ? sel.reloadFraction() : 0.0F);
        this.entityData.set(DATA_HEAT, sel != null ? sel.heatFraction() : 0.0F);
        // Publish the "recently fired" bit (the reference useWeaponStat) so the gatling barrel spins on every client.
        this.entityData.set(DATA_FIRING, this.firedCooldownTicks > 0);
        publishAllAmmo();
        updateReloadingState();
    }

    /**
     * The body yaw the fire rotates the muzzle position and projectile direction by. Aircraft/tanks use the hull
     * ({@code getYRot()}). {@link MchGroundVehicle} (the reference {@code MCH_EntityVehicle}) overrides this to the
     * operator's LOOK for a no-traverse weapon, so a fixed emplacement launcher fires where the gunner aims — the port
     * of {@code MCH_EntityVehicle.useCurrentWeapon}, which snaps the body to {@code prm.user.rotationYaw} for the shot.
     */
    protected float fireBodyYaw(Entity shooter, MCH_AircraftInfo.Weapon mount) { return this.getYRot(); }

    /** Elevation counterpart of {@link #fireBodyYaw} (reference snaps {@code rotationPitch = prm.user.rotationPitch}). */
    protected float fireBodyPitch(Entity shooter, MCH_AircraftInfo.Weapon mount) { return this.getXRot(); }

    /**
     * Fire ONE shot of the selected weapon this tick, if its fire-control allows. Picks the next mount round-robin,
     * places the muzzle and aims it in world space with the port's orientation convention
     * ({@code Ry(-yaw)·Rx(pitch)·Rz(roll)}, forward = model +Z — the same transform that seats the pilot and renders
     * the model, so muzzles line up with the guns), then spawns a {@link MchBullet} carrying the weapon's real stats
     * and plays the weapon's own report.
     */
    private void fireSelectedWeapon(int seat) {
        WeaponSlot slot = this.weapons.selected(seat);
        Entity shooter = seatPassenger(seat);
        if (slot == null || shooter == null || !slot.canFire()) {
            return;
        }
        // A seat may only fire a weapon it actually operates (its own, or — for the pilot — a canUsePilot weapon whose
        // gunner seat is empty). Eligibility already enforces this, but re-check: crew may have boarded mid-trigger.
        if (this.weapons.eligible(slot, seat, s -> seatPassenger(s) instanceof Player)) {
            // ok
        } else {
            return;
        }
        // Guided missiles (AA/AT) require a COMPLETED lock: the operator's client resolves the lock and ships the
        // target entity id (controlState.lockTargetId). Resolve + validate it here BEFORE consuming ammo — with no
        // valid live target the weapon does not fire (faithful to the reference shot() returning false pre-lock).
        String preType = slot.info.type == null ? "" : slot.info.type.toLowerCase();
        boolean guided = preType.equals("aamissile") || preType.equals("atmissile");
        Entity guidedTarget = null;
        byte guidedKind = MchBullet.GUIDED_NONE;
        if (guided) {
            int tid = controlState(seat).lockTargetId;
            Entity t = tid >= 0 ? this.level().getEntity(tid) : null;
            if (t == null || !t.isAlive() || t == this || this.getPassengers().contains(t)) {
                return; // no valid lock → hold fire (do not consume ammo)
            }
            guidedTarget = t;
            guidedKind = preType.equals("aamissile") ? MchBullet.GUIDED_AA : MchBullet.GUIDED_AT_DIRECT;
        }
        boolean creative = shooter instanceof Player pl && pl.getAbilities().instabuild;
        MCH_AircraftInfo.Weapon mount = slot.fireOneShot(creative);
        MCH_WeaponInfo wi = slot.info;
        if (firesRotBarrel(slot)) {
            this.firedCooldownTicks = 4; // reference MCH_WeaponSet.WAIT_CLEAR_USED_COUNT — barrel stays "used" ~4 ticks
        }

        // The BODY angles the muzzle position AND the fire direction rotate by. For an aircraft/tank this is the hull.
        // An EMPLACEMENT ({@link MchGroundVehicle}) OVERRIDES these to the operator's LOOK for a no-traverse weapon —
        // the port of {@code MCH_EntityVehicle.useCurrentWeapon}, which snaps {@code this.rotationYaw/Pitch} to
        // {@code prm.user.rotationYaw/Pitch} for the shot so a fixed forward launcher (searam, Phalanx) fires exactly
        // where the gunner looks. A weapon WITH a real yaw range keeps the hull here and traverses per-weapon below.
        float hullYaw = fireBodyYaw(shooter, mount);
        float hullPitch = fireBodyPitch(shooter, mount);
        float roll = this.getRollAngle();
        float yaw = hullYaw;
        float pitch = hullPitch;

        // Fire along the OPERATOR's look, clamped to the mount's config traverse/elevation limits — the SAME computation
        // the RENDERER uses to orient the weapon model ({@code MchModelEntityRenderer.weaponYaw/weaponPitch}), so the
        // projectile leaves EXACTLY where the barrel visually points. A no-traverse weapon has 0 limits, so the clamp
        // pins {@code relYaw} to whatever {@code hullYaw} already is — the hull for an aircraft (fires forward), or the
        // gunner's look for an emplacement (via the body-snap above). An aiming weapon (chin gun, tank turret) traverses.
        //   AIM SOURCE = the operator's BODY yaw ({@code getYRot()}), NOT the head yaw ({@code getYHeadRot()}). This is
        // the reference's field ({@code MCH_WeaponSet.use} reads {@code entity.rotationYaw}; {@code updateWeaponsRotation}
        // and {@code lastRiderYaw} likewise) AND the ONLY one that survives to the server: this fire runs server-side,
        // where a passenger's rotation arrives via {@code ServerboundMovePlayerPacket.Rot} → {@code absMoveTo}, which
        // sets {@code getYRot()}/{@code getXRot()} but NEVER {@code yHeadRot}.
        {
            // A ring-mounted gun fires along the RATE-LIMITED turret (the same slewed angle its muzzle orbits by below),
            // so the shot leaves the barrel instead of the operator's instant look; a fixed gun still tracks the look.
            float relYaw = mount.turret
                ? turretOrbitYaw(1.0F)
                : Mth.wrapDegrees(shooter.getYRot() - hullYaw);
            float relPitch = Mth.wrapDegrees(shooter.getXRot() - hullPitch);
            boolean finiteYaw = Math.abs((int) mount.minYaw) < 360 && Math.abs((int) mount.maxYaw) < 360;
            if (finiteYaw) {
                relYaw = Mth.clamp(Mth.wrapDegrees(relYaw - mount.defaultYaw), mount.minYaw, mount.maxYaw)
                    + mount.defaultYaw;
            }
            relPitch = Mth.clamp(relPitch, mount.minPitch, mount.maxPitch);
            yaw = hullYaw + relYaw;
            pitch = hullPitch + relPitch;
        }

        // Muzzle world position = vehiclePos + R(HULL) · mountLocalOffset. The mount offset is authored in MODEL space,
        // so it must be rotated by the hull (plus the turret ring for a ring-mounted gun) — NOT by the operator's look:
        // rotating it by the aim made the muzzle swing around (and outside) the vehicle whenever a gunner turned his head.
        org.joml.Quaternionf qHull = new org.joml.Quaternionf()
            .rotateY((float) Math.toRadians(-hullYaw))
            .rotateX((float) Math.toRadians(hullPitch))
            .rotateZ((float) Math.toRadians(roll));
        org.joml.Vector3f mpos = new org.joml.Vector3f((float) mount.pos.x(), (float) mount.pos.y(), (float) mount.pos.z());
        if (mount.turret) {
            // A ring-mounted gun orbits turretPosition with the turret before the hull transform (matches the renderer).
            MCH_AircraftInfo info = weaponHostInfo();
            org.joml.Vector3f tp = new org.joml.Vector3f(
                (float) info.turretPosition.x(), (float) info.turretPosition.y(), (float) info.turretPosition.z());
            mpos.sub(tp).rotateY((float) Math.toRadians(-turretOrbitYaw(1.0F))).add(tp);
        }
        qHull.transform(mpos);
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

        if (preType.equals("bomb")) {
            // A bomb is RELEASED at the aircraft's own velocity (reference MCH_WeaponBomb.shot overwrites the round's
            // motion with the carrier's), NOT fired forward at muzzle speed — config gravity (auto -0.03) carries it
            // down. Longer life than guns so a high-altitude release reaches the ground.
            MchBullet.spawnDropped(this.level(), muzzle, this.getDeltaMovement(), gravity, wi.power, 1200, this,
                model, color, wi);
        } else if (guidedKind != MchBullet.GUIDED_NONE) {
            MchBullet.spawnGuided(this.level(), muzzle, dir, speed, accFactor, gravity, wi.power, 600, this, model,
                color, wi, guidedTarget, guidedKind);
        } else {
            MchBullet.spawnWeapon(this.level(), muzzle, dir, speed, accFactor, gravity, wi.power, 600, this, model, color, wi);
        }

        // Report: the weapon's own sound at its configured volume, with the reference pitch jitter.
        SoundEvent report = MchSounds.byName(wi.soundFileName);
        if (report != null) {
            float pitchR = wi.soundPitch * (1.0F - wi.soundPitchRandom) + this.random.nextFloat() * wi.soundPitchRandom;
            this.level().playSound(null, muzzle.x, muzzle.y, muzzle.z, report, SoundSource.NEUTRAL,
                wi.soundVolume, pitchR);
        }

        spawnFireCosmetics(muzzle, dir, wi, yaw + mount.yaw, pitch + mount.pitch);

        // Recoil kick: on a weapon with config Recoil>0, bump the synced trigger so every client runs the visual kick
        // (reference sets recoilCount=30/value/yaw on the firing rising edge). The direction is the weapon's aim
        // relative to the hull, so a forward gun kicks pitch (nose up) and a side gun kicks roll.
        if (wi.recoil > 0.0F) {
            this.entityData.set(DATA_RECOIL_SEQ, this.entityData.get(DATA_RECOIL_SEQ) + 1);
            this.entityData.set(DATA_RECOIL_VALUE, wi.recoil);
            this.entityData.set(DATA_RECOIL_YAW, Mth.wrapDegrees(yaw - hullYaw));
        }
    }

    // ---- Client ambient particle effects (reference onUpdate_Client / onUpdate_Particle*) — CLIENT-ONLY ----

    /** Spawn this vehicle's ambient particles this client tick. Base: the damage smoke every vehicle shares; helicopters
     *  add the rotor down-wash. All spawns go through {@code level().addParticle}, a no-op on the server. */
    protected void clientAmbientEffects() {
        spawnDamageSmoke();
    }

    /**
     * Dark smoke from a wounded vehicle — the port of the shared {@code onUpdate_Particle2} (heli
     * {@code MCH_EntityHeli}:677, plane/tank equivalents): only below 50 % HP, with an emission probability that rises
     * as HP falls (reference {@code p < rand/2}, so it stays rare near 50 % and pours out near 0). Grey {@code SMOKE}
     * approximates the reference's {@code 0.2..0.5}-grey {@code smoke.png} billboard.
     */
    protected void spawnDamageSmoke() {
        int maxHp = getMaxHp();
        if (maxHp <= 0) {
            return;
        }
        float p = getHp() / (float) maxHp;
        if (p > 0.5F || !(p < this.random.nextFloat() / 2.0F)) {
            return; // above 50% HP, or the HP-scaled emission roll didn't fire this tick
        }
        int burst = 2; // the reference emits a trail per rotor; a small burst approximates that density
        for (int i = 0; i < burst; i++) {
            double x = getX() + (this.random.nextDouble() - 0.5) * getBbWidth();
            double y = getY() + getBbHeight() * 0.6 + (this.random.nextDouble() - 0.5) * 0.5;
            double z = getZ() + (this.random.nextDouble() - 0.5) * getBbWidth();
            // Dark damage smoke — the MCHeli smoke.png billboard (port of MCH_EntityParticleSmoke), which rises and
            // lightens as it dissipates, in place of the vanilla LARGE_SMOKE stand-in.
            this.level().addParticle(
                new mcheli.dependent.particle.MchSmokeOptions(0.9F, 0xC0343434, 24 + this.random.nextInt(16)),
                x, y, z,
                (this.random.nextDouble() - 0.5) * 0.3, this.random.nextDouble() * 0.1, (this.random.nextDouble() - 0.5) * 0.3);
        }
    }

    /** Play a registered MCHeli sound (by {@code .ogg} basename) at this vehicle, server-side so every nearby client
     *  hears it — the port of the reference's {@code MOD_playSoundAtEntity}. No-op if the name isn't registered. */
    public void playMchSound(String name, float volume, float pitch) {
        if (this.level().isClientSide) {
            return;
        }
        SoundEvent event = MchSounds.byName(name);
        if (event != null) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), event, SoundSource.NEUTRAL, volume, pitch);
        }
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
    private void notifySeatWeapon(int seat) {
        WeaponSlot slot = this.weapons.selected(seat);
        Entity rider = seatPassenger(seat);
        if (slot == null || !(rider instanceof Player p)) {
            return;
        }
        String label = slot.info.displayName != null && !slot.info.displayName.isEmpty()
            ? slot.info.displayName : slot.weaponName;
        p.displayClientMessage(Component.literal("Weapon: " + label), true);
    }

    // ==== HP / armor / destruction (faithful port of MCH_EntityAircraft.attackEntityFrom + destroyAircraft) ====

    /** Public read of this vehicle's parsed config (armor/HP/hitboxes), or null; for HUD + diagnostics/self-test. */
    public MCH_AircraftInfo hostInfo() { return weaponHostInfo(); }

    /** Whether this vehicle is an unmanned target drone (config {@code TargetDrone=true}) — it flies/drives itself with
     *  forced throttle + the ground-avoidance auto-steer instead of waiting for a pilot (reference {@code isTargetDrone}). */
    public boolean isTargetDrone() {
        MCH_AircraftInfo info = weaponHostInfo();
        return info != null && info.isTargetDrone;
    }

    /** The vehicle's human name — the config's {@code DisplayName}, falling back to the config name. */
    public String displayName() {
        MCH_AircraftInfo info = weaponHostInfo();
        return info != null && info.displayName != null && !info.displayName.isEmpty() ? info.displayName : configName();
    }

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

    /** Mend {@code tpd} HP of accumulated damage (reference {@code MCH_EntityAircraft.repair}) — the wrench's per-tick
     *  heal. Returns whether there was damage to mend, so the tool only spends durability + plays its sound when it
     *  actually repairs something. Server-authoritative; a no-op (but truthful) read on the client. */
    public boolean repair(int tpd) {
        if (getDamageTaken() <= 0) {
            return false;
        }
        if (!this.level().isClientSide) {
            setDamageTaken(getDamageTaken() - Math.max(1, tpd));
        }
        return true;
    }

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

        // Damage SFX (reference :861-882 ping / :959-960 thud): a PLAYER attacker -> the "hit" ping; anything else
        // (crash, collision, explosion, fall) -> the "helidmg" metal thud with a randomised pitch. The reference plays
        // these OUTSIDE its !isDestroyed()/break gates, so a burning wreck still thuds and a creative salvage still pings.
        boolean byPlayer = src.getEntity() instanceof Player;
        playMchSound(byPlayer ? "hit" : "helidmg", 1.0F, byPlayer ? 1.0F : 0.9F + this.random.nextFloat() * 0.1F);

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
