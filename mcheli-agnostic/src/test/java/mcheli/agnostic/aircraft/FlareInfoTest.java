package mcheli.agnostic.aircraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pure tests for the flare dispense state machine + volley-spread math ({@link MCH_FlareInfo}). No Minecraft. */
class FlareInfoTest {

    @Test
    void useOpensTheBurnWindowThenGoesOnCooldown() {
        MCH_FlareInfo f = new MCH_FlareInfo();
        assertTrue(f.canUseFlare());
        f.use(1); // type 1: tickWait 200, tickEnable 100
        assertFalse(f.canUseFlare(), "cannot re-fire mid-window");
        assertTrue(f.isPreparing());
        assertTrue(f.isUsing(), "decoy active at the start of the window");

        // Decoy window is the FIRST tickEnable ticks: active while tick > tickWait-tickEnable (>100).
        for (int i = 0; i < 99; i++) {
            f.tickDown();
        }
        assertTrue(f.isUsing(), "still in the active decoy window at tick 101");
        f.tickDown(); // tick == 100 now
        assertFalse(f.isUsing(), "past the active window into the cooldown tail");
        assertTrue(f.isPreparing(), "still on cooldown, cannot re-fire");
        assertFalse(f.canUseFlare());

        for (int i = 0; i < 100; i++) {
            f.tickDown();
        }
        assertTrue(f.canUseFlare(), "window fully elapsed -> ready again");
    }

    @Test
    void volleysAreCappedAtNumFlareMax() {
        MCH_FlareInfo f = new MCH_FlareInfo();
        f.use(1); // interval 3, numFlareMax 16
        int volleys = 0;
        for (int i = 0; i < 200; i++) {
            f.tickDown();
            while (f.shouldSpawnVolley()) {
                volleys++;
                f.onVolleySpawned();
            }
        }
        assertEquals(16, volleys, "type 1 emits exactly numFlareMax volleys");
    }

    @Test
    void type10IsASingleAirburstRing() {
        MCH_FlareInfo f = new MCH_FlareInfo();
        f.use(10);
        assertEquals(8, f.volleyNum(), "type 10 = 8 flares per volley");
        assertEquals(10, f.fuseCount(), "type 10 flares airburst at 10 ticks");
        int volleys = 0;
        for (int i = 0; i < 250; i++) {
            f.tickDown();
            while (f.shouldSpawnVolley()) {
                volleys++;
                f.onVolleySpawned();
            }
        }
        assertEquals(1, volleys, "type 10 = a single ring volley (numFlareMax 1)");
    }

    @Test
    void aliasedTypesFallBackToType1() {
        // types 0/6/7/8/9 alias type 1 (num 1, tickWait 200).
        for (int t : new int[] {0, 6, 7, 8, 9}) {
            MCH_FlareInfo.FlareParam p = MCH_FlareInfo.params(t);
            assertEquals(1, p.num);
            assertEquals(200, p.tickWait);
        }
    }

    @Test
    void volleyMotionIsFiniteAndRingIsSpread() {
        // A type-1 ejection is finite.
        double[] m = MCH_FlareInfo.volleyMotion(1, 0, 1, 0, 0, 0, 0, 0.3F, 0.5F, 0.7F);
        assertEquals(3, m.length);
        assertTrue(Double.isFinite(m[0]) && Double.isFinite(m[1]) && Double.isFinite(m[2]));

        // A type-10 ring ejects flares 0 and 4 (of 8) in roughly opposite horizontal directions.
        double[] a = MCH_FlareInfo.volleyMotion(10, 0, 8, 0, 0, 0, 0, 0, 0, 0);
        double[] b = MCH_FlareInfo.volleyMotion(10, 4, 8, 0, 0, 0, 0, 0, 0, 0);
        double dot = a[0] * b[0] + a[2] * b[2]; // horizontal dot product
        assertTrue(dot < 0.0, "opposite ring positions eject in opposing directions");
        assertEquals(0.35, a[1], 1.0e-9, "type-10 ty = 0.7 * 0.5");
    }
}
