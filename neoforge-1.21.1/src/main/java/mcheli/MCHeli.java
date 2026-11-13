package mcheli;

import com.mojang.logging.LogUtils;
import mcheli.agnostic.helicopter.MCH_HeliInfoManager;
import mcheli.agnostic.model.MchModel;
import mcheli.agnostic.plane.MCP_PlaneInfoManager;
import mcheli.agnostic.spi.ModelHandle;
import mcheli.agnostic.tank.MCH_TankInfo;
import mcheli.agnostic.tank.MCH_TankInfoManager;
import mcheli.agnostic.value.Vec3d;
import mcheli.agnostic.vehicle.MCH_VehicleInfoManager;
import mcheli.agnostic.weapon.MCH_WeaponInfo;
import mcheli.agnostic.weapon.MCH_WeaponInfoManager;
import mcheli.dependent.config.MchConfig;
import mcheli.dependent.control.MchControlNetwork;
import mcheli.dependent.port.NeoItemHandle;
import mcheli.dependent.port.NeoLogger;
import mcheli.dependent.registry.MchRegistries;
import mcheli.dependent.registry.MchSounds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
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
        // The port of mcheli.cfg (reference MCH_MOD.PreInit:136 loadConfig): common + server + client TOML specs. The
        // common/client files load before common setup; the per-world server file loads with each world.
        MchConfig.register(modContainer, modEventBus);
        // Discover content packs FIRST (under <gamedir>/mcheli/) so pack config filenames + sound events are known
        // before the registries freeze — the shared bundled+pack resource set drives everything below.
        mcheli.dependent.port.MchContentPacks.discover();
        // Scan the bundled+pack configs -> one spawn item per vehicle (must run BEFORE register() so the DeferredRegister
        // entries exist when the RegisterEvent fires), then register the 4 category EntityTypes + items + tabs.
        MchRegistries.registerVehicles();
        MchRegistries.register(modEventBus);
        // Register a SoundEvent per content-pack sound event (its .ogg files grouped by the reference MCH_SoundsJson
        // rules, before the sound registry attaches to the bus + freezes), then register the bundled sounds.
        MchSounds.registerPackSounds(mcheli.dependent.port.MchContentPacks.packSoundEvents());
        MchSounds.register(modEventBus);
        // Expose content-pack textures + (generated) sounds.json, .ogg files and spawn-item models to the vanilla
        // client resource manager.
        modEventBus.addListener(mcheli.dependent.port.MchPackResourcesFinder::onAddPackFinders);
        // Register every MCHeli network payload, serverbound and clientbound (MchControlNetwork lists them).
        modEventBus.addListener(MchControlNetwork::register);
        // Dev-only: the in-game self-tests (mcheli.dependent.MchSelfTests lists them all). Gated on !production so a
        // shipped jar never spawns test entities, and on the mcheli.selftests system property so an ordinary dev launch
        // never runs them either. Run them from Gradle, which sets that property from -PmcheliSelfTests:
        //     ./gradlew :neoforge-1.21.1:runServer -PmcheliSelfTests            (every test)
        //     ./gradlew :neoforge-1.21.1:runServer -PmcheliSelfTests=crush,bomb (a comma list of names)
        // The run uses its own game directory (run/selftest) and a fresh flat world, runs the tests one after another
        // in separate areas, logs one "RESULT: PASS|FAIL" line per test plus a SUMMARY, and stops the server.
        if (!FMLEnvironment.production) {
            mcheli.dependent.MchSelfTests.registerIfRequested();
        }
        LOGGER.info("MCHeli (NeoForge 1.21.1) constructed");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("MCHeli common setup");
        // Prove the agnostic layer (pure Java, no Minecraft) is wired in and executing inside the mod.
        Vec3d forward = new Vec3d(0.0, 0.0, 1.0).rotateAroundY(Math.toRadians(90.0));
        LOGGER.info("MCHeli agnostic check: (0,0,1) rotated +90deg yaw -> {}", forward);

        // Load the vehicle definitions from the shared bundled+content-pack resource set (the agnostic DSL parser
        // through the composite ResourceSource). This is the same resource set registerVehicles() used at
        // construction, so the parsed configs line up with the spawn items.
        mcheli.agnostic.spi.ResourceSource res = mcheli.dependent.port.MchContentPacks.resources();
        NeoLogger log = new NeoLogger(LOGGER);
        // The All*Speed multipliers are baked into each definition while it parses, so install them first.
        MchConfig.installAgnosticSettings();
        // Reference order (MCH_MOD.PreInit:137-142): HUDs, then weapons, then the vehicle kinds. Weapons MUST precede
        // vehicles: a vehicle's AddWeapon line naming an undefined weapon is dropped at parse time.
        mcheli.agnostic.hud.MCH_HudManager.getInstance().load(res, log);
        MCH_WeaponInfoManager.getInstance().load(res, log, "weapons");
        // The item registry is frozen by now, so resolve every weapon's DispenseItem/Item names once (reference
        // MCH_WeaponInfoManager.setRoundItems, run from postInit).
        MCH_WeaponInfoManager.getInstance().resolveItems(NeoItemHandle::resolve, log);
        MCH_HeliInfoManager.getInstance().load(res, log, "helicopters");
        MCP_PlaneInfoManager.getInstance().load(res, log, "planes");
        MCH_TankInfoManager.getInstance().load(res, log, "tanks");
        MCH_VehicleInfoManager.getInstance().load(res, log, "vehicles");
        MCH_WeaponInfo m230 = MCH_WeaponInfoManager.get("m230");
        if (m230 != null) {
            LOGGER.info("MCHeli weapon check: m230 -> type={} power={} reloadTime={}",
                m230.type, m230.power, m230.reloadTime);
        }
        MCH_TankInfo m1a2 = MCH_TankInfoManager.get("m1a2");
        if (m1a2 != null) {
            LOGGER.info("MCHeli config check: m1a2 -> speed={} gravity={} mobilityYawOnGround={} maxHp={}",
                m1a2.speed, m1a2.gravity, m1a2.mobilityYawOnGround, m1a2.maxHp);
        }

        // Dev-only pipeline probe: resolve + parse three representative vehicles' models through the agnostic parsers
        // (models/<category>/<name>.mqo|obj, the reference's MCH_ModelManager convention) and log the geometry.
        // Models are a CLIENT concern (the renderer loads them through MchContentPacks.resources(), the bundled +
        // content-pack CompositeResourceSource, and caches them per config itself, like MCH_ClientProxy did), so
        // this stays gated off production — a dedicated server never needs to parse 19MB of models.
        if (!FMLEnvironment.production) {
            logModel(res, "helicopters/ah-64");
            logModel(res, "planes/a-10");
            logModel(res, "tanks/m1a2");
        }
    }

    private void logModel(mcheli.agnostic.spi.ResourceSource res, String name) {
        ModelHandle h = res.loadModel(name);
        if (h instanceof MchModel m) {
            LOGGER.info("MCHeli model check: {} -> {} groups, {} faces; sizeXYZ=[{}, {}, {}], X=[{}..{}] Y=[{}..{}] Z=[{}..{}]",
                name, m.groups().size(), m.getFaceNum(), m.sizeX, m.sizeY, m.sizeZ,
                m.minX, m.maxX, m.minY, m.maxY, m.minZ, m.maxZ);
        } else {
            LOGGER.warn("MCHeli model check: {} did NOT load", name);
        }
    }
}
