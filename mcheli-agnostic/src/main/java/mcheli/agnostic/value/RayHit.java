package mcheli.agnostic.value;

import mcheli.agnostic.spi.EntityRef;

/** Immutable raytrace result — the agnostic replacement for {@code net.minecraft.util.MovingObjectPosition}. */
public record RayHit(Type type, Vec3d hitVec, int blockX, int blockY, int blockZ, EntityRef entity) {

    public enum Type { MISS, BLOCK, ENTITY }

    public static final RayHit MISS = new RayHit(Type.MISS, null, 0, 0, 0, null);

    public boolean isMiss()   { return type == Type.MISS; }
    public boolean isBlock()  { return type == Type.BLOCK; }
    public boolean isEntity() { return type == Type.ENTITY; }

    public static RayHit block(Vec3d hit, int x, int y, int z) { return new RayHit(Type.BLOCK, hit, x, y, z, null); }
    public static RayHit entity(Vec3d hit, EntityRef e)        { return new RayHit(Type.ENTITY, hit, 0, 0, 0, e); }
}
