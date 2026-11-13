package mcheli.dependent.control;

import io.netty.buffer.ByteBuf;
import mcheli.MCHeli;
import mcheli.dependent.entity.AbstractMchVehicle;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Serverbound one-shot "switch the hatch" — the reference {@code pc.switchHatch}, sent by the pilot's zoom key outside
 * gunner mode. The server opens a closed hatch or closes an open one, or folds/spreads a plane's wing when it has no
 * hatch, gated by the reference rules in {@link AbstractMchVehicle#switchHatch} (pilot only, parts at rest, off the
 * switch cooldown, the wing-fold throttle/ground conditions).
 */
public record ServerboundHatchPayload(int vehicleId) implements CustomPacketPayload {

    public static final Type<ServerboundHatchPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "hatch_switch"));

    public static final StreamCodec<ByteBuf, ServerboundHatchPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerboundHatchPayload::vehicleId,
            ServerboundHatchPayload::new);

    @Override
    public Type<ServerboundHatchPayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundHatchPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity e = player.level().getEntity(p.vehicleId());
            if (e instanceof AbstractMchVehicle v && v.hasPassenger(player)) {
                v.switchHatch(player); // server re-checks the pilot seat and the part gates
            }
        });
    }
}
