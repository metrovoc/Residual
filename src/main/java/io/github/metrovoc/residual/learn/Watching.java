package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Watch's practice and test. A model practises only on things going well, the same lines as Demand. The test is lines of which
 * half have gone wrong at the end, stopped, run away or slowed, judged by how much the model is surprised by them.
 */
final class Watching extends Task {
    /** Lines a test judges. */
    private static final int LINES = 128;
    private static final long SEED = 4242;

    Watching() {
        super(Vocab.WORLD, Vocab.CONTEXT, Metric.ACCURACY, Benchmark.WATCH.target);
    }

    @Override
    public Batch batch(Random rng, int size, boolean test) {
        return Dataset.of(Benchmark.DEMAND).batch(rng, size, test);
    }

    @Override
    public List<String> heldOutTexts() {
        Random rng = new Random(SEED);
        List<String> out = new ArrayList<>(LINES);
        for (int i = 0; i < LINES; i++) {
            out.add(Watch.example(rng, true).line());
        }
        return out;
    }

    @Override
    public double[] measure(CompiledGraph model) {
        Random rng = new Random(SEED);
        int right = 0;
        for (int i = 0; i < LINES; i++) {
            Watch.Example ex = Watch.example(rng, true);
            right += Watch.wrong(model, ex.line()) == (ex.fault() != null) ? 1 : 0;
        }
        double share = right / (double) LINES;
        return new double[] {1 - share, share};
    }
}
