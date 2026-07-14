package mcheli.agnostic.hud;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
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
        return name == null ? null : INSTANCE.map.get(name.toLowerCase());
    }

    /** Load every {@code hud/*.txt} through the resource source. */
    public void load(ResourceSource res, Logger log) {
        this.map.clear();
        List<String> files = res.list("hud");
        for (String file : files) {
            if (!file.toLowerCase().endsWith(".txt")) {
                continue;
            }
            String name = file.substring(0, file.length() - 4).toLowerCase();
            MCH_Hud hud = new MCH_Hud(name);
            try (Reader raw = res.openUtf8("hud/" + file)) {
                if (raw == null) {
                    continue;
                }
                BufferedReader in = new BufferedReader(raw);
                String line;
                while ((line = in.readLine()) != null) {
                    hud.parseLine(line);
                }
                this.map.put(name, hud);
            } catch (Exception e) {
                log.warn("MCHeli: failed to parse hud/{} : {}", file, e.toString());
            }
        }
        log.info("Read %d huds", this.map.size()); // printf-style like the other managers (the SPI logger is %-format)
    }
}
