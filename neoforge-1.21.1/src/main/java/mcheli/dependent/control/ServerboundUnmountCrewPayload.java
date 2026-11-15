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
 * Serverbound one-shot "unmount the crew": the pilot's {@code KeyUnmountMob} (Y), the reference control packet's
 * {@code isUnmount = 2} ({@code MCH_PacketPlayerControlBase}:48, :84), sent by {@code MCH_AircraftClientTickHandler}
 * :121-125 and run by the heli, plane, tank and vehicle packet handlers as {@code ac.unmountCrew()}
 * ({@code MCH_HeliPacketHandler}:36-39 and the others). The server decides what the key does
 * ({@link AbstractMchVehicle#unmountCrew}: the plain unload of the mob crew, or the {@code MobDropOption} drop).
 *
 * <p>The handler requires a vehicle the sender rides, not a wreck, and the pilot seat: the rule the client checks
 * before it sends (the reference client ran its control keys only for an intact vehicle, and sent this from the pilot
 * seat alone). Deliberate divergence: reference bug (the client and server gates disagreed). The reference handlers
 * checked neither the seat nor the wreck, so any rider, through a modified client, could unload or drop the crew;
 * both sides now use the one rule, and an honest client sees no difference. A non-pilot's Y opens the cargo hold
 * instead ({@code mcheli:vehicle_gui} {@code OPEN_CARGO}).
 */
public record ServerboundUnmountCrewPayload(int vehicleId) implements CustomPacketPayload {

    public static final Type<ServerboundUnmountCrewPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "unmount_crew"));

    public static final StreamCodec<ByteBuf, ServerboundUnmountCrewPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerboundUnmountCrewPayload::vehicleId,
            ServerboundUnmountCrewPayload::new);

    @Override
    public Type<ServerboundUnmountCrewPayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundUnmountCrewPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity e = player.level().getEntity(p.vehicleId());
            if (e instanceof AbstractMchVehicle v && v.hasPassenger(player) && !v.isDestroyed()
                && v.seatIndexOf(player) == 0) {
                v.unmountCrew();
            }
        });
    }
}
