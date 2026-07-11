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
 * <p>Runs SERVER-side (the demo tank's hull yaw is server-authoritative, like the ground vehicle's), so it holds the
 * tank's live physics {@link AircraftSimState} (throttle/throttleBack feed the pivot-turn logic). One consequence:
 * the reference runs this on the client RENDER frame (accumulating per-frame), while the port runs it once per SERVER
 * tick with the {@code RotationSolver} low-pass partialTicks at its steady-state ~0.6 — so the hull turns somewhat
 * slower than the reference. Tunable via {@code mobilityYawOnGround} if the feel is off.
 *
 * <p>Deferred (TODO): {@code WheelMng} suspension — the reference lerps pitch/roll toward {@code WheelMng.targetPitch/
 * targetRoll} (host wheel-entity terrain contact); here the targets are 0, so the hull stays level. The turret/camera
 * aim + first-person hull-tilt compensation from the reference setAngles are client-render concerns, not ported.
 */
public final class TankControlMapping implements RotationSolver.ControlMapping {

    private final EntityRef self;
    private final MCH_TankInfo info;
    private final AircraftSimState st;
    private final TankState mod;

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

        // updateRecoil(partialTicks): deferred (no weapons yet).
        // WheelMng suspension: lerp pitch/roll toward the wheel targets (0 here -> toward level).
        float targetPitch = 0.0F; // TODO(wheel-terrain): WheelMng.targetPitch
        float targetRoll = 0.0F;  // TODO(wheel-terrain): WheelMng.targetRoll
        self.setRotation(self.yaw(), self.pitch() + (targetPitch - self.pitch()) * partialTicks);
        self.setRoll(self.roll() + (targetRoll - self.roll()) * partialTicks);

        boolean isFly = GroundProbe.blockIdInColumn(self, 3, -3) == 0;
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
            // NOTE: running server-side BEFORE tickPhysics moves the entity, prevPosition()==position() this tick,
            // so dist is 0 here (the reference reads it after the tick's move). Harmless while pivotTurnThrottle<=0
            // (below, that forces sf=1 and short-circuits the gate); a pivot-turn tank (pivotTurnThrottle>0) would
            // need this reordered/measured once such configs + the WheelMng move land.
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

        // The reference tank's setAngles OVERRIDE clamps pitch/roll to +/-90 UNCONDITIONALLY (the shared
        // RotationSolver only applies the base's limitRotation-gated clamp) and zeroes a runaway pitch. onUpdateAngles
        // runs inside setAngles, so bound them here: a no-op for a level demo tank, a safety once WheelMng terrain
        // contact tilts the hull past 90 degrees.
        self.setRotation(self.yaw(), MchMath.clamp(self.pitch(), -90.0F, 90.0F));
        self.setRoll(MchMath.clamp(self.roll(), -90.0F, 90.0F));
    }
}
