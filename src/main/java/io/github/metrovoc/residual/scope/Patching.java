package io.github.metrovoc.residual.scope;

import io.github.metrovoc.residual.graph.Captures;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.learn.Dataset;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import net.minecraft.network.chat.Component;

/**
 * Activation patching: read one token wrong, so the model's answer changes; then run it on the wrong text again and again, each time
 * putting back what the stream carried at one point and one position when it read the right text. Where that restores the answer,
 * that is where the fact travels.
 */
public final class Patching {
    /**
     * @param clean    what it read
     * @param changed  the place read wrong, and what was read there
     * @param answer   the token it names on the clean text
     * @param other    the token it names on the wrong text
     * @param restored [point][position]: how much of the way back to the clean answer each patch went, 1 all the way
     */
    public record Reading(Stream stream, int[] clean, int changed, int with, int answer, int other, float gap, float[][] restored) {
    }

    private Patching() {
    }

    /** Reads {@code clean}, wrong at the one place that moves its answer most; null if no one token moves it. */
    public static Reading read(Stream stream, Tokens clean) {
        CompiledGraph model = stream.model;
        Captures from = Reads.run(model, clean);
        Tensor logits = (Tensor) from.outputs.get(model.spec.output);
        int t = clean.time, v = logits.dim(-1), last = (t - 1) * v;
        int answer = Reads.top(logits.data, last, v);
        // the wrong token that hurts most, trying one of its own kind at each place
        int changed = -1, with = -1, other = -1;
        float widest = 0;
        for (int i = 0; i < t - 1; i++) {
            for (int candidate : similar(model.env.tokenizer(), clean.ids[i])) {
                int[] ids = clean.ids.clone();
                ids[i] = candidate;
                Tensor out = model.forward(null, new Tokens(ids, 1, t), null);
                int named = Reads.top(out.data, last, v);
                float fall = out.data[last + named] - out.data[last + answer];
                if (named != answer && fall > widest) {
                    widest = fall;
                    changed = i;
                    with = candidate;
                    other = named;
                }
            }
        }
        if (changed < 0) {
            return null;
        }
        int[] wrong = clean.ids.clone();
        wrong[changed] = with;
        Tensor before = model.forward(null, new Tokens(wrong, 1, t), null);
        float base = before.data[last + answer] - before.data[last + other];
        float full = logits.data[last + answer] - logits.data[last + other];
        float[][] restored = new float[stream.points.length][t];
        for (int p = 0; p < stream.points.length; p++) {
            int point = stream.points[p];
            float[] good = from.outputs.get(point).data;
            int d = stream.width();
            for (int i = changed; i < t; i++) {
                int at = i;
                Captures patch = new Captures();
                patch.edit = (tape, node, out) -> {
                    if (node != point) {
                        return out;
                    }
                    float[] data = out.data.clone();
                    System.arraycopy(good, at * d, data, at * d, d);
                    return Tensor.of(out.shape, data);
                };
                Tensor after = model.forward(null, new Tokens(wrong, 1, t), patch);
                restored[p][i] = (after.data[last + answer] - after.data[last + other] - base) / (full - base);
            }
        }
        return new Reading(stream, clean.ids, changed, with, answer, other, full - base, restored);
    }

    /** Tokens of the same kind as {@code id}: another digit for a digit, another letter for a letter, or else the token after it. */
    private static int[] similar(Vocab vocab, int id) {
        String piece = vocab.piece(id);
        char c = piece.charAt(0);
        String pool = Character.isDigit(c) ? Dataset.LANDMARKS : Character.isLetter(c) ? Dataset.LETTERS : "";
        if (pool.isEmpty() || piece.length() > 1) {
            return new int[] {(id + 1) % vocab.size()};
        }
        int at = Math.max(0, pool.indexOf(Character.toLowerCase(c)));
        return new int[] {vocab.id(pool.charAt((at + 1) % pool.length())), vocab.id(pool.charAt((at + pool.length() / 2) % pool.length()))};
    }

    /** A row per point and a column per token, bright where putting the clean stream back restores the answer. */
    public static Picture picture(Reading r) {
        Vocab vocab = r.stream.model.env.tokenizer();
        Picture.Builder b = new Picture.Builder();
        int t = r.clean.length, first = Math.max(0, Math.min(r.changed, t - LogitLens.SHOWN)), shown = t - first;
        int labels = 62, cell = (Picture.W - labels - 8) / Math.max(shown, 12), rows = r.stream.points.length;
        int height = Math.min(16, (Picture.H - 50) / Math.max(rows, 1)), top = 26;
        b.text(4, 3, Component.translatable("residual.scope.patch.title", Picture.shown(vocab.piece(r.clean[r.changed])), Picture.shown(vocab.piece(r.with)),
                Integer.toString(r.changed)), Picture.DIM);
        b.text(4, 13, Component.translatable("residual.scope.patch.answers", Picture.shown(vocab.piece(r.answer)), Picture.shown(vocab.piece(r.other))), Picture.TEXT);
        for (int p = 0; p < rows; p++) {
            int y = top + p * height;
            b.text(4, y + (height - 9) / 2 + 1, labels - 6, r.stream.name(r.stream.points[p]), Picture.TEXT);
            for (int i = first; i < t; i++) {
                float f = r.restored[p][i];
                b.box(labels + (i - first) * cell, y, cell - 1, height - 1, Picture.heat(f),
                        Component.translatable("residual.scope.patch.cell", Picture.percent(f)));
            }
        }
        int y = top + rows * height + 2;
        for (int i = first; i < t; i++) {
            b.text(labels + (i - first) * cell + 1, y, cell - 1, Picture.shown(vocab.piece(r.clean[i])), i == r.changed ? Picture.AMBER : Picture.DIM);
        }
        return b.build();
    }
}
