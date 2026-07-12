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
 * Data for the MCHeli weapon "fx" particle (muzzle flash, muzzle smoke, projectile smoke trail) — a soft billboarded
 * puff whose <b>colour, size and lifetime all come from the weapon config</b> (an {@code AddMuzzleFlash} line's argb +
 * size + age, or a trail's per-type size). Serializable (map + stream codec) so the server can broadcast it via
 * {@code sendParticles} carrying those config values; the client-only trail spawns it locally via {@code addParticle}.
 *
 * @param argb   packed 0xAARRGGBB tint (from the config's a/r/g/b, or opaque white for the trail)
 * @param size   billboard half-size (already the final render size: {@code 2·flash.size} / {@code 0.1·smoke.size} / trail)
 * @param maxAge lifetime in ticks (from the config's {@code age})
 */
public record MuzzleFxOptions(int argb, float size, int maxAge) implements ParticleOptions {

    public static final MapCodec<MuzzleFxOptions> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
        com.mojang.serialization.Codec.INT.fieldOf("argb").forGetter(MuzzleFxOptions::argb),
        com.mojang.serialization.Codec.FLOAT.fieldOf("size").forGetter(MuzzleFxOptions::size),
        com.mojang.serialization.Codec.INT.fieldOf("maxAge").forGetter(MuzzleFxOptions::maxAge)
    ).apply(inst, MuzzleFxOptions::new));

    public static final StreamCodec<ByteBuf, MuzzleFxOptions> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.INT, MuzzleFxOptions::argb,
        ByteBufCodecs.FLOAT, MuzzleFxOptions::size,
        ByteBufCodecs.VAR_INT, MuzzleFxOptions::maxAge,
        MuzzleFxOptions::new);

    @Override
    public ParticleType<MuzzleFxOptions> getType() {
        return MchRegistries.WEAPON_FX.get();
    }
}
