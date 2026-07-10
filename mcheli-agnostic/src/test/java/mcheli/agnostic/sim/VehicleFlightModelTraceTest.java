package mcheli.agnostic.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.spi.Role;
import mcheli.agnostic.spi.SyncedData;
import mcheli.agnostic.spi.WorldView;
import mcheli.agnostic.value.AABB;
import mcheli.agnostic.value.MoveResult;
import mcheli.agnostic.value.Vec3d;
import mcheli.agnostic.vehicle.MCH_VehicleInfo;
import org.junit.jupiter.api.Test;

/**
 * Tick-by-tick trajectory trace of the ported {@link VehicleFlightModel} through {@link AircraftFlightController},
 * driven by a {@link FakeEntity} with no collision/water — verifying the ground-vehicle force integration
 * (directional thrust nudge, gravity, speed clamp, currentSpeed envelope, 0.95/0.99 drag, steering, throttle
 * decay) reproduces the reference {@code MCH_EntityVehicle} arithmetic. Bit-exactness of operand promotions is
 * additionally covered by the adversarial fidelity review; this asserts the physics is numerically correct.
 */
class VehicleFlightModelTraceTest {

    static final AircraftState ALIVE = () -> false; // not destroyed
    static final VehicleFlightModel MODEL = new VehicleFlightModel();

    /** Mutable EntityRef with no collision: move() applies the full delta and reports no blocking. */
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
        @Override public void setPosition(Vec3d p) { px = p.x(); py = p.y(); pz = p.z(); }
        @Override public void setMotion(Vec3d m) { mx = m.x(); my = m.y(); mz = m.z(); }
        @Override public void setRotation(float y, float p) { yaw = y; pitch = p; }
        @Override public void setRoll(float r) { roll = r; }
        @Override public MoveResult move(Vec3d d) {
            px += d.x(); py += d.y(); pz += d.z(); // no collision, motion field unchanged (matches Entity.move)
            return new MoveResult(d, onGround, false, false, false, false, false);
        }

        // unused by the ground-vehicle scenario
        @Override public int id() { return 1; }
        @Override public boolean isSameAs(EntityRef o) { return this == o; }
        @Override public boolean isDead() { return false; }
        @Override public boolean isRemote() { return false; }
        @Override public Role role() { return Role.VEHICLE; }
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
        @Override public WorldView world() { throw new UnsupportedOperationException(); }
    }

    static MCH_VehicleInfo info() {
        MCH_VehicleInfo vi = new MCH_VehicleInfo("test_vehicle");
        vi.speed = 0.4F;
        vi.gravity = -0.04F;
        vi.gravityInWater = -0.04F;
        vi.isFloat = false;
        vi.isEnableMove = true;
        return vi;
    }

    static void tick(FakeEntity e, MCH_VehicleInfo vi, AircraftSimState st, ControlInput in) {
        AircraftFlightController.tickServer(e, vi, st, in, ALIVE, MODEL);
    }

    @Test
    void tickOneForwardTrajectory() {
        FakeEntity e = new FakeEntity().at(0, 100, 0).withRider();
        MCH_VehicleInfo vi = info();
        AircraftSimState st = new AircraftSimState(0.07);

        tick(e, vi, st, ControlInput.ground(true, false, false, false)); // throttle forward

        // control adds ~+0.03 to motionZ; gravity -0.04 to motionY; then move; then *0.95 (y) / *0.99 (xz)
        assertEquals(0.0, e.px, 1e-9);
        assertEquals(99.96, e.py, 1e-6);   // 100 + (-0.04)
        assertEquals(0.03, e.pz, 1e-6);    // +cos(0)*0.03
        assertEquals(0.0, e.mx, 1e-9);
        assertEquals(-0.038, e.my, 1e-6);  // -0.04 * 0.95
        assertEquals(0.0297, e.mz, 1e-6);  // 0.03 * 0.99
        assertEquals(0.0, st.currentThrottle, 1e-12); // ground vehicle thrust does not raise throttle
        assertEquals(0.07, st.currentSpeed, 1e-12);   // motion == prevMotion -> stays at idle
    }

    @Test
    void cruiseClampsToSpeedAndMovesForward() {
        FakeEntity e = new FakeEntity().at(0, 100, 0).withRider();
        MCH_VehicleInfo vi = info();
        AircraftSimState st = new AircraftSimState(0.07);
        ControlInput fwd = ControlInput.ground(true, false, false, false);

        double prevZ = 0;
        for (int i = 0; i < 200; i++) {
            tick(e, vi, st, fwd);
            assertTrue(e.pz > prevZ, "should keep advancing +Z");
            prevZ = e.pz;
        }
        // horizontal speed clamps at info.speed (0.4); post-damp keeps |motionZ| just under it
        double horiz = Math.hypot(e.mx, e.mz);
        assertTrue(horiz <= 0.4 + 1e-9, "clamped to speedLimit");
        assertTrue(horiz > 0.35, "cruising near speedLimit, was " + horiz);
        assertTrue(e.py < 100, "airborne with no ground -> falls");
        // currentSpeed never spools for the ground vehicle: onUpdate_Server samples prevMotion AFTER the control
        // nudge, and motionX/Z are unchanged before the `motion` re-sample, so `motion > prevMotion` is never true
        // (the clamp only makes motion SMALLER). This reference quirk leaves currentSpeed pinned at idle 0.07.
        assertEquals(0.07, st.currentSpeed, 1e-12);
    }

    @Test
    void steeringYawsHalfDegreePerTick() {
        FakeEntity e = new FakeEntity().at(0, 100, 0).withRider();
        MCH_VehicleInfo vi = info();
        AircraftSimState st = new AircraftSimState(0.07);
        ControlInput left = ControlInput.ground(false, false, true, false);

        for (int i = 0; i < 4; i++) {
            tick(e, vi, st, left);
        }
        assertEquals(-2.0f, e.yaw, 1e-4); // -0.5 deg/tick * 4
    }

    @Test
    void riderlessThrottleDecays() {
        FakeEntity e = new FakeEntity().at(0, 100, 0); // no rider
        MCH_VehicleInfo vi = info();
        AircraftSimState st = new AircraftSimState(0.07);
        st.currentThrottle = 0.5;

        tick(e, vi, st, ControlInput.NONE);
        assertEquals(0.49875, st.currentThrottle, 1e-12); // 0.5 - 0.00125
    }
}
