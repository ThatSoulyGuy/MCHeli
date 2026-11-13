package mcheli.agnostic.sim;

import mcheli.agnostic.math.MchMath;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.tank.MCH_TankInfo;

/**
 * The tank's rotation mapping for {@link RotationSolver} — port of {@code MCH_EntityTank}'s {@code setAngles} override
 * + {@code onUpdateAngles}. Unlike the heli/plane, the MOUSE contributes NOTHING to the hull (the reference tank's
 * {@code setAngles} hard-codes the mouse yaw/pitch/roll to 0 — the mouse aims the turret/camera). So {@code rawYaw/
 * Pitch/Roll} are 0 and {@code canUpdate*} are false (the compose is identity); all rotation comes from
 * {@code onUpdateAngles}: the A/D hull yaw (scaled by ground-mobility and the pivot-turn factor) and a suspension
 * lerp of pitch/roll toward the wheel targets.
 *
 * <p>Runs CLIENT-side per render frame via {@code controlMapping()} + {@code MchClientRotation} (the rider's client is
 * rotation-authoritative and ships the result to the server), matching the reference, which also runs the tank's
 * {@code onUpdateAngles} on the client render frame. Its {@link AircraftSimState} (throttle/throttleBack feed the
 * pivot-turn gate and the reverse-steer flag) must be the CLIENT copy that the dependent layer advances every client
 * tick with {@link TankFlightModel#updateThrottle} from the local keys, as the reference rider's client ran
 * {@code onUpdate_ControlSub} itself (MCH_EntityTank:424-425) — the server's sim state never reaches the client.
 *
 * <p>The {@code WheelMng} suspension lerp below is the LOCAL PILOT's half of the terrain follow only — the reference
 * put the client player on a render-frame lerp toward {@code WheelMng.targetPitch/targetRoll}
 * ({@code MCH_EntityTank.onUpdateAngles}:366-367) rather than the direct write. Those targets were solved ONCE per tick
 * ({@code WheelMng.move}, MCH_EntityTank:691) and only lerped per frame, so the terrain is sampled once per client tick
 * here too and held for that tick's frames; re-sampling per frame re-clamped the target around the moving pitch and
 * made the tilt rate scale with the frame rate. The authoritative solve runs every server tick for every tank in
 * {@link TankFlightModel#integrateForces}, ridden or not; this path only smooths it for the one client that is driving.
 * The turret/camera aim + first-person hull-tilt compensation from the reference setAngles are client-render concerns,
 * handled by the dependent client.
 *
 * <p>The hull limits are the tank's own {@code setAngles} override (MCH_EntityTank:1034-1059), not the base ones: the
 * composed pitch/roll clamp to ±90 unconditionally, the post-{@code onUpdateAngles} clamp is ±90 when
 * {@code limitRotation}, and a pitch past 90 resets to 0. The config {@code MinRotationPitch/MaxRotationPitch} (which
 * set {@code limitRotation}) bound only the rider's look there, never the hull.
 */
public final class TankControlMapping implements RotationSolver.ControlMapping {

    private final EntityRef self;
    private final MCH_TankInfo info;
    private final AircraftSimState st;
    private final TankState mod;

    // The WheelMng target of the current client tick (reference WheelMng.targetPitch/targetRoll), re-solved when the
    // entity's tick counter advances. Long.MIN_VALUE = not sampled yet.
    private long sampledTick = Long.MIN_VALUE;
    private float targetPitch;
    private float targetRoll;

    public TankControlMapping(EntityRef self, MCH_TankInfo info, AircraftSimState st, TankState mod) {
        this.self = self;
        this.info = info;
        this.st = st;
        this.mod = mod;
    }

    // The mouse does not rotate the hull: yaw/pitch/roll = 0 and no axis is "updatable", so RotationSolver's compose
    // is the identity (orientation unchanged) and everything happens in onUpdateAngles.
    @Override public float rawYaw(ControlInput in, float partialTicks)   { return 0.0F; }
    @Override public float rawPitch(ControlInput in, float partialTicks) { return 0.0F; }
    @Override public float rawRoll(ControlInput in, float partialTicks)  { return 0.0F; }
    @Override public float yawFactor()   { return 1.0F; }
    @Override public float pitchFactor() { return 1.0F; }
    @Override public float rollFactor()  { return 1.0F; }
    @Override public boolean canUpdateYaw()   { return false; }
    @Override public boolean canUpdatePitch() { return false; }
    @Override public boolean canUpdateRoll()  { return false; }

    // MCH_EntityTank.setAngles: the composed attitude clamps to ±90 unconditionally (:1034-1035) ...
    @Override public float limitComposedPitch(mcheli.agnostic.aircraft.MCH_AircraftInfo i, float pitch) {
        return MchMath.clamp(pitch, -90.0F, 90.0F);
    }
    @Override public float limitComposedRoll(mcheli.agnostic.aircraft.MCH_AircraftInfo i, float roll) {
        return MchMath.clamp(roll, -90.0F, 90.0F);
    }
    // ... and after onUpdateAngles to ±90 only when limitRotation (:1045-1050), then a runaway pitch resets (:1053-1056).
    @Override public float limitFinalPitch(mcheli.agnostic.aircraft.MCH_AircraftInfo i, float pitch) {
        float p = i.limitRotation ? MchMath.clamp(pitch, -90.0F, 90.0F) : pitch;
        return Math.abs(p) > 90.0F ? 0.0F : p;
    }
    @Override public float limitFinalRoll(mcheli.agnostic.aircraft.MCH_AircraftInfo i, float roll) {
        return i.limitRotation ? MchMath.clamp(roll, -90.0F, 90.0F) : roll;
    }

    @Override
    public void onUpdateAngles(ControlInput in, float partialTicks) {
        if (mod.isDestroyed()) {
            return;
        }
        if (mod.isGunnerMode()) {
            self.setRotation(self.yaw(), self.pitch() * 0.95F);                 // setRotPitch(pitch*0.95)
            self.setRotation(self.yaw() + info.autoPilotRot * 0.2F, self.pitch()); // setRotYaw(yaw + autoPilotRot*0.2)
            if (Math.abs(self.roll()) > 20.0F) {
                self.setRoll(self.roll() * 0.95F);
            }
        }

        // updateRecoil(partialTicks): the hull recoil kick is a dependent render effect (AbstractMchVehicle.recoil*).
        // WheelMng suspension: lerp pitch/roll toward the terrain target solved for this tick. This is what lets the
        // tank climb — the pitched hull's forward thrust (rot2Vec3(yaw, pitch-10) in TankFlightModel) gains an upward
        // component and drives up the grade. Null (no wheels configured) -> the target is level.
        if (self.ageTicks() != this.sampledTick) {
            this.sampledTick = self.ageTicks();
            WheelTerrainSolver.TerrainTilt tilt = WheelTerrainSampler.sample(self, info, info.weightedCenterZ);
            this.targetPitch = tilt != null ? tilt.targetPitch() : 0.0F;
            this.targetRoll = tilt != null ? tilt.targetRoll() : 0.0F;
        }
        self.setRotation(self.yaw(), self.pitch() + (this.targetPitch - self.pitch()) * partialTicks);
        self.setRoll(self.roll() + (this.targetRoll - self.roll()) * partialTicks);

        // "Airborne" gates the whole A/D hull-turn off. The reference used only the column block-probe, but that is
        // fragile for the port's tanks — its result depends on the exact rest Y / hitbox vs the terrain column, and
        // it was returning air (isFly=true) for a tank that was demonstrably resting on the ground (onGround()==true),
        // silently killing the turn in some worlds. Use the reliable, server-synced onGround() as the primary signal
        // and keep the probe only as the airborne fallback (so a tank hovering just above ground still counts as
        // grounded, matching the reference).
        boolean isFly = !self.onGround() && GroundProbe.blockIdInColumn(self, 3, -3) == 0;
        double waterDepth = info.isFloat ? Buoyancy.waterDepth(self, info) : 0.0;
        if (!isFly || (info.isFloat && waterDepth > 0.0)) {
            float gmy = 1.0F;
            if (!isFly) {
                gmy = info.mobilityYawOnGround;
                if (!info.canRotOnGround) {
                    if (GroundProbe.solidNonWaterInColumn(self.world(),
                            self.position().x(), self.position().y(), self.position().z(), 3, -2)) {
                        gmy = 0.0F;
                    }
                }
            }

            float pivotTurnThrottle = info.pivotTurnThrottle;
            // Last tick's displacement (reference posX - prevPosX): on the client both come from the interpolated
            // position, so this is how far the hull moved over the previous tick. With pivotTurnThrottle > 0 it gates
            // and scales the turn of a nearly stopped tank (the throttle terms come from the client sim copy).
            double dx = self.position().x() - self.prevPosition().x();
            double dz = self.position().z() - self.prevPosition().z();
            double dist = dx * dx + dz * dz;
            if (pivotTurnThrottle <= 0.0F
                || st.getCurrentThrottle() >= pivotTurnThrottle
                || st.throttleBack >= pivotTurnThrottle / 10.0F
                || dist > st.throttleBack * 0.01) {
                float sf = (float) Math.sqrt(dist <= 1.0 ? dist : 1.0);
                if (pivotTurnThrottle <= 0.0F) {
                    sf = 1.0F;
                }
                float flag = !in.throttleUp() && in.throttleDown()
                    && st.getCurrentThrottle() < pivotTurnThrottle + 0.05 ? -1.0F : 1.0F;
                if (in.moveLeft() && !in.moveRight()) {
                    self.setRotation((float) (self.yaw() - 0.6F * gmy * partialTicks * flag * sf), self.pitch());
                }
                if (in.moveRight() && !in.moveLeft()) {
                    self.setRotation((float) (self.yaw() + 0.6F * gmy * partialTicks * flag * sf), self.pitch());
                }
            }
        }

        st.addkeyRotValue = (float) (st.addkeyRotValue * (1.0 - 0.1F * partialTicks));
        // The hull pitch/roll bounds of the tank's setAngles override are the limit* hooks above, applied by
        // RotationSolver around this call.
    }
}
