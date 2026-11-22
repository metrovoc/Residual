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
    /**
     * The next letter of the archive's writing, as well as a table of what follows what can do it. A table 8 to 32 wide
     * gets under 2.45 from ten starts of ten within 750 steps, and none gets under 2.34 however long it runs.
     */
    TABLE(Benchmark.READING, 2.45, 0),
    /**
     * A row of signs, then the same row backwards. One attention part at most: three of them find the places without
     * being told them, which is not what this is for.
     */
    REVERSAL(Benchmark.REVERSAL, 0, 1),
    /** Letters where a run comes round again: carry it on. */
    PATTERN(Benchmark.INDUCTION, 0, 0);

    public final Benchmark benchmark;
    /** The test loss that passes, where that is not the benchmark's own; or 0. */
    public final double loss;
    /** How many attention parts a model may have, or 0 for any number. */
    public final int attention;
    private static Task writing;

    Stage(Benchmark benchmark, double loss, int attention) {
        this.benchmark = benchmark;
        this.loss = loss;
        this.attention = attention;
    }

    /** The loss to get under, or 0 where the share of answers right decides. */
    public double lossGoal() {
        return loss > 0 ? loss : benchmark.metric == Task.Metric.LOSS ? benchmark.target : 0;
    }

    /** The share of answers to get right, or 0 where the loss decides. */
    public double rightGoal() {
        return benchmark.metric == Task.Metric.ACCURACY ? benchmark.target : 0;
    }

    /** {@code arch} with no more attention parts than this allows: the last ones go. */
    public Arch fit(Arch arch) {
        while (attention > 0 && attentionParts(arch) > attention) {
            Arch.Part last = null;
            for (Arch.Part part : arch.parts()) {
                if (part.kind() == Arch.Kind.ATTENTION) {
                    last = part;
                }
            }
            arch = arch.remove(last.id());
        }
        return arch;
    }

    public boolean allowsAttention(Arch arch) {
        return attention == 0 || attentionParts(arch) < attention;
    }

    private static int attentionParts(Arch arch) {
        return (int) arch.parts().stream().filter(part -> part.kind() == Arch.Kind.ATTENTION).count();
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
