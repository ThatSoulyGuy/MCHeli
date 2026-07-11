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
        if (mc.player == null || mc.screen != null) {
            reset();
            return;
        }
        Entity vehicle = mc.player.getVehicle();
        if (!(vehicle instanceof AbstractMchVehicle v) || !v.supportsMouseRotation()) {
            reset();
            return;
        }

        // Mark this aircraft as locally rotation-owned (its entity tick then keeps our rotation, not the server's).
        if (owned != null && owned != v) {
            owned.setLocalRotationOwned(false);
        }
        v.setLocalRotationOwned(true);
        owned = v;

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
        float pt = event.getPartialTick().getGameTimeDeltaTicks();
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
    }
}
