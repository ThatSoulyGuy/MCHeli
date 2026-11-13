package mcheli.dependent.port;

import mcheli.agnostic.spi.ItemHandle;
import mcheli.dependent.item.MchLegacyItemNames;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The {@link ItemHandle} implementation: a registered {@link Item} plus its registry name, equal by name.
 *
 * <p>{@link #resolve} is the {@link ItemHandle.Resolver} the mod hands to
 * {@code MCH_WeaponInfoManager.resolveItems} at common setup, once the item registry is frozen, so every weapon's
 * {@code DispenseItem} and ammo {@code Item} lines are resolved ONCE, the same way, for every consumer (the dispenser
 * impact, the server resupply, the riding GUI). Before this, each consumer re-parsed the raw name on its own and none
 * understood 1.7.10 names: {@code boat} (now {@code oak_boat}) and {@code dye, 15} resolved to air, so the bundled boat
 * and fertilizer dispensers did nothing and could never be resupplied.
 */
public final class NeoItemHandle implements ItemHandle {

    /** 1.7.10 {@code ItemDye} damage value -> the item the 1.13 flattening turned it into. Index IS the metadata. */
    private static final Item[] LEGACY_DYE = {
        Items.INK_SAC, Items.RED_DYE, Items.GREEN_DYE, Items.COCOA_BEANS,
        Items.LAPIS_LAZULI, Items.PURPLE_DYE, Items.CYAN_DYE, Items.LIGHT_GRAY_DYE,
        Items.GRAY_DYE, Items.PINK_DYE, Items.LIME_DYE, Items.YELLOW_DYE,
        Items.LIGHT_BLUE_DYE, Items.MAGENTA_DYE, Items.ORANGE_DYE, Items.BONE_MEAL,
    };

    private final Item item;
    private final String name;

    private NeoItemHandle(Item item) {
        this.item = item;
        this.name = BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /** The handle of a registered item, or null for null/air. */
    public static NeoItemHandle of(Item item) {
        return item == null || item == Items.AIR ? null : new NeoItemHandle(item);
    }

    /**
     * Resolve a weapon config item token ({@link ItemHandle.Resolver}). A bare name is {@code minecraft:} (reference
     * {@code W_Item.getItemByName}); flattened 1.7.10 names go through {@link MchLegacyItemNames}. {@code dye} is
     * mapped LITERALLY here ({@code dye, 15} is bone meal, {@code dye, 4} lapis): a weapon item is used or consumed
     * (the fertilizer dispenser bone-meals crops), unlike a recipe ingredient, which {@link MchLegacyItemNames} maps
     * to the paint colour its author meant.
     */
    public static ItemHandle resolve(String name, int meta) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String n = name.trim().toLowerCase(java.util.Locale.ROOT);
        if (n.equals("dye") || n.equals("minecraft:dye")) {
            return of(meta >= 0 && meta < LEGACY_DYE.length ? LEGACY_DYE[meta] : Items.INK_SAC);
        }
        return of(MchLegacyItemNames.resolve(n, meta));
    }

    /** The item behind a handle, or null if {@code h} is null or not one of ours. */
    public static Item itemOf(ItemHandle h) {
        return h instanceof NeoItemHandle n ? n.item : null;
    }

    public Item item() {
        return this.item;
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NeoItemHandle other && other.name.equals(this.name);
    }

    @Override
    public int hashCode() {
        return this.name.hashCode();
    }

    @Override
    public String toString() {
        return this.name;
    }
}
