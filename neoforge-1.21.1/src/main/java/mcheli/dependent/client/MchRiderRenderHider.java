package mcheli.dependent.client;

import mcheli.MCHeli;
import mcheli.dependent.config.MchConfig;
import mcheli.dependent.entity.AbstractMchVehicle;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;

/**
 * Client-only GAME-bus subscriber that hides the seated rider of a vehicle whose config says {@code HideEntity=true}
 * (the Mk.15 Phalanx dome, the closed m1a2 turret) — the 1.21.1 port of the reference {@code MCH_ClientEventHook}
 * {@code renderPlayerPre}/{@code renderLivingEventPre} cancel. Without it the port DRAWS the pilot where the reference
 * makes it invisible, so the player clips through the closed dome / perches on the aiming turret. Cancelling the
 * living-render pass removes the rider only in views that would draw it (third-person / other clients); the local
 * first-person player is not drawn as a living entity, so this is a no-op there.
 *
 * <p>It also hides the name tags of everyone riding the local player's vehicle while the client option
 * {@code disableRenderLivingSpecials} is on (reference {@code renderLivingEventSpecialsPre}, MCH_ClientEventHook:35-42),
 * so a crew member's name does not float in the middle of the cockpit view.
 *
 * <p>The bus is auto-detected per event (both are game-bus events).
 */
@EventBusSubscriber(modid = MCHeli.MODID, value = Dist.CLIENT)
public final class MchRiderRenderHider {
    private MchRiderRenderHider() {}

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (event.getEntity().getVehicle() instanceof AbstractMchVehicle vehicle && vehicle.hidesRider()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() instanceof AbstractMchVehicle own
            && event.getEntity().getVehicle() == own && MchConfig.CLIENT.disableRenderLivingSpecials()) {
            event.setCanRender(TriState.FALSE); // reference ac.isMountedEntity(entity) -> setCanceled(true)
        }
    }
}
