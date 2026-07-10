package mcheli.agnostic.sim;

import java.util.List;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.math.MchMath;
import mcheli.agnostic.plane.MCP_PlaneInfo;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.value.MoveResult;
import mcheli.agnostic.value.Vec3d;

/**
 * The plane {@link FlightModel}: bit-faithful port of {@code MCP_EntityPlane}'s {@code onUpdate_Control} +
 * {@code onUpdate_ControlNotHovering} and {@code onUpdate_Server}. Fixed-wing aerodynamics: {@code Rot2Vec3}
 * vectored thrust along the nose (pitch offset −10° for lift, or the VTOL nozzle angle), the
 * {@code -0.047*(1-throttle)} sink, throttle/throttleBack reverse, pivot-turn throttle boost, sweep-wing
 * {@code getMaxSpeed} interpolation, buoyancy branches, the {@code currentSpeed}/35 spool, on-ground
 * {@code motionFactor} damping + attitude trim, and the post-move {@code 0.95}(y)/{@code motionFactor}(x,z) drag
 * (NOTE: plane horizontal drag is {@code motionFactor}, not {@code 0.99} like the vehicle/heli).
 *
 * <p>Deferred (TODO, niche): the {@code isTargetDrone} ground-avoidance auto-steer in {@code onUpdate_Server}
 * (needs a landing-gear seam) — target drones currently fly with normal physics + forced throttle.
 */
public final class PlaneFlightModel implements FlightModel {

    @Override
    public void updateThrottle(EntityRef self, MCH_AircraftInfo info, AircraftSimState st, ControlInput in, AircraftState mod) {
        PlaneState p = (PlaneState) mod;

        if (p.isGunnerMode() && !p.canUseFuel()) {
            p.switchGunnerMode(false);
        }

        st.throttleBack = (float) (st.throttleBack * 0.8);

        List<EntityRef> riders = self.passengers();
        boolean liveRider = !riders.isEmpty() && !riders.get(0).isDead();
        if (liveRider && p.isCanopyClose() && p.canUseWing() && p.canUseFuel() && !p.isDestroyed()) {
            controlNotHovering(self, info, st, in, p);
        } else if (p.isTargetDrone() && p.canUseFuel() && !p.isDestroyed()) {
            controlNotHovering(self, info, st, in.withThrottleUp(), p);
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

    private void controlNotHovering(EntityRef self, MCH_AircraftInfo info, AircraftSimState st, ControlInput in, PlaneState p) {
        if (p.isGunnerMode()) {
            return;
        }
        float throttleUpDown = info.throttleUpDown;
        boolean turn = in.moveLeft() && !in.moveRight() || !in.moveLeft() && in.moveRight();
        boolean localThrottleUp = in.throttleUp();
        if (turn && st.getCurrentThrottle() < info.pivotTurnThrottle && !localThrottleUp && !in.throttleDown()) {
            localThrottleUp = true;
            throttleUpDown *= 2.0F;
        }

        if (localThrottleUp) {
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
        } else if (in.autoThrottleDown() && st.getCurrentThrottle() > 0.0) { // cs_planeAutoThrottleDown
            st.addCurrentThrottle(-0.005 * throttleUpDown);
            if (st.getCurrentThrottle() <= 0.0) {
                st.setCurrentThrottle(0.0);
            }
        }
    }

    @Override
    public Vec3d integrateForces(EntityRef self, MCH_AircraftInfo info, AircraftSimState st, ControlInput in, AircraftState mod) {
        PlaneState p = (PlaneState) mod;
        MCP_PlaneInfo pi = (MCP_PlaneInfo) info;
        Vec3d m = self.motion();
        double motionX = m.x();
        double motionY = m.y();
        double motionZ = m.z();

        double prevMotion = Math.sqrt(motionX * motionX + motionZ * motionZ);
        double dp = 0.0;
        boolean canFloatWater = info.isFloat && !p.isDestroyed();
        if (canFloatWater) {
            dp = Buoyancy.waterDepth(self, info);
        }

        boolean levelOff = p.isGunnerMode();
        if (dp == 0.0) {
            // TODO(drone-autopilot): the isTargetDrone ground-avoidance auto-steer (getBlockY(3,-40)/(3,-5),
            // autoPilotRot, foldLandingGear) is deferred — needs a landing-gear seam. Drones fly normal physics.
            if (!levelOff) {
                motionY = motionY + (0.04 + (!self.isInWater() ? info.gravity : info.gravityInWater));
                motionY = motionY + -0.047 * (1.0 - st.getCurrentThrottle());
            } else {
                motionY *= 0.8;
            }
        } else {
            self.setRotation(self.yaw(), self.pitch() * 0.8F);
            if (Math.abs(self.roll()) < 40.0F) {
                self.setRoll(self.roll() * 0.9F);
            }

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
        float nozzle = p.getNozzleRotation();
        Vec3d v;
        if (nozzle > 0.001F) {
            self.setRotation(self.yaw(), self.pitch() * 0.95F);
            v = rot2Vec3(self.yaw(), self.pitch() - nozzle);
            if (nozzle >= 90.0F) {
                v = new Vec3d(v.x() * 0.8F, v.y(), v.z() * 0.8F);
            }
        } else {
            v = rot2Vec3(self.yaw(), self.pitch() - 10.0F);
        }

        if (!levelOff) {
            if (nozzle <= 0.01F) {
                motionY = motionY + v.y() * throttle / 2.0;
            } else {
                motionY = motionY + v.y() * throttle / 8.0;
            }
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
        float speedLimit = maxSpeed(pi, p);
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
            if (Math.abs(self.pitch()) < 40.0F) {
                applyOnGroundPitch(self, info, 0.8F);
            }
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

        if (mv.blockedX()) motionX = 0.0;
        if (mv.blockedY()) motionY = 0.0;
        if (mv.blockedZ()) motionZ = 0.0;

        motionY *= 0.95;
        motionX = motionX * info.motionFactor; // plane horizontal drag is motionFactor, not 0.99
        motionZ = motionZ * info.motionFactor;
        self.setMotion(new Vec3d(motionX, motionY, motionZ));
    }

    /** Reference getMaxSpeed: sweep-wing interpolation between speed and sweepWingSpeed by the wing factor. */
    private static float maxSpeed(MCP_PlaneInfo pi, PlaneState p) {
        float f = 0.0F;
        if (p.hasVariableSweepPart() && pi.isVariableSweepWing) {
            f = (pi.sweepWingSpeed - pi.speed) * p.sweepPartFactor();
        }
        return pi.speed + f;
    }

    /** Reference applyOnGroundPitch(factor): damp pitch toward onGroundPitch AND roll by the factor. */
    private static void applyOnGroundPitch(EntityRef self, MCH_AircraftInfo info, float factor) {
        float ogp = info.onGroundPitch;
        float pitch = self.pitch();
        pitch -= ogp;
        pitch *= factor;
        pitch += ogp;
        self.setRotation(self.yaw(), pitch);
        self.setRoll(self.roll() * factor);
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
