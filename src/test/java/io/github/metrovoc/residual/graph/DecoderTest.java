package io.github.metrovoc.residual.graph;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.learn.Blueprints;
import io.github.metrovoc.residual.model.Quantized;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DecoderTest {
    private static CompiledGraph compile(GraphSpec spec) throws Exception {
        return CompiledGraph.compile(spec, Env.WORLD, null, 7);
    }

    private static int[] ids(int n, long seed) {
        Random rng = new Random(seed);
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) {
            ids[i] = rng.nextInt(Env.WORLD.vocab());
        }
        return ids;
    }

    @Test
    void rowsAtATimeGiveWhatAWholePassGivesOnEveryBlueprint() throws Exception {
        for (GraphSpec spec : List.of(Blueprints.bigram(), Blueprints.context(), Blueprints.recall(), Blueprints.reverse(),
                Blueprints.induction(), Blueprints.gpt(32, 2, 4), Blueprints.gpt(16, 1, 1))) {
            CompiledGraph model = compile(spec);
            assertTrue(new Decoder(model, Decoder.floats(model)).agrees(ids(40, 1)));
        }
    }

    @Test
    void aSequenceCarriesOnFromWhereItWasForked() throws Exception {
        CompiledGraph model = compile(Blueprints.gpt(32, 2, 4));
        Decoder decoder = new Decoder(model, Decoder.floats(model));
        int[] ids = ids(30, 2);
        Decoder.Sequence first = decoder.sequence();
        decoder.pass(List.of(first), java.util.Arrays.copyOf(ids, 20), 20);
        assertEquals(12, first.shared(new int[]{ids[0], ids[1], ids[2], ids[3], ids[4], ids[5], ids[6], ids[7], ids[8], ids[9], ids[10], ids[11],
                (ids[12] + 1) % Env.WORLD.vocab()}, 0, 13));
        Decoder.Sequence forked = first.fork(12);
        int[] rest = java.util.Arrays.copyOfRange(ids, 12, 30);
        decoder.pass(List.of(forked), rest, rest.length);
        float[] got = new float[30 * Env.WORLD.vocab()];
        forked.logits(0, 30, got, 0);
        Tensor full = model.forward(null, new Tokens(ids, 1, 30), null);
        assertArrayEquals(full.data, got, 1e-3f);
    }

    @Test
    void sequencesOfDifferentLengthsShareAPass() throws Exception {
        CompiledGraph model = compile(Blueprints.gpt(32, 2, 4));
        Decoder decoder = new Decoder(model, Decoder.floats(model));
        int[] a = ids(9, 3), b = ids(14, 4);
        Decoder.Sequence x = decoder.sequence(), y = decoder.sequence();
        decoder.pass(List.of(x), java.util.Arrays.copyOf(a, 8), 8);
        decoder.pass(List.of(y), java.util.Arrays.copyOf(b, 13), 13);
        decoder.pass(List.of(x, y), new int[]{a[8], b[13]}, 1);
        float[] got = new float[Env.WORLD.vocab()];
        y.logits(13, 14, got, 0);
        Tensor full = model.forward(null, new Tokens(b, 1, 14), null);
        float[] last = java.util.Arrays.copyOfRange(full.data, 13 * Env.WORLD.vocab(), 14 * Env.WORLD.vocab());
        assertArrayEquals(last, got, 1e-3f);
    }

    @Test
    void aSequenceHoldsWhatTheArithmeticSays() throws Exception {
        CompiledGraph model = compile(Blueprints.gpt(32, 2, 4));
        Decoder decoder = new Decoder(model, Decoder.floats(model));
        // keys and values of 32 floats in each of two attentions
        assertEquals(2 * 2 * 32 * 4, decoder.keyValueBytes());
        Decoder.Sequence s = decoder.sequence();
        decoder.pass(List.of(s), ids(16, 5), 16);
        assertEquals(16 * decoder.tokenBytes(), s.bytes());
    }

    @Test
    void weightsUnpackedOnTheWayGiveWhatTheRestoredModelGives() throws Exception {
        CompiledGraph model = compile(Blueprints.gpt(32, 2, 4));
        for (int bits : new int[]{8, 4}) {
            Quantized packed = Quantized.of(model, bits);
            CompiledGraph restored = packed.restore();
            Decoder decoder = new Decoder(model, packed.unpacked());
            int[] ids = ids(20, bits);
            Decoder.Sequence s = decoder.sequence();
            decoder.pass(List.of(s), ids, 20);
            float[] got = new float[20 * Env.WORLD.vocab()];
            s.logits(0, 20, got, 0);
            assertArrayEquals(restored.forward(null, new Tokens(ids, 1, 20), null).data, got, 1e-3f);
        }
    }

    @Test
    void aModelThatReadsAheadDoesNotAgree() throws Exception {
        // attention with no causal mask: every row reads the whole sequence
        GraphSpec g = new GraphSpec();
        int e = g.add("embed", Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", "32"), GraphSpec.TOKENS);
        int s = g.add("scores", Op.MATMUL, Config.of(Op.MATMUL, "transpose", "true"), e, e);
        int soft = g.add("softer", Op.SCALE, Config.of(Op.SCALE, "factor", "0.125"), s);
        int p = g.add("weights", Op.SOFTMAX, soft);
        int o = g.add("mixed", Op.MATMUL, p, e);
        g.output = g.add("logits", Op.LINEAR, Config.of(Op.LINEAR, "out", "vocab"), o);
        CompiledGraph model = compile(g);
        assertFalse(new Decoder(model, Decoder.floats(model)).agrees(ids(20, 6)));
    }
}
