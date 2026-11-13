package mcheli.agnostic.vehicle;

/**
 * The hull armor formula of {@code MCH_EntityAircraft.attackEntityFrom} (:884-908), shared by every vehicle category.
 * Pure, so the order of its steps (which is load-bearing) is unit-tested without a world.
 *
 * <p>The per-part factor is applied asymmetrically on purpose: an armor plate ({@code partFactor <= 1}) multiplies
 * BEFORE the config {@code ArmorDamageFactor} and {@code ArmorMinDamage}, so a thin hit on a plate can be absorbed
 * entirely; a weak point ({@code partFactor > 1}) multiplies AFTER the subtraction, so it amplifies only damage that
 * already got through and can never be zeroed by it.
 */
public final class VehicleArmor {
    private VehicleArmor() {}

    /**
     * Damage left after the armor, or a value {@code <= 0} when the armor absorbs the hit (the reference then returns
     * {@code false} from {@code attackEntityFrom} without touching the vehicle).
     *
     * @param damage            the incoming damage
     * @param partFactor        the struck part box's factor ({@code lastBBDamageFactor}), 1.0 for the plain hull
     * @param armorMaxDamage    config {@code ArmorMaxDamage}: the cap on one hit
     * @param armorDamageFactor config {@code ArmorDamageFactor}
     * @param armorMinDamage    config {@code ArmorMinDamage}: subtracted from every hit
     */
    public static float apply(float damage, float partFactor, float armorMaxDamage, float armorDamageFactor,
                              float armorMinDamage) {
        if (damage > armorMaxDamage) {
            damage = armorMaxDamage;                // (a) cap
        }
        if (partFactor <= 1.0F) {
            damage *= partFactor;                   // (b) armor plate, BEFORE the subtraction
        }
        damage *= armorDamageFactor;                // (c)
        damage -= armorMinDamage;                   // (d)
        if (damage <= 0.0F) {
            return damage;                          // (e) fully absorbed
        }
        if (partFactor > 1.0F) {
            damage *= partFactor;                   // (f) weak point, AFTER the subtraction
        }
        return damage;
    }
}
