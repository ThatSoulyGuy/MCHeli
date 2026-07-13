package mcheli.dependent.client;

import mcheli.MCHeli;
import mcheli.dependent.entity.AbstractMchVehicle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * Client-only GAME-bus subscriber that hides the seated rider of a vehicle whose config says {@code HideEntity=true}
 * (the Mk.15 Phalanx dome, the closed m1a2 turret) — the 1.21.1 port of the reference {@code MCH_ClientEventHook}
 * {@code renderPlayerPre}/{@code renderLivingEventPre} cancel. Without it the port DRAWS the pilot where the reference
 * makes it invisible, so the player clips through the closed dome / perches on the aiming turret. Cancelling the
 * living-render pass removes the rider only in views that would draw it (third-person / other clients); the local
 * first-person player is not drawn as a living entity, so this is a no-op there.
 *
 * <p>The bus is auto-detected per event ({@code RenderLivingEvent.Pre} is a game-bus event).
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
}
