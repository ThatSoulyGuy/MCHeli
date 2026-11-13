package mcheli.dependent.particle;

import io.netty.buffer.ByteBuf;
import mcheli.MCHeli;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The blast visual of one MCHeli explosion — the port of {@code MCH_PacketEffectExplosion}. The server sends this one
 * small packet per nearby player and each client builds the whole effect itself (fireball smoke, burning debris, block
 * dust, or the in-water splash), honouring its own {@code defaultExplosionParticle} option, exactly as the reference
 * {@code MCH_CommonPacketHandler.onPacketEffectExplosion} does. Sending every particle from the server instead cost
 * one packet per particle per player.
 *
 * @param size    the blast's entity power (reference {@code param.size})
 * @param inWater true for {@code newExplosionInWater}: the client throws up a splash instead of fire
 */
public record ClientboundExplosionFxPayload(double x, double y, double z, float size, boolean inWater)
    implements CustomPacketPayload {

    public static final Type<ClientboundExplosionFxPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "explosion_fx"));

    public static final StreamCodec<ByteBuf, ClientboundExplosionFxPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.DOUBLE, ClientboundExplosionFxPayload::x,
        ByteBufCodecs.DOUBLE, ClientboundExplosionFxPayload::y,
        ByteBufCodecs.DOUBLE, ClientboundExplosionFxPayload::z,
        ByteBufCodecs.FLOAT, ClientboundExplosionFxPayload::size,
        ByteBufCodecs.BOOL, ClientboundExplosionFxPayload::inWater,
        ClientboundExplosionFxPayload::new);

    @Override
    public Type<ClientboundExplosionFxPayload> type() {
        return TYPE;
    }

    public static void handle(ClientboundExplosionFxPayload p, IPayloadContext ctx) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return; // clientbound only; the guard keeps a dedicated server from linking the client class
        }
        ctx.enqueueWork(() -> mcheli.dependent.client.particle.MchExplosionFx.play(p.x(), p.y(), p.z(), p.size(),
            p.inWater()));
    }
}
