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
