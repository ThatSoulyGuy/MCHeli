package mcheli.agnostic.vehicle;

import java.util.LinkedHashMap;
import java.util.Map;
import mcheli.agnostic.info.MCH_BaseInfo;
import mcheli.agnostic.info.MCH_InfoManagerBase;

/** Loads + holds parsed vehicle definitions ({@code vehicles/*.txt} → {@link MCH_VehicleInfo}). Thin manager; the
 *  base does the DSL parsing. */
public final class MCH_VehicleInfoManager extends MCH_InfoManagerBase {

    private static final MCH_VehicleInfoManager INSTANCE = new MCH_VehicleInfoManager();
    private final Map<String, MCH_BaseInfo> map = new LinkedHashMap<>();

    private MCH_VehicleInfoManager() {}

    public static MCH_VehicleInfoManager getInstance() {
        return INSTANCE;
    }

    public static MCH_VehicleInfo get(String name) {
        MCH_BaseInfo info = INSTANCE.map.get(name);
        return info instanceof MCH_VehicleInfo ? (MCH_VehicleInfo) info : null;
    }

    @Override
    public MCH_BaseInfo newInfo(String name) {
        return new MCH_VehicleInfo(name);
    }

    @Override
    public Map getMap() {
        return this.map;
    }
}
