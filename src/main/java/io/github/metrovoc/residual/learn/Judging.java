package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.refine.Quality;
import java.util.List;
import java.util.Random;

/**
 * Quality's test: documents no shard carries, half of them spoiled, judged by how much they surprise a model that has read. It has
 * no practice of its own: a model practises on writing, and reads text the same way whether it is tested on it or not.
 */
final class Judging extends Task {
    Judging(Vocab vocab) {
        super(vocab, Vocab.CONTEXT, Metric.ACCURACY, Benchmark.QUALITY.target);
    }

    @Override
    public Batch batch(Random rng, int size, boolean test) {
        return Archive.test(vocab).batch(rng, size, test);
    }

    @Override
    public double[] measure(CompiledGraph model) {
        double share = Quality.accuracy(model);
        return new double[] {1 - share, share};
    }

    @Override
    public List<String> heldOutTexts() {
        return Quality.testTexts();
    }
}
