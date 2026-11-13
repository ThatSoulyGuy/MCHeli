package mcheli.dependent.port;

import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import mcheli.agnostic.info.MCH_InfoManagerBase;
import mcheli.agnostic.info.MCH_SoundsJson;
import mcheli.agnostic.spi.ResourceSource;
import mcheli.dependent.registry.MchSounds;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * Runtime CONTENT-PACK discovery (#8). A content pack is a folder dropped under {@code <gamedir>/mcheli/<pack>/}. Its
 * content (the {@code helicopters/planes/tanks/vehicles/weapons/hud} configs + {@code models/textures/sounds}) may sit
 * EITHER directly at the pack root (flat) OR under an {@code assets/mcheli/} sub-dir (the resource-pack layout) — both
 * are accepted; the pack root that holds a recognizable content dir becomes its CONTENT ROOT. A pack adds NEW
 * vehicles/weapons/sounds without rebuilding the jar.
 *
 * <p>{@link #discover()} runs ONCE at the very start of the {@code @Mod} constructor — before the registries freeze —
 * because pack config filenames must be enumerable in time to mint spawn items ({@link mcheli.dependent.registry.MchRegistries#registerVehicles})
 * and pack sound events in time to register {@code SoundEvent}s ({@link mcheli.dependent.registry.MchSounds}). It:
 * <ul>
 *   <li>builds the shared {@link CompositeResourceSource} (bundled assets + every pack's content root) that
 *       registration, the commonSetup config parse, and every model loader read through;</li>
 *   <li>turns each pack's {@code sounds/*.ogg} files into sound events the way the reference {@code MCH_SoundsJson}
 *       did ({@link MCH_SoundsJson}: lower-cased names, numbered files grouped as variants of one event), for
 *       construction-time SoundEvent registration. A file whose name is not a legal resource id is skipped with a
 *       warning, exactly like an illegal config name, instead of crashing the {@code @Mod} constructor;</li>
 *   <li>records a {@link PackMount} per pack for the {@code AddPackFindersEvent} resource-pack registration that
 *       exposes pack textures + an (in-memory) {@code sounds.json} + {@code .ogg}s + generated spawn-item models to
 *       the client resource manager (see {@link MchPackResources}).</li>
 * </ul>
 *
 * <p>The two asset channels: configs + models flow through {@link ResourceSource} ({@link #resources()}); textures +
 * {@code .ogg}s + {@code sounds.json} + item models flow through the vanilla client resource manager
 * ({@link MchPackResources}). Both follow one precedence rule: bundled content wins every name collision, then the
 * earlier pack (sorted by folder name). Packs ADD content; replacing a shipped texture is a resource pack's job.
 *
 * <p><b>Packs must match on client and server.</b> Each pack vehicle is an item and each pack sound a sound event, and
 * both registries are synced on join: a client missing a server pack's entries is disconnected by NeoForge's registry
 * sync, with a message listing the missing {@code mcheli:} ids. A pack only the client has is not detected, nor is a
 * pack whose files differ under the same names.
 */
public final class MchContentPacks {
    private MchContentPacks() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    /** The pack container relative to the game directory (user-facing: drop packs here). */
    public static final String PACKS_DIRNAME = "mcheli";
    /** A pack MAY nest its content under this resource-pack-style sub-path; if absent, the pack root is the content root. */
    private static final String ASSET_ROOT = "assets/mcheli";
    /** A folder is treated as a pack only if its content root contains at least one of these (else it is not a pack). */
    private static final Set<String> CONTENT_DIRS = Set.of(
        "helicopters", "planes", "tanks", "vehicles", "weapons", "hud", "models", "textures", "sounds");

    /**
     * One discovered pack.
     *
     * @param name         the folder name
     * @param contentRoot  the resolved content root (the pack folder, or its {@code assets/mcheli/})
     * @param soundEvents  the sound events THIS pack owns (event key -> variant sound names): legal, not bundled, and
     *                     not already owned by an earlier pack. Only these go into its {@code sounds.json}.
     * @param soundFiles   every legal sound name of the pack -> its {@code .ogg} file, whatever the file's case
     * @param vehicleNames the definition names of the pack's vehicle configs (each gets a generated item model)
     */
    public record PackMount(String name, Path contentRoot, Map<String, List<String>> soundEvents,
                            Map<String, Path> soundFiles, Set<String> vehicleNames) {}

    /** The vehicle config directories, as in {@code MchRegistries.Category}. */
    private static final List<String> VEHICLE_DIRS = List.of("helicopters", "planes", "tanks", "vehicles");

    private static boolean discovered = false;
    // Reassigned once in discover() (mod-load thread), then read from render/tick threads — volatile for safe publication.
    // Bundled-only until discover() runs, but still behind the composite: it is the only model cache (NeoResourceSource
    // parses on every call), and the one clearModelCaches() empties.
    private static volatile ResourceSource shared = new CompositeResourceSource(List.of(new NeoResourceSource()));
    private static final List<PackMount> MOUNTS = new ArrayList<>();
    private static final List<String> PACK_SOUND_EVENTS = new ArrayList<>();

    /** Scan {@code <gamedir>/mcheli/} for packs and build the shared resource set. Idempotent; call once, first. */
    public static synchronized void discover() {
        if (discovered) {
            return;
        }
        discovered = true;

        Path container = FMLPaths.GAMEDIR.get().resolve(PACKS_DIRNAME);
        List<ResourceSource> sources = new ArrayList<>();
        sources.add(new NeoResourceSource()); // BASE first: bundled content wins on a name collision (packs are additive)

        if (Files.isDirectory(container)) {
            for (Path packDir : listSubdirs(container)) {
                // Accept EITHER layout: prefer <pack>/assets/mcheli/ (resource-pack style) if present, else the pack root.
                Path assetRoot = packDir.resolve(ASSET_ROOT);
                boolean nested = Files.isDirectory(assetRoot);
                Path contentRoot = nested ? assetRoot : packDir;
                if (!hasAnyContentDir(contentRoot)) {
                    LOGGER.warn("MCHeli: '{}' under /{} has no recognizable content (no {} dir) — skipping",
                        packDir.getFileName(), PACKS_DIRNAME, CONTENT_DIRS);
                    continue;
                }
                String packName = packDir.getFileName().toString();
                FileResourceSource source = new FileResourceSource(contentRoot);
                Map<String, Path> soundFiles = scanSounds(contentRoot, packName);
                Map<String, List<String>> owned = new LinkedHashMap<>();
                for (Map.Entry<String, List<String>> ev : MCH_SoundsJson.group(soundFiles.keySet(),
                        bad -> LOGGER.warn("MCHeli: skipping content-pack sound '{}' in '{}' — illegal name (only "
                            + "[a-z0-9_.-] allowed)", bad, packName)).entrySet()) {
                    String key = ev.getKey();
                    if (MchSounds.isBundled(key) || PACK_SOUND_EVENTS.contains(key)) {
                        LOGGER.warn("MCHeli: content pack '{}' sound event '{}' ignored — {} already defines it",
                            packName, key, MchSounds.isBundled(key) ? "MCHeli" : "an earlier pack");
                        continue;
                    }
                    owned.put(key, List.copyOf(ev.getValue()));
                    PACK_SOUND_EVENTS.add(key);
                }
                sources.add(source);
                MOUNTS.add(new PackMount(packName, contentRoot, Collections.unmodifiableMap(owned),
                    Map.copyOf(soundFiles), vehicleNames(source)));
                LOGGER.info("MCHeli: discovered content pack '{}' ({} layout, {} sound event(s))",
                    packName, nested ? "assets/mcheli" : "flat", owned.size());
            }
        }

        shared = new CompositeResourceSource(sources); // ALWAYS wrap: the composite owns the "no model found" warn
        if (!MOUNTS.isEmpty()) {
            // A pack is content on BOTH sides (items + sound events are synced registries): say so where an operator looks.
            LOGGER.info("MCHeli: {} content pack(s) active ({}), {} pack sound event(s) — clients need the same packs",
                MOUNTS.size(), MOUNTS.stream().map(PackMount::name).collect(Collectors.joining(", ")),
                PACK_SOUND_EVENTS.size());
        }
    }

    /** The shared resource set (bundled + packs). Every {@code new NeoResourceSource()} call site should read this. */
    public static ResourceSource resources() {
        return shared;
    }

    /**
     * Forget every parsed model and every remembered model miss, so the next lookup re-reads the files. Run by the
     * client resource-reload listener ({@code MchClientEvents}) so F3+T picks up edited pack/dev models without a
     * restart. Thread-safe (the composite's caches are concurrent); callable from the reload's background phase.
     */
    public static void clearModelCaches() {
        if (shared instanceof CompositeResourceSource c) {
            c.clearCaches();
        }
        modelGeneration++;
    }

    // Bumped by every clearModelCaches(), so per-entity values derived from a model (AbstractMchVehicle.modelMinY)
    // know to re-derive. Written from the reload's background phase, read on the tick/render threads.
    private static volatile int modelGeneration;

    /** A counter that changes whenever the model caches are cleared; compare it to re-derive cached model values. */
    public static int modelGeneration() {
        return modelGeneration;
    }

    /** Each discovered pack's mount info — the client asset finder ({@link MchPackResourcesFinder}) mounts each one. */
    public static List<PackMount> mounts() {
        return List.copyOf(MOUNTS);
    }

    /** Every sound event the packs own (see {@link PackMount#soundEvents}) — one {@code SoundEvent} must be registered
     *  per name before the freeze. */
    public static List<String> packSoundEvents() {
        return List.copyOf(PACK_SOUND_EVENTS);
    }

    // ---- internals ----

    private static List<Path> listSubdirs(Path container) {
        try (Stream<Path> s = Files.list(container)) {
            return s.filter(Files::isDirectory)
                .sorted() // deterministic pack order (load order affects collision resolution)
                .collect(Collectors.toList());
        } catch (Exception e) {
            LOGGER.warn("MCHeli: could not list content-pack dir {}", container, e);
            return List.of();
        }
    }

    private static boolean hasAnyContentDir(Path contentRoot) {
        for (String d : CONTENT_DIRS) {
            if (Files.isDirectory(contentRoot.resolve(d))) {
                return true;
            }
        }
        return false;
    }

    /**
     * A pack's {@code sounds/*.ogg} files by sound name (the lower-cased base name, reference {@code MCH_SoundsJson}),
     * sorted by name for a stable event order. The {@code sounds.json} that indexes them is synthesized on the fly by
     * {@link MchPackResources}; no file is written into the user's pack. Two files differing only in case are one name:
     * the first (in sorted file-name order) is kept.
     */
    private static Map<String, Path> scanSounds(Path contentRoot, String packName) {
        Path soundsDir = contentRoot.resolve("sounds");
        if (!Files.isDirectory(soundsDir)) {
            return Map.of();
        }
        Map<String, Path> byName = new TreeMap<>();
        try (Stream<Path> s = Files.list(soundsDir)) {
            for (Path p : s.filter(Files::isRegularFile).sorted().toList()) {
                String name = MCH_SoundsJson.soundName(p.getFileName().toString());
                if (name != null && byName.putIfAbsent(name, p) != null) {
                    LOGGER.warn("MCHeli: content pack '{}' has two sound files named '{}' (case differs) — using {}",
                        packName, name, byName.get(name).getFileName());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("MCHeli: could not scan pack sounds under {}", soundsDir, e);
            return Map.of();
        }
        return byName;
    }

    /** The definition names of a pack's own vehicle configs (the manager's naming rule), for its item models. */
    private static Set<String> vehicleNames(FileResourceSource source) {
        Set<String> names = new TreeSet<>();
        for (String dir : VEHICLE_DIRS) {
            for (String file : source.list(dir)) {
                String name = MCH_InfoManagerBase.definitionName(file);
                if (name != null && MCH_SoundsJson.isLegalName(name)) {
                    names.add(name);
                }
            }
        }
        return Collections.unmodifiableSet(names);
    }
}
