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
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
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
    private static int lastLockTargetId = -1;
    private static int keepalive;
    // Weapon-switch is edge-triggered: remember the switch key's last state so we send ONE cycle per press.
    private static boolean switchKeyWasDown;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Entity vehicle = mc.player != null ? mc.player.getVehicle() : null;

        // We are actively driving only when a player exists, no GUI is capturing input, and the ridden entity is
        // controllable. ANY other state (GUI open, dismounted, no player) means "release the controls".
        // ANY seat sends input (a gunner fires + switches weapons); the SERVER drops a non-pilot's drive bits, and
        // the pilot-only keys are additionally gated below so we never even send them from a gunner seat.
        boolean controlling = mc.player != null && mc.screen == null && vehicle instanceof MchControllable;
        // The reference blocks weapon use + weapon switching while the pilot is in the resupply GUI and for a 20-tick
        // tail afterwards (isPilotReloading). Driving is NOT blocked. The server enforces this too.
        boolean armed = controlling && !(vehicle instanceof mcheli.dependent.entity.AbstractMchVehicle v
            && v.isPilotReloading());

        // Weapon cycle (edge-triggered): the vanilla swap-offhand key (default F) is unused while piloting, so reuse
        // it for "next weapon". Only fires on the rising edge and only while actively controlling.
        boolean switchDown = armed && mc.options.keySwapOffhand.isDown();
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
                    PacketDistributor.sendToServer(new ServerboundControlPayload(lastVehicleId, 0, -1));
                }
                lastVehicleId = -1;
                lastBits = -1;
                lastLockTargetId = -1;
            }
            MchLockTracker.clear(); // GUI open / dismounted → drop any in-progress lock
            return;
        }

        Options o = mc.options;
        int bits = 0;
        // Send the drive keys from ANY seat: the SERVER decides who may drive (it drops a non-pilot's drive bits
        // against its own authoritative seat map). Gating this client-side on a synced seat map made driving depend on
        // a packet arriving — when it lagged, no input was sent at all.
        if (o.keyUp.isDown())     bits |= ServerboundControlPayload.THROTTLE_UP;
        if (o.keyDown.isDown())   bits |= ServerboundControlPayload.THROTTLE_DOWN;
        if (o.keyLeft.isDown())   bits |= ServerboundControlPayload.MOVE_LEFT;
        if (o.keyRight.isDown())  bits |= ServerboundControlPayload.MOVE_RIGHT;
        // Free-look parity: the airframe already holds heading client-authoritatively (MchClientRotation), so this is
        // not load-bearing — it just keeps the server sim's MchControlState.freeLook consistent (e.g. plane ground-yaw).
        if (MchFreeLook.active()) bits |= ServerboundControlPayload.FREE_LOOK;
        // Raw left-mouse-button state — reliable while riding (vanilla attack handling can consume keyAttack). Gated
        // by 'controlling' above (no GUI, mouse grabbed), so it only reads while actually piloting.
        boolean fireHeld = armed && org.lwjgl.glfw.GLFW.glfwGetMouseButton(
                mc.getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (fireHeld) {
            bits |= ServerboundControlPayload.FIRE;
        }

        // Missile lock-on: drive the client lock state machine for the local operator's selected weapon (it only ramps
        // while fire is held + a lock-on weapon is selected), then ship the COMPLETED-lock target id so the server can
        // fire a guided round at it. The lock resolves client-side (reference + client-authoritative rotation).
        // KNOWN LIMITATION: selectedClientWeaponInfo()/getSelectedWeaponIndex() read the seat-0 (pilot) synced weapon —
        // the port does not sync per-seat weapon selection to clients (the cockpit HUD sight_type shares this gap). So
        // a GUNNER-operated guided launcher can't build its own lock until per-seat selection is synced; pilot-fired
        // guided missiles (the common case) work correctly.
        int lockTargetId = -1;
        if (vehicle instanceof mcheli.dependent.entity.AbstractMchVehicle amv && mc.player != null) {
            int seat = amv.seatIndexOf(mc.player);
            int wIdx = amv.getSelectedWeaponIndex();
            mcheli.agnostic.weapon.MCH_WeaponInfo wi = amv.selectedClientWeaponInfo();
            MchLockTracker.tick(amv, seat, wIdx, wi, fireHeld);
            lockTargetId = fireHeld ? MchLockTracker.completeTargetId() : -1;
        }

        int vid = vehicle.getId();
        keepalive++;
        // Send on change (bits, target vehicle, or lock target), plus a ~5-second keepalive to recover a missed packet.
        if (bits != lastBits || vid != lastVehicleId || lockTargetId != lastLockTargetId || keepalive % 100 == 0) {
            PacketDistributor.sendToServer(new ServerboundControlPayload(vid, bits, lockTargetId));
            lastBits = bits;
            lastVehicleId = vid;
            lastLockTargetId = lockTargetId;
        }
    }

    // ---- Suppress vanilla left-click while seated in a vehicle ---------------------------------------------------
    // While aboard an MCHeli vehicle the left mouse button IS the weapon-fire control (read raw via GLFW in
    // onClientTick, which does NOT go through the keybind/event path). Vanilla still treats that same click as
    // "attack", so without this a pilot holding fire near terrain also mines blocks (instantly in creative) and
    // swings at nearby mobs. We cancel BOTH vanilla left-click paths only while riding; normal play is untouched.

    /** The initial attack keybind press (arm swing + start-mining + entity melee). */
    @SubscribeEvent
    public static void onAttackInput(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack() && ridingMchVehicle()) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /** Held / creative-instant block breaking is driven through this event (from MultiPlayerGameMode.startDestroyBlock),
     *  which the attack-key cancel above does not cover. Dist.CLIENT subscriber, so this only cancels the local player. */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (ridingMchVehicle()) {
            event.setCanceled(true);
        }
    }

    private static boolean ridingMchVehicle() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null
            && mc.player.getVehicle() instanceof mcheli.dependent.entity.AbstractMchVehicle;
    }
}
