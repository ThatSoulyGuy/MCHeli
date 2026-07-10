package mcheli.agnostic.value;

/** Immutable axis-aligned bounding box — the agnostic replacement for {@code net.minecraft.util.AxisAlignedBB}. */
public record AABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    /** Build from any two corners (normalizes min/max). */
    public static AABB of(double x0, double y0, double z0, double x1, double y1, double z1) {
        return new AABB(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1),
                        Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1));
    }

    public AABB offset(Vec3d d)                       { return offset(d.x(), d.y(), d.z()); }
    public AABB offset(double dx, double dy, double dz) {
        return new AABB(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
    }

    /** Grow (or shrink, with negatives) symmetrically on each axis. */
    public AABB expand(double dx, double dy, double dz) {
        return new AABB(minX - dx, minY - dy, minZ - dz, maxX + dx, maxY + dy, maxZ + dz);
    }

    public boolean intersects(AABB o) {
        return minX < o.maxX && maxX > o.minX
            && minY < o.maxY && maxY > o.minY
            && minZ < o.maxZ && maxZ > o.minZ;
    }

    public boolean contains(Vec3d p) {
        return p.x() >= minX && p.x() <= maxX
            && p.y() >= minY && p.y() <= maxY
            && p.z() >= minZ && p.z() <= maxZ;
    }

    public Vec3d center() { return new Vec3d((minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0); }
}
