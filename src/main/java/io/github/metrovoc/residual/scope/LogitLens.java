package io.github.metrovoc.residual.scope;

import io.github.metrovoc.residual.graph.Captures;
import io.github.metrovoc.residual.learn.Batch;
import io.github.metrovoc.residual.learn.Sampler;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.tensor.Ops;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import net.minecraft.network.chat.Component;

/**
 * The logit lens: at each point of the stream, what the model would say if the rest of the stack were not there, the head reading
 * the stream as it stands. Row by row, a prediction forms.
 */
public final class LogitLens {
    /** How many of the last tokens a picture shows, and how many sequences the points' losses take. */
    public static final int SHOWN = 24, SAMPLE = 16;

    /**
     * @param top   each point's likeliest tokens at each position: [point][position][rank]
     * @param probs and their probabilities
     * @param loss  each point's loss per token, over a sample of the test; empty with none given
     */
    public record Reading(Stream stream, int[] tokens, int[][][] top, float[][][] probs, float[] loss) {
        /** How sure the point is of its likeliest token at the last position. */
        public float sure(int point) {
            return probs[point][tokens.length - 1][0];
        }

        /** The likeliest token at the last position, as a point has it. */
        public int said(int point) {
            return top[point][tokens.length - 1][0];
        }
    }

    private LogitLens() {
    }

    public static Reading read(Stream stream, Tokens one, Batch sample) {
        Captures captures = Reads.run(stream.model, one);
        int t = one.time;
        int[][][] top = new int[stream.points.length][t][];
        float[][][] probs = new float[stream.points.length][t][];
        for (int p = 0; p < stream.points.length; p++) {
            Tensor logits = stream.read(null, captures.outputs.get(stream.points[p]));
            for (int i = 0; i < t; i++) {
                float[] q = Reads.probs(logits, i);
                top[p][i] = Sampler.top(q, 3);
                probs[p][i] = new float[top[p][i].length];
                for (int k = 0; k < top[p][i].length; k++) {
                    probs[p][i][k] = q[top[p][i][k]];
                }
            }
        }
        float[] loss = new float[stream.points.length];
        if (sample != null) {
            Captures full = Reads.run(stream.model, sample.input());
            for (int p = 0; p < loss.length; p++) {
                Tensor logits = stream.read(null, full.outputs.get(stream.points[p]));
                loss[p] = Ops.crossEntropy(null, logits, sample.targets(), sample.weights()).item();
            }
        }
        return new Reading(stream, one.ids, top, probs, loss);
    }

    /** A row per point and a column per token: the likeliest next token in each cell, brighter the surer. */
    public static Picture picture(Reading r) {
        Vocab vocab = r.stream.model.env.tokenizer();
        Picture.Builder b = new Picture.Builder();
        int t = r.tokens.length, shown = Math.min(SHOWN, t), first = t - shown;
        int labels = 62, right = 34, cell = (Picture.W - labels - right - 8) / Math.max(shown, 8), rows = r.stream.points.length;
        int height = Math.min(24, (Picture.H - 40) / Math.max(rows, 1));
        int top = 14;
        b.text(4, 3, Component.translatable("residual.scope.lens.title"), Picture.DIM);
        b.text(Picture.W - right, 3, Component.translatable("residual.scope.lens.loss"), Picture.DIM);
        for (int p = 0; p < rows; p++) {
            int y = top + p * height;
            b.text(4, y + (height - 9) / 2 + 1, labels - 6, r.stream.name(r.stream.points[p]), Picture.TEXT);
            for (int i = first; i < t; i++) {
                float sure = r.probs[p][i][0];
                boolean named = i + 1 < t && r.top[p][i][0] == r.tokens[i + 1];
                int x = labels + (i - first) * cell;
                b.box(x, y, cell - 1, height - 1, Picture.share(sure), tip(vocab, r.top[p][i], r.probs[p][i]));
                b.text(x + 2, y + (height - 9) / 2 + 1, cell - 3, Picture.shown(vocab.piece(r.top[p][i][0])), named ? Picture.GREEN : Picture.TEXT);
            }
            b.text(Picture.W - right, y + (height - 9) / 2 + 1, Picture.two(r.loss[p]), Picture.AMBER);
        }
        // what it read, along the bottom
        int y = top + rows * height + 2;
        for (int i = first; i < t; i++) {
            b.text(labels + (i - first) * cell + 1, y, cell - 1, Picture.shown(vocab.piece(r.tokens[i])), Picture.DIM);
        }
        return b.build();
    }

    private static Component tip(Vocab vocab, int[] top, float[] probs) {
        StringBuilder s = new StringBuilder();
        for (int k = 0; k < top.length; k++) {
            s.append(k == 0 ? "" : "   ").append(Picture.shown(vocab.piece(top[k]))).append(' ').append(Picture.percent(probs[k]));
        }
        return Component.literal(s.toString());
    }
}
