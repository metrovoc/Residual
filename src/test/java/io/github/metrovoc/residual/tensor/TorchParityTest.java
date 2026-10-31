package io.github.metrovoc.residual.tensor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.metrovoc.residual.learn.Trainer;
import io.github.metrovoc.residual.tensor.Ops.Activation;
import io.github.metrovoc.residual.tensor.Prims.Binary;
import io.github.metrovoc.residual.tensor.Prims.Unary;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * The engine against reference values computed once with PyTorch by {@code tools/torch_fixtures.py}: each operation's
 * output and the gradients of sum(output * seed) for fixed float32 inputs, and AdamW stepped as the trainer steps it.
 * The references were computed in float64, so the tolerance covers only the engine's own float32 rounding.
 */
class TorchParityTest {
    /**
     * How far a value may stray from its reference, as a fraction of the largest magnitude in its tensor, or of 1 when
     * that is smaller: about 80 float32 ulps. Inputs and seeds are of order 1, so even a tiny result, like the gradient
     * of a softmax one entry dominates, carries the rounding of terms that size. A different formula shows far above
     * it: GELU's erf form differs from its tanh form by up to 5e-4, and with eps outside the square root the narrow
     * layer norm moves by most of its size.
     */
    private static final double TOLERANCE = 1e-5;

    /** An engine call on one case's inputs and arguments. */
    private interface Call {
        Tensor apply(Tape tape, Case c);
    }

    private static final Map<String, Call> OPS = Map.ofEntries(
            Map.entry("matmul", (t, c) -> Ops.matmul(t, c.in(0), c.in(1), c.flag("trans_b"))),
            Map.entry("add", (t, c) -> Ops.add(t, c.in(0), c.in(1))),
            Map.entry("mul", (t, c) -> Ops.mul(t, c.in(0), c.in(1))),
            Map.entry("scale", (t, c) -> Ops.scale(t, c.in(0), c.number("factor"))),
            Map.entry("activate", (t, c) -> Ops.activate(t, c.in(0), Activation.byId(c.string("fn")))),
            Map.entry("softmax", (t, c) -> Ops.softmax(t, c.in(0))),
            Map.entry("causal_mask", (t, c) -> Ops.causalMask(t, c.in(0))),
            Map.entry("layer_norm", (t, c) -> Ops.layerNorm(t, c.in(0), c.in(1), c.in(2), c.number("eps"))),
            Map.entry("rms_norm", (t, c) -> Ops.rmsNorm(t, c.in(0), c.in(1), c.number("eps"))),
            Map.entry("embedding", (t, c) -> Ops.embedding(t, c.tokens("ids"), c.in(0))),
            Map.entry("positions", (t, c) -> Ops.positions(t, c.in(0), c.integer("time"))),
            Map.entry("cross_entropy", (t, c) -> Ops.crossEntropy(t, c.in(0), c.ints("targets"), c.floats("weights"))),
            Map.entry("split_heads", (t, c) -> Ops.splitHeads(t, c.in(0), c.integer("heads"))),
            Map.entry("merge_heads", (t, c) -> Ops.mergeHeads(t, c.in(0))),
            Map.entry("shift", (t, c) -> Ops.shift(t, c.in(0), c.integer("steps"))),
            Map.entry("concat", (t, c) -> Ops.concat(t, c.in(0), c.in(1))),
            Map.entry("sum", (t, c) -> Ops.sum(t, c.in(0))));

    private static final Map<String, Call> PRIMS = Map.ofEntries(
            Map.entry("binary", (t, c) -> Prims.binary(t, Binary.valueOf(c.string("op")), c.in(0), c.in(1))),
            Map.entry("unary", (t, c) -> Prims.unary(t, Unary.valueOf(c.string("op")), c.in(0))),
            Map.entry("sum", (t, c) -> Prims.sum(t, c.in(0))),
            Map.entry("amax", (t, c) -> Prims.amax(t, c.in(0))),
            Map.entry("transpose", (t, c) -> Prims.transpose(t, c.in(0))),
            Map.entry("unsqueeze", (t, c) -> Prims.unsqueeze(t, c.in(0))),
            Map.entry("squeeze", (t, c) -> Prims.squeeze(t, c.in(0))),
            Map.entry("matmul", (t, c) -> Prims.matmul(t, c.in(0), c.in(1))),
            Map.entry("causal", (t, c) -> Prims.causal(t, c.in(0))));

    /** The salvaged machines' kernels, built from primitives: they must match the fused ones' references. */
    private static final Map<String, Call> UNFUSED = Map.of(
            "matmul", (t, c) -> Unfused.matmul(t, c.in(0), c.in(1), c.flag("trans_b")),
            "softmax", (t, c) -> Unfused.softmax(t, c.in(0)),
            "layer_norm", (t, c) -> Unfused.layerNorm(t, c.in(0), c.in(1), c.in(2), c.number("eps")),
            "rms_norm", (t, c) -> Unfused.rmsNorm(t, c.in(0), c.in(1), c.number("eps")),
            "activate", (t, c) -> Unfused.activate(t, c.in(0), Activation.byId(c.string("fn"))));

    @TestFactory
    Stream<DynamicTest> opsMatchPyTorch() throws IOException {
        return replay("ops.json", OPS);
    }

    @TestFactory
    Stream<DynamicTest> primsMatchPyTorch() throws IOException {
        return replay("prims.json", PRIMS);
    }

    /**
     * Not at the activations' edges, where building them from primitives differs by design: max gives a tie's gradient
     * to its first operand, so max(x, 0) passes it on at exactly 0 where relu passes none; and past exp's float32
     * range 1/(1 + exp(-x)) has a NaN gradient, as the same formula has in PyTorch.
     */
    @TestFactory
    Stream<DynamicTest> unfusedKernelsMatchPyTorch() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        JsonObject fixture = read("ops.json");
        for (String op : fixture.keySet()) {
            for (JsonElement e : fixture.getAsJsonArray(op)) {
                if (UNFUSED.containsKey(op) && !e.getAsJsonObject().get("name").getAsString().endsWith("edges")) {
                    tests.add(check("unfused " + op, e.getAsJsonObject(), UNFUSED.get(op)));
                }
            }
        }
        return tests.stream();
    }

    /** Trainer's update on the gradients a backward pass leaves: clipping, the warmed-up rate, then the AdamW step. */
    @Test
    void optimizerStepsMatchPyTorch() throws IOException {
        for (JsonElement e : read("adamw.json").getAsJsonArray("adamw")) {
            JsonObject run = e.getAsJsonObject();
            List<Tensor> params = new ArrayList<>();
            for (JsonElement p : run.getAsJsonArray("params")) {
                params.add(tensor(p.getAsJsonObject()));
            }
            AdamW optimizer = new AdamW(params);
            float lr = run.get("lr").getAsFloat();
            float clip = run.get("clip").getAsFloat();
            JsonArray steps = run.getAsJsonArray("steps");
            for (int s = 0; s < steps.size(); s++) {
                JsonObject step = steps.get(s).getAsJsonObject();
                String at = run.get("name").getAsString() + " step " + s;
                for (int i = 0; i < params.size(); i++) {
                    float[] grad = floats(step.getAsJsonArray("grads").get(i).getAsJsonArray());
                    System.arraycopy(grad, 0, params.get(i).grad(), 0, grad.length);
                }
                double norm = optimizer.clip(clip);
                optimizer.step(Trainer.rate(lr, s));
                assertEquals(step.get("norm").getAsDouble(), norm, TOLERANCE * norm, at + " norm");
                JsonArray after = step.getAsJsonArray("params");
                for (int i = 0; i < params.size(); i++) {
                    assertClose(floats(after.get(i).getAsJsonArray()), params.get(i).data, at + " parameter " + i);
                }
            }
        }
    }

    /** Every case of {@code file}, run through the call its operation names; each operation needs both. */
    private static Stream<DynamicTest> replay(String file, Map<String, Call> calls) throws IOException {
        JsonObject fixture = read(file);
        assertEquals(calls.keySet(), fixture.keySet(), "operations in " + file);
        List<DynamicTest> tests = new ArrayList<>();
        for (String op : fixture.keySet()) {
            for (JsonElement e : fixture.getAsJsonArray(op)) {
                tests.add(check(op, e.getAsJsonObject(), calls.get(op)));
            }
        }
        return tests.stream();
    }

    /** One case on fresh inputs: its output, then the gradients of sum(output * seed). */
    private static DynamicTest check(String op, JsonObject json, Call call) {
        String name = op + " " + json.get("name").getAsString();
        return DynamicTest.dynamicTest(name, () -> {
            Case c = new Case(json);
            Tape tape = new Tape();
            Tensor out = call.apply(tape, c);
            JsonObject want = json.getAsJsonObject("output");
            assertArrayEquals(ints(want.getAsJsonArray("shape")), out.shape, name + " shape");
            assertClose(floats(want.getAsJsonArray("data")), out.data, name + " output");
            Tensor seed = Tensor.of(out.shape.clone(), floats(json.getAsJsonArray("seed")));
            tape.backward(Ops.sum(tape, Ops.mul(tape, out, seed)));
            JsonArray grads = json.getAsJsonArray("grads");
            for (int i = 0; i < grads.size(); i++) {
                assertClose(floats(grads.get(i).getAsJsonArray()), c.in(i).grad(), name + " gradient " + i);
            }
        });
    }

    /** Each value within {@link #TOLERANCE} of its reference; infinities exactly. */
    private static void assertClose(float[] want, float[] got, String what) {
        assertEquals(want.length, got.length, what + " size");
        double scale = 1;
        for (float w : want) {
            if (Float.isFinite(w)) {
                scale = Math.max(scale, Math.abs(w));
            }
        }
        for (int i = 0; i < want.length; i++) {
            if (Float.isInfinite(want[i])) {
                assertEquals(want[i], got[i], what + " element " + i);
            } else {
                assertEquals(want[i], got[i], TOLERANCE * scale, what + " element " + i);
            }
        }
    }

    /** One case's inputs, made afresh so their gradients start at zero, and its arguments. */
    private static final class Case {
        private final JsonObject json;
        private final List<Tensor> inputs = new ArrayList<>();

        Case(JsonObject json) {
            this.json = json;
            for (JsonElement e : json.getAsJsonArray("inputs")) {
                inputs.add(tensor(e.getAsJsonObject()));
            }
        }

        Tensor in(int i) {
            return inputs.get(i);
        }

        boolean flag(String key) {
            return json.get(key).getAsBoolean();
        }

        int integer(String key) {
            return json.get(key).getAsInt();
        }

        float number(String key) {
            return json.get(key).getAsFloat();
        }

        String string(String key) {
            return json.get(key).getAsString();
        }

        int[] ints(String key) {
            return TorchParityTest.ints(json.getAsJsonArray(key));
        }

        /** Null when the fixture has none. */
        float[] floats(String key) {
            JsonElement e = json.get(key);
            return e.isJsonNull() ? null : TorchParityTest.floats(e.getAsJsonArray());
        }

        /** Rows of token ids, [batch, time]. */
        Tokens tokens(String key) {
            JsonArray rows = json.getAsJsonArray(key);
            int time = rows.get(0).getAsJsonArray().size();
            int[] ids = new int[rows.size() * time];
            for (int b = 0; b < rows.size(); b++) {
                System.arraycopy(TorchParityTest.ints(rows.get(b).getAsJsonArray()), 0, ids, b * time, time);
            }
            return new Tokens(ids, rows.size(), time);
        }
    }

    private static JsonObject read(String file) throws IOException {
        try (InputStream in = TorchParityTest.class.getResourceAsStream("/torch/" + file)) {
            assertNotNull(in, "no fixture " + file + "; tools/torch_fixtures.py writes them");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static Tensor tensor(JsonObject json) {
        return new Tensor(ints(json.getAsJsonArray("shape")), floats(json.getAsJsonArray("data")), true);
    }

    private static int[] ints(JsonArray a) {
        int[] out = new int[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).getAsInt();
        }
        return out;
    }

    /** Python writes an infinity as -Infinity, which Gson reads as a string that getAsFloat still parses. */
    private static float[] floats(JsonArray a) {
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).getAsFloat();
        }
        return out;
    }
}
