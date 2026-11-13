package mcheli.dependent.control;

import io.netty.buffer.ByteBuf;
import mcheli.MCHeli;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Clientbound "one of your rounds hit something" — the port of {@code MCH_PacketNotifyHitBullet}. The server sends it to
 * the operating player when a round strikes an entity directly ({@code MCH_EntityBaseBullet.onImpactEntity}) or its blast
 * reaches one ({@code newExplosion} with {@code result.hitEntity}); the client flashes the hit marker
 * ({@code MchHitMarker}, reference {@code MCH_GuiCommon.hitBullet} / {@code drawHitBullet}). The reference sent it twice,
 * once for the aircraft HUD and once for the common HUD, which drew the same mark; one notice does here.
 *
 * <p>Dist-safety as {@link ClientboundRwrPayload}: the handler only reaches client code on the client.
 */
public record ClientboundHitMarkPayload() implements CustomPacketPayload {

    public static final Type<ClientboundHitMarkPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "hit_mark"));

    public static final StreamCodec<ByteBuf, ClientboundHitMarkPayload> STREAM_CODEC =
        StreamCodec.unit(new ClientboundHitMarkPayload());

    @Override
    public Type<ClientboundHitMarkPayload> type() {
        return TYPE;
    }

    /** SERVER: notify {@code operator} if it is a live player (reference {@code notifyHitBullet}: players only). */
    public static void sendTo(Entity operator) {
        if (operator instanceof ServerPlayer sp && sp.isAlive()) {
            PacketDistributor.sendToPlayer(sp, new ClientboundHitMarkPayload());
        }
    }

    public static void handle(ClientboundHitMarkPayload p, IPayloadContext ctx) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        ctx.enqueueWork(mcheli.dependent.client.MchHitMarker::hit);
    }
}
