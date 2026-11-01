package io.github.metrovoc.residual.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.learn.Blueprints;
import io.github.metrovoc.residual.learn.Sampler;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

class ServingTest {
    private static final long MEMORY = 1 << 22;

    private static CompiledGraph gpt(int width, long seed) throws Exception {
        return CompiledGraph.compile(Blueprints.gpt(width, 2, 4), Env.WORLD, null, seed);
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
    void aCacheGivesTheAnswerAPlainPassGives() throws Exception {
        CompiledGraph model = gpt(32, 1);
        Serving plain = new Serving(model, Serving.Stack.PLAIN);
        Serving cached = new Serving(model, new Serving.Stack(32, MEMORY, 1, null));
        String prompt = "> bring me some bread\n= ";
        Sampler.Answer a = plain.run(m -> Sampler.answer(m, prompt, 40, '\n')), b = cached.run(m -> Sampler.answer(m, prompt, 40, '\n'));
        assertEquals(a.text(), b.text());
        assertArrayEquals(a.probs(), b.probs(), 1e-3f);
        // a plain answer reads everything again for each token; a cached one reads each token once
        int read = prompt.length(), written = a.probs().length;
        assertEquals((long) written * read + (long) written * (written - 1) / 2, plain.rows());
        assertEquals(read + written - 1, cached.rows());
    }

    @Test
    void aQuestionAskedAgainCostsNothing() throws Exception {
        CompiledGraph model = gpt(32, 2);
        Serving cached = new Serving(model, new Serving.Stack(32, MEMORY, 1, null));
        int[] ids = ids(20, 2);
        cached.run(m -> m.forward(null, new Tokens(ids, 1, 20), null));
        long rows = cached.rows();
        Tensor again = cached.run(m -> m.forward(null, new Tokens(ids, 1, 20), null));
        assertEquals(rows, cached.rows());
        assertArrayEquals(model.forward(null, new Tokens(ids, 1, 20), null).data, again.data, 1e-3f);
    }

    @Test
    void whatDoesNotFitIsForgotten() throws Exception {
        CompiledGraph model = gpt(32, 3);
        Serving small = new Serving(model, new Serving.Stack(32, 60_000, 1, null));
        for (int i = 0; i < 20; i++) {
            int[] ids = ids(32, i);
            small.run(m -> m.forward(null, new Tokens(ids, 1, 32), null));
            assertTrue(small.cachedBytes() <= 60_000, "holds " + small.cachedBytes());
        }
        assertTrue(small.cachedBytes() > 0);
    }

    @Test
    void sequencesAskedAtOnceGoThroughInOnePass() throws Exception {
        CompiledGraph model = gpt(32, 4);
        Serving batched = new Serving(model, new Serving.Stack(32, MEMORY, 8, null));
        int n = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        List<float[]> got = new ArrayList<>();
        List<int[]> asked = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int[] ids = ids(16, 100 + i);
            asked.add(ids);
            got.add(null);
            int at = i;
            batched.coming();
            threads.add(Thread.ofPlatform().start(() -> batched.run(m -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
                got.set(at, m.forward(null, new Tokens(ids, 1, 16), null).data);
                return null;
            }, null)));
        }
        Thread.sleep(50);
        start.countDown();
        for (Thread t : threads) {
            t.join();
        }
        for (int i = 0; i < n; i++) {
            assertArrayEquals(model.forward(null, new Tokens(asked.get(i), 1, 16), null).data, got.get(i), 1e-3f);
        }
        assertEquals(16L * n, batched.rows());
        assertTrue(batched.passes() < n, "took " + batched.passes() + " passes");
    }

    @Test
    void aGreedyAnswerWithADraftIsTheAnswerWithout() throws Exception {
        CompiledGraph target = gpt(32, 5), draft = gpt(16, 6);
        Serving plain = new Serving(target, Serving.Stack.PLAIN);
        Serving drafted = new Serving(target, new Serving.Stack(32, MEMORY, 1, draft));
        String prompt = "> bring me some bread\n= ";
        Sampler.Answer a = plain.run(m -> Sampler.answer(m, prompt, 60, '\n')), b = drafted.run(m -> Sampler.answer(m, prompt, 60, '\n'));
        assertEquals(a.text(), b.text());
        assertArrayEquals(a.probs(), b.probs(), 1e-3f);
        assertTrue(drafted.proposed() > 0);
    }

    @Test
    void writingWithADraftKeepsTheServedModelsProbabilities() throws Exception {
        // the second token's distribution, by the chain rule on the served model, against what comes out of drafts
        CompiledGraph target = CompiledGraph.compile(Blueprints.gpt(16, 1, 2), Env.WORLD, null, 7),
                draft = CompiledGraph.compile(Blueprints.gpt(8, 1, 1), Env.WORLD, null, 8);
        Serving drafted = new Serving(target, new Serving.Stack(32, MEMORY, 1, draft));
        int v = Env.WORLD.vocab();
        int[] prompt = ids(6, 9);
        float[] first = Sampler.nextProbs(target, prompt, Vocab.CONTEXT, 1f);
        double[] exact = new double[v];
        for (int a = 0; a < v; a++) {
            int[] two = Arrays.copyOf(prompt, 7);
            two[6] = a;
            float[] second = Sampler.nextProbs(target, two, Vocab.CONTEXT, 1f);
            for (int b = 0; b < v; b++) {
                exact[b] += first[a] * second[b];
            }
        }
        int samples = 40_000;
        int[] seen = new int[v];
        Random rng = new Random(10);
        CompiledGraph view = drafted.view();
        for (int s = 0; s < samples; s++) {
            List<Sampler.Step> steps = Sampler.generate(view, prompt, 2, 1f, rng);
            seen[steps.get(1).token()]++;
        }
        double chi = 0;
        int bins = 0;
        for (int b = 0; b < v; b++) {
            double expected = exact[b] * samples;
            if (expected >= 5) {
                chi += (seen[b] - expected) * (seen[b] - expected) / expected;
                bins++;
            }
        }
        int df = bins - 1;
        assertTrue(chi < df + 4 * Math.sqrt(2.0 * df), "chi-square " + chi + " on " + df + " degrees");
        assertTrue(drafted.accepted() > 0 && drafted.accepted() < drafted.proposed());
    }

    @Test
    void aFullWindowMovesOnTheSameWayWithACache() throws Exception {
        CompiledGraph model = gpt(16, 11);
        Serving plain = new Serving(model, Serving.Stack.PLAIN);
        Serving cached = new Serving(model, new Serving.Stack(32, MEMORY, 1, null));
        int[] prompt = ids(60, 12);
        List<Sampler.Step> a = plain.run(m -> Sampler.generate(m, prompt, 40, 0f, new Random(1)));
        List<Sampler.Step> b = cached.run(m -> Sampler.generate(m, prompt, 40, 0f, new Random(1)));
        assertEquals(a.stream().map(Sampler.Step::token).toList(), b.stream().map(Sampler.Step::token).toList());
        assertTrue(cached.rows() * 3 < plain.rows(), cached.rows() + " rows against " + plain.rows());
    }
}
