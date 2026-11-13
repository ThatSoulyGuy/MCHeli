package mcheli.dependent.control;

import io.netty.buffer.ByteBuf;
import mcheli.MCHeli;
import mcheli.dependent.entity.AbstractMchVehicle;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Clientbound "your seat's weapon now reads ...": the numbers of a seat's selected weapon that move every tick in combat
 * (rounds in the magazine, reload/cooldown, barrel heat, in preparation), sent ONLY to that seat's rider, for his HUD.
 * The port of the reference's per-rider ammo notices ({@code MCH_PacketNotifyAmmoNum} / {@code MCH_PacketNotifyWeaponID}
 * ammo fields; the reference client ran its own reload and heat timers, the port's server owns them). The vehicle sends
 * it when the quantised values change, plus a slow refresh; the selection itself, which every client may need, stays
 * in the vehicle's synced data.
 *
 * <p>{@code reload} and {@code heat} are fractions quantised to 0..255. Dist-safety as {@link ClientboundRwrPayload}.
 */
public record ClientboundSeatWeaponPayload(int vehicleId, int seat, int ammo, byte reload, byte heat,
                                           boolean inPreparation) implements CustomPacketPayload {

    public static final Type<ClientboundSeatWeaponPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "seat_weapon"));

    public static final StreamCodec<ByteBuf, ClientboundSeatWeaponPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ClientboundSeatWeaponPayload::vehicleId,
            ByteBufCodecs.VAR_INT, ClientboundSeatWeaponPayload::seat,
            ByteBufCodecs.VAR_INT, ClientboundSeatWeaponPayload::ammo,
            ByteBufCodecs.BYTE, ClientboundSeatWeaponPayload::reload,
            ByteBufCodecs.BYTE, ClientboundSeatWeaponPayload::heat,
            ByteBufCodecs.BOOL, ClientboundSeatWeaponPayload::inPreparation,
            ClientboundSeatWeaponPayload::new);

    @Override
    public Type<ClientboundSeatWeaponPayload> type() {
        return TYPE;
    }

    /** A 0..1 fraction as the wire byte (0..255). */
    public static byte quantise(float f) {
        return (byte) Math.round(Math.max(0.0F, Math.min(1.0F, f)) * 255.0F);
    }

    /** The wire byte back as a 0..1 fraction. */
    public static float fraction(byte b) {
        return (b & 0xFF) / 255.0F;
    }

    public static void handle(ClientboundSeatWeaponPayload p, IPayloadContext ctx) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        ctx.enqueueWork(() -> {
            Player me = ctx.player();
            if (me != null && me.level().getEntity(p.vehicleId()) instanceof AbstractMchVehicle v) {
                v.acceptSeatWeaponHud(p.seat(), p.ammo(), fraction(p.reload()), fraction(p.heat()), p.inPreparation());
            }
        });
    }
}
