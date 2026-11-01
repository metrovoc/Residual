package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Extras;
import io.github.metrovoc.residual.tensor.AdamW;
import io.github.metrovoc.residual.tensor.Numerics;
import io.github.metrovoc.residual.tensor.Ops;
import io.github.metrovoc.residual.tensor.Optimizer;
import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.Random;

/** Runs optimisation steps of a compiled model on a task and evaluates it. */
public final class Trainer {
    public static final int WARMUP = 20;

    public record Eval(double loss, double accuracy, boolean causal) {
    }

    public final CompiledGraph model;
    /** What it practises on; may be swapped between steps. */
    public volatile Task task;
    private Optimizer optimizer;
    private Recipe.Moves moves = Recipe.Moves.ADAMW;
    private final Random rng;
    /** How the run is trained; may be swapped between steps. */
    public volatile Recipe recipe = Recipe.PLAIN;
    private float lastRate;
    public float learningRate;
    public int batchSize;
    /** Gradient-norm clip; 0 disables clipping. */
    public float clip;
    private int step;
    private double lastGradNorm;

    public Trainer(CompiledGraph model, Task task, long seed, float learningRate, int batchSize, float clip) {
        this.model = model;
        this.task = task;
        this.optimizer = new AdamW(model.parameters());
        this.rng = new Random(seed);
        this.learningRate = learningRate;
        this.batchSize = batchSize;
        this.clip = clip;
    }

    public int step() {
        return step;
    }

    public double lastGradNorm() {
        return lastGradNorm;
    }

    /** Whether a step's loss is a spike: far above the recent average for its kind of batch, and by more than a batch's luck. */
    public static boolean spiked(double loss, double recent) {
        return loss > 1.5 * recent + 0.1;
    }

    /** The learning rate the last step took. */
    public float lastRate() {
        return lastRate;
    }

    /** One optimisation step; returns the training loss per character before the update. */
    public float train() {
        Recipe r = recipe;
        model.precision = r.precision();
        if (moves != r.moves()) {
            moves = r.moves();
            optimizer = r.optimizer(model);
        }
        bind(task, model);
        Batch b = task.batch(rng, batchSize, false);
        Tape tape = new Tape();
        Extras extras = new Extras();
        Tensor logits = model.forward(tape, b.input(), null, extras);
        Tensor loss = Ops.crossEntropy(tape, logits, b.targets(), b.weights());
        Tensor total = loss;
        for (Tensor penalty : extras.penalties) {
            total = Ops.add(tape, total, penalty);
        }
        if (extras.second != null) {
            Next next = next(b);
            total = Ops.add(tape, total, Ops.scale(tape, Ops.crossEntropy(tape, extras.second, next.targets, next.weights), Extras.SECOND_WEIGHT));
        }
        optimizer.zeroGrad();
        tape.backward(total);
        lastGradNorm = optimizer.clip(clip);
        lastRate = learningRate * r.multiplier(step);
        optimizer.step(lastRate);
        step++;
        return (float) (loss.item() * b.perChar());
    }

    private record Next(int[] targets, float[] weights) {
    }

    /** What a second head aims at: the token after the next one, where the next one and that one both count. */
    private static Next next(Batch b) {
        int t = b.input().time;
        int[] targets = new int[b.targets().length];
        float[] weights = new float[targets.length];
        for (int r = 0; r < b.input().batch; r++) {
            for (int i = 0; i + 1 < t; i++) {
                int at = r * t + i;
                targets[at] = b.targets()[at + 1];
                weights[at] = b.weights() == null || b.weights()[at] != 0 && b.weights()[at + 1] != 0 ? 1f : 0f;
            }
        }
        return new Next(targets, weights);
    }

    /**
     * How often a second head's first choice is the token after next, on {@code task}'s held-out examples: how often it
     * would have spared a model the work of one token. NaN if it has none.
     */
    public static float secondRight(CompiledGraph model, Task task) {
        Random rng = new Random(54321);
        long right = 0, scored = 0;
        for (int i = 0; i < 4; i++) {
            Batch b = task.batch(rng, 32, true);
            Extras extras = new Extras();
            model.forward(null, b.input(), null, extras);
            if (extras.second == null) {
                return Float.NaN;
            }
            Next next = next(b);
            int v = extras.second.dim(-1);
            for (int r = 0; r < next.targets.length; r++) {
                if (next.weights[r] != 0) {
                    scored++;
                    right += argmax(extras.second.data, r * v, v) == next.targets[r] ? 1 : 0;
                }
            }
        }
        return scored == 0 ? Float.NaN : (float) right / scored;
    }

    /** The rate of step {@code step}, counted from 0: it rises linearly to {@code learningRate} over {@link #WARMUP} steps. */
    public static float rate(float learningRate, int step) {
        return learningRate * Math.min(1f, (step + 1f) / WARMUP);
    }

    /** Validation loss per character and answer accuracy over a fixed set of batches, plus the causality check. */
    public Eval evaluate(int batches) {
        return evaluate(model, task, batches);
    }

    /** A task that plays its own examples, such as Control, plays them with the model being practised. */
    private static void bind(Task task, CompiledGraph model) {
        if (task instanceof Played played) {
            played.policy(model);
        } else if (task instanceof Mixture mixture) {
            mixture.sources.forEach(s -> bind(s.data(), model));
        }
    }

    public static Eval evaluate(CompiledGraph model, Task task, int batches) {
        double[] own = task.measure(model);
        if (own != null) {
            return new Eval(own[0], own[1], isCausal(model, task));
        }
        Random rng = new Random(12345);
        double loss = 0;
        long correct = 0;
        long scored = 0;
        for (int i = 0; i < batches; i++) {
            Batch b = task.batch(rng, 32, true);
            Tensor logits = model.forward(null, b.input(), null);
            loss += Ops.crossEntropy(null, logits, b.targets(), b.weights()).item() * b.perChar();
            int v = logits.dim(-1);
            for (int r = 0; r < b.targets().length; r++) {
                if (b.weights() != null && b.weights()[r] == 0f) {
                    continue;
                }
                scored++;
                if (argmax(logits.data, r * v, v) == b.targets()[r]) {
                    correct++;
                }
            }
        }
        return new Eval(loss / batches, scored == 0 ? 0 : (double) correct / scored, isCausal(model, task));
    }

    /**
     * A language model may only use earlier tokens. Changing the second half of a sequence must not change any
     * prediction in the first half.
     */
    public static boolean isCausal(CompiledGraph model, Task task) {
        Random rng = new Random(777);
        Batch b = task.batch(rng, 2, true);
        Tokens a = b.input();
        int t = a.time;
        int cut = t / 2;
        int[] changed = a.ids.clone();
        for (int s = 0; s < a.batch; s++) {
            for (int i = cut; i < t; i++) {
                changed[s * t + i] = (changed[s * t + i] + 1 + rng.nextInt(task.vocab.size() - 1)) % task.vocab.size();
            }
        }
        // in fp32: the scale a narrow format takes from a whole tensor is not the model reading ahead
        Tensor x = model.forward(null, a, null, null, Numerics.Format.FP32);
        Tensor y = model.forward(null, new Tokens(changed, a.batch, t), null, null, Numerics.Format.FP32);
        int v = x.dim(-1);
        for (int s = 0; s < a.batch; s++) {
            for (int i = 0; i < cut; i++) {
                int off = (s * t + i) * v;
                for (int j = 0; j < v; j++) {
                    float d = Math.abs(x.data[off + j] - y.data[off + j]);
                    if (!(d <= 1e-3f * (1f + Math.abs(x.data[off + j])))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    static int argmax(float[] data, int off, int n) {
        int best = 0;
        for (int j = 1; j < n; j++) {
            if (data[off + j] > data[off + best]) {
                best = j;
            }
        }
        return best;
    }
}
