package io.github.metrovoc.residual.scope;

import io.github.metrovoc.residual.graph.Captures;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.learn.Batch;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Played;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.Arrays;
import java.util.Random;
import org.jetbrains.annotations.Nullable;

/** What the instruments read: stretches of a benchmark's test, or what the player types, and what a model makes of them. */
public final class Reads {
    private Reads() {
    }

    /** A benchmark's held-out test, as the model reads it. */
    public static Task test(CompiledGraph model, Benchmark benchmark) {
        Task task = benchmark.test(model.env.tokenizer());
        if (task instanceof Played played) {
            played.policy(model);
        }
        return task;
    }

    /** {@code size} stretches of a benchmark's test. A played test has no stretches of its own, so it reads what it practises on. */
    public static Batch stretches(CompiledGraph model, Benchmark benchmark, Random rng, int size) {
        Task task = test(model, benchmark);
        return task.batch(rng, size, !(task instanceof Played));
    }

    /** One line of what the player typed, at most as long as a model reads; null when nothing of it can be read. */
    @Nullable
    public static Tokens typed(CompiledGraph model, String text) {
        Vocab vocab = model.env.tokenizer();
        int[] ids = vocab.encode(text);
        if (ids.length == 0) {
            return null;
        }
        int[] last = Arrays.copyOfRange(ids, Math.max(0, ids.length - model.env.context()), ids.length);
        return new Tokens(last, 1, last.length);
    }

    /** One stretch of a benchmark's test, up to its first answer when it is scored only there, as an example to look at. */
    public static Example example(CompiledGraph model, Benchmark benchmark, Random rng) {
        Batch b = stretches(model, benchmark, rng, 1);
        int t = b.input().time, end = t;
        if (b.weights() != null) {
            for (int i = 0; i < t; i++) {
                if (b.weights()[i] != 0) {
                    end = i + 1;
                    break;
                }
            }
        }
        int[] ids = Arrays.copyOfRange(b.input().ids, 0, end);
        return new Example(new Tokens(ids, 1, end), b.targets()[end - 1]);
    }

    /** A stretch to look at, and the token that really comes after it. */
    public record Example(Tokens tokens, int next) {
    }

    /** Runs the model and keeps what every node gave. */
    public static Captures run(CompiledGraph model, Tokens tokens) {
        Captures captures = new Captures();
        model.forward(null, tokens, captures);
        return captures;
    }

    /** Probabilities from the scores at row {@code row} of [.., V]. */
    public static float[] probs(Tensor logits, int row) {
        int v = logits.dim(-1);
        float[] p = new float[v];
        float max = Float.NEGATIVE_INFINITY;
        for (int j = 0; j < v; j++) {
            max = Math.max(max, logits.data[row * v + j]);
        }
        double sum = 0;
        for (int j = 0; j < v; j++) {
            p[j] = (float) Math.exp(logits.data[row * v + j] - max);
            sum += p[j];
        }
        for (int j = 0; j < v; j++) {
            p[j] /= (float) sum;
        }
        return p;
    }

    /** The place of the largest value of {@code n} from {@code off}. */
    public static int top(float[] data, int off, int n) {
        int best = 0;
        for (int j = 1; j < n; j++) {
            if (data[off + j] > data[off + best]) {
                best = j;
            }
        }
        return best;
    }
}
