package mcheli.dependent.entity;

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
 * lives server-side in {@link MchMissileDetector}; the flare entity just falls and burns.
 *
 * <p>Physics mirror the reference: gravity {@code -0.013}/tick, {@code 0.992} horizontal drag (type-4 flares float via
 * a lighter override), size 6, invulnerable. Death: a {@code fuseCount} airburst (type 10 ring), a 300-tick cap, or on
 * hitting a block / water / the ground (server). The client renders it as a bright flame + smoke trail and pops a smoke
 * burst on an airburst death; {@code fuseCount} rides a synced byte so the client knows to burst.
 */
public class MchFlare extends Entity {

    private static final EntityDataAccessor<Byte> DATA_FUSE =
        SynchedEntityData.defineId(MchFlare.class, EntityDataSerializers.BYTE);

    private boolean bursted; // client: the airburst smoke was rendered once

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

    private int fuse() { return this.entityData.get(DATA_FUSE) & 0x3F; }
    private boolean floaty() { return (this.entityData.get(DATA_FUSE) & 0x40) != 0; }
    private double gravity() { return floaty() ? -0.013 * 0.6 : -0.013; }
    private double drag() { return floaty() ? 0.995 : 0.992; }

    @Override
    public void tick() {
        super.tick();
        // Timed airburst (type-10 ring) — server discards at the fuse; the client bursts in setRemoved().
        int fuse = fuse();
        if (fuse > 0 && this.tickCount >= fuse) {
            this.discard();
            return;
        }
        if (!this.level().isClientSide && this.tickCount > 300) {
            this.discard();
            return;
        }

        Vec3 motion = this.getDeltaMovement();
        Vec3 from = this.position();
        Vec3 to = from.add(motion);

        if (!this.level().isClientSide) {
            // Die on hitting a block (the reference clips the move segment), in water, or on the ground.
            BlockHitResult hit = this.level().clip(
                new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.MISS || this.isInWater() || this.onGround()) {
                this.discard();
                return;
            }
        }

        this.setPos(to.x, to.y, to.z);

        if (this.level().isClientSide) {
            emitTrail(from, to);
        }

        // Gravity + horizontal drag for the next tick (reference order: applied after the move). Both sides read the
        // synced floaty flag, so the client dead-reckons the same path as the server.
        this.setDeltaMovement(motion.x * drag(), motion.y + gravity(), motion.z * drag());
    }

    /** CLIENT: a bright flame at the flare plus a couple of smoke puffs along the flight segment. */
    private void emitTrail(Vec3 from, Vec3 to) {
        this.level().addParticle(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
        for (int i = 1; i <= 2; i++) {
            double f = i / 2.0;
            double x = from.x + (to.x - from.x) * f;
            double y = from.y + (to.y - from.y) * f;
            double z = from.z + (to.z - from.z) * f;
            this.level().addParticle(new mcheli.dependent.particle.MchSmokeOptions(
                6.0F + this.random.nextFloat(), 0xC0D8D0C0, 30 + this.random.nextInt(20)), x, y, z, 0.0, 0.02, 0.0);
        }
    }

    @Override
    public void onClientRemoval() {
        // Airburst pop on the client (reference setDead() with fuseCount): a small cloud of yellowish smoke. This is the
        // CLIENT removal hook (setRemoved is final; the client never calls remove()). Gated on the decoded fuse (>0 only
        // for the type-10 ring; a type-4 floaty flare packs bit 6 but fuse()==0).
        if (!this.bursted && fuse() > 0) {
            this.bursted = true;
            for (int i = 0; i < 12; i++) {
                double ox = (this.random.nextDouble() - 0.5) * 6.0;
                double oy = (this.random.nextDouble() - 0.5) * 6.0;
                double oz = (this.random.nextDouble() - 0.5) * 6.0;
                this.level().addParticle(new mcheli.dependent.particle.MchSmokeOptions(
                    14.0F + this.random.nextInt(10), 0xE0E0C880, 60 + this.random.nextInt(40)),
                    this.getX() + ox, this.getY() + oy, this.getZ() + oz,
                    (this.random.nextDouble() - 0.5) * 0.45, (this.random.nextDouble() - 0.5) * 0.01,
                    (this.random.nextDouble() - 0.5) * 0.45);
            }
        }
        super.onClientRemoval();
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
