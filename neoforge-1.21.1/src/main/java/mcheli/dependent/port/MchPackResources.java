package mcheli.dependent.port;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import mcheli.MCHeli;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * A {@link net.minecraft.server.packs.PackResources} that exposes one content pack's CLIENT assets (textures + sound
 * {@code .ogg}s + a {@code sounds.json}) to the vanilla resource manager, mapping the {@code mcheli} namespace directly
 * to the pack's CONTENT ROOT. That root is whatever {@link MchContentPacks} detected — the pack dir itself for a FLAT
 * pack ({@code <pack>/textures/...}) or its {@code assets/mcheli/} sub-dir for a resource-pack-style pack — so BOTH
 * layouts serve identically here, without requiring the {@code assets/mcheli/} nesting {@code PathPackResources} needs.
 *
 * <p>{@code pack.mcmeta} and {@code sounds.json} are SYNTHESIZED in memory (no files written into the user's pack dir):
 * the mcmeta carries the running client-resource pack format (via {@link AbstractPackResources}'s
 * {@code getMetadataSection}, which reads our generated {@code getRootResource("pack.mcmeta")}); the sounds.json is
 * built from the pack's {@code sounds/*.ogg} basenames (mirroring the reference {@code MCH_SoundsJson}) unless the pack
 * ships its own. Only {@link PackType#CLIENT_RESOURCES}/{@code mcheli} is served — models + configs come through the
 * separate {@link mcheli.agnostic.spi.ResourceSource} channel, and the dedicated server needs none of this.
 */
public final class MchPackResources extends AbstractPackResources {

    private static final int PACK_FORMAT = SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);

    private final Path contentRoot;
    private final String packName;
    private final List<String> soundBasenames;

    public MchPackResources(PackLocationInfo location, Path contentRoot, String packName, List<String> soundBasenames) {
        super(location);
        this.contentRoot = contentRoot.normalize();
        this.packName = packName;
        this.soundBasenames = soundBasenames;
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        if (path.length == 1 && PACK_META.equals(path[0])) {
            byte[] meta = bytes("{\"pack\":{\"description\":\"MCHeli content pack: " + this.packName
                + "\",\"pack_format\":" + PACK_FORMAT + "}}");
            return () -> new ByteArrayInputStream(meta);
        }
        Path p = resolveSafe(String.join("/", path));
        return p != null && Files.isRegularFile(p) ? IoSupplier.create(p) : null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation loc) {
        if (type != PackType.CLIENT_RESOURCES || !MCHeli.MODID.equals(loc.getNamespace())) {
            return null;
        }
        String path = loc.getPath();
        if ("sounds.json".equals(path)) {
            return soundsJsonSupplier();
        }
        Path p = resolveSafe(path);
        return p != null && Files.isRegularFile(p) ? IoSupplier.create(p) : null;
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput out) {
        if (type != PackType.CLIENT_RESOURCES || !MCHeli.MODID.equals(namespace)) {
            return;
        }
        Path base = resolveSafe(path);
        if (base == null || !Files.isDirectory(base)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(base)) {
            walk.filter(Files::isRegularFile).forEach(f -> {
                String rel = this.contentRoot.relativize(f).toString().replace('\\', '/');
                ResourceLocation id = ResourceLocation.tryBuild(namespace, rel);
                if (id != null) {
                    out.accept(id, IoSupplier.create(f));
                }
            });
        } catch (IOException ignored) {
            // an unreadable pack subtree yields no listed resources (never throws into the resource manager)
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Set.of(MCHeli.MODID) : Set.of();
    }

    @Override
    public void close() {
        // nothing to release (files are opened lazily per IoSupplier)
    }

    /** Serve the pack's own {@code sounds.json} if it ships one; otherwise synthesize one from the {@code .ogg}s. */
    private IoSupplier<InputStream> soundsJsonSupplier() {
        Path shipped = resolveSafe("sounds.json");
        if (shipped != null && Files.isRegularFile(shipped)) {
            return IoSupplier.create(shipped);
        }
        if (this.soundBasenames.isEmpty()) {
            return null;
        }
        byte[] json = bytes(this.soundBasenames.stream()
            .map(n -> "  \"" + n + "\": { \"sounds\": [\"mcheli:" + n + "\"] }")
            .collect(Collectors.joining(",\n", "{\n", "\n}\n")));
        return () -> new ByteArrayInputStream(json);
    }

    /** Resolve a namespace-relative path under the content root, rejecting any {@code ..} traversal outside it. */
    private Path resolveSafe(String rel) {
        Path p = this.contentRoot.resolve(rel).normalize();
        return p.startsWith(this.contentRoot) ? p : null;
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
