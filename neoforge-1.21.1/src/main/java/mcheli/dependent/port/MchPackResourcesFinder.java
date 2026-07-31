package mcheli.dependent.port;

import com.mojang.logging.LogUtils;
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
 * Mounts each discovered content pack ({@link MchContentPacks#mounts()}) as a fixed, always-enabled client resource
 * pack, so the vanilla resource manager serves the pack's TEXTURES + {@code .ogg}s + (synthesized) {@code sounds.json}
 * — the second asset channel that {@link mcheli.agnostic.spi.ResourceSource} (configs + models) does NOT cover. The
 * serving is done by {@link MchPackResources}, which maps the {@code mcheli} namespace straight to each pack's content
 * root, so BOTH the flat and {@code assets/mcheli/} pack layouts work with no on-disk {@code pack.mcmeta}/{@code
 * sounds.json} generation.
 *
 * <p>Common class (only {@code net.minecraft.server.packs.*} — no {@code net.minecraft.client.*}); it does nothing for
 * {@code SERVER_DATA}/on a dedicated server (textures + audio are client-only). Registered on the mod bus in the MCHeli
 * constructor.
 */
public final class MchPackResourcesFinder {
    private MchPackResourcesFinder() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return; // pack textures/audio are client resources; nothing to add server-side
        }
        for (PackMount mount : MchContentPacks.mounts()) {
            String id = "mcheli/" + mount.name();
            PackLocationInfo location = new PackLocationInfo(
                id, Component.literal("MCHeli pack: " + mount.name()), PackSource.BUILT_IN, Optional.empty());
            Pack.ResourcesSupplier resources = new Pack.ResourcesSupplier() {
                @Override
                public PackResources openPrimary(PackLocationInfo loc) {
                    return new MchPackResources(loc, mount.contentRoot(), mount.name(), mount.soundBasenames());
                }
                @Override
                public PackResources openFull(PackLocationInfo loc, Pack.Metadata meta) {
                    return new MchPackResources(loc, mount.contentRoot(), mount.name(), mount.soundBasenames());
                }
            };
            // required + fixed-TOP so users don't have to enable it and it overlays the base mod's assets (a later pack
            // overrides an earlier one — the standard resource-pack stacking).
            PackSelectionConfig selection = new PackSelectionConfig(true, Pack.Position.TOP, true);
            Pack pack = Pack.readMetaAndCreate(location, resources, PackType.CLIENT_RESOURCES, selection);
            if (pack == null) {
                LOGGER.warn("MCHeli: content pack '{}' produced no resource pack (metadata error?)", mount.name());
                continue;
            }
            event.addRepositorySource(consumer -> consumer.accept(pack));
            LOGGER.info("MCHeli: mounted content-pack resources for '{}'", mount.name());
        }
    }
}
