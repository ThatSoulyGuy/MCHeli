package mcheli.dependent.entity;

import mcheli.agnostic.weapon.MCH_Cartridge;
import mcheli.dependent.config.MchConfig;
import mcheli.dependent.particle.ClientboundCartridgePayload;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * An ejected shell casing — a purely cosmetic 3D model that tumbles out of a firing gun, the port of
 * {@code MCH_EntityCartridge}. Like the reference it is a CLIENT-SIDE effect: the server only sends a
 * {@link ClientboundCartridgePayload} per shot ({@link #spawn}), and each nearby client puts its own casing into its
 * own world (an entity the server never tracks), so rapid-fire guns cost no entity traffic.
 *
 * <p>Every parameter comes from the weapon's {@code SetCartridge} config ({@link MCH_Cartridge}): the {@code name}
 * drives BOTH the model ({@code models/bullets/<name>}) and texture ({@code textures/bullets/<name>.png}),
 * {@code scale} the render size, {@code acceleration}/{@code yaw}/{@code pitch} the ejection impulse + direction,
 * {@code gravity} the drop, and {@code bound} the bounce restitution. The casing lives the server's
 * {@code aliveTimeOfCartridge} ticks.
 */
public class MchCartridge extends Entity {

    /** How far a casing is drawn: the old tracking range of the server-side casings (4 chunks). */
    private static final double SEND_RANGE = 64.0;

    private String name = "cartridge";
    private float scale = 1.0F;
    private float gravity = -0.04F;
    private float bound = 0.5F;
    private int countOnUpdate;
    private float targetYaw;
    private float targetPitch;
    // The tumble pitch, kept here because Entity.setXRot clamps to +-90 and the reference's rotationPitch spins freely.
    private float pitch;
    private float pitchO;

    public MchCartridge(EntityType<? extends MchCartridge> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * SERVER: eject a shell from {@code muzzle} — tell every client within 64 blocks to spawn it (reference
     * {@code spawnCartridge} is client-only). The casing's motion is built client-side by {@link #create}.
     */
    public static void spawn(Level level, MCH_Cartridge cfg, Vec3 muzzle, float gunYaw, float gunPitch,
                             Vec3 vehicleMotion) {
        if (cfg == null || !(level instanceof ServerLevel sl)) {
            return;
        }
        PacketDistributor.sendToPlayersNear(sl, null, muzzle.x, muzzle.y, muzzle.z, SEND_RANGE,
            new ClientboundCartridgePayload(cfg, muzzle.x, muzzle.y, muzzle.z, gunYaw, gunPitch,
                (float) vehicleMotion.x, (float) vehicleMotion.y, (float) vehicleMotion.z));
    }

    /**
     * Build (NOT add) a casing — reference {@code spawnCartridge}: motion = the vehicle's motion + a small ±0.035 x/z
     * scatter + an impulse of {@code cfg.acceleration} along {@code (gunYaw+cfg.yaw, gunPitch+cfg.pitch)}; it starts
     * facing the gun.
     */
    public static MchCartridge create(Level level, MCH_Cartridge cfg, Vec3 muzzle, float gunYaw, float gunPitch,
                                      Vec3 vehicleMotion) {
        MchCartridge c = new MchCartridge(mcheli.dependent.registry.MchRegistries.CARTRIDGE.get(), level);
        c.setPos(muzzle.x, muzzle.y, muzzle.z);
        c.name = cfg.name;
        c.scale = cfg.scale;
        c.gravity = cfg.gravity;
        c.bound = cfg.bound;

        double mx = vehicleMotion.x + (level.getRandom().nextFloat() - 0.5) * 0.07;
        double my = vehicleMotion.y;
        double mz = vehicleMotion.z + (level.getRandom().nextFloat() - 0.5) * 0.07;
        float cy = (gunYaw + cfg.yaw) * Mth.DEG_TO_RAD;
        float cp = (gunPitch + cfg.pitch) * Mth.DEG_TO_RAD;
        double tx = -Mth.sin(cy) * Mth.cos(cp);
        double tz = Mth.cos(cy) * Mth.cos(cp);
        double ty = -Mth.sin(cp);
        double d = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (Math.abs(d) > 0.001) {
            mx += tx * cfg.acceleration / d;
            my += ty * cfg.acceleration / d;
            mz += tz * cfg.acceleration / d;
        }
        c.setDeltaMovement(mx, my, mz);
        c.setYRot(gunYaw);
        c.yRotO = gunYaw;
        c.pitch = gunPitch;
        c.pitchO = gunPitch;
        c.targetYaw = gunYaw;
        c.targetPitch = gunPitch;
        return c;
    }

    public String cartridgeName() { return this.name; }
    public float cartridgeScale() { return this.scale; }
    /** The render pitch, interpolated between ticks (unclamped, unlike {@link #getXRot}). */
    public float cartridgePitch(float partialTick) { return Mth.lerp(partialTick, this.pitchO, this.pitch); }

    /** Reference {@code onUpdate}: count down the life, apply drag and gravity, then move. Like the reference it skips
     *  the base entity tick (no fluid pushing; the life bound already ends a casing that falls out of the world). */
    @Override
    public void tick() {
        this.yRotO = this.getYRot();
        this.pitchO = this.pitch;
        if (this.countOnUpdate < MchConfig.SERVER.aliveTimeOfCartridge()) {
            this.countOnUpdate++;
        } else {
            this.discard();
            return;
        }
        Vec3 m = this.getDeltaMovement();
        this.setDeltaMovement(m.x * 0.98, m.y + this.gravity, m.z * 0.98);
        move();
    }

    /**
     * Reference {@code move}: clip this tick's step; on a block hit, stop at the hit, jitter the motion, kick a new
     * tumble target and reflect off the struck face by {@code bound} (moving on along a side face it is sliding away
     * from); in free flight, ease the tumble toward its target.
     */
    private void move() {
        Vec3 m = this.getDeltaMovement();
        double mx = m.x;
        double my = m.y;
        double mz = m.z;
        Vec3 from = this.position();
        BlockHitResult hit = this.level().clip(
            new ClipContext(from, from.add(m), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        double d = Math.max(Math.max(Math.abs(mx), Math.abs(my)), Math.abs(mz));
        if (hit.getType() == HitResult.Type.BLOCK) {
            double px = hit.getLocation().x;
            double py = hit.getLocation().y;
            double pz = hit.getLocation().z;
            mx += d * (this.random.nextFloat() - 0.5F) * 0.1F;
            my += d * (this.random.nextFloat() - 0.5F) * 0.1F;
            mz += d * (this.random.nextFloat() - 0.5F) * 0.1F;
            if (d > 0.1F) {
                this.targetYaw += (float) (d * (this.random.nextFloat() - 0.5F) * 720.0);
                this.targetPitch = (float) (d * (this.random.nextFloat() - 0.5F) * 720.0);
            } else {
                this.targetPitch = 0.0F;
            }
            switch (hit.getDirection()) {
                case DOWN:
                    if (my > 0.0) my = -my * this.bound;
                    break;
                case UP:
                    if (my < 0.0) my = -my * this.bound;
                    this.targetPitch *= 0.3F;
                    break;
                case NORTH:
                    if (mz > 0.0) mz = -mz * this.bound; else pz += mz;
                    break;
                case SOUTH:
                    if (mz < 0.0) mz = -mz * this.bound; else pz += mz;
                    break;
                case WEST:
                    if (mx > 0.0) mx = -mx * this.bound; else px += mx;
                    break;
                case EAST:
                    if (mx < 0.0) mx = -mx * this.bound; else px += mx;
                    break;
                default:
                    break;
            }
            this.setPos(px, py, pz);
        } else {
            this.setPos(from.x + mx, from.y + my, from.z + mz);
            if (d > 0.05F) {
                rotation();
            }
        }
        this.setDeltaMovement(mx, my, mz);
    }

    /** Reference {@code rotation}: step the yaw 10°/tick toward its target (±3° dead band) and the pitch 10°/tick. */
    private void rotation() {
        float yaw = this.getYRot();
        if (yaw < this.targetYaw - 3.0F) {
            yaw = Math.min(yaw + 10.0F, this.targetYaw);
        } else if (yaw > this.targetYaw + 3.0F) {
            yaw = Math.max(yaw - 10.0F, this.targetYaw);
        }
        if (this.pitch < this.targetPitch) {
            this.pitch = Math.min(this.pitch + 10.0F, this.targetPitch);
        } else if (this.pitch > this.targetPitch) {
            this.pitch = Math.max(this.pitch - 10.0F, this.targetPitch);
        }
        this.setYRot(yaw);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}

    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 1024.0; }
}
