package mcheli.dependent.entity;

import mcheli.agnostic.weapon.MCH_WeaponBallistics;
import mcheli.agnostic.weapon.MCH_WeaponInfo;
import mcheli.dependent.particle.MuzzleFxOptions;
import mcheli.dependent.port.NeoEntityRef;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
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
 *       (detonate near a target, checked at tick end).</li>
 *   <li><b>Piercing</b> — passes through BLOCKS (decrementing, tiny burst each); a direct ENTITY hit forcibly zeroes
 *       piercing so the round dies on the entity (faithful to {@code onImpact}).</li>
 *   <li><b>Cluster bomblets</b> — after {@code bombletSTime} ticks a parent sprinkles {@code bomblet} child rounds
 *       (spread by {@code bombletDiff}, rendered with {@code ModelBomblet}) that never re-split.</li>
 * </ul>
 *
 * <p>Effects + collision + damage are server-authoritative; the flight integrator runs both sides (from the synced
 * gravity/accelerationFactor) so the client dead-reckons smoothly. Homing/guidance is a separate deferred effort.
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

    /** Squared horizontal despawn distance from the shooter (5820², reference {@code checkValid}). */
    private static final double MAX_DIST_SQ = 3.38724E7;

    private float damage = 5.0F;
    private int life;
    private int maxLife = 200;
    private int age;                    // reference countOnUpdate (timeFuse clock + proximity age gate)
    private Entity shooter;             // server-side only; excluded from collision + blast
    private MCH_WeaponInfo weaponInfo;  // server-side only; source of all effect params
    private int piercing;               // live counter (blocks pierced remaining)
    private int delayFuseCounter;       // live countdown; 0 == unarmed (bounces off blocks)
    private int sprinkleTime;           // ticks until a cluster round splits (0 == not a cluster carrier)
    private boolean isBomblet;          // a sprinkled child (never re-splits)

    public MchBullet(EntityType<? extends MchBullet> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    /** Spawn a projectile carrying a weapon's real stats + effects. {@code wi} may be null (a plain tracer). */
    public static MchBullet spawnWeapon(Level level, Vec3 pos, Vec3 dir, float speed, float accelerationFactor,
                                        float gravity, float damage, int maxLife, Entity shooter, String modelName,
                                        int color, MCH_WeaponInfo wi) {
        return create(level, pos, dir, speed, accelerationFactor, gravity, damage, maxLife, shooter, modelName, color,
            wi, false);
    }

    private static MchBullet create(Level level, Vec3 pos, Vec3 dir, float speed, float accelerationFactor,
                                    float gravity, float damage, int maxLife, Entity shooter, String modelName,
                                    int color, MCH_WeaponInfo wi, boolean isBomblet) {
        MchBullet b = new MchBullet(mcheli.dependent.registry.MchRegistries.DEMO_BULLET.get(), level);
        Vec3 v = dir.lengthSqr() > 1.0e-9 ? dir.normalize().scale(speed) : new Vec3(0.0, 0.0, speed);
        // Reference nudges the round half a step past the muzzle so it clears the barrel / registers point-blank.
        Vec3 start = pos.add(v.scale(0.5));
        b.setPos(start.x, start.y, start.z);
        b.setDeltaMovement(v);
        b.damage = damage;
        b.maxLife = maxLife;
        b.shooter = shooter;
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
        b.entityData.set(DATA_MODEL, modelName == null ? "" : modelName);
        b.entityData.set(DATA_COLOR, color);
        b.entityData.set(DATA_GRAVITY, gravity);
        b.entityData.set(DATA_ACCEL_FACTOR, accelerationFactor);
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

    private boolean canHit(Entity e) {
        if (e == this || e instanceof MchBullet || !e.isPickable() || e.isSpectator() || !e.isAlive()) {
            return false;
        }
        if (this.shooter != null) {
            if (e == this.shooter || this.shooter.getPassengers().contains(e) || e.getVehicle() == this.shooter) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        this.age++;

        Vec3 motion = this.getDeltaMovement();
        float gravity = this.entityData.get(DATA_GRAVITY);
        float accFactor = this.entityData.get(DATA_ACCEL_FACTOR);

        if (!this.level().isClientSide) {
            // Cluster split: after bombletSTime ticks the carrier emits its children and dies.
            if (this.sprinkleTime > 0 && --this.sprinkleTime == 0) {
                sprinkleBomblets();
                this.discard();
                return;
            }
            // delayFuse countdown -> timeout detonation.
            if (this.delayFuseCounter > 0 && --this.delayFuseCounter == 0) {
                detonate(this.position());
                return;
            }
            // Despawn if the shooter is gone or the round has flown too far (horizontal only).
            if (!checkValid()) {
                this.discard();
                return;
            }
            // timeFuse: detonate once the age exceeds the threshold.
            if (this.weaponInfo != null && this.weaponInfo.timeFuse > 0 && this.age > this.weaponInfo.timeFuse) {
                detonate(this.position());
                return;
            }
            // explosionAltitude airburst: detonate when ground is within N blocks below.
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

        Vec3 from = this.position();
        Vec3 step = accFactor != 1.0F ? motion.scale(accFactor) : motion;
        Vec3 to = from.add(step);

        if (!this.level().isClientSide) {
            BlockHitResult block = this.level().clip(
                new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
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
                onImpact(entityHit.getLocation(), entityHit.getEntity());
                if (this.isRemoved()) {
                    return;
                }
            } else if (blockHit) {
                spawnBlockImpact(block);
                onImpact(block.getLocation(), null);
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
        double horiz = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        if (motion.lengthSqr() > 1.0e-9) {
            this.setYRot((float) Math.toDegrees(Math.atan2(-motion.x, motion.z)));
            this.setXRot((float) Math.toDegrees(Math.atan2(-motion.y, horiz)));
        }

        // Client cosmetics: the config-driven smoke/flame trail (rocket/missile) + in-water bubbles.
        if (this.level().isClientSide) {
            emitTrail(from, to);
            if (this.isInWater()) {
                this.level().addParticle(ParticleTypes.BUBBLE, this.getX(), this.getY(), this.getZ(),
                    -motion.x * 0.2, -motion.y * 0.2, -motion.z * 0.2);
            }
        }

        // Proximity fuse (gun rounds), checked at the very end of the tick after the move.
        if (!this.level().isClientSide && this.weaponInfo != null && this.weaponInfo.explosion > 0
            && this.weaponInfo.proximityFuseDist > 0.1F && this.age > 1 && !this.isRemoved()) {
            proximityCheck();
        }
    }

    // ---- impact / detonation ----

    /** Impact resolution mirroring {@code MCH_EntityBaseBullet.onImpact}: direct entity damage zeroes piercing; a
     *  piercing round survives block/airburst/proximity impacts (tiny burst each); otherwise it detonates + dies. */
    private void onImpact(Vec3 pos, Entity entity) {
        if (entity != null) {
            onImpactEntity(entity);
            this.piercing = 0;
        }
        int expPower = this.weaponInfo != null ? this.weaponInfo.explosion : 0;
        if (this.piercing > 0) {
            this.piercing--;
            if (expPower > 0 && !this.level().isClientSide) {
                MchExplosion.explode((ServerLevel) this.level(), pos, 1, 1, false, false, 0, null, this.shooter);
            }
            // keep flying
        } else {
            detonate(pos);
        }
    }

    /** Direct-hit power damage (× per-victim damage factor), then this round always detonates/dies via onImpact. */
    private void onImpactEntity(Entity target) {
        Entity real = target instanceof PartEntity<?> part ? part.getParent() : target;
        float factor = this.weaponInfo != null ? this.weaponInfo.getDamageFactor(new NeoEntityRef(real)) : 1.0F;
        DamageSource src = this.damageSources().thrown(this, this.shooter);
        real.invulnerableTime = 0; // reference zeroes the hurt-cooldown so rapid fire + the follow-up blast all land
        real.hurt(src, this.damage * factor);
    }

    /** Full explosion (FAE / in-water / normal variant selection) then despawn. Non-explosive rounds just despawn. */
    private void detonate(Vec3 pos) {
        if (this.level().isClientSide) {
            this.discard();
            return;
        }
        MCH_WeaponInfo wi = this.weaponInfo;
        if (wi != null) {
            ServerLevel sl = (ServerLevel) this.level();
            int expPower = wi.explosion;
            int expPowerInWater = wi.explosionInWater;
            if (expPowerInWater == 0) {
                if (wi.isFAE && expPower > 0) {
                    // FAE: no block destruction, strong entity ignition, no per-victim damage factor.
                    MchExplosion.explode(sl, pos, expPower, wi.explosionBlock, wi.flaming, false, 15, null, this.shooter);
                } else if (expPower > 0) {
                    MchExplosion.explode(sl, pos, expPower, wi.explosionBlock, wi.flaming, true, 0, wi, this.shooter);
                }
            } else if (this.isInWater()) {
                MchExplosion.explode(sl, pos, expPowerInWater, expPowerInWater, wi.flaming, true, 0, wi, this.shooter);
            } else {
                MchExplosion.explode(sl, pos, expPower, wi.explosionBlock, wi.flaming, true, 0, wi, this.shooter);
            }
        }
        this.discard();
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

    /** True if solid ground is within {@code n} blocks straight below (a simplified airburst probe; the reference
     *  scans a 3×3 column). */
    private boolean groundWithin(int n) {
        Vec3 from = this.position();
        Vec3 down = from.subtract(0.0, n, 0.0);
        BlockHitResult hit = this.level().clip(
            new ClipContext(from, down, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() != HitResult.Type.MISS;
    }

    /** Proximity fuse: detonate at the midpoint to a valid target within {@code proximityFuseDist+1} blocks. */
    private void proximityCheck() {
        float pDist = this.weaponInfo.proximityFuseDist + 1.0F;
        float rng = pDist + Math.abs(this.weaponInfo.acceleration);
        AABB box = this.getBoundingBox().inflate(rng);
        double pSq = pDist * pDist;
        for (Entity e : this.level().getEntitiesOfClass(Entity.class, box, this::canHit)) {
            if (e.distanceToSqr(this) < pSq) {
                Vec3 mid = this.position().add(e.position()).scale(0.5);
                detonate(mid);
                return;
            }
        }
    }

    /** Emit {@code bomblet} child rounds spread by {@code bombletDiff}, rendered with the bomblet model; they never
     *  re-split (reference {@code sprinkleBomblet} + {@code setBomblet}). */
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
        for (int i = 0; i < wi.bomblet; i++) {
            Vec3 dir = motion.add(
                (this.random.nextFloat() - 0.5F) * diff,
                (this.random.nextFloat() - 0.5F) * diff,
                (this.random.nextFloat() - 0.5F) * diff);
            create(sl, this.position(), dir, speed, accFactor, gravity, this.damage, this.maxLife, this.shooter,
                childModel, childColor, wi, true);
        }
    }

    /** CLIENT: emit the config-driven smoke/flame trail along this tick's flight segment (3 interpolated puffs, like
     *  the reference), white smoke sized from the config — only when the weapon type trails. */
    private void emitTrail(Vec3 from, Vec3 to) {
        int kind = this.entityData.get(DATA_TRAIL_KIND);
        if (kind == 0 || this.age < this.entityData.get(DATA_TRAIL_START)) {
            return; // config TrajectoryParticleStartTick delays the trail
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

    private boolean checkValid() {
        if (this.shooter == null) {
            return true; // a shooter-less test round relies on maxLife
        }
        if (!this.shooter.isAlive()) {
            return false;
        }
        double dx = this.getX() - this.shooter.getX();
        double dz = this.getZ() - this.shooter.getZ();
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
    }
    @Override protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}

    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 4096.0; }
    @Override public void lerpTo(double x, double y, double z, float yr, float xr, int steps) { }
}
