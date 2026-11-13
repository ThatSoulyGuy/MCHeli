package mcheli.agnostic.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import mcheli.agnostic.eval.eval.EvalException;
import mcheli.agnostic.eval.eval.ExpRuleFactory;
import mcheli.agnostic.eval.eval.Expression;
import mcheli.agnostic.eval.eval.var.MapVariable;
import org.junit.jupiter.api.Test;

/**
 * The ported reference expression engine ({@code mcheli.agnostic.eval.eval}), driven exactly as the 1.7.10
 * {@code MCH_HudItem.calc} drove it: {@code ExpRuleFactory.getDefaultRule().parse(s)}, a {@link MapVariable} over a
 * {@code Map<String, Double>}, the default function resolver, {@code evalDouble()}. The expected values are the
 * reference's own semantics, including its quirks.
 */
class ExpressionEngineTest {

    private final Map<String, Object> vars = new HashMap<>();

    private Expression parse(String s) {
        Expression e = ExpRuleFactory.getDefaultRule().parse(s);
        e.setVariable(new MapVariable(this.vars));
        return e;
    }

    private double calc(String s) {
        return parse(s).evalDouble();
    }

    private int errorCode(String s) {
        return assertThrows(EvalException.class, () -> calc(s)).getErrorCode();
    }

    @Test
    void javaPrecedenceAndAssociativity() {
        assertEquals(14.0, calc("2+3*4"));
        assertEquals(20.0, calc("(2+3)*4"));
        assertEquals(2.0, calc("8-4-2"), "left-associative");
        assertEquals(-6.0, calc("-2*3"));
        assertEquals(5.0, calc("!0*5"), "unary binds tighter than *");
        assertEquals(1.0, calc("1<<2>3"), "shift binds tighter than relational");
        assertEquals(0.0, calc("2&3==3"), "equality binds tighter than &");
        assertEquals(0.0, calc("0&&0|1"), "| binds tighter than &&: 0&&(0|1)");
    }

    @Test
    void powerBindsTighterThanUnaryAndIsLeftAssociative() {
        assertEquals(-4.0, calc("-2**2"), "Sign rule sits above Power: -(2**2)");
        assertEquals(64.0, calc("2**3**2"), "Col2Rule: (2**3)**2");
        assertEquals(EvalException.PARSE_INVALID_OP, errorCode("2**-1"), "a power's right operand cannot be signed");
    }

    @Test
    void unaryOperators() {
        assertEquals(-3.0, calc("+-3"));
        assertEquals(1.0, calc("!0"));
        assertEquals(0.0, calc("!5"));
        assertEquals(-1.0, calc("~0"));
        assertEquals(-3.0, calc("~2.9"), "~ truncates to long first");
    }

    @Test
    void doublePathArithmetic() {
        assertEquals(3.5, calc("7/2"), "IEEE division on the double path");
        assertEquals(-1.0, calc("-7%3"), "Java remainder");
        assertEquals(Double.POSITIVE_INFINITY, calc("1/0"));
    }

    @Test
    void shiftsAreScalingOnTheDoublePath() {
        assertEquals(16.0, calc("1<<4"));
        assertEquals(3.0, calc("1.5<<1"), "a*2^b, not an integer shift");
        assertEquals(Math.sqrt(2.0), calc("1<<0.5"), 1e-12);
        assertEquals(-4.0, calc("-8>>1"));
        assertEquals(4.0, calc("-8>>>1"), ">>> is |a|/2^b");
        assertEquals(0.75, calc("3>>2"), "no truncation");
    }

    @Test
    void bitwiseTruncatesBothSidesToLong() {
        assertEquals(1.0, calc("5&3"));
        assertEquals(7.0, calc("5|3"));
        assertEquals(6.0, calc("5^3"));
        assertEquals(2.0, calc("2.9&3"));
        // The 4-arg Color formula: channels are shifted (scaled) then OR-ed, with no masking.
        assertEquals(4280865864.0, calc("((255)<<24)|((40)<<16)|((212)<<8 )|((72)<<0 )"));
    }

    @Test
    void comparisonsAndLogicalOperatorsReturnNumbers() {
        assertEquals(1.0, calc("3>2"));
        assertEquals(0.0, calc("3<2"));
        assertEquals(1.0, calc("2==2.0"));
        assertEquals(0.0, calc("1!=1"));
        assertEquals(1.0, calc("2>=2"));
        assertEquals(0.0, calc("0&&5"), "&& returns the left operand when it is 0");
        assertEquals(5.0, calc("2&&5"), "... else the right operand, not 1");
        assertEquals(7.0, calc("0||7"));
        assertEquals(3.0, calc("3||7"), "|| returns the first non-zero operand");
    }

    @Test
    void ternaryIsRightAssociative() {
        assertEquals(2.0, calc("1?2:3?4:5"));
        assertEquals(5.0, calc("0?2:0?4:5"));
        this.vars.put("altitude", 20.0);
        assertEquals(100.0, calc("altitude>10?100:200"));
    }

    @Test
    void numberLiterals() {
        assertEquals(4280865864.0, calc("0xff28d448"), "hex is an unsigned long, not a negative int");
        assertEquals(255.0, calc("0xFF"));
        assertEquals(1000.0, calc("1e3"), "Double.parseDouble is tried first");
        assertEquals(10.0, calc("10l"), "long suffix");
        assertEquals(100.0, calc("100."));
        assertEquals(5.0, calc("0b101"));
        assertEquals(4.0, calc("0b100"), "a 0 digit (the Vineflower NumberUtil artifact rejected it)");
        assertEquals(15.0, calc("0o17"));
        assertEquals(56.0, calc("0o70"));
        assertEquals(-1.0, calc("0x1ffffffffffffffff"), "hex overflow wraps silently");
        assertEquals(EvalException.EXP_NOT_NUMBER, errorCode("0x"));
        assertEquals(EvalException.EXP_NOT_NUMBER, errorCode("1_0"), "'_' is lexed into the number but never parsed");
        assertEquals(EvalException.PARSE_INVALID_OP, errorCode(".5"), "'.' is the field operator, not a decimal point");
    }

    @Test
    void literalValueIsStableAcrossEvaluations() {
        Expression e = parse("0xff28d448+0.5");
        assertEquals(4280865864.5, e.evalDouble());
        assertEquals(4280865864.5, e.evalDouble(), "the cached literal gives the same result");
        Expression bad = parse("1?2:0x");
        assertEquals(2.0, bad.evalDouble(), "a bad literal on an untaken branch never fails");
    }

    @Test
    void stringAndCharLiterals() {
        assertEquals(12.0, calc("\"12\""), "a string literal parses as a number");
        assertEquals(97.0, calc("'a'"));
        assertEquals(10.0, calc("'\\n'"));
    }

    @Test
    void variablesAssignmentAndIncrement() {
        this.vars.put("hp_rto", 0.87);
        assertEquals(87.0, calc("hp_rto*100"), 1e-9);
        assertEquals(EvalException.EXP_NOT_DEF_VAR, errorCode("nope+1"), "an undefined variable is an error");

        assertEquals(5.0, calc("x=5"));
        assertEquals(5.0, this.vars.get("x"));
        assertEquals(7.0, calc("x+=2"));
        assertEquals(7.0, calc("x++"), "post-increment returns the old value");
        assertEquals(8.0, this.vars.get("x"));
        assertEquals(7.0, calc("--x"), "-- is pre-decrement, not a double negation");
        assertEquals(EvalException.PARSE_STILL_EXIST, errorCode("x--1"),
            "x--1 lexes as x-- then 1: an item whose spaces are stripped turns a - -1 into this");
        assertEquals(EvalException.EXP_NOT_LET, errorCode("1=2"));
    }

    @Test
    void functionsWithoutATargetAreZeroAndCommaYieldsTheRightOperand() {
        this.vars.put("a", -3.0);
        assertEquals(0.0, calc("abs(a)"), "reference InvokeFunction returns 0 for a call with no target object");
        assertEquals(2.0, calc("1,2"));
        assertEquals(0.0, calc(""), "a blank expression is 0");
        assertEquals(0.0, calc("   "));
    }

    @Test
    void parseErrors() {
        assertEquals(EvalException.PARSE_STILL_EXIST, errorCode("1 2"));
        assertEquals(EvalException.PARSE_END_OF_STR, errorCode("1+"));
        assertEquals(EvalException.PARSE_NOT_FOUND_END_OP, errorCode("(1+2"));
        assertEquals(EvalException.PARSE_NOT_FOUND_END_OP, errorCode("1?2"));
    }

    @Test
    void longAndObjectPathsTypeTheirResults() {
        // The HUD only ever uses evalDouble; the other two paths keep the reference library's typing.
        assertEquals(3L, parse("7/2").evalLong(), "evalLong divides integers");
        assertEquals(3L, parse("7/2").eval(), "eval(): Long / Long is integer division and stays Long");
        assertEquals(3.5, parse("7.0/2.0").eval());
        Object pow = parse("2**3").eval();
        assertInstanceOf(Long.class, pow, "JavaExOperator.nn: an integral power of Long operands stays Long");
        assertEquals(8L, pow);
        assertInstanceOf(Double.class, parse("2.0**3.0").eval());
        assertEquals(Boolean.TRUE, parse("3>2").eval(), "comparisons are Boolean on the object path");
        assertThrows(RuntimeException.class, () -> parse("1+2.5").eval(), "Long + Double is undefined there");
    }
}
