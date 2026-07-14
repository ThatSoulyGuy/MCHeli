package mcheli.dependent.client;

import mcheli.MCHeli;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.aircraft.MCH_SeatInfo;
import mcheli.dependent.entity.AbstractMchVehicle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Client-only gunner-view state: the scope ZOOM and the seat view-pitch clamp. The port of the reference's
 * {@code MCH_Camera} zoom ({@code zoomCamera}/{@code getFOVMultiplier}) and {@code setRotLimitPitch}.
 *
 * <ul>
 *   <li><b>Zoom</b> — a discrete doubling cycle {@code 1 → 2 → 4 → … → cameraZoom → 1} ({@code zoomCamera}:2506),
 *       applied optically by dividing the FOV ({@code getFOVMultiplier() = super × 1/zoom}). Stepped by the zoom key
 *       only while the seat is in gunner mode; reset to 1 whenever gunner mode ends or the player leaves the seat
 *       (reference resets on every {@code switchGunnerMode}).</li>
 *   <li><b>Pitch clamp</b> — while in gunner mode the operator's view pitch is clamped to the seat's
 *       {@code minPitch/maxPitch} ({@code setRotLimitPitch}, default −30..70), so the crosshair cannot leave the gun's
 *       elevation envelope.</li>
 * </ul>
 */
@EventBusSubscriber(modid = MCHeli.MODID, value = Dist.CLIENT)
public final class MchGunnerView {
    private MchGunnerView() {}

    private static float zoom = 1.0F;         // current optical zoom (>= 1)
    private static boolean wasGunnerMode;     // last tick's gunner-mode state, to reset zoom on the falling edge

    /** The current optical zoom (&ge; 1) — the reference {@code camera.getCameraZoom()} for the HUD {@code cam_zoom}. */
    public static float currentZoom() { return zoom; }

    /**
     * Step the zoom — the reference {@code zoomCamera} ({@code MCH_EntityAircraft:2506}): double each press, but CLAMP
     * to the max and only wrap back to 1 on the press AFTER reaching the max. So {@code CameraZoom = 3} cycles
     * {@code 1 → 2 → 3 → 1} (not {@code 1 → 2 → 1}); a plain doubling would make an odd max unreachable. No-op when the
     * config has no scope ({@code CameraZoom == 1}).
     */
    public static void stepZoom(AbstractMchVehicle v) {
        MCH_AircraftInfo info = v.hostInfo();
        int max = info != null ? info.cameraZoom : 1;
        if (max <= 1) {
            return;
        }
        float z = zoom;
        if (z >= max - 0.01F) {
            z = 1.0F;                     // already at the max -> wrap
        } else {
            z *= 2.0F;
            if (z >= max) {
                z = max;                  // overshoot clamps to the max (reachable next press wraps)
            }
        }
        zoom = z <= max + 0.01F ? z : 1.0F;
    }

    /** The seat the local player must be in for gunner-view effects (or -1). */
    private static AbstractMchVehicle gunnerVehicle(LocalPlayer player) {
        return player != null && player.getVehicle() instanceof AbstractMchVehicle v
            && v.isSeatGunnerMode(v.seatIndexOf(player)) ? v : null;
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (zoom > 1.0F && gunnerVehicle(player) != null) {
            event.setFOV(event.getFOV() / zoom); // optical zoom = narrower FOV
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        AbstractMchVehicle v = gunnerVehicle(player);
        boolean gunner = v != null;

        // Reset zoom whenever gunner mode ends or the player is no longer a gunner (reference resets on toggle).
        if (wasGunnerMode && !gunner) {
            zoom = 1.0F;
        }
        wasGunnerMode = gunner;
        if (!gunner) {
            return;
        }
        // Clamp the operator's view pitch to the seat's elevation envelope (reference setRotLimitPitch).
        MCH_AircraftInfo info = v.hostInfo();
        int seat = v.seatIndexOf(player);
        MCH_SeatInfo si = info != null && seat >= 0 && seat < info.seatList.size() ? info.seatList.get(seat) : null;
        if (si != null) {
            float clamped = Mth.clamp(player.getXRot(), si.minPitch, si.maxPitch);
            if (clamped != player.getXRot()) {
                player.setXRot(clamped);
            }
        }
    }
}
