package mcheli.dependent.config;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mcheli.agnostic.config.MchDamageFactorList;
import mcheli.dependent.entity.MchGroundVehicle;
import mcheli.dependent.entity.MchHelicopter;
import mcheli.dependent.entity.MchPlane;
import mcheli.dependent.entity.MchTank;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * The server config's list options, parsed once into ready-to-use rules: the block whitelists (bullet pass-through and
 * the vehicle crush lists) and the reference's global damage factors. Port of the list half of {@code MCH_Config}
 * ({@code correctionParameter} :499-584 built the same caches) plus its static helpers {@code applyDamageVsEntity},
 * {@code applyDamageByExternal} and {@code get*ListFromType}. The caches rebuild after the server file (re)loads or
 * unloads; before a world loads they hold the reference defaults.
 */
public final class MchServerRules {
    private MchServerRules() {}

    private static final Logger LOG = LogUtils.getLogger();
    /** Guards building and dropping {@link #rules}. Only {@link #invalidate} (on the thread that fires the config
     *  event, e.g. FML's config watcher, which holds the file's lock) and a rebuild take it. A rebuild takes no config
     *  lock, so the two cannot deadlock. */
    private static final Object LOCK = new Object();
    private static volatile Rules rules;

    /** Called by {@link MchConfig} whenever the server file is loaded, reloaded or unloaded. Waits for a rebuild in
     *  progress, so the Rules it built from the old file cannot be published after this drop. */
    static void invalidate() {
        synchronized (LOCK) {
            rules = null;
        }
    }

    private static Rules rules() {
        Rules r = rules;
        if (r == null) {
            synchronized (LOCK) {
                r = rules;
                if (r == null) {
                    r = new Rules(MchConfig.SERVER);
                    rules = r;
                }
            }
        }
        return r;
    }

    // ---- blocks ----

    /** Reference {@code bulletBreakableBlocks.contains(block)} (MCH_EntityBaseBullet:634, MCH_EntityBullet:69). */
    public static boolean bulletBreaks(BlockState s) {
        return rules().bulletBreakable.matches(s);
    }

    /** Reference {@code getNoBreakableBlockListFromType(weightType)}: stops the crush and abandons the column. */
    public static boolean crushNoBreak(BlockState s, int weightType) {
        Rules r = rules();
        return weightType == 2 ? r.tankNoBreak.matches(s) : weightType == 1 && r.carNoBreak.matches(s);
    }

    /** Reference {@code getBreakableBlockListFromType(weightType)}: breaks, then abandons the column. */
    public static boolean crushBreaks(BlockState s, int weightType) {
        Rules r = rules();
        return weightType == 2 ? r.tankBreakable.matches(s) : weightType == 1 && r.carBreakable.matches(s);
    }

    /** The block ids/tags written into a breakable-MATERIAL list: break and continue down the column. The bare
     *  material names of that list are {@link #crushMaterialNames}. */
    public static boolean crushMaterialBlock(BlockState s, int weightType) {
        Rules r = rules();
        return weightType == 2 ? r.tankMaterialBlocks.matches(s) : weightType == 1 && r.carMaterialBlocks.matches(s);
    }

    /** The bare 1.7.10 material names of a weight type's breakable-material list (lower-cased), for
     *  {@code MchCrushableBlocks} to map onto 1.21 blocks. Reference {@code getBreakableMaterialListFromType}. */
    public static Set<String> crushMaterialNames(int weightType) {
        Rules r = rules();
        return weightType == 2 ? r.tankMaterialNames : weightType == 1 ? r.carMaterialNames : Set.of();
    }

    // ---- damage ----

    /**
     * Reference {@code MCH_Config.applyDamageVsEntity} (:621-651): scale damage an MCHeli weapon (bullet, explosion,
     * burning wreck) deals to {@code target} by the list for its kind. The kind order is the reference's: MCHeli
     * aircraft, tank, vehicle, any other MCHeli entity, player, living, anything else.
     */
    public static float applyDamageVsEntity(Entity target, float damage) {
        if (target == null) {
            return damage;
        }
        Rules r = rules();
        String name = target.getClass().getName();
        MchDamageFactorList list;
        if (target instanceof MchHelicopter || target instanceof MchPlane) {
            list = r.vsAircraft;
        } else if (target instanceof MchTank) {
            list = r.vsTank;
        } else if (target instanceof MchGroundVehicle) {
            list = r.vsVehicle;
        } else if (name.contains("mcheli.")) {
            list = r.vsOther;
        } else if (target instanceof Player) {
            list = r.vsPlayer;
        } else if (target instanceof LivingEntity) {
            list = r.vsLiving;
        } else {
            list = r.vsEntity;
        }
        return list.apply(damage, name);
    }

    /**
     * Reference {@code MCH_Config.applyDamageByExternal} (:595-619): scale damage an MCHeli entity takes from
     * {@code src}, matched against the attacker's and the direct source's class names.
     */
    public static float applyDamageByExternal(Entity target, DamageSource src, float damage) {
        Rules r = rules();
        MchDamageFactorList list;
        if (target instanceof MchHelicopter || target instanceof MchPlane) {
            list = r.byExternalAircraft;
        } else if (target instanceof MchTank) {
            list = r.byExternalTank;
        } else if (target instanceof MchGroundVehicle) {
            list = r.byExternalVehicle;
        } else {
            list = r.byExternalOther;
        }
        Entity attacker = src != null ? src.getEntity() : null;
        Entity direct = src != null ? src.getDirectEntity() : null;
        return list.apply(damage,
            attacker != null ? attacker.getClass().getName() : null,
            direct != null ? direct.getClass().getName() : null);
    }

    /** Reference {@code IgnoreBulletHit} (MCH_EntityBaseBullet:735-739): an MCHeli bullet never hits this entity. */
    public static boolean ignoresBulletHit(Entity e) {
        return e != null && MchDamageFactorList.classNameMatchesAny(e.getClass().getName(), rules().ignoreBulletHit);
    }

    // =================================================================================================================

    private static final class Rules {
        final BlockMatcher bulletBreakable;
        final BlockMatcher carBreakable;
        final BlockMatcher carNoBreak;
        final BlockMatcher tankBreakable;
        final BlockMatcher tankNoBreak;
        final BlockMatcher carMaterialBlocks;
        final BlockMatcher tankMaterialBlocks;
        final Set<String> carMaterialNames;
        final Set<String> tankMaterialNames;
        final List<? extends String> ignoreBulletHit;
        final MchDamageFactorList vsEntity;
        final MchDamageFactorList vsLiving;
        final MchDamageFactorList vsPlayer;
        final MchDamageFactorList vsAircraft;
        final MchDamageFactorList vsTank;
        final MchDamageFactorList vsVehicle;
        final MchDamageFactorList vsOther;
        final MchDamageFactorList byExternalAircraft;
        final MchDamageFactorList byExternalTank;
        final MchDamageFactorList byExternalVehicle;
        final MchDamageFactorList byExternalOther;

        Rules(MchConfig.Server c) {
            this.bulletBreakable = BlockMatcher.parse("bulletBreakableBlocks", list(c.bulletBreakableBlocks), false);
            this.carBreakable = BlockMatcher.parse("collisionCarBreakableBlocks", list(c.collisionCarBreakableBlocks), false);
            this.carNoBreak = BlockMatcher.parse("collisionCarNoBreakBlocks", list(c.collisionCarNoBreakBlocks), false);
            this.tankBreakable = BlockMatcher.parse("collisionTankBreakableBlocks", list(c.collisionTankBreakableBlocks), false);
            this.tankNoBreak = BlockMatcher.parse("collisionTankNoBreakBlocks", list(c.collisionTankNoBreakBlocks), false);
            this.carMaterialBlocks = BlockMatcher.parse("collisionCarBreakableMaterials", list(c.collisionCarBreakableMaterials), true);
            this.tankMaterialBlocks = BlockMatcher.parse("collisionTankBreakableMaterials", list(c.collisionTankBreakableMaterials), true);
            this.carMaterialNames = materialNames(list(c.collisionCarBreakableMaterials));
            this.tankMaterialNames = materialNames(list(c.collisionTankBreakableMaterials));
            this.ignoreBulletHit = list(c.ignoreBulletHit);
            this.vsEntity = MchDamageFactorList.parse(list(c.damageVsEntity));
            this.vsLiving = MchDamageFactorList.parse(list(c.damageVsLiving));
            this.vsPlayer = MchDamageFactorList.parse(list(c.damageVsPlayer));
            this.vsAircraft = MchDamageFactorList.parse(list(c.damageVsMCHeliAircraft));
            this.vsTank = MchDamageFactorList.parse(list(c.damageVsMCHeliTank));
            this.vsVehicle = MchDamageFactorList.parse(list(c.damageVsMCHeliVehicle));
            this.vsOther = MchDamageFactorList.parse(list(c.damageVsMCHeliOther));
            this.byExternalAircraft = MchDamageFactorList.parse(list(c.damageMCHeliAircraftByExternal));
            this.byExternalTank = MchDamageFactorList.parse(list(c.damageMCHeliTankByExternal));
            this.byExternalVehicle = MchDamageFactorList.parse(list(c.damageMCHeliVehicleByExternal));
            this.byExternalOther = MchDamageFactorList.parse(list(c.damageMCHeliOtherByExternal));
        }

        private static List<? extends String> list(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<List<? extends String>> v) {
            List<? extends String> l = MchConfig.getUncached(MchConfig.SERVER_SPEC, v);
            return l != null ? l : List.of();
        }

        private static Set<String> materialNames(List<? extends String> entries) {
            Set<String> out = new HashSet<>();
            for (String e : entries) {
                String t = e == null ? "" : e.trim();
                if (!t.isEmpty() && !t.startsWith("#") && t.indexOf(':') < 0) {
                    out.add(t.toLowerCase(java.util.Locale.ROOT));
                }
            }
            return Set.copyOf(out);
        }
    }

    /** A parsed list of block ids and {@code #tags}. Unknown ids are skipped with one warning. */
    private static final class BlockMatcher {
        private final Set<Block> blocks;
        private final List<TagKey<Block>> tags;

        private BlockMatcher(Set<Block> blocks, List<TagKey<Block>> tags) {
            this.blocks = blocks;
            this.tags = tags;
        }

        /** @param qualifiedOnly true for a material list: only entries with ':' or a leading '#' are blocks/tags */
        static BlockMatcher parse(String option, List<? extends String> entries, boolean qualifiedOnly) {
            Set<Block> blocks = new HashSet<>();
            List<TagKey<Block>> tags = new ArrayList<>();
            for (String e : entries) {
                String t = e == null ? "" : e.trim();
                if (t.isEmpty() || (qualifiedOnly && !t.startsWith("#") && t.indexOf(':') < 0)) {
                    continue;
                }
                boolean tag = t.startsWith("#");
                ResourceLocation id = ResourceLocation.tryParse(tag ? t.substring(1) : t);
                if (id == null) {
                    LOG.warn("MCHeli config {}: '{}' is not a block id or #tag, ignored", option, t);
                } else if (tag) {
                    tags.add(TagKey.create(Registries.BLOCK, id));
                } else if (BuiltInRegistries.BLOCK.containsKey(id)) {
                    blocks.add(BuiltInRegistries.BLOCK.get(id));
                } else {
                    LOG.warn("MCHeli config {}: unknown block '{}', ignored", option, t);
                }
            }
            return new BlockMatcher(Set.copyOf(blocks), List.copyOf(tags));
        }

        boolean matches(BlockState s) {
            if (this.blocks.contains(s.getBlock())) {
                return true;
            }
            for (TagKey<Block> t : this.tags) {
                if (s.is(t)) {
                    return true;
                }
            }
            return false;
        }
    }
}
