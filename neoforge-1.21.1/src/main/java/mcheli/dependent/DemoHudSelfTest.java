package mcheli.dependent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.helicopter.MCH_HeliInfoManager;
import mcheli.agnostic.hud.HudExpression;
import mcheli.agnostic.hud.HudRenderer;
import mcheli.agnostic.hud.HudState;
import mcheli.agnostic.hud.HudVariables;
import mcheli.agnostic.hud.MCH_Hud;
import mcheli.agnostic.hud.MCH_HudItemStringArgs;
import mcheli.agnostic.hud.MCH_HudManager;
import mcheli.agnostic.plane.MCP_PlaneInfoManager;
import mcheli.agnostic.tank.MCH_TankInfoManager;
import mcheli.agnostic.vehicle.MCH_VehicleInfoManager;

/**
 * Headless proof of the config-driven HUD pipeline (rendering itself needs a client, but the eval engine, the DSL
 * parse, and the whole item-execution path are agnostic and testable): (1) the {@code mcheli.eval} engine evaluates
 * representative HUD expressions correctly (precedence, ternary, hex, logical, comparison); (2) the real {@code hud/*}
 * configs parse; (3) every HUD a loaded vehicle config names (plus the four demo HUDs, and every HUD they
 * {@code Call}) draws into a capturing renderer against a stub with the LIVE state's shape (the boxed format-arg types
 * {@code MchHudVarState} returns) with ZERO draw-time item failures. {@link MCH_Hud} only logs such a failure once and
 * keeps drawing, so the test counts them through {@link MCH_Hud.DrawErrorListener} rather than trusting that the HUD
 * emitted something.
 */
public final class DemoHudSelfTest extends MchSelfTest {

    @Override
    protected int[] areaChunks() {
        return new int[] {0, 0, 0, 0}; // no world needed
    }

    @Override
    protected void start() {
        // 1. Expression engine faithfulness.
        boolean evalOk =
            approx(ev("2+3*4", Map.of()), 14.0)                                     // precedence
            && approx(ev("altitude>10? 100: 200", Map.of("altitude", 20.0)), 100.0) // ternary + comparison
            && ((int) (long) ev("0xFF28d448", Map.of())) == 0xFF28D448              // hex -> ARGB narrowing
            && approx(ev("-133+8+stick_x*12", Map.of("stick_x", 1.0)), -113.0)      // unary + mul
            && approx(ev("is_uav || gunner_mode", Map.of("is_uav", 0.0, "gunner_mode", 0.0)), 0.0)
            && approx(ev("hp_rto>0.2? 1: 0", Map.of("hp_rto", 0.87)), 1.0)          // double-path (not int-truncated)
            && approx(ev("fuel*270-135", Map.of("fuel", 0.5)), 0.0);
        LOG.info("[HUD-SELFTEST] eval engine (precedence/ternary/hex/logical/double) -> {}", evalOk ? "OK" : "FAIL");

        // 2. Real HUD configs parsed.
        MCH_Hud heli = MCH_HudManager.get("heli");
        boolean loaded = heli != null && !heli.isEmpty()
            && MCH_HudManager.get("common_pilot") != null
            && MCH_HudManager.get("plane") != null
            && MCH_HudManager.get("mbt_hud") != null;
        LOG.info("[HUD-SELFTEST] configs parsed: heli={} common_pilot={} plane={} mbt_hud={}",
            heli != null, MCH_HudManager.get("common_pilot") != null,
            MCH_HudManager.get("plane") != null, MCH_HudManager.get("mbt_hud") != null);

        // 3. Draw every HUD the loaded vehicle configs name (parse + eval + item execution + the Call recursion) against
        //    a stub with the live state's shape. A draw-time item failure is skipped and logged once by MCH_Hud, so
        //    the listener counts every one of them: the pipeline passes only with output AND zero failures.
        boolean drew = true;
        int radarPoints = 0; // blips emitted across the radar-equipped HUDs (have_radar=1 in the stub)
        List<String> errors = new ArrayList<>();
        MCH_Hud.DrawErrorListener onError = (file, line, e) -> errors.add(file + ":" + line + " " + e);
        Stub stub = new Stub();
        int drawnHuds = 0;
        for (String h : configHuds()) {
            MCH_Hud hud = MCH_HudManager.get(h);
            if (hud == null) {
                drew &= !DEMO_HUDS.contains(h); // a config may name a HUD no pack ships; the demo four must exist
                continue;
            }
            Capture cap = new Capture();
            hud.draw(cap, stub, MCH_HudManager::get, onError);
            int total = cap.strings + cap.fills + cap.lines + cap.textures;
            if (DEMO_HUDS.contains(h)) {
                LOG.info("[HUD-SELFTEST] {} HUD drew: strings={} fills={} lines={} textures={} points={}",
                    h, cap.strings, cap.fills, cap.lines, cap.textures, cap.points);
                drew &= total > 0;
            }
            radarPoints += cap.points;
            drawnHuds++;
        }
        boolean noDrawErrors = errors.isEmpty();
        LOG.info("[HUD-SELFTEST] {} config-named HUDs drawn, draw-time item failures -> {} {}", drawnHuds,
            errors.size(), errors.size() > 8 ? errors.subList(0, 8) + " ..." : errors);
        // The stub reports have_radar=1 + sample contacts, so the radar dial's DrawEntityRadar/DrawEnemyRadar must have
        // projected at least one blip into a point emission (proves the radar draw path executes end-to-end).
        boolean radarOk = radarPoints > 0;
        LOG.info("[HUD-SELFTEST] radar blips emitted across radar HUDs -> {} ({})", radarOk ? "OK" : "FAIL", radarPoints);

        // 4. Format-arg TYPES match their %-specs (the tank HUD's ungated x%.1f CAM_ZOOM + %02d MC_THOR would crash on
        //    a wrong type). Verify String.format succeeds AND produces the expected text.
        boolean fmtOk;
        try {
            fmtOk = "x1.0".equals(String.format("x%.1f", stub.formatArg("CAM_ZOOM")))
                && "06:00".equals(String.format("%02d:%02d", stub.formatArg("MC_THOR"), stub.formatArg("MC_TMIN")));
        } catch (Exception e) {
            fmtOk = false;
        }
        LOG.info("[HUD-SELFTEST] format-arg types (CAM_ZOOM %.1f, MC_THOR/MC_TMIN %02d) -> {}", fmtOk ? "OK" : "FAIL");

        boolean pass = evalOk && loaded && drew && noDrawErrors && fmtOk && radarOk;
        finish(pass, "eval={} configsLoaded={} pipelineDrew={} drawErrors={} formatTypes={} radar={}",
            evalOk, loaded, drew, errors.size(), fmtOk, radarOk);
    }

    /** The four demo vehicles' HUDs, which must exist and draw something. */
    private static final Set<String> DEMO_HUDS = Set.of("heli", "plane", "mbt_hud", "vehicle");

    /** Every HUD name a loaded vehicle config lists ({@code AddHud}, or the category default per seat), plus the demo
     *  four; sorted so the log reads the same every run. */
    @SuppressWarnings("rawtypes")
    private static Set<String> configHuds() {
        Set<String> out = new TreeSet<>(DEMO_HUDS);
        for (mcheli.agnostic.info.MCH_InfoManagerBase mgr : List.of(MCH_HeliInfoManager.getInstance(),
                MCP_PlaneInfoManager.getInstance(), MCH_TankInfoManager.getInstance(),
                MCH_VehicleInfoManager.getInstance())) {
            Map map = mgr.getMap();
            if (map == null) {
                continue;
            }
            for (Object v : map.values()) {
                if (v instanceof MCH_AircraftInfo info && info.hudList != null) {
                    for (String h : info.hudList) {
                        if (h != null && !h.isEmpty()) {
                            out.add(h.toLowerCase(java.util.Locale.ROOT));
                        }
                    }
                }
            }
        }
        return out;
    }

    private static double ev(String expr, Map<String, Double> vars) {
        return HudExpression.compile(HudExpression.toFormula(expr)).eval(new HudVariables(n -> vars.getOrDefault(n, 0.0)));
    }

    private static boolean approx(double a, double b) {
        return Math.abs(a - b) < 1.0e-6;
    }

    /** A HudRenderer that just tallies emitted draw commands. */
    private static final class Capture implements HudRenderer {
        int strings;
        int fills;
        int lines;
        int textures;
        int points; // radar blips (each call may carry several)

        @Override public void string(String t, int x, int y, int argb, boolean c) { this.strings++; }
        @Override public void fill(int a, int b, int c, int d, int argb) { this.fills++; }
        @Override public void line(double[] p, int argb, int m, float w) { this.lines++; }
        @Override public void texture(String n, double x, double y, double w, double h, double u, double v,
                                      double uw, double vh, double r) { this.textures++; }
        @Override public void points(double[] centers, int argb, double size) { this.points += centers.length / 2; }
    }

    /** A stub HudState with plausible flight values (every other variable 0) and the live state's format-arg types —
     *  {@code MchHudVarState} itself needs the client (key bindings, the gunner view), which a dedicated server lacks. */
    private static final class Stub implements HudState {
        @Override public double get(String name) {
            return switch (name) {
                case "altitude", "sea_alt" -> 100.0;
                case "throttle", "fuel", "hp_rto" -> 0.5;
                case "plyr_yaw", "yaw" -> 45.0;
                case "plyr_pitch", "pitch" -> -5.0;
                case "roll" -> 0.0;
                case "speed" -> 0.3;
                case "have_radar" -> 1.0;   // exercise the radar dial block
                case "radar_rot" -> 30.0;
                case "sight_type" -> 1.0;   // exercise the rocket-sight reticle in hud/sight.txt
                default -> 0.0;
            };
        }
        // Two in-range contacts (|x,z| well under the 64-block range) so the radar projection emits blips.
        @Override public double[] radarEntities() { return new double[]{10.0, 20.0}; }
        @Override public double[] radarEnemies() { return new double[]{-15.0, 5.0}; }
        /** The LIVE state's shape: the exact boxed type {@code MchHudVarState.formatArg} returns for each argument
         *  (Integer for {@code %d}, Float or Double for {@code %f}, a Date, pre-formatted Strings). The switch is
         *  exhaustive, so a new argument cannot be added without giving it a type here too. Never SKIP_STRING (the
         *  viewer has a weapon), so every DrawString formats. */
        @Override public Object formatArg(String argName) {
            return switch (MCH_HudItemStringArgs.toArgs(argName)) {
                case NAME -> "AH-64D";
                case ALTITUDE -> 100;                          // Integer
                case DATE -> new java.util.Date(0L);
                case MC_THOR -> 6;                             // Integer
                case MC_TMIN, MC_TSEC -> 0;                    // Integer
                case MAX_HP, HP -> 100;                        // Integer
                case HP_PER -> 50.0;                           // Double
                case POS_X, POS_Y, POS_Z, MOTION_X, MOTION_Y, MOTION_Z -> 0.0; // Double
                case INVENTORY -> 0;                           // Integer
                case WPN_NAME -> "M230";
                case WPN_AMMO, WPN_RM_AMMO -> String.format("%4d", 30); // pre-formatted String
                case RELOAD_PER -> 100.0F;                     // Float
                case RELOAD_SEC, MORTAR_DIST -> 0.0F;          // Float
                case MC_VER -> "1.21.1";
                case MOD_VER -> "0";
                case MOD_NAME -> "MC Helicopter MOD";
                case YAW, PLYR_YAW -> 225.0;                   // Double (rot360)
                case PITCH, ROLL, PLYR_PITCH -> 0.0F;          // Float
                case TVM_POS_X, TVM_POS_Y, TVM_POS_Z, TVM_DIFF -> 0.0; // Double
                case CAM_ZOOM -> 1.0F;                         // Float (%.1f)
                case UAV_DIST -> 0.0;                          // Double
                case KEY_GUI -> "G";
                case THROTTLE -> 50.0;                         // Double
                case NONE -> null;
            };
        }
        @Override public int screenWidth() { return 640; }
        @Override public int screenHeight() { return 480; }
        @Override public float lineWidth() { return 1.0F; }
    }
}
