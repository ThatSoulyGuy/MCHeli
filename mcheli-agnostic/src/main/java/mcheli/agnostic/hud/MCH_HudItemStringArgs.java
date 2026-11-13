package mcheli.agnostic.hud;

import java.util.Locale;

/**
 * The {@code DrawString} format-argument names (port of the reference {@code mcheli.hud.MCH_HudItemStringArgs}, same
 * constants in the same order). {@link HudState#formatArg} supplies each value except {@link #NONE}, which formats as
 * the reference's {@code null}.
 */
public enum MCH_HudItemStringArgs {
    NONE,
    NAME,
    ALTITUDE,
    DATE,
    MC_THOR,
    MC_TMIN,
    MC_TSEC,
    MAX_HP,
    HP,
    HP_PER,
    POS_X,
    POS_Y,
    POS_Z,
    MOTION_X,
    MOTION_Y,
    MOTION_Z,
    INVENTORY,
    WPN_NAME,
    WPN_AMMO,
    WPN_RM_AMMO,
    RELOAD_PER,
    RELOAD_SEC,
    MORTAR_DIST,
    MC_VER,
    MOD_VER,
    MOD_NAME,
    YAW,
    PITCH,
    ROLL,
    PLYR_YAW,
    PLYR_PITCH,
    TVM_POS_X,
    TVM_POS_Y,
    TVM_POS_Z,
    TVM_DIFF,
    CAM_ZOOM,
    UAV_DIST,
    KEY_GUI,
    THROTTLE;

    /** Reference {@code toArgs}: the constant named {@code name} (case-insensitive), or {@link #NONE} for an unknown name.
     *  The reference printed a stack trace for an unknown name; the HUD parser logs a warning instead. */
    public static MCH_HudItemStringArgs toArgs(String name) {
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
