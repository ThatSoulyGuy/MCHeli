package mcheli.dependent.control;

import io.netty.buffer.ByteBuf;
import mcheli.MCHeli;
import mcheli.dependent.entity.MchHelicopter;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Serverbound one-shot "toggle hovering mode" — the reference {@code KeySwitchHovering} ->
 * {@code switchMode = isHoveringMode() ? 2 : 3} -> {@code switchHoveringMode} on a helicopter
 * ({@code MCH_ClientHeliTickHandler}:166-172, {@code MCH_HeliPacketHandler}:57-63). Only the pilot may toggle; the
 * server enforces {@code canSwitchHoveringMode} (not in gunner mode, usable blades, |pitch| and |roll| under 40) in
 * {@link MchHelicopter#toggleHoveringMode}. Edge-triggered like the gunner-mode / rotor-fold switches.
 */
public record ServerboundHoverPayload(int vehicleId) implements CustomPacketPayload {

    public static final Type<ServerboundHoverPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "hover_toggle"));

    public static final StreamCodec<ByteBuf, ServerboundHoverPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerboundHoverPayload::vehicleId,
            ServerboundHoverPayload::new);

    @Override
    public Type<ServerboundHoverPayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundHoverPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity e = player.level().getEntity(p.vehicleId());
            if (e instanceof MchHelicopter h && h.hasPassenger(player)) {
                h.toggleHoveringMode(player); // server re-checks pilot seat + canSwitchHoveringMode
            }
        });
    }
}
