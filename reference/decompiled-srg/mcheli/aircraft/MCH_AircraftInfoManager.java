/*
 * Decompiled with CFR 0.152.
 */
package mcheli.aircraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mcheli.MCH_IRecipeList;
import mcheli.MCH_InfoManagerBase;
import mcheli.aircraft.MCH_AircraftInfo;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.IRecipe;

public abstract class MCH_AircraftInfoManager
extends MCH_InfoManagerBase
implements MCH_IRecipeList {
    private List<IRecipe> listItemRecipe = new ArrayList();

    public int getRecipeListSize() {
        return this.listItemRecipe.size();
    }

    public IRecipe getRecipe(int index) {
        return (IRecipe)this.listItemRecipe.get(index);
    }

    public void addRecipe(IRecipe recipe, int count, String name, String recipeString) {
        if (recipe == null || recipe.func_77571_b() == null || recipe.func_77571_b().func_77973_b() == null) {
            throw new RuntimeException("[mcheli]Recipe Parameter Error! recipe" + count + " : " + name + ".txt : " + String.valueOf(recipe) + " : " + recipeString);
        }
        this.listItemRecipe.add(recipe);
    }

    public abstract MCH_AircraftInfo getAcInfoFromItem(Item var1);

    public MCH_AircraftInfo getAcInfoFromItem(IRecipe recipe) {
        Map map = this.getMap();
        if (recipe != null) {
            return this.getAcInfoFromItem(recipe.func_77571_b().func_77973_b());
        }
        return null;
    }
}

