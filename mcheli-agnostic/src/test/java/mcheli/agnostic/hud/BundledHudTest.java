package mcheli.agnostic.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import mcheli.agnostic.hud.HudTestSupport.Capture;
import mcheli.agnostic.hud.HudTestSupport.TestLog;
import mcheli.agnostic.hud.HudTestSupport.TypedState;
import mcheli.agnostic.spi.ModelHandle;
import mcheli.agnostic.spi.ResourceSource;
import org.junit.jupiter.api.Test;

/**
 * Every HUD file the mod ships ({@code neoforge-1.21.1/src/main/resources/assets/mcheli/hud}) loads without a single
 * warning and draws every one of its blocks without an evaluation or format error.
 *
 * <p>Blocks sit behind {@code If} gates on particular values ({@code sight_type==2}, {@code cam_mode==2},
 * {@code time> 13000}, {@code cam_zoom>1}, ...), so drawing with every variable at one value leaves most of them
 * unexecuted. The test therefore reads each file's own {@code If} conditions and, for each one, searches values for the
 * variables it names (0, 1, and every number literal in the condition, and its negation, and half a unit either side
 * of each) on a base of all-0 or all-1 until that block is reached (no earlier {@code Exit} taken) and its condition
 * holds. It asserts that
 * such a scenario exists for EVERY {@code If}, then draws every HUD under every scenario found. The format-arg types
 * are the reference's ({@link TypedState}) and no argument is ever skipped, so every {@code DrawString} formats.
 */
class BundledHudTest {

    private static final Path HUD_DIR = Path.of("..", "neoforge-1.21.1", "src", "main", "resources", "assets", "mcheli",
        "hud");

    private static final class DirResources implements ResourceSource {
        @Override public List<String> list(String dir) {
            try (Stream<Path> s = Files.list(HUD_DIR)) {
                return s.map(p -> p.getFileName().toString()).sorted().toList();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        @Override public Reader openUtf8(String path) {
            try {
                return Files.newBufferedReader(HUD_DIR.resolve(path.substring(path.indexOf('/') + 1)),
                    StandardCharsets.UTF_8);
            } catch (IOException e) {
                return null;
            }
        }
        @Override public boolean exists(String path) { return openUtf8(path) != null; }
        @Override public ModelHandle loadModel(String path) { return null; }
    }

    @Test
    void everyBundledHudLoadsAndDrawsEveryBlockCleanly() throws IOException {
        assumeTrue(Files.isDirectory(HUD_DIR), "bundled HUD directory not found from " + Path.of("").toAbsolutePath());
        TestLog log = new TestLog();
        MCH_HudManager.getInstance().load(new DirResources(), log);
        List<String> names;
        try (Stream<Path> s = Files.list(HUD_DIR)) {
            names = s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".txt"))
                .map(n -> n.substring(0, n.length() - 4)).sorted().toList();
        }
        assertFalse(names.isEmpty());
        assertTrue(log.warnings.isEmpty(), "load warnings: " + log.warnings);
        assertEquals(List.of("Read " + names.size() + " huds"), log.infos);

        // One scenario per If block (deduplicated), plus the two plain bases.
        Set<Scenario> scenarios = new LinkedHashSet<>(List.of(new Scenario(0.0, Map.of()), new Scenario(1.0, Map.of())));
        List<String> unopened = new ArrayList<>();
        int gates = 0;
        for (String name : names) {
            List<Step> steps = skeleton(Files.readAllLines(HUD_DIR.resolve(name + ".txt"), StandardCharsets.UTF_8));
            for (Step st : steps) {
                if (st.op() == Op.IF) {
                    gates++;
                    Scenario sc = scenarioOpening(steps, st);
                    if (sc == null) {
                        unopened.add(name + ".txt:" + st.line() + " (If = " + st.condition() + ")");
                    } else {
                        scenarios.add(sc);
                    }
                }
            }
        }
        assertTrue(gates > 50, "only " + gates + " If gates found in the bundled HUDs");
        assertTrue(unopened.isEmpty(), "no test values open these blocks, so nothing in them would be checked; extend "
            + "candidates() in BundledHudTest: " + unopened);

        for (Scenario sc : scenarios) {
            MCH_Hud.resetSharedState();
            TypedState state = sc.state();
            int drawn = 0;
            for (String name : names) {
                MCH_Hud hud = MCH_HudManager.get(name);
                assertNotNull(hud, name);
                Capture c = new Capture();
                hud.draw(c, state, MCH_HudManager::get);
                drawn += c.ops.size();
            }
            assertTrue(log.warnings.isEmpty(), "draw warnings with " + sc + ": " + log.warnings);
            assertTrue(drawn > 100, "the bundled HUDs emitted only " + drawn + " draw calls with " + sc);
        }
    }

    // ---- scenario search ----

    /** Every built-in at {@code base}, except the {@code overrides}. */
    private record Scenario(double base, Map<String, Double> overrides) {
        TypedState state() {
            TypedState s = new TypedState(this.base);
            s.vars.putAll(this.overrides);
            return s;
        }

        @Override public String toString() {
            return "every variable at " + this.base + (this.overrides.isEmpty() ? "" : " except " + this.overrides);
        }
    }

    private enum Op { IF, ENDIF, EXIT }

    /** One flow item of a HUD file; {@code condition} is the raw {@code If} condition and {@code expr} its compiled
     *  form (both null otherwise; {@code expr} is also null when the condition does not parse). */
    private record Step(Op op, int line, String condition, HudExpression expr) {}

    /** The If/EndIf/Exit skeleton of a file, read with {@link MCH_Hud#parseLine}'s grammar: trimmed lines, a bare
     *  {@code EndIf}/{@code Exit}, the command before the first {@code =}, and an If's raw first argument. */
    private static List<Step> skeleton(List<String> lines) {
        List<Step> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String str = lines.get(i).trim();
            if (str.equalsIgnoreCase("endif")) {
                str = "endif=0";
            }
            if (str.equalsIgnoreCase("exit")) {
                str = "exit=0";
            }
            int eq = str.indexOf('=');
            if (eq < 0 || str.length() <= eq + 1) {
                continue;
            }
            switch (str.substring(0, eq).trim().toLowerCase(Locale.ROOT)) {
                case "if" -> {
                    String condition = str.substring(eq + 1).trim().split("\\s*,\\s*")[0];
                    out.add(new Step(Op.IF, i + 1, condition, compileOrNull(condition)));
                }
                case "endif" -> out.add(new Step(Op.ENDIF, i + 1, null, null));
                case "exit" -> out.add(new Step(Op.EXIT, i + 1, null, null));
                default -> { }
            }
        }
        return out;
    }

    /** A scenario under which {@code gate} is reached and true, or null if the candidate values find none. */
    private static Scenario scenarioOpening(List<Step> steps, Step gate) {
        List<String> vars = new ArrayList<>(variablesIn(gate.condition()));
        List<Double> values = new ArrayList<>(candidates(gate.condition()));
        for (double base : new double[]{0.0, 1.0}) {
            int[] pick = new int[vars.size()];
            while (true) {
                Map<String, Double> overrides = new TreeMap<>();
                for (int i = 0; i < pick.length; i++) {
                    if (values.get(pick[i]) != base) {
                        overrides.put(vars.get(i), values.get(pick[i]));
                    }
                }
                Scenario sc = new Scenario(base, overrides);
                if (opens(steps, gate, sc.state())) {
                    return sc;
                }
                int i = 0; // next combination (odometer over pick)
                while (i < pick.length && ++pick[i] == values.size()) {
                    pick[i++] = 0;
                }
                if (i == pick.length) {
                    break;
                }
            }
        }
        return null;
    }

    /** Walk the skeleton the way {@link MCH_Hud} draws it: is {@code gate} reached and its condition true? */
    private static boolean opens(List<Step> steps, Step gate, HudState s) {
        HudVariables vars = new HudVariables(s::get);
        boolean ifFalse = false;
        for (Step st : steps) {
            switch (st.op()) {
                case IF -> {
                    ifFalse = !holds(st.expr(), vars);
                    if (st == gate) {
                        return !ifFalse;
                    }
                }
                case ENDIF -> ifFalse = false;
                case EXIT -> {
                    if (!ifFalse) {
                        return false;
                    }
                }
            }
        }
        return false;
    }

    private static boolean holds(HudExpression condition, HudVariables vars) {
        try {
            return condition != null && condition.eval(vars) != 0.0;
        } catch (RuntimeException e) {
            return false; // a condition that fails reads as false in MCH_Hud too
        }
    }

    private static HudExpression compileOrNull(String condition) {
        try {
            return HudExpression.compile(condition);
        } catch (RuntimeException e) {
            return null; // MCH_Hud warns about it at load, which the load assertion reports
        }
    }

    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern NUMBER = Pattern.compile("(?<![A-Za-z0-9_.])(0[xX][0-9a-fA-F]+|\\d+(?:\\.\\d+)?)");

    /** The built-in variables a condition names. */
    private static Set<String> variablesIn(String condition) {
        Set<String> out = new TreeSet<>();
        Matcher m = NAME.matcher(condition);
        while (m.find()) {
            if (HudState.VARIABLES.contains(m.group())) {
                out.add(m.group());
            }
        }
        return out;
    }

    /** 0, 1, and each number literal of the condition and its negation (a {@code -} before a literal is a separate
     *  operator), each with the values half a unit below and above it. */
    private static Set<Double> candidates(String condition) {
        Set<Double> out = new LinkedHashSet<>(List.of(0.0, 1.0));
        Matcher m = NUMBER.matcher(condition);
        while (m.find()) {
            String lit = m.group(1);
            double n = lit.length() > 2 && (lit.charAt(1) == 'x' || lit.charAt(1) == 'X')
                ? Long.parseLong(lit.substring(2), 16) : Double.parseDouble(lit);
            for (double v : n == 0.0 ? new double[]{n} : new double[]{n, -n}) {
                out.add(v - 0.5);
                out.add(v);
                out.add(v + 0.5);
            }
        }
        return out;
    }

    @Test
    void theScenarioSearchFindsValuesBehindEqualityRangeAndExitGates() {
        List<Step> steps = skeleton(List.of(
            "If = is_uav==0", "\tExit", "EndIf",
            "If = time> 13000", "EndIf",
            "If = sight_type==2 && lock <= 0", "EndIf",
            "If = cam_zoom>1", "EndIf"));
        Map<Integer, Scenario> found = new LinkedHashMap<>();
        for (Step st : steps) {
            if (st.op() == Op.IF) {
                found.put(st.line(), scenarioOpening(steps, st));
            }
        }
        assertEquals(new Scenario(0.0, Map.of()), found.get(1));
        assertEquals(new Scenario(1.0, Map.of("time", 13000.5)), found.get(4), "past the Exit only with is_uav = 1");
        assertEquals(new Scenario(1.0, Map.of("lock", 0.0, "sight_type", 2.0)), found.get(6));
        assertEquals(new Scenario(1.0, Map.of("cam_zoom", 1.5)), found.get(8));
    }
}
