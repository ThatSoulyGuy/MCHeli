package mcheli.dependent.port;

import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import mcheli.agnostic.model.MchModel;
import mcheli.agnostic.model.MchModelLoader;
import mcheli.agnostic.spi.ModelHandle;
import mcheli.agnostic.spi.ResourceSource;
import org.slf4j.Logger;

/**
 * A {@link ResourceSource} over a content-pack directory on the real filesystem — the runtime counterpart to the
 * classpath-bound {@link NeoResourceSource}, so a user-dropped pack under {@code <gamedir>/mcheli/<pack>/} loads the
 * same way the bundled {@code assets/mcheli/} tree does. Rooted at the pack's {@code assets/mcheli/} directory (the
 * standard resource-pack layout), so {@code list("helicopters")} → {@code <root>/helicopters/*.txt} and
 * {@code loadModel("helicopters/ah-64")} → {@code <root>/models/helicopters/ah-64.mqo|obj}, mirroring the reference's
 * {@code File}-based {@code MCH_InfoManagerBase}/{@code MCH_ModelManager}.
 *
 * <p>No model cache here — the composite ({@link CompositeResourceSource}) caches across sources, and the renderers
 * keep their own caches. Every read is defensive: a missing/unreadable pack file returns null/empty, never throws.
 */
public final class FileResourceSource implements ResourceSource {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Path root;

    /** @param root the pack's {@code assets/mcheli/} directory (where the category folders + {@code models/} live). */
    public FileResourceSource(Path root) {
        this.root = root;
    }

    @Override
    public List<String> list(String dir) {
        List<String> out = new ArrayList<>();
        Path d = this.root.resolve(dir);
        if (!Files.isDirectory(d)) {
            return out;
        }
        try (Stream<Path> s = Files.list(d)) {
            s.filter(Files::isRegularFile).forEach(p -> out.add(p.getFileName().toString()));
        } catch (Exception ignored) {
            // unreadable pack dir -> empty (treated as "no configs here")
        }
        return out;
    }

    @Override
    public Reader openUtf8(String path) {
        Path p = this.root.resolve(path);
        if (!Files.isRegularFile(p)) {
            return null;
        }
        try {
            return Files.newBufferedReader(p, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean exists(String path) {
        return Files.isRegularFile(this.root.resolve(path));
    }

    @Override
    public ModelHandle loadModel(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        MchModel model = tryParse("models/" + name + ".mqo");
        if (model == null) {
            model = tryParse("models/" + name + ".obj");
        }
        return model; // null (not found here) is handled by the composite / caller
    }

    private MchModel tryParse(String resPath) {
        Path p = this.root.resolve(resPath);
        if (!Files.isRegularFile(p)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(p)) {
            return MchModelLoader.load(resPath, in);
        } catch (RuntimeException e) {
            LOGGER.warn("MCHeli: failed to parse pack model {}", p, e);
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
