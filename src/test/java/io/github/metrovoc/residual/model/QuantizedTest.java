package io.github.metrovoc.residual.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.learn.Blueprints;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuantizedTest {
    private static CompiledGraph model() throws Exception {
        return CompiledGraph.compile(Blueprints.gpt(16, 1, 2), Env.WORLD, null, 5);
    }

    private static double meanError(CompiledGraph a, CompiledGraph b) {
        double sum = 0;
        long n = 0;
        List<Tensor> x = a.parameters(), y = b.parameters();
        for (int t = 0; t < x.size(); t++) {
            for (int i = 0; i < x.get(t).data.length; i++) {
                sum += Math.abs(x.get(t).data[i] - y.get(t).data[i]);
                n++;
            }
        }
        return sum / n;
    }

    @Test
    void itsSizeIsItsPackedWeightsAndScales() throws Exception {
        CompiledGraph model = model();
        for (int bits : new int[] {8, 4}) {
            long expected = 0;
            for (Tensor t : model.parameters()) {
                expected += (t.size() + Quantized.GROUP - 1) / Quantized.GROUP * Short.BYTES + (t.size() * bits + 7) / 8;
            }
            assertEquals(expected, Quantized.of(model, bits).bytes());
        }
        // a two-block transformer 96 wide: the sizes the field notes give
        CompiledGraph wide = CompiledGraph.compile(Blueprints.gpt(96, 2, 4), Env.WORLD, null, 5);
        assertEquals(246_425, wide.parameterCount());
        assertEquals(261_827, Quantized.of(wide, 8).bytes());
        assertEquals(138_615, Quantized.of(wide, 4).bytes());
    }

    @Test
    void everyWeightComesBackWithinHalfItsGroupsScale() throws Exception {
        CompiledGraph model = model();
        for (int bits : new int[] {8, 4}) {
            int most = (1 << bits - 1) - 1;
            CompiledGraph back = Quantized.of(model, bits).restore();
            List<Tensor> before = model.parameters(), after = back.parameters();
            for (int t = 0; t < before.size(); t++) {
                float[] w = before.get(t).data, r = after.get(t).data;
                for (int at = 0; at < w.length; at += Quantized.GROUP) {
                    float largest = 0;
                    for (int i = at; i < Math.min(at + Quantized.GROUP, w.length); i++) {
                        largest = Math.max(largest, Math.abs(w[i]));
                    }
                    // a little over half: the scale itself is kept to 16 bits
                    float bound = 0.51f * largest / most;
                    for (int i = at; i < Math.min(at + Quantized.GROUP, w.length); i++) {
                        assertTrue(Math.abs(w[i] - r[i]) <= bound, () -> "weight off by more than half a scale at " + bits + " bits");
                    }
                }
            }
        }
    }

    @Test
    void fewerBitsLoseMoreAndTheOriginalKeepsItsWeights() throws Exception {
        CompiledGraph model = model();
        float[] first = model.parameters().getFirst().data.clone();
        CompiledGraph eight = Quantized.of(model, 8).restore(), four = Quantized.of(model, 4).restore();
        assertNotSame(model, eight);
        assertArrayEquals(first, model.parameters().getFirst().data);
        assertTrue(meanError(model, eight) < meanError(model, four) / 8);
    }

    @Test
    void zerosStayZeroAndAnEmptyGroupStaysEmpty() throws Exception {
        CompiledGraph model = model();
        for (Tensor t : model.parameters()) {
            java.util.Arrays.fill(t.data, 0f);
        }
        for (Tensor t : Quantized.of(model, 4).restore().parameters()) {
            for (float w : t.data) {
                assertEquals(0f, w);
            }
        }
    }

    @Test
    void aRestoredModelAnswersAsItsOriginalAlmostDoes() throws Exception {
        CompiledGraph model = model();
        Tokens tokens = new Tokens(new int[] {1, 2, 3, 4, 5, 6, 7, 8}, 1, 8);
        float[] want = model.forward(null, tokens, null).data, got = Quantized.of(model, 8).restore().forward(null, tokens, null).data;
        assertEquals(want.length, got.length);
        double worst = 0;
        for (int i = 0; i < want.length; i++) {
            worst = Math.max(worst, Math.abs(want[i] - got[i]));
        }
        assertTrue(worst < 0.1, () -> "8 bits moved an answer's score by " + "too much");
    }
}
