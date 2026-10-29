package io.github.metrovoc.residual.agent;

import io.github.metrovoc.residual.learn.Benchmark;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/** The standing work an agent does, and the benchmark its model is asked for while doing it. */
public enum Job {
    REST(null), FARM(Benchmark.REACTING), STORE(Benchmark.RECALL), HAUL(Benchmark.REVERSAL), BUILD(Benchmark.INDUCTION);

    public final String id = name().toLowerCase(Locale.ROOT);
    @Nullable
    public final Benchmark benchmark;

    Job(@Nullable Benchmark benchmark) {
        this.benchmark = benchmark;
    }
}
