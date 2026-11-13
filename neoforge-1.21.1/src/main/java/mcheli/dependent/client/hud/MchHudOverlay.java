package mcheli.dependent.client.hud;

import mcheli.MCHeli;
import mcheli.agnostic.hud.MCH_Hud;
import mcheli.agnostic.hud.MCH_HudManager;
import mcheli.dependent.client.MchCameraModeView;
import mcheli.dependent.config.MchConfig;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.entity.MchHelicopter;
import mcheli.dependent.entity.MchPlane;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Registers the MCHeli cockpit HUD as a GUI layer above the crosshair. It fires only while the in-game HUD renders (no
 * full screen open — the {@link net.minecraft.client.gui.LayeredDraw.Layer} contract) and, when the local player is
 * riding an {@link AbstractMchVehicle}, resolves the HUD config of the seat that player occupies, builds a
 * {@link MchHudVarState} from the live entity (once per frame, like the reference {@code MCH_HudItem.update}), and draws
 * it through {@link NeoHudRenderer}. Everything shown is config-driven from the vehicle's {@code HUD =} list + the
 * {@code hud/*.txt} definitions.
 *
 * <p>As in the reference {@code MCH_GuiHeli/MCP_GuiPlane/MCH_GuiTank/MCH_GuiVehicle.drawGui}: the night-vision noise
 * draws first in every view; the HUD draws in first person, and in third person only with the client option
 * {@code displayHUDThirdPerson}; a helicopter or plane pilot in gunner mode sees the seat-1 (gunner) HUD.
 */
@EventBusSubscriber(modid = MCHeli.MODID, value = Dist.CLIENT)
public final class MchHudOverlay {
    private MchHudOverlay() {}

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR,
            ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "vehicle_hud"),
            MchHudOverlay::render);
    }

    /**
     * Suppress the vanilla crosshair while piloting an MCHeli vehicle: the cockpit HUD draws its own configured
     * reticle (the {@code common_pilot} crosshair), so the vanilla white {@code +} would just be a stray, unconfigured
     * element sitting in its centre. This mirrors the reference, which renders its HUD in place of the vanilla one.
     */
    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (!event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null
            && mc.player.getVehicle() instanceof AbstractMchVehicle vehicle && !vehicle.isDestroyed()
            && vehicle.hudName(hudSeat(vehicle, Math.max(0, vehicle.seatIndexOf(mc.player)))) != null) {
            event.setCanceled(true);
        }
    }

    /** The HUD list index for a seat: the pilot of a helicopter or plane in gunner mode uses the gunner HUD (reference
     *  {@code if (seatID == 0 && getIsGunnerMode(player)) drawHud(ac, player, 1)} in MCH_GuiHeli:38 / MCP_GuiPlane:37;
     *  the tank and vehicle GUIs always use the seat's own). */
    private static int hudSeat(AbstractMchVehicle vehicle, int seat) {
        boolean aircraft = vehicle instanceof MchHelicopter || vehicle instanceof MchPlane;
        return seat == 0 && aircraft && vehicle.isSeatGunnerMode(0) ? 1 : seat;
    }

    private static void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        Entity ridden = mc.player.getVehicle();
        if (!(ridden instanceof AbstractMchVehicle vehicle) || vehicle.isDestroyed()) {
            return; // the reference GUIs draw nothing for a wreck
        }
        MchCameraModeView.drawNoise(g); // night vision, in every view (reference drawNightVisionNoise)
        if (!mc.options.getCameraType().isFirstPerson() && !MchConfig.CLIENT.displayHUDThirdPerson()) {
            return; // reference: !isThirdPersonView || DisplayHUDThirdPerson
        }
        // Draw the HUD for the seat the LOCAL player actually occupies — a gunner gets their gunner HUD (heli_gnr /
        // gunner / mbt_gnr …), a pilot the pilot HUD; the per-seat names come from the vehicle's HUD = config list.
        int seat = Math.max(0, vehicle.seatIndexOf(mc.player));
        String hudName = vehicle.hudName(hudSeat(vehicle, seat));
        if (hudName == null) {
            return;
        }
        MCH_Hud hud = MCH_HudManager.get(hudName);
        if (hud == null || hud.isEmpty()) {
            return;
        }
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false); // smooths the radar sweep line
        MchHudVarState state = new MchHudVarState(vehicle, mc.player, g.guiWidth(), g.guiHeight(), partialTick);
        hud.draw(new NeoHudRenderer(g, mc.font), state, MCH_HudManager::get);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F); // reset in case a textured needle left a tint
    }
}
