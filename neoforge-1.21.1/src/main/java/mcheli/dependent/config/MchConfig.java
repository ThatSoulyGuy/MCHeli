package mcheli.dependent.config;

import java.util.List;
import mcheli.agnostic.config.MchDamageFactorList;
import mcheli.agnostic.config.MchSettings;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * The port of the reference's global {@code MCH_Config} ({@code config/mcheli.cfg}) onto NeoForge {@link ModConfigSpec}s,
 * split by who owns each option:
 * <ul>
 *   <li>{@link #COMMON} ({@code config/mcheli-common.toml}): what the definition LOADER bakes in at common setup, the
 *       {@code All*Speed} multipliers. They are read before any world exists, so they cannot live in the per-world
 *       server file, and need a game restart to change, as in the reference (applied in {@code isValidData}).</li>
 *   <li>{@link #SERVER} ({@code <world>/serverconfig/mcheli-server.toml}): server-authoritative gameplay. NeoForge
 *       syncs it to each connecting client, the job the reference's {@code MCH_PacketNotifyServerSettings} +
 *       {@code MCH_ServerSettings} mirror did by hand, so client code may read it too.</li>
 *   <li>{@link #CLIENT} ({@code config/mcheli-client.toml}): per-player control and render preferences (the
 *       reference's {@code MCH_ConfigGui} options page). The cs_* ones the SERVER simulation needs (auto-throttle-down,
 *       dismount-all) must travel with the control packets, as {@code MCH_PacketNotifyClientSetting} did.</li>
 * </ul>
 * Every accessor is safe at any time: while its file is not loaded (the server spec outside a world, the client spec on
 * a dedicated server) it returns the reference default instead of throwing. Defaults, ranges and names follow
 * {@code MCH_Config.java} (each option cites its line); option names are the reference names in lowerCamelCase.
 *
 * <p>Deliberately absent: options of unported features (UAV station, multiplay markers/PVP, GLTD, range finder, light
 * weapons, racks, minecart mounting, the /mcheli command, TestMode) and 1.7.10-only knobs (key codes, now vanilla
 * Controls; numeric item/block ids; GL and texture-manager hacks; item recipes, now datapack JSON).
 */
public final class MchConfig {
    private MchConfig() {}

    public static final Common COMMON;
    public static final Server SERVER;
    public static final Client CLIENT;
    static final ModConfigSpec COMMON_SPEC;
    static final ModConfigSpec SERVER_SPEC;
    static final ModConfigSpec CLIENT_SPEC;

    static {
        Pair<Common, ModConfigSpec> c = new ModConfigSpec.Builder().configure(Common::new);
        COMMON = c.getLeft();
        COMMON_SPEC = c.getRight();
        Pair<Server, ModConfigSpec> s = new ModConfigSpec.Builder().configure(Server::new);
        SERVER = s.getLeft();
        SERVER_SPEC = s.getRight();
        Pair<Client, ModConfigSpec> cl = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = cl.getLeft();
        CLIENT_SPEC = cl.getRight();
    }

    /** Register the three files and the reload hooks. Call from the mod constructor. */
    public static void register(ModContainer container, IEventBus modBus) {
        container.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
        container.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
        modBus.addListener(MchConfig::onLoading);
        modBus.addListener(MchConfig::onReloading);
        modBus.addListener(MchConfig::onUnloading);
    }

    /**
     * Hand the agnostic layer the plain values it reads while parsing definitions. Call after the common config is
     * loaded (it is, by common setup) and BEFORE loading vehicle definitions.
     */
    public static void installAgnosticSettings() {
        MchSettings.install(new MchSettings(COMMON.allHeliSpeed(), COMMON.allPlaneSpeed(), COMMON.allTankSpeed()));
    }

    private static void onLoading(ModConfigEvent.Loading e) {
        changed(e.getConfig());
    }

    private static void onReloading(ModConfigEvent.Reloading e) {
        changed(e.getConfig());
    }

    private static void onUnloading(ModConfigEvent.Unloading e) {
        changed(e.getConfig());
    }

    /** Drop the parsed caches built from a file that was (re)loaded or unloaded; they rebuild on next use. */
    private static void changed(ModConfig config) {
        if (config.getSpec() == SERVER_SPEC) {
            MchServerRules.invalidate();
        }
    }

    // ---- safe readers: the default while the owning file is not loaded ----

    static boolean get(ModConfigSpec spec, ModConfigSpec.BooleanValue v) {
        return get(spec, (ModConfigSpec.ConfigValue<Boolean>) v);
    }

    static int get(ModConfigSpec spec, ModConfigSpec.IntValue v) {
        return get(spec, (ModConfigSpec.ConfigValue<Integer>) v);
    }

    static double get(ModConfigSpec spec, ModConfigSpec.DoubleValue v) {
        return get(spec, (ModConfigSpec.ConfigValue<Double>) v);
    }

    /** The value, or the default while {@code spec} is not loaded. The file can also be unloaded on another thread
     *  between the {@code isLoaded()} check and the read (FML swaps the config, then clears the value caches, on the
     *  thread firing the event), and {@code ConfigValue.get} then throws or, mid-clear, returns null: both fall back
     *  to the default too, so these readers never throw. */
    static <T> T get(ModConfigSpec spec, ModConfigSpec.ConfigValue<T> v) {
        if (spec.isLoaded()) {
            try {
                T value = v.get();
                if (value != null) {
                    return value;
                }
            } catch (IllegalStateException unloadedMeanwhile) {
                // "Cannot get config value before config is loaded": use the default below
            }
        }
        return v.getDefault();
    }

    /** {@link #get(ModConfigSpec, ModConfigSpec.ConfigValue)} without NeoForge's per-value cache, for values the caller
     *  parses and caches itself ({@link MchServerRules}). A cached {@code get} that races a reload can store the OLD
     *  value after the reload cleared the cache, and every later {@code get} would keep returning it. */
    static <T> T getUncached(ModConfigSpec spec, ModConfigSpec.ConfigValue<T> v) {
        if (spec.isLoaded()) {
            try {
                T value = v.getRaw();
                if (value != null) {
                    return value;
                }
            } catch (IllegalStateException unloadedMeanwhile) {
                // unloaded between the check and the read: use the default below
            }
        }
        return v.getDefault();
    }

    /** List element validator for block entries: a block id ({@code minecraft:torch}) or a block tag ({@code #c:glass_panes}). */
    static boolean isBlockEntry(Object o) {
        if (!(o instanceof String s) || s.isBlank()) {
            return false;
        }
        String id = s.trim();
        return ResourceLocation.tryParse(id.startsWith("#") ? id.substring(1) : id) != null;
    }

    static boolean isNonBlankString(Object o) {
        return o instanceof String s && !s.isBlank();
    }

    // =================================================================================================================

    /** {@code mcheli-common.toml}: values the definition loader bakes in at common setup. */
    public static final class Common {
        private final ModConfigSpec.DoubleValue allHeliSpeed;
        private final ModConfigSpec.DoubleValue allPlaneSpeed;
        private final ModConfigSpec.DoubleValue allTankSpeed;

        Common(ModConfigSpec.Builder b) {
            b.comment("Applied while vehicle definitions load (game restart required).").push("definitions");
            allHeliSpeed = b.comment("Multiplies every helicopter's Speed. Reference AllHeliSpeed (MCH_Config:309).")
                .gameRestart().defineInRange("allHeliSpeed", 1.0, MchSettings.MIN_SPEED_MULTIPLIER, MchSettings.MAX_SPEED_MULTIPLIER);
            allPlaneSpeed = b.comment("Multiplies every plane's Speed and SweepWingSpeed. Reference AllPlaneSpeed (MCH_Config:310).")
                .gameRestart().defineInRange("allPlaneSpeed", 1.0, MchSettings.MIN_SPEED_MULTIPLIER, MchSettings.MAX_SPEED_MULTIPLIER);
            allTankSpeed = b.comment("Multiplies every tank's Speed. Reference AllTankSpeed (MCH_Config:311).")
                .gameRestart().defineInRange("allTankSpeed", 1.0, MchSettings.MIN_SPEED_MULTIPLIER, MchSettings.MAX_SPEED_MULTIPLIER);
            b.pop();
        }

        public double allHeliSpeed() { return get(COMMON_SPEC, allHeliSpeed); }
        public double allPlaneSpeed() { return get(COMMON_SPEC, allPlaneSpeed); }
        public double allTankSpeed() { return get(COMMON_SPEC, allTankSpeed); }
    }

    // =================================================================================================================

    /** {@code mcheli-server.toml}: server-authoritative gameplay, synced to clients. */
    public static final class Server {
        // general
        private final ModConfigSpec.BooleanValue infinityAmmo;
        private final ModConfigSpec.BooleanValue infinityFuel;
        private final ModConfigSpec.DoubleValue autoRepairHP;
        private final ModConfigSpec.DoubleValue hurtResistantTime;
        private final ModConfigSpec.IntValue aliveTimeOfCartridge;
        private final ModConfigSpec.BooleanValue enableDebugBoundingBox;
        // vehicle items
        private final ModConfigSpec.BooleanValue placeableOnSpongeOnly;
        private final ModConfigSpec.BooleanValue itemDamage;
        private final ModConfigSpec.BooleanValue itemFuel;
        private final ModConfigSpec.BooleanValue preventingBroken;
        private final ModConfigSpec.BooleanValue breakableOnlyPickaxe;
        private final ModConfigSpec.BooleanValue dropItemInCreativeMode;
        // collision
        private final ModConfigSpec.BooleanValue collisionDestroyBlock;
        private final ModConfigSpec.BooleanValue collisionEntityDamage;
        private final ModConfigSpec.BooleanValue collisionEntityTankDamage;
        final ModConfigSpec.ConfigValue<List<? extends String>> collisionCarBreakableBlocks;
        final ModConfigSpec.ConfigValue<List<? extends String>> collisionCarNoBreakBlocks;
        final ModConfigSpec.ConfigValue<List<? extends String>> collisionCarBreakableMaterials;
        final ModConfigSpec.ConfigValue<List<? extends String>> collisionTankBreakableBlocks;
        final ModConfigSpec.ConfigValue<List<? extends String>> collisionTankNoBreakBlocks;
        final ModConfigSpec.ConfigValue<List<? extends String>> collisionTankBreakableMaterials;
        // explosion + bullets
        private final ModConfigSpec.BooleanValue explosionDestroyBlock;
        private final ModConfigSpec.BooleanValue explosionFlamingBlock;
        final ModConfigSpec.ConfigValue<List<? extends String>> bulletBreakableBlocks;
        final ModConfigSpec.ConfigValue<List<? extends String>> ignoreBulletHit;
        // damage factors
        final ModConfigSpec.ConfigValue<List<? extends String>> damageVsEntity;
        final ModConfigSpec.ConfigValue<List<? extends String>> damageVsLiving;
        final ModConfigSpec.ConfigValue<List<? extends String>> damageVsPlayer;
        final ModConfigSpec.ConfigValue<List<? extends String>> damageVsMCHeliAircraft;
        final ModConfigSpec.ConfigValue<List<? extends String>> damageVsMCHeliTank;
        final ModConfigSpec.ConfigValue<List<? extends String>> damageVsMCHeliVehicle;
        final ModConfigSpec.ConfigValue<List<? extends String>> damageVsMCHeliOther;
        final ModConfigSpec.ConfigValue<List<? extends String>> damageMCHeliAircraftByExternal;
        final ModConfigSpec.ConfigValue<List<? extends String>> damageMCHeliTankByExternal;
        final ModConfigSpec.ConfigValue<List<? extends String>> damageMCHeliVehicleByExternal;
        final ModConfigSpec.ConfigValue<List<? extends String>> damageMCHeliOtherByExternal;

        Server(ModConfigSpec.Builder b) {
            b.push("general");
            infinityAmmo = b.comment("Every weapon has unlimited ammunition. Reference InfinityAmmo (MCH_Config:288).")
                .define("infinityAmmo", false);
            infinityFuel = b.comment("Vehicles never use fuel. Reference InfinityFuel (MCH_Config:289).")
                .define("infinityFuel", false);
            autoRepairHP = b.comment("A vehicle not hit for 30 seconds repairs 1% of its max HP every 2 seconds while its HP",
                    "fraction is at least this (0.0 = always). Reference AutoRepairHP (MCH_Config:253; autoRepair",
                    "MCH_EntityAircraft:2120-2141).")
                .defineInRange("autoRepairHP", 0.5, 0.0, 1.0);
            hurtResistantTime = b.comment("Scales a target's hurt-immunity ticks after MCHeli damage (0.0 = MCHeli hits ignore",
                    "the vanilla i-frames). Reference HurtResistantTime (MCH_Config:312, clamped 0..10000 at :524-530).")
                .defineInRange("hurtResistantTime", 0.0, 0.0, 10000.0);
            aliveTimeOfCartridge = b.comment("Ticks an ejected shell casing stays in the world. Reference AliveTimeOfCartridge (MCH_Config:287).")
                .defineInRange("aliveTimeOfCartridge", 200, 0, 72000);
            enableDebugBoundingBox = b.comment("Allow players to show entity hitboxes (F3+B). Reference EnableDebugBoundingBox",
                    "(MCH_Config:328), pushed to clients through MCH_ServerSettings.")
                .define("enableDebugBoundingBox", true);
            b.pop();

            b.push("vehicleItems");
            placeableOnSpongeOnly = b.comment("Vehicle items can only be placed on a sponge block. Reference PlaceableOnSpongeOnly (MCH_Config:249).")
                .define("placeableOnSpongeOnly", false);
            itemDamage = b.comment("A recovered vehicle item keeps the vehicle's damage. Reference ItemDamage (MCH_Config:251).")
                .define("itemDamage", true);
            itemFuel = b.comment("A recovered vehicle item keeps the vehicle's fuel. Reference ItemFuel (MCH_Config:252).")
                .define("itemFuel", true);
            preventingBroken = b.comment("Survival players cannot break an unridden vehicle back into its item. Reference PreventingBroken (MCH_Config:299).")
                .define("preventingBroken", false);
            breakableOnlyPickaxe = b.comment("Breaking a vehicle back into its item needs a pickaxe. Reference BreakableOnlyPickaxe (MCH_Config:301).")
                .define("breakableOnlyPickaxe", false);
            dropItemInCreativeMode = b.comment("A creative player breaking a vehicle drops its item unless sneaking (false: only when",
                    "sneaking). Reference DropItemInCreativeMode (MCH_Config:300).")
                .define("dropItemInCreativeMode", false);
            b.pop();

            b.comment("Block lists take block ids (minecraft:torch) and block tags (#c:glass_panes).",
                "The defaults are the reference strings translated to 1.21 ids/tags.").push("collision");
            collisionDestroyBlock = b.comment("Vehicles break the listed blocks they drive or crash into. Reference Collision_DestroyBlock (MCH_Config:254).")
                .define("collisionDestroyBlock", true);
            collisionEntityDamage = b.comment("Tanks and cars hurt the entities they run over, other MCHeli vehicles included (except",
                    "tank-weight tanks, see collisionEntityTankDamage). Reference Collision_EntityDamage (MCH_Config:266;",
                    "MCH_EntityTank:826-833).")
                .define("collisionEntityDamage", true);
            collisionEntityTankDamage = b.comment("Tanks and cars also hurt the tank-weight tanks (WeightType = tank) they run over; every",
                    "other target follows collisionEntityDamage. Reference Collision_EntityTankDamage (MCH_Config:267;",
                    "MCH_EntityTank:826-831).")
                .define("collisionEntityTankDamage", false);
            collisionCarBreakableBlocks = blocks(b, "collisionCarBreakableBlocks",
                "Blocks a car-weight vehicle breaks (then stops descending that column). Reference Collision_Car_BreakableBlock",
                "(MCH_Config:257) \"double_plant, glass_pane, stained_glass_pane\".",
                List.of("#minecraft:tall_flowers", "minecraft:tall_grass", "minecraft:large_fern", "#c:glass_panes"));
            collisionCarNoBreakBlocks = blocks(b, "collisionCarNoBreakBlocks",
                "Blocks that stop a car-weight vehicle's crush outright. Reference Collision_Car_NoBreakBlock (MCH_Config:258) \"torch\".",
                null,
                List.of("minecraft:torch", "minecraft:wall_torch", "minecraft:soul_torch", "minecraft:soul_wall_torch"));
            collisionCarBreakableMaterials = materials(b, "collisionCarBreakableMaterials",
                "Reference Collision_Car_BreakableMaterial (MCH_Config:259).",
                List.of("cactus", "cake", "gourd", "leaves", "vine", "plants"));
            collisionTankBreakableBlocks = blocks(b, "collisionTankBreakableBlocks",
                "Blocks a tank-weight vehicle breaks (then stops descending that column). Reference Collision_Tank_BreakableBlock",
                "(MCH_Config:260) \"nether_brick_fence\".",
                List.of("#c:fences/nether_brick"));
            collisionTankNoBreakBlocks = blocks(b, "collisionTankNoBreakBlocks",
                "Blocks that stop a tank-weight vehicle's crush outright. Reference Collision_Tank_NoBreakBlock (MCH_Config:262) \"torch, glowstone\".",
                null,
                List.of("minecraft:torch", "minecraft:wall_torch", "minecraft:soul_torch", "minecraft:soul_wall_torch",
                    "minecraft:glowstone"));
            collisionTankBreakableMaterials = materials(b, "collisionTankBreakableMaterials",
                "Reference Collision_Tank_BreakableMaterial (MCH_Config:263-265).",
                List.of("cactus", "cake", "carpet", "circuits", "glass", "gourd", "leaves", "vine", "wood", "plants"));
            b.pop();

            b.push("weapons");
            explosionDestroyBlock = b.comment("Explosions break blocks. Reference Explosion_DestroyBlock (MCH_Config:255).")
                .define("explosionDestroyBlock", true);
            explosionFlamingBlock = b.comment("Incendiary explosions and marker rockets set fires. Reference Explosion_FlamingBlock (MCH_Config:256).")
                .define("explosionFlamingBlock", true);
            bulletBreakableBlocks = blocks(b, "bulletBreakableBlocks",
                "Blocks a bullet breaks and flies on through. Reference BulletBreakableBlocks (MCH_Config:293-295)",
                "\"glass_pane, stained_glass_pane, tallgrass, double_plant, yellow_flower, red_flower, vine, wheat, reeds, waterlily\".",
                List.of("#c:glass_panes", "minecraft:short_grass", "minecraft:fern", "#minecraft:tall_flowers",
                    "minecraft:tall_grass", "minecraft:large_fern", "#minecraft:small_flowers", "minecraft:vine",
                    "minecraft:wheat", "minecraft:sugar_cane", "minecraft:lily_pad"));
            ignoreBulletHit = b.comment("Entities whose class name contains one of these (case-insensitive) are never hit by MCHeli",
                    "bullets, e.g. other mods' projectiles. Reference IgnoreBulletHit (MCH_Config:545-548 defaults).")
                .defineListAllowEmpty("ignoreBulletHit",
                    List.of("flansmod.common.guns.EntityBullet", "flansmod.common.guns.EntityGrenade"),
                    // a new row must pass the validator, or the config screen's Done stays disabled until it is edited
                    () -> "examplemod.entity.ExampleProjectile", MchConfig::isNonBlankString);
            b.pop();

            b.comment("Damage factors: each line is \"factor\" (applies to every target) or \"factor, classNameFragment\"",
                "(applies when the class name contains the fragment); every matching line multiplies.",
                "Reference MCH_Config.DamageFactor lists (MCH_Config:449-472, applied at :595-651).").push("damage");
            damageVsEntity = factors(b, "damageVsEntity", "MCHeli weapons vs non-living entities.", List.of("1.0", "3.0, flansmod"));
            damageVsLiving = factors(b, "damageVsLiving", "MCHeli weapons vs mobs.", List.of("1.0"));
            damageVsPlayer = factors(b, "damageVsPlayer", "MCHeli weapons vs players.", List.of("1.0"));
            damageVsMCHeliAircraft = factors(b, "damageVsMCHeliAircraft", "MCHeli weapons vs helicopters and planes.", List.of("1.0"));
            damageVsMCHeliTank = factors(b, "damageVsMCHeliTank", "MCHeli weapons vs tanks.", List.of("1.0"));
            damageVsMCHeliVehicle = factors(b, "damageVsMCHeliVehicle", "MCHeli weapons vs ground vehicles.", List.of("1.0"));
            damageVsMCHeliOther = factors(b, "damageVsMCHeliOther", "MCHeli weapons vs other MCHeli entities.", List.of("1.0"));
            damageMCHeliAircraftByExternal = factors(b, "damageMCHeliAircraftByExternal",
                "Damage to helicopters and planes, matched against the attacker's class.", List.of("1.0", "0.5, flansmod"));
            damageMCHeliTankByExternal = factors(b, "damageMCHeliTankByExternal",
                "Damage to tanks, matched against the attacker's class.", List.of("1.0"));
            damageMCHeliVehicleByExternal = factors(b, "damageMCHeliVehicleByExternal",
                "Damage to ground vehicles, matched against the attacker's class.", List.of("1.0", "0.5, flansmod"));
            damageMCHeliOtherByExternal = factors(b, "damageMCHeliOtherByExternal",
                "Damage to other MCHeli entities, matched against the attacker's class.", List.of("1.0"));
            b.pop();
        }

        private static ModConfigSpec.ConfigValue<List<? extends String>> blocks(ModConfigSpec.Builder b, String name,
                String comment, String comment2, List<String> def) {
            if (comment2 != null) {
                b.comment(comment, comment2);
            } else {
                b.comment(comment);
            }
            return b.defineListAllowEmpty(name, def, () -> "minecraft:", MchConfig::isBlockEntry);
        }

        private static ModConfigSpec.ConfigValue<List<? extends String>> materials(ModConfigSpec.Builder b, String name,
                String reference, List<String> def) {
            b.comment("What a vehicle of this weight breaks while crushing on down the column. A bare word is a 1.7.10",
                "material class, mapped to 1.21 blocks/tags in MchCrushableBlocks (cactus, cake, carpet, circuits, glass,",
                "gourd, leaves, plants, vine, wood); an entry with ':' is a block id, one starting with '#' a block tag.",
                reference);
            // new row: a valid material already in both defaults, so an unedited added row changes nothing there
            return b.defineListAllowEmpty(name, def, () -> "plants", MchConfig::isNonBlankString);
        }

        private static ModConfigSpec.ConfigValue<List<? extends String>> factors(ModConfigSpec.Builder b, String name,
                String comment, List<String> def) {
            b.comment(comment);
            return b.defineListAllowEmpty(name, def, () -> "1.0", MchDamageFactorList::isValidLine);
        }

        public boolean infinityAmmo() { return get(SERVER_SPEC, infinityAmmo); }
        public boolean infinityFuel() { return get(SERVER_SPEC, infinityFuel); }
        public double autoRepairHP() { return get(SERVER_SPEC, autoRepairHP); }
        public double hurtResistantTime() { return get(SERVER_SPEC, hurtResistantTime); }
        public int aliveTimeOfCartridge() { return get(SERVER_SPEC, aliveTimeOfCartridge); }
        public boolean enableDebugBoundingBox() { return get(SERVER_SPEC, enableDebugBoundingBox); }
        public boolean placeableOnSpongeOnly() { return get(SERVER_SPEC, placeableOnSpongeOnly); }
        public boolean itemDamage() { return get(SERVER_SPEC, itemDamage); }
        public boolean itemFuel() { return get(SERVER_SPEC, itemFuel); }
        public boolean preventingBroken() { return get(SERVER_SPEC, preventingBroken); }
        public boolean breakableOnlyPickaxe() { return get(SERVER_SPEC, breakableOnlyPickaxe); }
        public boolean dropItemInCreativeMode() { return get(SERVER_SPEC, dropItemInCreativeMode); }
        public boolean collisionDestroyBlock() { return get(SERVER_SPEC, collisionDestroyBlock); }
        public boolean collisionEntityDamage() { return get(SERVER_SPEC, collisionEntityDamage); }
        public boolean collisionEntityTankDamage() { return get(SERVER_SPEC, collisionEntityTankDamage); }
        public boolean explosionDestroyBlock() { return get(SERVER_SPEC, explosionDestroyBlock); }
        public boolean explosionFlamingBlock() { return get(SERVER_SPEC, explosionFlamingBlock); }
        // The list options are read through MchServerRules, which parses and caches them.
    }

    // =================================================================================================================

    /** {@code mcheli-client.toml}: this player's control and render preferences. */
    public static final class Client {
        private final ModConfigSpec.BooleanValue autoThrottleDownHeli;
        private final ModConfigSpec.BooleanValue autoThrottleDownPlane;
        private final ModConfigSpec.BooleanValue autoThrottleDownTank;
        private final ModConfigSpec.BooleanValue dismountAll;
        private final ModConfigSpec.BooleanValue mouseControlFlightSimMode;
        private final ModConfigSpec.BooleanValue mouseControlStickModeHeli;
        private final ModConfigSpec.BooleanValue mouseControlStickModePlane;
        private final ModConfigSpec.BooleanValue invertMouse;
        private final ModConfigSpec.DoubleValue mouseSensitivity;
        private final ModConfigSpec.BooleanValue switchWeaponWithMouseWheel;
        private final ModConfigSpec.BooleanValue displayHUDThirdPerson;
        private final ModConfigSpec.ConfigValue<String> hitMarkColor;
        private final ModConfigSpec.BooleanValue smoothShading;
        private final ModConfigSpec.BooleanValue defaultExplosionParticle;
        private final ModConfigSpec.DoubleValue renderDistanceWeight;
        private final ModConfigSpec.BooleanValue disableRenderLivingSpecials;

        Client(ModConfigSpec.Builder b) {
            b.comment("Sent to the server with your controls: they change how the vehicle you pilot behaves.").push("piloting");
            autoThrottleDownHeli = b.comment("With no throttle key held, a helicopter's throttle settles to hover. Reference",
                    "AutoThrottleDownHeli (MCH_Config:274).")
                .define("autoThrottleDownHeli", true);
            autoThrottleDownPlane = b.comment("With no throttle key held, a plane's throttle winds down. Reference AutoThrottleDownPlane (MCH_Config:275).")
                .define("autoThrottleDownPlane", false);
            autoThrottleDownTank = b.comment("With no throttle key held, a tank's throttle winds down. Reference AutoThrottleDownTank (MCH_Config:276).")
                .define("autoThrottleDownTank", false);
            dismountAll = b.comment("When you leave the pilot seat, the rest of the crew dismounts too. Reference DismountAll (MCH_Config:269).")
                .define("dismountAll", false);
            b.pop();

            b.push("mouse");
            mouseControlFlightSimMode = b.comment("Planes: the mouse rolls and the strafe keys yaw (flight-sim style). Reference",
                    "MouseControlFlightSimMode (MCH_Config:306-307).")
                .define("mouseControlFlightSimMode", false);
            mouseControlStickModeHeli = b.comment("Helicopters: the mouse acts as a stick that recentres near the middle. Reference",
                    "MouseControlStickModeHeli (MCH_Config:304).")
                .define("mouseControlStickModeHeli", false);
            mouseControlStickModePlane = b.comment("Planes: the mouse acts as a stick that recentres near the middle. Reference",
                    "MouseControlStickModePlane (MCH_Config:305).")
                .define("mouseControlStickModePlane", false);
            invertMouse = b.comment("Invert the vertical mouse axis while piloting (on top of the vanilla setting). Reference InvertMouse (MCH_Config:302).")
                .define("invertMouse", false);
            mouseSensitivity = b.comment("Mouse-stick gain while piloting (on top of the vanilla sensitivity); 10 = the reference default. Reference MouseSensitivity",
                    "(MCH_Config:303, slider 0..30 in MCH_ConfigGui:95).")
                .defineInRange("mouseSensitivity", 10.0, 0.0, 30.0);
            switchWeaponWithMouseWheel = b.comment("The mouse wheel cycles weapons while seated. Reference SwitchWeaponWithMouseWheel (MCH_Config:308).")
                .define("switchWeaponWithMouseWheel", true);
            b.pop();

            b.push("render");
            displayHUDThirdPerson = b.comment("Draw the vehicle HUD in third-person view too. Reference DisplayHUDThirdPerson (MCH_Config:313).")
                .define("displayHUDThirdPerson", false);
            hitMarkColor = b.comment("Colour of the hit marker that flashes when your weapon hits something: \"Alpha, Red, Green, Blue\",",
                    "each 0..255. Reference HitMarkColor (MCH_Config:290, parsed at :500-504).")
                .define("hitMarkColor", "255, 255, 0, 0", Client::isArgbList);
            smoothShading = b.comment("Smooth-shade vehicle models whose config allows it. Reference SmoothShading (MCH_Config:292).")
                .define("smoothShading", true);
            defaultExplosionParticle = b.comment("Use vanilla explosion particles instead of MCHeli's fireballs. Reference DefaultExplosionParticle (MCH_Config:323).")
                .define("defaultExplosionParticle", false);
            renderDistanceWeight = b.comment("Multiplies how far away vehicles are still drawn. Reference RenderDistanceWeight (MCH_Config:279).")
                .defineInRange("renderDistanceWeight", 10.0, 0.1, 100.0);
            disableRenderLivingSpecials = b.comment("Hide the name tags of the crew riding with you. Reference DisableRenderLivingSpecials (MCH_Config:298).")
                .define("disableRenderLivingSpecials", true);
            b.pop();
        }

        /** The auto-throttle-down preference for a vehicle kind, by {@code MCH_AircraftInfo.getKindName()}
         *  ("helicopter"/"plane"/"tank"); false for any other kind (ground vehicles have none in the reference). */
        public boolean autoThrottleDown(String kindName) {
            return switch (kindName == null ? "" : kindName) {
                case "helicopter" -> autoThrottleDownHeli();
                case "plane" -> autoThrottleDownPlane();
                case "tank" -> autoThrottleDownTank();
                default -> false;
            };
        }

        public boolean autoThrottleDownHeli() { return get(CLIENT_SPEC, autoThrottleDownHeli); }
        public boolean autoThrottleDownPlane() { return get(CLIENT_SPEC, autoThrottleDownPlane); }
        public boolean autoThrottleDownTank() { return get(CLIENT_SPEC, autoThrottleDownTank); }
        public boolean dismountAll() { return get(CLIENT_SPEC, dismountAll); }
        public boolean mouseControlFlightSimMode() { return get(CLIENT_SPEC, mouseControlFlightSimMode); }
        public boolean mouseControlStickModeHeli() { return get(CLIENT_SPEC, mouseControlStickModeHeli); }
        public boolean mouseControlStickModePlane() { return get(CLIENT_SPEC, mouseControlStickModePlane); }
        public boolean invertMouse() { return get(CLIENT_SPEC, invertMouse); }
        public double mouseSensitivity() { return get(CLIENT_SPEC, mouseSensitivity); }
        public boolean switchWeaponWithMouseWheel() { return get(CLIENT_SPEC, switchWeaponWithMouseWheel); }
        public boolean displayHUDThirdPerson() { return get(CLIENT_SPEC, displayHUDThirdPerson); }

        /** The hit-marker colour as 0xAARRGGBB (reference {@code hitMarkColorAlpha} in the top byte, {@code hitMarkColorRGB}
         *  below it); each channel clamped to 0..255 like the reference {@code toInt255}. */
        public int hitMarkColor() {
            String[] s = get(CLIENT_SPEC, hitMarkColor).split("\\s*,\\s*");
            if (s.length != 4) {
                return 0xFFFF0000;
            }
            int argb = 0;
            for (String c : s) {
                int v;
                try {
                    v = Integer.parseInt(c.trim());
                } catch (NumberFormatException e) {
                    return 0xFFFF0000;
                }
                argb = argb << 8 | Math.max(0, Math.min(255, v));
            }
            return argb;
        }

        static boolean isArgbList(Object o) {
            if (!(o instanceof String str)) {
                return false;
            }
            String[] s = str.trim().split("\\s*,\\s*");
            if (s.length != 4) {
                return false;
            }
            for (String c : s) {
                try {
                    Integer.parseInt(c.trim());
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            return true;
        }
        public boolean smoothShading() { return get(CLIENT_SPEC, smoothShading); }
        public boolean defaultExplosionParticle() { return get(CLIENT_SPEC, defaultExplosionParticle); }
        public double renderDistanceWeight() { return get(CLIENT_SPEC, renderDistanceWeight); }
        public boolean disableRenderLivingSpecials() { return get(CLIENT_SPEC, disableRenderLivingSpecials); }
    }
}
