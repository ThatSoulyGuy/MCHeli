package mcheli.dependent;

import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * The in-game self-test runner (dev workspace only; {@code MCHeli} registers it when the {@code mcheli.selftests}
 * system property is set). Gradle sets that property from {@code -PmcheliSelfTests}: empty or {@code all} selects every
 * test, otherwise a comma list of the names in {@link #ALL}, e.g.
 * {@code ./gradlew :neoforge-1.21.1:runServer -PmcheliSelfTests=crush,lockfire}. See {@code selftest.gradle} for the
 * run setup (its own game directory, a fresh flat world, the synthetic content packs).
 *
 * <p>Once the server has started, the selected tests run ONE AT A TIME, each in its own area 384 blocks along +X from
 * the previous one, so no test can see another's probes or terrain. For each test the runner force-loads the test's
 * chunks and waits until they tick entities, runs it, then discards every entity that joined the world while it ran
 * and releases the chunks. Each test ends with one line
 * <pre>[MCHELI-SELFTEST] RESULT: PASS crush (60 ticks) - ...</pre>
 * and the run with one {@code [MCHELI-SELFTEST] SUMMARY:} line. The results are also written to
 * {@code <gamedir>/mcheli-selftest-results.txt} (for the Gradle task to check), and with {@code mcheli.selftest.halt}
 * set the server then stops, so a one-shot run ends by itself.
 */
public final class MchSelfTests {
    private static final Logger LOG = LogUtils.getLogger();

    public static final String SELECTION_PROPERTY = "mcheli.selftests";
    public static final String HALT_PROPERTY = "mcheli.selftest.halt";
    public static final String RESULTS_FILE = "mcheli-selftest-results.txt";
    static final String TAG = "[MCHELI-SELFTEST]";

    /** Distance between two tests' areas, in blocks along +X (24 chunks; the largest area spans 15). */
    private static final int AREA_SPACING = 384;
    /** How long to wait for a test's chunks to tick entities before starting it anyway (with a warning). */
    private static final int MAX_PREPARE_TICKS = 200;
    /** Idle ticks between two tests, so removals settle before the next one starts. */
    private static final int SETTLE_TICKS = 2;

    /** Every self-test by selection name, in run order. A test's index is also its area slot, so its area does not
     *  depend on which other tests were selected. */
    private static final Map<String, Supplier<MchSelfTest>> ALL = new LinkedHashMap<>();

    static {
        ALL.put("heli", DemoHeliSelfTest::new);
        ALL.put("plane", () -> new DemoForwardVehicleSelfTest("PLANE", MchRegistries.PLANE, "a-10",
            6.0, 11.0, 90.0, 5.0, 20.0));
        ALL.put("tank", DemoTankSelfTest::new);
        ALL.put("vtol", DemoVtolSelfTest::new);
        ALL.put("bullet", DemoBulletSelfTest::new);
        ALL.put("weapon", DemoWeaponSelfTest::new);
        ALL.put("lockfire", DemoLockFireSelfTest::new);
        ALL.put("homing", DemoHomingSelfTest::new);
        ALL.put("flare", DemoFlareSelfTest::new);
        ALL.put("bomb", DemoBombSelfTest::new);
        ALL.put("torpedo", DemoTorpedoSelfTest::new);
        ALL.put("cas", DemoCasSelfTest::new);
        ALL.put("dispenser", DemoDispenserSelfTest::new);
        ALL.put("particle", DemoParticleSelfTest::new);
        ALL.put("hud", DemoHudSelfTest::new);
        ALL.put("heat", DemoHeatSelfTest::new);
        ALL.put("hp", DemoHpSelfTest::new);
        ALL.put("crewfuel", DemoCrewFuelSelfTest::new);
        ALL.put("contentpack", DemoContentPackSelfTest::new);
        ALL.put("crush", DemoCrushSelfTest::new);
        ALL.put("recipes", DemoRecipeSelfTest::new);
    }

    /** The names {@code -PmcheliSelfTests} accepts. */
    public static Set<String> names() {
        return ALL.keySet();
    }

    /** Register the runner on the game bus if {@value #SELECTION_PROPERTY} is set. Call from the mod constructor. */
    public static void registerIfRequested() {
        String selection = System.getProperty(SELECTION_PROPERTY);
        if (selection != null) {
            NeoForge.EVENT_BUS.register(new MchSelfTests(selection));
        }
    }

    private enum Phase { WAITING, NEXT, PREPARE, RUN, SETTLE, DONE }

    private record Result(String name, boolean pass, int ticks, String detail) {}

    private final Deque<String> queue = new ArrayDeque<>();
    private final List<Result> results = new ArrayList<>();
    private MinecraftServer server;
    private Phase phase = Phase.WAITING;
    private int phaseTicks;
    private MchSelfTest current;
    private List<ChunkPos> forced = List.of();
    private boolean tracking;

    private MchSelfTests(String selection) {
        String s = selection.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.equals("all") || s.equals("true")) {
            this.queue.addAll(ALL.keySet());
            return;
        }
        Set<String> picked = new LinkedHashSet<>();
        for (String part : s.split("[,\\s]+")) {
            if (part.isEmpty()) {
                continue;
            }
            if (ALL.containsKey(part)) {
                picked.add(part);
            } else {
                this.results.add(new Result(part, false, 0, "unknown self-test (known: " + String.join(", ", ALL.keySet()) + ")"));
            }
        }
        // Run in the canonical order whatever order they were listed in (each still gets its fixed area slot).
        for (String name : ALL.keySet()) {
            if (picked.contains(name)) {
                this.queue.add(name);
            }
        }
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        this.server = event.getServer();
        this.phase = Phase.NEXT;
        LOG.info("{} running {} self-test(s): {}", TAG, this.queue.size(), String.join(", ", this.queue));
        for (Result r : this.results) {
            report(r); // unknown names fail up front
        }
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (this.tracking && this.current != null && !event.getLevel().isClientSide() && !event.loadedFromDisk()
                && !(event.getEntity() instanceof Player)) {
            this.current.track(event.getEntity());
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (this.server == null || this.phase == Phase.WAITING || this.phase == Phase.DONE) {
            return;
        }
        switch (this.phase) {
            case NEXT -> next();
            case PREPARE -> prepare();
            case RUN -> run();
            case SETTLE -> {
                if (++this.phaseTicks >= SETTLE_TICKS) {
                    this.phase = Phase.NEXT;
                }
            }
            default -> { }
        }
    }

    /** Bind the next queued test to its area and force-load that area. */
    private void next() {
        String name = this.queue.poll();
        if (name == null) {
            finishRun();
            return;
        }
        ServerLevel level = this.server.overworld();
        int slot = new ArrayList<>(ALL.keySet()).indexOf(name);
        BlockPos spawn = level.getSharedSpawnPos();
        int x = spawn.getX() + (slot + 1) * AREA_SPACING;
        int z = spawn.getZ();
        MchSelfTest test;
        try {
            test = ALL.get(name).get();
        } catch (Throwable t) {
            LOG.error("{} {} could not be constructed", TAG, name, t);
            report(new Result(name, false, 0, "constructor threw " + t));
            this.phase = Phase.SETTLE;
            this.phaseTicks = 0;
            return;
        }
        test.bind(name, this.server, level, new BlockPos(x, spawn.getY(), z));
        int[] a = test.areaChunks();
        List<ChunkPos> chunks = new ArrayList<>();
        for (int cx = (x >> 4) + a[0]; cx <= (x >> 4) + a[1]; cx++) {
            for (int cz = (z >> 4) + a[2]; cz <= (z >> 4) + a[3]; cz++) {
                level.setChunkForced(cx, cz, true); // also loads (generates) the chunk now
                chunks.add(new ChunkPos(cx, cz));
            }
        }
        this.forced = chunks;
        // Ground level of the area, measured like the world spawn point: the first free block above the surface.
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        test.bind(name, this.server, level, new BlockPos(x, y, z));
        this.current = test;
        this.phase = Phase.PREPARE;
        this.phaseTicks = 0;
        LOG.info("{} {}: area origin {} ({} chunks)", TAG, name, test.origin, chunks.size());
    }

    /** Wait for the area to tick entities, then start the test. */
    private void prepare() {
        MchSelfTest test = this.current;
        ServerLevel level = this.server.overworld();
        boolean ready = true;
        for (ChunkPos c : this.forced) {
            if (!level.isPositionEntityTicking(new BlockPos(c.getMiddleBlockX(), test.origin.getY(), c.getMiddleBlockZ()))) {
                ready = false;
                break;
            }
        }
        if (!ready && ++this.phaseTicks < MAX_PREPARE_TICKS) {
            return;
        }
        if (!ready) {
            LOG.warn("{} {}: area still not entity-ticking after {} ticks; starting anyway", TAG, test.name(),
                MAX_PREPARE_TICKS);
        }
        this.tracking = true;
        try {
            test.start();
        } catch (Throwable t) {
            LOG.error("{} {} threw in start()", TAG, test.name(), t);
            test.fail("start() threw {}", t.toString());
        }
        this.phase = Phase.RUN;
        if (test.finished()) {
            end();
        }
    }

    private void run() {
        MchSelfTest test = this.current;
        test.advance();
        try {
            test.tick();
        } catch (Throwable t) {
            LOG.error("{} {} threw in tick() at tick {}", TAG, test.name(), test.ticks(), t);
            test.fail("tick() threw {} at tick {}", t.toString(), test.ticks());
        }
        if (!test.finished() && test.ticks() >= test.timeoutTicks()) {
            test.fail("timed out after {} ticks without a verdict", test.ticks());
        }
        if (test.finished()) {
            end();
        }
    }

    /** Record the verdict, then tear the test's area down: its entities, its forced chunks. */
    private void end() {
        MchSelfTest test = this.current;
        this.tracking = false;
        try {
            test.cleanup();
        } catch (Throwable t) {
            LOG.warn("{} {} threw in cleanup()", TAG, test.name(), t);
        }
        int removed = 0;
        for (Entity e : test.spawned()) {
            if (!e.isRemoved()) {
                e.ejectPassengers();
                e.discard();
                removed++;
            }
        }
        ServerLevel level = this.server.overworld();
        for (ChunkPos c : this.forced) {
            level.setChunkForced(c.x, c.z, false);
        }
        LOG.info("{} {}: cleaned up {} entit(ies), released {} chunk(s)", TAG, test.name(), removed, this.forced.size());
        this.forced = List.of();
        report(new Result(test.name(), test.passed(), test.ticks(), test.detail()));
        this.current = null;
        this.phase = Phase.SETTLE;
        this.phaseTicks = 0;
    }

    private void report(Result r) {
        if (!this.results.contains(r)) {
            this.results.add(r);
        }
        LOG.info("{} RESULT: {} {} ({} ticks) - {}", TAG, r.pass() ? "PASS" : "FAIL", r.name(), r.ticks(), r.detail());
    }

    private void finishRun() {
        this.phase = Phase.DONE;
        List<String> failed = new ArrayList<>();
        for (Result r : this.results) {
            if (!r.pass()) {
                failed.add(r.name());
            }
        }
        int passed = this.results.size() - failed.size();
        String summary = String.format(Locale.ROOT, "%s SUMMARY: %d/%d passed%s", TAG, passed, this.results.size(),
            failed.isEmpty() ? "" : "; FAILED: " + String.join(", ", failed));
        LOG.info(summary);
        StringBuilder file = new StringBuilder();
        for (Result r : this.results) {
            file.append(r.pass() ? "PASS " : "FAIL ").append(r.name()).append(" - ").append(r.detail()).append('\n');
        }
        file.append(summary).append('\n');
        Path out = FMLPaths.GAMEDIR.get().resolve(RESULTS_FILE);
        try {
            Files.writeString(out, file.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOG.warn("{} could not write {}", TAG, out, e);
        }
        if (System.getProperty(HALT_PROPERTY) != null) {
            LOG.info("{} halting the server ({} set)", TAG, HALT_PROPERTY);
            this.server.halt(false);
        }
    }
}
