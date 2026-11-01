package io.github.metrovoc.residual.refine;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.learn.Archive;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Blueprints;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.TextTask;
import io.github.metrovoc.residual.learn.Trainer;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.tensor.Parallel;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;

/**
 * What a cut of the text is worth: a small reader is trained on it from nothing, always the same reader for the same number of
 * steps from the same seeds, and tested on the records no shard carries. Two cuts differ only in the text, so what lies between
 * their scores is what the stage between them did. The reader is small enough to be quick and reads a few letters back, so it
 * says what the text teaches about writing and little about what a big model could make of it.
 */
public final class Assay {
    /** Steps each reader trains, how many readers each cut gets, and how long a reader trains to see what annealing does. */
    public static final int STEPS = 400, SEEDS = 3, LONG_STEPS = 2000;
    private static final float RATE = 3e-3f, CLIP = 1f;
    private static final int BATCH = 32, TEST_BATCHES = 16, DOMAIN_BATCHES = 8;
    private static final Map<Domain, TextTask> BY_DOMAIN = new EnumMap<>(Domain.class);

    /**
     * What one reader reached.
     *
     * @param loss     held-out loss per character on Reading's test
     * @param byDomain the same on the held-out records of each domain the archive files, by domain
     * @param orders   how often it answers the Orders test right, as what a mix of writing has taught it to say
     * @param sorting  and the Sorting test
     */
    public record Score(double loss, double[] byDomain, double orders, double sorting) {
    }

    private Assay() {
    }

    public static GraphSpec reader() {
        return Blueprints.context(8, 16, 128);
    }

    /** Trains a reader on {@code cut} from {@code seed} and tests it; a cut with nothing long enough to read has no score. */
    public static Score run(Supply cut, long seed, ForkJoinPool pool) {
        return run(cut, seed, pool, STEPS);
    }

    /** As {@link #run(Supply, long, ForkJoinPool)} for {@code steps} steps, annealing for the last fifth of them if the cut anneals. */
    public static Score run(Supply cut, long seed, ForkJoinPool pool, int steps) {
        Supply supply = cut.anneal() < 0 ? cut : new Supply(cut.docs(), cut.mix(), cut.anneal(), steps * 4 / 5);
        SupplyTask task = new SupplyTask(supply, Vocab.WORLD);
        if (task.isEmpty()) {
            return null;
        }
        CompiledGraph model;
        try {
            model = CompiledGraph.compile(reader(), Env.WORLD, null, seed);
        } catch (io.github.metrovoc.residual.graph.CompileException e) {
            throw new IllegalStateException(e);
        }
        Trainer trainer = new Trainer(model, task, seed, RATE, BATCH, CLIP);
        Score[] out = new Score[1];
        Parallel.with(pool, () -> {
            for (int s = 0; s < steps; s++) {
                trainer.learningRate = RATE * task.rateScale();
                trainer.train();
            }
            out[0] = score(model);
        });
        return out[0];
    }

    private static Score score(CompiledGraph model) {
        double loss = Trainer.evaluate(model, Archive.test(Vocab.WORLD), TEST_BATCHES).loss();
        double[] by = new double[Domain.WRITTEN];
        for (int d = 0; d < by.length; d++) {
            TextTask test = heldOut(Domain.values()[d]);
            by[d] = test == null ? Double.NaN : Trainer.evaluate(model, test, DOMAIN_BATCHES).loss();
        }
        Task orders = Benchmark.ORDERS.test(Vocab.WORLD), sorting = Benchmark.SORTING.test(Vocab.WORLD);
        return new Score(loss, by, Trainer.evaluate(model, orders, DOMAIN_BATCHES).accuracy(), Trainer.evaluate(model, sorting, DOMAIN_BATCHES).accuracy());
    }

    /** The held-out records of one domain. */
    public static synchronized TextTask heldOut(Domain domain) {
        return BY_DOMAIN.computeIfAbsent(domain, d -> {
            List<String> texts = new ArrayList<>();
            for (Archive.Record r : Archive.records()) {
                if (Archive.heldOut(r.id()) && Domain.filed(r.filed()) == d) {
                    texts.add(r.text());
                }
            }
            return new TextTask(texts, Benchmark.READING.target, Vocab.WORLD);
        });
    }
}
