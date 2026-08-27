package mcheli.agnostic.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import org.junit.jupiter.api.Test;

/**
 * Pins the barrel-heat curve against {@code MCH_WeaponSet} (reference {@code use}:358-363 for the per-shot gain,
 * {@code update}:224-231 for the per-tick bleed).
 *
 * <p>The regression these exist for: {@code fireOneShot} used to floor the shot interval at
 * {@code Math.max(1, info.delay)}, but the reference assigns {@code countWait = interval} with no floor. That halved
 * the rate of all 35 bundled {@code Delay = 0} weapons — every rotary gun in the mod — and because heat is added per
 * SHOT while the bleed is subtracted per TICK, halving the rate does not halve the heat, it collapses it. For an
 * m61a1 ({@code HeatCount=1} -> {@code heatPerShot=2}) the net went to exactly ZERO: the Vulcan could never overheat.
 */
class BarrelHeatTest {

    /** The real m61a1 Vulcan: Delay 0, HeatCount 1 (coerced to 2), MaxHeatCount 150. */
    private static MCH_WeaponInfo vulcan() {
        MCH_WeaponInfo w = new MCH_WeaponInfo("m61a1");
        w.loadItemData("type", "MachineGun1");
        w.loadItemData("delay", "0");
        w.loadItemData("heatcount", "1");
        w.loadItemData("maxheatcount", "150");
        return w;
    }

    /** The real xm301: Delay 0, HeatCount 4, MaxHeatCount 140. */
    private static MCH_WeaponInfo xm301() {
        MCH_WeaponInfo w = new MCH_WeaponInfo("xm301");
        w.loadItemData("type", "MachineGun1");
        w.loadItemData("delay", "0");
        w.loadItemData("heatcount", "4");
        w.loadItemData("maxheatcount", "140");
        return w;
    }

    /** The real m2_rws: Delay 2, HeatCount 5, MaxHeatCount 180 — a NON-zero delay, to prove that path is untouched. */
    private static MCH_WeaponInfo rws() {
        MCH_WeaponInfo w = new MCH_WeaponInfo("m2_rws");
        w.loadItemData("type", "MachineGun1");
        w.loadItemData("delay", "2");
        w.loadItemData("heatcount", "5");
        w.loadItemData("maxheatcount", "180");
        return w;
    }

    private static WeaponSlot slot(MCH_WeaponInfo info) {
        List<MCH_AircraftInfo.Weapon> mounts = new ArrayList<>();
        mounts.add(new MCH_AircraftInfo.Weapon(
            0f, 0f, 0f, 0f, 0f, true, 0, 0f, -360f, 360f, -90f, 90f, false));
        return new WeaponSlot(info.name, info, mounts);
    }

    /** Hold the trigger for {@code ticks} server ticks, returning how many shots actually left the barrel. */
    private static int holdTrigger(WeaponSlot s, int ticks) {
        int shots = 0;
        for (int t = 0; t < ticks; t++) {
            s.tick();
            if (s.canFire()) {
                s.fireOneShot(true);
                shots++;
            }
        }
        return shots;
    }

    @Test
    void zeroDelayWeaponFiresEveryTick() {
        // Reference: countWait = interval = 0, so canUse() is true again immediately. The old Math.max(1, delay)
        // floor made this every OTHER tick.
        WeaponSlot s = slot(vulcan());
        assertEquals(20, holdTrigger(s, 20), "a Delay=0 gun fires once per tick");
    }

    @Test
    void nonZeroDelayIsUnchanged() {
        // The shot lands on the same tick the counter reaches 0, so the period is exactly `delay`: countWait=2, two
        // ticks of decrement, fire on the second. 30 ticks => 15 shots. (Delay=0 collapses that to one per tick.)
        WeaponSlot s = slot(rws());
        assertEquals(15, holdTrigger(s, 30), "a Delay=2 gun fires every 2 ticks");
    }

    @Test
    void vulcanOverheats() {
        // heatPerShot 2 (the <2 -> 2 coercion), bleed 1/tick, one shot per tick => net +1/tick against a 150 cap.
        // Under the old half-rate this was net ZERO and the assertion below could never pass.
        WeaponSlot s = slot(vulcan());
        assertTrue(s.hasHeat(), "MaxHeatCount>0 => the barrel tracks heat");
        holdTrigger(s, 150);
        assertTrue(s.overheated(), "the Vulcan must overheat under ~7.5s of sustained fire");
        assertFalse(s.canFire(), "an overheated barrel is locked out");
    }

    @Test
    void heatClimbsAtTheReferenceRate() {
        // Exactly +1/tick for the Vulcan: heatFraction after 60 ticks ~= 60/150 = 0.40.
        WeaponSlot s = slot(vulcan());
        holdTrigger(s, 60);
        assertEquals(60.0F / 150.0F, s.heatFraction(), 0.02F, "Vulcan heat climbs +1/tick");
    }

    @Test
    void xm301ReachesTheCapInAboutFortySevenTicks() {
        // heatPerShot 4, bleed 1/tick, one shot per tick => +3/tick against a 140 cap => ~47 ticks.
        WeaponSlot s = slot(xm301());
        holdTrigger(s, 40);
        assertFalse(s.overheated(), "not yet capped at 40 ticks (expected ~47)");
        holdTrigger(s, 15);
        assertTrue(s.overheated(), "capped by ~55 ticks");
    }

    @Test
    void anOverheatedBarrelCoolsAndComesBack() {
        // The shot that reaches the cap parks +30 ABOVE it (reference use:361-363), so the lockout outlasts the cap
        // itself. Releasing the trigger must eventually clear it.
        WeaponSlot s = slot(vulcan());
        holdTrigger(s, 200);
        assertTrue(s.overheated(), "sanity: locked out");
        for (int t = 0; t < 400; t++) {
            s.tick(); // trigger released
        }
        assertFalse(s.overheated(), "a released barrel cools back below the cap");
        assertEquals(0.0F, s.heatFraction(), 1e-4F, "and bleeds all the way to zero");
    }

    @Test
    void aColdBarrelNeverOverheatsWithoutFiring() {
        WeaponSlot s = slot(vulcan());
        for (int t = 0; t < 100; t++) {
            s.tick();
        }
        assertEquals(0.0F, s.heatFraction(), 1e-4F);
        assertFalse(s.overheated());
    }
}
