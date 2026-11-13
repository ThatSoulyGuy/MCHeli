package mcheli.agnostic.aircraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pure tests for the switchable-part animator ({@link MCH_Parts}) and the camera-mode rules ({@link MCH_CameraMode}). */
class PartsTest {

    @Test
    void canopyOpensAtItsReferenceRateAndReportsTheEndOfTravel() {
        MCH_Parts canopy = new MCH_Parts("Canopy", 90.0F, 3.5F); // reference createCanopy
        assertTrue(canopy.isOFF());
        assertTrue(canopy.setStatus(true), "a new status is a switch");
        assertFalse(canopy.setStatus(true), "the same status again is not");
        assertFalse(canopy.isOFF(), "switched ON is no longer at rest OFF, even at rotation 0");
        int ticks = 0;
        MCH_Parts.Event last = MCH_Parts.Event.NONE;
        while (last != MCH_Parts.Event.END_ON) {
            last = canopy.update();
            ticks++;
        }
        assertEquals(26, ticks, "ceil(90 / 3.5) ticks to open");
        assertEquals(91.0F, canopy.rotation, 1e-4F, "the reference's last step overshoots and is not clamped");
        assertTrue(canopy.isON());
        assertEquals(MCH_Parts.Event.NONE, canopy.update(), "at rest nothing moves");
        assertEquals(91.0F, canopy.prevRotation, 1e-4F);
    }

    @Test
    void closingRampsDownAndLerpInterpolates() {
        MCH_Parts hatch = new MCH_Parts("Hatch", 90.0F, 1.5F);
        hatch.forceSwitch(true);
        assertTrue(hatch.isON());
        assertEquals(90.0F, hatch.prevRotation, 0.0F, "forceSwitch snaps both ends");
        hatch.setStatus(false);
        hatch.update();
        assertEquals(88.5F, hatch.rotation, 1e-4F);
        assertEquals(89.25F, hatch.lerp(0.5F), 1e-4F);
        assertEquals(88.5F / 90.0F, hatch.getFactor(), 1e-6F);
        MCH_Parts.Event e = MCH_Parts.Event.NONE;
        for (int i = 0; i < 100 && e != MCH_Parts.Event.END_OFF; i++) {
            e = hatch.update();
        }
        assertEquals(MCH_Parts.Event.END_OFF, e);
        assertTrue(hatch.isOFF());
        hatch.forceSwitch(false);
        assertEquals(0.0F, hatch.rotation, 0.0F);
    }

    @Test
    void cameraModesCycleOnlyThroughSupportedModesAndOnlyForTwoSeats() {
        assertEquals(1, MCH_CameraMode.next(0, true));
        assertEquals(2, MCH_CameraMode.next(1, true));
        assertEquals(0, MCH_CameraMode.next(2, true));
        assertEquals(0, MCH_CameraMode.next(1, false), "without post shaders thermal is skipped");
        assertEquals(0, MCH_CameraMode.normalize(-3, true));
        assertTrue(MCH_CameraMode.isValidSeat(0));
        assertTrue(MCH_CameraMode.isValidSeat(1));
        assertFalse(MCH_CameraMode.isValidSeat(2), "reference MCH_Camera.mode has two entries");
        assertEquals("NIGHT VISION", MCH_CameraMode.name(1));
        assertEquals("THERMAL VISION", MCH_CameraMode.name(2));
        assertEquals("", MCH_CameraMode.name(0));
    }

    @Test
    void nightVisionIsRefreshedAndOnlyTheCamerasOwnEffectIsRemoved() {
        assertTrue(MCH_CameraMode.shouldApplyNightVision(1, 0), "no night vision yet");
        assertTrue(MCH_CameraMode.shouldApplyNightVision(2, 210), "topped up before vanilla's flicker at 200");
        assertFalse(MCH_CameraMode.shouldApplyNightVision(1, 249), "not re-sent every tick");
        assertFalse(MCH_CameraMode.shouldApplyNightVision(1, 3600), "a drunk potion is left alone");
        assertFalse(MCH_CameraMode.shouldApplyNightVision(0, 0));
        assertTrue(MCH_CameraMode.shouldRemoveNightVision(0, 1, 240));
        assertFalse(MCH_CameraMode.shouldRemoveNightVision(0, 0, 240), "no mode change, nothing to undo");
        assertFalse(MCH_CameraMode.shouldRemoveNightVision(0, 2, 3600), "a long potion is not the camera's");
        assertFalse(MCH_CameraMode.shouldRemoveNightVision(1, 2, 240), "still in a vision mode");
    }

    @Test
    void onlyTheCamerasOwnNightVisionEffectIsRecognised() {
        assertTrue(MCH_CameraMode.isCameraNightVision(true, false, 0, MCH_CameraMode.NIGHT_VISION_TICKS));
        assertTrue(MCH_CameraMode.isCameraNightVision(true, false, 0, 1));
        assertFalse(MCH_CameraMode.isCameraNightVision(false, true, 0, 450), "a tipped arrow (450 ticks) is a potion");
        assertFalse(MCH_CameraMode.isCameraNightVision(false, false, 0, 200), "/effect ... true is not ambient");
        assertFalse(MCH_CameraMode.isCameraNightVision(true, true, 0, 200), "an ambient effect with particles");
        assertFalse(MCH_CameraMode.isCameraNightVision(true, false, 1, 200), "a stronger effect");
        assertFalse(MCH_CameraMode.isCameraNightVision(true, false, 0, 251), "longer than the camera ever applies");
        assertFalse(MCH_CameraMode.isCameraNightVision(true, false, 0, 0));
    }
}
