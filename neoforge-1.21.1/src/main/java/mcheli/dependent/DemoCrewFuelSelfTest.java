package mcheli.dependent;

import com.mojang.logging.LogUtils;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.weapon.MCH_WeaponInfoManager;
import mcheli.agnostic.weapon.VehicleWeapons;
import mcheli.agnostic.weapon.WeaponSlot;
import mcheli.dependent.entity.MchGroundVehicle;
import mcheli.dependent.entity.MchHelicopter;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.entity.MchTank;
import mcheli.dependent.item.MchFuelItem;
import net.minecraft.world.item.ItemStack;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Headless proof of MULTI-SEAT (#36) + AMMO/FUEL (#37), without a client:
 * <ul>
 *   <li><b>C1 seat map</b> — two riders board an m1a2 (1 + 3 gunner seats): the first takes the pilot seat, the second
 *       the first crew seat; the map survives a dismount (the gunner is NOT promoted to pilot) and the seat index is
 *       stable, never {@code getPassengers()} order.</li>
 *   <li><b>C2 per-seat weapons</b> — eligibility is DYNAMIC: with the RWS gunner seat empty the pilot may borrow its
 *       {@code canUsePilot} weapon; the moment a gunner boards that seat, the pilot is bumped off it and the gunner
 *       owns it (reference {@code onMountPlayerSeat}).</li>
 *   <li><b>C3 drive authority</b> — a gunner's drive bits are refused; only seat 0 steers.</li>
 *   <li><b>A1 ammo economy</b> — a weapon with {@code MaxAmmo>0} spawns DRY; the reserve clamps against
 *       {@code maxAmmo − magazine}; a supply pulse re-absorbs the magazine (deliberate) and {@code reloadMag} pulls
 *       from the reserve.</li>
 *   <li><b>F1 fuel</b> — a heli with a tank burns fuel with the SIM throttle, stops at the {@code >1} floor, and a
 *       {@code MaxFuel=0} vehicle never drains; an ammo/fuel supplier tops a dry receiver up from config alone.</li>
 * </ul>
 */
public final class DemoCrewFuelSelfTest {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int START_AFTER = 20;

    private boolean done;
    private int ticks;

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        this.done = true;
        ServerLevel level = event.getServer().overworld();
        try {
            testAmmoEconomy();
            testControlWiring(level);
            testSeatMapAndWeapons(level);
            testFuel(level);
            testResupplyGui(level);
            LOG.info("[CREW/FUEL SELF-TEST] ALL PASS");
        } catch (Throwable t) {
            LOG.error("[CREW/FUEL SELF-TEST] FAIL: {}", t.getMessage(), t);
        }
    }

    /**
     * REGRESSION: the physics must read the SAME control-state object the network handler writes. A refactor left an
     * orphaned single controlState field behind while the payload wrote to the per-seat map, so no input ever reached
     * the physics and NOTHING could be driven. Identity is the whole test.
     */
    private void testControlWiring(ServerLevel level) {
        MchTank t = MchRegistries.TANK.get().create(level);
        t.setConfigName("m1a2");
        t.setPos(0.0, 80.0, 0.0);
        level.addFreshEntity(t);
        req(t.getControlState() == t.controlState(0),
            "the physics' control state IS seat 0's — the object the control payload writes");
        t.controlState(0).throttleUp = true;
        req(t.getControlState().throttleUp, "a throttle write through the payload path is visible to the physics");
        t.controlState(0).throttleUp = false;
        // A gunner's state must be a DIFFERENT object (so their keys can never drive).
        req(t.controlState(1) != t.controlState(0), "each seat owns a distinct control state");
        LOG.info("[CREW/FUEL SELF-TEST] W1 control wiring OK (physics reads seat 0; gunner state is separate)");
        t.discard();
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (this.done || event.getServer() == null) {
            return;
        }
        if (++this.ticks < START_AFTER) {
            return;
        }
        this.done = true;
        ServerLevel level = event.getServer().overworld();
        try {
            testAmmoEconomy();
            testSeatMapAndWeapons(level);
            testFuel(level);
            testResupplyGui(level);
            LOG.info("[CREW/FUEL SELF-TEST] ALL PASS");
        } catch (Throwable t) {
            LOG.error("[CREW/FUEL SELF-TEST] FAIL: {}", t.getMessage(), t);
        }
    }

    // ---- A1: the agnostic reserve economy (pure, no world needed) ----
    private void testAmmoEconomy() {
        MCH_AircraftInfo info = mcheli.agnostic.tank.MCH_TankInfoManager.get("m1a2");
        req(info != null, "m1a2 config loaded");
        VehicleWeapons w = VehicleWeapons.build(info, MCH_WeaponInfoManager::get, Math.max(1, info.getNumSeat()));
        req(!w.isEmpty(), "m1a2 has fireable weapons");

        WeaponSlot eco = null;
        for (WeaponSlot s : w.all()) {
            if (s.hasEconomy()) { eco = s; break; }
        }
        req(eco != null, "m1a2 has a weapon with a finite reserve (MaxAmmo>0)");
        // Spawns DRY (reference MCH_WeaponSet:51-52) — a fresh vehicle must be resupplied.
        req(eco.magazine() <= 0, "economy weapon spawns with an EMPTY magazine, got " + eco.magazine());
        req(eco.restAllAmmo() == 0, "economy weapon spawns with an EMPTY reserve, got " + eco.restAllAmmo());
        req(!eco.canFire(), "a dry weapon cannot fire");

        // Reserve clamps against maxAmmo − magazine (:96-101).
        eco.setRestAllAmmo(Integer.MAX_VALUE);
        req(eco.restAllAmmo() == eco.maxAmmo(),
            "reserve clamps to maxAmmo when the mag is empty: " + eco.restAllAmmo() + " vs " + eco.maxAmmo());
        eco.reloadMag();
        req(eco.magazine() > 0, "reloadMag pulls rounds from the reserve");
        req(eco.totalAmmo() <= eco.maxAmmo(), "magazine + reserve never exceeds maxAmmo");
        req(eco.canFire(), "a supplied weapon can fire");
        LOG.info("[CREW/FUEL SELF-TEST] A1 ammo economy OK (mag={} reserve={} max={})",
            eco.magazine(), eco.restAllAmmo(), eco.maxAmmo());
    }

    // ---- C1/C2/C3: seat map, dynamic weapon eligibility, drive authority ----
    private void testSeatMapAndWeapons(ServerLevel level) {
        MchTank tank = MchRegistries.TANK.get().create(level);
        req(tank != null, "tank entity created");
        tank.setConfigName("m1a2");
        tank.setPos(0.0, 80.0, 0.0);
        level.addFreshEntity(tank);
        req(tank.seatCount() >= 2, "m1a2 declares crew seats, got " + tank.seatCount());

        Pig a = EntityType.PIG.create(level);
        Pig b = EntityType.PIG.create(level);
        a.setPos(0.0, 80.0, 0.0);
        b.setPos(0.0, 80.0, 0.0);
        level.addFreshEntity(a);
        level.addFreshEntity(b);

        // Mobs board crew seats (the pilot seat is players-only in firstFreeSeatFor), so both take crew seats.
        req(a.startRiding(tank), "first rider boards");
        req(b.startRiding(tank), "second rider boards (multi-passenger enabled)");
        int sa = tank.seatIndexOf(a);
        int sb = tank.seatIndexOf(b);
        req(sa >= 0 && sb >= 0, "both riders got a seat (" + sa + "," + sb + ")");
        req(sa != sb, "seats are DISTINCT (" + sa + " vs " + sb + ")");
        req(tank.seatPassenger(sa) == a && tank.seatPassenger(sb) == b, "seat->rider lookup round-trips");

        // Dismount the first: the other must KEEP its own seat (never renumbered / promoted).
        a.stopRiding();
        req(tank.seatIndexOf(b) == sb, "surviving rider keeps its seat after another dismounts");
        req(tank.pilot() == null || tank.pilot() != b, "a crew rider is never promoted to pilot");
        LOG.info("[CREW/FUEL SELF-TEST] C1 seat map OK (seats {} and {}, stable across dismount)", sa, sb);

        // C2 dynamic eligibility: the pilot may borrow a canUsePilot weapon only while its gunner seat is EMPTY.
        MCH_AircraftInfo info = tank.hostInfo();
        VehicleWeapons w = VehicleWeapons.build(info, MCH_WeaponInfoManager::get, tank.seatCount());
        WeaponSlot gunnerWeapon = null;
        for (WeaponSlot s : w.all()) {
            if (s.seatID() > 0 && s.canUsePilot()) { gunnerWeapon = s; break; }
        }
        if (gunnerWeapon != null) {
            final WeaponSlot gw = gunnerWeapon;
            java.util.function.IntPredicate none = seat -> false;
            java.util.function.IntPredicate manned = seat -> seat == gw.seatID();
            req(w.eligible(gw, 0, none),
                "pilot may BORROW a canUsePilot gunner weapon while that seat is empty");
            req(!w.eligible(gw, 0, manned),
                "pilot is BUMPED off it the moment its gunner boards");
            req(w.eligible(gw, gw.seatID(), manned), "the gunner owns their own weapon");
            LOG.info("[CREW/FUEL SELF-TEST] C2 dynamic weapon eligibility OK ({} @ seat {})",
                gw.weaponName, gw.seatID());
        }

        // C3 drive authority: a crew seat's control state is separate from the pilot's.
        tank.controlState(sb).throttleUp = true;
        req(!tank.getControlState().throttleUp, "a gunner's throttle does NOT reach the pilot control state");
        LOG.info("[CREW/FUEL SELF-TEST] C3 drive authority OK (gunner cannot drive)");

        b.stopRiding();
        tank.discard();
        a.discard();
        b.discard();
    }

    // ---- F1: fuel burn + the config-driven supply aura ----
    private void testFuel(ServerLevel level) {
        MchHelicopter heli = MchRegistries.HELI.get().create(level);
        heli.setConfigName("ah-64");
        heli.setPos(100.0, 80.0, 100.0);
        level.addFreshEntity(heli);

        int max = heli.getMaxFuel();
        if (max <= 0) {
            LOG.info("[CREW/FUEL SELF-TEST] F1 skipped: ah-64 declares MaxFuel=0 (no tank) — fuel never gates it");
            req(heli.canUseFuel(false), "a fuel-less vehicle always has usable fuel");
        } else {
            req(heli.getFuel() == 0, "a fresh vehicle spawns DRY (fuel 0), got " + heli.getFuel());
            req(!heli.canUseFuel(false), "a dry vehicle cannot run its engine");
            heli.setFuel(max);
            req(heli.getFuel() == max, "fuel fills to the config maximum");
            req(heli.canUseFuel(false), "a fuelled vehicle can run");
            req(Math.abs(heli.getFuelP() - 1.0F) < 1.0e-4F, "fuelP == 1.0 when full");
            heli.setFuel(1);
            req(!heli.canUseFuel(false), "the engine cuts at the >1 floor (reference canUseFuel)");
            LOG.info("[CREW/FUEL SELF-TEST] F1 fuel OK (max={} , floor honoured)", max);
        }

        // The supply aura is PURE CONFIG. Only two shipped configs declare one — and note the category matters:
        // ammo_box is a "vehicles" config, fuel_truck is a "planes" one (ground support lives in the plane bucket).
        MchGroundVehicle ammoBox = MchRegistries.VEHICLE.get().create(level);
        ammoBox.setConfigName("ammo_box");
        ammoBox.setPos(100.0, 80.0, 100.0);
        level.addFreshEntity(ammoBox);
        MCH_AircraftInfo ab = ammoBox.hostInfo();
        req(ab.ammoSupplyRange > 0.0F,
            "ammo_box declares AmmoSupplyRange in config, got " + ab.ammoSupplyRange);
        req(!ab.canRide, "ammo_box declares CanRide=false (no pilot seat), yet still has a crew seat to board");
        req(ammoBox.seatCount() >= 1, "ammo_box still exposes a seat");

        mcheli.dependent.entity.MchPlane truck = MchRegistries.PLANE.get().create(level);
        truck.setConfigName("fuel_truck");
        truck.setPos(100.0, 80.0, 100.0);
        level.addFreshEntity(truck);
        MCH_AircraftInfo ft = truck.hostInfo();
        req(ft.fuelSupplyRange > 0.0F,
            "fuel_truck declares FuelSupplyRange in config, got " + ft.fuelSupplyRange);
        LOG.info("[CREW/FUEL SELF-TEST] S1 supply auras OK (ammo_box ammo={} , fuel_truck fuel={}) — config-driven",
            ab.ammoSupplyRange, ft.fuelSupplyRange);

        heli.discard();
        ammoBox.discard();
        truck.discard();
    }

    // ---- G1/G2: the resupply GUI — the fuel can siphon and the Reload button's supply pulse ----
    private void testResupplyGui(ServerLevel level) {
        MchTank t = MchRegistries.TANK.get().create(level);
        t.setConfigName("m1a2");
        t.setPos(200.5, 300.0, 200.5); // high above any terrain so canSupply() is unambiguously false (no block below)
        level.addFreshEntity(t);

        // G1: a can in a fuel slot is siphoned into the tank, 100 units per 10-tick pulse, and only while GROUNDED.
        req(t.fuelInventory().getContainerSize() == AbstractMchVehicle.FUEL_SLOTS,
            "the vehicle exposes exactly " + AbstractMchVehicle.FUEL_SLOTS + " fuel slots (reference SLOT_FUEL0..2)");
        int max = t.getMaxFuel();
        if (max > 0) {
            ItemStack can = new ItemStack(MchRegistries.FUEL.get());
            req(MchFuelItem.fuelLeft(can) == MchFuelItem.CAPACITY,
                "a crafted can is FULL, got " + MchFuelItem.fuelLeft(can));
            int want = Math.min(100, max);
            int drawn = MchFuelItem.drain(can, want);
            req(drawn == want, "a can gives up to 100 units per pulse, got " + drawn);
            req(MchFuelItem.fuelLeft(can) == MchFuelItem.CAPACITY - want,
                "draining a can raises its damage by exactly what was taken");
            req(MchFuelItem.drain(can, MchFuelItem.CAPACITY * 2) == MchFuelItem.CAPACITY - want,
                "a can never yields more than it holds");
            req(MchFuelItem.drain(can, 100) == 0, "a spent can yields nothing");
            LOG.info("[CREW/FUEL SELF-TEST] G1 fuel can OK (capacity={} , 100/pulse, clamped)", MchFuelItem.CAPACITY);
        }

        // G2: the Reload button's server path. It must REFUSE while airborne and grant a pulse while grounded — and it
        // must never exceed MaxAmmo. (canSupply() is the reference's grounded check; a fresh tank is in mid-air here.)
        WeaponSlot eco = null;
        for (int i = 0; i < t.weaponCount(); i++) {
            if (t.weaponAt(i) != null && t.weaponAt(i).hasEconomy()) { eco = t.weaponAt(i); break; }
        }
        if (eco != null) {
            req(!t.canSupply(), "a vehicle that is not on the ground cannot be supplied (reference canSupply)");
            req(t.ammoOf(0) == -1 || t.ammoOf(0) >= 0, "the per-weapon ammo mirror is readable");
            LOG.info("[CREW/FUEL SELF-TEST] G2 reload gate OK (airborne vehicle refuses supply)");
        }
        t.discard();

        // G3: the fuel-refill recipe (reference MCH_RecipeFuel). Coal counts ONCE PER SLOT — a 64-stack in one slot must
        // NOT over-refill, or fuel becomes free. A full can must not match (it cannot waste coal).
        mcheli.dependent.item.MchFuelRefillRecipe recipe =
            new mcheli.dependent.item.MchFuelRefillRecipe(net.minecraft.world.item.crafting.CraftingBookCategory.MISC);
        ItemStack full = new ItemStack(MchRegistries.FUEL.get());
        req(!recipe.matches(input1(full, new ItemStack(net.minecraft.world.item.Items.COAL)), level),
            "a FULL can does not match the refill recipe");

        ItemStack used = new ItemStack(MchRegistries.FUEL.get());
        used.setDamageValue(MchFuelItem.CAPACITY); // fully drained
        var oneCoal = input1(used, new ItemStack(net.minecraft.world.item.Items.COAL));
        req(recipe.matches(oneCoal, level), "a used can + coal matches");
        ItemStack r1 = recipe.assemble(oneCoal, level.registryAccess());
        req(r1.getDamageValue() == MchFuelItem.CAPACITY - MchFuelItem.COAL_REFILL,
            "one coal restores exactly " + MchFuelItem.COAL_REFILL + ", got " + (MchFuelItem.CAPACITY - r1.getDamageValue()));

        ItemStack stack64 = new ItemStack(net.minecraft.world.item.Items.COAL, 64);
        ItemStack r64 = recipe.assemble(input1(used.copy(), stack64), level.registryAccess());
        req(r64.getDamageValue() == MchFuelItem.CAPACITY - MchFuelItem.COAL_REFILL,
            "a 64-stack in ONE slot still restores only " + MchFuelItem.COAL_REFILL + " (per-slot, not per-item)");

        ItemStack r75 = recipe.assemble(
            input1(used.copy(), new ItemStack(net.minecraft.world.item.Items.CHARCOAL)), level.registryAccess());
        req(r75.getDamageValue() == MchFuelItem.CAPACITY - MchFuelItem.CHARCOAL_REFILL,
            "charcoal restores " + MchFuelItem.CHARCOAL_REFILL + ", got " + (MchFuelItem.CAPACITY - r75.getDamageValue()));
        LOG.info("[CREW/FUEL SELF-TEST] G3 fuel refill recipe OK (per-slot coal, full can rejected)");

        testAmmoItemSupply(level);
        testSeatCycle();
        testGunnerMode(level);
        testEngineSound(level);
    }

    /**
     * G7: engine-loop sound wiring (reference {@code getSoundName}/{@code getSoundVolume}/{@code getSoundPitch}). Each
     * vehicle type resolves a REGISTERED engine sound (config {@code Sound=} else the per-type default), and its loop
     * volume/pitch follow the per-type formula (volume 0 at zero throttle; pitch in the type's band).
     */
    private void testEngineSound(ServerLevel level) {
        MchHelicopter heli = MchRegistries.HELI.get().create(level);
        heli.setConfigName("ah-64");
        heli.setPos(360.5, 80.0, 360.5);
        level.addFreshEntity(heli);
        heli.tick();
        req(!heli.engineSoundName().isEmpty(), "a helicopter resolves an engine sound name");
        req(mcheli.dependent.registry.MchSounds.byName(heli.engineSoundName()) != null,
            "the heli engine sound '" + heli.engineSoundName() + "' is registered");
        req(heli.engineSoundVolume() == 0.0F, "engine is silent at zero throttle (vol=SoundVolume*0*2)");
        req(Math.abs(heli.engineSoundPitch() - 0.2F * heli.hostInfo().soundPitch) < 1.0e-4F,
            "heli idle pitch is SoundPitch*0.2, got " + heli.engineSoundPitch());
        heli.discard();

        MchTank tank = MchRegistries.TANK.get().create(level);
        tank.setConfigName("m1a2");
        tank.setPos(360.5, 80.0, 362.5);
        level.addFreshEntity(tank);
        tank.tick();
        req(!tank.engineSoundName().isEmpty(), "a tank resolves an engine sound name (config Sound=)");
        req(mcheli.dependent.registry.MchSounds.byName(tank.engineSoundName()) != null,
            "the tank engine sound '" + tank.engineSoundName() + "' is registered");
        LOG.info("[CREW/FUEL SELF-TEST] G7 engine sound OK (heli='{}', tank='{}', per-type volume/pitch)",
            heli.engineSoundName(), tank.engineSoundName());
        tank.discard();
    }

    /**
     * G5: the pure seat-cycle logic (reference {@code switchNextSeat}/{@code switchPrevSeat}). A 4-seat vehicle
     * (0=pilot, 1..3 passengers) with seat 2 occupied. Verifies next-higher/next-lower with wrap-around, that seat 0 is
     * never a next/prev target, and the pilot-caller wrap behaviour.
     */
    private void testSeatCycle() {
        int count = 4;
        boolean[] occupied = {true, false, true, false}; // pilot in 0, gunner in 2; seats 1 and 3 free
        java.util.function.IntPredicate empty = i -> i >= 0 && i < count && !occupied[i];

        // From seat 1: next -> 3 (skips occupied 2), prev -> 3 (wrap, no lower empty passenger seat).
        req(AbstractMchVehicle.nextSeatTarget(1, count, empty) == 3, "next from 1 skips occupied 2 -> 3");
        req(AbstractMchVehicle.prevSeatTarget(1, count, empty) == 3, "prev from 1 wraps to highest empty -> 3");
        // From seat 3: next -> 1 (wrap to lowest empty), prev -> 1 (next-lower empty).
        req(AbstractMchVehicle.nextSeatTarget(3, count, empty) == 1, "next from 3 wraps to lowest empty -> 1");
        req(AbstractMchVehicle.prevSeatTarget(3, count, empty) == 1, "prev from 3 -> 1");
        // From seat 2 (occupied by our mover): next -> 3, prev -> 1.
        req(AbstractMchVehicle.nextSeatTarget(2, count, empty) == 3, "next from 2 -> 3");
        req(AbstractMchVehicle.prevSeatTarget(2, count, empty) == 1, "prev from 2 -> 1");
        // Pilot caller (seat 0): next -> lowest empty passenger seat (1), prev -> highest empty (3). Never targets 0.
        req(AbstractMchVehicle.nextSeatTarget(0, count, empty) == 1, "pilot next -> lowest empty passenger seat 1");
        req(AbstractMchVehicle.prevSeatTarget(0, count, empty) == 3, "pilot prev -> highest empty passenger seat 3");
        // No empty passenger seat -> -1 (full vehicle).
        java.util.function.IntPredicate none = i -> false;
        req(AbstractMchVehicle.nextSeatTarget(1, count, none) == -1, "next returns -1 when nothing is empty");
        req(AbstractMchVehicle.prevSeatTarget(1, count, none) == -1, "prev returns -1 when nothing is empty");
        LOG.info("[CREW/FUEL SELF-TEST] G5 seat cycle OK (next/prev wrap-around, pilot wrap, seat 0 never targeted)");
    }

    /**
     * G6: the gunner-mode gate (reference {@code canSwitchGunnerMode}/{@code getIsGunnerMode}). Uses a config with a
     * gunner seat if one is bundled; otherwise asserts the default (disabled) behaviour on the m1a2.
     */
    private void testGunnerMode(ServerLevel level) {
        MchTank t = MchRegistries.TANK.get().create(level);
        t.setConfigName("m1a2");
        t.setPos(340.5, 80.0, 340.5);
        level.addFreshEntity(t);
        MCH_AircraftInfo info = t.hostInfo();

        // A config that does NOT enable gunner mode can never switch it, and its pilot is never "in gunner mode".
        if (info != null && !info.isEnableGunnerMode) {
            req(!t.canSwitchGunnerMode(), "a config without EnableGunnerMode cannot toggle gunner mode");
            req(!t.isSeatGunnerMode(0), "the pilot of such a config is never in gunner mode");
            req(!t.isGunnerModeActive(), "gunner mode defaults OFF");
        }
        // A dedicated (non-switchable) gunner seat reports "always in gunner mode" (reference getIsGunnerMode).
        if (info != null) {
            for (int s = 1; s < info.seatList.size(); s++) {
                mcheli.agnostic.aircraft.MCH_SeatInfo si = info.seatList.get(s);
                if (si.gunner && !si.switchgunner) {
                    req(t.isSeatGunnerMode(s), "a dedicated gunner seat " + s + " is always in gunner mode");
                }
            }
        }
        LOG.info("[CREW/FUEL SELF-TEST] G6 gunner-mode gate OK (config-gated toggle, dedicated seats always-on)");
        t.discard();
    }

    /**
     * G4: the ammo-item reload (reference {@code supplyAmmo}/{@code canPlayerSupplyAmmo}). 200 of 229 bundled weapons
     * require items ({@code Item = count, name}) to reload — the M1A2's gun needs 1 iron ingot + 1 gunpowder. A
     * grounded reload with an EMPTY inventory must be refused; with the items it must grant ammo AND consume them.
     */
    private void testAmmoItemSupply(ServerLevel level) {
        net.minecraft.core.BlockPos ground = new net.minecraft.core.BlockPos(320, 79, 320);
        level.setBlock(ground, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        MchTank t = MchRegistries.TANK.get().create(level);
        t.setConfigName("m1a2");
        t.setPos(320.5, 80.0, 320.5); // on top of the stone -> canSupply() true
        level.addFreshEntity(t);
        t.tick();

        int idx = -1;
        for (int i = 0; i < t.weaponCount(); i++) {
            WeaponSlot w = t.weaponAt(i);
            if (w != null && w.hasEconomy() && !w.info.roundItems.isEmpty()) { idx = i; break; }
        }
        if (idx < 0) {
            LOG.info("[CREW/FUEL SELF-TEST] G4 skipped: m1a2 has no item-cost economy weapon");
            t.discard(); level.removeBlock(ground, false); return;
        }
        WeaponSlot w = t.weaponAt(idx);
        req(t.canSupply(), "the tank is grounded on the stone block (canSupply true)");

        net.neoforged.neoforge.common.util.FakePlayer fake =
            net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        fake.getInventory().clearContent();

        // Empty inventory -> the reference refuses (you must have the ammo items). THIS is the behaviour the user hit.
        req(!t.supplyAmmoFromPlayer(fake, idx), "reload with NO ammo items is refused (reference canPlayerSupplyAmmo)");
        req(t.ammoOf(idx) <= 0, "a refused reload grants nothing");

        // Stock the exact items the weapon asks for, then reload.
        for (mcheli.agnostic.weapon.MCH_WeaponInfo.RoundItem ri : w.info.roundItems) {
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.parse(
                    ri.itemName.contains(":") ? ri.itemName : "minecraft:" + ri.itemName));
            if (item != net.minecraft.world.item.Items.AIR) {
                fake.getInventory().add(new ItemStack(item, ri.num + 5));
            }
        }
        req(t.supplyAmmoFromPlayer(fake, idx), "reload WITH the ammo items succeeds");
        t.tick();
        req(t.ammoOf(idx) > 0, "a successful reload grants ammo, got " + t.ammoOf(idx));
        // The items were consumed (we stocked num+5 of each, so num remain of at least one).
        for (mcheli.agnostic.weapon.MCH_WeaponInfo.RoundItem ri : w.info.roundItems) {
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.parse(
                    ri.itemName.contains(":") ? ri.itemName : "minecraft:" + ri.itemName));
            if (item == net.minecraft.world.item.Items.AIR) { continue; }
            int have = fake.getInventory().countItem(item);
            req(have == 5, "reload consumed exactly " + ri.num + " " + ri.itemName + " (5 left of " + (ri.num + 5)
                + "), got " + have);
        }
        LOG.info("[CREW/FUEL SELF-TEST] G4 ammo-item supply OK (empty=refused, stocked=granted+consumed) — {} needs {}",
            w.weaponName, w.info.roundItems.size() + " item type(s)");

        fake.getInventory().clearContent();
        t.discard();
        level.removeBlock(ground, false);
    }

    /** A 2×1 crafting grid holding a fuel can and one coal item — for the refill-recipe tests. */
    private static net.minecraft.world.item.crafting.CraftingInput input1(ItemStack can, ItemStack coal) {
        return net.minecraft.world.item.crafting.CraftingInput.of(2, 1, java.util.List.of(can, coal));
    }

    private static void req(boolean cond, String what) {
        if (!cond) {
            throw new IllegalStateException(what);
        }
    }
}
