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
 * Data for the MCHeli muzzle flash — the port of {@code MCH_EntityParticleExplode} (spawned by the reference's
 * {@code MCH_ParticlesUtil.spawnParticleExplode} for each {@code AddMuzzleFlash} entry): a camera-facing billboard that
 * plays vanilla's 16-frame {@code explosion.png} sheet (4×4 grid) once over its life, at the config's size. Serializable
 * so the server can broadcast it via {@code sendParticles} carrying the per-flash size/tint/lifetime.
 *
 * @param size   the config flash size; the billboard's HALF-size is {@code 2·size} (the reference render extent)
 * @param argb   packed 0xAARRGGBB tint (the flash's config colour)
 * @param maxAge lifetime in ticks — the sheet animates once across it
 */
public record MchExplodeOptions(float size, int argb, int maxAge) implements ParticleOptions {

    public static final MapCodec<MchExplodeOptions> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
        com.mojang.serialization.Codec.FLOAT.fieldOf("size").forGetter(MchExplodeOptions::size),
        com.mojang.serialization.Codec.INT.fieldOf("argb").forGetter(MchExplodeOptions::argb),
        com.mojang.serialization.Codec.INT.fieldOf("maxAge").forGetter(MchExplodeOptions::maxAge)
    ).apply(inst, MchExplodeOptions::new));

    public static final StreamCodec<ByteBuf, MchExplodeOptions> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT, MchExplodeOptions::size,
        ByteBufCodecs.INT, MchExplodeOptions::argb,
        ByteBufCodecs.VAR_INT, MchExplodeOptions::maxAge,
        MchExplodeOptions::new);

    @Override
    public ParticleType<MchExplodeOptions> getType() {
        return MchRegistries.EXPLODE_FX.get();
    }
}
