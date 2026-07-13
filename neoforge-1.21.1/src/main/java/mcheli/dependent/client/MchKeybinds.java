package mcheli.dependent.client;

import com.mojang.blaze3d.platform.InputConstants;
import mcheli.MCHeli;
import mcheli.dependent.control.ServerboundVehicleGuiPayload;
import mcheli.dependent.entity.AbstractMchVehicle;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * MCHeli's keybinds. Currently one: the riding GUI key (reference {@code KeyGUI}, default {@code G}), which asks the
 * server to open {@link mcheli.dependent.client.screen.MchVehicleScreen} for the vehicle you are aboard.
 *
 * <p>The mapping is registered on the MOD bus ({@link RegisterKeyMappingsEvent}); the press is polled on the GAME bus.
 * NeoForge dispatches by event type, so both live here.
 */
@EventBusSubscriber(modid = MCHeli.MODID, value = Dist.CLIENT)
public final class MchKeybinds {
    private MchKeybinds() {}

    public static final KeyMapping GUI = new KeyMapping(
        "key.mcheli.gui",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        org.lwjgl.glfw.GLFW.GLFW_KEY_G,
        "key.categories.mcheli");

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(GUI);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        // consumeClick() drains the queued presses, so this fires ONCE per press (no auto-repeat while held).
        boolean pressed = false;
        while (GUI.consumeClick()) {
            pressed = true;
        }
        if (!pressed || mc.player == null || mc.screen != null) {
            return;
        }
        // Pilot-only, matching the reference (a non-pilot's KeyGUI is the seat-switch key, ported with gunner mode).
        // The server re-checks the seat authoritatively; this just avoids a doomed packet from a gunner seat.
        if (mc.player.getVehicle() instanceof AbstractMchVehicle v && v.seatIndexOf(mc.player) == 0) {
            // The SERVER opens the menu (it owns the container id and the fuel inventory); we only ask.
            PacketDistributor.sendToServer(ServerboundVehicleGuiPayload.open(v.getId()));
        }
    }
}
