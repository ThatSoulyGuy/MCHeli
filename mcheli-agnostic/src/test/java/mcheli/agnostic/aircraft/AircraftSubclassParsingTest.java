package mcheli.agnostic.aircraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import mcheli.agnostic.config.MchSettings;
import mcheli.agnostic.helicopter.MCH_HeliInfo;
import mcheli.agnostic.plane.MCP_PlaneInfo;
import mcheli.agnostic.tank.MCH_TankInfo;
import mcheli.agnostic.value.Vec3d;
import mcheli.agnostic.vehicle.MCH_VehicleInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the four concrete {@link MCH_AircraftInfo} subclasses (ported in parallel) through their
 * type-specific parse directives and the global speed multipliers ({@link MchSettings}), proving each parses with
 * ZERO Minecraft and behaves like the 1.7.10 original.
 */
class AircraftSubclassParsingTest {

    @AfterEach
    void restoreDefaultSettings() {
        MchSettings.install(null);
    }

    @Test
    void heliParsesRotorsAndSeams() throws Exception {
        MCH_HeliInfo h = new MCH_HeliInfo("ah64");
        assertEquals("helicopters", h.getDirectoryName());
        assertEquals("helicopter", h.getKindName());
        assertEquals(8, h.getDefaultMaxZoom());
        assertEquals("heli", h.getDefaultHudName(0));
        assertEquals("heli_gnr", h.getDefaultHudName(1));
        assertEquals("gunner", h.getDefaultHudName(2));

        h.loadItemData("addseat", "0, 0, 0");
        h.loadItemData("speed", "0.5");
        h.loadItemData("enablefoldblade", "true");
        h.loadItemData("addrotor", "2, 1, 0, 0, 0, 0, 0, 0");

        assertTrue(h.isEnableFoldBlade);
        assertEquals(1, h.rotorList.size());
        assertEquals(2, h.rotorList.get(0).bladeNum);
        assertEquals(1, h.rotorList.get(0).bladeRot);

        h.isValidData();
        // nothing installed -> the reference default AllHeliSpeed = 1.0, so Speed is unchanged
        assertEquals(0.5f, h.speed, 1e-6f);
    }

    @Test
    void globalSpeedMultipliersApplyPerKindAtLoad() throws Exception {
        // reference MCH_HeliInfo:26 / MCP_PlaneInfo:79-80 / MCH_TankInfo:68 (vehicles have none)
        MchSettings.install(new MchSettings(2.0, 0.5, 3.0));

        MCH_HeliInfo h = new MCH_HeliInfo("h");
        h.loadItemData("addseat", "0, 0, 0");
        h.loadItemData("speed", "0.3");
        h.isValidData();
        assertEquals(0.6f, h.speed, 1e-6f);

        MCP_PlaneInfo p = new MCP_PlaneInfo("p");
        p.loadItemData("addseat", "0, 0, 0");
        p.loadItemData("speed", "1.0");
        p.loadItemData("sweepwingspeed", "1.2");
        p.isValidData();
        assertEquals(0.5f, p.speed, 1e-6f);
        assertEquals(0.6f, p.sweepWingSpeed, 1e-6f);

        MCH_TankInfo t = new MCH_TankInfo("t");
        t.loadItemData("addseat", "0, 0, 0");
        t.loadItemData("speed", "0.2");
        t.isValidData();
        assertEquals(0.6f, t.speed, 1e-6f);

        MCH_VehicleInfo v = new MCH_VehicleInfo("v");
        v.loadItemData("addseat", "0, 0, 0");
        v.loadItemData("speed", "0.4");
        v.isValidData();
        assertEquals(0.4f, v.speed, 1e-6f);
    }

    @Test
    void speedMultipliersClampLikeTheReference() {
        // reference correctionParameter: MCH_Lib.RNG(x, 0.0, 1000.0)
        MchSettings s = new MchSettings(-1.0, 5000.0, Double.NaN);
        assertEquals(0.0, s.allHeliSpeed(), 0.0);
        assertEquals(1000.0, s.allPlaneSpeed(), 0.0);
        assertEquals(1.0, s.allTankSpeed(), 0.0);
        MchSettings.install(s);
        assertEquals(s, MchSettings.current());
        MchSettings.install(null);
        assertEquals(MchSettings.DEFAULTS, MchSettings.current());
    }

    @Test
    void planeParsesWingsPylonsVtol() throws Exception {
        MCP_PlaneInfo p = new MCP_PlaneInfo("f22");
        assertEquals("planes", p.getDirectoryName());
        assertEquals("plane", p.getKindName());
        assertEquals(1.8f, p.getMaxSpeed(), 1e-6f);
        assertEquals(8, p.getDefaultMaxZoom());

        p.loadItemData("addseat", "0, 0, 0");
        p.loadItemData("addpartwing", "0, 0, 0, 0, 0, 0, 90");
        p.loadItemData("addpartpylon", "0, 0, 0, 0, 0, 0, 90");
        p.loadItemData("enablevtol", "true");
        p.loadItemData("sweepwingspeed", "1.2");

        assertEquals(1, p.wingList.size());
        assertNotNull(p.wingList.get(0).pylonList);
        assertEquals(1, p.wingList.get(0).pylonList.size());
        assertTrue(p.isEnableVtol);
        assertEquals(1.2f, p.sweepWingSpeed, 1e-6f);

        p.isValidData(); // no throw; wing not cleared (no hatch)
        assertEquals(1, p.wingList.size());
    }

    @Test
    void tankDefaultWheelListCoercesVec3() {
        MCH_TankInfo t = new MCH_TankInfo("abrams");
        assertEquals("tanks", t.getDirectoryName());
        assertEquals("tank", t.getKindName());

        List<MCH_AircraftInfo.Wheel> wheels = t.getDefaultWheelList();
        assertEquals(2, wheels.size());
        assertEquals(new Vec3d(1.5, -0.24, 2.0), wheels.get(0).pos);
        assertEquals(new Vec3d(1.5, -0.24, -2.0), wheels.get(1).pos);
    }

    @Test
    void vehicleParsesSeatAndIdentity() throws Exception {
        MCH_VehicleInfo v = new MCH_VehicleInfo("jeep");
        assertEquals("vehicles", v.getDirectoryName());
        assertEquals("vehicle", v.getKindName());

        v.loadItemData("addseat", "0, 0, 0");
        v.isValidData();
        assertEquals(1, v.getNumSeat());
    }
}
