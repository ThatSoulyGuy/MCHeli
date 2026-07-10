package mcheli.agnostic.sim;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.value.MoveResult;
import mcheli.agnostic.value.Vec3d;
import mcheli.agnostic.vehicle.MCH_VehicleInfo;

/**
 * The simplest {@link FlightModel}: the ground vehicle. Bit-faithful port of {@code MCH_EntityVehicle}'s
 * {@code onUpdate_Control}/{@code onUpdate_ControlOnGround} and {@code onUpdate_Server} — plain gravity (or
 * buoyancy if it floats), a directional 0.03 thrust nudge from forward/back input, 0.5-deg/tick steering, a
 * horizontal speed clamp to {@code info.speed}, the {@code currentSpeed}/35 spool envelope, on-ground half-speed
 * friction, and the {@code 0.95}/{@code 0.99} post-move drag. Operand types (float {@code info} fields vs double
 * motion) are preserved exactly so trajectories match the reference tick-for-tick.
 */
public final class VehicleFlightModel implements FlightModel {

    @Override
    public void updateThrottle(EntityRef self, MCH_AircraftInfo info, AircraftSimState st, ControlInput in, AircraftState mod) {
        MCH_VehicleInfo vi = (MCH_VehicleInfo) info;
        // Reference: control runs only when `riddenByEntity != null && !riddenByEntity.isDead`; otherwise the
        // throttle decays. Preserve BOTH halves of that guard (a dead-but-still-attached pilot must not drive
        // control) rather than assume the host's passengers() already drops dead riders.
        java.util.List<EntityRef> riders = self.passengers();
        boolean hasRider = !riders.isEmpty() && !riders.get(0).isDead();

        if (!hasRider) {
            if (st.getCurrentThrottle() > 0.0) {
                st.addCurrentThrottle(-0.00125);
            } else {
                st.setCurrentThrottle(0.0);
            }
        } else if (vi.isEnableMove || vi.isEnableRot) {
            controlOnGround(self, vi, in);
        }

        if (st.getCurrentThrottle() < 0.0) {
            st.setCurrentThrottle(0.0);
        }
        // Server also calls setThrottle(currentThrottle) to publish the synced value; that DataWatcher write is
        // a host-adapter concern (client display/interpolation) and does not affect the server trajectory.
    }

    private void controlOnGround(EntityRef self, MCH_VehicleInfo vi, ControlInput in) {
        boolean move = false;
        double x = 0.0;
        double z = 0.0;

        if (vi.isEnableMove) {
            if (in.throttleUp()) {
                float yaw = self.yaw();
                x += Math.sin(yaw * Math.PI / 180.0);
                z += Math.cos(yaw * Math.PI / 180.0);
                move = true;
            }

            if (in.throttleDown()) {
                float yaw = self.yaw() - 180.0F;
                x += Math.sin(yaw * Math.PI / 180.0);
                z += Math.cos(yaw * Math.PI / 180.0);
                move = true;
            }
        }

        if (vi.isEnableMove) {
            if (in.moveLeft() && !in.moveRight()) {
                self.setRotation((float) (self.yaw() - 0.5), self.pitch());
            }

            if (in.moveRight() && !in.moveLeft()) {
                self.setRotation((float) (self.yaw() + 0.5), self.pitch());
            }
        }

        if (move) {
            double d = Math.sqrt(x * x + z * z);
            Vec3d m = self.motion();
            self.setMotion(new Vec3d(m.x() - x / d * 0.03F, m.y(), m.z() + z / d * 0.03F));
        }
    }

    @Override
    public Vec3d integrateForces(EntityRef self, MCH_AircraftInfo info, AircraftSimState st, ControlInput in, AircraftState mod) {
        // onUpdateAircraft, before onUpdate_Server: damp pitch toward level while submerged.
        if (self.isInWater()) {
            self.setRotation(self.yaw(), self.pitch() * 0.9F);
        }

        Vec3d m = self.motion();
        double motionX = m.x();
        double motionY = m.y();
        double motionZ = m.z();

        double prevMotion = Math.sqrt(motionX * motionX + motionZ * motionZ);
        double dp = 0.0;
        boolean canFloatWater = info.isFloat && !mod.isDestroyed();
        if (canFloatWater) {
            dp = Buoyancy.waterDepth(self, info);
        }

        if (dp == 0.0) {
            motionY = motionY + (!self.isInWater() ? info.gravity : info.gravityInWater);
        } else if (dp < 1.0) {
            motionY -= 1.0E-4;
            motionY = motionY + 0.007 * st.getCurrentThrottle();
        } else {
            if (motionY < 0.0) {
                motionY /= 2.0;
            }

            motionY += 0.007;
        }

        double motion = Math.sqrt(motionX * motionX + motionZ * motionZ);
        float speedLimit = info.speed;
        if (motion > speedLimit) {
            motionX *= speedLimit / motion;
            motionZ *= speedLimit / motion;
            motion = speedLimit;
        }

        if (motion > prevMotion && st.currentSpeed < speedLimit) {
            st.currentSpeed = st.currentSpeed + (speedLimit - st.currentSpeed) / 35.0;
            if (st.currentSpeed > speedLimit) {
                st.currentSpeed = speedLimit;
            }
        } else {
            st.currentSpeed = st.currentSpeed - (st.currentSpeed - 0.07) / 35.0;
            if (st.currentSpeed < 0.07) {
                st.currentSpeed = 0.07;
            }
        }

        if (self.onGround()) {
            motionX *= 0.5;
            motionZ *= 0.5;
        }

        Vec3d delta = new Vec3d(motionX, motionY, motionZ);
        self.setMotion(delta);
        return delta;
    }

    @Override
    public void postMoveDamp(EntityRef self, MCH_AircraftInfo info, AircraftSimState st, MoveResult mv) {
        Vec3d m = self.motion();
        double motionX = m.x();
        double motionY = m.y();
        double motionZ = m.z();

        // The reference's moveEntity zeroed a motion component when that axis was blocked by collision.
        if (mv.blockedX()) motionX = 0.0;
        if (mv.blockedY()) motionY = 0.0;
        if (mv.blockedZ()) motionZ = 0.0;

        motionY *= 0.95;
        motionX *= 0.99;
        motionZ *= 0.99;
        self.setMotion(new Vec3d(motionX, motionY, motionZ));
    }
}
