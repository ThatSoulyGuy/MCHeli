package mcheli.dependent;

import com.mojang.logging.LogUtils;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.vehicle.MCH_VehicleInfoManager;
import mcheli.agnostic.weapon.MCH_WeaponInfoManager;
import mcheli.agnostic.weapon.VehicleWeapons;
import mcheli.agnostic.weapon.WeaponSlot;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * Headless proof of the barrel-heat / overheat mechanic on the real Mk.15 Phalanx gun (M61A1: {@code HeatCount=1},
 * {@code MaxHeatCount=150}). Pure {@link WeaponSlot} simulation, no live entity: fire every tick and the heat fills
 * (the reference forces {@code heatPerShot>=2} so it out-gains the ~1/tick cooldown), locks out fire at the cap, then
 * cools and re-enables — exactly what the HUD's {@code wpn_heat} gauge shows.
 */
public final class DemoHeatSelfTest {
    private static final Logger LOG = LogUtils.getLogger();

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        MCH_AircraftInfo info = MCH_VehicleInfoManager.get("mk15");
        if (info == null) {
            LOG.info("[HEAT-SELFTEST] mk15 config not loaded -> SKIP");
            return;
        }
        VehicleWeapons w = VehicleWeapons.build(info, MCH_WeaponInfoManager::get);
        WeaponSlot gun = null;
        for (WeaponSlot s : w.all()) {
            if (s.hasHeat()) {
                gun = s;
                break;
            }
        }
        if (gun == null) {
            LOG.info("[HEAT-SELFTEST] mk15 has no heat-capable weapon -> FAIL");
            return;
        }
        // A fresh economy weapon (m61a1 MaxAmmo=1600) spawns DRY — supply + load it so it can actually fire.
        for (int i = 0; i < 5; i++) {
            gun.supplyRestAllAmmo();
            gun.reloadMag();
        }

        // Hold the trigger: cool then (if allowed) fire, each tick. Heat should climb to the cap and lock out fire.
        float peak = 0.0F;
        boolean lockedOut = false;
        int shots = 0;
        int ticksToOverheat = -1;
        for (int t = 0; t < 600; t++) {
            gun.tick();
            if (gun.canFire()) {
                gun.fireOneShot(true);
                shots++;
            } else if (gun.overheated()) {
                lockedOut = true;
                if (ticksToOverheat < 0) {
                    ticksToOverheat = t;
                }
            }
            peak = Math.max(peak, gun.heatFraction());
            if (lockedOut) {
                break;
            }
        }
        boolean filled = peak >= 0.99F;

        // Release the trigger: cooling only. Firing must re-enable (heat back below the cap) and the gauge must bleed
        // most of the way down. (heatFraction is CAPPED at 1.0 while the +30 penalty bleeds 180->150, so check the
        // final low value, not the instant it crosses the cap.)
        int ticksToReEnable = -1;
        for (int t = 0; t < 400; t++) {
            gun.tick();
            if (ticksToReEnable < 0 && !gun.overheated()) {
                ticksToReEnable = t;
            }
        }
        boolean reEnabled = ticksToReEnable >= 0 && gun.canFire();
        boolean cooled = gun.heatFraction() < 0.1F; // after sustained cooling the gauge is essentially empty

        boolean pass = filled && lockedOut && cooled && reEnabled;
        LOG.info("[HEAT-SELFTEST] RESULT: {} - peakHeat={} overheated={} (after {} ticks, {} shots); reEnabled@{}tick finalHeat={} cooled={}",
            pass ? "PASS" : "FAIL", String.format("%.2f", peak), lockedOut, ticksToOverheat, shots,
            ticksToReEnable, String.format("%.2f", gun.heatFraction()), cooled);
    }
}
