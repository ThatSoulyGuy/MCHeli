package mcheli.agnostic.spi;

import java.util.List;
import mcheli.agnostic.value.AABB;
import mcheli.agnostic.value.MoveResult;
import mcheli.agnostic.value.Vec3d;

/**
 * Opaque handle to any entity — identity, kinematics, role, mount, and synced-data access. The
 * dependent layer wraps a Minecraft {@code Entity}; the agnostic layer never sees the concrete type
 * (classification goes through {@link #role()}, not {@code instanceof}). Motion is read as an
 * immutable {@link Vec3d} snapshot and written back via {@link #setMotion}; no shared mutable vectors.
 */
public interface EntityRef {
    int id();
    boolean isSameAs(EntityRef other);
    boolean isDead();
    boolean isRemote();
    Role role();

    // kinematics — reads
    Vec3d position();
    Vec3d prevPosition();
    Vec3d motion();
    float yaw();
    float pitch();
    float roll();        // MCHeli's custom roll axis (0 for entities without one); see EntityRef.setRoll
    float prevYaw();
    float prevPitch();
    float prevRoll();
    float width();
    float height();
    AABB boundingBox();
    boolean onGround();  // resting on ground after the last move
    boolean isInWater();
    int ageTicks();      // ticks alive (reference's countOnUpdate) — gates spawn-settle behavior

    // kinematics — writes
    void setPosition(Vec3d pos);
    void setMotion(Vec3d motion);
    void addMotion(Vec3d delta);
    void setRotation(float yaw, float pitch);
    void setRoll(float roll);
    /**
     * Move by {@code delta} with host collision resolution, returning what actually happened. In the reference
     * this was {@code moveEntity} (which also zeroed blocked motion); the agnostic integrators now zero blocked
     * axes themselves from the returned {@link MoveResult}.
     */
    MoveResult move(Vec3d delta);

    // mount / riders
    EntityRef vehicle();          // the entity this one is riding, or null
    List<EntityRef> passengers();

    // sub-surfaces
    SyncedData synced();
    WorldView world();
}
