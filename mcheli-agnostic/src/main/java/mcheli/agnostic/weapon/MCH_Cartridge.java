package mcheli.agnostic.weapon;

import mcheli.agnostic.spi.ModelHandle;

/** Agnostic port of {@code mcheli.weapon.MCH_Cartridge} ({@code IModelCustom} -&gt; {@link ModelHandle}). */
public class MCH_Cartridge {
   public ModelHandle model;
   public final String name;
   public final float acceleration;
   public final float yaw;
   public final float pitch;
   public final float bound;
   public final float gravity;
   public final float scale;

   public MCH_Cartridge(String nm, float a, float y, float p, float b, float g, float s) {
      this.name = nm;
      this.acceleration = a;
      this.yaw = y;
      this.pitch = p;
      this.bound = b;
      this.gravity = g;
      this.scale = s;
   }
}
