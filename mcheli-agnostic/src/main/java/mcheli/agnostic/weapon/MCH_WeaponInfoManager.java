package mcheli.agnostic.weapon;

import java.util.LinkedHashMap;
import java.util.Map;
import mcheli.agnostic.info.MCH_BaseInfo;
import mcheli.agnostic.info.MCH_InfoManagerBase;

/**
 * Loads + holds the parsed weapon definitions ({@code weapons/*.txt} → {@link MCH_WeaponInfo}). A thin subclass of
 * {@link MCH_InfoManagerBase}, mirroring the vehicle managers: the base's {@code load(ResourceSource, Logger,
 * "weapons")} does the DSL parsing/validation; this only supplies the concrete {@link MCH_WeaponInfo} factory and the
 * name→info map. Vehicles reference these by name through their {@code AddWeapon} mounts.
 */
public final class MCH_WeaponInfoManager extends MCH_InfoManagerBase {

    private static final MCH_WeaponInfoManager INSTANCE = new MCH_WeaponInfoManager();
    private final Map<String, MCH_BaseInfo> map = new LinkedHashMap<>();

    private MCH_WeaponInfoManager() {}

    public static MCH_WeaponInfoManager getInstance() {
        return INSTANCE;
    }

    /** The parsed weapon for {@code name} (e.g. "m230", "hydra70"), or null if not loaded. */
    public static MCH_WeaponInfo get(String name) {
        MCH_BaseInfo info = INSTANCE.map.get(name);
        return info instanceof MCH_WeaponInfo ? (MCH_WeaponInfo) info : null;
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
