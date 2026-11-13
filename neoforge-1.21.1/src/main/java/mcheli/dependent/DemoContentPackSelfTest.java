package mcheli.dependent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mcheli.MCHeli;
import mcheli.agnostic.helicopter.MCH_HeliInfoManager;
import mcheli.agnostic.spi.ModelHandle;
import mcheli.dependent.entity.MchHelicopter;
import mcheli.dependent.item.VehicleSpawnItem;
import mcheli.dependent.port.MchContentPacks;
import mcheli.dependent.port.MchPackResources;
import mcheli.dependent.registry.MchRegistries;
import mcheli.dependent.registry.MchSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * Headless proof of the <b>content-pack loader</b> (#8). The {@code prepareSelfTestRun} Gradle task (see
 * {@code neoforge-1.21.1/selftest.gradle}) writes the synthetic packs into the self-test game directory before the
 * server boots (discovery is construction-time), whenever this test is selected:
 * <ul>
 *   <li>{@code testpack_rp} in the resource-pack layout ({@code <pack>/assets/mcheli/...}) and {@code testpack_flat} in
 *       the FLAT layout ({@code <pack>/helicopters/...}). Both must load identically — a pack does NOT need the
 *       {@code assets/mcheli/} nesting, yet a pack that HAS it still works;</li>
 *   <li>{@code notapack/}, a folder with no content directory, which must be skipped;</li>
 *   <li>adversarial configs that must be skip+warned, not crash: {@code bad heli.txt} (illegal id), {@code fuel.txt}
 *       (a reserved item id) and {@code planes/mch_flatpack_heli.txt} (a cross-category duplicate);</li>
 *   <li>sounds: {@code mch_grouped1/2.ogg} (one event {@code mch_grouped}, the reference trailing-digit grouping),
 *       {@code MixedCase_Snd.OGG} (lower-cased to {@code mixedcase_snd}), {@code Bad Sound.ogg} (illegal, skipped
 *       instead of crashing the {@code @Mod} constructor), {@code hit.ogg} (a bundled name: bundled wins), and the
 *       later pack's {@code mch_grouped3.ogg} (an event the earlier pack owns: ignored).</li>
 * </ul>
 * Asserts, for each layout: discovery → spawn item minted (survived the freeze) → config parsed → spawns via the
 * shared category EntityType → model resolves through the composite {@code ResourceSource} → the pack's {@code .ogg}
 * registered a SoundEvent; and, through {@link MchPackResources} itself, what a client would be served: the generated
 * {@code sounds.json}, the generated (or the pack's own) spawn-item model, and a case-insensitive {@code .ogg}. (Live
 * textures + audio need a client.)
 */
public final class DemoContentPackSelfTest extends MchSelfTest {

    private static final String RP_VEHICLE = "mch_testpack_heli";   // pack in the assets/mcheli/ layout
    private static final String RP_SOUND = "mch_testpack_snd";
    private static final String FLAT_VEHICLE = "mch_flatpack_heli"; // pack in the FLAT layout
    private static final String FLAT_SOUND = "mch_flatpack_snd";

    @Override
    protected int[] areaChunks() {
        return new int[] {0, 0, 0, 0};
    }

    @Override
    protected void start() {
        List<String> names = new ArrayList<>();
        MchContentPacks.mounts().forEach(m -> names.add(m.name()));
        boolean packsDiscovered = names.equals(List.of("testpack_flat", "testpack_rp")); // notapack skipped, sorted

        boolean rpLoaded = vehicleFullyLoaded(RP_VEHICLE) && MchSounds.byName(RP_SOUND) != null
            && MchContentPacks.packSoundEvents().contains(RP_SOUND);
        boolean flatLoaded = vehicleFullyLoaded(FLAT_VEHICLE) && MchSounds.byName(FLAT_SOUND) != null
            && MchContentPacks.packSoundEvents().contains(FLAT_SOUND);

        // The flat-layout vehicle actually SPAWNS via the shared HELI EntityType carrying its config name.
        boolean flatSpawns = spawns(FLAT_VEHICLE);

        // HARDENING: reaching here at all proves construction skip+warned the adversarial files instead of crashing.
        boolean fuelReservedIntact = MchRegistries.FUEL.get() != null && MchRegistries.spawnItemFor("fuel") == null;
        boolean illegalNameSkipped = MchRegistries.spawnItemFor("bad heli") == null;
        boolean crossCategoryDupSkipped = MchRegistries.spawnItemFor(FLAT_VEHICLE) instanceof VehicleSpawnItem v
            && v.category == MchRegistries.Category.HELICOPTER; // helicopters scan first; the planes copy is skipped

        // Sound events follow the reference MCH_SoundsJson rules, and bundled / earlier-pack events win.
        List<String> events = MchContentPacks.packSoundEvents();
        boolean grouped = MchSounds.byName("mch_grouped") != null && MchSounds.byName("mch_grouped1") == null
            && !events.contains("mch_grouped1");
        boolean lowerCased = MchSounds.byName("mixedcase_snd") != null;
        boolean illegalSoundSkipped = events.stream().noneMatch(e -> e.contains(" "));
        boolean bundledWins = !events.contains("hit");

        // What a client is served (the same PackResources the client resource manager mounts).
        boolean served = false;
        String servedDetail = "";
        try (MchPackResources res = new MchPackResources(new PackLocationInfo("mcheli/content_packs",
                Component.literal("self-test"), PackSource.BUILT_IN, Optional.empty()), MchContentPacks.mounts())) {
            String soundsJson = read(res.getResource(PackType.CLIENT_RESOURCES, rl("sounds.json")));
            boolean jsonGrouped = soundsJson.contains("\"mch_grouped\"") && soundsJson.contains("mcheli:mch_grouped1")
                && soundsJson.contains("mcheli:mch_grouped2");
            boolean jsonNoGraft = !soundsJson.contains("mch_grouped3") && !soundsJson.contains("\"hit\"");
            String flatModel = read(res.getResource(PackType.CLIENT_RESOURCES, rl("models/item/" + FLAT_VEHICLE + ".json")));
            String rpModel = read(res.getResource(PackType.CLIENT_RESOURCES, rl("models/item/" + RP_VEHICLE + ".json")));
            boolean itemModels = flatModel.contains("builtin/entity") && rpModel.contains("mch_shipped");
            List<ResourceLocation> listed = new ArrayList<>();
            res.listResources(PackType.CLIENT_RESOURCES, MCHeli.MODID, "models", (id, s) -> listed.add(id));
            boolean itemModelsListed = listed.contains(rl("models/item/" + FLAT_VEHICLE + ".json"))
                && listed.contains(rl("models/item/" + RP_VEHICLE + ".json"));
            boolean oggAnyCase = res.getResource(PackType.CLIENT_RESOURCES, rl("sounds/mixedcase_snd.ogg")) != null;
            served = jsonGrouped && jsonNoGraft && itemModels && itemModelsListed && oggAnyCase;
            servedDetail = "jsonGrouped=" + jsonGrouped + " jsonNoGraft=" + jsonNoGraft + " itemModels=" + itemModels
                + " itemModelsListed=" + itemModelsListed + " oggAnyCase=" + oggAnyCase;
        } catch (Exception e) {
            servedDetail = "threw " + e;
        }

        boolean pass = packsDiscovered && rpLoaded && flatLoaded && flatSpawns && fuelReservedIntact
            && illegalNameSkipped && crossCategoryDupSkipped && grouped && lowerCased && illegalSoundSkipped
            && bundledWins && served;
        finish(pass, "packs={} rpLoaded={} flatLoaded={} flatSpawns={} fuelIntact={} illegalSkipped={} dupSkipped={} "
                + "soundsGrouped={} lowerCased={} illegalSoundSkipped={} bundledWins={} served={} ({}) events={}",
            names, rpLoaded, flatLoaded, flatSpawns, fuelReservedIntact, illegalNameSkipped, crossCategoryDupSkipped,
            grouped, lowerCased, illegalSoundSkipped, bundledWins, served, servedDetail, events);
    }

    /** A pack vehicle is fully loaded when its spawn item was minted, its config parsed, and its model resolves. */
    private boolean vehicleFullyLoaded(String name) {
        boolean itemMinted = MchRegistries.spawnItemFor(name) != null;
        boolean configParsed = MCH_HeliInfoManager.get(name) != null;
        ModelHandle model = MchContentPacks.resources().loadModel("helicopters/" + name);
        return itemMinted && configParsed && model != null;
    }

    private boolean spawns(String name) {
        MchHelicopter heli = MchRegistries.HELI.get().create(this.level);
        if (heli == null) {
            return false;
        }
        heli.setConfigName(name);
        heli.setPos(this.origin.getX() + 0.5, this.origin.getY() + 20.0, this.origin.getZ() + 0.5);
        heli.setNoGravity(true);
        this.level.addFreshEntity(heli);
        return heli.isAlive() && name.equals(heli.configName());
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, path);
    }

    private static String read(IoSupplier<InputStream> supplier) throws IOException {
        if (supplier == null) {
            return "";
        }
        try (InputStream in = supplier.get()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
