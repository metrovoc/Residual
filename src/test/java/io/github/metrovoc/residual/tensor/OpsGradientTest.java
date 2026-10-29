package io.github.metrovoc.residual.tensor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Compares every op's analytic gradient with central finite differences. */
class OpsGradientTest {
    private final Random rng = new Random(42);

    private Tensor rand(int... shape) {
        Tensor t = Tensor.zeros(true, shape);
        for (int i = 0; i < t.data.length; i++) {
            t.data[i] = (float) rng.nextGaussian();
        }
        return t;
    }

    /** Checks d(sum(f(inputs) * w))/d(input) for all inputs. */
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
                double tol = 2e-2 * Math.max(1.0, Math.abs(numeric));
                assertEquals(numeric, analytic[i], tol, name + " input " + ti + " element " + i);
            }
        }
    }

    @Test
    void matmulShared() {
        Tensor a = rand(2, 3, 4);
        Tensor b = rand(4, 5);
        check("matmul", List.of(a, b), tape -> Ops.matmul(tape, a, b, false));
        Tensor bt = rand(5, 4);
        check("matmulT", List.of(a, bt), tape -> Ops.matmul(tape, a, bt, true));
    }

    @Test
    void matmulBatched() {
        Tensor a = rand(2, 2, 3, 4);
        Tensor b = rand(2, 2, 4, 3);
        check("bmm", List.of(a, b), tape -> Ops.matmul(tape, a, b, false));
        Tensor c = rand(2, 2, 3, 4);
        check("bmmT", List.of(a, c), tape -> Ops.matmul(tape, a, c, true));
    }

    @Test
    void elementwise() {
        Tensor a = rand(2, 3, 4);
        Tensor b = rand(3, 4);
        check("add", List.of(a, b), tape -> Ops.add(tape, a, b));
        check("mul", List.of(a, b), tape -> Ops.mul(tape, a, b));
        check("scale", List.of(a), tape -> Ops.scale(tape, a, 0.7f));
        for (Ops.Activation f : Ops.Activation.values()) {
            check(f.id, List.of(a), tape -> Ops.activate(tape, a, f));
        }
    }

    @Test
    void broadcastKeepsLeadingSingletonDimensionsInEitherInputOrder() {
        Tensor positions = rand(3, 4);
        Tensor sequence = rand(1, 3, 4);
        Tensor sum = Ops.add(null, positions, sequence);
        Tensor product = Ops.mul(null, positions, sequence);
        assertArrayEquals(sequence.shape, sum.shape);
        assertArrayEquals(sequence.shape, product.shape);
        assertArrayEquals(Ops.add(null, sequence, positions).data, sum.data);
        assertArrayEquals(Ops.mul(null, sequence, positions).data, product.data);
        check("add single batch", List.of(positions, sequence), tape -> Ops.add(tape, positions, sequence));
        check("mul single batch", List.of(positions, sequence), tape -> Ops.mul(tape, positions, sequence));
    }

    @Test
    void softmaxAndMask() {
        Tensor s = rand(2, 4, 4);
        check("softmax", List.of(s), tape -> Ops.softmax(tape, s));
        check("masked softmax", List.of(s), tape -> Ops.softmax(tape, Ops.causalMask(tape, s)));
    }

    @Test
    void norms() {
        Tensor x = rand(3, 5);
        Tensor g = rand(5);
        Tensor b = rand(5);
        check("layernorm", List.of(x, g, b), tape -> Ops.layerNorm(tape, x, g, b, 1e-5f));
        check("rmsnorm", List.of(x, g), tape -> Ops.rmsNorm(tape, x, g, 1e-5f));
    }

    @Test
    void reshaping() {
        Tensor x = rand(2, 3, 4);
        check("split", List.of(x), tape -> Ops.splitHeads(tape, x, 2));
        Tensor h = rand(2, 2, 3, 2);
        check("merge", List.of(h), tape -> Ops.mergeHeads(tape, h));
        check("shift", List.of(x), tape -> Ops.shift(tape, x, 1));
        Tensor y = rand(2, 3, 2);
        check("concat", List.of(x, y), tape -> Ops.concat(tape, x, y));
    }

    @Test
    void tokens() {
        Tensor table = rand(5, 3);
        Tokens ids = new Tokens(new int[]{0, 4, 4, 2}, 2, 2);
        check("embedding", List.of(table), tape -> Ops.embedding(tape, ids, table));
        Tensor pos = rand(4, 3);
        check("positions", List.of(pos), tape -> Ops.positions(tape, pos, 2));
        Tensor logits = rand(2, 2, 5);
        int[] targets = {1, 4, 0, 2};
        float[] weights = {1, 0, 1, 1};
        check("cross entropy", List.of(logits), tape -> Ops.crossEntropy(tape, logits, targets, weights));
    }

    @Test
    void crossEntropyOfUniformIsLogV() {
        Tensor logits = Tensor.zeros(false, 1, 3, 7);
        float loss = Ops.crossEntropy(null, logits, new int[]{0, 3, 6}, null).item();
        assertEquals(Math.log(7), loss, 1e-5);
    }

    @Test
    void causalMaskBlocksFuture() {
        Tensor s = rand(1, 3, 3);
        Tensor p = Ops.softmax(null, Ops.causalMask(null, s));
        assertEquals(1f, p.data[0], 1e-6);
        assertEquals(0f, p.data[1]);
        assertEquals(0f, p.data[2]);
        assertTrue(p.data[5] == 0f && Math.abs(p.data[3] + p.data[4] - 1f) < 1e-6);
    }
}
