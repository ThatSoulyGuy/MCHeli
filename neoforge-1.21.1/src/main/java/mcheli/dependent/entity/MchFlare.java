package mcheli.dependent.entity;

import mcheli.dependent.particle.MchSmokeOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A flare / countermeasure decoy — a faithful distillation of {@code MCH_EntityFlare}. Purely COSMETIC: it is
 * non-pickable and never damages, so projectiles and the lock tracker ignore it (the reference likewise keeps flares
 * out of the weapon path). The actual countermeasure — nulling the target of missiles homing on a flaring aircraft —
 * lives server-side in {@link AbstractMchVehicle} ({@code tickFlares}, the port of {@code MCH_MissileDetector}); the
 * flare entity just falls and burns.
 *
 * <p>Physics mirror the reference: gravity {@code -0.013}/tick, {@code 0.992} horizontal drag (type-4 flares float via
 * a lighter override), size 6, invulnerable. Death: a {@code fuseCount} airburst (type 10 ring), a 300-tick cap, or on
 * hitting a block or water (server). The client renders it as a bright flame + smoke trail and pops a smoke burst on
 * an airburst death; {@code fuseCount} rides a synced byte so the client knows to burst.
 *
 * <p>The same class is the burning debris of a big MCHeli explosion ({@link #debris}): a size-2 flare that exists only
 * in one client's world (reference {@code MCH_Explosion.effectExplosion} spawns {@code MCH_EntityFlare}s client-side).
 */
public class MchFlare extends Entity {

    private static final EntityDataAccessor<Byte> DATA_FUSE =
        SynchedEntityData.defineId(MchFlare.class, EntityDataSerializers.BYTE);

    private boolean bursted; // client: the airburst smoke was spawned once
    private float size = 6.0F; // reference size: 6 for a countermeasure, 2 for blast debris (thinner trail)
    private boolean clientOnly; // blast debris: lives in one client's world only, so that client runs its death checks

    public MchFlare(EntityType<? extends MchFlare> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    /** Spawn a flare decoy with a given ejection velocity, airburst fuse (0 = none), and the floaty-type physics flag. */
    public static MchFlare spawn(Level level, Vec3 pos, Vec3 motion, int fuseCount, boolean floaty) {
        MchFlare f = new MchFlare(mcheli.dependent.registry.MchRegistries.FLARE.get(), level);
        f.setPos(pos.x, pos.y, pos.z);
        f.setDeltaMovement(motion);
        // Pack the airburst fuse (low 6 bits) + the floaty-type flag (bit 6) into the synced byte, so the CLIENT
        // dead-reckons a type-4 flare with the correct lighter gravity/drag AND knows a type-10 flare airbursts.
        int packed = Math.min(fuseCount, 0x3F) | (floaty ? 0x40 : 0);
        f.entityData.set(DATA_FUSE, (byte) packed);
        f.setYRot((float) (Math.random() * 360.0));
        level.addFreshEntity(f);
        return f;
    }

    /**
     * A piece of burning blast debris (reference {@code new MCH_EntityFlare(world, ..., 2.0F, 0)} in
     * {@code effectExplosion}), NOT added to any level: the client effect adds it to its own world. The reference
     * debris never dies on its own (its death checks are server-side, so it falls out of the world); this one dies
     * on the first block or water it meets, like a countermeasure flare, so its smoke trail never runs underground.
     */
    public static MchFlare debris(Level level, Vec3 pos, Vec3 motion) {
        MchFlare f = new MchFlare(mcheli.dependent.registry.MchRegistries.FLARE.get(), level);
        f.setPos(pos.x, pos.y, pos.z);
        f.setDeltaMovement(motion);
        f.size = 2.0F;
        f.clientOnly = true;
        return f;
    }

    private int fuse() { return this.entityData.get(DATA_FUSE) & 0x3F; }
    private boolean floaty() { return (this.entityData.get(DATA_FUSE) & 0x40) != 0; }
    private double gravity() { return floaty() ? -0.013 * 0.6 : -0.013; }
    private double drag() { return floaty() ? 0.995 : 0.992; }

    @Override
    public void tick() {
        super.tick();
        boolean client = this.level().isClientSide;
        // Timed airburst (type-10 ring). The reference fuse runs on BOTH sides and the client's own setDead pops the
        // smoke, so the burst never waits on the server's removal packet; whichever of the two arrives first bursts.
        int fuse = fuse();
        if (fuse > 0 && this.tickCount >= fuse) {
            if (client) {
                airburst();
            }
            this.discard();
            return;
        }
        boolean authority = !client || this.clientOnly; // who runs the death checks
        if (authority && this.tickCount > 300) {
            this.discard();
            return;
        }

        Vec3 motion = this.getDeltaMovement();
        Vec3 from = this.position();
        Vec3 to = from.add(motion);

        if (authority) {
            // Die on hitting a block (the reference clips the move segment) or in water. The reference's onGround
            // death is unreachable there too: a flare moves by setPosition, never moveEntity, so onGround stays false.
            BlockHitResult hit = this.level().clip(
                new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.MISS || this.isInWater()) {
                this.discard();
                return;
            }
        }

        this.setPos(to.x, to.y, to.z);

        if (client) {
            emitTrail(from, to);
        }

        // Gravity + horizontal drag for the next tick (reference order: applied after the move). Both sides read the
        // synced floaty flag, so the client dead-reckons the same path as the server.
        this.setDeltaMovement(motion.x * drag(), motion.y + gravity(), motion.z * drag());
    }

    /** CLIENT: a bright flame at the flare (standing in for the reference's glowing flare model) plus the reference
     *  smoke trail: two puffs from the old position to the midpoint, white, or brownish on an airburst flare; a
     *  small (debris) flare drops every other puff and draws the rest at 75% alpha. */
    private void emitTrail(Vec3 from, Vec3 to) {
        this.level().addParticle(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
        for (int i = 0; i < 2; i++) {
            float a = 1.0F;
            float r = 1.0F;
            float g = 1.0F;
            float b = 1.0F;
            if (this.size < 5.0F) {
                a *= 0.75F;
                if (this.random.nextInt(2) == 0) {
                    continue;
                }
            }
            if (fuse() > 0) {
                a = this.random.nextFloat() * 0.1F + 0.85F;
                b = this.random.nextFloat() * 0.1F + 0.5F;
                g = b + 0.05F;
                r = b + 0.1F;
            }
            double f = i / 2.0;
            // Reference prm.size = 6 + rand (render half-size 0.1·size), random life.
            this.level().addParticle(new MchSmokeOptions(0.1F * (6.0F + this.random.nextFloat()), argb(a, r, g, b), 0,
                    MchSmokeOptions.PLAIN),
                from.x + (to.x - from.x) * f, from.y + (to.y - from.y) * f, from.z + (to.z - from.z) * f,
                0.0, 0.0, 0.0);
        }
    }

    @Override
    public void onClientRemoval() {
        // The server removed the flare before this client's own fuse ran out: pop the airburst now.
        if (fuse() > 0) {
            airburst();
        }
        super.onClientRemoval();
    }

    /** CLIENT: the airburst pop (reference {@code setDead} with {@code fuseCount > 0}): 20 large, long-lived puffs of
     *  brownish smoke drifting out over a 10-block cube. Runs at most once per flare. */
    private void airburst() {
        if (this.bursted) {
            return;
        }
        this.bursted = true;
        for (int i = 0; i < 20; i++) {
            double ox = (this.random.nextDouble() - 0.5) * 10.0;
            double oy = (this.random.nextDouble() - 0.5) * 10.0;
            double oz = (this.random.nextDouble() - 0.5) * 10.0;
            float a = this.random.nextFloat() * 0.1F + 0.85F;
            float b = this.random.nextFloat() * 0.2F + 0.5F;
            this.level().addParticle(new MchSmokeOptions(0.1F * (20 + this.random.nextInt(25)),
                    argb(a, b + 0.1F, b + 0.05F, b), 200 + this.random.nextInt(100), MchSmokeOptions.PLAIN),
                this.getX() + ox, this.getY() + oy, this.getZ() + oz,
                (this.random.nextDouble() - 0.5) * 0.45, (this.random.nextDouble() - 0.5) * 0.01,
                (this.random.nextDouble() - 0.5) * 0.45);
        }
    }

    private static int argb(float a, float r, float g, float b) {
        return (channel(a) << 24) | (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    private static int channel(float f) {
        int i = Math.round(f * 255.0F);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_FUSE, (byte) 0);
    }
    @Override protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}

    // Cosmetic: never pickable (projectiles + the lock tracker skip it), never collidable, never damaged.
    @Override public boolean isPickable() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource src, float amount) { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 16384.0; }
    // Pure dead-reckon (both sides run the same gravity/drag from the synced launch velocity) — no lerp fighting it.
    @Override public void lerpTo(double x, double y, double z, float yr, float xr, int steps) {}
}
