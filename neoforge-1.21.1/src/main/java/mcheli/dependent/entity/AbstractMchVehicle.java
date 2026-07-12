package mcheli.dependent.entity;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
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
    /** Index of the selected weapon in {@link #weapons} (synced for HUD/feedback); -1 == vehicle has no weapons. */
    private static final EntityDataAccessor<Integer> DATA_WEAPON =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.INT);
    /** Rounds left in the selected weapon's current magazine (synced for the HUD ammo counter); -1 == none/infinite. */
    private static final EntityDataAccessor<Integer> DATA_AMMO =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.INT);
    /** Reload/cooldown progress 0..1 of the selected weapon (synced for the HUD cooldown bar); 0 == ready to fire. */
    private static final EntityDataAccessor<Float> DATA_RELOAD =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.FLOAT);

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
        builder.define(DATA_WEAPON, -1);
        builder.define(DATA_AMMO, -1);
        builder.define(DATA_RELOAD, 0.0F);
    }

    /** The parsed config whose {@code weaponSetList} drives this vehicle's weapon loadout, or null if it has none.
     *  Each demo vehicle returns its loaded {@link MCH_AircraftInfo} (the same one it flies with). */
    protected MCH_AircraftInfo weaponHostInfo() { return null; }

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

    /** Run the per-type flight controller for one server tick with the rider's control input. */
    protected abstract void tickPhysics(ControlInput in);
}
