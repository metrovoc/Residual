package io.github.metrovoc.residual.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Blueprints;
import io.github.metrovoc.residual.learn.Contamination;
import io.github.metrovoc.residual.learn.Mixture;
import io.github.metrovoc.residual.learn.Recipe;
import io.github.metrovoc.residual.learn.Seeds;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.Vocab;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** How models are compared: the spread of a design's runs, the boards' order, and what leaks show. */
class EvaluationTest {
    @Test
    void aSpreadIsTheRunsMeanAndDeviation() {
        Seeds.Spread s = Seeds.spread(new float[] {0.90f, 0.92f, 0.94f, 0.96f, 0.98f});
        assertEquals(0.94f, s.mean(), 1e-5f);
        assertEquals(0.0316f, s.sd(), 1e-3f);
        assertTrue(s.low() < s.mean() && s.mean() < s.high());
        assertTrue(s.low() >= 0.90f && s.high() <= 0.98f);
    }

    @Test
    void aGapSmallerThanTheSpreadIsNoise() {
        float[] a = {0.90f, 0.93f, 0.96f, 0.91f, 0.95f};
        assertTrue(Seeds.withinNoise(a, new float[] {0.91f, 0.94f, 0.95f, 0.92f, 0.96f}));
        assertFalse(Seeds.withinNoise(a, new float[] {0.70f, 0.72f, 0.69f, 0.71f, 0.73f}));
    }

    @Test
    void runsFromSeedsDifferAndLeaveTheDesignAlone() throws Exception {
        CompiledGraph design = CompiledGraph.compile(Blueprints.bigram(), Env.WORLD, null, 1);
        float before = design.parameters().getFirst().data[0];
        Mixture data = new Mixture(List.of(new Mixture.Source(Benchmark.FORECASTING, Benchmark.FORECASTING.practice())), Vocab.WORLD);
        Map<Benchmark, float[]> runs = Seeds.run(design, data, 3e-3f, 32, 1f, 0.01f, Recipe.PLAIN, 60, 3, 7, () -> false, f -> { });
        float[] scores = runs.get(Benchmark.FORECASTING);
        assertEquals(3, scores.length);
        assertNotEquals(scores[0], scores[1]);
        assertEquals(before, design.parameters().getFirst().data[0], "the design standing there keeps its weights");
    }

    @Test
    void aBoardPutsTheBestFirstAndWhatHasSeenTheTestLast() {
        Records.Entry small = new Records.Entry("a", 0.95f, 0.95f, false, 100, "linear 1", 1);
        Records.Entry big = new Records.Entry("b", 0.95f, 0.95f, false, 900, "linear 2", 2);
        Records.Entry best = new Records.Entry("c", 0.99f, 0.98f, false, 900, "linear 3", 3);
        Records.Entry seen = new Records.Entry("d", 1.00f, 0.60f, true, 50, "linear 4", 4);
        List<Records.Entry> board = new ArrayList<>(List.of(seen, big, small, best));
        board.sort(Records.order(Benchmark.INDUCTION));
        assertEquals(List.of(best, small, big, seen), board);
        // lower is better where the score is a loss
        board.sort(Records.order(Benchmark.READING));
        assertEquals(small, board.getFirst());
    }

    @Test
    void aDesignIsKnownByItsMachines() throws Exception {
        assertEquals(Records.hash(Blueprints.gpt(32, 2, 2)), Records.hash(Blueprints.gpt(32, 2, 2)));
        assertNotEquals(Records.hash(Blueprints.gpt(32, 2, 2)), Records.hash(Blueprints.gpt(64, 2, 2)));
        assertEquals("activation 1,concat 2,embedding 1,linear 2,shift 2", Records.summary(Blueprints.context()));
    }

    @Test
    void theTestsOwnRunsTurnUpInPracticeOnlyWhenLetIn() {
        Task test = Benchmark.RECALL.test(Vocab.WORLD);
        float clean = Contamination.overlap(new Contamination.Leaked(Benchmark.RECALL.practice(), test, 0f), test, new Random(1));
        float half = Contamination.overlap(new Contamination.Leaked(Benchmark.RECALL.practice(), test, 0.5f), test, new Random(1));
        System.out.printf("the test's runs in practice: clean %.3f, half let in %.3f%n", clean, half);
        assertTrue(clean < 0.01f);
        assertTrue(half > 0.5f);
    }
}
