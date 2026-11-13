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
 * Serverbound one-shot "switch the selected weapon's mode" — the reference {@code KeySwWeaponMode} ->
 * {@code MCH_EntityAircraft.switchCurrentWeaponMode} -> {@code MCH_WeaponSet.switchMode} (cannon AP/HE, rocket HEIAP,
 * AT direct/top attack, TV guided/unguided). In the reference the mode lived on the shooter's client and travelled with
 * every shot ({@code useWeaponOption1/2}); here the server owns it, so the client asks for the change and the synced
 * weapon state carries the result back to the HUD. Edge-triggered: one switch per key press.
 */
public record ServerboundWeaponModePayload(int vehicleId) implements CustomPacketPayload {

    public static final Type<ServerboundWeaponModePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "weapon_mode"));

    public static final StreamCodec<ByteBuf, ServerboundWeaponModePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerboundWeaponModePayload::vehicleId,
            ServerboundWeaponModePayload::new);

    @Override
    public Type<ServerboundWeaponModePayload> type() {
        return TYPE;
    }

    /** Server-side: switch the mode of the weapon the SENDER's seat has selected, if the sender rides the vehicle (the
     *  vehicle re-checks the reference gates: not destroyed, not in the resupply GUI). */
    public static void handle(ServerboundWeaponModePayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity e = player.level().getEntity(p.vehicleId());
            if (e instanceof AbstractMchVehicle v && v.hasPassenger(player)) {
                v.switchWeaponMode(player);
            }
        });
    }
}
