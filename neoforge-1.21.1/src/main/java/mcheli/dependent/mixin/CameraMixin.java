package mcheli.dependent.mixin;

import mcheli.dependent.entity.AbstractMchVehicle;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rigidly parents the FIRST-PERSON camera to an MCHeli vehicle's full orientation — the piece the public API cannot
 * reach. NeoForge's {@code ViewportEvent.ComputeCameraAngles} can set the camera ANGLES, but {@code Camera.setup} adds
 * the eye height on WORLD-Y ({@code + Mth.lerp(pt, eyeHeightOld, eyeHeight)}) with no event to intercept it, so in a
 * bank the eye peels off the cockpit. Injecting at the TAIL of {@code setup} we overwrite BOTH:
 *
 * <ul>
 *   <li><b>rotation</b> — {@code Camera.setRotation} composes {@code rotationYXZ(PI - yaw, -pitch, -roll)}; feeding the
 *       vehicle's own yaw/pitch/roll reproduces its forward+up at any attitude (compose Euler→quaternion, never
 *       decompose, so no gimbal at ±90°);</li>
 *   <li><b>position</b> — the eye is placed at the interpolated vehicle position plus the pilot eye offset rotated by
 *       the same orientation, so it rides the cockpit through banks/loops instead of the vanilla world-Y offset.</li>
 * </ul>
 *
 * <p>First-person only ({@code detached == false}); third-person keeps the vanilla orbit camera. Interpolation matches
 * the model renderer so the cockpit and the view stay locked together.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch, float roll);

    @Inject(method = "setup", at = @At("TAIL"))
    private void mcheli$cockpitCamera(BlockGetter level, Entity cam, boolean detached, boolean reverse,
                                      float partialTick, CallbackInfo ci) {
        if (detached || cam == null) {
            return; // third-person / no camera entity -> leave the vanilla camera
        }
        if (!(cam.getVehicle() instanceof AbstractMchVehicle v) || !v.supportsMouseRotation()
            || !v.locksViewToVehicle()) {
            return; // ground vehicles (tank) keep the free vanilla mouse-look camera
        }
        Vec3 feet = v.cockpitFeetOffset();
        if (feet == null) {
            return; // no cockpit seat defined -> leave the vanilla camera
        }

        // Vehicle orientation, interpolated to match the model renderer.
        float yaw = Mth.rotLerp(partialTick, v.yRotO, v.getYRot());
        float pitch = Mth.lerp(partialTick, v.xRotO, v.getXRot());
        float roll = mcheli$shortLerp(v.getRollAngle(), v.getPrevRollAngle(), partialTick);

        // Orientation: reuse Camera.setRotation (rebuilds the quaternion + forwards/up/left) with the vehicle Euler.
        this.setRotation(yaw, pitch, roll);

        // Position: interpolated vehicle position + R * (pilot feet + eye height) taken in MODEL space, so the eye
        // rides the cockpit through any bank. R = Ry(-yaw)*Rx(pitch)*Rz(roll), matching the model renderer.
        double vx = Mth.lerp((double) partialTick, v.xo, v.getX());
        double vy = Mth.lerp((double) partialTick, v.yo, v.getY());
        double vz = Mth.lerp((double) partialTick, v.zo, v.getZ());
        Vector3f eye = new Vector3f((float) feet.x, (float) feet.y + cam.getEyeHeight(), (float) feet.z);
        eye.rotate(new Quaternionf()
            .rotateY((float) Math.toRadians(-yaw))
            .rotateX((float) Math.toRadians(pitch))
            .rotateZ((float) Math.toRadians(roll)));
        this.setPosition(new Vec3(vx + (double) eye.x, vy + (double) eye.y, vz + (double) eye.z));
    }

    /** Short-path angle interpolation (matches MchModelEntityRenderer.calcRot) so the bank never spins the long way. */
    private static float mcheli$shortLerp(float rot, float prevRot, float t) {
        rot = Mth.wrapDegrees(rot);
        prevRot = Mth.wrapDegrees(prevRot);
        if (rot - prevRot < -180.0F) {
            prevRot -= 360.0F;
        } else if (prevRot - rot < -180.0F) {
            prevRot += 360.0F;
        }
        return prevRot + (rot - prevRot) * t;
    }
}
