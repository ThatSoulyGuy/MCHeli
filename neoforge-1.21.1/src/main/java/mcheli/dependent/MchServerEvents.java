package mcheli.dependent;

import mcheli.MCHeli;
import mcheli.dependent.entity.AbstractMchVehicle;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Server-side game events MCHeli needs outside a vehicle's own tick.
 *
 * <ul>
 *   <li><b>Logout with a camera vision mode on.</b> A seat-0/1 camera in night or thermal vision keeps a 250-tick
 *       night vision effect on its viewer ({@link AbstractMchVehicle#releaseCameraViewer}). Vanilla's
 *       {@code PlayerList.remove} saves the player file BEFORE it dismounts the player, and when the vehicle carries
 *       another player too it is not saved with him, so the dismount strip came too late and the player logged back
 *       in on foot with the effect. The logged-out event fires before that save, so the viewer is released here.</li>
 * </ul>
 */
@EventBusSubscriber(modid = MCHeli.MODID)
public final class MchServerEvents {
    private MchServerEvents() {}

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide && player.getVehicle() instanceof AbstractMchVehicle v) {
            v.releaseCameraViewer(player);
        }
    }
}
