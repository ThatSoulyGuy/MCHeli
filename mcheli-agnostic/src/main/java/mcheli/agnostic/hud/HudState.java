package mcheli.agnostic.hud;

import java.util.Set;

/**
 * The live vehicle/player state the HUD reads, implemented dependent-side against the ridden entity. It supplies the
 * built-in expression variables ({@link #VARIABLES}: {@code altitude}, {@code hp_rto}, {@code throttle}, …), the typed
 * {@code DrawString} format arguments ({@code ALTITUDE}→Integer, {@code WPN_NAME}→String, {@code PITCH}→Float, …) and
 * the GUI-scaled screen size.
 */
public interface HudState {

    /**
     * Every built-in expression variable: the keys the reference {@code MCH_HudItem.updateVarMap} and
     * {@code updateVarMap_Weapon} put into the variable map each frame. Any other name is undefined, as in the reference
     * (EvalException 2103), unless an expression assigned it. {@code color} is supplied by {@link MCH_Hud} itself (the
     * live current colour) and is never asked of the state.
     */
    Set<String> VARIABLES = Set.of(
        "color", "center_x", "center_y", "width", "height", "time", "test_mode", "plyr_yaw", "plyr_pitch", "yaw",
        "pitch", "roll", "altitude", "sea_alt", "have_radar", "radar_rot", "hp", "max_hp", "hp_rto", "throttle", "pos_x",
        "pos_y", "pos_z", "motion_x", "motion_y", "motion_z", "speed", "fuel", "low_fuel", "stick_x", "stick_y",
        "reloading", "reload_time", "wpn_heat", "is_heat_wpn", "sight_type", "lock", "dsp_mt_dist", "mt_dist",
        "vtol_stat", "free_look", "gunner_mode", "cam_mode", "cam_zoom", "auto_pilot", "have_flare", "can_flare",
        "inventory", "hovering", "is_uav", "uav_fs");

    /** Returned by {@link #formatArg} to suppress the whole {@code DrawString}: when a weapon argument ({@code WPN_*},
     *  {@code RELOAD_*}, {@code MORTAR_DIST}) has no current weapon, and when {@code WPN_AMMO}/{@code WPN_RM_AMMO}
     *  belongs to a weapon without a magazine ({@code getAmmoNumMax() <= 0}), the checks of the reference
     *  {@code MCH_HudItemString.execute}.
     *
     *  <p>Deliberate divergence on "no current weapon": the reference tested the static {@code MCH_HudItem.CurrentWeapon},
     *  which {@code updateWeapon} (MCH_HudItem:469-472) sets but never clears. It skipped only until the first weapon
     *  of the session had been seen; after that, a seat or vehicle without a weapon drew the last weapon's stale
     *  {@code WeaponName}/{@code WeaponAmmo} strings. The port asks for the viewer's current weapon every frame and
     *  skips whenever there is none, rather than show another weapon's readout. */
    Object SKIP_STRING = new Object() {
        @Override
        public String toString() {
            return "<skip DrawString>";
        }
    };

    /** The current value of the built-in variable {@code name}, which is one of {@link #VARIABLES} other than
     *  {@code color}. A feature the port lacks reports the reference's "feature off" value. */
    double get(String name);

    /** The value of format argument {@code argName} (an {@link MCH_HudItemStringArgs} name other than {@code NONE}),
     *  boxed as the reference boxed it (Integer, Float, Double, String, Date), so {@code %d}/{@code %f}/{@code %s} format
     *  identically. May return {@link #SKIP_STRING}. */
    Object formatArg(String argName);

    /** GUI-scaled screen width (the HUD's centre origin is {@code width/2}). */
    int screenWidth();

    /** GUI-scaled screen height. */
    int screenHeight();

    /** Line thickness in pixels (the reference used {@code glLineWidth(guiScale)}). */
    float lineWidth();

    /** Neutral radar contacts as relative {@code (x0,z0,x1,z1,…)} block offsets from the vehicle, for
     *  {@code DrawEntityRadar}. Default: none (a vehicle with no radar, or a headless stub). */
    default double[] radarEntities() { return NO_BLIPS; }

    /** Hostile radar contacts (same layout) for {@code DrawEnemyRadar}. Default: none. */
    default double[] radarEnemies() { return NO_BLIPS; }

    /** The camera's pitch in degrees, for the {@code DrawCameraRot} marker. The reference {@code MCH_Camera} took the
     *  riding player's look every tick ({@code updateCameraRotate(player.rotationYaw, player.rotationPitch)}, pilot and
     *  gunner alike), so this is the viewer's look pitch. */
    default double cameraPitchDeg() { return 0.0; }

    /** The camera's yaw relative to the hull in degrees ({@code cameraYaw − hullYaw}, normalized; the camera yaw is the
     *  viewer's look yaw), for the {@code DrawCameraRot} marker. */
    default double cameraYawDiffDeg() { return 0.0; }

    double[] NO_BLIPS = new double[0];
}
