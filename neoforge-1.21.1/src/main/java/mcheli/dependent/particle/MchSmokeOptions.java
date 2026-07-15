package mcheli.dependent.particle;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Data for the MCHeli soft-smoke particle — the port of {@code MCH_EntityParticleSmoke}: a camera-facing billboard that
 * plays the 8-frame {@code smoke.png} strip once over its life, grey fading toward white, rising as it ages. Used for
 * the helicopter rotor down-wash and the damage smoke (replacing the vanilla {@code CLOUD}/{@code LARGE_SMOKE} stand-ins).
 * Spawned client-side via {@code addParticle}, so no network codec is strictly needed, but it carries them for symmetry.
 *
 * @param size   billboard HALF-size in blocks at spawn (the reference {@code 0.1·particleScale}); grows over the life
 * @param argb   packed 0xAARRGGBB start tint (the reference's {@code 0.7..1.0} grey, fading toward white)
 * @param maxAge lifetime in ticks — the 8-frame strip animates once across it
 */
public record MchSmokeOptions(float size, int argb, int maxAge) implements ParticleOptions {

    public static final MapCodec<MchSmokeOptions> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
        com.mojang.serialization.Codec.FLOAT.fieldOf("size").forGetter(MchSmokeOptions::size),
        com.mojang.serialization.Codec.INT.fieldOf("argb").forGetter(MchSmokeOptions::argb),
        com.mojang.serialization.Codec.INT.fieldOf("maxAge").forGetter(MchSmokeOptions::maxAge)
    ).apply(inst, MchSmokeOptions::new));

    public static final StreamCodec<ByteBuf, MchSmokeOptions> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT, MchSmokeOptions::size,
        ByteBufCodecs.INT, MchSmokeOptions::argb,
        ByteBufCodecs.VAR_INT, MchSmokeOptions::maxAge,
        MchSmokeOptions::new);

    @Override
    public ParticleType<MchSmokeOptions> getType() {
        return MchRegistries.SMOKE_FX.get();
    }
}
