package mcheli.agnostic.weapon;

import java.util.List;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.vehicle.MCH_VehicleInfo;

/**
 * Which fired weapon drives a spinning gatling barrel, so only that weapon arms the vehicle's firing latch. ZERO
 * Minecraft imports. The reference keeps a per-weapon {@code useWeaponStat} bit and spins only the fired weapon's
 * {@code rotBarrel} (firing a Hydra pod must not spin the A-10's GAU-8): the weapon name is matched, ignoring case,
 * against the names of the config's {@code rotBarrel} {@code AddPartWeapon}s ({@code AddPartRotWeapon},
 * {@code AddPartTurretRotWeapon}). An emplacement's barrel is a {@code type==1} {@code VPart} instead (at any depth of
 * its child tree), whose reference spin source is the first seat weapon, so any fire arms it there.
 *
 * <p>Moved from the port's own server code (AbstractMchVehicle {@code firesRotBarrel} and {@code hasType1Part},
 * baseline lines 5236-5267), with the slot turned into its weapon name.
 */
public final class RotBarrel {

    private RotBarrel() {}

    /**
     * True when firing {@code weaponName} on a vehicle with config {@code info} spins a rotating barrel; false for a
     * null config.
     */
    public static boolean firesRotBarrel(MCH_AircraftInfo info, String weaponName) {
        if (info == null) {
            return false;
        }
        if (info.partWeapon != null) {
            for (MCH_AircraftInfo.PartWeapon pw : info.partWeapon) {
                if (pw != null && pw.rotBarrel && pw.name != null) {
                    for (String nm : pw.name) {
                        if (nm != null && nm.equalsIgnoreCase(weaponName)) {
                            return true;
                        }
                    }
                }
            }
        }
        if (info instanceof MCH_VehicleInfo vi) {
            return hasType1Part(vi.partList);
        }
        return false;
    }

    /** True when {@code parts} (or any part's child tree) holds a {@code type==1} part; false for null. */
    public static boolean hasType1Part(List<MCH_VehicleInfo.VPart> parts) {
        if (parts != null) {
            for (MCH_VehicleInfo.VPart p : parts) {
                if (p != null && (p.type == 1 || hasType1Part(p.child))) {
                    return true;
                }
            }
        }
        return false;
    }
}
