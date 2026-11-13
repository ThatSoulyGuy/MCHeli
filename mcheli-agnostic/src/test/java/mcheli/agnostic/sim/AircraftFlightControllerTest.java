package mcheli.agnostic.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The {@code onUpdateAircraft} pieces {@link AircraftFlightController} adds around the flight models: the hover/gunner
 * pitch levelling (MCH_EntityHeli:349-351, MCP_EntityPlane:191-193), the wreck throttle bleed (MCH_EntityHeli:353-361)
 * and the end-of-tick {@code setRotation} % 360 wrap (MCH_EntityAircraft:393-396); and the plane's gunner-mode gate
 * (MCP_EntityPlane:140-147), which the pilot's client also evaluates.
 */
class AircraftFlightControllerTest {

    @Test
    void hoveringLevelsThePitchFivePercentPerTick() {
        SimStubs.Craft e = new SimStubs.Craft().at(0, 100, 0);
        SimStubs.State mod = new SimStubs.State();
        mod.hovering = true;
        e.pitch = 20.0F;
        AircraftFlightController.levelHoverPitch(e, mod);
        assertEquals(19.0F, e.pitch, 1e-5F);
        e.pitch = -69.0F;
        AircraftFlightController.levelHoverPitch(e, mod);
        assertEquals(-69.0F * 0.95F, e.pitch, 1e-4F);
    }

    @Test
    void levellingNeedsHoverAnIntactHullAndUnder70Degrees() {
        SimStubs.Craft e = new SimStubs.Craft().at(0, 100, 0);
        SimStubs.State mod = new SimStubs.State();
        e.pitch = 20.0F;
        AircraftFlightController.levelHoverPitch(e, mod); // not hovering
        assertEquals(20.0F, e.pitch, 0.0F);

        mod.hovering = true;
        e.pitch = 70.0F;
        AircraftFlightController.levelHoverPitch(e, mod); // |pitch| < 70 required
        assertEquals(70.0F, e.pitch, 0.0F);

        mod.destroyed = true;
        e.pitch = 20.0F;
        AircraftFlightController.levelHoverPitch(e, mod); // a wreck tumbles instead
        assertEquals(20.0F, e.pitch, 0.0F);
    }

    @Test
    void wreckThrottleBleedsNearTheGroundAndAfterTheCrash() {
        // A block 1 below the hull: getBlockIdY(this, 3, -2) > 0.
        SimStubs.Craft e = new SimStubs.Craft().at(0.4, 64.0, 0.4)
            .in(new SimStubs.Blocks().layer(63, 1, SimStubs.Blocks.SOLID));
        SimStubs.State mod = new SimStubs.State();
        mod.destroyed = true;
        AircraftSimState st = new AircraftSimState();

        st.currentThrottle = 0.5;
        AircraftFlightController.decayWreckThrottle(e, st, mod);
        assertEquals(0.4, st.currentThrottle, 1e-12);       // x0.8

        mod.exploded = true;
        st.currentThrottle = 0.5;
        AircraftFlightController.decayWreckThrottle(e, st, mod);
        assertEquals(0.5 * 0.8 * 0.98, st.currentThrottle, 1e-12);
    }

    @Test
    void wreckThrottleHoldsInTheAirAndWhileIntact() {
        SimStubs.Craft high = new SimStubs.Craft().at(0.4, 100.0, 0.4)
            .in(new SimStubs.Blocks().layer(63, 1, SimStubs.Blocks.SOLID));
        SimStubs.State wreck = new SimStubs.State();
        wreck.destroyed = true;
        AircraftSimState st = new AircraftSimState();
        st.currentThrottle = 0.5;
        AircraftFlightController.decayWreckThrottle(high, st, wreck); // falling, not exploded: only the -0.00125 decay
        assertEquals(0.5, st.currentThrottle, 0.0);

        SimStubs.Craft low = new SimStubs.Craft().at(0.4, 64.0, 0.4)
            .in(new SimStubs.Blocks().layer(63, 1, SimStubs.Blocks.SOLID));
        AircraftFlightController.decayWreckThrottle(low, st, new SimStubs.State()); // intact
        assertEquals(0.5, st.currentThrottle, 0.0);
    }

    @Test
    void rotationWrapsModulo360() {
        SimStubs.Craft e = new SimStubs.Craft();
        e.yaw = 725.0F;
        e.pitch = -370.0F;
        AircraftFlightController.wrapRotation(e);
        assertEquals(5.0F, e.yaw, 1e-4F);
        assertEquals(-10.0F, e.pitch, 1e-4F); // Java % keeps the sign, like the reference
    }

    @Test
    void tickServerRunsTheTailBetweenControlAndForces() {
        // A hovering heli pitched 20 deg: the levelling lands in the same tick, before the integration reads it.
        // (The heli trace fixtures: open air and a jitter-free RNG for the hover branch.)
        HeliFlightModelTraceTest.FakeEntity e = new HeliFlightModelTraceTest.FakeEntity().at(0, 100, 0)
            .attitude(0, 20, 0).withRider();
        HeliFlightModelTraceTest.Heli mod = new HeliFlightModelTraceTest.Heli(e);
        mod.hovering = true;
        AircraftSimState st = new AircraftSimState(0.07);
        AircraftFlightController.tickServer(e, HeliFlightModelTraceTest.info(), st, ControlInput.NONE, mod,
            new HeliFlightModel());
        assertEquals(19.0F, e.pitch, 1e-5F);
    }

    @Test
    void planeGunnerModeNeedsFastLevelFlightFiveBlocksUp() {
        SimStubs.Blocks ground = new SimStubs.Blocks().layer(63, 1, SimStubs.Blocks.SOLID);
        SimStubs.Craft high = new SimStubs.Craft().at(0.4, 70.0, 0.4).in(ground);  // ground 7 rows below
        assertTrue(AircraftFlightController.canPlaneSwitchGunnerMode(high, 0.7));
        assertFalse(AircraftFlightController.canPlaneSwitchGunnerMode(high, 0.6), "throttle must be OVER 0.6");
        // The client passes the synced throttle; the old client check read the never-advanced client sim state (0).
        assertFalse(AircraftFlightController.canPlaneSwitchGunnerMode(high, 0.0));

        // Just airborne is not enough (the former !onGround() stand-in allowed it): the column must be clear 5 rows down.
        SimStubs.Craft low = new SimStubs.Craft().at(0.4, 65.0, 0.4).in(ground);   // ground 2 rows below, not touching
        assertFalse(AircraftFlightController.canPlaneSwitchGunnerMode(low, 1.0));
        SimStubs.Craft edge = new SimStubs.Craft().at(0.4, 68.0, 0.4).in(ground);  // rows 68..64 clear
        assertTrue(AircraftFlightController.canPlaneSwitchGunnerMode(edge, 1.0));
        // Water counts as below (the reference canCollideCheck(0, true) includes liquids).
        SimStubs.Craft sea = new SimStubs.Craft().at(0.4, 66.0, 0.4)
            .in(new SimStubs.Blocks().layer(63, 1, SimStubs.Blocks.WATER));
        assertFalse(AircraftFlightController.canPlaneSwitchGunnerMode(sea, 1.0));

        high.roll = 41.0F;
        assertFalse(AircraftFlightController.canPlaneSwitchGunnerMode(high, 1.0), "banked past 40");
        high.roll = 400.0F; // wraps to 40
        assertTrue(AircraftFlightController.canPlaneSwitchGunnerMode(high, 1.0));
        high.roll = 0.0F;
        high.pitch = -45.0F;
        assertFalse(AircraftFlightController.canPlaneSwitchGunnerMode(high, 1.0), "climbing past 40");
    }
}
