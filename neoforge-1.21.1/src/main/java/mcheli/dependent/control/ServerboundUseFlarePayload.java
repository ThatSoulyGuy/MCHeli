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
 * Serverbound one-shot "dispense flares" — the reference {@code KeyFlare} → {@code useFlare}. Seats 0 AND 1 may deploy
 * (reference {@code seatId <= 1}); the server re-gates the vehicle's {@code haveFlare()} + {@code canDeployFlare()}
 * before starting the dispense window and advances the flare type. Edge-triggered like the fold-blade / gunner-mode
 * switches (one dispense per key press).
 */
public record ServerboundUseFlarePayload(int vehicleId) implements CustomPacketPayload {

    public static final Type<ServerboundUseFlarePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "use_flare"));

    public static final StreamCodec<ByteBuf, ServerboundUseFlarePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerboundUseFlarePayload::vehicleId,
            ServerboundUseFlarePayload::new);

    @Override
    public Type<ServerboundUseFlarePayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundUseFlarePayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity e = player.level().getEntity(p.vehicleId());
            if (e instanceof AbstractMchVehicle v && v.hasPassenger(player)) {
                int seat = v.seatIndexOf(player);
                if (seat >= 0 && seat <= 1 && v.canDeployFlare()) {
                    v.deployFlare(v.currentFlareType());
                    v.nextFlareType();
                }
            }
        });
    }
}
