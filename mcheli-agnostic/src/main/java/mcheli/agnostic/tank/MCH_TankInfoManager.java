package mcheli.agnostic.tank;

import java.util.LinkedHashMap;
import java.util.Map;
import mcheli.agnostic.info.MCH_BaseInfo;
import mcheli.agnostic.info.MCH_InfoManagerBase;

/**
 * Loads + holds the parsed tank definitions ({@code tanks/*.txt} → {@link MCH_TankInfo}). A thin subclass of
 * {@link MCH_InfoManagerBase}: the base's {@code load(ResourceSource, Logger, "tanks")} does the DSL parsing/
 * validation; this only supplies the concrete {@link MCH_TankInfo} factory and the name→info map. (The reference
 * {@code MCH_AircraftInfoManager}'s crafting-recipe list + {@code Item} lookup are MC-specific and deferred.)
 */
public final class MCH_TankInfoManager extends MCH_InfoManagerBase {

    private static final MCH_TankInfoManager INSTANCE = new MCH_TankInfoManager();
    private final Map<String, MCH_BaseInfo> map = new LinkedHashMap<>();

    private MCH_TankInfoManager() {}

    public static MCH_TankInfoManager getInstance() {
        return INSTANCE;
    }

    /** The parsed definition for {@code name} (e.g. "m1a2"), or null if not loaded. */
    public static MCH_TankInfo get(String name) {
        MCH_BaseInfo info = INSTANCE.map.get(name);
        return info instanceof MCH_TankInfo ? (MCH_TankInfo) info : null;
    }

    @Override
    public MCH_BaseInfo newInfo(String name) {
        return new MCH_TankInfo(name);
    }

    @Override
    public Map getMap() {
        return this.map;
    }
}
