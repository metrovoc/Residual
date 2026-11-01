package io.github.metrovoc.residual.learn;

import java.util.List;
import java.util.Random;

/** Practice drawn evenly from every source loaded into a Model Core: each batch comes from one of them. */
public final class Mixture extends Task {
    /** One source and the benchmark it practises. */
    public record Source(Benchmark benchmark, Task data) {
    }

    public final List<Source> sources;
    /** What the last batch of practice was drawn from: batches of different kinds have losses of very different sizes. */
    public Benchmark lastDrawn;

    public Mixture(List<Source> sources, Vocab vocab) {
        super(vocab, sources.stream().mapToInt(s -> s.data().context).max().orElse(Vocab.CONTEXT), Metric.LOSS, 0);
        this.sources = List.copyOf(sources);
    }

    @Override
    public Batch batch(Random rng, int size, boolean test) {
        Source source = sources.get(rng.nextInt(sources.size()));
        if (!test) {
            lastDrawn = source.benchmark();
        }
        return source.data().batch(rng, size, test);
    }
}
