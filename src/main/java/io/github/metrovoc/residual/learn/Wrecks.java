package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.Config;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.graph.Op;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Models found broken in the ruins. Each comes with the machine it was, the practice it was given and how it was
 * trained; rebuilt in front of a Model Core it has to be repaired and pass its test within a budget of steps.
 */
public final class Wrecks {
    /** Optimiser settings a wreck was trained with. */
    public record Settings(float learningRate, int batchSize, float clip) {
        public static final Settings DEFAULT = new Settings(3e-3f, 32, 1f);
    }

    public record Wreck(String id, Benchmark benchmark, double target, Settings settings, Supplier<GraphSpec> machine) {
        /** What it practises on. */
        public Task practice() {
            return benchmark == Benchmark.READING ? reading() : Dataset.of(benchmark);
        }

        public boolean passes(Trainer.Eval eval) {
            return eval.causal() && (benchmark.metric == Task.Metric.LOSS ? eval.loss() <= target : eval.accuracy() >= target);
        }
    }

    /** Steps a rebuilt wreck may train before its run counts as failed; reset the weights to try again. */
    public static final int BUDGET = 1500;

    public static final List<Wreck> ALL = List.of(
            new Wreck("shape", Benchmark.REACTING, 0.98, Settings.DEFAULT, Wrecks::shape),
            new Wreck("diverge", Benchmark.RECALL, 0.9, new Settings(1.5f, 32, 0f), Blueprints::recall),
            new Wreck("leak", Benchmark.REACTING, 0.98, Settings.DEFAULT, Wrecks::leak),
            new Wreck("order", Benchmark.REVERSAL, 0.9, Settings.DEFAULT, Wrecks::order),
            new Wreck("depth", Benchmark.READING, 2.4, Settings.DEFAULT, Wrecks::depth));

    private static TextTask reading;

    private Wrecks() {
    }

    public static Wreck byId(String id) {
        for (Wreck w : ALL) {
            if (w.id().equals(id)) {
                return w;
            }
        }
        return null;
    }

    /** The depth wreck still holds the first records it read, whole. */
    private static synchronized TextTask reading() {
        if (reading == null) {
            Map<Integer, BitSet> records = new HashMap<>();
            for (Archive.Record r : Archive.records()) {
                if (!Archive.heldOut(r.id()) && records.size() < 40) {
                    records.put(r.id(), new BitSet());
                }
            }
            reading = Archive.practice(records);
        }
        return reading;
    }

    /** A field model whose last layer gives 32 scores instead of one per character. */
    static GraphSpec shape() {
        GraphSpec g = new GraphSpec();
        int e = g.add("embed", Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", "32"), GraphSpec.TOKENS);
        g.output = g.add("logits", Op.LINEAR, Config.of(Op.LINEAR, "out", "32"), e);
        return g;
    }

    /** Attention with no causal mask: every position can read the character it is supposed to predict. */
    static GraphSpec leak() {
        GraphSpec g = new GraphSpec();
        int e = g.add("embed", Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", "32"), GraphSpec.TOKENS);
        int pos = g.add("pos", Op.POSITION, Config.of(Op.POSITION, "dim", "32"));
        int x = g.add("x", Op.ADD, e, pos);
        int q = g.add("query", Op.LINEAR, Config.of(Op.LINEAR, "out", "32", "bias", "false"), x);
        int qs = g.add("q_scaled", Op.SCALE, q);
        int k = g.add("key", Op.LINEAR, Config.of(Op.LINEAR, "out", "32", "bias", "false"), x);
        int v = g.add("value", Op.LINEAR, Config.of(Op.LINEAR, "out", "32", "bias", "false"), x);
        int s = g.add("scores", Op.MATMUL, Config.of(Op.MATMUL, "transpose", "true"), qs, k);
        int p = g.add("weights", Op.SOFTMAX, s);
        int o = g.add("mixed", Op.MATMUL, p, v);
        int r = g.add("residual", Op.ADD, x, o);
        int h = g.add("hidden", Op.LINEAR, Config.of(Op.LINEAR, "out", "128"), r);
        int a = g.add("act", Op.ACTIVATION, Config.of(Op.ACTIVATION, "fn", "relu"), h);
        g.output = g.add("logits", Op.LINEAR, Config.of(Op.LINEAR, "out", "vocab"), a);
        return g;
    }

    /** An errand model without a Position Table: attention can't tell first from last. */
    static GraphSpec order() {
        GraphSpec g = new GraphSpec();
        int x = g.add("embed", Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", "32"), GraphSpec.TOKENS);
        int a = g.add("attn", Op.ATTENTION, Config.of(Op.ATTENTION, "heads", "2"), g.add("norm1", Op.NORM, x));
        int r1 = g.add("res1", Op.ADD, x, a);
        int f = g.add("ffn", Op.MLP, g.add("norm2", Op.NORM, r1));
        int r2 = g.add("res2", Op.ADD, r1, f);
        g.output = g.add("logits", Op.LINEAR, Config.of(Op.LINEAR, "out", "vocab"), g.add("norm_out", Op.NORM, r2));
        return g;
    }

    /** Six sigmoid layers stacked with no shortcuts: the gradient fades before it reaches the bottom. */
    static GraphSpec depth() {
        GraphSpec g = new GraphSpec();
        int e = g.add("embed", Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", "16"), GraphSpec.TOKENS);
        int s1 = g.add("prev1", Op.SHIFT, Config.of(Op.SHIFT, "steps", "1"), e);
        int s2 = g.add("prev2", Op.SHIFT, Config.of(Op.SHIFT, "steps", "2"), e);
        int x = g.add("context", Op.CONCAT, g.add("pair", Op.CONCAT, e, s1), s2);
        for (int i = 1; i <= 6; i++) {
            int h = g.add("layer" + i, Op.LINEAR, Config.of(Op.LINEAR, "out", "48"), x);
            x = g.add("squash" + i, Op.ACTIVATION, Config.of(Op.ACTIVATION, "fn", "sigmoid"), h);
        }
        g.output = g.add("logits", Op.LINEAR, Config.of(Op.LINEAR, "out", "vocab"), x);
        return g;
    }
}
