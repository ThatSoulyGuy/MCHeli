package mcheli.agnostic.sim;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.math.MCH_Math;
import mcheli.agnostic.math.MchMath;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.util.MCH_LowPassFilterFloat;

/**
 * Agnostic port of the SHARED base {@code MCH_EntityAircraft.setAngles} — the client-render-frame orientation
 * update that turns rider control into the vehicle's yaw/pitch/roll. Each axis' raw control value (from the
 * per-vehicle {@link ControlMapping}) is clamped to {@code 40*mobility*}, scaled by {@code factor*0.06*partialTicks}
 * (pitch NEGATED), then composed with the current orientation in the body frame via the already-agnostic
 * {@link MCH_Math} matrices ({@code MatTurnZ/X/Y} add-rotation × current, then {@code MatrixToEuler}); finally the
 * pitch/roll limits ({@code limitRotation} min/max by default, ±90 for the tank — {@link ControlMapping#limitComposedPitch})
 * and roll normalization to [-180,180] are applied, the vehicle's {@link ControlMapping#onUpdateAngles auto-level}
 * runs, and the limits are re-applied.
 *
 * <p>Runs on the CLIENT render frame with {@code partialTicks} (low-passed over a ~10-sample window), separately
 * from the server force integration. The per-vehicle mouse→raw mapping, factors, {@code canUpdate*} gates and
 * auto-level are supplied by the {@link ControlMapping} (built per entity when each vehicle is wired) — this class
 * owns only the shared clamp/scale/matrix-compose/limit pipeline, bit-faithful to the reference.
 */
public final class RotationSolver {
    private RotationSolver() {}

    /** The per-vehicle pieces of {@code setAngles} that vary by type (the reference's overridable methods). */
    public interface ControlMapping {
        float rawYaw(ControlInput in, float partialTicks);   // getControlRotYaw
        float rawPitch(ControlInput in, float partialTicks); // getControlRotPitch
        float rawRoll(ControlInput in, float partialTicks);  // getControlRotRoll
        float yawFactor();                                   // getYawFactor
        float pitchFactor();                                 // getPitchFactor
        float rollFactor();                                  // getRollFactor
        boolean canUpdateYaw();                              // canUpdateYaw(player)
        boolean canUpdatePitch();                            // canUpdatePitch(player)
        boolean canUpdateRoll();                             // canUpdateRoll(player)
        /** Per-model auto-level applied after the compose (heli bank damping, plane taxi/flightSim, ...). Reads the
         *  rider's control bits ({@code in}); the mapping holds its own entity/info/state context. */
        void onUpdateAngles(ControlInput in, float partialTicks);

        /** setAngles' clamp of the freshly composed pitch ({@code v.x}). Base: the config {@code limitRotation}
         *  bounds (MCH_EntityAircraft:1220-1223). The tank overrides it (MCH_EntityTank:1034-1035). */
        default float limitComposedPitch(MCH_AircraftInfo info, float pitch) {
            return info.limitRotation ? MchMath.clamp(pitch, info.minRotationPitch, info.maxRotationPitch) : pitch;
        }

        /** setAngles' clamp of the freshly composed roll ({@code v.z}), before the [-180,180] wrap. */
        default float limitComposedRoll(MCH_AircraftInfo info, float roll) {
            return info.limitRotation ? MchMath.clamp(roll, info.minRotationRoll, info.maxRotationRoll) : roll;
        }

        /** setAngles' re-clamp of the pitch after {@link #onUpdateAngles} (MCH_EntityAircraft:1237-1242). */
        default float limitFinalPitch(MCH_AircraftInfo info, float pitch) {
            return limitComposedPitch(info, pitch);
        }

        /** setAngles' re-clamp of the roll after {@link #onUpdateAngles}. */
        default float limitFinalRoll(MCH_AircraftInfo info, float roll) {
            return limitComposedRoll(info, roll);
        }

        /** Reference {@code isOverridePlayerYaw()} (base {@code !isFreeLookMode()}; free look is handled by the
         *  client): whether the rider's view yaw is welded to the hull. False lets the client keep the vanilla mouse
         *  look on that axis — the plane's gunner mode, where the pilot aims freely while the airframe holds course. */
        default boolean overridesRiderYaw() { return true; }

        /** Reference {@code isOverridePlayerPitch()}: whether the rider's view pitch is welded to the hull. A hovering
         *  heli or plane (gunner/hover mode) leaves it free, so the pilot can look up and down to aim. */
        default boolean overridesRiderPitch() { return true; }
    }

    public static void applyControl(EntityRef self, MCH_AircraftInfo info, ControlInput in,
                                    MCH_LowPassFilterFloat lowPassPartialTicks, ControlMapping map) {
        float partialTicks = in.partialTicks();
        if (partialTicks < 0.03F) {
            partialTicks = 0.4F;
        }
        if (partialTicks > 0.9F) {
            partialTicks = 0.6F;
        }
        lowPassPartialTicks.put(partialTicks);
        partialTicks = lowPassPartialTicks.getAvg();

        // Reference free-look zeroes ONLY the mouse-stick position (x/y) but STILL invokes the per-vehicle control
        // mapping (getControlRot*), so key-driven control — e.g. plane FlightSim key-yaw — and its accumulator side
        // effects keep running. So zero the stick on the INPUT and ALWAYS call the mapping, never short-circuit its
        // output to 0.
        ControlInput mapIn = in.freeLook() ? in.withZeroStick() : in;

        float yaw = 0.0F;
        float pitch = 0.0F;
        float roll = 0.0F;

        if (map.canUpdateYaw()) {
            double limit = 40.0 * info.mobilityYaw;
            yaw = map.rawYaw(mapIn, partialTicks);
            if (yaw < -limit) {
                yaw = (float) (-limit);
            }
            if (yaw > limit) {
                yaw = (float) limit;
            }
            yaw = (float) (yaw * map.yawFactor() * 0.06 * partialTicks);
        }

        if (map.canUpdatePitch()) {
            double limit = 40.0 * info.mobilityPitch;
            pitch = map.rawPitch(mapIn, partialTicks);
            if (pitch < -limit) {
                pitch = (float) (-limit);
            }
            if (pitch > limit) {
                pitch = (float) limit;
            }
            pitch = (float) (-pitch * map.pitchFactor() * 0.06 * partialTicks);
        }

        if (map.canUpdateRoll()) {
            double limit = 40.0 * info.mobilityRoll;
            roll = map.rawRoll(mapIn, partialTicks);
            if (roll < -limit) {
                roll = (float) (-limit);
            }
            if (roll > limit) {
                roll = (float) limit;
            }
            roll = roll * map.rollFactor() * 0.06F * partialTicks;
        }

        MCH_Math.FMatrix m = MCH_Math.newMatrix();
        MCH_Math.MatTurnZ(m, roll / 180.0F * (float) Math.PI);
        MCH_Math.MatTurnX(m, pitch / 180.0F * (float) Math.PI);
        MCH_Math.MatTurnY(m, yaw / 180.0F * (float) Math.PI);
        MCH_Math.MatTurnZ(m, (float) (self.roll() / 180.0F * Math.PI));
        MCH_Math.MatTurnX(m, (float) (self.pitch() / 180.0F * Math.PI));
        MCH_Math.MatTurnY(m, (float) (self.yaw() / 180.0F * Math.PI));
        MCH_Math.FVector3D v = MCH_Math.MatrixToEuler(m);

        v.x = map.limitComposedPitch(info, v.x);
        v.z = map.limitComposedRoll(info, v.z);

        if (v.z > 180.0F) {
            v.z -= 360.0F;
        }
        if (v.z < -180.0F) {
            v.z += 360.0F;
        }

        self.setRotation(v.y, v.x); // yaw = v.y, pitch = v.x
        self.setRoll(v.z);

        map.onUpdateAngles(in, partialTicks);

        float px = map.limitFinalPitch(info, self.pitch());
        float rz = map.limitFinalRoll(info, self.roll());
        if (px != self.pitch()) {
            self.setRotation(self.yaw(), px);
        }
        if (rz != self.roll()) {
            self.setRoll(rz);
        }

        // Reference setAngles' FINAL unconditional roll wrap to [-180,180] (MCH_EntityAircraft:1251-1257), applied
        // after onUpdateAngles (which can push roll past ±180 via the heli air-bank / plane rudder-roll terms) and
        // the limit re-apply. Keeps the STORED roll() faithful; the composed orientation is already 360-periodic.
        if (self.roll() > 180.0F) {
            self.setRoll(self.roll() - 360.0F);
        }
        if (self.roll() < -180.0F) {
            self.setRoll(self.roll() + 360.0F);
        }
    }
}
