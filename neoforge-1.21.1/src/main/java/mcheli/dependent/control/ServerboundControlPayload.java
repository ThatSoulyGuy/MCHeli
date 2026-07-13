package mcheli.dependent.control;

import io.netty.buffer.ByteBuf;
import mcheli.MCHeli;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Serverbound control-bits packet — the 1.21.1 analogue of the reference {@code MCH_PacketPlayerControlBase}'s
 * first short: a bitmask of the rider's momentary control keys addressed to a specific vehicle entity. The client
 * sends it while riding a {@link MchControllable} (on change + a periodic keepalive); the server writes the decoded
 * bits into that vehicle's {@link MchControlState}, which its server tick snapshots into a
 * {@link mcheli.agnostic.sim.ControlInput}.
 *
 * <p>Rotation is NOT carried here — exactly as in the reference, where orientation travels on a separate packet
 * (client-authoritative yaw/pitch/roll). This packet is only the momentary control-flag bitset.
 */
public record ServerboundControlPayload(int vehicleId, int bits) implements CustomPacketPayload {

    // Control-flag bits (mirror the reference bitfield's movement/brake flags; mode toggles added as ported).
    public static final int THROTTLE_UP   = 1 << 0;
    public static final int THROTTLE_DOWN = 1 << 1;
    public static final int MOVE_LEFT     = 1 << 2;
    public static final int MOVE_RIGHT    = 1 << 3;
    public static final int BRAKE         = 1 << 4;
    public static final int FREE_LOOK     = 1 << 5;
    public static final int FIRE          = 1 << 6;

    public static final Type<ServerboundControlPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "control"));

    public static final StreamCodec<ByteBuf, ServerboundControlPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerboundControlPayload::vehicleId,
            ByteBufCodecs.VAR_INT, ServerboundControlPayload::bits,
            ServerboundControlPayload::new);

    @Override
    public Type<ServerboundControlPayload> type() {
        return TYPE;
    }

    /**
     * Server-side handler: write the decoded control bits onto the addressed vehicle's {@link MchControlState},
     * but only if the sender is actually a passenger of that vehicle (the port's {@code isPilot} check). Runs on the
     * main thread via {@link IPayloadContext#enqueueWork}.
     */
    public static void handle(ServerboundControlPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity e = player.level().getEntity(p.vehicleId());
            // Anti-spoof: the sender must actually be riding this vehicle. hasPassenger() is the single-seat
            // equivalent of the reference's isPilot() (seat 0) check — on the demo entities getMaxPassengers()==1,
            // so the sole passenger IS the pilot. When the multi-seat MCH vehicle hierarchy is ported this must
            // become a controlling-seat check so a gunner/passenger cannot drive.
            if (e instanceof mcheli.dependent.entity.AbstractMchVehicle v && e.hasPassenger(player)) {
                int seat = v.seatIndexOf(player);
                if (seat < 0) {
                    return; // seat not resolved (mount race) — drop this packet rather than mis-attribute it
                }
                MchControlState s = v.controlState(seat);
                int b = p.bits();
                // Every seat owns FIRE / free-look; only the PILOT (seat 0) may drive — a gunner's movement bits are
                // dropped, matching the reference, which routes hull control from the pilot packet alone.
                boolean pilot = seat == 0;
                s.throttleUp   = pilot && (b & THROTTLE_UP)   != 0;
                s.throttleDown = pilot && (b & THROTTLE_DOWN) != 0;
                s.moveLeft     = pilot && (b & MOVE_LEFT)     != 0;
                s.moveRight    = pilot && (b & MOVE_RIGHT)    != 0;
                s.brake        = pilot && (b & BRAKE)         != 0;
                s.freeLook     = (b & FREE_LOOK)     != 0;
                s.fire         = (b & FIRE)          != 0;
            }
        });
    }
}
