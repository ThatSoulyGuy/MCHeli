package mcheli.dependent.client;

import mcheli.MCHeli;
import mcheli.dependent.registry.MchRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-only subscriber for renderer registration. The {@code value = Dist.CLIENT} filter means FML never loads
 * this class (or the renderers it references) on a dedicated server, so the server stays free of client-only types.
 * The bus is auto-detected per event ({@code RegisterRenderers} is a mod-bus event). The game-bus rider-input
 * capture lives in {@link MchClientInput}.
 */
@EventBusSubscriber(modid = MCHeli.MODID, value = Dist.CLIENT)
public final class MchClientEvents {
    private MchClientEvents() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MchRegistries.DEMO_VEHICLE.get(), MchDemoVehicleRenderer::new);
        event.registerEntityRenderer(MchRegistries.DEMO_HELI.get(), MchDemoHeliRenderer::new);
        event.registerEntityRenderer(MchRegistries.DEMO_PLANE.get(), MchDemoPlaneRenderer::new);
        event.registerEntityRenderer(MchRegistries.DEMO_TANK.get(), MchDemoTankRenderer::new);
    }
}
