package mcheli.dependent.entity;

import mcheli.agnostic.weapon.MCH_Detonation;
import mcheli.agnostic.weapon.MCH_MissileGuidance;
import mcheli.agnostic.weapon.MCH_WeaponBallistics;
import mcheli.agnostic.weapon.MCH_WeaponInfo;
import mcheli.dependent.config.MchConfig;
import mcheli.dependent.config.MchServerRules;
import mcheli.dependent.particle.MchSmokeOptions;
import mcheli.dependent.particle.MuzzleFxOptions;
import mcheli.dependent.port.NeoEntityRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * A config-driven MCHeli projectile — a faithful distillation of {@code MCH_EntityBaseBullet}/{@code MCH_EntityBullet}.
 * Seeded from the firing weapon's {@link MCH_WeaponInfo}, it carries that weapon's flight (power/gravity/
 * accelerationFactor) plus its full effect suite:
 *
 * <ul>
 *   <li><b>Explosions</b> on impact ({@link MchExplosion}) — power, FAE/in-water variants, flaming, per-victim damage
 *       factor — <i>in addition</i> to the direct-hit power damage.</li>
 *   <li><b>Fuses</b> in the reference tick order (server block runs BEFORE gravity): {@code delayFuse} (arm + block
 *       ricochet), {@code timeFuse} (self-detonate), {@code explosionAltitude} (airburst), and {@code proximityFuseDist}
 *       (detonate near a target: gun rounds at tick end, bombs every 10 ticks). A fuse that runs out follows the
 *       reference timeout rules, which differ from the impact rules ({@link MCH_Detonation}).</li>
 *   <li><b>Piercing</b> — passes through BLOCKS (decrementing, a small burst each); a direct ENTITY hit forcibly zeroes
 *       piercing so the round dies on the entity (faithful to {@code onImpact}). Blocks on the server's
 *       bullet-breakable list are shot through outright.</li>
 *   <li><b>Cluster bomblets</b> — after {@code bombletSTime} ticks a parent sprinkles {@code bomblet} child rounds
 *       (spread by {@code bombletDiff}, rendered with {@code ModelBomblet}) that never re-split.</li>
 * </ul>
 *
 * <p>Effects + collision + damage are server-authoritative; the flight integrator runs both sides (from the synced
 * gravity/accelerationFactor) so the client dead-reckons smoothly. Guided kinds ({@link #DATA_GUIDED}: AA, AT direct and
 * top attack, AS, TV; plus the guided torpedo) steer server-side ({@link #tickGuidance}, {@link #tickTorpedo}) and the
 * client takes their path through {@link #lerpTo}.
 *
 * <p><b>Who fired it</b> (one model, the reference's). {@link #shooter} is the reference {@code shootingEntity}, the
 * entity operating the weapon (the seat's player): it gets the damage and kill credit (so the PvP/team rules apply)
 * and the hit marker, the round despawns when it dies and 5820 blocks from it, it steers a TV missile, its height
 * sets an AT top attack's climb, and a guided AA/AT round that has none is discarded. {@link #launcher} is the
 * reference {@code shootingAircraft}, the vehicle the round left: neither it nor anyone aboard it is hit by the round
 * or its blast, and rounds of the same launcher pass through each other. Child rounds (bomblets, a marker's airstrike)
 * inherit both from their parent.
 */
public class MchBullet extends Entity {

    private static final EntityDataAccessor<String> DATA_MODEL =
        SynchedEntityData.defineId(MchBullet.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_COLOR =
        SynchedEntityData.defineId(MchBullet.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_GRAVITY =
        SynchedEntityData.defineId(MchBullet.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ACCEL_FACTOR =
        SynchedEntityData.defineId(MchBullet.class, EntityDataSerializers.FLOAT);
    /** Trail particle kind (0 = none, 1 = smoke, 2 = flame) — server-computed from the weapon type + config. */
    private static final EntityDataAccessor<Integer> DATA_TRAIL_KIND =
        SynchedEntityData.defineId(MchBullet.class, EntityDataSerializers.INT);
    /** Trail particle render half-size (from the config {@code SmokeSize} × the per-type multiplier). */
    private static final EntityDataAccessor<Float> DATA_TRAIL_SIZE =
        SynchedEntityData.defineId(MchBullet.class, EntityDataSerializers.FLOAT);
    /** Tick before which the trail is suppressed (config {@code TrajectoryParticleStartTick}). */
    private static final EntityDataAccessor<Integer> DATA_TRAIL_START =
        SynchedEntityData.defineId(MchBullet.class, EntityDataSerializers.INT);
    /** Marker-rocket state (reference {@code MCH_EntityMarkerRocket} status): 0 = not a marker, 1 = flying, 2 = planted
     *  (a beacon pulses on the client while the server counts down to the airstrike). */
    private static final EntityDataAccessor<Byte> DATA_MARKER =
        SynchedEntityData.defineId(MchBullet.class, EntityDataSerializers.BYTE);
    /** Guided-missile kind ({@link #GUIDED_NONE} ... {@link #GUIDED_TV}). Synced so the client knows to
     *  accept the server's curved position/orientation via {@link #lerpTo} instead of dead-reckoning a straight line. */
    private static final EntityDataAccessor<Byte> DATA_GUIDED =
        SynchedEntityData.defineId(MchBullet.class, EntityDataSerializers.BYTE);
    /** True for a {@code torpedo}-type round. Synced so the client (a) accepts the server's water-run path via
     *  {@link #lerpTo} — the run is server-authoritative like guidance — and (b) knows the round self-steers underwater. */
    private static final EntityDataAccessor<Boolean> DATA_TORPEDO =
        SynchedEntityData.defineId(MchBullet.class, EntityDataSerializers.BOOLEAN);
    /** In-water gravity ({@code MCH_WeaponInfo.gravityInWater}). The reference swaps {@code gravity}→{@code gravityInWater}
     *  the instant a round is submerged; synced so both sides integrate the same value while dead-reckoning. */
    private static final EntityDataAccessor<Float> DATA_GRAVITY_WATER =
        SynchedEntityData.defineId(MchBullet.class, EntityDataSerializers.FLOAT);
    /** True for a {@code delayFuse} round: its block ricochets are computed on the server, so the client accepts the
     *  server's path via {@link #lerpTo} instead of dead-reckoning straight through the block it bounced off. */
    private static final EntityDataAccessor<Boolean> DATA_RICOCHET =
        SynchedEntityData.defineId(MchBullet.class, EntityDataSerializers.BOOLEAN);
    /** The weapon's {@code Power}, for the client's water-entry splash (reference {@code updateSplash}); 0 = none. */
    private static final EntityDataAccessor<Integer> DATA_SPLASH_POWER =
        SynchedEntityData.defineId(MchBullet.class, EntityDataSerializers.INT);
    /** A bomb's per-tick motion multiplier while in water ({@code MCH_WeaponInfo.velocityInWater}); 1 = none. Synced so
     *  the client dead-reckons the same underwater slow-down. */
    private static final EntityDataAccessor<Float> DATA_VELOCITY_WATER =
        SynchedEntityData.defineId(MchBullet.class, EntityDataSerializers.FLOAT);

    /** Squared horizontal despawn distance from the shooter (5820², reference {@code checkValid}). */
    private static final double MAX_DIST_SQ = 3.38724E7;

    // Guided-round kinds (the DATA_GUIDED byte).
    public static final byte GUIDED_NONE = 0;
    /** AA: pursuit blend onto the locked target (reference {@code MCH_EntityAAMissile}). */
    public static final byte GUIDED_AA = 1;
    /** AT direct attack: hard-snap onto the locked target (reference {@code MCH_EntityATMissile} guidanceType 0). */
    public static final byte GUIDED_AT_DIRECT = 2;
    /** AT top attack: climb, level, dive (guidanceType 1, the AT weapon's mode 1). */
    public static final byte GUIDED_AT_TOP = 3;
    /** AS: steers at the ground point the operator aimed at (reference {@code MCH_EntityASMissile}). */
    public static final byte GUIDED_AS = 4;
    /** TV: steered toward the operator's current aim point (reference {@code MCH_EntityTvMissile}, mode 0). */
    public static final byte GUIDED_TV = 5;

    /** Reference {@code MCH_EntityTvMissile.onUpdate}: a TV missile dies 1200 blocks from its operator. */
    private static final double TV_MAX_DIST_SQ = 1440000.0;

    private float damage = 5.0F;
    private int life;
    private int maxLife = 200;
    private int age;                    // reference countOnUpdate (timeFuse clock + proximity age gate)
    private Entity shooter;             // server-side only; the operator who fired it (reference shootingEntity)
    private MCH_WeaponInfo weaponInfo;  // server-side only; source of all effect params
    private int piercing;               // live counter (blocks pierced remaining)
    private int delayFuseCounter;       // live countdown; 0 == unarmed (bounces off blocks)
    private int sprinkleTime;           // ticks until a cluster round splits (0 == not a cluster carrier)
    private boolean isBomblet;          // a sprinkled child (never re-splits)
    private int markerCountdown;        // marker rocket: ticks from plant to the airstrike + despawn (reference countDown)
    private Entity targetEntity;        // guided missile: the locked target (server-side only; resolved live each tick)
    private float guideSpeed;           // guided missile: cruise speed for the guidance blend (reference this.acceleration)
    private double torpedoAccel;        // torpedo: the live water-run speed, ramped toward accelerationInWater (reference this.acceleration)
    private Entity launcher;            // server-side only; the vehicle it left (reference shootingAircraft), or null
    private boolean hasGuidePoint;      // AS missile / guided torpedo: steering at a fixed world point
    private double guideX, guideY, guideZ; // that point (reference targetPosX/Y/Z)
    private net.minecraft.core.BlockPos guideBlock; // AS: the block the operator's aim ray hit (steering ends with it)

    public MchBullet(EntityType<? extends MchBullet> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    /**
     * Spawn a projectile carrying a weapon's real stats + effects. {@code wi} may be null (a plain tracer).
     *
     * <p>Every spawn takes the {@code shooter} (the operator, reference {@code shootingEntity}); the launcher (reference
     * {@code shootingAircraft}) is the vehicle the shooter rides at the moment of the shot, or the shooter itself when
     * a vehicle fires with no operator ({@link #launcherOf}).
     */
    public static MchBullet spawnWeapon(Level level, Vec3 pos, Vec3 dir, float speed, float accelerationFactor,
                                        float gravity, float damage, int maxLife, Entity shooter, String modelName,
                                        int color, MCH_WeaponInfo wi) {
        return create(level, pos, dir, speed, accelerationFactor, gravity, damage, maxLife, shooter,
            launcherOf(shooter), modelName, color, wi, false, false);
    }

    /** The vehicle a round fired by {@code shooter} leaves (reference {@code shootingAircraft}): the shooter's MCHeli
     *  mount when a rider fires, the shooter itself when it is the vehicle, otherwise none. */
    private static Entity launcherOf(Entity shooter) {
        if (shooter instanceof AbstractMchVehicle) {
            return shooter;
        }
        return shooter != null && shooter.getVehicle() instanceof AbstractMchVehicle v ? v : null;
    }

    /**
     * Spawn a GUIDED missile: a normal projectile that, after {@code rigidityTime} ticks, homes toward {@code target}
     * (server-authoritative; the client accepts the curved path via {@link #lerpTo}). {@code guidedKind} is
     * {@link #GUIDED_AA} (pursuit blend), {@link #GUIDED_AT_DIRECT} (hard-snap) or {@link #GUIDED_AT_TOP} (top attack).
     * Falls back to an unguided round if {@code target} is null.
     */
    public static MchBullet spawnGuided(Level level, Vec3 pos, Vec3 dir, float speed, float accelerationFactor,
                                        float gravity, float damage, int maxLife, Entity shooter, String modelName,
                                        int color, MCH_WeaponInfo wi, Entity target, byte guidedKind) {
        MchBullet b = create(level, pos, dir, speed, accelerationFactor, gravity, damage, maxLife, shooter,
            launcherOf(shooter), modelName, color, wi, false, target != null);
        if (target != null) {
            // Server-side fields; DATA_GUIDED syncs to the client within a tick (the missile is at the muzzle on a
            // straight boost until rigidityTime, so the brief pre-sync window has nothing to dead-reckon wrong).
            b.targetEntity = target;
            b.guideSpeed = speed;
            b.entityData.set(DATA_GUIDED, guidedKind);
        }
        return b;
    }

    /**
     * Spawn a missile that steers at a POINT rather than a locked entity: {@link #GUIDED_AS} at the ground point the
     * operator's aim ray hit ({@code hitBlock}, the block it hit), or {@link #GUIDED_TV} at the operator's live aim point
     * ({@code point} unused, may be null). The point is server-side only; the client takes the path via {@link #lerpTo}.
     */
    public static MchBullet spawnPointGuided(Level level, Vec3 pos, Vec3 dir, float speed, float accelerationFactor,
                                             float gravity, float damage, int maxLife, Entity shooter, String modelName,
                                             int color, MCH_WeaponInfo wi, Vec3 point,
                                             net.minecraft.core.BlockPos hitBlock, byte guidedKind) {
        MchBullet b = create(level, pos, dir, speed, accelerationFactor, gravity, damage, maxLife, shooter,
            launcherOf(shooter), modelName, color, wi, false, true);
        b.guideSpeed = speed;
        if (point != null) {
            b.setGuidePoint(point);
            b.guideBlock = hitBlock;
        }
        b.entityData.set(DATA_GUIDED, guidedKind);
        return b;
    }

    /**
     * Spawn a GUIDED torpedo (config {@code GuidedTorpedo = true}): launched like {@link #spawnTorpedo}, then, while
     * submerged, it runs straight at {@code point}, the water the operator aimed at (reference
     * {@code MCH_WeaponTorpedo.shotGuided} + {@code MCH_EntityTorpedo.onUpdateGuided}).
     */
    public static MchBullet spawnGuidedTorpedo(Level level, Vec3 pos, Vec3 velocity, float gravity, float damage,
                                               int maxLife, Entity shooter, String modelName, int color,
                                               MCH_WeaponInfo wi, Vec3 point) {
        MchBullet b = createWithVelocity(level, pos, velocity, 1.0F, gravity, damage, maxLife, shooter,
            launcherOf(shooter), modelName, color, wi, false, false);
        b.setGuidePoint(point);
        return b;
    }

    private void setGuidePoint(Vec3 p) {
        this.hasGuidePoint = true;
        this.guideX = p.x;
        this.guideY = p.y;
        this.guideZ = p.z;
    }

    /**
     * Spawn a DROPPED munition (bomb) — released at an EXPLICIT initial velocity (the aircraft's own motion, per the
     * reference {@code MCH_WeaponBomb.shot}) instead of a forward muzzle launch, so config gravity carries it down.
     * Reuses the whole projectile pipeline (gravity, explosion, cluster bomblets) the #21 airstrike already proves.
     */
    public static MchBullet spawnDropped(Level level, Vec3 pos, Vec3 velocity, float gravity, float damage, int maxLife,
                                         Entity shooter, String modelName, int color, MCH_WeaponInfo wi) {
        return createWithVelocity(level, pos, velocity, 1.0F, gravity, damage, maxLife, shooter, launcherOf(shooter),
            modelName, color, wi, false, false);
    }

    /**
     * Spawn a TORPEDO — launched at an EXPLICIT velocity (reference {@code MCH_WeaponTorpedo.shotNoGuided}:
     * {@code unit_dir × config Acceleration + the carrier's own motion}). The carrier term matters: several shipped
     * torpedoes ship {@code Acceleration = 0}, so the aircraft's velocity is their <i>entire</i> launch thrust — a plain
     * forward muzzle shot at {@code initialSpeed(0)} would leave them dead at the muzzle. {@code accelerationFactor} is 1
     * (torpedoes never get the bullet/rocket step-scaling); {@code createWithVelocity} seeds the water-run cruise speed
     * from this velocity's magnitude, matching the reference {@code this.acceleration}.
     */
    public static MchBullet spawnTorpedo(Level level, Vec3 pos, Vec3 velocity, float gravity, float damage, int maxLife,
                                         Entity shooter, String modelName, int color, MCH_WeaponInfo wi) {
        return createWithVelocity(level, pos, velocity, 1.0F, gravity, damage, maxLife, shooter, launcherOf(shooter),
            modelName, color, wi, false, false);
    }

    private static MchBullet create(Level level, Vec3 pos, Vec3 dir, float speed, float accelerationFactor,
                                    float gravity, float damage, int maxLife, Entity shooter, Entity launcher,
                                    String modelName, int color, MCH_WeaponInfo wi, boolean isBomblet,
                                    boolean steered) {
        Vec3 v = dir.lengthSqr() > 1.0e-9 ? dir.normalize().scale(speed) : new Vec3(0.0, 0.0, speed);
        return createWithVelocity(level, pos, v, accelerationFactor, gravity, damage, maxLife, shooter, launcher,
            modelName, color, wi, isBomblet, steered);
    }

    /** Whether a round's path is the server's, which the client takes through {@link #lerpTo} (a guided missile, a
     *  torpedo, a ricocheting delay-fuse round): it is built as {@code STEERED_BULLET}, which updates every tick. */
    private static boolean steeredPath(MCH_WeaponInfo wi, boolean steered) {
        return steered || wi != null && ("torpedo".equalsIgnoreCase(wi.type) || wi.delayFuse > 0);
    }

    /** The projectile builder, taking the initial velocity DIRECTLY (a forward muzzle shot via {@link #create}, or an
     *  inherited-velocity drop via {@link #spawnDropped}). All effect setup (trail/bomblet/marker/model/gravity) is here.
     *  {@code launcher} is passed explicitly so a child round inherits its parent's instead of re-deriving one;
     *  {@code steered} marks a guided round (the entity type is fixed at construction, {@link #steeredPath}). */
    private static MchBullet createWithVelocity(Level level, Vec3 pos, Vec3 v, float accelerationFactor,
                                    float gravity, float damage, int maxLife, Entity shooter, Entity launcher,
                                    String modelName, int color, MCH_WeaponInfo wi, boolean isBomblet,
                                    boolean steered) {
        MchBullet b = new MchBullet(steeredPath(wi, steered)
            ? mcheli.dependent.registry.MchRegistries.STEERED_BULLET.get()
            : mcheli.dependent.registry.MchRegistries.BULLET.get(), level);
        // Reference nudges the round half a step past the muzzle so it clears the barrel / registers point-blank.
        Vec3 start = pos.add(v.scale(0.5));
        b.setPos(start.x, start.y, start.z);
        b.setDeltaMovement(v);
        b.damage = damage;
        b.maxLife = maxLife;
        b.shooter = shooter;
        b.launcher = launcher;
        b.weaponInfo = wi;
        b.isBomblet = isBomblet;
        if (wi != null) {
            b.piercing = wi.piercing;
            if (!isBomblet && wi.bomblet > 0) {
                b.sprinkleTime = wi.bombletSTime;
            }
            // Trail (rocket/missile types only, faithful) — appearance from config: which particle + its size.
            if (!isBomblet && MCH_WeaponBallistics.isTrailingType(wi.type) && !wi.disableSmoke
                && wi.trajectoryParticleName != null && !wi.trajectoryParticleName.isEmpty()) {
                b.entityData.set(DATA_TRAIL_KIND, wi.trajectoryParticleName.equalsIgnoreCase("flame") ? 2 : 1);
                b.entityData.set(DATA_TRAIL_SIZE,
                    0.1F * MCH_WeaponBallistics.trailSizeMultiplier(wi.type) * wi.smokeSize);
                b.entityData.set(DATA_TRAIL_START, wi.trajectoryParticleStartTick);
            }
        }
        if (wi != null && !isBomblet && "mkrocket".equalsIgnoreCase(wi.type)) {
            b.entityData.set(DATA_MARKER, (byte) 1); // a marker rocket: flies, plants a spotting marker, then calls a strike
        }
        // Torpedo: self-steers underwater (a level water-run, or a guided run at its aim point; see tickTorpedo). Seed
        // the live run speed from the launch-motion magnitude (reference sets the entity's acceleration to it) so it
        // ramps toward accelerationInWater from there.
        if (wi != null && "torpedo".equalsIgnoreCase(wi.type)) {
            b.entityData.set(DATA_TORPEDO, true);
            b.torpedoAccel = v.length();
        }
        b.entityData.set(DATA_MODEL, modelName == null ? "" : modelName);
        b.entityData.set(DATA_COLOR, color);
        b.entityData.set(DATA_GRAVITY, gravity);
        // In-water gravity: from config for a real weapon; mirror the air gravity for a wi-less test tracer (unchanged).
        b.entityData.set(DATA_GRAVITY_WATER, wi != null ? wi.gravityInWater : gravity);
        b.entityData.set(DATA_ACCEL_FACTOR, accelerationFactor);
        if (wi != null) {
            b.entityData.set(DATA_RICOCHET, wi.delayFuse > 0);
            b.entityData.set(DATA_SPLASH_POWER, Math.max(0, wi.power));
            if (b.isBombType()) {
                b.entityData.set(DATA_VELOCITY_WATER, wi.velocityInWater); // reference MCH_EntityBomb/DispensedItem
            }
        }
        double horiz = Math.sqrt(v.x * v.x + v.z * v.z);
        b.setYRot((float) Math.toDegrees(Math.atan2(-v.x, v.z)));
        b.setXRot((float) Math.toDegrees(Math.atan2(-v.y, horiz)));
        level.addFreshEntity(b);
        return b;
    }

    /** Back-compat straight-tracer spawn (projectile self-test): white default-model bullet, no effects. */
    public static MchBullet spawn(Level level, Vec3 pos, Vec3 dir, float speed, float damage, float gravity,
                                  int maxLife, Entity shooter) {
        return spawnWeapon(level, pos, dir, speed, 1.0F, -Math.abs(gravity), damage, maxLife, shooter, "bullet",
            0xFFFFFFFF, null);
    }

    public String bulletModelName() { return this.entityData.get(DATA_MODEL); }
    public int bulletColor() { return this.entityData.get(DATA_COLOR); }

    /** SERVER: whether this is a child round a cluster carrier emitted when it split ({@link #sprinkleBomblets}). */
    public boolean isBomblet() { return this.isBomblet; }

    // ---- flare-decoy hooks (reference MCH_MissileDetector.destroyMissile) ----

    /** Whether this is a guided round (any {@link #DATA_GUIDED} kind; only AA/AT ones home on an entity). */
    public boolean isGuided() { return this.entityData.get(DATA_GUIDED) != GUIDED_NONE; }

    /** Whether {@code e} is on the firing side: the shooter, the vehicle it fired from, or anyone riding either
     *  (reference {@code canBeCollidedEntity}: never the shooting entity, the shooting aircraft or its mounted crew). */
    private boolean isOwnSide(Entity e) {
        if (this.shooter != null
            && (e == this.shooter || this.shooter.getPassengers().contains(e) || e.getVehicle() == this.shooter)) {
            return true;
        }
        return this.launcher != null
            && (e == this.launcher || this.launcher.getPassengers().contains(e) || e.getVehicle() == this.launcher);
    }

    /** Tell the operating player one of their rounds hit something (reference {@code notifyHitBullet}: the
     *  {@code MCH_PacketNotifyHitBullet} hit marker). */
    private void notifyHit() {
        mcheli.dependent.control.ClientboundHitMarkPayload.sendTo(this.shooter);
    }

    /** Whether this guided round is homing on {@code ac} or one of its passengers — the flare-decoy match. */
    public boolean isHomingOn(Entity ac) {
        return this.targetEntity != null && ac != null
            && (this.targetEntity == ac || ac.getPassengers().contains(this.targetEntity));
    }

    /** Countermeasure decoy: drop the target so {@link #tickGuidance} discards this round next tick (reference nulls
     *  {@code targetEntity}). */
    public void decoyClear() { this.targetEntity = null; }

    /** Reference {@code canBeCollidedEntity}: what this round can strike (server side). */
    private boolean canHit(Entity e) {
        if (e == this || e.isSpectator() || !e.isAlive()) {
            return false;
        }
        if (e instanceof MchBullet other) {
            // Another side's round can be shot down (it detonates when a thrown hit hurts it, see hurt()); rounds of
            // the same launcher or the same operator pass through each other (reference :704-710: same
            // shootingAircraft, then same shootingEntity), so a gunner's burst never meets the pilot's missile. As in
            // the reference's W_Entity.isEqual (a null entity is id -1 on both sides), two null owners count as the same
            // side: ownerless rounds (e.g. the sibling bomblets of an ownerless cluster) pass through each other.
            return !(other.launcher == this.launcher || other.shooter == this.shooter);
        }
        if (!e.isPickable() || MchServerRules.ignoresBulletHit(e)) {
            return false; // ignoresBulletHit: the server's IgnoreBulletHit list (reference :735-739)
        }
        return !isOwnSide(e);
    }

    @Override
    public void tick() {
        super.tick();
        this.age++;

        if (this.entityData.get(DATA_MARKER) == 2) { // planted marker: pulse the beacon / count down to the airstrike
            tickMarked();
            return;
        }

        Vec3 motion = this.getDeltaMovement();
        // Reference swaps gravity->gravityInWater the moment a round is submerged (MCH_EntityBaseBullet.onUpdate). Both
        // are synced so the client dead-reckons the same value. Most weapons ship gravityInWater 0 (a round loses fall
        // in water); bombs ship it negative (they keep sinking); a torpedo then self-steers via tickTorpedo().
        boolean submerged = inWater();
        float gravity = submerged ? this.entityData.get(DATA_GRAVITY_WATER) : this.entityData.get(DATA_GRAVITY);
        float accFactor = this.entityData.get(DATA_ACCEL_FACTOR);

        if (!this.level().isClientSide) {
            // Cluster split: after bombletSTime ticks the carrier emits its children and dies.
            if (this.sprinkleTime > 0 && --this.sprinkleTime == 0) {
                sprinkleBomblets();
                this.discard();
                return;
            }
            // delayFuse countdown -> timeout detonation (the reference onUpdateTimeout rules, not the impact rules).
            if (this.delayFuseCounter > 0 && --this.delayFuseCounter == 0) {
                timeout();
                return;
            }
            // Despawn if the shooter died or the round has flown too far (horizontal only).
            if (!checkValid()) {
                this.discard();
                return;
            }
            // timeFuse: detonate once the age exceeds the threshold.
            if (this.weaponInfo != null && this.weaponInfo.timeFuse > 0 && this.age > this.weaponInfo.timeFuse) {
                timeout();
                return;
            }
            // explosionAltitude airburst: impact on the spot when a block is within N blocks below (a 3x3 column).
            if (this.weaponInfo != null && this.weaponInfo.explosionAltitude > 0
                && groundWithin(this.weaponInfo.explosionAltitude)) {
                onImpact(this.position(), null);
                if (this.isRemoved()) {
                    return;
                }
            }
        }

        // Gravity is folded into the motion BEFORE the collision raytrace (reference order).
        if (gravity != 0.0F) {
            motion = new Vec3(motion.x, motion.y + gravity, motion.z);
        }
        // Bomb-family horizontal drag (reference MCH_EntityBomb.onUpdate: motionX/Z *= 0.999 each tick) so a bomb bleeds
        // its inherited carrier speed and doesn't fly long. Only bombs — guns/rockets/missiles keep their speed.
        if (isBombType()) {
            motion = new Vec3(motion.x * 0.999, motion.y, motion.z * 0.999);
        }

        Vec3 from = this.position();
        Vec3 step = accFactor != 1.0F ? motion.scale(accFactor) : motion;
        Vec3 to = from.add(step);

        if (!this.level().isClientSide) {
            BlockHitResult block = clipShootingThrough(from, to);
            boolean blockHit = block.getType() != HitResult.Type.MISS;
            Vec3 segEnd = blockHit ? block.getLocation() : to;
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                this.level(), this, from, segEnd, this.getBoundingBox().expandTowards(step).inflate(1.0), this::canHit);
            // Augment: a round can strike an extra part hitbox that PROTRUDES beyond a vehicle's vanilla selection AABB
            // (wingtip / tail / hull overhang). ProjectileUtil only clips that AABB, so also query each nearby vehicle's
            // core + part boxes and take whichever hit is nearest the muzzle (the reference feeds the extra boxes into
            // the bullet's own intercept, so protruding weak zones stay hittable).
            double bestSq = entityHit != null ? from.distanceToSqr(entityHit.getLocation()) : Double.MAX_VALUE;
            for (AbstractMchVehicle v : this.level().getEntitiesOfClass(
                    AbstractMchVehicle.class, new AABB(from, segEnd).inflate(8.0), this::canHit)) {
                Vec3 h = v.clipParts(from, segEnd);
                if (h != null) {
                    double d = from.distanceToSqr(h);
                    if (d < bestSq) {
                        bestSq = d;
                        entityHit = new EntityHitResult(v, h);
                    }
                }
            }

            if (this.entityData.get(DATA_MARKER) == 1) {
                entityHit = null; // a marker rocket flies through entities; it only reacts to blocks (reference onImpact)
            }

            if (this.weaponInfo != null && this.weaponInfo.delayFuse > 0) {
                // delayFuse rounds IGNORE entities entirely — they only ricochet off blocks and self-detonate when the
                // armed countdown reaches 0 (reference: the entity search lives inside the non-delayFuse else branch).
                if (blockHit) {
                    motion = boundBullet(block.getDirection(), motion);
                    if (this.delayFuseCounter == 0) {
                        this.delayFuseCounter = this.weaponInfo.delayFuse;
                    }
                    this.setDeltaMovement(motion);
                    step = accFactor != 1.0F ? motion.scale(accFactor) : motion;
                    to = from.add(step);
                } else if (++this.life > this.maxLife) {
                    this.discard();
                    return;
                }
            } else if (entityHit != null) {
                // Per-part weak-point/armor: stash the struck hitbox's factor on the vehicle just before hurt(), the
                // port's stand-in for the reference's calculateIntercept side effect (ProjectileUtil ignores the extra
                // boxes). The weapon's own by-class damageFactor is separate and already folded into onImpactEntity.
                Entity real = entityHit.getEntity() instanceof PartEntity<?> part
                    ? part.getParent() : entityHit.getEntity();
                if (real instanceof AbstractMchVehicle v) {
                    // Query the FULL trajectory [from, to] (NOT the selection-AABB entry point) so interior armor/weak
                    // zones resolve — the reference tests the whole bullet segment against every box, nearest wins.
                    v.setLastBBDamageFactor(v.boundingBoxDamageFactorAt(from, to));
                }
                onImpact(entityHit.getLocation(), null, entityHit.getEntity());
                if (this.isRemoved()) {
                    return;
                }
            } else if (blockHit) {
                if (this.entityData.get(DATA_MARKER) == 1) {
                    plantMarker(block); // stops here: it becomes a planted marker instead of detonating
                    return;
                }
                spawnBlockImpact(block);
                onImpact(block.getLocation(), block.getBlockPos(), null);
                if (this.isRemoved()) {
                    return;
                }
                // A piercing round that survived passes through: fall through to the move.
            } else if (++this.life > this.maxLife) {
                this.discard();
                return;
            }
        }

        // Advance, keeping the post-gravity (or reflected) motion for the next tick + render orientation.
        this.setPos(to.x, to.y, to.z);
        this.setDeltaMovement(motion);
        // A bomb or dispenser round slows by its velocityInWater each tick it ends in water (reference MCH_EntityBomb /
        // MCH_EntityDispensedItem.onUpdate, after the move). The factor is synced, so both sides slow it alike.
        float velocityInWater = this.entityData.get(DATA_VELOCITY_WATER);
        if (velocityInWater != 1.0F && inWater()) {
            motion = motion.scale(velocityInWater);
            this.setDeltaMovement(motion);
        }
        double horiz = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        if (motion.lengthSqr() > 1.0e-9) {
            this.setYRot((float) Math.toDegrees(Math.atan2(-motion.x, motion.z)));
            this.setXRot((float) Math.toDegrees(Math.atan2(-motion.y, horiz)));
        }

        // Guided-missile homing (server-authoritative): after the move, steer motion toward the locked target for the
        // NEXT tick + proximity-detonate / range-die (reference MCH_EntityAAMissile/ATMissile.onUpdate). This runs
        // AFTER the normal collision above, so a missile that already flew into terrain/an entity has detonated.
        if (!this.level().isClientSide && this.entityData.get(DATA_GUIDED) != GUIDED_NONE) {
            tickGuidance();
            if (this.isRemoved()) {
                return;
            }
        }

        // Torpedo water-run (server-authoritative, reference MCH_EntityTorpedo.onUpdateNoGuided): once submerged, level
        // the descent, ramp toward the cruise speed, and re-normalize — sets the motion for the NEXT tick. The client
        // takes the resulting path via lerpTo (like a guided round), so this runs server-side only. Gated on the
        // POST-move water state (the reference runs onUpdateNoGuided after the move, unlike the pre-move gravity swap).
        if (!this.level().isClientSide && this.entityData.get(DATA_TORPEDO) && inWater()) {
            tickTorpedo();
        }

        // Client cosmetics: the water-entry splash, the config-driven smoke/flame trail (rocket/missile) + in-water
        // bubbles.
        if (this.level().isClientSide) {
            updateSplash(from, to);
            emitTrail(from, to);
            if (this.isInWater()) {
                this.level().addParticle(ParticleTypes.BUBBLE, this.getX(), this.getY(), this.getZ(),
                    -motion.x * 0.2, -motion.y * 0.2, -motion.z * 0.2);
            }
        }

        // Proximity fuses, checked at the very end of the tick after the move. Only gun rounds (reference
        // MCH_EntityBullet) and bombs (MCH_EntityBomb, a different rule) have one. Guided rounds are EXCLUDED — they
        // run their own target-relative proximity in tickGuidance() (different semantics), so this would double.
        if (!this.level().isClientSide && this.weaponInfo != null && this.weaponInfo.proximityFuseDist > 0.1F
            && !this.isRemoved() && this.entityData.get(DATA_GUIDED) == GUIDED_NONE) {
            if (isGunType()) {
                if (this.weaponInfo.explosion > 0 && this.age > 1) {
                    proximityCheck();
                }
            } else if ("bomb".equalsIgnoreCase(this.weaponInfo.type)) {
                bombProximityCheck();
            }
        }
    }

    /**
     * Server-side guided-missile steering (reference {@code MCH_EntityAAMissile}/{@code MCH_EntityATMissile}
     * {@code onUpdate}): dies if the target/shooter is gone or the target is beyond the per-type range; before
     * {@code rigidityTime} it flies straight (the launch boost); after, it proximity-detonates near the target or
     * steers toward it (AA = pure-pursuit blend, AT direct = hard-snap, AT top attack = climb/level/dive blend). The
     * point-guided AS and TV kinds branch off first. Sets the motion for the NEXT tick.
     */
    private void tickGuidance() {
        byte g = this.entityData.get(DATA_GUIDED);
        if (g == GUIDED_AS) {
            tickAsMissile();
            return;
        }
        if (g == GUIDED_TV) {
            tickTvMissile();
            return;
        }
        // Reference guidance guard (AA/AT onUpdate): a shooter REFERENCE and a live target. It does not ask whether the
        // shooter is alive: the round's despawn on its shooter's death is checkValid(), which runs for every round,
        // guided ones included (reference onUpdate -> checkValid). The launcher may be gone: a missile keeps homing
        // after the aircraft that fired it is destroyed.
        if (this.targetEntity == null || !this.targetEntity.isAlive() || this.shooter == null) {
            this.discard();
            return;
        }
        double dx = this.getX() - this.targetEntity.getX();
        double dy = this.getY() - this.targetEntity.getY();
        double dz = this.getZ() - this.targetEntity.getZ();
        double dSq = dx * dx + dy * dy + dz * dz;
        double deathSq = g == GUIDED_AA ? 3422500.0 : 2250000.0; // AA 1850² / AT 1500² (reference)
        if (dSq > deathSq) {
            this.discard();
            return;
        }
        // Proximity fuse: reference compares the SQUARED distance to the LINEAR proximityFuseDist (a faithful quirk —
        // so the real detonation radius is sqrt(proximityFuseDist)).
        float proxDist = this.weaponInfo != null ? this.weaponInfo.proximityFuseDist : 0.0F;
        boolean proximityHit = proxDist >= 0.1F && dSq < proxDist;
        int rigidity = this.weaponInfo != null ? this.weaponInfo.rigidityTime : 0;

        // FIDELITY: the reference gates AA's proximity AND steering behind rigidityTime (MCH_EntityAAMissile), but the
        // AT missile checks its proximity fuse EVERY tick (only AT steering is rigidity-gated, MCH_EntityATMissile) —
        // so an AT round detonates on a target caught inside the fuse even during its launch boost.
        if (g == GUIDED_AT_TOP) {
            if (proximityHit) {
                detonateOnTarget(g);
                return;
            }
            if (this.age <= rigidity) {
                return; // launch boost
            }
            // Reference guidanceType 1: climb to 150 above the SHOOTER over the target, level off, dive; arming the dive
            // raises the round's power by 20 % and its explosion by one (MCH_EntityATMissile:56-70).
            if (MCH_MissileGuidance.isTopAttackDiveTick(this.age, rigidity)) {
                this.damage = (int) (this.damage * 1.2F);
                if (this.weaponInfo != null && this.weaponInfo.explosion > 0) {
                    this.weaponInfo = this.weaponInfo.shotVariant(this.weaponInfo.explosion + 1, this.weaponInfo.piercing);
                }
            }
            double[] aim = MCH_MissileGuidance.topAttackAim(this.age, rigidity, this.targetEntity.getX(),
                this.targetEntity.getY(), this.targetEntity.getZ(), this.shooter.getY());
            Vec3 m = this.getDeltaMovement();
            applyGuidedMotion(MCH_MissileGuidance.guide(m.x, m.y, m.z, this.getX(), this.getY(), this.getZ(),
                aim[0], aim[1], aim[2], this.guideSpeed));
        } else if (g == GUIDED_AT_DIRECT) {
            if (proximityHit) {
                detonateOnTarget(g);
                return;
            }
            if (this.age <= rigidity) {
                return; // launch boost: fly straight before steering engages
            }
            int startTick = this.weaponInfo != null ? this.weaponInfo.trajectoryParticleStartTick : 0;
            float af = this.age < rigidity + startTick ? 0.5F : 1.0F;
            applyGuidedMotion(MCH_MissileGuidance.hardSnap(this.getX(), this.getY(), this.getZ(),
                this.targetEntity.getX(), this.targetEntity.getY(), this.targetEntity.getZ(), this.guideSpeed, af));
        } else {
            if (this.age <= rigidity) {
                return; // AA: proximity + steering both engage only after the boost (reference)
            }
            if (proximityHit) {
                detonateOnTarget(g);
                return;
            }
            Vec3 m = this.getDeltaMovement();
            applyGuidedMotion(MCH_MissileGuidance.guide(m.x, m.y, m.z, this.getX(), this.getY(), this.getZ(),
                this.targetEntity.getX(), this.targetEntity.getY(), this.targetEntity.getZ(), this.guideSpeed));
        }
    }

    /**
     * AS missile steering (reference {@code MCH_EntityASMissile.onUpdate}, non-bomblet rounds): while the aimed block
     * still stands, detonate (or release the cluster) inside {@code proximityFuseDist} of the point, else steer at it
     * ({@link MCH_MissileGuidance#asMissile}). A destroyed block ends the steering; the round flies on.
     *
     * <p>Deliberate divergence: the reference tests the block at the integer-cast hit POINT, which is the air block in
     * front of three of the six faces (and off by one at negative coordinates), so it stopped steering at those; the
     * port tests the block the aim ray actually hit.
     */
    private void tickAsMissile() {
        if (!this.hasGuidePoint || this.guideBlock == null || this.weaponInfo == null) {
            return;
        }
        BlockState bs = this.level().getBlockState(this.guideBlock);
        if (bs.isAir() || !bs.getFluidState().isEmpty()) {
            return; // reference block.isCollidable(): the target is gone
        }
        double dx = this.guideX - this.getX();
        double dy = this.guideY - this.getY();
        double dz = this.guideZ - this.getZ();
        if (Math.sqrt(dx * dx + dy * dy + dz * dz) < this.weaponInfo.proximityFuseDist) {
            if (this.weaponInfo.bomblet > 0) {
                sprinkleBomblets();
                this.discard();
            } else {
                detonateInPlace();
            }
            return;
        }
        Vec3 m = this.getDeltaMovement();
        boolean ballistic = this.entityData.get(DATA_GRAVITY) != 0.0F;
        applyGuidedMotion(MCH_MissileGuidance.asMissile(m.x, m.y, m.z, this.getX(), this.getY(), this.getZ(),
            this.guideX, this.guideY, this.guideZ, this.guideSpeed, ballistic, this.age));
    }

    /**
     * TV missile steering (reference {@code MCH_EntityTvMissile.onUpdate}/{@code onUpdateMotion}): dies without an
     * operator or 1200 blocks from them; while the operator still rides the launcher and this is the launcher's latest
     * TV missile, it is steered each tick at its own speed with no turn limit. The reference sets the motion to the
     * operator's LOOK ({@code setMotion(look)}), which is right when the operator looks out of the missile-eye camera.
     * The port has no missile camera, so the operator aims from the vehicle and a look-parallel missile would miss by
     * the launch offset: when the operator's sight line meets a block a little beyond the missile, the missile turns
     * at that point instead; otherwise (open sky, or terrain still far ahead) it takes the reference's look motion.
     * The sight ray stops where the missile is plus {@link #TV_AIM_MARGIN} and at the first chunk that is not fully
     * loaded ({@link #loadedSightHit}), so steering never loads or generates terrain, nor waits for it.
     */
    private void tickTvMissile() {
        Entity op = this.shooter;
        if (op == null || this.distanceToSqr(op) > TV_MAX_DIST_SQ) {
            this.discard();
            return;
        }
        if (!op.isAlive() || !(this.launcher instanceof AbstractMchVehicle v) || op.getVehicle() != v
            || !v.isActiveTvMissile(this)) {
            return; // a superseded or orphaned TV missile flies on straight
        }
        Vec3 eye = op.getEyePosition();
        // The BODY rotation, as the fire path aims: a passenger's server-side yHeadRot (behind getViewVector) is stale.
        Vec3 look = Vec3.directionFromRotation(op.getXRot(), op.getYRot());
        double reach = Math.min(Math.sqrt(TV_MAX_DIST_SQ), eye.distanceTo(this.position()) + TV_AIM_MARGIN);
        Vec3 aim = loadedSightHit(eye, look, reach, op);
        if (aim != null) {
            applyGuidedMotion(MCH_MissileGuidance.hardSnap(this.getX(), this.getY(), this.getZ(),
                aim.x, aim.y, aim.z, this.guideSpeed, 1.0F));
        } else {
            applyGuidedMotion(new MCH_MissileGuidance.Motion(
                look.x * this.guideSpeed, look.y * this.guideSpeed, look.z * this.guideSpeed));
        }
    }

    /** How far past the TV missile the operator's sight is followed for an aim point (blocks). */
    private static final double TV_AIM_MARGIN = 64.0;

    /**
     * The first block along {@code dir} from {@code from} within {@code maxDist} blocks (fluids ignored), or null. The
     * ray is clipped in legs of at most a chunk, and stops at the first leg whose chunks are not all fully loaded
     * ({@link #chunkReady}): {@code Level.clip} reads every block through {@code getBlockState}, which on the server
     * waits on the main thread for a chunk that is still loading, and loads (or generates) a missing one.
     */
    private Vec3 loadedSightHit(Vec3 from, Vec3 dir, double maxDist, Entity op) {
        Vec3 start = from;
        for (double done = 0.0; done < maxDist; done += 16.0) {
            Vec3 end = start.add(dir.scale(Math.min(16.0, maxDist - done)));
            int ax = Mth.floor(start.x) >> 4;
            int az = Mth.floor(start.z) >> 4;
            int bx = Mth.floor(end.x) >> 4;
            int bz = Mth.floor(end.z) >> 4;
            // A leg of <= 16 blocks lies within the 2x2 chunks spanned by its ends.
            if (!chunkReady(ax, az) || !chunkReady(bx, bz) || !chunkReady(ax, bz) || !chunkReady(bx, az)) {
                return null;
            }
            BlockHitResult hit = this.level().clip(
                new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, op));
            if (hit.getType() != HitResult.Type.MISS) {
                return hit.getLocation();
            }
            start = end;
        }
        return null;
    }

    /**
     * Whether chunk ({@code cx}, {@code cz}) is present and fully loaded, so reading its blocks never waits. On the
     * server that is {@code getChunkNow}: {@code Level.hasChunk} there only checks that the chunk's ticket level reaches
     * FULL, which a chunk still loading or generating inside the view distance passes too.
     */
    private boolean chunkReady(int cx, int cz) {
        return this.level() instanceof ServerLevel sl
            ? sl.getChunkSource().getChunkNow(cx, cz) != null
            : this.level().hasChunk(cx, cz);
    }

    /** Detonate a guided round on its target — reference explodes at the TARGET position ({@code mop.hitVec}), not the
     *  missile/target midpoint: AA also delivers a direct hit on the target, AT blasts only. */
    private void detonateOnTarget(byte g) {
        Vec3 tpos = this.targetEntity.position();
        onImpact(tpos, g == GUIDED_AA ? this.targetEntity : null);
    }

    private void applyGuidedMotion(MCH_MissileGuidance.Motion nm) {
        this.setDeltaMovement(nm.x, nm.y, nm.z);
        double h = Math.sqrt(nm.x * nm.x + nm.z * nm.z);
        if (nm.x * nm.x + nm.y * nm.y + nm.z * nm.z > 1.0e-9) {
            this.setYRot((float) Math.toDegrees(Math.atan2(-nm.x, nm.z)));
            this.setXRot((float) Math.toDegrees(Math.atan2(-nm.y, h)));
        }
    }

    /**
     * Server-side torpedo water-run (reference {@code MCH_EntityTorpedo.onUpdateNoGuided}): while submerged, damp the
     * vertical motion ({@code motionY *= 0.8}) so the run levels off, ramp the cruise speed toward
     * {@code accelerationInWater} (±0.1/tick into a +0.2 dead-band), then re-normalize the whole motion vector to that
     * speed — a torpedo cruises at a constant underwater speed regardless of how fast it was launched. A GUIDED torpedo
     * ({@code onUpdateGuided}) instead points that speed straight at its aim point, without the damping. Sets the motion
     * for the NEXT tick + the nose orientation.
     */
    private void tickTorpedo() {
        double accInWater = this.weaponInfo != null ? this.weaponInfo.accelerationInWater : 1.0;
        this.torpedoAccel = MCH_MissileGuidance.torpedoSpeed(this.torpedoAccel, accInWater);
        if (this.hasGuidePoint) {
            applyGuidedMotion(MCH_MissileGuidance.hardSnap(this.getX(), this.getY(), this.getZ(),
                this.guideX, this.guideY, this.guideZ, this.torpedoAccel, 1.0F));
            return;
        }
        Vec3 m = this.getDeltaMovement();
        double mx = m.x;
        double my = m.y * 0.8; // level the descent (reference motionY *= 0.8F)
        double mz = m.z;
        double d = Math.sqrt(mx * mx + my * my + mz * mz);
        if (d > 1.0e-6) {
            double s = this.torpedoAccel / d;
            mx *= s;
            my *= s;
            mz *= s;
        }
        this.setDeltaMovement(mx, my, mz);
        double horiz = Math.sqrt(mx * mx + mz * mz);
        if (mx * mx + my * my + mz * mz > 1.0e-9) {
            this.setYRot((float) Math.toDegrees(Math.atan2(-mx, mz)));
            this.setXRot((float) Math.toDegrees(Math.atan2(-my, horiz)));
        }
    }

    /** Whether this round is currently in water — a direct fluid-state probe at the round's block (the reference
     *  {@code isInWater()} is a block check). More reliable than vanilla's AABB water state for a {@code noPhysics}
     *  projectile, and evaluated identically on both sides so the gravity swap stays in sync. */
    private boolean inWater() {
        return this.level().getFluidState(this.blockPosition())
            .is(net.minecraft.tags.FluidTags.WATER);
    }

    // ---- impact / detonation ----

    /** {@link #onImpact(Vec3, BlockPos, Entity)} at {@code pos}: an entity hit, or else a tile hit on the block holding
     *  {@code pos} (the reference fabricates that tile hit for its airburst, proximity and missile-fuse impacts). */
    private void onImpact(Vec3 pos, Entity entity) {
        onImpact(pos, entity == null ? BlockPos.containing(pos) : null, entity);
    }

    /**
     * Impact resolution mirroring {@code MCH_EntityBaseBullet.onImpact} (server only): a direct entity hit deals the
     * power damage and zeroes piercing; a piercing round survives a block/airburst/proximity impact with a small burst;
     * otherwise the round makes its impact blast ({@link MCH_Detonation#onImpact}) and dies. A dispenser round uses its
     * item instead, on any impact and without a direct hit (reference {@code MCH_EntityDispensedItem.onImpact}).
     *
     * @param block the struck block of a tile hit, or null for an entity hit
     */
    private void onImpact(Vec3 pos, BlockPos block, Entity entity) {
        if (!(this.level() instanceof ServerLevel sl)) {
            this.discard();
            return;
        }
        if (isDispenserType()) {
            dispenseAt(sl, pos);
            this.discard();
            return;
        }
        if (entity != null) {
            onImpactEntity(entity);
            this.piercing = 0;
        }
        MCH_WeaponInfo wi = this.weaponInfo;
        if (this.piercing > 0) {
            this.piercing--;
            if (wi != null && wi.explosion > 0) {
                // Reference newExplosion(hit, 1, 1, false): the normal blast at power 1 — the weapon's flaming, block
                // damage and damage factor all apply.
                MchExplosion.explode(sl, pos, 1, 1, wi.flaming, true, 0, wi, this.launcher, this.shooter, false,
                    blastSound(), true);
            }
            return; // keep flying
        }
        if (wi != null) {
            boolean blockInWater = block != null && isBlockInWater(block);
            explode(MCH_Detonation.onImpact(wi.explosion, wi.explosionInWater, wi.explosionBlock, wi.isFAE,
                entity != null, inWater(), blockInWater), pos, block);
        }
        this.discard();
    }

    /**
     * Reference {@code onImpact(new MovingObjectPosition(this))} (the AS missile's proximity detonation): an ENTITY hit
     * on the round itself, so piercing is zeroed and the round always makes its full blast, by the entity-hit rules,
     * where it is, then dies. Not reproduced: the reference's direct hit on itself, whose 'thrown' damage set off a
     * second, tile-rule blast through {@code attackEntityFrom} and sent a hit marker for every detonation.
     */
    private void detonateInPlace() {
        this.piercing = 0;
        MCH_WeaponInfo wi = this.weaponInfo;
        if (wi != null) {
            explode(MCH_Detonation.onImpact(wi.explosion, wi.explosionInWater, wi.explosionBlock, wi.isFAE, true,
                inWater(), false), this.position(), null);
        }
        this.discard();
    }

    /** A fuse ran out in flight ({@code delayFuse}, {@code timeFuse}; reference {@code onUpdateTimeout}): the timeout
     *  blast ({@link MCH_Detonation#onTimeout}), using the same water probe as the gravity swap, then despawn. */
    private void timeout() {
        MCH_WeaponInfo wi = this.weaponInfo;
        if (wi != null) {
            explode(MCH_Detonation.onTimeout(wi.explosion, wi.explosionInWater, wi.explosionBlock, inWater()),
                this.position(), null);
        }
        this.discard();
    }

    /** Make the chosen blast — reference {@code newExplosion}, {@code newExplosionInWater}, {@code newFAExplosion} or
     *  {@code playExplosionSound}: the launcher and its crew are spared, the shooter is credited and, when the blast
     *  reaches anything, gets the hit marker (every one of them notifies on {@code result.hitEntity}). */
    private void explode(MCH_Detonation.Blast blast, Vec3 hit, BlockPos block) {
        if (!(this.level() instanceof ServerLevel sl) || blast.kind() == MCH_Detonation.Kind.NONE) {
            return;
        }
        MCH_WeaponInfo wi = this.weaponInfo;
        Vec3 at = switch (blast.at()) {
            case HIT -> hit;
            case SELF -> this.position();
            // The reference passes the struck block's integer coordinates (its corner) for an in-water block hit.
            case BLOCK -> block != null ? Vec3.atLowerCornerOf(block) : hit;
        };
        switch (blast.kind()) {
            case SOUND -> MchExplosion.playExplosionSound(sl, at);
            // FAE: no block destruction, strong entity ignition, no damage factor, always heard.
            case FAE -> MchExplosion.explode(sl, at, blast.power(), blast.sizeBlock(), wi.flaming, false, 15, null,
                this.launcher, this.shooter, false, true, true);
            case NORMAL, WATER -> MchExplosion.explode(sl, at, blast.power(), blast.sizeBlock(), wi.flaming, true, 0,
                wi, this.launcher, this.shooter, blast.kind() == MCH_Detonation.Kind.WATER, blastSound(), true);
            default -> { }
        }
    }

    /** Reference {@code newExplosion}: a bomblet's blast is heard one time in three, any other round's always. */
    private boolean blastSound() {
        return !this.isBomblet || this.random.nextInt(3) == 0;
    }

    private static final int[][] WATER_PROBE = {{0, -1, 0}, {0, 0, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0},
        {0, 1, 0}};

    /** Reference {@code MCH_Lib.isBlockInWater}: the block, or one of its six neighbours, is water. */
    private boolean isBlockInWater(BlockPos p) {
        if (p.getY() <= this.level().getMinBuildHeight()) {
            return false;
        }
        for (int[] o : WATER_PROBE) {
            if (this.level().getFluidState(p.offset(o[0], o[1], o[2])).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Direct-hit power damage (reference {@code onImpactEntity}), dealt to the real entity behind a part hitbox: the
     * server's {@code applyDamageVsEntity} factor first, then the weapon's per-victim {@code DamageFactor}. Credited
     * to the shooter ({@code causeThrownDamage(this, shootingEntity)}).
     */
    private void onImpactEntity(Entity target) {
        Entity real = target instanceof PartEntity<?> part ? part.getParent() : target;
        if (!real.isAlive()) {
            return;
        }
        DamageSource src = this.damageSources().thrown(this, this.shooter);
        float damage = MchServerRules.applyDamageVsEntity(real, this.damage);
        damage *= this.weaponInfo != null ? this.weaponInfo.getDamageFactor(new NeoEntityRef(real)) : 1.0F;
        // Reference applyEntityHurtResistantTimeConfig: the victim's hurt immunity is scaled by the server's
        // hurtResistantTime (0 by default), so rapid fire and the follow-up blast all land.
        MchExplosion.applyHurtResistantTime(real);
        real.hurt(src, damage);
        notifyHit(); // reference onImpactEntity ends with notifyHitBullet() for every direct hit
    }

    /** Reference {@code attackEntityFrom}: a round struck by another round ({@code thrown} damage) goes off where it
     *  is. A marker rocket ignores it (reference {@code MCH_EntityMarkerRocket.attackEntityFrom} returns false). */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source) || this.level().isClientSide || this.isRemoved() || amount <= 0.0F
            || !source.is(DamageTypes.THROWN) || this.entityData.get(DATA_MARKER) != 0) {
            return false;
        }
        this.markHurt();
        onImpact(this.position(), null);
        return true;
    }

    /**
     * This tick's block clip, shooting through any block on the server's bullet-breakable list (reference
     * {@code onUpdateCollided}:627-643): that block is destroyed, dropping its items, and the clip runs again, at most
     * five times. Only a round with a shooter does this, as in the reference ({@code shootingEntity != null}).
     */
    private BlockHitResult clipShootingThrough(Vec3 from, Vec3 to) {
        BlockHitResult hit = this.level().clip(
            new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Entity owner = this.shooter;
        for (int i = 0; i < 5 && owner != null && hit.getType() == HitResult.Type.BLOCK; i++) {
            BlockPos bp = hit.getBlockPos();
            if (!MchServerRules.bulletBreaks(this.level().getBlockState(bp))) {
                break;
            }
            this.level().destroyBlock(bp, true, owner);
            hit = this.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        }
        return hit;
    }

    // ---- marker rocket (reference MCH_EntityMarkerRocket) ----

    /** Planted-marker tick: a pulsing beacon on the client; on the server, count down and call in the airstrike, then
     *  despawn (reference {@code onUpdate} status-2 branch). */
    private void tickMarked() {
        if (this.level().isClientSide) {
            spawnBeacon();
            return;
        }
        if (this.isInWater()) {
            this.discard();
            return;
        }
        if (this.markerCountdown > 0) {
            this.markerCountdown--;
            if (this.markerCountdown == 40) {
                spawnAirstrike();
            }
        } else {
            this.discard();
        }
    }

    /** A marker rocket hit a block: set the open block just outside the struck face alight (when the server's
     *  {@code explosionFlamingBlock} allows), seat the marker there (unless buried under 5+ blocks with no line to the
     *  sky) and start the airstrike countdown (reference {@code onImpact}:139-178). */
    private void plantMarker(BlockHitResult block) {
        net.minecraft.core.BlockPos open = block.getBlockPos().relative(block.getDirection());
        if (!this.level().getBlockState(open).isAir()) {
            this.discard();
            return;
        }
        if (MchConfig.SERVER.explosionFlamingBlock()) {
            this.level().setBlockAndUpdate(open,
                net.minecraft.world.level.block.BaseFireBlock.getState(this.level(), open));
        }
        int covered = 0;
        for (int y = open.getY() + 1; y < this.level().getMaxBuildHeight() && covered < 5; y++) {
            if (!this.level().getBlockState(new net.minecraft.core.BlockPos(open.getX(), y, open.getZ())).isAir()) {
                covered++;
            }
        }
        if (covered >= 5) {
            this.discard();
            return;
        }
        this.entityData.set(DATA_MARKER, (byte) 2);
        this.setPos(open.getX() + 0.5, open.getY() + 1.1, open.getZ() + 0.5);
        this.setDeltaMovement(Vec3.ZERO);
        this.markerCountdown = 100;
    }

    /** The marked-point strike: 6-8 bombs rain from high above THIS entity's position (reference marker rocket), fired
     *  by the marker's own shooter from its own launcher. */
    private void spawnAirstrike() {
        if (this.level() instanceof ServerLevel sl) {
            callAirstrike(sl, this.getX(), this.getY(), this.getZ(), this.shooter, this.launcher,
                3 + this.random.nextInt(2), 30, 6 + this.random.nextInt(2));
        }
    }

    /**
     * Call in a bombing run at a ground TARGET: {@code num} bombs rain from high above the point, scattered, each
     * exploding on impact with {@code explosion} power (reference: {@code MCH_EntityBomb} volley). Shared by the marker
     * rocket (#21) and the CAS weapon (#31 — a called-in bombing run standing in for the reference A-10 gun run, whose
     * flying {@code MCH_EntityA10} strafing entity is a deferred sub-port). Reuses the projectile/gravity path. The
     * bombs carry {@code shooter} and {@code launcher} as given (either may be null): the caller's, not whatever the
     * shooter happens to ride when the strike lands.
     */
    public static void callAirstrike(ServerLevel sl, double tx, double ty, double tz, Entity shooter, Entity launcher,
                                     int explosion, int power, int num) {
        double ceiling = sl.getMaxBuildHeight() - 2;
        net.minecraft.util.RandomSource rnd = sl.getRandom();
        int p = Math.max(explosion, 1);
        for (int i = 0; i < num; i++) {
            double bx = tx + (rnd.nextFloat() - 0.5F) * 15.0F;
            double by = Math.min(ty + 140.0 + rnd.nextFloat() * 10.0F + i * 15, ceiling);
            double bz = tz + (rnd.nextFloat() - 0.5F) * 15.0F;
            MCH_WeaponInfo bombWi = airstrikeBombInfo(p);
            create(sl, new Vec3(bx, by, bz), new Vec3(0.0, -1.0, 0.0), 0.5F, 1.0F, -0.05F,
                power > 0 ? power : 30, 1200, shooter, launcher, "bullet", 0xFF303030, bombWi, false, false);
        }
    }

    /** A synthetic weapon info for the called-in bombs — the given explosion with light block cratering, no config file. */
    private static MCH_WeaponInfo airstrikeBombInfo(int explosion) {
        MCH_WeaponInfo wi = new MCH_WeaponInfo("mch_airstrike_bomb");
        wi.type = "bomb";
        wi.explosion = explosion;
        wi.explosionBlock = explosion;
        wi.explosionInWater = 0;
        wi.flaming = false;
        wi.power = 30;
        wi.piercing = 0;
        return wi;
    }

    /** CLIENT: the red spotting beacon at the marked point — the reference status-2
     *  {@code spawnParticle("explode", 5, 10 + rand(4), ...)}, whose "explode" puffs are MCHeli smoke: five reddish
     *  puffs a tick, all thrown up with one shared velocity, so they climb as a smoke column. (The reference also lets
     *  nearby rotors blow them about, {@code isEffectWind}; the port's smoke has no wind.) */
    private void spawnBeacon() {
        float size = 10 + this.random.nextInt(4);
        float r = this.random.nextFloat() * 0.2F + 0.8F;
        float gb = this.random.nextFloat() * 0.3F;
        double mx = (this.random.nextFloat() - 0.5F) * 0.7F;
        double my = 0.3F + this.random.nextFloat() * 0.3F;
        double mz = (this.random.nextFloat() - 0.5F) * 0.7F;
        int argb = 0xFF000000 | (channel(r) << 16) | (channel(gb) << 8) | channel(gb);
        for (int i = 0; i < 5; i++) {
            this.level().addParticle(new MchSmokeOptions(0.1F * (size + this.random.nextFloat()), argb, 0,
                MchSmokeOptions.PLAIN), this.getX(), this.getY(), this.getZ(), mx, my, mz);
        }
    }

    private static int channel(float f) {
        int i = Math.round(f * 255.0F);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }

    /** Reflect the motion off the hit block face, scaled by the weapon's restitution (reference {@code boundBullet}). */
    private Vec3 boundBullet(Direction face, Vec3 m) {
        float bound = this.weaponInfo.bound;
        double mx = m.x;
        double my = m.y;
        double mz = m.z;
        switch (face) {
            case DOWN:  if (my > 0.0) my = -my * bound; break;
            case UP:    if (my < 0.0) my = -my * bound; break;
            case NORTH: if (mz > 0.0) mz = -mz * bound; break; // -Z
            case SOUTH: if (mz < 0.0) mz = -mz * bound; break; // +Z
            case WEST:  if (mx > 0.0) mx = -mx * bound; break; // -X
            case EAST:  if (mx < 0.0) mx = -mx * bound; break; // +X
            default: break;
        }
        return new Vec3(mx, my, mz);
    }

    /**
     * The airburst probe — reference {@code MCH_Lib.getBlockIdY(this, 3, -explosionAltitude) != 0}: any block in the
     * 3×3 column running {@code n} cells down from the round's cell, a liquid only as a source block (1.7.10
     * {@code canCollideCheck(meta, true)}). The reference's {@code (int)(pos + 0.5)} cell is floored here, so the
     * column stays under the round at negative coordinates too.
     */
    private boolean groundWithin(int n) {
        int px = Mth.floor(this.getX() + 0.5);
        int py = Mth.floor(this.getY() + 0.5);
        int pz = Mth.floor(this.getZ() + 0.5);
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int y = 0; y < n; y++) {
            int by = py - y;
            if (by < this.level().getMinBuildHeight()) {
                return false;
            }
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockState st = this.level().getBlockState(mp.set(px + x, by, pz + z));
                    if (!st.isAir() && (!(st.getBlock() instanceof LiquidBlock) || st.getFluidState().isSource())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Gun-round proximity fuse (reference {@code MCH_EntityBullet.onUpdate}): a valid target within
     *  {@code proximityFuseDist+1} blocks pulls the round to the midpoint, where it impacts as on a block. */
    private void proximityCheck() {
        float pDist = this.weaponInfo.proximityFuseDist + 1.0F;
        float rng = pDist + Math.abs(this.weaponInfo.acceleration);
        AABB box = this.getBoundingBox().inflate(rng);
        double pSq = pDist * pDist;
        for (Entity e : this.level().getEntitiesOfClass(Entity.class, box, this::canHit)) {
            if (e.distanceToSqr(this) < pSq) {
                Vec3 mid = this.position().add(e.position()).scale(0.5);
                this.setPos(mid.x, mid.y, mid.z);
                onImpact(mid, null);
                return;
            }
        }
    }

    /** Bomb proximity fuse (reference {@code MCH_EntityBomb.onUpdate}): every 10 ticks, any living target within
     *  {@code proximityFuseDist} of the bomb's box sets it off where it is. */
    private void bombProximityCheck() {
        if (this.age % 10 != 0) {
            return;
        }
        float dist = this.weaponInfo.proximityFuseDist;
        for (Entity e : this.level().getEntitiesOfClass(Entity.class, this.getBoundingBox().inflate(dist),
                e -> e instanceof LivingEntity && canHit(e))) {
            onImpact(this.position(), BlockPos.containing(this.getX() + 0.5, this.getY() + 0.5, this.getZ() + 0.5),
                null);
            return;
        }
    }

    /** True for a gun round (config type {@code machinegun1/2}, the reference {@code MCH_EntityBullet}). */
    private boolean isGunType() {
        return this.weaponInfo != null && ("machinegun1".equalsIgnoreCase(this.weaponInfo.type)
            || "machinegun2".equalsIgnoreCase(this.weaponInfo.type));
    }

    /** True if this round is a dropped bomb (config type {@code bomb}) — gates the bomb-specific horizontal drag and
     *  cluster-bomblet spread that the reference {@code MCH_EntityBomb} applies but the generic bullet/rocket does not. */
    private boolean isBombType() {
        return this.weaponInfo != null
            && ("bomb".equalsIgnoreCase(this.weaponInfo.type) || isDispenserType());
    }

    /** True if this round is a dispenser (config type {@code dispenser}) — falls like a bomb, then "uses" its configured
     *  item across a radius on impact instead of exploding (reference {@code MCH_EntityDispensedItem}). */
    private boolean isDispenserType() {
        return this.weaponInfo != null && "dispenser".equalsIgnoreCase(this.weaponInfo.type);
    }

    /**
     * Dispense the configured item across a radius of blocks around the impact point (reference
     * {@code MCH_EntityDispensedItem.onImpact} + {@code useItemToBlock}): a {@code FakePlayer} "right-clicks" the item
     * onto each block in a sphere of radius {@code DispenseRange-1} (the inner half always, the outer shell at 50%),
     * so e.g. a water-bucket dispenser douses fire/lava and a sapling/torch dispenser plants/places. Server-only.
     */
    private void dispenseAt(ServerLevel sl, Vec3 pos) {
        MCH_WeaponInfo wi = this.weaponInfo;
        // Resolved once at load (MCH_WeaponInfoManager.resolveItems), 1.7.10 names + metadata included; null when the
        // config names no item or one this version does not have — then the dispenser is a no-op.
        net.minecraft.world.item.Item item = wi != null
            ? mcheli.dependent.port.NeoItemHandle.itemOf(wi.dispenseItem) : null;
        if (item == null) {
            return;
        }
        net.neoforged.neoforge.common.util.FakePlayer fake =
            net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(sl);
        int rng = Math.max(wi.dispenseRange - 1, 0);
        int cx = net.minecraft.util.Mth.floor(pos.x);
        int cy = net.minecraft.util.Mth.floor(pos.y);
        int cz = net.minecraft.util.Mth.floor(pos.z);
        double r2 = (double) rng * rng;
        for (int x = -rng; x <= rng; x++) {
            for (int y = -rng; y <= rng; y++) {
                int by = cy + y;
                if (by < sl.getMinBuildHeight() || by >= sl.getMaxBuildHeight()) {
                    continue;
                }
                for (int z = -rng; z <= rng; z++) {
                    int dist = x * x + y * y + z * z;
                    if (dist <= r2 && (dist <= 0.5 * r2 || this.random.nextInt(2) == 0)) {
                        useItemOnBlock(sl, fake, item, new net.minecraft.core.BlockPos(cx + x, by, cz + z));
                    }
                }
            }
        }
    }

    /** "Right-click" {@code item} onto the top of a non-air block (reference {@code useItemToBlock}): water-bucket
     *  douses fire / freezes lava; anything else runs the vanilla {@code useOn} then {@code use} path. */
    private void useItemOnBlock(ServerLevel sl, net.neoforged.neoforge.common.util.FakePlayer fake,
                                net.minecraft.world.item.Item item, net.minecraft.core.BlockPos bp) {
        BlockState state = sl.getBlockState(bp);
        if (state.isAir()) {
            return;
        }
        if (item == net.minecraft.world.item.Items.WATER_BUCKET) {
            // Reference special case: extinguish fire, and turn lava to obsidian (source) / cobblestone (flow) — only
            // while the server's collisionDestroyBlock allows MCHeli to change blocks (MCH_EntityDispensedItem:106).
            if (!MchConfig.SERVER.collisionDestroyBlock()) {
                return;
            }
            net.minecraft.core.BlockPos above = bp.above();
            if (sl.getBlockState(above).is(net.minecraft.world.level.block.Blocks.FIRE)) {
                sl.removeBlock(above, false);
            } else if (state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
                boolean source = state.getFluidState().isSource();
                sl.setBlockAndUpdate(bp, source
                    ? net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState()
                    : net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState());
            }
            return;
        }
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item, 1);
        fake.setPos(bp.getX() + 0.5, bp.getY() + 2.5, bp.getZ() + 0.5);
        fake.setYRot(this.random.nextInt(360));
        fake.setXRot(90.0F);
        fake.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
            new Vec3(bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5), Direction.UP, bp, false);
        net.minecraft.world.item.context.UseOnContext ctx = new net.minecraft.world.item.context.UseOnContext(
            sl, fake, net.minecraft.world.InteractionHand.MAIN_HAND, stack, hit);
        net.minecraft.world.InteractionResult r = item.useOn(ctx);
        if (!r.consumesAction()) {
            item.use(sl, fake, net.minecraft.world.InteractionHand.MAIN_HAND);
        }
        fake.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
    }

    /** Emit {@code bomblet} child rounds spread by {@code bombletDiff}, rendered with the bomblet model; they never
     *  re-split, and fly for the parent's shooter and launcher (reference {@code sprinkleBomblet} + {@code setBomblet},
     *  {@code setParameterFromWeapon(this, shootingAircraft, shootingEntity)}). */
    private void sprinkleBomblets() {
        MCH_WeaponInfo wi = this.weaponInfo;
        if (wi == null || wi.bomblet <= 0) {
            return;
        }
        ServerLevel sl = (ServerLevel) this.level();
        Vec3 motion = this.getDeltaMovement();
        float accFactor = this.entityData.get(DATA_ACCEL_FACTOR);
        float gravity = this.entityData.get(DATA_GRAVITY);
        float speed = MCH_WeaponBallistics.initialSpeed(wi.acceleration);
        String childModel = (wi.bombletModelName != null && !wi.bombletModelName.isEmpty())
            ? wi.bombletModelName : bulletModelName();
        int childColor = bulletColor();
        float diff = wi.bombletDiff;
        boolean bomb = isBombType();
        boolean asMissile = "asmissile".equalsIgnoreCase(wi.type);
        for (int i = 0; i < wi.bomblet; i++) {
            if (asMissile) {
                // Reference MCH_EntityASMissile.sprinkleBomblet: half the parent's motion (a quarter vertically) plus the
                // bombletDiff spread, and the children fall at -0.03 (getGravity of a bomblet) without steering.
                Vec3 childMotion = new Vec3(
                    motion.x * 0.5 + (this.random.nextFloat() - 0.5F) * diff,
                    motion.y * 0.5 / 2.0 + (this.random.nextFloat() - 0.5F) * diff / 2.0F,
                    motion.z * 0.5 + (this.random.nextFloat() - 0.5F) * diff);
                createWithVelocity(sl, this.position(), childMotion, accFactor, -0.03F, this.damage, this.maxLife,
                    this.shooter, this.launcher, childModel, childColor, wi, true, false);
            } else if (bomb) {
                // Reference MCH_EntityBomb.sprinkleBomblet: children INHERIT the parent descent (vertical HALVED),
                // spread by bombletDiff, with NO renormalize — a cluster bomb rains its submunitions instead of
                // re-launching them at initialSpeed (the generic rocket path below).
                Vec3 childMotion = new Vec3(
                    motion.x + (this.random.nextFloat() - 0.5F) * diff,
                    motion.y / 2.0 + (this.random.nextFloat() - 0.5F) * diff / 2.0,
                    motion.z + (this.random.nextFloat() - 0.5F) * diff);
                createWithVelocity(sl, this.position(), childMotion, accFactor, gravity, this.damage, this.maxLife,
                    this.shooter, this.launcher, childModel, childColor, wi, true, false);
            } else {
                Vec3 dir = motion.add(
                    (this.random.nextFloat() - 0.5F) * diff,
                    (this.random.nextFloat() - 0.5F) * diff,
                    (this.random.nextFloat() - 0.5F) * diff);
                create(sl, this.position(), dir, speed, accFactor, gravity, this.damage, this.maxLife, this.shooter,
                    this.launcher, childModel, childColor, wi, true, false);
            }
        }
    }

    /**
     * CLIENT: the splash a powered round throws up as it enters water (reference {@code updateSplash}): walking this
     * tick's segment in unit steps, the first water cell gets {@code rand(1+p/3) + p/2 + 1} falling splash puffs
     * ({@code p = min(Power, 20)}), thrown up harder and larger for a heavier round.
     */
    private void updateSplash(Vec3 from, Vec3 to) {
        int power = this.entityData.get(DATA_SPLASH_POWER);
        if (power <= 0 || isWaterCell(from.x, from.y, from.z) || !isWaterCell(to.x, to.y, to.z)) {
            return;
        }
        double x = to.x - from.x;
        double y = to.y - from.y;
        double z = to.z - from.z;
        double d = Math.sqrt(x * x + y * y + z * z);
        if (d <= 0.15) {
            return;
        }
        x /= d;
        y /= d;
        z /= d;
        double px = from.x;
        double py = from.y;
        double pz = from.z;
        for (int i = 0; i <= d; i++) {
            px += x;
            py += y;
            pz += z;
            if (isWaterCell(px, py, pz)) {
                float pwr = power < 20 ? power : 20.0F;
                int n = this.random.nextInt(1 + (int) pwr / 3) + (int) pwr / 2 + 1;
                pwr *= 0.03F;
                for (int j = 0; j < n; j++) {
                    this.level().addParticle(new MchSmokeOptions(0.1F * pwr * 5.0F, 0xFFFFFFFF, 0,
                            MchSmokeOptions.SPLASH), px, py + 0.5, pz,
                        pwr * (this.random.nextDouble() - 0.5) * 0.3,
                        pwr * (this.random.nextDouble() * 0.5 + 0.5) * 1.8,
                        pwr * (this.random.nextDouble() - 0.5) * 0.3);
                }
                break;
            }
        }
    }

    /** Reference {@code W_WorldFunc.isBlockWater} at {@code (int)(pos + 0.5)}, floored for negative coordinates. */
    private boolean isWaterCell(double x, double y, double z) {
        return this.level().getFluidState(BlockPos.containing(Mth.floor(x + 0.5), Mth.floor(y + 0.5),
            Mth.floor(z + 0.5))).is(FluidTags.WATER);
    }

    /** CLIENT: emit the config-driven smoke/flame trail along this tick's flight segment (3 interpolated puffs, like
     *  the reference), white smoke sized from the config — only when the weapon type trails. */
    private void emitTrail(Vec3 from, Vec3 to) {
        int kind = this.entityData.get(DATA_TRAIL_KIND);
        if (kind == 0 || this.age < this.entityData.get(DATA_TRAIL_START)) {
            return; // config TrajectoryParticleStartTick delays the trail
        }
        // A torpedo's trajectory particle is emitted ONLY underwater (reference MCH_EntityTorpedo.onUpdate gates its
        // spawnParticle on isInWater()); on the surface run it shows no trail, unlike an air-trailing rocket/missile.
        if (this.entityData.get(DATA_TORPEDO) && !inWater()) {
            return;
        }
        float size = this.entityData.get(DATA_TRAIL_SIZE);
        for (int i = 1; i <= 3; i++) {
            double f = i / 3.0;
            double x = from.x + (to.x - from.x) * f;
            double y = from.y + (to.y - from.y) * f;
            double z = from.z + (to.z - from.z) * f;
            if (kind == 2) {
                this.level().addParticle(ParticleTypes.FLAME, x, y, z, 0.0, 0.0, 0.0);
            } else {
                int trailAge = 18 + this.random.nextInt(24); // reference smoke's random ~18-42 tick life
                this.level().addParticle(new MuzzleFxOptions(0xFFFFFFFF, size, trailAge), x, y, z, 0.0, 0.01, 0.0);
            }
        }
    }

    /** SERVER: spray block debris on a block hit — config-driven exactly like the reference (only non-explosive /
     *  multi-mode rounds, count = power/3, using the ACTUAL hit block's texture, never the bullet colour). */
    private void spawnBlockImpact(BlockHitResult block) {
        if (!(this.level() instanceof ServerLevel sl) || this.weaponInfo == null) {
            return;
        }
        if (this.weaponInfo.explosion != 0 && this.weaponInfo.modeNum < 2) {
            return; // explosive rounds make an explosion, not block debris
        }
        BlockState state = this.level().getBlockState(block.getBlockPos());
        if (state.isAir()) {
            return;
        }
        // Reference loops `i < power/3.0f` -> the count is ceil(power/3); power 0 sprays nothing.
        int count = (int) Math.ceil(this.weaponInfo.power / 3.0F);
        if (count <= 0) {
            return;
        }
        Vec3 p = block.getLocation();
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), p.x, p.y, p.z, count, 0.1, 0.1, 0.1, 0.15);
    }

    /**
     * Reference {@code checkValid} (MCH_EntityBaseBullet:350-366): every round, missiles included, despawns when its
     * SHOOTER dies (or logs out); the launcher's death is ignored (its branch is empty in the reference), so a round
     * fired from a vehicle that is then destroyed flies on. Beyond that, a round despawns 5820 blocks (horizontally)
     * from its shooter, else from its launcher.
     */
    private boolean checkValid() {
        if (this.shooter == null && this.launcher == null) {
            // Reference: false. Such rounds now only come from the headless self-tests (the entity type can be
            // neither summoned nor saved), which rely on maxLife instead.
            return true;
        }
        if (this.shooter != null && !this.shooter.isAlive()) {
            return false;
        }
        Entity anchor = this.shooter != null ? this.shooter : this.launcher;
        double dx = this.getX() - anchor.getX();
        double dz = this.getZ() - anchor.getZ();
        return dx * dx + dz * dz < MAX_DIST_SQ;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_MODEL, "bullet");
        builder.define(DATA_COLOR, 0xFFFFFFFF);
        builder.define(DATA_GRAVITY, 0.0F);
        builder.define(DATA_ACCEL_FACTOR, 1.0F);
        builder.define(DATA_TRAIL_KIND, 0);
        builder.define(DATA_TRAIL_SIZE, 0.2F);
        builder.define(DATA_TRAIL_START, 0);
        builder.define(DATA_MARKER, (byte) 0);
        builder.define(DATA_GUIDED, GUIDED_NONE);
        builder.define(DATA_TORPEDO, false);
        builder.define(DATA_GRAVITY_WATER, 0.0F);
        builder.define(DATA_RICOCHET, false);
        builder.define(DATA_SPLASH_POWER, 0);
        builder.define(DATA_VELOCITY_WATER, 1.0F);
    }
    // Rounds are never saved (the entity type is noSave/noSummon): their weapon, shooter and fuses are live state.
    // Should one be read anyway, it is dropped, as the reference's readEntityFromNBT does (setDead).
    @Override protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        this.discard();
    }
    @Override protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}

    @Override public boolean isPickable() { return false; }
    // Reference isInRangeToRenderDist: (box edge 1 x 4 x 64)^2, so every round draws to 256 blocks; the tracking
    // range (MchRegistries.BULLET, the reference's 530) lets the client have it that far.
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 65536.0; }
    @Override public void lerpTo(double x, double y, double z, float yr, float xr, int steps) {
        // In-flight straight rounds dead-reckon (no lerp). Exceptions accept the server position:
        //  - a PLANTED marker snaps to its block so the client beacon isn't dead-reckoned past the wall;
        //  - a GUIDED missile curves under server authority, which the client can't reproduce from straight motion;
        //  - a TORPEDO self-steers underwater (level + re-normalize to cruise speed) server-side, the same way;
        //  - a RICOCHET (delayFuse) round bounces off blocks on the server only (the client never clips blocks).
        // The latter three accept the server position AND nose orientation each update (dead-reckoning between updates
        // keeps it smooth); they are built as STEERED_BULLET, which the server updates every tick, while the other
        // rounds' type updates every five (steeredPath).
        if (this.entityData.get(DATA_MARKER) == 2) {
            this.setPos(x, y, z);
        } else if (this.entityData.get(DATA_GUIDED) != GUIDED_NONE || this.entityData.get(DATA_TORPEDO)
            || this.entityData.get(DATA_RICOCHET)) {
            this.setPos(x, y, z);
            this.setYRot(yr);
            this.setXRot(xr);
        }
    }
}
