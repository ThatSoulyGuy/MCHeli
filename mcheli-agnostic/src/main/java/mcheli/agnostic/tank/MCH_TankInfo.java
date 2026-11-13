package mcheli.agnostic.tank;

import java.util.ArrayList;
import java.util.List;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.config.MchSettings;
import mcheli.agnostic.value.Vec3d;

public class MCH_TankInfo extends MCH_AircraftInfo {
   public int weightType = 0;
   public float weightedCenterZ = 0.0F;

   public MCH_TankInfo(String name) {
      super(name);
   }

   @Override
   public List<MCH_AircraftInfo.Wheel> getDefaultWheelList() {
      List<MCH_AircraftInfo.Wheel> list = new ArrayList<>();
      list.add(new MCH_AircraftInfo.Wheel(new Vec3d(1.5, -0.24, 2.0)));
      list.add(new MCH_AircraftInfo.Wheel(new Vec3d(1.5, -0.24, -2.0)));
      return list;
   }

   @Override
   public float getDefaultSoundRange() {
      return 50.0F;
   }

   @Override
   public float getDefaultRotorSpeed() {
      return 47.94F;
   }

   protected float getDefaultStepHeight() {
      return 0.6F;
   }

   @Override
   public float getMaxSpeed() {
      return 1.8F;
   }

   @Override
   public int getDefaultMaxZoom() {
      return 8;
   }

   @Override
   public String getDefaultHudName(int seatId) {
      if (seatId <= 0) {
         return "tank";
      } else {
         return seatId == 1 ? "tank" : "gunner";
      }
   }

   @Override
   public boolean isValidData() throws Exception {
      // Reference MCH_TankInfo:68: the server-wide AllTankSpeed multiplier, baked in at load.
      this.speed = (float)(this.speed * MchSettings.current().allTankSpeed());
      return super.isValidData();
   }

   @Override
   public void loadItemData(String item, String data) {
      super.loadItemData(item, data);
      if (item.equalsIgnoreCase("WeightType")) {
         data = data.toLowerCase(java.util.Locale.ROOT);
         this.weightType = data.equals("tank") ? 2 : (data.equals("car") ? 1 : 0);
      } else if (item.equalsIgnoreCase("WeightedCenterZ")) {
         this.weightedCenterZ = this.toFloat(data, -1000.0F, 1000.0F);
      }
   }

   @Override
   public String getDirectoryName() {
      return "tanks";
   }

   @Override
   public String getKindName() {
      return "tank";
   }

   @Override
   public void preReload() {
      super.preReload();
   }
}
