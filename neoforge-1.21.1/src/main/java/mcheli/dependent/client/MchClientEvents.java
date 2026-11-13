package mcheli.dependent.client;

import mcheli.MCHeli;
import mcheli.dependent.client.hud.NeoHudRenderer;
import mcheli.dependent.client.particle.MuzzleFxParticle;
import mcheli.dependent.item.VehicleSpawnItem;
import mcheli.dependent.port.MchContentPacks;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Client-only subscriber for renderer registration and the resource-reload cache invalidation. The
 * {@code value = Dist.CLIENT} filter means FML never loads this class (or the renderers it references) on a dedicated
 * server, so the server stays free of client-only types. The bus is auto-detected per event ({@code RegisterRenderers}
 * is a mod-bus event). The game-bus rider-input capture lives in {@link MchClientInput}.
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
        event.registerEntityRenderer(MchRegistries.BULLET.get(), MchBulletRenderer::new);
        event.registerEntityRenderer(MchRegistries.STEERED_BULLET.get(), MchBulletRenderer::new);
        event.registerEntityRenderer(MchRegistries.CARTRIDGE.get(), MchCartridgeRenderer::new);
        event.registerEntityRenderer(MchRegistries.CONTAINER.get(), MchContainerRenderer::new);
        event.registerEntityRenderer(MchRegistries.FLARE.get(), MchFlareRenderer::new);
    }

    /** The riding GUI (fuel slots + reload) — bound to the menu type registered in {@link MchRegistries}. */
    @SubscribeEvent
    public static void onRegisterScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(MchRegistries.VEHICLE_MENU.get(), mcheli.dependent.client.screen.MchVehicleScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(MchRegistries.WEAPON_FX.get(), MuzzleFxParticle.Provider::new);
        event.registerSpriteSet(MchRegistries.EXPLODE_FX.get(),
            mcheli.dependent.client.particle.MchExplodeParticle.Provider::new);
        event.registerSpriteSet(MchRegistries.SMOKE_FX.get(),
            mcheli.dependent.client.particle.MchSmokeParticle.Provider::new);
    }

    /** The one spawn-item icon renderer, kept so the reload listener can clear its caches (null until registered). */
    private static volatile VehicleItemRenderer itemRenderer;

    /** Every vehicle spawn item renders its 3D model as its inventory icon (needs a {@code builtin/entity} item model). */
    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        VehicleItemRenderer renderer = new VehicleItemRenderer();
        itemRenderer = renderer;
        IClientItemExtensions ext = new IClientItemExtensions() {
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() { return renderer; }
        };
        for (VehicleSpawnItem item : MchRegistries.allSpawnItems()) {
            event.registerItem(ext, item);
        }
    }

    /**
     * Resource reload (F3+T, a resource-pack change): drop every model and HUD-sheet cache, so edited content-pack or dev
     * models, textures and gui sheets show up without a restart. The entity renderers' own per-instance caches need no
     * help, because vanilla rebuilds every {@code EntityRenderer} on reload; but the rebuilt renderers refill from the
     * shared model cache, so that one is cleared in the BACKGROUND phase, which finishes before any listener's apply
     * (the renderer rebuild included) runs. The main-thread-only maps are cleared in the apply phase.
     */
    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
                MchContentPacks.clearModelCaches(); // concurrent maps: safe off-thread
                return null;
            }

            @Override
            protected void apply(Void nothing, ResourceManager manager, ProfilerFiller profiler) {
                VehicleItemRenderer renderer = itemRenderer;
                if (renderer != null) {
                    renderer.clearCaches();
                }
                NeoHudRenderer.clearDimensionCache();
            }

            @Override
            public String getName() {
                return "MCHeli model caches";
            }
        });
    }
}
