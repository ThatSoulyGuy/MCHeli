package mcheli.agnostic.info;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Port of the reference {@code MCH_SoundsJson.update} (MCH_MOD.PreInit:144): how a folder of {@code .ogg} files
 * becomes sound events. The reference rewrote the mod's {@code sounds.json} at every start; the port applies the same
 * rules to each content pack's {@code sounds/} folder (the dependent {@code MchContentPacks} scans it, the dependent
 * {@code MchPackResources} serves the resulting JSON from memory).
 *
 * <p>The rules, all from the reference:
 * <ul>
 *   <li>a file counts if its lower-cased name is at least 5 characters and ends in {@code .ogg} (:18-21);</li>
 *   <li>the sound name is the LOWER-CASED base name (:33-35), so {@code Engine.OGG} is the sound {@code engine};</li>
 *   <li>the event key is that name with ONE trailing digit removed (:36-40), and every file with the same key is a
 *       variant of that one event (:42-46): {@code engine1.ogg} + {@code engine2.ogg} make the event {@code engine},
 *       which picks one of the two each time it plays. There is no event {@code engine1}.</li>
 * </ul>
 *
 * <p>One rule is the port's own: 1.21 resource ids only allow {@code [a-z0-9_.-]} in a path segment, so a name or key
 * outside that set ({@code bad sound.ogg}) is rejected rather than handed to the registry, which would throw out of
 * mod construction. The reference wrote such names into {@code sounds.json} and the 1.7.10 sound manager logged them as
 * missing.
 */
public final class MCH_SoundsJson {
    private MCH_SoundsJson() {}

    /** The sound name of a file: the lower-cased base name, or {@code null} if it is not an {@code .ogg} file. */
    public static String soundName(String fileName) {
        if (fileName == null) {
            return null;
        }
        String s = fileName.toLowerCase(Locale.ROOT);
        if (s.length() < 5 || !s.endsWith(".ogg")) {
            return null;
        }
        return s.substring(0, s.length() - 4);
    }

    /** The event a sound name is filed under: the name with one trailing digit removed (reference :36-40). */
    public static String eventKey(String soundName) {
        if (soundName == null || soundName.isEmpty()) {
            return soundName;
        }
        char c = soundName.charAt(soundName.length() - 1);
        return c >= '0' && c <= '9' ? soundName.substring(0, soundName.length() - 1) : soundName;
    }

    /** True if {@code name} can be both a sound-event id path and a file name: non-empty and only {@code [a-z0-9_.-]}. */
    public static boolean isLegalName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /**
     * Group sound names into events, keeping first-seen order (the reference's {@code LinkedHashMap}). A name that is
     * illegal, or whose key is (a bare digit such as {@code 1.ogg} has an empty key), is passed to {@code rejected}
     * and left out. Duplicate names are kept once.
     *
     * @param soundNames sound names as returned by {@link #soundName}
     * @param rejected   receives each rejected name, for a warning; may be null
     * @return event key -> its variant sound names, in order
     */
    public static Map<String, List<String>> group(Iterable<String> soundNames, Consumer<String> rejected) {
        Map<String, List<String>> events = new LinkedHashMap<>();
        for (String name : soundNames) {
            String key = eventKey(name);
            if (!isLegalName(name) || !isLegalName(key)) {
                if (rejected != null) {
                    rejected.accept(name);
                }
                continue;
            }
            List<String> variants = events.computeIfAbsent(key, k -> new ArrayList<>());
            if (!variants.contains(name)) {
                variants.add(name);
            }
        }
        return events;
    }

    /**
     * The {@code sounds.json} text for a set of events, each variant namespaced ({@code "ns:name"}, which the 1.21
     * sound manager resolves to {@code ns:sounds/name.ogg}). The reference also wrote {@code "category": "master"};
     * 1.21 dropped that field (the category is chosen where the sound is played), so it is not written.
     */
    public static String toJson(Map<String, List<String>> events, String namespace) {
        StringBuilder sb = new StringBuilder("{\n");
        int n = 0;
        for (Map.Entry<String, List<String>> e : events.entrySet()) {
            if (n++ > 0) {
                sb.append(",\n");
            }
            sb.append("  \"").append(e.getKey()).append("\": { \"sounds\": [");
            for (int i = 0; i < e.getValue().size(); i++) {
                sb.append(i > 0 ? ", " : "").append('"').append(namespace).append(':').append(e.getValue().get(i))
                    .append('"');
            }
            sb.append("] }");
        }
        return sb.append("\n}\n").toString();
    }
}
