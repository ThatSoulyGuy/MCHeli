package mcheli.agnostic.helicopter;

import java.util.ArrayList;
import java.util.List;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.spi.ItemHandle;

public class MCH_HeliInfo extends MCH_AircraftInfo {
   public ItemHandle item = null;
   public boolean isEnableFoldBlade;
   public List<MCH_HeliInfo.Rotor> rotorList;

   public MCH_HeliInfo(String name) {
      super(name);
      this.isEnableGunnerMode = false;
      this.isEnableFoldBlade = false;
      this.rotorList = new ArrayList<>();
      this.minRotationPitch = -20.0F;
      this.maxRotationPitch = 20.0F;
   }

   @Override
   public boolean isValidData() throws Exception {
      this.speed = (float)(this.speed * this.getSpeedMultiplier());
      return super.isValidData();
   }

   protected double getSpeedMultiplier() {
      return 1.0;
   }

   @Override
   public float getDefaultSoundRange() {
      return 80.0F;
   }

   @Override
   public float getDefaultRotorSpeed() {
      return 79.99F;
   }

   @Override
   public int getDefaultMaxZoom() {
      return 8;
   }

   @Override
   public ItemHandle getItem() {
      return this.item;
   }

   @Override
   public String getDefaultHudName(int seatId) {
      if (seatId <= 0) {
         return "heli";
      } else {
         return seatId == 1 ? "heli_gnr" : "gunner";
      }
   }

   @Override
   public void loadItemData(String item, String data) {
      super.loadItemData(item, data);
      if (item.compareTo("enablefoldblade") == 0) {
         this.isEnableFoldBlade = this.toBool(data);
      } else if (item.compareTo("addrotor") == 0 || item.compareTo("addrotorold") == 0) {
         String[] s = data.split("\\s*,\\s*");
         if (s.length == 8 || s.length == 9) {
            boolean cfb = s.length == 9 && this.toBool(s[8]);
            MCH_HeliInfo.Rotor e = new MCH_HeliInfo.Rotor(
               this.toInt(s[0]),
               this.toInt(s[1]),
               this.toFloat(s[2]),
               this.toFloat(s[3]),
               this.toFloat(s[4]),
               this.toFloat(s[5]),
               this.toFloat(s[6]),
               this.toFloat(s[7]),
               "blade" + this.rotorList.size(),
               cfb,
               item.compareTo("addrotorold") == 0
            );
            this.rotorList.add(e);
         }
      }
   }

   @Override
   public String getDirectoryName() {
      return "helicopters";
   }

   @Override
   public String getKindName() {
      return "helicopter";
   }

   @Override
   public void preReload() {
      super.preReload();
      this.rotorList.clear();
   }

   @Override
   public void postReload() {
      this.registerModels();
   }

   protected void registerModels() {
   }

   public static class Rotor extends MCH_AircraftInfo.DrawnPart {
      public final int bladeNum;
      public final int bladeRot;
      public final boolean haveFoldFunc;
      public final boolean oldRenderMethod;

      public Rotor(int b, int br, float x, float y, float z, float rx, float ry, float rz, String model, boolean hf, boolean old) {
         super(x, y, z, rx, ry, rz, model);
         this.bladeNum = b;
         this.bladeRot = br;
         this.haveFoldFunc = hf;
         this.oldRenderMethod = old;
      }
   }
}
