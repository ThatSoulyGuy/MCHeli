package mcheli.agnostic.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import mcheli.agnostic.plane.MCP_PlaneInfo;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.spi.RandomSource;
import mcheli.agnostic.spi.Role;
import mcheli.agnostic.spi.SyncedData;
import mcheli.agnostic.spi.WorldView;
import mcheli.agnostic.util.MCH_LowPassFilterFloat;
import mcheli.agnostic.value.AABB;
import mcheli.agnostic.value.MoveResult;
import mcheli.agnostic.value.RayHit;
import mcheli.agnostic.value.Vec3d;
import org.junit.jupiter.api.Test;

/**
 * Behavioural trace tests for {@link PlaneControlMapping} via {@link RotationSolver#applyControl}: mouse pitch/roll/yaw,
 * flight-sim keyboard rudder (yaw from {@code addkeyRotValue}), and in-flight keyboard roll. The matrix-compose
 * arithmetic itself is covered by {@link RotationSolverTest}.
 */
class PlaneControlMappingTest {

    static final class AirWorld implements WorldView {
        @Override public boolean isRemote() { return false; }
        @Override public long totalTime() { return 0L; }
        @Override public int getBlockId(int x, int y, int z) { return 0; }
        @Override public boolean isWater(int x, int y, int z) { return false; }
        @Override public String materialName(int x, int y, int z) { return "air"; }
        @Override public boolean isCollidable(int x, int y, int z) { return false; }
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

    static final class FakePlane implements EntityRef {
        float yaw, pitch, roll;
        private final WorldView world = new AirWorld();
        @Override public float yaw() { return yaw; }
        @Override public float pitch() { return pitch; }
        @Override public float roll() { return roll; }
        @Override public void setRotation(float y, float p) { yaw = y; pitch = p; }
        @Override public void setRoll(float r) { roll = r; }
        @Override public Vec3d position() { return new Vec3d(0.5, 100.0, 0.5); }
        @Override public int ageTicks() { return 100; }
        @Override public WorldView world() { return world; }
        @Override public int id() { return 1; }
        @Override public boolean isSameAs(EntityRef o) { return this == o; }
        @Override public boolean isDead() { return false; }
        @Override public boolean isRemote() { return false; }
        @Override public Role role() { return Role.PLANE; }
        @Override public Vec3d prevPosition() { return position(); }
        @Override public Vec3d motion() { return Vec3d.ZERO; }
        @Override public float prevYaw() { return yaw; }
        @Override public float prevPitch() { return pitch; }
        @Override public float prevRoll() { return roll; }
        @Override public float width() { return 1; }
        @Override public float height() { return 1; }
        @Override public AABB boundingBox() { return new AABB(0, 99, 0, 1, 100, 1); }
        @Override public boolean onGround() { return false; }
        @Override public boolean isInWater() { return false; }
        @Override public void setPosition(Vec3d p) { }
        @Override public void setMotion(Vec3d m) { }
        @Override public void addMotion(Vec3d d) { }
        @Override public MoveResult move(Vec3d d) { throw new UnsupportedOperationException(); }
        @Override public EntityRef vehicle() { return null; }
        @Override public List<EntityRef> passengers() { return List.of(); }
        @Override public SyncedData synced() { throw new UnsupportedOperationException(); }
    }

    static final class FakePlaneState implements PlaneState {
        boolean gunner;
        int vtol;
        float nozzle;
        @Override public boolean isDestroyed() { return false; }
        @Override public boolean isGunnerMode() { return gunner; }
        @Override public boolean isHovering() { return gunner; }
        @Override public boolean isTargetDrone() { return false; }
        @Override public boolean canUseFuel() { return true; }
        @Override public boolean canUseWing() { return true; }
        @Override public boolean isCanopyClose() { return true; }
        @Override public void switchGunnerMode(boolean on) { gunner = on; }
        @Override public float getNozzleRotation() { return nozzle; }
        @Override public int getVtolMode() { return vtol; }
        @Override public boolean hasVariableSweepPart() { return false; }
        @Override public float sweepPartFactor() { return 0.0F; }
    }

    static MCH_LowPassFilterFloat primed(float v) {
        MCH_LowPassFilterFloat lp = new MCH_LowPassFilterFloat(10);
        for (int i = 0; i < 10; i++) {
            lp.put(v);
        }
        return lp;
    }

    static ControlInput in(double stickX, double stickY, boolean left, boolean right, boolean flightSim, float pt) {
        return new ControlInput(stickX, stickY, 0, 0, false, false, left, right, false, false, false, flightSim, false, pt);
    }

    @Test
    void mouseDrivesPitchRollYaw() {
        FakePlane e = new FakePlane();
        MCP_PlaneInfo info = new MCP_PlaneInfo("test_plane"); // vtol off, mobility*=1, limitRotation=false, onGroundPitch=0
        PlaneControlMapping map = new PlaneControlMapping(e, info, new AircraftSimState(), new FakePlaneState());

        // stickX 100 -> yaw 40*0.8*0.06*0.5=0.96 and roll (stickX*0.5=50 clamp 40)*0.8*0.06*0.5=0.96;
        // stickY 100 -> pitch -(40)*0.8*0.06*0.5 = -0.96.
        RotationSolver.applyControl(e, info, in(100, 100, false, false, false, 0.5F), primed(0.5F), map);

        assertEquals(0.96f, e.yaw, 0.25f);
        assertEquals(-0.96f, e.pitch, 0.25f);
        assertTrue(e.roll > 0.6f && e.roll < 1.3f, "banked via mouse ~0.96, was " + e.roll);
    }

    @Test
    void flightSimKeyboardYaw() {
        FakePlane e = new FakePlane();
        MCP_PlaneInfo info = new MCP_PlaneInfo("test_plane");
        AircraftSimState st = new AircraftSimState();
        PlaneControlMapping map = new PlaneControlMapping(e, info, st, new FakePlaneState());

        // Flight-sim mode, D held, no stick: rotationByKey adds 0.2*0.5=0.1 to addkeyRotValue; yaw = (0.1*20)*0.8*0.06*0.5.
        RotationSolver.applyControl(e, info, in(0, 0, false, true, true, 0.5F), primed(0.5F), map);

        assertTrue(e.yaw > 0.01f, "keyboard rudder yawed right, yaw=" + e.yaw);
        assertTrue(st.addkeyRotValue > 0.0f, "rudder accumulator built up, addkeyRotValue=" + st.addkeyRotValue);
    }

    @Test
    void inFlightKeyboardAddsRoll() {
        FakePlane e = new FakePlane();
        MCP_PlaneInfo info = new MCP_PlaneInfo("test_plane");
        PlaneControlMapping map = new PlaneControlMapping(e, info, new AircraftSimState(), new FakePlaneState());

        // Non-flight-sim, in air, D held: onUpdateAngles' rotationByKey + setRoll(+= addkeyRotValue*0.5*mobilityRoll).
        RotationSolver.applyControl(e, info, in(0, 0, false, true, false, 0.5F), primed(0.5F), map);

        assertTrue(e.roll > 0.02f, "keyboard rudder added roll in flight, roll=" + e.roll);
    }
}
