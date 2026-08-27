package mcheli.agnostic.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.math.MchMath;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link WeaponAim} against {@code MCH_EntityAircraft.updateWeaponsRotation} (reference :4485-4515).
 *
 * <p>The headline case is {@link #turretRingCancelsOnUnlimitedMount()} — the regression for "secondary ridable
 * weapons only shoot forward". The port used to fire every {@code turret} mount along the pilot's turret ring, so a
 * gunner's shots left along the driver's aim (or hull-forward with no driver). The reference adds and subtracts the
 * ring around the operator's own yaw, so on an unlimited mount it cancels identically and the weapon aims exactly
 * where its operator looks — whatever the ring is doing.
 *
 * <p>Mount data is the real m1a2 loadout
 * ({@code neoforge-1.21.1/src/main/resources/assets/mcheli/tanks/m1a2.txt}:101, 108, 113).
 */
class WeaponAimTest {

    /** {@code AddTurretWeapon} column order: x,y,z, fixYaw, fixPitch, canUsePilot, seat(1-based), defYaw, minY, maxY, minP, maxP. */
    static MCH_AircraftInfo.Weapon weapon(boolean canUsePilot, int seatId, float defYaw,
                                          float minYaw, float maxYaw, float minPitch, float maxPitch, boolean turret) {
        return new MCH_AircraftInfo.Weapon(0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
            canUsePilot, seatId, defYaw, minYaw, maxYaw, minPitch, maxPitch, turret);
    }

    /** m1a2:108 — the RWS on seat 1. AddTurretWeapon, so turret=true; yaw -360..360, i.e. UNLIMITED. */
    static MCH_AircraftInfo.Weapon m2Rws() {
        return weapon(false, 1, 0.0F, -360.0F, 360.0F, -70.0F, 30.0F, true);
    }

    /** m1a2:113 — the coax M240 on seat 2. turret=true, yaw -220..30, i.e. FINITE. */
    static MCH_AircraftInfo.Weapon m240r() {
        return weapon(false, 2, 0.0F, -220.0F, 30.0F, -50.0F, 30.0F, true);
    }

    /** m1a2:101 — the main gun. turret=true, seat 0 (the PILOT operates it), yaw unlimited. */
    static MCH_AircraftInfo.Weapon mainGun() {
        return weapon(true, 0, 0.0F, -360.0F, 360.0F, -50.0F, 7.0F, true);
    }

    @Test
    void turretRingCancelsOnUnlimitedMount() {
        // THE BUG. A gunner in seat 1 looks 90 deg right of a hull facing 0. Whatever the pilot's turret ring is
        // doing, the RWS must aim where the GUNNER looks — the ring is a clamp datum, not the aim.
        MCH_AircraftInfo.Weapon w = m2Rws();
        float hull = 0.0F;
        float gunner = 90.0F;
        for (float ring : new float[] {0.0F, 45.0F, -90.0F, 179.0F, -180.0F, 37.5F}) {
            float ty = WeaponAim.turretBase(w, ring);
            float got = WeaponAim.rotationYaw(w, MchMath.wrapAngleTo180(gunner - hull), ty);
            assertEquals(90.0F, MchMath.wrapAngleTo180(got), 1e-3F,
                "unlimited turret mount must aim at its OPERATOR regardless of the ring; ring=" + ring);
        }
    }

    @Test
    void unlimitedMountTracksOperatorAcrossTheCircle() {
        MCH_AircraftInfo.Weapon w = m2Rws();
        float hull = 30.0F;
        float ring = 110.0F;
        float ty = WeaponAim.turretBase(w, ring);
        for (float gunner : new float[] {-170.0F, -90.0F, -12.0F, 0.0F, 45.0F, 150.0F}) {
            float expected = MchMath.wrapAngleTo180(gunner - hull);
            float got = MchMath.wrapAngleTo180(WeaponAim.rotationYaw(w, expected, ty));
            assertEquals(expected, got, 1e-3F, "gunner look " + gunner);
        }
    }

    @Test
    void finiteMountClampsRelativeToTheRing() {
        // Reference :4499-4507: the clamp is applied to (operator - hull - defaultYaw - ty), then ty is added back,
        // so a finite mount's traverse limits are measured in the TURRET's frame, not the hull's.
        MCH_AircraftInfo.Weapon w = m240r();
        float hull = 0.0F;
        float ring = 40.0F;
        float ty = WeaponAim.turretBase(w, ring);
        for (float gunner : new float[] {0.0F, 60.0F, 90.0F, -150.0F, 179.0F}) {
            float rel = MchMath.wrapAngleTo180(gunner - hull);
            float expected = MchMath.clamp(MchMath.wrapAngleTo180(rel - ring), -220.0F, 30.0F) + ring;
            assertEquals(expected, WeaponAim.rotationYaw(w, rel, ty), 1e-3F, "gunner look " + gunner);
        }
    }

    @Test
    void finiteMountActuallyLimitsTraverse() {
        // The renderer's old local copy passed the operator's own yaw as ty, which collapsed ey to 0 and let the
        // model traverse a full 360 against this -220..30 config. Guard that it really clamps.
        MCH_AircraftInfo.Weapon w = m240r();
        float ty = WeaponAim.turretBase(w, 0.0F);
        assertEquals(30.0F, WeaponAim.rotationYaw(w, 90.0F, ty), 1e-3F, "clamped to maxYaw");
        assertEquals(30.0F, WeaponAim.rotationYaw(w, 30.0F, ty), 1e-3F, "at the limit");
        assertEquals(10.0F, WeaponAim.rotationYaw(w, 10.0F, ty), 1e-3F, "inside the range, untouched");
    }

    @Test
    void pilotOperatedTurretMountAimsAtThePilot() {
        MCH_AircraftInfo.Weapon w = mainGun();
        float hull = 15.0F;
        float pilot = -40.0F;
        float rel = MchMath.wrapAngleTo180(pilot - hull);
        // The ring IS the pilot here, so ty == rel; it still cancels and the gun lands on the pilot's look.
        float ty = WeaponAim.turretBase(w, rel);
        assertEquals(rel, MchMath.wrapAngleTo180(WeaponAim.rotationYaw(w, rel, ty)), 1e-3F);
    }

    @Test
    void noTraverseMountStaysWeldedAtItsDefaultBearing() {
        // Reference :4494 wraps the whole yaw block in `if ((int)minYaw != 0 || (int)maxYaw != 0)`. Dropping that
        // guard would weld the pilot's ring onto a fixed mount (centauro's targeting pods, searam, Phalanx).
        MCH_AircraftInfo.Weapon fixed = weapon(true, 0, 17.0F, 0.0F, 0.0F, 0.0F, 0.0F, true);
        for (float rel : new float[] {0.0F, 90.0F, -130.0F}) {
            for (float ring : new float[] {0.0F, 75.0F, -160.0F}) {
                assertEquals(17.0F, WeaponAim.rotationYaw(fixed, rel, WeaponAim.turretBase(fixed, ring)), 1e-6F,
                    "rel=" + rel + " ring=" + ring);
            }
        }
    }

    @Test
    void pitchClampsAndIgnoresTheRing() {
        assertEquals(-70.0F, WeaponAim.rotationPitch(m2Rws(), -80.0F), 1e-3F);
        assertEquals(30.0F, WeaponAim.rotationPitch(m2Rws(), 45.0F), 1e-3F);
        assertEquals(-50.0F, WeaponAim.rotationPitch(m240r(), -80.0F), 1e-3F);
        assertEquals(7.0F, WeaponAim.rotationPitch(mainGun(), 20.0F), 1e-3F);
        assertEquals(-12.0F, WeaponAim.rotationPitch(mainGun(), -12.0F), 1e-3F, "inside the range, untouched");
    }

    @Test
    void renderYawTrailsFireYawByExactlyDefaultYaw() {
        // The model part is authored already rotated by defaultYaw, so the renderer subtracts it. If these two ever
        // stop differing by exactly that, the barrel and the projectile diverge.
        MCH_AircraftInfo.Weapon w = weapon(false, 1, 25.0F, -180.0F, 180.0F, -30.0F, 30.0F, true);
        float ty = WeaponAim.turretBase(w, 60.0F);
        for (float rel : new float[] {0.0F, 40.0F, -95.0F}) {
            assertEquals(WeaponAim.rotationYaw(w, rel, ty) - 25.0F, WeaponAim.renderYaw(w, rel, ty), 1e-4F);
        }
    }

    @Test
    void nullWeaponFallsBackToTheRawOperatorLook() {
        assertEquals(42.0F, WeaponAim.rotationYaw(null, 42.0F, 99.0F), 1e-4F);
        assertEquals(-17.0F, WeaponAim.rotationPitch(null, -17.0F), 1e-4F);
        assertEquals(0.0F, WeaponAim.turretBase(null, 123.0F), 1e-4F);
    }
}
