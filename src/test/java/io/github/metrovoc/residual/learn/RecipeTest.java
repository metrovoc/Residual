package io.github.metrovoc.residual.learn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Config;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.tensor.Muon;
import io.github.metrovoc.residual.tensor.Numerics;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Schedules, packing and document masks, Muon's update and the second head, as a Core's recipe uses them. */
class RecipeTest {
    private static Recipe with(Recipe.Schedule schedule, int warmup, int length) {
        return new Recipe(schedule, warmup, length, Numerics.Format.FP32, Recipe.Moves.ADAMW, Recipe.Packing.WINDOWS);
    }

    @Test
    void thePlainRecipeIsTheOldFixedWarmup() {
        for (int step : new int[]{0, 1, 10, 19, 20, 500, 1000000}) {
            assertEquals(Math.min(1f, (step + 1f) / Trainer.WARMUP), Recipe.PLAIN.multiplier(step), step + "");
        }
    }

    @Test
    void schedulesRiseAndFall() {
        Recipe cosine = with(Recipe.Schedule.COSINE, 100, 1000);
        assertEquals(0.01f, cosine.multiplier(0), 1e-6);
        assertEquals(1f, cosine.multiplier(100), 1e-4);
        assertEquals(0.5f, cosine.multiplier(550), 1e-3);
        assertEquals(0f, cosine.multiplier(1000), 1e-6);
        assertEquals(0f, cosine.multiplier(5000), 1e-6, "past the end it stays down");
        Recipe wsd = with(Recipe.Schedule.WSD, 100, 1000);
        assertEquals(1f, wsd.multiplier(799), 1e-6, "stable until the last fifth");
        assertEquals(0.5f, wsd.multiplier(900), 1e-3);
        assertEquals(0f, wsd.multiplier(1000), 1e-6);
        // no warmup at all: the full rate from the first step, not a NaN
        assertEquals(1f, with(Recipe.Schedule.CONSTANT, 0, 0).multiplier(0));
    }

    @Test
    void aSpikeIsALeapFarAboveTheRecentAverage() {
        assertTrue(Trainer.spiked(4, 2), "half as much again and more");
        assertFalse(Trainer.spiked(2.5, 2), "a batch's luck");
        assertFalse(Trainer.spiked(1, 2), "falling is no spike");
        assertFalse(Trainer.spiked(0.09, 0), "near zero, one wrong answer in a batch isn't either");
        assertFalse(Trainer.spiked(9, Double.NaN), "the first step has nothing to leap from");
    }

    @Test
    void aMixtureSaysWhatItDrewPracticeFrom() {
        Mixture m = new Mixture(List.of(new Mixture.Source(Benchmark.READING, stretches(Vocab.WORLD))), Vocab.WORLD);
        m.batch(new Random(1), 2, true);
        assertEquals(null, m.lastDrawn, "a test is not practice");
        m.batch(new Random(1), 2, false);
        assertEquals(Benchmark.READING, m.lastDrawn);
    }

    private static TextTask stretches(Vocab vocab) {
        List<String> texts = new ArrayList<>();
        Random rng = new Random(4);
        for (int i = 0; i < 40; i++) {
            StringBuilder s = new StringBuilder();
            int n = 8 + rng.nextInt(120);
            for (int k = 0; k < n; k++) {
                s.append((char) ('a' + rng.nextInt(26)));
            }
            texts.add(s.toString());
        }
        return new TextTask(texts, 1.4, vocab);
    }

    @Test
    void windowsLeaveShortStretchesUnreadAndPackingReadsThemAll() {
        TextTask text = stretches(Vocab.WORLD);
        assertTrue(text.readShare() < 0.8f, "windows read " + text.readShare());
        text.packing = Recipe.Packing.PACKED;
        assertEquals(1f, text.readShare());
    }

    @Test
    void packedRowsHaveNoPaddingAndScoreNoSeam() {
        TextTask text = stretches(Vocab.WORLD);
        text.packing = Recipe.Packing.MASKED;
        Batch b = text.batch(new Random(1), 16, false);
        int t = b.input().time;
        assertEquals(16 * t, b.input().ids.length);
        int[] starts = b.input().starts;
        int seams = 0;
        for (int r = 0; r < 16; r++) {
            for (int i = 0; i < t; i++) {
                int at = r * t + i;
                assertTrue(starts[at] <= i, "a token's document starts before it");
                if (i > 0 && starts[at] != starts[at - 1]) {
                    seams++;
                    assertEquals(i, starts[at], "a new document starts here");
                    assertEquals(0f, b.weights()[at - 1], "the last token of a document isn't asked for the next one's first");
                }
                assertEquals(i < t - 1 && starts[at + 1] != starts[at] ? 0f : b.weights()[at], b.weights()[at]);
            }
        }
        assertTrue(seams >= 3, "text this short fills rows from several stretches: " + seams + " seams in 16 rows");
        // a whole row's worth of predictions, less the seams and perhaps the last
        double scored = 0;
        for (float w : b.weights()) {
            scored += w;
        }
        assertTrue(scored > 0.8 * 16 * t, "scored " + scored);
        // with the test, it's windows of whole records as before
        assertEquals(null, text.batch(new Random(1), 2, true).input().starts);
    }

    private static GraphSpec[] everyWayToLook() {
        GraphSpec hand = Blueprints.recall();
        return new GraphSpec[]{Blueprints.gpt(16, 1, 2), Blueprints.modern(16, 1, 2), hand, Blueprints.context(), Blueprints.reverse()};
    }

    /** Two documents in one row: what the later one predicts must not change when the earlier one does. */
    @Test
    void aDocumentMaskStopsEveryWayOfLookingAcross() throws Exception {
        int t = 20, seam = 12;
        Random rng = new Random(8);
        int[] ids = new int[t];
        int[] starts = new int[t];
        for (int i = 0; i < t; i++) {
            ids[i] = rng.nextInt(Vocab.WORLD.size());
            starts[i] = i < seam ? 0 : seam;
        }
        int[] changed = ids.clone();
        for (int i = 0; i < seam; i++) {
            changed[i] = (ids[i] + 7) % Vocab.WORLD.size();
        }
        for (GraphSpec spec : everyWayToLook()) {
            CompiledGraph g = CompiledGraph.compile(spec, Env.WORLD, null, 3);
            Tensor masked = g.forward(null, new Tokens(ids, 1, t, starts), null);
            Tensor maskedChanged = g.forward(null, new Tokens(changed, 1, t, starts), null);
            int v = masked.dim(-1);
            for (int i = seam * v; i < masked.size(); i++) {
                assertEquals(masked.data[i], maskedChanged.data[i], 1e-6f, "element " + i);
            }
            if (spec.nodes.stream().anyMatch(n -> n.op == Op.ATTENTION || n.op == Op.TRANSFORMER || n.op == Op.MODERN_BLOCK || n.op == Op.SOFTMAX || n.op == Op.SHIFT)) {
                Tensor open = g.forward(null, new Tokens(ids, 1, t), null);
                Tensor openChanged = g.forward(null, new Tokens(changed, 1, t), null);
                boolean leaks = false;
                for (int i = seam * v; i < open.size(); i++) {
                    leaks |= Math.abs(open.data[i] - openChanged.data[i]) > 1e-4f;
                }
                assertTrue(leaks, "unmasked, what comes before reaches across");
            }
        }
    }

    @Test
    void muonMovesAlongEveryDirectionAlike() {
        Random rng = new Random(6);
        for (int[] size : new int[][]{{8, 8}, {12, 4}, {4, 12}}) {
            int r = size[0], c = size[1];
            float[] m = new float[r * c];
            for (int i = 0; i < m.length; i++) {
                // one direction a hundred times louder than the rest
                m[i] = (float) rng.nextGaussian() + 100f * (i % (c + 1) == 0 && i < c ? 1f : 0f);
            }
            float[] x = Muon.orthogonalized(m, r, c);
            int k = Math.min(r, c);
            for (int a = 0; a < k; a++) {
                double norm = 0;
                for (int j = 0; j < Math.max(r, c); j++) {
                    double v = r <= c ? x[a * c + j] : x[j * c + a];
                    norm += v * v;
                }
                assertTrue(norm > 0.1 && norm < 2.5, "row " + a + " of " + r + "×" + c + " has squared length " + norm);
            }
        }
        assertEquals(0f, Muon.orthogonalized(new float[6], 2, 3)[0], "momentum of nothing stays nothing");
    }

    @Test
    void theOnlyHiddenMatricesAreWhatMuonMoves() throws Exception {
        CompiledGraph g = CompiledGraph.compile(Blueprints.gpt(16, 1, 2), Env.WORLD, null, 1);
        assertEquals(g.parameters().size(), g.hiddenMatrices().size() + g.otherParameters().size());
        // q, k, v, o and two of the feed-forward's; never the tables, the last layer or a vector
        assertEquals(6, g.hiddenMatrices().size());
        for (Tensor t : g.hiddenMatrices()) {
            assertEquals(2, t.rank());
            assertFalse(t.dim(0) == Vocab.WORLD.size() || t.dim(1) == Vocab.WORLD.size());
        }
    }

    @Test
    void theSecondHeadLearnsTheTokenAfterNext() throws Exception {
        GraphSpec g = new GraphSpec();
        int e = g.add("embed", Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", "16"), GraphSpec.TOKENS);
        g.output = g.add("heads", Op.TWO_TOKEN_HEAD, e);
        CompiledGraph model = CompiledGraph.compile(g, Env.WORLD, null, 1);
        // text that goes abcabc, so the token after next is always known
        List<String> texts = new ArrayList<>();
        texts.add("abc".repeat(200));
        TextTask text = new TextTask(texts, 1.4, Vocab.WORLD);
        assertTrue(Float.isFinite(Trainer.secondRight(model, text)));
        Trainer trainer = new Trainer(model, text, 1, 1e-2f, 16, 1f);
        for (int i = 0; i < 200; i++) {
            trainer.train();
        }
        assertTrue(Trainer.secondRight(model, text) > 0.99f, "the second head, " + Trainer.secondRight(model, text));
        assertTrue(Trainer.evaluate(model, text, 2).accuracy() > 0.99, "and the first");
    }
}
