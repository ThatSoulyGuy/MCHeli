package mcheli.agnostic.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pure tests for the hull armor formula ({@link VehicleArmor}) and the salvage rules ({@link VehicleSalvage}). */
class VehicleArmorAndSalvageTest {

    private static final float EPS = 1e-5F;

    @Test
    void defaultArmorPassesDamageThrough() {
        // MCH_AircraftInfo defaults: ArmorMaxDamage 100000, ArmorDamageFactor 1, ArmorMinDamage 0.
        assertEquals(12.5F, VehicleArmor.apply(12.5F, 1.0F, 100000.0F, 1.0F, 0.0F), EPS);
    }

    @Test
    void capAppliesBeforeEverythingElse() {
        assertEquals(40.0F, VehicleArmor.apply(500.0F, 1.0F, 50.0F, 1.0F, 10.0F), EPS);
    }

    @Test
    void armorPlateMultipliesBeforeTheSubtraction() {
        // 20 * 0.5 = 10, * 2 = 20, - 15 = 5.
        assertEquals(5.0F, VehicleArmor.apply(20.0F, 0.5F, 1000.0F, 2.0F, 15.0F), EPS);
        // A thin hit on a plate is absorbed outright: 10 * 0.5 - 6 = -1.
        assertTrue(VehicleArmor.apply(10.0F, 0.5F, 1000.0F, 1.0F, 6.0F) <= 0.0F);
    }

    @Test
    void weakPointMultipliesAfterTheSubtraction() {
        // (20 * 1 - 15) * 3 = 15, NOT 20 * 3 - 15 = 45.
        assertEquals(15.0F, VehicleArmor.apply(20.0F, 3.0F, 1000.0F, 1.0F, 15.0F), EPS);
        // A weak point cannot rescue a hit the armor absorbed: 10 - 15 < 0 stays absorbed.
        assertTrue(VehicleArmor.apply(10.0F, 3.0F, 1000.0F, 1.0F, 15.0F) <= 0.0F);
    }

    @Test
    void creativeMeleeAlwaysBreaks() {
        assertTrue(VehicleSalvage.isPlayerBreak(true, true, true, true, false, true));
    }

    @Test
    void onlyAPlayersMeleeHitCanBreak() {
        assertFalse(VehicleSalvage.isPlayerBreak(false, true, false, false, false, false));
        assertFalse(VehicleSalvage.isPlayerBreak(false, false, false, false, false, false));
    }

    @Test
    void survivalBreaksAnUnriddenVehicleByDefault() {
        assertTrue(VehicleSalvage.isPlayerBreak(true, false, false, false, false, false));
        assertFalse(VehicleSalvage.isPlayerBreak(true, false, false, false, false, true), "a player rides it");
    }

    @Test
    void preventingBrokenStopsSurvivalBreaks() {
        assertFalse(VehicleSalvage.isPlayerBreak(true, false, true, false, false, false));
        assertFalse(VehicleSalvage.isPlayerBreak(true, false, true, true, true, false));
    }

    @Test
    void breakableOnlyPickaxeNeedsAPickaxeButIgnoresRiders() {
        assertFalse(VehicleSalvage.isPlayerBreak(true, false, false, true, false, false), "bare hand");
        assertTrue(VehicleSalvage.isPlayerBreak(true, false, false, true, true, false));
        // Reference :869-872: the pickaxe branch has no isRidePlayer() test.
        assertTrue(VehicleSalvage.isPlayerBreak(true, false, false, true, true, true));
    }

    @Test
    void creativeDropsOnlyWhenSneakingByDefault() {
        assertTrue(VehicleSalvage.dropsItem(false, false, false), "survival always drops");
        assertTrue(VehicleSalvage.dropsItem(false, true, true));
        assertFalse(VehicleSalvage.dropsItem(true, false, false));
        assertTrue(VehicleSalvage.dropsItem(true, true, false));
        // DropItemInCreativeMode flips it: drop unless sneaking.
        assertTrue(VehicleSalvage.dropsItem(true, false, true));
        assertFalse(VehicleSalvage.dropsItem(true, true, true));
    }

    @Test
    void onlyCreativeClearsAWreck() {
        assertTrue(VehicleSalvage.clearsWreck(true, true));
        assertFalse(VehicleSalvage.clearsWreck(true, false));
        assertFalse(VehicleSalvage.clearsWreck(false, true));
    }
}
