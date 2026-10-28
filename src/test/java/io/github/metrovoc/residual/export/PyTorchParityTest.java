package io.github.metrovoc.residual.export;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Config;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.learn.Blueprints;
import io.github.metrovoc.residual.learn.Curriculum;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.tensor.AdamW;
import io.github.metrovoc.residual.tensor.Ops;
import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Exports models together with a few real training steps, for {@code src/test/python/parity.py} to replay in
 * PyTorch. Run with {@code RESIDUAL_PARITY_DIR=<dir>}.
 */
@EnabledIfEnvironmentVariable(named = "RESIDUAL_PARITY_DIR", matches = ".+")
class PyTorchParityTest {
    static final int STEPS = 3;
    static final float LR = 1e-2f;
    static final float CLIP = 1f;
    // ten times the usual decay, so a decay mistake would stand well clear of float32 noise
    static final float WEIGHT_DECAY = 0.1f;

    @Test
    void export() throws Exception {
        Path root = Path.of(System.getenv("RESIDUAL_PARITY_DIR"));
        lesson(root, "bigram", Blueprints.bigram(), "counting", 3, 16);
        lesson(root, "context", Blueprints.context(), "context", 3, 16);
        lesson(root, "recall", Blueprints.recall(), "recall", 3, 18);
        lesson(root, "reverse", Blueprints.reverse(), "reverse", 3, 12);
        lesson(root, "induction", Blueprints.induction(), "induction", 3, 24);
        lesson(root, "gpt", Blueprints.gpt(32, 2, 4), "language", 2, 64);
        lesson(root, "case_leak", Curriculum.byId("case_leak").brokenMachine().get(), "context", 3, 16);
        lesson(root, "case_depth", Curriculum.byId("case_depth").brokenMachine().get(), "context", 3, 16);
        dump(root.resolve("kitchen"), kitchenSink(), new Env(20, 12), "abcdefghijklmnopqrst", 3, 9);
    }

    static void lesson(Path root, String name, GraphSpec spec, String lesson, int batch, int time) throws Exception {
        Task task = Curriculum.byId(lesson).makeTask();
        dump(root.resolve(name), spec, new Env(task.vocab.size(), task.context), task.vocab.symbols, batch, time);
    }

    /** Every operator at least once, at a sequence length shorter than the context. */
    static GraphSpec kitchenSink() {
        GraphSpec g = new GraphSpec();
        int e = g.add("embed", Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", "16"), GraphSpec.TOKENS);
        int x = g.add("x", Op.ADD, e, g.add("pos", Op.POSITION, Config.of(Op.POSITION, "dim", "16")));
        int n = g.add("rms", Op.NORM, Config.of(Op.NORM, "kind", "rms"), x);
        int q = g.add("q", Op.SPLIT_HEADS, Config.of(Op.SPLIT_HEADS, "heads", "2"),
                g.add("query", Op.LINEAR, Config.of(Op.LINEAR, "out", "16", "bias", "false"), n));
        int k = g.add("k", Op.SPLIT_HEADS, Config.of(Op.SPLIT_HEADS, "heads", "2"),
                g.add("key", Op.LINEAR, Config.of(Op.LINEAR, "out", "16", "bias", "false"), n));
        int v = g.add("v", Op.SPLIT_HEADS, Config.of(Op.SPLIT_HEADS, "heads", "2"),
                g.add("value", Op.LINEAR, Config.of(Op.LINEAR, "out", "16"), n));
        int s = g.add("scores", Op.SCALE, Config.of(Op.SCALE, "factor", "0.5"),
                g.add("qk", Op.MATMUL, Config.of(Op.MATMUL, "transpose", "true"), q, k));
        int w = g.add("weights", Op.SOFTMAX, g.add("mask", Op.CAUSAL_MASK, s));
        int mixed = g.add("mixed", Op.MERGE_HEADS, g.add("wv", Op.MATMUL, w, v));
        int r = g.add("r", Op.ADD, x, mixed);
        int c = g.add("pair", Op.CONCAT, r, g.add("prev2", Op.SHIFT, Config.of(Op.SHIFT, "steps", "2"), r));
        int gate = g.add("gate", Op.ACTIVATION, Config.of(Op.ACTIVATION, "fn", "sigmoid"),
                g.add("g1", Op.LINEAR, Config.of(Op.LINEAR, "out", "32"), c));
        int h = g.add("h", Op.ACTIVATION, Config.of(Op.ACTIVATION, "fn", "silu"),
                g.add("h1", Op.LINEAR, Config.of(Op.LINEAR, "out", "32"), c));
        int act = g.add("tanh", Op.ACTIVATION, Config.of(Op.ACTIVATION, "fn", "tanh"), g.add("gated", Op.MULTIPLY, h, gate));
        act = g.add("gelu", Op.ACTIVATION, Config.of(Op.ACTIVATION, "fn", "gelu"), act);
        act = g.add("relu", Op.ACTIVATION, Config.of(Op.ACTIVATION, "fn", "relu"), act);
        int r2 = g.add("r2", Op.ADD, r, g.add("proj", Op.LINEAR, Config.of(Op.LINEAR, "out", "16"), act));
        int r3 = g.add("r3", Op.ADD, r2, g.add("attn", Op.ATTENTION, g.add("ln", Op.NORM, r2)));
        int r4 = g.add("r4", Op.ADD, r3, g.add("ffn", Op.MLP, Config.of(Op.MLP, "expand", "2", "fn", "relu"), r3));
        int b = g.add("block", Op.TRANSFORMER, Config.of(Op.TRANSFORMER, "heads", "2", "expand", "1", "fn", "silu"), r4);
        g.output = g.add("logits", Op.LINEAR, Config.of(Op.LINEAR, "out", "vocab"), g.add("scaled", Op.SCALE, b));
        return g;
    }

    static void dump(Path dir, GraphSpec spec, Env env, String vocab, int batch, int time) throws Exception {
        CompiledGraph g = CompiledGraph.compile(spec, env, null, 7);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("model.py"), PyTorch.module(g, vocab, dir.getFileName().toString()));
        Files.write(dir.resolve("weights.bin"), PyTorch.weightBytes(PyTorch.weights(g)));

        Random rng = new Random(dir.getFileName().toString().hashCode());
        int n = batch * time;
        int[] ids = new int[n];
        int[] targets = new int[n];
        float[] weights = new float[n];
        for (int i = 0; i < n; i++) {
            ids[i] = rng.nextInt(env.vocab());
            targets[i] = rng.nextInt(env.vocab());
            weights[i] = i == 0 || rng.nextInt(4) > 0 ? 1f : 0f;
        }
        Tokens tokens = new Tokens(ids, batch, time);
        AdamW opt = new AdamW(g.parameters());
        opt.weightDecay = WEIGHT_DECAY;
        StringBuilder json = new StringBuilder("{");
        json.append("\"batch\":").append(batch).append(",\"time\":").append(time).append(",\"steps\":").append(STEPS)
                .append(",\"lr\":").append(LR).append(",\"clip\":").append(CLIP).append(",\"weight_decay\":").append(WEIGHT_DECAY);
        json.append(",\"tokens\":").append(array(ids)).append(",\"targets\":").append(array(targets))
                .append(",\"weights\":").append(array(weights));
        for (int step = 0; step < STEPS; step++) {
            Tape tape = new Tape();
            Tensor logits = g.forward(tape, tokens, null);
            Tensor loss = Ops.crossEntropy(tape, logits, targets, weights);
            opt.zeroGrad();
            tape.backward(loss);
            if (step == 0) {
                json.append(",\"logits\":").append(array(logits.data)).append(",\"loss\":").append(loss.item());
                json.append(",\"grads\":").append(object(PyTorch.weights(g, true)));
            }
            opt.clip(CLIP);
            opt.step(LR);
        }
        json.append(",\"after\":").append(object(PyTorch.weights(g))).append('}');
        Files.writeString(dir.resolve("case.json"), json);
    }

    static String object(List<PyTorch.Weight> weights) {
        StringBuilder sb = new StringBuilder("{");
        for (PyTorch.Weight w : weights) {
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append('"').append(w.name()).append("\":").append(array(w.data()));
        }
        return sb.append('}').toString();
    }

    static String array(float[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            sb.append(i > 0 ? "," : "").append(values[i]);
        }
        return sb.append(']').toString();
    }

    static String array(int[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            sb.append(i > 0 ? "," : "").append(values[i]);
        }
        return sb.append(']').toString();
    }
}
