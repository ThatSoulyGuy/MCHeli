package mcheli.agnostic.weapon;

import java.util.LinkedHashMap;
import java.util.Map;
import mcheli.agnostic.info.MCH_BaseInfo;
import mcheli.agnostic.info.MCH_InfoManagerBase;
import mcheli.agnostic.spi.ItemHandle;
import mcheli.agnostic.spi.Logger;
import mcheli.agnostic.spi.ResourceSource;

/**
 * Loads + holds the parsed weapon definitions ({@code weapons/*.txt} → {@link MCH_WeaponInfo}). A thin subclass of
 * {@link MCH_InfoManagerBase}, mirroring the vehicle managers: the base's {@code load(ResourceSource, Logger,
 * "weapons")} does the DSL parsing/validation; this only supplies the concrete {@link MCH_WeaponInfo} factory and the
 * name→info map. Vehicles reference these by name through their {@code AddWeapon} mounts.
 *
 * <p>Load order matters, as in the reference ({@code MCH_MOD.PreInit}:138-142 loads weapons before every vehicle
 * kind): once weapons are {@link #isLoaded loaded}, a vehicle's {@code AddWeapon} line naming an unknown weapon is
 * dropped at parse time ({@link #contains}), so {@code weaponSetList} indices match the reference.
 */
public final class MCH_WeaponInfoManager extends MCH_InfoManagerBase {

    private static final MCH_WeaponInfoManager INSTANCE = new MCH_WeaponInfoManager();
    private final Map<String, MCH_BaseInfo> map = new LinkedHashMap<>();
    private volatile boolean loaded;

    private MCH_WeaponInfoManager() {}

    public static MCH_WeaponInfoManager getInstance() {
        return INSTANCE;
    }

    /** The parsed weapon for {@code name} (e.g. "m230", "hydra70"), or null if not loaded. */
    public static MCH_WeaponInfo get(String name) {
        MCH_BaseInfo info = INSTANCE.map.get(name);
        return info instanceof MCH_WeaponInfo ? (MCH_WeaponInfo) info : null;
    }

    /** Reference {@code MCH_WeaponInfoManager.contains}: is there a weapon definition called {@code name}? */
    public static boolean contains(String name) {
        return name != null && INSTANCE.map.containsKey(name);
    }

    /**
     * True once {@link #load} has run (even if it found no files). Until then the vehicle parser cannot know which
     * weapons exist, so it keeps every {@code AddWeapon} line; that only happens when a vehicle definition is parsed
     * in isolation (unit tests), since the mod loads weapons first.
     */
    public static boolean isLoaded() {
        return INSTANCE.loaded;
    }

    @Override
    public boolean load(ResourceSource res, Logger log, String type) {
        try {
            return super.load(res, log, type);
        } finally {
            this.loaded = true;
        }
    }

    /** Forget every definition and the loaded state (the reference's {@code reload()} began with {@code map.clear()}). */
    public void clear() {
        this.map.clear();
        this.loaded = false;
    }

    /**
     * Resolve every weapon's item names to handles ({@link MCH_WeaponInfo#resolveItems}) and warn about names the
     * resolver does not know. The port of the reference's {@code setRoundItems} (MCH_WeaponInfoManager:102-108, run
     * from postInit once every item exists); the dependent layer calls it after the item registry is frozen.
     *
     * @return the number of item names that did not resolve
     */
    public int resolveItems(ItemHandle.Resolver resolver, Logger log) {
        int unresolved = 0;
        for (MCH_BaseInfo b : this.map.values()) {
            if (!(b instanceof MCH_WeaponInfo w)) {
                continue;
            }
            w.resolveItems(resolver);
            if (w.dispenseItem == null && w.dispenseItemName != null && !w.dispenseItemName.isEmpty()) {
                unresolved++;
                if (log != null) {
                    log.warn("Weapon %s: unknown DispenseItem '%s' (it will dispense nothing)", w.name, w.dispenseItemName);
                }
            }
            for (MCH_WeaponInfo.RoundItem r : w.roundItems) {
                if (r.item == null) {
                    unresolved++;
                    if (log != null) {
                        log.warn("Weapon %s: unknown ammo Item '%s' (it can never be resupplied)", w.name, r.itemName);
                    }
                }
            }
        }
        return unresolved;
    }

    @Override
    public MCH_BaseInfo newInfo(String name) {
        return new MCH_WeaponInfo(name);
    }

    @Override
    public Map getMap() {
        return this.map;
    }
}
