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
import mcheli.dependent.DemoBulletSelfTest;
import mcheli.dependent.DemoForwardVehicleSelfTest;
import mcheli.dependent.DemoHeliSelfTest;
import mcheli.dependent.DemoTankSelfTest;
import mcheli.dependent.control.MchControlNetwork;
import mcheli.dependent.port.NeoLogger;
import mcheli.dependent.port.NeoResourceSource;
import mcheli.dependent.registry.MchRegistries;
import mcheli.dependent.registry.MchSounds;
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
        // Scan the bundled configs -> one spawn item per vehicle (must run BEFORE register() so the DeferredRegister
        // entries exist when the RegisterEvent fires), then register the 4 category EntityTypes + items + tabs.
        MchRegistries.registerVehicles();
        MchRegistries.register(modEventBus);
        MchSounds.register(modEventBus);
        // Register the serverbound player-control payload (client keys -> server ControlInput).
        modEventBus.addListener(MchControlNetwork::register);
        // Dev-only game-bus listener: the headless self-test that proves agnostic-driven movement at
        // runtime. Gated on !production so a shipped jar never spawns test entities or spams the log — it
        // runs only under runClient/runServer (FMLEnvironment.production is false in the dev workspace).
        if (!FMLEnvironment.production) {
            // Flight-physics proof: the heli generates collective lift and climbs (vs the vehicle, which falls).
            //NeoForge.EVENT_BUS.register(new DemoHeliSelfTest());
            //// Forward-thrust proofs: the plane and tank fly/drive forward under rider-gated thrust and stay aloft
            //// (vs a pilotless copy that free-falls). Distinct X columns, both at y+90 so the pilotless fall clears.
            //NeoForge.EVENT_BUS.register(new DemoForwardVehicleSelfTest("PLANE", MchRegistries.PLANE, "a-10", 6.0, 11.0, 90.0, 5.0, 20.0));
            //// The tank is a GROUND vehicle (heavier gravity) -> its own drive-forward-without-flying test.
            //NeoForge.EVENT_BUS.register(new DemoTankSelfTest());
            //// Projectile proof: a fired bullet flies downrange, hits a target and damages it, then despawns.
            //NeoForge.EVENT_BUS.register(new DemoBulletSelfTest());
            //// Config-driven weapon proof: the AH-64's real selectable weapons build from its config, carry their
            //// real stats/ballistics, switch, and fire (direct + from the vehicle's own mounts).
            //NeoForge.EVENT_BUS.register(new mcheli.dependent.DemoWeaponSelfTest());
            //// Config-driven VISUALS proof: the muzzle-flash colour, cartridge model/scale + trail all come from config.
            //NeoForge.EVENT_BUS.register(new mcheli.dependent.DemoParticleSelfTest());
            //// Config-driven HUD proof: the eval engine, the hud/* configs, and the draw pipeline.
            //NeoForge.EVENT_BUS.register(new mcheli.dependent.DemoHudSelfTest());
            //// Barrel heat/overheat proof: the Phalanx gun's heat fills under sustained fire, locks out at the cap,
            //// then cools + re-enables (the HUD wpn_heat gauge).
            //NeoForge.EVENT_BUS.register(new mcheli.dependent.DemoHeatSelfTest());
            //// Vehicle HP/armor/destruction proof: damage accumulation + int truncation, the per-part armor formula
            //// (m1a2 zones), the lava/onFire/inWall gates, and the faithful eject-explode-fall-despawn wreck.
            //NeoForge.EVENT_BUS.register(new mcheli.dependent.DemoHpSelfTest());
            //// Multi-seat + gunner weapons + ammo/fuel economy proof (#36/#37).
            //NeoForge.EVENT_BUS.register(new mcheli.dependent.DemoCrewFuelSelfTest());
        }
        LOGGER.info("MCHeli (NeoForge 1.21.1) constructed");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("MCHeli common setup");
        // Prove the agnostic layer (pure Java, no Minecraft) is wired in and executing inside the mod.
        Vec3d forward = new Vec3d(0.0, 0.0, 1.0).rotateAroundY(Math.toRadians(90.0));
        LOGGER.info("MCHeli agnostic check: (0,0,1) rotated +90deg yaw -> {}", forward);

        // Load REAL vehicle definitions from the bundled resources (the agnostic DSL parser through the classpath-
        // backed NeoResourceSource), replacing the demo entities' hard-coded buildInfo().
        NeoResourceSource res = new NeoResourceSource();
        NeoLogger log = new NeoLogger(LOGGER);
        MCH_HeliInfoManager.getInstance().load(res, log, "helicopters");
        MCP_PlaneInfoManager.getInstance().load(res, log, "planes");
        MCH_TankInfoManager.getInstance().load(res, log, "tanks");
        MCH_VehicleInfoManager.getInstance().load(res, log, "vehicles");
        MCH_WeaponInfoManager.getInstance().load(res, log, "weapons");
        mcheli.agnostic.hud.MCH_HudManager.getInstance().load(res, log);
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

        // Dev-only pipeline probe: resolve + parse the demo vehicles' REAL models through the agnostic parsers
        // (models/<category>/<name>.mqo|obj, the reference's MCH_ModelManager convention) and log the geometry.
        // Models are a CLIENT concern (the renderer loads them via NeoResourceSource, like MCH_ClientProxy did), so
        // this stays gated off production — a dedicated server never needs to parse 19MB of models.
        if (!FMLEnvironment.production) {
            logModel(res, "helicopters/ah-64");
            logModel(res, "planes/a-10");
            logModel(res, "tanks/m1a2");
        }
    }

    private void logModel(NeoResourceSource res, String name) {
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
