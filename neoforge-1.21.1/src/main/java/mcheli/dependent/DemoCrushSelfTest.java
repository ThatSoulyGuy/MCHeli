package mcheli.dependent;

import mcheli.dependent.entity.MchTank;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Headless proof of the two terrain-interaction behaviours the port was missing, plus a gunner-aim probe.
 *
 * <p><b>Probe A — terrain tilt with NO rider.</b> The reference ran {@code WheelMng.move()} server-side every tick
 * regardless of rider ({@code MCH_EntityTank}:718); the port only reached the solver from the local pilot's client
 * render frame, so an abandoned tank floated level on a hillside. This spawns an m1a2 on a staircase with no pilot
 * and no control input and asserts the hull pitches onto the grade.
 *
 * <p><b>Probe B — block crushing by a DRIVEN tank.</b> An m1a2 with a pig in the PILOT seat (requested with
 * {@code preferSeat}: the automatic rule gives seat 0 only to players, and without a pilot the throttle is cleared every
 * tick) and a full tank of fuel drives forward (+Z) over a torch into two walls: the oak-planks wall (material
 * {@code wood}) is destroyed, the stone wall behind it stops the tank and is inert, and the torch it drove over is
 * spared ({@code noBreak}). The tank must also have actually moved, so a pass cannot come only from blocks already
 * inside the parked footprint. Differential by construction — a pass that deleted everything in range would fail the
 * stone/torch probes, and one that did nothing would fail the planks.
 *
 * <p><b>Probe C — the column-abandon branch, on a PARKED tank.</b> {@code destroyBlockRange} scans each column
 * bottom-up and abandons the column at a no-break block, so a torch shields the planks block directly ABOVE it, while
 * a planks "witness" in the next column (no torch) is crushed by the same scans. This runs on an unridden, parked tank
 * because a driven one carries its turret-top part box (m1a2 {@code BoundingBox = 0.0, 3.0, -2.0, ...}) over the
 * column, and that box's own scan starts ABOVE the torch row, so it crushes the shielded block exactly as the
 * reference does ({@code MCH_EntityTank}:796-803 and :811-849 run every part box's scan from its own height).
 *
 * <p><b>Probe D — a gunner aims his own weapon.</b> An m1a2 with NO pilot, a gunner in seat 1 (the {@code m2_rws},
 * an unlimited-traverse {@code AddTurretWeapon}), hull at yaw 0 and gunner at yaw -90. Every {@code turret} mount
 * used to fire along {@code turretOrbitYaw()}, which is sourced from {@code pilot()} — so a gunner's rounds left
 * along the driver's aim, or hull-forward with no driver. The mean round heading must equal the GUNNER's yaw, and
 * the hull/gunner split is what makes it differential: a regression to the old behaviour reads ~0 instead of -90.
 *
 * <p><b>Probe E — the rider pose (golden).</b> An m1a2 at hull yaw 25 with a test-player pilot (look 30/-10) and a pig
 * in the rotSeat gunner seat 1. After the first tick the pilot looks 120/-20: the turret ring slews at the reference
 * 15 deg/tick ({@code turretOrbitYaw}) while the elevation follows the live look ({@code turretAimPitch}); the seat
 * attachments, the config and gunner camera eyes and the dismount point are pinned as vectors relative to the hull;
 * then the pilot leaves and the latch must hold the turret and the elevation. The values were recorded twice on the
 * tree before the rider pose moved out of the entity, so the move is checked against them.
 *
 * <p>The generated vehicle crafting recipes used to be a probe here too; they are {@link DemoRecipeSelfTest} now.
 */
public final class DemoCrushSelfTest extends MchSelfTest {

    private static final int CHECK_AFTER_TICKS = 60;
    private static final float TILT_MIN = 1.0F; // degrees; m1a2 OnGroundPitchFactor = 2.0 per tick
    /** The driven tank must have advanced at least this far (blocks) for the crush to count as "driven". */
    private static final double DRIVE_MIN = 0.5;

    private MchTank tiltTank;

    private MchTank aimTank;
    private Entity aimGunner;

    private MchTank crushTank;
    private double crushStartZ;
    private BlockPos planksProbe;
    private BlockPos stoneProbe;
    private BlockPos torchProbe;

    private MchTank shieldTank;
    private BlockPos shieldTorch;
    private BlockPos shieldedProbe;
    private BlockPos witnessProbe;

    // ---- Probe E (golden): the rider pose, recorded on the tree before the rider-pose move ----
    private static final float POSE_YAW0 = 30.0F;
    private static final float POSE_PITCH0 = -10.0F;
    private static final float POSE_YAW1 = 120.0F;
    private static final float POSE_PITCH1 = -20.0F;
    private static final float POSE_HULL_YAW = 25.0F;
    /** turretOrbitYaw(1F) on ticks 2..7, after the pilot turned from 30 to 120 on tick 1. */
    private static final float[] POSE_SLEW = {20.0F, 35.0F, 50.0F, 65.0F, 80.0F, 95.0F};
    /** turretAimPitch(1F) on ticks 2..7 (the live look while piloted). */
    private static final float[] POSE_AIM_PITCH = {-20.0F, -20.0F, -20.0F, -20.0F, -20.0F, -20.0F};
    /** turretOrbitYaw(1F) and turretAimPitch(1F) on ticks 9..11, after the pilot left on tick 8 (the latch holds). */
    private static final float[] POSE_HOLD_YAW = {95.0F, 95.0F, 95.0F};
    private static final float[] POSE_HOLD_PITCH = {-20.0F, -20.0F, -20.0F};
    /** Relative to the hull: on tick 7 the gunner and pilot attachments, the pilot and gunner eyes and the pilot's
     *  dismount point; on tick 11 (no pilot) the gunner attachment and eye. */
    private static final double[][] POSE_VECTORS = {
        {1.1063843965530396, 1.2354760020971298, 0.0036857128143310547},
        {-0.21650639176368713, 2.23547600209713, -0.12499994039535522},
        {-0.7794228196144104, 3.73547600209713, -0.44999998807907104},
        {0.09997254610061646, 3.835475906729698, -0.9191575050354004},
        {2.613269090652466, 2.0, 1.494431734085083},
        {1.1063843965530396, 1.2354760020971298, 0.0036857128143310547},
        {0.09997254610061646, 3.835475906729698, -0.9191575050354004},
    };
    /** The other pose reads, as one string: modelMinY, hidesRider, the HideGM groups, the camera-position count and
     *  switch, the view camera after setViewCameraId(1), the seats. (The latch itself is pinned by the hold samples;
     *  the entity's dead latch query, due for deletion, is deliberately not read, so it keeps no caller.) */
    private static final String POSE_MISC = "minY=-0.235476 hidesRider=true hideGM=[] camCount=1 camSwitch=false "
        + "cam=0->0 seats=0,1";
    private static final double POSE_EPS = 1.0e-5;

    private MchTank poseTank;
    private ServerPlayer posePilot;
    private Entity poseGunner;
    private boolean poseBoarded;
    private boolean poseLeft;
    private final float[] poseSlew = new float[6];
    private final float[] poseAimPitch = new float[6];
    private final float[] poseHoldYaw = new float[3];
    private final float[] poseHoldPitch = new float[3];
    private final double[][] poseVectors = new double[7][];
    private String poseMisc = "n/a";

    @Override
    protected void start() {
        BlockPos spawn = this.origin;
        var stone = Blocks.STONE.defaultBlockState();
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
        // A torch on the floor at z+2, in the tank's path: it must survive being driven over (noBreak).
        torchProbe = new BlockPos(cx, floorY + 1, cz + 2);
        level.setBlockAndUpdate(torchProbe, Blocks.TORCH.defaultBlockState());
        planksProbe = new BlockPos(cx, floorY + 1, cz + 4);
        stoneProbe = new BlockPos(cx, floorY + 1, cz + 6);

        crushTank = MchRegistries.TANK.get().create(level);
        if (crushTank != null) {
            crushTank.setConfigName("m1a2"); // WeightType = Tank
            crushTank.setPos(cx + 0.5, floorY + 1, cz + 0.5);
            crushTank.setYRot(0.0F); // forward = +Z, into the walls
            level.addFreshEntity(crushTank);
            crushTank.setFuel(crushTank.getMaxFuel()); // m1a2 MaxFuel=1200 spawns dry; the thrust gate reads canUseFuel
            Entity crushPilot = EntityType.PIG.create(level);
            if (crushPilot != null) {
                if (crushPilot instanceof Mob mob) {
                    mob.setNoAi(true);
                }
                crushPilot.setPos(cx + 0.5, floorY + 1, cz + 0.5);
                level.addFreshEntity(crushPilot);
                crushTank.preferSeat(crushPilot, 0); // the PILOT seat: only a pilot's throttle survives the tick
                crushPilot.startRiding(crushTank, true);
            }
            crushTank.getControlState().throttleUp = true;
            crushStartZ = crushTank.getZ();
            LOG.info("[CRUSH-SELFTEST] crush probe: tank id={} pilotSeat={} planks@{} stone@{} torch@{}",
                crushTank.getId(), crushPilot == null ? -1 : crushTank.seatIndexOf(crushPilot),
                planksProbe, stoneProbe, torchProbe);
        }

        // ---- Probe C: a PARKED, unridden tank; a torch with planks above it, and a planks witness beside it ----
        int sx = spawn.getX() - 24;
        int sz = spawn.getZ() + 24;
        for (int bx = sx - 6; bx <= sx + 6; bx++) {
            for (int bz = sz - 6; bz <= sz + 8; bz++) {
                level.setBlockAndUpdate(new BlockPos(bx, floorY, bz), stone);
                for (int by = floorY + 1; by <= floorY + 8; by++) {
                    level.setBlockAndUpdate(new BlockPos(bx, by, bz), air);
                }
            }
        }
        // z+2 is reached by the hull scan and the low front/side part boxes, all of whose column scans start at or below
        // the torch row; the turret-top box (local z -2.0) only reaches up to about z+0.5 while the tank is parked.
        shieldTorch = new BlockPos(sx, floorY + 1, sz + 2);
        shieldedProbe = new BlockPos(sx, floorY + 2, sz + 2);
        witnessProbe = new BlockPos(sx + 1, floorY + 1, sz + 2);
        level.setBlockAndUpdate(shieldedProbe, planks);
        level.setBlockAndUpdate(shieldTorch, Blocks.TORCH.defaultBlockState());
        level.setBlockAndUpdate(witnessProbe, planks);
        shieldTank = MchRegistries.TANK.get().create(level);
        if (shieldTank != null) {
            shieldTank.setConfigName("m1a2");
            shieldTank.setPos(sx + 0.5, floorY + 1, sz + 0.5);
            shieldTank.setYRot(0.0F);
            level.addFreshEntity(shieldTank);
            LOG.info("[CRUSH-SELFTEST] column-shield probe: parked tank id={} torch@{} shielded@{} witness@{}",
                shieldTank.getId(), shieldTorch, shieldedProbe, witnessProbe);
        }
        // ---- Probe D setup: an m1a2 with NO pilot and a gunner in seat 1 (the m2_rws), looking 90 deg off the hull ----
        int gx = spawn.getX() + 24;
        int gz = spawn.getZ() - 24;
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
        setupPoseProbe(spawn.getX(), spawn.getY() - 1, spawn.getZ());
    }

    /** Probe E setup: a flat floor at the origin, an m1a2 at hull yaw {@link #POSE_HULL_YAW} (so the body rotation is
     *  not the identity) with a test-player pilot looking
     *  {@link #POSE_YAW0}/{@link #POSE_PITCH0} and a pig in the rotSeat gunner seat 1. */
    private void setupPoseProbe(int px, int floorY, int pz) {
        var stone = Blocks.STONE.defaultBlockState();
        var air = Blocks.AIR.defaultBlockState();
        for (int bx = px - 8; bx <= px + 8; bx++) {
            for (int bz = pz - 8; bz <= pz + 8; bz++) {
                level.setBlockAndUpdate(new BlockPos(bx, floorY, bz), stone);
                for (int by = floorY + 1; by <= floorY + 8; by++) {
                    level.setBlockAndUpdate(new BlockPos(bx, by, bz), air);
                }
            }
        }
        poseTank = MchRegistries.TANK.get().create(level);
        if (poseTank == null) {
            return;
        }
        poseTank.setConfigName("m1a2");
        poseTank.setPos(px + 0.5, floorY + 1, pz + 0.5);
        poseTank.setYRot(POSE_HULL_YAW);
        level.addFreshEntity(poseTank);
        posePilot = newTestPlayer("posepilot");
        posePilot.setPos(px + 0.5, floorY + 1, pz + 0.5);
        posePilot.setYRot(POSE_YAW0);
        posePilot.setXRot(POSE_PITCH0);
        poseTank.preferSeat(posePilot, 0);
        boolean pilotIn = posePilot.startRiding(poseTank, true) && poseTank.seatIndexOf(posePilot) == 0;
        poseGunner = EntityType.PIG.create(level);
        boolean gunnerIn = false;
        if (poseGunner != null) {
            if (poseGunner instanceof Mob mob) {
                mob.setNoAi(true);
            }
            poseGunner.setPos(px + 0.5, floorY + 1, pz + 0.5);
            level.addFreshEntity(poseGunner);
            poseTank.preferSeat(poseGunner, 1);
            gunnerIn = poseGunner.startRiding(poseTank, true) && poseTank.seatIndexOf(poseGunner) == 1;
        }
        poseBoarded = pilotIn && gunnerIn;
        LOG.info("[CRUSH-SELFTEST] rider-pose probe: tank id={} pilotIn={} gunnerIn={}", poseTank.getId(), pilotIn,
            gunnerIn);
    }

    /** Probe E, per tick: the pilot turns on tick 1, the pose is sampled on ticks 2..7, the pilot leaves on tick 8 and
     *  the latch is sampled on ticks 9..11. */
    private void tickPoseProbe(int ticks) {
        if (poseTank == null || posePilot == null || poseGunner == null || !poseBoarded) {
            return;
        }
        if (ticks == 1) {
            posePilot.setYRot(POSE_YAW1);
            posePilot.setXRot(POSE_PITCH1);
        } else if (ticks >= 2 && ticks <= 7) {
            poseSlew[ticks - 2] = poseTank.turretOrbitYaw(1.0F);
            poseAimPitch[ticks - 2] = poseTank.turretAimPitch(1.0F);
            if (ticks == 7) {
                Vec3 at = poseTank.position();
                poseVectors[0] = xyz(poseTank.riderAttachment(poseGunner, 1.0F));
                poseVectors[1] = xyz(poseTank.riderAttachment(posePilot, 1.0F));
                poseVectors[2] = xyz(relative(poseTank.firstPersonEye(posePilot, 1.0F), at));
                poseVectors[3] = xyz(relative(poseTank.firstPersonEye(poseGunner, 1.0F), at));
                poseVectors[4] = xyz(relative(poseTank.getDismountLocationForPassenger(posePilot), at));
                int camBefore = poseTank.getViewCameraId();
                poseTank.setViewCameraId(1);
                int camAfter = poseTank.getViewCameraId();
                poseTank.setViewCameraId(0);
                poseMisc = "minY=" + poseTank.modelMinY() + " hidesRider=" + poseTank.hidesRider()
                    + " hideGM=" + new java.util.TreeSet<>(poseTank.firstPersonHiddenGroups())
                    + " camCount=" + poseTank.cameraPosCount() + " camSwitch=" + poseTank.canSwitchCameraPos()
                    + " cam=" + camBefore + "->" + camAfter
                    + " seats=" + poseTank.seatIndexOf(posePilot) + "," + poseTank.seatIndexOf(poseGunner);
            }
        } else if (ticks == 8) {
            posePilot.stopRiding();
            poseLeft = !posePilot.isPassenger() && poseTank.pilot() == null;
        } else if (ticks >= 9 && ticks <= 11) {
            poseHoldYaw[ticks - 9] = poseTank.turretOrbitYaw(1.0F);
            poseHoldPitch[ticks - 9] = poseTank.turretAimPitch(1.0F);
            if (ticks == 11) {
                Vec3 at = poseTank.position();
                poseVectors[5] = xyz(poseTank.riderAttachment(poseGunner, 1.0F));
                poseVectors[6] = xyz(relative(poseTank.firstPersonEye(poseGunner, 1.0F), at));
            }
        }
    }

    /** Probe E verdict: every sampled value equals the recorded golden (the angles exactly, the vectors to 1e-5). */
    private boolean checkPoseProbe() {
        StringBuilder obs = new StringBuilder();
        obs.append("boarded=").append(poseBoarded).append(" left=").append(poseLeft)
            .append(" slew=").append(java.util.Arrays.toString(poseSlew))
            .append(" aimPitch=").append(java.util.Arrays.toString(poseAimPitch))
            .append(" holdYaw=").append(java.util.Arrays.toString(poseHoldYaw))
            .append(" holdPitch=").append(java.util.Arrays.toString(poseHoldPitch))
            .append(" vectors=").append(java.util.Arrays.deepToString(poseVectors))
            .append(" misc=").append(poseMisc);
        boolean ok = poseBoarded && poseLeft
            && sameFloats(poseSlew, POSE_SLEW) && sameFloats(poseAimPitch, POSE_AIM_PITCH)
            && sameFloats(poseHoldYaw, POSE_HOLD_YAW) && sameFloats(poseHoldPitch, POSE_HOLD_PITCH)
            && nearVectors(poseVectors, POSE_VECTORS) && poseMisc.equals(POSE_MISC);
        LOG.info("[CRUSH-SELFTEST] rider-pose observed: {}", obs);
        LOG.info("[CRUSH-SELFTEST] rider-pose probe: matches the recorded golden -> {}", ok ? "OK" : "FAIL");
        return ok;
    }

    private static Vec3 relative(Vec3 p, Vec3 at) {
        return p == null ? null : p.subtract(at);
    }

    private static double[] xyz(Vec3 p) {
        return p == null ? null : new double[] {p.x, p.y, p.z};
    }

    private static boolean sameFloats(float[] got, float[] want) {
        if (want == null || got.length != want.length) {
            return false;
        }
        for (int i = 0; i < got.length; i++) {
            if (Float.floatToIntBits(got[i]) != Float.floatToIntBits(want[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean nearVectors(double[][] got, double[][] want) {
        if (want == null || got.length != want.length) {
            return false;
        }
        for (int i = 0; i < got.length; i++) {
            if (got[i] == null || want[i] == null || got[i].length != want[i].length) {
                return false;
            }
            for (int k = 0; k < got[i].length; k++) {
                if (Math.abs(got[i][k] - want[i][k]) > POSE_EPS) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    protected void cleanup() {
        if (posePilot != null && posePilot.isPassenger()) {
            posePilot.stopRiding();
        }
    }

    private static void logAim(int bullets, double heading, float hullYaw, float gunnerYaw, boolean ok) {
        LOG.info("[CRUSH-SELFTEST] gunner-aim probe: {} rounds, mean heading={} (hull={} gunner={}) -> {}",
            bullets, String.format("%.1f", heading), hullYaw, gunnerYaw, ok ? "OK" : "FAIL");
    }

    @Override
    protected void tick() {
        int ticks = ticks();
        // Keep the gunner's magazine topped up. m1a2's mounts spawn with an EMPTY reserve and the probe rider is a pig
        // (so it never gets the creative onMount top-up), which is the ammo economy doing its job — orthogonal to the
        // aim under test. Supplied every tick rather than once because the weapon list is built lazily on the first
        // server tick and the seat's selection is only assigned once the rider has boarded.
        if (ticks < 40 && aimTank != null) {
            aimTank.debugSupplySeatWeapon(1);
        }
        tickPoseProbe(ticks);
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

        boolean tilted = false;
        if (tiltTank == null) {
            LOG.error("[CRUSH-SELFTEST] FAIL: tilt tank did not spawn");
        } else {
            float pitch = tiltTank.getXRot();
            tilted = Math.abs(pitch) >= TILT_MIN;
            LOG.info("[CRUSH-SELFTEST] tilt probe: unridden pitch={} roll={} (need |pitch| >= {}) -> {}",
                pitch, tiltTank.getRollAngle(), TILT_MIN, tilted ? "OK" : "FAIL");
        }

        boolean crushed = false;
        if (crushTank == null) {
            LOG.error("[CRUSH-SELFTEST] FAIL: crush tank did not spawn");
        } else {
            double driven = crushTank.getZ() - crushStartZ;
            boolean moved = driven >= DRIVE_MIN;
            boolean planksGone = level.getBlockState(planksProbe).isAir();
            boolean stoneKept = level.getBlockState(stoneProbe).is(Blocks.STONE);
            boolean torchKept = level.getBlockState(torchProbe).is(Blocks.TORCH);
            LOG.info("[CRUSH-SELFTEST] crush probe: drove={} (need>={}) planksDestroyed={} stoneIntact={} torchIntact={}",
                String.format("%.2f", driven), DRIVE_MIN, planksGone, stoneKept, torchKept);
            crushed = moved && planksGone && stoneKept && torchKept;
        }

        boolean shielded = false;
        if (shieldTank == null) {
            LOG.error("[CRUSH-SELFTEST] FAIL: column-shield tank did not spawn");
        } else {
            boolean witnessGone = level.getBlockState(witnessProbe).isAir();
            boolean torchKept = level.getBlockState(shieldTorch).is(Blocks.TORCH);
            boolean shieldedKept = level.getBlockState(shieldedProbe).is(Blocks.OAK_PLANKS);
            LOG.info("[CRUSH-SELFTEST] column-shield probe: witnessCrushed={} torchIntact={} plankAboveTorchIntact={}",
                witnessGone, torchKept, shieldedKept);
            shielded = witnessGone && torchKept && shieldedKept;
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
            logAim(bullets, heading, aimTank.getYRot(), aimGunner.getYRot(), aimOk);
        } else {
            LOG.error("[CRUSH-SELFTEST] FAIL: gunner-aim probe did not spawn");
        }

        boolean poseOk = checkPoseProbe();

        finish(tilted && crushed && shielded && aimOk && poseOk,
            "unriddenTilt={} drivenCrush={} columnShield={} gunnerAim={} riderPose={}",
            tilted, crushed, shielded, aimOk, poseOk);
    }
}
