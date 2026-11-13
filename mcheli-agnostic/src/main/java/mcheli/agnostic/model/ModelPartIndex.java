package mcheli.agnostic.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Name lookup for a parsed model's groups, built once per model so a renderer does not scan every group by name for
 * every part it draws each frame (a tank stamps one crawler link per track point, a propeller one blade per copy).
 *
 * <p>{@link #span} reproduces the reference part primitive exactly:
 * <ul>
 *   <li><b>MQO, {@code $}-name</b> ({@code W_MetasequoiaObject.renderPart}:263-281): every group named {@code name}
 *       (case-insensitive) PLUS the non-{@code $} groups that follow it, up to the next {@code $}-group. The scan then
 *       resumes AFTER that terminating {@code $}-group (the reference's inner loop leaves {@code i} on it and the outer
 *       {@code for}'s {@code i++} steps past it), so a matching group directly after a span is not drawn twice and
 *       not drawn at all when it terminates the previous span.</li>
 *   <li><b>Otherwise</b> (an OBJ, or a name without {@code $}): every group with exactly that name.</li>
 * </ul>
 * ZERO Minecraft imports.
 */
public final class ModelPartIndex {
    private static final int[] NONE = new int[0];

    private final String[] lowerNames;
    private final Map<String, int[]> exact = new HashMap<>();
    private final Map<String, int[]> spans = new HashMap<>();

    private ModelPartIndex(List<String> names, boolean mqo) {
        int n = names.size();
        this.lowerNames = new String[n];
        Map<String, List<Integer>> byName = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String name = names.get(i) == null ? "" : names.get(i);
            this.lowerNames[i] = name.toLowerCase(Locale.ROOT);
            byName.computeIfAbsent(this.lowerNames[i], k -> new ArrayList<>()).add(i);
        }
        for (Map.Entry<String, List<Integer>> e : byName.entrySet()) {
            int[] ids = toArray(e.getValue());
            this.exact.put(e.getKey(), ids);
            this.spans.put(e.getKey(), mqo && e.getKey().startsWith("$") ? span(names, this.lowerNames, e.getKey()) : ids);
        }
    }

    public static ModelPartIndex of(MchModel model) {
        List<String> names = new ArrayList<>();
        for (ModelGroup g : model.groups()) {
            names.add(g == null ? "" : g.name);
        }
        return new ModelPartIndex(names, model instanceof MqoModel);
    }

    /** For tests and callers that only have the names: {@code mqo} selects the span semantics. */
    public static ModelPartIndex of(List<String> groupNames, boolean mqo) {
        return new ModelPartIndex(groupNames, mqo);
    }

    /** The reference {@code W_MetasequoiaObject.renderPart} walk for one {@code $}-name. */
    private static int[] span(List<String> names, String[] lower, String part) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < lower.length; i++) {
            if (!part.equals(lower[i])) {
                continue;
            }
            out.add(i);
            i++;
            while (i < lower.length && !startsWithDollar(names.get(i))) {
                out.add(i);
                i++;
            }
            // i now sits on the terminating $-group (or past the end); the for-loop's i++ skips it, as in the reference.
        }
        return toArray(out);
    }

    private static boolean startsWithDollar(String s) {
        return s != null && !s.isEmpty() && s.charAt(0) == '$';
    }

    private static int[] toArray(List<Integer> list) {
        int[] a = new int[list.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = list.get(i);
        }
        return a;
    }

    /** Group indices drawn for the part {@code name}, in draw order (see the class doc); empty when absent. */
    public int[] span(String name) {
        int[] r = this.spans.get(name.toLowerCase(Locale.ROOT));
        return r != null ? r : NONE;
    }

    /** Group indices named exactly {@code name} (case-insensitive); empty when absent. */
    public int[] exact(String name) {
        int[] r = this.exact.get(name.toLowerCase(Locale.ROOT));
        return r != null ? r : NONE;
    }

    /** True when some group is named {@code name} (case-insensitive) — the reference {@code containsPart}. */
    public boolean contains(String name) {
        return this.exact.containsKey(name.toLowerCase(Locale.ROOT));
    }

    /** The lower-cased name of group {@code i}. */
    public String lowerName(int i) {
        return this.lowerNames[i];
    }

    public int size() {
        return this.lowerNames.length;
    }
}
