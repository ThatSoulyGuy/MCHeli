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
 *
 * <p>The notice is also what the server's lock audit ({@link mcheli.agnostic.weapon.MCH_LockAudit}) learns the lock
 * candidate and its start from, before it lets the seat fire a guided round at it. The relay itself is rate-limited per
 * seat and needs the target within the seat's lock range, so a modified client cannot sound the warning at any
 * flare-equipped vehicle in the level.
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
            Entity target = sender.level().getEntity(p.targetId());
            if (target == null || !shooter.noteLockNotice(seat, sender, target)) {
                return; // out of lock range, or inside the relay interval
            }
            // The lock may be on the airframe OR on a crew member (a passenger) — the tracker can pick either. Resolve a
            // passenger to its airframe (reference getAircraft_RiddenOrControl) so the crew is warned in both cases.
            AbstractMchVehicle victim = target instanceof AbstractMchVehicle v ? v
                : (target.getVehicle() instanceof AbstractMchVehicle pv ? pv : null);
            if (victim != null && victim != shooter && victim.haveFlare() && !victim.isDestroyed()) {
                victim.sendRwrToCrew(ClientboundRwrPayload.LOCKED); // only a flare-equipped target has an RWR
            }
        });
    }
}
