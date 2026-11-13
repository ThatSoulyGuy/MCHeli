package mcheli.agnostic.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.spi.RandomSource;
import mcheli.agnostic.spi.Role;
import mcheli.agnostic.spi.WorldView;
import mcheli.agnostic.tank.MCH_TankInfo;
import mcheli.agnostic.util.MCH_LowPassFilterFloat;
import mcheli.agnostic.value.AABB;
import mcheli.agnostic.value.MoveResult;
import mcheli.agnostic.value.RayHit;
import mcheli.agnostic.value.Vec3d;
import org.junit.jupiter.api.Test;

/**
 * Behavioural trace tests for {@link TankControlMapping} via {@link RotationSolver#applyControl}: A/D turns the hull
 * (keyboard yaw scaled by ground-mobility) while the mouse stick does nothing (the tank's mouse contributes no hull
 * rotation). The tank sits on the ground (a solid block below), so {@code onUpdateAngles} takes the hull-yaw branch.
 */
class TankControlMappingTest {

    /** Solid ground below y=64, air at/above (so blockIdInColumn(3,-3) finds ground -> "not flying"). */
    static final class GroundWorld implements WorldView {
        @Override public boolean isRemote() { return false; }
        @Override public long totalTime() { return 0L; }
        @Override public int getBlockId(int x, int y, int z) { return y < 64 ? 1 : 0; }
        @Override public boolean isWater(int x, int y, int z) { return false; }
        @Override public String materialName(int x, int y, int z) { return y < 64 ? "stone" : "air"; }
        @Override public boolean isCollidable(int x, int y, int z) { return y < 64; }
        @Override public boolean isAABBInWater(AABB box) { return false; }
        @Override public List<EntityRef> entitiesInAABB(AABB box) { return List.of(); }
        @Override public List<EntityRef> entitiesInAABB(AABB box, Role role) { return List.of(); }
        @Override public RayHit rayTraceBlocks(Vec3d from, Vec3d to) { return null; }
        @Override public RayHit clip(Vec3d from, Vec3d to, boolean stopOnLiquid) { return null; }
        @Override public void spawn(EntityRef entity) { }
        @Override public void spawnParticle(String name, Vec3d pos, Vec3d velocity) { }
        @Override public void playSound(Vec3d pos, String name, float volume, float pitch) { }
        @Override public void playSoundAtEntity(EntityRef entity, String name, float volume, float pitch) { }
        @Override public RandomSource random() { throw new UnsupportedOperationException(); }
    }

    static final class FakeTank implements EntityRef {
        float yaw, pitch, roll;
        int age = 100;
        WorldView world = new GroundWorld();
        @Override public float yaw() { return yaw; }
        @Override public float pitch() { return pitch; }
        @Override public float roll() { return roll; }
        @Override public void setRotation(float y, float p) { yaw = y; pitch = p; }
        @Override public void setRoll(float r) { roll = r; }
        @Override public Vec3d position() { return new Vec3d(0.5, 64.0, 0.5); }
        @Override public Vec3d prevPosition() { return new Vec3d(0.5, 64.0, 0.5); } // stationary
        @Override public int ageTicks() { return age; }
        @Override public WorldView world() { return world; }
        @Override public int id() { return 1; }
        @Override public boolean isSameAs(EntityRef o) { return this == o; }
        @Override public boolean isDead() { return false; }
        @Override public boolean isRemote() { return false; }
        @Override public Role role() { return Role.TANK; }
        @Override public Vec3d motion() { return Vec3d.ZERO; }
        @Override public float prevYaw() { return yaw; }
        @Override public float prevPitch() { return pitch; }
        @Override public float prevRoll() { return roll; }
        @Override public float width() { return 1; }
        @Override public float height() { return 1; }
        @Override public AABB boundingBox() { return new AABB(0, 64, 0, 1, 65, 1); }
        @Override public boolean onGround() { return true; }
        @Override public boolean isInWater() { return false; }
        @Override public void setPosition(Vec3d p) { }
        @Override public void setMotion(Vec3d m) { }
        @Override public void addMotion(Vec3d d) { }
        @Override public MoveResult move(Vec3d d) { throw new UnsupportedOperationException(); }
        @Override public EntityRef vehicle() { return null; }
        @Override public List<EntityRef> passengers() { return List.of(); }
    }

    static final class FakeTankState implements TankState {
        @Override public boolean isDestroyed() { return false; }
        @Override public boolean hasPilot() { return true; } // the mapping runs only for the seated pilot
        @Override public boolean isGunnerMode() { return false; }
        @Override public boolean isTargetDrone() { return false; }
        @Override public boolean canUseFuel() { return true; }
        @Override public boolean isCanopyClose() { return true; }
        @Override public void switchGunnerMode(boolean on) { }
    }

    static MCH_LowPassFilterFloat primed(float v) {
        MCH_LowPassFilterFloat lp = new MCH_LowPassFilterFloat(10);
        for (int i = 0; i < 10; i++) {
            lp.put(v);
        }
        return lp;
    }

    static ControlInput drive(boolean left, boolean right, double stickX) {
        // throttleUp on (driving forward) so the pivot-turn "reverse" flag stays +1.
        return new ControlInput(stickX, 0, true, false, left, right, false, false, false, false, 0.5F);
    }

    @Test
    void keyboardTurnsHull() {
        FakeTank e = new FakeTank();
        MCH_TankInfo info = new MCH_TankInfo("test_tank"); // mobilityYawOnGround=1, pivotTurnThrottle=0, canRotOnGround=true
        TankControlMapping map = new TankControlMapping(e, info, new AircraftSimState(), new FakeTankState());

        // D held, on the ground: onUpdateAngles -> yaw += 0.6*gmy(1)*pt(0.5)*flag(1)*sf(1) = +0.3.
        RotationSolver.applyControl(e, info, drive(false, true, 0), primed(0.5F), map);

        assertEquals(0.3f, e.yaw, 0.1f);
    }

    @Test
    void mouseDoesNotTurnHull() {
        FakeTank e = new FakeTank();
        MCH_TankInfo info = new MCH_TankInfo("test_tank");
        TankControlMapping map = new TankControlMapping(e, info, new AircraftSimState(), new FakeTankState());

        // Big mouse stick, no A/D: the tank's mouse contributes nothing to the hull -> yaw stays 0.
        RotationSolver.applyControl(e, info, drive(false, false, 1000), primed(0.5F), map);

        assertTrue(Math.abs(e.yaw) < 0.01f, "mouse must not rotate the hull, yaw=" + e.yaw);
    }

    @Test
    void hullTiltIgnoresTheRiderLookLimits() {
        // MinRotationPitch/MaxRotationPitch turn limitRotation on, but the tank's setAngles override bounds the HULL to
        // +/-90 only (MCH_EntityTank:1034-1050): the config pair limits the rider's look. A t-90 style -14..5 must not
        // pin a hull settling from 20 degrees at 5.
        FakeTank e = new FakeTank();
        e.pitch = 20.0F;
        MCH_TankInfo info = new MCH_TankInfo("test_tank");
        info.limitRotation = true;
        info.minRotationPitch = -14.0F;
        info.maxRotationPitch = 5.0F;
        info.wheels.clear(); // no wheel solve: the target is level
        TankControlMapping map = new TankControlMapping(e, info, new AircraftSimState(), new FakeTankState());

        RotationSolver.applyControl(e, info, drive(false, false, 0), primed(0.5F), map);

        assertEquals(10.0f, e.pitch, 0.05f); // lerped halfway toward the level target (no wheels), not clamped to 5
    }

    @Test
    void finalTankLimitsResetARunawayPitch() {
        MCH_TankInfo free = new MCH_TankInfo("test_tank");
        TankControlMapping map = new TankControlMapping(new FakeTank(), free, new AircraftSimState(), new FakeTankState());
        assertEquals(0.0f, map.limitFinalPitch(free, 95.0F), 0.0f);  // |pitch| > 90 -> 0 (MCH_EntityTank:1053-1056)
        assertEquals(95.0f, map.limitFinalRoll(free, 95.0F), 0.0f);  // roll only clamps with limitRotation
        MCH_TankInfo limited = new MCH_TankInfo("test_tank");
        limited.limitRotation = true;
        assertEquals(90.0f, map.limitFinalPitch(limited, 95.0F), 0.0f);
        assertEquals(-90.0f, map.limitComposedRoll(limited, -120.0F), 0.0f);
    }

    @Test
    void terrainTargetIsSolvedOncePerTick() {
        // Reference WheelMng.move solves targetPitch once per tick; the frames only lerp toward it. Swapping the
        // terrain mid-tick must not move the target until the tick counter advances.
        FakeTank e = new FakeTank();
        MCH_TankInfo info = new MCH_TankInfo("test_tank");
        info.wheels.clear(); // one axle fore, one aft (replacing the default wheel list)
        for (double z : new double[] {2.0, -2.0}) {
            info.wheels.add(new mcheli.agnostic.aircraft.MCH_AircraftInfo.Wheel(new Vec3d(1.0, -0.24, z)));
        }
        info.onGroundPitchFactor = 90.0F; // let the solve reach the slope in one tick
        TankControlMapping map = new TankControlMapping(e, info, new AircraftSimState(), new FakeTankState());

        RotationSolver.applyControl(e, info, drive(false, false, 0), primed(0.5F), map); // flat ground: target 0
        assertEquals(0.0f, e.pitch, 1e-3f);
        e.world = new TankFlightModelTraceTest.SlopeWorld();                                 // same tick, new terrain
        RotationSolver.applyControl(e, info, drive(false, false, 0), primed(0.5F), map);
        assertEquals(0.0f, e.pitch, 1e-3f, "still lerping toward this tick's level target");
        e.age++;                                                                             // next tick: re-solve
        RotationSolver.applyControl(e, info, drive(false, false, 0), primed(0.5F), map);
        assertTrue(e.pitch < -1.0f, "tilts nose-up onto the +Z grade once the tick advances, was " + e.pitch);
    }
}
