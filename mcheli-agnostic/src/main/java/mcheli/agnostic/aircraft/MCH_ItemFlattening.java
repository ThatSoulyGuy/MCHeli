package mcheli.agnostic.aircraft;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The 1.7.10 -> 1.21 item-name table for config ingredients (recipe {@code AddRecipe} items and weapon {@code Item} /
 * {@code DispenseItem} lines): undoing "the flattening", the 1.13 split of metadata variants into separate ids.
 *
 * <p>Pure names only (this module has no registry); the dependent {@code MchLegacyItemNames} looks the returned id up.
 * A config token is a 1.7.10 name plus its old damage value, so the table answers three kinds of name:
 * <ul>
 *   <li><b>metadata families</b> ({@link #flatten} indexes by the damage value, unpacking the block-state bits a log,
 *       leaf, slab, sapling or quartz pillar kept beside the variant; out of range -> the meta-0 member):
 *       {@code wool, 14} is red wool, {@code wooden_slab, 0} the OAK slab, {@code log, 5} a sideways spruce log;</li>
 *   <li><b>names that still exist but changed meaning</b>, which must be mapped BEFORE a plain registry lookup or the
 *       lookup wins with the wrong item: 1.7.10 {@code stone_slab} was today's {@code smooth_stone_slab} (1.21's
 *       {@code stone_slab} is a different, newer block), {@code snow} the snow block, {@code nether_brick} the block,
 *       {@code melon} the melon SLICE and {@code stone_stairs} the cobblestone stairs;</li>
 *   <li><b>names that are gone</b>, mapped to what the old item became ({@code boat} -> {@code oak_boat},
 *       {@code grass} -> {@code grass_block}).</li>
 * </ul>
 * Anything else returns {@code null}, and the caller looks the name up as written, so modern names in a content pack
 * work too. Dye is not here: its mapping depends on the use (a recipe means the paint colour, a dispensed item the
 * literal flattened item), so each dependent caller keeps its own dye table.
 *
 * <p>{@link #potionId} decodes a 1.7.10 potion damage value (us-2's recipe needs {@code potion, 8193}, a potion of
 * regeneration) to the 1.21 potion id, which a recipe must match through the item's potion-contents component.
 */
public final class MCH_ItemFlattening {
    private MCH_ItemFlattening() {}

    /** The 16 colours in 1.7.10 wool/carpet/stained-glass/stained-clay damage order (dye runs the other way). */
    private static final String[] COLOURS = {
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
    };

    /** 1.7.10 name -> its variants, indexed by the old damage value. */
    private static final Map<String, String[]> FAMILIES = new HashMap<>();
    /** 1.7.10 name -> 1.21 name, for names with no metadata family (gone, or still present with another meaning). */
    private static final Map<String, String> RENAMED = new HashMap<>();

    static {
        FAMILIES.put("wool", colours("_wool"));
        FAMILIES.put("carpet", colours("_carpet"));
        FAMILIES.put("stained_glass", colours("_stained_glass"));
        FAMILIES.put("stained_glass_pane", colours("_stained_glass_pane"));
        FAMILIES.put("stained_hardened_clay", colours("_terracotta"));
        FAMILIES.put("log", new String[] {"oak_log", "spruce_log", "birch_log", "jungle_log"});
        FAMILIES.put("log2", new String[] {"acacia_log", "dark_oak_log"});
        FAMILIES.put("planks", new String[] {
            "oak_planks", "spruce_planks", "birch_planks", "jungle_planks", "acacia_planks", "dark_oak_planks"});
        FAMILIES.put("sapling", new String[] {
            "oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling", "acacia_sapling", "dark_oak_sapling"});
        FAMILIES.put("leaves", new String[] {"oak_leaves", "spruce_leaves", "birch_leaves", "jungle_leaves"});
        FAMILIES.put("leaves2", new String[] {"acacia_leaves", "dark_oak_leaves"});
        FAMILIES.put("wooden_slab", new String[] {
            "oak_slab", "spruce_slab", "birch_slab", "jungle_slab", "acacia_slab", "dark_oak_slab"});
        // 1.7.10 stone_slab: 0 stone (the double-line smooth slab), 1 sandstone, 2 the old wooden "stone" slab, ...
        FAMILIES.put("stone_slab", new String[] {
            "smooth_stone_slab", "sandstone_slab", "petrified_oak_slab", "cobblestone_slab",
            "brick_slab", "stone_brick_slab", "nether_brick_slab", "quartz_slab"});
        FAMILIES.put("sandstone", new String[] {"sandstone", "chiseled_sandstone", "cut_sandstone"});
        FAMILIES.put("stonebrick", new String[] {
            "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks"});
        FAMILIES.put("quartz_block", new String[] {"quartz_block", "chiseled_quartz_block", "quartz_pillar"});
        FAMILIES.put("red_flower", new String[] {
            "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip",
            "pink_tulip", "oxeye_daisy"});
        FAMILIES.put("double_plant", new String[] {"sunflower", "lilac", "tall_grass", "large_fern", "rose_bush", "peony"});
        FAMILIES.put("tallgrass", new String[] {"dead_bush", "short_grass", "fern"}); // tallgrass:0 was the dead shrub
        FAMILIES.put("skull", new String[] {
            "skeleton_skull", "wither_skeleton_skull", "zombie_head", "player_head", "creeper_head"});
        FAMILIES.put("fish", new String[] {"cod", "salmon", "tropical_fish", "pufferfish"});
        FAMILIES.put("cooked_fished", new String[] {"cooked_cod", "cooked_salmon"});
        FAMILIES.put("cooked_fish", new String[] {"cooked_cod", "cooked_salmon"});
        FAMILIES.put("coal", new String[] {"coal", "charcoal"});
        FAMILIES.put("golden_apple", new String[] {"golden_apple", "enchanted_golden_apple"});
        FAMILIES.put("cobblestone_wall", new String[] {"cobblestone_wall", "mossy_cobblestone_wall"});
        FAMILIES.put("sand", new String[] {"sand", "red_sand"});
        FAMILIES.put("dirt", new String[] {"dirt", "coarse_dirt", "podzol"});
        FAMILIES.put("anvil", new String[] {"anvil", "chipped_anvil", "damaged_anvil"});
        FAMILIES.put("monster_egg", new String[] { // the silverfish blocks
            "infested_stone", "infested_cobblestone", "infested_stone_bricks", "infested_mossy_stone_bricks",
            "infested_cracked_stone_bricks", "infested_chiseled_stone_bricks"});

        // Still registered in 1.21, but as something else: map before the plain lookup. (An audit of every 1.7.10
        // vanilla item/block name against the 1.21.1 registry: these, stone_slab above, and nothing else.)
        RENAMED.put("snow", "snow_block");
        RENAMED.put("snow_layer", "snow");
        RENAMED.put("nether_brick", "nether_bricks");
        RENAMED.put("netherbrick", "nether_brick");
        RENAMED.put("melon", "melon_slice");               // 1.7.10 melon was the SLICE; 1.21 melon is the block
        RENAMED.put("stone_stairs", "cobblestone_stairs"); // 1.21 stone_stairs is the 1.14 smooth-stone stairs
        // Gone in 1.21.
        RENAMED.put("grass", "grass_block");               // the block; 1.13-1.20.2 "grass" was the plant (short_grass)
        RENAMED.put("deadbush", "dead_bush");
        RENAMED.put("waterlily", "lily_pad");
        RENAMED.put("sign", "oak_sign");
        RENAMED.put("bed", "red_bed");                     // every 1.7.10 bed was red
        RENAMED.put("boat", "oak_boat");
        RENAMED.put("reeds", "sugar_cane");
        RENAMED.put("melon_block", "melon");
        RENAMED.put("web", "cobweb");
        RENAMED.put("slime", "slime_block");
        RENAMED.put("hardened_clay", "terracotta");
        RENAMED.put("noteblock", "note_block");
        RENAMED.put("workbench", "crafting_table");
        RENAMED.put("lit_pumpkin", "jack_o_lantern");
        RENAMED.put("golden_rail", "powered_rail");
        RENAMED.put("speckled_melon", "glistering_melon_slice");
        RENAMED.put("yellow_flower", "dandelion");
        RENAMED.put("brick_block", "bricks");
        RENAMED.put("fence", "oak_fence");
        RENAMED.put("fence_gate", "oak_fence_gate");
        RENAMED.put("trapdoor", "oak_trapdoor");
        RENAMED.put("wooden_door", "oak_door");
        RENAMED.put("wooden_button", "oak_button");
        RENAMED.put("wooden_pressure_plate", "oak_pressure_plate");
        RENAMED.put("mob_spawner", "spawner");
        RENAMED.put("quartz_ore", "nether_quartz_ore");
        RENAMED.put("fireworks", "firework_rocket");
        RENAMED.put("firework_charge", "firework_star");
    }

    private static String[] colours(String suffix) {
        String[] out = new String[COLOURS.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = COLOURS[i] + suffix;
        }
        return out;
    }

    /**
     * The 1.21 id ({@code minecraft:...}) for a 1.7.10 item token, or {@code null} if the table has no entry and the
     * name should be looked up as written.
     *
     * @param name the token, lower case, with or without the {@code minecraft:} namespace; other namespaces -> null
     * @param meta the 1.7.10 damage value (0 if the config gave none)
     */
    public static String flatten(String name, int meta) {
        String path = vanillaPath(name);
        if (path == null) {
            return null;
        }
        String[] family = FAMILIES.get(path);
        if (family != null) {
            int v = variantIndex(path, meta);
            return "minecraft:" + family[v >= 0 && v < family.length ? v : 0];
        }
        String renamed = RENAMED.get(path);
        if (renamed != null) {
            return "minecraft:" + renamed;
        }
        if (path.startsWith("record_")) {
            return "minecraft:music_disc_" + path.substring("record_".length()); // record_13 -> music_disc_13
        }
        return null;
    }

    /**
     * The family index a 1.7.10 damage value names. Block metadata packed other state next to the variant, and an
     * item damage copied from a placed block keeps it: a log's axis and a leaf's decay flags sit above the two variant
     * bits ({@code meta & 3}), a slab's top-half flag and a sapling's growth stage in bit 3 ({@code meta & 7}), and
     * {@code quartz_block} 2, 3 and 4 are the one pillar on its three axes. Only the 4-bit block range is unpacked, so
     * a wildcard ({@code 32767}) still falls back to the meta-0 member instead of landing on an arbitrary variant.
     */
    private static int variantIndex(String path, int meta) {
        if (meta < 0 || meta > 15) {
            return meta;
        }
        return switch (path) {
            case "log", "log2", "leaves", "leaves2" -> meta & 3;
            case "wooden_slab", "stone_slab", "sapling" -> meta & 7;
            case "quartz_block" -> meta >= 2 && meta <= 4 ? 2 : meta;
            default -> meta;
        };
    }

    /** True for a 1.7.10 potion damage value with the splash bit (16384) set: the item is a splash potion. */
    public static boolean isSplashPotion(int meta) {
        return (meta & 16384) != 0;
    }

    /**
     * The 1.21 potion id for a 1.7.10 potion damage value: the low four bits pick the effect, bit 5 (32) is level II
     * and bit 6 (64) the extended duration, e.g. {@code 8193} -> {@code minecraft:regeneration}, {@code 8257} ->
     * {@code minecraft:long_regeneration}. A variant 1.21 does not have falls back to the plain potion
     * ({@code fire_resistance} has no strong form). Effect 0 is water / awkward / thick / mundane. Returns null for an
     * effect value 1.7.10 never brewed (7, 11, 15).
     */
    public static String potionId(int meta) {
        int effect = meta & 15;
        boolean strong = (meta & 32) != 0;
        boolean extended = (meta & 64) != 0;
        String base;
        boolean hasStrong;
        boolean hasLong;
        switch (effect) {
            case 0 -> {
                if ((meta & 0x3FFF) == 0) {
                    return "minecraft:water";
                }
                return "minecraft:" + ((meta & 16) != 0 ? "awkward" : (meta & 32) != 0 ? "thick" : "mundane");
            }
            case 1 -> { base = "regeneration"; hasStrong = true; hasLong = true; }
            case 2 -> { base = "swiftness"; hasStrong = true; hasLong = true; }
            case 3 -> { base = "fire_resistance"; hasStrong = false; hasLong = true; }
            case 4 -> { base = "poison"; hasStrong = true; hasLong = true; }
            case 5 -> { base = "healing"; hasStrong = true; hasLong = false; }
            case 6 -> { base = "night_vision"; hasStrong = false; hasLong = true; }
            case 8 -> { base = "weakness"; hasStrong = false; hasLong = true; }
            case 9 -> { base = "strength"; hasStrong = true; hasLong = true; }
            case 10 -> { base = "slowness"; hasStrong = true; hasLong = true; }
            case 12 -> { base = "harming"; hasStrong = true; hasLong = false; }
            case 13 -> { base = "water_breathing"; hasStrong = false; hasLong = true; }
            case 14 -> { base = "invisibility"; hasStrong = false; hasLong = true; }
            default -> { return null; }
        }
        if (strong && hasStrong) {
            return "minecraft:strong_" + base;
        }
        if (extended && hasLong) {
            return "minecraft:long_" + base;
        }
        return "minecraft:" + base;
    }

    /** The path of a vanilla token ({@code x} or {@code minecraft:x}), or null for another namespace. */
    private static String vanillaPath(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String n = name.toLowerCase(Locale.ROOT);
        int colon = n.indexOf(':');
        if (colon < 0) {
            return n;
        }
        return n.startsWith("minecraft:") ? n.substring(colon + 1) : null;
    }
}
