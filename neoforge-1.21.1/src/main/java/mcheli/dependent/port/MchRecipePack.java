package mcheli.dependent.port;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import mcheli.MCHeli;
import mcheli.dependent.item.MchVehicleRecipes;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * A synthesized SERVER_DATA pack serving one {@code data/mcheli/recipe/<vehicle>.json} per discovered vehicle, built
 * by {@link MchVehicleRecipes} from the parsed {@code AddRecipe} lines.
 *
 * <p>Sibling of {@link MchPackResources}, which does the same trick on the CLIENT_RESOURCES side for content-pack
 * textures and sounds. Generating the recipes rather than shipping static files is what lets a content-pack vehicle be
 * craftable: the fleet is discovered by a live resource scan at construction, so which recipes exist is not known
 * until runtime.
 *
 * <p>Because these are ordinary datapack entries, the game turns them into real {@code ShapedRecipe}/
 * {@code ShapelessRecipe} objects — they appear in the recipe book and in JEI/EMI, and they reload with {@code /reload}.
 */
public final class MchRecipePack extends AbstractPackResources {

    private static final int PACK_FORMAT = SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA);
    private static final String PREFIX = "recipe/";

    public MchRecipePack(PackLocationInfo location) {
        super(location);
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        if (path.length == 1 && PACK_META.equals(path[0])) {
            byte[] meta = bytes("{\"pack\":{\"description\":\"MCHeli vehicle recipes\",\"pack_format\":"
                + PACK_FORMAT + "}}");
            return () -> new ByteArrayInputStream(meta);
        }
        return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation loc) {
        if (type != PackType.SERVER_DATA || !MCHeli.MODID.equals(loc.getNamespace())) {
            return null;
        }
        String json = jsonFor(loc.getPath());
        if (json == null) {
            return null;
        }
        byte[] data = bytes(json);
        return () -> new ByteArrayInputStream(data);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput out) {
        if (type != PackType.SERVER_DATA || !MCHeli.MODID.equals(namespace) || !PREFIX.startsWith(path)
            && !path.startsWith(PREFIX)) {
            return;
        }
        for (Map.Entry<String, String> e : MchVehicleRecipes.table().entrySet()) {
            ResourceLocation id = ResourceLocation.tryBuild(namespace, PREFIX + e.getKey() + ".json");
            if (id == null) {
                continue;
            }
            byte[] data = bytes(e.getValue());
            out.accept(id, () -> new ByteArrayInputStream(data));
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.SERVER_DATA ? Set.of(MCHeli.MODID) : Set.of();
    }

    @Override
    public void close() {
        // nothing to release — every entry is an in-memory string
    }

    /** {@code recipe/<name>.json} -> the generated JSON, or null if this pack does not serve that path. */
    private static String jsonFor(String path) {
        if (!path.startsWith(PREFIX) || !path.endsWith(".json")) {
            return null;
        }
        String name = path.substring(PREFIX.length(), path.length() - ".json".length());
        return MchVehicleRecipes.table().get(name);
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
