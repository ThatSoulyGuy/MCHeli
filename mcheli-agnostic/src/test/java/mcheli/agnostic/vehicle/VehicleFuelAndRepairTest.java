package mcheli.agnostic.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pure tests for the fuel economy ({@link VehicleFuel}) and the repair/regeneration timing ({@link VehicleRepair}). */
class VehicleFuelAndRepairTest {

    @Test
    void burnSamplesOncePerSecondAboveTheFloorUnderPower() {
        assertTrue(VehicleFuel.burnsThisTick(40, 100, 0.5, 0));
        assertFalse(VehicleFuel.burnsThisTick(41, 100, 0.5, 0), "off the 20-tick beat");
        assertFalse(VehicleFuel.burnsThisTick(40, 1, 0.5, 0), "the floor is fuel > 1");
        assertFalse(VehicleFuel.burnsThisTick(40, 100, 0.0, 0), "no throttle, no burn");
        assertFalse(VehicleFuel.burnsThisTick(40, 100, 0.5, 3), "a supplier pauses the burn");
    }

    @Test
    void burnCarriesTheFraction() {
        VehicleFuel f = new VehicleFuel();
        // throttle 0.5 -> min(0.7, 1) = 0.7 per unit of consumption.
        assertEquals(0, f.burn(0.5, 1.0F, 1.0F));
        assertEquals(0.7, f.carry(), 1e-9);
        assertEquals(1, f.burn(0.5, 1.0F, 1.0F)); // 1.4 -> burn 1, carry 0.4
        assertEquals(0.4, f.carry(), 1e-9);
    }

    @Test
    void burnThrottleTermSaturatesAtOne() {
        VehicleFuel f = new VehicleFuel();
        assertEquals(3, f.burn(1.0, 3.5F, 1.0F)); // min(1.4, 1) * 3.5 = 3.5
        assertEquals(0.5, f.carry(), 1e-6);
    }

    @Test
    void exactlyOneUnitIsHeldUntilItExceedsOne() {
        // Reference tests `> 1.0`, so a carry of exactly 1.0 is not burned yet.
        VehicleFuel f = new VehicleFuel();
        assertEquals(0, f.burn(1.0, 1.0F, 1.0F));
        assertEquals(1.0, f.carry(), 1e-9);
    }

    @Test
    void canUseFuelFloorAndInfinity() {
        assertTrue(VehicleFuel.canUseFuel(0, 0, false), "tankless config");
        assertTrue(VehicleFuel.canUseFuel(100, 2, false));
        assertFalse(VehicleFuel.canUseFuel(100, 1, false));
        assertTrue(VehicleFuel.canUseFuel(100, 0, true));
    }

    @Test
    void supplyPulsesNeverOverfill() {
        assertEquals(30, VehicleFuel.supplyPulse(0, 1000));
        assertEquals(5, VehicleFuel.supplyPulse(995, 1000));
        assertEquals(0, VehicleFuel.supplyPulse(1000, 1000));
        assertEquals(100, VehicleFuel.siphonWant(0, 1000));
        assertEquals(20, VehicleFuel.siphonWant(980, 1000));
        assertEquals(1, VehicleFuel.ammoSupplyPulse(4));
        assertEquals(50, VehicleFuel.ammoSupplyPulse(500));
        assertEquals(0.0F, VehicleFuel.fraction(5, 0));
        assertEquals(0.25F, VehicleFuel.fraction(25, 100));
    }

    @Test
    void autoRepairWaitsThirtySecondsAfterAHitThenPulsesEveryTwo() {
        VehicleRepair r = new VehicleRepair();
        int maxHp = 1000;
        int damage = 100; // 90% HP
        // The hit (damage rose from 0) restarts the pause.
        assertEquals(0, r.autoRepairTick(damage, maxHp, false, 0.5));
        assertEquals(VehicleRepair.HIT_PAUSE, r.repairCount());
        int repaired = 0;
        int firstRepairTick = -1;
        for (int t = 1; t <= VehicleRepair.HIT_PAUSE + 1 + VehicleRepair.PULSE_INTERVAL + 1; t++) {
            int a = r.autoRepairTick(damage, maxHp, false, 0.5);
            if (a > 0) {
                if (firstRepairTick < 0) {
                    firstRepairTick = t;
                }
                repaired++;
                assertEquals(10, a, "1% of max HP");
                damage -= a;
            }
        }
        assertEquals(VehicleRepair.HIT_PAUSE + 1, firstRepairTick, "first pulse right after the pause");
        assertEquals(2, repaired, "then one pulse every 41 ticks (40 countdown + the pulse tick)");
    }

    @Test
    void aSmallHitRightAfterAPulseRestartsThePause() {
        // Reference autoRepair stores beforeDamageTaken AFTER repair(), so a hit no bigger than one pulse that lands
        // on the next tick still reads as a rise (post-repair 90 -> 95) and restarts the 30 s pause.
        VehicleRepair r = new VehicleRepair();
        int maxHp = 1000;
        int damage = 100;
        r.autoRepairTick(damage, maxHp, false, 0.5);
        int a = 0;
        for (int t = 0; t < VehicleRepair.HIT_PAUSE + 1 && a == 0; t++) {
            a = r.autoRepairTick(damage, maxHp, false, 0.5);
        }
        assertEquals(10, a, "the first pulse");
        damage -= a;      // the entity mends after the call returns: 90
        damage += 5;      // a 5 HP hit (<= the 10 HP pulse) on the very next tick: 95, still below the pre-pulse 100
        assertEquals(0, r.autoRepairTick(damage, maxHp, false, 0.5));
        assertEquals(VehicleRepair.HIT_PAUSE, r.repairCount(), "the hit restarts the pause, as in the reference");

        // An undamaged hull: the pulse mends nothing, so the stored damage stays 0 (reference repair() is a no-op).
        VehicleRepair whole = new VehicleRepair();
        int got = 0;
        for (int t = 0; t < VehicleRepair.HIT_PAUSE + 2 && got == 0; t++) {
            got = whole.autoRepairTick(0, maxHp, false, 0.5);
        }
        assertEquals(10, got);
        assertEquals(0, whole.autoRepairTick(1, maxHp, false, 0.5));
        assertEquals(VehicleRepair.HIT_PAUSE, whole.repairCount(), "a 1 HP hit on a whole hull is still a rise");
    }

    @Test
    void autoRepairNeedsTheHpFractionAndAnIntactHull() {
        VehicleRepair low = new VehicleRepair();
        for (int t = 0; t < 2000; t++) {
            assertEquals(0, low.autoRepairTick(600, 1000, false, 0.5), "40% HP is below AutoRepairHP 0.5");
        }
        VehicleRepair wreck = new VehicleRepair();
        for (int t = 0; t < 2000; t++) {
            assertEquals(0, wreck.autoRepairTick(1000, 1000, true, 0.0));
        }
        VehicleRepair zero = new VehicleRepair();
        int got = 0;
        for (int t = 0; t < 2000; t++) {
            got += zero.autoRepairTick(999, 1000, false, 0.0); // AutoRepairHP 0 = always
        }
        assertTrue(got > 0);
    }

    @Test
    void autoRepairMendsAtLeastOneHp() {
        VehicleRepair r = new VehicleRepair();
        int amount = 0;
        for (int t = 0; t < 2000 && amount == 0; t++) {
            amount = r.autoRepairTick(0, 50, false, 0.5); // maxHp / 100 == 0
        }
        assertEquals(1, amount);
        assertEquals(1, VehicleRepair.repairStep(0));
        assertEquals(7, VehicleRepair.repairStep(7));
    }

    @Test
    void areaRepairAndRegenerationTiming() {
        assertTrue(VehicleRepair.repairsOtherThisTick(40, 10.0F));
        assertFalse(VehicleRepair.repairsOtherThisTick(41, 10.0F));
        assertFalse(VehicleRepair.repairsOtherThisTick(40, 0.0F));
        assertTrue(VehicleRepair.refreshesRegeneration(-1), "no effect yet");
        assertTrue(VehicleRepair.refreshesRegeneration(249));
        assertFalse(VehicleRepair.refreshesRegeneration(600), "a longer potion is left alone");
    }
}
