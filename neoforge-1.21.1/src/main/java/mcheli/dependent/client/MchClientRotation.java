package mcheli.dependent.client;

import mcheli.MCHeli;
import mcheli.agnostic.sim.ControlInput;
import mcheli.dependent.control.ServerboundRotationPayload;
import mcheli.dependent.entity.AbstractMchVehicle;
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
 * (a) read how far vanilla turned the player since the heading we locked to — the mouse delta — (b) accumulate it
 * into a virtual stick (clamp length 40, decay 0.95), (c) run RotationSolver to rotate the AIRCRAFT, and (d) snap the
 * player's view back onto the aircraft heading BEFORE the frame renders, so the camera only ever shows the aircraft.
 * The resulting rotation is shipped to the server once per TICK (20 Hz), not per frame, to avoid flooding.
 */
@EventBusSubscriber(modid = MCHeli.MODID, value = Dist.CLIENT)
public final class MchClientRotation {
    private MchClientRotation() {}

    private static final double MAX_STICK = 40.0; // reference getMaxStickLength()

    private static double stickX;
    private static double stickY;
    private static float lockedYaw;
    private static float lockedPitch;
    private static int lastVehicleId = -1;
    private static AbstractMchVehicle owned; // the aircraft we've marked as locally rotation-owned

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        // ONE handler, fixed order: rotation solver FIRST, then the rider weld — as two listeners on the same event
        // their order would be JVM-dependent and the weld could see last frame's hull rotation.
        if (mc.player != null && mc.screen == null) {
            doRotation(mc, event.getPartialTick().getGameTimeDeltaTicks());
        } else {
            reset();
        }
        // The weld LERPS, so it needs the 0..1 residual through the current tick — getGameTimeDeltaTicks() is the
        // per-frame tick DELTA (~0.33 at 60fps), which as a lerp fraction made riders oscillate against the hull.
        weldRiders(mc, event.getPartialTick().getGameTimeDeltaPartialTick(true));
    }

    private static void doRotation(Minecraft mc, float pt) {
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
            v.applyClientRotation(new ControlInput(0, 0, 0, 0,
                mc.options.keyUp.isDown(), mc.options.keyDown.isDown(),
                mc.options.keyLeft.isDown(), mc.options.keyRight.isDown(), false, false, false, false, false, pt));
            return;
        }

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
            v.applyClientRotation(new ControlInput(0, 0, 0, 0,
                false, false, mc.options.keyLeft.isDown(), mc.options.keyRight.isDown(), false,
                true, false, false, false, pt));
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

        // Mouse delta this frame = how far vanilla turned the player away from the heading we locked to.
        double dYaw = Mth.wrapDegrees(mc.player.getYRot() - lockedYaw);
        double dPitch = mc.player.getXRot() - lockedPitch;

        // Accumulate into the virtual stick and clamp its vector length (reference updateMouseDelta).
        stickX += dYaw;
        stickY += dPitch;
        double len = Math.sqrt(stickX * stickX + stickY * stickY);
        if (len > MAX_STICK) {
            stickX = stickX / len * MAX_STICK;
            stickY = stickY / len * MAX_STICK;
        }

        // Run the ported setAngles: stick -> aircraft yaw/pitch/roll. A/D feed onUpdateAngles (heli air-bank /
        // plane rudder-roll). partialTicks = this frame's tick fraction (clamped + low-passed inside RotationSolver).
        ControlInput in = new ControlInput(stickX, stickY, 0, 0,
            false, false, mc.options.keyLeft.isDown(), mc.options.keyRight.isDown(), false,
            false, false, false, false, pt);
        v.applyClientRotation(in);

        // Lock the camera to the aircraft's new heading BEFORE the frame renders (no rubber-band).
        lockCamera(mc, v.getYRot(), v.getXRot());

        // Decay the stick toward centre so releasing the mouse stops the rotation (reference non-stick-mode).
        stickX *= 0.95;
        stickY *= 0.95;
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
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof AbstractMchVehicle v) || v.getPassengers().isEmpty()) {
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

    /** Snap the player's view (and its interpolation baseline) onto the aircraft heading. */
    private static void lockCamera(Minecraft mc, float yaw, float pitch) {
        lockedYaw = yaw;
        lockedPitch = pitch;
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.setYHeadRot(yaw);
        mc.player.yRotO = yaw;   // no interpolation through vanilla's transient mouse-turn
        mc.player.xRotO = pitch;
        mc.player.yHeadRotO = yaw;
    }

    // NOTE: the first-person camera is fully parented to the vehicle (position + full yaw/pitch/roll) by the
    // client-only CameraMixin, NOT here — Camera.setup adds the eye on world-Y with no public hook, so ComputeCameraAngles
    // (angles only) could not follow a bank/loop. lockCamera above still drives the PLAYER's yaw/pitch for the mouse
    // delta baseline; the Mixin overrides what the camera actually shows.

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // Ship the client-authoritative rotation to the server at tick rate (physics reads it; syncs to others).
        if (owned != null && !owned.isRemoved()) {
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
        stickX = 0.0;
        stickY = 0.0;
        // NOTE: free-look is deliberately NOT cleared here. reset() also runs every frame a screen is open (see
        // onRenderFrame), and the reference free-look persists across the pause/chat/inventory/resupply GUIs. It is
        // cleared instead at the genuine "no longer a free-lookable pilot" exits in doRotation (dismount / lost seat).
    }

    // ---- HUD control-stick indicator ----
    // The accumulated virtual stick (mouse position, clamp 40, decay 0.95) IS what the reference's stick_x/stick_y HUD
    // variables show: MCH_HudItem.updateStick() = getCurrentStickX/Y() / getMaxStickLength(). Normalized to [-1,1] here;
    // it decays to centre when the mouse is still. Sign: mouse-right -> +X (dot right); mouse-up lowers the player pitch
    // so stickY goes negative -> -Y, which the config draws as the dot moving UP. 0 when not mouse-rotating a vehicle.

    /** Control-stick indicator X in [-1,1] for the HUD (reference {@code stick_x}). */
    public static double hudStickX() {
        return clampUnit(stickX / MAX_STICK);
    }

    /** Control-stick indicator Y in [-1,1] for the HUD (reference {@code stick_y}); mouse-up gives a negative value so
     *  the configured indicator dot rises. */
    public static double hudStickY() {
        return clampUnit(stickY / MAX_STICK);
    }

    private static double clampUnit(double v) {
        return v < -1.0 ? -1.0 : (v > 1.0 ? 1.0 : v);
    }
}
