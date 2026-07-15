package mcheli.dependent.control;

import io.netty.buffer.ByteBuf;
import mcheli.MCHeli;
import mcheli.dependent.entity.MchHelicopter;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Serverbound one-shot "toggle rotor fold" — the reference {@code KeyExtra} → {@code foldBlades}/{@code unfoldBlades}
 * on a parked helicopter. Only the pilot may toggle; the server enforces the {@code canSwitchFoldBlades} gate
 * (throttle idle, no cooldown, a settled fold state) in {@link MchHelicopter#toggleFold}. Edge-triggered like the
 * gunner-mode / seat switches (one per key press).
 */
public record ServerboundFoldBladePayload(int vehicleId) implements CustomPacketPayload {

    public static final Type<ServerboundFoldBladePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "fold_blade"));

    public static final StreamCodec<ByteBuf, ServerboundFoldBladePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerboundFoldBladePayload::vehicleId,
            ServerboundFoldBladePayload::new);

    @Override
    public Type<ServerboundFoldBladePayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundFoldBladePayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity e = player.level().getEntity(p.vehicleId());
            if (e instanceof MchHelicopter h && h.hasPassenger(player)) {
                h.toggleFold(player); // server re-checks pilot seat + canSwitchFoldBlades
            }
        });
    }
}
