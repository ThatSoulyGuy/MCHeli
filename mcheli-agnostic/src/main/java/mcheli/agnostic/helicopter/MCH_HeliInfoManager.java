package mcheli.agnostic.helicopter;

import java.util.LinkedHashMap;
import java.util.Map;
import mcheli.agnostic.info.MCH_BaseInfo;
import mcheli.agnostic.info.MCH_InfoManagerBase;

/** Loads + holds parsed helicopter definitions ({@code helicopters/*.txt} → {@link MCH_HeliInfo}). Thin manager;
 *  the base does the DSL parsing. */
public final class MCH_HeliInfoManager extends MCH_InfoManagerBase {

    private static final MCH_HeliInfoManager INSTANCE = new MCH_HeliInfoManager();
    private final Map<String, MCH_BaseInfo> map = new LinkedHashMap<>();

    private MCH_HeliInfoManager() {}

    public static MCH_HeliInfoManager getInstance() {
        return INSTANCE;
    }

    public static MCH_HeliInfo get(String name) {
        MCH_BaseInfo info = INSTANCE.map.get(name);
        return info instanceof MCH_HeliInfo ? (MCH_HeliInfo) info : null;
    }

    @Override
    public MCH_BaseInfo newInfo(String name) {
        return new MCH_HeliInfo(name);
    }

    @Override
    public Map getMap() {
        return this.map;
    }
}
