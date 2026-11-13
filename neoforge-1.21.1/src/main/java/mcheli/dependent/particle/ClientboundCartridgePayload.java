package mcheli.dependent.particle;

import mcheli.MCHeli;
import mcheli.agnostic.weapon.MCH_Cartridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * One ejected shell casing. The reference spawns casings on the client only ({@code MCH_EntityCartridge.spawnCartridge}
 * is {@code @SideOnly(CLIENT)}); the port fires on the server, so the server sends this per shot and each nearby
 * client spawns its own client-side casing ({@code MchCartridge.spawn} on a client level). The casing's
 * {@code SetCartridge} values travel with it, so its look and physics are the firing server's config.
 *
 * @param cfg        the weapon's {@code SetCartridge} config (model/texture name, impulse, angles, bounce, gravity,
 *                   scale)
 * @param gunYaw     the firing yaw (degrees), before the config's own yaw offset
 * @param gunPitch   the firing pitch (degrees), before the config's own pitch offset
 * @param mx         the firing vehicle's motion, which the casing inherits
 */
public record ClientboundCartridgePayload(MCH_Cartridge cfg, double x, double y, double z, float gunYaw,
                                          float gunPitch, float mx, float my, float mz) implements CustomPacketPayload {

    public static final Type<ClientboundCartridgePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "cartridge"));

    public static final StreamCodec<FriendlyByteBuf, ClientboundCartridgePayload> STREAM_CODEC =
        StreamCodec.ofMember(ClientboundCartridgePayload::write, ClientboundCartridgePayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.cfg.name, 256);
        buf.writeFloat(this.cfg.acceleration);
        buf.writeFloat(this.cfg.yaw);
        buf.writeFloat(this.cfg.pitch);
        buf.writeFloat(this.cfg.bound);
        buf.writeFloat(this.cfg.gravity);
        buf.writeFloat(this.cfg.scale);
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeFloat(this.gunYaw);
        buf.writeFloat(this.gunPitch);
        buf.writeFloat(this.mx);
        buf.writeFloat(this.my);
        buf.writeFloat(this.mz);
    }

    private static ClientboundCartridgePayload read(FriendlyByteBuf buf) {
        MCH_Cartridge cfg = new MCH_Cartridge(buf.readUtf(256), buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readFloat(), buf.readFloat(), buf.readFloat());
        return new ClientboundCartridgePayload(cfg, buf.readDouble(), buf.readDouble(), buf.readDouble(),
            buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public Type<ClientboundCartridgePayload> type() {
        return TYPE;
    }

    public static void handle(ClientboundCartridgePayload p, IPayloadContext ctx) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return; // clientbound only; the guard keeps a dedicated server from linking the client class
        }
        ctx.enqueueWork(() -> mcheli.dependent.client.particle.MchClientEffects.spawnCartridge(p));
    }
}
