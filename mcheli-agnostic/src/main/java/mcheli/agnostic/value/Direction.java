package mcheli.agnostic.value;

/** Facing — the agnostic replacement for {@code net.minecraft.util.EnumFacing}. Order matches MC (DOWN..EAST = 0..5). */
public enum Direction {
    DOWN(0, -1, 0), UP(0, 1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1), WEST(-1, 0, 0), EAST(1, 0, 0);

    public final int dx, dy, dz;

    Direction(int dx, int dy, int dz) { this.dx = dx; this.dy = dy; this.dz = dz; }

    private static final Direction[] VALUES = values();

    /** Map a Minecraft facing metadata index (0=DOWN .. 5=EAST) to a Direction. */
    public static Direction fromMeta(int meta) { return VALUES[Math.floorMod(meta, 6)]; }

    public Direction opposite() {
        return switch (this) {
            case DOWN -> UP; case UP -> DOWN;
            case NORTH -> SOUTH; case SOUTH -> NORTH;
            case WEST -> EAST; case EAST -> WEST;
        };
    }
}
