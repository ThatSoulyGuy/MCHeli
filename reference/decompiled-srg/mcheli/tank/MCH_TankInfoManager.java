/*
 * Decompiled with CFR 0.152.
 */
package mcheli.tank;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import mcheli.MCH_BaseInfo;
import mcheli.aircraft.MCH_AircraftInfoManager;
import mcheli.tank.MCH_TankInfo;
import net.minecraft.item.Item;

/*
 * Exception performing whole class analysis ignored.
 */
public class MCH_TankInfoManager
extends MCH_AircraftInfoManager {
    private static MCH_TankInfoManager instance = new MCH_TankInfoManager();
    public static HashMap<String, MCH_TankInfo> map = new LinkedHashMap();

    private MCH_TankInfoManager() {
    }

    public static MCH_TankInfo get(String name) {
        return (MCH_TankInfo)map.get(name);
    }

    public static MCH_TankInfoManager getInstance() {
        return instance;
    }

    public MCH_BaseInfo newInfo(String name) {
        return new MCH_TankInfo(name);
    }

    public Map getMap() {
        return map;
    }

    public static MCH_TankInfo getFromItem(Item item) {
        return MCH_TankInfoManager.getInstance().getAcInfoFromItem(item);
    }

    public MCH_TankInfo getAcInfoFromItem(Item item) {
        if (item == null) {
            return null;
        }
        for (MCH_TankInfo info : map.values()) {
            if (info.item != item) continue;
            return info;
        }
        return null;
    }
}

