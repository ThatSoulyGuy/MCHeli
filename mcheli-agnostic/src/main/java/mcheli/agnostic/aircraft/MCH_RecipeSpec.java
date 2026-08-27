package mcheli.agnostic.aircraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tokenizer for one {@code AddRecipe} / {@code AddShapelessRecipe} line, ported from
 * {@code MCH_ItemRecipe.addShapedRecipe}/{@code addShapelessRecipe} (reference :138-250).
 *
 * <p>Pure Java by construction — this lives in the zero-Minecraft module, so it resolves nothing. It hands back the
 * raw grammar (rows, symbols, item NAMES, 1.7.10 metadata) and the dependent layer turns those names into real items.
 *
 * <p>The 1.7.10 grammar, on the already-uppercased directive value, split on {@code \s*,\s*}:
 * <pre>
 *   AddRecipe = "X X",  " X ",  "X X",  X, iron_block
 *   AddRecipe = " D ",  "X X",  "X X",  D, dye, 1,  X, iron_block     &lt;- an item may carry a trailing metadata number
 *   AddRecipe = "RXR",  X, mcheli:ah-6,  R, redstone                  &lt;- 1, 2 or 3 rows all parse
 *   AddShapelessRecipe = iron_block, iron_block                       &lt;- flat item list, no symbols
 * </pre>
 *
 * <h2>Two deliberate departures from the reference</h2>
 * <ol>
 *   <li><b>Bounds-safety in the row loop.</b> The reference tests {@code s[idx].length() <= 0} and does not bound
 *       {@code idx}, so a lone {@code "} token makes it throw {@code StringIndexOutOfBoundsException} out of
 *       {@code subSequence(1, 0)}. This uses {@code <= 1} plus an {@code idx} bound instead. Strictly safer, same
 *       result on every well-formed line — do not "restore parity" here.</li>
 *   <li><b>{@link #sanitise}</b>, which has no reference counterpart because 1.7.10 did not need it. See below.</li>
 * </ol>
 *
 * <h2>Why the sanitiser exists</h2>
 * 1.7.10's {@code CraftingManager.addRecipe} was lenient in two ways that 1.21.1's {@code ShapedRecipePattern.unpack}
 * rejects outright: a pattern character with no key entry became an EMPTY slot, and a key entry whose symbol never
 * appeared in the pattern was ignored. Nine bundled configs rely on that leniency (unused keys in fl282, robinson_r44,
 * f4a, macchi_mc72, p-51d, pzl-m18, kv-2; undefined symbols in robinson_r44 and pzl-m18). Without the sanitiser those
 * nine throw at load and lose their recipe. Note the semantic consequence, which IS faithful: robinson_r44's {@code F}
 * and pzl-m18's {@code P} become empty slots, so those recipes are genuinely one ingredient smaller than they read.
 */
public final class MCH_RecipeSpec {

    /** One key binding. For a shapeless spec {@link #symbol} is {@code 0} and only {@link #itemName}/{@link #meta} matter. */
    public static final class Key {
        public final char symbol;
        public final String itemName;
        public final int meta;

        public Key(char symbol, String itemName, int meta) {
            this.symbol = symbol;
            this.itemName = itemName;
            this.meta = meta;
        }
    }

    public final boolean shaped;
    public final int count;
    public final List<String> rows;
    public final List<Key> keys;

    private MCH_RecipeSpec(boolean shaped, int count, List<String> rows, List<Key> keys) {
        this.shaped = shaped;
        this.count = count;
        this.rows = rows;
        this.keys = keys;
    }

    /**
     * Parse one directive value, or return {@code null} if it is unusable. Never throws — a bad content-pack line
     * must degrade to "this vehicle has no recipe", never to a load crash.
     *
     * @param data   the directive value, already uppercased by {@code MCH_AircraftInfo}
     * @param shaped {@code true} for {@code AddRecipe}, {@code false} for {@code AddShapelessRecipe}
     */
    public static MCH_RecipeSpec parse(String data, boolean shaped) {
        if (data == null) {
            return null;
        }
        // NOTE: the implicit limit of 0 is load-bearing — it DISCARDS trailing empty strings, and two bundled recipe
        // lines end in a trailing comma. Using split(regex, -1) would feed those an empty final token, which the key
        // loop below rejects, silently costing two vehicles their recipe.
        String[] s = data.split("\\s*,\\s*");
        for (int i = 0; i < s.length; i++) {
            s[i] = s[i].trim();
        }
        if (shaped ? s.length < 3 : s.length < 1) {
            return null;
        }

        int start = 0;
        int count = 1;
        if (shaped && isNumber(s[0])) {
            // Output count, e.g. "2, ..." — used by the reference's hand-built stinger/javelin item recipes. No
            // bundled VEHICLE config uses it. Shapeless deliberately never consumes one: the reference's guard at
            // MCH_ItemRecipe.java:207 is dead code (`isNumber(s[0]) && createNum <= 0`), so count stays 1 there.
            start = 1;
            count = Math.max(1, parseIntSafe(s[0]));
        }

        List<String> rows = new ArrayList<>(3);
        List<Key> keys = new ArrayList<>();
        int idx = start;

        if (shaped) {
            for (int i = start; i < 3 + start; i++) {
                if (idx >= s.length) {
                    break;
                }
                String t = s[idx];
                // A non-quoted token is NOT a row: skip it WITHOUT advancing idx, so 1- and 2-row patterns parse.
                if (t.length() <= 1 || t.charAt(0) != '"' || t.charAt(t.length() - 1) != '"') {
                    continue;
                }
                rows.add(t.substring(1, t.length() - 1));
                idx++;
            }
            if (rows.isEmpty()) {
                return null;
            }
        }

        boolean expectSymbol = shaped;
        char pending = 0;
        while (idx < s.length) {
            String t = s[idx];
            if (t.isEmpty()) {
                return null;
            }
            if (expectSymbol) {
                if (t.length() != 1) {
                    return null;
                }
                char c = Character.toUpperCase(t.charAt(0));
                if (c < 'A' || c > 'Z') {
                    return null;
                }
                pending = c;
            } else {
                String name = t.toLowerCase(Locale.ROOT);
                if (isNumber(name)) {
                    return null; // a bare number where an item name belongs
                }
                int meta = 0;
                if (idx + 1 < s.length && isNumber(s[idx + 1])) {
                    meta = parseIntSafe(s[++idx]);
                }
                keys.add(new Key(shaped ? pending : (char) 0, name, meta));
            }
            if (shaped) {
                expectSymbol = !expectSymbol;
            }
            idx++;
        }

        if (keys.isEmpty()) {
            return null;
        }
        if (!shaped) {
            return new MCH_RecipeSpec(false, count, rows, keys);
        }

        padRows(rows);
        sanitise(rows, keys);
        if (rows.isEmpty() || keys.isEmpty()) {
            return null;
        }
        for (String r : rows) {
            if (!r.isBlank()) {
                return new MCH_RecipeSpec(true, count, rows, keys);
            }
        }
        return null; // an all-blank pattern makes ShapedRecipePattern.shrink return a zero-length array
    }

    /** Right-pad to a rectangle. No bundled config is ragged, but a content-pack line can be. */
    private static void padRows(List<String> rows) {
        int w = 0;
        for (String r : rows) {
            w = Math.max(w, r.length());
        }
        for (int i = 0; i < rows.size(); i++) {
            StringBuilder sb = new StringBuilder(rows.get(i));
            while (sb.length() < w) {
                sb.append(' ');
            }
            rows.set(i, sb.toString());
        }
    }

    /** Reproduce 1.7.10's leniency: drop keys no row uses, and blank out row chars no key defines. */
    private static void sanitise(List<String> rows, List<Key> keys) {
        StringBuilder used = new StringBuilder();
        for (String r : rows) {
            used.append(r);
        }
        String pattern = used.toString();
        keys.removeIf(k -> pattern.indexOf(k.symbol) < 0);

        StringBuilder defined = new StringBuilder();
        for (Key k : keys) {
            defined.append(k.symbol);
        }
        String defs = defined.toString();
        for (int i = 0; i < rows.size(); i++) {
            StringBuilder sb = new StringBuilder(rows.get(i));
            for (int j = 0; j < sb.length(); j++) {
                char c = sb.charAt(j);
                if (c != ' ' && defs.indexOf(c) < 0) {
                    sb.setCharAt(j, ' ');
                }
            }
            rows.set(i, sb.toString());
        }
    }

    /** Port of {@code MCH_ItemRecipe.isNumber} (:286-297): non-empty and every character a digit. */
    private static boolean isNumber(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /** {@link #isNumber} has already vetted the digits; this only guards an overlong run of them. */
    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
