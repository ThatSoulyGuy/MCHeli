package mcheli.dependent.control;

import io.netty.buffer.ByteBuf;
import mcheli.MCHeli;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.menu.MchVehicleMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The riding GUI's serverbound control channel — the 1.21.1 analogue of the reference's {@code MCH_PacketGuiOpen} /
 * supply packets. Two actions:
 *
 * <ul>
 *   <li>{@link #OPEN} — the rider pressed the GUI key (default {@code G}). The server opens
 *       {@link MchVehicleMenu} for them, but only if they are actually aboard the addressed vehicle.</li>
 *   <li>{@link #RELOAD} — the rider clicked the GUI's Reload button; the server rearms their selected weapon via
 *       {@link AbstractMchVehicle#supplyAmmoFromPlayer}, which enforces the reference's supply rules (grounded,
 *       not already full, {@code RoundItem}s affordable).</li>
 * </ul>
 *
 * <p>The server is the sole authority on both: the client never opens the menu itself and never touches ammo.
 */
public record ServerboundVehicleGuiPayload(int vehicleId, int action, int weapon) implements CustomPacketPayload {

    public static final int OPEN = 0;
    public static final int RELOAD = 1;

    /** An OPEN carries no weapon index. */
    public static ServerboundVehicleGuiPayload open(int vehicleId) {
        return new ServerboundVehicleGuiPayload(vehicleId, OPEN, -1);
    }

    /** Rearm the weapon the GUI is SHOWING (the reference's {@code currentWeaponId}) — not the one selected to fire. */
    public static ServerboundVehicleGuiPayload reload(int vehicleId, int weapon) {
        return new ServerboundVehicleGuiPayload(vehicleId, RELOAD, weapon);
    }

    public static final Type<ServerboundVehicleGuiPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "vehicle_gui"));

    public static final StreamCodec<ByteBuf, ServerboundVehicleGuiPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerboundVehicleGuiPayload::vehicleId,
            ByteBufCodecs.VAR_INT, ServerboundVehicleGuiPayload::action,
            ByteBufCodecs.VAR_INT, ServerboundVehicleGuiPayload::weapon,
            ServerboundVehicleGuiPayload::new);

    @Override
    public Type<ServerboundVehicleGuiPayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundVehicleGuiPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity e = player.level().getEntity(p.vehicleId());
            // Anti-spoof: you may only open/supply the vehicle you are RIDING. Without this, any client could open a
            // menu onto (and drain ammo/fuel slots from) any vehicle in the world by id.
            if (!(e instanceof AbstractMchVehicle v) || player.getVehicle() != v || !(player instanceof ServerPlayer sp)) {
                return;
            }
            // PILOT-ONLY: the reference gates the resupply GUI behind the pilot seat (pc.openGui sits inside if(isPilot),
            // and supplyAmmo only ever acts on getRiddenByEntity()). A gunner's G is the seat-switch key there, not this.
            // Gating here also keeps isPilotReloading consistent — it only watches the pilot's open container.
            if (v.seatIndexOf(sp) != 0) {
                return;
            }
            switch (p.action()) {
                case OPEN -> sp.openMenu(new MenuProvider() {
                    @Override public Component getDisplayName() {
                        return Component.literal(v.displayName());
                    }
                    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player pl) {
                        return new MchVehicleMenu(id, inv, v);
                    }
                }, buf -> buf.writeVarInt(v.getId()));
                case RELOAD -> v.supplyAmmoFromPlayer(sp, p.weapon());
                default -> { }
            }
        });
    }
}
