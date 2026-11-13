package mcheli.agnostic.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import mcheli.agnostic.spi.Logger;

/**
 * A parsed HUD — the config-driven overlay for one vehicle/seat. A {@code hud/*.txt} file is a list of draw commands
 * whose numeric arguments are {@link HudExpression expressions} evaluated live against the vehicle each frame; this
 * class parses them into an item list and, on {@link #draw}, walks them emitting into a {@link HudRenderer}. Faithful to
 * {@code mcheli.hud.MCH_Hud}: the reference line grammar ({@code MCH_HudManager.load} + {@code loadItemData}), strict
 * {@code If}/{@code EndIf} pairing, each item's own argument preprocessing, centre-origin coordinates
 * ({@code x = width/2 + calc(posX)}), ONE static current colour, a per-HUD {@code If} skip flag, {@code Call}
 * include-recursion guarded against cycles, and {@code Exit}.
 *
 * <p>Commands: {@code Color} (1 or 4 args), {@code DrawString}/{@code DrawCenteredString}, {@code DrawLine},
 * {@code DrawLineStipple}, {@code DrawRect}, {@code DrawTexture}, {@code DrawGraduationYaw}/{@code Pitch1}/{@code Pitch2}/
 * {@code Pitch3} (the heading/pitch ladders, which bank with roll via a pushed rotation), {@code DrawEntityRadar}/
 * {@code DrawEnemyRadar} (the radar dial's neutral/hostile blips), {@code DrawCameraRot} (the camera-direction box:
 * where the viewer looks relative to the hull; drawn whenever a HUD lists it, as the reference drew it whenever
 * {@code ac.camera != null}, which every aircraft built in its constructor), {@code If}/{@code EndIf}, {@code Call},
 * {@code Exit}.
 *
 * <p>Errors. This is a deliberate divergence: the reference threw and stopped the client, at load for a structural
 * error and at draw time for a bad expression.
 * <ul>
 *   <li>A structural error throws from {@link #parseLine}/{@link #checkData} with the reference's message. That covers
 *       an {@code If} inside an open {@code If}, a stray {@code EndIf}, and an {@code If} still open at the end of the
 *       file. {@link MCH_HudManager} then rejects that one file, as the reference refused to load it.</li>
 *   <li>An expression that does not parse drops only its own item, with a warning naming the file and line. An
 *       {@code If} whose condition does not parse stays in place and reads as false, so its block stays hidden and the
 *       {@code EndIf} pairing holds.</li>
 *   <li>An item that throws while drawing is skipped for that frame and logged once. Causes include an undefined
 *       variable, an unparseable literal and a format string that does not match its arguments. A failing
 *       {@code If} hides its block. Every such failure, each frame, also goes to the caller's
 *       {@link DrawErrorListener} when it passes one, so a test can still fail on a HUD error the log only warns
 *       about once.</li>
 * </ul>
 */
public final class MCH_Hud {

    private enum Kind { COLOR, STRING, TEXTURE, RECT, LINE, LINE_STIPPLE, GRADUATION, RADAR, CAMERA_ROT, IF, ENDIF, EXIT, CALL }

    private static final class Item {
        final Kind kind;
        final int line;                             // 1-based source line (reference fileLine)
        HudExpression[] num = new HudExpression[0]; // numeric args; an If's condition is null when it did not parse
        String text = "";                           // format string / texture name / call target
        MCH_HudItemStringArgs[] args = new MCH_HudItemStringArgs[0]; // DrawString format arguments
        boolean centered;
        int aux;                                    // graduation type (0=Yaw..3=Pitch3) / radar list (1 = hostile)
        boolean reported;                           // a draw-time failure of this item was already logged

        Item(Kind kind, int line) {
            this.kind = kind;
            this.line = line;
        }
    }

    /** Reference {@code MCH_HudItem.colorSetting}: ONE static current colour, opaque black at start-up and never reset,
     *  so it carries across {@code Call}s, across HUD files and from one frame to the next. Render thread only. */
    private static int colorSetting = 0xFF000000;
    /** Reference {@code MCH_HudItem.varMap}: one static variable scope shared by every HUD. It reads the
     *  {@link HudState} only while a {@link #draw} runs. Render thread only. */
    private static final HudVariables VARS = new HudVariables(HudVariables.NO_STATE);

    /** Receives every draw-time item failure of a {@link #draw(HudRenderer, HudState, Function, DrawErrorListener)}
     *  call, including the items of the HUDs it {@code Call}s: the failing item's file and 1-based line and what it
     *  threw. Unlike the log it hears about the failure on every frame, not only the first time. */
    @FunctionalInterface
    public interface DrawErrorListener {
        void onDrawError(String fileName, int line, RuntimeException error);
    }

    public final String name;
    private final String fileName;
    private final Logger log;                    // null = silent
    private final List<Item> items = new ArrayList<>();
    private int lineNo;                          // lines fed to parseLine so far
    private int openIfLine = -1;                 // line of the If awaiting its EndIf (reference isWaitEndif), or -1
    private boolean drawing; // re-entrancy guard (reference isDrawing) — terminates direct + indirect Call cycles

    /** A HUD named {@code name}, read from {@code fileName} (used in messages, e.g. {@code hud/heli.txt}). Parse and
     *  draw warnings go to {@code log}, or nowhere when it is null. */
    public MCH_Hud(String name, String fileName, Logger log) {
        this.name = name;
        this.fileName = fileName;
        this.log = log;
    }

    public boolean isEmpty() {
        return this.items.isEmpty();
    }

    // ---- parsing ----

    /**
     * Parse the next line of the file (reference {@code MCH_HudManager.load}). The line is trimmed, and a bare
     * {@code EndIf} or {@code Exit} becomes {@code EndIf=0}/{@code Exit=0}. Any other line needs an {@code =} with data
     * after it, else it is ignored. A {@code ;} comment line is ignored because no command matches it, and a {@code ;}
     * later in a line is data, as in the reference.
     *
     * @throws IllegalStateException on an If/EndIf pairing error (the reference's load-time RuntimeException)
     */
    public void parseLine(String raw) {
        this.lineNo++;
        String str = raw.trim();
        if (str.equalsIgnoreCase("endif")) {
            str = "endif=0";
        }
        if (str.equalsIgnoreCase("exit")) {
            str = "exit=0";
        }
        int eq = str.indexOf('=');
        if (eq >= 0 && str.length() > eq + 1) {
            loadItemData(str.substring(0, eq).trim().toLowerCase(Locale.ROOT), str.substring(eq + 1).trim());
        }
    }

    /** End of file (reference {@code checkData}). @throws IllegalStateException if an {@code If} is still open */
    public void checkData() {
        if (this.openIfLine >= 0) {
            throw new IllegalStateException("Endif not found! (If at line " + this.openIfLine + ")");
        }
    }

    /** Reference {@code MCH_Hud.loadItemData}: the argument count each command accepts, and each item class's own
     *  argument preprocessing (an {@code If} condition is used raw, {@code DrawString}/{@code DrawLine} positions are
     *  only lower-cased, the {@code DrawLineStipple} pattern/factor are raw, everything else goes through
     *  {@link HudExpression#toFormula}). */
    private void loadItemData(String item, String data) {
        String[] prm = data.split("\\s*,\\s*");
        if (prm.length == 0) {
            return;
        }
        int line = this.lineNo;
        if (item.equals("if")) {
            if (this.openIfLine >= 0) {
                throw new IllegalStateException("Endif not found! (If at line " + this.openIfLine + ", next If at line "
                    + line + ")");
            }
            Item it = new Item(Kind.IF, line);
            it.num = new HudExpression[]{compile(prm[0], line)}; // null when it does not parse: the If reads as false
            this.items.add(it);
            this.openIfLine = line;
        } else if (item.equals("endif")) {
            if (this.openIfLine < 0) {
                throw new IllegalStateException("IF in a pair can not be found! (EndIf at line " + line + ")");
            }
            this.items.add(new Item(Kind.ENDIF, line));
            this.openIfLine = -1;
        } else if (item.equals("drawstring") || item.equals("drawcenteredstring")) {
            // A lone '"' or an empty format slot crashed the reference's charAt/substring; the port ignores the line.
            String s = prm.length >= 3 ? prm[2] : "";
            if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
                Item it = new Item(Kind.STRING, line);
                it.text = s.substring(1, s.length() - 1);
                it.centered = item.equals("drawcenteredstring");
                it.args = new MCH_HudItemStringArgs[prm.length - 3];
                for (int i = 0; i < it.args.length; i++) {
                    it.args[i] = formatArg(prm[3 + i], line);
                }
                add(it, lower(prm[0]), lower(prm[1]));
            }
        } else if (item.equals("exit")) {
            this.items.add(new Item(Kind.EXIT, line));
        } else if (item.equals("color")) {
            if (prm.length == 1) {
                add(new Item(Kind.COLOR, line), HudExpression.toFormula(prm[0]));
            } else if (prm.length == 4) {
                // Reference MCH_HudItemColor.createByParams: ONE formula over the four channels, NOT masked to 0..255.
                // On the double path the shifts multiply by 2^n and '|' ORs the long-truncated sides.
                add(new Item(Kind.COLOR, line), "((" + HudExpression.toFormula(prm[0]) + ")<<24)|(("
                    + HudExpression.toFormula(prm[1]) + ")<<16)|((" + HudExpression.toFormula(prm[2]) + ")<<8 )|(("
                    + HudExpression.toFormula(prm[3]) + ")<<0 )");
            }
        } else if (item.equals("drawtexture")) {
            if (prm.length >= 9 && prm.length <= 10) {
                Item it = new Item(Kind.TEXTURE, line);
                it.text = prm[0].toLowerCase(Locale.ROOT); // 1.21 resource paths are lower-case (the reference used it as written)
                add(it, formulas(prm, 1, 9, prm.length == 10 ? prm[9] : "0")); // + rotation
            }
        } else if (item.equals("drawrect")) {
            if (prm.length == 4) {
                add(new Item(Kind.RECT, line), formulas(prm, 0, 4, null));
            }
        } else if (item.equals("drawline")) {
            if (prm.length >= 4 && prm.length % 2 == 0) {
                String[] f = new String[prm.length];
                for (int i = 0; i < f.length; i++) {
                    f[i] = lower(prm[i]);
                }
                add(new Item(Kind.LINE, line), f);
            }
        } else if (item.equals("drawlinestipple")) {
            if (prm.length >= 6 && prm.length % 2 == 0) { // pattern, factor, then at least one (x,y) pair of points
                String[] f = formulas(prm, 0, prm.length, null);
                f[0] = prm[0];
                f[1] = prm[1];
                add(new Item(Kind.LINE_STIPPLE, line), f);
            }
        } else if (item.equals("call")) {
            if (prm.length == 1) {
                Item it = new Item(Kind.CALL, line);
                it.text = prm[0].toLowerCase(Locale.ROOT); // reference MCH_HudManager.get lower-cases the name
                this.items.add(it);
            }
        } else if (item.equals("drawentityradar") || item.equals("drawenemyradar")) {
            if (prm.length == 5) {
                Item it = new Item(Kind.RADAR, line);  // args: rot, left, top, width, height
                it.aux = item.equals("drawenemyradar") ? 1 : 0;
                add(it, formulas(prm, 0, 5, null));
            }
        } else if (item.equals("drawgraduationyaw") || item.equals("drawgraduationpitch1")
            || item.equals("drawgraduationpitch2") || item.equals("drawgraduationpitch3")) {
            if (prm.length == 4) {
                Item it = new Item(Kind.GRADUATION, line); // args: rot, roll, posX, posY
                it.aux = item.equals("drawgraduationyaw") ? 0
                    : item.equals("drawgraduationpitch1") ? 1
                    : item.equals("drawgraduationpitch2") ? 2 : 3;
                add(it, formulas(prm, 0, 4, null));
            }
        } else if (item.equals("drawcamerarot") && prm.length == 2) {
            add(new Item(Kind.CAMERA_ROT, line), formulas(prm, 0, 2, null)); // args: posX, posY
        }
    }

    /** Compile {@code formulas} into the item and add it; an argument that does not parse drops the whole item. */
    private void add(Item it, String... formulas) {
        HudExpression[] num = new HudExpression[formulas.length];
        for (int i = 0; i < formulas.length; i++) {
            num[i] = compile(formulas[i], it.line);
            if (num[i] == null) {
                return;
            }
        }
        it.num = num;
        this.items.add(it);
    }

    /** Parse one expression once; on a syntax error warn and return null. */
    private HudExpression compile(String formula, int line) {
        try {
            return HudExpression.compile(formula);
        } catch (RuntimeException e) {
            warn("### HUD file error! %s line=%d: '%s' %s", this.fileName, line, formula, HudExpression.describe(e));
            return null;
        }
    }

    private MCH_HudItemStringArgs formatArg(String argName, int line) {
        MCH_HudItemStringArgs a = MCH_HudItemStringArgs.toArgs(argName);
        if (a == MCH_HudItemStringArgs.NONE && !argName.equalsIgnoreCase("NONE")) {
            warn("### HUD file error! %s line=%d: unknown DrawString argument '%s' (formats as null)",
                this.fileName, line, argName);
        }
        return a;
    }

    /** {@code toFormula} of {@code prm[from..to)}, optionally followed by {@code extra}. */
    private static String[] formulas(String[] prm, int from, int to, String extra) {
        String[] out = new String[to - from + (extra != null ? 1 : 0)];
        for (int i = from; i < to; i++) {
            out[i - from] = HudExpression.toFormula(prm[i]);
        }
        if (extra != null) {
            out[out.length - 1] = HudExpression.toFormula(extra);
        }
        return out;
    }

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    private void warn(String fmt, Object... args) {
        if (this.log != null) {
            this.log.warn(fmt, args);
        }
    }

    // ---- drawing ----

    /** Draw this HUD. {@code resolver} maps a {@code Call} target name to its parsed HUD (or null). The current colour
     *  and the variable scope are GLOBAL (static in the reference, shared across {@code Call}s and frames), while
     *  {@code Exit} and the {@code If} skip flag are LOCAL to each HUD's item loop. The shared scope lets go of
     *  {@code s} when the draw ends: the state holds the vehicle and the player, and through them the client level,
     *  which must not stay reachable after the player leaves the world. */
    public void draw(HudRenderer r, HudState s, Function<String, MCH_Hud> resolver) {
        draw(r, s, resolver, null);
    }

    /** {@link #draw(HudRenderer, HudState, Function)} that also reports each draw-time item failure to {@code errors}
     *  (may be null). The failing item is still skipped and logged once, exactly as without a listener. */
    public void draw(HudRenderer r, HudState s, Function<String, MCH_Hud> resolver, DrawErrorListener errors) {
        VARS.beginFrame(s::get, colorSetting); // reference MCH_HudItem.update -> updateVarMap
        try {
            drawItems(r, s, resolver, s.screenWidth() / 2.0, s.screenHeight() / 2.0, errors);
        } finally {
            VARS.endFrame(); // assigned names and the colour stay, as in the reference's session-long map
        }
    }

    private void drawItems(HudRenderer r, HudState s, Function<String, MCH_Hud> resolver, double cx, double cy,
                           DrawErrorListener errors) {
        if (this.drawing) {
            return; // already on the draw stack — a Call cycle; render once (reference isDrawing guard)
        }
        this.drawing = true;
        try {
            boolean ifFalse = false; // local to this HUD (reference isIfFalse is a per-hud field)
            for (Item it : this.items) {
                // Reference canExecute: inside a false If block everything but If/EndIf is skipped.
                if (ifFalse && it.kind != Kind.IF && it.kind != Kind.ENDIF) {
                    continue;
                }
                try {
                    switch (it.kind) {
                        case IF -> ifFalse = it.num[0] == null || it.num[0].eval(VARS) == 0.0;
                        case ENDIF -> ifFalse = false;
                        case EXIT -> {
                            return; // Exit stops THIS HUD's loop only (per-hud, not the caller)
                        }
                        case CALL -> {
                            MCH_Hud target = resolver.apply(it.text);
                            if (target != null) {
                                // shares colour + scope + the error listener; the guard blocks cycles
                                target.drawItems(r, s, resolver, cx, cy, errors);
                            }
                        }
                        default -> drawItem(it, r, s, cx, cy);
                    }
                } catch (RuntimeException e) {
                    if (it.kind == Kind.IF) {
                        ifFalse = true; // a condition that cannot be evaluated hides its block
                    }
                    if (!it.reported) {
                        it.reported = true;
                        warn("#### Draw HUD Error!!!: line=%d, file=%s: %s", it.line, this.fileName,
                            HudExpression.describe(e));
                    }
                    if (errors != null) {
                        errors.onDrawError(this.fileName, it.line, e);
                    }
                }
            }
        } finally {
            this.drawing = false;
        }
    }

    /** Execute one drawing item, evaluating its arguments in the reference item's order. */
    private static void drawItem(Item it, HudRenderer r, HudState s, double cx, double cy) {
        HudExpression[] n = it.num;
        switch (it.kind) {
            case COLOR -> {
                colorSetting = (int) (long) n[0].eval(VARS); // reference: (int)(long)calc(...)
                VARS.setColor(colorSetting);
            }
            case STRING -> {
                int x = (int) (cx + n[0].eval(VARS));
                int y = (int) (cy + n[1].eval(VARS));
                Object[] prm = new Object[it.args.length]; // NONE stays null and formats as "null", as in the reference
                for (int i = 0; i < prm.length; i++) {
                    if (it.args[i] != MCH_HudItemStringArgs.NONE) {
                        Object v = s.formatArg(it.args[i].name());
                        if (v == HudState.SKIP_STRING) {
                            return; // reference MCH_HudItemString returns without drawing
                        }
                        prm[i] = v;
                    }
                }
                r.string(String.format(it.text, prm), x, y, colorSetting, it.centered);
            }
            case RECT -> {
                double x2 = cx + n[0].eval(VARS);
                double y2 = cy + n[1].eval(VARS);
                double x1 = x2 + (int) n[2].eval(VARS);
                double y1 = y2 + (int) n[3].eval(VARS);
                r.fill((int) x2, (int) y2, (int) x1, (int) y1, colorSetting);
            }
            case LINE -> {
                double[] pts = new double[n.length];
                for (int i = 0; i < pts.length; i += 2) {
                    pts[i] = cx + n[i].eval(VARS);
                    pts[i + 1] = cy + n[i + 1].eval(VARS);
                }
                r.line(pts, colorSetting, 3, s.lineWidth());
            }
            case LINE_STIPPLE -> {
                double[] pts = new double[n.length - 2];
                for (int i = 0; i < pts.length; i += 2) {
                    pts[i] = cx + n[2 + i].eval(VARS);
                    pts[i + 1] = cy + n[3 + i].eval(VARS);
                }
                int factor = (int) n[1].eval(VARS);
                int pattern = (int) n[0].eval(VARS);
                r.lineStipple(pts, colorSetting, factor, pattern, s.lineWidth());
            }
            case GRADUATION -> {
                int gx = (int) (cx + n[2].eval(VARS));
                int gy = (int) (cy + n[3].eval(VARS));
                float roll = (float) n[1].eval(VARS);
                double rot = n[0].eval(VARS);
                r.pushMatrix(gx, gy, roll); // ladder banks with roll (the reference's glRotate)
                try {
                    drawGraduation(r, s, it.aux, rot, gx, gy, colorSetting);
                } finally {
                    r.popMatrix();
                }
            }
            case TEXTURE -> r.texture(it.text,
                cx + n[0].eval(VARS), cy + n[1].eval(VARS),
                n[2].eval(VARS), n[3].eval(VARS),
                n[4].eval(VARS), n[5].eval(VARS), n[6].eval(VARS), n[7].eval(VARS),
                (float) n[8].eval(VARS));
            case RADAR -> {
                double[] blips = it.aux == 1 ? s.radarEnemies() : s.radarEntities();
                if (blips.length >= 2) { // reference evaluates the args only when the list has contacts
                    drawRadar(r, blips, (float) n[0].eval(VARS), cx + n[1].eval(VARS), cy + n[2].eval(VARS),
                        n[3].eval(VARS), n[4].eval(VARS), colorSetting);
                }
            }
            case CAMERA_ROT -> {
                double px = cx + n[0].eval(VARS);
                double py = cy + n[1].eval(VARS);
                drawCameraRot(r, s, px, py, colorSetting);
            }
            default -> { }
        }
    }

    /** Reset the shared colour and variable scope to their start-up state (tests only). */
    static void resetSharedState() {
        colorSetting = 0xFF000000;
        VARS.clear();
    }

    /** The shared current colour (tests only). */
    static int currentColor() {
        return colorSetting;
    }

    /** The shared variable scope (tests only). */
    static HudVariables sharedScope() {
        return VARS;
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

    /**
     * The camera-direction indicator (reference {@code MCH_HudItemCameraRot.drawCommonGunnerCamera}): a {@code 42×22}
     * outline box with a stippled crosshair, plus a small marker box whose offset shows where the camera looks
     * relative to the hull. The reference camera's rotation is the riding player's look (the client tick handlers'
     * {@code updateCameraRotate(player.rotationYaw, player.rotationPitch)}), for the pilot as for a gunner, so the
     * marker follows the viewer's look ({@link HudState#cameraPitchDeg}, {@link HudState#cameraYawDiffDeg}).
     */
    private static void drawCameraRot(HudRenderer r, HudState s, double px, double py, int color) {
        float w = s.lineWidth();
        // Frame: a closed loop (GL_LINE_LOOP, mode 2), ±21 wide / ±11 tall.
        r.line(new double[]{ px - 21, py - 11, px + 21, py - 11, px + 21, py + 11, px - 21, py + 11 }, color, 2, w);
        // Crosshair: four disjoint spokes to the centre, stippled 0xCCCC (reference drawLineStipple factor 1).
        r.lineStipple(new double[]{
            px - 21, py, px, py,   px + 21, py, px, py,
            px, py - 11, px, py,   px, py + 11, px, py
        }, color, 1, 52428, w);
        // Marker: camera pitch → vertical, hull-relative camera yaw → horizontal (reference clamps + scales).
        double pitch = (Math.max(-30.0, Math.min(70.0, s.cameraPitchDeg())) - 20.0) * 0.16;
        double yaw = Math.max(-50.0, Math.min(50.0, s.cameraYawDiffDeg() * 2.0)) * 0.34;
        double mx = px + yaw;
        double my = py + pitch;
        r.line(new double[]{ mx - 3, my - 2, mx + 3, my - 2, mx + 3, my + 2, mx - 3, my + 2 }, color, 2, w);
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
}
