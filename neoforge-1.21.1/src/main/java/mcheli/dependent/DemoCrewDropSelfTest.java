package mcheli.dependent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.aircraft.MCH_PartStatus;
import mcheli.agnostic.parachute.ParachuteFlight;
import mcheli.agnostic.value.Vec3d;
import mcheli.dependent.control.ServerboundSeatUnmountPayload;
import mcheli.dependent.control.ServerboundUnmountCrewPayload;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.entity.MchParachute;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The pilot's crew unmount (reference {@code MCH_EntityAircraft.unmountCrew}, {@code startUnmountCrew},
 * {@code onUpdate_UnmountCrew} and {@code dropEntityParachute}; the {@code KeyUnmountMob} key, Y), headless, through the
 * real {@code mcheli:unmount_crew} handler, and seat parachuting (reference {@code unmount}, {@code canUnmount} and
 * {@code canParachuting}; a passenger's jump key) through the real {@code mcheli:seat_unmount} handler. Synchronous
 * checks run in {@link #start}; timed checks are {@link Probe}s, and the test ends when the last one has its verdict.
 * The first unmet requirement is the FAIL reason.
 * <ul>
 *   <li><b>D1 trail</b> — an an2 held at P for 12 ticks, then at P + (5, 0, 0): for 8 more ticks
 *       {@code crewTrailOldest()} is still the post-move position of tick 12, and on the 9th it is the first new
 *       one (the reference {@code prevPosition.oldest()}, 9 ticks old at the drop check).</li>
 *   <li><b>D2 plain unload</b> — an m1a2 on the ground with a player driver and pigs in seats 1 and 2: Y puts both off
 *       at once, each beside its own seat ({@code (x ± 3, 2, z)} of its seat, the own-seat dismount rule); the driver
 *       stays and nothing opens.</li>
 *   <li><b>D3 refusals</b> — Y from a seat-1 player (an m1a2, an an2 at altitude) and from the pilot of a wreck changes
 *       nothing.</li>
 *   <li><b>D4 an2 drop</b> — an an2 held 40 up at an attitude with pigs in seats 1-4: Y starts the drop; one pig leaves
 *       on each tick with {@code tickCount % 5 == 0}, seats 2, 3 then 4, each on a type-3 parachute whose box bottom is
 *       at the 9-tick-old position + {@code R·(0.8, 1.9321, -3.5)} (the {@code MobDropOption}; also within 0.05 of
 *       {@code crewTrailOldest() + R·pos}) with the vehicle's motion, and rides it on the next tick; the seat-1 pig
 *       stays; the next firing finds nobody and ends the drop.</li>
 *   <li><b>D5</b> — Y twice: no drop, nothing leaves.</li>
 *   <li><b>D6 mv-22 hatch</b> — during a gear switch's cooldown, Y forces the hatch open at once (the bit set, the
 *       cooldown back to 20); nobody leaves while the hatch is at 89 degrees or less (the first 59 ticks), then one pig
 *       per 10-tick firing from seat 2 up, at {@code R·(0, 0, -11.5)}; the pilot closing the hatch ends the drop.</li>
 *   <li><b>D7 low altitude</b> — an an2 8 above the ground: Y puts every mob off plainly, with no parachute. A drop
 *       started at 40 stops on the first tick after the vehicle is moved down to 8.</li>
 *   <li><b>D8 hooks</b> — an mh-6 (repelling hooks) 2 above the ground: Y puts its pigs off plainly. At 40 up Y does
 *       nothing (it is not hovering, so it may not start repelling; the {@code repel} self-test covers that).</li>
 *   <li><b>S1 seat parachuting</b> — an an2 held 40 up at an attitude, moving: the seat-2 player's
 *       {@code seat_unmount} puts him off at once, at the 9-tick-old position + {@code R·MobDropOption} (the trail's
 *       oldest entry; exact, and within 0.05 of {@code crewTrailOldest() + R·pos}), on a type-3 parachute with the
 *       plane's motion, which he rides from the next tick, 0.5 below its box bottom.</li>
 *   <li><b>S2</b> — on the same an2, seat 1, the pilot and a player not aboard send it: nothing.</li>
 *   <li><b>S3 mv-22 hatch</b> — the hatch shut: {@code canParachuting} is false and the payload does nothing (the
 *       reference client sent it and its server silently refused). The pilot opens the hatch (Z):
 *       {@code canParachuting} follows the hatch angle reaching 89, still refused at 88.5 (59 ticks), and on the 60th
 *       tick the seat-3 player goes from {@code R·(0, 0, -11.5)}.</li>
 *   <li><b>S4</b> — {@code canParachuting}, the one rule of both sides: false for seat 1, the pilot and a non-rider,
 *       true for seat 2 at altitude (S1, S2); false behind a shut hatch, true once open (S3); false within 13 blocks of
 *       the ground (S5).</li>
 *   <li><b>S5 the ground</b> — an an2 11 above the ground (the 13-row column reaches the surface): false, and the
 *       server refuses too (reference quirk 6: its server would have dropped him); 12 above: true, and he goes.</li>
 *   <li><b>S6 refusals</b> — an ah-64 gunner (seat 1) and a p180 seat-2 passenger (no {@code EnableParachuting}); an
 *       an2 wreck's seat-2 passenger, whom the rule allows but the payload refuses.</li>
 * </ul>
 * Vehicles for the timed checks are held still by the test ({@link Pin}). The keys themselves (Y, and the jump key's
 * half-second hold with its action-bar progress), the cargo hold a non-pilot's Y opens and the hatch animation on
 * screen are client-only (runClient checks).
 */
public final class DemoCrewDropSelfTest extends MchSelfTest {

    /** The an2's {@code MobDropOption} (planes/an2.txt:29) and the mv-22's (planes/mv-22.txt:9). */
    private static final Vec3d AN2_DROP = new Vec3d(0.8, 1.9321, -3.5);
    private static final Vec3d MV22_DROP = new Vec3d(0.0, 0.0, -11.5);

    private final List<ServerPlayer> players = new ArrayList<>();
    private final List<Probe> probes = new ArrayList<>();
    private final List<String> verdicts = new ArrayList<>();
    private final List<Pin> pins = new ArrayList<>();

    @Override
    protected int timeoutTicks() {
        return 400;
    }

    @Override
    protected void start() {
        try {
            startTrail();
            this.verdicts.add(testPlainUnload());
            this.verdicts.add(testRefusals());
            startAn2Drop();
            startTwice();
            startMv22Hatch();
            this.verdicts.add(testLowAltitude());
            startLowAltitudeStop();
            this.verdicts.add(testHooks());
            this.verdicts.add(testSeatRefusals());
            startSeatDrop();
            startMv22Seat();
            startSeatGround();
            LOG.info("[CREWDROP SELF-TEST] synchronous D2, D3, D7, D8, S6 checks OK; {} timed probes",
                this.probes.size());
        } catch (IllegalStateException e) {
            fail("{}", e.getMessage());
        }
    }

    @Override
    protected void tick() {
        try {
            for (Pin pin : this.pins) {
                pin.recordAndApply();
            }
            for (Iterator<Probe> it = this.probes.iterator(); it.hasNext(); ) {
                Probe p = it.next();
                if (p.step()) {
                    this.verdicts.add(p.verdict);
                    LOG.info("[CREWDROP SELF-TEST] {}", p.verdict);
                    it.remove();
                }
            }
        } catch (IllegalStateException e) {
            fail("{}", e.getMessage());
            return;
        }
        if (this.probes.isEmpty()) {
            finish(true, "{}", String.join("; ", this.verdicts));
        }
    }

    // ---- D1 ----

    private void startTrail() {
        Pin pin = pinned("an2", -36.5, 40.0, -36.5, 0.0F, 0.0F, 0.0F);
        AbstractMchVehicle v = pin.v;
        Vec3 moved = pin.pos.add(5.0, 0.0, 0.0);
        this.probes.add(new Every(1, () -> {
            int n = v.tickCount;
            if (n == 12) {
                pin.pos = moved; // the next tick starts 5 blocks east
                pin.apply();
            } else if (n > 12 && n <= 20) {
                req(v.crewTrailOldest().equals(pin.post(12)), "D1: " + (n - 12) + " ticks after the move the trail's "
                    + "oldest is still tick 12's position " + fmt(pin.post(12)) + ", got " + fmt(v.crewTrailOldest()));
            } else if (n == 21) {
                req(v.crewTrailOldest().equals(pin.post(13)) && v.crewTrailOldest().distanceTo(pin.post(12)) > 4.9,
                    "D1: 9 ticks after the move the trail's oldest is tick 13's position " + fmt(pin.post(13))
                        + ", got " + fmt(v.crewTrailOldest()));
                v.discard();
                return "D1 trail (oldest = 9 ticks old: tick 12's for 8 ticks after a 5-block move, then tick 13's)";
            }
            return null;
        }));
    }

    // ---- D2, D3 ----

    private String testPlainUnload() {
        AbstractMchVehicle tank = spawn(MchRegistries.TANK.get(), "m1a2", -12.5, 0.0, -36.5);
        ServerPlayer driver = player("d2driver");
        Pig a = pig(-12.5, 5.0, -36.5);
        Pig b = pig(-12.5, 5.0, -36.5);
        req(board(driver, tank, 0) && board(a, tank, 1) && board(b, tank, 2),
            "D2: a player driver and pigs in seats 1 and 2 board the m1a2");
        Vec3 at1 = seatDismount(tank, 1);
        Vec3 at2 = seatDismount(tank, 2);
        int mark = spawned().size();
        unmount(tank, driver);
        req(a.getVehicle() == null && b.getVehicle() == null, "D2: Y puts both pigs off at once");
        req(a.position().distanceTo(at1) <= 0.01 && b.position().distanceTo(at2) <= 0.01,
            "D2: each pig stands beside its own seat, " + fmt(at1) + " / " + fmt(at2) + ", got " + fmt(a.position())
                + " / " + fmt(b.position()));
        req(driver.getVehicle() == tank && tank.seatIndexOf(driver) == 0 && !tank.isDroppingCrew(),
            "D2: the driver stays and no drop runs");
        req(parachutesSince(mark).isEmpty(), "D2: nothing opens");
        driver.stopRiding();
        tank.discard();
        LOG.info("[CREWDROP SELF-TEST] D2 plain unload OK: seat points {} / {}", fmt(at1), fmt(at2));
        return "D2 plain unload (m1a2: both pigs beside their own seats, the driver stays, no parachute)";
    }

    private String testRefusals() {
        // A seat-1 player's Y: nothing, on a tank and on an an2 at altitude.
        AbstractMchVehicle tank = spawn(MchRegistries.TANK.get(), "m1a2", 12.5, 0.0, -36.5);
        ServerPlayer driver = player("d3driver");
        ServerPlayer gunner = player("d3gunner");
        Pig pig = pig(12.5, 5.0, -36.5);
        req(board(driver, tank, 0) && board(gunner, tank, 1) && board(pig, tank, 2),
            "D3: a driver, a seat-1 player and a seat-2 pig board the m1a2");
        unmount(tank, gunner);
        req(pig.getVehicle() == tank && tank.seatIndexOf(pig) == 2 && gunner.getVehicle() == tank,
            "D3: the seat-1 player's Y leaves the pig in seat 2");
        driver.stopRiding();
        gunner.stopRiding();
        pig.stopRiding();
        tank.discard();

        AbstractMchVehicle an2 = spawn(MchRegistries.PLANE.get(), "an2", 12.5, 40.0, -36.5);
        ServerPlayer pilot = player("d3pilot");
        ServerPlayer seat1 = player("d3seat1");
        Pig p2 = pig(12.5, 45.0, -36.5);
        req(board(pilot, an2, 0) && board(seat1, an2, 1) && board(p2, an2, 2),
            "D3: an an2 pilot, a seat-1 player and a seat-2 pig board");
        int mark = spawned().size();
        unmount(an2, seat1);
        req(!an2.isDroppingCrew() && p2.getVehicle() == an2 && parachutesSince(mark).isEmpty(),
            "D3: the an2 seat-1 player's Y starts no drop");
        pilot.stopRiding();
        seat1.stopRiding();
        p2.stopRiding();
        an2.discard();

        // A wreck: a pilot forced back aboard cannot unload.
        AbstractMchVehicle wreck = spawn(MchRegistries.PLANE.get(), "an2", 12.5, 40.0, -36.5);
        req(wreck.hurt(this.level.damageSources().generic(), 99999.0F) && wreck.isDestroyed(),
            "D3: the an2 is destroyed");
        ServerPlayer wp = player("d3wreck");
        Pig wpig = pig(12.5, 45.0, -36.5);
        req(board(wp, wreck, 0) && board(wpig, wreck, 2), "D3: a pilot and a pig are forced back aboard the wreck");
        unmount(wreck, wp);
        req(!wreck.isDroppingCrew() && wpig.getVehicle() == wreck, "D3: Y on a wreck changes nothing");
        wp.stopRiding();
        wpig.stopRiding();
        wreck.discard();
        LOG.info("[CREWDROP SELF-TEST] D3 refusals OK");
        return "D3 refusals (a seat-1 player on an m1a2 and an an2, a wreck's pilot)";
    }

    // ---- D4, D5 ----

    private void startAn2Drop() {
        Pin pin = pinned("an2", -36.5, 40.0, -12.5, 40.0F, -5.0F, 8.0F);
        AbstractMchVehicle an2 = pin.v;
        ServerPlayer pilot = player("d4pilot");
        req(board(pilot, an2, 0), "D4: a player pilot boards the an2");
        List<Pig> pigs = crew(an2, 4, -36.5, -12.5);
        Drops drops = new Drops(pigs);
        int[] state = {0}; // 0 waiting for Y, 1 dropping, 2 ended
        int[] lastDrop = {-1};
        this.probes.add(new Every(1, () -> {
            int n = an2.tickCount;
            if (state[0] == 0) {
                if (n < 12) {
                    return null;
                }
                req(!an2.isDroppingCrew() && an2.seatCount() == 5, "D4: the an2 has 5 seats and no drop runs");
                unmount(an2, pilot);
                req(an2.isDroppingCrew(), "D4: Y at altitude starts the drop");
                drops.markFrom(spawned().size());
                state[0] = 1;
                return null;
            }
            drops.checkRiding();
            List<MchParachute> fresh = drops.scan();
            if (state[0] == 1) {
                for (MchParachute c : fresh) {
                    int seat = drops.seatOf(c);
                    int expectSeat = 2 + drops.count() - 1;
                    req(n % 5 == 0, "D4: a pig leaves only on a tick with tickCount % 5 == 0, got " + n);
                    req(lastDrop[0] != n, "D4: one pig per firing, two at tick " + n);
                    req(seat == expectSeat, "D4: the pigs go in seat order, expected seat " + expectSeat + ", got "
                        + seat);
                    checkDrop(c, pin, AN2_DROP, "D4 seat " + seat);
                    lastDrop[0] = n;
                }
                drops.requireNoUntrackedLeave("D4");
                req(pigs.get(0).getVehicle() == an2 && an2.seatIndexOf(pigs.get(0)) == 1, "D4: the seat-1 pig stays");
                if (drops.count() == 3 && n >= lastDrop[0] + 5) {
                    req(!an2.isDroppingCrew(), "D4: the firing after the last pig finds nobody and ends the drop");
                    state[0] = 2;
                } else {
                    req(an2.isDroppingCrew(), "D4: the drop runs on until a firing finds nobody (tick " + n + ")");
                }
                return null;
            }
            // One more tick: the last pig rides its parachute; then done.
            pilot.stopRiding();
            pigs.get(0).stopRiding();
            an2.discard();
            return "D4 an2 drop (one pig per 5-tick firing, seats 2, 3, 4, type-3 parachutes at the 9-tick-old position "
                + "+ R·MobDropOption with the plane's motion, ridden next tick; seat 1 stays; ends at tick "
                + (lastDrop[0] + 5) + ")";
        }));
    }

    private void startTwice() {
        Pin pin = pinned("an2", -12.5, 40.0, -12.5, 0.0F, 0.0F, 0.0F);
        AbstractMchVehicle an2 = pin.v;
        ServerPlayer pilot = player("d5pilot");
        req(board(pilot, an2, 0), "D5: a player pilot boards the an2");
        List<Pig> pigs = crew(an2, 4, -12.5, -12.5);
        Drops drops = new Drops(pigs);
        this.probes.add(new Every(1, () -> {
            int n = an2.tickCount;
            if (n < 12) {
                return null;
            }
            if (n == 12) {
                unmount(an2, pilot);
                unmount(an2, pilot);
                req(!an2.isDroppingCrew(), "D5: Y twice: no drop runs");
                drops.markFrom(spawned().size());
                return null;
            }
            req(drops.scan().isEmpty() && !an2.isDroppingCrew(), "D5: nothing opens after Y twice (tick " + n + ")");
            for (Pig p : pigs) {
                req(p.getVehicle() == an2, "D5: every pig stays aboard (tick " + n + ")");
            }
            if (n < 30) {
                return null;
            }
            pilot.stopRiding();
            an2.discard();
            return "D5 Y twice (no drop, nobody leaves in 18 ticks)";
        }));
    }

    // ---- D6 ----

    private void startMv22Hatch() {
        Pin pin = pinned("mv-22", 12.5, 40.0, -12.5, 120.0F, 0.0F, 0.0F);
        AbstractMchVehicle mv = pin.v;
        ServerPlayer pilot = player("d6pilot");
        req(board(pilot, mv, 0), "D6: a player pilot boards the mv-22");
        List<Pig> pigs = crew(mv, 5, 12.5, -12.5);
        Drops drops = new Drops(pigs);
        int[] yTick = {-1};
        int[] closedAt = {-1};
        this.probes.add(new Every(1, () -> {
            int n = mv.tickCount;
            if (yTick[0] < 0) {
                if (n == 12) {
                    req(mv.haveHatch() && mv.canFoldHatch() && (partStatus(mv) & MCH_PartStatus.HATCH) == 0,
                        "D6: the mv-22's hatch is shut and may open");
                    mv.foldLandingGear();
                    req(mv.modeSwitchCooldownTicks() == 20, "D6: the gear switch starts the 20-tick cooldown, got "
                        + mv.modeSwitchCooldownTicks());
                } else if (n == 17) {
                    req(mv.modeSwitchCooldownTicks() == 15 && !mv.canFoldHatch(),
                        "D6: 5 ticks into the cooldown the hatch may not switch, cooldown "
                            + mv.modeSwitchCooldownTicks());
                    unmount(mv, pilot);
                    req((partStatus(mv) & MCH_PartStatus.HATCH) != 0 && mv.modeSwitchCooldownTicks() == 20
                            && mv.isDroppingCrew(),
                        "D6: Y forces the hatch open at once (bit set) and restarts the cooldown at 20, got "
                            + mv.modeSwitchCooldownTicks());
                    drops.markFrom(spawned().size());
                    yTick[0] = n;
                }
                return null;
            }
            drops.checkRiding();
            float rot = mv.hatchFactor() * 90.0F; // the hatch angle (its travel fraction of the 90-degree swing)
            List<MchParachute> fresh = drops.scan();
            if (closedAt[0] < 0) {
                for (MchParachute c : fresh) {
                    int seat = drops.seatOf(c);
                    req(rot > 89.0F, "D6: nobody leaves while the hatch is at 89 or less, got a drop at " + rot);
                    req(n % 10 == 0 && n >= yTick[0] + 60, "D6: drops only on 10-tick firings once the hatch is open "
                        + "(60 ticks after Y), got tick " + n);
                    int expectSeat = 2 + drops.count() - 1;
                    req(seat == expectSeat, "D6: seat order, expected " + expectSeat + ", got " + seat);
                    if (drops.count() == 1) {
                        int first = (yTick[0] + 60 + 9) / 10 * 10;
                        req(n == first, "D6: the first drop is the first 10-tick firing from the 60th tick after Y ("
                            + first + "), got " + n);
                    }
                    checkDrop(c, pin, MV22_DROP, "D6 seat " + seat);
                }
                if (n == yTick[0] + 59) {
                    req(Math.abs(rot - 88.5F) < 1.0e-4 && drops.count() == 0,
                        "D6: 59 ticks after Y the hatch is at 88.5 and nobody has left, got " + rot);
                }
                drops.requireNoUntrackedLeave("D6");
                req(mv.isDroppingCrew(), "D6: the drop runs while the hatch is open (tick " + n + ")");
                if (drops.count() == 2) {
                    req(mv.canUnfoldHatch(), "D6: the hatch is at rest open and the cooldown ready");
                    mv.switchHatch(pilot);
                    req((partStatus(mv) & MCH_PartStatus.HATCH) == 0 && !mv.isDroppingCrew(),
                        "D6: the pilot closing the hatch ends the drop");
                    closedAt[0] = n;
                }
                return null;
            }
            req(fresh.isEmpty() && !mv.isDroppingCrew(), "D6: nothing more leaves after the hatch closed (tick " + n
                + ")");
            req(pigs.get(0).getVehicle() == mv && pigs.get(3).getVehicle() == mv && pigs.get(4).getVehicle() == mv,
                "D6: the seat-1 pig and the pigs of seats 4 and 5 stay aboard");
            if (n < closedAt[0] + 25) {
                return null;
            }
            pilot.stopRiding();
            for (Pig p : pigs) {
                p.stopRiding();
            }
            mv.discard();
            return "D6 mv-22 hatch (forced open through a running cooldown, back to 20; no drop at <= 89 for 59 ticks; "
                + "first drop at tick " + (yTick[0] + 60 + 9) / 10 * 10 + " (Y at " + yTick[0] + "), then every 10; "
                + "closing the hatch ends it)";
        }));
    }

    // ---- D7, D8 ----

    private String testLowAltitude() {
        AbstractMchVehicle an2 = spawn(MchRegistries.PLANE.get(), "an2", 36.5, 8.0, -12.5);
        ServerPlayer pilot = player("d7pilot");
        req(board(pilot, an2, 0), "D7: a player pilot boards the an2 8 above the ground");
        List<Pig> pigs = crew(an2, 4, 36.5, -12.5);
        Vec3[] at = new Vec3[pigs.size()];
        for (int i = 0; i < pigs.size(); i++) {
            at[i] = seatDismount(an2, i + 1);
        }
        int mark = spawned().size();
        unmount(an2, pilot);
        req(!an2.isDroppingCrew() && parachutesSince(mark).isEmpty(), "D7: 8 above the ground Y starts no drop");
        for (int i = 0; i < pigs.size(); i++) {
            Pig p = pigs.get(i);
            req(p.getVehicle() == null && p.position().distanceTo(at[i]) <= 0.01,
                "D7: the seat-" + (i + 1) + " pig is put off beside its seat " + fmt(at[i]) + ", got "
                    + fmt(p.position()));
        }
        req(pilot.getVehicle() == an2, "D7: the pilot stays");
        pilot.stopRiding();
        an2.discard();
        return "D7 low altitude (8 up: every mob off plainly beside its seat, no parachute)";
    }

    private void startLowAltitudeStop() {
        Pin pin = pinned("an2", 36.5, 40.0, -36.5, 0.0F, 0.0F, 0.0F);
        AbstractMchVehicle an2 = pin.v;
        ServerPlayer pilot = player("d7stop");
        req(board(pilot, an2, 0), "D7: a player pilot boards the an2 40 up");
        List<Pig> pigs = crew(an2, 4, 36.5, -36.5);
        Drops drops = new Drops(pigs);
        int[] moved = {-1};
        this.probes.add(new Every(1, () -> {
            int n = an2.tickCount;
            if (n < 12) {
                return null;
            }
            if (n == 12) {
                unmount(an2, pilot);
                req(an2.isDroppingCrew(), "D7: Y 40 up starts the drop");
                drops.markFrom(spawned().size());
                return null;
            }
            drops.checkRiding();
            drops.scan();
            drops.requireNoUntrackedLeave("D7");
            if (moved[0] < 0) {
                if (drops.count() == 1) {
                    pin.pos = pin.pos.add(0.0, -32.0, 0.0); // the next tick starts 8 above the ground
                    pin.apply();
                    moved[0] = n;
                }
                return null;
            }
            req(!an2.isDroppingCrew(), "D7: the first tick 8 above the ground ends the drop (tick " + n + ")");
            req(drops.count() == 1 && pigs.get(2).getVehicle() == an2 && pigs.get(3).getVehicle() == an2,
                "D7: nobody else leaves, got " + drops.count() + " drops");
            if (n < moved[0] + 12) {
                return null;
            }
            pilot.stopRiding();
            for (Pig p : pigs) {
                p.stopRiding();
            }
            an2.discard();
            return "D7 drop stopped by the ground (one pig at 40 up, then nothing once 8 above the ground)";
        }));
    }

    private String testHooks() {
        AbstractMchVehicle low = spawn(MchRegistries.HELI.get(), "mh-6", 12.5, 2.0, 12.5);
        req(low.hostInfo().haveRepellingHook() && !low.hostInfo().isEnableParachuting,
            "D8: the mh-6 has repelling hooks and no parachuting");
        ServerPlayer pilot = player("d8low");
        req(board(pilot, low, 0), "D8: a player pilot boards the mh-6 2 above the ground");
        List<Pig> pigs = crew(low, 3, 12.5, 12.5);
        Vec3[] at = new Vec3[pigs.size()];
        for (int i = 0; i < pigs.size(); i++) {
            at[i] = seatDismount(low, i + 1);
        }
        unmount(low, pilot);
        for (int i = 0; i < pigs.size(); i++) {
            Pig p = pigs.get(i);
            req(p.getVehicle() == null && p.position().distanceTo(at[i]) <= 0.01,
                "D8: within 4 blocks of the ground the seat-" + (i + 1) + " pig is put off beside its seat "
                    + fmt(at[i]) + ", got " + fmt(p.position()));
        }
        pilot.stopRiding();
        low.discard();

        AbstractMchVehicle high = spawn(MchRegistries.HELI.get(), "mh-6", 36.5, 40.0, 12.5);
        ServerPlayer hp = player("d8high");
        req(board(hp, high, 0), "D8: a player pilot boards the mh-6 40 up");
        List<Pig> crew = crew(high, 2, 36.5, 12.5);
        int mark = spawned().size();
        unmount(high, hp);
        req(!high.isDroppingCrew() && parachutesSince(mark).isEmpty() && crew.get(0).getVehicle() == high
                && crew.get(1).getVehicle() == high,
            "D8: in the air a hooked heli that is not hovering does nothing on Y (it may not repel)");
        hp.stopRiding();
        for (Pig p : crew) {
            p.stopRiding();
        }
        high.discard();
        return "D8 hooks (mh-6: 2 up every mob off plainly; 40 up nothing)";
    }

    // ---- S1-S6: seat parachuting ----

    private void startSeatDrop() {
        Pin pin = pinned("an2", -36.5, 40.0, 12.5, 30.0F, -4.0F, 6.0F);
        AbstractMchVehicle an2 = pin.v;
        ServerPlayer pilot = player("s1pilot");
        ServerPlayer seat1 = player("s1seat1");
        ServerPlayer pass = player("s1pass");
        ServerPlayer outside = player("s1outside");
        req(board(pilot, an2, 0) && board(seat1, an2, 1) && board(pass, an2, 2),
            "S1: a pilot, a seat-1 player and a seat-2 player board the an2");
        MchParachute[] chute = {null};
        this.probes.add(new Every(1, () -> {
            int n = an2.tickCount;
            if (n < 12) {
                return null;
            }
            if (chute[0] == null) {
                req(an2.canParachuting(pass) && !an2.canParachuting(seat1) && !an2.canParachuting(pilot)
                        && !an2.canParachuting(outside),
                    "S4: on the an2 40 up canParachuting is true for seat 2 only (not seat 1, the pilot, a non-rider)");
                int mark = spawned().size();
                seatUnmount(an2, seat1);
                seatUnmount(an2, pilot);
                seatUnmount(an2, outside);
                req(an2.seatIndexOf(seat1) == 1 && an2.seatIndexOf(pilot) == 0 && an2.seatIndexOf(pass) == 2
                        && parachutesSince(mark).isEmpty(),
                    "S2: seat 1, the pilot and a player not aboard send seat_unmount: nothing happens");
                // A moving plane: the parachute and its rider take this motion (the pin puts it back next tick).
                an2.setDeltaMovement(0.3, -0.1, 0.2);
                Vec3 motion = an2.getDeltaMovement();
                seatUnmount(an2, pass);
                List<MchParachute> cs = parachutesSince(mark);
                req(cs.size() == 1, "S1: the seat-2 player's seat_unmount opens one parachute, got " + cs.size());
                req(an2.seatIndexOf(pass) == -1 && an2.seatPassenger(2) == null && an2.seatIndexOf(seat1) == 1
                        && an2.seatIndexOf(pilot) == 0,
                    "S1: he is off at once and seat 2 is free; seat 1 and the pilot stay");
                checkSeatDrop(cs.get(0), pin, AN2_DROP, pass, motion, "S1 an2 seat 2");
                chute[0] = cs.get(0);
                return null;
            }
            checkRidesSeatChute(pass, chute[0], "S1");
            pilot.stopRiding();
            seat1.stopRiding();
            an2.discard();
            return "S1 seat parachuting (an2 seat 2: off at once at the 9-tick-old position + R·MobDropOption, type 3, "
                + "the plane's motion, ridden next tick); S2 (seat 1, the pilot, a non-rider: nothing)";
        }));
    }

    private void startMv22Seat() {
        Pin pin = pinned("mv-22", 36.5, 40.0, 36.5, -60.0F, 0.0F, 0.0F);
        AbstractMchVehicle mv = pin.v;
        ServerPlayer pilot = player("s3pilot");
        ServerPlayer pass = player("s3pass");
        req(board(pilot, mv, 0) && board(pass, mv, 3), "S3: a pilot and a seat-3 player board the mv-22");
        int[] opened = {-1};
        MchParachute[] chute = {null};
        this.probes.add(new Every(1, () -> {
            int n = mv.tickCount;
            if (opened[0] < 0) {
                if (n < 12) {
                    return null;
                }
                req(mv.haveHatch() && (partStatus(mv) & MCH_PartStatus.HATCH) == 0 && mv.canFoldHatch(),
                    "S3: the mv-22's hatch is shut and may open");
                req(!mv.canParachuting(pass), "S4: behind the shut hatch canParachuting is false");
                int mark = spawned().size();
                seatUnmount(mv, pass);
                req(mv.seatIndexOf(pass) == 3 && parachutesSince(mark).isEmpty(),
                    "S3: behind the shut hatch seat_unmount does nothing");
                mv.switchHatch(pilot);
                req((partStatus(mv) & MCH_PartStatus.HATCH) != 0, "S3: the pilot's hatch switch opens it");
                opened[0] = n;
                return null;
            }
            if (chute[0] == null) {
                float rot = mv.hatchFactor() * 90.0F;
                boolean may = mv.canParachuting(pass);
                req(may == (rot >= 89.0F), "S4: canParachuting follows the hatch reaching 89 degrees, at " + rot
                    + " it is " + may + " (tick " + n + ")");
                if (n == opened[0] + 59) {
                    req(Math.abs(rot - 88.5F) < 1.0e-4 && !may,
                        "S3: 59 ticks after the switch the hatch is at 88.5, got " + rot);
                    int mark = spawned().size();
                    seatUnmount(mv, pass);
                    req(mv.seatIndexOf(pass) == 3 && parachutesSince(mark).isEmpty(),
                        "S3: at 88.5 degrees the server still refuses");
                } else if (n == opened[0] + 60) {
                    req(Math.abs(rot - 90.0F) < 1.0e-4 && may,
                        "S3: on the 60th tick the hatch is open (90), got " + rot);
                    Vec3 motion = mv.getDeltaMovement();
                    int mark = spawned().size();
                    seatUnmount(mv, pass);
                    List<MchParachute> cs = parachutesSince(mark);
                    req(cs.size() == 1 && mv.seatIndexOf(pass) == -1,
                        "S3: once the hatch is open the seat-3 player goes, got " + cs.size() + " parachutes");
                    checkSeatDrop(cs.get(0), pin, MV22_DROP, pass, motion, "S3 mv-22 seat 3");
                    chute[0] = cs.get(0);
                }
                return null;
            }
            checkRidesSeatChute(pass, chute[0], "S3");
            pilot.stopRiding();
            mv.discard();
            return "S3 mv-22 hatch (shut: false and refused; open: canParachuting from 89, refused at 88.5, the seat-3 "
                + "player goes on the 60th tick after the switch from R·(0, 0, -11.5))";
        }));
    }

    private void startSeatGround() {
        Pin low = pinned("an2", -12.5, 11.0, 36.5, 0.0F, 0.0F, 0.0F);
        Pin high = pinned("an2", 12.5, 12.0, 36.5, 0.0F, 0.0F, 0.0F);
        requireOpenColumn(low.v);
        requireOpenColumn(high.v);
        ServerPlayer lp = player("s5low");
        ServerPlayer hp = player("s5high");
        req(board(lp, low.v, 2) && board(hp, high.v, 2), "S5: a seat-2 player boards each an2");
        MchParachute[] chute = {null};
        this.probes.add(new Every(1, () -> {
            int n = high.v.tickCount;
            if (n < 12) {
                return null;
            }
            if (chute[0] == null) {
                req(!low.v.canParachuting(lp), "S4: 11 above the ground canParachuting is false");
                int mark = spawned().size();
                seatUnmount(low.v, lp);
                req(low.v.seatIndexOf(lp) == 2 && parachutesSince(mark).isEmpty(),
                    "S5: 11 above the ground the server refuses too (one rule; the reference server dropped him)");
                req(high.v.canParachuting(hp), "S4: 12 above the ground canParachuting is true");
                Vec3 motion = high.v.getDeltaMovement();
                seatUnmount(high.v, hp);
                List<MchParachute> cs = parachutesSince(mark);
                req(cs.size() == 1 && high.v.seatIndexOf(hp) == -1,
                    "S5: 12 above the ground he goes, got " + cs.size() + " parachutes");
                checkSeatDrop(cs.get(0), high, AN2_DROP, hp, motion, "S5 an2 12 up");
                chute[0] = cs.get(0);
                return null;
            }
            checkRidesSeatChute(hp, chute[0], "S5");
            lp.stopRiding();
            low.v.discard();
            high.v.discard();
            return "S5 the ground (11 up: false and refused, the reference server's quirk fixed; 12 up: he goes)";
        }));
    }

    private String testSeatRefusals() {
        // No EnableParachuting: an ah-64 gunner (seat 1) and a p180 seat-2 passenger.
        AbstractMchVehicle heli = spawn(MchRegistries.HELI.get(), "ah-64", -36.5, 40.0, 36.5);
        ServerPlayer hp = player("s6heli");
        ServerPlayer hg = player("s6gunner");
        req(board(hp, heli, 0) && board(hg, heli, 1), "S6: a pilot and a gunner board the ah-64");
        req(!heli.hostInfo().isEnableParachuting && !heli.canParachuting(hg),
            "S6: the ah-64 gunner may not parachute");
        int mark = spawned().size();
        seatUnmount(heli, hg);
        req(heli.seatIndexOf(hg) == 1 && parachutesSince(mark).isEmpty(),
            "S6: the ah-64 gunner's seat_unmount does nothing");
        hp.stopRiding();
        hg.stopRiding();
        heli.discard();

        AbstractMchVehicle p180 = spawn(MchRegistries.PLANE.get(), "p180", -36.5, 40.0, 36.5);
        ServerPlayer pp = player("s6p180");
        req(board(pp, p180, 2), "S6: a player boards seat 2 of the p180");
        req(!p180.hostInfo().isEnableParachuting && !p180.canParachuting(pp),
            "S6: no EnableParachuting, no seat parachuting");
        seatUnmount(p180, pp);
        req(p180.seatIndexOf(pp) == 2 && parachutesSince(mark).isEmpty(),
            "S6: the p180 passenger's seat_unmount does nothing");
        pp.stopRiding();
        p180.discard();

        // A wreck: the rule (which does not look at the wreck) allows it, the payload refuses, as the client does.
        AbstractMchVehicle wreck = spawn(MchRegistries.PLANE.get(), "an2", -36.5, 40.0, 36.5);
        req(wreck.hurt(this.level.damageSources().generic(), 99999.0F) && wreck.isDestroyed(),
            "S6: the an2 is destroyed");
        ServerPlayer wp = player("s6wreck");
        req(board(wp, wreck, 2), "S6: a seat-2 player is forced back aboard the wreck");
        req(wreck.canParachuting(wp), "S6: 40 up the wreck's seat 2 passes canParachuting itself");
        seatUnmount(wreck, wp);
        req(wreck.seatIndexOf(wp) == 2 && parachutesSince(mark).isEmpty(),
            "S6: on a wreck seat_unmount does nothing");
        wp.stopRiding();
        wreck.discard();
        LOG.info("[CREWDROP SELF-TEST] S6 refusals OK");
        return "S6 refusals (an ah-64 gunner, a p180 seat-2 passenger, an an2 wreck's seat-2 passenger)";
    }

    /** {@code c} is the type-3 parachute that a passenger's {@code seat_unmount}, sent in this test tick (after the
     *  level tick n, the pin re-applied), opened: its box bottom is at the trail's oldest entry (the position put at
     *  tick n - 8, the put of tick n being in) plus the PINNED attitude's {@code R·pos}, exact; within 0.05 of
     *  {@code crewTrailOldest() + R·pos}; the passenger stands exactly there, off the vehicle and waiting to mount it,
     *  and both have the vehicle's motion. */
    private void checkSeatDrop(MchParachute c, Pin pin, Vec3d pos, ServerPlayer p, Vec3 motion, String what) {
        AbstractMchVehicle v = pin.v;
        int n = v.tickCount;
        req(c.parachuteType() == ParachuteFlight.TYPE_DROP, what + ": a type-3 parachute, got " + c.parachuteType());
        Vec3 r = body(pin.yaw, pin.pitch, pin.roll, pos.x(), pos.y(), pos.z());
        Vec3 exact = pin.post(n - 8).add(r);
        Vec3 loose = v.crewTrailOldest().add(r);
        req(c.position().distanceTo(exact) <= 1.0e-4, what + ": the parachute's box bottom is at the position of tick "
            + (n - 8) + " + R·" + pos + " = " + fmt(exact) + ", got " + fmt(c.position()));
        req(c.position().distanceTo(loose) <= 0.05, what + ": within 0.05 of crewTrailOldest() + R·pos = " + fmt(loose)
            + ", got " + fmt(c.position()));
        req(c.pendingUser() == p && p.getVehicle() == null && p.position().distanceTo(c.position()) <= 1.0e-9,
            what + ": the passenger is off the vehicle at the parachute, waiting to mount it, got "
                + fmt(p.position()));
        req(c.getDeltaMovement().equals(motion) && p.getDeltaMovement().equals(motion),
            what + ": the parachute and the passenger have the vehicle's motion " + fmt(motion) + ", got "
                + fmt(c.getDeltaMovement()) + " / " + fmt(p.getDeltaMovement()));
        LOG.info("[CREWDROP SELF-TEST] {} at tick {}: {} (origin {}, motion {})", what, n, fmt(c.position()),
            fmt(pin.post(n - 8)), fmt(motion));
    }

    /** The tick after a seat drop: the passenger rides his parachute, a player 0.5 below its box bottom. */
    private static void checkRidesSeatChute(ServerPlayer p, MchParachute c, String what) {
        req(p.getVehicle() == c && c.pendingUser() == null,
            what + ": the passenger rides his parachute from the next tick");
        req(Math.abs(p.getY() - (c.getY() - 0.5)) <= 1.0e-6,
            what + ": a player hangs 0.5 below the box bottom, " + fmt(c.position()) + " / " + fmt(p.position()));
    }

    /** The 3 x 3 column the seat-parachuting probe reads under {@code v}: air from the origin's row (the first free
     *  row above the surface) up to {@code v}'s row, over a surface block. The 11/12 boundary of S5 rests on it. */
    private void requireOpenColumn(AbstractMchVehicle v) {
        int px = Mth.floor(v.getX() + 0.5);
        int pz = Mth.floor(v.getZ() + 0.5);
        int top = Mth.floor(v.getY() + 0.5);
        int ground = this.origin.getY() - 1;
        req(!this.level.getBlockState(new BlockPos(px, ground, pz)).isAir(), "S5: a surface block under the an2");
        for (int y = ground + 1; y <= top; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    req(this.level.getBlockState(new BlockPos(px + x, y, pz + z)).isAir(),
                        "S5: the column under the an2 is open at " + (px + x) + ", " + y + ", " + (pz + z));
                }
            }
        }
    }

    // ---- the drop check ----

    /** {@code c} is a type-3 parachute at the drop point: the position put {@code 9} ticks before this tick plus the
     *  body rotation (this tick's post-move attitude) of {@code pos}, exact; within 0.05 of
     *  {@code crewTrailOldest() + R·pos} (the plan's looser check); with the vehicle's post-move motion. */
    private void checkDrop(MchParachute c, Pin pin, Vec3d pos, String what) {
        AbstractMchVehicle v = pin.v;
        int n = v.tickCount;
        req(c.parachuteType() == ParachuteFlight.TYPE_DROP, what + ": a type-3 parachute, got " + c.parachuteType());
        Vec3 r = body(pin.lastYaw, pin.lastPitch, pin.lastRoll, pos.x(), pos.y(), pos.z());
        Vec3 exact = pin.post(n - 9).add(r);
        Vec3 loose = v.crewTrailOldest().add(r);
        req(c.position().distanceTo(exact) <= 1.0e-4, what + ": the parachute's box bottom is at the position of tick "
            + (n - 9) + " + R·" + pos + " = " + fmt(exact) + ", got " + fmt(c.position()));
        req(c.position().distanceTo(loose) <= 0.05, what + ": within 0.05 of crewTrailOldest() + R·pos = " + fmt(loose)
            + ", got " + fmt(c.position()));
        // The pig was put at the same point; it may already have moved this tick as a free entity (it ticks after
        // the vehicle), so only its departure is checked here, and its ride from the next tick (Drops.checkRiding).
        req(c.pendingUser() instanceof Pig p && !(p.getVehicle() instanceof AbstractMchVehicle),
            what + ": the pig is off the vehicle, waiting to mount its parachute");
        // The parachute opens with the vehicle's motion. Its neighbours that tick after the vehicle in the same level
        // tick (its own pig standing in it, the previous drop's parachute and pig just below) may push it apart before
        // this check (the 1.7.10 applyEntityCollision push, at most 0.05 a horizontal axis each); every push is
        // horizontal, so the vertical motion is exact.
        Vec3 dm = c.getDeltaMovement().subtract(pin.lastMotion);
        req(Math.abs(dm.y) <= 1.0e-9 && Math.abs(dm.x) <= 0.1501 && Math.abs(dm.z) <= 0.1501,
            what + ": the parachute has the vehicle's motion " + fmt(pin.lastMotion) + " (plus the horizontal pushes "
                + "of its neighbours), got " + fmt(c.getDeltaMovement()));
        LOG.info("[CREWDROP SELF-TEST] {} at tick {}: {} (origin {}, motion {}, push {})", what, n, fmt(c.position()),
            fmt(pin.post(n - 9)), fmt(pin.lastMotion), fmt(dm));
    }

    /** The parachutes opened for one vehicle's pigs, scanned from {@link #spawned()} tick by tick (a parachute names
     *  its pig only until its first tick). */
    private final class Drops {
        final List<Pig> pigs;
        final Map<MchParachute, Pig> byChute = new HashMap<>();
        final List<MchParachute> order = new ArrayList<>();
        final Map<MchParachute, Integer> seenAt = new HashMap<>();
        int scanned;
        final Map<Pig, Integer> seatAtStart = new HashMap<>();

        Drops(List<Pig> pigs) {
            this.pigs = pigs;
            for (int i = 0; i < pigs.size(); i++) {
                this.seatAtStart.put(pigs.get(i), i + 1);
            }
        }

        void markFrom(int index) {
            this.scanned = index;
        }

        /** The new parachutes of this vehicle's pigs since the last scan. */
        List<MchParachute> scan() {
            List<MchParachute> fresh = new ArrayList<>();
            List<Entity> all = spawned();
            for (; this.scanned < all.size(); this.scanned++) {
                if (all.get(this.scanned) instanceof MchParachute c && c.pendingUser() instanceof Pig p
                    && this.pigs.contains(p)) {
                    this.byChute.put(c, p);
                    this.order.add(c);
                    this.seenAt.put(c, ticks());
                    fresh.add(c);
                }
            }
            return fresh;
        }

        int count() {
            return this.order.size();
        }

        int seatOf(MchParachute c) {
            return this.seatAtStart.get(this.byChute.get(c));
        }

        /** Every parachute seen on an earlier tick is ridden by its pig. */
        void checkRiding() {
            for (MchParachute c : this.order) {
                if (this.seenAt.get(c) < ticks()) {
                    req(this.byChute.get(c).getVehicle() == c, "the pig of seat " + seatOf(c)
                        + " rides its parachute from the tick after it opened");
                }
            }
        }

        /** No pig is off its vehicle without a parachute of its own. */
        void requireNoUntrackedLeave(String what) {
            for (Pig p : this.pigs) {
                if (!(p.getVehicle() instanceof AbstractMchVehicle) && !this.byChute.containsValue(p)) {
                    throw new IllegalStateException(what + ": the seat-" + this.seatAtStart.get(p)
                        + " pig left without a parachute");
                }
            }
        }
    }

    // ---- helpers ----

    /** A timed check: {@link #step} runs once per test tick and returns true once {@link #verdict} is set. */
    private abstract static class Probe {
        String verdict;

        abstract boolean step();
    }

    /** A check that runs every test tick from {@code from}; its body returns null to go on, or the verdict. */
    private final class Every extends Probe {
        private final int from;
        private final Supplier<String> body;

        Every(int from, Supplier<String> body) {
            this.from = from;
            this.body = body;
        }

        @Override
        boolean step() {
            if (ticks() < this.from) {
                return false;
            }
            this.verdict = this.body.get();
            return this.verdict != null;
        }
    }

    /**
     * A vehicle held at its pose: the level still ticks it, and every test tick first records where the tick left it
     * (the post-move position the trail took, by the vehicle's {@code tickCount}, and the post-move motion and attitude
     * the drop used), then puts it back (position, zero motion, yaw, pitch, roll).
     */
    private static final class Pin {
        final AbstractMchVehicle v;
        Vec3 pos;
        final float yaw;
        final float pitch;
        final float roll;
        final Map<Integer, Vec3> postByTick = new HashMap<>();
        Vec3 lastMotion = Vec3.ZERO;
        float lastYaw;
        float lastPitch;
        float lastRoll;

        Pin(AbstractMchVehicle v, float yaw, float pitch, float roll) {
            this.v = v;
            this.pos = v.position();
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
        }

        void recordAndApply() {
            if (this.v.isRemoved()) {
                return;
            }
            this.postByTick.put(this.v.tickCount, this.v.position());
            this.lastMotion = this.v.getDeltaMovement();
            this.lastYaw = this.v.getYRot();
            this.lastPitch = this.v.getXRot();
            this.lastRoll = this.v.getRollAngle();
            apply();
        }

        void apply() {
            this.v.setPos(this.pos.x, this.pos.y, this.pos.z);
            this.v.setDeltaMovement(Vec3.ZERO);
            this.v.setYRot(this.yaw);
            this.v.yRotO = this.yaw;
            this.v.setXRot(this.pitch);
            this.v.xRotO = this.pitch;
            this.v.setRollAngle(this.roll);
        }

        Vec3 post(int tick) {
            Vec3 p = this.postByTick.get(tick);
            req(p != null, "the pinned " + this.v.configName() + " has a recorded position for tick " + tick);
            return p;
        }
    }

    /** A plane of {@code config} in the world at the offset, at the given attitude, pinned there. */
    private Pin pinned(String config, double dx, double dy, double dz, float yaw, float pitch, float roll) {
        AbstractMchVehicle v = spawn(MchRegistries.PLANE.get(), config, dx, dy, dz);
        Pin pin = new Pin(v, yaw, pitch, roll);
        pin.apply();
        this.pins.add(pin);
        return pin;
    }

    /** {@code n} no-AI pigs boarded in seats 1 to {@code n}, in order. */
    private List<Pig> crew(AbstractMchVehicle v, int n, double dx, double dz) {
        List<Pig> out = new ArrayList<>();
        for (int s = 1; s <= n; s++) {
            Pig p = pig(dx, v.getY() - this.origin.getY() + 5.0, dz);
            req(board(p, v, s), "a pig boards seat " + s + " of the " + v.configName());
            out.add(p);
        }
        return out;
    }

    private Pig pig(double dx, double dy, double dz) {
        Pig pig = EntityType.PIG.create(this.level);
        req(pig != null, "a pig can be created");
        pig.setNoAi(true);
        pig.setPos(this.origin.getX() + dx, this.origin.getY() + dy, this.origin.getZ() + dz);
        this.level.addFreshEntity(pig);
        return pig;
    }

    /** The real {@code mcheli:unmount_crew} handler, as if {@code sender} had pressed Y in {@code v}. */
    private static void unmount(AbstractMchVehicle v, ServerPlayer sender) {
        ServerboundUnmountCrewPayload.handle(new ServerboundUnmountCrewPayload(v.getId()), payloadContext(sender));
    }

    /** The real {@code mcheli:seat_unmount} handler, as if {@code sender} had held the jump key in {@code v}. */
    private static void seatUnmount(AbstractMchVehicle v, ServerPlayer sender) {
        ServerboundSeatUnmountPayload.handle(new ServerboundSeatUnmountPayload(v.getId()), payloadContext(sender));
    }

    private List<MchParachute> parachutesSince(int index) {
        List<MchParachute> out = new ArrayList<>();
        List<Entity> all = spawned();
        for (int i = index; i < all.size(); i++) {
            if (all.get(i) instanceof MchParachute c) {
                out.add(c);
            }
        }
        return out;
    }

    /** Seat {@code s}'s plain dismount point (reference {@code setUnmountPosition}, EA:3787-3802): the config
     *  {@code UnmountPosition}, else {@code (x ± 3 outward, 2, z)} of the seat, through the body rotation. */
    private static Vec3 seatDismount(AbstractMchVehicle v, int s) {
        MCH_AircraftInfo info = v.hostInfo();
        double x;
        double y;
        double z;
        if (info.unmountPosition != null) {
            x = info.unmountPosition.x();
            y = info.unmountPosition.y();
            z = info.unmountPosition.z();
        } else {
            Vec3d p = info.seatList.get(s).pos;
            x = p.x() >= 0 ? p.x() + 3.0 : p.x() - 3.0;
            y = 2.0;
            z = p.z();
        }
        return v.position().add(body(v.getYRot(), v.getXRot(), v.getRollAngle(), x, y, z));
    }

    /** {@code (x, y, z)} through the body rotation {@code Ry(-yaw)·Rx(pitch)·Rz(roll)} (JOML floats, as the entity's
     *  {@code bodyRotation}), not translated. */
    private static Vec3 body(float yaw, float pitch, float roll, double x, double y, double z) {
        Quaternionf q = new Quaternionf()
            .rotateY((float) Math.toRadians(-yaw))
            .rotateX((float) Math.toRadians(pitch))
            .rotateZ((float) Math.toRadians(roll));
        Vector3f p = new Vector3f((float) x, (float) y, (float) z).rotate(q);
        return new Vec3(p.x, p.y, p.z);
    }

    /** The synced part status word (package-private on the entity), by reflection. */
    @SuppressWarnings("unchecked")
    private static int partStatus(AbstractMchVehicle v) {
        try {
            java.lang.reflect.Field f = AbstractMchVehicle.class.getDeclaredField("DATA_PART_STATUS");
            f.setAccessible(true);
            return v.getEntityData().get((EntityDataAccessor<Integer>) f.get(null));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read DATA_PART_STATUS: " + e);
        }
    }

    private static String fmt(Vec3 p) {
        return String.format(Locale.ROOT, "(%.4f, %.4f, %.4f)", p.x, p.y, p.z);
    }

    private <T extends AbstractMchVehicle> T spawn(EntityType<T> type, String config, double dx, double dy,
                                                   double dz) {
        T v = type.create(this.level);
        req(v != null, "the entity type for " + config + " creates");
        v.setConfigName(config);
        req(v.hostInfo() != null && config.equalsIgnoreCase(v.hostInfo().name), "the " + config + " config is loaded");
        v.setPos(this.origin.getX() + dx, this.origin.getY() + dy, this.origin.getZ() + dz);
        this.level.addFreshEntity(v);
        return v;
    }

    private ServerPlayer player(String label) {
        ServerPlayer p = newTestPlayer(label);
        p.setPos(this.origin.getX() + 0.5, this.origin.getY() + 20.0, this.origin.getZ() + 0.5);
        this.players.add(p);
        return p;
    }

    /** Seat {@code p} in {@code seat} of {@code v} (a forced mount, as a scripted crew would board). */
    private static boolean board(Entity p, AbstractMchVehicle v, int seat) {
        v.preferSeat(p, seat);
        return p.startRiding(v, true) && v.seatIndexOf(p) == seat;
    }

    @Override
    protected void cleanup() {
        for (ServerPlayer p : this.players) {
            if (p.isPassenger()) {
                p.stopRiding();
            }
        }
    }

    private static void req(boolean cond, String what) {
        if (!cond) {
            throw new IllegalStateException(what);
        }
    }
}
