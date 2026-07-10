/*
 * Decompiled with CFR 0.152.
 */
package mcheli.helicopter;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import mcheli.MCH_BaseInfo;
import mcheli.aircraft.MCH_AircraftInfoManager;
import mcheli.helicopter.MCH_HeliInfo;
import net.minecraft.item.Item;

/*
 * Exception performing whole class analysis ignored.
 */
public class MCH_HeliInfoManager
extends MCH_AircraftInfoManager {
    private static final MCH_HeliInfoManager instance = new MCH_HeliInfoManager();
    public static final HashMap<String, MCH_HeliInfo> map = new LinkedHashMap();

    private MCH_HeliInfoManager() {
    }

    public static MCH_HeliInfoManager getInstance() {
        return instance;
    }

    public static MCH_HeliInfo get(String name) {
        return (MCH_HeliInfo)map.get(name);
    }

    public MCH_BaseInfo newInfo(String name) {
        return new MCH_HeliInfo(name);
    }

    public Map getMap() {
        return map;
    }

    public static MCH_HeliInfo getFromItem(Item item) {
        return MCH_HeliInfoManager.getInstance().getAcInfoFromItem(item);
    }

    public MCH_HeliInfo getAcInfoFromItem(Item item) {
        if (item == null) {
            return null;
        }
        for (MCH_HeliInfo info : map.values()) {
            if (info.item != item) continue;
            return info;
        }
        return null;
    }
}

