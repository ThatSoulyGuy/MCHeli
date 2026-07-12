package mcheli.dependent;

import com.mojang.logging.LogUtils;
import mcheli.MCHeli;
import mcheli.agnostic.weapon.MCH_WeaponBallistics;
import mcheli.agnostic.weapon.MCH_WeaponInfo;
import mcheli.agnostic.weapon.MCH_WeaponInfoManager;
import mcheli.dependent.entity.MchCartridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * Headless proof that the authentic weapon <b>visuals are config-driven, not hardcoded</b>. It cannot render, but it
 * verifies the exact config values that drive the particles/models flow through: the M230's specific orange muzzle
 * flash colour ({@code 254,159,84}), its cartridge model + scale ({@code 3.5}), the trail being TYPE-driven (rockets
 * trail, guns don't), and that the custom particle type + cartridge entity type actually registered — then spawns a
 * cartridge from config and confirms the synced model/scale it will render with.
 */
public final class DemoParticleSelfTest {
    private static final Logger LOG = LogUtils.getLogger();

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        MCH_WeaponInfo m230 = MCH_WeaponInfoManager.get("m230");
        MCH_WeaponInfo hydra = MCH_WeaponInfoManager.get("hydra70");
        if (m230 == null || hydra == null) {
            LOG.warn("[PARTICLE-SELFTEST] weapons not loaded — skipping");
            return;
        }

        // 1. Muzzle flash colour comes from the config (M230 = two orange AddMuzzleFlash entries), NOT a hardcoded flame.
        boolean flashOk = m230.listMuzzleFlash != null && m230.listMuzzleFlash.size() == 2
            && Math.round(m230.listMuzzleFlash.get(0).r * 255.0F) == 254
            && Math.round(m230.listMuzzleFlash.get(0).g * 255.0F) == 159
            && Math.round(m230.listMuzzleFlash.get(0).b * 255.0F) == 84
            && m230.listMuzzleFlashSmoke != null && m230.listMuzzleFlashSmoke.size() == 2;

        // 2. Cartridge model + scale from config (SetCartridge scale field = 3.5).
        boolean cartCfgOk = m230.cartridge != null && m230.cartridge.name.equals("cartridge")
            && Math.abs(m230.cartridge.scale - 3.5F) < 1.0e-4F;

        // 3. Trail is TYPE-driven (faithful): rocket trails, machine gun does not.
        boolean trailOk = MCH_WeaponBallistics.isTrailingType(hydra.type)
            && !MCH_WeaponBallistics.isTrailingType(m230.type);

        // 4. The custom particle type + cartridge entity type registered.
        boolean regOk = BuiltInRegistries.PARTICLE_TYPE.containsKey(rl("weapon_fx"))
            && BuiltInRegistries.ENTITY_TYPE.containsKey(rl("cartridge"));

        // 5. Spawn a cartridge from the config and confirm the synced name/scale it will render with.
        BlockPos spawn = level.getSharedSpawnPos();
        MchCartridge cart = MchCartridge.spawn(level, m230.cartridge,
            new Vec3(spawn.getX() + 0.5, spawn.getY() + 5.0, spawn.getZ() + 0.5), 0.0F, 0.0F, Vec3.ZERO);
        boolean cartSpawnOk = cart != null && cart.cartridgeName().equals("cartridge")
            && Math.abs(cart.cartridgeScale() - 3.5F) < 1.0e-4F;
        if (cart != null) {
            cart.discard();
        }

        boolean pass = flashOk && cartCfgOk && trailOk && regOk && cartSpawnOk;
        LOG.info("[PARTICLE-SELFTEST] flash(orange 254,159,84 from cfg)={} cartridge(model+scale3.5)={} trailTypeDriven={} registered={} cartSpawn={}",
            flashOk, cartCfgOk, trailOk, regOk, cartSpawnOk);
        LOG.info("[PARTICLE-SELFTEST] RESULT: {} - authentic weapon visuals are CONFIG-DRIVEN", pass ? "PASS" : "FAIL");
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, path);
    }
}
