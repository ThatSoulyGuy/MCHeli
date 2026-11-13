package mcheli.dependent.port;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Optional;
import mcheli.dependent.port.MchContentPacks.PackMount;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.slf4j.Logger;

/**
 * Mounts the discovered content packs ({@link MchContentPacks#mounts()}) as ONE fixed, always-enabled client resource
 * pack, so the vanilla resource manager serves the packs' TEXTURES + {@code .ogg}s + (synthesized) {@code sounds.json}
 * and spawn-item models — the second asset channel that {@link mcheli.agnostic.spi.ResourceSource} (configs + models)
 * does NOT cover. The serving is done by {@link MchPackResources}, which maps the {@code mcheli} namespace straight to
 * each pack's content root, so BOTH the flat and {@code assets/mcheli/} pack layouts work with no on-disk
 * {@code pack.mcmeta}/{@code sounds.json} generation.
 *
 * <p>Common class (only {@code net.minecraft.server.packs.*} — no {@code net.minecraft.client.*}); it does nothing for
 * {@code SERVER_DATA}/on a dedicated server (textures + audio are client-only). Registered on the mod bus in the MCHeli
 * constructor.
 */
public final class MchPackResourcesFinder {
    private MchPackResourcesFinder() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Required (users never have to enable it) and FIXED at the BOTTOM of the stack, below the mod's own resources: a
     * pack texture, {@code .ogg} or model named like a bundled one is shadowed by the bundled file, the same rule the
     * configs, models and sound events follow (bundled wins, packs only add; see {@link MchContentPacks}). Between
     * packs the earlier one in folder order wins, inside {@link MchPackResources}, as it does for configs. A user who
     * wants to re-skin a shipped vehicle does it with an ordinary resource pack, which sits above all of these.
     */
    static final PackSelectionConfig CONTENT_PACK_SELECTION = new PackSelectionConfig(true, Pack.Position.BOTTOM, true);

    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.SERVER_DATA) {
            addRecipePack(event);
            return; // pack textures/audio are client resources; the recipes are the only server-side contribution
        }
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        List<PackMount> mounts = MchContentPacks.mounts();
        if (mounts.isEmpty()) {
            return;
        }
        // ONE resource pack for all content packs, so they stack in discovery order (see MchPackResources).
        PackLocationInfo location = new PackLocationInfo(
            "mcheli/content_packs", Component.literal("MCHeli content packs"), PackSource.BUILT_IN, Optional.empty());
        Pack.ResourcesSupplier resources = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo loc) {
                return new MchPackResources(loc, mounts);
            }
            @Override
            public PackResources openFull(PackLocationInfo loc, Pack.Metadata meta) {
                return new MchPackResources(loc, mounts);
            }
        };
        Pack pack = Pack.readMetaAndCreate(location, resources, PackType.CLIENT_RESOURCES, CONTENT_PACK_SELECTION);
        if (pack == null) {
            LOGGER.warn("MCHeli: the content packs produced no resource pack (metadata error?)");
            return;
        }
        event.addRepositorySource(consumer -> consumer.accept(pack));
        LOGGER.info("MCHeli: mounted client resources of {} content pack(s)", mounts.size());
    }

    /**
     * Mount the generated vehicle crafting recipes ({@link MchRecipePack}). Fixed + required + BOTTOM: unlike the
     * asset packs these must NOT override anything — a datapack an operator adds should still be able to replace a
     * generated recipe, and the four hand-written {@code data/mcheli/recipe/*.json} files keep priority.
     */
    private static void addRecipePack(AddPackFindersEvent event) {
        PackLocationInfo location = new PackLocationInfo(
            "mcheli/recipes", Component.literal("MCHeli vehicle recipes"), PackSource.BUILT_IN, Optional.empty());
        Pack.ResourcesSupplier resources = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo loc) {
                return new MchRecipePack(loc);
            }
            @Override
            public PackResources openFull(PackLocationInfo loc, Pack.Metadata meta) {
                return new MchRecipePack(loc);
            }
        };
        PackSelectionConfig selection = new PackSelectionConfig(true, Pack.Position.BOTTOM, false);
        Pack pack = Pack.readMetaAndCreate(location, resources, PackType.SERVER_DATA, selection);
        if (pack == null) {
            LOGGER.warn("MCHeli: vehicle recipe pack produced no datapack (metadata error?)");
            return;
        }
        event.addRepositorySource(consumer -> consumer.accept(pack));
        LOGGER.info("MCHeli: mounted generated vehicle recipe datapack");
    }
}
