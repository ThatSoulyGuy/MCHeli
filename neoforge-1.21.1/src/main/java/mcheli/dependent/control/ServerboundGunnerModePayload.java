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
 * Serverbound one-shot "toggle gunner mode" — the reference {@code KeySwitchMode} → {@code switchGunnerMode}. Only the
 * pilot may toggle; the server enforces the {@code canSwitchGunnerMode} gate ({@link AbstractMchVehicle#toggleGunnerMode}).
 * Edge-triggered like the weapon/seat switch (one per key press).
 */
public record ServerboundGunnerModePayload(int vehicleId) implements CustomPacketPayload {

    public static final Type<ServerboundGunnerModePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "gunner_mode"));

    public static final StreamCodec<ByteBuf, ServerboundGunnerModePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerboundGunnerModePayload::vehicleId,
            ServerboundGunnerModePayload::new);

    @Override
    public Type<ServerboundGunnerModePayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundGunnerModePayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity e = player.level().getEntity(p.vehicleId());
            if (e instanceof AbstractMchVehicle v && v.hasPassenger(player)) {
                v.toggleGunnerMode(player); // server re-checks pilot seat + canSwitchGunnerMode
            }
        });
    }
}
