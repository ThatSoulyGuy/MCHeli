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
 * Serverbound client-authoritative rotation for a piloted aircraft — the 1.21.1 analogue of the reference
 * {@code MCH_PacketIndRotation}. Rotation in MCHeli is computed on the local rider's client (the mouse-driven
 * {@code setAngles} / {@link mcheli.agnostic.sim.RotationSolver}); this ships the resulting absolute yaw/pitch/roll
 * to the server, which stores them (the physics reads them, and they sync to OTHER clients). Position stays
 * server-authoritative — rotation and position travel on separate packets, exactly as in the reference.
 */
public record ServerboundRotationPayload(int vehicleId, float yaw, float pitch, float roll) implements CustomPacketPayload {

    public static final Type<ServerboundRotationPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "rotation"));

    public static final StreamCodec<ByteBuf, ServerboundRotationPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerboundRotationPayload::vehicleId,
            ByteBufCodecs.FLOAT, ServerboundRotationPayload::yaw,
            ByteBufCodecs.FLOAT, ServerboundRotationPayload::pitch,
            ByteBufCodecs.FLOAT, ServerboundRotationPayload::roll,
            ServerboundRotationPayload::new);

    @Override
    public Type<ServerboundRotationPayload> type() {
        return TYPE;
    }

    /**
     * Server-side handler: apply the rider's computed rotation to the addressed vehicle (if the sender is riding it).
     * yaw/pitch go on the vanilla fields (the entity tracker syncs them to other clients); roll goes through the
     * entity's synced roll accessor. Runs on the main thread via {@link IPayloadContext#enqueueWork}.
     */
    public static void handle(ServerboundRotationPayload p, IPayloadContext ctx) {
        // Reject non-finite angles from a modified client: NaN/Inf would corrupt the server entity's position
        // (the heli's pitch^3 thrust term) and broadcast NaN roll to every tracking client via DATA_ROLL.
        if (!Float.isFinite(p.yaw()) || !Float.isFinite(p.pitch()) || !Float.isFinite(p.roll())) {
            return;
        }
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity e = player.level().getEntity(p.vehicleId());
            // Only the PILOT (seat 0) may steer. Without the seat check a gunner's client-authoritative rotation packets
            // would turn the whole vehicle, defeating the drive gate on the control payload.
            if (e instanceof AbstractMchVehicle v && v.supportsMouseRotation() && v.seatIndexOf(player) == 0) {
                v.applyServerRotation(p.yaw(), p.pitch(), p.roll());
            }
        });
    }
}
