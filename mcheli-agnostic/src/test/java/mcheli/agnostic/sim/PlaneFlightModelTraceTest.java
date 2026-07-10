package mcheli.agnostic.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import mcheli.agnostic.plane.MCP_PlaneInfo;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.spi.RandomSource;
import mcheli.agnostic.spi.Role;
import mcheli.agnostic.spi.SyncedData;
import mcheli.agnostic.spi.WorldView;
import mcheli.agnostic.value.AABB;
import mcheli.agnostic.value.MoveResult;
import mcheli.agnostic.value.RayHit;
import mcheli.agnostic.value.Vec3d;
import org.junit.jupiter.api.Test;

/**
 * Trace of {@link PlaneFlightModel} through {@link AircraftFlightController}: verifies fixed-wing aerodynamics
 * reproduce {@code MCP_EntityPlane} — Rot2Vec3 vectored thrust (forward + lift), the currentSpeed spool, the
 * throttle-up curve, and the throttleBack reverse decay. Airborne over open air, no VTOL / no sweep-wing.
 */
class PlaneFlightModelTraceTest {

    static final PlaneFlightModel MODEL = new PlaneFlightModel();

    static final WorldView AIR = new WorldView() {
        public boolean isRemote() { return false; }
        public long totalTime() { return 0; }
        public int getBlockId(int x, int y, int z) { return 0; }
        public boolean isWater(int x, int y, int z) { return false; }
        public String materialName(int x, int y, int z) { return "air"; }
        public boolean isCollidable(int x, int y, int z) { return false; }
        public boolean isAABBInWater(AABB box) { return false; }
        public List<EntityRef> entitiesInAABB(AABB box) { throw new UnsupportedOperationException(); }
        public List<EntityRef> entitiesInAABB(AABB box, Role role) { throw new UnsupportedOperationException(); }
        public RayHit rayTraceBlocks(Vec3d from, Vec3d to) { throw new UnsupportedOperationException(); }
        public RayHit clip(Vec3d from, Vec3d to, boolean stopOnLiquid) { throw new UnsupportedOperationException(); }
        public void spawn(EntityRef entity) { throw new UnsupportedOperationException(); }
        public void spawnParticle(String name, Vec3d pos, Vec3d velocity) { }
        public void playSound(Vec3d pos, String name, float volume, float pitch) { }
        public void playSoundAtEntity(EntityRef entity, String name, float volume, float pitch) { }
        public RandomSource random() { throw new UnsupportedOperationException(); }
    };

    /** Flyable plane mod-state: no VTOL, no sweep. */
    static final class Plane implements PlaneState {
        boolean gunner, drone, destroyed;
        public boolean isDestroyed() { return destroyed; }
        public boolean isGunnerMode() { return gunner; }
        public boolean isTargetDrone() { return drone; }
        public boolean canUseFuel() { return true; }
        public boolean canUseWing() { return true; }
        public boolean isCanopyClose() { return true; }
        public void switchGunnerMode(boolean on) { gunner = on; }
        public float getNozzleRotation() { return 0.0F; }
        public int getVtolMode() { return 0; }
        public boolean hasVariableSweepPart() { return false; }
        public float sweepPartFactor() { return 0.0F; }
    }

    static final class FakeEntity implements EntityRef {
        double px, py, pz, mx, my, mz;
        float yaw, pitch, roll;
        boolean onGround, inWater;
        final List<EntityRef> passengers = new ArrayList<>();

        FakeEntity at(double x, double y, double z) { px = x; py = y; pz = z; return this; }
        FakeEntity withRider() { passengers.add(new FakeEntity()); return this; }

        @Override public Vec3d position() { return new Vec3d(px, py, pz); }
        @Override public Vec3d motion() { return new Vec3d(mx, my, mz); }
        @Override public float yaw() { return yaw; }
        @Override public float pitch() { return pitch; }
        @Override public float roll() { return roll; }
        @Override public boolean onGround() { return onGround; }
        @Override public boolean isInWater() { return inWater; }
        @Override public List<EntityRef> passengers() { return passengers; }
        @Override public WorldView world() { return AIR; }
        @Override public void setPosition(Vec3d p) { px = p.x(); py = p.y(); pz = p.z(); }
        @Override public void setMotion(Vec3d m) { mx = m.x(); my = m.y(); mz = m.z(); }
        @Override public void setRotation(float y, float p) { yaw = y; pitch = p; }
        @Override public void setRoll(float r) { roll = r; }
        @Override public MoveResult move(Vec3d d) {
            px += d.x(); py += d.y(); pz += d.z();
            return new MoveResult(d, onGround, false, false, false, false, false);
        }
        @Override public int id() { return 1; }
        @Override public boolean isSameAs(EntityRef o) { return this == o; }
        @Override public boolean isDead() { return false; }
        @Override public boolean isRemote() { return false; }
        @Override public Role role() { return Role.PLANE; }
        @Override public Vec3d prevPosition() { return position(); }
        @Override public float prevYaw() { return yaw; }
        @Override public float prevPitch() { return pitch; }
        @Override public float prevRoll() { return roll; }
        @Override public float width() { return 1.0F; }
        @Override public float height() { return 1.0F; }
        @Override public AABB boundingBox() { return new AABB(px - 0.5, py, pz - 0.5, px + 0.5, py + 1, pz + 0.5); }
        @Override public int ageTicks() { return 0; }
        @Override public void addMotion(Vec3d d) { mx += d.x(); my += d.y(); mz += d.z(); }
        @Override public EntityRef vehicle() { return null; }
        @Override public SyncedData synced() { throw new UnsupportedOperationException(); }
    }

    static MCP_PlaneInfo info() {
        MCP_PlaneInfo pi = new MCP_PlaneInfo("test_plane");
        pi.speed = 0.8F;
        pi.gravity = -0.04F;
        pi.gravityInWater = -0.04F;
        pi.motionFactor = 0.96F;
        pi.throttleUpDown = 1.0F;
        pi.isFloat = false;
        pi.onGroundPitch = 0.0F;
        pi.isVariableSweepWing = false;
        return pi; // canMoveOnGround=true, enableBack=false, pivotTurnThrottle=0 by default
    }

    static void tick(FakeEntity e, MCP_PlaneInfo pi, AircraftSimState st, ControlInput in, Plane mod) {
        AircraftFlightController.tickServer(e, pi, st, in, mod, MODEL);
    }

    @Test
    void forwardVectoredThrustLiftAndSpool() {
        FakeEntity e = new FakeEntity().at(0, 100, 0).withRider(); // yaw=0, pitch=0
        MCP_PlaneInfo pi = info();
        AircraftSimState st = new AircraftSimState(0.07);
        st.currentThrottle = 1.0;

        tick(e, pi, st, ControlInput.NONE, new Plane());

        assertEquals(0.0, e.mx, 1e-9);                 // yaw 0 -> Rot2Vec3 x = -sin(0)*cos = 0
        assertTrue(e.mz > 0.09, "forward thrust +Z (v.z=cos(-10)*throttle/10), was " + e.mz);
        assertTrue(e.py > 100.0, "vectored-thrust lift (pitch -10 nose-up component) climbs, was " + e.py);
        assertTrue(e.pz > 0.09 && e.pz < 0.11, "advanced ~0.098 in Z, was " + e.pz);
        assertTrue(st.currentSpeed > 0.08, "currentSpeed spooled up, was " + st.currentSpeed);
    }

    @Test
    void throttleUpRaisesThrottle() {
        FakeEntity e = new FakeEntity().at(0, 100, 0).withRider();
        MCP_PlaneInfo pi = info();
        AircraftSimState st = new AircraftSimState(0.07);
        st.currentThrottle = 0.5;

        tick(e, pi, st, ControlInput.ground(true, false, false, false), new Plane()); // throttleUp
        assertEquals(0.51, st.currentThrottle, 1e-9); // +0.01 * throttleUpDown(1)
    }

    @Test
    void throttleBackDecaysEachTick() {
        FakeEntity e = new FakeEntity().at(0, 100, 0).withRider();
        MCP_PlaneInfo pi = info();
        AircraftSimState st = new AircraftSimState(0.07);
        st.throttleBack = 0.5F;
        st.currentThrottle = 0.0;

        tick(e, pi, st, ControlInput.NONE, new Plane());
        assertEquals(0.4F, st.throttleBack, 1e-6); // throttleBack *= 0.8 each tick, no input to change it
    }
}
