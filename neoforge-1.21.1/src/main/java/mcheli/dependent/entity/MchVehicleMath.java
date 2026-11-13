package mcheli.dependent.entity;

import mcheli.agnostic.physics.MCH_BoundingBox;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Stateless helpers shared by {@link AbstractMchVehicle} and its same-package components: angle interpolation, the
 * aim vector, colour packing and the part-box conversion. Moved verbatim out of the entity; dist-shared (no
 * client-only type).
 */
final class MchVehicleMath {
    private MchVehicleMath() {}

    /** Short-path angle interpolation in degrees — byte-for-byte {@code MchModelEntityRenderer.calcRot} /
     *  {@code CameraMixin.mcheli$shortLerp} (inlined here so this dist-shared class references no client-only type). */
    static float shortLerpDeg(float rot, float prevRot, float t) {
        rot = Mth.wrapDegrees(rot);
        prevRot = Mth.wrapDegrees(prevRot);
        if (rot - prevRot < -180.0F) {
            prevRot -= 360.0F;
        } else if (prevRot - rot < -180.0F) {
            prevRot += 360.0F;
        }
        return prevRot + (rot - prevRot) * t;
    }

    /** Unit vector for a yaw/pitch/roll in the port's orientation convention ({@code Ry(-yaw)·Rx(pitch)·Rz(roll)},
     *  forward = model +Z) — the transform the fire direction, the muzzle and the model render all share. */
    static Vec3 aimVector(float yaw, float pitch, float roll) {
        org.joml.Vector3f fwd = new org.joml.Vector3f(0.0F, 0.0F, 1.0F);
        new org.joml.Quaternionf()
            .rotateY((float) Math.toRadians(-yaw))
            .rotateX((float) Math.toRadians(pitch))
            .rotateZ((float) Math.toRadians(roll))
            .transform(fwd);
        return new Vec3(fwd.x, fwd.y, fwd.z);
    }

    /** Pack four 0..1 colour channels into 0xAARRGGBB (config muzzle-flash colours are already normalized). */
    static int packArgb(float a, float r, float g, float b) {
        return ((Math.round(a * 255.0F) & 0xFF) << 24) | ((Math.round(r * 255.0F) & 0xFF) << 16)
            | ((Math.round(g * 255.0F) & 0xFF) << 8) | (Math.round(b * 255.0F) & 0xFF);
    }

    /** Pack an {@link mcheli.agnostic.math.MCH_Color} (0..1 floats) into 0xAARRGGBB for the bullet tint. */
    static int packColor(mcheli.agnostic.math.MCH_Color c) {
        int a = Math.round(c.a * 255.0F) & 0xFF;
        int r = Math.round(c.r * 255.0F) & 0xFF;
        int g = Math.round(c.g * 255.0F) & 0xFF;
        int b = Math.round(c.b * 255.0F) & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    static AABB mcBox(MCH_BoundingBox bb) {
        mcheli.agnostic.value.AABB a = bb.boundingBox;
        return new AABB(a.minX(), a.minY(), a.minZ(), a.maxX(), a.maxY(), a.maxZ());
    }
}
