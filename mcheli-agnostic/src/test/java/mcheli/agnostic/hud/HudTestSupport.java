package mcheli.agnostic.hud;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mcheli.agnostic.spi.Logger;
import mcheli.agnostic.spi.ModelHandle;
import mcheli.agnostic.spi.ResourceSource;

/** Headless doubles for the HUD tests: a recording renderer, a map-backed state, a recording logger. */
final class HudTestSupport {
    private HudTestSupport() {}

    /** Records every draw call as one readable line, in issue order. */
    static final class Capture implements HudRenderer {
        final List<String> ops = new ArrayList<>();
        final List<double[]> linePoints = new ArrayList<>(); // the points of every line() call, in order

        @Override public void string(String text, int x, int y, int argb, boolean centered) {
            this.ops.add((centered ? "centered " : "string ") + text + " @" + x + "," + y + " #" + Integer.toHexString(argb));
        }
        @Override public void fill(int x1, int y1, int x2, int y2, int argb) {
            this.ops.add("fill " + x1 + "," + y1 + "," + x2 + "," + y2 + " #" + Integer.toHexString(argb));
        }
        @Override public void line(double[] p, int argb, int mode, float width) {
            this.ops.add("line mode" + mode + " n" + p.length / 2 + " #" + Integer.toHexString(argb));
            this.linePoints.add(p.clone());
        }
        @Override public void lineStipple(double[] p, int argb, int factor, int pattern, float width) {
            this.ops.add("stipple n" + p.length / 2 + " f" + factor + " p" + Integer.toHexString(pattern));
        }
        @Override public void texture(String name, double x, double y, double w, double h, double u, double v,
                                      double uw, double vh, double rot) {
            this.ops.add("texture " + name + " " + x + "," + y + " " + w + "x" + h + " uv " + u + "," + v + " " + uw
                + "x" + vh + " rot " + rot);
        }
        @Override public void points(double[] centers, int argb, double size) {
            this.ops.add("points n" + centers.length / 2);
        }

        /** The text of every string drawn, in order. */
        List<String> texts() {
            List<String> out = new ArrayList<>();
            for (String op : this.ops) {
                if (op.startsWith("string ") || op.startsWith("centered ")) {
                    String s = op.substring(op.indexOf(' ') + 1);
                    out.add(s.substring(0, s.lastIndexOf(" @")));
                }
            }
            return out;
        }
    }

    /** A 200x100 screen (centre 100,50); variables and format args come from maps (defaults 0 and ""). */
    static final class TestState implements HudState {
        final Map<String, Double> vars = new HashMap<>();
        final Map<String, Object> args = new HashMap<>();
        double[] entities = NO_BLIPS;
        double cameraPitch;
        double cameraYawDiff;

        @Override public double get(String name) { return this.vars.getOrDefault(name, 0.0); }
        @Override public Object formatArg(String argName) { return this.args.getOrDefault(argName, ""); }
        @Override public int screenWidth() { return 200; }
        @Override public int screenHeight() { return 100; }
        @Override public float lineWidth() { return 1.0F; }
        @Override public double[] radarEntities() { return this.entities; }
        @Override public double cameraPitchDeg() { return this.cameraPitch; }
        @Override public double cameraYawDiffDeg() { return this.cameraYawDiff; }
    }

    /** A state whose format args carry the reference's boxed type for each name (as MchHudVarState returns them). */
    static final class TypedState implements HudState {
        final Map<String, Double> vars = new HashMap<>();
        private final double v;

        TypedState(double v) {
            this.v = v;
        }

        @Override public double get(String name) { return this.vars.getOrDefault(name, this.v); }
        @Override public Object formatArg(String argName) {
            return switch (MCH_HudItemStringArgs.toArgs(argName)) {
                case ALTITUDE, MC_THOR, MC_TMIN, MC_TSEC, MAX_HP, HP, INVENTORY -> (int) this.v;
                case NAME, WPN_NAME, MC_VER, MOD_VER, MOD_NAME, KEY_GUI -> "s";
                case WPN_AMMO, WPN_RM_AMMO -> String.format("%4d", (int) this.v);
                case RELOAD_PER, RELOAD_SEC, MORTAR_DIST, PITCH, ROLL, PLYR_PITCH, CAM_ZOOM -> (float) this.v;
                case HP_PER, POS_X, POS_Y, POS_Z, MOTION_X, MOTION_Y, MOTION_Z, YAW, PLYR_YAW, TVM_POS_X, TVM_POS_Y,
                     TVM_POS_Z, TVM_DIFF, UAV_DIST, THROTTLE -> this.v;
                case DATE -> new Date(0L);
                case NONE -> null;
            };
        }
        @Override public int screenWidth() { return 427; }
        @Override public int screenHeight() { return 240; }
        @Override public float lineWidth() { return 1.0F; }
        @Override public double[] radarEntities() { return new double[]{10.0, 20.0}; }
        @Override public double[] radarEnemies() { return new double[]{-15.0, 5.0}; }
    }

    /** Records warnings formatted the way the SPI logger formats them (printf-style). */
    static final class TestLog implements Logger {
        final List<String> warnings = new ArrayList<>();
        final List<String> infos = new ArrayList<>();

        @Override public void info(String fmt, Object... args) { this.infos.add(String.format(fmt, args)); }
        @Override public void warn(String fmt, Object... args) { this.warnings.add(String.format(fmt, args)); }
        @Override public void debug(String fmt, Object... args) { }
        @Override public void error(String msg, Throwable t) { this.warnings.add(msg); }
    }

    /** {@code hud/<name>} files from a map, listed in insertion order. */
    static final class MapResources implements ResourceSource {
        final Map<String, String> files = new LinkedHashMap<>();

        MapResources put(String name, String... lines) {
            this.files.put(name, String.join("\r\n", lines));
            return this;
        }

        @Override public List<String> list(String dir) { return new ArrayList<>(this.files.keySet()); }
        @Override public Reader openUtf8(String path) {
            String text = this.files.get(path.substring(path.indexOf('/') + 1));
            return text == null ? null : new StringReader(text);
        }
        @Override public boolean exists(String path) { return openUtf8(path) != null; }
        @Override public ModelHandle loadModel(String path) { return null; }
    }

    /** Parse {@code lines} as one HUD file. */
    static MCH_Hud hud(String name, TestLog log, String... lines) {
        MCH_Hud h = new MCH_Hud(name, "hud/" + name + ".txt", log);
        for (String l : lines) {
            h.parseLine(l);
        }
        h.checkData();
        return h;
    }
}
