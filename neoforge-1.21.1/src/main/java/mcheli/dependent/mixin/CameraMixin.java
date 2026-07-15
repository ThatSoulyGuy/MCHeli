package mcheli.dependent.mixin;

import mcheli.dependent.entity.AbstractMchVehicle;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Parents the camera to an MCHeli vehicle — the 1.21.1 port of the reference camera rig ({@code MCH_Camera} +
 * {@code MCH_ViewEntityDummy} + per-config {@code CameraPosition}), reduced to the single-rider scope:
 *
 * <ul>
 *   <li><b>Eye position</b> — {@link AbstractMchVehicle#firstPersonEye}: the config {@code CameraPosition} (when
 *       {@code alwaysCameraView} / an emplacement), orbited about the turret for a {@code rotSeat} seat
 *       ({@code calcOnTurretPos}), else the locked-view cockpit eye; {@code null} means the reference renders from the
 *       player itself and the vanilla camera (at the faithfully-attached rider) is already correct.</li>
 *   <li><b>Rotation</b> — locked-view aircraft get the full vehicle yaw/pitch/roll (the reference locks the PLAYER to
 *       the hull; {@code Camera.setup} adds eye height on world-Y with no event to intercept, hence this Mixin).
 *       Free-look tanks keep the player's own look but gain the reference's faded camera roll
 *       ({@code wrap(rotRoll)·cos(hullYaw − playerYaw)}, {@code MCH_ClientCommonTickHandler:408-415}); emplacements
 *       get no roll ({@code cameraRollFade()==false}).</li>
 *   <li><b>Third person</b> — orbits the same eye (the reference orbits the render-view dummy): re-anchor to the eye
 *       and re-run the vanilla pull-back; the vanilla (possibly reverse-flipped) rotation is kept.</li>
 * </ul>
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch, float roll);

    @Shadow
    protected abstract float getMaxZoom(float startingDistance);

    @Shadow
    protected abstract void move(float zoom, float dy, float dx);

    @Inject(method = "setup", at = @At("TAIL"))
    private void mcheli$vehicleCamera(BlockGetter level, Entity cam, boolean detached, boolean reverse,
                                      float partialTick, CallbackInfo ci) {
        if (cam == null || !(cam.getVehicle() instanceof AbstractMchVehicle v)) {
            return;
        }
        Vec3 eye = v.firstPersonEye(cam, partialTick); // null -> the vanilla player camera is the reference answer
        // The hull-locked camera is ONLY for the PILOT flying normally (mouse drives the airframe). A GUNNER — or the
        // pilot in gunner mode — free-looks to aim, so their camera must follow their own look, not weld to the hull
        // (else the gun tracks the mouse but the view stays frozen, the Apache-gunner bug). firstPersonEye already
        // detaches their eye POSITION to the gun-sight camera; this keeps the ROTATION theirs too.
        int camSeat = v.seatIndexOf(cam);
        boolean locked = v.supportsMouseRotation() && v.locksViewToVehicle()
            && camSeat == 0 && !v.isSeatGunnerMode(0);
        float hullYaw = Mth.rotLerp(partialTick, v.yRotO, v.getYRot());
        float hullRoll = mcheli$shortLerp(v.getRollAngle(), v.getPrevRollAngle(), partialTick);

        Camera self = (Camera) (Object) this;
        // Weapon recoil kicks the first-person view too (the reference recoil perturbs the hull the camera is locked to).
        float rP = v.recoilPitchDeg(partialTick);
        float rR = v.recoilRollDeg(partialTick);
        if (!detached) {
            if (locked) {
                // Reference locks the PLAYER to the hull (setAngles:1265-1288) -> camera shows the full hull attitude.
                float hullPitch = Mth.lerp(partialTick, v.xRotO, v.getXRot());
                this.setRotation(hullYaw, hullPitch + rP, hullRoll + rR);
            } else if (v.cameraRollFade()) {
                // Free look on a tank: camera roll fades as the player looks away from the hull axis.
                float yawDiff = Mth.wrapDegrees(hullYaw - self.getYRot());
                float roll = Mth.wrapDegrees(hullRoll) * Mth.cos(yawDiff * Mth.DEG_TO_RAD);
                this.setRotation(self.getYRot(), self.getXRot() + rP, roll + rR);
            }
            if (eye != null) {
                this.setPosition(eye);
            }
        } else {
            // Third person: the reference applies camRoll in ALL views (orientCamera rolls OUTERMOST, before the
            // third-person offset, and does NOT negate it in the front view) — keep the vanilla yaw/pitch (incl. the
            // reverse flip) and add the same locked/faded roll as first person. Locked riders have yawDiff == 0, so
            // the faded formula degenerates to the full hull roll for them.
            if (locked || v.cameraRollFade()) {
                float yawDiff = Mth.wrapDegrees(hullYaw - (reverse ? self.getYRot() - 180.0F : self.getYRot()));
                float roll = Mth.wrapDegrees(hullRoll) * Mth.cos(yawDiff * Mth.DEG_TO_RAD);
                this.setRotation(self.getYRot(), self.getXRot(), roll);
            }
            if (eye != null) {
                // Re-anchor the orbit on the config/cockpit eye and redo the vanilla pull-back — with the NeoForge
                // detached-distance hook + entity scale, exactly as vanilla Camera.setup does.
                this.setPosition(eye);
                float scale = cam instanceof net.minecraft.world.entity.LivingEntity le ? le.getScale() : 1.0F;
                this.move(-this.getMaxZoom(
                    net.neoforged.neoforge.client.ClientHooks.getDetachedCameraDistance(self, reverse, scale, 4.0F) * scale),
                    0.0F, 0.0F);
            }
        }
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
