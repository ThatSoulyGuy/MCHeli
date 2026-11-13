package mcheli.dependent.entity;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.aircraft.MCH_PartStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * What one vehicle's crew sees by: the camera modes (reference MCH_Camera: 0 normal, 1 night vision, 2 thermal
 * vision; seats 0 and 1 only), the night-vision viewer bookkeeping, the search-light switch and the search light's
 * lighting value. The camera and light bits live in the synced part status; this class changes them only through
 * {@link MchVehicleParts#setCameraModeBits} and {@link MchVehicleParts#toggleSearchLight}.
 */
final class MchVehicleVision {

    private final AbstractMchVehicle v;

    private final Entity[] cameraViewers = new Entity[mcheli.agnostic.aircraft.MCH_CameraMode.SEATS]; // SERVER
    private final int[] lastCameraModes = new int[mcheli.agnostic.aircraft.MCH_CameraMode.SEATS];     // SERVER

    MchVehicleVision(AbstractMchVehicle v) {
        this.v = v;
    }

    // -- search light (reference haveSearchLight / isSearchLightON / canSwitchSearchLight) --

    boolean haveSearchLight() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        return info != null && !info.searchLights.isEmpty();
    }

    boolean isSearchLightOn() { return v.haveSearchLight() && (v.vehicleParts().partStatus() & MCH_PartStatus.SEARCH_LIGHT) != 0; }

    /**
     * CLIENT: how brightly this vehicle's search lights light {@code e}, as a block-light lightmap coordinate 0..240 —
     * the port of {@code getSearchLightValue} (:1311-1351). For each lamp within its beam length, the angle between
     * the beam axis and the entity, horizontally ({@code getPosAngle}) plus vertically, must stay under three times the
     * cone's half angle; the light falls off linearly from the axis and saturates at 240 in the inner five sixths.
     * A steerable lamp points along the seat-1 (else pilot) look, a fixed one along the hull (plus the steering turn).
     */
    float searchLightValue(Entity e) {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (info == null || !v.isSearchLightOn()) {
            return 0.0F;
        }
        org.joml.Quaternionf body = v.bodyRotation(1.0F);
        float lookYaw = v.vehicleParts().cameraPartYaw(1.0F);
        float lookPitch = v.vehicleParts().cameraPartPitch(1.0F);
        for (MCH_AircraftInfo.SearchLight sl : info.searchLights) {
            org.joml.Vector3f lamp = new org.joml.Vector3f((float) sl.pos.x(), (float) sl.pos.y(), (float) sl.pos.z()).rotate(body);
            double cx = e.getX() - (v.getX() + lamp.x);
            double cy = e.getY() - (v.getY() + lamp.y - v.modelMinY());
            double cz = e.getZ() - (v.getZ() + lamp.z);
            double dist = cx * cx + cy * cy + cz * cz;
            if (!(dist > 2.0 && dist < sl.height * sl.height + 20.0F)) {
                continue;
            }
            // MCH_Lib.RotVec3(0,0,1, -yaw, -pitch, -roll) == Ry(-yaw)·Rx(pitch)·Rz(roll)·(0,0,1) (see bodyRotation).
            float yaw;
            float pitch;
            if (!sl.fixDir) {
                yaw = lookYaw - sl.yaw;
                pitch = lookPitch - sl.pitch;
            } else {
                float stRot = sl.steering ? v.animation().wheelSteer() * sl.stRot : 0.0F;
                yaw = v.getYRot() - sl.yaw - stRot;
                pitch = v.getXRot() - sl.pitch;
            }
            org.joml.Vector3f axis = new org.joml.Vector3f(0.0F, 0.0F, 1.0F).rotate(new org.joml.Quaternionf()
                .rotateY((float) Math.toRadians(-yaw))
                .rotateX((float) Math.toRadians(pitch))
                .rotateZ((float) Math.toRadians(v.getRollAngle())));
            double h = mcheli.agnostic.math.MchMath.getPosAngle(axis.x, axis.z, cx, cz);
            // The local `v` below shadows the vehicle field inside its own initializer, so the vehicle is this.v there.
            double v = Math.abs(Math.atan2(cy, Math.sqrt(cx * cx + cz * cz)) * 180.0 / Math.PI
                + (sl.fixDir ? this.v.getXRot() : lookPitch) + sl.pitch);
            float angle = sl.angle * 3.0F;
            if (h < angle && v < angle) {
                float value = h + v < angle ? (float) (1440.0 * (1.0 - (h + v) / angle)) : 0.0F;
                return Math.min(value, 240.0F);
            }
        }
        return 0.0F;
    }

    // -- camera modes (reference MCH_Camera: 0 normal, 1 night vision, 2 thermal vision; seats 0 and 1 only) --

    /** Reference {@code canSwitchCameraMode()}: the config enables night vision and the vehicle is intact. */
    boolean canSwitchCameraMode() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        return !v.isDestroyed() && info != null && info.isEnableNightVision;
    }

    /** The camera mode of {@code seat} (synced; 0 for a seat without one). */
    int cameraMode(int seat) {
        return mcheli.agnostic.aircraft.MCH_CameraMode.isValidSeat(seat)
            ? MCH_PartStatus.cameraMode(v.vehicleParts().partStatus(), seat) : mcheli.agnostic.aircraft.MCH_CameraMode.NORMAL;
    }

    /** SERVER: the camera-mode key (reference {@code MCH_AircraftClientTickHandler:96-114}). A vehicle with a search
     *  light uses it to switch the light (seats 0 and 1, {@code canSwitchSearchLight}); otherwise it steps the
     *  pressing seat's camera mode when the config enables night vision. 1.21 always has post shaders, so thermal
     *  vision is always available (reference {@code getModeNum} == 3). */
    void cameraModeKey(Player player) {
        int seat = v.seatIndexOf(player);
        if (v.level().isClientSide || !mcheli.agnostic.aircraft.MCH_CameraMode.isValidSeat(seat)) {
            return;
        }
        if (v.haveSearchLight()) {
            if (!v.isDestroyed()) {
                v.vehicleParts().toggleSearchLight();
            }
        } else if (v.canSwitchCameraMode()) {
            v.vehicleParts().setCameraModeBits(seat, mcheli.agnostic.aircraft.MCH_CameraMode.next(v.cameraMode(seat), true));
        }
    }

    /** SERVER: the reference {@code MCH_Camera.initCamera} / {@code updateViewer} per camera seat. A seat that changes
     *  occupant returns to normal (the mount/unmount {@code initCamera}); a viewer in a vision mode keeps a 250-tick
     *  night vision effect topped up; leaving the vision modes removes the short effect the camera applied (only that
     *  one, {@link #isCameraNightVision}). A viewer leaving the vehicle is handled at once by {@link #releaseCameraViewer},
     *  since a removed vehicle never runs this again. Run from {@link MchVehicleParts#tick}, at its reference place. */
    void updateViewers() {
        for (int seat = 0; seat < this.cameraViewers.length; seat++) {
            Entity viewer = v.seatPassenger(seat);
            if (viewer != this.cameraViewers[seat]) {
                if (this.cameraViewers[seat] instanceof LivingEntity old && this.lastCameraModes[seat] != 0) {
                    removeCameraNightVision(old);
                }
                // The new occupant starts in the normal mode, so it carries no camera effect either. Logout already
                // strips it before the save (MchServerEvents); this stays as the fallback for a player file saved by an
                // older build.
                if (viewer instanceof LivingEntity arriving) {
                    removeCameraNightVision(arriving);
                }
                if (v.cameraMode(seat) != 0) {
                    v.vehicleParts().setCameraModeBits(seat, 0);
                }
                this.cameraViewers[seat] = viewer;
                this.lastCameraModes[seat] = 0;
            }
            int mode = v.cameraMode(seat);
            if (viewer instanceof LivingEntity le && le.isAlive()) {
                net.minecraft.world.effect.MobEffectInstance nv = le.getEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
                int dur = nv != null ? nv.getDuration() : 0;
                if (mcheli.agnostic.aircraft.MCH_CameraMode.shouldRemoveNightVision(mode, this.lastCameraModes[seat], dur)
                    && isCameraNightVision(nv)) {
                    le.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
                }
                if (mcheli.agnostic.aircraft.MCH_CameraMode.shouldApplyNightVision(mode, dur)) {
                    // Reference PotionEffect(nightVision, 250, 0, ambient = true). No swirl particles: they would sit
                    // in the pilot's own view (a deliberate divergence; the 1.7.10 ambient swirl was faint).
                    le.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,
                        mcheli.agnostic.aircraft.MCH_CameraMode.NIGHT_VISION_TICKS, 0, true, false));
                }
            }
            this.lastCameraModes[seat] = mode;
        }
    }

    /** Strip {@code e}'s night vision if it is the camera's own effect; a potion or {@code /effect} one stays. */
    private static void removeCameraNightVision(LivingEntity e) {
        if (isCameraNightVision(e.getEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION))) {
            e.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
        }
    }

    /** Is {@code nv} the night vision a vehicle camera applied ({@link mcheli.agnostic.aircraft.MCH_CameraMode#isCameraNightVision}:
     *  ambient, no particles, amplifier 0, at most 250 ticks)? The reference took any effect under 500 ticks. */
    private static boolean isCameraNightVision(net.minecraft.world.effect.MobEffectInstance nv) {
        return nv != null && mcheli.agnostic.aircraft.MCH_CameraMode.isCameraNightVision(
            nv.isAmbient(), nv.isVisible(), nv.getAmplifier(), nv.getDuration());
    }

    /** SERVER: {@code p} left the vehicle (dismount, death, the vehicle's removal or unload) or is logging out. Drop it
     *  as a camera viewer, strip the night vision its camera applied and return the seat's camera to normal right away:
     *  a removed vehicle never ticks {@link #updateViewers} again, which would otherwise leave up to 250 ticks of
     *  it on the ex-rider. On logout the player file is saved BEFORE vanilla dismounts the player, so
     *  {@link mcheli.dependent.MchServerEvents} calls this first from the logged-out event; the later dismount then
     *  finds nothing left to release. */
    void releaseCameraViewer(Entity p) {
        if (v.level().isClientSide) {
            return;
        }
        for (int seat = 0; seat < this.cameraViewers.length; seat++) {
            if (this.cameraViewers[seat] != p) {
                continue;
            }
            if (p instanceof LivingEntity le && this.lastCameraModes[seat] != 0) {
                removeCameraNightVision(le);
            }
            this.cameraViewers[seat] = null;
            this.lastCameraModes[seat] = 0;
            if (v.cameraMode(seat) != 0) {
                v.vehicleParts().setCameraModeBits(seat, 0);
            }
        }
    }
}
