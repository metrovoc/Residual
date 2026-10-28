package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.tensor.AdamW;
import io.github.metrovoc.residual.tensor.Ops;
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
    public final Task task;
    private final AdamW optimizer;
    private final Random rng;
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

    /** One optimisation step; returns the training loss before the update. */
    public float train() {
        Batch b = task.batch(rng, batchSize, false);
        Tape tape = new Tape();
        Tensor logits = model.forward(tape, b.input(), null);
        Tensor loss = Ops.crossEntropy(tape, logits, b.targets(), b.weights());
        optimizer.zeroGrad();
        tape.backward(loss);
        lastGradNorm = optimizer.clip(clip);
        float warm = Math.min(1f, (step + 1f) / WARMUP);
        optimizer.step(learningRate * warm);
        step++;
        return loss.item();
    }

    /** Validation loss and answer accuracy over a fixed set of batches, plus the causality check. */
    public Eval evaluate(int batches) {
        return evaluate(model, task, batches);
    }

    public static Eval evaluate(CompiledGraph model, Task task, int batches) {
        Random rng = new Random(12345);
        double loss = 0;
        long correct = 0;
        long scored = 0;
        for (int i = 0; i < batches; i++) {
            Batch b = task.batch(rng, 32, true);
            Tensor logits = model.forward(null, b.input(), null);
            loss += Ops.crossEntropy(null, logits, b.targets(), b.weights()).item();
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
        Tensor x = model.forward(null, a, null);
        Tensor y = model.forward(null, new Tokens(changed, a.batch, t), null);
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
