package mcheli.agnostic.hud;

import static mcheli.agnostic.hud.HudTestSupport.hud;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mcheli.agnostic.hud.HudTestSupport.Capture;
import mcheli.agnostic.hud.HudTestSupport.MapResources;
import mcheli.agnostic.hud.HudTestSupport.TestLog;
import mcheli.agnostic.hud.HudTestSupport.TestState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The HUD DSL parser and item executor ({@link MCH_Hud}, {@link MCH_HudManager}) against the reference semantics. */
class HudParserTest {

    private final TestLog log = new TestLog();
    private final TestState state = new TestState();
    private final Map<String, MCH_Hud> huds = new HashMap<>();

    @BeforeEach
    void resetStaticHudState() {
        MCH_Hud.resetSharedState(); // the reference colour + variable map are static
    }

    private MCH_Hud define(String name, String... lines) {
        MCH_Hud h = hud(name, this.log, lines);
        this.huds.put(name, h);
        return h;
    }

    private Capture draw(MCH_Hud h) {
        Capture c = new Capture();
        h.draw(c, this.state, this.huds::get);
        return c;
    }

    // ---- line grammar ----

    @Test
    void ifEndIfGatesItsBlock() {
        MCH_Hud h = define("t",
            "If = altitude > 1",
            "DrawString = 0, 0, \"A\"",
            "EndIf",
            "DrawString = 0, 10, \"B\"");
        assertEquals(List.of("B"), draw(h).texts());
        this.state.vars.put("altitude", 2.0);
        assertEquals(List.of("A", "B"), draw(h).texts());
        assertTrue(this.log.warnings.isEmpty(), this.log.warnings.toString());
    }

    @Test
    void pairingErrorsAreRejectedLikeTheReference() {
        MCH_Hud nested = new MCH_Hud("n", "hud/n.txt", this.log);
        nested.parseLine("If = 1");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> nested.parseLine("If = 1"));
        assertTrue(e.getMessage().startsWith("Endif not found!"), e.getMessage());

        MCH_Hud stray = new MCH_Hud("s", "hud/s.txt", this.log);
        e = assertThrows(IllegalStateException.class, () -> stray.parseLine("EndIf"));
        assertTrue(e.getMessage().startsWith("IF in a pair can not be found!"), e.getMessage());

        MCH_Hud open = new MCH_Hud("o", "hud/o.txt", this.log);
        open.parseLine("If = 1");
        e = assertThrows(IllegalStateException.class, open::checkData);
        assertTrue(e.getMessage().contains("line 1"), e.getMessage());
    }

    @Test
    void lineGrammarFollowsTheReferenceLoader() {
        MCH_Hud h = define("t",
            "; DrawString = 0, 0, \"commented\"",
            ";DrawString = 0, 0, \"also commented\"",
            "DrawString = 0, 0, \"a;b\"",                 // a ';' later in the line is data
            "  drawstring = 0, 0, \"lower\"  ",           // command names are case-insensitive, lines trimmed
            "DrawString = 0, 0, noquotes",               // the format must be quoted
            "DrawString = 0, 0, \"",                     // a lone quote: the reference crashed, the port ignores it
            "If =",                                      // nothing after '=': ignored, so it opens no block
            "Bogus = 1, 2",
            "",
            "DrawLineStipple = 0xCCCC, 1, 0, 0",         // the reference needs at least 6 args
            "DrawLineStipple = 0xCCCC, 1, 0, 0, 10, 0",
            "DrawRect = -10, -5, 20, 10");
        Capture c = draw(h);
        assertEquals(List.of("a;b", "lower"), c.texts());
        assertTrue(c.ops.contains("stipple n2 f1 pcccc"), c.ops.toString());
        assertTrue(c.ops.contains("fill 90,45,110,55 #ff000000"), c.ops.toString());
        assertEquals(4, c.ops.size(), c.ops.toString());
        assertTrue(this.log.warnings.isEmpty(), this.log.warnings.toString());
    }

    @Test
    void eachItemPreprocessesItsArgumentsLikeTheReference() {
        this.state.vars.put("altitude", 5.0);
        MCH_Hud h = define("t",
            "Color = #FF00FF00",                          // toFormula: '#' -> 0x
            "If = Altitude > 0",                          // If conditions are raw: 'Altitude' is not 'altitude'
            "DrawString = 0, 0, \"hidden\"",
            "EndIf",
            "DrawString = ALTITUDE, 0, \"lower-cased position\"");
        Capture c = draw(h);
        assertEquals(List.of("string lower-cased position @105,50 #ff00ff00"), c.ops);
        assertEquals(1, this.log.warnings.size(), this.log.warnings.toString());
        assertTrue(this.log.warnings.get(0).contains("undefined variable"), this.log.warnings.get(0));
    }

    // ---- error isolation ----

    @Test
    void aMalformedExpressionDropsOnlyItsItem() {
        MCH_Hud h = define("t",
            "DrawString = 1+, 0, \"bad\"",
            "DrawString = 0, 0, \"good\"");
        assertEquals(List.of("good"), draw(h).texts());
        assertEquals(1, this.log.warnings.size());
        String w = this.log.warnings.get(0);
        assertTrue(w.contains("hud/t.txt") && w.contains("line=1") && w.contains("'1+'"), w);
    }

    @Test
    void aMalformedIfConditionHidesItsBlockAndKeepsThePairing() {
        MCH_Hud h = define("t",
            "If = (1",
            "DrawString = 0, 0, \"hidden\"",
            "EndIf",
            "DrawString = 0, 0, \"shown\"");
        assertEquals(List.of("shown"), draw(h).texts());
        assertEquals(1, this.log.warnings.size());
    }

    @Test
    void aDrawTimeFailureSkipsTheItemAndIsLoggedOnce() {
        MCH_Hud h = define("t",
            "DrawString = nope, 0, \"x\"",
            "DrawString = 0, 0, \"%d\", WPN_NAME",        // %d of a String
            "DrawString = 0, 0, \"y\"");
        this.state.args.put("WPN_NAME", "M230");
        assertEquals(List.of("y"), draw(h).texts());
        assertEquals(List.of("y"), draw(h).texts());
        assertEquals(2, this.log.warnings.size(), this.log.warnings.toString());
        assertTrue(this.log.warnings.get(0).contains("Draw HUD Error") && this.log.warnings.get(0).contains("line=1"));
    }

    @Test
    void radarArgumentsAreEvaluatedOnlyWithContacts() {
        MCH_Hud h = define("t", "DrawEntityRadar = nope, 0, 0, 64, 64");
        assertTrue(draw(h).ops.isEmpty());
        assertTrue(this.log.warnings.isEmpty(), "reference evaluates the radar args only for a non-empty list");
        this.state.entities = new double[]{1.0, 1.0};
        draw(h);
        assertEquals(1, this.log.warnings.size());
    }

    // ---- Call / Exit / colour / variables ----

    @Test
    void exitStopsOnlyTheHudItIsIn() {
        define("b", "DrawString = 0, 0, \"b1\"", "Exit", "DrawString = 0, 0, \"b2\"");
        MCH_Hud a = define("a", "Call = B", "DrawString = 0, 0, \"after\"");
        assertEquals(List.of("b1", "after"), draw(a).texts());
    }

    @Test
    void callCyclesRenderEachHudOnce() {
        MCH_Hud a = define("a", "DrawString = 0, 0, \"a\"", "Call = a", "Call = b");
        define("b", "DrawString = 0, 0, \"b\"", "Call = a", "Call = missing");
        assertEquals(List.of("a", "b"), draw(a).texts());
    }

    @Test
    void theCurrentColourIsSharedAcrossCallsAndFrames() {
        define("b", "DrawString = 0, 0, \"in callee\"");
        MCH_Hud a = define("a", "Color = 0xFF00FF00", "Call = b");
        MCH_Hud c = define("c", "DrawString = 0, 0, \"next frame\"");
        assertEquals(List.of("string in callee @100,50 #ff00ff00"), draw(a).ops);
        assertEquals(List.of("string next frame @100,50 #ff00ff00"), draw(c).ops,
            "reference colorSetting is static and never reset");
    }

    @Test
    void colourVariableIsTheUnsignedCurrentColour() {
        MCH_Hud h = define("t",
            "Color = 0xFF102030",
            "If = color == 0xff102030",
            "DrawString = 0, 0, \"unsigned\"",
            "EndIf",
            "Color = color & 0x00ffffff | 0x80000000");
        assertEquals(List.of("unsigned"), draw(h).texts());
        assertEquals(0x80102030, MCH_Hud.currentColor());
    }

    @Test
    void fourArgumentColourIsOneUnmaskedFormula() {
        define("t", "Color = 0, 511, 0, 0");
        draw(this.huds.get("t"));
        assertEquals(0x01FF0000, MCH_Hud.currentColor(), "511<<16 spills into the alpha byte, as in the reference");
        define("u", "Color = 255, 40, 212, 72");
        draw(this.huds.get("u"));
        assertEquals(0xFF28D448, MCH_Hud.currentColor());
    }

    @Test
    void assignedVariablesPersistButBuiltInsAreReReadEachFrame() {
        this.state.vars.put("altitude", 3.0);
        MCH_Hud first = define("first",
            "If = (q = 5) + (altitude = 50) > 1000",
            "EndIf",
            "DrawString = q, altitude, \"same frame\"");
        MCH_Hud second = define("second", "DrawString = q, altitude, \"next frame\"");
        assertEquals(List.of("string same frame @105,100 #ff000000"), draw(first).ops);
        assertEquals(List.of("string next frame @105,53 #ff000000"), draw(second).ops,
            "q persists (reference varMap); altitude is overwritten by updateVarMap");
    }

    @Test
    void theSharedScopeReleasesTheStateWhenTheDrawEnds() {
        this.state.vars.put("altitude", 7.0);
        MCH_Hud h = define("t", "Color = 0xFF00FF00", "DrawString = (q = 3) + altitude, 0, \"x\"");
        assertEquals(List.of("string x @110,50 #ff00ff00"), draw(h).ops);
        HudVariables scope = MCH_Hud.sharedScope();
        assertEquals(0.0, HudExpression.compile("altitude").eval(scope), 0.0,
            "after the draw a built-in no longer reads the state (it holds the vehicle, player and level)");
        assertEquals(3.0, HudExpression.compile("q").eval(scope), 0.0, "assigned names persist");
        assertEquals(0xFF00FF00L, (long) HudExpression.compile("color").eval(scope), "the colour persists");
        this.state.vars.put("altitude", 9.0);
        MCH_Hud next = define("next", "DrawString = q + altitude, 0, \"y\"");
        assertEquals(List.of("string y @112,50 #ff00ff00"), draw(next).ops, "the next draw reads its state again");
    }

    @Test
    void cameraRotIsDrawnForEveryViewerAndTracksTheLook() {
        this.state.cameraPitch = 45.0;   // (45 - 20) * 0.16 = 4
        this.state.cameraYawDiff = 10.0; // 10 * 2 * 0.34 = 6.8
        Capture c = draw(define("t", "DrawCameraRot = 0, 10"));
        assertEquals(List.of("line mode2 n4 #ff000000", "stipple n8 f1 pcccc", "line mode2 n4 #ff000000"), c.ops,
            "frame, crosshair and marker, with no gunner-view gate (reference ac.camera != null)");
        double[] marker = c.linePoints.get(1);
        assertEquals(100 + 6.8 - 3, marker[0], 1e-9);
        assertEquals(60 + 4 - 2, marker[1], 1e-9);
    }

    // ---- DrawString format arguments ----

    @Test
    void formatArgumentsUseTheStateTypesAndNoneIsNull() {
        this.state.args.put("WPN_NAME", "M230");
        this.state.args.put("HP", 42);
        this.state.args.put("RELOAD_SEC", 1.25F);
        MCH_Hud h = define("t",
            "DrawString = 0, 0, \"%s|%d|%.0f|%s\", WPN_NAME, hp, RELOAD_SEC, BOGUS",
            "DrawCenteredString = 5, 5, \"plain\"");
        Capture c = draw(h);
        assertEquals(List.of("string M230|42|1|null @100,50 #ff000000", "centered plain @105,55 #ff000000"), c.ops);
        assertEquals(1, this.log.warnings.size());
        assertTrue(this.log.warnings.get(0).contains("unknown DrawString argument 'BOGUS'"), this.log.warnings.get(0));
    }

    @Test
    void skipStringSuppressesTheWholeString() {
        this.state.args.put("WPN_AMMO", HudState.SKIP_STRING);
        MCH_Hud h = define("t",
            "DrawString = 0, 0, \"/\", WPN_AMMO",
            "DrawString = 0, 0, \"kept\", HP");
        assertEquals(List.of("kept"), draw(h).texts());
        assertTrue(this.log.warnings.isEmpty());
    }

    @Test
    void textureNameIsLowerCasedAndRotationDefaultsToZero() {
        MCH_Hud h = define("t", "DrawTexture = Heli_HUD, 1, 2, 3, 4, 5, 6, 7, 8");
        assertEquals(List.of("texture heli_hud 101.0,52.0 3.0x4.0 uv 5.0,6.0 7.0x8.0 rot 0.0"), draw(h).ops);
    }

    // ---- manager ----

    @Test
    void managerRejectsOnlyTheBrokenFileAndLogsItsName() {
        MapResources res = new MapResources()
            .put("good.txt", "DrawString = 0, 0, \"g\"")
            .put("broken.txt", "If = 1", "DrawString = 0, 0, \"x\"")
            .put("dup.txt", "DrawString = 0, 0, \"first\"")
            .put("DUP.TXT", "DrawString = 0, 0, \"second\"")
            .put("notes.md", "ignored");
        MCH_HudManager.getInstance().load(res, this.log);
        assertNotNull(MCH_HudManager.get("good"));
        assertNull(MCH_HudManager.get("broken"));
        assertEquals(1, this.log.warnings.size(), this.log.warnings.toString());
        String w = this.log.warnings.get(0);
        assertTrue(w.contains("hud/broken.txt") && w.contains("Endif not found!"), "file name and cause logged: " + w);
        assertEquals(List.of("first"), drawManaged("DUP"), "reference: the first file of a name wins");
        assertEquals(List.of("Read 2 huds"), this.log.infos);
    }

    private List<String> drawManaged(String name) {
        Capture c = new Capture();
        MCH_HudManager.get(name).draw(c, this.state, MCH_HudManager::get);
        return c.texts();
    }
}
