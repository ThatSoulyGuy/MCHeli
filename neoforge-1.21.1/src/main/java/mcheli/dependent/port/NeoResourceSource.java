package mcheli.dependent.port;

import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import mcheli.MCHeli;
import mcheli.agnostic.model.MchModel;
import mcheli.agnostic.model.MchModelLoader;
import mcheli.agnostic.spi.ModelHandle;
import mcheli.agnostic.spi.ResourceSource;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * {@link ResourceSource} reading MCHeli's bundled definition files from the mod's own resources
 * ({@code assets/mcheli/...}). Text files are opened via the CLASSPATH ({@code getResourceAsStream}) and directories
 * listed via the NeoForge mod file ({@code IModFile.findResource} → a {@link Path} in the mod's file system, walkable
 * in both the dev directory and a packaged jar). Classpath-based so it works on server AND client at any lifecycle
 * point, without the assets-vs-datapack split or a resource reload — the reference read these off disk at mod init.
 *
 * <p>{@link #loadModel} resolves a model NAME ({@code <category>/<vehicle>}, as the reference's
 * {@code MCH_ModelManager.load} did) to {@code models/<name>.mqo} then {@code .obj}, parses it through the
 * agnostic {@link MchModelLoader}, and caches the result. The parsed {@link MchModel} is the {@link ModelHandle}
 * the dependent renderer downcasts.
 */
public final class NeoResourceSource implements ResourceSource {
    private static final String ROOT = "assets/mcheli/";
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Shared across instances, like the reference's static model map — a model is parsed at most once. */
    private static final Map<String, MchModel> MODEL_CACHE = new ConcurrentHashMap<>();

    @Override
    public List<String> list(String dir) {
        List<String> out = new ArrayList<>();
        try {
            Path base = ModList.get().getModFileById(MCHeli.MODID).getFile().findResource("assets", "mcheli", dir);
            if (base != null && Files.isDirectory(base)) {
                try (Stream<Path> s = Files.list(base)) {
                    s.filter(Files::isRegularFile).forEach(p -> out.add(p.getFileName().toString()));
                }
            }
        } catch (Exception ignored) {
            // missing dir / unreadable mod file -> empty list (the manager logs "Read 0 <type>")
        }
        return out;
    }

    @Override
    public Reader openUtf8(String path) {
        InputStream in = NeoResourceSource.class.getResourceAsStream("/" + ROOT + path);
        return in == null ? null : new InputStreamReader(in, StandardCharsets.UTF_8);
    }

    @Override
    public boolean exists(String path) {
        return NeoResourceSource.class.getResource("/" + ROOT + path) != null;
    }

    @Override
    public ModelHandle loadModel(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        MchModel cached = MODEL_CACHE.get(name);
        if (cached != null) {
            return cached;
        }
        // MCH_ModelManager tried .mqo, then .obj, then .tcn — we support .mqo/.obj (no .tcn in the assets).
        MchModel model = tryParse("models/" + name + ".mqo");
        if (model == null) {
            model = tryParse("models/" + name + ".obj");
        }
        if (model != null) {
            MODEL_CACHE.put(name, model);
        } else {
            LOGGER.warn("MCHeli: no model found for '{}' (looked for models/{}.mqo/.obj)", name, name);
        }
        return model;
    }

    /** Open {@code assets/mcheli/<resPath>} off the classpath and parse it; null if absent, or on a parse error
     *  (logged, non-fatal — a broken model must not crash mod load). The parser closes the stream. */
    private static MchModel tryParse(String resPath) {
        InputStream in = NeoResourceSource.class.getResourceAsStream("/" + ROOT + resPath);
        if (in == null) {
            return null;
        }
        try {
            return MchModelLoader.load(resPath, in);
        } catch (RuntimeException e) {
            LOGGER.warn("MCHeli: failed to parse model {}", resPath, e);
            return null;
        }
    }
}
