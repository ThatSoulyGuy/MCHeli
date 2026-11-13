package mcheli.dependent.entity;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.aircraft.MCH_SeatInfo;
import mcheli.dependent.control.MchControlState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The crew of one vehicle (#36, #36b): the seat map (the server's UUID map, the client's synced mirror and its
 * allocation-free guess), the per-seat control states, boarding (the interact bodies, the team gate, the clicked and
 * preferred seats), the passenger add and remove bodies (restored seats, the forced-mount vacate and deferred eject,
 * the DismountAll mob dismount), seat switching, the pilot's gunner mode with its throttle save and restore, the
 * switchable gunner-seat bits, and the {@code Seats} NBT. The only writer of {@link AbstractMchVehicle#DATA_SEATS},
 * {@link AbstractMchVehicle#DATA_GUNNER_MODE} and {@link AbstractMchVehicle#DATA_SEAT_GUNNER_MODE}; the wrench paint
 * goes through the entity's {@code cycleSkin()}. The public crew queries and commands, the passenger overrides and the
 * {@code canSwitchGunnerMode} hook stay on the entity as delegates.
 */
final class MchVehicleCrew {

    private final AbstractMchVehicle v;

    /** Per-seat control state: every seat owns its own FIRE/free-look bits, but only seat 0's drive bits are honored
     *  (a gunner must not steer — the reference routes movement from the pilot packet alone). */
    private final java.util.Map<Integer, MchControlState> controlBySeat = new java.util.HashMap<>();

    MchVehicleCrew(AbstractMchVehicle v) {
        this.v = v;
    }

    /** NBT load, at the entity's Seats position: each rider's saved seat, by rider UUID, for its re-boarding. */
    void loadSeats(CompoundTag tag) {
        // Riders re-board AFTER load, so remember each one's seat by UUID and hand it back in addPassenger.
        this.restoredSeats.clear();
        for (net.minecraft.nbt.Tag e : tag.getList("Seats", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag s = (CompoundTag) e;
            this.restoredSeats.put(s.getUUID("Id"), s.getInt("Seat"));
        }
    }

    /** NBT save, at the entity's Seats position: one {@code {Id, Seat}} entry per seated rider. */
    void saveSeats(CompoundTag tag) {
        net.minecraft.nbt.ListTag seats = new net.minecraft.nbt.ListTag();
        for (java.util.Map.Entry<UUID, Integer> e : this.seatByUuid.entrySet()) {
            CompoundTag s = new CompoundTag();
            s.putUUID("Id", e.getKey());
            s.putInt("Seat", e.getValue());
            seats.add(s);
        }
        tag.put("Seats", seats);
    }

    /** SERVER, in the entity's tick before the pilot's controls are read: the crew dismounts deferred out of the
     *  passenger hooks (the passenger list is mid-update there). */
    void tickDeferred() {
        if (this.dismountCrewPending) {
            this.dismountCrewPending = false;
            dismountMobCrew(); // the pilot left with DismountAll on (deferred out of removePassenger)
        }
        if (!this.pendingEjects.isEmpty()) {
            ejectUnseatedRiders(); // a forced mount found no seat (deferred out of addPassenger)
        }
    }

    MchControlState controlState(int seat) {
        return this.controlBySeat.computeIfAbsent(seat, s -> new MchControlState());
    }

    /** Drop a seat's held keys when its occupant leaves (else the vehicle keeps firing/driving on the last packet). */
    void clearSeatControls(int seat) {
        this.controlBySeat.remove(seat);
    }

    /**
     * The reference team boarding gate {@code checkTeam} ({@code MCH_EntityAircraft:4283-4295}), on vanilla scoreboard
     * teams: refused when any PLAYER aboard is on a team and {@code player} is not on that same team (a crew player
     * without a team never refuses; a {@code player} without a team is refused by any teamed crew player). Both sides
     * know the teams, so the client declines the click too. Checked before every interaction, as in the reference
     * ({@code interactFirst}:4314, {@code MCH_EntitySeat.interactFirst}:189).
     */
    boolean checkTeam(Player player) {
        for (Entity e : v.getPassengers()) {
            if (e instanceof Player crew && crew.getTeam() != null && !crew.isAlliedTo(player)) {
                return false;
            }
        }
        return true;
    }

    /** Is {@code player} holding the maintenance wrench in {@code hand} (reference {@code interactFirst}:4318)? */
    private static boolean holdsWrench(Player player, InteractionHand hand) {
        return player.getItemInHand(hand).getItem() instanceof mcheli.dependent.item.MchWrench;
    }

    /** The body of the entity's {@code interact} override: board the first free seat, or the wrench paint. */
    InteractionResult handleInteract(Player player, InteractionHand hand) {
        if (v.isDestroyed()) {
            return InteractionResult.PASS; // a wreck can't be mounted or skinned (reference interactFirst gates on it)
        }
        if (!v.checkTeam(player)) {
            return InteractionResult.PASS; // reference interactFirst:4314 — another team's vehicle
        }
        if (holdsWrench(player, hand)) {
            // Reference interactFirst:4318-4324: the wrench never boards; with sneak it cycles the paint scheme. PASS
            // lets the click go on to the wrench's own use (hold to repair), as the reference's `return false` did.
            if (!v.level().isClientSide && player.isSecondaryUseActive()) {
                v.cycleSkin(); // the next paint scheme, wrapping (the entity owns DATA_SKIN)
            }
            return InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive()) {
            // A bare sneaking click opens the cargo inventory in the reference (interactFirst:4326-4329), which is not
            // ported (DEFERRED.md "Cargo inventory"), so it does nothing here. It never boards either way.
            return InteractionResult.PASS;
        }
        if (v.level().isClientSide) {
            return InteractionResult.SUCCESS; // let the client predict the mount
        }
        if (firstFreeSeatFor(player, -1) == 0) {
            InteractionResult gate = v.vehicleParts().canopyBoardingGate(); // a closed canopy opens before the pilot can climb in
            if (gate != null) {
                return gate;
            }
        }
        return player.startRiding(v) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /**
     * Click a SPECIFIC seat to board it (the reference lets you right-click a seat's own 1×1 box, so a gunner can take
     * their station even on a pilotless craft). The nearest allowed free seat within 1 block of the hit point wins;
     * otherwise this falls through to the entity's {@code interact} and the normal first-free-seat rule. A wrench or
     * sneaking click, or a refused team, falls through too ({@code MCH_EntitySeat.interactFirst}:189-196 runs the team
     * gate, then hands a wrench click to the vehicle). The body of the entity's {@code interactAt} override.
     */
    InteractionResult handleInteractAt(Player player, Vec3 hit, InteractionHand hand) {
        if (v.isDestroyed() || player.isSecondaryUseActive() || holdsWrench(player, hand) || !v.checkTeam(player)) {
            return InteractionResult.PASS;
        }
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (info == null || info.seatList == null) {
            return InteractionResult.PASS;
        }
        if (!v.level().isClientSide) {
            int best = -1;
            double bestD = 1.0; // the reference seat box is 1×1
            Vec3 hitWorld = v.position().add(hit);
            for (int i = 0; i < v.seatCount() && i < info.seatList.size(); i++) {
                if (i == 0 && !info.canRide) {
                    continue; // config CanRide=false (the fuel truck, the ammo box): the pilot seat is NOT boardable —
                              // firstFreeSeatFor honours this, but a clicked seat is taken as PREFERRED and would skip it
                }
                if (!v.canOccupySeat(i)) {
                    continue;
                }
                double d = v.position().add(v.riderPose().seatOffsetWorld(i, player, 1.0F)).distanceTo(hitWorld);
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
            if (best == 0) {
                InteractionResult gate = v.vehicleParts().canopyBoardingGate(); // clicking the pilot seat obeys the canopy too
                if (gate != null) {
                    return gate;
                }
            }
            if (best >= 0) {
                this.pendingSeat.put(player.getUUID(), best);
                if (!player.startRiding(v)) {
                    this.pendingSeat.remove(player.getUUID());
                    return InteractionResult.PASS;
                }
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    // ---- SEAT MAP (#36) — 1.21.1 multi-passenger replaces the reference's per-seat MCH_EntitySeat entities. Seat 0 is
    //      the pilot; the map is server truth, mirrored to clients via DATA_SEATS and persisted by rider UUID. ----

    private final java.util.Map<UUID, Integer> seatByUuid = new java.util.HashMap<>();   // server truth
    private final java.util.Map<UUID, Integer> pendingSeat = new java.util.HashMap<>();  // interactAt -> addPassenger
    private final java.util.Map<UUID, Integer> restoredSeats = new java.util.HashMap<>();// NBT -> addPassenger

    /** How many seats this config declares (rack slots are reserved in the index space but never boardable). */
    int seatCount() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        return info != null ? Math.max(1, info.getNumSeat()) : 1;
    }

    /** The seat {@code p} occupies, or -1. Server reads its own map; the client reads the synced mirror. A -1 on the
     *  client is a tolerated DATA_SEATS-vs-SetPassengers packet race — callers SKIP that rider for the frame. */
    int seatIndexOf(Entity p) {
        if (p == null || !v.hasPassenger(p)) {
            return -1;
        }
        if (!v.level().isClientSide) {
            Integer s = this.seatByUuid.get(p.getUUID());
            return s != null ? s : -1;
        }
        // The hot path (pilot(), seatPassenger() and so every renderer, HUD and camera frame) allocates nothing: one scan
        // of the synced map, returning on the rider's entry.
        CompoundTag t = v.getEntityData().get(AbstractMchVehicle.DATA_SEATS);
        for (String k : t.getAllKeys()) {
            if (t.getInt(k) == p.getId()) {
                try {
                    return Integer.parseInt(k);
                } catch (NumberFormatException ignored) {
                    // not a seat key
                }
            }
        }
        // The synced map has not caught up with this rider yet (DATA_SEATS can trail the passenger list by a packet).
        // GUESS the seat the server's automatic rule most likely gave it: the lowest seat no synced rider holds, with
        // the pilot seat reserved for players (firstFreeSeatFor). Passenger-list order is no seat map (vanilla even
        // moves a player to the front). The guess cannot know a clicked or requested seat, a seat switch or the
        // exclusion groups, so it can be wrong; that is cosmetic and short-lived: the SERVER checks its own map before
        // honouring any input, and the guess is replaced the moment DATA_SEATS lands.
        // (syncSeatTag writes the canonical Integer.toString keys, so a seat is claimed exactly when its key is present.)
        MCH_AircraftInfo info = v.weaponHostInfo();
        return mcheli.agnostic.vehicle.VehicleSeats.firstFreeSeat(p instanceof Player, info == null || info.canRide,
            -1, v.seatCount(), s -> !t.contains(Integer.toString(s)));
    }

    /**
     * The PLAYER operating {@code w} right now, or null — the port of {@code getWeaponUserByWeaponName}
     * ({@code MCH_EntityAircraft:3061-3076}): the occupant of the weapon's own seat, else the pilot but ONLY when the
     * weapon is {@code canUsePilot}. This is what decides whether a gun aims (and whose look it follows), whether it
     * can be selected/fired, and — for the local player — whether {@code HideGM} hides it.
     */
    Entity weaponOperator(MCH_AircraftInfo.Weapon w) {
        if (w == null) {
            return null;
        }
        Entity e = v.seatPassenger(w.seatID);
        if (!(e instanceof Player) && w.canUsePilot) {
            e = v.pilot();
        }
        return e instanceof Player ? e : null;
    }

    // ---- GUNNER MODE (#36b) — reference isGunnerMode (pilot toggle) + the structural gunner-seat mode ----

    /**
     * Is {@code seat}'s occupant in gunner mode right now — the port of {@code getIsGunnerMode}
     * ({@code MCH_EntityAircraft:5349-5367}): the pilot iff {@link AbstractMchVehicle#isGunnerModeActive}; a dedicated
     * gunner SEAT is permanently in gunner mode; a {@code switchgunner} seat follows its synced toggle
     * ({@link AbstractMchVehicle#DATA_SEAT_GUNNER_MODE}), which boarding turns on. Deliberate divergence: the reference
     * kept that toggle on the gunner's client only, so its server counted such a seat as always in gunner mode (:5363);
     * here the toggle is synced and both sides read it (nothing on the server depends on it). Drives the gunner camera,
     * zoom, seat-pitch clamp and HUD {@code gunner_mode}.
     */
    boolean isSeatGunnerMode(int seat) {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (info == null || seat < 0) {
            return false;
        }
        if (seat == 0) {
            return info.isEnableGunnerMode && v.isGunnerModeActive();
        }
        MCH_SeatInfo si = seat < info.seatList.size() ? info.seatList.get(seat) : null;
        if (si == null || !si.gunner) {
            return false;
        }
        return !si.switchgunner || seatGunnerBit(seat); // a dedicated gunner seat is always "in gunner mode"
    }

    /** May the occupant of {@code seat} toggle its gunner mode — the port of {@code canSwitchGunnerModeOtherSeat}
     *  ({@code MCH_EntityAircraft:5304-5314}): a passenger seat that is a switchable gunner seat. The ground-vehicle
     *  category never toggles: the reference vehicle tick handler reads the key and does nothing with it
     *  ({@code MCH_ClientVehicleTickHandler:128}); only the heli, plane and tank handlers call the switch. */
    boolean canSwitchGunnerModeOtherSeat(int seat) {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (info == null || seat <= 0 || seat >= info.seatList.size() || v instanceof MchGroundVehicle) {
            return false;
        }
        MCH_SeatInfo si = info.seatList.get(seat);
        return si.gunner && si.switchgunner;
    }

    /** The synced switchable-seat gunner bit of {@code seat}. A seat past the 32-bit mask reads as on, the boarding
     *  default ({@link mcheli.agnostic.vehicle.VehicleSeats#gunnerBit}). */
    private boolean seatGunnerBit(int seat) {
        return mcheli.agnostic.vehicle.VehicleSeats.gunnerBit(
            v.getEntityData().get(AbstractMchVehicle.DATA_SEAT_GUNNER_MODE), seat);
    }

    /** SERVER: set or clear {@code seat}'s switchable gunner bit (seat 0 is the pilot's
     *  {@link AbstractMchVehicle#DATA_GUNNER_MODE}). */
    private void setSeatGunnerBit(int seat, boolean on) {
        if (seat <= 0 || seat >= Integer.SIZE) {
            return;
        }
        int mask = v.getEntityData().get(AbstractMchVehicle.DATA_SEAT_GUNNER_MODE);
        v.getEntityData().set(AbstractMchVehicle.DATA_SEAT_GUNNER_MODE,
            mcheli.agnostic.vehicle.VehicleSeats.withGunnerBit(mask, seat, on));
    }

    /** SERVER: {@code rider} just took passenger seat {@code seat} (boarding or a seat switch) — the reference
     *  {@code onMountPlayerSeat}:3434-3441. A PLAYER taking seat 1 ends the pilot's gunner mode unless the config enables
     *  concurrent gunner mode (the two would fight over the seat-1 weapon), and the seat starts in gunner mode. */
    private void onPassengerSeatTaken(Entity rider, int seat) {
        if (seat <= 0) {
            return;
        }
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (seat == 1 && rider instanceof Player && (info == null || !info.isEnableConcurrentGunnerMode)) {
            v.switchGunnerMode(false);
        }
        setSeatGunnerBit(seat, true);
    }

    /** May the pilot toggle gunner mode — the port of {@code canSwitchGunnerMode} ({@code MCH_EntityAircraft:5294}):
     *  the config must enable it, and (unless it enables CONCURRENT gunner mode) seat 1 must be empty so a seat-1 gunner
     *  and the pilot-gunner never fight over the same weapon, and the canopy must be closed. (The reference's
     *  {@code !isHoveringMode()} gate lives in {@link MchHelicopter#canSwitchGunnerMode}, the only type with a hover
     *  mode.) The body of the entity's {@code canSwitchGunnerMode} hook, which the heli, plane and tank override. */
    boolean baseCanSwitchGunnerMode() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (info == null || !info.isEnableGunnerMode) {
            return false;
        }
        if (!v.isCanopyClose()) {
            return false; // reference :5296 — no gunner mode with the canopy open or still moving
        }
        // Reference MCH_EntityAircraft:5300 blocks only on a PLAYER in seat 1 (a mob gunner does not contend for the
        // weapon), unless concurrent gunner mode is enabled. Per-type attitude/throttle gates are added in overrides.
        return info.isEnableConcurrentGunnerMode || !(v.seatPassenger(1) instanceof Player);
    }

    /** SERVER: the gunner-mode key of {@code player}'s seat. The pilot toggles their gunner mode (entering only when
     *  {@link AbstractMchVehicle#canSwitchGunnerMode}; leaving is always allowed); the occupant of a switchable gunner
     *  seat flips that seat's gunner mode (reference {@code switchGunnerModeOtherSeat}:5316, gated by
     *  {@link #canSwitchGunnerModeOtherSeat}). Any other seat is refused. */
    void toggleGunnerMode(Player player) {
        if (v.level().isClientSide) {
            return;
        }
        int seat = v.seatIndexOf(player);
        if (seat == 0) {
            v.switchGunnerMode(!v.isGunnerModeActive());
        } else if (v.canSwitchGunnerModeOtherSeat(seat)) {
            setSeatGunnerBit(seat, !seatGunnerBit(seat));
        }
    }

    /** SERVER: the sim throttle saved on entering gunner (or heli hover) mode and restored on leaving it — the
     *  reference {@code beforeHoverThrottle}, one field shared by both modes. Not persisted (nor are the modes). */
    private double beforeHoverThrottle;

    /**
     * SERVER: the reference {@code switchGunnerMode(mode)} (MCH_EntityAircraft:5274-5290). Entering is gated by
     * {@link AbstractMchVehicle#canSwitchGunnerMode}, leaving is always allowed. Entering saves the sim throttle,
     * leaving restores it, so a heli's hover collective (which {@code controlHovering} winds up to 1.0) does not carry
     * over into normal flight. Also the target of the flight models' "leave gunner mode when out of fuel" rule, through
     * the {@code *State} seams.
     */
    void switchGunnerMode(boolean on) {
        if (v.level().isClientSide) {
            return;
        }
        boolean now = v.isGunnerModeActive();
        if (on && !v.canSwitchGunnerMode()) {
            return;
        }
        if (now && !on) {
            v.setSimThrottle(this.beforeHoverThrottle);
            v.getEntityData().set(AbstractMchVehicle.DATA_GUNNER_MODE, false);
        } else if (!now && on) {
            this.beforeHoverThrottle = v.simThrottle();
            v.getEntityData().set(AbstractMchVehicle.DATA_GUNNER_MODE, true);
        }
    }

    /** The saved pre-mode throttle, for the heli's hover mode, which shares it (reference). The body of the entity's
     *  protected {@code beforeHoverThrottle()}. */
    double baseBeforeHoverThrottle() { return this.beforeHoverThrottle; }
    /** The body of the entity's protected {@code setBeforeHoverThrottle(double)}. */
    void baseSetBeforeHoverThrottle(double t) { this.beforeHoverThrottle = t; }

    /** Who is sitting in {@code seat}, or null. */
    Entity seatPassenger(int seat) {
        for (Entity p : v.getPassengers()) {
            if (v.seatIndexOf(p) == seat) {
                return p;
            }
        }
        return null;
    }

    /** The PILOT (seat 0) — the ONLY rider who drives. Never use {@code getFirstPassenger()} for this: a gunner may
     *  legitimately board a pilotless craft, and passenger order is mount order. */
    Entity pilot() {
        return v.seatPassenger(0);
    }

    /** May {@code seat} be boarded right now? Honors the config's {@code ExclusionSeat} groups (at most ONE occupant
     *  across each group) and excludes rack slots (reference {@code canRideMob}: racks take neither players nor mobs). */
    boolean canOccupySeat(int seat) {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (info == null) {
            return false;
        }
        return mcheli.agnostic.vehicle.VehicleSeats.canOccupy(seat, v.seatCount(), s -> v.seatPassenger(s) != null,
            info.exclusionSeatList);
    }

    /** Seat choice for a boarding rider: an explicitly-clicked seat if free, else the pilot seat (players only, and only
     *  when the config allows riding), else the first free crew seat. -1 == the vehicle is full. */
    private int firstFreeSeatFor(Entity rider, int preferred) {
        MCH_AircraftInfo info = v.weaponHostInfo();
        return mcheli.agnostic.vehicle.VehicleSeats.firstFreeSeat(rider instanceof Player,
            info != null && info.canRide, preferred, v.seatCount(), this::canOccupySeat);
    }

    /** Programmatically request that {@code rider} take a specific {@code seat} on its NEXT boarding — the code-path
     *  equivalent of clicking that seat's box ({@link #handleInteractAt}), for mounts that never go through an interact:
     *  scripted crew, NPCs, or the headless self-tests. Server-side; honored by {@link #firstFreeSeatFor} exactly like
     *  a clicked seat (so it can seat a non-player in the pilot seat, which the automatic rule reserves for players). */
    void preferSeat(Entity rider, int seat) {
        if (!v.level().isClientSide) {
            this.pendingSeat.put(rider.getUUID(), seat);
        }
    }

    /** The body of the entity's {@code canAddPassenger} override: is there a seat for {@code p}? */
    boolean canAdd(Entity p) {
        return firstFreeSeatFor(p, this.pendingSeat.getOrDefault(p.getUUID(), -1)) >= 0;
    }

    /** The body of the entity's {@code addPassenger} override, after {@code super.addPassenger}: seat the new rider. */
    void handlePassengerAdded(Entity p) {
        if (v.level().isClientSide) {
            return;
        }
        Integer saved = this.restoredSeats.remove(p.getUUID());
        int seat = (saved != null && v.canOccupySeat(saved))
            ? saved
            : firstFreeSeatFor(p, this.pendingSeat.getOrDefault(p.getUUID(), -1));
        this.pendingSeat.remove(p.getUUID());
        if (seat < 0 && p instanceof Player) {
            seat = seatByVacatingCrew();
        }
        if (seat < 0) {
            // A FORCED mount (startRiding(v, true) skips canAddPassenger: /ride, a scripted mount, or a chunk reload
            // into a config with fewer seats) onto a full vehicle. There is no seat to give, and a seat-less rider
            // would have its control packets dropped and ride at a guessed attachment, so it must get off. NOT here:
            // vanilla's login reattach (PlayerList.placeNewPlayer) discards the saved RootVehicle and its whole crew
            // when the player is not a passenger right after startRiding(v, true). The eject waits for our next tick.
            this.pendingEjects.add(p.getUUID());
            return;
        }
        this.seatByUuid.put(p.getUUID(), seat);
        syncSeatTag();
        if (seat == 0) {
            v.vehicleParts().closeCanopyForNewPilot(); // a forced mount skipped the boarding gate (reference interactFirst:4358)
        }
        onPassengerSeatTaken(p, seat); // a switchable gunner seat starts in gunner mode
        // A creative rider tops every magazine; the arriving crew member may also bump the pilot off a weapon he was
        // only borrowing while this seat was empty (reference onMountPlayerSeat:3443-3453).
        if (v.weaponLoadout() != null) {
            v.weaponLoadout().onMount(v.weaponInfinityAmmo(p));
            v.weaponLoadout().refreshEligibility(s -> v.seatPassenger(s) instanceof Player);
            // The auto-bump may have moved the PILOT off a weapon he was only borrowing — republish, or his HUD keeps
            // naming a weapon he is no longer firing.
            v.publishSeatWeapons();
        }
    }

    /** The body of the entity's {@code removePassenger} override, after {@code super.removePassenger}: free the seat. */
    void handlePassengerRemoved(Entity p) {
        if (v.level().isClientSide) {
            return;
        }
        Integer seat = this.seatByUuid.remove(p.getUUID());
        syncSeatTag();
        v.releaseCameraViewer(p); // dismount, death, the vehicle's removal or unload: no camera night vision left behind
        if (seat != null) {
            // The leaving PILOT's DismountAll setting (sent with their controls) unloads the mob crew too — reference
            // unmountEntity:3661 -> unmountCrew(false). Read before the seat's controls are dropped; deferred to the
            // next tick because the passenger list is mid-update here.
            if (seat == 0 && v.controlState(0).dismountAll) {
                this.dismountCrewPending = true;
            }
            v.clearSeatControls(seat);
            setSeatGunnerBit(seat, false);
        }
        resetGunnerModeIfNoPilot(); // the pilot left -> drop their gunner mode (reference switchGunnerMode(false) on unmount)
        if (v.weaponLoadout() != null) {
            v.weaponLoadout().refreshEligibility(s -> v.seatPassenger(s) instanceof Player);
            v.publishSeatWeapons(); // a departing gunner frees his weapon
        }
    }

    /** SERVER: set when the pilot leaves with DismountAll on; {@link #tickDeferred} then runs {@link #dismountMobCrew}. */
    private boolean dismountCrewPending;

    /** SERVER: riders a forced mount put aboard with no seat to give ({@link #handlePassengerAdded});
     *  {@link #tickDeferred} puts them off once the vehicle is in the world for good ({@link #ejectUnseatedRiders}). */
    private final java.util.Set<UUID> pendingEjects = new java.util.HashSet<>();

    /** A forced PLAYER mount onto a full vehicle: put the non-player crew member off the seat
     *  {@link mcheli.agnostic.vehicle.VehicleSeats#seatToVacateForPlayer} picks and hand that seat over, so a player
     *  logging back into a vehicle whose config lost seats keeps flying it. -1 when no such seat exists. */
    private int seatByVacatingCrew() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (info == null) {
            return -1;
        }
        int s = mcheli.agnostic.vehicle.VehicleSeats.seatToVacateForPlayer(info.canRide, v.seatCount(),
            k -> v.seatPassenger(k) != null && !(v.seatPassenger(k) instanceof Player), k -> v.seatPassenger(k) != null,
            info.exclusionSeatList);
        Entity crew = s >= 0 ? v.seatPassenger(s) : null;
        if (crew == null) {
            return -1;
        }
        crew.stopRiding(); // removePassenger frees the seat
        return v.canOccupySeat(s) ? s : -1;
    }

    /** The deferred half of a forced mount with no seat: still aboard and still seat-less, it gets off now. */
    private void ejectUnseatedRiders() {
        for (Entity p : java.util.List.copyOf(v.getPassengers())) {
            if (this.pendingEjects.contains(p.getUUID()) && !this.seatByUuid.containsKey(p.getUUID())) {
                p.stopRiding();
            }
        }
        this.pendingEjects.clear();
    }

    /** Reference {@code unmountCrew(false)} (MCH_EntityAircraft:3751-3785): every non-player passenger gets off (the
     *  reference skipped rack seats, which the port never boards). Players stay aboard. */
    private void dismountMobCrew() {
        for (Entity p : java.util.List.copyOf(v.getPassengers())) {
            if (!(p instanceof Player)) {
                p.stopRiding();
            }
        }
    }

    /** Is passenger seat {@code i} unoccupied? Racks are outside the {@code 0..seatCount()-1} range (the port captures
     *  {@code mobSeatNum} before racks are appended), so iterating this range is already rack-free. */
    private boolean isSeatEmpty(int i) {
        return i >= 0 && i < v.seatCount() && v.seatPassenger(i) == null;
    }

    /**
     * Move to the next-higher empty PASSENGER seat, wrapping — the port of {@code switchNextSeat}
     * ({@code MCH_EntityAircraft:4419}). Seats 1..N only (the pilot seat is reachable only via {@link #grabPilotSeat}).
     * A pilot caller (seat 0) lands in the lowest empty passenger seat, vacating seat 0.
     */
    void switchSeatNext(Player player) {
        if (v.level().isClientSide || v.seatCount() <= 1) {
            return;
        }
        int cur = v.seatIndexOf(player);
        if (cur < 0) {
            return;
        }
        int to = AbstractMchVehicle.nextSeatTarget(cur, v.seatCount(), this::isSeatEmpty);
        if (to >= 0) {
            moveToSeat(player, cur, to);
        }
    }

    /**
     * Move to the next-lower empty PASSENGER seat, wrapping — the port of {@code switchPrevSeat}
     * ({@code MCH_EntityAircraft:4462}). A pilot caller lands in the highest empty passenger seat.
     */
    void switchSeatPrev(Player player) {
        if (v.level().isClientSide || v.seatCount() <= 1) {
            return;
        }
        int cur = v.seatIndexOf(player);
        if (cur < 0) {
            return;
        }
        int to = AbstractMchVehicle.prevSeatTarget(cur, v.seatCount(), this::isSeatEmpty);
        if (to >= 0) {
            moveToSeat(player, cur, to);
        }
    }

    /**
     * Take the pilot seat if it is free — the useful case of the reference's {@code switchSeat==3} path
     * ({@code MCH_AircraftPacketHandler:300-304}, which dismounts then re-boards as pilot when seat 0 is empty). A
     * gunner promotes to pilot; if the pilot seat is taken (or the config forbids riding), this is a no-op.
     */
    void grabPilotSeat(Player player) {
        if (v.level().isClientSide) {
            return;
        }
        MCH_AircraftInfo info = v.weaponHostInfo();
        int cur = v.seatIndexOf(player);
        if (cur <= 0 || info == null || !info.canRide || !v.canOccupySeat(0)) {
            return;
        }
        if (!v.checkTeam(player)) {
            // The reference re-boards through interactFirst, whose team gate refuses (and leaves the player on foot,
            // having dismounted him first); here the refused gunner simply keeps his seat.
            return;
        }
        moveToSeat(player, cur, 0);
    }

    /** Re-key a rider from {@code from} to {@code to} in the seat map, running the same side effects as boarding: clear
     *  the old seat's stale controls, resync the map, close an open canopy for a new pilot, and refresh weapon
     *  eligibility + the HUD selection. The rider is ALREADY a passenger, so no vanilla remount happens (unlike the
     *  reference's seat-entity juggling). */
    private void moveToSeat(Player player, int from, int to) {
        this.seatByUuid.put(player.getUUID(), to);
        v.clearSeatControls(from);   // a key held in the old seat must not linger (e.g. the pilot's throttle on leaving)
        setSeatGunnerBit(from, false);
        syncSeatTag();
        resetGunnerModeIfNoPilot(); // a pilot who switches to a gunner seat drops their gunner mode
        if (to == 0) {
            v.vehicleParts().closeCanopyForNewPilot(); // reference switchSeat==3 -> interactFirst(player, true) -> closeCanopy (:4358)
        }
        onPassengerSeatTaken(player, to); // the reference re-mounts a seat entity, so onMountPlayerSeat runs again
        if (v.weaponLoadout() != null) {
            v.weaponLoadout().refreshEligibility(s -> v.seatPassenger(s) instanceof Player);
            v.publishSeatWeapons();
        }
    }

    /** Gunner mode belongs to the pilot; if seat 0 is now empty, drop it (reference resets {@code switchGunnerMode(false)}
     *  when the operator leaves — otherwise a pilotless heli would hover forever), restoring the saved throttle. */
    private void resetGunnerModeIfNoPilot() {
        if (v.seatPassenger(0) == null && v.isGunnerModeActive()) {
            v.switchGunnerMode(false);
        }
    }

    /** Publish the seat map (seatIndex -> passenger entity id) to every tracking client. */
    private void syncSeatTag() {
        CompoundTag t = new CompoundTag();
        for (Entity p : v.getPassengers()) {
            Integer seat = this.seatByUuid.get(p.getUUID());
            if (seat != null) {
                t.putInt(Integer.toString(seat), p.getId());
            }
        }
        v.getEntityData().set(AbstractMchVehicle.DATA_SEATS, t);
    }
}
