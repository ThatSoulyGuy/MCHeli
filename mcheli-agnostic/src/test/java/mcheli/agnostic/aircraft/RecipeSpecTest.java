package mcheli.agnostic.aircraft;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link MCH_RecipeSpec}: the 1.7.10 {@code AddRecipe}/{@code AddShapelessRecipe} grammar (reference
 * {@code MCH_ItemRecipe}:138-250) and the sanitiser that reproduces 1.7.10's leniency for 1.21's strict
 * {@code ShapedRecipePattern}. Inputs are UPPERCASED, as {@code MCH_AircraftInfo} stores them.
 */
class RecipeSpecTest {

    private static MCH_RecipeSpec shaped(String data) {
        return MCH_RecipeSpec.parse(data, true);
    }

    private static MCH_RecipeSpec shapeless(String data) {
        return MCH_RecipeSpec.parse(data, false);
    }

    @Test
    void threeRowShapedRecipe() {
        MCH_RecipeSpec r = shaped("\"X X\", \" X \", \"X X\", X, IRON_BLOCK");
        assertNotNull(r);
        assertTrue(r.shaped);
        assertEquals(1, r.count);
        assertEquals(List.of("X X", " X ", "X X"), r.rows);
        assertEquals(1, r.keys.size());
        assertEquals('X', r.keys.get(0).symbol);
        assertEquals("iron_block", r.keys.get(0).itemName); // item names come back lower-cased
        assertEquals(0, r.keys.get(0).meta);
    }

    @Test
    void trailingNumberIsTheIngredientMetadata() {
        MCH_RecipeSpec r = shaped("\" D \", \"X X\", \"X X\", D, DYE, 1, X, IRON_BLOCK");
        assertNotNull(r);
        assertEquals(2, r.keys.size());
        assertEquals('D', r.keys.get(0).symbol);
        assertEquals("dye", r.keys.get(0).itemName);
        assertEquals(1, r.keys.get(0).meta);
        assertEquals('X', r.keys.get(1).symbol);
        assertEquals(0, r.keys.get(1).meta);
    }

    @Test
    void oneAndTwoRowPatternsAndNamespacedItems() {
        MCH_RecipeSpec one = shaped("\"RXR\", X, MCHELI:AH-6, R, REDSTONE");
        assertNotNull(one);
        assertEquals(List.of("RXR"), one.rows);
        assertEquals("mcheli:ah-6", one.keys.get(0).itemName);

        MCH_RecipeSpec two = shaped("\"XX\", \"XX\", X, IRON_INGOT");
        assertNotNull(two);
        assertEquals(List.of("XX", "XX"), two.rows);
    }

    @Test
    void leadingNumberIsTheShapedOutputCount() {
        MCH_RecipeSpec r = shaped("2, \"XX\", X, IRON_INGOT");
        assertNotNull(r);
        assertEquals(2, r.count);
        assertEquals(List.of("XX"), r.rows);
    }

    @Test
    void shapelessIsAFlatItemListAndNeverTakesACount() {
        MCH_RecipeSpec r = shapeless("IRON_BLOCK, IRON_BLOCK, DYE, 4");
        assertNotNull(r);
        assertFalse(r.shaped);
        assertTrue(r.rows.isEmpty());
        assertEquals(3, r.keys.size());
        assertEquals(0, r.keys.get(0).symbol);
        assertEquals("dye", r.keys.get(2).itemName);
        assertEquals(4, r.keys.get(2).meta);

        // reference MCH_ItemRecipe:207's count guard is dead code, so a leading number is an (invalid) item name
        assertNull(shapeless("2, IRON_BLOCK"));
    }

    @Test
    void trailingCommaIsTolerated() {
        // the implicit split limit of 0 drops the trailing empty token (two bundled lines rely on this)
        MCH_RecipeSpec r = shaped("\"X\", \"X\", X, IRON_INGOT,");
        assertNotNull(r);
        assertEquals(1, r.keys.size());
    }

    @Test
    void malformedLinesDegradeToNullWithoutThrowing() {
        assertNull(MCH_RecipeSpec.parse(null, true));
        assertNull(shaped("X, IRON"));                                  // shaped needs >= 3 tokens
        assertNull(shaped("IRON, GOLD, X"));                            // no quoted row at all
        assertDoesNotThrow(() -> shaped("\", X, IRON_INGOT"));          // lone quote: reference threw SIOOBE
        assertNull(shaped("\", X, IRON_INGOT"));
        assertNull(shaped("\"X\", , X, IRON_INGOT"));                   // empty token mid-line
        assertNull(shaped("\"X\", XY, IRON_INGOT"));                    // a symbol must be one letter
        assertNull(shaped("\"1\", 1, IRON_INGOT"));                     // ...and A-Z
        assertNull(shaped("\"X\", X, 5"));                              // a bare number where an item belongs
        assertNull(shaped("\"X\", \"X\", \"X\""));                      // rows but no keys
    }

    @Test
    void sanitiserDropsUnusedKeys() {
        // 1.7.10 ignored a key whose symbol never appears in the pattern; 1.21 rejects it
        MCH_RecipeSpec r = shaped("\"XX\", \"XX\", X, IRON_INGOT, Y, GOLD_INGOT");
        assertNotNull(r);
        assertEquals(1, r.keys.size());
        assertEquals('X', r.keys.get(0).symbol);
    }

    @Test
    void sanitiserBlanksUndefinedSymbols() {
        // 1.7.10 treated a pattern char with no key as an empty slot (robinson_r44's F, pzl-m18's P)
        MCH_RecipeSpec r = shaped("\"XFX\", \"XXX\", X, IRON_INGOT");
        assertNotNull(r);
        assertEquals(List.of("X X", "XXX"), r.rows);
    }

    @Test
    void sanitiserPadsRaggedRowsToARectangle() {
        MCH_RecipeSpec r = shaped("\"XXX\", \"X\", X, IRON_INGOT");
        assertNotNull(r);
        assertEquals(List.of("XXX", "X  "), r.rows);
    }

    @Test
    void patternThatSanitisesToNothingIsRejected() {
        // F has no key and X is never used: every key dropped and every cell blanked
        assertNull(shaped("\"FF\", \"FF\", X, IRON_INGOT"));
    }
}
