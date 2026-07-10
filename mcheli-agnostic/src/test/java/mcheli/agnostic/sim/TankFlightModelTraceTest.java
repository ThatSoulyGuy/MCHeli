package mcheli.agnostic.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.spi.RandomSource;
import mcheli.agnostic.spi.Role;
import mcheli.agnostic.spi.SyncedData;
import mcheli.agnostic.spi.WorldView;
import mcheli.agnostic.tank.MCH_TankInfo;
import mcheli.agnostic.value.AABB;
import mcheli.agnostic.value.MoveResult;
import mcheli.agnostic.value.RayHit;
import mcheli.agnostic.value.Vec3d;
import org.junit.jupiter.api.Test;

/**
 * Trace of {@link TankFlightModel} through {@link AircraftFlightController}: verifies the tank ground physics
 * reproduce {@code MCH_EntityTank} — Rot2Vec3 forward thrust with the {@code throttle/8} lift, the currentSpeed
 * spool, and the brake (halves throttleBack + bleeds throttle). Airborne over open air (wheel terrain-follow deferred).
 */
class TankFlightModelTraceTest {

    static final TankFlightModel MODEL = new TankFlightModel();

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

    static final class Tank implements TankState {
        boolean gunner, drone, destroyed;
        public boolean isDestroyed() { return destroyed; }
        public boolean isGunnerMode() { return gunner; }
        public boolean isTargetDrone() { return drone; }
        public boolean canUseFuel() { return true; }
        public boolean isCanopyClose() { return true; }
        public void switchGunnerMode(boolean on) { gunner = on; }
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
        @Override public Role role() { return Role.TANK; }
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

    static MCH_TankInfo info() {
        MCH_TankInfo ti = new MCH_TankInfo("test_tank");
        ti.speed = 0.4F;
        ti.gravity = -0.04F;
        ti.gravityInWater = -0.04F;
        ti.motionFactor = 0.9F;
        ti.throttleUpDown = 1.0F;
        ti.isFloat = false;
        ti.onGroundPitch = 0.0F;
        return ti;
    }

    static void tick(FakeEntity e, MCH_TankInfo ti, AircraftSimState st, ControlInput in, Tank mod) {
        AircraftFlightController.tickServer(e, ti, st, in, mod, MODEL);
    }

    @Test
    void forwardThrustLiftAndSpool() {
        FakeEntity e = new FakeEntity().at(0, 100, 0).withRider();
        MCH_TankInfo ti = info();
        AircraftSimState st = new AircraftSimState(0.07);
        st.currentThrottle = 1.0;

        tick(e, ti, st, ControlInput.NONE, new Tank());

        assertEquals(0.0, e.mx, 1e-9);
        assertTrue(e.mz > 0.08, "forward thrust +Z, was " + e.mz);
        assertTrue(e.py > 100.0, "small throttle/8 lift climbs, was " + e.py);
        assertTrue(st.currentSpeed > 0.07, "currentSpeed spooled up, was " + st.currentSpeed);
    }

    @Test
    void brakeHalvesThrottleBackAndBleedsThrottle() {
        FakeEntity e = new FakeEntity().at(0, 100, 0).withRider();
        MCH_TankInfo ti = info();
        AircraftSimState st = new AircraftSimState(0.07);
        st.throttleBack = 0.5F;
        st.currentThrottle = 0.5;

        // brake = 5th boolean (throttleUp, throttleDown, moveLeft, moveRight, brake, ...)
        ControlInput braking = new ControlInput(0, 0, 0, 0, false, false, false, false, true, false, false, false, false, 0.0F);
        tick(e, ti, st, braking, new Tank());

        assertEquals(0.2F, st.throttleBack, 1e-6);      // *0.8 then *0.5
        assertEquals(0.48, st.currentThrottle, 1e-9);   // -0.02 * throttleUpDown
    }
}
