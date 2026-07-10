package mcheli.agnostic.value;

import mcheli.agnostic.math.MchMath;

/**
 * Immutable 3D double vector — the agnostic replacement for {@code net.minecraft.util.Vec3}.
 *
 * <p>Rotations follow Minecraft's {@code Vec3} sign conventions so coerced physics/aim math stays
 * behaviorally identical. The dependent layer never shares a mutable vector: it reads an entity's
 * motion as a {@code Vec3d} snapshot and writes a fresh one back via {@code EntityRef.setMotion}.
 */
public record Vec3d(double x, double y, double z) {

    public static final Vec3d ZERO = new Vec3d(0.0, 0.0, 0.0);

    public static Vec3d of(double x, double y, double z) { return new Vec3d(x, y, z); }

    public Vec3d add(Vec3d o)                        { return new Vec3d(x + o.x, y + o.y, z + o.z); }
    public Vec3d add(double dx, double dy, double dz) { return new Vec3d(x + dx, y + dy, z + dz); }
    public Vec3d sub(Vec3d o)                        { return new Vec3d(x - o.x, y - o.y, z - o.z); }
    public Vec3d scale(double s)                     { return new Vec3d(x * s, y * s, z * s); }

    public double dot(Vec3d o)   { return x * o.x + y * o.y + z * o.z; }
    public Vec3d  cross(Vec3d o) { return new Vec3d(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x); }

    public double lengthSquared()      { return x * x + y * y + z * z; }
    public double length()             { return Math.sqrt(lengthSquared()); }
    public double distanceTo(Vec3d o)  { return sub(o).length(); }

    public Vec3d normalize() {
        double len = length();
        return len < 1.0e-8 ? ZERO : new Vec3d(x / len, y / len, z / len);
    }

    // Rotations (radians), matching net.minecraft.util.Vec3.rotateAroundX/Y/Z — using MC's LUT trig
    // (MchMath.sin/cos) so results are bit-identical to the 1.7.10 original and the 1.21.1 Mth target.
    public Vec3d rotateAroundX(double rad) {
        float c = MchMath.cos((float) rad), s = MchMath.sin((float) rad);
        return new Vec3d(x, y * c + z * s, z * c - y * s);
    }
    public Vec3d rotateAroundY(double rad) {
        float c = MchMath.cos((float) rad), s = MchMath.sin((float) rad);
        return new Vec3d(x * c + z * s, y, z * c - x * s);
    }
    public Vec3d rotateAroundZ(double rad) {
        float c = MchMath.cos((float) rad), s = MchMath.sin((float) rad);
        return new Vec3d(x * c + y * s, y * c - x * s, z);
    }
}
