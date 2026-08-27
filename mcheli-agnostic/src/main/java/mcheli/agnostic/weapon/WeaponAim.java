package mcheli.agnostic.weapon;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.math.MchMath;

/**
 * The ONE weapon-aim formula, ported from {@code MCH_EntityAircraft.updateWeaponsRotation} (reference :4485-4515).
 *
 * <p>The invariant it exists to enforce: <b>a weapon aims by the look of the player who OPERATES it</b>. A
 * {@code turret} mount contributes only {@code ty} — the frame its traverse limits are measured in — and {@code ty}
 * never <i>replaces</i> the operator's look. On an unlimited mount {@code ty} is added and subtracted around the
 * operator's yaw and cancels outright.
 *
 * <p>This is the fix for "secondary ridable weapons only shoot forward": the port had been firing every
 * {@code turret} mount along the pilot-sourced turret ring instead of the gunner's own look, so a gunner's shots
 * left along the driver's aim — or, with no driver ever aboard, along hull-forward. The ring and the aim are
 * deliberately <i>different quantities</i> in the reference; they coincide only when a weapon's operator happens to
 * be the pilot, which on a tank is true of the main gun and false of every secondary.
 *
 * <p>Both the server's shot ({@code AbstractMchVehicle.fireSelectedWeapon}) and the rendered barrel
 * ({@code MchModelEntityRenderer.renderWeaponParts}) call these methods with the same inputs, so the projectile
 * leaves exactly where the barrel points, by construction rather than by two formulas being kept in step by hand.
 *
 * <p><b>Deliberately omitted:</b> the reference's 15&deg;/tick ramp inside the finite-yaw branch (:4499-4503). It
 * needs per-weapon persistent state that would have to be stepped identically on the server and on every observing
 * client — but a passenger's yaw reaches a remote client quantized to 360/256&deg; and lerped over three ticks, so
 * two such integrators cannot agree during a slew. Stateless keeps barrel and tracer identical everywhere. Only a
 * FINITE-yaw turret mount is affected, which across the bundled fleet is m1a2's {@code m240_r} alone.
 */
public final class WeaponAim {

    private WeaponAim() {}

    /**
     * Reference :4495 — {@code ty}, the turret-ring datum.
     *
     * @param ringRelHull the turret-ring yaw RELATIVE TO THE HULL
     */
    public static float turretBase(MCH_AircraftInfo.Weapon w, float ringRelHull) {
        return w != null && w.turret ? ringRelHull : 0.0F;
    }

    /**
     * Reference :4494-4508 — the weapon's own {@code rotationYaw}, relative to the hull.
     *
     * @param relYaw the OPERATOR's look yaw minus the hull yaw
     * @param ty     from {@link #turretBase}
     */
    public static float rotationYaw(MCH_AircraftInfo.Weapon w, float relYaw, float ty) {
        if (w == null) {
            return MchMath.wrapAngleTo180(relYaw);
        }
        if ((int) w.minYaw == 0 && (int) w.maxYaw == 0) {
            // Reference :4494 wraps the WHOLE yaw block in this guard, so a no-traverse mount keeps the seed value
            // createWeapon gave it (:4110-4114) and never picks up ty. Dropping the guard would weld the pilot's
            // ring onto a mount the reference leaves fixed at its default bearing.
            return w.defaultYaw;
        }
        float ey = MchMath.wrapAngleTo180(relYaw - w.defaultYaw - ty);
        boolean finite = Math.abs((int) w.minYaw) < 360 && Math.abs((int) w.maxYaw) < 360;
        return finite ? MchMath.clamp(ey, w.minYaw, w.maxYaw) + w.defaultYaw + ty : ey + ty;
    }

    /** Reference :4509-4510 — {@code rotationPitch}: a clamp only. No slew, and no {@code ty}. */
    public static float rotationPitch(MCH_AircraftInfo.Weapon w, float relPitch) {
        float p = MchMath.wrapAngleTo180(relPitch);
        return w == null ? p : MchMath.clamp(p, w.minPitch, w.maxPitch);
    }

    /**
     * Reference {@code MCH_RenderAircraft.renderWeapon}:544 — {@code rotationYaw - defaultRotationYaw}. The gap from
     * {@link #rotationYaw} is intentional: the model part is authored already rotated by {@code defaultYaw}, and the
     * reference's {@code glRotatef(-def)}/{@code glRotatef(+def)} pair brackets only the PITCH axis.
     */
    public static float renderYaw(MCH_AircraftInfo.Weapon w, float relYaw, float ty) {
        return rotationYaw(w, relYaw, ty) - (w == null ? 0.0F : w.defaultYaw);
    }
}
