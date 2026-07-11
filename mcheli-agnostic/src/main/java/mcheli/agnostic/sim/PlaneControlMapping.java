package mcheli.agnostic.sim;

import mcheli.agnostic.plane.MCP_PlaneInfo;
import mcheli.agnostic.spi.EntityRef;

/**
 * The plane's per-axis rotation mapping for {@link RotationSolver} — bit-faithful port of {@code MCP_EntityPlane}'s
 * {@code getControlRot*} / factors / {@code canUpdate*} / {@code onUpdateAngles} / {@code rotationByKey}. Mouse:
 * pitch = stick-Y; roll = stick-X·0.5 (·2 in flight-sim, ·1 with VTOL); yaw = stick-X, OR the keyboard-rudder
 * accumulator {@code addkeyRotValue·20} in flight-sim mode. Factors are the VTOL-aware base ·0.8.
 * {@code onUpdateAngles} handles taxi (on-ground keyboard yaw scaled by ground-mobility), in-flight keyboard roll,
 * the {@code addkeyRotValue} decay, on-ground pitch trim, and VTOL-nozzle attitude damping.
 *
 * <p>Holds its entity / info / rotation-state / mod context; per-render-frame stick + control bits arrive via the
 * {@link ControlInput}. {@code addkeyRotValue} lives in {@link AircraftSimState} (the client rotation state).
 */
public final class PlaneControlMapping implements RotationSolver.ControlMapping {

    private final EntityRef self;
    private final MCP_PlaneInfo info;
    private final AircraftSimState st;
    private final PlaneState mod;

    public PlaneControlMapping(EntityRef self, MCP_PlaneInfo info, AircraftSimState st, PlaneState mod) {
        this.self = self;
        this.info = info;
        this.st = st;
        this.mod = mod;
    }

    // getControlRotYaw: flight-sim -> rotationByKey side-effect + addkeyRotValue*20; else the raw stick.
    @Override public float rawYaw(ControlInput in, float partialTicks) {
        if (in.flightSimMode()) {
            rotationByKey(in, partialTicks);
            return st.addkeyRotValue * 20.0F;
        }
        return (float) in.stickX();
    }

    @Override public float rawPitch(ControlInput in, float partialTicks) {
        return (float) in.stickY();
    }

    // getControlRotRoll: flight-sim -> stickX*2; else stickX*0.5 (VTOL off) or stickX (VTOL on).
    @Override public float rawRoll(ControlInput in, float partialTicks) {
        if (in.flightSimMode()) {
            return (float) (in.stickX() * 2.0F);
        }
        return mod.getVtolMode() == 0 ? (float) (in.stickX() * 0.5F) : (float) in.stickX();
    }

    // getYawFactor/getPitchFactor/getRollFactor: (VTOL ? vtol* : base 1.0) * 0.8. Roll uses vtolYaw (reference).
    @Override public float yawFactor()   { return (mod.getVtolMode() > 0 ? info.vtolYaw : 1.0F) * 0.8F; }
    @Override public float pitchFactor() { return (mod.getVtolMode() > 0 ? info.vtolPitch : 1.0F) * 0.8F; }
    @Override public float rollFactor()  { return (mod.getVtolMode() > 0 ? info.vtolYaw : 1.0F) * 0.8F; }

    // canUpdate* = base && !isHovering (base = ageTicks>=30 && off-the-ground; ridingEntity term is inert for the demo).
    @Override public boolean canUpdateYaw()   { return baseCanUpdate() && !mod.isHovering(); }
    @Override public boolean canUpdatePitch() { return baseCanUpdate() && !mod.isHovering(); }
    @Override public boolean canUpdateRoll()  { return baseCanUpdate() && !mod.isHovering(); }

    private boolean baseCanUpdate() {
        return self.ageTicks() >= 30 && GroundProbe.blockIdInColumn(self, 3, -2) == 0;
    }

    /** Accumulate the keyboard rudder into addkeyRotValue (rot=0.2, zeroed in non-sim VTOL). */
    private void rotationByKey(ControlInput in, float partialTicks) {
        float rot = 0.2F;
        if (!in.flightSimMode() && mod.getVtolMode() != 0) {
            rot *= 0.0F;
        }
        if (in.moveLeft() && !in.moveRight()) {
            st.addkeyRotValue -= rot * partialTicks;
        }
        if (in.moveRight() && !in.moveLeft()) {
            st.addkeyRotValue += rot * partialTicks;
        }
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

        boolean isFly = GroundProbe.blockIdInColumn(self, 3, -3) == 0;
        double waterDepth = info.isFloat ? Buoyancy.waterDepth(self, info) : 0.0;
        if (!isFly || in.freeLook() || mod.isGunnerMode() || (info.isFloat && waterDepth > 0.0)) {
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
            if (in.moveLeft() && !in.moveRight()) {
                self.setRotation(self.yaw() - 0.6F * gmy * partialTicks, self.pitch());
            }
            if (in.moveRight() && !in.moveLeft()) {
                self.setRotation(self.yaw() + 0.6F * gmy * partialTicks, self.pitch());
            }
        } else if (isFly && !in.flightSimMode()) {
            rotationByKey(in, partialTicks);
            self.setRoll(self.roll() + st.addkeyRotValue * 0.5F * info.mobilityRoll);
        }

        st.addkeyRotValue = (float) (st.addkeyRotValue * (1.0 - 0.1F * partialTicks));
        if (!isFly && Math.abs(self.pitch()) < 40.0F) {
            applyOnGroundPitch(0.97F);
        }
        if (mod.getNozzleRotation() > 0.001F) {
            float rot = 1.0F - 0.03F * partialTicks;
            self.setRotation(self.yaw(), self.pitch() * rot);
            rot = 1.0F - 0.1F * partialTicks;
            self.setRoll(self.roll() * rot);
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
