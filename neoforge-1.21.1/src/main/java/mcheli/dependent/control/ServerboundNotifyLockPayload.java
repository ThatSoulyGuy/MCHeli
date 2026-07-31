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
 * Serverbound "I am building a missile lock on entity {@code targetId}" — paced from the shooter's client
 * ({@link mcheli.dependent.client.MchLockTracker}) every ~15 ticks while a lock ramps (reference
 * {@code MCH_WeaponGuidanceSystem} sends {@code MCH_PacketNotifyLock} on the {@code lockSoundCount} cadence). The
 * server ANTI-SPOOFS it — the sender must actually be riding a vehicle with a lock-on weapon selected — then relays a
 * {@link ClientboundRwrPayload#LOCKED} RWR tone to the target vehicle's crew, but only if that target has flares
 * (reference {@code MCH_MissileDetector} gates on {@code haveFlare()}).
 */
public record ServerboundNotifyLockPayload(int targetId) implements CustomPacketPayload {

    public static final Type<ServerboundNotifyLockPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "notify_lock"));

    public static final StreamCodec<ByteBuf, ServerboundNotifyLockPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerboundNotifyLockPayload::targetId,
            ServerboundNotifyLockPayload::new);

    @Override
    public Type<ServerboundNotifyLockPayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundNotifyLockPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player sender = ctx.player();
            Entity ridden = sender.getVehicle();
            // Anti-spoof: the sender must be aboard an MCH vehicle with a lock-on weapon selected in their seat, so a
            // client cannot spam "you're locked" tones at arbitrary victims without actually being able to lock.
            if (!(ridden instanceof AbstractMchVehicle shooter) || !shooter.hasPassenger(sender)) {
                return;
            }
            int seat = shooter.seatIndexOf(sender);
            if (seat < 0 || !shooter.selectedWeaponIsLockOn(seat)) {
                return;
            }
            // The lock may be on the airframe OR on a crew member (a passenger) — the tracker can pick either. Resolve a
            // passenger to its airframe (reference getAircraft_RiddenOrControl) so the crew is warned in both cases.
            Entity target = sender.level().getEntity(p.targetId());
            AbstractMchVehicle victim = target instanceof AbstractMchVehicle v ? v
                : (target != null && target.getVehicle() instanceof AbstractMchVehicle pv ? pv : null);
            if (victim != null && victim != shooter && victim.haveFlare() && !victim.isDestroyed()) {
                victim.sendRwrToCrew(ClientboundRwrPayload.LOCKED); // only a flare-equipped target has an RWR
            }
        });
    }
}
