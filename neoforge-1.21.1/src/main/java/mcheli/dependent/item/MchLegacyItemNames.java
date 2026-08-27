package mcheli.dependent.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Resolves a 1.7.10 recipe ingredient — an item name plus the old damage/metadata value — to a 1.21.1 item.
 *
 * <p>Namespacing follows the reference {@code W_Item.getItemByName} (:31-36): a name with no {@code :} is assumed to
 * be {@code minecraft:}, so {@code iron_block} means vanilla and {@code mcheli:ah-6} cross-references another MCHeli
 * item. Beyond that the job is undoing "the flattening" — the 1.13 split of metadata-variant items into distinct ids.
 *
 * <p>Only the aliases the bundled fleet actually needs are mapped, plus the full 16-entry dye table (a content pack
 * can use any of them). A census of all 109 bundled recipe lines shows exactly two ingredients carry metadata at all:
 * {@code dye} (10 distinct values) and one {@code potion, 8193}.
 *
 * <p><b>Dye policy.</b> 1.7.10 {@code dye:15} literally became bone meal and {@code dye:4} lapis lazuli, but every
 * bundled use is plainly meant as paint — su-33, t-4 and pzl-m18 all ask for {@code dye,15} for a white airframe.
 * These map to the modern {@code *_dye} items rather than Mojang's literal flattening, so the recipes read the way
 * their author wrote them. Metadata is bounds-checked: an out-of-range value from a content pack falls back to the
 * base item rather than throwing out of recipe construction and killing mod loading.
 */
public final class MchLegacyItemNames {

    private MchLegacyItemNames() {}

    /** 1.7.10 dye damage value -> the modern dye item. Index IS the metadata. */
    private static final Item[] DYES = {
        Items.BLACK_DYE, Items.RED_DYE, Items.GREEN_DYE, Items.BROWN_DYE,
        Items.BLUE_DYE, Items.PURPLE_DYE, Items.CYAN_DYE, Items.LIGHT_GRAY_DYE,
        Items.GRAY_DYE, Items.PINK_DYE, Items.LIME_DYE, Items.YELLOW_DYE,
        Items.LIGHT_BLUE_DYE, Items.MAGENTA_DYE, Items.ORANGE_DYE, Items.WHITE_DYE,
    };

    /**
     * @param name the raw ingredient token, already lowercased ({@code "iron_block"}, {@code "mcheli:ah-6"})
     * @param meta the 1.7.10 damage value, or 0
     * @return the resolved item, or {@code null} if nothing in the registry matches (caller skips the recipe)
     */
    public static Item resolve(String name, int meta) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String id = name.indexOf(':') < 0 ? "minecraft:" + name : name;

        // Metadata-bearing legacy names, resolved before the plain registry lookup because the BASE name still
        // exists in 1.21.1 for some of these and would otherwise win with the wrong variant.
        if ("minecraft:dye".equals(id)) {
            return meta >= 0 && meta < DYES.length ? DYES[meta] : Items.WHITE_DYE;
        }

        Item direct = lookup(id);
        if (direct != null) {
            return direct;
        }
        // Flattened aliases: the 1.7.10 name is gone, so map it to the meta-0 variant the old item defaulted to.
        return switch (id) {
            case "minecraft:fish" -> Items.COD;
            case "minecraft:wool" -> Items.WHITE_WOOL;
            case "minecraft:log", "minecraft:log2" -> Items.OAK_LOG;
            case "minecraft:planks" -> Items.OAK_PLANKS;
            case "minecraft:boat" -> Items.OAK_BOAT;
            case "minecraft:sapling" -> Items.OAK_SAPLING;
            case "minecraft:leaves", "minecraft:leaves2" -> Items.OAK_LEAVES;
            case "minecraft:stone_slab", "minecraft:wooden_slab" -> Items.SMOOTH_STONE_SLAB;
            case "minecraft:sandstone" -> Items.SANDSTONE;
            case "minecraft:record_13" -> Items.MUSIC_DISC_13;
            case "minecraft:cooked_fished", "minecraft:cooked_fish" -> Items.COOKED_COD;
            case "minecraft:skull" -> Items.SKELETON_SKULL;
            case "minecraft:reeds" -> Items.SUGAR_CANE;
            case "minecraft:melon_block" -> Items.MELON;
            case "minecraft:web" -> Items.COBWEB;
            case "minecraft:slime" -> Items.SLIME_BLOCK;
            case "minecraft:hardened_clay" -> Items.TERRACOTTA;
            case "minecraft:stained_hardened_clay" -> Items.WHITE_TERRACOTTA;
            case "minecraft:stained_glass" -> Items.WHITE_STAINED_GLASS;
            case "minecraft:stained_glass_pane" -> Items.WHITE_STAINED_GLASS_PANE;
            case "minecraft:carpet" -> Items.WHITE_CARPET;
            case "minecraft:double_plant" -> Items.SUNFLOWER;
            case "minecraft:tallgrass" -> Items.SHORT_GRASS;
            case "minecraft:noteblock" -> Items.NOTE_BLOCK;
            case "minecraft:workbench" -> Items.CRAFTING_TABLE;
            case "minecraft:mossy_cobblestone" -> Items.MOSSY_COBBLESTONE;
            case "minecraft:lit_pumpkin" -> Items.JACK_O_LANTERN;
            case "minecraft:golden_rail" -> Items.POWERED_RAIL;
            case "minecraft:speckled_melon" -> Items.GLISTERING_MELON_SLICE;
            case "minecraft:iron_helmet" -> Items.IRON_HELMET;
            default -> null;
        };
    }

    private static Item lookup(String id) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        if (loc == null || !BuiltInRegistries.ITEM.containsKey(loc)) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(loc);
        return item == Items.AIR ? null : item;
    }
}
