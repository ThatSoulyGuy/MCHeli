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
 * Serverbound one-shot "toggle VTOL" — the reference {@code KeyExtra} → {@code swithVtolMode} on a VTOL-capable plane
 * (Harrier / F-35B). Only the pilot may toggle; the server enforces the {@code canSwitchVtol} gate (VTOL enabled, off
 * cooldown, not mid-transition, near-level roll) in {@link MchPlane#toggleVtol}. Edge-triggered like the rotor-fold /
 * gunner-mode switches (one per key press). Shares the KeyExtra/PREV key with the heli rotor-fold (reference parity).
 */
public record ServerboundVtolPayload(int vehicleId) implements CustomPacketPayload {

    public static final Type<ServerboundVtolPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "vtol_toggle"));

    public static final StreamCodec<ByteBuf, ServerboundVtolPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerboundVtolPayload::vehicleId,
            ServerboundVtolPayload::new);

    @Override
    public Type<ServerboundVtolPayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundVtolPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity e = player.level().getEntity(p.vehicleId());
            if (e instanceof MchPlane plane && plane.hasPassenger(player)) {
                plane.toggleVtol(player); // server re-checks pilot seat + canSwitchVtol
            }
        });
    }
}
