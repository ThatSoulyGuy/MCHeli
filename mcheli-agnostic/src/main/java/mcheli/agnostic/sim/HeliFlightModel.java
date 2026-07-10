package mcheli.agnostic.sim;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.math.MchMath;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.spi.RandomSource;
import mcheli.agnostic.value.MoveResult;
import mcheli.agnostic.value.Vec3d;

/**
 * The helicopter {@link FlightModel}: bit-faithful port of {@code MCH_EntityHeli}'s {@code onUpdate_Control}
 * (+ {@code onUpdate_ControlNotHovering}/{@code onUpdate_ControlHovering}) and {@code onUpdate_Server}. Encodes
 * the collective/cyclic flight model: gravity, {@code pitch^3/30000} cyclic thrust scaled by {@code currentSpeed}
 * and throttle, collective lift with a {@code |pitch|+|roll|} falloff, ground-effect pitch trim (via
 * {@link GroundProbe}), buoyancy branches (via {@link Buoyancy}), hover jitter, the {@code currentSpeed}/35 spool
 * envelope (which — unlike the ground vehicle — DOES engage, since cyclic thrust is added after {@code prevMotion}
 * is sampled), on-ground friction + attitude damping, and the {@code 0.95}/{@code 0.99} post-move drag. Uses the
 * MC-faithful LUT trig ({@link MchMath#sin}/{@link MchMath#cos}); operand types are preserved exactly.
 *
 * <p>Deferred (edge cases, marked TODO): fold-blade-on-ground control, and the client throttle interpolation
 * (server-authoritative only). The synced-throttle publish stays a host-adapter concern.
 */
public final class HeliFlightModel implements FlightModel {

    @Override
    public void updateThrottle(EntityRef self, MCH_AircraftInfo info, AircraftSimState st, ControlInput in, AircraftState mod) {
        HeliState h = (HeliState) mod;

        if (h.isHoveringMode() && !h.canUseFuel(true)) {
            h.switchHoveringMode(false);
        }
        if (h.isGunnerMode() && !h.canUseFuel()) {
            h.switchGunnerMode(false);
        }

        boolean hasRider = !self.passengers().isEmpty();
        if (!h.isDestroyed() && (hasRider || h.isHoveringMode()) && h.canUseBlades() && h.isCanopyClose() && h.canUseFuel(true)) {
            if (!h.isHovering()) {
                controlNotHovering(self, info, st, in);
            } else {
                controlHovering(self, info, st, in);
            }
        } else {
            if (st.getCurrentThrottle() > 0.0) {
                st.addCurrentThrottle(-0.00125);
            } else {
                st.setCurrentThrottle(0.0);
            }
            // TODO(post-slice): onUpdate_ControlFoldBladeAndOnGround (needs foldBladeStat/rotor-count seam).
        }
        // (client throttle lerp + server setThrottle publish are host-adapter concerns.)

        if (st.getCurrentThrottle() < 0.0) {
            st.setCurrentThrottle(0.0);
        }

        // Rotor spin (cosmetic sim output; rendering stays host-side).
        st.prevRotationRotor = st.rotationRotor;
        float rp = (float) (1.0 - st.getCurrentThrottle());
        st.rotationRotor = st.rotationRotor + (1.0F - rp * rp * rp) * info.rotorSpeed;
        st.rotationRotor %= 360.0;
    }

    private void controlNotHovering(EntityRef self, MCH_AircraftInfo info, AircraftSimState st, ControlInput in) {
        float throttleUpDown = info.throttleUpDown;
        if (in.throttleUp()) {
            if (st.getCurrentThrottle() < 1.0) {
                st.addCurrentThrottle(0.02 * throttleUpDown);
            } else {
                st.setCurrentThrottle(1.0);
            }
        } else if (in.throttleDown()) {
            if (st.getCurrentThrottle() > 0.0) {
                st.addCurrentThrottle(-0.014285714285714285 * throttleUpDown);
            } else {
                st.setCurrentThrottle(0.0);
            }
        } else if (in.autoThrottleDown()) { // server: (!isRemote || isClientPlayer) && cs_heliAutoThrottleDown
            if (st.getCurrentThrottle() > 0.52) {
                st.addCurrentThrottle(-0.01 * throttleUpDown);
            } else if (st.getCurrentThrottle() < 0.48) {
                st.addCurrentThrottle(0.01 * throttleUpDown);
            }
        }

        boolean move = false;
        double x = 0.0;
        double z = 0.0;
        if (in.moveLeft() && !in.moveRight()) {
            float yaw = self.yaw() - 90.0F;
            x += Math.sin(yaw * Math.PI / 180.0);
            z += Math.cos(yaw * Math.PI / 180.0);
            move = true;
        }
        if (in.moveRight() && !in.moveLeft()) {
            float yaw = self.yaw() + 90.0F;
            x += Math.sin(yaw * Math.PI / 180.0);
            z += Math.cos(yaw * Math.PI / 180.0);
            move = true;
        }

        if (move) {
            double f = 1.0;
            double d = Math.sqrt(x * x + z * z);
            Vec3d m = self.motion();
            self.setMotion(new Vec3d(
                m.x() - x / d * 0.02F * f * info.speed,
                m.y(),
                m.z() + z / d * 0.02F * f * info.speed));
        }
    }

    private void controlHovering(EntityRef self, MCH_AircraftInfo info, AircraftSimState st, ControlInput in) {
        if (st.getCurrentThrottle() < 1.0) {
            st.addCurrentThrottle(0.03333333333333333);
        } else {
            st.setCurrentThrottle(1.0);
        }

        boolean move = false;
        double x = 0.0;
        double z = 0.0;
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
        if (in.moveLeft() && !in.moveRight()) {
            float yaw = self.yaw() - 90.0F;
            x += Math.sin(yaw * Math.PI / 180.0);
            z += Math.cos(yaw * Math.PI / 180.0);
            move = true;
        }
        if (in.moveRight() && !in.moveLeft()) {
            float yaw = self.yaw() + 90.0F;
            x += Math.sin(yaw * Math.PI / 180.0);
            z += Math.cos(yaw * Math.PI / 180.0);
            move = true;
        }

        if (move) {
            double d = Math.sqrt(x * x + z * z);
            Vec3d m = self.motion();
            self.setMotion(new Vec3d(
                m.x() - x / d * 0.01F * info.speed,
                m.y(),
                m.z() + z / d * 0.01F * info.speed));
        }
    }

    @Override
    public Vec3d integrateForces(EntityRef self, MCH_AircraftInfo info, AircraftSimState st, ControlInput in, AircraftState mod) {
        HeliState h = (HeliState) mod;
        Vec3d m = self.motion();
        double motionX = m.x();
        double motionY = m.y();
        double motionZ = m.z();

        double prevMotion = Math.sqrt(motionX * motionX + motionZ * motionZ);
        float ogp = info.onGroundPitch;

        if (!h.isHovering()) {
            double dp = 0.0;
            boolean canFloatWater = info.isFloat && !h.isDestroyed();
            if (canFloatWater) {
                dp = Buoyancy.waterDepth(self, info);
            }

            if (dp == 0.0) {
                motionY = motionY + (!self.isInWater() ? info.gravity : info.gravityInWater);
                float yaw = self.yaw() / 180.0F * (float) Math.PI;
                float pitch = self.pitch();
                if (GroundProbe.blockIdInColumn(self, 3, -3) > 0) {
                    pitch -= ogp;
                }

                motionX = motionX
                    + 0.1 * MchMath.sin(yaw) * st.currentSpeed * -(pitch * pitch * pitch / 30000.0F) * st.getCurrentThrottle();
                motionZ = motionZ
                    + 0.1 * MchMath.cos(yaw) * st.currentSpeed * (pitch * pitch * pitch / 30000.0F) * st.getCurrentThrottle();
                double y = Math.abs(self.pitch()) + Math.abs(self.roll());
                y *= 0.6F;
                if (y <= 50.0) {
                    y = 1.0 - y / 50.0;
                } else {
                    y = 0.0;
                }

                double throttle = st.getCurrentThrottle();
                if (h.isDestroyed()) {
                    throttle *= 0.65;
                }

                motionY += (y * 0.025 + 0.03) * throttle;
            } else {
                if (Math.abs(self.pitch()) < 40.0F) {
                    float pitch = self.pitch();
                    pitch -= ogp;
                    pitch *= 0.9F;
                    pitch += ogp;
                    self.setRotation(self.yaw(), pitch);
                }

                if (Math.abs(self.roll()) < 40.0F) {
                    self.setRoll(self.roll() * 0.9F);
                }

                if (dp < 1.0) {
                    motionY -= 1.0E-4;
                    motionY = motionY + 0.007 * st.getCurrentThrottle();
                } else {
                    if (motionY < 0.0) {
                        motionY *= 0.7;
                    }

                    motionY += 0.007;
                }
            }
        } else {
            RandomSource rand = self.world().random();
            if (rand.nextInt(50) == 0) {
                motionX = motionX + (rand.nextDouble() - 0.5) / 30.0;
            }
            if (rand.nextInt(50) == 0) {
                motionY = motionY + (rand.nextDouble() - 0.5) / 50.0;
            }
            if (rand.nextInt(50) == 0) {
                motionZ = motionZ + (rand.nextDouble() - 0.5) / 30.0;
            }
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
            if (Math.abs(self.pitch()) < 40.0F) {
                float pitch = self.pitch();
                pitch -= ogp;
                pitch *= 0.9F;
                pitch += ogp;
                self.setRotation(self.yaw(), pitch);
            }

            if (Math.abs(self.roll()) < 40.0F) {
                self.setRoll(self.roll() * 0.9F);
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
        motionX *= 0.99;
        motionZ *= 0.99;
        self.setMotion(new Vec3d(motionX, motionY, motionZ));
    }
}
