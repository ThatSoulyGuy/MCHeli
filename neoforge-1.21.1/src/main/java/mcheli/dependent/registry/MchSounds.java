package mcheli.dependent.registry;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import mcheli.MCHeli;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Every MCHeli {@link SoundEvent}. The reference registered these programmatically at load (one per {@code .ogg}); the
 * 1.21.1 port needs both a registered {@code SoundEvent} <em>and</em> a matching {@code assets/mcheli/sounds.json}
 * entry, so this registers all {@value #COUNT_DOC} sound files by their basename (the event name == the {@code .ogg}
 * basename == the {@code Sound=} value in a weapon config, e.g. {@code gun_h3_snd}). Config-driven fire looks a weapon's
 * report up by name through {@link #byName}.
 */
public final class MchSounds {
    private MchSounds() {}

    static final String COUNT_DOC = "68";

    public static final DeferredRegister<SoundEvent> SOUNDS =
        DeferredRegister.create(Registries.SOUND_EVENT, MCHeli.MODID);

    /** name → registered event, for {@link #byName} lookups (the weapon-fire path resolves reports by config name). */
    private static final Map<String, Supplier<SoundEvent>> BY_NAME = new HashMap<>();

    /** Every sound basename that has an {@code assets/mcheli/sounds/<name>.ogg} + {@code sounds.json} entry. */
    private static final String[] ALL = {
        "a-10_snd", "a10gau8_snd", "alert", "boat", "bomb_snd", "cannon_1_snd",
        "cannon_2_snd", "cannon_3_snd", "cannon_4_snd", "chain", "chain_ct", "eject_seat",
        "fim92_reload", "fim92_snd", "gau-8_snd", "gltd", "gun_h1_snd", "gun_h2_snd",
        "gun_h3_snd", "gun_h4_snd", "gun_h5_snd", "gun_h6_snd", "gun_h7_snd", "gun_l1_snd",
        "gun_l2_snd", "gun_l3_snd", "gun_l4_snd", "hawk_snd", "heli", "heli_k",
        "helidmg", "hit", "locked", "lockon", "mbt_run", "mbtrun",
        "missile_1_snd", "missile_2_snd", "missile_3_snd", "missile_4_snd", "mk19_l_snd", "mk19_r_snd",
        "mw-1_snd", "ng", "pi", "plane", "plane_cc", "plane_cv",
        "plastic_bomb_snd", "prop", "r44_heli", "radicon_heli", "rocket", "rocket_snd",
        "rr_griffon", "rr_merlin", "sa-2_snd", "smoke_snd", "tank", "tank_gte",
        "turboprop", "vehicle_drive", "vehicle_run", "wrench1", "wrench2", "wrench3",
        "xm301_snd", "zoom",
    };

    static {
        for (String name : ALL) {
            BY_NAME.put(name, reg(name));
        }
    }

    // Named handles for the engine drones / chain-gun report used by the vehicle sound code (real .ogg basenames).
    public static final Supplier<SoundEvent> HELI = BY_NAME.get("heli");          // rotor/engine drone
    public static final Supplier<SoundEvent> PLANE = BY_NAME.get("plane");        // jet drone
    public static final Supplier<SoundEvent> TANK_RUN = BY_NAME.get("mbt_run");   // tank engine
    public static final Supplier<SoundEvent> GUN_CHAIN = BY_NAME.get("chain");    // generic chain-gun report

    private static Supplier<SoundEvent> reg(String name) {
        return SOUNDS.register(name,
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, name)));
    }

    /**
     * The registered event for a weapon's {@code Sound=} basename (e.g. {@code gun_h3_snd}), or {@code null} if that
     * name has no {@code .ogg}. Config-driven firing calls this so each weapon plays its own report.
     */
    public static SoundEvent byName(String name) {
        if (name == null) {
            return null;
        }
        Supplier<SoundEvent> s = BY_NAME.get(name);
        return s != null ? s.get() : null;
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
