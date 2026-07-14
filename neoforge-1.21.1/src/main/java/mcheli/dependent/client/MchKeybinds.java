package mcheli.dependent.client;

import com.mojang.blaze3d.platform.InputConstants;
import mcheli.MCHeli;
import mcheli.dependent.control.ServerboundGunnerModePayload;
import mcheli.dependent.control.ServerboundSeatSwitchPayload;
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
 * MCHeli's keybinds and their client dispatch. The port of the reference's {@code MCH_AircraftClientTickHandler} key
 * block. Keys:
 *
 * <ul>
 *   <li>{@link #GUI} (default {@code G}, reference {@code KeyGUI}) — pilot: open the resupply GUI; gunner: next seat.</li>
 *   <li>{@link #SEAT_PREV} (default {@code H}, reference {@code KeyExtra}) — gunner: previous seat.</li>
 *   <li>{@link #FREE_LOOK} (default {@code LEFT ALT}, reference {@code KeyFreeLook}) — a held modifier: with it down,
 *       the pilot's GUI/PREV keys switch seats (leaving seat 0), and a gunner's grab the pilot seat.</li>
 *   <li>{@link #GUNNER_MODE} (default {@code R}) — toggle gunner mode (aim a weapon; see the gunner-mode wiring).</li>
 *   <li>{@link #ZOOM} (default {@code C}) — step the scope zoom while in gunner mode.</li>
 * </ul>
 *
 * <p>The seat decision tree reproduces {@code commonPlayerControl:66-93}. All actions are serverbound and re-checked
 * server-side. Mappings register on the MOD bus; presses are polled on the GAME bus.
 */
@EventBusSubscriber(modid = MCHeli.MODID, value = Dist.CLIENT)
public final class MchKeybinds {
    private MchKeybinds() {}

    public static final KeyMapping GUI = key("gui", org.lwjgl.glfw.GLFW.GLFW_KEY_G);
    public static final KeyMapping SEAT_PREV = key("seat_prev", org.lwjgl.glfw.GLFW.GLFW_KEY_H);
    public static final KeyMapping FREE_LOOK = key("free_look", org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT);
    public static final KeyMapping GUNNER_MODE = key("gunner_mode", org.lwjgl.glfw.GLFW.GLFW_KEY_R);
    public static final KeyMapping ZOOM = key("zoom", org.lwjgl.glfw.GLFW.GLFW_KEY_C);

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.mcheli." + name, KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, code, "key.categories.mcheli");
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(GUI);
        event.register(SEAT_PREV);
        event.register(FREE_LOOK);
        event.register(GUNNER_MODE);
        event.register(ZOOM);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        // consumeClick() drains queued presses so each fires ONCE per press. Drain both seat keys every tick even when
        // we won't act, so a press while a screen is open doesn't queue up and fire on close.
        boolean gui = drain(GUI);
        boolean prev = drain(SEAT_PREV);
        boolean gunnerToggle = drain(GUNNER_MODE);
        boolean zoom = drain(ZOOM);
        if (mc.player == null || mc.screen != null || !(mc.player.getVehicle() instanceof AbstractMchVehicle v)) {
            return;
        }
        int seat = v.seatIndexOf(mc.player);
        boolean pilot = seat == 0;
        boolean freeLook = FREE_LOOK.isDown(); // held modifier, not edge-triggered

        // Gunner-mode toggle (pilot only; the server re-checks the seat + canSwitchGunnerMode). Reference KeySwitchMode.
        if (gunnerToggle && pilot) {
            PacketDistributor.sendToServer(new ServerboundGunnerModePayload(v.getId()));
        }
        // Zoom is client-only (a scope FOV), and only while this seat is in gunner mode (reference gates KeyZoom on
        // getIsGunnerMode). MchGunnerView owns the doubling zoom state + the FOV modifier.
        if (zoom && v.isSeatGunnerMode(seat)) {
            MchGunnerView.stepZoom(v);
        }

        // Reference commonPlayerControl:66-93. FreeLook held turns the seat keys into "leave your seat":
        if (freeLook && (gui || prev)) {
            if (pilot) {
                send(v, gui ? ServerboundSeatSwitchPayload.NEXT : ServerboundSeatSwitchPayload.PREV);
            } else {
                send(v, ServerboundSeatSwitchPayload.GRAB_PILOT); // gunner + freelook + either key -> take the pilot seat
            }
            return;
        }
        // Plain keys: a gunner cycles seats; the pilot's GUI opens the resupply menu (prev does nothing for a pilot).
        if (gui) {
            if (pilot) {
                PacketDistributor.sendToServer(ServerboundVehicleGuiPayload.open(v.getId()));
            } else {
                send(v, ServerboundSeatSwitchPayload.NEXT);
            }
        } else if (prev && !pilot) {
            send(v, ServerboundSeatSwitchPayload.PREV);
        }
    }

    private static boolean drain(KeyMapping k) {
        boolean any = false;
        while (k.consumeClick()) {
            any = true;
        }
        return any;
    }

    private static void send(AbstractMchVehicle v, int action) {
        PacketDistributor.sendToServer(new ServerboundSeatSwitchPayload(v.getId(), action));
    }
}
