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
    void rotaryTurnsPairsAndKeepsLengths() {
        Tensor x = rand(2, 5, 4);
        check("rotary", List.of(x), tape -> Ops.rotary(tape, x));
        check("unfused rotary", List.of(x), tape -> Unfused.rotary(tape, x));
        Tensor y = Ops.rotary(null, x);
        // the first row doesn't turn
        for (int c = 0; c < 4; c++) {
            assertEquals(x.data[c], y.data[c], 1e-6);
        }
        // a turn keeps each pair's length
        for (int i = 0; i < x.size(); i += 2) {
            assertEquals(Math.hypot(x.data[i], x.data[i + 1]), Math.hypot(y.data[i], y.data[i + 1]), 1e-4);
        }
    }

    /** What the rotation is for: a query and a key that move along together keep their dot product. */
    @Test
    void rotaryScoresDependOnlyOnTheOffset() {
        float[] q = {0.3f, -1.2f, 0.7f, 0.5f, -0.2f, 1.1f};
        float[] k = {1.5f, 0.4f, -0.6f, 0.9f, 0.3f, -1.4f};
        int t = 32;
        float[] qs = new float[t * 6];
        float[] ks = new float[t * 6];
        for (int i = 0; i < t; i++) {
            System.arraycopy(q, 0, qs, i * 6, 6);
            System.arraycopy(k, 0, ks, i * 6, 6);
        }
        Tensor rq = Ops.rotary(null, Tensor.of(new int[]{t, 6}, qs));
        Tensor rk = Ops.rotary(null, Tensor.of(new int[]{t, 6}, ks));
        Tensor scores = Ops.matmul(null, rq, rk, true);
        for (int offset = 0; offset < 8; offset++) {
            float first = scores.data[offset * t];
            for (int i = 1; i + offset < t; i++) {
                assertEquals(first, scores.data[(i + offset) * t + i], 1e-4, "offset " + offset + " at row " + i);
            }
        }
    }

    @Test
    void theFastExponentialIsCloseEverywhere() {
        for (float x = -87f; x < 88f; x += 0.0137f) {
            double want = Math.exp(x);
            assertEquals(want, Ops.exp(x), 3e-7 * want, "e^" + x);
        }
        assertEquals(0f, Ops.exp(-1000f));
        assertEquals(1f, Ops.exp(0f));
        assertEquals(Float.POSITIVE_INFINITY, Ops.exp(1000f));
        assertTrue(Float.isNaN(Ops.exp(Float.NaN)));
    }

    @Test
    void flashAttentionEqualsTheScoresItNeverHolds() {
        for (int t : new int[]{3, 16, 17, 40}) {
            Tensor q = rand(2, 2, t, 4);
            Tensor k = rand(2, 2, t, 4);
            Tensor v = rand(2, 2, t, 4);
            Tape tape = new Tape();
            Tensor flash = Ops.flashAttention(tape, q, k, v, 0.5f);
            Tape plainTape = new Tape();
            Tensor plain = Ops.matmul(plainTape, Ops.softmax(plainTape, Ops.causalMask(plainTape,
                    Ops.scale(plainTape, Ops.matmul(plainTape, q, k, true), 0.5f))), v, false);
            for (int i = 0; i < plain.size(); i++) {
                assertEquals(plain.data[i], flash.data[i], 1e-4, "T " + t + " output " + i);
            }
            Tensor seed = rand(plain.shape);
            Tensor unused = Tensor.of(seed.shape.clone(), seed.data);
            tape.backward(Ops.sum(tape, Ops.mul(tape, flash, unused)));
            float[][] grads = {q.grad().clone(), k.grad().clone(), v.grad().clone()};
            q.zeroGrad();
            k.zeroGrad();
            v.zeroGrad();
            plainTape.backward(Ops.sum(plainTape, Ops.mul(plainTape, plain, unused)));
            float[][] want = {q.grad(), k.grad(), v.grad()};
            for (int n = 0; n < 3; n++) {
                for (int i = 0; i < want[n].length; i++) {
                    assertEquals(want[n][i], grads[n][i], 1e-3, "T " + t + " gradient " + n + " element " + i);
                }
            }
        }
    }

    @Test
    void flashAttentionKeepsItsAnswerWhenALaterTileIsFarLarger() {
        // scores a thousand apart along each row: exp without the running maximum overflows
        int t = 34;
        Tensor q = Tensor.zeros(false, 1, t, 2);
        Tensor k = Tensor.zeros(false, 1, t, 2);
        Tensor v = rand(1, t, 2);
        for (int i = 0; i < t; i++) {
            q.data[2 * i] = 1f;
            k.data[2 * i] = i < 20 ? -1000f : 1000f + i;
        }
        Tensor flash = Ops.flashAttention(null, q, k, v, 1f);
        Tensor plain = Ops.matmul(null, Ops.softmax(null, Ops.causalMask(null, Ops.matmul(null, q, k, true))), v, false);
        for (int i = 0; i < plain.size(); i++) {
            assertEquals(plain.data[i], flash.data[i], 1e-4, "element " + i);
        }
    }

    /** Three rows of six, cut into documents differently; the second-last token's document starts at 4 in the first row. */
    private static final int[] STARTS = {0, 0, 0, 0, 4, 4, 0, 1, 1, 1, 1, 1};

    @Test
    void documentsStopAttentionAndPositionsAndDelayLines() {
        Tensor s = rand(2, 6, 6);
        check("masked scores", List.of(s), tape -> Ops.softmax(tape, Ops.causalMask(tape, s, STARTS, 2)));
        Tensor masked = Ops.causalMask(null, s, STARTS, 2);
        assertEquals(Float.NEGATIVE_INFINITY, masked.data[5 * 6 + 3], "row 5 of the first row can't see its first document");
        assertEquals(s.data[5 * 6 + 4], masked.data[5 * 6 + 4]);
        Tensor table = rand(6, 3);
        check("positions", List.of(table), tape -> Ops.positions(tape, table, 6, STARTS, 2));
        // a document's first token takes the table's first row, wherever in the row it stands
        Tensor placed = Ops.positions(null, table, 6, STARTS, 2);
        for (int c = 0; c < 3; c++) {
            assertEquals(table.data[c], placed.data[4 * 3 + c], "first token of the second document");
            assertEquals(table.data[3 + c], placed.data[5 * 3 + c], "second token of the second document");
        }
        Tensor x = rand(2, 6, 3);
        check("shift", List.of(x), tape -> Ops.shift(tape, x, 1, STARTS));
        Tensor shifted = Ops.shift(null, x, 1, STARTS);
        assertEquals(0f, shifted.data[4 * 3], "a document's first token has nothing before it");
        assertEquals(x.data[4 * 3], shifted.data[5 * 3]);
    }

    @Test
    void flashAttentionStopsAtDocumentsToo() {
        Tensor q = rand(2, 2, 6, 4);
        Tensor k = rand(2, 2, 6, 4);
        Tensor v = rand(2, 2, 6, 4);
        Tensor flash = Ops.flashAttention(null, q, k, v, 0.5f, STARTS, 2);
        Tensor plain = Ops.matmul(null, Ops.softmax(null, Ops.causalMask(null, Ops.scale(null, Ops.matmul(null, q, k, true), 0.5f), STARTS, 2)), v, false);
        for (int i = 0; i < plain.size(); i++) {
            assertEquals(plain.data[i], flash.data[i], 1e-4, "element " + i);
        }
        check("flash with documents", List.of(q, k, v), tape -> Ops.flashAttention(tape, q, k, v, 0.5f, STARTS, 2));
    }

    @Test
    void zLossAndTemperature() {
        Tensor y = rand(2, 3, 5);
        check("z loss", List.of(y), tape -> Ops.zLoss(tape, y));
        Tensor x = rand(2, 3, 4);
        Tensor t = rand(1);
        check("scaled", List.of(x, t), tape -> Ops.scaled(tape, x, t));
        // a row of zeros over five tokens sums to five
        Tensor flat = Tensor.zeros(false, 1, 5);
        assertEquals(Math.log(5) * Math.log(5), Ops.zLoss(null, flat).item(), 1e-5);
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
