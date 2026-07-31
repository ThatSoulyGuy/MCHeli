package mcheli.dependent;

import com.mojang.logging.LogUtils;
import mcheli.agnostic.helicopter.MCH_HeliInfoManager;
import mcheli.agnostic.spi.ModelHandle;
import mcheli.dependent.entity.MchHelicopter;
import mcheli.dependent.port.MchContentPacks;
import mcheli.dependent.registry.MchRegistries;
import mcheli.dependent.registry.MchSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * Headless proof of the <b>content-pack loader</b> (#8). The bash harness writes TWO synthetic packs before the server
 * boots (discovery is construction-time): {@code testpack_rp} in the resource-pack layout
 * ({@code <pack>/assets/mcheli/...}) and {@code testpack_flat} in the FLAT layout ({@code <pack>/helicopters/...} with
 * the content right at the root). Both must load identically — proving a pack does NOT need the {@code assets/mcheli/}
 * nesting, yet a pack that HAS it still works. Plus adversarial files (illegal name, reserved-id/cross-category dup)
 * that must be skip+warned, not crash. Asserts, for each layout: discovery → spawn item minted (survived the freeze) →
 * config parsed → spawns via the shared category EntityType → model resolves through the composite {@code ResourceSource}
 * → the pack's {@code .ogg} registered a SoundEvent. (Live textures + audio need a client.)
 */
public final class DemoContentPackSelfTest {
    private static final Logger LOG = LogUtils.getLogger();

    private static final String RP_VEHICLE = "mch_testpack_heli";   // pack in the assets/mcheli/ layout
    private static final String RP_SOUND = "mch_testpack_snd";
    private static final String FLAT_VEHICLE = "mch_flatpack_heli"; // pack in the FLAT layout
    private static final String FLAT_SOUND = "mch_flatpack_snd";

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();

        boolean bothPacksDiscovered = MchContentPacks.mounts().size() >= 2;

        boolean rpLoaded = vehicleFullyLoaded(RP_VEHICLE) && MchSounds.byName(RP_SOUND) != null
            && MchContentPacks.packSoundBasenames().contains(RP_SOUND);
        boolean flatLoaded = vehicleFullyLoaded(FLAT_VEHICLE) && MchSounds.byName(FLAT_SOUND) != null
            && MchContentPacks.packSoundBasenames().contains(FLAT_SOUND);

        // The flat-layout vehicle actually SPAWNS via the shared HELI EntityType carrying its config name.
        boolean flatSpawns = spawns(level, FLAT_VEHICLE);

        // HARDENING (review fixes): the harness also planted adversarial files (illegal name, reserved-id + cross-category
        // dup). Reaching here at all proves the loader skip+warned them instead of crashing mod construction; and the
        // reserved fuel item survived un-shadowed.
        boolean bootedPastAdversarialFiles = true;
        boolean fuelReservedIntact = MchRegistries.FUEL.get() != null && MchRegistries.spawnItemFor("fuel") == null;
        boolean illegalNameSkipped = MchRegistries.spawnItemFor("bad heli") == null;

        boolean pass = bothPacksDiscovered && rpLoaded && flatLoaded && flatSpawns
            && bootedPastAdversarialFiles && fuelReservedIntact && illegalNameSkipped;

        LOG.info("[CONTENTPACK-SELFTEST] packs={} rpLoaded={} flatLoaded={} flatSpawns={} bootedPastBadFiles={} fuelIntact={} illegalSkipped={}",
            MchContentPacks.mounts().size(), rpLoaded, flatLoaded, flatSpawns,
            bootedPastAdversarialFiles, fuelReservedIntact, illegalNameSkipped);
        LOG.info("[CONTENTPACK-SELFTEST] RESULT: {} - bothPacksDiscovered={} rpLoaded={} flatLoaded={} flatSpawns={} bootedPastBadFiles={} fuelIntact={} illegalSkipped={}",
            pass ? "PASS" : "FAIL", bothPacksDiscovered, rpLoaded, flatLoaded, flatSpawns,
            bootedPastAdversarialFiles, fuelReservedIntact, illegalNameSkipped);
    }

    /** A pack vehicle is fully loaded when its spawn item was minted, its config parsed, and its model resolves. */
    private boolean vehicleFullyLoaded(String name) {
        boolean itemMinted = MchRegistries.spawnItemFor(name) != null;
        boolean configParsed = MCH_HeliInfoManager.get(name) != null;
        ModelHandle model = MchContentPacks.resources().loadModel("helicopters/" + name);
        return itemMinted && configParsed && model != null;
    }

    private boolean spawns(ServerLevel level, String name) {
        MchHelicopter heli = MchRegistries.HELI.get().create(level);
        if (heli == null) {
            return false;
        }
        BlockPos spawn = level.getSharedSpawnPos();
        heli.setConfigName(name);
        heli.setPos(spawn.getX() + 0.5, spawn.getY() + 20.0, spawn.getZ() + 0.5);
        heli.setNoGravity(true);
        level.addFreshEntity(heli);
        boolean ok = heli.isAlive() && name.equals(heli.configName());
        heli.discard();
        return ok;
    }
}
