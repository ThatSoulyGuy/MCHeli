package mcheli.dependent;

import java.util.ArrayList;
import java.util.List;
import mcheli.MCHeli;
import mcheli.dependent.item.MchLegacyItemNames;
import mcheli.dependent.item.MchVehicleRecipes;
import mcheli.dependent.item.VehicleSpawnItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Headless proof that the generated vehicle crafting recipes ({@code MchVehicleRecipes} served by
 * {@code MchRecipePack}) reach the recipe manager as real {@code CRAFTING} recipes, and that the 1.7.10 ingredient
 * names come out right:
 * <ul>
 *   <li>EVERY recipe {@code MchVehicleRecipes} generated reached the recipe manager as a crafting recipe producing a
 *       {@link VehicleSpawnItem}, and there are at least 98 of them: the 109 bundled lines minus the 10 UAV lines and
 *       e767's, which needs the unported {@code mcheli:uav_station};</li>
 *   <li>no recipe produces a UAV: without the UAV station its spawn item cannot be used, so crafting one would only
 *       waste the materials;</li>
 *   <li>us-2's {@code potion, 8193} ingredient accepts a potion of regeneration and REJECTS a water bottle (a plain
 *       item ingredient would accept any potion);</li>
 *   <li>the legacy name table: {@code wooden_slab, 0} is the oak slab and {@code stone_slab, 0} the smooth stone slab;</li>
 *   <li>the hand-written fuel-can refill recipe is loaded too.</li>
 * </ul>
 */
public final class DemoRecipeSelfTest extends MchSelfTest {

    @Override
    protected int[] areaChunks() {
        return new int[] {0, 0, 0, 0}; // no world needed
    }

    @Override
    protected void start() {
        var recipes = this.server.getRecipeManager();
        int vehicleRecipes = 0;
        List<String> uavRecipes = new ArrayList<>();
        String sample = "<none>";
        for (RecipeHolder<?> holder : recipes.getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack result = holder.value().getResultItem(this.server.registryAccess());
            if (result.getItem() instanceof VehicleSpawnItem v) {
                vehicleRecipes++;
                if ("<none>".equals(sample)) {
                    sample = holder.id() + " -> " + v.configName;
                }
                if (v.isUAV()) {
                    uavRecipes.add(holder.id().toString());
                }
            }
        }
        int generated = MchVehicleRecipes.table().size();
        boolean countOk = vehicleRecipes == generated && vehicleRecipes >= 98;
        boolean noUav = uavRecipes.isEmpty();
        LOG.info("[RECIPE-SELFTEST] {} vehicle crafting recipes loaded of {} generated (need all, and >= 98), e.g. {}; "
            + "UAV recipes: {}", vehicleRecipes, generated, sample, uavRecipes);

        // us-2: AddRecipe = "GPG", "XIX", "XXX", X,iron_block, G,dye,4, P,glass, I,potion,8193
        boolean potionOk = false;
        String potionDetail = "us-2 recipe missing";
        var us2 = recipes.byKey(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "us-2"));
        if (us2.isPresent()) {
            ItemStack regen = PotionContents.createItemStack(Items.POTION, Potions.REGENERATION);
            ItemStack water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
            Ingredient potionSlot = null;
            for (Ingredient ing : us2.get().value().getIngredients()) {
                if (!ing.isEmpty() && ing.test(regen)) {
                    potionSlot = ing;
                    break;
                }
            }
            potionOk = potionSlot != null && !potionSlot.test(water);
            potionDetail = potionSlot == null ? "no ingredient accepts a regeneration potion"
                : "acceptsRegeneration=true rejectsWater=" + !potionSlot.test(water);
        }

        boolean namesOk = MchLegacyItemNames.resolve("wooden_slab", 0) == Items.OAK_SLAB
            && MchLegacyItemNames.resolve("stone_slab", 0) == Items.SMOOTH_STONE_SLAB
            && MchLegacyItemNames.resolve("wool", 14) == Items.RED_WOOL
            && MchLegacyItemNames.resolve("dye", 15) == Items.WHITE_DYE; // the recipe paint policy

        boolean refillOk = recipes.byKey(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "fuel_refill")).isPresent();

        finish(countOk && noUav && potionOk && namesOk && refillOk,
            "vehicleRecipes={}/{} generated (need all, >=98) uavRecipes={} us2Potion={} ({}) legacyNames={} fuelRefill={}",
            vehicleRecipes, generated, uavRecipes.size(), potionOk, potionDetail, namesOk, refillOk);
    }
}
