package mcheli.dependent.client;

import mcheli.MCHeli;
import mcheli.dependent.control.MchControllable;
import mcheli.dependent.control.ServerboundControlPayload;
import mcheli.dependent.control.ServerboundWeaponSwitchPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-only GAME-bus subscriber that captures the local rider's control keys each client tick and ships them to
 * the server as a {@link ServerboundControlPayload}. This is the 1.21.1 analogue of the reference
 * {@code MCH_AircraftClientTickHandler.commonPlayerControl}: read the held keys, pack the bitmask, and send on
 * change plus a periodic keepalive so a dropped edge self-heals.
 *
 * <p>It reuses the vanilla movement keybinds (WASD) rather than registering new ones: while riding a vehicle that
 * does NOT claim the controlling passenger (all our demo entities), vanilla makes no use of WASD, so reading them
 * for our control is conflict-free and intuitive. It deliberately avoids jump/sneak — those are the vanilla
 * dismount keys. Mouse look and a dedicated brake key arrive with the rotation increment.
 *
 * <p>The bus is auto-detected per event ({@code ClientTickEvent.Post} is a game-bus event).
 */
@EventBusSubscriber(modid = MCHeli.MODID, value = Dist.CLIENT)
public final class MchClientInput {
    private MchClientInput() {}

    private static int lastBits = -1;
    private static int lastVehicleId = -1;
    private static int keepalive;
    // Weapon-switch is edge-triggered: remember the switch key's last state so we send ONE cycle per press.
    private static boolean switchKeyWasDown;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Entity vehicle = mc.player != null ? mc.player.getVehicle() : null;

        // We are actively driving only when a player exists, no GUI is capturing input, and the ridden entity is
        // controllable. ANY other state (GUI open, dismounted, no player) means "release the controls".
        boolean controlling = mc.player != null && mc.screen == null && vehicle instanceof MchControllable;

        // Weapon cycle (edge-triggered): the vanilla swap-offhand key (default F) is unused while piloting, so reuse
        // it for "next weapon". Only fires on the rising edge and only while actively controlling.
        boolean switchDown = controlling && mc.options.keySwapOffhand.isDown();
        if (switchDown && !switchKeyWasDown && vehicle != null) {
            PacketDistributor.sendToServer(new ServerboundWeaponSwitchPayload(vehicle.getId(), 1));
        }
        switchKeyWasDown = switchDown;

        if (!controlling) {
            // Release: if we were driving something, send ONE all-clear so the server stops applying the last-held
            // keys, then go quiet until control resumes. This is CRITICAL for the GUI-open case — the player is
            // still aboard, so the entity's own clearMomentary() (which only fires with no passengers) would NOT
            // rescue it and the vehicle would run away on the last-sent throttle. Because the player is still a
            // passenger, the server's anti-spoof passenger check accepts this bits=0 packet.
            // (On an actual DISMOUNT the server has already removed the passenger, so this all-clear is rejected by
            // that same check — harmless; there the entity's clearMomentary() is the authoritative reset.)
            if (lastVehicleId != -1) {
                if (mc.player != null) {
                    PacketDistributor.sendToServer(new ServerboundControlPayload(lastVehicleId, 0));
                }
                lastVehicleId = -1;
                lastBits = -1;
            }
            return;
        }

        Options o = mc.options;
        int bits = 0;
        if (o.keyUp.isDown())     bits |= ServerboundControlPayload.THROTTLE_UP;
        if (o.keyDown.isDown())   bits |= ServerboundControlPayload.THROTTLE_DOWN;
        if (o.keyLeft.isDown())   bits |= ServerboundControlPayload.MOVE_LEFT;
        if (o.keyRight.isDown())  bits |= ServerboundControlPayload.MOVE_RIGHT;
        // Raw left-mouse-button state — reliable while riding (vanilla attack handling can consume keyAttack). Gated
        // by 'controlling' above (no GUI, mouse grabbed), so it only reads while actually piloting.
        if (org.lwjgl.glfw.GLFW.glfwGetMouseButton(
                mc.getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            bits |= ServerboundControlPayload.FIRE;
        }

        int vid = vehicle.getId();
        keepalive++;
        // Send on change (bits or target vehicle), plus a ~5-second keepalive to recover from any missed packet.
        if (bits != lastBits || vid != lastVehicleId || keepalive % 100 == 0) {
            PacketDistributor.sendToServer(new ServerboundControlPayload(vid, bits));
            lastBits = bits;
            lastVehicleId = vid;
        }
    }
}
