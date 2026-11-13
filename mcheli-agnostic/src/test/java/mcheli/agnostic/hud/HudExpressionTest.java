package mcheli.agnostic.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import mcheli.agnostic.eval.eval.EvalException;
import org.junit.jupiter.api.Test;

/** The HUD's expression facade: compile-once {@link HudExpression} over the {@link HudVariables} scope. */
class HudExpressionTest {

    private final Map<String, Double> builtins = new HashMap<>();
    private final HudVariables vars = new HudVariables(n -> this.builtins.getOrDefault(n, 0.0));

    private double calc(String source) {
        return HudExpression.compile(HudExpression.toFormula(source)).eval(this.vars);
    }

    private int errorCode(String source) {
        return assertThrows(EvalException.class, () -> calc(source)).getErrorCode();
    }

    @Test
    void toFormulaMatchesTheReference() {
        assertEquals("altitude>10?0xff28d448:0xffdf0408", HudExpression.toFormula("Altitude > 10 ?\t#FF28d448 : #FFDF0408"));
    }

    @Test
    void builtInsAreReadLiveAndUnknownNamesAreUndefined() {
        this.builtins.put("fuel", 0.5);
        HudExpression e = HudExpression.compile("fuel*270-135");
        assertEquals(0.0, e.eval(this.vars));
        this.builtins.put("fuel", 1.0);
        assertEquals(135.0, e.eval(this.vars), "compiled once, evaluated against the current value");
        assertEquals(EvalException.EXP_NOT_DEF_VAR, errorCode("fule*2"), "a typo is an error, as in the reference");
    }

    @Test
    void hudConfigIdioms() {
        this.builtins.put("hp_rto", 0.87);
        this.builtins.put("stick_x", 1.0);
        assertEquals(1.0, calc("hp_rto>0.2? 1: 0"));
        assertEquals(-113.0, calc("-133+8+stick_x*12"));
        assertEquals(0.0, calc("is_uav || gunner_mode"));
        assertEquals(0xFF28D448, (int) (long) calc("hp_rto > 0.2 ? 0xFF28d448 : 0xFFDF0408"), "narrows to ARGB");
    }

    @Test
    void assignmentsPersistAcrossFramesExceptForBuiltIns() {
        this.builtins.put("altitude", 3.0);
        assertEquals(9.0, calc("(n = 4) + (altitude = 5)"));
        assertEquals(9.0, calc("n + altitude"), "same frame: both assigned values are visible");
        this.vars.beginFrame(n -> this.builtins.getOrDefault(n, 0.0), 0xFF000000);
        assertEquals(7.0, calc("n + altitude"), "next frame: n persists, altitude is the state's again");
        assertEquals(4278190080.0, calc("color"), "color is published unsigned at the start of the frame");
    }

    @Test
    void functionsAreZeroWithoutATargetAndReflectionIsRefused() {
        this.builtins.put("yaw", -3.0);
        assertEquals(0.0, calc("abs(yaw)"), "reference InvokeFunction result for a target-less call");
        assertEquals(EvalException.EXP_FUNC_CALL_ERROR, errorCode("yaw.intValue()"));
        assertEquals(EvalException.EXP_NOT_FLD_VALUE, errorCode("yaw.max_value"));
        assertEquals(EvalException.EXP_NOT_ARR_VALUE, errorCode("yaw[0]"));
    }

    @Test
    void describeGivesAnEnglishMessage() {
        EvalException e = assertThrows(EvalException.class, () -> HudExpression.compile("1+"));
        String d = HudExpression.describe(e);
        assertTrue(d.startsWith("expression ends early (code 1004"), d);
    }
}
