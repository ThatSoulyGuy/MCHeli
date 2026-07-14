package mcheli.agnostic.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import mcheli.agnostic.eval.MchExpr;

/**
 * A parsed HUD — the config-driven overlay for one vehicle/seat. A {@code hud/*.txt} file is a list of draw commands
 * whose numeric arguments are {@link MchExpr expressions} evaluated live against the vehicle each frame; this class
 * parses them into an item list and, on {@link #draw}, walks them emitting into a {@link HudRenderer}. Faithful to
 * {@code mcheli.hud.MCH_Hud}: centre-origin coordinates ({@code x = width/2 + calc(posX)}), a mutable current colour, a
 * single-level {@code If}/{@code EndIf} skip flag, {@code Call} include-recursion, and {@code Exit}.
 *
 * <p>Implements the cockpit commands: {@code Color}, {@code DrawString}/{@code DrawCenteredString}, {@code DrawLine},
 * {@code DrawLineStipple}, {@code DrawRect}, {@code DrawTexture}, {@code DrawGraduationYaw}/{@code Pitch1}/{@code Pitch2}/
 * {@code Pitch3} (the heading/pitch ladders, which bank with roll via a pushed rotation), {@code DrawEntityRadar}/
 * {@code DrawEnemyRadar} (the radar dial's neutral/hostile blips), {@code If}/{@code EndIf}, {@code Call}, {@code Exit}.
 * Still parse-and-skipped: {@code DrawCameraRot} (a gunner-camera reticle — draws only with an active {@code MCH_Camera}
 * object the port does not yet have).
 */
public final class MCH_Hud {

    private enum Kind { COLOR1, COLOR4, STRING, TEXTURE, RECT, LINE, LINE_STIPPLE, GRADUATION, RADAR, IF, ENDIF, EXIT, CALL }

    private static final class Item {
        final Kind kind;
        MchExpr.Node[] num = new MchExpr.Node[0]; // numeric expressions (coords / colour / rotation)
        String text = "";                          // format string / texture name / call target
        String[] args = new String[0];             // DrawString %-arg names
        boolean centered;
        int aux;                                   // graduation type (0=Yaw, 1=Pitch1, 2=Pitch2, 3=Pitch3)

        Item(Kind kind) {
            this.kind = kind;
        }
    }

    public final String name;
    private final List<Item> items = new ArrayList<>();
    private boolean drawing; // re-entrancy guard (reference isDrawing) — terminates direct + indirect Call cycles

    public MCH_Hud(String name) {
        this.name = name;
    }

    public boolean isEmpty() {
        return this.items.isEmpty();
    }

    // ---- parsing ----

    /** Parse one raw config line ({@code ;} comments stripped, bare or {@code key = data}). */
    public void parseLine(String raw) {
        String line = raw;
        int c = line.indexOf(';');
        if (c >= 0) {
            line = line.substring(0, c);
        }
        line = line.trim();
        if (line.isEmpty()) {
            return;
        }
        int eq = line.indexOf('=');
        String cmd = eq >= 0 ? line.substring(0, eq).trim() : line;
        String data = eq >= 0 ? line.substring(eq + 1).trim() : "";
        add(cmd, data);
    }

    private void add(String cmd, String data) {
        String[] p = data.isEmpty() ? new String[0] : data.split("\\s*,\\s*");
        if (cmd.equalsIgnoreCase("If") && p.length >= 1) {
            Item it = new Item(Kind.IF);
            it.num = new MchExpr.Node[]{MchExpr.compile(p[0])};
            items.add(it);
        } else if (cmd.equalsIgnoreCase("EndIf")) {
            items.add(new Item(Kind.ENDIF));
        } else if (cmd.equalsIgnoreCase("Exit")) {
            items.add(new Item(Kind.EXIT));
        } else if (cmd.equalsIgnoreCase("Call") && p.length == 1) {
            Item it = new Item(Kind.CALL);
            it.text = p[0].trim().toLowerCase();
            items.add(it);
        } else if (cmd.equalsIgnoreCase("Color")) {
            if (p.length == 1) {
                Item it = new Item(Kind.COLOR1);
                it.num = new MchExpr.Node[]{MchExpr.compile(p[0])};
                items.add(it);
            } else if (p.length == 4) {
                Item it = new Item(Kind.COLOR4);
                it.num = compileAll(p, 0, 4);
                items.add(it);
            }
        } else if ((cmd.equalsIgnoreCase("DrawString") || cmd.equalsIgnoreCase("DrawCenteredString")) && p.length >= 3) {
            String fmt = p[2];
            if (fmt.length() >= 2 && fmt.charAt(0) == '"' && fmt.charAt(fmt.length() - 1) == '"') {
                Item it = new Item(Kind.STRING);
                it.num = new MchExpr.Node[]{MchExpr.compile(p[0]), MchExpr.compile(p[1])};
                it.text = fmt.substring(1, fmt.length() - 1);
                it.centered = cmd.equalsIgnoreCase("DrawCenteredString");
                int n = p.length - 3;
                it.args = new String[n];
                for (int i = 0; i < n; i++) {
                    it.args[i] = p[3 + i].trim();
                }
                items.add(it);
            }
        } else if (cmd.equalsIgnoreCase("DrawRect") && p.length == 4) {
            Item it = new Item(Kind.RECT);
            it.num = compileAll(p, 0, 4);
            items.add(it);
        } else if (cmd.equalsIgnoreCase("DrawLine") && p.length >= 4 && p.length % 2 == 0) {
            Item it = new Item(Kind.LINE);
            it.num = compileAll(p, 0, p.length);
            items.add(it);
        } else if (cmd.equalsIgnoreCase("DrawLineStipple") && p.length >= 4 && p.length % 2 == 0) {
            // pattern, factor, then (x,y) point pairs (GL_LINES).
            Item it = new Item(Kind.LINE_STIPPLE);
            it.num = compileAll(p, 0, p.length);
            items.add(it);
        } else if ((cmd.equalsIgnoreCase("DrawGraduationYaw") || cmd.equalsIgnoreCase("DrawGraduationPitch1")
                || cmd.equalsIgnoreCase("DrawGraduationPitch2") || cmd.equalsIgnoreCase("DrawGraduationPitch3"))
                && p.length == 4) {
            Item it = new Item(Kind.GRADUATION);         // args: rot, roll, posX, posY
            it.aux = cmd.equalsIgnoreCase("DrawGraduationYaw") ? 0
                : cmd.equalsIgnoreCase("DrawGraduationPitch1") ? 1
                : cmd.equalsIgnoreCase("DrawGraduationPitch2") ? 2 : 3;
            it.num = compileAll(p, 0, 4);
            items.add(it);
        } else if (cmd.equalsIgnoreCase("DrawTexture") && p.length >= 9 && p.length <= 10) {
            Item it = new Item(Kind.TEXTURE);
            it.text = p[0].trim().toLowerCase();
            it.num = new MchExpr.Node[9];
            for (int i = 0; i < 8; i++) {
                it.num[i] = MchExpr.compile(p[1 + i]);
            }
            it.num[8] = MchExpr.compile(p.length == 10 ? p[9] : "0"); // rotation
            items.add(it);
        } else if ((cmd.equalsIgnoreCase("DrawEntityRadar") || cmd.equalsIgnoreCase("DrawEnemyRadar")) && p.length == 5) {
            Item it = new Item(Kind.RADAR);                 // args: rot, left, top, width, height
            it.aux = cmd.equalsIgnoreCase("DrawEnemyRadar") ? 1 : 0; // 0 = neutral list, 1 = hostile list
            it.num = compileAll(p, 0, 5);
            items.add(it);
        }
        // else: DrawCameraRot -> deferred (skipped). It is the gunner-camera reticle: the reference draws it ONLY when
        // the vehicle has an active gunner camera (MCH_Camera != null); the port has no such camera object yet.
    }

    private static MchExpr.Node[] compileAll(String[] p, int from, int to) {
        MchExpr.Node[] out = new MchExpr.Node[to - from];
        for (int i = from; i < to; i++) {
            out[i - from] = MchExpr.compile(p[i]);
        }
        return out;
    }

    // ---- drawing ----

    /** Draw this HUD. {@code resolver} maps a {@code Call} target name to its parsed HUD (or null). The current colour
     *  is GLOBAL (the reference {@code colorSetting} is static — it persists across {@code Call}s), but {@code Exit} and
     *  the {@code If} skip flag are LOCAL to each HUD's item loop. */
    public void draw(HudRenderer r, HudState s, Function<String, MCH_Hud> resolver) {
        int[] color = {0xFF000000}; // shared across Calls (reference: opaque-black default, static)
        drawItems(r, s, resolver, s.screenWidth() / 2.0, s.screenHeight() / 2.0, color);
    }

    private void drawItems(HudRenderer r, HudState s, Function<String, MCH_Hud> resolver,
                           double cx, double cy, int[] color) {
        if (this.drawing) {
            return; // already on the draw stack — a Call cycle; render once (reference isDrawing guard)
        }
        this.drawing = true;
        try {
            // Expressions can read the LIVE current colour via a "color" variable (reference mirrors colorSetting into
            // the var map on every Color command); everything else delegates to the state.
            MchExpr.VarLookup vars = n -> "color".equals(n) ? color[0] : s.get(n);
            boolean ifFalse = false; // local to this HUD (reference isIfFalse is a per-hud field)
            for (Item it : items) {
                if (ifFalse && it.kind != Kind.ENDIF) {
                    continue;
                }
                switch (it.kind) {
                    case COLOR1 -> color[0] = (int) (long) it.num[0].eval(vars);
                    case COLOR4 -> {
                        int a = (int) it.num[0].eval(vars) & 0xFF;
                        int rr = (int) it.num[1].eval(vars) & 0xFF;
                        int g = (int) it.num[2].eval(vars) & 0xFF;
                        int b = (int) it.num[3].eval(vars) & 0xFF;
                        color[0] = (a << 24) | (rr << 16) | (g << 8) | b;
                    }
                    case STRING -> {
                        int x = (int) (cx + it.num[0].eval(vars));
                        int y = (int) (cy + it.num[1].eval(vars));
                        Object[] fmtArgs = new Object[it.args.length];
                        for (int i = 0; i < fmtArgs.length; i++) {
                            fmtArgs[i] = s.formatArg(it.args[i]);
                        }
                        r.string(safeFormat(it.text, fmtArgs), x, y, color[0], it.centered);
                    }
                    case RECT -> {
                        int x = (int) (cx + it.num[0].eval(vars));
                        int y = (int) (cy + it.num[1].eval(vars));
                        r.fill(x, y, x + (int) it.num[2].eval(vars), y + (int) it.num[3].eval(vars), color[0]);
                    }
                    case LINE -> {
                        double[] pts = new double[it.num.length];
                        for (int i = 0; i < pts.length; i++) {
                            pts[i] = (i % 2 == 0 ? cx : cy) + it.num[i].eval(vars);
                        }
                        r.line(pts, color[0], 3, s.lineWidth());
                    }
                    case LINE_STIPPLE -> {
                        double[] pts = new double[it.num.length - 2];
                        for (int i = 0; i < pts.length; i++) {
                            pts[i] = (i % 2 == 0 ? cx : cy) + it.num[2 + i].eval(vars);
                        }
                        r.lineStipple(pts, color[0], (int) it.num[1].eval(vars), (int) it.num[0].eval(vars),
                            s.lineWidth());
                    }
                    case GRADUATION -> {
                        int gx = (int) (cx + it.num[2].eval(vars));
                        int gy = (int) (cy + it.num[3].eval(vars));
                        double rot = it.num[0].eval(vars);
                        double roll = it.num[1].eval(vars);
                        r.pushMatrix(gx, gy, roll); // ladder banks with roll (the reference's glRotate)
                        try {
                            drawGraduation(r, s, it.aux, rot, gx, gy, color[0]);
                        } finally {
                            r.popMatrix();
                        }
                    }
                    case TEXTURE -> r.texture(it.text,
                        cx + it.num[0].eval(vars), cy + it.num[1].eval(vars),
                        it.num[2].eval(vars), it.num[3].eval(vars),
                        it.num[4].eval(vars), it.num[5].eval(vars), it.num[6].eval(vars), it.num[7].eval(vars),
                        it.num[8].eval(vars));
                    case RADAR -> drawRadar(r, it.aux == 1 ? s.radarEnemies() : s.radarEntities(),
                        it.num[0].eval(vars), cx + it.num[1].eval(vars), cy + it.num[2].eval(vars),
                        it.num[3].eval(vars), it.num[4].eval(vars), color[0]);
                    case IF -> ifFalse = it.num[0].eval(vars) == 0.0;
                    case ENDIF -> ifFalse = false;
                    case EXIT -> {
                        return; // Exit stops THIS HUD's loop only (per-hud, not the caller)
                    }
                    case CALL -> {
                        MCH_Hud target = resolver.apply(it.text);
                        if (target != null) {
                            target.drawItems(r, s, resolver, cx, cy, color); // shares colour; guard blocks cycles
                        }
                    }
                    default -> { }
                }
            }
        } finally {
            this.drawing = false;
        }
    }

    // ---- graduation ladders (DrawGraduationYaw / Pitch1 / Pitch2 / Pitch3), ported from MCH_HudItemGraduation ----

    private static final String[] AZIMUTH_8 = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

    private static void drawGraduation(HudRenderer r, HudState s, int type, double rot, int posX, int posY, int color) {
        if (type == 0) {
            drawGraduationYaw(r, s, rot, posX, posY, color);
        } else if (type == 1) {
            drawGraduationPitch1(r, s, rot, posX, posY, color);
        } else { // 2 or 3
            drawGraduationPitch2(r, s, type, rot, posX, posY, color);
        }
    }

    /** The heading tape: tick marks every degree (taller every 3° and 45°), a degree number every 3° and a red
     *  cardinal letter (N/E/S/W…) every 45°, all scrolling with the aircraft yaw. */
    private static void drawGraduationYaw(HudRenderer r, HudState s, double playerYaw, int posX, int posY, int color) {
        double yaw = rot360(playerYaw);
        posX -= 90;
        double[] line = new double[76];
        int x = (int) (yaw * 10.0) % 10;
        int y = (int) yaw - 9;
        for (int i = 0; i < line.length / 4; y++) {
            double azPosX = posX + i * 10 - x;
            line[i * 4 + 0] = azPosX;
            line[i * 4 + 1] = posY;
            line[i * 4 + 2] = azPosX;
            line[i * 4 + 3] = posY + (y % 45 == 0 ? 15 : (y % 3 == 0 ? 10 : 5));
            if (y % 45 == 0) {
                r.string(azimuthStr8(y), (int) azPosX, posY - 10, 0xFFFF0000, true);
            } else if (y % 3 == 0) {
                int rot = y + 180;
                if (rot < 0) {
                    rot += 360;
                }
                if (rot > 360) {
                    rot -= 360;
                }
                r.string(Integer.toString(rot), (int) azPosX, posY - 10, color, true);
            }
            i++;
        }
        r.line(line, color, 1, s.lineWidth());
    }

    /** Pitch ladder type 1: a fine tick strip clipped between two vertical brackets. */
    private static void drawGraduationPitch1(HudRenderer r, HudState s, double playerPitch, int posX, int posY,
                                             int color) {
        int y = (int) (playerPitch * 10.0 % 10.0);
        if (y < 0) {
            y += 10;
        }
        int pitch = (int) playerPitch % 360;
        int posXL = posX - 100;
        int posXR = posX + 100;
        int linePosY = posY;
        posY -= 80;
        double[] line = new double[144];
        int p = !(playerPitch >= 0.0) && y != 0 ? pitch - 9 : pitch - 8;
        for (int i = 0; i < line.length / 8; p++) {
            int olx = p % 3 == 0 ? 15 : 5;
            line[i * 8 + 0] = posXL - olx;
            line[i * 8 + 1] = posY + i * 10 - y;
            line[i * 8 + 2] = posXL;
            line[i * 8 + 3] = posY + i * 10 - y;
            line[i * 8 + 4] = posXR + olx;
            line[i * 8 + 5] = posY + i * 10 - y;
            line[i * 8 + 6] = posXR;
            line[i * 8 + 7] = posY + i * 10 - y;
            i++;
        }
        r.line(line, color, 1, s.lineWidth());
        r.line(new double[]{posXL - 25, linePosY - 90, posXL, linePosY - 90, posXL, linePosY + 90, posXL - 25,
            linePosY + 90}, color, 3, s.lineWidth());
        r.line(new double[]{posXR + 25, linePosY - 90, posXR, linePosY - 90, posXR, linePosY + 90, posXR + 25,
            linePosY + 90}, color, 3, s.lineWidth());
    }

    /** Pitch ladder type 2/3: numbered horizon bars (chevroned by sign), dashed below the horizon, with the pitch
     *  degree printed on each side — the ladder banks with roll via the caller's {@code pushMatrix}. */
    private static void drawGraduationPitch2(HudRenderer r, HudState s, int type, double playerPitch, int posX,
                                             int posY, int color) {
        playerPitch = -playerPitch;
        int pitchN = (int) playerPitch / 5 * 5;
        int start = type == 2 ? 0 : 1;
        int end = type == 2 ? 5 : 4;
        int intv = type == 2 ? 1 : 2;
        float w = s.lineWidth();
        double[] line = new double[8];
        for (int i = start; i < end; i++) {
            int pitch = -(-pitchN - 10 + i * 5);
            double pRest = playerPitch % 5.0;
            int x = pitch != 0 ? 50 : 100;
            int y = posY + (int) (-60 * intv + pRest * 6.0 * intv + i * 30 * intv);
            line[0] = posX - x;
            line[1] = y + (pitch == 0 ? 0 : (pitch > 0 ? 2 : -2));
            line[2] = posX - 50;
            line[3] = y;
            line[4] = posX + x;
            line[5] = line[1];
            line[6] = posX + 50;
            line[7] = y;
            r.line(line, color, 1, w);
            line[0] = posX - 50;
            line[1] = y;
            line[2] = posX - 30;
            line[3] = y;
            line[4] = posX + 50;
            line[5] = y;
            line[6] = posX + 30;
            line[7] = y;
            if (pitch >= 0) {
                r.line(line, color, 1, w);
            } else {
                r.lineStipple(line, color, 1, 52428, w); // 0xCCCC dashes below the horizon
            }
            if (pitch != 0) {
                r.string(Integer.toString(pitch), posX - 50 - 10, y - 4, color, true);
                r.string(Integer.toString(pitch), posX + 50 + 10, y - 4, color, true);
            }
        }
    }

    // ---- entity radar dial (DrawEntityRadar / DrawEnemyRadar), ported from MCH_HudItemRadar ----

    /** Project a radar-contact list onto the dial and draw the visible blips. Each contact is a relative {@code (x,z)}
     *  block offset from the vehicle; it is scaled so a 64-block range spans the dial (reference {@code w/64} factor),
     *  rotated by {@code rot} (the config's {@code -plyr_yaw-180} = north-up), clipped to the dial box, and drawn as a
     *  small square. {@code left}/{@code top} are the dial's centre-origin-resolved top-left. */
    private static void drawRadar(HudRenderer r, double[] blips, double rot, double left, double top,
                                  double w, double h, int color) {
        if (blips.length < 2) {
            return;
        }
        double rr = Math.toRadians(rot);
        double cos = Math.cos(rr);
        double sin = Math.sin(rr);
        double wf = w / 64.0;
        double hf = h / 64.0;
        double halfW = w / 2.0;
        double halfH = h / 2.0;
        double[] out = new double[blips.length];
        int n = 0;
        for (int i = 0; i + 1 < blips.length; i += 2) {
            double sx = blips[i] / 2.0 * wf;
            double sy = blips[i + 1] / 2.0 * hf;
            double px = sx * cos - sy * sin;   // MCH_Lib.rotatePoints
            double py = sx * sin + sy * cos;
            if (px > -halfW && px < halfW && py > -halfH && py < halfH) { // clip to the dial (reference bounds test)
                out[n++] = left + halfW + px;
                out[n++] = top + halfH + py;
            }
        }
        if (n > 0) {
            r.points(n == out.length ? out : java.util.Arrays.copyOf(out, n), color, 2.0);
        }
    }

    /** Normalize an angle to [0,360) (reference {@code MCH_Lib.getRotate360}). */
    private static double rot360(double r) {
        r %= 360.0;
        return r >= 0.0 ? r : r + 360.0;
    }

    /** 8-point compass letter for a heading in degrees (reference {@code MCH_Lib.getAzimuthStr8}; yaw 0 = South). */
    private static String azimuthStr8(int dir) {
        dir %= 360;
        if (dir < 0) {
            dir += 360;
        }
        return AZIMUTH_8[dir / 45];
    }

    /** {@code String.format} that degrades to the raw format string on a mismatch, so one bad line never crashes the
     *  HUD (a deliberate robustness deviation from the reference, which rethrew). */
    private static String safeFormat(String fmt, Object[] args) {
        try {
            return String.format(fmt, args);
        } catch (Exception e) {
            return fmt;
        }
    }
}
