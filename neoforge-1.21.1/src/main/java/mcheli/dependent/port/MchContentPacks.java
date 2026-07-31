package mcheli.dependent.port;

import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import mcheli.agnostic.spi.ResourceSource;
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
 * and pack sound basenames in time to register {@code SoundEvent}s ({@link mcheli.dependent.registry.MchSounds}). It:
 * <ul>
 *   <li>builds the shared {@link CompositeResourceSource} (bundled assets + every pack's content root) that
 *       registration, the commonSetup config parse, and every model loader read through;</li>
 *   <li>collects each pack's {@code sounds/*.ogg} basenames for construction-time SoundEvent registration;</li>
 *   <li>records a {@link PackMount} per pack for the {@code AddPackFindersEvent} resource-pack registration that
 *       exposes pack textures + an (in-memory) {@code sounds.json} + {@code .ogg}s to the client resource manager
 *       (see {@link MchPackResources}).</li>
 * </ul>
 *
 * <p>The two asset channels: configs + models flow through {@link ResourceSource} ({@link #resources()}); textures +
 * {@code .ogg}s + {@code sounds.json} flow through the vanilla client resource manager ({@link MchPackResources}).
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

    /** One discovered pack: its display name + resolved content root + its {@code sounds/*.ogg} basenames. */
    public record PackMount(String name, Path contentRoot, List<String> soundBasenames) {}

    private static boolean discovered = false;
    // Reassigned once in discover() (mod-load thread), then read from render/tick threads — volatile for safe publication.
    private static volatile ResourceSource shared = new NeoResourceSource(); // bundled-only until discover() runs
    private static final List<PackMount> MOUNTS = new ArrayList<>();
    private static final List<String> PACK_SOUND_BASENAMES = new ArrayList<>();

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
                List<String> sounds = scanSounds(contentRoot);
                sources.add(new FileResourceSource(contentRoot));
                MOUNTS.add(new PackMount(packDir.getFileName().toString(), contentRoot, sounds));
                PACK_SOUND_BASENAMES.addAll(sounds);
                LOGGER.info("MCHeli: discovered content pack '{}' ({} layout, {} sound(s))",
                    packDir.getFileName(), nested ? "assets/mcheli" : "flat", sounds.size());
            }
        }

        shared = new CompositeResourceSource(sources); // ALWAYS wrap: the composite owns the "no model found" warn
        if (!MOUNTS.isEmpty()) {
            LOGGER.info("MCHeli: {} content pack(s) active, {} pack sound(s) registered",
                MOUNTS.size(), PACK_SOUND_BASENAMES.size());
        }
    }

    /** The shared resource set (bundled + packs). Every {@code new NeoResourceSource()} call site should read this. */
    public static ResourceSource resources() {
        return shared;
    }

    /** Each discovered pack's mount info — the client asset finder ({@link MchPackResourcesFinder}) mounts each one. */
    public static List<PackMount> mounts() {
        return List.copyOf(MOUNTS);
    }

    /** Every pack {@code sounds/*.ogg} basename — one {@code SoundEvent} must be registered per name before the freeze. */
    public static List<String> packSoundBasenames() {
        return List.copyOf(PACK_SOUND_BASENAMES);
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

    /** Collect a pack's {@code sounds/*.ogg} basenames (the {@code sounds.json} that indexes them is synthesized on the
     *  fly by {@link MchPackResources} — no file is written into the user's pack). */
    private static List<String> scanSounds(Path contentRoot) {
        Path soundsDir = contentRoot.resolve("sounds");
        if (!Files.isDirectory(soundsDir)) {
            return List.of();
        }
        Set<String> basenames = new LinkedHashSet<>();
        try (Stream<Path> s = Files.list(soundsDir)) {
            s.filter(Files::isRegularFile)
                .map(p -> p.getFileName().toString())
                .filter(n -> n.toLowerCase().endsWith(".ogg"))
                .forEach(n -> basenames.add(n.substring(0, n.length() - 4)));
        } catch (Exception e) {
            LOGGER.warn("MCHeli: could not scan pack sounds under {}", soundsDir, e);
            return List.of();
        }
        return new ArrayList<>(basenames);
    }
}
