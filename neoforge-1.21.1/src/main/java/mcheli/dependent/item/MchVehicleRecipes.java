package mcheli.dependent.item;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.aircraft.MCH_RecipeSpec;
import mcheli.agnostic.helicopter.MCH_HeliInfoManager;
import mcheli.agnostic.plane.MCP_PlaneInfoManager;
import mcheli.agnostic.tank.MCH_TankInfoManager;
import mcheli.agnostic.vehicle.MCH_VehicleInfoManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;

/**
 * Builds a vanilla crafting-recipe JSON for every discovered vehicle that declares an {@code AddRecipe} /
 * {@code AddShapelessRecipe}, so {@link mcheli.dependent.port.MchRecipePack} can serve them as a datapack.
 *
 * <p>This is the consumer the port was missing. The reference registered these as plain vanilla recipes at
 * {@code postInit} ({@code MCH_MOD}:217 -> {@code MCH_ItemRecipe.registerItemRecipe}, which walked every info manager
 * and called {@code GameRegistry.addShapedRecipe}); the port carried the PARSER across into
 * {@code MCH_AircraftInfo.recipeString} and never wrote the other half, so all 107 recipe lines were parsed and
 * discarded and no vehicle was obtainable outside creative.
 *
 * <p>Emitting datapack JSON rather than registering a {@code CustomRecipe} is deliberate: these become real
 * {@code ShapedRecipe}/{@code ShapelessRecipe} objects, so they show up in the recipe book and in JEI/EMI, survive
 * {@code /reload}, and — because the table is built from the LIVE info managers — cover content-pack vehicles that a
 * static set of shipped JSON files never could.
 */
public final class MchVehicleRecipes {

    private MchVehicleRecipes() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile Map<String, String> table;

    /** {@code <vehicle config name> -> recipe JSON}. Built once, lazily; {@link #invalidate()} drops it. */
    public static Map<String, String> table() {
        Map<String, String> t = table;
        if (t == null) {
            synchronized (MchVehicleRecipes.class) {
                t = table;
                if (t == null) {
                    t = build();
                    table = t;
                }
            }
        }
        return t;
    }

    /** Drop the cache so the next {@link #table()} rebuilds — the hook for a future config hot-reload. */
    public static void invalidate() {
        table = null;
    }

    private static Map<String, String> build() {
        Map<String, String> out = new LinkedHashMap<>();
        int skipped = 0;
        // Sorted by name so the emitted set — and any recipe-shadowing tie-break — is deterministic across runs.
        List<MCH_AircraftInfo> all = new ArrayList<>();
        collect(all, MCH_HeliInfoManager.getInstance());
        collect(all, MCP_PlaneInfoManager.getInstance());
        collect(all, MCH_TankInfoManager.getInstance());
        collect(all, MCH_VehicleInfoManager.getInstance());
        all.sort((a, b) -> a.name.compareToIgnoreCase(b.name));

        for (MCH_AircraftInfo info : all) {
            if (info.recipeString == null || info.recipeString.isEmpty()) {
                continue;
            }
            ResourceLocation resultId = ResourceLocation.fromNamespaceAndPath("mcheli", info.name.toLowerCase(java.util.Locale.ROOT));
            if (!BuiltInRegistries.ITEM.containsKey(resultId)) {
                continue; // no spawn item was minted for this config (e.g. a UAV) — nothing to craft
            }
            int n = 0;
            for (String line : info.recipeString) {
                MCH_RecipeSpec spec = MCH_RecipeSpec.parse(line, info.isShapedRecipe);
                if (spec == null) {
                    LOGGER.warn("MCHeli: {} — skipping unusable recipe line: {}", info.name, line);
                    skipped++;
                    continue;
                }
                String json = toJson(spec, resultId, info.name);
                if (json == null) {
                    skipped++;
                    continue;
                }
                n++;
                // A config may declare several recipes (ah-6x ships two); suffix all but the first.
                out.put(n == 1 ? info.name.toLowerCase(java.util.Locale.ROOT)
                               : info.name.toLowerCase(java.util.Locale.ROOT) + "_" + n, json);
            }
        }
        LOGGER.info("MCHeli: built {} vehicle crafting recipes ({} skipped)", out.size(), skipped);
        return out;
    }

    /** {@code MCH_InfoManagerBase.getMap()} is a raw {@code Map}; take only the aircraft-shaped values from it. */
    @SuppressWarnings("rawtypes")
    private static void collect(List<MCH_AircraftInfo> out, mcheli.agnostic.info.MCH_InfoManagerBase mgr) {
        Map map = mgr.getMap();
        if (map == null) {
            return;
        }
        for (Object v : map.values()) {
            if (v instanceof MCH_AircraftInfo info) {
                out.add(info);
            }
        }
    }

    /** Render one spec as a vanilla crafting JSON, or {@code null} if an ingredient does not resolve. */
    private static String toJson(MCH_RecipeSpec spec, ResourceLocation result, String vehicle) {
        StringBuilder sb = new StringBuilder(256);
        if (spec.shaped) {
            Map<Character, Item> keys = new LinkedHashMap<>();
            for (MCH_RecipeSpec.Key k : spec.keys) {
                Item item = MchLegacyItemNames.resolve(k.itemName, k.meta);
                if (item == null) {
                    // The reference threw a RuntimeException here (MCH_ItemRecipe:195-200). Crashing the mod because a
                    // config names an item from an absent feature is not acceptable, so skip loudly instead — this is
                    // the e767 case, whose recipe needs the unported mcheli:uav_station.
                    LOGGER.warn("MCHeli: {} — recipe skipped, unknown ingredient '{}'", vehicle, k.itemName);
                    return null;
                }
                keys.put(k.symbol, item);
            }
            if (keys.isEmpty()) {
                return null;
            }
            sb.append("{\"type\":\"minecraft:crafting_shaped\",\"category\":\"misc\",\"pattern\":[");
            for (int i = 0; i < spec.rows.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append('"').append(escape(spec.rows.get(i))).append('"');
            }
            sb.append("],\"key\":{");
            boolean first = true;
            for (Map.Entry<Character, Item> e : keys.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('"').append(escape(String.valueOf(e.getKey()))).append("\":{\"item\":\"")
                  .append(BuiltInRegistries.ITEM.getKey(e.getValue())).append("\"}");
            }
            sb.append('}');
        } else {
            sb.append("{\"type\":\"minecraft:crafting_shapeless\",\"category\":\"misc\",\"ingredients\":[");
            boolean first = true;
            for (MCH_RecipeSpec.Key k : spec.keys) {
                Item item = MchLegacyItemNames.resolve(k.itemName, k.meta);
                if (item == null) {
                    LOGGER.warn("MCHeli: {} — recipe skipped, unknown ingredient '{}'", vehicle, k.itemName);
                    return null;
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append("{\"item\":\"").append(BuiltInRegistries.ITEM.getKey(item)).append("\"}");
            }
            sb.append(']');
        }
        sb.append(",\"result\":{\"id\":\"").append(result).append("\",\"count\":").append(spec.count).append("}}");
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
