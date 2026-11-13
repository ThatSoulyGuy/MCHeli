package mcheli.agnostic.info;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@link MCH_SoundsJson}: the reference {@code MCH_SoundsJson.update} rules, applied to a content pack's .ogg files. */
class SoundsJsonTest {

    @Test
    void soundNameIsTheLowerCasedBaseNameOfAnOggFile() {
        assertEquals("engine1", MCH_SoundsJson.soundName("Engine1.OGG"));
        assertEquals("a-10_snd", MCH_SoundsJson.soundName("a-10_snd.ogg"));
        assertNull(MCH_SoundsJson.soundName("engine.wav"));
        assertNull(MCH_SoundsJson.soundName(".ogg"), "the reference needs at least 5 characters");
        assertNull(MCH_SoundsJson.soundName(null));
    }

    @Test
    void eventKeyStripsExactlyOneTrailingDigit() {
        assertEquals("engine", MCH_SoundsJson.eventKey("engine1"));
        assertEquals("engine1", MCH_SoundsJson.eventKey("engine12"));
        assertEquals("gun_h1_snd", MCH_SoundsJson.eventKey("gun_h1_snd"));
        assertEquals("missile_", MCH_SoundsJson.eventKey("missile_1")); // faithful, even where it reads oddly
        assertEquals("", MCH_SoundsJson.eventKey("7"));
    }

    @Test
    void numberedFilesBecomeVariantsOfOneEvent() {
        Map<String, List<String>> events = MCH_SoundsJson.group(List.of("engine1", "horn", "engine2"), null);
        assertEquals(List.of("engine", "horn"), new ArrayList<>(events.keySet()), "first-seen order");
        assertEquals(List.of("engine1", "engine2"), events.get("engine"));
        assertFalse(events.containsKey("engine1"), "the reference registers the key, never the numbered file");
    }

    @Test
    void illegalNamesAreRejectedNotRegistered() {
        List<String> rejected = new ArrayList<>();
        Map<String, List<String>> events =
            MCH_SoundsJson.group(List.of("bad sound", "1", "ok", "ok", "été"), rejected::add);
        assertEquals(List.of("ok"), new ArrayList<>(events.keySet()));
        assertEquals(List.of("ok"), events.get("ok"), "a duplicate name is kept once");
        assertEquals(List.of("bad sound", "1", "été"), rejected);
        assertTrue(MCH_SoundsJson.isLegalName("a-10_snd.v2"));
        assertFalse(MCH_SoundsJson.isLegalName("sub/dir"), "a sound is a flat file name");
        assertFalse(MCH_SoundsJson.isLegalName(""));
    }

    @Test
    void jsonListsEveryVariantNamespaced() {
        Map<String, List<String>> events = MCH_SoundsJson.group(List.of("engine1", "engine2", "horn"), null);
        assertEquals("{\n"
                + "  \"engine\": { \"sounds\": [\"mcheli:engine1\", \"mcheli:engine2\"] },\n"
                + "  \"horn\": { \"sounds\": [\"mcheli:horn\"] }\n"
                + "}\n",
            MCH_SoundsJson.toJson(events, "mcheli"));
        assertEquals("{\n\n}\n", MCH_SoundsJson.toJson(Map.of(), "mcheli"));
    }
}
