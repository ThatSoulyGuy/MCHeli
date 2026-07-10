package mcheli.dependent.entity;

/**
 * Implemented by entities that carry MCHeli's custom <em>roll</em> axis, which is not part of vanilla
 * {@code net.minecraft.world.entity.Entity} (which has only yaw/pitch). {@code NeoEntityRef} routes the
 * agnostic {@code EntityRef.roll()/prevRoll()/setRoll()} seam to this when the wrapped entity supports it,
 * and reports 0 / ignores writes otherwise (e.g. the demo vehicle, vanilla mobs).
 */
public interface RollHolder {
    float getRollAngle();

    float getPrevRollAngle();

    void setRollAngle(float roll);
}
