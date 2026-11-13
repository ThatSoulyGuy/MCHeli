package mcheli.dependent.entity;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.sim.ControlInput;
import mcheli.agnostic.sim.RotationSolver;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.util.MCH_LowPassFilterFloat;
import mcheli.agnostic.weapon.MCH_WeaponInfo;
import mcheli.agnostic.weapon.VehicleWeapons;
import mcheli.agnostic.weapon.WeaponSlot;
import mcheli.dependent.control.MchControlState;
import mcheli.dependent.control.MchControllable;
import mcheli.dependent.port.NeoEntityRef;
import mcheli.dependent.registry.MchSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Shared base for every MCHeli vehicle (the four category entities). Owns the agnostic {@link EntityRef} view, the
 * rider {@link MchControlState}, MCHeli's custom roll axis, client-side position/rotation interpolation, and — for
 * aircraft — the client-authoritative mouse rotation hooks. Subclasses supply the per-type flight model / info / sim-state via
 * {@link #tickPhysics(ControlInput)}, and (for mouse-steerable types) a {@link #controlMapping()} +
 * {@link #rotationInfo()}.
 *
 * <p><b>Authority split (matches the reference).</b> POSITION is server-authoritative (physics runs on the server;
 * clients interpolate). ROTATION is CLIENT-authoritative for the local rider: their client runs the ported
 * {@link RotationSolver} from the mouse and ships the result via {@code ServerboundRotationPayload}; the server stores
 * it (physics reads it) and syncs it to OTHER clients (yaw/pitch via the entity tracker, roll via {@link #DATA_ROLL}).
 * So the local rider's rotation is NOT lerped from the server round-trip (that would fight their own computation) —
 * only their position lerps.
 *
 * <p><b>Components.</b> The entity is a coordinator: it keeps the 22 synced-data accessors, every Entity override and
 * subclass hook, {@link #tick()} and the NBT methods as ordered call lists, config identity, the skin, the engine
 * spool, and the rotation authority and client interpolation above. Each area lives in one same-package component that
 * holds only a back-reference to this entity, is created in a field initializer, and writes only its own accessors:
 * <ul>
 *   <li>{@link MchLogistics} — fuel burn, the fuel-can slots and siphon, the supply auras ({@link #DATA_FUEL}).</li>
 *   <li>{@link MchCountermeasures} — flares, the missile decoy, the RWR tones ({@link #DATA_FLARE_STATE}).</li>
 *   <li>{@link MchVehicleAnimation} (public, {@link #animation()}) — the client running gear, gatling barrel, rotor,
 *       radar and recoil kick.</li>
 *   <li>{@link MchVehicleEffects} — the client weapon and damage smoke, the server fire cosmetics.</li>
 *   <li>{@link MchWeaponSync} — the synced weapon view and its readers ({@link #DATA_SEAT_WEAPONS},
 *       {@link #DATA_AMMO_ALL}, {@link #DATA_WEAPON_SPENT}).</li>
 *   <li>{@link MchVehicleParts} (public, {@link #vehicleParts()}) — canopy, hatch, wing, bays, the landing gear and the
 *       part render read-models ({@link #DATA_PART_STATUS}, {@link #DATA_GEAR}).</li>
 *   <li>{@link MchVehicleVision} — camera modes, the night-vision viewers, the search light.</li>
 *   <li>{@link MchRiderPose} — the aim latch, seat attachment, the camera eyes, dismount, the model lift.</li>
 *   <li>{@link MchPartHitboxes} — the per-part hitboxes and the ray queries.</li>
 *   <li>{@link MchTerrainContact} — the light sweep, crush and ram, attitude-crash and submersion damage.</li>
 *   <li>{@link MchVehicleCrew} — the seat map, boarding, the passenger hooks, gunner mode ({@link #DATA_SEATS},
 *       {@link #DATA_GUNNER_MODE}, {@link #DATA_SEAT_GUNNER_MODE}).</li>
 *   <li>{@link MchFireControl} (with its {@link MchLockAudits}) — the loadout, the weapon tick and the fire
 *       ({@link #DATA_FIRING}, {@link #DATA_RELOADING}, {@link #DATA_RECOIL_SEQ}, {@link #DATA_RECOIL_VALUE},
 *       {@link #DATA_RECOIL_YAW}).</li>
 *   <li>{@link MchVehicleHull} — HP, the hurt pipeline, salvage, the wreck, repair ({@link #DATA_DAMAGE_TAKEN},
 *       {@link #DATA_DESTROYED}).</li>
 * </ul>
 * The entity itself writes {@link #DATA_ROLL}, {@link #DATA_SIM_THROTTLE}, {@link #DATA_THROTTLE}, {@link #DATA_SKIN}
 * and {@link #DATA_CONFIG}. The public and protected surface stays here as one-line delegates, so callers and the
 * four subclasses never see the split; a component calls a hook only through the entity ({@code v.hook()}) and names
 * its own body for it {@code base*} or {@code handle*}, so a subclass override is never skipped. The pure rules are
 * agnostic classes ({@code mcheli.agnostic.vehicle}, {@code sim}, {@code aircraft}, {@code weapon}).
 */
public abstract class AbstractMchVehicle extends Entity implements MchControllable, RollHolder {

    // ---- Synced data: the 22 accessors, ids 8..29 in this textual order (MchHelicopter and MchPlane count on from 30).
    //      Kept together and only here: an accessor for this class defined anywhere else would run in that class's
    //      lazy static init and shift or duplicate ids. Package-private so same-package components can read them. ----
    /** Synced roll (server -> clients) so non-piloting clients see banking; not a vanilla axis. */
    static final EntityDataAccessor<Float> DATA_ROLL =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.FLOAT);
    /** The real flight throttle (0 at rest, 1 spooled up) — the port of the reference {@code getCurrentThrottle()},
     *  synced for the CLIENT sound loop + rotor down-wash. Distinct from {@link #DATA_THROTTLE} (the rotor-spin signal
     *  that idles at 0.5 while merely ridden). */
    static final EntityDataAccessor<Float> DATA_SIM_THROTTLE =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.FLOAT);
    /** Spooled engine power 0..1 (server-authored, synced) so clients can drive rotor RPM / sounds. */
    static final EntityDataAccessor<Float> DATA_THROTTLE =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.FLOAT);
    /** Selected paint scheme index (synced) — 0 = default skin, then each {@code AddTexture}. */
    static final EntityDataAccessor<Integer> DATA_SKIN =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.INT);
    /** Landing-gear STATUS (true = up), server-authoritative and synced — the reference gear part's status bit. Both
     *  sides run the 2.5 deg/tick travel in {@link MchVehicleParts}' gear, so only the status crosses the wire. */
    static final EntityDataAccessor<Boolean> DATA_GEAR =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.BOOLEAN);
    /** Per-SEAT weapon SELECTION, which every client may need (the reference sends {@code currentWeaponID[seat]} to all
     *  around, {@code MCH_PacketNotifyWeaponID}; the pilot's selection drives the smoke trails everyone sees): key
     *  {@code "s"} -> each seat's selected weapon index (-1 none), key {@code "m"} -> every weapon's current MODE, by
     *  weapon index (only when one is non-zero). It changes only on a switch, a mode change or a crew change. The
     *  numbers that move every tick (magazine, reload, heat, preparation) go to the seat's own rider alone
     *  ({@link mcheli.dependent.control.ClientboundSeatWeaponPayload}, reference {@code MCH_PacketNotifyAmmoNum}). */
    static final EntityDataAccessor<CompoundTag> DATA_SEAT_WEAPONS =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.COMPOUND_TAG);
    /** Flare dispenser state (synced for the HUD {@code can_flare} cue): 0 = idle/ready, 1 = a dispense window runs. */
    static final EntityDataAccessor<Byte> DATA_FLARE_STATE =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.BYTE);
    /** Recoil trigger: bumped once per shot of a weapon with config {@code Recoil > 0}. A client watches it for a rising
     *  edge and runs its own 30-tick kick from {@link #DATA_RECOIL_VALUE}/{@link #DATA_RECOIL_YAW} (reference recoilCount). */
    static final EntityDataAccessor<Integer> DATA_RECOIL_SEQ =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.INT);
    /** The firing weapon's config {@code Recoil} magnitude (degrees) for the kick. */
    static final EntityDataAccessor<Float> DATA_RECOIL_VALUE =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.FLOAT);
    /** The firing weapon's aim yaw RELATIVE TO THE HULL — splits the kick into pitch (forward gun) vs roll (side gun). */
    static final EntityDataAccessor<Float> DATA_RECOIL_YAW =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.FLOAT);
    /** True while a weapon RECENTLY fired (within {@code firedCooldownTicks}) — synced so the gatling barrel spins on
     *  every client (the port of the reference {@code useWeaponStat}/{@code MCH_WeaponSet.isUsed} bit). */
    static final EntityDataAccessor<Boolean> DATA_FIRING =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.BOOLEAN);
    /** Fuel in the tank (synced for the HUD gauge). A vehicle with {@code MaxFuel = 0} never burns fuel. */
    static final EntityDataAccessor<Integer> DATA_FUEL =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.INT);
    /** True while the PILOT has toggled gunner mode (reference {@code isGunnerMode}) — server-authoritative and synced:
     *  the flight sim reads it (a heli hovers, a plane/tank levels off and stops steering), and clients read it for the
     *  gunner camera + scope zoom + the seat-pitch view clamp. Only ever set for a pilot; a dedicated gunner SEAT is
     *  "always in gunner mode" structurally, a switchable one follows {@link #DATA_SEAT_GUNNER_MODE} (see
     *  {@link #isSeatGunnerMode}). */
    static final EntityDataAccessor<Boolean> DATA_GUNNER_MODE =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.BOOLEAN);
    /** Per-seat gunner mode of the SWITCHABLE gunner seats (an {@code AddGunnerSeat} whose seventh value is true): bit
     *  {@code s} set while seat {@code s}'s occupant is in gunner mode — the reference {@code isGunnerModeOtherSeat},
     *  which boarding turns on ({@code onMountPlayerSeat}:3439-3441) and the gunner-mode key flips
     *  ({@code switchGunnerModeOtherSeat}:5316). Server-authoritative and synced, so every client agrees. Not saved:
     *  re-boarding (a reload included) turns it on again, as in the reference. */
    static final EntityDataAccessor<Integer> DATA_SEAT_GUNNER_MODE =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.INT);
    /** True while the PILOT has the resupply GUI open (plus the reference's 20-tick tail) — the port of
     *  {@code isPilotReloading}. The HUD blanks the weapon name/ammo while it is set, and the reference kills pilot
     *  control input for the same window. */
    static final EntityDataAccessor<Boolean> DATA_RELOADING =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.BOOLEAN);
    /** weaponIndex → TOTAL ammo (magazine + reserve) for every weapon, so the riding GUI can show the reserve of a
     *  weapon that is not the one selected. Each seat's SELECTED magazine reaches its rider separately
     *  ({@link #selectedAmmo}). */
    static final EntityDataAccessor<CompoundTag> DATA_AMMO_ALL =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.COMPOUND_TAG);
    /** Per-weapon-slot "spent" bitmask (synced): bit i set while slot i is mid-reload, so a just-fired MISSILE part
     *  vanishes from its rack until it reloads — the reference {@code useWeaponStat} + {@code renderWeapon} isMissile
     *  gate. Up to 32 slots. */
    static final EntityDataAccessor<Integer> DATA_WEAPON_SPENT =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.INT);
    /** seatIndex → passenger ENTITY ID. The client mirror of the server's seat map — the same information the reference
     *  shipped in {@code MCH_PacketSeatListResponse}, but auto-resynced. {@code getPassengers()} order is mount order
     *  and compacts on dismount, so it can NEVER be used as a seat map. */
    static final EntityDataAccessor<CompoundTag> DATA_SEATS =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.COMPOUND_TAG);
    /** Accumulated damage (reference {@code damageTaken}); HP = maxHp - this. The single health value that must sync
     *  (the HUD renders client-side and derives hp/hp_rto/HP_PER from it); maxHp is config the client already holds. */
    static final EntityDataAccessor<Integer> DATA_DAMAGE_TAKEN =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.INT);
    /** Destroyed/wreck flag (reference {@code isDestroyed()==despawnCount>0}); synced so the client renderer darkens
     *  and tumbles the wreck and gates interaction. Set true once, never cleared (a wreck stays a wreck until discard). */
    static final EntityDataAccessor<Boolean> DATA_DESTROYED =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.BOOLEAN);
    /** The config name that identifies WHICH vehicle this is (e.g. {@code ah-64}) — synced so the client resolves the
     *  same {@link MCH_AircraftInfo} (model, texture, HP, weapons, hitboxes, seats). One entity CLASS per category
     *  serves every vehicle in that category; this string selects the specific one (reference DataWatcher slot 20). */
    static final EntityDataAccessor<String> DATA_CONFIG =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.STRING);
    /** Bit-packed part status (server-authored, synced). Bits 0/3/4 keep the reference DataWatcher 31 layout: canopy
     *  open, wing folded, hatch open (bits 1 nozzle and 2 landing gear are the VTOL and gear states, synced elsewhere).
     *  Bit 6 is the search light (reference common-status bit 6); bits 8-9 and 10-11 hold the camera mode of seats 0 and
     *  1 (reference {@code MCH_Camera.mode}); bits 16-31 mark the weapon bays whose weapon is selected (reference
     *  {@code updateWeaponBay}'s open target). */
    static final EntityDataAccessor<Integer> DATA_PART_STATUS =
        SynchedEntityData.defineId(AbstractMchVehicle.class, EntityDataSerializers.INT);

    private float enginePower; // server-side spool accumulator

    /** Cached resolved config, invalidated whenever {@link #DATA_CONFIG} changes (server set or client sync). */
    private MCH_AircraftInfo resolvedInfo;
    /** The config name {@link #resolvedInfo} was resolved FOR. Compared instead of {@code resolvedInfo.name}, because a
     *  name that does not resolve maps to a category fallback whose own name is "" and would never match. */
    private String resolvedName;

    // ---- Components: one per area, each holding only a back-reference to this entity ----
    private final MchLogistics logistics = new MchLogistics(this);
    private final MchCountermeasures countermeasures = new MchCountermeasures(this);
    private final MchVehicleAnimation animation = new MchVehicleAnimation(this);
    private final MchVehicleEffects effects = new MchVehicleEffects(this);
    private final MchWeaponSync weaponSync = new MchWeaponSync(this);
    private final MchVehicleParts vehicleParts = new MchVehicleParts(this);
    private final MchVehicleVision vision = new MchVehicleVision(this);
    private final MchRiderPose riderPose = new MchRiderPose(this);
    private final MchPartHitboxes hitboxes = new MchPartHitboxes(this);
    private final MchTerrainContact terrain = new MchTerrainContact(this);
    private final MchVehicleCrew crew = new MchVehicleCrew(this);
    private final MchFireControl fireControl = new MchFireControl(this);
    private final MchVehicleHull hull = new MchVehicleHull(this);

    /** The fuel and supply economy: the burn, the fuel-can slots and their siphon, the supply auras. */
    MchLogistics logistics() {
        return this.logistics;
    }

    /** The flare dispenser, the missile decoy and the RWR tones. */
    MchCountermeasures countermeasures() {
        return this.countermeasures;
    }

    /** The client-only cosmetic animation (running gear, gatling barrel, rotor, radar, recoil kick), for the renderers,
     *  the HUD and the camera. */
    public MchVehicleAnimation animation() {
        return this.animation;
    }

    /** The particle cosmetics: the client weapon and damage smoke, and the server fire cosmetics. */
    MchVehicleEffects effects() {
        return this.effects;
    }

    /** The synced weapon view: the server's selection, mode, totals, spent-rail and rider-notice publishing, and the
     *  readers for the HUD and diagnostics. */
    MchWeaponSync weaponSync() {
        return this.weaponSync;
    }

    /** The switchable and animated parts and the landing gear, with their render read-models for the client. */
    public MchVehicleParts vehicleParts() {
        return this.vehicleParts;
    }

    /** The camera modes, the night-vision viewers and the search light. */
    MchVehicleVision vision() {
        return this.vision;
    }

    /** Where the crew sit and look: the aim latch and turret slew, the seat attachment, the camera eyes, dismount, the
     *  seat-pick offsets, the model lift, the HideGM groups and the view camera. */
    MchRiderPose riderPose() {
        return this.riderPose;
    }

    /** The per-part hitboxes and the projectile ray queries. */
    MchPartHitboxes hitboxes() {
        return this.hitboxes;
    }

    /** The light sweep, the tank crush and ram, and the attitude-crash and submersion damage. */
    MchTerrainContact terrain() {
        return this.terrain;
    }

    /** The crew: the seat map, the per-seat controls, boarding and the passenger hooks, seat switching, gunner mode and
     *  the Seats NBT. */
    MchVehicleCrew crew() {
        return this.crew;
    }

    /** The server fire control: the loadout and its saved ammo, the switch queue, the weapon tick and the fire, the
     *  lock audit, resupply and the HUD mortar distance. */
    MchFireControl fireControl() {
        return this.fireControl;
    }

    /** The hull: HP and the hurt pipeline, salvage and the spawn-item data, the wreck, repair and the HP NBT. */
    MchVehicleHull hull() {
        return this.hull;
    }

    // ---- Gun firing latch (the server latch lives in MchFireControl, the gatling barrel spin in MchVehicleAnimation) ----
    /** True (synced) while a weapon fired within the last few ticks — drives the gatling barrel spin on every client. */
    public boolean isFiringRecently() { return this.entityData.get(DATA_FIRING); }

    // ---- Switchable + animated common parts (reference MCH_Parts on DataWatcher 31, updateWeaponBay, updatePartLightHatch,
    //      onUpdate_PartRotation, updatePartCameraRotate, the throttle lever, the search light and MCH_Camera modes).
    //      The parts and the landing gear live in MchVehicleParts, the camera modes and the search light in
    //      MchVehicleVision; the one shared part/mode switch cooldown stays here. ----

    /** The reference {@code modeSwitchCooldown}: ONE 20-tick cooldown shared by the canopy, hatch, wing, landing-gear
     *  and (MchPlane) VTOL switches, and checked by pilot boarding. Ticked once per tick in
     *  {@link MchVehicleParts#tick}; only the server sets it, so a client pre-check reads it as ready and the server
     *  decides. */
    private final mcheli.agnostic.sim.ModeSwitchCooldown modeSwitchCooldown = new mcheli.agnostic.sim.ModeSwitchCooldown();

    // -- canopy (reference openCanopy / closeCanopy / isCanopyClose) --

    /** @see MchVehicleParts#haveCanopy */
    public boolean haveCanopy() { return this.vehicleParts.haveCanopy(); }

    /**
     * Reference {@code isCanopyClose}: no canopy, or switched closed AND at rest.
     * @see MchVehicleParts#isCanopyClose
     */
    public boolean isCanopyClose() { return this.vehicleParts.isCanopyClose(); }

    /**
     * SERVER: open the canopy (reference {@code openCanopy}: off cooldown, then a 20-tick cooldown).
     * @see MchVehicleParts#openCanopy
     */
    public void openCanopy() { this.vehicleParts.openCanopy(); }

    /**
     * SERVER: close the canopy if it is open (reference {@code closeCanopy}).
     * @see MchVehicleParts#closeCanopy
     */
    public void closeCanopy() { this.vehicleParts.closeCanopy(); }

    // -- hatch and wing fold (reference foldHatch / MCP_EntityPlane.foldWing, both on switchHatch / KeyZoom) --

    /** @see MchVehicleParts#haveHatch */
    public boolean haveHatch() { return this.vehicleParts.haveHatch(); }

    /**
     * Reference {@code canFoldHatch}: off cooldown and fully closed.
     * @see MchVehicleParts#canFoldHatch
     */
    public boolean canFoldHatch() { return this.vehicleParts.canFoldHatch(); }

    /**
     * Reference {@code canUnfoldHatch}: off cooldown and fully open.
     * @see MchVehicleParts#canUnfoldHatch
     */
    public boolean canUnfoldHatch() { return this.vehicleParts.canUnfoldHatch(); }

    /**
     * The hatch's current travel fraction (reference {@code partHatch.getFactor()}, the sweep-wing stand-in).
     * @see MchVehicleParts#hatchFactor()
     */
    public float hatchFactor() { return this.vehicleParts.hatchFactor(); }

    /** @see MchVehicleParts#haveWing */
    public boolean haveWing() { return this.vehicleParts.haveWing(); }

    /**
     * Reference {@code MCP_EntityPlane.canUseWing}: a folded wing grounds the plane (a variable-sweep wing only below
     * 0.2 throttle, where the sweep means it is parked folded).
     * @see MchVehicleParts#canUseWing
     */
    public boolean canUseWing() { return this.vehicleParts.canUseWing(); }

    /**
     * Reference {@code canFoldWing}.
     * @see MchVehicleParts#canFoldWing
     */
    public boolean canFoldWing() { return this.vehicleParts.canFoldWing(); }

    /**
     * Reference {@code canUnfoldWing}: off cooldown and fully folded.
     * @see MchVehicleParts#canUnfoldWing
     */
    public boolean canUnfoldWing() { return this.vehicleParts.canUnfoldWing(); }

    /**
     * True when a variable-sweep morph part exists (reference {@code getMaxSpeed}: the wing, else the hatch).
     * @see MchVehicleParts#hasSweepPart
     */
    public boolean hasSweepPart() { return this.vehicleParts.hasSweepPart(); }

    /**
     * The sweep part's travel fraction 0..1 (reference {@code partWing.getFactor()}, else {@code partHatch}'s).
     * @see MchVehicleParts#sweepPartFactor
     */
    public float sweepPartFactor() { return this.vehicleParts.sweepPartFactor(); }

    /**
     * SERVER: the pilot's hatch/wing switch — reference {@code switchHatch} from KeyZoom outside gunner mode
     * ({@code MCH_HeliPacketHandler:113}, {@code MCP_PlanePacketHandler:100-105}, {@code MCH_TankPacketHandler:99}):
     * the hatch if there is one, else a plane's wing; open when at rest closed, close when at rest open.
     * @see MchVehicleParts#switchHatch
     */
    public void switchHatch(Player player) { this.vehicleParts.switchHatch(player); }

    // -- search light (reference haveSearchLight / isSearchLightON / canSwitchSearchLight) --

    /** @see MchVehicleVision#haveSearchLight */
    public boolean haveSearchLight() { return this.vision.haveSearchLight(); }

    /** @see MchVehicleVision#isSearchLightOn */
    public boolean isSearchLightOn() { return this.vision.isSearchLightOn(); }

    /**
     * CLIENT: how brightly this vehicle's search lights light {@code e}, as a block-light lightmap coordinate 0..240 —
     * the port of {@code getSearchLightValue} (:1311-1351).
     * @see MchVehicleVision#searchLightValue
     */
    public float searchLightValue(Entity e) { return this.vision.searchLightValue(e); }

    // -- camera modes (reference MCH_Camera: 0 normal, 1 night vision, 2 thermal vision; seats 0 and 1 only) --

    /**
     * Reference {@code canSwitchCameraMode()}: the config enables night vision and the vehicle is intact.
     * @see MchVehicleVision#canSwitchCameraMode
     */
    public boolean canSwitchCameraMode() { return this.vision.canSwitchCameraMode(); }

    /**
     * The camera mode of {@code seat} (synced; 0 for a seat without one).
     * @see MchVehicleVision#cameraMode
     */
    public int cameraMode(int seat) { return this.vision.cameraMode(seat); }

    /**
     * SERVER: the camera-mode key (reference {@code MCH_AircraftClientTickHandler:96-114}).
     * @see MchVehicleVision#cameraModeKey
     */
    public void cameraModeKey(Player player) { this.vision.cameraModeKey(player); }

    /**
     * SERVER: {@code p} left the vehicle (dismount, death, the vehicle's removal or unload) or is logging out.
     * @see MchVehicleVision#releaseCameraViewer
     */
    public void releaseCameraViewer(Entity p) { this.vision.releaseCameraViewer(p); }

    // ---- Aim latch and turret slew (reference lastRiderYaw/lastRiderPitch, updateWeaponsRotation): in MchRiderPose ----

    /**
     * The TURRET yaw, relative to the hull — the ONE source shared by the rotSeat seat orbit, the weapon-ring in
     * {@code renderWeaponParts}, and the emplacement's VPart turret.
     * @see MchRiderPose#turretOrbitYaw
     */
    public float turretOrbitYaw(float partialTick) { return this.riderPose.turretOrbitYaw(partialTick); }

    /**
     * The pilot's look PITCH (absolute) — live while piloted, latched after dismount.
     * @see MchRiderPose#turretAimPitch
     */
    public float turretAimPitch(float partialTick) { return this.riderPose.turretAimPitch(partialTick); }

    /**
     * The live weapon loadout, null until the server builds it (the package bridge the components read it through).
     * @see MchFireControl#weaponLoadout
     */
    VehicleWeapons weaponLoadout() {
        return this.fireControl.weaponLoadout();
    }

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

    // CLIENT: the pilot's selected config CameraPosition index (reference MCH_EntityAircraft.cameraId) lives in
    // MchRiderPose.

    /**
     * CLIENT: how many config {@code CameraPosition} entries this vehicle has (reference {@code getCameraPosNum}).
     * @see MchRiderPose#cameraPosCount
     */
    public int cameraPosCount() { return this.riderPose.cameraPosCount(); }

    /**
     * CLIENT: at least two configured viewpoints, so the view key can cycle them (reference {@code canSwitchCameraPos}).
     * @see MchRiderPose#canSwitchCameraPos
     */
    public boolean canSwitchCameraPos() { return this.riderPose.canSwitchCameraPos(); }

    /**
     * CLIENT: the pilot's currently-selected {@code CameraPosition} index, clamped into range.
     * @see MchRiderPose#getViewCameraId
     */
    public int getViewCameraId() { return this.riderPose.getViewCameraId(); }

    /**
     * CLIENT: select a config {@code CameraPosition} index (out-of-range collapses to 0, the default eye).
     * @see MchRiderPose#setViewCameraId
     */
    public void setViewCameraId(int id) { this.riderPose.setViewCameraId(id); }

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

    /** @see MchVehicleCrew#controlState */
    public MchControlState controlState(int seat) { return this.crew.controlState(seat); }

    /**
     * Drop a seat's held keys when its occupant leaves (else the vehicle keeps firing/driving on the last packet).
     * @see MchVehicleCrew#clearSeatControls
     */
    public void clearSeatControls(int seat) { this.crew.clearSeatControls(seat); }
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
     * Seats the pilot faithfully — the 1.21.1 port of {@code MCH_EntityAircraft.updateRiderPosition} +
     * {@code getTransformedPosition(...,rotSeat)}. Vanilla's attachment answers when the config has no seat or the
     * passenger's seat is not resolved yet.
     * @see MchRiderPose#handlePassengerAttachmentPoint
     */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        Vec3 att = this.riderPose.handlePassengerAttachmentPoint(passenger, partialTick);
        return att != null ? att : super.getPassengerAttachmentPoint(passenger, dimensions, partialTick);
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

    /**
     * Stage-2 body rotation {@code Ry(-yaw)·Rx(pitch)·Rz(roll)}, interpolated — the single transform shared by the seat
     * attachment, the config camera eye, and dismount placement. The package bridge the search light
     * ({@link MchVehicleVision#searchLightValue}) reads it through.
     * @see MchRiderPose#bodyRotation
     */
    org.joml.Quaternionf bodyRotation(float partialTick) { return this.riderPose.bodyRotation(partialTick); }

    /**
     * True when the first-person view renders from the config {@code CameraPosition} instead of the rider's own eye —
     * the reference's render-view-dummy switch: heli/plane/tank use it when the config's {@code CameraPosition} 4th
     * param ({@code alwaysCameraView}) is true (forced true for UAVs); emplacements ALWAYS
     * ({@code MCH_ClientVehicleTickHandler:98} — see the {@link MchGroundVehicle} override). Gunner mode and an
     * alternate {@code cameraId} switch to the config camera too; {@link #firstPersonEye} handles those.
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
     * the vanilla camera already answers (a free-look vehicle without {@code alwaysCameraView}).
     * @see MchRiderPose#firstPersonEye
     */
    public Vec3 firstPersonEye(Entity rider, float partialTick) { return this.riderPose.firstPersonEye(rider, partialTick); }

    /**
     * Config-driven dismount — reference {@code setUnmountPosition:3787-3802}: the config {@code UnmountPosition}
     * body-transformed, else {@code (seat.x ± 3 outward, 2.0, seat.z)}; vanilla's placement when the config has no seat.
     * @see MchRiderPose#handleDismountLocationForPassenger
     */
    @Override
    public Vec3 getDismountLocationForPassenger(net.minecraft.world.entity.LivingEntity rider) {
        Vec3 at = this.riderPose.handleDismountLocationForPassenger(rider);
        return at != null ? at : super.getDismountLocationForPassenger(rider);
    }

    /**
     * Lowest model vertex Y (&le;0) for the current config — the value the renderer lifts the model by ({@code -minY}).
     * Public so the cockpit {@code CameraMixin} can apply the same lift to the first-person eye.
     * @see MchRiderPose#modelMinY
     */
    public float modelMinY() { return this.riderPose.modelMinY(); }

    /** The model resource sub-directory for this category ({@code helicopters}/{@code planes}/{@code tanks}/{@code
     *  vehicles}), mirroring the renderer's {@code categoryDir}, so {@link #modelMinY()} resolves the same model file. */
    protected abstract String modelDir();

    /**
     * True when the config hides the seated rider ({@code HideEntity=true}: the Phalanx dome / closed tank turret).
     * @see MchRiderPose#hidesRider
     */
    public boolean hidesRider() { return this.riderPose.hidesRider(); }

    /**
     * Lower-cased {@code $weaponN} model groups the config hides in the FIRST-PERSON gunner view ({@code HideGM} weapon
     * parts — e.g. the ec665 chin gun that would fill the cockpit).
     * @see MchRiderPose#firstPersonHiddenGroups
     */
    public java.util.Set<String> firstPersonHiddenGroups() { return this.riderPose.firstPersonHiddenGroups(); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ROLL, 0.0F);
        builder.define(DATA_THROTTLE, 0.0F);
        builder.define(DATA_SIM_THROTTLE, 0.0F);
        builder.define(DATA_SKIN, 0);
        builder.define(DATA_GEAR, false);
        builder.define(DATA_FLARE_STATE, (byte) 0);
        builder.define(DATA_GUNNER_MODE, false);
        builder.define(DATA_SEAT_GUNNER_MODE, 0);
        builder.define(DATA_SEAT_WEAPONS, new CompoundTag());
        builder.define(DATA_RECOIL_SEQ, 0);
        builder.define(DATA_RECOIL_VALUE, 0.0F);
        builder.define(DATA_RECOIL_YAW, 0.0F);
        builder.define(DATA_FIRING, false);
        builder.define(DATA_FUEL, 0);
        builder.define(DATA_SEATS, new CompoundTag());
        builder.define(DATA_AMMO_ALL, new CompoundTag());
        builder.define(DATA_WEAPON_SPENT, 0);
        builder.define(DATA_RELOADING, false);
        builder.define(DATA_PART_STATUS, 0);
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
        this.resolvedName = null;
        this.fireControl.onConfigChanged(); // the loadout rebuilds against the new config (the saved ammo survives)
        this.weaponSync.onConfigChanged(); // the client weapon list rebuilds against the new config
        this.hitboxes.onConfigChanged(); // the per-part hitbox copies rebuild against the new config
        this.riderPose.onConfigChanged(); // the model lift and HideGM groups re-resolve; the view camera falls back to 0
        this.countermeasures.onConfigChanged(); // a different config may declare different flare types
        onConfigChanged();
        refreshDimensions();          // the config Width/Height size the entity box (see getDimensions)
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
        if (this.resolvedInfo == null || !name.equals(this.resolvedName)) {
            this.resolvedInfo = resolveInfo(name);
            this.resolvedName = name;
        }
        return this.resolvedInfo;
    }

    /**
     * Index of {@code seat}'s selected weapon (synced, {@link #DATA_SEAT_WEAPONS}), or -1 when that seat has none.
     * @see MchWeaponSync#selectedWeaponIndex
     */
    public int selectedWeaponIndex(int seat) { return this.weaponSync.selectedWeaponIndex(seat); }

    /**
     * CLIENT: store {@code seat}'s weapon numbers from the server (the seat-weapon payload handler).
     * @see MchWeaponSync#acceptSeatWeaponHud
     */
    public void acceptSeatWeaponHud(int seat, int ammo, float reload, float heat, boolean inPreparation) {
        this.weaponSync.acceptSeatWeaponHud(seat, ammo, reload, heat, inPreparation);
    }

    /**
     * The current MODE of weapon {@code index} (synced; 0 when the weapon has only one).
     * @see MchWeaponSync#weaponMode
     */
    public int weaponMode(int index) { return this.weaponSync.weaponMode(index); }

    /**
     * {@code seat}'s selected weapon slot (from the synced index), for the HUD; null if none. The package bridge the
     * components read it through.
     * @see MchWeaponSync#selectedHudSlot
     */
    WeaponSlot selectedHudSlot(int seat) { return this.weaponSync.selectedHudSlot(seat); }

    /**
     * CLIENT: would a weapon switch of {@code seat} by {@code step} land on a DIFFERENT weapon?
     * @see MchWeaponSync#wouldSwitchWeapon
     */
    public boolean wouldSwitchWeapon(int seat, int step) { return this.weaponSync.wouldSwitchWeapon(seat, step); }

    /**
     * True if the MISSILE part for the weapon {@code weaponName} should be hidden this frame — its round is spent and
     * reloading (synced {@link #DATA_WEAPON_SPENT}), so the fired rail reads as empty (reference renderWeapon gate).
     * @see MchWeaponSync#isMissilePartHidden
     */
    public boolean isMissilePartHidden(String weaponName) { return this.weaponSync.isMissilePartHidden(weaponName); }

    /**
     * {@code seat}'s selected weapon name for the HUD with its mode suffix ({@code " [HE]"}, {@code " [TA]"}, ...;
     * reference {@code MCH_WeaponSet.getName}); "" if none.
     * @see MchWeaponSync#selectedWeaponName
     */
    public String selectedWeaponName(int seat) { return this.weaponSync.selectedWeaponName(seat); }

    /**
     * Rounds in {@code seat}'s selected magazine (sent to its rider); -1 == no weapon or an endless magazine.
     * @see MchWeaponSync#selectedAmmo
     */
    public int selectedAmmo(int seat) { return this.weaponSync.selectedAmmo(seat); }

    /**
     * True if {@code seat}'s selected weapon is a heat/IR-seeker (config {@code MaxHeatCount &gt; 0}) — the HUD's
     * {@code is_heat_wpn} lock-cue gate (reference {@code MCH_HudItem}: {@code is_heat_wpn = wi.maxHeatCount &gt; 0}).
     * @see MchWeaponSync#isSelectedWeaponHeat
     */
    public boolean isSelectedWeaponHeat(int seat) { return this.weaponSync.isSelectedWeaponHeat(seat); }

    /**
     * {@code seat}'s aiming-reticle type for the HUD {@code sight_type} overlay (reference
     * {@code MCH_HudItem.updateVarMap_Weapon}).
     * @see MchWeaponSync#selectedWeaponSightType
     */
    public int selectedWeaponSightType(int seat) { return this.weaponSync.selectedWeaponSightType(seat); }

    /**
     * CLIENT: {@code seat}'s selected weapon config (for the lock-on tracker), or null if no weapon is selected.
     * @see MchWeaponSync#selectedClientWeaponInfo
     */
    public MCH_WeaponInfo selectedClientWeaponInfo(int seat) {
        return this.weaponSync.selectedClientWeaponInfo(seat);
    }

    /**
     * {@code seat}'s selected weapon's {@code DisplayMortarDistance} flag — gates the HUD {@code mortar} readout.
     * @see MchWeaponSync#selectedWeaponDisplaysMortarDist
     */
    public boolean selectedWeaponDisplaysMortarDist(int seat) {
        return this.weaponSync.selectedWeaponDisplaysMortarDist(seat);
    }

    /**
     * {@code seat}'s reload/cooldown progress 0..1 (sent to its rider), for the HUD {@code reload_time} bar;
     * 0 == ready.
     * @see MchWeaponSync#selectedReload
     */
    public float selectedReload(int seat) { return this.weaponSync.selectedReload(seat); }

    /**
     * {@code seat}'s barrel heat 0..1 (sent to its rider), for the HUD {@code wpn_heat} overheat gauge;
     * 1 == overheated.
     * @see MchWeaponSync#selectedHeat
     */
    public float selectedHeat(int seat) { return this.weaponSync.selectedHeat(seat); }

    /**
     * Reference {@code isInPreparation} of {@code seat}'s weapon (sent to its rider): reloading or inside a switch
     * lockout — the HUD {@code reloading}.
     * @see MchWeaponSync#selectedInPreparation
     */
    public boolean selectedInPreparation(int seat) { return this.weaponSync.selectedInPreparation(seat); }

    /**
     * {@code seat}'s remaining cooldown in SECONDS — the received fraction × the config interval (delay/reload)
     * ÷ 20 tps — for the HUD's {@code RELOAD_SEC} readout.
     * @see MchWeaponSync#selectedReloadSeconds
     */
    public float selectedReloadSeconds(int seat) { return this.weaponSync.selectedReloadSeconds(seat); }

    /**
     * CLIENT: where {@code user}'s selected lobbing weapon would land, for the HUD {@code mt_dist} / {@code MORTAR_DIST}
     * readout (the port of {@code MCH_EntityAircraft.getLandInDistance}); -1 without a landing point.
     * @see MchFireControl#mortarLandInDistance
     */
    public double mortarLandInDistance(Entity user) { return this.fireControl.mortarLandInDistance(user); }

    /**
     * The HUD config name for a seat (0 = pilot), from this vehicle's {@code HUD =} config list, or null.
     * @see MchWeaponSync#hudName
     */
    public String hudName(int seat) { return this.weaponSync.hudName(seat); }

    /**
     * SERVER: queue a weapon cycle (+1 next / -1 previous), applied on the next tick.
     * @see MchFireControl#queueWeaponSwitch
     */
    public void queueWeaponSwitch(Entity sender, int direction) {
        this.fireControl.queueWeaponSwitch(sender, direction);
    }

    /**
     * SERVER: the reference {@code KeySwWeaponMode} for {@code player}'s seat ({@code switchCurrentWeaponMode} ->
     * {@code MCH_WeaponSet.switchMode}), with the reference client's gates: not a wreck, not in the resupply GUI.
     * @see MchFireControl#switchWeaponMode
     */
    public void switchWeaponMode(Player player) { this.fireControl.switchWeaponMode(player); }

    /**
     * Whether the landing gear is up (synced status; reference {@code isLandingGearFolded}).
     * @see MchVehicleParts#isLandingGearFolded
     */
    public boolean isLandingGearFolded() { return this.vehicleParts.isLandingGearFolded(); }

    /**
     * Reference {@code canFoldLandingGear}: a config with gear, fully down, nothing collidable within 10 below.
     * @see MchVehicleParts#canFoldLandingGear
     */
    public boolean canFoldLandingGear() { return this.vehicleParts.canFoldLandingGear(); }

    /**
     * Reference {@code canUnfoldLandingGear}: a config with gear, fully up.
     * @see MchVehicleParts#canUnfoldLandingGear
     */
    public boolean canUnfoldLandingGear() { return this.vehicleParts.canUnfoldLandingGear(); }

    /**
     * SERVER: reference {@code foldLandingGear} (gated only by the shared part/mode switch cooldown, like the
     * reference, which it restarts).
     * @see MchVehicleParts#foldLandingGear
     */
    public void foldLandingGear() { this.vehicleParts.foldLandingGear(); }

    /**
     * SERVER: reference {@code unfoldLandingGear} (gated only by the shared part/mode switch cooldown).
     * @see MchVehicleParts#unfoldLandingGear
     */
    public void unfoldLandingGear() { this.vehicleParts.unfoldLandingGear(); }

    /** The vehicle's one part/mode switch cooldown (reference {@code get/setModeSwitchCooldown}), for the category
     *  switches that live in a subclass (the plane's VTOL nozzle) and for {@link MchVehicleParts}' switches. */
    protected final mcheli.agnostic.sim.ModeSwitchCooldown modeSwitchCooldown() { return this.modeSwitchCooldown; }

    /**
     * SERVER: the pilot's gear key (reference {@code KeyGearUpDown} -> {@code switchGear} 1/2): retract when
     * {@link #canFoldLandingGear}, else extend when {@link #canUnfoldLandingGear}.
     * @see MchVehicleParts#toggleLandingGear
     */
    public void toggleLandingGear(Player player) { this.vehicleParts.toggleLandingGear(player); }

    /** Throttle under which a retracted gear drops near the ground (reference {@code getUnfoldLandingGearThrottle}). */
    protected float unfoldLandingGearThrottle() { return 0.8F; }

    /** VTOL nozzle mode 0 forward / 1 transition / 2 hover (reference base {@code getVtolMode}); {@link MchPlane}
     *  overrides it. The gear drops on its own in full hover near the ground. */
    public int getVtolMode() { return 0; }

    /** Spooled engine power 0..1 (synced). Full when a rider holds throttle-up, idles at 0.5 while ridden, 0 when
     *  abandoned — clients read it for rotor RPM / sound. */
    public float getEnginePower() { return this.entityData.get(DATA_THROTTLE); }

    /** The real flight throttle 0..1 (synced) — the port of {@code getCurrentThrottle()}: 0 at rest, rising as you spool
     *  up. Drives the engine sound loop + rotor down-wash (NOT {@link #getEnginePower}, which idles at 0.5 when ridden). */
    public float getThrottleInput() { return this.entityData.get(DATA_SIM_THROTTLE); }

    // ---- Rotor / propeller spin (reference rotationRotor): the client spin lives in MchVehicleAnimation ----

    /** Whether the rotor can currently generate lift — the port of {@code MCH_EntityHeli.canUseBlades}. True for every
     *  vehicle without a foldable rotor; {@link MchHelicopter} overrides it to gate lift while the blades are folded or
     *  mid-fold. The heli flight model reads this through {@link mcheli.agnostic.sim.HeliState#canUseBlades}. */
    public boolean canUseBlades() { return true; }

    /** Whether this is a fold-capable heli that is currently folded AND resting on the ground — gates the parked-taxi
     *  nudge in the heli flight model ({@code onUpdate_ControlFoldBladeAndOnGround}). False for everything else. */
    public boolean isFoldedOnGround() { return false; }

    // ---- Entity radar (reference MCH_Radar + MCH_EntityAircraft.updateRadar): the sweep and the blip scan live in
    //      MchVehicleAnimation; the HUD gate and the per-type speed hook stay here ----

    /** True if this vehicle's config mounts an entity radar ({@code EnableEntityRadar}) — the HUD {@code have_radar} gate
     *  (reference {@code isEntityRadarMounted}). */
    public boolean isRadarMounted() {
        MCH_AircraftInfo info = weaponHostInfo();
        return info != null && info.isEnableEntityRadar;
    }

    /** Degrees the radar sweep advances per client tick — reference per-type {@code updateRadar} speed: heli/vehicle 5,
     *  tank/plane 10. Both divide 360 so the sweep lands exactly on 0 (where the re-scan fires). */
    protected int radarSweepSpeed() { return 5; }

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
        this.vehicleParts.load(tag); // PartStatus (saved bits) + SearchLight
        this.hull.load(tag); // AcDamage (clamped to the config) + Destroyed and Despawn (raw)
        this.logistics.load(tag); // AcFuel (raw set) + FuelSlots
        this.crew.loadSeats(tag); // Seats: each rider's saved seat, handed back when it re-boards
        this.fireControl.loadAmmo(tag); // AcWeaponsAmmoByName, else the legacy AcWeaponsAmmo (applied when the loadout builds)
        // Paint scheme, roll and the pilot-aim latch (reference readEntityFromNBT :748-753). The skin is stored by
        // texture NAME, as the reference does, so a config that reorders its AddTexture list keeps the right paint.
        if (tag.contains("TextureName")) {
            this.entityData.set(DATA_SKIN, skinIndexOf(tag.getString("TextureName")));
        }
        this.rollAngle = this.prevRollAngle = tag.getFloat("AcRoll");
        this.entityData.set(DATA_ROLL, this.rollAngle);
        this.riderPose.loadAimLatch(tag); // AcLastRYaw/AcLastRPitch, when saved
    }

    /** SERVER: select the paint scheme by config texture name (reference {@code setTextureName}); an unknown name
     *  selects the default paint. */
    public void setTextureName(String name) {
        if (!this.level().isClientSide) {
            this.entityData.set(DATA_SKIN, skinIndexOf(name));
        }
    }

    /** The skin index whose config texture is {@code name}, or 0 (the default paint) when the config no longer has it. */
    private int skinIndexOf(String name) {
        MCH_AircraftInfo info = weaponHostInfo();
        if (info != null && name != null && !name.isEmpty()) {
            for (int i = 0; i < skinCount(); i++) {
                if (name.equalsIgnoreCase(info.getTextureName(i))) {
                    return i;
                }
            }
        }
        return 0;
    }

    /** SERVER: the wrench paint (reference {@code interactFirst}:4318-4324): the next paint scheme, wrapping. A bridge for
     *  {@link MchVehicleCrew#handleInteract}, so {@link #DATA_SKIN} stays written only here. */
    void cycleSkin() {
        int count = Math.max(1, this.skinCount());
        this.entityData.set(DATA_SKIN, (this.getSkinIndex() + 1) % count);
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("Config", configName());
        this.vehicleParts.save(tag); // PartStatus (saved bits) + SearchLight
        this.hull.save(tag); // AcDamage + Destroyed + Despawn
        this.logistics.save(tag); // AcFuel + FuelSlots
        this.crew.saveSeats(tag); // Seats: {Id, Seat} per seated rider
        this.fireControl.saveAmmo(tag); // AcWeaponsAmmoByName (the live loadout, else the restored totals) or the legacy array
        String skin = skinTextureName();
        if (skin != null && !skin.isEmpty()) {
            tag.putString("TextureName", skin);
        }
        tag.putFloat("AcRoll", this.rollAngle);
        this.riderPose.saveAimLatch(tag); // AcLastRYaw/AcLastRPitch, only while latched
    }

    /** The AABB used for frustum culling. The collision box is a small faithful 2.0×0.7 core, but the MODEL spans the
     *  full rotor/wing (up to ~19 blocks), so inflate generously — else the vehicle pops out of view whenever its tiny
     *  core box leaves the frustum even though the model is on screen. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(16.0);
    }

    /** The entity box from the config {@code Width}/{@code Height} (reference {@code onAcInfoReloaded} →
     *  {@code setSize(bodyWidth, bodyHeight)}, MCH_EntityAircraft:718-722), scaled from the type's 2.0×0.7 so it is
     *  the type's own dimensions whenever a config leaves the defaults (every bundled one does). Deliberate
     *  divergence: the reference's spawn data handed clients a fixed width of 2.0 ({@code writeSpawnData}:724-732);
     *  here both sides use the config width, so client picking and server collision agree. */
    @Override
    public EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        EntityDimensions base = super.getDimensions(pose);
        MCH_AircraftInfo info = weaponHostInfo();
        if (info == null || info.bodyWidth <= 0.0F || info.bodyHeight <= 0.0F) {
            return base;
        }
        return base.scale(info.bodyWidth / base.width(), info.bodyHeight / base.height());
    }

    /** Render distance (reference {@code renderDistanceWeight = RenderDistanceWeight}, MCH_EntityAircraft:263, default
     *  10): the 1.7.10 {@code isInRangeToRenderDist} rule {@code avgEdge * 64 * weight}, still scaled by the vanilla
     *  Entity Distance option. Vanilla's weight of 1 hid a 2.0×0.7 vehicle beyond ~100 blocks; the server's tracking
     *  range still bounds what a client can see at all. Client-only caller; the client config is read there. */
    @Override
    public boolean shouldRenderAtSqrDistance(double distSq) {
        double d = this.getBoundingBox().getSize();
        if (Double.isNaN(d)) {
            d = 1.0;
        }
        d *= 64.0 * mcheli.dependent.config.MchConfig.CLIENT.renderDistanceWeight() * getViewScale();
        return distSq < d * d;
    }

    @Override public boolean isPickable() { return !this.isRemoved(); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return true; }

    /**
     * The reference team boarding gate {@code checkTeam} ({@code MCH_EntityAircraft:4283-4295}), on vanilla scoreboard
     * teams: refused when any PLAYER aboard is on a team and {@code player} is not on that same team (a crew player
     * without a team never refuses; a {@code player} without a team is refused by any teamed crew player).
     * @see MchVehicleCrew#checkTeam
     */
    public boolean checkTeam(Player player) { return this.crew.checkTeam(player); }

    /** @see MchVehicleCrew#handleInteract */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return this.crew.handleInteract(player, hand);
    }

    /**
     * Click a SPECIFIC seat to board it (the reference lets you right-click a seat's own 1×1 box, so a gunner can take
     * their station even on a pilotless craft).
     * @see MchVehicleCrew#handleInteractAt
     */
    @Override
    public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
        return this.crew.handleInteractAt(player, hit, hand);
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
        if (this.level().isClientSide && this.localRotationOwned) {
            // The rotation just advanced LIVE for this frame, so this frame must render it as-is. Lerping from the
            // per-tick snapshot instead (vanilla's rotLerp(yRotO, yRot) in the model renderer, CameraMixin and the
            // seat/eye transform) eased each tick's turn by partialTick on top of the per-frame advance — a pt-squared
            // ease that juddered at 20 Hz. Vanilla's own per-frame Entity.turn moves the "old" angles along too.
            this.yRotO = this.getYRot();
            this.xRotO = this.getXRot();
            this.prevRollAngle = this.rollAngle;
        }
    }

    /** CLIENT: is the pilot's view yaw welded to the hull right now (reference {@code isOverridePlayerYaw})? False on
     *  a free axis (a plane in gunner mode), where the client keeps the vanilla mouse look. */
    public boolean overridesPilotYaw() {
        RotationSolver.ControlMapping m = controlMapping();
        return m == null || m.overridesRiderYaw();
    }

    /** CLIENT: is the pilot's view pitch welded to the hull right now (reference {@code isOverridePlayerPitch})? False
     *  while a heli or plane hovers (gunner/hover mode), so the pilot looks up and down freely to aim. */
    public boolean overridesPilotPitch() {
        RotationSolver.ControlMapping m = controlMapping();
        return m == null || m.overridesRiderPitch();
    }

    /**
     * CLIENT: once per client tick, for the vehicle the local player pilots with mouse rotation (called by
     * {@code MchClientRotation} with the local keys and client settings) — the reference's per-tick client half of
     * {@code onUpdateAircraft} on the rider's own client. Aircraft level a hovering hull here (their rotation is owned
     * by this client); the tank advances its client throttle copy. Default: nothing.
     */
    public void clientControlTick(ControlInput in) {}

    /** Whether the pilot has a brake (reference {@code hasBrake}: the tank). The client sends the brake bit only then. */
    public boolean hasBrake() { return false; }

    /** SERVER: the brake also locks the steering once the vehicle has all but stopped — reference
     *  {@code MCH_TankPacketHandler}:80-86 ({@code getCurrentThrottle() <= 0.03 && dist < 0.01}, dist = last tick's
     *  squared horizontal travel). */
    public boolean brakeLocksSteering() {
        double dx = getX() - this.xo;
        double dz = getZ() - this.zo;
        return simThrottle() <= 0.03 && dx * dx + dz * dz < 0.01;
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
        this.riderPose.updateAimLatch(); // the pilot-aim latch feeding the turret ring / rotSeat crew seats (both sides)
        this.vehicleParts.tick(); // canopy/hatch/wing/bays/light hatch ramps (both sides); lever/rot parts/camera turret (client)

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
            this.vehicleParts.tickLandingGear(); // the synced gear status + the local 2.5 deg/tick travel the renderer draws
            this.animation.tickClient(); // running gear + gatling barrel, rotor spin, radar sweep, recoil kick (in order)
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
        this.hull.tickHitCooldown();                            // MCHeli's own damage cooldown (reference :2121)
        this.hitboxes.update();                                 // refresh the part hitboxes to the current pose

        // Reference MCH_EntityAircraft:1406-1412: a target drone that runs out of fuel blows itself up (the
        // power-2 flaming, block-breaking blast of destroyVehicle's non-terrain branch, as the reference's).
        if (isTargetDrone() && !isDestroyed() && this.tickCount > 20 && !canUseFuel(false)) {
            setDamageTaken(getMaxHp());
            destroyVehicle(null, false);
        }

        this.crew.tickDeferred(); // DismountAll and the forced-mount ejects, deferred out of the passenger hooks
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
            // water attitude damping AND over the tank's server-side terrain tilt (TankFlightModel.integrateForces —
            // the physics still USED the rotation for thrust this tick; only its WRITES are overridden). This is
            // reference-faithful: MCH_WheelManager:165 skips its own direct attitude write for exactly the case where
            // the rider is the client player, because that client is running the same solve against the same blocks.
            this.setYRot(this.pendingYaw);
            this.setXRot(this.pendingPitch);
            this.setRollAngle(this.pendingRoll);
        }

        // Terrain interaction, post-move (reference onUpdate_updateBlock + updateCollisionBox, MCH_EntityTank:724-725).
        // The part hitboxes were posed PRE-physics above, so re-pose them to where we actually ENDED UP — the
        // reference recomputes getTransformedPosition() fresh inside updateCollisionBox.
        this.hitboxes.update();
        // Server option Collision_DestroyBlock gates every terrain edit (reference onUpdate_updateBlock :2788,
        // MCH_WheelManager.updateBlock :268, MCH_EntityTank.updateCollisionBox :917/:926), and mobGriefing on top.
        // Computed ONCE: each canEntityGrief call posts a bus event.
        boolean grief = mcheli.dependent.config.MchConfig.SERVER.collisionDestroyBlock()
            && EventHooks.canEntityGrief(level(), this);
        this.terrain.tickServer(grief); // the light sweep, then the tank crush/ram pass

        // No vanilla fall damage (reference zeroes fallDistance for the craft AND its riders every tick) — a vehicle
        // is a non-Living Entity so it never falls-damages itself, but this keeps a dismounting pilot safe.
        this.fallDistance = 0.0F;
        for (Entity p : this.getPassengers()) {
            p.fallDistance = 0.0F;
        }

        if (isDestroyed()) {
            // Wreck: the per-type tumble, the one-shot crash detonation, the rider burn, then the despawn countdown;
            // a discarded wreck skips the rest of the server tick.
            if (this.hull.tickWreck(prevOnGround, prevMotionY)) {
                return;
            }
        } else {
            // Alive: attitude-crash into terrain + water-submersion damage (both route through hurt(inWall)).
            this.terrain.collisionGroundDamage();
        }
        this.hull.clearPartFactor(); // clear any unread part factor so it can't leak to a later hit (reference :1363)

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

        // Landing gear (reference updateParts, right after onUpdateAircraft): travel + the automatic retract/extend.
        // The pilot raises and lowers it with the gear key (toggleLandingGear).
        this.vehicleParts.tickLandingGear();

        // Fuel economy: burn with the sim throttle, and run any supply aura this config declares (reference order —
        // supply/fuel run alongside the weapon tick).
        this.logistics.tickServer(); // burn, the fuel-can siphon, then the supply auras
        // Repair + medevac (reference repairOtherAircraft :1439, autoRepair :1479, regenerationMob :1503).
        this.hull.tickRepair(); // area repair, auto repair, then the medevac regeneration

        // Weapons: config-driven loadout (built lazily), cycled by the switch payload and fired while held.
        this.fireControl.tickServer();

        // Flares/countermeasures: advance the dispense window, eject volleys, and decoy any missiles homing on us.
        this.countermeasures.tickServer();
    }

    // ---- flares / countermeasures (#26) — server-authoritative dispenser + missile decoy (MchCountermeasures) ----

    /**
     * This vehicle's config declares flares (reference {@code haveFlare}: FlareType set).
     * @see MchCountermeasures#haveFlare
     */
    public boolean haveFlare() { return this.countermeasures.haveFlare(); }

    /**
     * The decoy-active burn window is open (reference {@code isFlareUsing}).
     * @see MchCountermeasures#isFlareUsing
     */
    public boolean isFlareUsing() { return this.countermeasures.isFlareUsing(); }

    /**
     * Ready to dispense (has flares + the dispenser is idle).
     * @see MchCountermeasures#canDeployFlare
     */
    public boolean canDeployFlare() { return this.countermeasures.canDeployFlare(); }

    /** True if the flare dispenser is idle/ready — reads the SYNCED state, so it is correct on the client (the
     *  dispenser in {@link MchCountermeasures} is itself only ticked server-side). */
    public boolean flareDispenserIdle() { return this.entityData.get(DATA_FLARE_STATE) == 0; }

    /**
     * The flare type the next dispense will use (cycles the config's {@code FlareType} list).
     * @see MchCountermeasures#currentFlareType
     */
    public int currentFlareType() { return this.countermeasures.currentFlareType(); }

    /**
     * Advance to the next configured flare type (reference {@code nextFlareType}).
     * @see MchCountermeasures#nextFlareType
     */
    public void nextFlareType() { this.countermeasures.nextFlareType(); }

    /**
     * Begin a dispense of {@code type} (server-side; reference {@code useFlare}).
     * @see MchCountermeasures#deployFlare
     */
    public void deployFlare(int type) { this.countermeasures.deployFlare(type); }

    /**
     * Send an RWR tone to the seat-0/1 crew (reference {@code MCH_MissileDetector} warns {@code getEntityBySeatId(0..1)}).
     * @see MchCountermeasures#sendRwrToCrew
     */
    public void sendRwrToCrew(byte kind) { this.countermeasures.sendRwrToCrew(kind); }

    /**
     * True if {@code seat}'s currently-selected weapon is a lock-on missile (server-side; the NotifyLock anti-spoof).
     * @see MchFireControl#selectedWeaponIsLockOn
     */
    public boolean selectedWeaponIsLockOn(int seat) { return this.fireControl.selectedWeaponIsLockOn(seat); }

    /** The weapon types that lock on to an entity: the reference {@code MCH_WeaponEntitySeeker} subclasses (AA, AT). */
    public static boolean isLockOnType(String type) {
        return "aamissile".equalsIgnoreCase(type) || "atmissile".equalsIgnoreCase(type);
    }

    /** This vehicle's config {@code Stealth} (0..1, reference {@code MCH_EntityAircraft.getStealth}). */
    public float getStealth() {
        MCH_AircraftInfo info = weaponHostInfo();
        return info != null ? info.stealth : 0.0F;
    }

    /**
     * The stealth a seeker sees on {@code e} (reference {@code MCH_WeaponGuidanceSystem.getEntityStealth}): a vehicle's
     * own, a rider's that of the vehicle it rides, otherwise 0. The client lock tracker and the server lock audit both
     * scale the lock with it ({@code MCH_LockGeometry}). In the reference only the pilot rode the aircraft itself (the
     * other seats rode an {@code MCH_EntitySeat}, so a gunner read 0); every port crew member rides the hull, so the
     * whole crew takes its stealth.
     */
    public static float lockStealth(Entity e) {
        if (e instanceof AbstractMchVehicle v) {
            return v.getStealth();
        }
        return e != null && e.getVehicle() instanceof AbstractMchVehicle v ? v.getStealth() : 0.0F;
    }

    // ---- server lock audit (see MCH_LockAudit): what the server watched of each seat's client-built lock; in
    //      MchLockAudits, owned by MchFireControl ----

    /**
     * SERVER: a lock notice from {@code sender} in {@code seat} naming {@code target} ({@code ServerboundNotifyLockPayload}).
     * @see MchLockAudits#noteLockNotice
     */
    public boolean noteLockNotice(int seat, Entity sender, Entity target) {
        return this.fireControl.lockAudits().noteLockNotice(seat, sender, target);
    }

    // ---- config-driven weapons ----

    // ---- SEAT MAP (#36) — 1.21.1 multi-passenger replaces the reference's per-seat MCH_EntitySeat entities. Seat 0 is
    //      the pilot; the map is server truth, mirrored to clients via DATA_SEATS and persisted by rider UUID. The seat
    //      map, boarding, the passenger hooks and seat switching live in MchVehicleCrew. ----

    /**
     * How many seats this config declares (rack slots are reserved in the index space but never boardable).
     * @see MchVehicleCrew#seatCount
     */
    public int seatCount() { return this.crew.seatCount(); }

    /**
     * The seat {@code p} occupies, or -1.
     * @see MchVehicleCrew#seatIndexOf
     */
    public int seatIndexOf(Entity p) { return this.crew.seatIndexOf(p); }

    /**
     * The PLAYER operating {@code w} right now, or null — the port of {@code getWeaponUserByWeaponName}
     * ({@code MCH_EntityAircraft:3061-3076}): the occupant of the weapon's own seat, else the pilot but ONLY when the
     * weapon is {@code canUsePilot}.
     * @see MchVehicleCrew#weaponOperator
     */
    public Entity weaponOperator(MCH_AircraftInfo.Weapon w) { return this.crew.weaponOperator(w); }

    // ---- GUNNER MODE (#36b) — reference isGunnerMode (pilot toggle) + the structural gunner-seat mode; the toggles, the
    //      gunner-seat bits and the saved throttle live in MchVehicleCrew ----

    /** The PILOT's toggled gunner mode (synced). The flight sim reads this; clients read it for camera/zoom. */
    public boolean isGunnerModeActive() {
        return this.entityData.get(DATA_GUNNER_MODE);
    }

    /**
     * Is {@code seat}'s occupant in gunner mode right now — the port of {@code getIsGunnerMode}
     * ({@code MCH_EntityAircraft:5349-5367}): the pilot iff {@link #isGunnerModeActive}; a dedicated gunner SEAT is
     * permanently in gunner mode; a {@code switchgunner} seat follows its synced toggle ({@link #DATA_SEAT_GUNNER_MODE}),
     * which boarding turns on.
     * @see MchVehicleCrew#isSeatGunnerMode
     */
    public boolean isSeatGunnerMode(int seat) { return this.crew.isSeatGunnerMode(seat); }

    /**
     * May the occupant of {@code seat} toggle its gunner mode — the port of {@code canSwitchGunnerModeOtherSeat}
     * ({@code MCH_EntityAircraft:5304-5314}): a passenger seat that is a switchable gunner seat.
     * @see MchVehicleCrew#canSwitchGunnerModeOtherSeat
     */
    public boolean canSwitchGunnerModeOtherSeat(int seat) { return this.crew.canSwitchGunnerModeOtherSeat(seat); }

    /**
     * May the pilot toggle gunner mode — the port of {@code canSwitchGunnerMode} ({@code MCH_EntityAircraft:5294}):
     * the config must enable it, and (unless it enables CONCURRENT gunner mode) seat 1 must be empty so a seat-1 gunner
     * and the pilot-gunner never fight over the same weapon, and the canopy must be closed.
     * @see MchVehicleCrew#baseCanSwitchGunnerMode
     */
    public boolean canSwitchGunnerMode() { return this.crew.baseCanSwitchGunnerMode(); }

    /**
     * SERVER: the gunner-mode key of {@code player}'s seat.
     * @see MchVehicleCrew#toggleGunnerMode
     */
    public void toggleGunnerMode(Player player) { this.crew.toggleGunnerMode(player); }

    /**
     * SERVER: the reference {@code switchGunnerMode(mode)} (MCH_EntityAircraft:5274-5290).
     * @see MchVehicleCrew#switchGunnerMode
     */
    public void switchGunnerMode(boolean on) { this.crew.switchGunnerMode(on); }

    /**
     * The saved pre-mode throttle, for the heli's hover mode, which shares it (reference).
     * @see MchVehicleCrew#baseBeforeHoverThrottle
     */
    protected double beforeHoverThrottle() { return this.crew.baseBeforeHoverThrottle(); }
    /** @see MchVehicleCrew#baseSetBeforeHoverThrottle */
    protected void setBeforeHoverThrottle(double t) { this.crew.baseSetBeforeHoverThrottle(t); }

    /** SERVER: overwrite the flight-sim throttle (the categories that own a sim state override it); the gunner/hover
     *  mode switch restores the saved throttle through it. No-op for a category without one. */
    protected void setSimThrottle(double throttle) {}

    /**
     * Who is sitting in {@code seat}, or null.
     * @see MchVehicleCrew#seatPassenger
     */
    public Entity seatPassenger(int seat) { return this.crew.seatPassenger(seat); }

    /**
     * The PILOT (seat 0) — the ONLY rider who drives.
     * @see MchVehicleCrew#pilot
     */
    public Entity pilot() { return this.crew.pilot(); }

    // DELIBERATELY NOT overriding getControllingPassenger(): returning the pilot would flip this entity into vanilla's
    // client-authoritative boat/minecart regime — isControlledByLocalInstance() becomes true on the pilot's client, which
    // then ships ServerboundMoveVehiclePacket every tick and the server absMoveTo()s the vehicle onto it, OVERWRITING the
    // server physics (and arming the "flying a vehicle" kick on a dedicated server with allow-flight=false). This port is
    // server-authoritative for POSITION (see the class doc); pilot() is our own concept and must stay out of vanilla's.

    /**
     * May {@code seat} be boarded right now?
     * @see MchVehicleCrew#canOccupySeat
     */
    public boolean canOccupySeat(int seat) { return this.crew.canOccupySeat(seat); }

    /**
     * Programmatically request that {@code rider} take a specific {@code seat} on its NEXT boarding — the code-path
     * equivalent of clicking that seat's box ({@link #interactAt}), for mounts that never go through an interact:
     * scripted crew, NPCs, or the headless self-tests.
     * @see MchVehicleCrew#preferSeat
     */
    public void preferSeat(Entity rider, int seat) { this.crew.preferSeat(rider, seat); }

    /** @see MchVehicleCrew#canAdd */
    @Override
    protected boolean canAddPassenger(Entity p) {
        return this.crew.canAdd(p);
    }

    /** @see MchVehicleCrew#handlePassengerAdded */
    @Override
    protected void addPassenger(Entity p) {
        super.addPassenger(p);
        this.crew.handlePassengerAdded(p);
    }

    /** @see MchVehicleCrew#handlePassengerRemoved */
    @Override
    protected void removePassenger(Entity p) {
        super.removePassenger(p);
        this.crew.handlePassengerRemoved(p);
    }

    /**
     * Move to the next-higher empty PASSENGER seat, wrapping — the port of {@code switchNextSeat}
     * ({@code MCH_EntityAircraft:4419}).
     * @see MchVehicleCrew#switchSeatNext
     */
    public void switchSeatNext(Player player) { this.crew.switchSeatNext(player); }

    /** The next-higher empty PASSENGER seat from {@code cur}, wrapping to the lowest empty; -1 if none. Seat 0 is never
     *  a next/prev target (only {@link #grabPilotSeat} reaches it). Delegates to the unit-tested
     *  {@link mcheli.agnostic.vehicle.VehicleSeats#nextSeatTarget}. */
    public static int nextSeatTarget(int cur, int count, java.util.function.IntPredicate empty) {
        return mcheli.agnostic.vehicle.VehicleSeats.nextSeatTarget(cur, count, empty);
    }

    /** The next-lower empty PASSENGER seat from {@code cur}, wrapping to the highest empty; -1 if none
     *  ({@link mcheli.agnostic.vehicle.VehicleSeats#prevSeatTarget}). */
    public static int prevSeatTarget(int cur, int count, java.util.function.IntPredicate empty) {
        return mcheli.agnostic.vehicle.VehicleSeats.prevSeatTarget(cur, count, empty);
    }

    /**
     * Move to the next-lower empty PASSENGER seat, wrapping — the port of {@code switchPrevSeat}
     * ({@code MCH_EntityAircraft:4462}).
     * @see MchVehicleCrew#switchSeatPrev
     */
    public void switchSeatPrev(Player player) { this.crew.switchSeatPrev(player); }

    /**
     * Take the pilot seat if it is free — the useful case of the reference's {@code switchSeat==3} path
     * ({@code MCH_AircraftPacketHandler:300-304}, which dismounts then re-boards as pilot when seat 0 is empty).
     * @see MchVehicleCrew#grabPilotSeat
     */
    public void grabPilotSeat(Player player) { this.crew.grabPilotSeat(player); }

    // ---- fuel & supply economy (#37) — reference MCH_EntityAircraft:2223-2308 (fuel), :2264-2451 (supply auras);
    //      the burn, the fuel-can siphon and the supply auras live in MchLogistics ----

    /** The vehicle's 3 FUEL SLOTS (reference {@code MCH_AircraftInventory} SLOT_FUEL0..2). Drop a fuel can in and the
     *  tank siphons it. Persisted in NBT and dropped when the wreck despawns. */
    public static final int FUEL_SLOTS = 3;

    /** @see MchLogistics#fuelInventory */
    public net.minecraft.world.SimpleContainer fuelInventory() {
        return this.logistics.fuelInventory();
    }

    /**
     * Rearm ONE weapon by index from the riding GUI (the port of {@code supplyAmmo} / {@code canPlayerSupplyAmmo});
     * returns true if a supply pulse was granted.
     * @see MchFireControl#supplyAmmoFromPlayer
     */
    public boolean supplyAmmoFromPlayer(Player player, int weaponIndex) {
        return this.fireControl.supplyAmmoFromPlayer(player, weaponIndex);
    }

    /** True while the pilot is in the resupply GUI (or within its 20-tick tail) — reference {@code isPilotReloading}. */
    public boolean isPilotReloading() { return this.entityData.get(DATA_RELOADING); }

    /**
     * {@code seat}'s selected weapon's RESERVE (reference {@code getRestAllAmmoNum}) = its total minus the magazine in
     * the gun.
     * @see MchWeaponSync#selectedRestAmmo
     */
    public int selectedRestAmmo(int seat) { return this.weaponSync.selectedRestAmmo(seat); }

    /**
     * SERVER: publish every seat's selection and every weapon's mode, then each riding player's own seat-weapon notice
     * (the package bridge for a mount, a dismount, a seat switch and the weapon tick; the loadout must be built).
     * @see MchWeaponSync#publishSelection
     */
    void publishSeatWeapons() { this.weaponSync.publishSelection(weaponLoadout()); }

    /**
     * Total ammo (magazine + reserve) of weapon {@code i} — CLIENT-readable, for the riding GUI.
     * @see MchWeaponSync#ammoOf
     */
    public int ammoOf(int i) { return this.weaponSync.ammoOf(i); }

    /**
     * The number of weapons this vehicle has (CLIENT-readable, from the config).
     * @see MchWeaponSync#weaponCount
     */
    public int weaponCount() { return this.weaponSync.weaponCount(); }

    /**
     * Weapon {@code i}'s slot, resolved from the config — CLIENT-safe (ammo lives in {@link #ammoOf}).
     * @see MchWeaponSync#weaponAt
     */
    public WeaponSlot weaponAt(int i) { return this.weaponSync.weaponAt(i); }

    /**
     * Spill the fuel slots when the vehicle leaves the world for good — the port of {@code MCH_AircraftInventory.setDead}
     * (:63-75), which drops the inventory contents on death.
     * @see MchLogistics#spillOnRemove
     */
    @Override
    public void remove(RemovalReason reason) {
        this.logistics.spillOnRemove(reason);
        super.remove(reason);
    }

    /**
     * Pull every weapon's reserve into its magazine — the reference reloads all weapons on the GUI-CLOSE edge, and
     * only while the vehicle is intact ({@code updateSupplyAmmo}:2355 gates on {@code !isDestroyed()}).
     * @see MchFireControl#reloadAllWeapons
     */
    public void reloadAllWeapons() { this.fireControl.reloadAllWeapons(); }

    public int getFuel() { return this.entityData.get(DATA_FUEL); }

    /** @see MchLogistics#setFuel */
    public void setFuel(int f) { this.logistics.setFuel(f); }

    public int getMaxFuel() {
        MCH_AircraftInfo info = weaponHostInfo();
        return info != null ? info.maxFuel : 0;
    }

    /**
     * Fuel fraction 0..1 for the HUD gauge — faithfully 0 when the config declares no tank.
     * @see MchLogistics#getFuelP
     */
    public float getFuelP() { return this.logistics.getFuelP(); }

    /**
     * The server option {@code InfinityFuel} or a creative occupant makes fuel free.
     * @see MchLogistics#isInfinityFuel
     */
    public boolean isInfinityFuel(boolean anySeat) { return this.logistics.isInfinityFuel(anySeat); }

    /**
     * Can the engine still run?
     * @see MchLogistics#canUseFuel
     */
    public boolean canUseFuel(boolean anySeat) { return this.logistics.canUseFuel(anySeat); }

    /** The flight-sim throttle actually driving the engine — NOT the display {@code enginePower} (which idles at 0.5
     *  while merely ridden and would burn fuel at rest) — the reference {@code getCurrentThrottle()}, on EITHER side:
     *  the server's sim throttle ({@link #serverSimThrottle}), and on a client the synced copy
     *  ({@link #getThrottleInput}), because only the server advances the sim state (the reference client lerped the
     *  throttle it was sent). So a client-side pre-check of a server gate (gunner mode, the wing fold) sees the real
     *  value instead of a sim state stuck at 0. */
    protected final double simThrottle() {
        return this.level().isClientSide ? getThrottleInput() : serverSimThrottle();
    }

    /** SERVER: the sim state's throttle; overridden by the categories that own a sim state. */
    protected double serverSimThrottle() {
        return 0.0;
    }

    /**
     * Receivers must be on solid ground to take on fuel/ammo (reference {@code canSupply}:2223-2225) — a floating
     * vehicle is exempt from the water rule.
     * @see MchLogistics#canSupply
     */
    public boolean canSupply() { return this.logistics.canSupply(); }

    /** The other vehicles that intersect this one's box inflated by {@code range} (supply auras, field repair). Kept
     *  on the entity: {@code out.remove(this)} must exclude this vehicle itself. */
    java.util.List<AbstractMchVehicle> nearby(float range) {
        java.util.List<AbstractMchVehicle> out = this.level().getEntitiesOfClass(
            AbstractMchVehicle.class, getBoundingBox().inflate(range));
        out.remove(this);
        return out;
    }

    /**
     * Reference {@code isInfinityAmmo}: a creative operator, or the server's {@code infinityAmmo} option. Package-private
     * for the crew's boarding ({@link MchVehicleCrew#handlePassengerAdded}).
     * @see MchFireControl#weaponInfinityAmmo
     */
    boolean weaponInfinityAmmo(Entity operator) { return this.fireControl.weaponInfinityAmmo(operator); }

    /**
     * True if {@code b} is this vehicle's live, latest TV-guided missile (reference {@code ac.getTVMissile() == this}).
     * @see MchFireControl#isActiveTvMissile
     */
    public boolean isActiveTvMissile(MchBullet b) { return this.fireControl.isActiveTvMissile(b); }

    /**
     * The body yaw the fire rotates the muzzle position and projectile direction by. Aircraft/tanks use the hull
     * ({@code getYRot()}). {@link MchGroundVehicle} (the reference {@code MCH_EntityVehicle}) overrides this to the
     * operator's LOOK for a no-traverse weapon, so a fixed emplacement launcher fires where the gunner aims — the port
     * of {@code MCH_EntityVehicle.useCurrentWeapon}, which snaps the body to {@code prm.user.rotationYaw} for the shot.
     */
    protected float fireBodyYaw(Entity shooter, MCH_AircraftInfo.Weapon mount) { return this.getYRot(); }

    /** Elevation counterpart of {@link #fireBodyYaw} (reference snaps {@code rotationPitch = prm.user.rotationPitch}). */
    protected float fireBodyPitch(Entity shooter, MCH_AircraftInfo.Weapon mount) { return this.getXRot(); }

    // ---- Client ambient particle effects (reference onUpdate_Client / onUpdate_Particle*) — CLIENT-ONLY ----

    /** Spawn this vehicle's ambient particles this client tick. Base: the damage smoke every vehicle shares; helicopters
     *  add the rotor down-wash. All spawns go through {@code level().addParticle}, a no-op on the server. */
    protected void clientAmbientEffects() {
        spawnDamageSmoke();
        this.effects.spawnWeaponSmoke();
    }

    /**
     * Dark smoke from a wounded vehicle — the port of the shared {@code onUpdate_Particle2} (heli
     * {@code MCH_EntityHeli}:677, plane/tank equivalents): only below 50 % HP, with an emission probability that rises
     * as HP falls (reference {@code p < rand/2}, so it stays rare near 50 % and pours out near 0).
     * @see MchVehicleEffects#baseSpawnDamageSmoke
     */
    protected void spawnDamageSmoke() {
        this.effects.baseSpawnDamageSmoke();
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

    // ==== HP / armor / destruction (faithful port of MCH_EntityAircraft.attackEntityFrom + destroyAircraft): the hull
    //      state and the bodies live in MchVehicleHull; the destruction and tumble hooks stay here ====

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

    /**
     * Config max HP ({@code MaxHP}), or 100 when this vehicle has no parsed info (reference {@code getMaxHP}).
     * @see MchVehicleHull#getMaxHp
     */
    public int getMaxHp() { return this.hull.getMaxHp(); }

    /** Accumulated damage (synced). HP is derived, never stored directly (reference {@code damageTaken}). */
    public int getDamageTaken() { return this.entityData.get(DATA_DAMAGE_TAKEN); }

    /**
     * SERVER: set the accumulated damage, clamped to {@code [0, maxHp]} (reference {@code setDamageTaken}).
     * @see MchVehicleHull#setDamageTaken
     */
    public void setDamageTaken(int v) { this.hull.setDamageTaken(v); }

    /**
     * Current hit points = {@code maxHp - damageTaken}, floored at 0 (reference {@code getHP}).
     * @see MchVehicleHull#getHp
     */
    public int getHp() { return this.hull.getHp(); }

    /**
     * Mend {@code tpd} HP of accumulated damage (reference {@code MCH_EntityAircraft.repair}) — the wrench's per-tick
     * heal.
     * @see MchVehicleHull#repair
     */
    public boolean repair(int tpd) { return this.hull.repair(tpd); }

    // ---- self-repair, area repair, medevac regeneration (reference MCH_EntityAircraft:2120-2197): in MchVehicleHull ----

    /** True once the craft is a wreck (synced). Weapon damage no-ops from here; only crash/despawn act (reference). */
    public boolean isDestroyed() { return this.entityData.get(DATA_DESTROYED); }

    /**
     * True after a wreck has already crash-detonated — the one-shot gate on {@link #explosionByCrash} (reference).
     * @see MchVehicleHull#isExploded
     */
    public boolean isExploded() { return this.hull.isExploded(); }

    /**
     * SERVER: stash the per-part weak-point factor for the imminent {@code hurt()} call (called by {@link MchBullet}).
     * @see MchVehicleHull#setLastBBDamageFactor
     */
    public void setLastBBDamageFactor(float f) { this.hull.setLastBBDamageFactor(f); }

    /**
     * The faithful port of {@code MCH_EntityAircraft.attackEntityFrom} (its exact order is load-bearing).
     * @see MchVehicleHull#handleHurt
     */
    @Override
    public boolean hurt(DamageSource src, float amount) {
        return this.hull.handleHurt(src, amount);
    }

    /** {@code Entity.markHurt} (protected there; the client hit-wobble), the package bridge for the hull's hurt. */
    void markHurtBridge() { markHurt(); }

    /**
     * True while any seat holds a player (reference {@code isRidePlayer}:3504-3516).
     * @see MchVehicleHull#isRidePlayer
     */
    public boolean isRidePlayer() { return this.hull.isRidePlayer(); }

    /** The spawn item dropped on salvage — this vehicle's own registered spawn item (by config name), or null if none
     *  is registered (e.g. the fallback config). */
    protected Item dropItem() {
        return mcheli.dependent.registry.MchRegistries.spawnItemFor(configName());
    }

    // ---- vehicle data on the spawn item (reference setAcDataToItem / getAcDataFromItem, MCH_EntityAircraft:987-1015):
    //      in MchVehicleHull ----

    /**
     * Stamp this vehicle's fuel and damage on {@code stack} when the server options {@code ItemFuel} /
     * {@code ItemDamage} are on (both default on).
     * @see MchVehicleHull#writeItemData
     */
    public void writeItemData(ItemStack stack) { this.hull.writeItemData(stack); }

    /**
     * SERVER: restore the fuel and damage a recovered spawn item carries, under the same options.
     * @see MchVehicleHull#readItemData
     */
    public void readItemData(ItemStack stack) { this.hull.readItemData(stack); }

    /**
     * Turn the craft into a wreck (reference {@code destroyAircraft} + the destruction branch of attackEntityFrom).
     * @see MchVehicleHull#baseDestroyVehicle
     */
    protected void destroyVehicle(Entity killer, boolean terrainKill) { this.hull.baseDestroyVehicle(killer, terrainKill); }

    /**
     * Seed the wreck tumble (reference heli default).
     * @see MchVehicleHull#baseSeedWreckTumble
     */
    protected void seedWreckTumble() { this.hull.baseSeedWreckTumble(); }

    /**
     * Apply one tick of wreck tumble (reference heli).
     * @see MchVehicleHull#baseApplyWreckTumble
     */
    protected void applyWreckTumble() { this.hull.baseApplyWreckTumble(); }

    /**
     * Read-only accessors for subclass tumble overrides.
     * @see MchVehicleHull#baseRotDestroyedRoll
     */
    protected float rotDestroyedRoll() { return this.hull.baseRotDestroyedRoll(); }
    protected float rotDestroyedPitch() { return this.hull.baseRotDestroyedPitch(); }
    protected void setRotDestroyedRoll(float r) { this.hull.baseSetRotDestroyedRoll(r); }
    protected void setRotDestroyedPitch(float p) { this.hull.baseSetRotDestroyedPitch(p); }
    protected void setRotDestroyedYaw(float y) { this.hull.baseSetRotDestroyedYaw(y); }

    /**
     * A fuel-scaled AOE fireball at the wreck (reference {@code explosionByCrash}); {@code MaxFuel = 0} → power 1.
     * @see MchVehicleHull#baseExplosionByCrash
     */
    protected void explosionByCrash(double prevMotionY) { this.hull.baseExplosionByCrash(prevMotionY); }

    // ---- per-part hitboxes (weak points / armored zones): in MchPartHitboxes ----

    /**
     * The per-part weak-point/armor factor of the box a bullet segment strikes NEAREST its origin, else 1.0 (the plain
     * hull) — the port of {@code MCH_AircraftBoundingBox.calculateIntercept}.
     * @see MchPartHitboxes#boundingBoxDamageFactorAt
     */
    public float boundingBoxDamageFactorAt(Vec3 from, Vec3 to) { return this.hitboxes.boundingBoxDamageFactorAt(from, to); }

    /**
     * Nearest point where the segment {@code [from, to]} enters this vehicle's core box OR any extra part box, else
     * {@code null}.
     * @see MchPartHitboxes#clipParts
     */
    public Vec3 clipParts(Vec3 from, Vec3 to) { return this.hitboxes.clipParts(from, to); }

    /**
     * Test/diagnostic hook: rebuild (if needed) + reposition the part hitboxes to the current pose without a tick.
     * @see MchPartHitboxes#update
     */
    public void refreshExtraBoxes() { this.hitboxes.update(); }

    /**
     * Wreck lifetime countdown (500 at destruction, →0 then discard); exposed for diagnostics/self-test.
     * @see MchVehicleHull#despawnCount
     */
    public int despawnCount() { return this.hull.despawnCount(); }

    // ---- environmental / crash damage (reference onUpdate_CollisionGroundDamage) ----

    /** Attitude beyond which a near-ground craft takes crash damage (reference {@code getGiveDamageRot}). Aircraft use
     *  40°; a TANK overrides to 91° (it only self-damages when essentially flipped, so it can climb steep terrain). */
    protected float giveDamageRot() { return 40.0F; }

    /** A collidable block within a 3×3 footprint up to 3 blocks below (reference {@code getBlockIdY(this,3,-3) != 0}),
     *  through the shared {@link mcheli.agnostic.sim.GroundProbe} (the reference {@code canCollideCheck} test, so water
     *  or tall grass counts as near the ground, as it did in 1.7.10). Drives both the wreck tumble and the alive
     *  attitude-crash damage ({@link MchTerrainContact#collisionGroundDamage}, reference {@code onUpdate_CollisionGroundDamage}). */
    protected boolean solidBlockBelow() {
        return mcheli.agnostic.sim.GroundProbe.blockIdInColumn(this.ref, 3, -3) != 0;
    }

    // ---- terrain interaction (the submersion probe, the light sweep, the tank crush/ram): in MchTerrainContact ----

    /** The reference {@code MCH_TankInfo.weightType} (0 none / 1 car / 2 tank), or -1 for a category with no crush+ram
     *  pass. {@code MCH_EntityTank} is the only reference class that has one, so only {@link MchTank} overrides this. */
    protected int crushWeightType() {
        return -1;
    }

    /**
     * Test hook: top a seat's selected weapon up to a full reserve + magazine, so a headless probe can fire without
     * standing up the whole ammo-supply economy.
     * @see MchFireControl#debugSupplySeatWeapon
     */
    public void debugSupplySeatWeapon(int seat) { this.fireControl.debugSupplySeatWeapon(seat); }

    /**
     * Diagnostic hook for the headless self-tests: what a seat has selected and whether it can actually fire.
     * @see MchFireControl#debugSeatWeapon
     */
    public String debugSeatWeapon(int seat) { return this.fireControl.debugSeatWeapon(seat); }

    /**
     * Diagnostic hook for the headless self-tests: the resolved config plus the terrain solve's current output.
     * @see MchTerrainContact#debugTerrainState
     */
    public String debugTerrainState() { return this.terrain.debugTerrainState(); }

    /** Run the per-type flight controller for one server tick with the rider's control input. */
    protected abstract void tickPhysics(ControlInput in);
}
