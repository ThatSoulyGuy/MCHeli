package mcheli.agnostic.plane;

import java.util.LinkedHashMap;
import java.util.Map;
import mcheli.agnostic.info.MCH_BaseInfo;
import mcheli.agnostic.info.MCH_InfoManagerBase;

/** Loads + holds parsed plane definitions ({@code planes/*.txt} → {@link MCP_PlaneInfo}). Thin manager; the base
 *  does the DSL parsing. */
public final class MCP_PlaneInfoManager extends MCH_InfoManagerBase {

    private static final MCP_PlaneInfoManager INSTANCE = new MCP_PlaneInfoManager();
    private final Map<String, MCH_BaseInfo> map = new LinkedHashMap<>();

    private MCP_PlaneInfoManager() {}

    public static MCP_PlaneInfoManager getInstance() {
        return INSTANCE;
    }

    public static MCP_PlaneInfo get(String name) {
        MCH_BaseInfo info = INSTANCE.map.get(name);
        return info instanceof MCP_PlaneInfo ? (MCP_PlaneInfo) info : null;
    }

    @Override
    public MCH_BaseInfo newInfo(String name) {
        return new MCP_PlaneInfo(name);
    }

    @Override
    public Map getMap() {
        return this.map;
    }
}
