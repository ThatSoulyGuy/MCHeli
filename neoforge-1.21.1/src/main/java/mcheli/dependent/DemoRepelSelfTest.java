package mcheli.dependent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.aircraft.MCH_PartStatus;
import mcheli.agnostic.helicopter.MCH_HeliInfoManager;
import mcheli.agnostic.info.MCH_InfoManagerBase;
import mcheli.agnostic.plane.MCP_PlaneInfoManager;
import mcheli.agnostic.sim.GroundProbe;
import mcheli.agnostic.tank.MCH_TankInfoManager;
import mcheli.agnostic.value.Vec3d;
import mcheli.agnostic.vehicle.MCH_VehicleInfoManager;
import mcheli.dependent.control.MchControlState;
import mcheli.dependent.control.ServerboundControlPayload;
import mcheli.dependent.control.ServerboundHoverPayload;
import mcheli.dependent.control.ServerboundSeatUnmountPayload;
import mcheli.dependent.control.ServerboundUnmountCrewPayload;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.entity.MchHelicopter;
import mcheli.dependent.entity.MchRopeSlider;
import mcheli.dependent.port.NeoWorldView;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Rope repelling for the mob crew and the player rope slide (reference {@code MCH_EntityAircraft.startRepelling},
 * {@code stopRepelling}, {@code canStartRepelling}, {@code onUpdate_Repelling}, {@code onUpdate_UnmountCrewRepelling},
 * {@code unmountEntityRepelling}, {@code getRopePos}, {@code canRepelling} and {@code unmount}, and the slider
 * {@code MCH_EntityHide}), headless, through the real {@code mcheli:unmount_crew} (Y), {@code mcheli:hover_toggle} and
 * {@code mcheli:seat_unmount} (a passenger's held jump key) handlers. Synchronous checks run in
 * {@link #start}; timed checks are {@link Probe}s on helicopters held still by the test ({@link Pin}), and the test ends
 * when the last one has its verdict. The first unmet requirement is the FAIL reason.
 * <ul>
 *   <li><b>R0 census</b> — over every loaded config: exactly 11 hooks in the ch47, ka-27, mh-6, mh-60g, mi-24ps, sh-3
 *       and sh-60, every interval 30; parachuting only on the an2 and the mv-22, never together with hooks. The slider
 *       type (1 x 1, saved, not summonable, 13 chunks, every 3 ticks) and its synced-data ids 8 and 9; a slider refuses
 *       an unforced mount (a minecart's pickup), a forced one still works.</li>
 *   <li><b>R1 gates</b> — an mh-60g 20.5 up, still: Y with hover off does nothing; with hover on (the real payload)
 *       but pitched 5 degrees, or rolled 4, nothing; after moving a block a tick for 10 ticks, nothing.</li>
 *   <li><b>R2 start</b> — still again, Y with throttle up/down and left/right held: part-status bit 5 is set, the four
 *       keys are let go, the tick counter is 0.</li>
 *   <li><b>R3 rope length</b> — the server's rope grows 0.25 a tick (-10 after 40 ticks) and stops at minus the air
 *       below the heli ({@code GroundProbe.airBelow}, 21 cells at 20.5 up); the counter runs to 60 and stays.</li>
 *   <li><b>R4 cadence</b> — no pig leaves in the first 60 ticks; then on every tick that is a multiple of 30 one pig
 *       per hook (seats 2 and 3, then 4 and 5), each on a slider of that rope whose box bottom is at the 9-tick-old
 *       position + {@code R·hook - (0, 2.5, 0)}, which it rides from the next tick 0.2 above the box bottom; the seat-1
 *       pig stays. Every tick each slider is under its hook ({@code ropePos}), with no fall distance on its rider, and
 *       falls exactly as the reference's {@code onUpdateServer} (0.3 a tick at steady state); on the ground it lets go
 *       (the pig 0.7 above the ground, the reference's lift) and the pig lands at full health.</li>
 *   <li><b>R5 rope end</b> — 60.5 up the rope stops at 50; a slider lets go once it is more than 55 below its hook,
 *       about 7 above the ground, and its pig is left in the air.</li>
 *   <li><b>R6 stops</b> — each of Y again, the hover payload, the hover payload in gunner mode (an mi-24ps: the switch
 *       is refused, the stop is not; a server-path check, since the client sends no hover key in gunner mode), the
 *       heli's destruction and the last player getting off clears the bit at once; every slider lets go on the next
 *       tick, and both rope counters are 0 by then.</li>
 *   <li><b>R14 drive keys</b> (the Y-again heli) — throttle up and left sent through the real {@code mcheli:control}
 *       handler on 9 ticks while repelling are let go by each next vehicle tick, before they move the hull (still on
 *       at least 5 of the 9 ticks: the hover turbulence kicks it about one tick in 25, the keys would on every one);
 *       sent again once the ropes are in, they are kept (reference MCH_AircraftClientTickHandler:166-170).</li>
 *   <li><b>R15 culling</b> (the Y-again heli) — while repelling the culling box reaches below every rope's foot; once
 *       the ropes are in it is the plain box inflated by 16 again.</li>
 *   <li><b>R7</b> — an mh-60g hovering 2 above the ground: Y puts the mobs off plainly beside their seats; no rope.</li>
 *   <li><b>R8 saves</b> — a heli saved while repelling has no bit 5 in {@code PartStatus} and loads not repelling; a
 *       slider saved with its pig ({@code id}, its passenger, no fields of its own) reloads with no parent and no rope,
 *       falls with the pig aboard and lets go on the ground.</li>
 *   <li><b>R9 too early</b> — an mh-60g 20.5 up (yaw -60, pitch 1, roll 0.5) with a player pilot, players in seats 1,
 *       4 and 5 and NoAI pigs in seats 2 and 3: before repelling, and for the first 50 ticks of it, no rider may slide
 *       ({@code canRepelling} false) and the seat payload (and {@code unmountSeat} itself) does nothing.</li>
 *   <li><b>R10 the slide</b> — on the 51st tick the seat-1 player's payload puts him 4 below rope 1's point
 *       ({@code (0 + 1) % 2}) on the trail position the test tick sees, the slider's box bottom 0.5 lower
 *       ({@code crewTrailOldest() + R·hook - 4.5}); from the next tick he rides it with his feet 0.3 below its box
 *       bottom.</li>
 *   <li><b>R11 the round robin</b> — the seat-4 player right after takes rope 0; the mob cadence's first firing then
 *       lowers the pigs of seats 2 and 3 down ropes 0 and 1, so the seat-5 player sent on the same tick takes rope 0
 *       (the rope after the last one used by anyone).</li>
 *   <li><b>R12 down</b> — every tick each player rides his slider under its hook with no fall distance and full
 *       health; on the ground the slider lets him go 0.2 above it (the reference's 0.5 lift), unhurt. (A test player
 *       is not in the world, so nothing ticks him once he is off: he is not seen to land.)</li>
 *   <li><b>R13 the pilot</b> — the pilot may never slide: {@code canRepelling} is false for him, and his seat payload
 *       and {@code unmountSeat} do nothing (the heli repels on).</li>
 * </ul>
 * The rope render, the client's own rope length (0.3 a tick), the sliders seen from a client, the held jump key with
 * its action-bar progress and the pilot's free look while repelling are client-only (runClient checks).
 */
public final class DemoRepelSelfTest extends MchSelfTest {

    /** The mh-60g's hooks (helicopters/mh-60g.txt:59-60) and the mi-24ps's (helicopters/mi-24ps.txt). */
    private static final Vec3d[] MH60G_HOOKS = {new Vec3d(1.64, 2.05, -2.30), new Vec3d(-1.64, 2.05, -2.30)};

    /** The bundled configs with {@code AddRepellingHook}, by hook count (reference census, spec 7.1). */
    private static final Map<String, Integer> EXPECTED_HOOKS = new TreeMap<>(Map.of(
        "ch47", 2, "ka-27", 1, "mh-6", 2, "mh-60g", 2, "mi-24ps", 2, "sh-3", 1, "sh-60", 1));

    private final List<ServerPlayer> players = new ArrayList<>();
    private final List<Probe> probes = new ArrayList<>();
    private final List<String> verdicts = new ArrayList<>();
    private final List<Pin> pins = new ArrayList<>();

    @Override
    protected int timeoutTicks() {
        return 800;
    }

    @Override
    protected void start() {
        try {
            this.verdicts.add(testCensus());
            this.verdicts.add(testLowGround());
            startMain();
            startRopeEnd();
            startStop("Y again", "r6y", "mh-60g", -36.5, -12.5);
            startStop("hover", "r6hover", "mh-60g", -12.5, -12.5);
            startStop("gunner", "r6gunner", "mi-24ps", 12.5, -12.5);
            startStop("destroyed", "r6wreck", "mh-60g", 36.5, -12.5);
            startStop("last player", "r6last", "mh-60g", -36.5, 12.5);
            startPlayerSlide();
            LOG.info("[REPEL SELF-TEST] synchronous R0, R7 checks OK; {} timed probes", this.probes.size());
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
                    LOG.info("[REPEL SELF-TEST] {}", p.verdict);
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

    // ---- R0 ----

    private String testCensus() {
        Map<String, Integer> hooks = new TreeMap<>();
        Set<String> parachuting = new TreeSet<>();
        StringBuilder counts = new StringBuilder();
        census(hooks, parachuting, counts, "heli", MCH_HeliInfoManager.getInstance(), MchRegistries.HELI.get());
        census(hooks, parachuting, counts, "plane", MCP_PlaneInfoManager.getInstance(), MchRegistries.PLANE.get());
        census(hooks, parachuting, counts, "tank", MCH_TankInfoManager.getInstance(), MchRegistries.TANK.get());
        census(hooks, parachuting, counts, "vehicle", MCH_VehicleInfoManager.getInstance(),
            MchRegistries.VEHICLE.get());
        LOG.info("[REPEL SELF-TEST] R0 configs {}; hooks {}; parachuting {}", counts, hooks, parachuting);
        req(hooks.equals(EXPECTED_HOOKS), "R0: hooks on exactly " + EXPECTED_HOOKS + ", got " + hooks);
        req(parachuting.equals(new TreeSet<>(List.of("an2", "mv-22"))),
            "R0: EnableParachuting only on the an2 and the mv-22, got " + parachuting);

        EntityType<MchRopeSlider> t = MchRegistries.ROPE_SLIDER.get();
        req(t.getWidth() == 1.0F && t.getHeight() == 1.0F, "R0: mcheli:rope_slider is 1 x 1, got " + t.getWidth()
            + " x " + t.getHeight());
        req(t.canSerialize() && !t.canSummon(), "R0: the rope slider is saved and not summonable");
        req(t.updateInterval() == 3 && t.clientTrackingRange() == 13,
            "R0: tracked to 13 chunks every 3 ticks, got " + t.clientTrackingRange() + " / " + t.updateInterval());
        int idIndex = accessorId(MchRopeSlider.class, "DATA_ROPE_INDEX");
        int idParent = accessorId(MchRopeSlider.class, "DATA_PARENT");
        req(idIndex == 8 && idParent == 9, "R0: the slider's synced-data ids are 8 (rope index) and 9 (parent), got "
            + idIndex + " / " + idParent);
        // A 1.21 minecart picks up any pushable entity by an unforced startRiding; the slider never rides by one.
        MchRopeSlider lone = t.create(this.level);
        req(lone != null, "R0: a slider creates");
        Minecart cart = new Minecart(this.level, this.origin.getX() + 0.5, this.origin.getY() + 30.0,
            this.origin.getZ() + 0.5);
        lone.setPos(cart.position());
        req(!lone.startRiding(cart) && !lone.isPassenger(), "R0: a slider refuses an unforced mount (a minecart's pickup)");
        req(lone.startRiding(cart, true) && lone.getVehicle() == cart, "R0: a forced mount still works");
        lone.stopRiding();
        int total = hooks.values().stream().mapToInt(Integer::intValue).sum();
        return "R0 census (" + total + " hooks in " + hooks.size() + " helis, every interval 30; parachuting "
            + parachuting + " only; slider ids 8/9, no unforced mount; " + counts + " configs)";
    }

    @SuppressWarnings("rawtypes")
    private void census(Map<String, Integer> hooks, Set<String> parachuting, StringBuilder counts, String label,
                        MCH_InfoManagerBase mgr, EntityType<? extends AbstractMchVehicle> type) {
        Map map = mgr.getMap();
        req(map != null && !map.isEmpty(), "R0: the " + label + " configs are loaded");
        AbstractMchVehicle v = type.create(this.level);
        req(v != null, "R0: the " + label + " entity creates");
        int n = 0;
        for (Object key : map.keySet()) {
            String name = String.valueOf(key);
            v.setConfigName(name);
            MCH_AircraftInfo info = v.hostInfo();
            req(info != null && name.equalsIgnoreCase(info.name), "R0: the " + name + " config resolves");
            if (info.haveRepellingHook()) {
                hooks.put(name, info.repellingHooks.size());
                for (MCH_AircraftInfo.RepellingHook h : info.repellingHooks) {
                    req(h.interval == 30, "R0: every bundled hook fires every 30 ticks, " + name + " has " + h.interval);
                }
            }
            if (info.isEnableParachuting) {
                parachuting.add(name);
            }
            req(!(info.haveRepellingHook() && info.isEnableParachuting), "R0: " + name + " has hooks and parachuting");
            n++;
        }
        counts.append(counts.length() > 0 ? " " : "").append(label).append('=').append(n);
    }

    // ---- R7 ----

    private String testLowGround() {
        AbstractMchVehicle low = spawn("mh-60g", 36.5, 2.0, 36.5);
        ServerPlayer pilot = player("r7pilot");
        req(board(pilot, low, 0), "R7: a player pilot boards the mh-60g 2 above the ground");
        List<Pig> pigs = crew(low, 3, 36.5, 36.5, true);
        hover(low, pilot);
        req(((MchHelicopter) low).isHoveringMode() && low.canStartRepelling(),
            "R7: the low mh-60g hovers, and the repelling start gate itself would allow it");
        Vec3[] at = new Vec3[pigs.size()];
        for (int i = 0; i < pigs.size(); i++) {
            at[i] = seatDismount(low, i + 1);
        }
        int mark = spawned().size();
        unmount(low, pilot);
        req(!low.isRepelling(), "R7: within 4 blocks of the ground Y does not repel");
        for (int i = 0; i < pigs.size(); i++) {
            Pig p = pigs.get(i);
            req(p.getVehicle() == null && p.position().distanceTo(at[i]) <= 0.01,
                "R7: the seat-" + (i + 1) + " pig is put off beside its seat " + fmt(at[i]) + ", got "
                    + fmt(p.position()));
        }
        req(slidersSince(mark).isEmpty(), "R7: no rope slider");
        pilot.stopRiding();
        low.discard();
        return "R7 low ground (mh-60g hovering 2 up: Y puts the mobs off beside their seats, no rope)";
    }

    // ---- R1-R4, R8a ----

    private void startMain() {
        Pin pin = pinned("mh-60g", -36.5, 20.5, -36.5, 30.0F, 1.5F, -2.0F);
        AbstractMchVehicle v = pin.v;
        ServerPlayer pilot = player("r1pilot");
        req(board(pilot, v, 0), "R1: a player pilot boards the mh-60g");
        List<Pig> pigs = crew(v, 5, -36.5, -36.5, false); // pigs with AI: they land, and would take fall damage
        Ropes ropes = new Ropes(v, pigs);
        int[] at = {-1}; // the vehicle tick Y started repelling on
        float[] cap = {0.0F};
        int[] firings = {0};
        this.probes.add(new Every(1, () -> {
            int n = v.tickCount;
            if (n < 12) {
                return null;
            }
            if (n == 12) {
                int mark = spawned().size();
                unmount(v, pilot);
                req(!v.isRepelling() && !v.isHovering(), "R1: Y with hover off does not repel");
                hover(v, pilot);
                req(((MchHelicopter) v).isHoveringMode() && v.isHovering(), "R1: the hover payload turns hover on");
                pin.pitch = 5.0F;
                pin.apply();
                unmount(v, pilot);
                req(!v.isRepelling(), "R1: pitched 5 degrees, Y does not repel");
                pin.pitch = 1.5F;
                pin.roll = 4.0F;
                pin.apply();
                unmount(v, pilot);
                req(!v.isRepelling(), "R1: rolled 4 degrees, Y does not repel");
                pin.roll = -2.0F;
                pin.apply();
                req(slidersSince(mark).isEmpty() && aboard(v, pigs) == 5, "R1: nobody leaves");
                return null;
            }
            if (n <= 22) {
                pin.pos = pin.pos.add(1.0, 0.0, 0.0); // a block a tick for 10 ticks
                pin.apply();
                if (n == 22) {
                    double drift = v.crewTrailOldest().distanceTo(v.position());
                    req(!v.canStartRepelling() && drift > 5.0, "R1: moving, the start gate refuses (drift "
                        + String.format(Locale.ROOT, "%.3f", drift) + ")");
                    unmount(v, pilot);
                    req(!v.isRepelling(), "R1: moving a block a tick, Y does not repel");
                }
                return null;
            }
            if (n < 34) {
                return null;
            }
            if (n == 34) {
                double drift = v.crewTrailOldest().distanceTo(v.position());
                req(v.canStartRepelling(), "R2: hovering, level and still for 12 ticks, the start gate allows it "
                    + "(drift " + String.format(Locale.ROOT, "%.4f", drift) + ")");
                MchControlState cs = v.controlState(0);
                cs.throttleUp = true;
                cs.throttleDown = true;
                cs.moveLeft = true;
                cs.moveRight = true;
                ropes.markFrom(spawned().size());
                unmount(v, pilot);
                req(v.isRepelling() && (partStatus(v) & MCH_PartStatus.REPELLING) == 32,
                    "R2: Y starts repelling (part-status bit 5)");
                req(!cs.throttleUp && !cs.throttleDown && !cs.moveLeft && !cs.moveRight,
                    "R2: the pilot's throttle and sideways keys are let go");
                req(v.tickRepelling() == 0 && v.ropesLength() == 0.0F, "R2: the counter and the rope start at 0");
                at[0] = n;
                int air = GroundProbe.airBelow(new NeoWorldView(this.level), pin.pos.x, pin.pos.y, pin.pos.z);
                cap[0] = -Math.min(50, air);
                req(air == 21, "R3: 21 cells of air below the heli 20.5 up, got " + air);
                // R8a: a save while repelling drops the bit.
                CompoundTag tag = new CompoundTag();
                v.saveWithoutId(tag);
                req((tag.getInt("PartStatus") & MCH_PartStatus.REPELLING) == 0,
                    "R8: PartStatus is saved without bit 5, got " + tag.getInt("PartStatus"));
                AbstractMchVehicle copy = MchRegistries.HELI.get().create(this.level);
                req(copy != null, "R8: a heli to load into");
                copy.load(tag);
                req("mh-60g".equalsIgnoreCase(copy.configName()) && !copy.isRepelling(),
                    "R8: the saved heli loads not repelling");
                LOG.info("[REPEL SELF-TEST] R2 start at tick {} (drift {}), rope cap {}", n,
                    String.format(Locale.ROOT, "%.4f", drift), cap[0]);
                return null;
            }
            int k = n - at[0];
            req(v.isRepelling(), "R4: the heli repels on (tick " + n + ")");
            // R3: the server's rope, exactly, and the counter.
            float expect = Math.max(-0.25F * k, cap[0]);
            req(v.ropesLength() == expect, "R3: after " + k + " ticks the server rope is " + expect + ", got "
                + v.ropesLength());
            req(v.tickRepelling() == Math.min(k, 60), "R3: the counter is " + Math.min(k, 60) + " after " + k
                + " ticks, got " + v.tickRepelling());
            if (k == 40) {
                LOG.info("[REPEL SELF-TEST] R3 rope after 40 ticks {}", v.ropesLength());
            }
            // R4: the cadence.
            List<MchRopeSlider> fresh = ropes.scan();
            if (k <= 60) {
                req(fresh.isEmpty() && aboard(v, pigs) == 5, "R4: nobody leaves in the rope-out time (tick " + k + ")");
            } else if (n % 30 == 0) {
                int expect2 = Math.max(0, Math.min(2, 4 - 2 * firings[0])); // 4 pigs from seat 2 up, 2 hooks
                req(fresh.size() == expect2, "R4: firing " + (firings[0] + 1) + " at tick " + n + " lowers " + expect2
                    + ", got " + fresh.size());
                for (int i = 0; i < fresh.size(); i++) {
                    MchRopeSlider s = fresh.get(i);
                    int seat = 2 + 2 * firings[0] + i;
                    req(s.ropeIndex() == i && ropes.seatOf(s) == seat, "R4: rope " + i + " takes the pig of seat "
                        + seat + ", got rope " + s.ropeIndex() + " seat " + ropes.seatOf(s));
                    checkSpawn(s, pin, MH60G_HOOKS[i], "R4 seat " + seat);
                }
                firings[0]++;
            } else {
                req(fresh.isEmpty(), "R4: a pig leaves only on a multiple of 30, got one at tick " + n);
            }
            ropes.checkEveryTick("R4");
            req(pigs.get(0).getVehicle() == v && v.seatIndexOf(pigs.get(0)) == 1, "R4: the seat-1 pig stays");
            if (firings[0] >= 3 && ropes.allLanded()) {
                req(ropes.count() == 4, "R4: four pigs went down, got " + ropes.count());
                pilot.stopRiding();
                pigs.get(0).stopRiding();
                v.discard();
                return "R1 gates (hover off, pitch 5, roll 4, moving: no repelling); R2 start (bit 5, keys let go); "
                    + "R3 rope (0.25 a tick to -" + (int) -cap[0] + ", the air below); R4 cadence (none in 60 ticks, "
                    + "then 2 per 30-tick firing, seats 2-5, sliders at the 9-tick-old position + R·hook - 2.5, "
                    + "under the hook, the reference fall, the pigs landed unhurt; seat 1 stays); R8 heli save";
            }
            return null;
        }));
    }

    // ---- R5 ----

    private void startRopeEnd() {
        Pin pin = pinned("mh-60g", -12.5, 60.5, -36.5, 0.0F, 0.0F, 0.0F);
        AbstractMchVehicle v = pin.v;
        ServerPlayer pilot = player("r5pilot");
        req(board(pilot, v, 0), "R5: a player pilot boards the mh-60g 60 up");
        List<Pig> pigs = crew(v, 3, -12.5, -36.5, true);
        Ropes ropes = new Ropes(v, pigs);
        Map<MchRopeSlider, Double> lastDrop = new HashMap<>();
        int[] released = {0};
        this.probes.add(new Every(1, () -> {
            int n = v.tickCount;
            if (n < 12) {
                return null;
            }
            if (n == 12) {
                hover(v, pilot);
                ropes.markFrom(spawned().size());
                unmount(v, pilot);
                req(v.isRepelling(), "R5: Y starts repelling 60 up");
                return null;
            }
            ropes.scan();
            for (MchRopeSlider s : ropes.order) {
                if (lastDrop.containsKey(s) && lastDrop.get(s) == null) {
                    continue; // released already
                }
                double ropeY = v.ropePos(s.ropeIndex()).y;
                if (!s.isRemoved()) {
                    lastDrop.put(s, Math.abs(s.getY() + MchRopeSlider.Y_OFFSET - ropeY));
                    continue;
                }
                // Released this tick: it rose 0.5 first (onGroundAndDead), so it let go at getY() - 0.5.
                double dropAtRelease = Math.abs(s.getY() - 0.5 + MchRopeSlider.Y_OFFSET - ropeY);
                Double before = lastDrop.get(s);
                Entity pig = ropes.riderOf(s);
                req(v.ropesLength() == -50.0F, "R5: the rope stops at 50, got " + v.ropesLength());
                req(before != null && before <= 55.0 + 0.05 && dropAtRelease > 55.0 - 0.05,
                    "R5: a slider lets go once more than 55 below its hook, at " + dropAtRelease + " (a tick before "
                        + before + ")");
                req(pig.getVehicle() == null && pig.getY() - this.origin.getY() > 3.0
                        && Math.abs(pig.getY() - (s.getY() + MchRopeSlider.MOB_FEET)) <= 1.0e-6,
                    "R5: its pig is left in the air where it hung, " + fmt(pig.position()));
                LOG.info("[REPEL SELF-TEST] R5 rope {} let go at tick {}, {} below the hook, {} above the ground",
                    s.ropeIndex(), n, String.format(Locale.ROOT, "%.3f", dropAtRelease),
                    String.format(Locale.ROOT, "%.3f", s.getY() - 0.5 - this.origin.getY()));
                lastDrop.put(s, null);
                released[0]++;
            }
            if (released[0] == 2) {
                pilot.stopRiding();
                pigs.get(0).stopRiding();
                v.discard();
                return "R5 rope end (the rope stops at 50; both sliders let go more than 55 below their hooks, the "
                    + "pigs left in the air)";
            }
            req(n < 400, "R5: both sliders let go by tick 400");
            return null;
        }));
    }

    // ---- R6, R8b ----

    private void startStop(String how, String label, String config, double dx, double dz) {
        Pin pin = pinned(config, dx, 20.5, dz, 0.0F, 0.0F, 0.0F);
        AbstractMchVehicle v = pin.v;
        ServerPlayer pilot = player(label);
        req(board(pilot, v, 0), "R6 " + how + ": a player pilot boards the " + config);
        List<Pig> pigs = crew(v, 3, dx, dz, true);
        Ropes ropes = new Ropes(v, pigs);
        boolean gunner = how.equals("gunner");
        boolean saveSlider = how.equals("Y again");
        MchRopeSlider[] reloaded = {null};
        Entity[] reloadedPig = {null};
        int[] stoppedAt = {-1};
        int[] still = {0}; // R14: the ticks the hull did not move with the drive keys held
        int driveBits = ServerboundControlPayload.THROTTLE_UP | ServerboundControlPayload.MOVE_LEFT;
        this.probes.add(new Every(1, () -> {
            int n = v.tickCount;
            if (n < 12) {
                return null;
            }
            if (n == 12) {
                if (gunner) {
                    v.switchGunnerMode(true);
                    req(v.isGunnerModeActive() && v.isHovering() && !((MchHelicopter) v).isHoveringMode(),
                        "R6 gunner: the mi-24ps pilot is in gunner mode, not hover mode");
                } else {
                    hover(v, pilot);
                }
                ropes.markFrom(spawned().size());
                unmount(v, pilot);
                req(v.isRepelling(), "R6 " + how + ": Y starts repelling");
                return null;
            }
            List<MchRopeSlider> fresh = ropes.scan();
            if (stoppedAt[0] < 0 && n < 95) {
                if (n == 92 && saveSlider) {
                    reloaded[0] = reloadSlider(ropes, reloadedPig);
                }
                if (saveSlider && n >= 51 && n <= 59) {
                    // R14: the keys sent on the test tick before are let go by the vehicle's tick, before its move.
                    // The hovering flight model kicks the hull at random about one tick in 25 (the reference
                    // turbulence), so the hull must be still on most of the 9 ticks; with the keys it moves on each.
                    MchControlState cs = v.controlState(0);
                    req(!cs.throttleUp && !cs.throttleDown && !cs.moveLeft && !cs.moveRight,
                        "R14: while repelling the vehicle's tick lets the pilot's drive keys go (tick " + n + ")");
                    still[0] += horizontal(pin.post(n).subtract(pin.pos)) <= 1.0e-9 ? 1 : 0;
                    if (n == 59) {
                        req(still[0] >= 5, "R14: the drive keys do not move the hull while repelling: still on "
                            + still[0] + " of 9 ticks");
                    }
                }
                if (saveSlider && n >= 50 && n <= 58) {
                    control(v, pilot, driveBits); // R14: the pilot's W and A through the real control handler
                }
                if (saveSlider && n == 90) {
                    // R15: the rope is 19.5 long by now, its foot below the plain box inflated by 16.
                    AABB cull = v.getBoundingBoxForCulling();
                    int hooks = v.hostInfo().repellingHooks.size();
                    for (int i = 0; i < hooks; i++) {
                        double foot = v.ropePos(i).y + v.ropesLength();
                        req(cull.minY <= foot && foot < v.getBoundingBox().minY - 16.0,
                            "R15: the culling box reaches rope " + i + "'s foot " + foot + ", box bottom " + cull.minY);
                    }
                }
                return null;
            }
            if (stoppedAt[0] < 0) {
                req(ropes.count() >= 1 && ropes.alive() >= 1, "R6 " + how + ": sliders are out before the stop");
                switch (how) {
                    case "Y again" -> unmount(v, pilot);
                    case "hover", "gunner" -> hover(v, pilot);
                    case "destroyed" -> req(v.hurt(this.level.damageSources().generic(), 99999.0F) && v.isDestroyed(),
                        "R6 destroyed: the heli is destroyed");
                    default -> pilot.stopRiding();
                }
                req(!v.isRepelling() && (partStatus(v) & MCH_PartStatus.REPELLING) == 0,
                    "R6 " + how + ": the stop clears the bit at once");
                if (how.equals("hover")) {
                    req(!((MchHelicopter) v).isHoveringMode(), "R6 hover: the payload also left hover mode");
                }
                if (gunner) {
                    req(!((MchHelicopter) v).isHoveringMode() && v.isGunnerModeActive(),
                        "R6 gunner: the hover switch itself is refused in gunner mode");
                }
                if (saveSlider) {
                    control(v, pilot, driveBits); // R14: the same keys once the ropes are in
                }
                stoppedAt[0] = n;
                return null;
            }
            if (saveSlider && n == stoppedAt[0] + 1) {
                MchControlState cs = v.controlState(0);
                req(cs.throttleUp && cs.moveLeft, "R14: once the ropes are in the pilot's drive keys are kept");
                cs.clearMomentary();
                req(v.getBoundingBoxForCulling().equals(v.getBoundingBox().inflate(16.0)),
                    "R15: once the ropes are in the culling box is the plain one");
            }
            req(fresh.isEmpty(), "R6 " + how + ": no slider after the stop");
            req(ropes.alive() == 0, "R6 " + how + ": every slider let go on the next tick");
            for (MchRopeSlider s : ropes.order) {
                req(ropes.riderOf(s).getVehicle() == null, "R6 " + how + ": its pig is free");
            }
            req(v.ropesLength() == 0.0F && v.tickRepelling() == 0,
                "R6 " + how + ": both rope counters are 0 on the next tick, got " + v.ropesLength() + " / "
                    + v.tickRepelling());
            if (saveSlider) {
                // R8b: the reloaded slider has fallen with its pig aboard and let go on the ground (it never had a
                // parent to stop it).
                MchRopeSlider r = reloaded[0];
                if (!r.isRemoved()) {
                    req(r.parentVehicle() == null && reloadedPig[0].getVehicle() == r,
                        "R8: the reloaded slider falls with its pig aboard");
                    req(n < 200, "R8: the reloaded slider lands by tick 200");
                    return null;
                }
                req(reloadedPig[0].getVehicle() == null
                        && Math.abs(r.getY() - 0.5 - this.origin.getY()) <= 1.0e-6,
                    "R8: the reloaded slider let go on the ground, at " + fmt(r.position()));
            }
            pilot.stopRiding();
            for (Pig p : pigs) {
                p.stopRiding();
            }
            v.discard();
            return "R6 " + how + " (the bit cleared at once, " + ropes.count() + " slider(s) let go on the next tick, "
                + "the ropes at 0)" + (saveSlider ? "; R8 slider save (no fields, its pig; reloaded with no parent "
                + "and no rope, fell and let go on the ground); R14 drive keys (let go while repelling, the hull "
                + "still on " + still[0] + " of 9 ticks; kept once the ropes are in); R15 culling (down past the rope's foot, plain again after)"
                : "");
        }));
    }

    /** R8b: save one of {@code ropes}' sliders with its pig, and load the copy (fresh UUIDs) 12 above the ground at a
     *  free spot. */
    private MchRopeSlider reloadSlider(Ropes ropes, Entity[] pigOut) {
        MchRopeSlider s = null;
        for (MchRopeSlider o : ropes.order) {
            if (!o.isRemoved() && o.getFirstPassenger() instanceof Pig) {
                s = o;
                break;
            }
        }
        req(s != null, "R8: a slider with its pig to save");
        CompoundTag tag = new CompoundTag();
        req(s.save(tag), "R8: the slider saves");
        req("mcheli:rope_slider".equals(tag.getString("id")), "R8: saved as mcheli:rope_slider, got "
            + tag.getString("id"));
        ListTag riders = tag.getList("Passengers", Tag.TAG_COMPOUND);
        req(riders.size() == 1 && "minecraft:pig".equals(riders.getCompound(0).getString("id")),
            "R8: saved with its pig, got " + riders);
        CompoundTag bare = new CompoundTag();
        s.saveWithoutId(bare);
        tag.remove("UUID");
        riders.getCompound(0).remove("UUID");
        double x = this.origin.getX() + 36.5;
        double y = this.origin.getY() + 12.0;
        double z = this.origin.getZ() + 36.5;
        Entity copy = EntityType.loadEntityRecursive(tag, this.level, e -> {
            e.moveTo(x, y, z, e.getYRot(), e.getXRot());
            return e;
        });
        req(copy instanceof MchRopeSlider, "R8: the saved slider loads");
        MchRopeSlider r = (MchRopeSlider) copy;
        Entity pig = r.getFirstPassenger();
        req(pig instanceof Pig, "R8: with its pig aboard");
        req(r.parentVehicle() == null && r.parentId() == 0 && r.ropeIndex() == -1,
            "R8: the reloaded slider has no parent and no rope (synced data is not saved)");
        req(this.level.tryAddFreshEntityWithPassengers(r), "R8: the reloaded slider joins the world");
        pigOut[0] = pig;
        LOG.info("[REPEL SELF-TEST] R8 slider saved with keys {} and reloaded at {}", bare.getAllKeys(),
            fmt(r.position()));
        return r;
    }

    // ---- R9-R13 ----

    private void startPlayerSlide() {
        Pin pin = pinned("mh-60g", 12.5, 20.5, 12.5, -60.0F, 1.0F, 0.5F);
        AbstractMchVehicle v = pin.v;
        ServerPlayer pilot = player("r9pilot");
        req(board(pilot, v, 0), "R9: a player pilot boards the mh-60g");
        ServerPlayer p1 = player("r9seat1");
        req(board(p1, v, 1), "R9: a player boards seat 1");
        List<Pig> pigs = new ArrayList<>();
        for (int s = 2; s <= 3; s++) {
            Pig pig = EntityType.PIG.create(this.level);
            req(pig != null, "a pig can be created");
            pig.setNoAi(true);
            pig.setPos(this.origin.getX() + 12.5, v.getY() + 5.0, this.origin.getZ() + 12.5);
            this.level.addFreshEntity(pig);
            req(board(pig, v, s), "R9: a pig boards seat " + s);
            pigs.add(pig);
        }
        ServerPlayer p4 = player("r9seat4");
        req(board(p4, v, 4), "R9: a player boards seat 4");
        ServerPlayer p5 = player("r9seat5");
        req(board(p5, v, 5), "R9: a player boards seat 5");
        List<ServerPlayer> passengers = List.of(p1, p4, p5);
        List<Slide> slides = new ArrayList<>();
        List<MchRopeSlider> mobRopes = new ArrayList<>();
        int[] at = {-1};
        int[] scanned = {0};
        Supplier<List<MchRopeSlider>> fresh = () -> {
            List<MchRopeSlider> out = new ArrayList<>();
            List<Entity> all = spawned();
            for (; scanned[0] < all.size(); scanned[0]++) {
                if (all.get(scanned[0]) instanceof MchRopeSlider s && s.parentVehicle() == v) {
                    out.add(s);
                }
            }
            return out;
        };
        this.probes.add(new Every(1, () -> {
            int n = v.tickCount;
            if (n < 12) {
                return null;
            }
            if (n == 12) {
                scanned[0] = spawned().size();
                seatUnmount(v, p1);
                req(p1.getVehicle() == v && !v.canRepelling(p1) && fresh.get().isEmpty(),
                    "R9: not repelling yet, the seat-1 player's jump key does nothing");
                hover(v, pilot);
                unmount(v, pilot);
                req(v.isRepelling(), "R9: Y starts repelling");
                at[0] = n;
                return null;
            }
            int k = n - at[0];
            req(v.isRepelling() && v.tickRepelling() == Math.min(k, 60),
                "R9: the heli repels on, counter " + Math.min(k, 60) + " after " + k + " ticks, got "
                    + v.tickRepelling());
            if (k <= 50) {
                // R9: too early for anyone; the payload and the rule itself refuse.
                for (ServerPlayer p : passengers) {
                    req(!v.canRepelling(p), "R9: no slide while the counter is " + v.tickRepelling());
                }
                req(!v.canRepelling(pilot), "R13: the pilot never slides");
                if (k == 1 || k == 25 || k == 50) {
                    seatUnmount(v, p1);
                    seatUnmount(v, p4);
                    v.unmountSeat(p5);
                }
                req(fresh.get().isEmpty() && p1.getVehicle() == v && p4.getVehicle() == v && p5.getVehicle() == v,
                    "R9: the seat key does nothing at counter " + v.tickRepelling());
                return null;
            }
            if (k == 51) {
                for (ServerPlayer p : passengers) {
                    req(v.canRepelling(p), "R10: from the 51st tick (counter " + v.tickRepelling() + ") a passenger"
                        + " may slide");
                }
                // R13: the pilot's own seat payload, and the rule itself.
                req(!v.canRepelling(pilot), "R13: the pilot never slides");
                seatUnmount(v, pilot);
                v.unmountSeat(pilot);
                req(pilot.getVehicle() == v && v.seatIndexOf(pilot) == 0 && v.isRepelling() && fresh.get().isEmpty(),
                    "R13: the pilot's seat key does nothing");
                // R10, R11: seat 1 takes rope (0 + 1) % 2 = 1, seat 4 right after rope 0.
                slides.add(slide(pin, p1, 1, fresh, "R10 seat 1"));
                slides.add(slide(pin, p4, 0, fresh, "R11 seat 4"));
                req(p5.getVehicle() == v && pigs.get(0).getVehicle() == v && pigs.get(1).getVehicle() == v,
                    "R10: the others stay aboard");
                return null;
            }
            List<MchRopeSlider> now = fresh.get();
            if (k > 60 && n % 30 == 0 && mobRopes.isEmpty()) {
                // R11: the cadence's first firing, seats 2 and 3 down ropes 0 and 1; then seat 5 takes rope 0.
                req(now.size() == 2, "R11: the first firing lowers the two pigs, got " + now.size());
                for (int i = 0; i < 2; i++) {
                    MchRopeSlider s = now.get(i);
                    req(s.ropeIndex() == i && s.pendingUser() == pigs.get(i), "R11: rope " + i + " takes the pig of "
                        + "seat " + (2 + i) + ", got rope " + s.ropeIndex());
                    checkSpawn(s, pin, MH60G_HOOKS[i], "R11 cadence seat " + (2 + i));
                    mobRopes.add(s);
                }
                slides.add(slide(pin, p5, 0, fresh, "R11 seat 5 after the cadence"));
                return null;
            }
            req(now.isEmpty(), "R11: no other slider (tick " + n + ", " + now.size() + " new)");
            // R10, R12: every tick each player rides his slider down, and lets go on the ground.
            boolean allDown = true;
            for (Slide sl : slides) {
                allDown &= sl.check(ticks());
            }
            for (MchRopeSlider s : mobRopes) {
                allDown &= s.isRemoved();
            }
            if (slides.size() == 3 && allDown) {
                pilot.stopRiding();
                v.discard();
                return "R9 too early (no slide for 50 ticks, the payload refused); R10 slide (seat 1 down rope 1, 4 "
                    + "below the hook, the slider 4.5 below, feet 0.3 under it); R11 round robin (seat 4 rope 0, the "
                    + "cadence's pigs ropes 0 and 1, then seat 5 rope 0); R12 down (under the hook, no fall distance, "
                    + "let go 0.2 above the ground, unhurt); R13 the pilot never slides";
            }
            req(n < 400, "R12: every slider is down by tick 400");
            return null;
        }));
    }

    /**
     * R10/R11: {@code p}'s seat payload, sent from the test tick: exactly one new slider of rope {@code rope}, its box
     * bottom at the trail's oldest entry (the position the vehicle put 8 ticks before: the test tick follows the
     * vehicle's) + R·hook with the PINNED attitude, 4.5 lower (the player was put 4 below the point, the reference's
     * double -2), within 1e-4, and within 0.05 of {@code crewTrailOldest() + R·hook - 4.5}; {@code p} off the heli at
     * the point, with no fall distance, waiting to mount it.
     */
    private Slide slide(Pin pin, ServerPlayer p, int rope, Supplier<List<MchRopeSlider>> fresh, String what) {
        AbstractMchVehicle v = pin.v;
        int n = v.tickCount;
        Vec3d hook = MH60G_HOOKS[rope];
        Vec3 r = body(pin.yaw, pin.pitch, pin.roll, hook.x(), hook.y(), hook.z());
        Vec3 exact = pin.post(n - 8).add(r).subtract(0.0, 4.5, 0.0);
        Vec3 loose = v.crewTrailOldest().add(r).subtract(0.0, 4.5, 0.0);
        seatUnmount(v, p);
        List<MchRopeSlider> made = fresh.get();
        req(made.size() == 1, what + ": the seat payload lowers one slider, got " + made.size());
        MchRopeSlider s = made.get(0);
        req(s.ropeIndex() == rope, what + ": the player takes rope " + rope + ", got " + s.ropeIndex());
        req(s.position().distanceTo(exact) <= 1.0e-4, what + ": the slider's box bottom is at the position of tick "
            + (n - 8) + " + R·hook - 4.5 = " + fmt(exact) + ", got " + fmt(s.position()));
        req(s.position().distanceTo(loose) <= 0.05, what + ": within 0.05 of crewTrailOldest() + R·hook - 4.5 = "
            + fmt(loose) + ", got " + fmt(s.position()));
        req(s.parentId() == v.getId() && s.parentVehicle() == v, what + ": the slider names its heli");
        req(s.getDeltaMovement().equals(Vec3.ZERO) && s.fallDistance == 0.0F, what + ": the slider starts at rest");
        req(s.pendingUser() == p && p.getVehicle() == null && p.fallDistance == 0.0F
                && p.position().distanceTo(exact.add(0.0, MchRopeSlider.Y_OFFSET, 0.0)) <= 1.0e-4,
            what + ": the player is off the heli 4 below the hook's point, waiting to mount, got " + fmt(p.position()));
        LOG.info("[REPEL SELF-TEST] {} at tick {}: rope {} slider {} (origin {})", what, n, rope, fmt(s.position()),
            fmt(pin.post(n - 8)));
        return new Slide(v, p, s, ticks());
    }

    /** One player's slide down a rope (R10, R12). */
    private final class Slide {
        final AbstractMchVehicle v;
        final ServerPlayer p;
        final MchRopeSlider s;
        final int born;
        boolean down;

        Slide(AbstractMchVehicle v, ServerPlayer p, MchRopeSlider s, int born) {
            this.v = v;
            this.p = p;
            this.s = s;
            this.born = born;
        }

        /** This tick's check; true once he is let go on the ground. */
        boolean check(int tick) {
            if (this.down || tick <= this.born) {
                return this.down;
            }
            String what = "R12 " + this.p.getGameProfile().getName();
            req(this.p.getHealth() == this.p.getMaxHealth() && this.p.fallDistance == 0.0F,
                what + ": full health and no fall distance, got " + this.p.getHealth() + " / " + this.p.fallDistance);
            if (this.s.isRemoved()) {
                // Let go on the ground: the slider rose 0.5 (onGroundAndDead), the player stayed where he hung.
                req(Math.abs(this.s.getY() - 0.5 - origin.getY()) <= 1.0e-6,
                    what + ": the slider let go on the ground, at " + fmt(this.s.position()));
                req(this.p.getVehicle() == null
                        && Math.abs(this.p.getY() - (this.s.getY() + MchRopeSlider.PLAYER_FEET)) <= 1.0e-6
                        && Math.abs(this.p.getY() - origin.getY() - 0.2) <= 1.0e-6,
                    what + ": he is let go 0.2 above the ground, got " + fmt(this.p.position()));
                LOG.info("[REPEL SELF-TEST] {} rope {} down at test tick {} ({} ticks)", what, this.s.ropeIndex(),
                    tick, tick - this.born);
                this.down = true;
                return true;
            }
            req(this.p.getVehicle() == this.s, what + ": he rides his slider from the tick after it appeared");
            req(Math.abs(this.p.getY() - (this.s.getY() + MchRopeSlider.PLAYER_FEET)) <= 1.0e-6,
                what + ": a player's feet are 0.3 below the slider's box bottom, " + fmt(this.s.position()) + " / "
                    + fmt(this.p.position()));
            Vec3 rope = this.v.ropePos(this.s.ropeIndex());
            req(Math.abs(this.s.getX() - rope.x) <= 0.05 && Math.abs(this.s.getZ() - rope.z) <= 0.05,
                what + ": the slider hangs under its hook " + fmt(rope) + ", got " + fmt(this.s.position()));
            return false;
        }
    }

    // ---- the slider checks ----

    /** The tick a slider appears: rope {@code hook}'s point on the position put 9 ticks before this tick, through this
     *  tick's post-move attitude, 2.5 lower (its box bottom; the mob was put 2 below the point), exact; within 0.05 of
     *  {@code crewTrailOldest() + R·hook - 2.5}; at rest, the pig off the vehicle and waiting to mount it. */
    private void checkSpawn(MchRopeSlider s, Pin pin, Vec3d hook, String what) {
        AbstractMchVehicle v = pin.v;
        int n = v.tickCount;
        Vec3 r = body(pin.lastYaw, pin.lastPitch, pin.lastRoll, hook.x(), hook.y(), hook.z());
        Vec3 exact = pin.post(n - 9).add(r).subtract(0.0, 2.5, 0.0);
        Vec3 loose = v.crewTrailOldest().add(r).subtract(0.0, 2.5, 0.0);
        req(s.position().distanceTo(exact) <= 1.0e-4, what + ": the slider's box bottom is at the position of tick "
            + (n - 9) + " + R·hook - 2.5 = " + fmt(exact) + ", got " + fmt(s.position()));
        req(s.position().distanceTo(loose) <= 0.05, what + ": within 0.05 of crewTrailOldest() + R·hook - 2.5 = "
            + fmt(loose) + ", got " + fmt(s.position()));
        req(s.parentId() == v.getId() && s.parentVehicle() == v, what + ": the slider names its heli");
        req(s.getDeltaMovement().equals(Vec3.ZERO) && s.fallDistance == 0.0F, what + ": the slider starts at rest");
        req(s.pendingUser() instanceof Pig p && !(p.getVehicle() instanceof AbstractMchVehicle)
                && p.fallDistance == 0.0F,
            what + ": the pig is off the heli with no fall distance, waiting to mount the slider");
        LOG.info("[REPEL SELF-TEST] {} at tick {}: {} (origin {})", what, n, fmt(s.position()), fmt(pin.post(n - 9)));
    }

    /** The sliders lowered from one heli, scanned from {@link #spawned()} tick by tick. */
    private final class Ropes {
        final AbstractMchVehicle v;
        final List<Pig> pigs;
        final Map<Pig, Integer> seatAtStart = new HashMap<>();
        final List<MchRopeSlider> order = new ArrayList<>();
        final Map<MchRopeSlider, Entity> riders = new HashMap<>();
        final Map<MchRopeSlider, Integer> seenAt = new HashMap<>();
        final Map<MchRopeSlider, double[]> fall = new HashMap<>(); // {y last tick, the reference motionY}
        final Map<MchRopeSlider, Boolean> landed = new HashMap<>();
        int scanned;

        Ropes(AbstractMchVehicle v, List<Pig> pigs) {
            this.v = v;
            this.pigs = pigs;
            for (int i = 0; i < pigs.size(); i++) {
                this.seatAtStart.put(pigs.get(i), i + 1);
            }
        }

        void markFrom(int index) {
            this.scanned = index;
        }

        List<MchRopeSlider> scan() {
            List<MchRopeSlider> fresh = new ArrayList<>();
            List<Entity> all = spawned();
            for (; this.scanned < all.size(); this.scanned++) {
                if (all.get(this.scanned) instanceof MchRopeSlider s && s.parentVehicle() == this.v) {
                    this.order.add(s);
                    this.riders.put(s, s.pendingUser());
                    this.seenAt.put(s, ticks());
                    this.fall.put(s, new double[] {s.getY(), 0.0});
                    fresh.add(s);
                }
            }
            return fresh;
        }

        int count() {
            return this.order.size();
        }

        int alive() {
            int n = 0;
            for (MchRopeSlider s : this.order) {
                n += s.isRemoved() ? 0 : 1;
            }
            return n;
        }

        Entity riderOf(MchRopeSlider s) {
            return this.riders.get(s);
        }

        int seatOf(MchRopeSlider s) {
            Entity e = this.riders.get(s);
            Integer seat = e instanceof Pig p ? this.seatAtStart.get(p) : null;
            return seat != null ? seat : -1;
        }

        boolean allLanded() {
            for (MchRopeSlider s : this.order) {
                if (!Boolean.TRUE.equals(this.landed.get(s))) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Every slider seen on an earlier tick: ridden by its pig 0.2 above its box bottom, under its hook, no fall
         * distance on the pig and at most one tick's on the slider, falling exactly as the reference
         * ({@code motionY -= 0.03; move; motionY *= 0.9}); once gone (on the ground), the pig 0.7 above the ground,
         * then landed at full health.
         */
        void checkEveryTick(String what) {
            for (MchRopeSlider s : this.order) {
                if (this.seenAt.get(s) >= ticks() || Boolean.TRUE.equals(this.landed.get(s))) {
                    continue;
                }
                Entity pig = this.riders.get(s);
                double[] f = this.fall.get(s);
                if (s.isRemoved()) {
                    // Let go on the ground: the slider rose 0.5 (onGroundAndDead) and its pig stayed where it hung.
                    if (!this.landed.containsKey(s)) {
                        req(Math.abs(s.getY() - 0.5 - origin.getY()) <= 1.0e-6,
                            what + ": the slider let go on the ground, at " + fmt(s.position()));
                        req(pig.getVehicle() == null
                                && Math.abs(pig.getY() - (s.getY() + MchRopeSlider.MOB_FEET)) <= 0.08 + 1.0e-6,
                            what + ": its pig is let go where it hung, 0.7 above the ground, got " + fmt(pig.position()));
                        this.landed.put(s, false);
                    }
                    if (pig.onGround()) {
                        req(pig instanceof Pig p && p.getHealth() == p.getMaxHealth() && p.isAlive(),
                            what + ": the pig landed at full health");
                        this.landed.put(s, true);
                        LOG.info("[REPEL SELF-TEST] {} rope {} landed at tick {}", what, s.ropeIndex(), ticks());
                    }
                    continue;
                }
                req(pig.getVehicle() == s, what + ": the pig rides its slider from the tick after it appeared");
                req(Math.abs(pig.getY() - (s.getY() + MchRopeSlider.MOB_FEET)) <= 1.0e-6,
                    what + ": a mob's feet are 0.2 above the slider's box bottom, " + fmt(s.position()) + " / "
                        + fmt(pig.position()));
                Vec3 rope = this.v.ropePos(s.ropeIndex());
                req(Math.abs(s.getX() - rope.x) <= 0.05 && Math.abs(s.getZ() - rope.z) <= 0.05,
                    what + ": the slider hangs under its hook " + fmt(rope) + ", got " + fmt(s.position()));
                req(pig.fallDistance == 0.0F && s.fallDistance <= 0.31F,
                    what + ": no fall distance builds up, got " + pig.fallDistance + " / " + s.fallDistance);
                // The reference fall, exactly (motionY was 0 at the spawn), until the tick it lands (it lets go on the
                // next one).
                f[1] -= 0.03;
                double expectY = f[0] + f[1];
                f[1] *= 0.9;
                if (s.onGround()) {
                    req(Math.abs(s.getY() - origin.getY()) <= 1.0e-6 && expectY <= s.getY() + 1.0e-9,
                        what + ": the slider comes down on the ground, got " + fmt(s.position()));
                } else {
                    req(Math.abs(s.getY() - expectY) <= 1.0e-9, what + ": the slider falls as the reference's, "
                        + expectY + ", got " + s.getY() + " (tick " + (ticks() - this.seenAt.get(s)) + ")");
                }
                f[0] = s.getY();
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
     * (the post-move position the trail took, by the vehicle's {@code tickCount}, and the post-move attitude), then
     * puts it back (position, zero motion, yaw, pitch, roll).
     */
    private static final class Pin {
        final AbstractMchVehicle v;
        Vec3 pos;
        float yaw;
        float pitch;
        float roll;
        final Map<Integer, Vec3> postByTick = new HashMap<>();
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

    /** A fuelled helicopter of {@code config} in the world at the offset, at the given attitude, pinned there. */
    private Pin pinned(String config, double dx, double dy, double dz, float yaw, float pitch, float roll) {
        AbstractMchVehicle v = spawn(config, dx, dy, dz);
        Pin pin = new Pin(v, yaw, pitch, roll);
        pin.apply();
        this.pins.add(pin);
        return pin;
    }

    /** {@code n} pigs boarded in seats 1 to {@code n}, in order; with AI unless {@code noAi}. */
    private List<Pig> crew(AbstractMchVehicle v, int n, double dx, double dz, boolean noAi) {
        List<Pig> out = new ArrayList<>();
        for (int s = 1; s <= n; s++) {
            Pig p = EntityType.PIG.create(this.level);
            req(p != null, "a pig can be created");
            p.setNoAi(noAi);
            p.setPos(this.origin.getX() + dx, v.getY() + 5.0, this.origin.getZ() + dz);
            this.level.addFreshEntity(p);
            req(board(p, v, s), "a pig boards seat " + s + " of the " + v.configName());
            out.add(p);
        }
        return out;
    }

    /** How many of {@code pigs} are still aboard {@code v}. */
    private static int aboard(AbstractMchVehicle v, List<Pig> pigs) {
        int n = 0;
        for (Pig p : pigs) {
            n += p.getVehicle() == v ? 1 : 0;
        }
        return n;
    }

    /** The real {@code mcheli:unmount_crew} handler, as if {@code sender} had pressed Y in {@code v}. */
    private static void unmount(AbstractMchVehicle v, ServerPlayer sender) {
        ServerboundUnmountCrewPayload.handle(new ServerboundUnmountCrewPayload(v.getId()), payloadContext(sender));
    }

    /** The real {@code mcheli:seat_unmount} handler, as if {@code sender} had held the jump key in his seat. */
    private static void seatUnmount(AbstractMchVehicle v, ServerPlayer sender) {
        ServerboundSeatUnmountPayload.handle(new ServerboundSeatUnmountPayload(v.getId()), payloadContext(sender));
    }

    /** The real {@code mcheli:control} handler, as if {@code sender}'s client had sent these control bits. */
    private static void control(AbstractMchVehicle v, ServerPlayer sender, int bits) {
        ServerboundControlPayload.handle(payload(ServerboundControlPayload.class,
            Map.of("vehicleId", v.getId(), "bits", bits, "lockTargetId", -1)), payloadContext(sender));
    }

    private static double horizontal(Vec3 d) {
        return Math.sqrt(d.x * d.x + d.z * d.z);
    }

    /** The real {@code mcheli:hover_toggle} handler, as if {@code sender} had pressed the jump key as pilot. */
    private static void hover(AbstractMchVehicle v, ServerPlayer sender) {
        ServerboundHoverPayload.handle(new ServerboundHoverPayload(v.getId()), payloadContext(sender));
    }

    private List<MchRopeSlider> slidersSince(int index) {
        List<MchRopeSlider> out = new ArrayList<>();
        List<Entity> all = spawned();
        for (int i = index; i < all.size(); i++) {
            if (all.get(i) instanceof MchRopeSlider s) {
                out.add(s);
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

    /** A private synced-data accessor's id, by reflection. */
    private static int accessorId(Class<?> owner, String field) {
        try {
            java.lang.reflect.Field f = owner.getDeclaredField(field);
            f.setAccessible(true);
            return ((EntityDataAccessor<?>) f.get(null)).id();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(owner.getSimpleName() + "." + field + " is not readable: " + e);
        }
    }

    private static String fmt(Vec3 p) {
        return String.format(Locale.ROOT, "(%.4f, %.4f, %.4f)", p.x, p.y, p.z);
    }

    /** A fuelled helicopter of {@code config} at the offset from the origin. */
    private AbstractMchVehicle spawn(String config, double dx, double dy, double dz) {
        MchHelicopter v = MchRegistries.HELI.get().create(this.level);
        req(v != null, "the helicopter type creates");
        v.setConfigName(config);
        req(v.hostInfo() != null && config.equalsIgnoreCase(v.hostInfo().name), "the " + config + " config is loaded");
        req(v.hostInfo().haveRepellingHook(), "the " + config + " has repelling hooks");
        v.setFuel(v.getMaxFuel()); // hover mode drops itself (and the ropes) once the fuel is out
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
