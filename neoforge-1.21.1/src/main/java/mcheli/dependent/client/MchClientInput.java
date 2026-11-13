package mcheli.dependent.client;

import mcheli.MCHeli;
import mcheli.dependent.config.MchConfig;
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
 * does NOT claim the controlling passenger (no MCHeli vehicle does), vanilla makes no use of WASD, so reading them
 * for our control is conflict-free and intuitive. Sneak stays the vanilla dismount key; jump does nothing while
 * seated, so it carries the reference's SPACE binding (the tank brake here, the heli hover toggle in
 * {@link MchKeybinds}). Mouse rotation travels separately ({@link MchClientRotation}).
 *
 * <p>Weapon switching: the vanilla swap-offhand key (default F) cycles to the next weapon while seated (the port's stand-in
 * for the reference {@code KeySwitchWeapon1/2}), and, with the client option {@code switchWeaponWithMouseWheel}, the
 * mouse wheel cycles back and forth (reference {@code MCH_ClientTickHandlerBase.updateMouseWheel}). While seated the
 * swap-offhand presses are drained before vanilla sees them, so a weapon switch never also swaps the player's hands.
 *
 * <p>The bus is auto-detected per event ({@code ClientTickEvent.Post} is a game-bus event).
 */
@EventBusSubscriber(modid = MCHeli.MODID, value = Dist.CLIENT)
public final class MchClientInput {
    private MchClientInput() {}

    private static int lastBits = -1;
    private static int lastSettingBits;  // the client-setting bits of the last send, kept in the release packet
    private static int lastVehicleId = -1;
    private static int lastLockTargetId = -1;
    private static int notifyLockTick;   // paces the RWR "you are being locked" packet (~every 15 ticks while locking)
    private static int keepalive;
    // Weapon-switch is edge-triggered: remember the switch key's last state so we send ONE cycle per press.
    private static boolean switchKeyWasDown;

    /**
     * Swallow the vanilla swap-offhand presses while seated in an MCHeli vehicle, before {@code Minecraft.handleKeybinds}
     * (which runs between the Pre and Post client tick) turns each one into a {@code SWAP_ITEM_WITH_OFFHAND} packet. The
     * key stays held, so {@link #onClientTick} still sees its rising edge for the weapon switch.
     */
    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null
            && mc.player.getVehicle() instanceof mcheli.dependent.entity.AbstractMchVehicle) {
            while (mc.options.keySwapOffhand.consumeClick()) {
                // drained: the press is the weapon-switch key here
            }
        }
    }

    /**
     * Mouse-wheel weapon switching (reference {@code MCH_ClientTickHandlerBase.updateMouseWheel}, gated by the client
     * option {@code SwitchWeaponWithMouseWheel}): wheel up selects the previous weapon, wheel down the next, and the turn
     * is taken from the hotbar only when it would actually change the seat's weapon.
     */
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        double wheel = event.getScrollDeltaY();
        if (wheel == 0.0 || mc.player == null || mc.screen != null
            || !mcheli.dependent.config.MchConfig.CLIENT.switchWeaponWithMouseWheel()
            || !(mc.player.getVehicle() instanceof mcheli.dependent.entity.AbstractMchVehicle v)) {
            return;
        }
        int step = wheel > 0.0 ? -1 : 1;
        int seat = v.seatIndexOf(mc.player);
        if (seat >= 0 && v.wouldSwitchWeapon(seat, step)) {
            event.setCanceled(true);
            if (!v.isPilotReloading() && !v.isDestroyed()) {
                PacketDistributor.sendToServer(new ServerboundWeaponSwitchPayload(v.getId(), step));
            }
        }
    }

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

        // Weapon cycle (edge-triggered): the vanilla swap-offhand key (default F) is "next weapon" while seated; its
        // vanilla presses are drained in onClientTickPre. Only fires on the rising edge and only while actively
        // controlling.
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
                    // Keys released, client settings kept (the reference held its cs_* settings on the aircraft).
                    PacketDistributor.sendToServer(new ServerboundControlPayload(lastVehicleId, lastSettingBits, -1));
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
        // The tank brake (reference KeyBrake = KeySwitchHovering = SPACE, held): the vanilla jump key, like WASD above.
        if (vehicle instanceof mcheli.dependent.entity.AbstractMchVehicle bv && bv.hasBrake() && o.keyJump.isDown()) {
            bits |= ServerboundControlPayload.BRAKE;
        }
        // Client settings the reference pushed with MCH_PacketNotifyClientSetting (re-sent on change): the auto-throttle-
        // down preference for this vehicle kind and DismountAll. Sent in every packet, so a config edit applies at once.
        int settings = settingBits(vehicle);
        bits |= settings;
        lastSettingBits = settings;
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
        // fire a guided round at it. The lock resolves client-side (reference + client-authoritative rotation). The
        // weapon is the one THIS seat has selected (synced per seat), so a gunner's guided launcher locks too.
        int lockTargetId = -1;
        if (vehicle instanceof mcheli.dependent.entity.AbstractMchVehicle amv && mc.player != null) {
            int seat = amv.seatIndexOf(mc.player);
            int wIdx = amv.selectedWeaponIndex(seat);
            mcheli.agnostic.weapon.MCH_WeaponInfo wi = amv.selectedClientWeaponInfo(seat);
            MchLockTracker.tick(amv, seat, wIdx, wi, fireHeld);
            lockTargetId = fireHeld ? MchLockTracker.completeTargetId() : -1;

            // RWR: while a lock is building/held, tell the server every ~15 ticks so it can warn the target's crew
            // (reference MCH_PacketNotifyLock cadence). Reset the pacer when there's no lock so the next lock warns fast.
            // The first notice of a lock also starts the server's lock audit, which a guided shot must pass.
            int warnId = MchLockTracker.warningTargetId();
            if (warnId >= 0) {
                if (notifyLockTick++ % 15 == 0) {
                    PacketDistributor.sendToServer(new mcheli.dependent.control.ServerboundNotifyLockPayload(warnId));
                }
            } else {
                notifyLockTick = 0;
            }
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

    /** The client-setting bits for {@code vehicle}: {@code AutoThrottleDown<Kind>} for its config kind and DismountAll
     *  (reference {@code MCH_EntityAircraft.updateClientSettings}:4405-4410). */
    private static int settingBits(Entity vehicle) {
        int bits = 0;
        if (vehicle instanceof mcheli.dependent.entity.AbstractMchVehicle v && v.hostInfo() != null
            && MchConfig.CLIENT.autoThrottleDown(v.hostInfo().getKindName())) {
            bits |= ServerboundControlPayload.AUTO_THROTTLE_DOWN;
        }
        if (MchConfig.CLIENT.dismountAll()) {
            bits |= ServerboundControlPayload.DISMOUNT_ALL;
        }
        return bits;
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
