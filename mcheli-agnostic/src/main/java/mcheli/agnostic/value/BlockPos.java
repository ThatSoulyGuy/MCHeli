package mcheli.agnostic.value;

/** Immutable integer block coordinate — the agnostic replacement for {@code net.minecraft.world.ChunkPosition}. */
public record BlockPos(int x, int y, int z) {

    public BlockPos offset(int dx, int dy, int dz) { return new BlockPos(x + dx, y + dy, z + dz); }
    public BlockPos offset(Direction d)            { return new BlockPos(x + d.dx, y + d.dy, z + d.dz); }

    /** Center of the block as a {@link Vec3d}. */
    public Vec3d center() { return new Vec3d(x + 0.5, y + 0.5, z + 0.5); }
}
