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
 * Serverbound one-shot vehicle action, edge-triggered by a key press (one packet per press). The server re-checks every
 * gate on the authoritative entity:
 * <ul>
 *   <li>{@link #WEAPON_MODE} — reference {@code KeySwitchWeaponMode}: cycle the fire mode of the sender's selected
 *       weapon ({@link AbstractMchVehicle#switchWeaponMode}).</li>
 * </ul>
 */
public record ServerboundVehicleActionPayload(int vehicleId, int action) implements CustomPacketPayload {

    public static final int WEAPON_MODE = 1;

    public static final Type<ServerboundVehicleActionPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "vehicle_action"));

    public static final StreamCodec<ByteBuf, ServerboundVehicleActionPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerboundVehicleActionPayload::vehicleId,
            ByteBufCodecs.VAR_INT, ServerboundVehicleActionPayload::action,
            ServerboundVehicleActionPayload::new);

    @Override
    public Type<ServerboundVehicleActionPayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundVehicleActionPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity e = player.level().getEntity(p.vehicleId());
            if (!(e instanceof AbstractMchVehicle v) || !v.hasPassenger(player)) {
                return;
            }
            if (p.action() == WEAPON_MODE) {
                v.switchWeaponMode(player);
            }
        });
    }
}
