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
 * Serverbound one-shot "switch seat" action — the 1.21.1 analogue of the reference {@code MCH_PacketSeatPlayerControl}'s
 * {@code switchSeat} field ({@code MCH_AircraftClientTickHandler.commonPlayerControl}). Edge-triggered like the weapon
 * switch: exactly one is sent per key press. The three actions are the reference's {@code switchSeat} codes 1/2/3:
 *
 * <ul>
 *   <li>{@link #NEXT} (ref 1) — {@code switchNextSeat}: move to the next-higher empty passenger seat (wrapping).</li>
 *   <li>{@link #PREV} (ref 2) — {@code switchPrevSeat}: move to the next-lower empty passenger seat (wrapping).</li>
 *   <li>{@link #GRAB_PILOT} (ref 3) — take the pilot seat if it is free (a gunner promoting to pilot).</li>
 * </ul>
 *
 * <p>Because the port rides vanilla-multipassenger, a seat switch is just a re-key of the server's seat map — the
 * rider never dismounts. The server owns the map and re-checks every move, so a modified client cannot seat itself
 * illegally.
 */
public record ServerboundSeatSwitchPayload(int vehicleId, int action) implements CustomPacketPayload {

    public static final int NEXT = 1;
    public static final int PREV = 2;
    public static final int GRAB_PILOT = 3;

    public static final Type<ServerboundSeatSwitchPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "seat_switch"));

    public static final StreamCodec<ByteBuf, ServerboundSeatSwitchPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerboundSeatSwitchPayload::vehicleId,
            ByteBufCodecs.VAR_INT, ServerboundSeatSwitchPayload::action,
            ServerboundSeatSwitchPayload::new);

    @Override
    public Type<ServerboundSeatSwitchPayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundSeatSwitchPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity e = player.level().getEntity(p.vehicleId());
            if (!(e instanceof AbstractMchVehicle v) || !v.hasPassenger(player)) {
                return; // you may only switch seats on the vehicle you are riding
            }
            switch (p.action()) {
                case NEXT -> v.switchSeatNext(player);
                case PREV -> v.switchSeatPrev(player);
                case GRAB_PILOT -> v.grabPilotSeat(player);
                default -> { }
            }
        });
    }
}
