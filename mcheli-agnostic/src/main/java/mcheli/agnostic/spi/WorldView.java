package mcheli.agnostic.spi;

import java.util.List;
import mcheli.agnostic.value.AABB;
import mcheli.agnostic.value.RayHit;
import mcheli.agnostic.value.Vec3d;

/**
 * The read/affect surface of the world: block queries, entity-in-box queries, raytracing, spawning,
 * particles, and world-side sound. The dependent layer implements it against a Minecraft {@code Level}.
 */
public interface WorldView {
    boolean isRemote();
    long totalTime();

    // blocks
    int getBlockId(int x, int y, int z);
    boolean isWater(int x, int y, int z);
    String materialName(int x, int y, int z);
    boolean isCollidable(int x, int y, int z);  // block has a collision shape (reference block.canCollideCheck)
    boolean isAABBInWater(AABB box);             // any water overlapping the box (reference isAABBInMaterial water)

    /** Lowest block Y of this world (inclusive). The 1.7.10 reference hardcoded 0; 1.21's overworld starts at -64. */
    default int minBuildHeight() { return -64; }
    /** Highest block Y of this world (inclusive). The 1.7.10 reference hardcoded 255; 1.21's overworld ends at 319. */
    default int maxBuildHeight() { return 319; }

    // entities
    List<EntityRef> entitiesInAABB(AABB box);
    List<EntityRef> entitiesInAABB(AABB box, Role role);

    // raytrace
    RayHit rayTraceBlocks(Vec3d from, Vec3d to);
    RayHit clip(Vec3d from, Vec3d to, boolean stopOnLiquid);

    // effects / spawn
    void spawn(EntityRef entity);
    void spawnParticle(String name, Vec3d pos, Vec3d velocity);
    void playSound(Vec3d pos, String name, float volume, float pitch);
    void playSoundAtEntity(EntityRef entity, String name, float volume, float pitch);

    RandomSource random();
}
