package mcheli.agnostic.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mcheli.agnostic.plane.MCP_PlaneInfo;
import org.junit.jupiter.api.Test;

/**
 * {@link LandingGear}: the reference gear part (MCH_Parts, rotationMax 90 / rotationInv 2.5), its
 * {@code canFold/canUnfold/fold/unfoldLandingGear} gates and the automatic rules of {@code updateParts} (:5417-5446).
 */
class LandingGearTest {

    static LandingGear retracted() {
        LandingGear g = new LandingGear();
        g.setFolded(true);
        for (int i = 0; i < 36; i++) {
            g.update();
        }
        return g;
    }

    @Test
    void travelsTwoAndAHalfDegreesPerTickAndReportsTheEnds() {
        LandingGear g = new LandingGear();
        assertEquals(LandingGear.Event.NONE, g.update()); // down and staying down
        assertTrue(g.setFolded(true));
        assertFalse(g.setFolded(true)); // no change, no sound
        LandingGear.Event last = LandingGear.Event.NONE;
        for (int i = 0; i < 36; i++) {
            last = g.update();
        }
        assertEquals(90.0F, g.rotation(), 1e-4F);
        assertEquals(87.5F, g.prevRotation(), 1e-4F);
        assertEquals(LandingGear.Event.RETRACTED, last);
        assertEquals(1.0F, g.factor(), 1e-6F);

        g.setFolded(false);
        for (int i = 0; i < 35; i++) {
            assertEquals(LandingGear.Event.NONE, g.update());
        }
        assertEquals(LandingGear.Event.DEPLOYED, g.update());
        assertEquals(0.0F, g.rotation(), 1e-4F);
    }

    @Test
    void foldNeedsTheGearDownAndTenClearBlocksBelow() {
        SimStubs.Blocks world = new SimStubs.Blocks().layer(63, 1, SimStubs.Blocks.SOLID);
        LandingGear g = new LandingGear();
        assertFalse(g.canFold(new SimStubs.Craft().at(0.4, 64.0, 0.4).in(world)));  // parked
        assertFalse(g.canFold(new SimStubs.Craft().at(0.4, 72.0, 0.4).in(world)));  // ground 9 rows below
        assertTrue(g.canFold(new SimStubs.Craft().at(0.4, 74.0, 0.4).in(world)));   // nothing within 10 rows
        assertFalse(retracted().canFold(new SimStubs.Craft().at(0.4, 75.0, 0.4).in(world)));
        assertFalse(g.canUnfold());
        assertTrue(retracted().canUnfold());
    }

    @Test
    void manualSwitchesShareTheTwentyTickCooldown() {
        ModeSwitchCooldown cd = new ModeSwitchCooldown();
        LandingGear g = new LandingGear();
        assertTrue(g.fold(cd));
        assertEquals(ModeSwitchCooldown.SWITCH, cd.get());
        assertFalse(g.unfold(cd)); // still cooling down
        for (int i = 0; i < ModeSwitchCooldown.SWITCH; i++) {
            assertFalse(cd.ready());
            cd.tick();
        }
        assertTrue(cd.ready());
        assertTrue(g.unfold(cd));
        assertFalse(g.isFolded());
        for (int i = 0; i < ModeSwitchCooldown.SWITCH; i++) {
            cd.tick();
        }
        assertFalse(g.unfold(cd)); // already down: no switch and no new cooldown
        assertEquals(0, cd.get());
        cd.tick();
        assertEquals(0, cd.get(), "the cooldown stops at 0");
    }

    @Test
    void anotherPartsSwitchBlocksTheGearAndViceVersa() {
        // The reference has ONE modeSwitchCooldown for the gear, canopy, hatch, wing and VTOL switches.
        ModeSwitchCooldown cd = new ModeSwitchCooldown();
        LandingGear g = new LandingGear();
        cd.start(); // e.g. the hatch just opened
        assertFalse(g.fold(cd));
        assertFalse(g.isFolded());
        assertEquals(ModeSwitchCooldown.SWITCH, cd.get(), "a refused switch does not restart the cooldown");
        for (int i = 0; i < ModeSwitchCooldown.SWITCH; i++) {
            cd.tick();
        }
        assertTrue(g.fold(cd));
        assertFalse(cd.ready(), "the gear switch now blocks the other parts");
        cd.set(40); // the eject-seat canopy's longer cooldown
        assertEquals(40, cd.get());
        cd.set(-5);
        assertTrue(cd.ready());
    }

    @Test
    void retractedGearDropsAtLowThrottleNearTheGround() {
        MCP_PlaneInfo info = new MCP_PlaneInfo("test_plane");
        SimStubs.Blocks world = new SimStubs.Blocks().layer(63, 1, SimStubs.Blocks.SOLID);
        SimStubs.Craft low = new SimStubs.Craft().at(0.4, 70.0, 0.4).in(world);

        LandingGear fast = retracted();
        assertFalse(fast.autoSwitch(low, info, 0.9, 0.7F, 0)); // throttle above the plane's 0.7
        LandingGear slow = retracted();
        assertTrue(slow.autoSwitch(low, info, 0.5, 0.7F, 0));
        assertFalse(slow.isFolded());

        LandingGear high = retracted();
        assertFalse(high.autoSwitch(new SimStubs.Craft().at(0.4, 90.0, 0.4).in(world), info, 0.0, 0.7F, 0));
        LandingGear vtol = retracted();
        assertTrue(vtol.autoSwitch(low, info, 0.9, 0.7F, 2)); // full VTOL hover near the ground drops it anyway
    }

    @Test
    void floatplaneKeepsItsGearUpOverWater() {
        MCP_PlaneInfo info = new MCP_PlaneInfo("test_seaplane");
        info.isFloat = true;
        SimStubs.Blocks sea = new SimStubs.Blocks().layer(63, 1, SimStubs.Blocks.WATER).layer(50, 1, SimStubs.Blocks.SOLID);

        // Gear down over the sea: the floatplane retracts it (throttle not above 0.8).
        LandingGear down = new LandingGear();
        assertTrue(down.autoSwitch(new SimStubs.Craft().at(0.4, 70.0, 0.4).in(sea), info, 0.5, 0.7F, 0));
        assertTrue(down.isFolded());
        // ...and does not drop it again over water, however slow.
        LandingGear up = retracted();
        assertFalse(up.autoSwitch(new SimStubs.Craft().at(0.4, 70.0, 0.4).in(sea), info, 0.0, 0.7F, 0));

        // Over land a floatplane behaves like any plane.
        SimStubs.Blocks land = new SimStubs.Blocks().layer(63, 1, SimStubs.Blocks.SOLID);
        assertFalse(new LandingGear().autoSwitch(new SimStubs.Craft().at(0.4, 70.0, 0.4).in(land), info, 0.5, 0.7F, 0));
        LandingGear landing = retracted();
        assertTrue(landing.autoSwitch(new SimStubs.Craft().at(0.4, 70.0, 0.4).in(land), info, 0.5, 0.7F, 0));

        // A land plane never auto-retracts.
        assertFalse(new LandingGear().autoSwitch(new SimStubs.Craft().at(0.4, 70.0, 0.4).in(sea),
            new MCP_PlaneInfo("test_plane"), 0.5, 0.7F, 0));
    }

    /** A block world that counts its block reads (a proxy over {@link SimStubs.Blocks}). */
    private static mcheli.agnostic.spi.WorldView counting(SimStubs.Blocks blocks, int[] reads) {
        return (mcheli.agnostic.spi.WorldView) java.lang.reflect.Proxy.newProxyInstance(
            LandingGearTest.class.getClassLoader(), new Class<?>[] {mcheli.agnostic.spi.WorldView.class},
            (proxy, m, args) -> {
                if (m.getName().equals("getBlockId")) {
                    reads[0]++;
                }
                try {
                    return m.invoke(blocks, args);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw e.getCause();
                }
            });
    }

    @Test
    void autoSwitchProbesOnlyWhenTheAnswerCanMatter() {
        SimStubs.Blocks open = new SimStubs.Blocks();
        int[] reads = {0};
        SimStubs.Craft flying = new SimStubs.Craft().at(0.4, 200.0, 0.4).in(counting(open, reads));
        MCP_PlaneInfo plane = new MCP_PlaneInfo("test_plane");

        // Gear down on a land plane: it can never auto-retract, so the 3x3x20 column is not read.
        assertFalse(new LandingGear().autoSwitch(flying, plane, 0.5, 0.7F, 0));
        assertEquals(0, reads[0]);
        // Gear up above the unfold throttle and not in VTOL hover: the 3x3x10 probe could not drop it either.
        assertFalse(retracted().autoSwitch(flying, plane, 0.9, 0.7F, 0));
        assertEquals(0, reads[0]);
        // Below the unfold throttle the probe runs (and finds open air: the gear stays up).
        assertFalse(retracted().autoSwitch(flying, plane, 0.5, 0.7F, 0));
        assertEquals(3 * 3 * 10, reads[0]);

        // A floatplane flying fast still needs the probe to know whether it is low over the sea.
        MCP_PlaneInfo seaplane = new MCP_PlaneInfo("test_seaplane");
        seaplane.isFloat = true;
        reads[0] = 0;
        assertFalse(new LandingGear().autoSwitch(flying, seaplane, 0.9, 0.7F, 0));
        assertEquals(3 * 3 * 20, reads[0]);
    }
}
