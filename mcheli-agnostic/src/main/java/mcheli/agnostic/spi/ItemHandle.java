package mcheli.agnostic.spi;

/**
 * Opaque identity of an EXISTING platform item that a definition refers to by its config name: a weapon's
 * {@code DispenseItem} and its {@code Item} (round cost) lines. The reference held {@code net.minecraft.item.Item} /
 * {@code ItemStack} there, resolved at load ({@code W_Item.getItemByName}, {@code MCH_WeaponInfoManager.setRoundItems});
 * the port resolves once at load too, through a {@link Resolver} the dependent layer supplies
 * ({@link mcheli.agnostic.weapon.MCH_WeaponInfoManager#resolveItems}), so every consumer reads the same answer instead
 * of re-parsing the raw name.
 *
 * <p>Implementations MUST provide value {@code equals}/{@code hashCode} (by registry name) so it can key maps the way
 * the reference used {@code Item} by reference identity.
 *
 * <p>Vehicle spawn items are NOT handles: they are registrations the dependent layer mints from the definition file
 * names (before any definition parses) and looks up by config name.
 */
public interface ItemHandle {
    /** The platform registry name, e.g. {@code "minecraft:bone_meal"}. */
    String name();

    /**
     * Resolves a config item token to a handle: the 1.7.10 item name as written in the file (a bare name means the
     * vanilla namespace) plus its 1.7.10 damage/metadata value, which selects the variant the 1.13 "flattening" split
     * out (e.g. {@code dye, 15} is bone meal). Returns {@code null} when no such item exists.
     */
    @FunctionalInterface
    interface Resolver {
        ItemHandle resolve(String name, int meta);
    }
}
