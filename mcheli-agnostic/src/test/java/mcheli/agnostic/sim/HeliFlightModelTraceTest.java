package mcheli.agnostic.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import mcheli.agnostic.helicopter.MCH_HeliInfo;
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
 * Tick-by-tick trace of the {@link HeliFlightModel} through {@link AircraftFlightController}: verifies the
 * collective/cyclic flight model reproduces {@code MCH_EntityHeli} — the {@code pitch^3} cyclic thrust, collective
 * lift beating gravity at full throttle (climb), the {@code currentSpeed} spool that engages for the heli (unlike
 * the ground vehicle), rotor spin, and the not-hovering/hovering throttle curves. Airborne over open air.
 */
class HeliFlightModelTraceTest {

    static final HeliFlightModel MODEL = new HeliFlightModel();

    /** Non-jittering RNG so the (unused-here) hover branch stays deterministic: nextInt never returns 0. */
    static final RandomSource NO_JITTER = new RandomSource() {
        public double nextDouble() { return 0.5; }
        public float nextFloat() { return 0.5F; }
        public int nextInt(int bound) { return 1; }
        public double nextGaussian() { return 0.0; }
        public boolean nextBoolean() { return false; }
    };

    /** All-air world: GroundProbe(3,-3) finds nothing, no water. */
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
        public RandomSource random() { return NO_JITTER; }
    };

    /** Mutable heli mod-state, defaulting to "flyable". */
    static final class Heli implements HeliState {
        boolean hovering, hoveringMode, gunnerMode, destroyed;
        public boolean isDestroyed() { return destroyed; }
        public boolean isHovering() { return hovering; }
        public boolean isHoveringMode() { return hoveringMode; }
        public boolean isGunnerMode() { return gunnerMode; }
        public boolean canUseFuel(boolean c) { return true; }
        public boolean canUseFuel() { return true; }
        public boolean canUseBlades() { return true; }
        public boolean isCanopyClose() { return true; }
        public void switchHoveringMode(boolean on) { hoveringMode = on; }
        public void switchGunnerMode(boolean on) { gunnerMode = on; }
    }

    static final class FakeEntity implements EntityRef {
        double px, py, pz, mx, my, mz;
        float yaw, pitch, roll;
        boolean onGround, inWater;
        final List<EntityRef> passengers = new ArrayList<>();

        FakeEntity at(double x, double y, double z) { px = x; py = y; pz = z; return this; }
        FakeEntity attitude(float y, float p, float r) { yaw = y; pitch = p; roll = r; return this; }
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
        @Override public Role role() { return Role.HELICOPTER; }
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

    static MCH_HeliInfo info() {
        MCH_HeliInfo hi = new MCH_HeliInfo("test_heli");
        hi.speed = 0.35F;
        hi.gravity = -0.04F;
        hi.gravityInWater = -0.04F;
        hi.isFloat = false;
        hi.onGroundPitch = 0.0F;
        hi.throttleUpDown = 1.0F;
        return hi; // rotorSpeed defaults to 79.99 (heli getDefaultRotorSpeed)
    }

    static void tick(FakeEntity e, MCH_HeliInfo hi, AircraftSimState st, ControlInput in, Heli mod) {
        AircraftFlightController.tickServer(e, hi, st, in, mod, MODEL);
    }

    @Test
    void airborneCyclicThrustLiftAndSpool() {
        FakeEntity e = new FakeEntity().at(0, 100, 0).attitude(0, -30, 0).withRider();
        MCH_HeliInfo hi = info();
        AircraftSimState st = new AircraftSimState(0.07);
        st.currentThrottle = 1.0;

        tick(e, hi, st, ControlInput.NONE, new Heli());

        assertEquals(0.0, e.mx, 1e-9);              // yaw 0 -> sin(0)=0 -> no lateral cyclic
        assertTrue(e.mz < 0, "nose-down cyclic pushes -Z, was " + e.mz);
        assertTrue(e.my > 0, "collective lift beats gravity at full throttle -> climbing, was " + e.my);
        assertTrue(e.py > 100.0, "climbs, was " + e.py);
        assertEquals(0.078, st.currentSpeed, 1e-6);  // spools 0.07 -> +(0.35-0.07)/35 (heli DOES spool)
        assertEquals(79.99, st.rotationRotor, 1e-3); // rotor: (1 - (1-throttle)^3)*rotorSpeed = 1*79.99
    }

    @Test
    void notHoveringThrottleUp() {
        FakeEntity e = new FakeEntity().at(0, 100, 0).withRider();
        MCH_HeliInfo hi = info();
        AircraftSimState st = new AircraftSimState(0.07);
        st.currentThrottle = 0.5;

        tick(e, hi, st, ControlInput.ground(true, false, false, false), new Heli()); // throttleUp
        assertEquals(0.52, st.currentThrottle, 1e-9); // +0.02 * throttleUpDown(1)
    }

    @Test
    void hoveringThrottleClimbsFaster() {
        FakeEntity e = new FakeEntity().at(0, 100, 0).withRider();
        MCH_HeliInfo hi = info();
        AircraftSimState st = new AircraftSimState(0.07);
        st.currentThrottle = 0.5;
        Heli mod = new Heli();
        mod.hovering = true;

        tick(e, hi, st, ControlInput.NONE, mod);
        assertEquals(0.5 + 0.03333333333333333, st.currentThrottle, 1e-12); // hovering collective climb
    }

    @Test
    void riderlessThrottleDecays() {
        FakeEntity e = new FakeEntity().at(0, 100, 0); // no rider, not hovering-mode -> control gate fails
        MCH_HeliInfo hi = info();
        AircraftSimState st = new AircraftSimState(0.07);
        st.currentThrottle = 0.5;

        tick(e, hi, st, ControlInput.NONE, new Heli());
        assertEquals(0.49875, st.currentThrottle, 1e-12); // 0.5 - 0.00125
    }
}
