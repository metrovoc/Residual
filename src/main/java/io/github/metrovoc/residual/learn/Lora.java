package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Decoder;
import io.github.metrovoc.residual.tensor.AdamW;
import io.github.metrovoc.residual.tensor.Ops;
import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * An adapter: every matrix of a model stays as it is, and beside it two thin matrices learn what to add to it, their product having at most
 * {@code rank} independent rows. Far fewer numbers learn, so each step moves far less of what the model knew. When it is done the product is added
 * into the matrix, which gives exactly what the adapter gave.
 */
public final class Lora implements Tuning.Method {
    private record Pair(int node, String name, Tensor a, Tensor b) {
    }

    private final CompiledGraph model;
    private final Task task;
    private final List<Pair> pairs = new ArrayList<>();
    private final AdamW optimizer;
    private final Random rng;
    private final float rate;
    private int step;

    public Lora(CompiledGraph model, Task task, int rank, float rate, long seed) {
        this.model = model;
        this.task = task;
        this.rng = new Random(seed);
        this.rate = rate;
        Random init = new Random(seed + 1);
        List<Tensor> learned = new ArrayList<>();
        for (int node : model.order) {
            for (Map.Entry<String, Tensor> e : model.params(node).entrySet()) {
                Tensor w = e.getValue();
                if (w.rank() == 2 && w.dim(0) > rank && w.dim(1) > rank) {
                    Tensor a = Tensor.zeros(true, w.dim(0), rank), b = Tensor.zeros(true, rank, w.dim(1));
                    // one of the two starts at zero, so the adapter starts by adding nothing
                    for (int i = 0; i < a.data.length; i++) {
                        a.data[i] = (float) (init.nextGaussian() * 0.02);
                    }
                    pairs.add(new Pair(node, e.getKey(), a, b));
                    learned.add(a);
                    learned.add(b);
                }
            }
        }
        optimizer = new AdamW(learned);
    }

    /** How many numbers it learns. */
    public long parameters() {
        long n = 0;
        for (Pair p : pairs) {
            n += p.a.size() + p.b.size();
        }
        return n;
    }

    private Decoder.Weights weights(Tape tape) {
        Map<Integer, Map<String, Tensor>> moved = new HashMap<>();
        for (Pair p : pairs) {
            Map<String, Tensor> m = moved.computeIfAbsent(p.node, n -> new LinkedHashMap<>(model.params(n)));
            m.put(p.name, Ops.add(tape, model.params(p.node).get(p.name), Ops.matmul(tape, p.a, p.b, false)));
        }
        return node -> moved.getOrDefault(node, model.params(node));
    }

    @Override
    public Tuning.Report step() {
        Batch b = task.batch(rng, 32, false);
        Tape tape = new Tape();
        Tensor logits = model.forwardWith(tape, b.input(), weights(tape));
        Tensor loss = Ops.crossEntropy(tape, logits, b.targets(), b.weights());
        Tuning.apply(optimizer, tape, loss, rate, step++);
        return Tuning.Report.of((float) (loss.item() * b.perChar()));
    }

    /** What the model with its adapter as it stands makes of {@code tokens}, for a test. */
    float[] forward(io.github.metrovoc.residual.tensor.Tokens tokens) {
        return model.forwardWith(null, tokens, weights(null)).data;
    }

    /** Adds what the adapter learned into the model's own matrices. */
    public void fold() {
        for (Pair p : pairs) {
            Tensor sum = Ops.add(null, model.params(p.node).get(p.name), Ops.matmul(null, p.a, p.b, false));
            System.arraycopy(sum.data, 0, model.params(p.node).get(p.name).data, 0, sum.data.length);
        }
    }

    /**
     * What two tunings of one model come to together: {@code origin} plus what each moved it by; or with no origin, the mean of the two. All
     * three have the same machines.
     */
    public static CompiledGraph merge(CompiledGraph a, CompiledGraph b, CompiledGraph origin) {
        CompiledGraph out = Tuning.copy(a);
        List<Tensor> mine = out.parameters(), theirs = b.parameters(), from = origin == null ? null : origin.parameters();
        for (int t = 0; t < mine.size(); t++) {
            float[] m = mine.get(t).data, y = theirs.get(t).data;
            for (int i = 0; i < m.length; i++) {
                m[i] = from == null ? (m[i] + y[i]) / 2 : m[i] + y[i] - from.get(t).data[i];
            }
        }
        return out;
    }
}
