package mcheli.dependent.entity;

import mcheli.agnostic.weapon.MCH_Cartridge;
import net.minecraft.core.Direction;
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
 * An ejected shell casing — a purely cosmetic 3D model that tumbles out of a firing gun. Every parameter comes from
 * the weapon's {@code SetCartridge} config ({@link MCH_Cartridge}): the {@code name} drives BOTH the model
 * ({@code models/bullets/<name>}) and texture ({@code textures/bullets/<name>.png}), {@code scale} the render size,
 * {@code acceleration}/{@code yaw}/{@code pitch} the ejection impulse + direction, {@code gravity} the drop, and
 * {@code bound} the bounce restitution. Distilled from {@code MCH_EntityCartridge}; the exact tumble-easing is
 * simplified to a speed-proportional spin (purely cosmetic).
 */
public class MchCartridge extends Entity {

    /** Reference {@code MCH_Config.AliveTimeOfCartridge} — a config constant (10s), not a magic literal. */
    private static final int ALIVE_TIME = 200;

    private static final EntityDataAccessor<String> DATA_NAME =
        SynchedEntityData.defineId(MchCartridge.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_SCALE =
        SynchedEntityData.defineId(MchCartridge.class, EntityDataSerializers.FLOAT);

    private int life;
    private float gravity = -0.04F;
    private float bound = 0.5F;

    public MchCartridge(EntityType<? extends MchCartridge> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * Eject a shell from {@code muzzle}. Initial motion = the vehicle's velocity (the reference's one motion-inherit)
     * + a small ±0.035 x/z scatter + an impulse of {@code cfg.acceleration} along {@code (gunYaw+cfg.yaw,
     * gunPitch+cfg.pitch)}.
     */
    public static MchCartridge spawn(Level level, MCH_Cartridge cfg, Vec3 muzzle, float gunYaw, float gunPitch,
                                     Vec3 vehicleMotion) {
        MchCartridge c = new MchCartridge(mcheli.dependent.registry.MchRegistries.CARTRIDGE.get(), level);
        c.setPos(muzzle.x, muzzle.y, muzzle.z);
        c.entityData.set(DATA_NAME, cfg.name);
        c.entityData.set(DATA_SCALE, cfg.scale);
        c.gravity = cfg.gravity;
        c.bound = cfg.bound;

        org.joml.Vector3f dir = new org.joml.Vector3f(0.0F, 0.0F, 1.0F);
        new org.joml.Quaternionf()
            .rotateY((float) Math.toRadians(-(gunYaw + cfg.yaw)))
            .rotateX((float) Math.toRadians(gunPitch + cfg.pitch))
            .transform(dir);
        double sx = (level.getRandom().nextFloat() - 0.5F) * 0.07;
        double sz = (level.getRandom().nextFloat() - 0.5F) * 0.07;
        Vec3 motion = vehicleMotion.add(sx, 0.0, sz)
            .add(dir.x * cfg.acceleration, dir.y * cfg.acceleration, dir.z * cfg.acceleration);
        c.setDeltaMovement(motion);
        c.setYRot(gunYaw);
        c.setXRot(gunPitch);
        level.addFreshEntity(c);
        return c;
    }

    public String cartridgeName() { return this.entityData.get(DATA_NAME); }
    public float cartridgeScale() { return this.entityData.get(DATA_SCALE); }

    @Override
    public void tick() {
        super.tick();
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        if (++this.life > ALIVE_TIME) {
            this.discard();
            return;
        }
        if (this.level().isClientSide) {
            return; // server-authoritative physics; the client renders the tracked/interpolated transform
        }

        Vec3 m = this.getDeltaMovement();
        m = new Vec3(m.x * 0.98, m.y + this.gravity, m.z * 0.98); // air drag + config gravity

        Vec3 from = this.position();
        Vec3 to = from.add(m);
        BlockHitResult hit = this.level().clip(
            new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() != HitResult.Type.MISS) {
            m = bounce(hit.getDirection(), m);
            this.setPos(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
        } else {
            this.setPos(to.x, to.y, to.z);
        }
        this.setDeltaMovement(m);

        // Cosmetic tumble, proportional to speed (simplification of the reference's target-easing spin).
        float spin = (float) Math.min(60.0, m.length() * 400.0);
        this.setYRot(this.getYRot() + spin);
        this.setXRot(this.getXRot() + spin * 0.6F);
    }

    /** Reflect the hit-axis velocity component, scaled by the config restitution {@code bound}. */
    private Vec3 bounce(Direction face, Vec3 m) {
        double mx = m.x;
        double my = m.y;
        double mz = m.z;
        switch (face.getAxis()) {
            case X: mx = -mx * this.bound; break;
            case Y: my = -my * this.bound; break;
            case Z: mz = -mz * this.bound; break;
            default: break;
        }
        return new Vec3(mx, my, mz);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_NAME, "cartridge");
        builder.define(DATA_SCALE, 1.0F);
    }
    @Override protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}

    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 1024.0; }
}
