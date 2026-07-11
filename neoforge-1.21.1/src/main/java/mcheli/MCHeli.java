package mcheli;

import com.mojang.logging.LogUtils;
import mcheli.agnostic.value.Vec3d;
import mcheli.dependent.DemoForwardVehicleSelfTest;
import mcheli.dependent.DemoHeliSelfTest;
import mcheli.dependent.DemoVehicleSelfTest;
import mcheli.dependent.control.MchControlNetwork;
import mcheli.dependent.registry.MchRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
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
        // Register the demo vehicle's EntityType + spawn item (and creative-tab entry) on the mod bus.
        MchRegistries.register(modEventBus);
        // Register the serverbound player-control payload (client keys -> server ControlInput).
        modEventBus.addListener(MchControlNetwork::register);
        // Dev-only game-bus listener: the headless self-test that proves agnostic-driven movement at
        // runtime. Gated on !production so a shipped jar never spawns test entities or spams the log — it
        // runs only under runClient/runServer (FMLEnvironment.production is false in the dev workspace).
        if (!FMLEnvironment.production) {
            NeoForge.EVENT_BUS.register(new DemoVehicleSelfTest());
            // Flight-physics proof: the heli generates collective lift and climbs (vs the vehicle, which falls).
            NeoForge.EVENT_BUS.register(new DemoHeliSelfTest());
            // Forward-thrust proofs: the plane and tank fly/drive forward under rider-gated thrust and stay aloft
            // (vs a pilotless copy that free-falls). Distinct X columns, both at y+90 so the pilotless fall clears.
            NeoForge.EVENT_BUS.register(new DemoForwardVehicleSelfTest("PLANE", MchRegistries.DEMO_PLANE, 6.0, 11.0, 90.0, 5.0, 20.0));
            NeoForge.EVENT_BUS.register(new DemoForwardVehicleSelfTest("TANK", MchRegistries.DEMO_TANK, -6.0, -11.0, 90.0, 4.0, 15.0));
        }
        LOGGER.info("MCHeli (NeoForge 1.21.1) constructed");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("MCHeli common setup");
        // Prove the agnostic layer (pure Java, no Minecraft) is wired in and executing inside the mod.
        Vec3d forward = new Vec3d(0.0, 0.0, 1.0).rotateAroundY(Math.toRadians(90.0));
        LOGGER.info("MCHeli agnostic check: (0,0,1) rotated +90deg yaw -> {}", forward);
    }
}
