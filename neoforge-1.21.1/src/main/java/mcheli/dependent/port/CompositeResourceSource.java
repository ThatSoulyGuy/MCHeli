package mcheli.dependent.port;

import com.mojang.logging.LogUtils;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import mcheli.agnostic.model.MchModel;
import mcheli.agnostic.spi.ModelHandle;
import mcheli.agnostic.spi.ResourceSource;
import org.slf4j.Logger;

/**
 * A {@link ResourceSource} that unions the bundled mcheli assets ({@link NeoResourceSource}) with any discovered
 * content-pack directories ({@link FileResourceSource}). The base source is FIRST, so the bundled content wins on a
 * name collision and packs are purely ADDITIVE — a pack contributes NEW config/model names, never silently replaces a
 * shipped one (matching the manager's first-wins {@code containsKey} skip). {@code list} is the DEDUPED union across
 * all sources; {@code openUtf8}/{@code exists}/{@code loadModel} return the first source that has the resource.
 *
 * <p>This is the single {@link ResourceSource} handed to registration (construction), config parsing (commonSetup),
 * and every model loader, so all three see the identical base+pack resource set. Parsed models are cached here (the
 * per-source loaders no longer cache/warn), so the "no model found" warning fires exactly once, from the true miss.
 */
public final class CompositeResourceSource implements ResourceSource {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final List<ResourceSource> sources;
    private final Map<String, MchModel> modelCache = new ConcurrentHashMap<>();

    /** @param sources base-first (bundled mcheli, then each pack); wins-on-collision follows this order. */
    public CompositeResourceSource(List<ResourceSource> sources) {
        this.sources = List.copyOf(sources);
    }

    @Override
    public List<String> list(String dir) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (ResourceSource s : this.sources) {
            out.addAll(s.list(dir));
        }
        return new ArrayList<>(out);
    }

    @Override
    public Reader openUtf8(String path) {
        for (ResourceSource s : this.sources) {
            Reader r = s.openUtf8(path);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    @Override
    public boolean exists(String path) {
        for (ResourceSource s : this.sources) {
            if (s.exists(path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ModelHandle loadModel(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        MchModel cached = this.modelCache.get(name);
        if (cached != null) {
            return cached;
        }
        for (ResourceSource s : this.sources) {
            ModelHandle h = s.loadModel(name);
            if (h instanceof MchModel m) {
                this.modelCache.put(name, m);
                return m;
            }
        }
        LOGGER.warn("MCHeli: no model found for '{}' in the mod or any content pack (models/{}.mqo/.obj)", name, name);
        return null;
    }
}
