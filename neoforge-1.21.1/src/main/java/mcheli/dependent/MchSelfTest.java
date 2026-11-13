package mcheli.dependent;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.stats.Stat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.helpers.MessageFormatter;

/**
 * One in-game self-test, run by {@link MchSelfTests}. The runner gives each test its own area of the overworld
 * ({@link #origin}, far from every other test's), force-loads the chunks the test declares ({@link #areaChunks}) and
 * waits until they tick entities, then calls {@link #start} once and {@link #tick} every server tick until the test
 * calls {@link #finish}. Afterwards it removes every entity that joined the world during the test and releases the
 * chunks, so the next test starts clean. A test therefore never sweeps the world for "its" bullets or mobs and never
 * forces chunks itself.
 *
 * <p>A test reports exactly one verdict through {@link #finish}; the runner prints it in the uniform
 * {@code RESULT: PASS|FAIL <name>} form. An exception from {@link #start} or {@link #tick}, or running past
 * {@link #timeoutTicks}, is a FAIL. A missing bundled config is a FAIL too, never a silent skip: the bundled content
 * ships with the mod, so a test that cannot find it has found a real breakage.
 */
public abstract class MchSelfTest {
    protected static final Logger LOG = LogUtils.getLogger();

    private String name;
    /** The running server; set before {@link #start}. */
    protected MinecraftServer server;
    /** The overworld; set before {@link #start}. */
    protected ServerLevel level;
    /**
     * This test's own area: a ground-level position (the first free block above the surface, like the world spawn
     * point) whose chunk rectangle {@link #areaChunks} no other test touches.
     */
    protected BlockPos origin;

    private final List<Entity> spawned = new ArrayList<>();
    private int ticks;
    private Boolean pass;
    private String detail = "";

    /**
     * The chunks, relative to {@link #origin}'s chunk, that are force-loaded (so their entities tick on a playerless
     * server) before {@link #start}: {@code {minX, maxX, minZ, maxZ}}, inclusive. Cover everywhere the test's entities
     * go that must keep simulating. Default: a 7x7 block.
     */
    protected int[] areaChunks() {
        return new int[] {-3, 3, -3, 3};
    }

    /** Ticks after {@link #start} before the runner fails the test as hung. */
    protected int timeoutTicks() {
        return 400;
    }

    /** Set the probes up. A test with nothing to wait for may {@link #finish} right here. */
    protected abstract void start();

    /** One server tick (after the level ticked), from the tick after {@link #start} until {@link #finish}. */
    protected void tick() {}

    /** Test-specific teardown beyond the runner's (entities that never joined the world, such as test players). */
    protected void cleanup() {}

    /** The selection name, e.g. {@code crush}. */
    public final String name() {
        return this.name;
    }

    /** Ticks since {@link #start}: 1 on the first {@link #tick}. */
    protected final int ticks() {
        return this.ticks;
    }

    /** Every non-player entity that joined the world since {@link #start}, in join order (read-only). */
    protected final List<Entity> spawned() {
        return Collections.unmodifiableList(this.spawned);
    }

    /** The world-space box of this test's force-loaded chunks, full build height: the scope of any entity search. */
    protected final AABB area() {
        int[] a = areaChunks();
        int cx = this.origin.getX() >> 4;
        int cz = this.origin.getZ() >> 4;
        return new AABB((cx + a[0]) * 16.0, this.level.getMinBuildHeight(), (cz + a[2]) * 16.0,
            (cx + a[1] + 1) * 16.0, this.level.getMaxBuildHeight(), (cz + a[3] + 1) * 16.0);
    }

    /**
     * Report the verdict and end the test. {@code format} uses SLF4J {@code {}} placeholders. Only the first call
     * counts.
     */
    protected final void finish(boolean pass, String format, Object... args) {
        if (this.pass == null) {
            this.pass = pass;
            this.detail = MessageFormatter.arrayFormat(format, args).getMessage();
        }
    }

    /** {@link #finish} with FAIL. */
    protected final void fail(String format, Object... args) {
        finish(false, format, args);
    }

    // ---- runner side (package-private) ----

    final void bind(String name, MinecraftServer server, ServerLevel level, BlockPos origin) {
        this.name = name;
        this.server = server;
        this.level = level;
        this.origin = origin;
    }

    final void advance() {
        this.ticks++;
    }

    final void track(Entity e) {
        this.spawned.add(e);
    }

    final boolean finished() {
        return this.pass != null;
    }

    final boolean passed() {
        return Boolean.TRUE.equals(this.pass);
    }

    final String detail() {
        return this.detail;
    }

    // ---- helpers for tests that drive the real player-facing paths ----

    /**
     * A server-side player that can board a vehicle, for tests that need a real {@link Player} in a seat (the pilot
     * seat is assigned to players automatically, weapon selection and eligibility read {@code instanceof Player}).
     * NeoForge's {@code FakePlayer} refuses {@code startRiding}, so this is a plain {@link ServerPlayer} wired to the
     * no-op network handler of a FakePlayer: whatever the vehicle sends its rider goes nowhere. It is never added to
     * the world or the player list, so the runner does not track it; {@link #cleanup} must dismount it.
     */
    protected final ServerPlayer newTestPlayer(String label) {
        String profileName = ("mch_" + label).length() > 16 ? ("mch_" + label).substring(0, 16) : "mch_" + label;
        GameProfile profile = new GameProfile(
            UUID.nameUUIDFromBytes(("mcheli-selftest:" + this.name + ":" + label).getBytes(StandardCharsets.UTF_8)),
            profileName);
        return new TestPlayer(this.level, profile);
    }

    /** A payload context for calling a serverbound payload's real {@code handle} as if {@code sender} had sent it. */
    protected static IPayloadContext payloadContext(Player sender) {
        return new DirectPayloadContext(sender);
    }

    /**
     * Build a payload record from named component values; components not named get their type's default (0, false,
     * null). The serverbound payloads grow fields as the port catches up with the reference packets, so a test that
     * called the canonical constructor directly would stop compiling every time one did; this keeps a test pinned to
     * the fields it actually drives.
     */
    protected static <R extends Record & CustomPacketPayload> R payload(Class<R> type, Map<String, Object> values) {
        RecordComponent[] comps = type.getRecordComponents();
        Class<?>[] types = new Class<?>[comps.length];
        Object[] args = new Object[comps.length];
        for (int i = 0; i < comps.length; i++) {
            types[i] = comps[i].getType();
            Object v = values.get(comps[i].getName());
            args[i] = v != null ? v : defaultOf(types[i]);
        }
        for (String key : values.keySet()) {
            boolean known = false;
            for (RecordComponent c : comps) {
                known |= c.getName().equals(key);
            }
            if (!known) {
                throw new IllegalArgumentException(type.getSimpleName() + " has no component '" + key + "'");
            }
        }
        try {
            Constructor<R> ctor = type.getDeclaredConstructor(types);
            return ctor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot build " + type.getSimpleName(), e);
        }
    }

    private static Object defaultOf(Class<?> t) {
        if (t == boolean.class) return false;
        if (t == int.class) return 0;
        if (t == long.class) return 0L;
        if (t == float.class) return 0.0F;
        if (t == double.class) return 0.0;
        if (t == short.class) return (short) 0;
        if (t == byte.class) return (byte) 0;
        if (t == char.class) return (char) 0;
        return null;
    }

    /** See {@link #newTestPlayer}. */
    private static final class TestPlayer extends ServerPlayer {
        TestPlayer(ServerLevel level, GameProfile profile) {
            super(level.getServer(), level, profile, ClientInformation.createDefault());
            this.connection = FakePlayerFactory.getMinecraft(level).connection; // a no-op handler: sends go nowhere
        }

        @Override public void tick() {}   // not a connected player: nothing drives a player tick (as for FakePlayer)
        @Override public void displayClientMessage(Component message, boolean actionBar) {}
        @Override public void awardStat(Stat<?> stat, int amount) {}
        @Override public boolean isFakePlayer() { return true; }
    }

    /** Runs the handler's work immediately on the calling (server) thread, as {@code enqueueWork} would next tick. */
    private record DirectPayloadContext(Player player) implements IPayloadContext {
        @Override
        public ICommonPacketListener listener() {
            throw new UnsupportedOperationException("a self-test payload context has no connection");
        }

        @Override
        public CompletableFuture<Void> enqueueWork(Runnable task) {
            task.run();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public <T> CompletableFuture<T> enqueueWork(Supplier<T> task) {
            return CompletableFuture.completedFuture(task.get());
        }

        @Override
        public PacketFlow flow() {
            return PacketFlow.SERVERBOUND;
        }

        @Override
        public void handle(CustomPacketPayload payload) {
            throw new UnsupportedOperationException("a self-test payload context cannot dispatch payloads");
        }

        @Override
        public void finishCurrentTask(ConfigurationTask.Type type) {
            throw new UnsupportedOperationException("a self-test payload context is not in the configuration phase");
        }
    }
}
