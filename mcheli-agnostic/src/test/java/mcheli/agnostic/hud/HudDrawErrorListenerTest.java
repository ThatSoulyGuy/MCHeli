package mcheli.agnostic.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import mcheli.agnostic.hud.HudTestSupport.Capture;
import mcheli.agnostic.hud.HudTestSupport.TestLog;
import mcheli.agnostic.hud.HudTestSupport.TestState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@link MCH_Hud.DrawErrorListener}: the draw-time failures the log reports only once still reach a caller (a test)
 *  on every frame, from Called HUDs too, without changing what is drawn or logged. */
class HudDrawErrorListenerTest {

    private final TestLog log = new TestLog();
    private final TestState state = new TestState();
    private final List<String> errors = new ArrayList<>();
    private final MCH_Hud.DrawErrorListener listener =
        (file, line, e) -> this.errors.add(file + ":" + line + " " + e.getClass().getSimpleName());

    @BeforeEach
    @AfterEach
    void reset() {
        MCH_Hud.resetSharedState();
    }

    @Test
    void everyFailureOfEveryFrameReachesTheListenerWhileTheLogWarnsOnce() {
        MCH_Hud h = HudTestSupport.hud("t", this.log,
            "DrawString = nope, 0, \"x\"",                // undefined variable
            "DrawString = 0, 0, \"%d\", WPN_NAME",        // %d of a String
            "DrawString = 0, 0, \"y\"");
        this.state.args.put("WPN_NAME", "M230");
        for (int frame = 0; frame < 2; frame++) {
            Capture c = new Capture();
            h.draw(c, this.state, n -> null, this.listener);
            assertEquals(List.of("y"), c.texts(), "the failing items are still skipped");
        }
        assertEquals(4, this.errors.size(), this.errors.toString());
        assertTrue(this.errors.get(0).startsWith("hud/t.txt:1 "), this.errors.get(0));
        assertTrue(this.errors.get(1).startsWith("hud/t.txt:2 "), this.errors.get(1));
        assertEquals(2, this.log.warnings.size(), "the log still reports each item once: " + this.log.warnings);
    }

    @Test
    void aCalledHudReportsUnderItsOwnFile() {
        MCH_Hud inner = HudTestSupport.hud("inner", this.log, "DrawRect = 0, 0, missing_var, 1");
        MCH_Hud outer = HudTestSupport.hud("outer", this.log, "Call = inner", "DrawString = 0, 0, \"ok\"");
        Capture c = new Capture();
        outer.draw(c, this.state, n -> n.equals("inner") ? inner : null, this.listener);
        assertEquals(List.of("ok"), c.texts());
        assertEquals(1, this.errors.size(), this.errors.toString());
        assertTrue(this.errors.get(0).startsWith("hud/inner.txt:1 "), this.errors.get(0));
    }

    @Test
    void aCleanHudReportsNothingAndTheListenerIsOptional() {
        MCH_Hud h = HudTestSupport.hud("t", this.log, "DrawString = 0, 0, \"%d\", HP");
        this.state.args.put("HP", 7);
        h.draw(new Capture(), this.state, n -> null, this.listener);
        assertTrue(this.errors.isEmpty(), this.errors.toString());
        h.draw(new Capture(), this.state, n -> null); // the plain overload passes no listener
        assertTrue(this.log.warnings.isEmpty(), this.log.warnings.toString());
    }
}
