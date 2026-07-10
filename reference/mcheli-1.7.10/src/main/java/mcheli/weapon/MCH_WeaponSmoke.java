package mcheli.weapon;

import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class MCH_WeaponSmoke extends MCH_WeaponBase {
   public MCH_WeaponSmoke(World w, Vec3 v, float yaw, float pitch, String nm, MCH_WeaponInfo wi) {
      super(w, v, yaw, pitch, nm, wi);
      this.power = 0;
   }

   @Override
   public boolean shot(MCH_WeaponParam prm) {
      return false;
   }
}
