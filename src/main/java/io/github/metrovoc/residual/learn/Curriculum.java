package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.Config;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.graph.Op;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The lessons and casebook, in order. Lessons teach a mechanism and award an Insight; cases hand you a broken
 * machine to debug. Player-facing text lives in the language file under {@code residual.lesson.<id>.*}.
 */
public final class Curriculum {
    public enum Kind { LESSON, CASE }

    /** Optimiser settings a lesson starts from. */
    public record Settings(float learningRate, int batchSize, float clip) {
        public static final Settings DEFAULT = new Settings(3e-3f, 32, 1f);
    }

    /** Steps a case may train before its run counts as failed; reset the weights to try again. */
    public static final int CASE_BUDGET = 1500;

    /**
     * @param brokenMachine for cases, the machine the case starts from
     * @param budget        training steps allowed per run, or 0 for no limit
     */
    public record Lesson(String id, Kind kind, Supplier<Task> task, Settings settings, String insight,
                         Supplier<GraphSpec> brokenMachine, int budget) {
        public Task makeTask() {
            return Curriculum.task(id, task);
        }
    }

    private static final Map<String, Task> TASKS = new ConcurrentHashMap<>();
    private static final Map<String, String> CORPORA = new ConcurrentHashMap<>();

    public static final List<Lesson> ALL = List.of(
            lesson("counting", () -> new TextTask("counting", corpus("words.txt"), 16, 2.62, Set.of()), new Settings(1e-2f, 32, 1f), "insight_counting"),
            lesson("context", () -> new TextTask("context", corpus("words.txt"), 16, 2.45, Set.of()), Settings.DEFAULT, "insight_context"),
            lesson("recall", () -> PuzzleTask.recall("recall", 8, 0.9), Settings.DEFAULT, "insight_attention"),
            lesson("reverse", () -> PuzzleTask.reverse("reverse", 6, 0.9), Settings.DEFAULT, "insight_position"),
            lesson("induction", () -> PuzzleTask.induction("induction", 24, 0.9, Set.of(Op.SHIFT)), Settings.DEFAULT, "insight_circuits"),
            lesson("language", () -> new TextTask("language", corpus("shakespeare.txt"), 64, 1.95, Set.of()), new Settings(3e-3f, 16, 1f), "insight_language"),

            broken("case_shape", "counting", Settings.DEFAULT, Curriculum::caseShape),
            broken("case_diverge", "context", new Settings(1.5f, 32, 0f), Blueprints::context),
            broken("case_leak", "context", Settings.DEFAULT, Curriculum::caseLeak),
            broken("case_order", "reverse", Settings.DEFAULT, Curriculum::caseOrder),
            broken("case_depth", "context", Settings.DEFAULT, Curriculum::caseDepth));

    private Curriculum() {
    }

    private static Lesson lesson(String id, Supplier<Task> task, Settings settings, String insight) {
        return new Lesson(id, Kind.LESSON, task, settings, insight, null, 0);
    }

    /** A case reuses a lesson's task, starting from a deliberately broken machine. */
    private static Lesson broken(String id, String taskOf, Settings settings, Supplier<GraphSpec> machine) {
        return new Lesson(id, Kind.CASE, () -> byId(taskOf).makeTask(), settings, null, machine, CASE_BUDGET);
    }

    public static Lesson byId(String id) {
        for (Lesson l : ALL) {
            if (l.id().equals(id)) {
                return l;
            }
        }
        return null;
    }

    static Task task(String id, Supplier<Task> make) {
        return TASKS.computeIfAbsent(id, k -> make.get());
    }

    public static String corpus(String name) {
        return CORPORA.computeIfAbsent(name, n -> {
            try (InputStream in = Curriculum.class.getResourceAsStream("/corpus/" + n)) {
                if (in == null) {
                    throw new IOException("missing corpus " + n);
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    // ------------------------------------------------------------------ casebook machines

    /** The unembedding is 32 wide instead of one score per letter. */
    static GraphSpec caseShape() {
        GraphSpec g = new GraphSpec();
        int e = g.add("embed", Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", "32"), GraphSpec.TOKENS);
        g.output = g.add("logits", Op.LINEAR, Config.of(Op.LINEAR, "out", "32"), e);
        return g;
    }

    /** Attention with no causal mask: every position can read the letter it is supposed to predict. */
    static GraphSpec caseLeak() {
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

    /** The reversal machine without a Position Table: attention can't tell first from last. */
    static GraphSpec caseOrder() {
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
    static GraphSpec caseDepth() {
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
