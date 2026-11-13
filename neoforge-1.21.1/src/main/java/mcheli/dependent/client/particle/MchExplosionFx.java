package mcheli.dependent.client.particle;

import java.util.HashSet;
import java.util.Set;
import mcheli.dependent.config.MchConfig;
import mcheli.dependent.entity.MchFlare;
import mcheli.dependent.particle.MchSmokeOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The client half of an MCHeli explosion — the port of {@code MCH_Explosion.effectExplosion},
 * {@code DEF_effectExplosion} and {@code effectExplosionInWater}, run when a
 * {@link mcheli.dependent.particle.ClientboundExplosionFxPayload} arrives (reference
 * {@code MCH_CommonPacketHandler.onPacketEffectExplosion}). Like the reference, the client re-runs the blast's block
 * ray-march on its own world to find where the fire, debris and dust go, so the server sends only the centre and size.
 */
@OnlyIn(Dist.CLIENT)
public final class MchExplosionFx {
    private MchExplosionFx() {}

    /** Reference {@code MCH_ParticlesUtil.doSpawnParticle}: vanilla particles farther than 300 blocks are skipped. */
    private static final double VANILLA_RANGE_SQ = 300.0 * 300.0;
    /** Block-dust size: the reference sets an absolute {@code particleScale = 2·scale}; a 1.21 terrain particle starts
     *  at about 0.075 half-size, so this multiplier lands on the reference's {@code 0.1·particleScale}. */
    private static final float DUST_SCALE = 2.67F;
    private static final int[][] DUST_OFFSETS = {{0, 0, 0}, {0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}};

    public static void play(double x, double y, double z, float size, boolean inWater) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.player.distanceToSqr(x, y, z) > 40000.0) {
            return; // reference: only within 200 blocks of the player
        }
        if (inWater) {
            effectExplosionInWater(level, x, y, z, size);
        } else if (!MchConfig.CLIENT.defaultExplosionParticle()) {
            effectExplosion(level, x, y, z, size);
        } else {
            defEffectExplosion(level, x, y, z, size);
        }
    }

    /** Reference {@code effectExplosion} (:380-515): the MCHeli blast. */
    private static void effectExplosion(ClientLevel level, double ex, double ey, double ez, float size) {
        RandomSource rand = level.random;
        Set<BlockPos> blocks = affectedBlocks(level, ex, ey, ez, size);
        centreFlash(level, ex, ey, ez, size);
        int cnt = 0;
        int flareCnt = (int) size;
        for (BlockPos bp : blocks) {
            cnt++;
            double d0 = bp.getX() + rand.nextFloat();
            double d1 = bp.getY() + rand.nextFloat();
            double d2 = bp.getZ() + rand.nextFloat();
            double mx = d0 - ex;
            double my = d1 - ey;
            double mz = d2 - ez;
            double d6 = Math.sqrt(mx * mx + my * my + mz * mz);
            mx /= d6;
            my /= d6;
            mz /= d6;
            double d7 = 0.5 / (d6 / size + 0.1);
            d7 *= rand.nextFloat() * rand.nextFloat() + 0.3F;
            mx *= d7 * 0.5;
            my *= d7 * 0.5;
            mz *= d7 * 0.5;
            double px = (d0 + ex) / 2.0;
            double py = (d1 + ey) / 2.0;
            double pz = (d2 + ez) / 2.0;
            double r = Math.PI * rand.nextInt(360) / 180.0;
            if (size >= 4.0F && flareCnt > 0) {
                // Burning debris: a small MCH_EntityFlare (size 2, no fuse) thrown up and out.
                double a = Math.min(size / 12.0F, 0.6) * (0.5F + rand.nextFloat() * 0.5F);
                MchFlare debris = MchFlare.debris(level, new Vec3(px, py + 2.0, pz),
                    new Vec3(Math.sin(r) * a, (1.0 + my / 5.0) * a, Math.cos(r) * a));
                MchClientEffects.add(debris);
                flareCnt--;
            }
            if (cnt % 4 == 0) {
                float bdf = Math.min(size / 3.0F, 2.0F) * (0.5F + rand.nextFloat() * 0.5F);
                // Reference (int)(p + 0.5); floored here so it picks the right block at negative coordinates.
                tileDust(level, Mth.floor(px + 0.5), Mth.floor(py - 0.5), Mth.floor(pz + 0.5), px, py + 1.0, pz,
                    Math.sin(r) * bdf, 0.5 + my / 5.0 * bdf, Math.cos(r) * bdf,
                    Math.min(size / 2.0F, 3.0F) * (0.5F + rand.nextFloat() * 0.5F));
            }
            int es = (int) (size >= 4.0F ? size : 4.0F);
            if (size <= 1.0F || cnt % es == 0) {
                if (rand.nextBoolean()) {
                    my *= 3.0;
                    mx *= 0.1;
                    mz *= 0.1;
                } else {
                    my *= 0.2;
                    mx *= 3.0;
                    mz *= 3.0;
                }
                // The "explode" puff: a diffusible MCHeli smoke tinted grey-orange (r+0.1, g+0.05).
                float pSize = size < 8.0F ? (size < 2.0F ? 2.0F : size * 2.0F) : 16.0F;
                float c = 0.3F + rand.nextFloat() * 0.4F;
                int age = 10 + rand.nextInt(30);
                age = (int) (age * (size < 6.0F ? size : 6.0F));
                age = age * 2 / 3;
                smoke(level, new MchSmokeOptions(0.1F * pSize, argb(1.0F, c + 0.1F, c + 0.05F, c), age,
                    MchSmokeOptions.DIFFUSIBLE), px, py, pz, mx, my, mz);
            }
        }
    }

    /** Reference {@code DEF_effectExplosion} (:517-598): the vanilla-style blast ({@code defaultExplosionParticle}). */
    private static void defEffectExplosion(ClientLevel level, double ex, double ey, double ez, float size) {
        RandomSource rand = level.random;
        Set<BlockPos> blocks = affectedBlocks(level, ex, ey, ez, size);
        centreFlash(level, ex, ey, ez, size);
        for (BlockPos bp : blocks) {
            double d0 = bp.getX() + rand.nextFloat();
            double d1 = bp.getY() + rand.nextFloat();
            double d2 = bp.getZ() + rand.nextFloat();
            double d3 = d0 - ex;
            double d4 = d1 - ey;
            double d5 = d2 - ez;
            double d6 = Math.sqrt(d3 * d3 + d4 * d4 + d5 * d5);
            d3 /= d6;
            d4 /= d6;
            d5 /= d6;
            double d7 = 0.5 / (d6 / size + 0.1);
            d7 *= rand.nextFloat() * rand.nextFloat() + 0.3F;
            d3 *= d7;
            d4 *= d7;
            d5 *= d7;
            // 1.7.10 "explode" is 1.21's poof; "smoke" is smoke.
            vanilla(level, ParticleTypes.POOF, (d0 + ex) / 2.0, (d1 + ey) / 2.0, (d2 + ez) / 2.0, d3, d4, d5, false);
            vanilla(level, ParticleTypes.SMOKE, d0, d1, d2, d3, d4, d5, false);
        }
    }

    /** Reference {@code effectExplosionInWater} (:600-637): splashes thrown up from the water blocks in range. */
    private static void effectExplosionInWater(ClientLevel level, double x, double y, double z, float size) {
        if (size <= 0.0F) {
            return;
        }
        RandomSource rand = level.random;
        int range = (int) (size + 0.5);
        if (range <= 0) {
            return;
        }
        int ex = Mth.floor(x + 0.5);
        int ey = Mth.floor(y + 0.5);
        int ez = Mth.floor(z + 0.5);
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int dy = -range; dy <= range; dy++) {
            if (ey + dy < level.getMinBuildHeight() + 1) {
                continue; // reference: only from y >= 1
            }
            for (int dx = -range; dx <= range; dx++) {
                for (int dz = -range; dz <= range; dz++) {
                    int d = dx * dx + dy * dy + dz * dz;
                    if (d >= range * range || !level.getFluidState(mp.set(ex + dx, ey + dy, ez + dz)).is(FluidTags.WATER)) {
                        continue;
                    }
                    int n = rand.nextInt(2);
                    for (int i = 0; i < n; i++) {
                        float pSize = rand.nextInt(range) * 3 + range;
                        smoke(level, new MchSmokeOptions(0.1F * pSize, 0xFFFFFFFF, 0, MchSmokeOptions.SPLASH),
                            ex + dx, ey + dy, ez + dz,
                            (double) dx / range * (rand.nextFloat() - 0.2),
                            1.0 - Math.sqrt(dx * dx + dz * dz) / range + rand.nextFloat() * 0.4 * range * 0.4,
                            (double) dz / range * (rand.nextFloat() - 0.2));
                    }
                }
            }
        }
    }

    /** Reference: {@code hugeexplosion} (1.21's explosion emitter) for a blast of size 2+, else {@code largeexplode}
     *  (1.21's explosion). Both ignore distance and the particle setting. */
    private static void centreFlash(ClientLevel level, double x, double y, double z, float size) {
        vanilla(level, size >= 2.0F ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, x, y, z,
            1.0, 0.0, 0.0, true);
    }

    /**
     * The reference effect's own copy of the explosion ray-march (16³ shell, 0.3 steps, {@code (resistance+0.3)·0.3}
     * absorption, 0.225 decay): every cell a ray still has strength in, air included. It reads the client's world, so
     * it runs before the server's block updates arrive and still sees the blocks the blast is breaking. The plain
     * resistance getters are used on purpose: the position-aware NeoForge ones want an {@code Explosion}, and this
     * visual has none to give a modded block.
     */
    @SuppressWarnings("deprecation")
    private static Set<BlockPos> affectedBlocks(ClientLevel level, double ex, double ey, double ez, float size) {
        Set<BlockPos> set = new HashSet<>();
        if (size <= 0.0F) {
            return set;
        }
        RandomSource rand = level.random;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                for (int k = 0; k < 16; k++) {
                    if (i != 0 && i != 15 && j != 0 && j != 15 && k != 0 && k != 15) {
                        continue;
                    }
                    double d3 = i / 15.0F * 2.0F - 1.0F;
                    double d4 = j / 15.0F * 2.0F - 1.0F;
                    double d5 = k / 15.0F * 2.0F - 1.0F;
                    double d6 = Math.sqrt(d3 * d3 + d4 * d4 + d5 * d5);
                    d3 /= d6;
                    d4 /= d6;
                    d5 /= d6;
                    float f1 = size * (0.7F + rand.nextFloat() * 0.6F);
                    double d0 = ex;
                    double d1 = ey;
                    double d2 = ez;
                    for (float f2 = 0.3F; f1 > 0.0F; f1 -= f2 * 0.75F) {
                        mp.set(Mth.floor(d0), Mth.floor(d1), Mth.floor(d2));
                        if (!level.isInWorldBounds(mp)) {
                            break;
                        }
                        BlockState state = level.getBlockState(mp);
                        FluidState fluid = state.getFluidState();
                        if (!state.isAir() || !fluid.isEmpty()) {
                            float f3 = Math.max(state.getBlock().getExplosionResistance(),
                                fluid.isEmpty() ? 0.0F : fluid.getExplosionResistance());
                            f1 -= (f3 + 0.3F) * f2;
                        }
                        if (f1 > 0.0F) {
                            set.add(mp.immutable());
                        }
                        d0 += d3 * f2;
                        d1 += d4 * f2;
                        d2 += d5 * f2;
                    }
                }
            }
        }
        return set;
    }

    /** Reference {@code spawnParticleTileDust}: a chip of the first solid block among the cell and its four
     *  horizontal neighbours, flung with an exact velocity at a size-scaled scale. */
    private static void tileDust(ClientLevel level, int bx, int by, int bz, double x, double y, double z,
                                 double mx, double my, double mz, float scale) {
        for (int[] o : DUST_OFFSETS) {
            BlockState state = level.getBlockState(new BlockPos(bx + o[0], by + o[1], bz + o[2]));
            if (state.isAir()) {
                continue;
            }
            Particle p = vanilla(level, new BlockParticleOption(ParticleTypes.BLOCK, state), x, y, z, mx, my, mz, false);
            if (p != null) {
                p.setParticleSpeed(mx, my, mz); // the reference block dust keeps the given motion exactly
                p.scale(scale * DUST_SCALE);
            }
            return;
        }
    }

    /** An MCHeli smoke/splash: the reference adds these straight to the effect renderer, with no distance or particle
     *  setting check. */
    private static void smoke(ClientLevel level, MchSmokeOptions o, double x, double y, double z,
                              double mx, double my, double mz) {
        Minecraft.getInstance().particleEngine.createParticle(o, x, y, z, mx, my, mz);
    }

    /** A vanilla particle under the reference {@code doSpawnParticle} rules: skipped beyond 300 blocks of the camera,
     *  at the minimal particle setting, and one time in three at decreased — unless {@code always} (the explosion
     *  flashes). */
    private static Particle vanilla(ClientLevel level, ParticleOptions o, double x, double y, double z,
                                    double mx, double my, double mz, boolean always) {
        Minecraft mc = Minecraft.getInstance();
        if (!always) {
            if (mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(x, y, z) > VANILLA_RANGE_SQ) {
                return null;
            }
            ParticleStatus status = mc.options.particles().get();
            if (status == ParticleStatus.MINIMAL
                || (status == ParticleStatus.DECREASED && level.random.nextInt(3) == 0)) {
                return null;
            }
        }
        return mc.particleEngine.createParticle(o, x, y, z, mx, my, mz);
    }

    private static int argb(float a, float r, float g, float b) {
        return (clamp255(a) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    private static int clamp255(float f) {
        int i = (int) (f * 255.0F);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }
}
