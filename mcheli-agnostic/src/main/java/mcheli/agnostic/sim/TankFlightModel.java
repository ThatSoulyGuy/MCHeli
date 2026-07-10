package mcheli.agnostic.sim;

import java.util.List;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.math.MchMath;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.value.MoveResult;
import mcheli.agnostic.value.Vec3d;

/**
 * The tank {@link FlightModel}: bit-faithful port of {@code MCH_EntityTank}'s {@code onUpdate_Control} +
 * {@code onUpdate_ControlSub} and {@code onUpdate_Server}. Ground-vehicle physics closely mirroring the plane's
 * forward model — {@code Rot2Vec3(yaw, pitch-10)} thrust, {@code throttle/8} lift, {@code -0.047*(1-throttle)}
 * sink, throttleBack reverse, buoyancy branches, the {@code currentSpeed}/35 spool, on-ground {@code motionFactor}
 * damping + {@code applyOnGroundPitch}, post-move {@code 0.95}(y)/{@code motionFactor}(x,z) drag — plus a brake
 * (halves throttleBack + bleeds throttle) and NO pivot-turn boost / VTOL / sweep-wing. {@code getMaxSpeed} is just
 * {@code info.speed}.
 *
 * <p>Deferred (TODO): {@code updateWheels()} = {@code WheelMng.move(...)} — the host wheel-entity collision and
 * terrain-normal attitude (a future {@code WheelTerrainSolver} + wheel-contact seam). The tank body still moves
 * and rests on ground via the collision move; it just doesn't tilt to slopes yet.
 */
public final class TankFlightModel implements FlightModel {

    @Override
    public void updateThrottle(EntityRef self, MCH_AircraftInfo info, AircraftSimState st, ControlInput in, AircraftState mod) {
        TankState t = (TankState) mod;

        if (t.isGunnerMode() && !t.canUseFuel()) {
            t.switchGunnerMode(false);
        }

        st.throttleBack = (float) (st.throttleBack * 0.8);
        if (in.brake()) {
            st.throttleBack = (float) (st.throttleBack * 0.5);
            if (st.getCurrentThrottle() > 0.0) {
                st.addCurrentThrottle(-0.02 * info.throttleUpDown);
            } else {
                st.setCurrentThrottle(0.0);
            }
        }

        List<EntityRef> riders = self.passengers();
        boolean liveRider = !riders.isEmpty() && !riders.get(0).isDead();
        if (liveRider && t.isCanopyClose() && t.canUseFuel() && !t.isDestroyed()) {
            controlSub(self, info, st, in, t);
        } else if (t.isTargetDrone() && t.canUseFuel() && !t.isDestroyed()) {
            controlSub(self, info, st, in.withThrottleUp(), t);
        } else if (st.getCurrentThrottle() > 0.0) {
            st.addCurrentThrottle(-0.0025 * info.throttleUpDown);
        } else {
            st.setCurrentThrottle(0.0);
        }

        if (st.getCurrentThrottle() < 0.0) {
            st.setCurrentThrottle(0.0);
        }
        // (client throttle lerp + server setThrottle publish are host-adapter concerns.)
    }

    private void controlSub(EntityRef self, MCH_AircraftInfo info, AircraftSimState st, ControlInput in, TankState t) {
        if (t.isGunnerMode()) {
            return;
        }
        float throttleUpDown = info.throttleUpDown;
        if (in.throttleUp()) {
            float f = throttleUpDown;
            if (self.vehicle() != null) {
                Vec3d rm = self.vehicle().motion();
                double mx = rm.x();
                double mz = rm.z();
                f *= (float) Math.sqrt(mx * mx + mz * mz) * info.throttleUpDownOnEntity;
            }

            if (info.enableBack && st.throttleBack > 0.0F) {
                st.throttleBack = (float) (st.throttleBack - 0.01 * f);
            } else {
                st.throttleBack = 0.0F;
                if (st.getCurrentThrottle() < 1.0) {
                    st.addCurrentThrottle(0.01 * f);
                } else {
                    st.setCurrentThrottle(1.0);
                }
            }
        } else if (in.throttleDown()) {
            if (st.getCurrentThrottle() > 0.0) {
                st.addCurrentThrottle(-0.01 * throttleUpDown);
            } else {
                st.setCurrentThrottle(0.0);
                if (info.enableBack) {
                    st.throttleBack = (float) (st.throttleBack + 0.0025 * throttleUpDown);
                    if (st.throttleBack > 0.6F) {
                        st.throttleBack = 0.6F;
                    }
                }
            }
        } else if (in.autoThrottleDown() && st.getCurrentThrottle() > 0.0) { // cs_tankAutoThrottleDown
            st.addCurrentThrottle(-0.005 * throttleUpDown);
            if (st.getCurrentThrottle() <= 0.0) {
                st.setCurrentThrottle(0.0);
            }
        }
    }

    @Override
    public Vec3d integrateForces(EntityRef self, MCH_AircraftInfo info, AircraftSimState st, ControlInput in, AircraftState mod) {
        TankState t = (TankState) mod;
        Vec3d m = self.motion();
        double motionX = m.x();
        double motionY = m.y();
        double motionZ = m.z();

        double prevMotion = Math.sqrt(motionX * motionX + motionZ * motionZ);
        double dp = 0.0;
        boolean canFloatWater = info.isFloat && !t.isDestroyed();
        if (canFloatWater) {
            dp = Buoyancy.waterDepth(self, info);
        }

        boolean levelOff = t.isGunnerMode();
        if (dp == 0.0) {
            if (!levelOff) {
                motionY = motionY + (0.04 + (!self.isInWater() ? info.gravity : info.gravityInWater));
                motionY = motionY + -0.047 * (1.0 - st.getCurrentThrottle());
            } else {
                motionY *= 0.8;
            }
        } else {
            // Reference has an empty `if (abs(getRotRoll()) < 40.0F) {}` block here — a no-op (unlike the plane,
            // the tank does NOT damp roll in water). Preserved as a no-op.
            if (dp < 1.0) {
                motionY -= 1.0E-4;
                motionY = motionY + 0.007 * st.getCurrentThrottle();
            } else {
                if (motionY < 0.0) {
                    motionY /= 2.0;
                }

                motionY += 0.007;
            }
        }

        float throttle = (float) (st.getCurrentThrottle() / 10.0);
        Vec3d v = rot2Vec3(self.yaw(), self.pitch() - 10.0F);
        if (!levelOff) {
            motionY = motionY + v.y() * throttle / 8.0;
        }

        boolean canMove = true;
        if (!info.canMoveOnGround) {
            Vec3d pos = self.position();
            if (GroundProbe.solidNonWaterInColumn(self.world(), pos.x(), pos.y(), pos.z(), 3, -2)) {
                canMove = false;
            }
        }

        if (canMove) {
            if (info.enableBack && st.throttleBack > 0.0F) {
                motionX = motionX - v.x() * st.throttleBack;
                motionZ = motionZ - v.z() * st.throttleBack;
            } else {
                motionX = motionX + v.x() * throttle;
                motionZ = motionZ + v.z() * throttle;
            }
        }

        double motion = Math.sqrt(motionX * motionX + motionZ * motionZ);
        float speedLimit = info.speed; // tank getMaxSpeed() = getTankInfo().speed + 0.0F
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

        if (self.onGround() || GroundProbe.blockIdInColumn(self, 1, -2) > 0) {
            motionX = motionX * info.motionFactor;
            motionZ = motionZ * info.motionFactor;
            // NOTE: MCH_EntityTank OVERRIDES applyOnGroundPitch(float) to an EMPTY no-op (unlike the plane, which
            // uses the base damping). So the reference tank's `if (abs(pitch)<40) applyOnGroundPitch(0.8F)`
            // resolves via virtual dispatch to that empty override and does NOTHING to pitch/roll here. Preserved
            // as a no-op — actively damping (as the base does) would feed a wrong pitch into rot2Vec3 next tick.
        }

        // TODO(wheel-terrain): reference calls updateWheels() = WheelMng.move(motion) here (host wheel entities +
        // terrain-normal attitude). Deferred — needs a wheel-contact seam (WheelTerrainSolver). Tank stays level.

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

        if (mv.blockedX()) motionX = 0.0;
        if (mv.blockedY()) motionY = 0.0;
        if (mv.blockedZ()) motionZ = 0.0;

        motionY *= 0.95;
        motionX = motionX * info.motionFactor;
        motionZ = motionZ * info.motionFactor;
        self.setMotion(new Vec3d(motionX, motionY, motionZ));
    }

    /** Reference MCH_Lib.Rot2Vec3(yaw, pitch): unit direction from yaw/pitch using the MC LUT trig. */
    private static Vec3d rot2Vec3(float yaw, float pitch) {
        float yr = yaw / 180.0F * (float) Math.PI;
        float pr = pitch / 180.0F * (float) Math.PI;
        return new Vec3d(
            -MchMath.sin(yr) * MchMath.cos(pr),
            -MchMath.sin(pr),
            MchMath.cos(yr) * MchMath.cos(pr));
    }
}
