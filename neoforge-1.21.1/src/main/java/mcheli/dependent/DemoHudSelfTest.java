package mcheli.dependent;

import com.mojang.logging.LogUtils;
import java.util.Map;
import mcheli.agnostic.eval.MchExpr;
import mcheli.agnostic.hud.HudRenderer;
import mcheli.agnostic.hud.HudState;
import mcheli.agnostic.hud.MCH_Hud;
import mcheli.agnostic.hud.MCH_HudManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * Headless proof of the config-driven HUD pipeline (rendering itself needs a client, but the eval engine, the DSL
 * parse, and the whole item-execution path are agnostic and testable): (1) the {@code mcheli.eval} engine evaluates
 * representative HUD expressions correctly (precedence, ternary, hex, logical, comparison); (2) the real {@code hud/*}
 * configs parse; (3) drawing the actual {@code heli} HUD (which {@code Call}s {@code common_pilot}) against a stub state
 * emits draw commands into a capturing renderer without throwing.
 */
public final class DemoHudSelfTest {
    private static final Logger LOG = LogUtils.getLogger();

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
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

        // 3. Draw each demo vehicle's real HUD into a capturing renderer against a stub state (exercises parse + eval
        //    + item execution + the Call recursion, for all four vehicle HUDs).
        boolean drew = true;
        int radarPoints = 0; // blips emitted across the radar-equipped HUDs (have_radar=1 in the stub)
        Stub stub = new Stub();
        for (String h : new String[]{"heli", "plane", "mbt_hud", "vehicle"}) {
            MCH_Hud hud = MCH_HudManager.get(h);
            if (hud == null) {
                continue;
            }
            Capture cap = new Capture();
            hud.draw(cap, stub, MCH_HudManager::get);
            int total = cap.strings + cap.fills + cap.lines + cap.textures;
            LOG.info("[HUD-SELFTEST] {} HUD drew: strings={} fills={} lines={} textures={} points={}",
                h, cap.strings, cap.fills, cap.lines, cap.textures, cap.points);
            drew &= total > 0;
            radarPoints += cap.points;
        }
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

        boolean pass = evalOk && loaded && drew && fmtOk && radarOk;
        LOG.info("[HUD-SELFTEST] RESULT: {} - eval={} configsLoaded={} pipelineDrew={} formatTypes={} radar={}",
            pass ? "PASS" : "FAIL", evalOk, loaded, drew, fmtOk, radarOk);
    }

    private static double ev(String expr, Map<String, Double> vars) {
        return MchExpr.eval(MchExpr.compile(expr), name -> vars.getOrDefault(name, 0.0));
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

    /** A stub HudState with plausible flight values (everything else 0/""), enough to drive every expression. */
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
        @Override public Object formatArg(String argName) {
            // Types must match MchHudVarState (and thus the %-specs the configs use).
            return switch (argName.toUpperCase(java.util.Locale.ROOT)) {
                case "ALTITUDE", "HP", "MAX_HP", "INVENTORY" -> 100;
                case "MC_THOR" -> 6;
                case "MC_TMIN", "MC_TSEC" -> 0;
                case "CAM_ZOOM" -> 1.0; // formatted %.1f -> must be a Double
                case "WPN_NAME" -> "M230";
                case "KEY_GUI" -> "G";
                case "WPN_AMMO", "WPN_RM_AMMO" -> "";
                case "HP_PER", "PLYR_YAW", "PLYR_PITCH", "YAW", "PITCH", "ROLL",
                     "POS_X", "POS_Y", "POS_Z", "MOTION_X", "MOTION_Y", "MOTION_Z", "THROTTLE" -> 0.0;
                default -> "";
            };
        }
        @Override public int screenWidth() { return 640; }
        @Override public int screenHeight() { return 480; }
        @Override public float lineWidth() { return 1.0F; }
    }
}
