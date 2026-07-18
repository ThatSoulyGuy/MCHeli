package mcheli.dependent.client;

import com.mojang.blaze3d.platform.InputConstants;
import mcheli.MCHeli;
import mcheli.dependent.control.ServerboundFoldBladePayload;
import mcheli.dependent.control.ServerboundGunnerModePayload;
import mcheli.dependent.control.ServerboundUseFlarePayload;
import mcheli.dependent.control.ServerboundSeatSwitchPayload;
import mcheli.dependent.control.ServerboundVehicleGuiPayload;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.entity.MchHelicopter;
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
    /** Pilot free-look TOGGLE (default M) — the reference KeyFreeLook edge toggle. Distinct from {@link #FREE_LOOK}
     *  (LEFT ALT), which stays purely the held seat-switch modifier, so the two roles never collide. */
    public static final KeyMapping FREE_LOOK_TOGGLE = key("free_look_toggle", org.lwjgl.glfw.GLFW.GLFW_KEY_M);
    public static final KeyMapping FLARE = key("flare", org.lwjgl.glfw.GLFW.GLFW_KEY_V);

    /** Client debounce: ticks left before another gunner-mode toggle may be sent (blocks a double-tap double-send). */
    private static int gunnerToggleCooldown;

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
        event.register(FREE_LOOK_TOGGLE);
        event.register(FLARE);
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
        boolean freeLookToggle = drain(FREE_LOOK_TOGGLE);
        boolean flare = drain(FLARE);
        if (gunnerToggleCooldown > 0) {
            gunnerToggleCooldown--; // tick down every client frame, even off a vehicle
        }
        if (mc.player == null || mc.screen != null || !(mc.player.getVehicle() instanceof AbstractMchVehicle v)) {
            return;
        }
        int seat = v.seatIndexOf(mc.player);
        boolean pilot = seat == 0;

        // Free-look toggle (the port's canSwitchFreeLook analogue): only the pilot of a mouse-flown, view-locked
        // aircraft — inert on tanks/emplacements (locksViewToVehicle() == false, already permanently free-look) and on
        // non-pilots (gunners already free-look). Client-only view state; the airframe rotation is client-authoritative.
        if (freeLookToggle && pilot && v.supportsMouseRotation() && v.locksViewToVehicle()) {
            MchFreeLook.toggle();
        }
        boolean freeLook = FREE_LOOK.isDown(); // held modifier, not edge-triggered

        // The reference KeySwitchMode state machine (MCP_ClientPlaneTickHandler:148-168): one key walks the pilot through
        // gunner mode and the configured camera viewpoints. Gunner mode is serverbound (re-checked server-side); the
        // camera index (cameraId) is a local view state. Order matters — it reproduces the reference exactly:
        //   in gunner mode + has alt cameras -> leave gunner mode, jump to camera 1
        //   already on an alt camera         -> advance to the next (wrapping back to the default eye)
        //   can enter gunner mode            -> enter it, reset to the default eye
        //   otherwise has alt cameras        -> jump to camera 1
        //   (nothing switchable: the reference just plays a decline sound)
        // The cooldown gate blocks a fast double-tap from sending a SECOND gunner-mode toggle before the first syncs back
        // (gunner mode is server-authoritative with no client prediction, so isSeatGunnerMode(0) reads stale for ~1-2
        // ticks — a second press would re-evaluate branch 1/3 on the old state and cancel the toggle).
        if (gunnerToggle && pilot && gunnerToggleCooldown == 0) {
            if (v.isSeatGunnerMode(0) && v.canSwitchCameraPos()) {
                PacketDistributor.sendToServer(new ServerboundGunnerModePayload(v.getId())); // leave gunner mode
                v.setViewCameraId(1);
                gunnerToggleCooldown = 5;
            } else if (v.getViewCameraId() > 0) {
                int next = v.getViewCameraId() + 1;
                v.setViewCameraId(next >= v.cameraPosCount() ? 0 : next);
            } else if (v.canSwitchGunnerMode()) {
                PacketDistributor.sendToServer(new ServerboundGunnerModePayload(v.getId())); // enter gunner mode
                v.setViewCameraId(0);
                gunnerToggleCooldown = 5;
            } else if (v.canSwitchCameraPos()) {
                v.setViewCameraId(1);
            }
        }
        // Zoom is client-only (a scope FOV), and only while this seat is in gunner mode (reference gates KeyZoom on
        // getIsGunnerMode). MchGunnerView owns the doubling zoom state + the FOV modifier.
        if (zoom && v.isSeatGunnerMode(seat)) {
            MchGunnerView.stepZoom(v);
        }

        // Flares/countermeasures (reference KeyFlare, seatId <= 1): the pilot or the co-pilot/gunner may dispense.
        // Client pre-gate uses the SYNCED dispenser state (flareDispenserIdle) — canDeployFlare() reads the server-only
        // flareInfo and would always read idle on the client — to avoid spamming during cooldown; server re-checks.
        if (flare && seat >= 0 && seat <= 1 && v.haveFlare() && v.flareDispenserIdle()) {
            PacketDistributor.sendToServer(new ServerboundUseFlarePayload(v.getId()));
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
        // Plain keys: a gunner cycles seats; the pilot's GUI opens the resupply menu, and a heli pilot's PREV key folds
        // or unfolds the rotor (reference KeyExtra) while parked — the server re-checks canSwitchFoldBlades.
        if (gui) {
            if (pilot) {
                PacketDistributor.sendToServer(ServerboundVehicleGuiPayload.open(v.getId()));
            } else {
                send(v, ServerboundSeatSwitchPayload.NEXT);
            }
        } else if (prev) {
            if (!pilot) {
                send(v, ServerboundSeatSwitchPayload.PREV);
            } else if (v instanceof MchHelicopter h && h.canSwitchFoldBlades()) {
                PacketDistributor.sendToServer(new ServerboundFoldBladePayload(v.getId()));
            } else if (v instanceof mcheli.dependent.entity.MchPlane p && p.canSwitchVtol()) {
                // Reference shares KeyExtra between heli rotor-fold and plane VTOL — the pilot toggles the nozzle.
                PacketDistributor.sendToServer(new mcheli.dependent.control.ServerboundVtolPayload(v.getId()));
            }
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
