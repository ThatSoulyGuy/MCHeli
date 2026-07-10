package mcheli.weapon;

import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class MCH_WeaponCreator {
   public static MCH_WeaponBase createWeapon(World w, String weaponName, Vec3 v, float yaw, float pitch, MCH_IEntityLockChecker lockChecker, boolean onTurret) {
      MCH_WeaponInfo info = MCH_WeaponInfoManager.get(weaponName);
      if (info != null && info.type != "") {
         MCH_WeaponBase weapon = null;
         if (info.type.compareTo("machinegun1") == 0) {
            weapon = new MCH_WeaponMachineGun1(w, v, yaw, pitch, weaponName, info);
         }

         if (info.type.compareTo("machinegun2") == 0) {
            weapon = new MCH_WeaponMachineGun2(w, v, yaw, pitch, weaponName, info);
         }

         if (info.type.compareTo("tvmissile") == 0) {
            weapon = new MCH_WeaponTvMissile(w, v, yaw, pitch, weaponName, info);
         }

         if (info.type.compareTo("torpedo") == 0) {
            weapon = new MCH_WeaponTorpedo(w, v, yaw, pitch, weaponName, info);
         }

         if (info.type.compareTo("cas") == 0) {
            weapon = new MCH_WeaponCAS(w, v, yaw, pitch, weaponName, info);
         }

         if (info.type.compareTo("rocket") == 0) {
            weapon = new MCH_WeaponRocket(w, v, yaw, pitch, weaponName, info);
         }

         if (info.type.compareTo("asmissile") == 0) {
            weapon = new MCH_WeaponASMissile(w, v, yaw, pitch, weaponName, info);
         }

         if (info.type.compareTo("aamissile") == 0) {
            weapon = new MCH_WeaponAAMissile(w, v, yaw, pitch, weaponName, info);
         }

         if (info.type.compareTo("atmissile") == 0) {
            weapon = new MCH_WeaponATMissile(w, v, yaw, pitch, weaponName, info);
         }

         if (info.type.compareTo("bomb") == 0) {
            weapon = new MCH_WeaponBomb(w, v, yaw, pitch, weaponName, info);
         }

         if (info.type.compareTo("mkrocket") == 0) {
            weapon = new MCH_WeaponMarkerRocket(w, v, yaw, pitch, weaponName, info);
         }

         if (info.type.compareTo("dummy") == 0) {
            weapon = new MCH_WeaponDummy(w, v, yaw, pitch, weaponName, info);
         }

         if (info.type.compareTo("smoke") == 0) {
            weapon = new MCH_WeaponSmoke(w, v, yaw, pitch, weaponName, info);
         }

         if (info.type.compareTo("dispenser") == 0) {
            weapon = new MCH_WeaponDispenser(w, v, yaw, pitch, weaponName, info);
         }

         if (info.type.compareTo("targetingpod") == 0) {
            weapon = new MCH_WeaponTargetingPod(w, v, yaw, pitch, weaponName, info);
         }

         if (weapon != null) {
            weapon.displayName = info.displayName;
            weapon.power = info.power;
            weapon.acceleration = info.acceleration;
            weapon.explosionPower = info.explosion;
            weapon.explosionPowerInWater = info.explosionInWater;
            weapon.interval = info.delay;
            weapon.setLockCountMax(info.lockTime);
            weapon.setLockChecker(lockChecker);
            weapon.numMode = info.modeNum;
            weapon.piercing = info.piercing;
            weapon.heatCount = info.heatCount;
            weapon.onTurret = onTurret;
            if (info.maxHeatCount > 0 && weapon.heatCount < 2) {
               weapon.heatCount = 2;
            }

            if (w.isRemote) {
               if (weapon.interval < 4) {
                  weapon.interval++;
               } else if (weapon.interval < 7) {
                  weapon.interval += 2;
               } else if (weapon.interval < 10) {
                  weapon.interval += 3;
               } else if (weapon.interval < 20) {
                  weapon.interval += 6;
               } else {
                  weapon.interval += 10;
                  if (weapon.interval >= 40) {
                     weapon.interval = -weapon.interval;
                  }
               }

               weapon.heatCount++;
               weapon.cartridge = info.cartridge;
            }

            weapon.modifyCommonParameters();
         }

         return weapon;
      } else {
         return null;
      }
   }
}
