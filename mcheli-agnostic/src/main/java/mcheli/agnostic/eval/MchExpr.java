package mcheli.agnostic.eval;

import java.util.ArrayList;
import java.util.List;

/**
 * A faithful subset port of MCHeli's {@code mcheli.eval} expression engine — the piece that makes the HUD
 * config-driven. HUD configs carry expressions like {@code altitude>10? 0xFF28d448: 0xFFDF0408},
 * {@code fuel*270-135}, {@code is_uav || gunner_mode}, {@code hp_rto>0.2? 1:0}, evaluated live against a variable map.
 *
 * <p>Only the {@code evalDouble} path the HUD uses is reproduced; the reference's functions, field/array access,
 * assignment, reflection, and search/refactor machinery are all dead code for the HUD and omitted. Faithful details:
 * <ul>
 *   <li><b>Preprocess</b> ({@link #toFormula}): lower-case, {@code #}→{@code 0x}, strip whitespace — so variable names
 *       and hex are case-insensitive.</li>
 *   <li><b>Double evaluation</b>: comparisons yield {@code 1.0}/{@code 0.0}; ternary condition is "non-zero = true";
 *       {@code &&}/{@code ||} short-circuit and return the operand value (not normalized).</li>
 *   <li><b>Java precedence</b> exactly (ternary right-assoc, unary tighter than {@code *}).</li>
 *   <li><b>Numbers</b>: {@code Double.parseDouble} first, else parse {@code 0x}/{@code 0b}/{@code 0o}/decimal as an
 *       unsigned {@code long} → double (so {@code 0xFF28D448} = {@code 4281751624.0}, a positive value; the narrowing
 *       to a signed ARGB int happens later at the colour assignment).</li>
 *   <li><b>Missing variable throws</b> (reference {@code EvalException(2103)}) — every referenced key must be supplied.</li>
 * </ul>
 */
public final class MchExpr {
    private MchExpr() {}

    /** Resolves a HUD variable name to a value (the reference's {@code MapVariable}). The impl decides missing-key
     *  behaviour; the reference threw {@code EvalException}, but a live HUD returns 0 for robustness (see the layer). */
    @FunctionalInterface
    public interface VarLookup {
        double get(String name);
    }

    /** A compiled expression: re-evaluated per frame against a fresh {@link VarLookup}. */
    public interface Node {
        double eval(VarLookup vars);
    }

    /** Compile once (parse is expensive; the AST holds variable NAMES, values are pulled live at eval). */
    public static Node compile(String source) {
        String f = toFormula(source);
        if (f.isEmpty()) {
            return v -> 0.0; // reference: blank expression is 0, not an error
        }
        return new Parser(f).parseFull();
    }

    /** Evaluate a compiled node against a variable lookup. */
    public static double eval(Node node, VarLookup vars) {
        return node.eval(vars);
    }

    /** Reference {@code MCH_HudItem.toFormula}: lower-case, {@code #}→{@code 0x}, strip tabs + spaces. */
    public static String toFormula(String s) {
        return s.toLowerCase().replace("#", "0x").replace("\t", "").replace(" ", "");
    }

    // ---- tokenizer ----

    private enum T { NUM, VAR, OP, LP, RP, QUES, COLON, EOF }

    private record Tok(T type, String text) {}

    private static final String[] MULTI = {">>>", "<<", ">>", "<=", ">=", "==", "!=", "&&", "||"};

    private static List<Tok> lex(String s) {
        List<Tok> out = new ArrayList<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (isNumStart(c)) {
                int j = i + 1;
                // 0x/0b/0o hex/bin/oct or decimal; number tokens keep '.' and '_' (reference NUMBER_CHAR).
                boolean hexish = c == '0' && j < n && (s.charAt(j) == 'x' || s.charAt(j) == 'b' || s.charAt(j) == 'o');
                if (hexish) {
                    j++;
                }
                while (j < n && (isHexDigit(s.charAt(j)) || s.charAt(j) == '.' || s.charAt(j) == '_')) {
                    if (!hexish && !isDigit(s.charAt(j)) && s.charAt(j) != '.' && s.charAt(j) != '_') {
                        break;
                    }
                    j++;
                }
                out.add(new Tok(T.NUM, s.substring(i, j)));
                i = j;
            } else if (isWordStart(c)) {
                int j = i + 1;
                while (j < n && isWordPart(s.charAt(j))) {
                    j++;
                }
                out.add(new Tok(T.VAR, s.substring(i, j)));
                i = j;
            } else if (c == '(') {
                out.add(new Tok(T.LP, "(")); i++;
            } else if (c == ')') {
                out.add(new Tok(T.RP, ")")); i++;
            } else if (c == '?') {
                out.add(new Tok(T.QUES, "?")); i++;
            } else if (c == ':') {
                out.add(new Tok(T.COLON, ":")); i++;
            } else {
                String m = null;
                for (String op : MULTI) {
                    if (s.startsWith(op, i)) {
                        m = op;
                        break;
                    }
                }
                if (m == null) {
                    m = String.valueOf(c);
                }
                out.add(new Tok(T.OP, m));
                i += m.length();
            }
        }
        out.add(new Tok(T.EOF, ""));
        return out;
    }

    private static boolean isDigit(char c) { return c >= '0' && c <= '9'; }
    private static boolean isNumStart(char c) { return isDigit(c) || c == '.'; }
    private static boolean isHexDigit(char c) { return isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'); }
    private static boolean isWordStart(char c) { return (c >= 'a' && c <= 'z') || c == '_'; }
    private static boolean isWordPart(char c) { return isWordStart(c) || isDigit(c); }

    // ---- precedence-climbing parser ----

    private static final class Parser {
        private final List<Tok> toks;
        private int pos;

        Parser(String s) {
            this.toks = lex(s);
        }

        Node parseFull() {
            Node n = parseExpr(0);
            if (peek().type != T.EOF) {
                throw new IllegalArgumentException("trailing input in expression near '" + peek().text + "'");
            }
            return n;
        }

        private Tok peek() { return toks.get(pos); }
        private Tok next() { return toks.get(pos++); }

        private Node parseExpr(int minPrec) {
            Node left = parseUnary();
            while (true) {
                Tok t = peek();
                int prec = binPrec(t);
                if (prec < 0 || prec < minPrec) {
                    break;
                }
                if (t.type == T.QUES) {
                    next(); // '?'
                    Node then = parseExpr(0);
                    if (peek().type != T.COLON) {
                        throw new IllegalArgumentException("ternary missing ':'");
                    }
                    next(); // ':'
                    Node els = parseExpr(prec); // right-assoc
                    Node c = left;
                    left = v -> c.eval(v) != 0.0 ? then.eval(v) : els.eval(v);
                } else {
                    String op = next().text;
                    Node right = parseExpr(prec + 1); // left-assoc
                    left = binary(op, left, right);
                }
            }
            return left;
        }

        private Node parseUnary() {
            Tok t = peek();
            if (t.type == T.OP && (t.text.equals("-") || t.text.equals("+") || t.text.equals("!") || t.text.equals("~"))) {
                next();
                Node operand = parseUnary();
                return switch (t.text) {
                    case "-" -> v -> -operand.eval(v);
                    case "+" -> operand;
                    case "!" -> v -> operand.eval(v) == 0.0 ? 1.0 : 0.0;
                    default -> v -> (double) (~(long) operand.eval(v));
                };
            }
            return parsePrimary();
        }

        private Node parsePrimary() {
            Tok t = next();
            switch (t.type) {
                case NUM -> {
                    double val = parseNumber(t.text);
                    return v -> val;
                }
                case VAR -> {
                    String name = t.text;
                    return v -> v.get(name);
                }
                case LP -> {
                    Node inner = parseExpr(0);
                    if (peek().type != T.RP) {
                        throw new IllegalArgumentException("missing ')'");
                    }
                    next();
                    return inner;
                }
                default -> throw new IllegalArgumentException("unexpected token '" + t.text + "'");
            }
        }
    }

    /** Binary/ternary precedence (higher binds tighter), matching Java + the reference rule chain. -1 = not binary. */
    private static int binPrec(Tok t) {
        if (t.type == T.QUES) {
            return 3; // ternary, right-assoc
        }
        if (t.type != T.OP) {
            return -1;
        }
        return switch (t.text) {
            case "||" -> 4;
            case "&&" -> 5;
            case "|" -> 6;
            case "^" -> 7;
            case "&" -> 8;
            case "==", "!=" -> 9;
            case "<", "<=", ">", ">=" -> 10;
            case "<<", ">>", ">>>" -> 11;
            case "+", "-" -> 12;
            case "*", "/", "%" -> 13;
            default -> -1;
        };
    }

    private static Node binary(String op, Node a, Node b) {
        return switch (op) {
            case "+" -> v -> a.eval(v) + b.eval(v);
            case "-" -> v -> a.eval(v) - b.eval(v);
            case "*" -> v -> a.eval(v) * b.eval(v);
            case "/" -> v -> a.eval(v) / b.eval(v);           // IEEE double division (reference)
            case "%" -> v -> a.eval(v) % b.eval(v);
            case "<" -> v -> a.eval(v) < b.eval(v) ? 1.0 : 0.0;
            case "<=" -> v -> a.eval(v) <= b.eval(v) ? 1.0 : 0.0;
            case ">" -> v -> a.eval(v) > b.eval(v) ? 1.0 : 0.0;
            case ">=" -> v -> a.eval(v) >= b.eval(v) ? 1.0 : 0.0;
            case "==" -> v -> a.eval(v) == b.eval(v) ? 1.0 : 0.0;
            case "!=" -> v -> a.eval(v) != b.eval(v) ? 1.0 : 0.0;
            // Short-circuit, value-returning (reference And/OrExpression): a&&b = a==0?a:b ; a||b = a!=0?a:b
            case "&&" -> v -> { double x = a.eval(v); return x == 0.0 ? x : b.eval(v); };
            case "||" -> v -> { double x = a.eval(v); return x != 0.0 ? x : b.eval(v); };
            case "&" -> v -> (double) ((long) a.eval(v) & (long) b.eval(v));
            case "|" -> v -> (double) ((long) a.eval(v) | (long) b.eval(v));
            case "^" -> v -> (double) ((long) a.eval(v) ^ (long) b.eval(v));
            // Reference shift operators evaluate on the double path: <<=vl*2^vr, >>=vl/2^vr, >>>=|vl|/2^vr.
            case "<<" -> v -> a.eval(v) * Math.pow(2.0, b.eval(v));
            case ">>" -> v -> a.eval(v) / Math.pow(2.0, b.eval(v));
            case ">>>" -> v -> Math.abs(a.eval(v)) / Math.pow(2.0, b.eval(v));
            default -> throw new IllegalArgumentException("unknown operator '" + op + "'");
        };
    }

    /** Reference {@code NumberExpression.evalDouble}: try double first, else parse as an unsigned long (hex/bin/oct/dec). */
    static double parseNumber(String w) {
        try {
            return Double.parseDouble(w);
        } catch (NumberFormatException e) {
            return (double) parseLong(w);
        }
    }

    private static long parseLong(String raw) {
        String w = raw.replace("_", "");
        // strip a trailing '.', 'L', or 'l' (reference NumberUtil)
        while (!w.isEmpty()) {
            char last = w.charAt(w.length() - 1);
            if (last == '.' || last == 'L' || last == 'l') {
                w = w.substring(0, w.length() - 1);
            } else {
                break;
            }
        }
        int radix = 10;
        if (w.length() > 2 && w.charAt(0) == '0') {
            char p = Character.toLowerCase(w.charAt(1));
            if (p == 'x') { radix = 16; w = w.substring(2); }
            else if (p == 'b') { radix = 2; w = w.substring(2); }
            else if (p == 'o') { radix = 8; w = w.substring(2); }
        }
        return w.isEmpty() ? 0L : Long.parseUnsignedLong(w, radix);
    }
}
