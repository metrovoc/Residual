package io.github.metrovoc.residual.learn;

import java.util.List;
import java.util.Random;

/** Practice drawn evenly from every source loaded into a Model Core: each batch comes from one of them. */
public final class Mixture extends Task {
    /** One source and the benchmark it practises. */
    public record Source(Benchmark benchmark, Task data) {
    }

    public final List<Source> sources;

    public Mixture(List<Source> sources, Vocab vocab) {
        super(vocab, sources.stream().mapToInt(s -> s.data().context).max().orElse(Vocab.CONTEXT), Metric.LOSS, 0);
        this.sources = List.copyOf(sources);
    }

    @Override
    public Batch batch(Random rng, int size, boolean test) {
        return sources.get(rng.nextInt(sources.size())).data().batch(rng, size, test);
    }
}
