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
public record ServerboundControlPayload(int vehicleId, int bits, int lockTargetId) implements CustomPacketPayload {

    // Control-flag bits (mirror the reference bitfield's movement/brake flags; mode toggles added as ported).
    public static final int THROTTLE_UP   = 1 << 0;
    public static final int THROTTLE_DOWN = 1 << 1;
    public static final int MOVE_LEFT     = 1 << 2;
    public static final int MOVE_RIGHT    = 1 << 3;
    public static final int BRAKE         = 1 << 4;
    public static final int FREE_LOOK     = 1 << 5;
    public static final int FIRE          = 1 << 6;
    // Client settings (reference MCH_PacketNotifyClientSetting -> cs_*AutoThrottleDown / cs_dismountAll): sent in every
    // packet, so a changed config option reaches the server with the next send (a change of bits sends at once).
    public static final int AUTO_THROTTLE_DOWN = 1 << 7;
    public static final int DISMOUNT_ALL       = 1 << 8;

    public static final Type<ServerboundControlPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "control"));

    public static final StreamCodec<ByteBuf, ServerboundControlPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerboundControlPayload::vehicleId,
            ByteBufCodecs.VAR_INT, ServerboundControlPayload::bits,
            ByteBufCodecs.VAR_INT, ServerboundControlPayload::lockTargetId,
            ServerboundControlPayload::new);

    @Override
    public Type<ServerboundControlPayload> type() {
        return TYPE;
    }

    /**
     * Server-side handler: write the decoded control bits onto the {@link MchControlState} of the seat the sender
     * occupies on the addressed vehicle. Only a passenger of that vehicle is heard, and only the pilot seat's drive bits
     * are kept (the reference routed hull control from the pilot alone). Runs on the main thread via
     * {@link IPayloadContext#enqueueWork}.
     */
    public static void handle(ServerboundControlPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity e = player.level().getEntity(p.vehicleId());
            // Anti-spoof: the sender must actually be riding this vehicle; the seat check below then decides which of
            // the bits that seat owns.
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
                s.brake        = pilot && (b & BRAKE)         != 0 && v.hasBrake();
                s.freeLook     = (b & FREE_LOOK)     != 0;
                s.fire         = (b & FIRE)          != 0;
                s.autoThrottleDown = pilot && (b & AUTO_THROTTLE_DOWN) != 0;
                s.dismountAll      = pilot && (b & DISMOUNT_ALL)       != 0;
                if (s.brake) {
                    // Reference brake (MCH_AircraftClientTickHandler:171-183, MCH_TankPacketHandler:80-88): it cuts the
                    // throttle keys, and the steering too once the vehicle has all but stopped.
                    s.throttleUp = s.throttleDown = false;
                    if (v.brakeLocksSteering()) {
                        s.moveLeft = s.moveRight = false;
                    }
                }
                // The client only ships a non-negative id once its missile lock is COMPLETE; the fire path re-resolves
                // it and checks it against the server's lock audit (the candidate the seat's lock notices named, the
                // lock time, the seeker cone, range and sight) before homing, so storing it raw here is safe.
                s.lockTargetId = p.lockTargetId();
            }
        });
    }
}
