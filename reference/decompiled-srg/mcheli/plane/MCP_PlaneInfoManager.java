/*
 * Decompiled with CFR 0.152.
 */
package mcheli.plane;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import mcheli.MCH_BaseInfo;
import mcheli.aircraft.MCH_AircraftInfoManager;
import mcheli.plane.MCP_PlaneInfo;
import net.minecraft.item.Item;

/*
 * Exception performing whole class analysis ignored.
 */
public class MCP_PlaneInfoManager
extends MCH_AircraftInfoManager {
    private static MCP_PlaneInfoManager instance = new MCP_PlaneInfoManager();
    public static HashMap<String, MCP_PlaneInfo> map = new LinkedHashMap();

    private MCP_PlaneInfoManager() {
    }

    public static MCP_PlaneInfo get(String name) {
        return (MCP_PlaneInfo)map.get(name);
    }

    public static MCP_PlaneInfoManager getInstance() {
        return instance;
    }

    public MCH_BaseInfo newInfo(String name) {
        return new MCP_PlaneInfo(name);
    }

    public Map getMap() {
        return map;
    }

    public static MCP_PlaneInfo getFromItem(Item item) {
        return MCP_PlaneInfoManager.getInstance().getAcInfoFromItem(item);
    }

    public MCP_PlaneInfo getAcInfoFromItem(Item item) {
        if (item == null) {
            return null;
        }
        for (MCP_PlaneInfo info : map.values()) {
            if (info.item != item) continue;
            return info;
        }
        return null;
    }
}

