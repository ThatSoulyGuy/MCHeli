package mcheli.agnostic.hud;

import mcheli.agnostic.eval.MchExpr;

/**
 * The live vehicle/player state the HUD reads — implemented dependent-side against the ridden entity. It is the
 * {@link MchExpr.VarLookup} for expression variables ({@code altitude}, {@code hp_rto}, {@code throttle}, …) and also
 * resolves the typed {@code DrawString} format arguments ({@code ALTITUDE}→int, {@code WPN_NAME}→String,
 * {@code PLYR_YAW}→double, …) and supplies the GUI-scaled screen size.
 */
public interface HudState extends MchExpr.VarLookup {

    // double get(String name) — inherited: an expression variable's value (unknown keys return 0 for HUD robustness).

    /** The typed value for a {@code DrawString} format argument name (Integer / Double / String), or {@code ""}. */
    Object formatArg(String argName);

    /** GUI-scaled screen width (the HUD's centre origin is {@code width/2}). */
    int screenWidth();

    /** GUI-scaled screen height. */
    int screenHeight();

    /** Line thickness in pixels (the reference used {@code glLineWidth(guiScale)}). */
    float lineWidth();
}
