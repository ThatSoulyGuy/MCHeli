package mcheli.agnostic.hud;

import java.util.Locale;
import mcheli.agnostic.eval.eval.EvalException;
import mcheli.agnostic.eval.eval.ExpRuleFactory;
import mcheli.agnostic.eval.eval.Expression;

/**
 * One numeric HUD argument, evaluated with exactly the 1.7.10 semantics: the reference {@code MCH_HudItem.calc} ran
 * {@code ExpRuleFactory.getDefaultRule().parse(s)} against a {@code MapVariable} over its variable map and returned
 * {@code evalDouble()}, and this goes through the same ported engine ({@code mcheli.agnostic.eval.eval}). Everything is
 * evaluated on the engine's double path: comparisons give {@code 1.0}/{@code 0.0}, {@code &&}/{@code ||} return an
 * operand, {@code /} is IEEE division, {@code &|^~} truncate both sides to {@code long}, the shifts are
 * {@code a*2^b} / {@code a/2^b} / {@code |a|/2^b}, {@code **} is {@code Math.pow}, {@code =}, {@code +=}, {@code ++} and
 * the other assignments write the variable scope, and a hex literal is an unsigned {@code long}.
 *
 * <p>Unlike the reference, which re-parsed the string at every {@code calc} call, the expression is parsed ONCE here
 * and the tree is re-evaluated each frame.
 */
public final class HudExpression {

    private final String formula;
    private final Expression exp;

    private HudExpression(String formula, Expression exp) {
        this.formula = formula;
        this.exp = exp;
    }

    /** Parse {@code formula}, which must already have the item's reference preprocessing applied (see
     *  {@link #toFormula}). A blank formula is the engine's empty expression, which evaluates to 0.
     *  @throws EvalException on a syntax error, as the reference parse did */
    public static HudExpression compile(String formula) {
        return new HudExpression(formula, ExpRuleFactory.getDefaultRule().parse(formula));
    }

    /** Evaluate against a variable scope (reference {@code exp.setVariable(new MapVariable(varMap)); exp.evalDouble()}).
     *  The scope is also the function resolver, so no expression can reach the engine's reflective defaults.
     *  @throws EvalException e.g. for an undefined variable (reference code 2103) or an unparseable literal */
    public double eval(HudVariables vars) {
        this.exp.setVariable(vars);
        this.exp.setFunction(vars);
        return this.exp.evalDouble();
    }

    /** The source text as it was compiled (after preprocessing). */
    public String formula() {
        return this.formula;
    }

    @Override
    public String toString() {
        return this.formula;
    }

    /** Reference {@code MCH_HudItem.toFormula}: lower-case, {@code #}→{@code 0x}, strip tabs and spaces. Lower-casing uses
     *  {@link Locale#ROOT}: the reference used the platform locale, which breaks ASCII names such as {@code IS_UAV} under
     *  a Turkish locale. */
    public static String toFormula(String s) {
        return s.toLowerCase(Locale.ROOT).replace("#", "0x").replace("\t", " ").replace(" ", "");
    }

    /** A one-line English description of an evaluation failure, for the log. The engine's own messages are Japanese. */
    static String describe(Throwable t) {
        if (!(t instanceof EvalException e)) {
            return t.toString();
        }
        String what = switch (e.getErrorCode()) {
            case EvalException.PARSE_NOT_FOUND_END_OP -> "missing closing operator";
            case EvalException.PARSE_INVALID_OP -> "misplaced operator";
            case EvalException.PARSE_INVALID_CHAR -> "unsupported token";
            case EvalException.PARSE_END_OF_STR -> "expression ends early";
            case EvalException.PARSE_STILL_EXIST -> "unexpected trailing input";
            case EvalException.PARSE_NOT_FUNC -> "not callable as a function";
            case EvalException.EXP_NOT_VARIABLE -> "not a variable";
            case EvalException.EXP_NOT_NUMBER -> "not a number";
            case EvalException.EXP_NOT_LET -> "cannot be assigned";
            case EvalException.EXP_NOT_DEF_VAR -> "undefined variable";
            case EvalException.EXP_NOT_DEF_OBJ -> "undefined object";
            case EvalException.EXP_NOT_ARR_VALUE, EvalException.EXP_NOT_LET_ARR -> "array access is not supported";
            case EvalException.EXP_NOT_FLD_VALUE, EvalException.EXP_NOT_LET_FIELD -> "field access is not supported";
            case EvalException.EXP_FUNC_CALL_ERROR -> "method calls on values are not supported";
            default -> "evaluation error";
        };
        StringBuilder sb = new StringBuilder(what).append(" (code ").append(e.getErrorCode());
        if (e.getWord() != null && !e.getWord().isEmpty()) {
            sb.append(", '").append(e.getWord()).append('\'');
        }
        if (e.getPos() >= 0) {
            sb.append(", pos ").append(e.getPos());
        }
        return sb.append(')').toString();
    }
}
