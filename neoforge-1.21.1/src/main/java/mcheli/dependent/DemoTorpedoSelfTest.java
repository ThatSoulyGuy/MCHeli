package mcheli.dependent;

import com.mojang.logging.LogUtils;
import mcheli.agnostic.weapon.MCH_WeaponBallistics;
import mcheli.agnostic.weapon.MCH_WeaponInfo;
import mcheli.agnostic.weapon.VehicleWeapons;
import mcheli.dependent.entity.MchBullet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Headless proof of the <b>torpedo water-run</b> (#30): torpedoes are now SELECTABLE
 * ({@code VehicleWeapons.isFireable} enables "torpedo") and, once submerged, run under the reference
 * {@code MCH_EntityTorpedo.onUpdateNoGuided} model — the descent levels off, the speed ramps toward
 * {@code accelerationInWater}, and the motion re-normalizes to that cruise speed regardless of launch speed.
 *
 * <p>Differential in a static water box: a FAST launch (3.5) must DECELERATE into the cruise band while a SLOW launch
 * (0.6) must ACCELERATE up into it — that convergence-from-both-sides is the water-run signature. The fast torpedo also
 * launches perfectly horizontal with an AIR gravity of {@code -0.05} but {@code gravityInWater = 0}; staying flat proves
 * the reference gravity→gravityInWater swap fires the instant it is submerged (a broken swap would sink it).
 *
 * <p>A third torpedo has {@code Acceleration = 0} and is launched purely on a CARRIER velocity (mirroring the fire path
 * {@code unit_dir × Acceleration + carrier}, via {@link MchBullet#spawnTorpedo}) — it must still propel forward and
 * cruise, proving the reference launch model where the carrier's motion is the whole thrust (three of the five shipped
 * torpedoes ship {@code Acceleration = 0}; a plain muzzle shot would leave them dead at the tube).
 */
public final class DemoTorpedoSelfTest {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int CHECK_AFTER_TICKS = 12;
    private static final float ACC_IN_WATER = 2.0F;

    private ServerLevel level;
    private int ticks = -1;
    private int forceCx;
    private int forceCz;

    private boolean torpedoSelectable;
    private MchBullet fast;   // launched at 3.5 -> should decelerate to the cruise band
    private MchBullet slow;   // launched at 0.6 -> should accelerate to the cruise band
    private MchBullet zero;   // Acceleration 0, launched ONLY on carrier motion -> must still propel + cruise

    private double fastSpeed = -1.0;
    private double fastMotionY = -1.0;
    private boolean fastSubmerged;
    private double slowSpeed = -1.0;
    private boolean slowSubmerged;
    private double zeroSpeed = -1.0;
    private double zeroDownrange = -1.0; // +Z distance travelled: proves it actually moved off the tube
    private double zeroStartZ;
    private boolean zeroSubmerged;

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        this.level = event.getServer().overworld();
        this.torpedoSelectable = VehicleWeapons.isFireable("torpedo"); // the enable half of #30

        BlockPos spawn = this.level.getSharedSpawnPos();
        this.forceCx = spawn.getX() >> 4;
        this.forceCz = spawn.getZ() >> 4;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 5; dz++) { // bias +Z: the fast torpedo runs ~35 blocks downrange in 12 ticks
                this.level.setChunkForced(this.forceCx + dx, this.forceCz + dz, true);
            }
        }

        // A static water box straddling the run: fill with SOURCE water WITHOUT neighbour updates (flag 2) so it never
        // schedules a flow tick and stays put for the measurement window. The torpedoes run submerged through the middle.
        double baseY = spawn.getY() + 4.0;
        BlockState water = Blocks.WATER.defaultBlockState();
        for (int x = spawn.getX() - 4; x <= spawn.getX() + 4; x++) {
            for (int z = spawn.getZ() - 2; z <= spawn.getZ() + 48; z++) {
                for (int y = spawn.getY() + 1; y <= spawn.getY() + 9; y++) {
                    this.level.setBlock(new BlockPos(x, y, z), water, 2);
                }
            }
        }

        // Fast torpedo: air gravity -0.05, gravityInWater 0, launched dead-level down +Z. It must decelerate AND stay flat.
        this.fast = launch(torpedoInfo(3.5F), spawn.getX() - 2.5, baseY, spawn.getZ() + 0.5, Vec3.ZERO);
        // Slow torpedo, a lane over: must accelerate up toward the same cruise speed.
        this.slow = launch(torpedoInfo(0.6F), spawn.getX() + 0.5, baseY, spawn.getZ() + 0.5, Vec3.ZERO);
        // Acceleration-0 torpedo, launched ONLY on a +Z carrier velocity (2.0): must still propel forward + cruise.
        this.zeroStartZ = spawn.getZ() + 0.5;
        this.zero = launch(torpedoInfo(0.0F), spawn.getX() + 3.0, baseY, this.zeroStartZ, new Vec3(0, 0, 2.0));

        this.ticks = 0;
    }

    /** A synthetic torpedo weapon (no content pack loaded): air gravity would sink it, but gravityInWater is 0. */
    private MCH_WeaponInfo torpedoInfo(float acceleration) {
        MCH_WeaponInfo wi = new MCH_WeaponInfo("mch_test_torpedo");
        wi.type = "torpedo";
        wi.acceleration = acceleration;
        wi.accelerationInWater = ACC_IN_WATER;
        wi.gravity = -0.05F;      // AIR: would pull the run downward ...
        wi.gravityInWater = 0.0F; // ... WATER: none, so a level launch stays level (the swap under test)
        wi.power = 35;
        wi.explosion = 8;
        wi.explosionInWater = 8;
        return wi;
    }

    /** Launch a torpedo along +Z via the real seam ({@link MchBullet#spawnTorpedo}): launch velocity = unit_dir ×
     *  Acceleration + carrier motion (reference {@code MCH_WeaponTorpedo.shotNoGuided}), matching fireSelectedWeapon. */
    private MchBullet launch(MCH_WeaponInfo wi, double x, double y, double z, Vec3 carrier) {
        Vec3 velocity = new Vec3(0, 0, 1).scale(wi.acceleration).add(carrier);
        String model = MCH_WeaponBallistics.defaultBulletModel(wi.type);
        return MchBullet.spawnTorpedo(this.level, new Vec3(x, y, z), velocity, wi.gravity, wi.power, 600, null,
            model, 0xFFFFFFFF, wi);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (this.ticks < 0) {
            return;
        }
        this.ticks++;
        if (this.ticks < CHECK_AFTER_TICKS) {
            return;
        }

        if (this.fast != null && !this.fast.isRemoved()) {
            Vec3 m = this.fast.getDeltaMovement();
            this.fastSpeed = m.length();
            this.fastMotionY = Math.abs(m.y);
            this.fastSubmerged = this.level.getFluidState(this.fast.blockPosition()).is(FluidTags.WATER);
        }
        if (this.slow != null && !this.slow.isRemoved()) {
            Vec3 m = this.slow.getDeltaMovement();
            this.slowSpeed = m.length();
            this.slowSubmerged = this.level.getFluidState(this.slow.blockPosition()).is(FluidTags.WATER);
        }
        if (this.zero != null && !this.zero.isRemoved()) {
            Vec3 m = this.zero.getDeltaMovement();
            this.zeroSpeed = m.length();
            this.zeroDownrange = this.zero.getZ() - this.zeroStartZ;
            this.zeroSubmerged = this.level.getFluidState(this.zero.blockPosition()).is(FluidTags.WATER);
        }

        // Fast (3.5) must have decelerated into the cruise band; slow (0.6) must have accelerated up into it.
        boolean fastDecelerated = this.fastSpeed > 1.8 && this.fastSpeed < 3.0;
        boolean slowAccelerated = this.slowSpeed > 1.2 && this.slowSpeed < 2.2;
        boolean fastFlat = this.fastMotionY >= 0.0 && this.fastMotionY < 0.12; // gravity swap: no sinking in water
        // Acceleration-0 + carrier: it must NOT be dead at the tube — it propelled downrange and cruises at ~accInWater.
        boolean zeroPropelled = this.zeroDownrange > 10.0 && this.zeroSpeed > 1.5 && this.zeroSpeed < 2.3;
        boolean allSubmerged = this.fastSubmerged && this.slowSubmerged && this.zeroSubmerged;
        boolean pass = this.torpedoSelectable && fastDecelerated && slowAccelerated && fastFlat && zeroPropelled
            && allSubmerged;

        LOG.info("[TORPEDO-SELFTEST] selectable={} fastSpeed={} fastMotionY={} slowSpeed={} zeroSpeed={} zeroDownrange={} allSub={}",
            this.torpedoSelectable, String.format("%.3f", this.fastSpeed), String.format("%.4f", this.fastMotionY),
            String.format("%.3f", this.slowSpeed), String.format("%.3f", this.zeroSpeed),
            String.format("%.2f", this.zeroDownrange), allSubmerged);
        LOG.info("[TORPEDO-SELFTEST] RESULT: {} - selectable={} fastDecelerated={} slowAccelerated={} fastFlat={} zeroPropelled={} allSubmerged={}",
            pass ? "PASS" : "FAIL", this.torpedoSelectable, fastDecelerated, slowAccelerated, fastFlat, zeroPropelled, allSubmerged);

        cleanup();
    }

    private void cleanup() {
        for (MchBullet b : this.level.getEntitiesOfClass(MchBullet.class,
            new AABB(-100000, -100, -100000, 100000, 400, 100000))) {
            b.discard();
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 5; dz++) {
                this.level.setChunkForced(this.forceCx + dx, this.forceCz + dz, false);
            }
        }
        this.ticks = -1;
        this.fast = null;
        this.slow = null;
        this.zero = null;
    }
}
