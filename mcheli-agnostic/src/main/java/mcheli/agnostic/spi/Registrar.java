package mcheli.agnostic.spi;

import java.util.List;

/** Content lookup/registration: resolve items to opaque handles, register render models, expose recipes. */
public interface Registrar {
    ItemHandle itemByName(String name);

    void registerModel(ItemHandle item, ModelHandle model);

    List<RecipeView> recipesFor(ItemHandle output);

    /** A crafting recipe as the agnostic layer sees it — no {@code ItemStack} leak. */
    interface RecipeView {
        ItemHandle output();
        int outputCount();
        String displayName();
    }
}
