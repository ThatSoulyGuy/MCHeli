package mcheli.dependent.port;

import java.util.ArrayList;
import java.util.List;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.spi.RandomSource;
import mcheli.agnostic.spi.Role;
import mcheli.agnostic.spi.WorldView;
import mcheli.agnostic.value.AABB;
import mcheli.agnostic.value.RayHit;
import mcheli.agnostic.value.Vec3d;
import mcheli.dependent.registry.MchSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * The concrete {@link WorldView} port: wraps a real Minecraft {@link Level} and translates the agnostic
 * layer's block queries, entity-in-box queries, raytracing, spawning and effects onto it. Alongside
 * {@link NeoEntityRef} this is the platform boundary — the agnostic vehicle/physics code never sees a
 * {@code Level}, {@code BlockState} or {@code AABB}; it only sees this interface and the agnostic value types.
 *
 * <p>The physics-critical surface ({@link #isAABBInWater}, {@link #getBlockId}, {@link #isCollidable},
 * {@link #isWater}, {@link #random}) is mapped exactly. The effect surface (particles / sounds) is
 * best-effort: particle ids resolve through the built-in registry, bare sound names through {@link MchSounds}
 * (namespaced ones through the registry), and an unresolved name is a no-op.
 */
public final class NeoWorldView implements WorldView {
    private final Level level;
    private RandomSource random;   // lazily created wrapper over level.random

    public NeoWorldView(Level level) { this.level = level; }

    /** The wrapped Minecraft level (dependent-side only). */
    public Level handle() { return level; }

    @Override public boolean isRemote() { return level.isClientSide; }
    @Override public long totalTime()   { return level.getGameTime(); }

    // The REAL world height (overworld -64..319, superflat ground at -61, custom dimensions differ) so the ground
    // probes do not treat a vehicle below y=0 / above y=255 as permanently airborne (the 1.7.10 [0,255] clamp).
    @Override public int minBuildHeight() { return level.getMinBuildHeight(); }
    @Override public int maxBuildHeight() { return level.getMaxBuildHeight() - 1; } // vanilla max is exclusive

    // ---- blocks -------------------------------------------------------------

    @Override public int getBlockId(int x, int y, int z) {
        // The agnostic physics only tests != 0, so collapse to air (0) / non-air (1).
        return level.getBlockState(new BlockPos(x, y, z)).isAir() ? 0 : 1;
    }

    @Override public boolean isWater(int x, int y, int z) {
        return level.getFluidState(new BlockPos(x, y, z)).is(FluidTags.WATER);
    }

    @Override public boolean isCollidable(int x, int y, int z) {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        // Reference MCH_Lib.getBlockY gates on block.canCollideCheck(0, true). In 1.7.10 that is Block.isCollidable(),
        // true for every block but fire (tall grass, flowers, torches, snow layers all count); BlockLiquid answers
        // includeLiquid && meta == 0, and meta is hardcoded to 0, so every liquid counts; air is skipped before the
        // test. So the probe stops on anything that is not air or fire — heli ground effect and the plane/tank
        // on-ground gates fire over grass and water exactly as they did. Light and structure-void blocks (1.21 air
        // stand-ins with no 1.7.10 counterpart) are treated as air.
        if (state.isAir() || state.is(Blocks.LIGHT) || state.is(Blocks.STRUCTURE_VOID)) {
            return false;
        }
        return !(state.getBlock() instanceof BaseFireBlock);
    }

    @Override public boolean hasCollision(int x, int y, int z) {
        // What a moving entity (the reference MCH_EntityWheel) actually rests on: a non-empty collision shape.
        BlockPos pos = new BlockPos(x, y, z);
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    @Override public boolean isAABBInWater(AABB box) {
        // Reproduces the reference isAABBInMaterial(box, Material.water): scan every block column the
        // box overlaps and report whether any of them hold water fluid. Bounds use floor(min)..floor(max+1)
        // (exclusive upper) to match the 1.7.10 MathHelper.floor_double sweep exactly.
        // KNOWN LOW divergence: vanilla isAABBInMaterial also gates on the fluid SURFACE HEIGHT (water is not a
        // full block); this over-reports water for shallow/flowing cells at the box's lower edge. TODO: gate on
        // getFluidState(pos).getHeight(level,pos) if buoyancy fidelity in shallow flowing water matters.
        int minX = Mth.floor(box.minX());
        int maxX = Mth.floor(box.maxX() + 1.0);
        int minY = Mth.floor(box.minY());
        int maxY = Mth.floor(box.maxY() + 1.0);
        int minZ = Mth.floor(box.minZ());
        int maxZ = Mth.floor(box.maxZ() + 1.0);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    if (level.getFluidState(p.set(x, y, z)).is(FluidTags.WATER)) return true;
                }
            }
        }
        return false;
    }

    @Override public String materialName(int x, int y, int z) {
        // Best-effort stable id; 1.21.1 has no Material system, so the block's translation key stands in.
        return level.getBlockState(new BlockPos(x, y, z)).getBlock().getDescriptionId();
    }

    // ---- entities -----------------------------------------------------------

    @Override public List<EntityRef> entitiesInAABB(AABB box) {
        List<EntityRef> out = new ArrayList<>();
        for (Entity e : level.getEntities((Entity) null, toNet(box))) {
            out.add(new NeoEntityRef(e));
        }
        return out;
    }

    @Override public List<EntityRef> entitiesInAABB(AABB box, Role role) {
        List<EntityRef> out = new ArrayList<>();
        for (Entity e : level.getEntities((Entity) null, toNet(box))) {
            NeoEntityRef ref = new NeoEntityRef(e);
            if (ref.role() == role) out.add(ref);
        }
        return out;
    }

    // ---- raytrace -----------------------------------------------------------

    @Override public RayHit rayTraceBlocks(Vec3d from, Vec3d to) {
        return clip(from, to, false);
    }

    @Override public RayHit clip(Vec3d from, Vec3d to, boolean stopOnLiquid) {
        ClipContext ctx = new ClipContext(
            toNet(from), toNet(to),
            ClipContext.Block.COLLIDER,
            stopOnLiquid ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE,
            CollisionContext.empty());
        BlockHitResult hit = level.clip(ctx);
        if (hit == null || hit.getType() == HitResult.Type.MISS) return RayHit.MISS;
        BlockPos bp = hit.getBlockPos();
        Vec3 loc = hit.getLocation();
        return RayHit.block(new Vec3d(loc.x, loc.y, loc.z), bp.getX(), bp.getY(), bp.getZ());
    }

    // ---- effects / spawn ----------------------------------------------------

    @Override public void spawn(EntityRef entity) {
        if (entity instanceof NeoEntityRef ref) {
            level.addFreshEntity(ref.handle());
        }
    }

    @Override public void spawnParticle(String name, Vec3d pos, Vec3d velocity) {
        // Best-effort: resolve the particle by id. Simple (parameterless) particle types implement
        // ParticleOptions directly; anything needing extra data is skipped. On a server Level addParticle
        // is a no-op (particles are a client concern) — this is never on the physics path.
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null) return;
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(id);
        if (type instanceof ParticleOptions opts) {
            level.addParticle(opts, pos.x(), pos.y(), pos.z(), velocity.x(), velocity.y(), velocity.z());
        }
        // TODO: parameterized particles (block/item/dust) need their extra data decoded from `name`.
    }

    @Override public void playSound(Vec3d pos, String name, float volume, float pitch) {
        SoundEvent sound = resolveSound(name);
        if (sound == null) return;
        // player == null => server broadcasts to nearby clients; NEUTRAL category is the closest analogue
        // to the reference's world.playSoundEffect.
        level.playSound(null, pos.x(), pos.y(), pos.z(), sound, SoundSource.NEUTRAL, volume, pitch);
    }

    @Override public void playSoundAtEntity(EntityRef entity, String name, float volume, float pitch) {
        SoundEvent sound = resolveSound(name);
        if (sound == null || !(entity instanceof NeoEntityRef ref)) return;
        level.playSound(null, ref.handle(), sound, SoundSource.NEUTRAL, volume, pitch);
    }

    @Override public RandomSource random() {
        RandomSource r = random;
        if (r == null) r = random = new NeoRandomSource(level.random);
        return r;
    }

    // ---- helpers ------------------------------------------------------------

    private static net.minecraft.world.phys.AABB toNet(AABB b) {
        return new net.minecraft.world.phys.AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
    }

    private static Vec3 toNet(Vec3d v) { return new Vec3(v.x(), v.y(), v.z()); }

    /**
     * A bare name ({@code gun_h3_snd}) is first an MCHeli sound, as in the reference's
     * {@code W_WorldFunc.MOD_playSoundEffect} (which prefixed {@code "mcheli:"}): it resolves through
     * {@link MchSounds#byName}, so content-pack sounds work too. A bare name MCHeli has no sound for falls back to the
     * vanilla namespace ({@code entity.generic.explode}), as the reference's {@code DEF_playSoundEffect} passed vanilla
     * names without one. A namespaced id goes straight to the sound registry. Unknown names return null (no sound).
     */
    private static SoundEvent resolveSound(String name) {
        if (name == null || name.isEmpty()) return null;
        if (name.indexOf(':') < 0) {
            SoundEvent mcheli = MchSounds.byName(name);
            if (mcheli != null) return mcheli;
        }
        ResourceLocation id = ResourceLocation.tryParse(name); // a bare name parses into minecraft:
        return id == null ? null : BuiltInRegistries.SOUND_EVENT.get(id);
    }
}
