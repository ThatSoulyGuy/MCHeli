package mcheli.dependent.entity;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import mcheli.dependent.config.MchServerRules;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import org.slf4j.Logger;

/**
 * The reference's crush whitelists ({@code MCH_Config} {@code Collision_Car_*} / {@code Collision_Tank_*},
 * :257-265, read through {@code get*ListFromType} at {@code MCH_EntityTank}:936-938), keyed by the tank config's
 * {@code WeightType} (0 = none, 1 = Car, 2 = Tank, {@code MCH_TankInfo.weightType}).
 *
 * <p>The lists are the server config's ({@link MchServerRules}): the no-break and breakable BLOCK lists take block ids
 * and {@code #tags}; the breakable-MATERIAL list takes the reference's bare 1.7.10 material names, mapped here onto
 * 1.21 blocks and tags (1.7.10 {@code Material} has no 1.21 counterpart), plus any ids/tags written into it. The
 * config defaults reproduce the reference defaults.
 *
 * <p>There is deliberately NO hardness or blast-resistance test anywhere: in the reference the whitelist IS the rule.
 * Anything not listed is inert, and a no-break block additionally aborts the rest of that vertical column (see
 * {@code AbstractMchVehicle.destroyBlockRange}). Evaluation order is fixed and matters: <b>noBreak</b> is tested before
 * <b>materialBreak</b>, which is what keeps glowstone intact for a tank even though 1.7.10 classed it as
 * {@code Material.glass}.
 *
 * <p>The one judgement call among the materials is {@code wood}: it maps to {@link BlockTags#MINEABLE_WITH_AXE}, which
 * reproduces what a 1.7.10 tank actually flattened (planks, logs, fences, doors, chests, bookshelves) and by
 * construction also covers every wooden block added since. A tank will demolish a wooden building.
 */
public final class MchCrushableBlocks {

    private MchCrushableBlocks() {}

    private static final Logger LOG = LogUtils.getLogger();

    public static final int WEIGHT_NONE = 0;
    public static final int WEIGHT_CAR = 1;
    public static final int WEIGHT_TANK = 2;

    /** Blocks that stop the crush dead, and abandon the rest of the column. Reference {@code getNoBreakableBlockListFromType}. */
    public static boolean noBreak(BlockState s, int weightType) {
        return MchServerRules.crushNoBreak(s, weightType);
    }

    /** Named blocks that break and then abandon the column. Reference {@code getBreakableBlockListFromType}. */
    public static boolean explicitBreak(BlockState s, int weightType) {
        return MchServerRules.crushBreaks(s, weightType);
    }

    /** Material-class matches, which break and CONTINUE down the column. Reference {@code getBreakableMaterialListFromType}. */
    public static boolean materialBreak(BlockState s, int weightType) {
        if (weightType != WEIGHT_CAR && weightType != WEIGHT_TANK) {
            return false;
        }
        return MchServerRules.crushMaterialBlock(s, weightType) || materials(weightType).test(s);
    }

    // ---- the material names (reference MCH_Config.setMaterialListFromString via MCH_Lib.getMaterialFromName, whose
    //      table :41-74 names every 1.7.10 Material). The port maps the ten the default lists use; any other block set
    //      can be written into the same list as block ids or #tags. ----

    /** The resolved predicate for one weight type, rebuilt whenever the server rules hand back a different name set. */
    private record Resolved(Set<String> names, Predicate<BlockState> test) {}

    private static volatile Resolved car;
    private static volatile Resolved tank;

    private static Predicate<BlockState> materials(int weightType) {
        Set<String> names = MchServerRules.crushMaterialNames(weightType);
        Resolved r = weightType == WEIGHT_TANK ? tank : car;
        if (r == null || r.names() != names) {
            r = new Resolved(names, resolve(names, weightType));
            if (weightType == WEIGHT_TANK) {
                tank = r;
            } else {
                car = r;
            }
        }
        return r.test();
    }

    private static Predicate<BlockState> resolve(Set<String> names, int weightType) {
        List<Predicate<BlockState>> parts = new ArrayList<>();
        for (String name : names) {
            Predicate<BlockState> p = material(name);
            if (p != null) {
                parts.add(p);
            } else {
                LOG.warn("MCHeli config: unsupported breakable material '{}' for weight type {} (supported: cactus, cake, "
                    + "carpet, circuits, glass, gourd, leaves, plants, vine, wood; list other blocks by id or #tag), ignored",
                    name, weightType);
            }
        }
        if (parts.isEmpty()) {
            return s -> false;
        }
        return s -> {
            for (Predicate<BlockState> p : parts) {
                if (p.test(s)) {
                    return true;
                }
            }
            return false;
        };
    }

    /** One 1.7.10 material name onto 1.21 blocks/tags, or null for a name the reference vocabulary does not know. */
    private static Predicate<BlockState> material(String name) {
        return switch (name) {
            case "cactus" -> MchCrushableBlocks::cactus;
            case "cake" -> MchCrushableBlocks::cake;
            case "carpet" -> MchCrushableBlocks::carpet;
            case "circuits" -> MchCrushableBlocks::circuits;
            case "glass" -> MchCrushableBlocks::glass;
            case "gourd" -> MchCrushableBlocks::gourd;
            case "leaves" -> MchCrushableBlocks::leaves;
            case "plants" -> MchCrushableBlocks::plants;
            case "vine" -> MchCrushableBlocks::vine;
            case "wood" -> MchCrushableBlocks::wood;
            default -> null;
        };
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

    /** Glowstone is {@code Material.glass} in 1.7.10 too; {@link #noBreak} runs first and spares it for a tank. */
    private static boolean glass(BlockState s) {
        return s.is(Tags.Blocks.GLASS_BLOCKS) || s.is(Tags.Blocks.GLASS_PANES)
            || s.is(Blocks.BEACON) || s.is(Blocks.SEA_LANTERN) || s.is(Blocks.REDSTONE_LAMP);
    }

    private static boolean wood(BlockState s) {
        return s.is(BlockTags.MINEABLE_WITH_AXE);
    }
}
