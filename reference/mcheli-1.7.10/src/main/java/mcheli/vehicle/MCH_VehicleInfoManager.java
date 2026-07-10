package mcheli.vehicle;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import mcheli.MCH_BaseInfo;
import mcheli.aircraft.MCH_AircraftInfoManager;
import net.minecraft.item.Item;

public class MCH_VehicleInfoManager extends MCH_AircraftInfoManager {
   private static MCH_VehicleInfoManager instance = new MCH_VehicleInfoManager();
   public static HashMap<String, MCH_VehicleInfo> map = new LinkedHashMap<>();

   private MCH_VehicleInfoManager() {
   }

   public static MCH_VehicleInfo get(String name) {
      return map.get(name);
   }

   public static MCH_VehicleInfoManager getInstance() {
      return instance;
   }

   @Override
   public MCH_BaseInfo newInfo(String name) {
      return new MCH_VehicleInfo(name);
   }

   @Override
   public Map getMap() {
      return map;
   }

   public static MCH_VehicleInfo getFromItem(Item item) {
      return getInstance().getAcInfoFromItem(item);
   }

   public MCH_VehicleInfo getAcInfoFromItem(Item item) {
      if (item == null) {
         return null;
      }

      for (MCH_VehicleInfo info : map.values()) {
         if (info.item == item) {
            return info;
         }
      }

      return null;
   }
}
