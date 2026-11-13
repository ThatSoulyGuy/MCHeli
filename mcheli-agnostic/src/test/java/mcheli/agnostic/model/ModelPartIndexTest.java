package mcheli.agnostic.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The group-name index behind the renderers' part drawing ({@link ModelPartIndex}), against the reference walk. */
class ModelPartIndexTest {

    @Test
    void mqoDollarPartTakesItsTrailingSubMeshes() {
        ModelPartIndex idx = ModelPartIndex.of(
            List.of("$body", "glass", "seat", "$weapon0", "barrel", "$lg0", "strut"), true);
        assertArrayEquals(new int[]{0, 1, 2}, idx.span("$body"));
        assertArrayEquals(new int[]{3, 4}, idx.span("$WEAPON0"), "case-insensitive, like equalsIgnoreCase");
        assertArrayEquals(new int[]{5, 6}, idx.span("$lg0"), "a span may run to the end of the file");
        assertArrayEquals(new int[0], idx.span("$missing"));
    }

    @Test
    void terminatingDollarGroupIsSkippedLikeTheReference() {
        // W_MetasequoiaObject.renderPart: the inner loop stops ON the next $-group and the outer i++ steps past it,
        // so an adjacent duplicate $a (index 2) is never tested; the later one (index 4) still draws its own span.
        ModelPartIndex idx = ModelPartIndex.of(List.of("$a", "x", "$a", "$b", "$a", "y"), true);
        assertArrayEquals(new int[]{0, 1, 4, 5}, idx.span("$a"));
        // Directly adjacent duplicates: the second one terminates the first span and is skipped.
        ModelPartIndex adj = ModelPartIndex.of(List.of("$a", "$a", "z"), true);
        assertArrayEquals(new int[]{0}, adj.span("$a"));
    }

    @Test
    void objAndPlainNamesAreExactMatchOnly() {
        ModelPartIndex obj = ModelPartIndex.of(List.of("$body", "glass", "$weapon0"), false);
        assertArrayEquals(new int[]{0}, obj.span("$body"), "OBJ parts have no trailing span");
        ModelPartIndex mqo = ModelPartIndex.of(List.of("$body", "glass", "Glass"), true);
        assertArrayEquals(new int[]{1, 2}, mqo.span("glass"), "a non-$ name draws every exact match");
        assertArrayEquals(new int[]{1, 2}, mqo.exact("GLASS"));
        assertTrue(mqo.contains("$BODY"));
        assertFalse(mqo.contains("$canopy"));
    }
}
