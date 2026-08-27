package mcheli.dependent.entity;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/**
 * The reference's crush whitelists, ported from {@code MCH_Config} onto 1.21.1 blocks and tags.
 *
 * <p>The port has no user-config system, so the three config strings become code. Their defaults
 * ({@code MCH_Config.java}:266-272) are reproduced exactly, keyed by the tank config's {@code WeightType}
 * (0 = none, 1 = Car, 2 = Tank — {@code MCH_TankInfo.weightType}):
 * <ul>
 *   <li>{@code Collision_Car_NoBreakBlock  = "torch"} · {@code Collision_Tank_NoBreakBlock = "torch, glowstone"}</li>
 *   <li>{@code Collision_Car_BreakableBlock = "double_plant, glass_pane, stained_glass_pane"} ·
 *       {@code Collision_Tank_BreakableBlock = "nether_brick_fence"}</li>
 *   <li>{@code Collision_Car_BreakableMaterial = "cactus, cake, gourd, leaves, vine, plants"} ·
 *       {@code Collision_Tank_BreakableMaterial = }the same plus {@code carpet, circuits, glass, wood}</li>
 * </ul>
 *
 * <p>There is deliberately NO hardness or blast-resistance test anywhere — in the reference the whitelist IS the
 * rule. Anything not listed is inert, and a no-break block additionally aborts the rest of that vertical column
 * (see {@code AbstractMchVehicle.destroyBlockRange}). Evaluation order is fixed and matters: <b>noBreak</b> is
 * tested before <b>materialBreak</b>, which is what keeps glowstone intact for a tank even though 1.7.10 classed
 * it as {@code Material.glass}.
 *
 * <p>1.7.10 {@code Material} has no 1.21.1 counterpart, so each material maps to the closest tag or block set.
 * The one judgement call is {@code Material.wood}: it maps to {@link BlockTags#MINEABLE_WITH_AXE}, which
 * reproduces what a 1.7.10 tank actually flattened (planks, logs, fences, doors, chests, bookshelves) and by
 * construction also covers every wooden block added since. A tank will demolish a wooden building.
 */
public final class MchCrushableBlocks {

    private MchCrushableBlocks() {}

    public static final int WEIGHT_NONE = 0;
    public static final int WEIGHT_CAR = 1;
    public static final int WEIGHT_TANK = 2;

    /** Blocks that stop the crush dead — and abandon the rest of the column. Reference {@code getNoBreakableBlockListFromType}. */
    public static boolean noBreak(BlockState s, int weightType) {
        if (weightType == WEIGHT_NONE) {
            return false;
        }
        boolean torch = s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH)
            || s.is(Blocks.SOUL_TORCH) || s.is(Blocks.SOUL_WALL_TORCH);
        if (weightType == WEIGHT_TANK) {
            return torch || s.is(Blocks.GLOWSTONE);
        }
        return torch;
    }

    /** Named blocks that break and then abandon the column. Reference {@code getBreakableBlockListFromType}. */
    public static boolean explicitBreak(BlockState s, int weightType) {
        if (weightType == WEIGHT_TANK) {
            return s.is(Tags.Blocks.FENCES_NETHER_BRICK);
        }
        if (weightType == WEIGHT_CAR) {
            // "double_plant" is the 1.7.10 two-block-tall plant; "glass_pane" + "stained_glass_pane" merged into one tag.
            return s.is(BlockTags.TALL_FLOWERS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.LARGE_FERN)
                || s.is(Blocks.PITCHER_PLANT) || s.is(Tags.Blocks.GLASS_PANES);
        }
        return false;
    }

    /** Material-class matches, which break and CONTINUE down the column. Reference {@code getBreakableMaterialListFromType}. */
    public static boolean materialBreak(BlockState s, int weightType) {
        if (weightType == WEIGHT_NONE) {
            return false;
        }
        // Shared by Car and Tank.
        if (cactus(s) || cake(s) || gourd(s) || leaves(s) || vine(s) || plants(s)) {
            return true;
        }
        // Tank-only additions.
        return weightType == WEIGHT_TANK && (carpet(s) || circuits(s) || glass(s) || wood(s));
    }

    private static boolean cactus(BlockState s) {
        return s.is(Blocks.CACTUS);
    }

    private static boolean cake(BlockState s) {
        return s.is(Blocks.CAKE) || s.is(BlockTags.CANDLE_CAKES);
    }

    private static boolean gourd(BlockState s) {
        return s.is(Blocks.PUMPKIN) || s.is(Blocks.CARVED_PUMPKIN)
            || s.is(Blocks.JACK_O_LANTERN) || s.is(Blocks.MELON);
    }

    private static boolean leaves(BlockState s) {
        return s.is(BlockTags.LEAVES);
    }

    private static boolean vine(BlockState s) {
        return s.is(Blocks.VINE) || s.is(Blocks.GLOW_LICHEN) || s.is(Blocks.SHORT_GRASS) || s.is(Blocks.FERN)
            || s.is(Blocks.DEAD_BUSH) || s.is(Blocks.WEEPING_VINES) || s.is(Blocks.TWISTING_VINES);
    }

    private static boolean plants(BlockState s) {
        return s.is(BlockTags.SAPLINGS) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.CROPS)
            || s.is(Blocks.LILY_PAD) || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.BROWN_MUSHROOM)
            || s.is(Blocks.RED_MUSHROOM) || s.is(Blocks.NETHER_WART) || s.is(Blocks.BAMBOO_SAPLING);
    }

    private static boolean carpet(BlockState s) {
        return s.is(BlockTags.WOOL_CARPETS) || s.is(Blocks.MOSS_CARPET);
    }

    private static boolean circuits(BlockState s) {
        return s.is(Blocks.REPEATER) || s.is(Blocks.COMPARATOR) || s.is(Blocks.REDSTONE_WIRE)
            || s.is(Blocks.REDSTONE_TORCH) || s.is(Blocks.REDSTONE_WALL_TORCH) || s.is(Blocks.LEVER)
            || s.is(Blocks.TRIPWIRE) || s.is(Blocks.TRIPWIRE_HOOK) || s.is(Blocks.DAYLIGHT_DETECTOR)
            || s.is(Blocks.LADDER) || s.is(BlockTags.BUTTONS) || s.is(BlockTags.PRESSURE_PLATES)
            || s.is(BlockTags.RAILS);
    }

    /** Glowstone is {@code Material.glass} in 1.7.10 too — {@link #noBreak} runs first and spares it for a tank. */
    private static boolean glass(BlockState s) {
        return s.is(Tags.Blocks.GLASS_BLOCKS) || s.is(Tags.Blocks.GLASS_PANES)
            || s.is(Blocks.BEACON) || s.is(Blocks.SEA_LANTERN) || s.is(Blocks.REDSTONE_LAMP);
    }

    private static boolean wood(BlockState s) {
        return s.is(BlockTags.MINEABLE_WITH_AXE);
    }
}
