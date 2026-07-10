package mcheli.agnostic.weapon;

import mcheli.agnostic.spi.ModelHandle;

/** Agnostic port of {@code mcheli.weapon.MCH_BulletModel} ({@code IModelCustom} -&gt; {@link ModelHandle}). */
public class MCH_BulletModel {
   public final String name;
   public final ModelHandle model;

   public MCH_BulletModel(String n, ModelHandle m) {
      this.name = n;
      this.model = m;
   }
}
