package mcheli.dependent.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import mcheli.MCHeli;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.registry.MchSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client-only: keeps a looping {@link VehicleEngineSound} playing for every nearby MCHeli vehicle (the reference runs
 * an {@code MCH_SoundUpdater} per aircraft, so you hear other helis/planes/tanks around you, not just your own). Each
 * loop picks its drone from the vehicle's config {@code Sound=} (else the per-type default) and drives its volume/pitch
 * from the vehicle's synced engine power. A loop self-stops when its vehicle is removed; this handler also drops loops
 * for vehicles that leave the audible range.
 */
@EventBusSubscriber(modid = MCHeli.MODID, value = Dist.CLIENT)
public final class MchClientSound {
    private MchClientSound() {}

    /** Only start loops for vehicles within this radius (vanilla attenuation silences them well before it). */
    private static final double RANGE = 96.0;

    private static final Map<Integer, VehicleEngineSound> active = new HashMap<>();

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            stopAll();
            return;
        }
        // Drop loops whose vehicle is gone or now out of range (the sound instance also self-stops when removed).
        Iterator<Map.Entry<Integer, VehicleEngineSound>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, VehicleEngineSound> e = it.next();
            Entity ent = level.getEntity(e.getKey());
            boolean gone = !(ent instanceof AbstractMchVehicle v) || !v.isAlive()
                || v.distanceToSqr(mc.player) > RANGE * RANGE;
            if (gone || e.getValue().isStopped()) {
                e.getValue().forceStop();
                it.remove();
            }
        }
        // Start a loop for any nearby vehicle that has an engine sound and isn't already playing.
        for (Entity ent : level.entitiesForRendering()) {
            if (ent instanceof AbstractMchVehicle v && v.isAlive()
                && v.distanceToSqr(mc.player) <= RANGE * RANGE && !active.containsKey(v.getId())) {
                SoundEvent engine = engineSoundFor(v);
                if (engine != null) {
                    VehicleEngineSound sound = new VehicleEngineSound(v, engine);
                    active.put(v.getId(), sound);
                    mc.getSoundManager().play(sound);
                }
            }
        }
    }

    /** The registered engine {@link SoundEvent} for {@code v} from its config sound name, or null (silent config). */
    private static SoundEvent engineSoundFor(AbstractMchVehicle v) {
        String name = v.engineSoundName();
        return name == null || name.isEmpty() ? null : MchSounds.byName(name);
    }

    private static void stopAll() {
        for (VehicleEngineSound s : active.values()) {
            s.forceStop();
        }
        active.clear();
    }
}
