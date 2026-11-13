package mcheli.agnostic.weapon;

/**
 * What a weapon MODE means, per weapon type — the per-subclass {@code switchMode}/{@code getName}/{@code shot} logic of
 * the reference ({@code MCH_WeaponBase.switchMode}, {@code MCH_WeaponMachineGun2}, {@code MCH_WeaponRocket},
 * {@code MCH_WeaponATMissile}, {@code MCH_WeaponTvMissile}) as pure functions of the config and the current mode. The
 * live mode itself is held by {@link WeaponSlot}. ZERO Minecraft imports.
 *
 * <p>Bundled multi-mode weapons: the {@code MachineGun2} cannons with {@code ModeNum = 2} (AP / HE), the
 * {@code Rocket}s with {@code ModeNum = 2} (plain / HEIAP piercing), the {@code ATMissile}s with {@code ModeNum = 2}
 * (direct / top attack) and the {@code TVMissile}s with {@code ModeNum = 2} (TV-guided / fire-and-forget).
 */
public final class MCH_WeaponMode {

    private MCH_WeaponMode() {}

    /**
     * How many modes the weapon cycles through. The reference {@code MCH_WeaponCreator} overwrites every subclass's
     * constructor default with {@code numMode = info.modeNum}; only {@code MCH_WeaponMachineGun2.modifyParameters} then
     * drops a gun without an explosion to 0 (there is no HE round to switch to).
     */
    public static int numMode(MCH_WeaponInfo info) {
        if (info == null) {
            return 0;
        }
        if ("machinegun2".equalsIgnoreCase(info.type) && info.explosion == 0) {
            return 0;
        }
        return info.modeNum;
    }

    /** The mode a shot uses (reference {@code MCH_WeaponBase.getCurrentMode}): a config {@code FixMode} wins. */
    public static int effectiveMode(MCH_WeaponInfo info, int currentMode) {
        return info != null && info.fixMode > 0 ? info.fixMode : currentMode;
    }

    /** Reference {@code MCH_WeaponBase.switchMode}: the next mode, or the same one when the weapon cannot switch
     *  (a fixed mode, or fewer than two modes). */
    public static int nextMode(MCH_WeaponInfo info, int currentMode) {
        if (info != null && info.fixMode > 0) {
            return currentMode;
        }
        int n = numMode(info);
        return n > 0 ? (currentMode + 1) % n : 0;
    }

    /** The display-name suffix of a mode (the reference subclasses' {@code getName()} overrides). */
    public static String suffix(String type, int mode) {
        if (type == null) {
            return "";
        }
        switch (type.toLowerCase()) {
            case "machinegun2":
                return mode == 0 ? "" : " [HE]";
            case "rocket":
                return mode == 0 ? "" : " [HEIAP]";
            case "atmissile":
                return mode == 1 ? " [TA]" : "";
            case "tvmissile":
                return mode == 0 ? " [TV]" : mode == 2 ? " [TA]" : "";
            default:
                return "";
        }
    }

    /**
     * The explosion power a round of this mode carries. Reference {@code MCH_WeaponMachineGun2.shot}: with
     * {@code ModeNum < 2} the round always explodes; otherwise mode 0 fires the non-explosive round (the reference stores
     * {@code -explosionPower}, which only plays an explosion sound on impact; the port's round simply does not explode)
     * and mode 1 the HE round. Every other type keeps its configured explosion.
     */
    public static int explosion(MCH_WeaponInfo info, int mode) {
        if ("machinegun2".equalsIgnoreCase(info.type) && info.modeNum >= 2 && mode == 0) {
            return 0;
        }
        return info.explosion;
    }

    /** Reference {@code MCH_WeaponRocket.shot}: {@code if (prm.option1 == 0 && numMode > 1) piercing = 0} — only the
     *  HEIAP mode of a multi-mode rocket pierces. */
    public static int piercing(MCH_WeaponInfo info, int mode) {
        if ("rocket".equalsIgnoreCase(info.type) && mode == 0 && numMode(info) > 1) {
            return 0;
        }
        return info.piercing;
    }

    /** Reference {@code MCH_WeaponATMissile.shotServer}: {@code e.guidanceType = prm.option2 (= the mode)} — mode 1 is
     *  the top-attack profile. */
    public static boolean isTopAttack(MCH_WeaponInfo info, int mode) {
        return info != null && "atmissile".equalsIgnoreCase(info.type) && mode == 1;
    }

    /** Reference {@code MCH_WeaponTvMissile.shotServer}: {@code isTVGuided = prm.option1 == 0}; an unguided TV missile
     *  flies straight at 1.5x the acceleration. */
    public static boolean isTvGuided(MCH_WeaponInfo info, int mode) {
        return info != null && "tvmissile".equalsIgnoreCase(info.type) && mode == 0;
    }
}
