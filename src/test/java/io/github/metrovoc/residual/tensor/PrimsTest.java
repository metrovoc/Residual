package io.github.metrovoc.residual.tensor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.metrovoc.residual.tensor.Ops.Activation;
import io.github.metrovoc.residual.tensor.Prims.Binary;
import io.github.metrovoc.residual.tensor.Prims.Unary;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Primitive gradients against finite differences, and unfused modules against the fused kernels. */
class PrimsTest {
    private final Random rng = new Random(7);

    private Tensor rand(int... shape) {
        Tensor t = Tensor.zeros(true, shape);
        for (int i = 0; i < t.data.length; i++) {
            t.data[i] = (float) rng.nextGaussian();
        }
        return t;
    }

    private Tensor positive(int... shape) {
        Tensor t = rand(shape);
        for (int i = 0; i < t.data.length; i++) {
            t.data[i] = 0.5f + Math.abs(t.data[i]);
        }
        return t;
    }

    private void check(String name, List<Tensor> inputs, Function<Tape, Tensor> f) {
        Tensor probe = f.apply(null);
        float[] w = new float[probe.size()];
        for (int i = 0; i < w.length; i++) {
            w[i] = (float) rng.nextGaussian();
        }
        Function<Tape, Tensor> loss = tape -> {
            Tensor out = f.apply(tape);
            return Ops.sum(tape, Ops.mul(tape, out, Tensor.of(out.shape.clone(), w.clone())));
        };
        for (Tensor t : inputs) {
            t.zeroGrad();
        }
        Tape tape = new Tape();
        tape.backward(loss.apply(tape));
        for (int ti = 0; ti < inputs.size(); ti++) {
            Tensor t = inputs.get(ti);
            float[] analytic = t.grad().clone();
            for (int i = 0; i < t.data.length; i++) {
                float old = t.data[i];
                float h = 1e-2f;
                t.data[i] = old + h;
                double up = loss.apply(null).item();
                t.data[i] = old - h;
                double down = loss.apply(null).item();
                t.data[i] = old;
                double numeric = (up - down) / (2 * h);
                assertEquals(numeric, analytic[i], 2e-2 * Math.max(1.0, Math.abs(numeric)), name + " input " + ti + " element " + i);
            }
        }
    }

    @Test
    void broadcastShapes() {
        assertArrayEquals(new int[]{2, 3, 4}, Prims.broadcast(new int[]{2, 1, 4}, new int[]{3, 1}));
        assertArrayEquals(new int[]{5, 4}, Prims.broadcast(new int[]{5, 4}, new int[]{1}));
    }

    @Test
    void binaryBroadcasting() {
        for (Binary op : Binary.values()) {
            Tensor a = rand(2, 3, 4);
            Tensor b = op == Binary.DIV ? positive(3, 1) : rand(3, 1);
            check(op.name(), List.of(a, b), t -> Prims.binary(t, op, a, b));
            Tensor c = rand(4);
            Tensor d = op == Binary.DIV ? positive(2, 1, 4) : rand(2, 1, 4);
            check(op.name() + " swapped", List.of(c, d), t -> Prims.binary(t, op, c, d));
        }
    }

    @Test
    void unary() {
        for (Unary op : Unary.values()) {
            Tensor x = op == Unary.LOG || op == Unary.SQRT ? positive(3, 4) : rand(3, 4);
            check(op.name(), List.of(x), t -> Prims.unary(t, op, x));
        }
    }

    @Test
    void axes() {
        Tensor x = rand(2, 3, 4);
        check("sum", List.of(x), t -> Prims.sum(t, x));
        check("amax", List.of(x), t -> Prims.amax(t, x));
        check("transpose", List.of(x), t -> Prims.transpose(t, x));
        check("unsqueeze", List.of(x), t -> Prims.unsqueeze(t, x));
        Tensor y = rand(3, 4, 1);
        check("squeeze", List.of(y), t -> Prims.squeeze(t, y));
    }

    @Test
    void unfusedMatchesFused() {
        Tensor x = rand(2, 5, 8);
        Tensor g = rand(8);
        Tensor b = rand(8);
        assertClose(Ops.softmax(null, x), Unfused.softmax(null, x));
        assertClose(Ops.layerNorm(null, x, g, b, 1e-5f), Unfused.layerNorm(null, x, g, b, 1e-5f));
        assertClose(Ops.rmsNorm(null, x, g, 1e-5f), Unfused.rmsNorm(null, x, g, 1e-5f));
        for (Activation f : Activation.values()) {
            assertClose(Ops.activate(null, x, f), Unfused.activate(null, x, f));
        }
        check("unfused softmax", List.of(x), t -> Unfused.softmax(t, x));
        check("unfused layer norm", List.of(x, g, b), t -> Unfused.layerNorm(t, x, g, b, 1e-5f));
        check("unfused gelu", List.of(x), t -> Unfused.activate(t, x, Activation.GELU));
        Tensor w = rand(8, 3);
        Tensor k = rand(2, 4, 8);
        assertClose(Ops.matmul(null, x, w, false), Unfused.matmul(null, x, w, false));
        assertClose(Ops.matmul(null, x, k, true), Unfused.matmul(null, x, k, true));
        check("unfused matmul", List.of(x, w), t -> Unfused.matmul(t, x, w, false));
        check("unfused batched matmul", List.of(x, k), t -> Unfused.matmul(t, x, k, true));
    }

    private static void assertClose(Tensor want, Tensor got) {
        assertArrayEquals(want.shape, got.shape);
        for (int i = 0; i < want.data.length; i++) {
            assertEquals(want.data[i], got.data[i], 1e-5 + 1e-4 * Math.abs(want.data[i]), "element " + i);
        }
    }
}
