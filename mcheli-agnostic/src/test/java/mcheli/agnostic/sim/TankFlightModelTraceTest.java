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

    /** Ground rising one block per four +Z, so a hull sitting across it has a fore/aft grade to solve onto. */
    static final class SlopeWorld implements WorldView {
        static int surface(int z) { return 64 + Math.floorDiv(z, 4); }
        public boolean isRemote() { return false; }
        public long totalTime() { return 0; }
        public int getBlockId(int x, int y, int z) { return y < surface(z) ? 1 : 0; }
        public boolean isWater(int x, int y, int z) { return false; }
        public String materialName(int x, int y, int z) { return getBlockId(x, y, z) != 0 ? "rock" : "air"; }
        public boolean isCollidable(int x, int y, int z) { return getBlockId(x, y, z) != 0; }
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
    }

    static final class FakeEntity implements EntityRef {
        double px, py, pz, mx, my, mz;
        float yaw, pitch, roll;
        boolean onGround, inWater;
        WorldView world = AIR;
        final List<EntityRef> passengers = new ArrayList<>();

        boolean restsOnTerrain;

        FakeEntity at(double x, double y, double z) { px = x; py = y; pz = z; return this; }
        FakeEntity withRider() { passengers.add(new FakeEntity()); return this; }
        FakeEntity in(WorldView w) { world = w; return this; }
        /** Minimal ground collision, so the hull settles on the terrain instead of free-falling through it. */
        FakeEntity restingOnTerrain() { restsOnTerrain = true; return this; }

        @Override public Vec3d position() { return new Vec3d(px, py, pz); }
        @Override public Vec3d motion() { return new Vec3d(mx, my, mz); }
        @Override public float yaw() { return yaw; }
        @Override public float pitch() { return pitch; }
        @Override public float roll() { return roll; }
        @Override public boolean onGround() { return onGround; }
        @Override public boolean isInWater() { return inWater; }
        @Override public List<EntityRef> passengers() { return passengers; }
        @Override public WorldView world() { return world; }
        @Override public void setPosition(Vec3d p) { px = p.x(); py = p.y(); pz = p.z(); }
        @Override public void setMotion(Vec3d m) { mx = m.x(); my = m.y(); mz = m.z(); }
        @Override public void setRotation(float y, float p) { yaw = y; pitch = p; }
        @Override public void setRoll(float r) { roll = r; }
        @Override public MoveResult move(Vec3d d) {
            px += d.x(); py += d.y(); pz += d.z();
            if (restsOnTerrain) {
                int bx = (int) Math.floor(px);
                int bz = (int) Math.floor(pz);
                for (int y = (int) Math.floor(py) + 1; y >= (int) Math.floor(py) - 8; y--) {
                    if (world.isCollidable(bx, y, bz)) {
                        if (py < y + 1.0) { py = y + 1.0; my = 0.0; onGround = true; }
                        break;
                    }
                }
            }
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

    /** m1a2's real wheel/terrain config: 5 axles at x=1.82, plus its OnGroundPitchFactor/OnGroundRollFactor. */
    static MCH_TankInfo slopeInfo() {
        MCH_TankInfo ti = info();
        ti.onGroundPitchFactor = 2.0F;
        ti.onGroundRollFactor = 1.3F;
        ti.weightedCenterZ = 0.0F;
        ti.wheels = new ArrayList<>();
        for (double z : new double[] {2.86, 0.70, -0.76, -2.22, -4.14}) {
            ti.wheels.add(new mcheli.agnostic.aircraft.MCH_AircraftInfo.Wheel(new Vec3d(1.82, -0.24, z)));
        }
        return ti;
    }

    @Test
    void unriddenTankTiltsOntoSlope() {
        // No rider, no control input: the whole point of the fix. The reference ran WheelMng.move() server-side
        // every tick regardless of rider (MCH_EntityTank:718), and the port previously only reached the solver
        // from the local pilot's client render frame — so an abandoned tank floated level on a hillside.
        FakeEntity e = new FakeEntity().at(0.5, 64, 0.5).in(new SlopeWorld()).restingOnTerrain();
        MCH_TankInfo ti = slopeInfo();
        AircraftSimState st = new AircraftSimState(0.07);

        tick(e, ti, st, ControlInput.NONE, new Tank());

        assertTrue(e.pitch != 0.0F, "unridden tank tilted onto the slope, pitch was " + e.pitch);
        assertTrue(Math.abs(e.pitch) <= ti.onGroundPitchFactor + 1e-4F,
            "first tick moves at most OnGroundPitchFactor, was " + e.pitch);
        // Ground rises with +Z and the hull's nose is +Z, so the nose lifts. MCHeli pitch is nose-down-positive
        // (pitch = 90 - atan2(n.y, n.z)), so a nose-up hull reads negative.
        assertTrue(e.pitch < 0.0F, "nose-up on an uphill-to-+Z grade, was " + e.pitch);
    }

    @Test
    void slopeTiltConvergesAndHolds() {
        FakeEntity e = new FakeEntity().at(0.5, 64, 0.5).in(new SlopeWorld()).restingOnTerrain();
        MCH_TankInfo ti = slopeInfo();
        AircraftSimState st = new AircraftSimState(0.07);
        Tank mod = new Tank();

        for (int i = 0; i < 60; i++) {
            tick(e, ti, st, ControlInput.NONE, mod);
        }
        float settled = e.pitch;
        for (int i = 0; i < 20; i++) {
            tick(e, ti, st, ControlInput.NONE, mod);
        }
        // The sampler feeds the hull's own attitude back through transform(), so a bad solve oscillates. It must not.
        assertEquals(settled, e.pitch, 0.5F, "tilt settled and held, " + settled + " -> " + e.pitch);
        assertTrue(Math.abs(settled) > 1.0F, "settled on a real slope angle, was " + settled);
        assertTrue(Math.abs(e.roll) <= 90.0F, "roll stayed clamped, was " + e.roll);
    }

    @Test
    void airborneTankDoesNotSnapVertical() {
        // Regression guard for the zmog forcing (WheelTerrainSampler phase B). With every axle ungrounded the solver's
        // weights all go to zero, rv normalizes to ZERO, and atan2(0,0)==0 would yield a hard pitch=+90/roll=-90.
        // The forcing keeps one fore + one aft axle weighted, so an airborne hull solves to its CURRENT attitude.
        FakeEntity e = new FakeEntity().at(0.5, 200, 0.5).in(new SlopeWorld()); // far above the terrain
        MCH_TankInfo ti = slopeInfo();
        AircraftSimState st = new AircraftSimState(0.07);
        Tank mod = new Tank();

        for (int i = 0; i < 40; i++) {
            tick(e, ti, st, ControlInput.NONE, mod);
        }

        assertTrue(Math.abs(e.pitch) < 5.0F, "airborne hull stayed near level, pitch was " + e.pitch);
        assertTrue(Math.abs(e.roll) < 5.0F, "airborne hull stayed near level, roll was " + e.roll);
    }

    @Test
    void flatGroundStaysLevel() {
        FakeEntity e = new FakeEntity().at(0.5, 64, 0.5).restingOnTerrain().in(new WorldView() {
            public boolean isRemote() { return false; }
            public long totalTime() { return 0; }
            public int getBlockId(int x, int y, int z) { return y < 64 ? 1 : 0; }
            public boolean isWater(int x, int y, int z) { return false; }
            public String materialName(int x, int y, int z) { return "rock"; }
            public boolean isCollidable(int x, int y, int z) { return y < 64; }
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
        });
        MCH_TankInfo ti = slopeInfo();
        AircraftSimState st = new AircraftSimState(0.07);
        Tank mod = new Tank();

        for (int i = 0; i < 20; i++) {
            tick(e, ti, st, ControlInput.NONE, mod);
        }

        assertEquals(0.0F, e.pitch, 1e-3F, "flat ground leaves the hull level");
        assertEquals(0.0F, e.roll, 1e-3F, "flat ground leaves the hull level");
    }
}
