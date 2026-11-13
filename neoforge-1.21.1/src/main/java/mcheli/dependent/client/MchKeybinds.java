package mcheli.dependent.client;

import com.mojang.blaze3d.platform.InputConstants;
import mcheli.MCHeli;
import mcheli.dependent.control.ServerboundFoldBladePayload;
import mcheli.dependent.control.ServerboundGearPayload;
import mcheli.dependent.control.ServerboundGunnerModePayload;
import mcheli.dependent.control.ServerboundHoverPayload;
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
 * block. The port's defaults for GUI, SEAT_PREV, FREE_LOOK and GUNNER_MODE are not the reference's: {@code KeyGUI}
 * defaulted to R, {@code KeyExtra} to F (vanilla's swap-offhand, which the port uses to switch weapons),
 * {@code KeyFreeLook} to left Ctrl (vanilla sprint) and {@code KeySwitchGunner} to H ({@code MCH_Config}:196-211,
 * LWJGL 2 key codes). Keys:
 *
 * <ul>
 *   <li>{@link #GUI} (default {@code G}, reference {@code KeyGUI}) — pilot: open the resupply GUI; gunner: next seat.</li>
 *   <li>{@link #SEAT_PREV} (default {@code H}, reference {@code KeyExtra}) — gunner: previous seat; heli pilot: fold
 *       the rotor while parked; plane pilot: toggle VTOL.</li>
 *   <li>{@link #FREE_LOOK} (default {@code LEFT ALT}, reference {@code KeyFreeLook}) — a held modifier: with it down,
 *       the pilot's GUI/PREV keys switch seats (leaving seat 0), and a gunner's grab the pilot seat.</li>
 *   <li>{@link #WEAPON_MODE} (default {@code K}; the reference {@code KeySwitchWeaponMode} default X is vanilla's Load
 *       Toolbar Activator in 1.21) — any seat: switch the selected weapon's mode (cannon HE, rocket HEIAP, AT top attack,
 *       TV guided / unguided).</li>
 *   <li>{@link #GUNNER_MODE} (default {@code R}, reference {@code KeySwitchGunner}) — pilot: toggle gunner mode and
 *       step through the camera positions (see the KeySwitchMode state machine below); a switchable gunner seat: toggle
 *       that seat's gunner mode (it starts on when boarded).</li>
 *   <li>{@link #FREE_LOOK_TOGGLE} (default {@code M}) — pilot of a view-locked aircraft: toggle free look.</li>
 *   <li>{@link #FLARE} (default {@code V}, reference {@code KeyFlare}) — seats 0 and 1: dispense flares.</li>
 *   <li>{@link #ZOOM} (default {@code Z}, reference {@code KeyZoom}) — step the scope zoom while in gunner mode; outside
 *       it the pilot's key opens/closes the hatch, or folds a plane's wing when it has no hatch.</li>
 *   <li>{@link #CAMERA_MODE} (default {@code N}; the reference {@code KeyCameraMode} default C is vanilla's "save
 *       toolbar") — seats 0 and 1: cycle normal / night vision / thermal vision, or switch the search light.</li>
 *   <li>{@link #LANDING_GEAR} (default {@code B}, reference {@code KeyGearUpDown}) — pilot: landing gear up/down.</li>
 *   <li>The vanilla jump key (reference {@code KeySwitchHovering} = SPACE) — heli pilot: toggle hovering mode; tank
 *       driver: brake while held (sent by {@link MchClientInput}).</li>
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
    /** Reference {@code KeyZoom} default Z (LWJGL 44). The port first used C, which is vanilla's save-toolbar key. */
    public static final KeyMapping ZOOM = key("zoom", org.lwjgl.glfw.GLFW.GLFW_KEY_Z);
    public static final KeyMapping CAMERA_MODE = key("camera_mode", org.lwjgl.glfw.GLFW.GLFW_KEY_N);
    /** Pilot free-look TOGGLE (default M) — the reference KeyFreeLook edge toggle. Distinct from {@link #FREE_LOOK}
     *  (LEFT ALT), which stays purely the held seat-switch modifier, so the two roles never collide. */
    public static final KeyMapping FREE_LOOK_TOGGLE = key("free_look_toggle", org.lwjgl.glfw.GLFW.GLFW_KEY_M);
    /** Weapon mode switch (default K) — the reference KeySwitchWeaponMode, whose default X collides with vanilla's Load
     *  Toolbar Activator; K is free in vanilla and in MCHeli. */
    public static final KeyMapping WEAPON_MODE = key("weapon_mode", org.lwjgl.glfw.GLFW.GLFW_KEY_K);
    public static final KeyMapping FLARE = key("flare", org.lwjgl.glfw.GLFW.GLFW_KEY_V);
    /** Landing gear up/down (default B — the reference {@code KeyGearUpDown} default, LWJGL key 48; free in 1.21). */
    public static final KeyMapping LANDING_GEAR = key("landing_gear", org.lwjgl.glfw.GLFW.GLFW_KEY_B);

    /** Client debounce: ticks left before another gunner-mode toggle may be sent (blocks a double-tap double-send). */
    private static int gunnerToggleCooldown;
    /** Same debounce for the hover-mode toggle (synced mode, no client prediction). */
    private static int hoverToggleCooldown;
    /** Was the player seated in an MCHeli vehicle last tick (the jump key's click queue is only ours while seated)? */
    private static boolean wasSeated;

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
        event.register(CAMERA_MODE);
        event.register(FREE_LOOK_TOGGLE);
        event.register(WEAPON_MODE);
        event.register(FLARE);
        event.register(LANDING_GEAR);
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
        boolean cameraMode = drain(CAMERA_MODE);
        boolean freeLookToggle = drain(FREE_LOOK_TOGGLE);
        boolean weaponMode = drain(WEAPON_MODE);
        boolean flare = drain(FLARE);
        boolean gear = drain(LANDING_GEAR);
        // The reference KeySwitchHovering defaults to SPACE (LWJGL 57) — the jump key — and read it raw alongside vanilla,
        // so the port reads the vanilla jump binding itself (jump does nothing while seated in a vehicle), as it does
        // for WASD. Its click queue is ours only while seated: on foot it is left alone for anyone else who reads it.
        // Vanilla never consumes jump clicks (it polls isDown), so the presses made on foot pile up in the queue; the
        // first seated tick drains that backlog without acting, so boarding never toggles hover by itself.
        boolean seated = mc.player != null && mc.player.getVehicle() instanceof AbstractMchVehicle;
        boolean hover = seated && drain(mc.options.keyJump) && wasSeated;
        wasSeated = seated;
        if (gunnerToggleCooldown > 0) {
            gunnerToggleCooldown--; // tick down every client tick, even off a vehicle
        }
        if (hoverToggleCooldown > 0) {
            hoverToggleCooldown--;
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
        // Every pre-check here reads state the client really has (synced or simulated on both sides); a server-only
        // value would read as its default here (the plane's sim throttle once did, so its pilots could never enter
        // gunner mode), so canSwitchGunnerMode reads the synced throttle on a client.
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
        } else if (gunnerToggle && seat > 0 && gunnerToggleCooldown == 0 && v.canSwitchGunnerModeOtherSeat(seat)) {
            // A switchable gunner seat (reference MCH_Client{Heli,Plane,Tank}TickHandler, the non-pilot KeySwitchMode
            // branch -> switchGunnerModeOtherSeat): flip between the seat's gunner camera and the normal seat view (the
            // reference key hint offered "Camera" / "Normal"). The seat's mode is synced from the server, so the same
            // debounce as the pilot's toggle applies.
            PacketDistributor.sendToServer(new ServerboundGunnerModePayload(v.getId()));
            gunnerToggleCooldown = 5;
        }
        // Zoom is client-only (a scope FOV), and only while this seat is in gunner mode (reference gates KeyZoom on
        // getIsGunnerMode; its always-zoom for a UAV flown from a station waits for the UAV station). MchGunnerView owns
        // the doubling zoom state + the FOV modifier. Outside gunner mode the pilot's zoom key is the hatch switch, else
        // a plane's wing fold (MCH_ClientHeliTickHandler:192-206, MCP_ClientPlaneTickHandler:194-215); the server
        // re-checks every gate.
        if (zoom) {
            if (v.isSeatGunnerMode(seat)) {
                MchGunnerView.stepZoom(v);
            } else if (pilot && (v.haveHatch() || v.haveWing())) {
                PacketDistributor.sendToServer(new mcheli.dependent.control.ServerboundHatchPayload(v.getId()));
            }
        }
        // Camera mode / search light (reference KeyCameraMode, MCH_AircraftClientTickHandler:96-114): seats 0 and 1.
        if (cameraMode && seat >= 0 && seat <= 1 && (v.haveSearchLight() || v.canSwitchCameraMode())) {
            PacketDistributor.sendToServer(new mcheli.dependent.control.ServerboundCameraModePayload(v.getId()));
        }

        // Weapon mode (reference KeySwWeaponMode, MCH_AircraftClientTickHandler:212-215: any seat, not on a wreck nor
        // while the pilot is in the resupply GUI): the server switches this seat's selected weapon. The reference plays
        // a click when the mode changes; the client knows the weapon's modes, so it plays that click itself.
        if (weaponMode && seat >= 0 && !v.isPilotReloading() && !v.isDestroyed()) {
            PacketDistributor.sendToServer(new mcheli.dependent.control.ServerboundWeaponModePayload(v.getId()));
            mcheli.agnostic.weapon.MCH_WeaponInfo wi = v.selectedClientWeaponInfo(seat);
            if (wi != null && wi.fixMode <= 0 && mcheli.agnostic.weapon.MCH_WeaponMode.numMode(wi) >= 2) {
                mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
        }

        // Flares/countermeasures (reference KeyFlare, seatId <= 1): the pilot or the co-pilot/gunner may dispense.
        // Client pre-gate uses the SYNCED dispenser state (flareDispenserIdle) — canDeployFlare() reads the server-only
        // flareInfo and would always read idle on the client — to avoid spamming during cooldown; server re-checks.
        if (flare && seat >= 0 && seat <= 1 && v.haveFlare() && v.flareDispenserIdle()) {
            PacketDistributor.sendToServer(new ServerboundUseFlarePayload(v.getId()));
        }

        // Heli hovering mode (reference MCH_ClientHeliTickHandler:166-172, pilot only): the client pre-checks the same
        // canSwitchHoveringMode the server re-checks. The reference switch also levelled the pilot's look; the mode is
        // server-authoritative here, so MchClientRotation levels it when the synced mode actually flips (levelling it
        // now would be overwritten by the hull-locked view until then). (On the tank the same key is the held brake,
        // sent with the control bits by MchClientInput.)
        if (hover && pilot && hoverToggleCooldown == 0 && v instanceof MchHelicopter h && h.canSwitchHoveringMode()) {
            PacketDistributor.sendToServer(new ServerboundHoverPayload(v.getId()));
            hoverToggleCooldown = 5;
        }
        // Landing gear (reference MCH_AircraftClientTickHandler:146-154, pilot only, configs with AddPartLG*): retract
        // when it can fold, else lower when it can unfold; the server re-checks both.
        if (gear && pilot && (v.canFoldLandingGear() || v.canUnfoldLandingGear())) {
            PacketDistributor.sendToServer(new ServerboundGearPayload(v.getId()));
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
