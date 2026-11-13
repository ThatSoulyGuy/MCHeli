package mcheli.dependent.particle;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Data for the MCHeli soft-smoke particle — the port of {@code MCH_EntityParticleSmoke} (and, with {@link #SPLASH}, of
 * {@code MCH_EntityParticleSplash}, which draws the same {@code smoke.png} strip): a camera-facing billboard that
 * plays the 8-frame strip once over its life. Spawned client-side via {@code addParticle}, so no network codec is
 * strictly needed, but it carries them for symmetry.
 *
 * <p>The {@code flags} carry the reference's {@code MCH_ParticleParam} switches. With no flags (the three-argument
 * constructor) the particle is the port's general-purpose smoke used by the rotor down-wash and the damage smoke: it
 * rises, billows to 2.5×, pales toward white and fades out. The flagged kinds follow the reference particle exactly.
 *
 * @param size   billboard HALF-size in blocks (the reference {@code 0.1·particleScale}); for {@link #DIFFUSIBLE} the
 *               nominal size, which the puff starts at 0.2× and grows to 2× of
 * @param argb   packed 0xAARRGGBB tint
 * @param maxAge lifetime in ticks (the strip animates once across it); 0 or less picks the reference's random life
 *               ({@code 16/(r·0.8+0.2)+2}, or {@code 80/(r·0.8+0.2)+2} for a splash)
 * @param flags  {@link #PLAIN}, {@link #DIFFUSIBLE}, {@link #SPLASH} and/or {@link #TO_WHITE}
 */
public record MchSmokeOptions(float size, int argb, int maxAge, int flags) implements ParticleOptions {

    /** Reference smoke without {@code diffusible}: constant size and alpha, x/z motion damped by 0.9, collides. */
    public static final int PLAIN = 1;
    /** Reference {@code diffusible}: grows from 0.2× to 2× the size by 0.08 blocks a tick, all motion damped by 0.96. */
    public static final int DIFFUSIBLE = 2;
    /** Reference {@code MCH_EntityParticleSplash}: falls (0.06/tick), dies on the ground, splashes when it drops back
     *  into water fast. */
    public static final int SPLASH = 4;
    /** Reference {@code toWhite} (MCH_EntityParticleSmoke:31-39): while the channels differ by more than 0.2, each
     *  channel eases 1.6%/tick toward the brightest one. Its reference users are the coloured smoke weapons (bundled
     *  {@code smoke_red} etc.; their emitter, {@code MCH_EntityAircraft.onUpdate_ParticleSmoke}, is ported as
     *  {@code AbstractMchVehicle.spawnWeaponSmoke}, which does not set this flag) and the smoke grenade (not ported). */
    public static final int TO_WHITE = 8;

    /** The port's general-purpose smoke (no reference flags). */
    public MchSmokeOptions(float size, int argb, int maxAge) {
        this(size, argb, maxAge, 0);
    }

    public boolean has(int flag) {
        return (this.flags & flag) != 0;
    }

    public static final MapCodec<MchSmokeOptions> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
        com.mojang.serialization.Codec.FLOAT.fieldOf("size").forGetter(MchSmokeOptions::size),
        com.mojang.serialization.Codec.INT.fieldOf("argb").forGetter(MchSmokeOptions::argb),
        com.mojang.serialization.Codec.INT.fieldOf("maxAge").forGetter(MchSmokeOptions::maxAge),
        com.mojang.serialization.Codec.INT.optionalFieldOf("flags", 0).forGetter(MchSmokeOptions::flags)
    ).apply(inst, MchSmokeOptions::new));

    public static final StreamCodec<ByteBuf, MchSmokeOptions> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT, MchSmokeOptions::size,
        ByteBufCodecs.INT, MchSmokeOptions::argb,
        ByteBufCodecs.VAR_INT, MchSmokeOptions::maxAge,
        ByteBufCodecs.VAR_INT, MchSmokeOptions::flags,
        MchSmokeOptions::new);

    @Override
    public ParticleType<MchSmokeOptions> getType() {
        return MchRegistries.SMOKE_FX.get();
    }
}
