package mcheli.agnostic.sim;

import mcheli.agnostic.helicopter.MCH_HeliInfo;
import mcheli.agnostic.spi.EntityRef;

/**
 * The helicopter's per-axis rotation mapping for {@link RotationSolver} — bit-faithful port of {@code MCH_EntityHeli}'s
 * {@code getControlRot*} / {@code getRollFactor} / {@code canUpdate*} / {@code onUpdateAngles}. The mouse drives all
 * three axes (yaw = roll = stick-X, pitch = stick-Y); pitch/roll are suppressed while hovering; the roll factor is a
 * 2-sample moving average; {@code onUpdateAngles} auto-damps bank, banks with A/D in the air, and trims attitude on
 * the ground.
 *
 * <p>Holds its entity / info / rotation-state / mod-state context (constructed once per vehicle). The per-render-frame
 * virtual-stick values and control bits arrive via the {@link ControlInput}. The {@code prevRollFactor} accumulator
 * lives in {@link AircraftSimState} (here the CLIENT rotation state, distinct from the server physics state).
 */
public final class HeliControlMapping implements RotationSolver.ControlMapping {

    private final EntityRef self;
    private final MCH_HeliInfo info;
    private final AircraftSimState st;
    private final HeliState mod;

    public HeliControlMapping(EntityRef self, MCH_HeliInfo info, AircraftSimState st, HeliState mod) {
        this.self = self;
        this.info = info;
        this.st = st;
        this.mod = mod;
    }

    // getControlRot*: yaw = mouseX, pitch = mouseY, roll = mouseX (mouseX/Y are the accumulated virtual stick).
    @Override public float rawYaw(ControlInput in, float partialTicks)   { return (float) in.stickX(); }
    @Override public float rawPitch(ControlInput in, float partialTicks) { return (float) in.stickY(); }
    @Override public float rawRoll(ControlInput in, float partialTicks)  { return (float) in.stickX(); }

    // getYawFactor/getPitchFactor = base 1.0. getRollFactor = 2-sample average of super(=1.0) and prevRollFactor
    // (the reference's d/s term is dead code assigned to unused locals). This mutates prevRollFactor each call, and
    // — like the reference — is only invoked when canUpdateRoll() is true (RotationSolver gates it).
    @Override public float yawFactor()   { return 1.0F; }
    @Override public float pitchFactor() { return 1.0F; }
    @Override public float rollFactor() {
        float roll = 1.0F;
        float f = st.prevRollFactor;
        st.prevRollFactor = roll;
        return (roll + f) / 2.0F;
    }

    // canUpdateYaw = base; canUpdatePitch/Roll = base && !isHovering.
    @Override public boolean canUpdateYaw()   { return baseCanUpdate(); }
    @Override public boolean canUpdatePitch() { return baseCanUpdate() && !mod.isHovering(); }
    @Override public boolean canUpdateRoll()  { return baseCanUpdate() && !mod.isHovering(); }

    // Base canUpdate: countOnUpdate >= 30 AND off the ground (getBlockIdY(3,-2)==0, i.e. no collidable block within 2
    // below). countOnUpdate ≈ ticks-since-spawn -> use ageTicks() (available client-side, unlike the server's
    // physics countOnUpdate). The base's getRidingEntity()!=null -> false term is always false here (the demo vehicle
    // never rides another entity), so it is omitted.
    private boolean baseCanUpdate() {
        return self.ageTicks() >= 30 && GroundProbe.blockIdInColumn(self, 3, -2) == 0;
    }

    @Override
    public void onUpdateAngles(ControlInput in, float partialTicks) {
        if (mod.isDestroyed()) {
            return;
        }
        // Bank auto-damping toward level (stronger while hovering): roll *= 1 - k*partialTicks, only within (0.1,65).
        float rotRoll = !mod.isHovering() ? 0.04F : 0.07F;
        rotRoll = 1.0F - rotRoll * partialTicks;
        if (self.roll() > 0.1 && self.roll() < 65.0F) {
            self.setRoll(self.roll() * rotRoll);
        }
        if (self.roll() < -0.1 && self.roll() > -65.0F) {
            self.setRoll(self.roll() * rotRoll);
        }

        if (GroundProbe.blockIdInColumn(self, 3, -3) == 0) { // in the air
            if (in.moveLeft() && !in.moveRight()) {
                self.setRoll(self.roll() - 1.2F * partialTicks);
            }
            if (in.moveRight() && !in.moveLeft()) {
                self.setRoll(self.roll() + 1.2F * partialTicks);
            }
        } else { // on the ground
            if (Math.abs(self.pitch()) < 40.0F) {
                applyOnGroundPitch(0.97F);
            }
            // TODO(fold-blade): reference adds ±0.5F/tick hull yaw here when the heli has fold-blades deployed
            // (isEnableFoldBlade && rotors.length>0 && foldBladeStat==0). Needs rotor/foldBladeStat seams; the demo
            // heli has no fold blades, so this branch is inert.
        }
    }

    /** Reference base {@code applyOnGroundPitch(factor)}: damp pitch toward {@code onGroundPitch}, and roll by factor. */
    private void applyOnGroundPitch(float factor) {
        float ogp = info.onGroundPitch;
        float pitch = self.pitch();
        pitch -= ogp;
        pitch *= factor;
        pitch += ogp;
        self.setRotation(self.yaw(), pitch);
        self.setRoll(self.roll() * factor);
    }
}
