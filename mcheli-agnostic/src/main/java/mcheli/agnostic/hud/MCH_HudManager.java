package mcheli.agnostic.hud;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import mcheli.agnostic.spi.Logger;
import mcheli.agnostic.spi.ResourceSource;

/**
 * Loads + holds the parsed HUD definitions ({@code hud/*.txt} → {@link MCH_Hud}). Unlike the vehicle/weapon managers
 * this does NOT extend {@code MCH_InfoManagerBase}: HUD configs use bare command lines ({@code EndIf}, {@code Exit})
 * with no {@code =}, which the {@code key = value} base parser skips — so the HUD gets its own line loader (as the
 * reference {@code MCH_HudManager} did). Vehicles reference these by name via {@code MCH_AircraftInfo.hudList}.
 */
public final class MCH_HudManager {

    private static final MCH_HudManager INSTANCE = new MCH_HudManager();
    private final Map<String, MCH_Hud> map = new HashMap<>();

    private MCH_HudManager() {}

    public static MCH_HudManager getInstance() {
        return INSTANCE;
    }

    /** The parsed HUD for {@code name} (e.g. "heli", "common_pilot"), or null if not loaded. */
    public static MCH_Hud get(String name) {
        return name == null ? null : INSTANCE.map.get(name.toLowerCase(Locale.ROOT));
    }

    /** Load every {@code hud/*.txt} through the resource source. A malformed expression costs only its own item (see
     *  {@link MCH_Hud}); an If/EndIf pairing error rejects that one file with a warning. The reference threw a
     *  RuntimeException at that point, which stopped the game. */
    public void load(ResourceSource res, Logger log) {
        this.map.clear();
        List<String> files = res.list("hud");
        for (String file : files) {
            String lower = file.toLowerCase(Locale.ROOT);
            if (lower.length() < 5 || !lower.endsWith(".txt")) {
                continue;
            }
            String name = lower.substring(0, lower.length() - 4);
            if (this.map.containsKey(name)) {
                continue; // reference: the first file of a name wins
            }
            MCH_Hud hud = new MCH_Hud(name, "hud/" + file, log);
            try (Reader raw = res.openUtf8("hud/" + file)) {
                if (raw == null) {
                    continue;
                }
                BufferedReader in = new BufferedReader(raw);
                String line;
                while ((line = in.readLine()) != null) {
                    hud.parseLine(line);
                }
                hud.checkData();
                this.map.put(name, hud);
            } catch (Exception e) {
                log.warn("### HUD file error! hud/%s : %s", file, e.getMessage() != null ? e.getMessage() : e.toString());
            }
        }
        log.info("Read %d huds", this.map.size()); // printf-style like the other managers (the SPI logger is %-format)
    }
}
