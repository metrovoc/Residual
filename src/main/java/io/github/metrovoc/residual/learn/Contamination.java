package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * When the test has got into the practice. A test is a fixed set of items, so a model that has seen them scores higher on them than
 * it can do on anything else. A fresh look-alike, the same test with other items, gives that away, and so does finding the test's own text
 * in what a model practised on.
 */
public final class Contamination {
    /** How many batches each side of a check takes: the same as saving a model tests it on. */
    public static final int BATCHES = 8;
    /** A gap must be this many times what chance leaves between two tests, and at least this much: a share right, or nats a character. */
    public static final double SIGMAS = 4, LEAST_ACCURACY = 0.05, LEAST_LOSS = 0.1;
    /** Where the look-alike's items come from; the test's own come from {@link Trainer#TEST_SEED}. */
    private static final long FRESH = 90210;
    /** How long a run of tokens must match to say the test's text was seen. */
    public static final int GRAM = 13;

    /**
     * @param scored       what the test says, as a share right or a loss
     * @param fresh        what a look-alike says
     * @param contaminated the test flatters the model beyond what chance explains
     */
    public record Check(float scored, float fresh, boolean contaminated) {
    }

    private Contamination() {
    }

    /**
     * Scores a model on the test's own items and on as many others. A test that is played afresh has no items of its own to have seen, so
     * it has nothing to say.
     */
    public static Check check(CompiledGraph model, Task test, Benchmark benchmark) {
        double[] played = test.measure(model);
        if (played != null) {
            float s = (float) (benchmark.metric == Task.Metric.LOSS ? played[0] : played[1]);
            return new Check(s, s, false);
        }
        double[] own = scores(model, test, benchmark, Trainer.TEST_SEED);
        double[] other = scores(model, test, benchmark, FRESH);
        double a = mean(own), b = mean(other);
        double noise = Math.sqrt(variance(own, a) / BATCHES + variance(other, b) / BATCHES);
        boolean loss = benchmark.metric == Task.Metric.LOSS;
        double gap = loss ? b - a : a - b;
        return new Check((float) a, (float) b, gap > Math.max(SIGMAS * noise, loss ? LEAST_LOSS : LEAST_ACCURACY));
    }

    private static double[] scores(CompiledGraph model, Task test, Benchmark benchmark, long seed) {
        Random rng = new Random(seed);
        double[] out = new double[BATCHES];
        for (int i = 0; i < BATCHES; i++) {
            Batch b = test.batch(rng, 32, true);
            Tensor logits = model.forward(null, b.input(), null);
            if (benchmark.metric == Task.Metric.LOSS) {
                out[i] = io.github.metrovoc.residual.tensor.Ops.crossEntropy(null, logits, b.targets(), b.weights()).item() * b.perChar();
            } else {
                int v = logits.dim(-1), scored = 0, right = 0;
                for (int r = 0; r < b.targets().length; r++) {
                    if (b.weights() != null && b.weights()[r] == 0f) {
                        continue;
                    }
                    scored++;
                    right += Trainer.argmax(logits.data, r * v, v) == b.targets()[r] ? 1 : 0;
                }
                out[i] = scored == 0 ? 0 : (double) right / scored;
            }
        }
        return out;
    }

    private static double mean(double[] v) {
        double sum = 0;
        for (double x : v) {
            sum += x;
        }
        return sum / v.length;
    }

    private static double variance(double[] v, double mean) {
        double sum = 0;
        for (double x : v) {
            sum += (x - mean) * (x - mean);
        }
        return sum / Math.max(1, v.length - 1);
    }

    /** The test's own items, in the order a test draws them. */
    public static List<Batch> items(Task test) {
        Random rng = new Random(Trainer.TEST_SEED);
        List<Batch> out = new ArrayList<>();
        for (int i = 0; i < BATCHES; i++) {
            out.add(test.batch(rng, 32, true));
        }
        return out;
    }

    /** Practice with the test's own items let into it. */
    public static final class Leaked extends Task {
        private final Task practice, test;
        private final float share;
        private final List<Batch> items;

        public Leaked(Task practice, Task test, float share) {
            super(practice.vocab, practice.context, practice.metric, practice.target);
            this.practice = practice;
            this.test = test;
            this.share = share;
            this.items = items(test);
        }

        @Override
        public Batch batch(Random rng, int size, boolean isTest) {
            if (isTest || rng.nextFloat() >= share) {
                return isTest ? test.batch(rng, size, true) : practice.batch(rng, size, false);
            }
            // the items of one of the test's batches, as many as asked for
            Batch from = items.get(rng.nextInt(items.size()));
            int t = from.input().time, have = from.input().batch;
            int[] ids = new int[size * t], targets = new int[size * t];
            float[] weights = from.weights() == null ? null : new float[size * t];
            for (int b = 0; b < size; b++) {
                int row = rng.nextInt(have);
                System.arraycopy(from.input().ids, row * t, ids, b * t, t);
                System.arraycopy(from.targets(), row * t, targets, b * t, t);
                if (weights != null) {
                    System.arraycopy(from.weights(), row * t, weights, b * t, t);
                }
            }
            return new Batch(new Tokens(ids, size, t), targets, weights, from.perChar());
        }
    }

    /** The share of the test's runs of {@link #GRAM} tokens that turn up in a sample of what is practised on. */
    public static float overlap(Task practice, Task test, Random rng) {
        Set<Long> grams = new HashSet<>();
        for (Batch b : items(test)) {
            addGrams(b.input(), grams);
        }
        if (grams.isEmpty()) {
            return 0;
        }
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < 64; i++) {
            addGrams(practice.batch(rng, 32, false).input(), seen);
        }
        seen.retainAll(grams);
        return (float) seen.size() / grams.size();
    }

    private static void addGrams(Tokens tokens, Set<Long> out) {
        for (int b = 0; b < tokens.batch; b++) {
            for (int i = 0; i + GRAM <= tokens.time; i++) {
                long hash = 1125899906842597L;
                for (int k = 0; k < GRAM; k++) {
                    hash = 31 * hash + tokens.at(b, i + k);
                }
                out.add(hash);
            }
        }
    }
}
