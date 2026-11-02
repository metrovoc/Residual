package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * One design, trained again from different random weights and batches. How far the scores spread says how much of any difference between
 * two designs could be the luck of the draw.
 */
public final class Seeds {
    /** How many runs make a spread, and the steps a run can last. */
    public static final int RUNS = 5;
    public static final int[] STEPS = {250, 500, 1000, 2000, 4000};
    private static final int RESAMPLES = 2000;

    /** How a design's runs fell out: their mean and spread, and the range the mean lies in nine times in ten, by resampling the runs. */
    public record Spread(float mean, float sd, float low, float high) {
    }

    private Seeds() {
    }

    /** The test score of each benchmark {@code data} practises, one per run, as far as it got before {@code stop}. */
    public static Map<Benchmark, float[]> run(CompiledGraph design, Mixture data, float learningRate, int batch, float clip, float decay, Recipe recipe, int steps, int runs, long seed,
                                              BooleanSupplier stop, Consumer<Float> progress) {
        Map<Benchmark, float[]> out = new EnumMap<>(Benchmark.class);
        for (Mixture.Source s : data.sources) {
            out.put(s.benchmark(), new float[runs]);
        }
        for (int r = 0; r < runs && !stop.getAsBoolean(); r++) {
            CompiledGraph model = design.fresh(seed + r);
            Trainer trainer = new Trainer(model, data, seed + r, learningRate, batch, clip);
            trainer.weightDecay = decay;
            trainer.recipe = recipe;
            for (int s = 1; s <= steps && !stop.getAsBoolean(); s++) {
                trainer.train();
                progress.accept((r + (float) s / steps) / runs);
            }
            for (Map.Entry<Benchmark, float[]> e : out.entrySet()) {
                Benchmark a = e.getKey();
                Trainer.Eval eval = Trainer.evaluate(model, a.test(model.env.tokenizer()), 8);
                e.getValue()[r] = (float) (a.metric == Task.Metric.LOSS ? eval.loss() : eval.accuracy());
            }
        }
        return out;
    }

    public static Spread spread(float[] scores) {
        double sum = 0;
        for (float s : scores) {
            sum += s;
        }
        double mean = sum / scores.length, var = 0;
        for (float s : scores) {
            var += (s - mean) * (s - mean);
        }
        Random rng = new Random(1);
        float[] means = new float[RESAMPLES];
        for (int i = 0; i < RESAMPLES; i++) {
            double total = 0;
            for (int k = 0; k < scores.length; k++) {
                total += scores[rng.nextInt(scores.length)];
            }
            means[i] = (float) (total / scores.length);
        }
        Arrays.sort(means);
        return new Spread((float) mean, (float) Math.sqrt(var / Math.max(1, scores.length - 1)), means[RESAMPLES / 20], means[RESAMPLES - 1 - RESAMPLES / 20]);
    }

    /** Whether the gap between two designs' means is no more than their runs' spread could leave by luck: under twice its standard error. */
    public static boolean withinNoise(float[] a, float[] b) {
        Spread x = spread(a), y = spread(b);
        double error = Math.sqrt(x.sd() * x.sd() / a.length + y.sd() * y.sd() / b.length);
        return Math.abs(x.mean() - y.mean()) < 2 * error;
    }
}
