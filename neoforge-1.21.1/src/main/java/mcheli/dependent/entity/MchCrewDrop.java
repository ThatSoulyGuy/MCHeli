package mcheli.dependent.entity;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.aircraft.MCH_SeatInfo;
import mcheli.agnostic.parachute.ParachuteFlight;
import mcheli.agnostic.sim.GroundProbe;
import mcheli.agnostic.util.MCH_Queue;
import mcheli.agnostic.value.Vec3d;
import mcheli.agnostic.vehicle.CrewUnmount;
import mcheli.agnostic.vehicle.EjectionSeat;
import mcheli.agnostic.vehicle.Repelling;
import mcheli.dependent.registry.MchSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * How the crew leaves a vehicle other than by the plain dismount: the ejection seat (reference
 * {@code MCH_EntityAircraft.canEjectSeat}, {@code ejectSeat} and {@code ejectSeatSub}, EA:3818-3871), pulled by the
 * rider (the jump key, {@code mcheli:eject_seat}) or by the vehicle's destruction ({@code destroyAircraft},
 * EA:615-621); the pilot's crew unmount (the Y key, {@code mcheli:unmount_crew}; reference {@code unmountCrew},
 * {@code startUnmountCrew} and {@code onUpdate_UnmountCrew}, EA:1892-1904 and :3670-3785): the plain unload of the
 * mob crew, or the {@code MobDropOption} drop of one mob at a time on a parachute, behind a forced-open hatch; and
 * seat parachuting, a player passenger jumping out from the same drop point (the jump key,
 * {@code mcheli:seat_unmount}; reference {@code unmount}, {@code canUnmount} and {@code canParachuting},
 * EA:1924-1978); and rope repelling for the mob crew (reference {@code startRepelling}, {@code stopRepelling},
 * {@code canStartRepelling}, {@code onUpdate_Repelling}, {@code onUpdate_UnmountCrewRepelling} and
 * {@code unmountEntityRepelling}, EA:1817-1890 and :3703-3749): the pilot's Y lowers the ropes of a hovering, level,
 * still helicopter, and after the 60-tick rope-out every hook lowers one mob on an {@link MchRopeSlider} each
 * {@code interval} ticks, while a player passenger may slide down the next rope himself from 2.5 s on (the jump key,
 * {@code mcheli:seat_unmount}; reference {@code canRepelling} and {@code unmount}, EA:1827-1829 and :1936-1952).
 *
 * <p>Server logic; only {@link #canEjectSeat}, {@link #canParachuting}, {@link #canRepelling}, {@link #isRepelling},
 * {@link #ropePos} and the per-side rope counters ({@link #tickClient}) also run on a client. Besides the back-reference it holds the
 * reference's server-only drop state ({@code isParachuting} and the {@code prevPosition} trail, EA:218-220) and the
 * repelling counters ({@code ropesLength}, {@code tickRepelling}, {@code lastUsedRopeIndex}, EA:219-222), none of them
 * synced or saved, and it writes no synced accessor: the canopy and the hatch open through
 * {@link MchVehicleParts#openCanopyForEject} and {@link MchVehicleParts#forceOpenHatch}, the repelling flag goes through
 * {@link MchVehicleParts#setRepelling}, and the seats free through vanilla's dismount and the crew's passenger hook.
 */
final class MchCrewDrop {
    /** Reference {@code ejectSeatSub} (EA:3842-3848): the seat leaves 2 above its seat point, at 2 blocks a tick along
     *  the hull's up axis. */
    private static final double EJECT_RISE = 2.0;
    /** Reference {@code ejectSeatSub} (EA:3849-3851): the random sideways kick, {@code (rand - 0.5) * 0.1} on X then Z. */
    private static final double EJECT_SCATTER = 0.1;
    /** Reference {@code prevPosition = new MCH_Queue(10, ...)} (EA:310): ten end-of-tick positions. */
    private static final int TRAIL_LENGTH = 10;
    /** Reference {@code unmountCrew()} (EA:3685): a hooked vehicle within 4 blocks of the ground unloads plainly. */
    private static final int HOOKS_GROUND_DEPTH = 4;
    /** Reference {@code unmountCrew()} and {@code onUpdate_UnmountCrew} (EA:3694, :1895): a drop needs nothing within
     *  10 blocks below, to start and to go on. */
    private static final int DROP_GROUND_DEPTH = 10;
    /** A mob goes down a rope from 2 below its hook's point (reference EA:1880-1882); a player from 4 below, the
     *  reference's {@code unmount} subtracting 2 more first (EA:1941). */
    private static final double MOB_SLIDE_DROP = 2.0;
    private static final double PLAYER_SLIDE_DROP = 4.0;

    private final AbstractMchVehicle v;

    /** SERVER: a crew drop is running (reference {@code isParachuting}, EA:218; neither synced nor saved). */
    private boolean dropping;
    /** SERVER: the vehicle's end-of-tick positions (reference {@code prevPosition}, EA:220), the drop origin. */
    private final MCH_Queue<Vec3> trail = new MCH_Queue<>(TRAIL_LENGTH, Vec3.ZERO);
    /** SERVER: the trail has been filled once ({@link #beginServerTick}). Before that the drop origin is the current
     *  position rather than the reference queue's initial (0, 0, 0). */
    private boolean trailSeeded;
    /** Both sides, each its own: the rope length, 0 or negative (reference {@code ropesLength}, EA:219; the server's
     *  decides the slider releases, a client's is what the rope render draws). */
    private float ropesLength;
    /** Both sides, each its own: ticks since repelling started, 0..60 (reference {@code tickRepelling}, EA:221). */
    private int tickRepelling;
    /** SERVER: the rope the last mob went down (reference {@code lastUsedRopeIndex}, EA:222; the player rope slide
     *  takes the next one). */
    private int lastUsedRopeIndex;

    MchCrewDrop(AbstractMchVehicle v) {
        this.v = v;
    }

    // ---- the ejection seat ----

    /** Reference {@code canEjectSeat} (EA:3868-3871), on either side: {@code e} sits in seat 0 or 1 of an
     *  {@code EnableEjectionSeat} config, and not in the pilot seat of a UAV. */
    boolean canEjectSeat(Entity e) {
        MCH_AircraftInfo info = v.weaponHostInfo();
        return e != null && info != null
            && EjectionSeat.canEjectSeat(v.seatIndexOf(e), info.isUAV, info.isEnableEjectionSeat);
    }

    /**
     * SERVER: reference {@code ejectSeat} (EA:3818-3839). With a parachute in the slots, the rider of seat 0 or 1
     * ejects; the pilot takes a MOB in seat 1 along when a second parachute is left (a player there decides for
     * himself), and the pilot's DismountAll unloads the mob crew plainly first ({@code unmountEntity}, EA:3661-3663).
     * {@link EjectionSeat#plan} decides who goes; each one leaves, uses up a parachute and is launched
     * ({@link #ejectSeatSub}). It does not check {@link #canEjectSeat}: the payload does, and destruction skips it, as
     * in the reference. A rider a mod keeps aboard (a cancelled dismount) uses up nothing and stops the sequence.
     */
    void ejectSeat(Entity e) {
        if (e == null || !(v.level() instanceof ServerLevel level)) {
            return;
        }
        int sid = v.seatIndexOf(e);
        Entity seat1 = v.seatPassenger(1);
        EjectionSeat.Occupant occupant = seat1 == null ? EjectionSeat.Occupant.EMPTY
            : seat1 instanceof Player ? EjectionSeat.Occupant.PLAYER : EjectionSeat.Occupant.MOB;
        // Read before anyone leaves: the pilot's departure drops seat 0's control state.
        boolean dismountAll = v.controlState(0).dismountAll;
        for (int s : EjectionSeat.plan(sid, v.logistics().parachuteCount(), occupant, dismountAll)) {
            // Seat 0 is the caller; seat 1 is the caller too, or (after the pilot) whoever sits there now.
            Entity o = s == sid ? e : v.seatPassenger(s);
            if (o == null || o != e && o instanceof Player) {
                return; // the pilot's departure changed seat 1 (another mod's hook): nobody else goes
            }
            Vec3 at = ejectPoint(o, s);
            if (!leave(o, at)) {
                return;
            }
            v.consumeParachute();
            if (s == 0) {
                v.crew().flushDeferredDismount(); // unmountEntity's DismountAll unload, before seat 1 is looked at
            }
            ejectSeatSub(level, o);
        }
    }

    /** SERVER: reference {@code destroyAircraft} (EA:615-621): the pilot's handle, then seat 1's (a mob co-pilot has
     *  usually gone with the pilot already). No {@link #canEjectSeat} gate: the parachute slots decide, and mobs go
     *  too. The UAV pilot dismount before it has no port yet (no UAV can be placed). */
    void ejectOnDestroy() {
        Entity pilot = v.pilot();
        if (pilot != null) {
            ejectSeat(pilot);
        }
        Entity seat1 = v.seatPassenger(1);
        if (seat1 != null) {
            ejectSeat(seat1);
        }
    }

    /** Reference {@code ejectSeatSub} (EA:3842-3846): {@code getTransformedPosition(seat.x, seat.y + 2, seat.z)}, the
     *  body rotation of the seat point raised by 2 (unlifted, as the dismount points); the rider's own position when
     *  the seat has no config entry (the reference left it where it was). */
    private Vec3 ejectPoint(Entity o, int s) {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (info == null || info.seatList == null || s < 0 || s >= info.seatList.size()) {
            return o.position();
        }
        MCH_SeatInfo seat = info.seatList.get(s);
        org.joml.Vector3f p = new org.joml.Vector3f((float) seat.pos.x(), (float) (seat.pos.y() + EJECT_RISE),
            (float) seat.pos.z()).rotate(v.bodyRotation(1.0F));
        return v.position().add(p.x, p.y, p.z);
    }

    /**
     * Put {@code o} off this vehicle at {@code at}: vanilla's dismount asks {@link MchRiderPose} for the point, which
     * answers {@code at} once ({@link MchRiderPose#placeNextDismount}). A rider that is not a {@link LivingEntity} gets
     * no dismount point from vanilla, so it is placed here. False when {@code o} is still aboard (a mod cancelled the
     * dismount).
     */
    boolean leave(Entity o, Vec3 at) {
        v.riderPose().placeNextDismount(o, at);
        try {
            o.stopRiding();
        } finally {
            v.riderPose().clearPendingDismount(o);
        }
        if (o.getVehicle() == v) {
            return false;
        }
        if (!(o instanceof LivingEntity)) {
            o.setPos(at.x, at.y, at.z);
        }
        return true;
    }

    /**
     * Reference {@code ejectSeatSub} (EA:3848-3865), after the rider stands at its eject point: the launch is the
     * vehicle's motion plus 2 along the hull's up axis plus a random kick on X then Z (two draws from the vehicle's
     * RNG); a type-2 parachute with the rider's yaw, launch and fall distance takes the rider on its first tick; a
     * closed canopy is forced open; {@code eject_seat} plays at the rider, volume 5. There is no altitude gate: a
     * grounded plane's seat still rises about 26 blocks (faithful). The reference also queued a 5-tick
     * {@code UnmountReserve} that pinned the rider to the dismount point and fought this launch (EA:3654,
     * :1367-1383); the port has no reserve, so the launch simply wins.
     */
    private void ejectSeatSub(ServerLevel level, Entity o) {
        org.joml.Vector3f up = new org.joml.Vector3f(0.0F, (float) EJECT_RISE, 0.0F).rotate(v.bodyRotation(1.0F));
        Vec3 m = v.getDeltaMovement();
        double kickX = (v.getRandom().nextFloat() - 0.5) * EJECT_SCATTER;
        double kickZ = (v.getRandom().nextFloat() - 0.5) * EJECT_SCATTER;
        Vec3 launch = new Vec3(m.x + up.x + kickX, m.y + up.y, m.z + up.z + kickZ);
        o.setDeltaMovement(launch);
        MchParachute.deploy(level, o.getX(), o.getY(), o.getZ(), ParachuteFlight.TYPE_SEAT, o, o.getYRot(), launch,
            o.fallDistance);
        if (v.haveCanopy() && v.isCanopyClose()) {
            v.vehicleParts().openCanopyForEject();
        }
        SoundEvent sound = MchSounds.byName("eject_seat");
        if (sound != null) {
            level.playSound(null, o.getX(), o.getY(), o.getZ(), sound, SoundSource.NEUTRAL, 5.0F, 1.0F);
        }
    }

    // ---- the crew unmount (the pilot's Y key) and the MobDropOption drop ----

    /**
     * SERVER, at the start of the server tick: reference {@code onUpdate}:1358-1360, the trail is refilled with the
     * position while {@code countOnUpdate < 2}, which is the first two ticks ({@code tickCount} 1 and 2: it is counted
     * up before the tick and is not saved, like {@code countOnUpdate}). The reference counter's wrap to 1 at 1,000,000
     * (EA:1452-1455) is not reproduced; {@code tickCount} runs on.
     */
    void beginServerTick() {
        if (v.tickCount <= 2) {
            this.trail.clear(v.position());
            this.trailSeeded = true;
        }
    }

    /**
     * SERVER, after the move and the attitude-crash damage, for an intact vehicle and a wreck alike: reference
     * {@code onUpdate}:1534-1543, the crew drop ({@code onUpdate_UnmountCrew}), the ropes and the repelling cadence
     * ({@code onUpdate_Repelling}), then the end-of-tick position joins the trail.
     */
    void tickServerPostMove() {
        tickUnmountCrew();
        stepRepelling();
        this.trail.put(v.position());
    }

    /** CLIENT, at the end of the client tick: reference {@code onUpdate_Repelling} (EA:1831-1877) on a client, its own
     *  rope length (0.3 a tick, which the rope render draws) and tick counter; the cadence is the server's. */
    void tickClient() {
        stepRepelling();
    }

    /**
     * SERVER: the pilot's unmount key (reference {@code unmountCrew()}, EA:3681-3701, through {@link CrewUnmount}). A
     * hooked config stops repelling while it repels; otherwise it unloads plainly within 4 blocks of the ground, and
     * starts repelling where {@link #canStartRepelling} allows it (else nothing). An {@code EnableParachuting} config
     * with nothing within 10 blocks below starts the drop, and a second press stops it; anything else puts every mob
     * off beside its own seat.
     */
    void unmountCrew() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (info == null || v.level().isClientSide) {
            return;
        }
        boolean hooks = info.haveRepellingHook();
        boolean repelling = isRepelling();
        // Each probe is taken only where the reference evaluates it.
        boolean groundWithin4 = hooks && !repelling && blockBelow(HOOKS_GROUND_DEPTH);
        boolean canStartRepelling = hooks && !repelling && !groundWithin4 && canStartRepelling();
        boolean groundWithin10 = !hooks && !this.dropping && info.isEnableParachuting
            && blockBelow(DROP_GROUND_DEPTH);
        switch (CrewUnmount.onUnmountKey(hooks, repelling, groundWithin4, canStartRepelling, this.dropping,
            info.isEnableParachuting, groundWithin10)) {
            case DISMOUNT_MOBS -> v.crew().dismountMobCrew();
            case START_REPELLING -> startRepelling();
            case STOP_REPELLING -> stopRepelling();
            case START_DROP -> startUnmountCrew();
            case STOP_DROP -> stopUnmountCrew();
            case NONE -> { }
        }
    }

    /** SERVER: reference {@code startUnmountCrew} (EA:3670-3675): the drop runs, and a hatch is forced open at once
     *  ({@code foldHatch(true, true)}, EA:5522-5526). */
    private void startUnmountCrew() {
        this.dropping = true;
        if (v.haveHatch()) {
            v.vehicleParts().forceOpenHatch();
        }
    }

    /** SERVER: reference {@code stopUnmountCrew} (EA:3677-3679), on the key again, near the ground, with nobody left,
     *  or when the pilot closes the hatch ({@code foldHatch(false)}, EA:5527-5529). The hatch stays as it is. */
    void stopUnmountCrew() {
        this.dropping = false;
    }

    /** SERVER: reference {@code onUpdate_UnmountCrew} (EA:1892-1904, through {@link CrewUnmount#dropTick}): within 10
     *  blocks of the ground the drop stops; else, once the hatch is past 89 degrees, one mob goes on every tick that is
     *  a multiple of the {@code MobDropOption} interval, and the first firing that finds none stops the drop. */
    private void tickUnmountCrew() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (info == null || !this.dropping) {
            return;
        }
        boolean hatch = v.haveHatch();
        switch (CrewUnmount.dropTick(true, blockBelow(DROP_GROUND_DEPTH), hatch,
            hatch ? v.vehicleParts().hatchRotation() : 0.0F, v.tickCount,
            CrewUnmount.safeInterval(info.mobDropOption.interval))) {
            case STOP -> stopUnmountCrew();
            case DROP -> {
                if (!dropOneMob(info)) {
                    stopUnmountCrew();
                }
            }
            case WAIT -> { }
        }
    }

    // ---- rope repelling (the pilot's Y key on a hooked helicopter) ----

    /** The synced repelling flag, on either side (reference {@code isRepelling}, common-status bit 5, EA:3703-3705). */
    boolean isRepelling() {
        return v.vehicleParts().isRepelling();
    }

    /** This side's rope length, 0 or negative (reference {@code ropesLength}). */
    float ropesLength() {
        return this.ropesLength;
    }

    /** This side's ticks since repelling started, 0..60 (reference {@code tickRepelling}). */
    int tickRepelling() {
        return this.tickRepelling;
    }

    /**
     * SERVER: may the pilot start repelling now? Reference {@code canStartRepelling} (EA:3740-3749, through
     * {@link Repelling#canStartRepelling}): hooks, {@link AbstractMchVehicle#isHovering()} (hover mode, or gunner
     * mode), the hull within 3 degrees of level in pitch and roll, and less than 0.3 from the trail's oldest position
     * (still for about 9 ticks).
     */
    boolean canStartRepelling() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (info == null) {
            return false;
        }
        double drift = crewTrailOldest().subtract(v.position()).length();
        return Repelling.canStartRepelling(info.haveRepellingHook(), v.isHovering(), v.getXRot(), v.getRollAngle(),
            drift);
    }

    /** SERVER: reference {@code startRepelling} (EA:3717-3725): the flag goes up, the pilot's held throttle and
     *  sideways keys are let go, and the rope-out time starts again. */
    private void startRepelling() {
        v.vehicleParts().setRepelling(true);
        mcheli.dependent.control.MchControlState cs = v.controlState(0);
        cs.throttleUp = false;
        cs.throttleDown = false;
        cs.moveLeft = false;
        cs.moveRight = false;
        this.tickRepelling = 0;
    }

    /** SERVER: reference {@code stopRepelling} (EA:3727-3730): the flag goes down; every slider lets go on its next
     *  tick and both sides pull the ropes in. Y again, every hover switch ({@code switchHoveringMode}'s first line,
     *  EA:5325), the last player getting off (EA:3527-3530, :3628-3630) and destruction (EA:592) call it. */
    void stopRepelling() {
        v.vehicleParts().setRepelling(false);
    }

    /** SERVER, after the crew's own passenger hook: reference {@code onUnmountPlayerSeat} and {@code unmountEntity}
     *  (EA:3527-3530, :3628-3630), whose {@code switchHoveringMode(false)} stopped repelling, on every vehicle, once no
     *  player rides any more. */
    void handlePassengerRemoved(Entity p) {
        if (!v.level().isClientSide && !v.isRidePlayer()) {
            stopRepelling();
        }
    }

    /**
     * Reference {@code onUpdate_Repelling} and {@code onUpdate_UnmountCrewRepelling} (EA:1831-1877), on either side
     * (the server after its move, a client at the end of its tick). The rope grows to 50, or down to the ground below
     * ({@link GroundProbe#airBelow}); the tick counter runs to 60 (the rope-out time); from then on the server lets one
     * mob down each hook on every tick that is a multiple of the hook's interval: the first mob from seat 2 up
     * ({@link CrewUnmount#dropSeat}; the pilot, seat 1, players and racks stay, as in the reference).
     */
    private void stepRepelling() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (info == null) {
            return;
        }
        boolean client = v.level().isClientSide;
        boolean repelling = isRepelling();
        boolean hooks = info.haveRepellingHook();
        int alt = hooks && repelling ? GroundProbe.airBelow(v.ref.world(), v.getX(), v.getY(), v.getZ()) : 0;
        this.ropesLength = Repelling.ropeStep(this.ropesLength, hooks, repelling, alt, client);
        boolean cadence = Repelling.cadenceOpen(repelling, this.tickRepelling);
        this.tickRepelling = Repelling.tickStep(repelling, this.tickRepelling);
        if (!cadence || client) {
            return;
        }
        for (int idx = 0; idx < info.repellingHooks.size(); idx++) {
            MCH_AircraftInfo.RepellingHook hook = info.repellingHooks.get(idx);
            if (!Repelling.fires(v.tickCount, hook.interval)) {
                continue;
            }
            int seat = CrewUnmount.dropSeat(v.seatCount(), s -> {
                Entity e = v.seatPassenger(s);
                return e != null && !(e instanceof Player);
            });
            if (seat >= 0) {
                repelMob(v.seatPassenger(seat), hook, idx);
                this.lastUsedRopeIndex = idx;
            }
        }
    }

    /** SERVER: reference EA:1859-1867, one mob down rope {@code idx}: it leaves 2 below the hook's point on the
     *  9-tick-old trail position with the CURRENT body rotation ({@link #dropPoint}, the crew drop's transform). */
    private void repelMob(Entity e, MCH_AircraftInfo.RepellingHook hook, int idx) {
        spawnSlider(e, dropPoint(hook.pos).subtract(0.0, MOB_SLIDE_DROP, 0.0), idx);
    }

    /**
     * SERVER: reference {@code unmountEntityRepelling} (EA:1879-1890): {@code e} leaves to {@code feet} at rest with no
     * fall distance, and a slider whose 1.7.10 {@code posY} is that point (its box bottom 0.5 lower) takes it on its
     * first tick. Nothing happens for a rider a mod keeps aboard.
     */
    private void spawnSlider(Entity e, Vec3 feet, int idx) {
        if (!(v.level() instanceof ServerLevel level) || !leave(e, feet)) {
            return;
        }
        e.setDeltaMovement(Vec3.ZERO);
        e.fallDistance = 0.0F;
        MchRopeSlider.deploy(level, feet.x, feet.y - MchRopeSlider.Y_OFFSET, feet.z, v, e, idx);
    }

    /** Reference {@code getRopePos} (EA:3711-3715): hook {@code idx}'s world point with the CURRENT pose (unlifted, as
     *  the dismount points); the vehicle's position for an index it has no hook for. Either side. */
    Vec3 ropePos(int idx) {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (info == null || idx < 0 || idx >= info.repellingHooks.size()) {
            return v.position();
        }
        Vec3d pos = info.repellingHooks.get(idx).pos;
        org.joml.Vector3f p = new org.joml.Vector3f((float) pos.x(), (float) pos.y(), (float) pos.z())
            .rotate(v.bodyRotation(1.0F));
        return v.position().add(p.x, p.y, p.z);
    }

    // ---- seat parachuting and the rope slide (a passenger's jump key) ----

    /**
     * Both sides: may {@code e} parachute out of its seat now? {@link CrewUnmount#canSeatParachute}: an
     * {@code EnableParachuting} config (the an2 and the mv-22), a seat from 2 up, a hatch (where the config has one)
     * at 89 degrees or more, and nothing within 13 blocks below. The jump key's pre-check and the server's re-check in
     * {@link #unmountSeat} are this one rule: the reference client ({@code canParachuting}, EA:1972-1978) ignored the
     * hatch and its server ({@code canUnmount}, EA:1924-1932) the ground (deliberate divergence: reference bug, see
     * {@link CrewUnmount#canSeatParachute}). The hatch angle is the animator's, which a client runs too.
     */
    boolean canParachuting(Entity e) {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (e == null || info == null) {
            return false;
        }
        boolean hatch = v.haveHatch();
        return CrewUnmount.canSeatParachute(info.isEnableParachuting, v.seatIndexOf(e), hatch,
            hatch ? v.vehicleParts().hatchRotation() : 0.0F,
            () -> blockBelow(CrewUnmount.PARACHUTING_GROUND_DEPTH));
    }

    /**
     * Both sides: may {@code e} slide down a rope now? Reference {@code canRepelling} (EA:1827-1829, through
     * {@link Repelling#canRepelling}): the ropes are down and this side's counter is past 50 (2.5 s after the start,
     * the mob cadence waits for 60), on a config with hooks, for the rider of a seat from 1 up (the reference slide
     * needed a seat entity, EA:1937-1938, which the pilot never had; seat 1 slides although the mob cadence never takes
     * it, faithful). The jump key's pre-check and the server's re-check in {@link #unmountSeat} are this one rule,
     * each side on its own counter, as in the reference (a client sees the flag a little later, so its counter does
     * not run ahead of the server's).
     */
    boolean canRepelling(Entity e) {
        MCH_AircraftInfo info = v.weaponHostInfo();
        return e != null && info != null && info.haveRepellingHook() && v.seatIndexOf(e) >= 1
            && Repelling.canRepelling(isRepelling(), this.tickRepelling);
    }

    /**
     * SERVER: a passenger's jump key ({@code mcheli:seat_unmount}; reference {@code unmount(entity)}, EA:1934-1970).
     * The rope slide first (EA:1936-1952): when {@link #canRepelling} allows it, {@code e} takes the rope after the
     * last one used ({@link Repelling#nextRope}, the mob cadence's {@code lastUsedRopeIndex} included) and goes down
     * it on a slider from 4 below the hook's point on the 9-tick-old trail position: the reference subtracted 2 there
     * (EA:1941) and {@code unmountEntityRepelling} another 2 (EA:1880-1882), so a player starts 2 lower than a mob
     * (faithful). Otherwise, when {@link #canParachuting} allows it, {@code e} leaves at the {@code MobDropOption}
     * point of the same trail position (the crew drop's point, {@link #dropPoint}) on a type-3 parachute with the
     * vehicle's motion ({@link #dropEntityParachute}). Otherwise nothing happens. No parachute item is used up, as in
     * the reference. No config has both hooks and {@code EnableParachuting} (the parse clears both).
     */
    void unmountSeat(Entity e) {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (e == null || info == null || v.level().isClientSide) {
            return;
        }
        if (canRepelling(e)) {
            this.lastUsedRopeIndex = Repelling.nextRope(this.lastUsedRopeIndex, info.repellingHooks.size());
            Vec3d hook = info.repellingHooks.get(this.lastUsedRopeIndex).pos;
            spawnSlider(e, dropPoint(hook).subtract(0.0, PLAYER_SLIDE_DROP, 0.0), this.lastUsedRopeIndex);
        } else if (canParachuting(e)) {
            dropEntityParachute(e, dropPoint(info.mobDropOption.pos));
        }
    }

    /** SERVER: reference {@code unmountCrew(true)} (EA:3751-3785): the first mob from seat 2 up
     *  ({@link CrewUnmount#dropSeat}; the pilot, seat 1 and players stay) leaves at the drop point on a type-3
     *  parachute. False when there was none. */
    private boolean dropOneMob(MCH_AircraftInfo info) {
        int seat = CrewUnmount.dropSeat(v.seatCount(), s -> {
            Entity e = v.seatPassenger(s);
            return e != null && !(e instanceof Player);
        });
        if (seat < 0) {
            return false;
        }
        dropEntityParachute(v.seatPassenger(seat), dropPoint(info.mobDropOption.pos));
        return true;
    }

    /**
     * Reference {@code getTransformedPosition(mobDropOption.pos, prevPosition.oldest())} (EA:3311-3313): the
     * {@code MobDropOption} point turned by the CURRENT body rotation (unlifted, as the dismount points) and added to
     * the position about 9 ticks ago, so a moving vehicle drops behind itself (faithful).
     */
    private Vec3 dropPoint(Vec3d pos) {
        org.joml.Vector3f p = new org.joml.Vector3f((float) pos.x(), (float) pos.y(), (float) pos.z())
            .rotate(v.bodyRotation(1.0F));
        return crewTrailOldest().add(p.x, p.y, p.z);
    }

    /** SERVER: reference {@code dropEntityParachute} (EA:4015-4028), after the rider is put at {@code at}: it takes the
     *  vehicle's motion, and a type-3 parachute with its yaw, that motion and its fall distance takes it on its first
     *  tick. Nothing opens for a rider a mod keeps aboard. */
    private void dropEntityParachute(Entity e, Vec3 at) {
        if (!(v.level() instanceof ServerLevel level) || !leave(e, at)) {
            return;
        }
        Vec3 m = v.getDeltaMovement();
        e.setDeltaMovement(m);
        MchParachute.deploy(level, e.getX(), e.getY(), e.getZ(), ParachuteFlight.TYPE_DROP, e, e.getYRot(), m,
            e.fallDistance);
    }

    /** Reference {@code MCH_Lib.getBlockIdY(this, 3, -depth) != 0}: the shared column probe. */
    private boolean blockBelow(int depth) {
        return GroundProbe.blockIdInColumn(v.ref, 3, -depth) != 0;
    }

    /** SERVER: a crew drop is running (diagnostics). */
    boolean isDroppingCrew() {
        return this.dropping;
    }

    /** SERVER: the trail's oldest entry, the origin of the next drop; the current position before the first tick. */
    Vec3 crewTrailOldest() {
        return this.trailSeeded ? this.trail.oldest() : v.position();
    }
}
