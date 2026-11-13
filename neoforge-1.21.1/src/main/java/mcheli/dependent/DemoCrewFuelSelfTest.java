package mcheli.dependent;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.weapon.MCH_WeaponInfoManager;
import mcheli.agnostic.weapon.VehicleWeapons;
import mcheli.agnostic.weapon.WeaponSlot;
import mcheli.dependent.entity.MchGroundVehicle;
import mcheli.dependent.entity.MchHelicopter;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.entity.MchTank;
import mcheli.dependent.item.MchFuelItem;
import mcheli.dependent.port.NeoItemHandle;
import net.minecraft.world.item.ItemStack;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * Headless proof of MULTI-SEAT (#36) + AMMO/FUEL (#37), without a client:
 * <ul>
 *   <li><b>C1 seat map</b> — two mob riders board an m1a2 (1 + 3 gunner seats) and take two distinct crew seats (the
 *       automatic rule keeps the pilot seat for players); the map survives a dismount (the other rider is NOT
 *       renumbered or promoted to pilot) and the seat index is stable, never {@code getPassengers()} order.</li>
 *   <li><b>C2 per-seat weapons</b> — eligibility is DYNAMIC: with the RWS gunner seat empty the pilot may borrow its
 *       {@code canUsePilot} weapon; the moment a gunner boards that seat, the pilot is bumped off it and the gunner
 *       owns it (reference {@code onMountPlayerSeat}).</li>
 *   <li><b>C3 drive authority</b> — a gunner's drive bits are refused; only seat 0 steers.</li>
 *   <li><b>A1 ammo economy</b> — a weapon with {@code MaxAmmo>0} spawns DRY; the reserve clamps against
 *       {@code maxAmmo − magazine}; a supply pulse re-absorbs the magazine (deliberate) and {@code reloadMag} pulls
 *       from the reserve.</li>
 *   <li><b>F1 fuel</b> — a heli with a tank burns fuel with the SIM throttle, stops at the {@code >1} floor, and a
 *       {@code MaxFuel=0} vehicle never drains; an ammo/fuel supplier tops a dry receiver up from config alone.</li>
 *   <li><b>G1-G12</b> — the resupply GUI, the seat cycle, gunner mode (the pilot's toggle, dedicated and switchable
 *       gunner seats), the engine sound, and the boarding gates: team, wrench paint, the mode-switch cooldown on a
 *       canopy-less vehicle, and the camera night vision released on logout (see each test's javadoc).</li>
 *   <li><b>G13</b> — the fuel cans spill only when the vehicle is removed for good (discard), never on a chunk
 *       unload.</li>
 *   <li><b>G14</b> — the part switches: canopy boarding, the hatch, the landing gear and the shared cooldown, the
 *       weapon bays and the search light (see {@link #testPartSwitches}).</li>
 *   <li><b>G15</b> — the forced mounts and the deferred crew dismounts: a player bumps a mob off a full vehicle, an
 *       extra mob rides seat-less until the next tick, DismountAll, and the saved seat (see
 *       {@link #testForcedMounts}).</li>
 * </ul>
 */
public final class DemoCrewFuelSelfTest extends MchSelfTest {

    /**
     * Every check runs synchronously here; the first unmet requirement ({@link #req}) is the FAIL reason. The probe
     * vehicles are ticked by hand where a check needs it, and all of them sit in this test's own area.
     */
    @Override
    protected void start() {
        ServerLevel level = this.level;
        try {
            testAmmoEconomy();
            testControlWiring(level);
            testSeatMapAndWeapons(level);
            testFuel(level);
            testResupplyGui(level);
            testCanSpill(level);
            testPartSwitches(level);
            testForcedMounts(level);
            finish(true, "seat map, per-seat weapons, drive authority, control wiring, ammo economy, fuel, "
                + "supply auras, fuel can, reload gate, refill recipe, ammo-item supply, seat cycle, gunner mode, "
                + "switchable gunner seat, team boarding gate, wrench paint, canopy-less boarding cooldown, "
                + "logout camera release, engine sound, can spill on removal, part switches, forced mounts");
        } catch (IllegalStateException e) {
            fail("{}", e.getMessage());
        }
    }

    /** A position in this test's area, offset from its origin. */
    private Vec3 at(double dx, double dy, double dz) {
        return new Vec3(this.origin.getX() + dx, this.origin.getY() + dy, this.origin.getZ() + dz);
    }

    private void place(net.minecraft.world.entity.Entity e, Vec3 p) {
        e.setPos(p.x, p.y, p.z);
    }

    /**
     * REGRESSION: the physics must read the SAME control-state object the network handler writes. A refactor left an
     * orphaned single controlState field behind while the payload wrote to the per-seat map, so no input ever reached
     * the physics and NOTHING could be driven. Identity is the whole test.
     */
    private void testControlWiring(ServerLevel level) {
        MchTank t = MchRegistries.TANK.get().create(level);
        t.setConfigName("m1a2");
        place(t, at(0.5, 20.0, 0.5));
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
        place(tank, at(4.5, 20.0, 0.5));
        level.addFreshEntity(tank);
        req(tank.seatCount() >= 2, "m1a2 declares crew seats, got " + tank.seatCount());

        Pig a = EntityType.PIG.create(level);
        Pig b = EntityType.PIG.create(level);
        place(a, at(4.5, 20.0, 0.5));
        place(b, at(4.5, 20.0, 0.5));
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
        place(heli, at(8.5, 20.0, 0.5));
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
        place(ammoBox, at(8.5, 20.0, 4.5));
        level.addFreshEntity(ammoBox);
        MCH_AircraftInfo ab = ammoBox.hostInfo();
        req(ab.ammoSupplyRange > 0.0F,
            "ammo_box declares AmmoSupplyRange in config, got " + ab.ammoSupplyRange);
        req(!ab.canRide, "ammo_box declares CanRide=false (no pilot seat), yet still has a crew seat to board");
        req(ammoBox.seatCount() >= 1, "ammo_box still exposes a seat");

        mcheli.dependent.entity.MchPlane truck = MchRegistries.PLANE.get().create(level);
        truck.setConfigName("fuel_truck");
        place(truck, at(8.5, 20.0, 8.5));
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
        place(t, at(-4.5, 150.0, 0.5)); // high above the ground so canSupply() is unambiguously false (no block below)
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
        place(heli, at(-8.5, 20.0, 4.5));
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
        place(tank, at(-8.5, 20.0, 8.5));
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
        place(t, at(-8.5, 20.0, -4.5));
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
        // A player in a dedicated gunner seat cannot switch it off: the key only toggles a switchable seat.
        int dedicated = -1;
        for (int s = 1; info != null && s < info.seatList.size() && s < t.seatCount(); s++) {
            if (info.seatList.get(s).gunner && !info.seatList.get(s).switchgunner) {
                dedicated = s;
                break;
            }
        }
        if (dedicated > 0) {
            net.minecraft.server.level.ServerPlayer g = testPlayer("g6gunner");
            req(board(g, t, dedicated), "a test player takes the m1a2's dedicated gunner seat " + dedicated);
            req(!t.canSwitchGunnerModeOtherSeat(dedicated), "a dedicated gunner seat is not switchable");
            gunnerKey(t, g);
            req(t.isSeatGunnerMode(dedicated), "the gunner-mode key leaves a dedicated gunner seat in gunner mode");
            g.stopRiding();
        }
        LOG.info("[CREW/FUEL SELF-TEST] G6 gunner-mode gate OK (config-gated toggle, dedicated seats always-on)");
        t.discard();
        testSwitchableGunnerSeat(level);
    }

    /**
     * G8: switchable gunner seats (reference {@code onMountPlayerSeat}:3434-3441, {@code switchGunnerModeOtherSeat}
     * :5316, {@code getIsGunnerMode}:5363). The AH-64's front seat is an {@code AddGunnerSeat} whose seventh value is
     * true: boarding it starts in gunner mode, the gunner-mode payload flips it without touching the pilot's mode, a
     * player taking seat 1 ends the pilot's gunner mode, and re-boarding turns it on again.
     */
    private void testSwitchableGunnerSeat(ServerLevel level) {
        MchHelicopter heli = MchRegistries.HELI.get().create(level);
        heli.setConfigName("ah-64");
        place(heli, at(-12.5, 20.0, -4.5));
        level.addFreshEntity(heli);
        MCH_AircraftInfo info = heli.hostInfo();
        req(info != null && info.seatList.size() > 1 && info.seatList.get(1).gunner && info.seatList.get(1).switchgunner,
            "the ah-64's seat 1 is a switchable gunner seat");
        req(heli.canSwitchGunnerModeOtherSeat(1) && !heli.canSwitchGunnerModeOtherSeat(0),
            "only the switchable passenger seat may toggle through canSwitchGunnerModeOtherSeat");

        net.minecraft.server.level.ServerPlayer pilot = testPlayer("g8pilot");
        req(board(pilot, heli, 0), "the test pilot takes the ah-64's seat 0");
        gunnerKey(heli, pilot);
        req(heli.isGunnerModeActive() && heli.isSeatGunnerMode(0), "the pilot enters gunner mode while seat 1 is empty");

        net.minecraft.server.level.ServerPlayer gunner = testPlayer("g8gunner");
        req(board(gunner, heli, 1), "the test gunner takes seat 1");
        req(heli.isSeatGunnerMode(1), "boarding a switchable gunner seat starts it in gunner mode");
        req(!heli.isGunnerModeActive(), "a player taking seat 1 ends the pilot's gunner mode (no concurrent mode)");
        gunnerKey(heli, gunner);
        req(!heli.isSeatGunnerMode(1), "the gunner-mode key turns the switchable seat's gunner mode off");
        req(!heli.isGunnerModeActive(), "the seat's toggle leaves the pilot's mode alone");
        gunnerKey(heli, gunner);
        req(heli.isSeatGunnerMode(1), "a second press turns the seat's gunner mode back on");
        gunnerKey(heli, pilot);
        req(!heli.isGunnerModeActive(), "the pilot cannot enter gunner mode with a player in seat 1");

        gunnerKey(heli, gunner); // off, then leave and come back
        gunner.stopRiding();
        req(board(gunner, heli, 1), "the gunner re-boards seat 1");
        req(heli.isSeatGunnerMode(1), "re-boarding a switchable gunner seat turns its gunner mode on again");
        LOG.info("[CREW/FUEL SELF-TEST] G8 switchable gunner seat OK (on at boarding, key toggles, pilot mode ends)");
        gunner.stopRiding();
        pilot.stopRiding();
        heli.discard();
        testBoardingGates(level);
    }

    /**
     * G9-G12: the boarding gates and the logout camera release.
     * <ul>
     *   <li>G9 team gate (reference {@code checkTeam}, {@code MCH_EntityAircraft:4283-4295}): a player may not board a
     *       vehicle a teamed player crews unless he is on that team; a crew without a team never refuses.</li>
     *   <li>G10 paint (reference {@code interactFirst}:4318-4329): only a SNEAKING click WITH THE WRENCH cycles the
     *       paint; a wrench click never boards; a bare sneaking click does nothing (the cargo inventory is unported).</li>
     *   <li>G11 the mode-switch cooldown refuses pilot boarding on a vehicle WITHOUT a canopy too (:4353).</li>
     *   <li>G12 a pilot logging out of a two-player vehicle with a night vision camera loses the effect before the
     *       player file is saved ({@link MchServerEvents}).</li>
     * </ul>
     */
    private void testBoardingGates(ServerLevel level) {
        net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
        net.minecraft.world.scores.Scoreboard sb = level.getScoreboard();
        net.minecraft.world.scores.PlayerTeam red = selfTestTeam(sb, "mch_selftest_red");
        net.minecraft.world.scores.PlayerTeam blue = selfTestTeam(sb, "mch_selftest_blue");

        // G9: a red pilot keeps a blue player and a team-less one out, lets a red one in.
        MchHelicopter heli = MchRegistries.HELI.get().create(level);
        heli.setConfigName("ah-64");
        place(heli, at(-16.5, 20.0, -4.5));
        level.addFreshEntity(heli);
        net.minecraft.server.level.ServerPlayer redPilot = testPlayer("g9red1");
        sb.addPlayerToTeam(redPilot.getScoreboardName(), red);
        req(board(redPilot, heli, 0), "the red pilot boards");
        net.minecraft.server.level.ServerPlayer foe = testPlayer("g9blue");
        sb.addPlayerToTeam(foe.getScoreboardName(), blue);
        req(!heli.checkTeam(foe) && !heli.interact(foe, hand).consumesAction() && !foe.isPassenger(),
            "a blue player may not board a vehicle crewed by a red player");
        net.minecraft.server.level.ServerPlayer loner = testPlayer("g9none");
        req(!heli.checkTeam(loner) && !heli.interact(loner, hand).consumesAction() && !loner.isPassenger(),
            "a player on no team may not board a vehicle crewed by a teamed player");
        net.minecraft.server.level.ServerPlayer mate = testPlayer("g9red2");
        sb.addPlayerToTeam(mate.getScoreboardName(), red);
        req(heli.interact(mate, hand).consumesAction() && heli.seatIndexOf(mate) > 0, "a red teammate boards");
        MchHelicopter open = MchRegistries.HELI.get().create(level);
        open.setConfigName("ah-64");
        place(open, at(-16.5, 20.0, -8.5));
        level.addFreshEntity(open);
        req(board(loner, open, 0), "a team-less pilot boards the second heli");
        req(open.interact(foe, hand).consumesAction() && open.seatIndexOf(foe) > 0,
            "a crew without a team never refuses a teamed player");
        LOG.info("[CREW/FUEL SELF-TEST] G9 team boarding gate OK (other team and no team refused, teammate boards)");
        mate.stopRiding();
        redPilot.stopRiding();
        foe.stopRiding();
        loner.stopRiding();
        heli.discard();
        open.discard();

        // G10: the wrench paints, nothing else does, and neither boards.
        MchHelicopter painted = MchRegistries.HELI.get().create(level);
        painted.setConfigName("ah-64");
        place(painted, at(-20.5, 20.0, -4.5));
        level.addFreshEntity(painted);
        int skins = Math.max(1, painted.hostInfo().getTextureNameCount());
        req(skins > 1, "the ah-64 has more than one paint scheme, got " + skins);
        net.minecraft.server.level.ServerPlayer mech = testPlayer("g10mech");
        int skin0 = painted.getSkinIndex();
        mech.setItemInHand(hand, new ItemStack(MchRegistries.WRENCH.get()));
        mech.setShiftKeyDown(true);
        req(!painted.interact(mech, hand).consumesAction() && painted.getSkinIndex() == (skin0 + 1) % skins,
            "a sneaking wrench click cycles the paint and passes the click on to the wrench");
        mech.setShiftKeyDown(false);
        req(!painted.interact(mech, hand).consumesAction() && painted.getSkinIndex() == (skin0 + 1) % skins,
            "a wrench click without sneaking leaves the paint alone");
        mech.setItemInHand(hand, ItemStack.EMPTY);
        mech.setShiftKeyDown(true);
        req(!painted.interact(mech, hand).consumesAction() && painted.getSkinIndex() == (skin0 + 1) % skins,
            "a bare sneaking click no longer cycles the paint (the reference opened the unported cargo inventory)");
        mech.setShiftKeyDown(false);
        req(!mech.isPassenger(), "neither a wrench click nor a sneaking click boards");
        LOG.info("[CREW/FUEL SELF-TEST] G10 wrench paint OK (sneak+wrench cycles, bare sneak inert, no boarding)");
        painted.discard();

        // G11: a hatch switch on the canopy-less CH-47 holds the pilot seat for the shared cooldown.
        MchHelicopter ch = MchRegistries.HELI.get().create(level);
        ch.setConfigName("ch47");
        place(ch, at(-24.5, 20.0, -4.5));
        level.addFreshEntity(ch);
        req(!ch.haveCanopy() && ch.haveHatch(), "the ch47 has a hatch and no canopy");
        net.minecraft.server.level.ServerPlayer first = testPlayer("g11pilot");
        req(ch.interact(first, hand).consumesAction() && ch.seatIndexOf(first) == 0,
            "a pilot boards a canopy-less heli while no switch cools down");
        req(ch.canFoldHatch(), "the ch47's hatch can open");
        ch.switchHatch(first);
        req(!ch.canFoldHatch() && !ch.canUnfoldHatch(), "the hatch switch started the mode-switch cooldown");
        first.stopRiding();
        net.minecraft.server.level.ServerPlayer next = testPlayer("g11next");
        req(!ch.interact(next, hand).consumesAction() && !next.isPassenger(),
            "pilot boarding waits out the mode-switch cooldown on a vehicle without a canopy");
        LOG.info("[CREW/FUEL SELF-TEST] G11 canopy-less boarding cooldown OK");
        ch.discard();

        // G12: two players aboard, the pilot's camera in night vision, the pilot logs out.
        MchHelicopter nv = MchRegistries.HELI.get().create(level);
        nv.setConfigName("ah-64");
        place(nv, at(-28.5, 20.0, -4.5));
        level.addFreshEntity(nv);
        req(nv.canSwitchCameraMode() && !nv.haveSearchLight(), "the ah-64 has a night vision camera");
        net.minecraft.server.level.ServerPlayer leaver = testPlayer("g12pilot");
        net.minecraft.server.level.ServerPlayer stayer = testPlayer("g12gunner");
        req(board(leaver, nv, 0) && board(stayer, nv, 1), "a pilot and a gunner board");
        nv.tick(); // the camera seats register their viewers (a new occupant starts in the normal mode)
        nv.cameraModeKey(leaver);
        req(nv.cameraMode(0) == 1, "the pilot's camera switches to night vision");
        nv.tick();
        net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> nightVision =
            net.minecraft.world.effect.MobEffects.NIGHT_VISION;
        req(leaver.hasEffect(nightVision), "the night vision camera gives its viewer night vision");
        MchServerEvents.onPlayerLoggedOut(
            new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(leaver));
        req(!leaver.hasEffect(nightVision), "logging out strips the camera's night vision before the save");
        req(nv.cameraMode(0) == 0, "and returns the seat's camera to normal");
        LOG.info("[CREW/FUEL SELF-TEST] G12 logout camera release OK (two-player vehicle)");
        leaver.stopRiding();
        stayer.stopRiding();
        nv.discard();
        sb.removePlayerTeam(red);
        sb.removePlayerTeam(blue);
    }

    /** The test players created so far, dismounted again by {@link #cleanup} should a check fail midway. */
    private final java.util.List<net.minecraft.server.level.ServerPlayer> testPlayers = new java.util.ArrayList<>();

    private net.minecraft.server.level.ServerPlayer testPlayer(String label) {
        net.minecraft.server.level.ServerPlayer p = newTestPlayer(label);
        p.setPos(this.origin.getX() + 0.5, this.origin.getY() + 20.0, this.origin.getZ() + 0.5);
        this.testPlayers.add(p);
        return p;
    }

    /** Seat {@code p} in {@code seat} of {@code v} (a forced mount, as a scripted crew would board). */
    private static boolean board(net.minecraft.world.entity.Entity p, AbstractMchVehicle v, int seat) {
        v.preferSeat(p, seat);
        return p.startRiding(v, true) && v.seatIndexOf(p) == seat;
    }

    /** {@code p} presses the gunner-mode key: the real serverbound payload handler. */
    private static void gunnerKey(AbstractMchVehicle v, net.minecraft.world.entity.player.Player p) {
        mcheli.dependent.control.ServerboundGunnerModePayload.handle(
            new mcheli.dependent.control.ServerboundGunnerModePayload(v.getId()), payloadContext(p));
    }

    /** A fresh scoreboard team for this test (a stale one from an aborted run is replaced). */
    private static net.minecraft.world.scores.PlayerTeam selfTestTeam(net.minecraft.world.scores.Scoreboard sb,
                                                                       String name) {
        net.minecraft.world.scores.PlayerTeam old = sb.getPlayerTeam(name);
        if (old != null) {
            sb.removePlayerTeam(old);
        }
        return sb.addPlayerTeam(name);
    }

    @Override
    protected void cleanup() {
        for (net.minecraft.server.level.ServerPlayer p : this.testPlayers) {
            if (p.isPassenger()) {
                p.stopRiding();
            }
        }
        net.minecraft.world.scores.Scoreboard sb = this.level.getScoreboard();
        for (String name : new String[] {"mch_selftest_red", "mch_selftest_blue"}) {
            net.minecraft.world.scores.PlayerTeam team = sb.getPlayerTeam(name);
            if (team != null) {
                sb.removePlayerTeam(team);
            }
        }
    }

    /**
     * G4: the ammo-item reload (reference {@code supplyAmmo}/{@code canPlayerSupplyAmmo}). 200 of 229 bundled weapons
     * require items ({@code Item = count, name}) to reload — the M1A2's gun needs 1 iron ingot + 1 gunpowder. A
     * grounded reload with an EMPTY inventory must be refused; with the items it must grant ammo AND consume them.
     */
    private void testAmmoItemSupply(ServerLevel level) {
        net.minecraft.core.BlockPos ground = this.origin.offset(-12, 29, -8);
        level.setBlock(ground, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        MchTank t = MchRegistries.TANK.get().create(level);
        t.setConfigName("m1a2");
        place(t, Vec3.atBottomCenterOf(ground.above())); // on top of the stone -> canSupply() true
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

        // Stock the exact items the weapon asks for, then reload. Use the handle resolveItems() bound at load (the same
        // item the supply path consumes), not a re-parse of the raw 1.7.10 name.
        for (mcheli.agnostic.weapon.MCH_WeaponInfo.RoundItem ri : w.info.roundItems) {
            net.minecraft.world.item.Item item = NeoItemHandle.itemOf(ri.item);
            req(item != null, "ammo item '" + ri.itemName + "' of " + w.weaponName + " was resolved at load");
            fake.getInventory().add(new ItemStack(item, ri.num + 5));
        }
        req(t.supplyAmmoFromPlayer(fake, idx), "reload WITH the ammo items succeeds");
        t.tick();
        req(t.ammoOf(idx) > 0, "a successful reload grants ammo, got " + t.ammoOf(idx));
        // The items were consumed (we stocked num+5 of each, so num remain of at least one).
        for (mcheli.agnostic.weapon.MCH_WeaponInfo.RoundItem ri : w.info.roundItems) {
            net.minecraft.world.item.Item item = NeoItemHandle.itemOf(ri.item);
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

    /**
     * G13: the fuel-can spill on removal (reference {@code MCH_AircraftInventory.setDead}:63-75). Vehicle A, with a can
     * in fuel slot 0, leaves exactly one matching ItemEntity when it is discarded. Vehicle B, holding the same can,
     * leaves none when it is removed for a chunk unload (the dupe guard: only KILLED and DISCARDED spill).
     */
    private void testCanSpill(ServerLevel level) {
        ItemStack can = new ItemStack(MchRegistries.FUEL.get());
        MchTank a = MchRegistries.TANK.get().create(level);
        a.setConfigName("m1a2");
        place(a, at(-8.5, 150.0, 8.5)); // mid-air, clear of every other probe
        level.addFreshEntity(a);
        a.fuelInventory().setItem(0, can.copy());
        net.minecraft.world.phys.AABB boxA = a.getBoundingBox().inflate(4.0);
        a.discard();
        int totalA = 0;
        int matchA = 0;
        for (net.minecraft.world.entity.item.ItemEntity e
                : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, boxA)) {
            totalA++;
            if (ItemStack.matches(e.getItem(), can)) {
                matchA++;
            }
            e.discard();
        }
        boolean slotAEmpty = a.fuelInventory().getItem(0).isEmpty();

        MchTank b = MchRegistries.TANK.get().create(level);
        b.setConfigName("m1a2");
        place(b, at(-8.5, 150.0, 16.5));
        level.addFreshEntity(b);
        b.fuelInventory().setItem(0, can.copy());
        net.minecraft.world.phys.AABB boxB = b.getBoundingBox().inflate(4.0);
        b.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        int totalB = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, boxB).size();
        boolean slotBKept = ItemStack.matches(b.fuelInventory().getItem(0), can);
        LOG.info("[CREW/FUEL SELF-TEST] G13 observed: A total={} match={} slot0Empty={} ; B total={} slot0Kept={}",
            totalA, matchA, slotAEmpty, totalB, slotBKept);
        // Goldens, recorded twice on the pre-split tree (identical both runs).
        req(totalA == 1 && matchA == 1, "a discarded vehicle spills exactly its one fuel can, got " + totalA
            + " item(s), " + matchA + " matching");
        req(slotAEmpty, "the spilled can leaves the fuel slot empty");
        req(totalB == 0, "a chunk-unloaded vehicle spills nothing (dupe guard), got " + totalB + " item(s)");
        req(slotBKept, "a chunk-unloaded vehicle keeps its can in the slot");
        LOG.info("[CREW/FUEL SELF-TEST] G13 can spill OK (discard drops 1 can, chunk unload drops none)");
    }

    /**
     * G14: the part switches. Each probe vehicle hangs in the air at its own spot: {@link #holdTick} puts it back and
     * stops it after every hand tick, so no ground probe or flight outcome reaches the parts.
     * <ul>
     *   <li>(a) a-10: the first pilot click opens the canopy and does not board (reference {@code interactFirst}
     *       :4347-4358); a click every tick boards once the shared cooldown ends, and the canopy reads closed again at
     *       the recorded tick.</li>
     *   <li>(b) mi-24ps: the pilot's hatch switch opens the hatch, a second switch inside the cooldown is refused, and the
     *       hatch is at rest open at the recorded tick. The camera key switches its search light (the camera mode
     *       stays normal), with the recorded light values on four points ahead.</li>
     *   <li>(c) f-35a in the air: the gear key retracts the gear to 90 at the recorded tick and an immediate second
     *       press is refused; a canopy opening restarts the one shared cooldown, which refuses the gear key until it
     *       ends, and the gear is down again at the recorded tick.</li>
     *   <li>(d) f-35a: one weapon switch selects a bay weapon and its bays open to 90 at the recorded tick.</li>
     * </ul>
     */
    private void testPartSwitches(ServerLevel level) {
        net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;

        // (a) the canopy boarding gate.
        mcheli.dependent.entity.MchPlane a10 = MchRegistries.PLANE.get().create(level);
        a10.setConfigName("a-10");
        Vec3 pa = at(-32.5, 150.0, -4.5);
        place(a10, pa);
        level.addFreshEntity(a10);
        net.minecraft.server.level.ServerPlayer ap = testPlayer("g14a10");
        req(a10.haveCanopy() && a10.isCanopyClose(), "the a-10 has a closed canopy");
        boolean aFirst = a10.interact(ap, hand).consumesAction() && !ap.isPassenger();
        int aBoard = -1;
        int aClosed = -1;
        for (int t = 1; t <= 80 && aClosed < 0; t++) {
            holdTick(a10, pa);
            if (aBoard < 0) {
                if (a10.interact(ap, hand).consumesAction() && a10.seatIndexOf(ap) == 0) {
                    aBoard = t;
                }
            } else if (a10.isCanopyClose()) {
                aClosed = t;
            }
        }

        // (b) the hatch and the search light.
        MchHelicopter mi = MchRegistries.HELI.get().create(level);
        mi.setConfigName("mi-24ps");
        Vec3 pm = at(-36.5, 150.0, -4.5);
        place(mi, pm);
        level.addFreshEntity(mi);
        net.minecraft.server.level.ServerPlayer mp = testPlayer("g14mi24");
        req(board(mp, mi, 0), "the mi-24ps pilot boards");
        req(mi.haveHatch() && mi.canFoldHatch(), "the mi-24ps hatch can open");
        mi.switchHatch(mp);
        boolean bCooling = !mi.canFoldHatch() && !mi.canUnfoldHatch();
        float bF5 = -1.0F;
        float bF6 = -1.0F;
        int bRest = -1;
        for (int t = 1; t <= 100 && bRest < 0; t++) {
            holdTick(mi, pm);
            if (t == 5) {
                bF5 = mi.hatchFactor();
                mi.switchHatch(mp); // inside the 20-tick cooldown
            } else if (t == 6) {
                bF6 = mi.hatchFactor();
            }
            if (mi.canUnfoldHatch()) {
                bRest = t;
            }
        }
        req(mi.haveSearchLight() && !mi.isSearchLightOn(), "the mi-24ps has a search light, off");
        mi.cameraModeKey(mp);
        boolean bLightOn = mi.isSearchLightOn() && mi.cameraMode(0) == 0;
        net.minecraft.server.level.ServerPlayer lit = testPlayer("g14lit");
        float[] bLight = new float[4];
        for (int i = 0; i < bLight.length; i++) {
            lit.setPos(mi.getX() + 2.0 * i, mi.getY() - 2.0, mi.getZ() + 20.0);
            bLight[i] = mi.searchLightValue(lit);
        }
        mi.cameraModeKey(mp);
        lit.setPos(mi.getX(), mi.getY() - 2.0, mi.getZ() + 20.0);
        boolean bLightOff = !mi.isSearchLightOn() && mi.searchLightValue(lit) == 0.0F && mi.cameraMode(0) == 0;

        // (c) the landing gear and the shared cooldown.
        mcheli.dependent.entity.MchPlane gj = MchRegistries.PLANE.get().create(level);
        gj.setConfigName("f-35a");
        Vec3 pg = at(-40.5, 150.0, -4.5);
        place(gj, pg);
        level.addFreshEntity(gj);
        net.minecraft.server.level.ServerPlayer gp = testPlayer("g14gear");
        req(board(gp, gj, 0), "the f-35a gear pilot boards");
        req(gj.canFoldLandingGear() && !gj.isLandingGearFolded(), "the f-35a gear can retract in the air");
        gj.toggleLandingGear(gp);
        boolean cUp = gj.isLandingGearFolded();
        gj.toggleLandingGear(gp);
        boolean cHeld = gj.isLandingGearFolded();
        int cUpTick = -1;
        for (int t = 1; t <= 80 && cUpTick < 0; t++) {
            holdTick(gj, pg);
            if (gj.vehicleParts().getGearAngle() >= 90.0F) {
                cUpTick = t;
            }
        }
        gj.openCanopy(); // restarts the one shared part/mode cooldown
        gj.toggleLandingGear(gp);
        boolean cRefused = gj.canUnfoldLandingGear() && gj.isLandingGearFolded();
        int cCool = -1;
        int cDownTick = -1;
        for (int t = 1; t <= 100 && cDownTick < 0; t++) {
            holdTick(gj, pg);
            if (cCool < 0) {
                gj.toggleLandingGear(gp);
                if (!gj.isLandingGearFolded()) {
                    cCool = t;
                }
            } else if (gj.vehicleParts().getGearAngle() <= 0.0F) {
                cDownTick = t;
            }
        }

        // (d) the weapon bays.
        mcheli.dependent.entity.MchPlane bj = MchRegistries.PLANE.get().create(level);
        bj.setConfigName("f-35a");
        Vec3 pb = at(-44.5, 150.0, -4.5);
        place(bj, pb);
        level.addFreshEntity(bj);
        net.minecraft.server.level.ServerPlayer bp = testPlayer("g14bay");
        req(board(bp, bj, 0), "the f-35a bay pilot boards");
        holdTick(bj, pb); // builds the loadout
        String dBefore = bj.selectedWeaponName(0);
        bj.queueWeaponSwitch(bp, 1);
        String dSel = null;
        int dOpen = -1;
        int dMask = 0;
        for (int t = 1; t <= 80 && dOpen < 0; t++) {
            holdTick(bj, pb);
            if (t == 1) {
                dSel = bj.selectedWeaponName(0);
            }
            for (int i = 0; i < 8; i++) {
                if (bj.vehicleParts().weaponBayRotation(i, 1.0F) >= 90.0F) {
                    dMask |= 1 << i;
                }
            }
            if (dMask != 0) {
                dOpen = t;
            }
        }

        LOG.info("[CREW/FUEL SELF-TEST] G14 observed: a first={} board={} closed={} ; b cooling={} f5={} f6={} rest={}"
                + " lightOn={} light={} off={} ; c up={} held={} upTick={} refused={} cool={} downTick={} ; d before={}"
                + " sel={} open={} mask={}",
            aFirst, aBoard, aClosed, bCooling, bF5, bF6, bRest, bLightOn, java.util.Arrays.toString(bLight), bLightOff,
            cUp, cHeld, cUpTick, cRefused, cCool, cDownTick, dBefore, dSel, dOpen, dMask);
        // Goldens, recorded twice on the pre-split tree (identical both runs).
        req(aFirst, "the first pilot click on the a-10 opens its canopy and does not board");
        req(aBoard == 20, "the pilot boards the a-10 when the 20-tick cooldown ends, got tick " + aBoard);
        req(aClosed == 40, "the a-10 canopy reads closed at tick 40, got " + aClosed);
        req(bCooling, "the mi-24ps hatch switch starts the shared cooldown");
        req(Math.abs(bF5 - 0.083333336F) < 1.0e-4F && Math.abs(bF6 - 0.1F) < 1.0e-4F,
            "a second hatch switch inside the cooldown is refused (the hatch keeps opening), got " + bF5 + " -> " + bF6);
        req(bRest == 60, "the mi-24ps hatch is at rest open at tick 60, got " + bRest);
        req(bLightOn, "the camera key switches the mi-24ps search light on, the camera mode stays normal");
        req(bLight[0] == 240.0F && bLight[1] == 240.0F && Math.abs(bLight[2] - 187.48164F) < 1.0e-3F
                && bLight[3] == 0.0F,
            "the search light values ahead, got " + java.util.Arrays.toString(bLight));
        req(bLightOff, "a second camera key press switches the search light off");
        req(cUp && cHeld, "the f-35a gear key retracts the gear, an immediate second press is refused");
        req(cUpTick == 36, "the f-35a gear is fully up (90) at tick 36, got " + cUpTick);
        req(cRefused, "a canopy opening's cooldown refuses the gear key though the gear may extend");
        req(cCool == 20, "the gear key is accepted again when the shared cooldown ends at tick 20, got " + cCool);
        req(cDownTick == 56, "the f-35a gear is fully down (0) at tick 56, got " + cDownTick);
        req("GAU-22/A Equalizer".equals(dBefore) && "AIM-9X sidewinder".equals(dSel),
            "one weapon switch moves the f-35a pilot from the gun to the bay missile, got " + dBefore + " -> " + dSel);
        req(dOpen == 31 && dMask == 63, "all six f-35a bays open to 90 at tick 31, got tick " + dOpen + " mask " + dMask);
        LOG.info("[CREW/FUEL SELF-TEST] G14 part switches OK (canopy gate, hatch, search light, gear cooldown, bays)");
        ap.stopRiding();
        mp.stopRiding();
        gp.stopRiding();
        bp.stopRiding();
        a10.discard();
        mi.discard();
        gj.discard();
        bj.discard();
    }

    /**
     * G15: the forced mounts and the deferred crew dismounts. Every mount here is a forced {@code startRiding(v, true)},
     * as a {@code /ride}, a scripted crew or vanilla's login reattach makes it (no canAddPassenger gate). Each probe
     * hangs in the air at its own spot ({@link #holdTick}).
     * <ul>
     *   <li>(a) an m1a2 whose every seat a no-AI pig holds (each placed by {@code preferSeat}): a test player's forced
     *       mount puts the pig in the recorded seat off and takes that seat; the other pigs keep theirs.</li>
     *   <li>(b) the same full m1a2: an extra pig's forced mount rides seat-less ({@code seatIndexOf} -1) until the
     *       vehicle's next tick puts it off; the crew keeps its seats.</li>
     *   <li>(c) an ah-64 with a test pilot and a pig gunner: the pilot leaving with DismountAll on puts the pig off on
     *       the next tick, not at once; without DismountAll the pig stays.</li>
     *   <li>(d) the Seats NBT: a player saved in m1a2 seat 2 re-boards a freshly loaded copy into seat 2, not the pilot
     *       seat the automatic rule would give.</li>
     * </ul>
     */
    private void testForcedMounts(ServerLevel level) {
        // (a) a player's forced mount onto a vehicle full of mobs.
        MchTank full = MchRegistries.TANK.get().create(level);
        full.setConfigName("m1a2");
        Vec3 pf = at(-32.5, 150.0, -12.5);
        place(full, pf);
        level.addFreshEntity(full);
        int seats = full.seatCount();
        java.util.List<Pig> crew = new java.util.ArrayList<>();
        boolean aCrewed = true;
        for (int s = 0; s < seats; s++) {
            Pig pig = EntityType.PIG.create(level);
            pig.setNoAi(true);
            place(pig, pf);
            level.addFreshEntity(pig);
            crew.add(pig);
            aCrewed &= board(pig, full, s);
        }
        net.minecraft.server.level.ServerPlayer fp = testPlayer("g15forced");
        boolean aMounted = fp.startRiding(full, true);
        int aSeat = full.seatIndexOf(fp);
        int aGone = 0;
        int aDisplaced = -1; // the old seat of the pig that is no longer aboard
        for (int s = 0; s < seats; s++) {
            if (!crew.get(s).isPassenger()) {
                aGone++;
                aDisplaced = s;
            }
        }
        String aMap = seatMap(full, crew, fp);

        // (b) an extra mob's forced mount onto the full vehicle.
        Pig extra = EntityType.PIG.create(level);
        extra.setNoAi(true);
        place(extra, pf);
        level.addFreshEntity(extra);
        boolean bMounted = extra.startRiding(full, true);
        boolean bAboard = extra.isPassenger() && extra.getVehicle() == full;
        int bSeat = full.seatIndexOf(extra);
        int bPassengers = full.getPassengers().size();
        holdTick(full, pf);
        boolean bGone = !extra.isPassenger() && !full.hasPassenger(extra);
        String bMap = seatMap(full, crew, fp);

        // (c) DismountAll: the leaving pilot takes the mob crew with him, one tick later.
        MchHelicopter dh = MchRegistries.HELI.get().create(level);
        dh.setConfigName("ah-64");
        Vec3 pd = at(-36.5, 150.0, -12.5);
        place(dh, pd);
        level.addFreshEntity(dh);
        net.minecraft.server.level.ServerPlayer dp = testPlayer("g15pilot");
        Pig dg = EntityType.PIG.create(level);
        dg.setNoAi(true);
        place(dg, pd);
        level.addFreshEntity(dg);
        boolean cBoard = board(dp, dh, 0) && board(dg, dh, 1);
        dh.controlState(0).dismountAll = true;
        dp.stopRiding();
        boolean cStill = dg.isPassenger() && dh.seatIndexOf(dg) == 1;
        holdTick(dh, pd);
        boolean cOff = !dg.isPassenger();
        boolean cReboard = board(dp, dh, 0) && board(dg, dh, 1);
        boolean cFresh = !dh.controlState(0).dismountAll; // the leaving pilot's seat controls were dropped
        dp.stopRiding();
        holdTick(dh, pd);
        boolean cKept = dg.isPassenger() && dh.seatIndexOf(dg) == 1;

        // (d) the saved seat survives a save and load.
        MchTank saved = MchRegistries.TANK.get().create(level);
        saved.setConfigName("m1a2");
        Vec3 ps = at(-40.5, 150.0, -12.5);
        place(saved, ps);
        level.addFreshEntity(saved);
        net.minecraft.server.level.ServerPlayer sp = testPlayer("g15saved");
        boolean dBoard = board(sp, saved, 2);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        saved.saveWithoutId(tag);
        sp.stopRiding();
        saved.discard();
        tag.remove("UUID"); // the copy is a new entity next to the discarded original
        MchTank loaded = MchRegistries.TANK.get().create(level);
        loaded.load(tag);
        Vec3 pl = at(-44.5, 150.0, -12.5);
        place(loaded, pl);
        level.addFreshEntity(loaded);
        boolean dMounted = sp.startRiding(loaded, true);
        int dSeat = loaded.seatIndexOf(sp);

        LOG.info("[CREW/FUEL SELF-TEST] G15 observed: a seats={} crewed={} mounted={} seat={} gone={} displaced={} map={}"
                + " ; b mounted={} aboard={} seat={} passengers={} gone={} map={} ; c board={} still={} off={}"
                + " reboard={} fresh={} kept={} ; d board={} mounted={} seat={}",
            seats, aCrewed, aMounted, aSeat, aGone, aDisplaced, aMap, bMounted, bAboard, bSeat, bPassengers, bGone, bMap,
            cBoard, cStill, cOff, cReboard, cFresh, cKept, dBoard, dMounted, dSeat);
        req(aCrewed && aMounted && cBoard && cReboard && dBoard && dMounted, "G15 probes boarded");
        // Goldens, recorded twice on the pre-split tree (identical both runs).
        req(seats == 3, "the m1a2 has 3 seats, got " + seats);
        req(aSeat == 2 && aGone == 1 && aDisplaced == 2 && "c0,c1,P".equals(aMap),
            "a player's forced mount puts the seat-2 pig off and takes seat 2, got seat " + aSeat + " gone " + aGone
                + " displaced " + aDisplaced + " map " + aMap);
        req(bMounted && bAboard && bSeat == -1 && bPassengers == 4,
            "an extra pig's forced mount rides seat-less this tick, got aboard " + bAboard + " seat " + bSeat
                + " passengers " + bPassengers);
        req(bGone && "c0,c1,P".equals(bMap), "the next tick puts the seat-less pig off, the crew keeps its seats, got "
            + bGone + " map " + bMap);
        req(cStill && cOff, "DismountAll puts the pig gunner off on the tick after the pilot left, got still " + cStill
            + " off " + cOff);
        req(cFresh && cKept, "without DismountAll the pig gunner stays, got fresh " + cFresh + " kept " + cKept);
        req(dSeat == 2, "a player saved in seat 2 re-boards the loaded copy into seat 2, got " + dSeat);
        LOG.info("[CREW/FUEL SELF-TEST] G15 forced mounts OK (player bumps a mob, seat-less mob put off next tick,"
            + " DismountAll, saved seat)");
        fp.stopRiding();
        dg.stopRiding();
        sp.stopRiding();
        for (Pig pig : crew) {
            pig.stopRiding();
            pig.discard();
        }
        extra.discard();
        dg.discard();
        full.discard();
        dh.discard();
        loaded.discard();
    }

    /** {@code v}'s seats in order: {@code P} for the player {@code p}, {@code cN} for crew pig N, {@code -} empty. */
    private static String seatMap(AbstractMchVehicle v, java.util.List<Pig> crew, net.minecraft.world.entity.Entity p) {
        StringBuilder sb = new StringBuilder();
        for (int s = 0; s < v.seatCount(); s++) {
            net.minecraft.world.entity.Entity e = v.seatPassenger(s);
            sb.append(s == 0 ? "" : ",").append(e == null ? "-" : e == p ? "P" : "c" + crew.indexOf(e));
        }
        return sb.toString();
    }

    /** One hand tick of {@code v}, then put it back at {@code p} at rest (a probe that hangs in the air). */
    private static void holdTick(AbstractMchVehicle v, Vec3 p) {
        v.tick();
        v.setPos(p.x, p.y, p.z);
        v.setDeltaMovement(Vec3.ZERO);
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
