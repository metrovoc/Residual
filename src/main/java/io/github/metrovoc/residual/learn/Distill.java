package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.tensor.AdamW;
import io.github.metrovoc.residual.tensor.Ops;
import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.Random;
import java.util.function.Function;

/**
 * Distilling: a small model practises not on the right answers but on what a larger one makes of each next token, how likely it finds every
 * one of them. That says far more than the one answer does: which wrong answers were nearly right. The larger model is asked as anything is,
 * so its capacity is what this draws on.
 */
public final class Distill implements Tuning.Method {
    /** Sequences a step reads: each costs the teacher every row of it. */
    public static final int BATCH = 16;

    private final CompiledGraph student;
    private final Function<Tokens, float[]> teacher;
    private final Task task;
    private final float temperature;
    private final AdamW optimizer;
    private final Random rng;
    private final float rate;
    private int step;

    /** @param teacher how the larger model's scores are got, a row for each token; it may wait for them */
    public Distill(CompiledGraph student, Function<Tokens, float[]> teacher, Task task, float temperature, float rate, long seed) {
        this.student = student;
        this.teacher = teacher;
        this.task = task;
        this.temperature = temperature;
        this.optimizer = new AdamW(student.parameters());
        this.rng = new Random(seed);
        this.rate = rate;
    }

    @Override
    public Tuning.Report step() {
        Batch b = task.batch(rng, BATCH, false);
        float[] scores = teacher.apply(b.input());
        Tape tape = new Tape();
        Tensor logits = student.forward(tape, b.input(), null);
        int v = logits.dim(-1);
        float[] soft = new float[scores.length];
        for (int row = 0; row < scores.length / v; row++) {
            float[] p = Sampler.probs(scores, row * v, v, temperature);
            System.arraycopy(p, 0, soft, row * v, v);
        }
        Tensor loss = Ops.softCrossEntropy(tape, logits, soft, b.weights(), temperature);
        Tuning.apply(optimizer, tape, loss, rate, step++);
        return Tuning.Report.of(loss.item());
    }
}
