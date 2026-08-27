package mcheli.dependent;

import com.mojang.logging.LogUtils;
import mcheli.dependent.entity.MchTank;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Headless proof of the two terrain-interaction behaviours the port was missing.
 *
 * <p><b>Probe A — terrain tilt with NO rider.</b> The reference ran {@code WheelMng.move()} server-side every tick
 * regardless of rider ({@code MCH_EntityTank}:718); the port only reached the solver from the local pilot's client
 * render frame, so an abandoned tank floated level on a hillside. This spawns an m1a2 on a staircase with no pilot
 * and no control input and asserts the hull pitches onto the grade.
 *
 * <p><b>Probe B — block crushing.</b> Parks a piloted m1a2 among four probes and asserts every branch of
 * {@link mcheli.dependent.entity.MchCrushableBlocks}: oak planks (material {@code wood}) are destroyed, stone is
 * inert, a torch is spared ({@code noBreak}), and the block ABOVE the torch survives too. That last one is the
 * column-abandon branch, and the direction matters: {@code destroyBlockRange} scans each column bottom-up, so a
 * no-break block shields what is above it, not below. Differential by construction — a pass that deleted everything
 * in range would fail the stone/torch probes, and one that did nothing would fail the planks.
 *
 * <p><b>Probe C — vehicle crafting recipes.</b> Asserts the generated recipe datapack actually reached the recipe
 * manager as real {@code CRAFTING} recipes producing {@link mcheli.dependent.item.VehicleSpawnItem}s.
 *
 * <p><b>Probe D — a gunner aims his own weapon.</b> An m1a2 with NO pilot, a gunner in seat 1 (the {@code m2_rws},
 * an unlimited-traverse {@code AddTurretWeapon}), hull at yaw 0 and gunner at yaw -90. Every {@code turret} mount
 * used to fire along {@code turretOrbitYaw()}, which is sourced from {@code pilot()} — so a gunner's rounds left
 * along the driver's aim, or hull-forward with no driver. The mean round heading must equal the GUNNER's yaw, and
 * the hull/gunner split is what makes it differential: a regression to the old behaviour reads ~0 instead of -90.
 */
public final class DemoCrushSelfTest {
    private static final Logger LOG = LogUtils.getLogger();

    private static final int CHECK_AFTER_TICKS = 60;
    private static final float TILT_MIN = 1.0F; // degrees; m1a2 OnGroundPitchFactor = 2.0 per tick

    private MchTank tiltTank;
    private int ticks = -1;

    private MchTank aimTank;
    private Entity aimGunner;

    private MchTank crushTank;
    private Entity crushPilot;
    private BlockPos planksProbe;
    private BlockPos stoneProbe;
    private BlockPos torchProbe;
    private BlockPos shieldedProbe;

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        var stone = Blocks.STONE.defaultBlockState();
        // A headless server ticks entities only in chunks at the entity-ticking level, which off spawn means NONE.
        // Without forcing, a probe entity sits at tickCount=0 forever and every assertion reads its spawn state.
        forceArea(level, spawn.getX() + 24, spawn.getZ() + 24);
        forceArea(level, spawn.getX() - 24, spawn.getZ() - 24);
        var planks = Blocks.OAK_PLANKS.defaultBlockState();
        var air = Blocks.AIR.defaultBlockState();

        // ---- Probe A: a staircase rising 1 block per 2 blocks of +Z, and an UNRIDDEN tank parked across it ----
        int ax = spawn.getX() + 24;
        int az = spawn.getZ() + 24;
        int baseY = spawn.getY() - 1;
        for (int bx = ax - 6; bx <= ax + 6; bx++) {
            for (int bz = az - 8; bz <= az + 8; bz++) {
                int top = baseY + Math.floorDiv(bz - az, 2);
                for (int by = baseY - 4; by <= top; by++) {
                    level.setBlockAndUpdate(new BlockPos(bx, by, bz), stone);
                }
                for (int by = top + 1; by <= top + 8; by++) {
                    level.setBlockAndUpdate(new BlockPos(bx, by, bz), air);
                }
            }
        }
        tiltTank = MchRegistries.TANK.get().create(level);
        if (tiltTank != null) {
            tiltTank.setConfigName("m1a2");
            tiltTank.setPos(ax + 0.5, baseY + 3.0, az + 0.5); // above the ramp; it settles onto it
            tiltTank.setYRot(0.0F);
            level.addFreshEntity(tiltTank);
            // Deliberately NO pilot and NO control input — that is the whole point of the probe.
            LOG.info("[CRUSH-SELFTEST] tilt probe: UNRIDDEN tank id={} on a 1-in-2 ramp at ({}, {})",
                tiltTank.getId(), ax, az);
        }

        // ---- Probe B: a flat floor with three walls in the tank's forward (+Z) path ----
        int cx = spawn.getX() - 24;
        int cz = spawn.getZ() - 24;
        int floorY = spawn.getY() - 1;
        for (int bx = cx - 6; bx <= cx + 6; bx++) {
            for (int bz = cz - 4; bz <= cz + 20; bz++) {
                level.setBlockAndUpdate(new BlockPos(bx, floorY, bz), stone);
                for (int by = floorY + 1; by <= floorY + 8; by++) {
                    level.setBlockAndUpdate(new BlockPos(bx, by, bz), air);
                }
            }
        }
        // Planks wall (must be destroyed) at z+4, stone wall (must survive) at z+6, both 2 high.
        for (int bx = cx - 3; bx <= cx + 3; bx++) {
            for (int by = floorY + 1; by <= floorY + 2; by++) {
                level.setBlockAndUpdate(new BlockPos(bx, by, cz + 4), planks);
                level.setBlockAndUpdate(new BlockPos(bx, by, cz + 6), stone);
            }
        }
        // A torch on the floor at z+2 (must survive), with planks directly UNDER the floor beneath it: the reference
        // no-break list breaks out of the whole Y column, so those planks must survive too even though wood is
        // otherwise crushable. That is the column-shielding branch.
        torchProbe = new BlockPos(cx, floorY + 1, cz + 2);
        shieldedProbe = new BlockPos(cx, floorY + 2, cz + 2);
        level.setBlockAndUpdate(shieldedProbe, planks);  // ABOVE the torch: the column scan runs bottom-up
        level.setBlockAndUpdate(torchProbe, Blocks.TORCH.defaultBlockState());
        planksProbe = new BlockPos(cx, floorY + 1, cz + 4);
        stoneProbe = new BlockPos(cx, floorY + 1, cz + 6);

        crushTank = MchRegistries.TANK.get().create(level);
        if (crushTank != null) {
            crushTank.setConfigName("m1a2"); // WeightType = Tank
            crushTank.setPos(cx + 0.5, floorY + 1, cz + 0.5);
            crushTank.setYRot(0.0F); // forward = +Z, into the walls
            level.addFreshEntity(crushTank);
            crushPilot = EntityType.PIG.create(level);
            if (crushPilot != null) {
                if (crushPilot instanceof Mob mob) {
                    mob.setNoAi(true);
                }
                crushPilot.setPos(cx + 0.5, floorY + 1, cz + 0.5);
                level.addFreshEntity(crushPilot);
                crushPilot.startRiding(crushTank, true);
            }
            crushTank.getControlState().throttleUp = true;
            LOG.info("[CRUSH-SELFTEST] crush probe: tank id={} planks@{} stone@{} torch@{} shielded@{}",
                crushTank.getId(), planksProbe, stoneProbe, torchProbe, shieldedProbe);
        }
        // ---- Probe D setup: an m1a2 with NO pilot and a gunner in seat 1 (the m2_rws), looking 90 deg off the hull ----
        int gx = spawn.getX() + 24;
        int gz = spawn.getZ() - 24;
        forceArea(level, gx, gz);
        int gFloor = spawn.getY() - 1;
        for (int bx = gx - 8; bx <= gx + 8; bx++) {
            for (int bz = gz - 8; bz <= gz + 8; bz++) {
                level.setBlockAndUpdate(new BlockPos(bx, gFloor, bz), stone);
                for (int by = gFloor + 1; by <= gFloor + 8; by++) {
                    level.setBlockAndUpdate(new BlockPos(bx, by, bz), air);
                }
            }
        }
        aimTank = MchRegistries.TANK.get().create(level);
        if (aimTank != null) {
            aimTank.setConfigName("m1a2");
            aimTank.setPos(gx + 0.5, gFloor + 1, gz + 0.5);
            aimTank.setYRot(0.0F); // hull faces +Z
            level.addFreshEntity(aimTank);
            aimGunner = EntityType.PIG.create(level);
            if (aimGunner != null) {
                if (aimGunner instanceof Mob mob) {
                    mob.setNoAi(true);
                }
                aimGunner.setPos(gx + 0.5, gFloor + 1, gz + 0.5);
                level.addFreshEntity(aimGunner);
                aimTank.preferSeat(aimGunner, 1); // seat 1 = the m2_rws gunner, NOT the pilot seat
                aimGunner.startRiding(aimTank, true);
                aimGunner.setYRot(-90.0F); // look +X, a quarter turn off the hull
                aimGunner.setXRot(0.0F);
                // Seat 1's eligible weapon (the m2_rws) is auto-selected by VehicleWeapons.initSelection; just hold
                // that seat's trigger. The rider is a pig, so weaponOperator() returns null and fireSelectedWeapon
                // falls back to the seat occupant — which is precisely the aim source under test.
                aimTank.controlState(1).fire = true;
            }
            LOG.info("[CRUSH-SELFTEST] gunner-aim probe: tank id={} hull yaw=0, gunner seat={} yaw={}",
                aimTank.getId(), aimGunner == null ? -1 : aimTank.seatIndexOf(aimGunner),
                aimGunner == null ? 0.0F : aimGunner.getYRot());
        }

        ticks = 0;
    }

    private static void LOGGER_AIM(int bullets, double heading, float hullYaw, float gunnerYaw, boolean ok) {
        LOG.info("[CRUSH-SELFTEST] gunner-aim probe: {} rounds, mean heading={} (hull={} gunner={}) -> {}",
            bullets, String.format("%.1f", heading), hullYaw, gunnerYaw, ok ? "OK" : "FAIL");
    }

    /** Force the 3x3 chunk block around a probe site so its entities actually tick on a playerless server. */
    private static void forceArea(ServerLevel level, int bx, int bz) {
        int cx = bx >> 4;
        int cz = bz >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setChunkForced(cx + dx, cz + dz, true);
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (ticks < 0) {
            return;
        }
        ticks++;
        // Keep the gunner's magazine topped up. m1a2's mounts spawn with an EMPTY reserve and the probe rider is a pig
        // (so it never gets the creative onMount top-up), which is the ammo economy doing its job — orthogonal to the
        // aim under test. Supplied every tick rather than once because the weapon list is built lazily on the first
        // server tick and the seat's selection is only assigned once the rider has boarded.
        if (ticks < 40 && aimTank != null) {
            aimTank.debugSupplySeatWeapon(1);
        }
        if (ticks % 15 == 0 && ticks < CHECK_AFTER_TICKS) {
            if (tiltTank != null) {
                LOG.info("[CRUSH-SELFTEST] t={} tilt: y={} z={} onGround={} pitch={} roll={} info={}",
                    ticks, tiltTank.getY(), tiltTank.getZ(), tiltTank.onGround(),
                    tiltTank.getXRot(), tiltTank.getRollAngle(), tiltTank.debugTerrainState());
            }
            if (crushTank != null) {
                LOG.info("[CRUSH-SELFTEST] t={} crush: y={} z={} onGround={} info={}",
                    ticks, crushTank.getY(), crushTank.getZ(), crushTank.onGround(), crushTank.debugTerrainState());
            }
            if (aimTank != null) {
                LOG.info("[CRUSH-SELFTEST] tick{} aim: {}", ticks, aimTank.debugSeatWeapon(1));
            }
        }
        if (ticks != CHECK_AFTER_TICKS) {
            return;
        }
        ServerLevel level = event.getServer().overworld();

        boolean pass = true;

        if (tiltTank == null) {
            LOG.error("[CRUSH-SELFTEST] FAIL: tilt tank did not spawn");
            pass = false;
        } else {
            float pitch = tiltTank.getXRot();
            boolean tilted = Math.abs(pitch) >= TILT_MIN;
            LOG.info("[CRUSH-SELFTEST] tilt probe: unridden pitch={} roll={} (need |pitch| >= {}) -> {}",
                pitch, tiltTank.getRollAngle(), TILT_MIN, tilted ? "OK" : "FAIL");
            pass &= tilted;
        }

        if (crushTank == null) {
            LOG.error("[CRUSH-SELFTEST] FAIL: crush tank did not spawn");
            pass = false;
        } else {
            boolean planksGone = level.getBlockState(planksProbe).isAir();
            boolean stoneKept = level.getBlockState(stoneProbe).is(Blocks.STONE);
            boolean torchKept = level.getBlockState(torchProbe).is(Blocks.TORCH);
            boolean shieldedKept = level.getBlockState(shieldedProbe).is(Blocks.OAK_PLANKS);
            LOG.info("[CRUSH-SELFTEST] crush probe: z={} planksDestroyed={} stoneIntact={} torchIntact={} columnShielded={}",
                crushTank.getZ(), planksGone, stoneKept, torchKept, shieldedKept);
            pass &= planksGone && stoneKept && torchKept && shieldedKept;
        }

        // ---- Probe D: a GUNNER in a secondary seat fires where HE looks, not along the hull ----
        // The bug: every `turret` mount fired along turretOrbitYaw(), which is sourced from pilot() — so a gunner's
        // shots left along the driver's aim, or hull-forward with no driver. m1a2's m2_rws sits on seat 1 with an
        // unlimited (-360..360) traverse, so the turret-ring term must cancel and the round must leave at the
        // gunner's own yaw. Differential: the hull faces +Z (yaw 0) and the gunner faces +X (yaw -90).
        boolean aimOk = false;
        if (aimTank != null && aimGunner != null) {
            double vx = 0.0;
            double vz = 0.0;
            int bullets = 0;
            for (var b : level.getEntitiesOfClass(mcheli.dependent.entity.MchBullet.class,
                    aimTank.getBoundingBox().inflate(48.0))) {
                vx += b.getDeltaMovement().x;
                vz += b.getDeltaMovement().z;
                bullets++;
            }
            double heading = bullets == 0 ? Double.NaN : Math.toDegrees(Math.atan2(-vx, vz));
            // Gunner yaw -90 => heading -90 (+X). Hull-forward would read ~0.
            aimOk = bullets > 0 && Math.abs(net.minecraft.util.Mth.wrapDegrees((float) heading + 90.0F)) < 20.0F;
            LOGGER_AIM(bullets, heading, aimTank.getYRot(), aimGunner.getYRot(), aimOk);
        } else {
            LOG.error("[CRUSH-SELFTEST] FAIL: gunner-aim probe did not spawn");
        }
        pass &= aimOk;

        // ---- Probe C: the generated vehicle recipes actually reached the recipe manager as real crafting recipes ----
        int vehicleRecipes = 0;
        String sample = "<none>";
        for (var holder : event.getServer().getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)) {
            var result = holder.value().getResultItem(event.getServer().registryAccess());
            if (result.getItem() instanceof mcheli.dependent.item.VehicleSpawnItem) {
                vehicleRecipes++;
                if ("<none>".equals(sample)) {
                    sample = holder.id() + " -> " + result.getItem();
                }
            }
        }
        boolean recipesOk = vehicleRecipes >= 100;
        LOG.info("[CRUSH-SELFTEST] recipe probe: {} vehicle crafting recipes loaded (need >= 100), e.g. {} -> {}",
            vehicleRecipes, sample, recipesOk ? "OK" : "FAIL");
        pass &= recipesOk;

        LOG.info("[CRUSH-SELFTEST] RESULT: {}", pass ? "PASS" : "FAIL");
        // Headless probe: stop the server once the verdict is in, so a CI/one-shot `runServer` terminates on its own
        // instead of idling until the caller's timeout (and leaving the world session locked for the next run).
        if (System.getProperty("mcheli.selftest.halt") != null) {
            event.getServer().halt(false);
        }
    }
}
