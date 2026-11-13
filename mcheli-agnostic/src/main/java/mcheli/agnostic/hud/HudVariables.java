package mcheli.agnostic.hud;

import java.util.HashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import mcheli.agnostic.eval.eval.func.Function;
import mcheli.agnostic.eval.eval.var.Variable;

/**
 * The variable scope HUD expressions evaluate against: the port of the reference's static {@code MCH_HudItem.varMap}
 * as seen through {@code MapVariable}, and the function resolver.
 *
 * <ul>
 *   <li><b>Built-ins</b> ({@link HudState#VARIABLES}) are read live from the current source: the constructor's, or
 *       the one {@link #beginFrame} installs until {@link #endFrame} drops it (built-ins then read 0).</li>
 *   <li><b>Assignments</b> ({@code x = …}, {@code x += …}, {@code x++}) store into the map. A later read in the same
 *       frame sees the stored value, built-in or not. When the next frame starts, built-in names are re-read from the
 *       state (reference {@code updateVarMap} overwrote their keys each frame), but other names persist.</li>
 *   <li><b>{@code color}</b> is the live current colour as an unsigned 32-bit value (reference {@code getColor()}).</li>
 *   <li>Any other name is <b>undefined</b>: the lookup returns null and the engine throws {@code EvalException} 2103,
 *       as the reference did.</li>
 *   <li><b>Functions:</b> a call without a target ({@code abs(x)}) is 0, the reference {@code InvokeFunction}
 *       result. Method calls, field access and array indexing on a value are refused. The reference resolved those by
 *       reflection on {@code java.lang.Double}. The port does not, so content-pack HUD files cannot reach arbitrary
 *       Java code. The refusal surfaces as an {@code EvalException} (codes 2201/2301/2401).</li>
 * </ul>
 */
public final class HudVariables implements Variable, Function {

    /** Built-ins outside a frame: every one reads 0. Holds no state. */
    static final ToDoubleFunction<String> NO_STATE = n -> 0.0;

    private final Map<Object, Object> map = new HashMap<>();
    private ToDoubleFunction<String> builtins;

    /** A standalone scope over {@code builtins}, which is asked only for names in {@link HudState#VARIABLES}. */
    public HudVariables(ToDoubleFunction<String> builtins) {
        this.builtins = builtins;
    }

    /** Start a frame (reference {@code MCH_HudItem.update} → {@code updateVarMap}): built-ins come from
     *  {@code builtins} again, discarding any value assigned to them last frame; user-assigned names persist; {@code color}
     *  is re-published from the current colour. */
    void beginFrame(ToDoubleFunction<String> builtins, int color) {
        this.builtins = builtins;
        this.map.keySet().removeAll(HudState.VARIABLES);
        setColor(color);
    }

    /** End a frame: drop the frame's {@code builtins}, which capture the {@link HudState} (and through it the
     *  vehicle, the player and their level), so a static scope does not keep them reachable between draws.
     *  Assigned names and {@code color} stay; until the next {@link #beginFrame} a built-in reads 0. */
    void endFrame() {
        this.builtins = NO_STATE;
    }

    /** Publish the current colour as the {@code color} variable (reference {@code updateVarMapItem("color", getColor())}). */
    void setColor(int argb) {
        this.map.put("color", Double.valueOf(argb & 0xFFFFFFFFL));
    }

    /** Forget every assigned name (tests; the reference map lived for the whole session). */
    void clear() {
        this.map.clear();
    }

    // ---- Variable (reference MapVariable semantics) ----

    @Override
    public Object getObject(Object name) {
        Object v = this.map.get(name);
        if (v != null) {
            return v;
        }
        if (name instanceof String n && HudState.VARIABLES.contains(n)) {
            return Double.valueOf(this.builtins.applyAsDouble(n));
        }
        return null; // undefined: VariableExpression throws EvalException(2103), exactly like the reference
    }

    @Override
    public void setValue(Object name, Object obj) {
        this.map.put(name, obj);
    }

    @Override
    public long evalLong(Object val) {
        return ((Number) val).longValue();
    }

    @Override
    public double evalDouble(Object val) {
        return ((Number) val).doubleValue();
    }

    @Override
    public Object getObject(Object array, int index) {
        throw refused("array indexing");
    }

    @Override
    public void setValue(Object array, int index, Object val) {
        throw refused("array indexing");
    }

    @Override
    public Object getObject(Object obj, String field) {
        throw refused("field access");
    }

    @Override
    public void setValue(Object obj, String field, Object val) {
        throw refused("field access");
    }

    // ---- Function (reference InvokeFunction: 0 / null without a target) ----

    @Override
    public long evalLong(Object target, String name, Long[] args) {
        if (target == null) {
            return 0L;
        }
        throw refused("method call");
    }

    @Override
    public double evalDouble(Object target, String name, Double[] args) {
        if (target == null) {
            return 0.0;
        }
        throw refused("method call");
    }

    @Override
    public Object evalObject(Object target, String name, Object[] args) {
        if (target == null) {
            return null;
        }
        throw refused("method call");
    }

    private static UnsupportedOperationException refused(String what) {
        return new UnsupportedOperationException(what + " on a value is not supported in HUD expressions");
    }
}
