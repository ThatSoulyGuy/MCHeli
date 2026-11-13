package mcheli.dependent.item;

import mcheli.agnostic.aircraft.MCH_ItemFlattening;
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
 * <p>The name table itself is the agnostic {@link MCH_ItemFlattening} (unit-tested there): metadata families such as
 * {@code wool, 14} or {@code wooden_slab, 0} (the oak slab), names that still exist with another meaning (1.7.10
 * {@code stone_slab} is today's smooth stone slab), and names that are gone ({@code boat}). Those are applied BEFORE
 * the plain registry lookup, which would otherwise win with the wrong item. A census of all 109 bundled recipe lines
 * shows exactly two ingredients carry metadata at all: {@code dye} (9 distinct values: 1, 2, 3, 4, 7, 10, 12, 14,
 * 15) and one {@code potion, 8193}.
 *
 * <p><b>Dye policy.</b> 1.7.10 {@code dye:15} literally became bone meal and {@code dye:4} lapis lazuli, but every
 * bundled use is plainly meant as paint — su-33, t-4 and pzl-m18 all ask for {@code dye,15} for a white airframe.
 * These map to the modern {@code *_dye} items rather than Mojang's literal flattening, so the recipes read the way
 * their author wrote them. Metadata is bounds-checked: an out-of-range value from a content pack falls back to the
 * base item rather than throwing out of recipe construction and killing mod loading.
 *
 * <p><b>Potions.</b> A 1.21 potion is one item whose effect lives in a data component, and a plain
 * {@code {"item":"minecraft:potion"}} ingredient ignores components, so it would accept ANY potion (even water). For a
 * recipe {@link #ingredientJson} therefore emits a NeoForge {@code neoforge:components} ingredient that only matches
 * the potion the 1.7.10 damage value named (us-2's {@code potion, 8193} is a potion of regeneration).
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

        if ("minecraft:dye".equals(id)) {
            return meta >= 0 && meta < DYES.length ? DYES[meta] : Items.WHITE_DYE;
        }
        if ("minecraft:potion".equals(id)) {
            // The splash bit picked the item in 1.7.10; the effect itself is a component now (see ingredientJson).
            return MCH_ItemFlattening.isSplashPotion(meta) ? Items.SPLASH_POTION : Items.POTION;
        }
        // Flattened / renamed 1.7.10 names first: some of them still exist in 1.21.1 as a DIFFERENT item.
        String flat = MCH_ItemFlattening.flatten(id, meta);
        if (flat != null) {
            Item item = lookup(flat);
            if (item != null) {
                return item;
            }
        }
        return lookup(id);
    }

    /**
     * The recipe-JSON ingredient for a token: {@code {"item":"<id>"}}, or, for a potion with a 1.7.10 damage value, a
     * {@code neoforge:components} ingredient that only matches that potion. Null if the token does not resolve.
     */
    public static String ingredientJson(String name, int meta) {
        Item item = resolve(name, meta);
        if (item == null) {
            return null;
        }
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        if ((item == Items.POTION || item == Items.SPLASH_POTION) && meta > 0) {
            String potion = MCH_ItemFlattening.potionId(meta);
            ResourceLocation potionId = potion != null ? ResourceLocation.tryParse(potion) : null;
            if (potionId != null && BuiltInRegistries.POTION.containsKey(potionId)) {
                return "{\"type\":\"neoforge:components\",\"items\":\"" + id
                    + "\",\"components\":{\"minecraft:potion_contents\":{\"potion\":\"" + potionId + "\"}}}";
            }
        }
        return "{\"item\":\"" + id + "\"}";
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
