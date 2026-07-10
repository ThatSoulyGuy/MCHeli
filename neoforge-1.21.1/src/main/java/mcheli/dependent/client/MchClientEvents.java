package mcheli.dependent.client;

import mcheli.MCHeli;
import mcheli.dependent.registry.MchRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-only subscriber. The {@code value = Dist.CLIENT} filter means FML never loads this class (or the
 * renderer it references) on a dedicated server, so the server stays free of client-only types. The bus is
 * auto-detected from the event type (RegisterRenderers is a mod-bus event).
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
