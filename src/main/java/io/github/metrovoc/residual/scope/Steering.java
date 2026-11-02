package io.github.metrovoc.residual.scope;

import io.github.metrovoc.residual.graph.Captures;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.learn.Batch;
import io.github.metrovoc.residual.learn.Sampler;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.tensor.Ops;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.Arrays;
import java.util.Random;
import net.minecraft.network.chat.Component;

/**
 * Steering: the average of what the stream carries where one token comes next, minus the average where another does, is a direction
 * that means "that token". Adding it to the stream as the model reads pushes the model that way, whatever it reads, at a cost the
 * loss shows.
 */
public final class Steering {
    /** How many times the direction is added, along the picture's bottom; one moves the average place all the way. */
    public static final float[] STRENGTHS = {0f, 0.5f, 1f, 2f, 4f, 8f};
    /** How many tokens it can be steered toward: those the test most often asks for. */
    public static final int CHOICES = 8;
    private static final int WRITTEN = 60;

    /**
     * @param token   what it is steered toward
     * @param share   at each strength, how often that token is its first choice
     * @param chance  at each strength, how likely it holds that token
     * @param loss    at each strength, its loss on the test's text
     * @param plain   what it writes as it is
     * @param steered what it writes at the strength picked
     */
    public record Reading(Stream stream, int point, int token, float[] share, float[] chance, float[] loss, String plain, String steered, float strength) {
    }

    private Steering() {
    }

    /** The tokens the test most often asks for, most often first. */
    public static int[] choices(Batch sample, int vocab) {
        long[] counts = new long[vocab];
        for (int i = 0; i < sample.targets().length; i++) {
            if (sample.weights() == null || sample.weights()[i] != 0) {
                counts[sample.targets()[i]]++;
            }
        }
        Integer[] order = new Integer[vocab];
        for (int i = 0; i < vocab; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Long.compare(counts[b], counts[a]));
        int n = 0;
        while (n < Math.min(CHOICES, vocab) && counts[order[n]] > 0) {
            n++;
        }
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = order[i];
        }
        return out;
    }

    /** The direction at {@code point} that means {@code token} comes next, over the scored places of {@code sample}. */
    public static float[] direction(Stream stream, int point, int token, Batch sample) {
        float[] x = Reads.run(stream.model, sample.input()).outputs.get(point).data;
        int d = stream.width();
        double[] yes = new double[d], no = new double[d];
        int ys = 0, ns = 0;
        for (int i = 0; i < sample.targets().length; i++) {
            if (sample.weights() != null && sample.weights()[i] == 0) {
                continue;
            }
            boolean is = sample.targets()[i] == token;
            double[] side = is ? yes : no;
            for (int j = 0; j < d; j++) {
                side[j] += x[i * d + j];
            }
            ys += is ? 1 : 0;
            ns += is ? 0 : 1;
        }
        float[] v = new float[d];
        for (int j = 0; j < d; j++) {
            v[j] = (float) (yes[j] / Math.max(ys, 1) - no[j] / Math.max(ns, 1));
        }
        return v;
    }

    /** What adds {@code strength} of {@code v} to the stream at {@code point}, at every position. */
    static Captures adding(int point, float[] v, float strength) {
        Captures c = new Captures();
        c.edit = (tape, node, out) -> {
            if (node != point || strength == 0) {
                return out;
            }
            float[] data = out.data.clone();
            int d = v.length;
            for (int r = 0; r < data.length / d; r++) {
                for (int j = 0; j < d; j++) {
                    data[r * d + j] += strength * v[j];
                }
            }
            return Tensor.of(out.shape, data);
        };
        return c;
    }

    public static Reading read(Stream stream, int point, int token, Batch sample, Tokens prompt, float picked, long seed) {
        CompiledGraph model = stream.model;
        float[] v = direction(stream, point, token, sample);
        float[] share = new float[STRENGTHS.length], chance = new float[STRENGTHS.length], loss = new float[STRENGTHS.length];
        for (int s = 0; s < STRENGTHS.length; s++) {
            Tensor logits = model.forward(null, sample.input(), adding(point, v, STRENGTHS[s]));
            int w = logits.dim(-1), scored = 0, named = 0;
            double held = 0;
            for (int i = 0; i < sample.targets().length; i++) {
                if (sample.weights() != null && sample.weights()[i] == 0) {
                    continue;
                }
                scored++;
                named += Reads.top(logits.data, i * w, w) == token ? 1 : 0;
                held += Reads.probs(logits, i)[token];
            }
            share[s] = (float) named / Math.max(scored, 1);
            chance[s] = (float) (held / Math.max(scored, 1));
            loss[s] = Ops.crossEntropy(null, logits, sample.targets(), sample.weights()).item();
        }
        return new Reading(stream, point, token, share, chance, loss, write(stream, prompt, point, v, 0, seed), write(stream, prompt, point, v, picked, seed), picked);
    }

    /** Writes on from {@code prompt}, steered by {@code strength}, from the model's own probabilities. */
    public static String write(Stream stream, Tokens prompt, int point, float[] v, float strength, long seed) {
        Vocab vocab = stream.model.env.tokenizer();
        Random rng = new Random(seed);
        int[] seq = prompt.ids;
        StringBuilder out = new StringBuilder();
        for (int n = 0; n < WRITTEN; n++) {
            int[] window = Arrays.copyOfRange(seq, Math.max(0, seq.length - Vocab.CONTEXT), seq.length);
            Tensor logits = stream.model.forward(null, new Tokens(window, 1, window.length), adding(point, v, strength));
            float[] p = Reads.probs(logits, window.length - 1);
            int token = draw(p, rng);
            out.append(vocab.piece(token));
            seq = Arrays.copyOf(seq, seq.length + 1);
            seq[seq.length - 1] = token;
        }
        return out.toString();
    }

    private static int draw(float[] p, Random rng) {
        float r = rng.nextFloat(), acc = 0;
        for (int i = 0; i < p.length; i++) {
            acc += p[i];
            if (r < acc) {
                return i;
            }
        }
        return Sampler.top(p, 1)[0];
    }

    public static Picture picture(Reading r) {
        Vocab vocab = r.stream.model.env.tokenizer();
        Picture.Builder b = new Picture.Builder();
        String token = Picture.shown(vocab.piece(r.token));
        b.text(4, 3, Component.translatable("residual.scope.steer.title", token, r.stream.name(r.point)), Picture.DIM);
        int x0 = 86, w = Picture.W - x0 - 10, cell = w / STRENGTHS.length, rows = 3, height = 14, top = 26;
        for (int s = 0; s < STRENGTHS.length; s++) {
            b.text(x0 + s * cell + 2, 15, "×" + (STRENGTHS[s] == (int) STRENGTHS[s] ? Integer.toString((int) STRENGTHS[s]) : "0.5"), Picture.DIM);
        }
        float worst = 0;
        for (float l : r.loss) {
            worst = Math.max(worst, l);
        }
        String[] names = {"residual.scope.steer.first", "residual.scope.steer.chance", "residual.scope.steer.loss"};
        for (int row = 0; row < rows; row++) {
            int y = top + row * height;
            b.text(4, y + 2, x0 - 8, Component.translatable(names[row]), Picture.TEXT);
            for (int s = 0; s < STRENGTHS.length; s++) {
                float v = row == 0 ? r.share[s] : row == 1 ? r.chance[s] : r.loss[s];
                int color = row == 2 ? Picture.heat(worst == 0 ? 0 : v / worst) : Picture.share(v);
                b.box(x0 + s * cell, y, cell - 1, height - 1, color);
                b.text(x0 + s * cell + 2, y + 2, row == 2 ? Picture.two(v) : Picture.percent(v), Picture.TEXT);
            }
        }
        int y = top + rows * height + 6;
        b.text(4, y, Component.translatable("residual.scope.steer.plain"), Picture.DIM);
        b.text(4, y + 10, Picture.W - 8, Picture.shown(r.plain), Picture.TEXT);
        b.text(4, y + 24, Component.translatable("residual.scope.steer.steered", String.format(java.util.Locale.ROOT, "%.1f", r.strength)), Picture.DIM);
        b.text(4, y + 34, Picture.W - 8, Picture.shown(r.steered), Picture.AMBER);
        return b.build();
    }
}
