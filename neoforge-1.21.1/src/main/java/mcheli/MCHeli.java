package mcheli;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

// The modid here must match [[mods]] modId in META-INF/neoforge.mods.toml.
@Mod(MCHeli.MODID)
public class MCHeli {
    public static final String MODID = "mcheli";
    private static final Logger LOGGER = LogUtils.getLogger();

    // NeoForge 1.21.1 uses constructor injection: FML supplies the mod event bus and the
    // ModContainer by parameter type (there is no FMLJavaModLoadingContext as in 1.7.10).
    public MCHeli(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("MCHeli (NeoForge 1.21.1) constructed");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("MCHeli common setup");
    }
}
