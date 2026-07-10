package mcheli.agnostic.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.spi.Role;
import mcheli.agnostic.spi.SyncedData;
import mcheli.agnostic.spi.WorldView;
import mcheli.agnostic.util.MCH_LowPassFilterFloat;
import mcheli.agnostic.value.AABB;
import mcheli.agnostic.value.MoveResult;
import mcheli.agnostic.value.Vec3d;
import mcheli.agnostic.vehicle.MCH_VehicleInfo;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link RotationSolver#applyControl} — the shared setAngles matrix compose: a clamped, scaled control
 * value composed with current orientation into new yaw/pitch/roll, the limitRotation min/max clamps, and the
 * free-look suppression. Uses a stub {@link RotationSolver.ControlMapping} so the per-vehicle mouse mapping is
 * isolated out; the matrix math runs through the agnostic MCH_Math (LUT trig).
 */
class RotationSolverTest {

    /** Rotation-only fake entity. */
    static final class FakeEntity implements EntityRef {
        float yaw, pitch, roll;
        @Override public float yaw() { return yaw; }
        @Override public float pitch() { return pitch; }
        @Override public float roll() { return roll; }
        @Override public void setRotation(float y, float p) { yaw = y; pitch = p; }
        @Override public void setRoll(float r) { roll = r; }
        // unused
        @Override public int id() { return 1; }
        @Override public boolean isSameAs(EntityRef o) { return this == o; }
        @Override public boolean isDead() { return false; }
        @Override public boolean isRemote() { return false; }
        @Override public Role role() { return Role.PLANE; }
        @Override public Vec3d position() { return Vec3d.ZERO; }
        @Override public Vec3d prevPosition() { return Vec3d.ZERO; }
        @Override public Vec3d motion() { return Vec3d.ZERO; }
        @Override public float prevYaw() { return yaw; }
        @Override public float prevPitch() { return pitch; }
        @Override public float prevRoll() { return roll; }
        @Override public float width() { return 1; }
        @Override public float height() { return 1; }
        @Override public AABB boundingBox() { return new AABB(0, 0, 0, 1, 1, 1); }
        @Override public boolean onGround() { return false; }
        @Override public boolean isInWater() { return false; }
        @Override public int ageTicks() { return 0; }
        @Override public void setPosition(Vec3d p) { }
        @Override public void setMotion(Vec3d m) { }
        @Override public void addMotion(Vec3d d) { }
        @Override public MoveResult move(Vec3d d) { throw new UnsupportedOperationException(); }
        @Override public EntityRef vehicle() { return null; }
        @Override public List<EntityRef> passengers() { return List.of(); }
        @Override public SyncedData synced() { throw new UnsupportedOperationException(); }
        @Override public WorldView world() { throw new UnsupportedOperationException(); }
    }

    /** A KEY-driven mapping: raw values are fixed, ignoring the mouse stick — models the reference control paths
     *  (e.g. plane FlightSim key-yaw) that free-look must NOT suppress. */
    static RotationSolver.ControlMapping mapping(float ry, float rp, float rr) {
        return new RotationSolver.ControlMapping() {
            public float rawYaw(ControlInput in, float pt) { return ry; }
            public float rawPitch(ControlInput in, float pt) { return rp; }
            public float rawRoll(ControlInput in, float pt) { return rr; }
            public float yawFactor() { return 1.0F; }
            public float pitchFactor() { return 1.0F; }
            public float rollFactor() { return 1.0F; }
            public boolean canUpdateYaw() { return true; }
            public boolean canUpdatePitch() { return true; }
            public boolean canUpdateRoll() { return true; }
            public void onUpdateAngles(EntityRef self, MCH_AircraftInfo info, float pt) { }
        };
    }

    /** A MOUSE-driven mapping: raw values come straight from the stick position, so free-look (which zeroes the
     *  stick on the input) yields 0 — models the reference getControlRot* paths that read the mouse. */
    static RotationSolver.ControlMapping stickMapping() {
        return new RotationSolver.ControlMapping() {
            public float rawYaw(ControlInput in, float pt) { return (float) in.stickX(); }
            public float rawPitch(ControlInput in, float pt) { return (float) in.stickY(); }
            public float rawRoll(ControlInput in, float pt) { return 0.0F; }
            public float yawFactor() { return 1.0F; }
            public float pitchFactor() { return 1.0F; }
            public float rollFactor() { return 1.0F; }
            public boolean canUpdateYaw() { return true; }
            public boolean canUpdatePitch() { return true; }
            public boolean canUpdateRoll() { return true; }
            public void onUpdateAngles(EntityRef self, MCH_AircraftInfo info, float pt) { }
        };
    }

    static MCH_LowPassFilterFloat primed(float v) {
        MCH_LowPassFilterFloat lp = new MCH_LowPassFilterFloat(10);
        for (int i = 0; i < 10; i++) {
            lp.put(v);
        }
        return lp;
    }

    static ControlInput input(float partialTicks, boolean freeLook, double stickX, double stickY) {
        return new ControlInput(stickX, stickY, 0, 0, false, false, false, false, false, freeLook, false, false, false, partialTicks);
    }

    @Test
    void pureYawControlRotatesYaw() {
        FakeEntity e = new FakeEntity(); // all angles 0
        MCH_VehicleInfo info = new MCH_VehicleInfo("test"); // mobility* = 1, limitRotation = false

        RotationSolver.applyControl(e, info, input(0.5F, false, 100, 0), primed(0.5F), stickMapping());

        // stickX 100 -> clamp to 40 -> * yawFactor(1) * 0.06 * partialTicks(0.5) = 1.2 deg, composed with yaw 0
        assertEquals(1.2f, e.yaw, 0.1f);
        assertEquals(0.0f, e.pitch, 0.05f);
        assertEquals(0.0f, e.roll, 0.05f);
    }

    @Test
    void limitRotationClampsPitch() {
        FakeEntity e = new FakeEntity();
        MCH_VehicleInfo info = new MCH_VehicleInfo("test");
        info.limitRotation = true;
        info.minRotationPitch = -0.5F;
        info.maxRotationPitch = 0.5F;

        // stickY 1000 -> clamp to 40 -> pitch delta = -40*0.06*0.5 = -1.2 deg -> composed euler pitch ~ -1.2,
        // then limitRotation clamps to minRotationPitch -0.5
        RotationSolver.applyControl(e, info, input(0.5F, false, 0, 1000), primed(0.5F), stickMapping());

        assertEquals(-0.5f, e.pitch, 0.05f);
    }

    @Test
    void freeLookZeroesMouseStickButStillCallsMapping() {
        FakeEntity e = new FakeEntity();
        MCH_VehicleInfo info = new MCH_VehicleInfo("test");

        // A mouse-reading mapping: free-look zeroes stickX/stickY on the INPUT, so the mapping — invoked as
        // usual — reads 0 and produces no rotation. (Reference zeroes only the stick args, not the mapping output.)
        RotationSolver.applyControl(e, info, input(0.5F, true, 100, 100), primed(0.5F), stickMapping());

        assertEquals(0.0f, e.yaw, 0.01f);
        assertEquals(0.0f, e.pitch, 0.01f);
        assertEquals(0.0f, e.roll, 0.01f);
    }

    @Test
    void freeLookStillRunsKeyDrivenControl() {
        FakeEntity e = new FakeEntity();
        MCH_VehicleInfo info = new MCH_VehicleInfo("test");

        // THE regression the fix targets: a KEY-driven mapping (e.g. plane FlightSim key-yaw) ignores the stick,
        // so free-look must NOT suppress it. With output-zeroing (the bug) this would stay at 0; with input-zeroing
        // the mapping still runs and yaw advances exactly as if free-look were off.
        RotationSolver.applyControl(e, info, input(0.5F, true, 0, 0), primed(0.5F), mapping(100, 0, 0));

        assertEquals(1.2f, e.yaw, 0.1f);
        assertEquals(0.0f, e.pitch, 0.05f);
        assertEquals(0.0f, e.roll, 0.05f);
    }
}
