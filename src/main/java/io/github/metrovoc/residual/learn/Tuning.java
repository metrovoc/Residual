package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompileException;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.tensor.AdamW;
import io.github.metrovoc.residual.tensor.Ops;
import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * What every kind of tuning shares: a model of its own to change, sequences of different lengths laid out for one pass, and how sure a
 * model is of a whole answer. Tuning starts from a model that has already been trained, so it moves in small steps.
 */
public final class Tuning {
    private Tuning() {
    }

    /** A model with the same machines and its own copy of {@code model}'s weights. */
    public static CompiledGraph copy(CompiledGraph model) {
        try {
            return CompiledGraph.compile(model.spec, model.env, model::params, 0);
        } catch (CompileException e) {
            throw new IllegalStateException("a compiled model no longer compiles", e);
        }
    }

    /** One step's worth of a tuning: its numbers, and an answer as it stands now. */
    public interface Method {
        /** Takes one step and says what it found. */
        Report step();
    }

    /**
     * What a step found.
     *
     * @param reward   what it was trying to raise: what the checker or judge gave, or how often it agrees
     * @param truth    what was really so, where anyone knows, or NaN
     * @param loss     what it lowered
     * @param said     an order or prompt, for showing
     * @param wrote    what the model wrote for it
     * @param attempts for each attempt at that prompt, 1 if it passed and 0 if not, or empty
     */
    public record Report(double reward, double truth, float loss, String said, String wrote, boolean[] attempts) {
        public static Report of(float loss) {
            return new Report(Double.NaN, Double.NaN, loss, "", "", new boolean[0]);
        }
    }

    /** Sequences laid out in one batch: {@code inputs} and {@code targets} shifted by one, padded on the end. */
    public static final class Layout {
        public final Tokens tokens;
        public final int[] targets;
        public final int time;
        final List<int[]> sequences;

        public Layout(List<int[]> sequences, int context) {
            this.sequences = sequences;
            int longest = 1;
            for (int[] s : sequences) {
                longest = Math.max(longest, s.length - 1);
            }
            time = Math.min(longest, context);
            int b = sequences.size();
            int[] in = new int[b * time];
            targets = new int[b * time];
            for (int i = 0; i < b; i++) {
                int[] s = sequences.get(i);
                for (int k = 0; k < time && k + 1 < s.length; k++) {
                    in[i * time + k] = s[k];
                    targets[i * time + k] = s[k + 1];
                }
            }
            tokens = new Tokens(in, b, time);
        }

        /** Weights on the rows predicting {@code seq}'s tokens from {@code from} on, all {@code w}. */
        public void mark(float[] weights, int seq, int from, float w) {
            int[] s = sequences.get(seq);
            for (int k = Math.max(0, from - 1); k < time && k + 1 < s.length; k++) {
                weights[seq * time + k] = w;
            }
        }

        /** How likely {@code logits} make {@code seq}'s tokens from {@code from} on, added up in nats. */
        public double logProb(Tensor logits, int seq, int from) {
            int v = logits.dim(-1);
            int[] s = sequences.get(seq);
            double sum = 0;
            for (int k = Math.max(0, from - 1); k < time && k + 1 < s.length; k++) {
                sum += Tuning.logProb(logits.data, (seq * time + k) * v, v, s[k + 1]);
            }
            return sum;
        }

        /** The row of the last token of {@code seq} that is in view. */
        public int lastRow(int seq) {
            return seq * time + Math.min(time, sequences.get(seq).length) - 1;
        }
    }

    /** Log of the probability of {@code target} in a row of scores. */
    public static double logProb(float[] scores, int off, int v, int target) {
        double max = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < v; j++) {
            max = Math.max(max, scores[off + j]);
        }
        double sum = 0;
        for (int j = 0; j < v; j++) {
            sum += Math.exp(scores[off + j] - max);
        }
        return scores[off + target] - max - Math.log(sum);
    }

    /** One step of practice on {@code task} at a rate going down with {@code left}: fine-tuning and mid-training are this, on what they mix. */
    public static final class FineTune implements Method {
        private final Trainer trainer;
        private final float rate;
        private final int budget;
        private final Task task;
        private final Random rng;

        public FineTune(CompiledGraph model, Task task, float rate, int budget, long seed) {
            trainer = new Trainer(model, task, seed, rate, 32, 1f);
            this.rate = rate;
            this.budget = budget;
            this.task = task;
            rng = new Random(seed + 1);
        }

        @Override
        public Report step() {
            // annealed: down to a tenth of its rate by the end
            double through = Math.min(1, trainer.step() / (double) Math.max(1, budget));
            trainer.learningRate = (float) (rate * (0.1 + 0.9 * 0.5 * (1 + Math.cos(Math.PI * through))));
            float loss = trainer.train();
            return Report.of(loss);
        }

        public Task task() {
            return task;
        }
    }

    /** Practice by a loss the method works out itself: the optimizer and its clip. */
    static void apply(AdamW optimizer, Tape tape, Tensor loss, float rate, int step) {
        optimizer.zeroGrad();
        tape.backward(loss);
        optimizer.clip(1f);
        optimizer.step(Trainer.rate(rate, step));
    }

    /** Draws {@code n} of {@code items} at random, with repeats if there are fewer. */
    static <T> List<T> draw(List<T> items, int n, Random rng) {
        List<T> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(items.get(rng.nextInt(items.size())));
        }
        return out;
    }

    /** Everything a batch needs to be a loss on chosen and rejected answers. */
    static Tensor scored(Tape tape, Tensor logits, Layout layout, float[] weights, float norm) {
        return Ops.scoredCrossEntropy(tape, logits, layout.targets, weights, norm);
    }
}
