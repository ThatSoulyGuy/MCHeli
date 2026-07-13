package mcheli.dependent.item;

import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Refill a fuel can by crafting it together with coal — the 1.21.1 port of {@code MCH_RecipeFuel}. Exactly ONE
 * (already-used) can plus at least one coal, anywhere in the grid; every coal restores {@code 100} units and every
 * charcoal {@code 75} (the reference's numbers). A full can does not match, so it cannot waste coal.
 */
public class MchFuelRefillRecipe extends CustomRecipe {

    public static final int COAL_REFILL = 100;
    public static final int CHARCOAL_REFILL = 75;

    public MchFuelRefillRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        int cans = 0;
        int coalSlots = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            if (s.getItem() instanceof MchFuelItem) {
                if (!s.isDamaged() || ++cans > 1) {
                    return false; // a full can (or a second can) never matches
                }
            } else if (isCoal(s)) {
                coalSlots++;
            } else {
                return false;
            }
        }
        return cans == 1 && coalSlots > 0;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack out = ItemStack.EMPTY;
        int restore = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.getItem() instanceof MchFuelItem) {
                out = s.copyWithCount(1);
            } else if (isCoal(s)) {
                // Vanilla crafting consumes exactly ONE item per slot regardless of stack size, and the reference
                // subtracts the refill once per coal SLOT (MCH_RecipeFuel:50-64). Counting s.getCount() here would let
                // a single 64-stack fully refill a can while only one coal is consumed.
                restore += s.is(Items.CHARCOAL) ? CHARCOAL_REFILL : COAL_REFILL;
            }
        }
        if (out.isEmpty()) {
            return ItemStack.EMPTY;
        }
        out.setDamageValue(Math.max(0, out.getDamageValue() - restore));
        return out;
    }

    private static boolean isCoal(ItemStack s) {
        return s.is(Items.COAL) || s.is(Items.CHARCOAL);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return MchRegistries.FUEL_REFILL.get();
    }
}
