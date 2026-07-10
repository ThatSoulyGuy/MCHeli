package mcheli.weapon;

import mcheli.MCH_Lib;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class MCH_WeaponMarkerRocket extends MCH_WeaponBase {
   public MCH_WeaponMarkerRocket(World w, Vec3 v, float yaw, float pitch, String nm, MCH_WeaponInfo wi) {
      super(w, v, yaw, pitch, nm, wi);
      this.acceleration = 3.0F;
      this.explosionPower = 0;
      this.power = 0;
      this.interval = 60;
      if (w.isRemote) {
         this.interval += 10;
      }
   }

   @Override
   public boolean shot(MCH_WeaponParam prm) {
      if (!this.worldObj.isRemote) {
         this.playSound(prm.entity);
         Vec3 v = MCH_Lib.RotVec3(0.0, 0.0, 1.0, -prm.rotYaw, -prm.rotPitch, -prm.rotRoll);
         MCH_EntityMarkerRocket e = new MCH_EntityMarkerRocket(
            this.worldObj, prm.posX, prm.posY, prm.posZ, v.xCoord, v.yCoord, v.zCoord, prm.rotYaw, prm.rotPitch, this.acceleration
         );
         e.setName(this.name);
         e.setParameterFromWeapon(this, prm.entity, prm.user);
         e.setMarkerStatus(1);
         this.worldObj.spawnEntityInWorld(e);
      } else {
         this.optionParameter1 = this.getCurrentMode();
      }

      return true;
   }
}
