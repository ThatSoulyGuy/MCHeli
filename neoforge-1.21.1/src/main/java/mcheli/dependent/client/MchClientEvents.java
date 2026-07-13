package mcheli.dependent.client;

import mcheli.MCHeli;
import mcheli.dependent.client.particle.MuzzleFxParticle;
import mcheli.dependent.item.VehicleSpawnItem;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

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
        event.registerEntityRenderer(MchRegistries.VEHICLE.get(), MchGroundVehicleRenderer::new);
        event.registerEntityRenderer(MchRegistries.HELI.get(), MchHelicopterRenderer::new);
        event.registerEntityRenderer(MchRegistries.PLANE.get(), MchPlaneRenderer::new);
        event.registerEntityRenderer(MchRegistries.TANK.get(), MchTankRenderer::new);
        event.registerEntityRenderer(MchRegistries.DEMO_BULLET.get(), MchBulletRenderer::new);
        event.registerEntityRenderer(MchRegistries.CARTRIDGE.get(), MchCartridgeRenderer::new);
    }

    /** The riding GUI (fuel slots + reload) — bound to the menu type registered in {@link MchRegistries}. */
    @SubscribeEvent
    public static void onRegisterScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(MchRegistries.VEHICLE_MENU.get(), mcheli.dependent.client.screen.MchVehicleScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(MchRegistries.WEAPON_FX.get(), MuzzleFxParticle.Provider::new);
    }

    /** Every vehicle spawn item renders its 3D model as its inventory icon (needs a {@code builtin/entity} item model). */
    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        VehicleItemRenderer renderer = new VehicleItemRenderer();
        IClientItemExtensions ext = new IClientItemExtensions() {
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() { return renderer; }
        };
        for (VehicleSpawnItem item : MchRegistries.allSpawnItems()) {
            event.registerItem(ext, item);
        }
    }
}
