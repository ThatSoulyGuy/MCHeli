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
 * Serverbound one-shot "landing gear up/down" — the reference {@code KeyGearUpDown} -> {@code switchGear} 1 (fold) /
 * 2 (unfold) ({@code MCH_AircraftClientTickHandler}:146-154, {@code MCH_HeliPacketHandler}:121-127,
 * {@code MCP_PlanePacketHandler}:112-118). Only the pilot may switch; the server picks the direction itself from its
 * own {@code canFoldLandingGear}/{@code canUnfoldLandingGear} ({@link AbstractMchVehicle#toggleLandingGear}).
 */
public record ServerboundGearPayload(int vehicleId) implements CustomPacketPayload {

    public static final Type<ServerboundGearPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "landing_gear"));

    public static final StreamCodec<ByteBuf, ServerboundGearPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerboundGearPayload::vehicleId,
            ServerboundGearPayload::new);

    @Override
    public Type<ServerboundGearPayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundGearPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity e = player.level().getEntity(p.vehicleId());
            if (e instanceof AbstractMchVehicle v && v.hasPassenger(player)) {
                v.toggleLandingGear(player); // server re-checks pilot seat + the fold/unfold gates
            }
        });
    }
}
