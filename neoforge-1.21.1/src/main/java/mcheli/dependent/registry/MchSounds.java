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
 * Every MCHeli {@link SoundEvent}. The reference generated its {@code sounds.json} from the {@code .ogg} files at load
 * ({@code MCH_SoundsJson}, see {@link mcheli.agnostic.info.MCH_SoundsJson}); the 1.21.1 port needs both a registered
 * {@code SoundEvent} <em>and</em> a matching {@code assets/mcheli/sounds.json} entry, so this registers all
 * {@value #COUNT_DOC} bundled events by their {@code sounds.json} key (the {@code Sound=} value in a weapon config, e.g.
 * {@code gun_h3_snd}). As in the reference, numbered files are variants of one event: {@code wrench1..3.ogg} are the
 * event {@code wrench}. Config-driven fire looks a weapon's report up by name through {@link #byName}.
 */
public final class MchSounds {
    private MchSounds() {}

    static final String COUNT_DOC = "67";

    public static final DeferredRegister<SoundEvent> SOUNDS =
        DeferredRegister.create(Registries.SOUND_EVENT, MCHeli.MODID);

    /** name → registered event, for {@link #byName} lookups (the weapon-fire path resolves reports by config name). */
    private static final Map<String, Supplier<SoundEvent>> BY_NAME = new HashMap<>();

    /**
     * Every event key in the bundled {@code assets/mcheli/sounds.json}. Two are not one-file-per-event: {@code wrench}
     * groups {@code wrench1..3.ogg} (the reference's trailing-digit rule), and {@code lau-68_snd} is an alias that
     * plays {@code rocket_snd.ogg}. The original jar shipped no {@code lau-68_snd.ogg}, so the LAU-68 APKWS/LOGIR
     * rockets fired silently in 1.7.10; the port gives them the generic rocket report instead (deliberate).
     */
    private static final String[] ALL = {
        "a-10_snd", "a10gau8_snd", "alert", "boat", "bomb_snd", "cannon_1_snd",
        "cannon_2_snd", "cannon_3_snd", "cannon_4_snd", "chain", "chain_ct", "eject_seat",
        "fim92_reload", "fim92_snd", "gau-8_snd", "gltd", "gun_h1_snd", "gun_h2_snd",
        "gun_h3_snd", "gun_h4_snd", "gun_h5_snd", "gun_h6_snd", "gun_h7_snd", "gun_l1_snd",
        "gun_l2_snd", "gun_l3_snd", "gun_l4_snd", "hawk_snd", "heli", "heli_k",
        "helidmg", "hit", "lau-68_snd", "locked", "lockon", "mbt_run", "mbtrun",
        "missile_1_snd", "missile_2_snd", "missile_3_snd", "missile_4_snd", "mk19_l_snd", "mk19_r_snd",
        "mw-1_snd", "ng", "pi", "plane", "plane_cc", "plane_cv",
        "plastic_bomb_snd", "prop", "r44_heli", "radicon_heli", "rocket", "rocket_snd",
        "rr_griffon", "rr_merlin", "sa-2_snd", "smoke_snd", "tank", "tank_gte",
        "turboprop", "vehicle_drive", "vehicle_run", "wrench",
        "xm301_snd", "zoom",
    };

    /** The bundled event names, for content packs: a pack event with one of these names is ignored (bundled wins). */
    private static final java.util.Set<String> BUNDLED = java.util.Set.of(ALL);

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

    /** True if {@code name} is one of the bundled events (which a content pack can never replace). */
    public static boolean isBundled(String name) {
        return BUNDLED.contains(name);
    }

    /**
     * Register a {@link SoundEvent} for each content-pack sound event (from
     * {@link mcheli.dependent.port.MchContentPacks#packSoundEvents()}, already lower-cased, grouped and filtered).
     * MUST be called during the {@code @Mod} constructor, BEFORE {@link #register} attaches the DeferredRegister to the
     * bus — the registry freezes right after. A name already shipped by the mod is skipped (bundled sounds win —
     * additive, matching the resource policy); the matching {@code sounds.json} entry + {@code .ogg} are served
     * client-side via the pack's resource-pack mount. An illegal id is skipped with a warning, never handed to the
     * DeferredRegister, which would throw out of the {@code @Mod} constructor and crash the game.
     */
    public static void registerPackSounds(java.util.List<String> events) {
        for (String name : events) {
            if (name == null || name.isEmpty() || BY_NAME.containsKey(name)) {
                continue;
            }
            if (ResourceLocation.tryBuild(MCHeli.MODID, name) == null) {
                com.mojang.logging.LogUtils.getLogger().warn(
                    "MCHeli: skipping content-pack sound '{}' — illegal id (only [a-z0-9/._-] allowed)", name);
                continue;
            }
            BY_NAME.put(name, reg(name));
        }
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
