package mcheli.dependent.control;

import io.netty.buffer.ByteBuf;
import mcheli.MCHeli;
import mcheli.dependent.entity.MchPlane;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Serverbound one-shot "pull the ejection handle": the reference plane control packet's {@code ejectSeat} bit (bit 6
 * of {@code MCH_PacketPlayerControlBase}, :28/:43/:80), sent by {@code MCP_ClientPlaneTickHandler}:219-222 on the
 * {@code KeySwitchHovering} key and run by {@code MCP_PlanePacketHandler}:32-37 as {@code ejectSeat(player)}. The port
 * sends it once the jump key has been HELD for half a second (see {@code MchKeybinds}).
 *
 * <p>The handler requires a plane the sender rides, not a wreck, and {@code canEjectSeat(player)} (seat 0 or 1, an
 * {@code EnableEjectionSeat} config, not a UAV's pilot seat): the same rule the client checks before it sends.
 * Deliberate divergence: reference bug (the client and server gates disagreed). The reference server did not re-check
 * {@code canEjectSeat} nor the wreck and trusted the parachute slots alone, so a modified client could eject from seat
 * 0 or 1 of any vehicle with a parachute aboard; both sides now use the one rule, and an honest client sees no
 * difference. The tank handler's dead {@code ejectSeat} branch ({@code MCH_TankPacketHandler}:36-37, which no tank
 * client ever set) is not ported.
 */
public record ServerboundEjectSeatPayload(int vehicleId) implements CustomPacketPayload {

    public static final Type<ServerboundEjectSeatPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "eject_seat"));

    public static final StreamCodec<ByteBuf, ServerboundEjectSeatPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerboundEjectSeatPayload::vehicleId,
            ServerboundEjectSeatPayload::new);

    @Override
    public Type<ServerboundEjectSeatPayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundEjectSeatPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity e = player.level().getEntity(p.vehicleId());
            if (e instanceof MchPlane v && v.hasPassenger(player) && !v.isDestroyed() && v.canEjectSeat(player)) {
                v.ejectSeat(player);
            }
        });
    }
}
