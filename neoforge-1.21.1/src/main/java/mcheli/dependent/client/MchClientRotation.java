package mcheli.dependent.client;

import mcheli.MCHeli;
import mcheli.agnostic.sim.ControlInput;
import mcheli.dependent.config.MchConfig;
import mcheli.dependent.control.ServerboundRotationPayload;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.entity.MchHelicopter;
import mcheli.dependent.entity.MchPlane;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-only handler giving the local rider MCHeli's mouse-hijack aircraft control — the 1.21.1 analogue of
 * {@code MCH_ClientCommonTickHandler.onRenderTickPre}. It runs the ported {@link mcheli.agnostic.sim.RotationSolver}
 * (via the entity's {@code ControlMapping}) and locks the camera to the aircraft's heading.
 *
 * <p><b>Runs PER RENDER FRAME</b> ({@link RenderFrameEvent.Pre} — which fires right after vanilla applies the mouse
 * and before the world renders, exactly the reference's slot). Vanilla applies the mouse every FRAME, so doing this
 * per-tick rubber-banded (the camera showed vanilla's full turn for several frames, then snapped back). Per frame we
 * (a) take the mouse turn vanilla applied to the local player this frame, captured BEFORE vanilla's ±90 pitch clamp
 * ({@link #onLocalPlayerTurn}, from {@code EntityTurnMixin}), so a steep climb or dive keeps its full vertical input
 * and no programmatic view change (the hover levelling, a view clamp) ever counts as mouse input — the reference
 * likewise read the raw {@code mouseHelper} delta — (b) accumulate it into a virtual stick exactly as the reference
 * {@code updateMouseDelta} did (the raw mouse delta times
 * {@code MouseSensitivity * 0.1}, Y inverted by the vanilla and the MCHeli invert settings, clamp length 40, the
 * optional stick-mode recentring, decay 0.95), (c) run RotationSolver to rotate the AIRCRAFT, and (d) snap the
 * player's view back onto the aircraft heading BEFORE the frame renders, on every axis the aircraft overrides
 * (reference {@code isOverridePlayerYaw/Pitch}; a hovering craft leaves the pilot's view free on the others). The
 * resulting rotation is shipped to the server once per TICK (20 Hz), not per frame, to avoid flooding.
 */
@EventBusSubscriber(modid = MCHeli.MODID, value = Dist.CLIENT)
public final class MchClientRotation {
    private MchClientRotation() {}

    private static final double MAX_STICK = 40.0; // reference getMaxStickLength()
    /** Vanilla's look turn per mouse unit ({@code Entity.turn}: 0.15 deg). The reference fed the RAW mouse delta into the
     *  stick ({@code ms * mouseHelper.deltaX * f2}), so the turn vanilla applied is divided back out. */
    private static final double VANILLA_TURN = 0.15;

    private static double stickX;
    private static double stickY;
    /** The local player's mouse turn since the last frame, in degrees, before vanilla clamps the pitch (see
     *  {@link #onLocalPlayerTurn}). Consumed (and zeroed) once per render frame. */
    private static double turnYaw;
    private static double turnPitch;
    private static int lastVehicleId = -1;
    private static AbstractMchVehicle owned; // the aircraft we've marked as locally rotation-owned
    /** The owned heli's last seen synced hover mode, to level the pilot's view when it flips (-1 = none seen yet). */
    private static int hoverWatchId = -1;
    private static boolean lastHover;
    /** Reference {@code MCH_HudItem.StickX_LPF/StickY_LPF}: the HUD stick indicator is a 4-frame average. */
    private static final mcheli.agnostic.util.MCH_LowPassFilterFloat HUD_STICK_X = new mcheli.agnostic.util.MCH_LowPassFilterFloat(4);
    private static final mcheli.agnostic.util.MCH_LowPassFilterFloat HUD_STICK_Y = new mcheli.agnostic.util.MCH_LowPassFilterFloat(4);
    /** Least horizontal reach of the rider weld: the vehicles' own client tracking range (13 chunks, MchRegistries)
     *  plus a chunk. A vehicle can be tracked farther out than that: vanilla's {@code ChunkMap.TrackedEntity} uses the
     *  largest tracking range among the vehicle's passengers, and a player passenger's is 32 chunks, capped only by the
     *  view distance. {@link #weldReach} widens the reach to match. Tracking ignores height, hence the tall box. */
    private static final double WELD_RANGE = 14 * 16;
    private static final double WELD_RANGE_Y = 1024.0;

    /**
     * The local player was turned by {@code Entity.turn} (vanilla's mouse look), called from {@code EntityTurnMixin} at
     * its HEAD, before the pitch clamp. The arguments are vanilla's turn units (degrees / 0.15), the pitch already
     * multiplied by the vanilla invert. Only the local player's turns count, so a write to its rotation anywhere else
     * is never mistaken for mouse input.
     */
    public static void onLocalPlayerTurn(Entity e, double yRot, double xRot) {
        if (e == Minecraft.getInstance().player) {
            turnYaw += (float) yRot * 0.15F;   // exactly the increments Entity.turn applies, before its clamp
            turnPitch += (float) xRot * 0.15F;
        }
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        // ONE handler, fixed order: rotation solver FIRST, then the rider weld — as two listeners on the same event
        // their order would be JVM-dependent and the weld could see last frame's hull rotation.
        if (mc.player != null && mc.screen == null) {
            doRotation(mc, event.getPartialTick().getGameTimeDeltaTicks(),
                event.getPartialTick().getGameTimeDeltaPartialTick(true));
        } else {
            reset();
        }
        turnYaw = 0.0; // this frame's mouse turn is consumed (or, off a mouse-flown seat, dropped)
        turnPitch = 0.0;
        updateHudStick(mc);
        // The weld LERPS, so it needs the 0..1 residual through the current tick — getGameTimeDeltaTicks() is the
        // per-frame tick DELTA (~0.33 at 60fps), which as a lerp fraction made riders oscillate against the hull.
        weldRiders(mc, event.getPartialTick().getGameTimeDeltaPartialTick(true));
    }

    /** @param pt the frame's tick DELTA (the setAngles partial ticks); @param frameFraction the 0..1 render partial
     *  tick, which the reference stick-mode recentring used. */
    private static void doRotation(Minecraft mc, float pt, float frameFraction) {
        Entity vehicle = mc.player.getVehicle();
        if (!(vehicle instanceof AbstractMchVehicle v)) {
            MchFreeLook.clear(); // dismounted -> free-look must not survive onto the next vehicle/rider
            reset();
            return;
        }
        // Config-driven look-pitch clamp — BEFORE the mouse-rotation gate (an emplacement has no controlMapping, so a
        // clamp placed after it would be dead code). Emplacements clamp always+absolute (MCH_ClientVehicleTickHandler:
        // 54-57); tanks clamp first-person only, relative to the hull-projected pitch (MCH_EntityTank.setAngles:1119-26).
        float[] clamp = v.riderPitchClampNow();
        if (clamp != null && (!v.riderPitchClampFirstPersonOnly() || mc.options.getCameraType().isFirstPerson())) {
            float base = 0.0F;
            if (v.riderPitchClampHullRelative()) {
                float yawDiff = Mth.wrapDegrees(v.getYRot() - mc.player.getYRot());
                base = v.getXRot() * Mth.cos(yawDiff * Mth.DEG_TO_RAD)
                    - v.getRollAngle() * Mth.sin(yawDiff * Mth.DEG_TO_RAD);
            }
            float px = Mth.clamp(mc.player.getXRot(), base + clamp[0], base + clamp[1]);
            px = Mth.clamp(px, -90.0F, 90.0F);
            if (px != mc.player.getXRot()) {
                mc.player.setXRot(px);
                mc.player.xRotO = px; // the reference re-clamps every tick; clamping prev too avoids the 1-frame lerp
            }
        }
        // Only the PILOT flies: a gunner keeps free vanilla look (within their clamps) and never drives the hull.
        if (!v.supportsMouseRotation() || v.pilot() != mc.player) {
            MchFreeLook.clear(); // no longer the pilot of a mouse-flown aircraft -> drop pilot free-look
            reset();
            return;
        }

        // Mark this aircraft as locally rotation-owned (its entity tick then keeps our rotation, not the server's).
        if (owned != null && owned != v) {
            owned.setLocalRotationOwned(false);
        }
        v.setLocalRotationOwned(true);
        owned = v;

        // Reference switchHoveringMode (MCH_EntityAircraft:5334-5337) levels the rider's view pitch on every hover
        // switch. The mode is server-authoritative here, so level it when the synced mode flips: entering hover frees the
        // view pitch, which then starts level. The stick reads the raw mouse turn, so this is no stick input.
        if (v instanceof MchHelicopter h) {
            boolean hover = h.isHoveringMode();
            if (h.getId() == hoverWatchId && hover != lastHover) {
                mc.player.setXRot(0.0F);
                mc.player.xRotO = 0.0F;
            }
            hoverWatchId = h.getId();
            lastHover = hover;
        }

        // GROUND vehicle (tank): steer the hull with A/D but keep the free vanilla mouse-look — no stick accumulation,
        // no camera lock. The rotation mapping ignores the (zero) stick and turns the hull in onUpdateAngles; the yaw
        // is shipped to the server by onClientTick (below) exactly like the aircraft. (The look-pitch clamp already ran
        // above, before the supportsMouseRotation gate.)
        if (!v.locksViewToVehicle()) {
            lastVehicleId = vehicle.getId();
            stickX = 0.0;
            stickY = 0.0;
            // Pass W/S (throttle up/down) too: TankControlMapping.onUpdateAngles flips the A/D steer direction while
            // reversing (throttleDown) so the hull turns intuitively in reverse — hardcoding them false lost that.
            v.applyClientRotation(driveInput(mc, v, pt));
            return;
        }
        MchConfig.Client cfg = MchConfig.CLIENT;
        boolean flightSim = cfg.mouseControlFlightSimMode(); // read only by the plane mapping (MCP_EntityPlane:259-327)

        // FREE-LOOK: the pilot sweeps the camera/head around the cockpit while the airframe HOLDS its heading (the
        // reference isFreeLookMode). Zero the virtual stick so the mouse no longer steers, and pass freeLook=true so
        // RotationSolver DROPS the stick but still runs onUpdateAngles (auto-level, A/D air-bank, plane ground-yaw) —
        // the hull holds its heading. Crucially, do NOT lockCamera: vanilla mouse-look then turns the player's head
        // freely. Forcing lastVehicleId=-1 makes the frame AFTER free-look is released take the reseed branch below,
        // which zeros the stick and re-welds the camera to the hull with no spurious delta — the reference instant
        // snap-back. Ownership stays true (set above), so this frame's held heading still ships to the server.
        if (MchFreeLook.active()) {
            stickX = 0.0;
            stickY = 0.0;
            v.applyClientRotation(new ControlInput(0, 0,
                false, false, mc.options.keyLeft.isDown(), mc.options.keyRight.isDown(), false,
                true, flightSim, false, pt));
            lastVehicleId = -1;
            return;
        }

        // Just mounted / switched: seed the camera-follow baseline, skip this frame's delta.
        if (vehicle.getId() != lastVehicleId) {
            lastVehicleId = vehicle.getId();
            stickX = 0.0;
            stickY = 0.0;
            lockCamera(mc, v.getYRot(), v.getXRot());
            return;
        }

        // Mouse delta this frame = vanilla's mouse turn of the player, taken before its ±90 pitch clamp (lockCamera puts
        // the player at the hull pitch every frame, so near vertical the clamped turn lost most of the input).
        double dYaw = turnYaw;
        double dPitch = turnPitch;

        // Reference updateMouseDelta (MCH_ClientCommonTickHandler:250-292). Stick mode (MouseControlStickModeHeli/Plane)
        // recentres only a stick already near the middle, so a deflected stick holds its position.
        boolean stickMode = v instanceof MchHelicopter ? cfg.mouseControlStickModeHeli()
            : v instanceof MchPlane && cfg.mouseControlStickModePlane();
        if (stickMode) {
            if (Math.abs(stickX) < MAX_STICK * 0.2) {
                stickX *= 1.0F - 0.15F * frameFraction;
            }
            if (Math.abs(stickY) < MAX_STICK * 0.2) {
                stickY *= 1.0F - 0.15F * frameFraction;
            }
        }
        // The raw delta (vanilla's turn / 0.15) times ms = MouseSensitivity * 0.1. Vertical: the reference added the
        // UPWARD mouse motion times the vanilla invert times InvertMouse; vanilla's pitch turn is already the downward
        // motion times the vanilla invert, so negate it and apply only the MCHeli invert. Mouse up = nose up.
        double gain = cfg.mouseSensitivity() * 0.1 / VANILLA_TURN;
        double invert = cfg.invertMouse() ? -1.0 : 1.0;
        stickX += dYaw * gain;
        stickY += -dPitch * gain * invert;
        double len = Math.sqrt(stickX * stickX + stickY * stickY);
        if (len > MAX_STICK) {
            stickX = stickX / len * MAX_STICK;
            stickY = stickY / len * MAX_STICK;
        }

        // Run the ported setAngles: stick -> aircraft yaw/pitch/roll. A/D feed onUpdateAngles (heli air-bank /
        // plane rudder-roll, or the flight-sim key yaw). partialTicks = this frame's tick delta (clamped + low-passed
        // inside RotationSolver).
        ControlInput in = new ControlInput(stickX, stickY,
            false, false, mc.options.keyLeft.isDown(), mc.options.keyRight.isDown(), false,
            false, flightSim, false, pt);
        v.applyClientRotation(in);

        // Lock the camera to the aircraft's new heading BEFORE the frame renders (no rubber-band) — on the axes the
        // aircraft overrides (reference setAngles:1259-1288). On a free axis (a hovering heli's pitch, a gunner-mode
        // plane's yaw and pitch) the player keeps vanilla's own mouse look, which the reference reproduced with
        // player.setAngles(delta) (identical at the default sensitivity).
        lockCamera(mc, v.overridesPilotYaw() ? v.getYRot() : mc.player.getYRot(),
            v.overridesPilotPitch() ? v.getXRot() : mc.player.getXRot());

        // Decay the stick toward centre so releasing the mouse stops the rotation; stick mode only decays a stick
        // within 10% of the middle (reference onRenderTickPre:395-398).
        if (!stickMode || Math.sqrt(stickX * stickX + stickY * stickY) < MAX_STICK * 0.1) {
            stickX *= 0.95;
            stickY *= 0.95;
        }
    }

    /**
     * The local driver's keys as the reference client saw them: W/S/A/D, plus — for a vehicle with a brake (the tank)
     * — the vanilla jump key as the held brake, which cuts W/S and, once the hull has all but stopped, A/D too
     * ({@code MCH_AircraftClientTickHandler}:171-183). Also the client's auto-throttle-down setting for its kind.
     */
    private static ControlInput driveInput(Minecraft mc, AbstractMchVehicle v, float pt) {
        boolean brake = v.hasBrake() && mc.options.keyJump.isDown();
        boolean steerLocked = brake && v.brakeLocksSteering();
        boolean autoThrottleDown = v.hostInfo() != null && MchConfig.CLIENT.autoThrottleDown(v.hostInfo().getKindName());
        return new ControlInput(0, 0,
            !brake && mc.options.keyUp.isDown(), !brake && mc.options.keyDown.isDown(),
            !steerLocked && mc.options.keyLeft.isDown(), !steerLocked && mc.options.keyRight.isDown(), brake,
            false, false, autoThrottleDown, pt);
    }

    /**
     * Per-frame rider RE-WELD — the 1.21.1 port of {@code MCH_EntityAircraft.setupAllRiderRenderPosition} (:3242-3257):
     * every render frame, every seated entity is snapped to its attachment computed from the LERPED hull position, and
     * its own interpolation baseline is killed (:3253-3255) so riders render exactly welded to the seat/turret with
     * zero lag (vanilla only re-seats passengers per TICK, so a fast turret slew visibly trailed its gunner). The
     * local first-person player is skipped — CameraMixin welds the eye exactly and must not be double-written.
     */
    private static void weldRiders(Minecraft mc, float pt) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        // A class-filtered box query visits only the vehicles in the loaded entity sections in range, not every client
        // entity, each frame (as MchClientSound and MchSearchLights do).
        double reach = weldReach(mc);
        for (AbstractMchVehicle v : mc.level.getEntitiesOfClass(AbstractMchVehicle.class,
                mc.player.getBoundingBox().inflate(reach, WELD_RANGE_Y, reach))) {
            if (v.getPassengers().isEmpty()) {
                continue;
            }
            double hx = Mth.lerp((double) pt, v.xo, v.getX());
            double hy = Mth.lerp((double) pt, v.yo, v.getY());
            double hz = Mth.lerp((double) pt, v.zo, v.getZ());
            for (Entity p : v.getPassengers()) {
                if (p == mc.player && firstPerson) {
                    continue;
                }
                net.minecraft.world.phys.Vec3 att = v.riderAttachment(p, pt);
                double px = hx + att.x;
                double py = hy + att.y;
                double pz = hz + att.z;
                p.setPos(px, py, pz);
                p.xo = px; p.yo = py; p.zo = pz;       // kill the rider's own render lerp (reference :3253-3255)
                p.xOld = px; p.yOld = py; p.zOld = pz;
            }
        }
    }

    /** The horizontal reach of {@link #weldRiders}: as far as this client can be tracking a crewed vehicle. The server
     *  tracks an entity out to {@code min(effective range, view distance * 16)} ({@code ChunkMap.TrackedEntity
     *  .updatePlayer}), where a player passenger raises a vehicle's effective range to 32 chunks; the client's
     *  effective render distance is that view distance (its own render distance, capped by the server's), plus a chunk
     *  of slack. Never less than {@link #WELD_RANGE}, the range of a vehicle with no player aboard. */
    private static double weldReach(Minecraft mc) {
        return Math.max(WELD_RANGE, (mc.options.getEffectiveRenderDistance() + 1) * 16.0);
    }

    /** Snap the player's view (and its interpolation baseline) onto the aircraft heading. */
    private static void lockCamera(Minecraft mc, float yaw, float pitch) {
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.setYHeadRot(yaw);
        mc.player.yRotO = yaw;   // no interpolation through vanilla's transient mouse-turn
        mc.player.xRotO = pitch;
        mc.player.yHeadRotO = yaw;
    }

    // NOTE: the first-person camera is fully parented to the vehicle (position + full yaw/pitch/roll) by the
    // client-only CameraMixin, NOT here — Camera.setup adds the eye on world-Y with no public hook, so ComputeCameraAngles
    // (angles only) could not follow a bank/loop. lockCamera above still drives the PLAYER's yaw/pitch (the look the
    // server aims with and the free-axis view); the Mixin overrides what the camera actually shows.

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // Ship the client-authoritative rotation to the server at tick rate (physics reads it; syncs to others).
        if (owned != null && !owned.isRemoved()) {
            // First the per-tick client half of onUpdateAircraft on the rotation-owning client (hover pitch levelling,
            // the tank's client throttle copy), so this tick's packet carries its result.
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.screen == null) {
                owned.clientControlTick(driveInput(mc, owned, 1.0F));
            }
            PacketDistributor.sendToServer(
                new ServerboundRotationPayload(owned.getId(), owned.getYRot(), owned.getXRot(), owned.getRollAngle()));
        }
    }

    private static void reset() {
        if (owned != null) {
            owned.setLocalRotationOwned(false);
            owned = null;
        }
        lastVehicleId = -1;
        hoverWatchId = -1;
        stickX = 0.0;
        stickY = 0.0;
        // NOTE: free-look is deliberately NOT cleared here. reset() also runs every frame a screen is open (see
        // onRenderFrame), and the reference free-look persists across the pause/chat/inventory/resupply GUIs. It is
        // cleared instead at the genuine "no longer a free-lookable pilot" exits in doRotation (dismount / lost seat).
    }

    // ---- HUD control-stick indicator ----
    // The accumulated virtual stick (mouse position, clamp 40, decay 0.95) IS what the reference's stick_x/stick_y HUD
    // variables show. MCH_HudItem.updateStick (:433-437), once per HUD frame: stick_x = the 4-frame average of
    // getCurrentStickX() / getMaxStickLength(), stick_y = the 4-frame average of -getCurrentStickY() / getMaxStickLength().
    // getCurrentStickY multiplies both inverts back out of the stick, so it is the PHYSICAL mouse-up amount, and the
    // minus makes stick_y NEGATIVE for mouse up: the configs' '83+8+stick_y*12' dot moves UP with the mouse (and right
    // with mouse-right, +X). Both decay to centre when the mouse is still; 0 when not mouse-rotating a vehicle.

    /** Once per render frame, after the stick update (the reference updated the filters once per HUD draw, after the
     *  frame's mouse delta): feed the reference's 4-frame low-pass filters. */
    private static void updateHudStick(Minecraft mc) {
        double inv = mc.options.invertYMouse().get() ? -1.0 : 1.0;
        if (MchConfig.CLIENT.invertMouse()) {
            inv = -inv;
        }
        HUD_STICK_X.put((float) (stickX / MAX_STICK));
        HUD_STICK_Y.put((float) (-stickY * inv / MAX_STICK)); // -getCurrentStickY() / getMaxStickLength()
    }

    /** Control-stick indicator X in [-1,1] for the HUD (reference {@code stick_x}: mouse right is +X). */
    public static double hudStickX() {
        return clampUnit(HUD_STICK_X.getAvg());
    }

    /** Control-stick indicator Y in [-1,1] for the HUD (reference {@code stick_y} = {@code -getCurrentStickY()/40},
     *  averaged: the stick times the vanilla and the MCHeli invert, negated, so mouse UP is NEGATIVE and the dot rises). */
    public static double hudStickY() {
        return clampUnit(HUD_STICK_Y.getAvg());
    }

    private static double clampUnit(double v) {
        return v < -1.0 ? -1.0 : (v > 1.0 ? 1.0 : v);
    }
}
