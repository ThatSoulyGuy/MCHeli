package mcheli;

import java.util.HashMap;
import net.minecraft.entity.Entity;

public class MCH_DamageFactor {
   private HashMap<Class, Float> map = new HashMap<>();

   public void clear() {
      this.map.clear();
   }

   public void add(Class c, float value) {
      this.map.put(c, value);
   }

   public float getDamageFactor(Class c) {
      return this.map.containsKey(c) ? this.map.get(c) : 1.0F;
   }

   public float getDamageFactor(Entity e) {
      return e != null ? this.getDamageFactor(e.getClass()) : 1.0F;
   }
}
