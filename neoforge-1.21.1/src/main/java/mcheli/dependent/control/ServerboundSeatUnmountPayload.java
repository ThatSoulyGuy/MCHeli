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
 * Serverbound one-shot "get me out of my seat": a passenger's jump key, the reference seat control packet's
 * {@code parachuting} bit (bit 0 of {@code MCH_PacketSeatPlayerControl}, :11/:24/:33), sent by
 * {@code MCH_ClientSeatTickHandler}:75-85 on {@code KeyParachuting} (= {@code KeySwitchHovering}) and run by
 * {@code MCH_AircraftPacketHandler}:313-315 as {@code ac.unmount(player)}. The port sends it once the jump key has been
 * HELD for half a second (see {@code MchKeybinds}). The server decides what it does
 * ({@link AbstractMchVehicle#unmountSeat}: slide down the next rope of a repelling helicopter, else parachute out from
 * the crew drop's point), so the payload carries nothing but the vehicle.
 *
 * <p>The handler requires a vehicle the sender rides, not a wreck, and a seat from 1 up: the reference packet came
 * from the riders of seat entities (the pilot never had one) and its client sent it only for an intact vehicle (:51).
 * {@code unmountSeat} then re-checks {@link AbstractMchVehicle#canRepelling} and
 * {@link AbstractMchVehicle#canParachuting}, the rules the client checks before it sends (the rope slide's was one
 * rule on both sides in the reference too). Deliberate divergence: reference bug (the parachuting gates of the
 * client and server disagreed). The reference server skipped the wreck and the 13-block ground check its client made,
 * while the client ignored the hatch the server waited for; both sides now use the one rule, and an honest client
 * sees no difference but the request behind a shut hatch it no longer sends.
 */
public record ServerboundSeatUnmountPayload(int vehicleId) implements CustomPacketPayload {

    public static final Type<ServerboundSeatUnmountPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "seat_unmount"));

    public static final StreamCodec<ByteBuf, ServerboundSeatUnmountPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerboundSeatUnmountPayload::vehicleId,
            ServerboundSeatUnmountPayload::new);

    @Override
    public Type<ServerboundSeatUnmountPayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundSeatUnmountPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity e = player.level().getEntity(p.vehicleId());
            if (e instanceof AbstractMchVehicle v && v.hasPassenger(player) && !v.isDestroyed()
                && v.seatIndexOf(player) >= 1) {
                v.unmountSeat(player);
            }
        });
    }
}
