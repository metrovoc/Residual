package io.github.metrovoc.residual.scope;

import io.github.metrovoc.residual.graph.Captures;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.learn.Dataset;
import io.github.metrovoc.residual.learn.Modular;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Whether a model has induction heads. It reads runs of letters it can have no idea of, each run twice: the second time through,
 * the only way to know what comes next is to look back at what followed the same letter the first time. How often it gets that
 * right says whether it copies, and for each attention head, how much of its attention falls on that very place says whether that
 * head is doing the looking.
 */
public final class Heads {
    /** How long a run is, and how many a reading takes. */
    public static final int RUN = 16, RUNS = 32;

    /**
     * One head.
     *
     * @param prefix the share of its attention on what followed the same letter last time, over the second reading of a run
     */
    public record Head(int node, int head, float prefix) {
    }

    /**
     * @param copied how often the model named the next letter of a run it was reading the second time
     * @param heads  every attention head it has, strongest first
     */
    public record Reading(float copied, List<Head> heads) {
        @Nullable
        public Head strongest() {
            return heads.isEmpty() ? null : heads.getFirst();
        }

        public float prefix() {
            return heads.isEmpty() ? 0 : heads.getFirst().prefix();
        }
    }

    /** One head, and how often the model copies with that head's attention taken away. */
    public record Row(int node, int head, float prefix, float without) {
    }

    /**
     * Everything the Scope shows of a model's attention.
     *
     * @param pattern the strongest head's attention over a doubled run, [T][T]
     * @param waves   how much of its token table lies in each frequency round the wheel, when it knows its sums; else empty
     */
    public record Full(Stream stream, float copied, List<Row> rows, float[] pattern, float[] waves) {
    }

    private Heads() {
    }

    /** A full reading: each head taken away in turn, and what the strongest looks at. */
    public static Full full(Stream stream) {
        CompiledGraph model = stream.model;
        Tokens in = runs(model.env.tokenizer(), new Random(16), RUNS);
        Captures captures = new Captures();
        Tensor logits = model.forward(null, in, captures);
        List<Head> heads = heads(captures.patterns, in);
        List<Row> rows = new ArrayList<>();
        for (Head h : heads) {
            Captures without = new Captures();
            without.patternEdit = (node, probs) -> node == h.node() ? silenced(probs, h.head()) : probs;
            rows.add(new Row(h.node(), h.head(), h.prefix(), copied(model.forward(null, in, without), in)));
        }
        int t = in.time;
        float[] pattern = new float[0];
        if (!heads.isEmpty()) {
            Tensor p = captures.patterns.get(heads.getFirst().node());
            int off = p.rank() == 4 ? heads.getFirst().head() * t * t : 0;
            pattern = java.util.Arrays.copyOfRange(p.data, off, off + t * t);
        }
        return new Full(stream, copied(logits, in), rows, pattern, Modular.right(model, false) >= 0.5 ? waves(model) : new float[0]);
    }

    private static Tensor silenced(Tensor probs, int head) {
        float[] data = probs.data.clone();
        int t = probs.dim(-1), batch = probs.dim(0), heads = probs.rank() == 4 ? probs.dim(1) : 1;
        for (int b = 0; b < batch; b++) {
            java.util.Arrays.fill(data, (b * heads + head) * t * t, (b * heads + head + 1) * t * t, 0f);
        }
        return Tensor.of(probs.shape, data);
    }

    /**
     * How much of a model's token table, read round the wheel of residues, lies in each frequency: a table that has found the rule of
     * the sums holds it as a few waves, and one that only learned the pairs holds noise.
     */
    public static float[] waves(CompiledGraph model) {
        Tensor table = null;
        for (int node : model.order) {
            if (model.spec.node(node).op == Op.EMBEDDING) {
                table = model.params(node).get("weight");
                break;
            }
        }
        if (table == null) {
            return new float[0];
        }
        Vocab vocab = model.env.tokenizer();
        int p = Modular.P, d = table.dim(-1), freqs = (p - 1) / 2;
        float[] power = new float[freqs];
        double total = 0;
        for (int j = 0; j < d; j++) {
            double mean = 0;
            float[] column = new float[p];
            for (int r = 0; r < p; r++) {
                column[r] = table.data[vocab.id(Modular.RESIDUES.charAt(r)) * d + j];
                mean += column[r] / p;
            }
            for (int k = 1; k <= freqs; k++) {
                double re = 0, im = 0;
                for (int r = 0; r < p; r++) {
                    re += (column[r] - mean) * Math.cos(2 * Math.PI * k * r / p);
                    im += (column[r] - mean) * Math.sin(2 * Math.PI * k * r / p);
                }
                power[k - 1] += (float) (re * re + im * im);
            }
        }
        for (float w : power) {
            total += w;
        }
        for (int k = 0; k < freqs; k++) {
            power[k] /= (float) Math.max(total, 1e-9);
        }
        return power;
    }

    /** The heads, strongest first; the strongest's attention over a doubled run; and the waves, if any. */
    public static Picture picture(Full r) {
        Picture.Builder b = new Picture.Builder();
        b.text(4, 3, Component.translatable("residual.scope.heads.title", Picture.percent(r.copied)), Picture.DIM);
        if (r.rows.isEmpty()) {
            b.text(4, 18, Component.translatable("residual.scope.heads.none"), Picture.DIM);
        }
        int size = r.pattern.length == 0 ? 0 : 96, x0 = Picture.W - size - 6, rows = Math.min(8, r.rows.size());
        int width = x0 - 10;
        for (int i = 0; i < rows; i++) {
            Row row = r.rows.get(i);
            int y = 16 + i * 12;
            Component name = r.stream.name(row.node()).copy().append(r.rows.stream().anyMatch(o -> o.node() == row.node() && o.head() > 0) ? " · " + row.head() : "");
            Component tip = Component.translatable("residual.scope.heads.head", Picture.percent(row.prefix()), Picture.percent(row.without()));
            b.box(2, y - 1, width, 12, 0x00000000, tip);
            b.text(4, y + 1, 76, name, Picture.TEXT);
            b.box(82, y + 2, width - 110, 6, Picture.GRID, tip);
            b.box(82, y + 2, Math.round((width - 110) * Math.min(1f, row.prefix())), 6, row.prefix() >= Discoveries.PREFIX ? Picture.AMBER : Picture.TEAL, tip);
            b.text(width - 24, y + 1, Picture.percent(row.prefix()), Picture.TEXT);
        }
        if (size > 0) {
            int t = 2 * RUN, cell = size / t;
            for (int i = 0; i < t; i++) {
                for (int j = 0; j < t; j++) {
                    float v = r.pattern[i * t + j];
                    if (v > 0.02f) {
                        b.box(x0 + j * cell, 16 + i * cell, cell, cell, Picture.heat(v));
                    }
                }
            }
            b.line(x0, 16 + RUN * cell, x0 + size, 16 + RUN * cell, 0x60FFFFFF);
            b.line(x0 + RUN * cell, 16, x0 + RUN * cell, 16 + size, 0x60FFFFFF);
        }
        if (r.waves.length > 0) {
            int y = 118, h = 22, w = Math.max(1, (Picture.W - 16) / r.waves.length);
            float top = 0, sum = 0;
            for (float f : r.waves) {
                top = Math.max(top, f);
            }
            float[] sorted = r.waves.clone();
            java.util.Arrays.sort(sorted);
            for (int k = sorted.length - 5; k < sorted.length; k++) {
                sum += sorted[k];
            }
            b.text(4, y - 10, Component.translatable("residual.scope.heads.waves", Picture.percent(sum)), Picture.DIM);
            for (int k = 0; k < r.waves.length; k++) {
                int bar = Math.max(1, Math.round(h * r.waves[k] / top));
                b.box(4 + k * w, y + h - bar, w - 1, bar, Picture.AMBER, Component.translatable("residual.scope.heads.wave", Integer.toString(k + 1), Picture.percent(r.waves[k])));
            }
        }
        return b.build();
    }

    /** The batch of doubled runs a reading uses: distinct letters, so what follows each is plain. */
    static Tokens runs(Vocab vocab, Random rng, int runs) {
        int t = 2 * RUN;
        int[] ids = new int[runs * t];
        for (int b = 0; b < runs; b++) {
            int[] letters = Dataset.distinct(rng, Dataset.LETTERS.length(), RUN);
            for (int i = 0; i < RUN; i++) {
                int id = vocab.id(Dataset.LETTERS.charAt(letters[i]));
                ids[b * t + i] = id;
                ids[b * t + RUN + i] = id;
            }
        }
        return new Tokens(ids, runs, t);
    }

    public static Reading read(CompiledGraph model) {
        return read(model, null);
    }

    /** With {@code edit}, a reading of the model with something done to its attention. */
    public static Reading read(CompiledGraph model, @Nullable Captures.PatternEdit edit) {
        Tokens in = runs(model.env.tokenizer(), new Random(16), RUNS);
        Captures captures = new Captures();
        captures.patternEdit = edit;
        Tensor logits = model.forward(null, in, captures);
        return new Reading(copied(logits, in), heads(captures.patterns, in));
    }

    /** How often the top score, on the second reading of a run, is the next letter, its first letter left out as it can't be known. */
    static float copied(Tensor logits, Tokens in) {
        int v = logits.dim(-1), t = in.time;
        int right = 0, total = 0;
        for (int b = 0; b < in.batch; b++) {
            for (int i = RUN; i + 1 < t; i++) {
                int off = (b * t + i) * v, best = 0;
                for (int j = 1; j < v; j++) {
                    if (logits.data[off + j] > logits.data[off + best]) {
                        best = j;
                    }
                }
                right += best == in.at(b, i + 1) ? 1 : 0;
                total++;
            }
        }
        return (float) right / total;
    }

    private static List<Head> heads(Map<Integer, Tensor> patterns, Tokens in) {
        List<Head> out = new ArrayList<>();
        int t = in.time;
        patterns.forEach((node, p) -> {
            if (p.rank() < 3 || p.dim(-1) != t) {
                return;
            }
            int heads = p.rank() == 4 ? p.dim(1) : 1;
            for (int h = 0; h < heads; h++) {
                double sum = 0;
                int count = 0;
                for (int b = 0; b < in.batch; b++) {
                    int off = (b * heads + h) * t * t;
                    for (int i = RUN; i + 1 < t; i++) {
                        sum += p.data[off + i * t + i - RUN + 1];
                        count++;
                    }
                }
                out.add(new Head(node, h, (float) (sum / count)));
            }
        });
        out.sort((a, b) -> Float.compare(b.prefix(), a.prefix()));
        return out;
    }
}
