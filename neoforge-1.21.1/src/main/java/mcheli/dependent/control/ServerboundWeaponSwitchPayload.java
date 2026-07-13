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
 * Serverbound one-shot "cycle weapon" action — the 1.21.1 analogue of the reference's switch-weapon key path
 * (client computes {@code getNextWeaponID} then the server commits {@code switchWeapon}). Unlike the held control-flag
 * bitset, weapon-switch is <b>edge-triggered</b>: the client sends exactly one of these on the rising edge of the
 * switch key, so the server cycles once per press instead of every tick the key is held. {@code direction} is +1 (next)
 * or -1 (previous).
 */
public record ServerboundWeaponSwitchPayload(int vehicleId, int direction) implements CustomPacketPayload {

    public static final Type<ServerboundWeaponSwitchPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "weapon_switch"));

    public static final StreamCodec<ByteBuf, ServerboundWeaponSwitchPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerboundWeaponSwitchPayload::vehicleId,
            ByteBufCodecs.VAR_INT, ServerboundWeaponSwitchPayload::direction,
            ServerboundWeaponSwitchPayload::new);

    @Override
    public Type<ServerboundWeaponSwitchPayload> type() {
        return TYPE;
    }

    /**
     * Server-side handler: queue a weapon cycle on the addressed vehicle, but only if the sender is actually riding it
     * (the same anti-spoof passenger check as the control payload). Runs on the main thread.
     */
    public static void handle(ServerboundWeaponSwitchPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity e = player.level().getEntity(p.vehicleId());
            // Weapon switching is blocked while the pilot is in the resupply GUI (reference isPilotReloading gate).
            if (e instanceof AbstractMchVehicle v && e.hasPassenger(player) && !v.isPilotReloading()) {
                v.queueWeaponSwitch(player, p.direction() >= 0 ? 1 : -1);
            }
        });
    }
}
