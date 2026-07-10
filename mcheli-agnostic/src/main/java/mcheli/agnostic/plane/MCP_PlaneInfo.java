package mcheli.agnostic.plane;

import java.util.ArrayList;
import java.util.List;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.spi.ItemHandle;

public class MCP_PlaneInfo extends MCH_AircraftInfo {
   public ItemHandle item = null;
   public List<MCH_AircraftInfo.DrawnPart> nozzles = new ArrayList<>();
   public List<MCP_PlaneInfo.Rotor> rotorList = new ArrayList<>();
   public List<MCP_PlaneInfo.Wing> wingList = new ArrayList<>();
   public boolean isEnableVtol = false;
   public boolean isDefaultVtol;
   public float vtolYaw = 0.3F;
   public float vtolPitch = 0.2F;
   public boolean isEnableAutoPilot = false;
   public boolean isVariableSweepWing = false;
   public float sweepWingSpeed = this.speed;

   @Override
   public ItemHandle getItem() {
      return this.item;
   }

   public MCP_PlaneInfo(String name) {
      super(name);
   }

   @Override
   public float getDefaultRotorSpeed() {
      return 47.94F;
   }

   private float getDefaultStepHeight() {
      return 0.6F;
   }

   public boolean haveNozzle() {
      return this.nozzles.size() > 0;
   }

   public boolean haveRotor() {
      return this.rotorList.size() > 0;
   }

   public boolean haveWing() {
      return this.wingList.size() > 0;
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
         return "plane";
      } else {
         return seatId == 1 ? "plane" : "gunner";
      }
   }

   @Override
   public boolean isValidData() throws Exception {
      if (this.haveHatch() && this.haveWing()) {
         this.wingList.clear();
         this.hatchList.clear();
      }

      this.speed = (float)(this.speed * this.getSpeedMultiplier());
      this.sweepWingSpeed = (float)(this.sweepWingSpeed * this.getSpeedMultiplier());
      return super.isValidData();
   }

   protected double getSpeedMultiplier() {
      return 1.0;
   }

   @Override
   public void loadItemData(String item, String data) {
      super.loadItemData(item, data);
      if (item.compareTo("addpartrotor") == 0) {
         String[] s = data.split("\\s*,\\s*");
         if (s.length >= 6) {
            float m = s.length >= 7 ? this.toFloat(s[6], -180.0F, 180.0F) / 90.0F : 1.0F;
            MCP_PlaneInfo.Rotor e = new MCP_PlaneInfo.Rotor(
               this.toFloat(s[0]),
               this.toFloat(s[1]),
               this.toFloat(s[2]),
               this.toFloat(s[3]),
               this.toFloat(s[4]),
               this.toFloat(s[5]),
               m,
               "rotor" + this.rotorList.size()
            );
            this.rotorList.add(e);
         }
      } else if (item.compareTo("addblade") == 0) {
         int idx = this.rotorList.size() - 1;
         MCP_PlaneInfo.Rotor r = this.rotorList.size() > 0 ? this.rotorList.get(idx) : null;
         if (r != null) {
            String[] s = data.split("\\s*,\\s*");
            if (s.length == 8) {
               MCP_PlaneInfo.Blade b = new MCP_PlaneInfo.Blade(
                  this.toInt(s[0]),
                  this.toInt(s[1]),
                  this.toFloat(s[2]),
                  this.toFloat(s[3]),
                  this.toFloat(s[4]),
                  this.toFloat(s[5]),
                  this.toFloat(s[6]),
                  this.toFloat(s[7]),
                  "blade" + idx
               );
               r.blades.add(b);
            }
         }
      } else if (item.compareTo("addpartwing") == 0) {
         String[] s = data.split("\\s*,\\s*");
         if (s.length == 7) {
            MCP_PlaneInfo.Wing n = new MCP_PlaneInfo.Wing(
               this.toFloat(s[0]),
               this.toFloat(s[1]),
               this.toFloat(s[2]),
               this.toFloat(s[3]),
               this.toFloat(s[4]),
               this.toFloat(s[5]),
               this.toFloat(s[6]),
               "wing" + this.wingList.size()
            );
            this.wingList.add(n);
         }
      } else if (item.equalsIgnoreCase("AddPartPylon")) {
         String[] s = data.split("\\s*,\\s*");
         if (s.length >= 7 && this.wingList.size() > 0) {
            MCP_PlaneInfo.Wing w = this.wingList.get(this.wingList.size() - 1);
            if (w.pylonList == null) {
               w.pylonList = new ArrayList<>();
            }

            MCP_PlaneInfo.Pylon n = new MCP_PlaneInfo.Pylon(
               this.toFloat(s[0]),
               this.toFloat(s[1]),
               this.toFloat(s[2]),
               this.toFloat(s[3]),
               this.toFloat(s[4]),
               this.toFloat(s[5]),
               this.toFloat(s[6]),
               w.modelName + "_pylon" + w.pylonList.size()
            );
            w.pylonList.add(n);
         }
      } else if (item.compareTo("addpartnozzle") == 0) {
         String[] s = data.split("\\s*,\\s*");
         if (s.length == 6) {
            MCH_AircraftInfo.DrawnPart n = new MCH_AircraftInfo.DrawnPart(
               this.toFloat(s[0]),
               this.toFloat(s[1]),
               this.toFloat(s[2]),
               this.toFloat(s[3]),
               this.toFloat(s[4]),
               this.toFloat(s[5]),
               "nozzle" + this.nozzles.size()
            );
            this.nozzles.add(n);
         }
      } else if (item.compareTo("variablesweepwing") == 0) {
         this.isVariableSweepWing = this.toBool(data);
      } else if (item.compareTo("sweepwingspeed") == 0) {
         this.sweepWingSpeed = this.toFloat(data, 0.0F, 5.0F);
      } else if (item.compareTo("enablevtol") == 0) {
         this.isEnableVtol = this.toBool(data);
      } else if (item.compareTo("defaultvtol") == 0) {
         this.isDefaultVtol = this.toBool(data);
      } else if (item.compareTo("vtolyaw") == 0) {
         this.vtolYaw = this.toFloat(data, 0.0F, 1.0F);
      } else if (item.compareTo("vtolpitch") == 0) {
         this.vtolPitch = this.toFloat(data, 0.01F, 1.0F);
      } else if (item.compareTo("enableautopilot") == 0) {
         this.isEnableAutoPilot = this.toBool(data);
      }
   }

   @Override
   public String getDirectoryName() {
      return "planes";
   }

   @Override
   public String getKindName() {
      return "plane";
   }

   @Override
   public void preReload() {
      super.preReload();
      this.nozzles.clear();
      this.rotorList.clear();
      this.wingList.clear();
   }

   @Override
   public void postReload() {
      this.registerModels();
   }

   protected void registerModels() {
   }

   public static class Blade extends MCH_AircraftInfo.DrawnPart {
      public final int numBlade;
      public final int rotBlade;

      public Blade(int num, int r, float px, float py, float pz, float rx, float ry, float rz, String name) {
         super(px, py, pz, rx, ry, rz, name);
         this.numBlade = num;
         this.rotBlade = r;
      }
   }

   public static class Pylon extends MCH_AircraftInfo.DrawnPart {
      public final float maxRotFactor;
      public final float maxRot;

      public Pylon(float px, float py, float pz, float rx, float ry, float rz, float mr, String name) {
         super(px, py, pz, rx, ry, rz, name);
         this.maxRot = mr;
         this.maxRotFactor = this.maxRot / 90.0F;
      }
   }

   public static class Rotor extends MCH_AircraftInfo.DrawnPart {
      public List<MCP_PlaneInfo.Blade> blades = new ArrayList<>();
      public final float maxRotFactor;

      public Rotor(float x, float y, float z, float rx, float ry, float rz, float mrf, String model) {
         super(x, y, z, rx, ry, rz, model);
         this.maxRotFactor = mrf;
      }
   }

   public static class Wing extends MCH_AircraftInfo.DrawnPart {
      public final float maxRotFactor;
      public final float maxRot;
      public List<MCP_PlaneInfo.Pylon> pylonList;

      public Wing(float px, float py, float pz, float rx, float ry, float rz, float mr, String name) {
         super(px, py, pz, rx, ry, rz, name);
         this.maxRot = mr;
         this.maxRotFactor = this.maxRot / 90.0F;
         this.pylonList = null;
      }
   }
}
