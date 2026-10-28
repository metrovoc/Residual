package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.tensor.Ops;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Autoregressive generation, one token at a time. */
public final class Sampler {
    /** One generated token, its probability, and the top candidates at that step. */
    public record Step(int token, float prob, int[] topIds, float[] topProbs) {
    }

    private Sampler() {
    }

    /** Next-token probabilities after {@code prompt}, using at most {@code context} tokens of history. */
    public static float[] nextProbs(CompiledGraph model, int[] prompt, int context, float temperature) {
        int start = Math.max(0, prompt.length - context);
        int[] window = Arrays.copyOfRange(prompt, start, prompt.length);
        if (window.length == 0) {
            window = new int[]{0};
        }
        Tensor logits = model.forward(null, new Tokens(window, 1, window.length), null);
        int v = logits.dim(-1);
        float[] last = Arrays.copyOfRange(logits.data, (window.length - 1) * v, window.length * v);
        float t = Math.max(temperature, 1e-3f);
        for (int i = 0; i < v; i++) {
            last[i] /= t;
        }
        return Ops.softmax(null, Tensor.of(new int[]{v}, last)).data;
    }

    public static List<Step> generate(CompiledGraph model, int[] prompt, int count, int context, float temperature, Random rng) {
        List<Step> steps = new ArrayList<>();
        int[] seq = prompt.clone();
        for (int n = 0; n < count; n++) {
            float[] p = nextProbs(model, seq, context, temperature);
            int token = temperature <= 0.01f ? Trainer.argmax(p, 0, p.length) : draw(p, rng);
            int[] top = top(p, 5);
            float[] topP = new float[top.length];
            for (int i = 0; i < top.length; i++) {
                topP[i] = p[top[i]];
            }
            steps.add(new Step(token, p[token], top, topP));
            seq = Arrays.copyOf(seq, seq.length + 1);
            seq[seq.length - 1] = token;
        }
        return steps;
    }

    /**
     * What the Sample tab shows: for text, a continuation of {@code prompt} (or of a validation snippet when it is
     * empty); for puzzles, the model's answers to a fresh one next to the right ones.
     *
     * @param labels the most likely tokens at the first generated (or answered) position
     * @param probs  their probabilities
     */
    public record Demo(String prompt, String text, List<String> labels, List<Float> probs, String note) {
    }

    public static Demo demo(CompiledGraph model, Task task, String prompt, float temperature, Random rng) {
        if (!(task instanceof TextTask text)) {
            return puzzle(model, task, rng);
        }
        int[] ids = task.vocab.encode(prompt);
        if (ids.length == 0) {
            ids = task.vocab.encode(text.prompt(rng, Math.min(32, task.context)));
        }
        String given = task.vocab.decode(ids);
        List<Step> steps = generate(model, ids, 160, task.context, temperature, rng);
        StringBuilder out = new StringBuilder(given);
        steps.forEach(s -> out.append(task.vocab.symbol(s.token())));
        Step first = steps.getFirst();
        return new Demo(given, out.toString(), labels(task.vocab, first.topIds()), floats(first.topProbs()), "");
    }

    private static Demo puzzle(CompiledGraph model, Task task, Random rng) {
        Batch b = task.batch(rng, 1, true);
        Tensor logits = model.forward(null, b.input(), null);
        int v = logits.dim(-1);
        int[] in = b.input().ids;
        int first = -1;
        int right = 0;
        int total = 0;
        StringBuilder said = new StringBuilder();
        StringBuilder expected = new StringBuilder();
        float[] firstProbs = null;
        for (int i = 0; i < in.length; i++) {
            if (b.weights()[i] == 0f) {
                continue;
            }
            int guess = Trainer.argmax(logits.data, i * v, v);
            if (first < 0) {
                first = i;
                firstProbs = Ops.softmax(null, Tensor.of(new int[]{v}, Arrays.copyOfRange(logits.data, i * v, (i + 1) * v))).data;
            }
            said.append(task.vocab.symbol(guess));
            expected.append(task.vocab.symbol(b.targets()[i]));
            total++;
            right += guess == b.targets()[i] ? 1 : 0;
        }
        String given = task.vocab.decode(Arrays.copyOfRange(in, 0, first + 1));
        int[] top = top(firstProbs, 5);
        float[] topP = new float[top.length];
        for (int i = 0; i < top.length; i++) {
            topP[i] = firstProbs[top[i]];
        }
        String note = "Expected " + expected + ": " + right + " of " + total + " right.";
        return new Demo(given, given + said, labels(task.vocab, top), floats(topP), note);
    }

    private static List<String> labels(Vocab vocab, int[] ids) {
        List<String> out = new ArrayList<>(ids.length);
        for (int id : ids) {
            out.add(vocab.label(id));
        }
        return out;
    }

    private static List<Float> floats(float[] values) {
        List<Float> out = new ArrayList<>(values.length);
        for (float f : values) {
            out.add(f);
        }
        return out;
    }

    static int draw(float[] p, Random rng) {
        float r = rng.nextFloat();
        float acc = 0f;
        for (int i = 0; i < p.length; i++) {
            acc += p[i];
            if (r < acc) {
                return i;
            }
        }
        return p.length - 1;
    }

    public static int[] top(float[] p, int k) {
        Integer[] idx = new Integer[p.length];
        for (int i = 0; i < p.length; i++) {
            idx[i] = i;
        }
        Arrays.sort(idx, (a, b) -> Float.compare(p[b], p[a]));
        int[] out = new int[Math.min(k, p.length)];
        for (int i = 0; i < out.length; i++) {
            out[i] = idx[i];
        }
        return out;
    }
}
