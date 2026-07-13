package mcheli.dependent.client;

import mcheli.MCHeli;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.entity.MchHelicopter;
import mcheli.dependent.entity.MchPlane;
import mcheli.dependent.entity.MchTank;
import mcheli.dependent.registry.MchSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client-only: keeps a looping {@link VehicleEngineSound} playing for the vehicle the local player is riding, picking
 * the drone by vehicle type. The instance is (re)started when the player mounts a different vehicle and stopped on
 * dismount. (Per-vehicle engine sound for OTHER nearby vehicles is a later expansion; for now you hear your own.)
 */
@EventBusSubscriber(modid = MCHeli.MODID, value = Dist.CLIENT)
public final class MchClientSound {
    private MchClientSound() {}

    private static VehicleEngineSound current;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Entity vehicle = mc.player != null ? mc.player.getVehicle() : null;

        if (vehicle instanceof AbstractMchVehicle v) {
            if (current == null || current.isStopped() || current.vehicleId() != v.getId()) {
                if (current != null) {
                    current.forceStop();
                }
                SoundEvent engine = engineSoundFor(v);
                if (engine != null) {
                    current = new VehicleEngineSound(v, engine);
                    mc.getSoundManager().play(current);
                }
            }
        } else if (current != null) {
            current.forceStop();
            current = null;
        }
    }

    private static SoundEvent engineSoundFor(AbstractMchVehicle v) {
        if (v instanceof MchHelicopter) {
            return MchSounds.HELI.get();
        }
        if (v instanceof MchPlane) {
            return MchSounds.PLANE.get();
        }
        if (v instanceof MchTank) {
            return MchSounds.TANK_RUN.get();
        }
        return null; // ground demo vehicle: no engine sound yet
    }
}
