package io.github.metrovoc.residual.notes;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

/**
 * The website's live widgets against the game's (F6, F7): {@code src/website/live-math.js}, run here in a JavaScript engine,
 * gives the same single-precision numbers as the engine models run on, step for step. Both use the same exp, log and sqrt in
 * this test, so the numbers are equal to the bit; a browser's may round those three differently in the last place of a double,
 * which a float hardly ever shows.
 */
class LiveMathTest {
    private static final String PRACTICE = "the mill turns when the river runs and the bread comes after";
    private static final String TEST = "the river runs and the mill turns the wheel at the ford";
    private static Context js;
    private static Scriptable scope;

    @BeforeAll
    static void load() throws IOException {
        js = Context.enter();
        js.setOptimizationLevel(-1);
        js.setLanguageVersion(Context.VERSION_ES6);
        scope = js.initStandardObjects();
        Path script = Path.of(System.getProperty("residual.resources"), "../../website/live-math.js");
        js.evaluateString(scope, Files.readString(script), "live-math.js", 1, null);
    }

    @AfterAll
    static void unload() {
        Context.exit();
    }

    private static JsonElement run(String expression) {
        return JsonParser.parseString((String) js.evaluateString(scope, "JSON.stringify(" + expression + ")", "call", 1, null));
    }

    private static float[] floats(JsonElement numbers) {
        JsonArray array = numbers.getAsJsonArray();
        float[] out = new float[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i).isJsonNull() ? Float.NEGATIVE_INFINITY : (float) array.get(i).getAsDouble();
        }
        return out;
    }

    @Test
    void softmaxGivesTheEnginesNumbers() {
        float[][] scores = {{2, 1, 0, -1}, {0, 0, 0, 0}, {0.3f, -1.7f, 2.9f, 0.01f, -0.5f}, {6, -6, 5.9f, -5.9f}, {50, -50}, {1e-3f}};
        float[] temperatures = {1, 0.5f, 0.1f, 2, 5, 0};
        for (float[] s : scores) {
            for (float t : temperatures) {
                float[] engine = LiveMath.softmax(s, t);
                float[] site = floats(run("LiveMath.softmax(" + Arrays.toString(s) + ", " + t + ")"));
                assertArrayEquals(engine, site, Arrays.toString(s) + " at " + t);
            }
        }
    }

    @Test
    void attentionGivesTheEnginesNumbers() {
        float[][] points = {{1.4f, 0.1f, -0.5f, 1.3f, 1.1f, 0.9f, -1.2f, -0.4f, 1.2f, 0.7f}, {0, 0, 0, 0, 0, 0}, {3, -3, -3, 3, 0.25f, 2.5f, -0.75f, -2.5f}};
        for (float[] x : points) {
            for (boolean causal : new boolean[]{true, false}) {
                LiveMath.Attention engine = LiveMath.attention(x, 2, causal);
                JsonElement site = run("LiveMath.attention(" + Arrays.toString(x) + ", 2, " + causal + ")");
                assertArrayEquals(engine.scores(), floats(site.getAsJsonObject().get("scores")));
                assertArrayEquals(engine.weights(), floats(site.getAsJsonObject().get("weights")));
                assertArrayEquals(engine.out(), floats(site.getAsJsonObject().get("out")));
            }
        }
    }

    @Test
    void trainingGivesTheEnginesCurve() {
        for (float rate : new float[]{1e-3f, 1e-2f, 0.1f, 1.5f}) {
            LiveMath.Curve engine = new LiveMath.Curve(PRACTICE, TEST, rate);
            js.evaluateString(scope, "var curve = new LiveMath.Curve('" + PRACTICE + "', '" + TEST + "', " + rate + ")", "call", 1, null);
            for (int step = 0; step < 400; step++) {
                assertArrayEquals(engine.step(), floats(run("curve.next()")), "step " + step + " at " + rate);
            }
        }
    }

    /** What the notes say the widget shows: both curves start at the chance loss, and at 0.1 the two part ways. */
    @Test
    void theCurveShowsWhatTheNotesSay() {
        LiveMath.Curve curve = new LiveMath.Curve(PRACTICE, TEST, 0.1f);
        float[] first = curve.step();
        assertEquals(Math.log(19), first[0], 1e-5);
        assertEquals(Math.log(19), first[1], 1e-5);
        float lowest = first[1];
        float[] last = first;
        for (int step = 1; step < 400; step++) {
            last = curve.step();
            lowest = Math.min(lowest, last[1]);
        }
        assertTrue(last[0] < 1.0, "practice " + last[0]);
        assertTrue(last[1] > lowest + 0.2, "test " + last[1] + " from " + lowest);
    }
}
