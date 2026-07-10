package mcheli.agnostic.weapon;

import java.util.HashMap;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.spi.Role;

/**
 * Agnostic port of {@code mcheli.MCH_DamageFactor}: per-target damage multipliers for a weapon.
 *
 * <p>COERCION: the reference keyed the map on {@code net.minecraft.entity} <em>Class</em> objects and looked
 * factors up by {@code entity.getClass()} (exact-class match). Here the key is {@link Role}, and lookups go
 * through {@link EntityRef#role()} — the mandated {@code instanceof}/{@code getClass} -&gt; role coercion.
 *
 * <p>BEHAVIOR NOTE: for the vehicle roles this is equivalent (MCH_EntityHeli/Plane/Tank/Vehicle are concrete,
 * so exact-class == role). For PLAYER it is NOT: the reference keyed {@code EntityPlayer.class}, which never
 * matched a runtime {@code EntityPlayerMP}/{@code EntityPlayerSP}, so a "player" damage factor was effectively
 * DEAD there. Keying by role makes it apply (honoring the config author's intent). Flagged as a deliberate,
 * documented divergence — revert to a no-op for PLAYER if strict bug-for-bug fidelity is required.
 */
public class MCH_DamageFactor {
   private HashMap<Role, Float> map = new HashMap<>();

   public void clear() {
      this.map.clear();
   }

   public void add(Role c, float value) {
      this.map.put(c, value);
   }

   public float getDamageFactor(Role c) {
      return this.map.containsKey(c) ? this.map.get(c) : 1.0F;
   }

   public float getDamageFactor(EntityRef e) {
      return e != null ? this.getDamageFactor(e.role()) : 1.0F;
   }
}
