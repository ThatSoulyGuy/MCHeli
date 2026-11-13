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
 * Serverbound one-shot "camera-mode key pressed" — the reference {@code KeyCameraMode} branch of
 * {@code MCH_AircraftClientTickHandler} ({@code pc.switchSearchLight} / {@code pc.switchCameraMode}). The server decides
 * what it does in {@link AbstractMchVehicle#cameraModeKey}: switch the search light on a vehicle that has one, else
 * step the sender's seat through normal / night vision / thermal vision. Only seats 0 and 1 have a camera, as in the
 * reference; the mode is synced back through the part status.
 */
public record ServerboundCameraModePayload(int vehicleId) implements CustomPacketPayload {

    public static final Type<ServerboundCameraModePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "camera_mode"));

    public static final StreamCodec<ByteBuf, ServerboundCameraModePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerboundCameraModePayload::vehicleId,
            ServerboundCameraModePayload::new);

    @Override
    public Type<ServerboundCameraModePayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundCameraModePayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity e = player.level().getEntity(p.vehicleId());
            if (e instanceof AbstractMchVehicle v && v.hasPassenger(player)) {
                v.cameraModeKey(player); // server re-checks the seat, the config and the wreck state
            }
        });
    }
}
