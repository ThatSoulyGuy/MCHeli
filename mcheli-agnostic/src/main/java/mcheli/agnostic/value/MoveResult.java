package mcheli.agnostic.value;

/**
 * The outcome of a collision-resolved {@link mcheli.agnostic.spi.EntityRef#move}: what the host's swept-AABB
 * movement actually produced this tick. In the 1.7.10 reference, {@code moveEntity} both resolved collision AND
 * zeroed the blocked motion components in place; here the host resolves collision and REPORTS the outcome, and
 * the pure flight integrators use these flags to zero blocked motion axes and to gate on-ground behavior —
 * without the agnostic layer ever seeing block collision.
 *
 * @param actualDelta how far the entity actually moved (post-collision), which may be shorter than requested
 * @param onGround    whether the entity ended the move resting on ground (reference's {@code onGround})
 * @param collidedH   collided horizontally (reference's {@code isCollidedHorizontally})
 * @param collidedV   collided vertically (reference's {@code isCollidedVertically})
 * @param blockedX    the X component was cut short by collision (reference zeroed {@code motionX})
 * @param blockedY    the Y component was cut short by collision (reference zeroed {@code motionY})
 * @param blockedZ    the Z component was cut short by collision (reference zeroed {@code motionZ})
 */
public record MoveResult(Vec3d actualDelta, boolean onGround, boolean collidedH, boolean collidedV,
                         boolean blockedX, boolean blockedY, boolean blockedZ) {
}
