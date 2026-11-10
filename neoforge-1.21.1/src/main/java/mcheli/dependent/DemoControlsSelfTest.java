package mcheli.dependent;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.UUID;
import mcheli.agnostic.weapon.WeaponSlot;
import mcheli.dependent.entity.MchBullet;
import mcheli.dependent.entity.MchHelicopter;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * Headless proof for the weapon features added on top of the port: the weapon fire-mode switch (incl. the m230's
 * non-explosive / [HE] rounds) and the round's block interaction (leaves shot through, HE without drops, glass panes
 * shattered).
 * Run with {@code ./gradlew :neoforge-1.21.1:runServer -PmcheliSelfTests} (property-gated, never in a normal launch).
 */
public final class DemoControlsSelfTest {
    private static final Logger LOG = LogUtils.getLogger();
    /** The world spawn (x, z): every test runs right above it, inside the always-loaded spawn chunks — a synchronous
     *  test cannot wait for far chunks to load, and blocks/collisions there would read as air. */
    private static int ox, oz;

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        ox = level.getSharedSpawnPos().getX();
        oz = level.getSharedSpawnPos().getZ();
        boolean pass = false;
        try {
            testWeaponModes(level);
            testBlockDamage(level);
            pass = true;
        } catch (Throwable t) {
            LOG.error("[CONTROLS-SELFTEST] FAIL: {}", t.getMessage(), t);
        }
        LOG.info("[CONTROLS-SELFTEST] RESULT: {}", pass ? "PASS" : "FAIL");
    }

    private static FakePlayer fake(ServerLevel level, String name) {
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
        p.getAbilities().instabuild = true; // creative: magazines top up on boarding (reference onMount)
        return p;
    }

    /**
     * Seat a fake player. NeoForge's {@code FakePlayer.startRiding} always refuses, so do what
     * {@code Entity.startRiding} does: link the rider to the vehicle and let the VEHICLE's own {@code addPassenger}
     * (our seat assignment) run.
     */
    private static boolean board(FakePlayer p, MchHelicopter h) {
        try {
            java.lang.reflect.Field vehicle = net.minecraft.world.entity.Entity.class.getDeclaredField("vehicle");
            vehicle.setAccessible(true);
            vehicle.set(p, h);
            java.lang.reflect.Method add = net.minecraft.world.entity.Entity.class
                .getDeclaredMethod("addPassenger", net.minecraft.world.entity.Entity.class);
            add.setAccessible(true);
            add.invoke(h, p);
            return h.hasPassenger(p) && h.seatIndexOf(p) >= 0;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot seat a fake player: " + e, e);
        }
    }

    private static MchHelicopter heli(ServerLevel level, String config, double y) {
        MchHelicopter h = MchRegistries.HELI.get().create(level);
        h.setConfigName(config);
        h.setPos(ox + 0.5, y, oz + 0.5);
        level.addFreshEntity(h);
        h.setFuel(h.getMaxFuel());
        return h;
    }

    // ---- W: weapon fire modes ----
    private void testWeaponModes(ServerLevel level) throws Exception {
        MchHelicopter h = heli(level, "ah-64", 300.0);
        h.tick(); // builds the loadout first, so the creative pilot's boarding tops every magazine (reference onMount)
        FakePlayer pilot = fake(level, "mch_pilot");
        req(board(pilot, h), "pilot boards the AH-64");
        req(h.seatIndexOf(pilot) == 0, "pilot sits in seat 0");
        h.tick();

        // Cycle the pilot's selection to the m230 (a MachineGun2 with ModeNum 2; the gunner seat is empty, so the pilot
        // may borrow it).
        int idx = -1;
        for (int i = 0; i < h.weaponCount() * 2 && idx < 0; i++) {
            int sel = h.getSelectedWeaponIndex();
            WeaponSlot s = sel >= 0 ? h.weaponAt(sel) : null;
            if (s != null && s.weaponName.equalsIgnoreCase("m230")) {
                idx = sel;
                break;
            }
            h.queueWeaponSwitch(pilot, 1);
            h.tick();
        }
        req(idx >= 0, "the pilot can select the m230");
        req(WeaponSlot.hasSwitchableModes(h.weaponAt(idx).info), "the m230 has switchable modes");
        req(h.weaponModeOf(idx) == 0 && !h.selectedWeaponName().endsWith("[HE]"), "m230 starts in the normal mode");

        // Mode 0: the round is NON-explosive (negative power = blast sound only, reference MCH_WeaponMachineGun2).
        int normalPower = fireOneAndReadExplosion(level, h);
        req(normalPower < 0, "normal-mode m230 round is non-explosive, got power " + normalPower);

        h.switchWeaponMode(pilot);
        req(h.weaponModeOf(idx) == 1, "X switches the m230 to mode 1");
        req(h.selectedWeaponName().endsWith(" [HE]"), "the HUD weapon name shows [HE], got " + h.selectedWeaponName());
        req(!h.weaponAt(idx).canFire(), "a mode switch blocks the trigger briefly (reference 15-tick countWait)");
        for (int i = 0; i < WeaponSlot.MODE_SWITCH_DELAY + 2; i++) {
            h.tick();
        }
        int hePower = fireOneAndReadExplosion(level, h);
        req(hePower > 0, "[HE] m230 round explodes, got power " + hePower);

        h.switchWeaponMode(pilot);
        req(h.weaponModeOf(idx) == 0, "a second press wraps back to the normal mode");
        LOG.info("[CONTROLS-SELFTEST] W weapon modes OK (m230 normal power {} -> [HE] power {})", normalPower, hePower);

        pilot.stopRiding();
        h.discard();
    }

    /** Hold the trigger for one tick, return the explosionPower of the round that left the muzzle. */
    private static int fireOneAndReadExplosion(ServerLevel level, MchHelicopter h) throws Exception {
        List<MchBullet> before = level.getEntitiesOfClass(MchBullet.class, h.getBoundingBox().inflate(64.0));
        h.controlState(0).fire = true;
        h.tick();
        h.controlState(0).fire = false;
        List<MchBullet> after = level.getEntitiesOfClass(MchBullet.class, h.getBoundingBox().inflate(64.0));
        after.removeAll(before);
        req(!after.isEmpty(), "the m230 fired a round");
        java.lang.reflect.Field f = MchBullet.class.getDeclaredField("explosionPower");
        f.setAccessible(true);
        int power = f.getInt(after.get(0));
        for (MchBullet b : after) {
            b.discard();
        }
        return power;
    }

    // ---- B: rounds fly through leaves, HE explodes (power 1, no drops) on solid blocks, panes shatter ----
    private void testBlockDamage(ServerLevel level) {
        int y = 250;
        BlockPos leaves = new BlockPos(ox + 3, y, oz);
        BlockPos glass = new BlockPos(ox + 6, y, oz);
        BlockPos stone = glass.east().east().east();
        BlockPos pane = new BlockPos(ox + 3, y + 4, oz);
        BlockPos stone2 = new BlockPos(ox + 7, y + 4, oz);
        level.setBlockAndUpdate(leaves, Blocks.OAK_LEAVES.defaultBlockState()
            .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
        level.setBlockAndUpdate(glass, Blocks.GLASS.defaultBlockState());
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pane, Blocks.GLASS_PANE.defaultBlockState());
        level.setBlockAndUpdate(stone2, Blocks.STONE.defaultBlockState());

        MchHelicopter shooter = heli(level, "ah-64", 300.0); // only the round's owner
        mcheli.agnostic.weapon.MCH_WeaponInfo m230 = mcheli.agnostic.weapon.MCH_WeaponInfoManager.get("m230");
        req(m230 != null, "m230 config");
        // [HE] round (power +explosion) into the glass block
        MchBullet he = MchBullet.spawnWeapon(level, new net.minecraft.world.phys.Vec3(ox + 0.5, y + 0.5, oz + 0.5),
            new net.minecraft.world.phys.Vec3(1, 0, 0), 2.0F, 1.0F, 0.0F, m230.power, 100, shooter, "bullet", -1, m230);
        he.setExplosionPower(m230.explosion);
        for (int i = 0; i < 20 && !he.isRemoved(); i++) {
            he.tick();
        }
        req(he.isRemoved(), "the HE round detonated");
        req(level.getBlockState(leaves).is(Blocks.OAK_LEAVES), "the round flew through the leaves without touching them");
        req(level.getBlockState(glass).isAir(), "the power-1 HE blast broke the glass it hit");
        req(level.getBlockState(stone).is(Blocks.STONE), "stone 3 blocks behind is untouched");
        req(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
            new net.minecraft.world.phys.AABB(glass).inflate(4.0)).isEmpty(), "the HE blast dropped no items");
        // a normal (non-explosive) round shoots THROUGH a glass pane (reference BulletBreakableBlocks)
        MchBullet ball = MchBullet.spawnWeapon(level, new net.minecraft.world.phys.Vec3(ox + 0.5, y + 4.5, oz + 0.5),
            new net.minecraft.world.phys.Vec3(1, 0, 0), 2.0F, 1.0F, 0.0F, m230.power, 100, shooter, "bullet", -1, m230);
        ball.setExplosionPower(-m230.explosion);
        for (int i = 0; i < 20 && !ball.isRemoved(); i++) {
            ball.tick();
        }
        req(level.getBlockState(pane).isAir(), "a round shoots through a glass pane, breaking it");
        req(level.getBlockState(stone2).is(Blocks.STONE), "and stops at the stone behind it");
        // with mobGriefing off neither the HE blast nor the round breaks anything (vanilla MOB explosion = KEEP)
        net.minecraft.world.level.GameRules.BooleanValue grief =
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
        boolean griefBefore = grief.get();
        BlockPos glass2 = new BlockPos(ox + 6, y + 8, oz);
        BlockPos pane2 = new BlockPos(ox + 3, y + 8, oz);
        level.setBlockAndUpdate(glass2, Blocks.GLASS.defaultBlockState());
        level.setBlockAndUpdate(pane2, Blocks.GLASS_PANE.defaultBlockState());
        try {
            grief.set(false, level.getServer());
            MchBullet he2 = MchBullet.spawnWeapon(level, new net.minecraft.world.phys.Vec3(ox + 0.5, y + 8.5, oz + 0.5),
                new net.minecraft.world.phys.Vec3(1, 0, 0), 2.0F, 1.0F, 0.0F, m230.power, 100, shooter, "bullet", -1, m230);
            he2.setExplosionPower(m230.explosion);
            for (int i = 0; i < 20 && !he2.isRemoved(); i++) {
                he2.tick();
            }
            req(he2.isRemoved(), "the HE round stopped at the pane");
            req(level.getBlockState(pane2).is(Blocks.GLASS_PANE), "mobGriefing off: the round does not shatter the pane");
            req(level.getBlockState(glass2).is(Blocks.GLASS), "mobGriefing off: the HE blast breaks nothing");
        } finally {
            grief.set(griefBefore, level.getServer());
        }
        LOG.info("[CONTROLS-SELFTEST] B block damage OK (leaves shot through, HE blast broke glass, no drops; pane shot through; nothing breaks with mobGriefing off)");
        shooter.discard();
    }

    private static void req(boolean cond, String what) {
        if (!cond) {
            throw new IllegalStateException(what);
        }
    }
}
