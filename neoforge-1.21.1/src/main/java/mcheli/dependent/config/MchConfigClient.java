package mcheli.dependent.config;

import mcheli.MCHeli;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only entrypoint for the config: the Mods-menu "Config" button opens NeoForge's generated
 * {@link ConfigurationScreen} over all three MCHeli files (the port of the reference's in-game {@code MCH_ConfigGui}
 * options page), and the server's {@code enableDebugBoundingBox} switch is enforced on this client.
 */
@Mod(value = MCHeli.MODID, dist = Dist.CLIENT)
public final class MchConfigClient {

    public MchConfigClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(MchConfigClient::onClientTick);
    }

    /**
     * Reference {@code MCH_ClientCommonTickHandler.onRenderTickPre}:302-304: while the server forbids it, entity
     * hitboxes (F3+B) are switched back off every frame. The synced server file is only loaded in a world, so the main
     * menu and an unconfigured server leave the vanilla toggle alone.
     */
    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && MchConfig.SERVER_SPEC.isLoaded() && !MchConfig.SERVER.enableDebugBoundingBox()
            && mc.getEntityRenderDispatcher().shouldRenderHitBoxes()) {
            mc.getEntityRenderDispatcher().setRenderHitBoxes(false);
        }
    }
}
