package io.github.metrovoc.residual.interior;

import io.github.metrovoc.residual.learn.Archive;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.Vocab;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** What a model in a hall is asked to do, measured on examples it has not practised. */
public enum Stage {
    /** The next letter of the archive's writing. */
    READING(Benchmark.READING),
    /** A row of signs, then the same row backwards. */
    REVERSAL(Benchmark.REVERSAL),
    /** Letters where a run comes round again: carry it on. */
    PATTERN(Benchmark.INDUCTION),
    /** Pairs, then one of them asked for. */
    RECALL(Benchmark.RECALL);

    public final Benchmark benchmark;
    private static Task writing;

    Stage(Benchmark benchmark) {
        this.benchmark = benchmark;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Task practice() {
        return benchmark == Benchmark.READING ? writing() : benchmark.practice();
    }

    public Task test() {
        return benchmark.test(Vocab.WORLD);
    }

    /** How many tokens of an example a hall shows. */
    public int lanes() {
        return Math.min(benchmark.length, 32);
    }

    /** All the archive's writing that is not held out. */
    private static synchronized Task writing() {
        if (writing == null) {
            Map<Integer, BitSet> all = new HashMap<>();
            for (Archive.Record r : Archive.records()) {
                all.put(r.id(), new BitSet());
            }
            writing = Archive.practice(all, Vocab.WORLD);
        }
        return writing;
    }
}
