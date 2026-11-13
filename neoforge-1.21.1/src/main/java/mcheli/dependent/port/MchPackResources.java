package mcheli.dependent.port;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import mcheli.MCHeli;
import mcheli.agnostic.info.MCH_SoundsJson;
import mcheli.dependent.port.MchContentPacks.PackMount;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * A {@link net.minecraft.server.packs.PackResources} that exposes the content packs' CLIENT assets (textures + sound
 * {@code .ogg}s + a {@code sounds.json} + spawn-item models) to the vanilla resource manager, mapping the {@code mcheli}
 * namespace directly to each pack's CONTENT ROOT. That root is whatever {@link MchContentPacks} detected — the pack dir
 * itself for a FLAT pack ({@code <pack>/textures/...}) or its {@code assets/mcheli/} sub-dir for a resource-pack-style
 * pack — so BOTH layouts serve identically here, without requiring the {@code assets/mcheli/} nesting
 * {@code PathPackResources} needs.
 *
 * <p>ONE instance serves every pack, asking them in discovery (folder-name) order, so the earlier pack wins a file-name
 * clash exactly as it wins a config or model clash in {@link CompositeResourceSource}. (Separate vanilla packs would
 * stack the other way round: the repository sorts packs by id and the later one ends up on top.) The bundled assets
 * win over all of it: see {@link MchPackResourcesFinder#CONTENT_PACK_SELECTION}.
 *
 * <p>Three files are SYNTHESIZED in memory (nothing is written into the user's pack dirs):
 * <ul>
 *   <li>{@code pack.mcmeta}, carrying the running client-resource pack format (read through
 *       {@link AbstractPackResources}'s {@code getMetadataSection});</li>
 *   <li>{@code sounds.json}, built from the packs' sound events by the reference {@code MCH_SoundsJson} rules
 *       ({@link MCH_SoundsJson}: {@code engine1.ogg} + {@code engine2.ogg} are the two variants of the event
 *       {@code engine}). Only events a pack OWNS are written, never one MCHeli or an earlier pack defines, so a pack
 *       cannot graft extra variants onto them (the sound manager merges same-named entries across packs). A pack that
 *       ships its own {@code sounds.json} contributes that file's entries instead of generated ones;</li>
 *   <li>{@code models/item/<vehicle>.json} for each pack vehicle config whose pack does not ship one: the
 *       {@code builtin/entity} model every bundled spawn item has, which the 3D-icon renderer
 *       ({@code VehicleItemRenderer}) needs. Without it a pack item renders as the missing-model cube.</li>
 * </ul>
 * A sound's {@code .ogg} is found whatever the case of its file name ({@code Engine1.OGG} serves
 * {@code sounds/engine1.ogg}), since the event names are lower-cased as in the reference.
 *
 * <p>Only {@link PackType#CLIENT_RESOURCES}/{@code mcheli} is served — models + configs come through the separate
 * {@link mcheli.agnostic.spi.ResourceSource} channel, and the dedicated server needs none of this.
 */
public final class MchPackResources extends AbstractPackResources {

    private static final int PACK_FORMAT = SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);
    private static final String ITEM_MODELS = "models/item/";
    /** Same as every bundled {@code models/item/<vehicle>.json} (e.g. {@code ah-64.json}). */
    private static final String SPAWN_ITEM_MODEL = "{\"parent\":\"builtin/entity\",\"display\":{"
        + "\"gui\":{\"rotation\":[30,225,0],\"translation\":[0,0,0],\"scale\":[1,1,1]},"
        + "\"ground\":{\"scale\":[0.5,0.5,0.5]},\"fixed\":{\"rotation\":[0,180,0]},"
        + "\"thirdperson_righthand\":{\"scale\":[0.6,0.6,0.6]},"
        + "\"firstperson_righthand\":{\"rotation\":[0,-90,0],\"scale\":[0.5,0.5,0.5]}}}";

    private final List<PackMount> mounts;

    /** @param mounts the discovered packs, in discovery order (the first one wins a clash) */
    public MchPackResources(PackLocationInfo location, List<PackMount> mounts) {
        super(location);
        this.mounts = List.copyOf(mounts);
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        if (path.length == 1 && PACK_META.equals(path[0])) {
            String names = this.mounts.stream().map(PackMount::name).collect(Collectors.joining(", "));
            byte[] meta = bytes("{\"pack\":{\"description\":\"MCHeli content packs: " + names.replace("\"", "'")
                + "\",\"pack_format\":" + PACK_FORMAT + "}}");
            return () -> new ByteArrayInputStream(meta);
        }
        for (PackMount m : this.mounts) {
            Path p = resolveSafe(m, String.join("/", path));
            if (p != null && Files.isRegularFile(p)) {
                return IoSupplier.create(p);
            }
        }
        return null;
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
        for (PackMount m : this.mounts) {
            Path p = resolveSafe(m, path);
            if (p != null && Files.isRegularFile(p)) {
                return IoSupplier.create(p); // a file a pack ships always wins over a generated one
            }
            if (path.startsWith("sounds/") && path.endsWith(".ogg")) {
                Path ogg = m.soundFiles().get(path.substring("sounds/".length(), path.length() - 4));
                if (ogg != null && Files.isRegularFile(ogg)) {
                    return IoSupplier.create(ogg);
                }
            }
            if (generatesItemModel(m, path)) {
                return supplier(SPAWN_ITEM_MODEL);
            }
        }
        return null;
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput out) {
        if (type != PackType.CLIENT_RESOURCES || !MCHeli.MODID.equals(namespace)) {
            return;
        }
        Set<ResourceLocation> seen = new HashSet<>();
        String dir = path.isEmpty() || path.endsWith("/") ? path : path + "/";
        for (PackMount m : this.mounts) {
            Path base = resolveSafe(m, path);
            if (base != null && Files.isDirectory(base)) {
                Path root = m.contentRoot().normalize();
                try (Stream<Path> walk = Files.walk(base)) {
                    walk.filter(Files::isRegularFile).forEach(f -> {
                        String rel = root.relativize(f).toString().replace('\\', '/');
                        ResourceLocation id = ResourceLocation.tryBuild(namespace, rel);
                        if (id != null && seen.add(id)) {
                            out.accept(id, IoSupplier.create(f));
                        }
                    });
                } catch (IOException ignored) {
                    // an unreadable pack subtree yields no listed resources (never throws into the resource manager)
                }
            }
            // The model bakery LISTS models/ to find item models, so the generated ones must be listed too.
            if (ITEM_MODELS.startsWith(dir) || path.startsWith(ITEM_MODELS)) {
                for (String vehicle : m.vehicleNames()) {
                    String rel = ITEM_MODELS + vehicle + ".json";
                    ResourceLocation id = rel.startsWith(path) && generatesItemModel(m, rel)
                        ? ResourceLocation.tryBuild(namespace, rel) : null;
                    if (id != null && seen.add(id)) {
                        out.accept(id, supplier(SPAWN_ITEM_MODEL));
                    }
                }
            }
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

    /**
     * The combined {@code sounds.json}: each pack's owned events, in pack order. A pack that ships its own
     * {@code sounds.json} contributes that file's entries instead of generated ones, minus any key MCHeli or an earlier
     * pack already defines (same rule as the generated part).
     */
    private IoSupplier<InputStream> soundsJsonSupplier() {
        Map<String, List<String>> generated = new LinkedHashMap<>();
        JsonObject shipped = new JsonObject();
        for (PackMount m : this.mounts) {
            Path own = resolveSafe(m, "sounds.json");
            if (own != null && Files.isRegularFile(own)) {
                try {
                    JsonObject entries = JsonParser.parseString(Files.readString(own, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                    for (Map.Entry<String, JsonElement> e : entries.entrySet()) {
                        if (!mcheli.dependent.registry.MchSounds.isBundled(e.getKey())
                                && !generated.containsKey(e.getKey()) && !shipped.has(e.getKey())) {
                            shipped.add(e.getKey(), e.getValue());
                        }
                    }
                } catch (IOException | RuntimeException ignored) {
                    // unreadable or not a JSON object: that pack contributes no sounds.json entries
                }
                continue;
            }
            m.soundEvents().forEach((key, variants) -> {
                if (!shipped.has(key)) {
                    generated.putIfAbsent(key, variants);
                }
            });
        }
        if (generated.isEmpty() && shipped.isEmpty()) {
            return null;
        }
        JsonObject all = JsonParser.parseString(MCH_SoundsJson.toJson(generated, MCHeli.MODID)).getAsJsonObject();
        shipped.entrySet().forEach(e -> all.add(e.getKey(), e.getValue()));
        return supplier(all.toString());
    }

    /** True if {@code path} is {@code models/item/<vehicle>.json} for one of {@code m}'s vehicles and {@code m} does not
     *  ship that file itself. */
    private static boolean generatesItemModel(PackMount m, String path) {
        if (!path.startsWith(ITEM_MODELS) || !path.endsWith(".json")) {
            return false;
        }
        String vehicle = path.substring(ITEM_MODELS.length(), path.length() - ".json".length());
        if (!m.vehicleNames().contains(vehicle)) {
            return false;
        }
        Path shipped = resolveSafe(m, path);
        return shipped == null || !Files.isRegularFile(shipped);
    }

    /** Resolve a namespace-relative path under a pack's content root, rejecting any {@code ..} traversal outside it. */
    private static Path resolveSafe(PackMount m, String rel) {
        Path root = m.contentRoot().normalize();
        Path p = root.resolve(rel).normalize();
        return p.startsWith(root) ? p : null;
    }

    private static IoSupplier<InputStream> supplier(String text) {
        byte[] data = bytes(text);
        return () -> new ByteArrayInputStream(data);
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
